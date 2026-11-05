package io.github.abdurazaaqmohammed.boot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Boot Image virtual archives in cache.
 * Unpacks to cache folder, watches for edits, and provides repack back to original file.
 */
public class BootInspectManager {

    private static final Map<String, Session> activeSessions = new ConcurrentHashMap<>();

    public static class Session {
        public final File originalImg;
        public final File inspectDir;
        public final File ramdiskDir;
        public long lastModifiedSnapshot;
        public final Map<String, Long> fileSnapshots = new HashMap<>();

        public Session(File originalImg, File inspectDir, File ramdiskDir) {
            this.originalImg = originalImg;
            this.inspectDir = inspectDir;
            this.ramdiskDir = ramdiskDir;
            this.lastModifiedSnapshot = originalImg.lastModified();
            takeSnapshot();
        }

        public void takeSnapshot() {
            fileSnapshots.clear();
            File target = (ramdiskDir != null && ramdiskDir.exists()) ? ramdiskDir : inspectDir;
            snapshotDirectory(target);
        }

        private void snapshotDirectory(File dir) {
            if (dir == null || !dir.exists()) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith("temp_repack.") || name.equals("header_info.json") || name.equals("ramdisk_tree.json")) {
                    continue;
                }
                fileSnapshots.put(f.getAbsolutePath(), f.lastModified() ^ (f.length() << 16));
                if (f.isDirectory()) {
                    snapshotDirectory(f);
                }
            }
        }

        public boolean isDirty() {
            Map<String, Long> current = new HashMap<>();
            File target = (ramdiskDir != null && ramdiskDir.exists()) ? ramdiskDir : inspectDir;
            collectCurrent(target, current);
            return !fileSnapshots.equals(current);
        }

        private void collectCurrent(File dir, Map<String, Long> current) {
            if (dir == null || !dir.exists()) return;
            File[] files = dir.listFiles();
            if (files == null) return;
            for (File f : files) {
                // Ignore temporary or tree metadata generated during pack/repack
                String name = f.getName();
                if (name.startsWith("temp_repack.") || name.equals("header_info.json") || name.equals("ramdisk_tree.json")) {
                    continue;
                }
                current.put(f.getAbsolutePath(), f.lastModified() ^ (f.length() << 16));
                if (f.isDirectory()) {
                    collectCurrent(f, current);
                }
            }
        }
    }

    public static boolean isBootInspectFolder(File folder) {
        if (folder == null) return false;
        File check = folder;
        while (check != null) {
            if (activeSessions.containsKey(check.getAbsolutePath())) return true;
            check = check.getParentFile();
        }
        return false;
    }

    public static Session getSessionForFolder(File folder) {
        if (folder == null) return null;
        File check = folder;
        while (check != null) {
            Session s = activeSessions.get(check.getAbsolutePath());
            if (s != null) return s;
            check = check.getParentFile();
        }
        return null;
    }

    /**
     * Resolves the parent directory for navigation. If the folder is the root of an unpacked
     * boot image inspection (inspectDir or ramdiskDir if inspectDir was directly opened),
     * returns the folder containing the original .img file instead of going up into cache.
     */
    public static File getParentOrImageOrigin(File folder) {
        if (folder == null) return null;
        Session session = getSessionForFolder(folder);
        if (session != null) {
            // If we are at the top-level inspect dir, or ramdisk dir when ramdisk is top-level
            if (folder.equals(session.inspectDir) || folder.equals(session.ramdiskDir)) {
                return session.originalImg.getParentFile();
            }
        }
        return folder.getParentFile();
    }

    public static void openBootImage(MainActivity context, File imageFile, boolean pane1) {
        if (imageFile == null || !imageFile.exists()) return;
        Extensions.showMessage(context, "Unpacking Boot Image...");

        new Thread(() -> {
            try {
                String hash = hashPath(imageFile.getAbsolutePath());
                File cacheDir = new File(context.getCacheDir(), "boot_inspect_" + hash);
                // Unpack
                BootImageEngine.unpack(imageFile, cacheDir, null);

                File ramdiskDir = new File(cacheDir, "ramdisk");
                File openTarget = ramdiskDir.exists() ? ramdiskDir : cacheDir;

                Session session = new Session(imageFile, cacheDir, ramdiskDir);
                activeSessions.put(cacheDir.getAbsolutePath(), session);
                activeSessions.put(openTarget.getAbsolutePath(), session);

                new Handler(Looper.getMainLooper()).post(() -> {
                    context.loadFolderInPane(openTarget, pane1, true);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    public static void checkAndRepackIfNeeded(MainActivity context, File folder, boolean pane1) {
        Session session = getSessionForFolder(folder);
        if (session == null || !session.originalImg.exists()) return;

        // Dirty checking: if no files were added, deleted or modified, skip repack entirely
        if (!session.isDirty()) {
            return;
        }

        // Changes were made — ask the user before overwriting the boot image
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Save Changes to Boot Image?")
                .setMessage("Modifications were detected in the ramdisk. Do you want to repack and update '" + session.originalImg.getName() + "'?")
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    executeRepack(context, session);
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    // Discard changes snapshot so subsequent navigation doesn't prompt again unless modified
                    session.takeSnapshot();
                })
                .show();
    }

    private static void executeRepack(MainActivity context, Session session) {
        Extensions.showMessage(context, "Repacking Boot Image...");
        new Thread(() -> {
            try {
                File tempOut = new File(context.getCacheDir(), "repack_" + System.currentTimeMillis() + ".img");
                BootImageEngine.repack(session.inspectDir, tempOut, null);
                if (tempOut.exists() && tempOut.length() > 0) {
                    // Replace original file
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(tempOut);
                         java.io.FileOutputStream fos = new java.io.FileOutputStream(session.originalImg)) {
                        byte[] buf = new byte[65536];
                        int r;
                        while ((r = fis.read(buf)) > 0) fos.write(buf, 0, r);
                    }
                    tempOut.delete();
                    session.takeSnapshot();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Extensions.showMessage(context, "Boot image repacked and updated successfully!");
                    });
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    private static String hashPath(String path) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(path.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 8);
        } catch (Exception e) {
            return String.valueOf(Math.abs(path.hashCode()));
        }
    }
}

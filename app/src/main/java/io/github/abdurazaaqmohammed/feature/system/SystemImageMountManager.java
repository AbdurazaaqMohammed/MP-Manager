package io.github.abdurazaaqmohammed.feature.system;

import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;

import android.os.Handler;
import android.os.Looper;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.files.FileOpener;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.abdurazaaqmohammed.privilege.api.ShellSession;
import io.github.abdurazaaqmohammed.util.text.ShellQuote;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Manages mounting, browsing, and unmounting disk images (EROFS, EXT4, F2FS, ISO, NTFS, FAT)
 * and physical/mapper block devices directly inside the file manager panes via loop mount.
 */
public final class SystemImageMountManager {

    private SystemImageMountManager() {}

    private static final String MOUNT_BASE_DIR = "/data/local/tmp/mp_img_mount";

    public static class MountSession {
        public final File sourceFile;
        public final File mountPoint;
        public final String fsType;
        public final boolean isBlockDevice;

        public MountSession(File sourceFile, File mountPoint, String fsType, boolean isBlockDevice) {
            this.sourceFile = sourceFile;
            this.mountPoint = mountPoint;
            this.fsType = fsType;
            this.isBlockDevice = isBlockDevice;
        }
    }

    private static final Map<String, MountSession> activeSessions = new ConcurrentHashMap<>();

    public static boolean isMountedFolder(File folder) {
        if (folder == null) return false;
        String p = folder.getAbsolutePath();
        for (String mnt : activeSessions.keySet()) {
            if (p.equals(mnt) || p.startsWith(mnt + "/")) return true;
        }
        return false;
    }

    public static MountSession getSessionForFolder(File folder) {
        if (folder == null) return null;
        String p = folder.getAbsolutePath();
        for (Map.Entry<String, MountSession> entry : activeSessions.entrySet()) {
            if (p.equals(entry.getKey()) || p.startsWith(entry.getKey() + "/")) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static File getParentOrImageOrigin(File folder) {
        MountSession session = getSessionForFolder(folder);
        if (session == null) return folder != null ? folder.getParentFile() : null;
        if (folder.equals(session.mountPoint)) {
            return session.sourceFile.getParentFile();
        }
        return folder.getParentFile();
    }

    /**
     * Checks if a file or block partition is a mountable filesystem image.
     */
    public static boolean isMountableImage(File file) {
        if (file == null) return false;
        SystemImageEngine.ImageIdentity id = SystemImageEngine.identifyImage(file);
        return id != null && id.isFilesystem;
    }

    /**
     * Mounts and navigates into a system image or partition in the given pane.
     */
    public static void openFilesystemImage(MainActivity context, File imageFile, boolean pane1) {
        if (imageFile == null) return;
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        SystemImageEngine.ImageIdentity id = SystemImageEngine.identifyImage(imageFile);
        if (id == null || !id.isFilesystem) {
            Extensions.showMessage(context, isAr ? "الملف ليس نظام ملفات صالح للتصفح" : "File is not a valid filesystem image");
            return;
        }

        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(isAr ? ("جاري فتح وتثبيت صورة " + imageFile.getName() + "...") : ("Mounting " + imageFile.getName() + "..."));
        pm.show();

        new Thread(() -> {
            try {
                String hash = hashPath(imageFile.getAbsolutePath());
                File mountPoint = new File(MOUNT_BASE_DIR, hash + "_" + sanitizeName(imageFile.getName()));

                boolean isBlockDev = imageFile.getAbsolutePath().startsWith("/dev/block/");
                String fsType = id.typeKey;
                String mountTypeOpt = "";
                if ("erofs".equalsIgnoreCase(fsType)) {
                    mountTypeOpt = "-t erofs ";
                } else if ("ext4".equalsIgnoreCase(fsType) || "ext3".equalsIgnoreCase(fsType) || "ext2".equalsIgnoreCase(fsType)) {
                    mountTypeOpt = "-t ext4 ";
                } else if ("f2fs".equalsIgnoreCase(fsType)) {
                    mountTypeOpt = "-t f2fs ";
                } else if ("iso".equalsIgnoreCase(fsType)) {
                    mountTypeOpt = "-t iso9660 ";
                } else if ("ntfs".equalsIgnoreCase(fsType)) {
                    mountTypeOpt = "-t ntfs ";
                } else if ("fat".equalsIgnoreCase(fsType)) {
                    mountTypeOpt = "-t vfat ";
                }

                String mntCmd = buildMountCommand(imageFile.getAbsolutePath(), mountPoint.getAbsolutePath(), mountTypeOpt, isBlockDev);

                // Execute mount via executeFs (which uses global ns if preferred) and try executeGlobalNs explicitly
                ShellSession.ShellResult res = PrivilegeManager.Root.executeFs(context, mntCmd, 15);
                if (!res.isSuccess()) {
                    res = PrivilegeManager.Root.executeGlobalNs(context, mntCmd, 15);
                }
                if (!res.isSuccess()) {
                    // Try generic loop mount without explicit -t if specific fs failed
                    String fallbackCmd = buildFallbackMountCommand(imageFile.getAbsolutePath(), mountPoint.getAbsolutePath());
                    res = PrivilegeManager.Root.executeFs(context, fallbackCmd, 15);
                    if (!res.isSuccess()) {
                        res = PrivilegeManager.Root.executeGlobalNs(context, fallbackCmd, 15);
                    }
                }

                if (!res.isSuccess()) {
                    throw new Exception(res.error() != null && !res.error().isEmpty() ? res.error() : "Mount returned non-zero exit code");
                }

                MountSession session = new MountSession(imageFile, mountPoint, fsType, isBlockDev);
                activeSessions.put(mountPoint.getAbsolutePath(), session);

                new Handler(Looper.getMainLooper()).post(() -> {
                    pm.dismiss();
                    context.loadFolderInPaneAsync(mountPoint, pane1, true);
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                });
            }
        }).start();
    }

    /**
     * Shows options dialog when an image file or partition is clicked.
     */
    public static void showImageOptionsDialog(MainActivity context, FileOpener opener, File file, String fileName, boolean pane1) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        SystemImageEngine.ImageIdentity id = SystemImageEngine.identifyImage(file);
        String desc = id != null ? id.getDisplayName(isAr) : fileName;
        String details = (id != null && id.details != null && !id.details.isEmpty()) ? (" (" + id.details + ")") : "";

        String msg = (isAr
                ? "نوع الصورة: " + desc + details + "\n\nهل ترغب في استعراض وتصفح محتويات النظام، أو فتحها بتطبيق خارجي؟"
                : "Image Type: " + desc + details + "\n\nWould you like to browse and explore the filesystem contents or open with an external app?");

        new MaterialAlertDialogBuilder(context)
                .setTitle(isAr ? "استعراض صورة النظام" : "System Image Explorer")
                .setMessage(msg)
                .setPositiveButton(isAr ? "📂 استعراض وتصفح" : "📂 Browse Files", (dialog, which) -> {
                    openFilesystemImage(context, file, pane1);
                })
                .setNeutralButton(R.string.open_with, (dialog, which) -> {
                    if (opener != null) opener.openWithDefaultOrDialog(file, fileName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Checks if user is exiting a mounted image folder and asks if they want to unmount it.
     */
    public static void checkUnmountOnExit(MainActivity context, File folder) {
        MountSession session = getSessionForFolder(folder);
        if (session == null) return;
        // If navigating strictly outside the mount point, unmount in background
        new Thread(() -> {
            try {
                String ucmd = buildUnmountCommand(session.mountPoint.getAbsolutePath());
                PrivilegeManager.Root.executeFs(context, ucmd, 10);
                PrivilegeManager.Root.executeGlobalNs(context, ucmd, 10);
                activeSessions.remove(session.mountPoint.getAbsolutePath());
            } catch (Exception ignored) {
            }
        }).start();
    }

    /**
     * Builds the mkdir/chmod/mount command for an image. Pure string builder
     * (no execution) so tests can verify quoting of hostile filenames.
     */
    static String buildMountCommand(String sourcePath, String mountPointPath, String mountTypeOpt, boolean isBlockDev) {
        return "mkdir -p " + ShellQuote.quoteForSh(mountPointPath)
                + " && chmod 755 " + ShellQuote.quoteForSh(MOUNT_BASE_DIR) + " && "
                + "mount -o ro" + (isBlockDev ? " " : ",loop ") + mountTypeOpt
                + ShellQuote.quoteForSh(sourcePath) + " " + ShellQuote.quoteForSh(mountPointPath);
    }

    /** Builds the generic loop-mount fallback command (no explicit -t). */
    static String buildFallbackMountCommand(String sourcePath, String mountPointPath) {
        return "mount -o ro,loop " + ShellQuote.quoteForSh(sourcePath) + " " + ShellQuote.quoteForSh(mountPointPath);
    }

    /** Builds the lazy-unmount plus rmdir cleanup command. */
    static String buildUnmountCommand(String mountPointPath) {
        return "umount -l " + ShellQuote.quoteForSh(mountPointPath)
                + " && rmdir " + ShellQuote.quoteForSh(mountPointPath) + " 2>/dev/null";
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

    private static String sanitizeName(String name) {
        if (name == null) return "img";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}

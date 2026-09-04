package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Staging layer that makes root-only files usable with regular
 * {@code java.io.File} APIs.
 *
 * <p>Why staging? Even on a rooted phone the app process keeps its own uid,
 * so {@code new File("/data/data/...").canRead()} stays false and
 * {@code FileInputStream} throws EACCES. The only thing root gives us is a
 * separate uid-0 process ({@code su}). This class copies bytes through that
 * process into the app's private cache, where normal File APIs work:
 *
 * <pre>
 *   root-only path --su cat--> cache/root_staging/... --> FileInputStream,
 *   BitmapFactory, zip4j, FileProvider, editors, ...
 * </pre>
 *
 * <p>Safety:
 * <ul>
 *   <li>Reads never modify the device (pure {@code cat}).</li>
 *   <li>Writes happen only via explicit {@link #writeBack} calls (Save
 *   buttons), go through {@link RootManager#streamToRoot} which blocks
 *   critical paths ({@code /, /system, /data, ...}).</li>
 *   <li>Staged files live in the app-private cache, mode-private, with a
 *   size cap ({@link RootManager#MAX_STAGE_BYTES}).</li>
 *   <li>Stale staged copies are best-effort cleaned by {@link #cleanup}.</li>
 * </ul>
 */
public final class RootStaging {

    private static final String STAGE_DIR = "root_staging";

    private RootStaging() {
    }

    /** True when {@code file} needs root staging to be read. */
    public static boolean needsStaging(Context context, File file) {
        if (context == null || file == null) return false;
        return needsStaging(context, file.getAbsolutePath());
    }

    /** True when {@code absPath} needs root staging to be read. */
    public static boolean needsStaging(Context context, String absPath) {
        if (context == null || absPath == null || absPath.isEmpty()) return false;
        try {
            File f = new File(absPath);
            if (f.exists() && f.canRead()) return false;
        } catch (Exception ignored) {
        }
        try {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootFileOpsEnabled() || !rm.isRootAvailable()) return false;
            return rm.exists(absPath);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Copy a root-only file into the app cache and return the readable copy.
     * Binary-safe (APKs, images, dex all survive). Throws with a clear
     * message when root is off, the file is too big, or the read fails.
     */
    public static File stageForRead(Context context, String srcAbsPath) throws IOException {
        if (context == null) throw new IOException("No context");
        if (srcAbsPath == null || !srcAbsPath.startsWith("/")) {
            throw new IOException("Refusing to stage non-absolute path");
        }
        RootManager rm = RootManager.getInstance(context);
        if (!rm.isRootFileOpsEnabled() || !rm.isRootAvailable()) {
            throw new IOException("Root file access is disabled or unavailable");
        }
        File dir = stageDir(context);
        String base = new File(srcAbsPath).getName();
        if (base.isEmpty()) base = "root_file";
        // Sanitize: keep cache file name harmless even for weird source names.
        base = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
        File dst = new File(dir, System.currentTimeMillis() + "_" + base);
        int dup = 0;
        while (dst.exists() && dup < 100) {
            dst = new File(dir, System.currentTimeMillis() + "_" + (dup++) + "_" + base);
        }
        try (OutputStream os = new FileOutputStream(dst)) {
            rm.streamFromRoot(srcAbsPath, os, RootManager.MAX_STAGE_BYTES);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            throw e;
        }
        if (!dst.isFile() || !dst.canRead()) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            throw new IOException("Staging failed for " + srcAbsPath);
        }
        return dst;
    }

    /**
     * Open a readable {@link InputStream} for any path: direct
     * {@code FileInputStream} when the app can read it, otherwise a stream
     * over a staged root copy. Caller closes the stream; the staged copy is
     * deleted when the stream closes.
     */
    public static InputStream openInputStream(Context context, File file) throws IOException {
        if (file == null) throw new IOException("Null file");
        try {
            if (file.exists() && file.canRead()) return FileUtils.getInputStream(file);
        } catch (IOException ignored) {
        }
        File staged = stageForRead(context, file.getAbsolutePath());
        return new StagedInputStream(staged);
    }

    /**
     * Write a (possibly edited) staged copy back to its root-original path.
     * Only call from explicit user Save actions; callers should confirm first
     * when {@link RootManager#isPathInKeyDirectory(String)} is true.
     */
    public static void writeBack(Context context, File stagedCopy, String originalAbsPath) throws IOException {
        if (context == null) throw new IOException("No context");
        if (stagedCopy == null || !stagedCopy.isFile()) throw new IOException("Nothing to save");
        if (originalAbsPath == null || !originalAbsPath.startsWith("/")) {
            throw new IOException("Refusing to write non-absolute path");
        }
        RootManager rm = RootManager.getInstance(context);
        if (!rm.isRootFileOpsEnabled() || !rm.isRootAvailable()) {
            throw new IOException("Root file access is disabled or unavailable");
        }
        rm.streamToRoot(stagedCopy, originalAbsPath);
    }

    /** True when writing back to {@code absPath} deserves an extra user confirmation. */
    public static boolean needsWriteConfirm(String absPath) {
        return RootManager.isPathInKeyDirectory(absPath);
    }

    /** App-private staging directory (created on demand). */
    public static File stageDir(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), STAGE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create staging dir");
        }
        return dir;
    }

    /**
     * Best-effort cleanup of staged copies older than {@code maxAgeMs}.
     * Safe: only deletes inside our own cache staging dir.
     */
    public static void cleanup(Context context, long maxAgeMs) {
        if (context == null) return;
        try {
            File dir = new File(context.getCacheDir(), STAGE_DIR);
            File[] kids = dir.listFiles();
            if (kids == null) return;
            long now = System.currentTimeMillis();
            for (File k : kids) {
                try {
                    if (now - k.lastModified() > maxAgeMs) {
                        //noinspection ResultOfMethodCallIgnored
                        k.delete();
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** FileInputStream that deletes its staged backing file on close. */
    private static class StagedInputStream extends java.io.FileInputStream {
        private final File staged;

        StagedInputStream(File staged) throws IOException {
            super(staged);
            this.staged = staged;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    staged.delete();
                } catch (Exception ignored) {
                }
            }
        }
    }
}

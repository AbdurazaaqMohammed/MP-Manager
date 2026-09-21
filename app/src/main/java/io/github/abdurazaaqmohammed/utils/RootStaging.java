package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

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

    public static boolean needsStaging(Context context, File file) {
        if (context == null || file == null) return false;
        return AccessManager.needsElevated(context, file.getAbsolutePath());
    }

    public static boolean needsStaging(Context context, String absPath) {
        return AccessManager.needsElevated(context, absPath);
    }

    public static File stageForRead(Context context, String srcAbsPath) throws IOException {
        return AccessManager.stageForRead(context, srcAbsPath);
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

    public static void writeBack(Context context, File stagedCopy, String originalAbsPath) throws IOException {
        AccessManager.writeBack(context, stagedCopy, originalAbsPath);
    }

    public static boolean needsWriteConfirm(String absPath) {
        return RootManager.isPathInKeyDirectory(absPath);
    }

    public static File stageDir(Context context) throws IOException {
        File dir = new File(context.getCacheDir(), STAGE_DIR);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create staging dir");
        }
        return dir;
    }

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
    private static class StagedInputStream extends FileInputStream {
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

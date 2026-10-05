package io.github.abdurazaaqmohammed.MPManager.shizuku;

import android.content.Context;

import java.io.File;
import java.io.IOException;

import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * Cross-boundary helpers for emulated-volume paths the app cannot reach directly: copying/moving
 * between them and normal folders, and materializing Shizuku-only files into the app cache for
 * open/share flows. Kept out of ShizukuFile so the File subclass stays minimal.
 */
public final class ShizukuFileOps {

    private static Context appContext;

    /** Initialized from MainActivity.onCreate so hooks can pass null. */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    private static Context ctx() {
        return appContext;
    }

    /** Returns the original file unless it can only be read via Shizuku; then returns a cache copy. */
    public static File materialize(Context context, File file) {
        Context c = context != null ? context : appContext;
        if (c == null || !(file instanceof ShizukuFile) || file.isDirectory()) return file;
        try {
            return ((ShizukuFile) file).materializeTo(c);
        } catch (IOException e) {
            return file;
        }
    }

/**
     * True when either side of the operation genuinely needs shell access. Files the app can read
     * on its own keep the fast native path, so ordinary files are never pushed through
     * {@code cp -r}/{@code mv} or needlessly wrapped as {@link ShizukuFile}.
     */
    public static boolean involvesShizukuPath(File src, File dest) {
        return needsShell(src) || needsShell(dest);
    }

    private static boolean needsShell(File f) {
        if (f == null) return false;
        if (f instanceof ShizukuFile) return true;
        return ShizukuFile.isShellPath(f) && !f.canRead();
    }

    /**
     * Copy src into destFolder via shell (cp -r). Returns the created file, or null on failure.
     * Works in both directions (into and out of the shell-only subtrees).
     */
    public static File shellCopy(File src, File destFolder, String name) {
        if (!ShizukuShell.isGranted()) return null;
        File dest = new File(destFolder, name);
        if (dest.equals(src)) return dest;
        String cmd = "cp -r " + RootManager.escapeShellArg(src.getAbsolutePath()) + " " + RootManager.escapeShellArg(dest.getAbsolutePath());
        return ShizukuShell.exec(cmd).success ? new ShizukuFile(dest.getAbsolutePath(), src.isDirectory(), src.length()) : null;
    }

    /** Move src into destFolder via shell (mv). Returns true on success. */
    public static boolean shellMove(File src, File destFolder, String name) {
        if (!ShizukuShell.isGranted()) return false;
        File dest = new File(destFolder, name);
        if (dest.equals(src)) return true;
        if (RootManager.isPathBlocked(src.getAbsolutePath())) return false;
        String cmd = "mv " + RootManager.escapeShellArg(src.getAbsolutePath()) + " " + RootManager.escapeShellArg(dest.getAbsolutePath());
        return ShizukuShell.exec(cmd).success;
    }
}

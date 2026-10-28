package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
public final class AccessManager {

    public enum Backend { NONE, ROOT, SHIZUKU }

    private AccessManager() {
    }

    public static Backend active(Context context) {
        if (context == null) return Backend.NONE;
        RootManager.WorkingMode mode = RootManager.getInstance(context).getWorkingMode();
        if (mode == RootManager.WorkingMode.ROOT) return Backend.ROOT;
        if (mode == RootManager.WorkingMode.SHIZUKU
                && Build.VERSION.SDK_INT >= 23) return Backend.SHIZUKU;
        return Backend.NONE;
    }

    public static boolean fileOpsOn(Context context) {
        if (context == null) return false;
        RootManager rm = RootManager.getInstance(context);
        if (rm.getWorkingMode() == RootManager.WorkingMode.ROOT) {
            return rm.isRootFileOpsEnabled() && rm.isRootAvailable();
        }
        if (rm.getWorkingMode() == RootManager.WorkingMode.SHIZUKU) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getBoolean("shizuku_file_ops", false) && ShizukuManager.ready();
        }
        return false;
    }

    public static boolean extractorOn(Context context) {
        if (context == null) return false;
        RootManager rm = RootManager.getInstance(context);
        if (rm.getWorkingMode() == RootManager.WorkingMode.ROOT) return rm.isRootExtractorEnabled();
        return false;
    }

    public static boolean needsElevated(Context context, String absPath) {
        if (context == null || absPath == null || absPath.isEmpty()) return false;
        try {
            File f = new File(absPath);
            if (f.exists() && f.canRead()) return false;
        } catch (Exception ignored) {
        }
        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootFileOpsEnabled() || !rm.isRootAvailable()) return false;
            try {
                return rm.exists(absPath);
            } catch (Exception e) {
                return false;
            }
        }
        if (backend == Backend.SHIZUKU) {
            try {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                if (!prefs.getBoolean("shizuku_file_ops", false)) return false;
                return ShizukuManager.exists(context, absPath);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public static boolean exists(Context context, String absPath) {
        if (absPath == null) return false;
        try {
            if (new File(absPath).exists()) return true;
        } catch (Exception ignored) {
        }
        Backend backend = active(context);
        try {
            if (backend == Backend.ROOT) return RootManager.getInstance(context).exists(absPath);
            if (backend == Backend.SHIZUKU) return ShizukuManager.exists(context, absPath);
        } catch (Exception ignored) {
        }
        return false;
    }

    public static File[] listWithStat(Context context, String dirPath) {
        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            return RootManager.getInstance(context).listRootFilesWithStat(dirPath);
        }
        if (backend == Backend.SHIZUKU) {
            return ShizukuManager.listWithStat(context, dirPath);
        }
        if (context != null && RootManager.getInstance(context).isRootAvailable()) {
            return RootManager.getInstance(context).listRootFilesWithStat(dirPath);
        }
        return null;
    }

    public static long getSize(Context context, String absPath) {
        Backend backend = active(context);
        if (backend == Backend.ROOT) return RootManager.getInstance(context).getFileSize(absPath);
        if (backend == Backend.SHIZUKU) return ShizukuManager.getFileSize(context, absPath);
        try {
            return new File(absPath).length();
        } catch (Exception e) {
            return -1;
        }
    }

    public static long getMtime(Context context, String absPath) {
        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            RootManager.RootEntry e = RootManager.getInstance(context).statEntry(absPath);
            return e != null ? e.lastModified() : 0;
        }
        if (backend == Backend.SHIZUKU) return ShizukuManager.getMtime(context, absPath);
        try {
            return new File(absPath).lastModified();
        } catch (Exception e) {
            return 0;
        }
    }

    public static File stageForRead(Context context, String srcAbsPath) throws IOException {
        if (context == null) throw new IOException("No context");
        if (srcAbsPath == null || !srcAbsPath.startsWith("/")) {
            throw new IOException("Refusing to stage non-absolute path");
        }
        Backend backend = active(context);
        File dst = newStagingTarget(context, srcAbsPath);
        IOException firstFailure;
        try {
            stageForRead(context, backend, srcAbsPath, dst);
            return dst;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            firstFailure = e;
            if (backend == Backend.ROOT && canUseShizuku(context)) {
                backend = Backend.SHIZUKU;
            } else if (backend == Backend.SHIZUKU && canUseRoot(context)) {
                backend = Backend.ROOT;
            } else {
                throw firstFailure;
            }
        }
        File retried = newStagingTarget(context, srcAbsPath);
        try {
            // The two backends have genuinely different permissions: the shell uid
            // often cannot read what root can (e.g. /vendor/build.prop is 0640
            // root:root and readable by neither the app nor the shell), so a
            // failure on one is worth retrying on the other before giving up.
            stageForRead(context, backend, srcAbsPath, retried);
            return retried;
        } catch (IOException retryFailure) {
            //noinspection ResultOfMethodCallIgnored
            retried.delete();
            retryFailure.addSuppressed(firstFailure);
            throw retryFailure;
        }
    }

    private static File newStagingTarget(Context context, String srcAbsPath) throws IOException {
        File dir = RootStaging.stageDir(context);
        String base = new File(srcAbsPath).getName();
        if (base.isEmpty()) base = "staged_file";
        base = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
        File dst = new File(dir, System.currentTimeMillis() + "_" + base);
        int dup = 0;
        while (dst.exists() && dup < 100) {
            dst = new File(dir, System.currentTimeMillis() + "_" + (dup++) + "_" + base);
        }
        return dst;
    }

    private static boolean canUseRoot(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            return rm.isRootFileOpsEnabled() && rm.isRootAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canUseShizuku(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getBoolean("shizuku_file_ops", false) && ShizukuManager.ready();
        } catch (Exception e) {
            return false;
        }
    }

    private static void stageForRead(Context context, Backend backend, String srcAbsPath, File dst) throws IOException {
        if (backend == Backend.ROOT) {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootFileOpsEnabled() || !rm.isRootAvailable()) {
                throw new IOException("Root file access is disabled or unavailable");
            }
            try (OutputStream os = new FileOutputStream(dst)) {
                rm.streamFromRoot(srcAbsPath, os, RootManager.MAX_STAGE_BYTES);
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                dst.delete();
                throw e;
            }
        } else if (backend == Backend.SHIZUKU) {
            if (!canUseShizuku(context)) {
                throw new IOException("Shizuku file access is disabled or unavailable");
            }
            try (OutputStream os = new FileOutputStream(dst)) {
                if (ShizukuManager.isAllowed(srcAbsPath)) {
                    ShizukuManager.streamFrom(context, srcAbsPath, os, RootManager.MAX_STAGE_BYTES);
                } else {
                    ShizukuManager.streamFromShell(srcAbsPath, os, RootManager.MAX_STAGE_BYTES);
                }
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                dst.delete();
                throw e;
            }
        } else {
            throw new IOException("No elevated access enabled");
        }
        if (!dst.isFile() || !dst.canRead()) {
            //noinspection ResultOfMethodCallIgnored
            dst.delete();
            throw new IOException("Staging failed");
        }
    }

    public static void writeBack(Context context, File stagedCopy, String originalAbsPath) throws IOException {
        if (context == null) throw new IOException("No context");
        if (stagedCopy == null || !stagedCopy.isFile()) throw new IOException("Nothing to save");
        if (originalAbsPath == null || !originalAbsPath.startsWith("/")) {
            throw new IOException("Refusing to write non-absolute path");
        }
        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootFileOpsEnabled() || !rm.isRootAvailable()) {
                throw new IOException("Root file access is disabled or unavailable");
            }
            rm.streamToRoot(stagedCopy, originalAbsPath);
        } else if (backend == Backend.SHIZUKU) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean("shizuku_file_ops", false) || !ShizukuManager.ready()) {
                throw new IOException("Shizuku file access is disabled or unavailable");
            }
            if (ShizukuManager.isAllowed(originalAbsPath)) {
                ShizukuManager.streamTo(context, stagedCopy, originalAbsPath);
            } else {
                ShizukuManager.streamToShell(stagedCopy, originalAbsPath);
            }
        } else {
            throw new IOException("No elevated access enabled");
        }
    }

    public static void copyFile(Context context, String src, String dest, boolean useElevated) throws IOException {
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.getInstance(context).copyFile(src, dest);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.copyFile(context, src, dest);
                return;
            }
        }
        FileUtils.copyFile(new File(src), new File(dest));
    }

    public static void copyDir(Context context, String src, String dest, boolean useElevated) throws IOException {
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.getInstance(context).copyDir(src, dest);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.copyDir(context, src, dest);
                return;
            }
        }
        FileUtils.copyFolder(new File(src), new File(dest));
    }

    /**
     * Copy that keeps mode, ownership and timestamps. Equivalent to a copy
     * followed by {@link #preserveTime}, but for the root backend it is a single
     * {@code cp -pf} instead of a copy plus a stat plus a {@code touch}, which
     * is what made moving small and empty files feel slow in root mode.
     */
    public static void copyPreserving(Context context, String src, String dest, boolean directory,
                                      boolean useElevated) throws IOException {
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager rm = RootManager.getInstance(context);
                if (directory) rm.copyDirPreserving(src, dest);
                else rm.copyFilePreserving(src, dest);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                if (directory) ShizukuManager.copyDir(context, src, dest);
                else ShizukuManager.copyFile(context, src, dest);
                preserveTime(context, src, dest);
                return;
            }
        }
        if (directory) FileUtils.copyFolder(new File(src), new File(dest));
        else FileUtils.copyFile(new File(src), new File(dest));
        preserveTime(context, src, dest);
    }

    /**
     * Copy that keeps mode, ownership and timestamps for a whole batch. The
     * backend gets one shell process (root) or one command (shizuku shell
     * paths) for the entire selection instead of one per item.
     */
    public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops) throws IOException {
        copyAllPreserving(context, ops, null);
    }

    /**
     * Copy that keeps mode, ownership and timestamps for a whole batch. The
     * backend gets one shell process (root) or one command (shizuku shell
     * paths) for the entire selection instead of one per item.
     *
     * @param progress receives per-item counters streamed out of that process
     */
    public static void copyAllPreserving(Context context, List<RootManager.CopyOp> ops,
                                         ShellProgress.Listener progress) throws IOException {
        if (ops == null || ops.isEmpty()) return;
        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            RootManager.getInstance(context).copyAllPreserving(ops, progress);
            return;
        }
        if (backend == Backend.SHIZUKU) {
            ShizukuManager.copyAllPreserving(context, ops, progress);
            return;
        }
        int files = 0;
        long bytes = 0L;
        for (RootManager.CopyOp op : ops) {
            if (op.directory) FileUtils.copyFolder(new File(op.src), new File(op.dest));
            else FileUtils.copyFile(new File(op.src), new File(op.dest));
            preserveTime(context, op.src, op.dest);
            files += Math.max(0, op.files);
            bytes += Math.max(0L, op.bytes);
            if (progress != null) progress.onProgress(files, bytes);
        }
    }

    /**
     * Counts files and bytes through the active elevated backend, because the
     * app process cannot list paths like {@code /data/data/...}.
     *
     * @return null when no elevated backend is active
     */
    public static FileUtils.MeasureSet measureSetElevated(Context context, List<String> paths) {
        if (paths == null || paths.isEmpty()) return null;
        Backend backend = active(context);
        try {
            if (backend == Backend.ROOT) return RootManager.getInstance(context).measureSet(paths);
            if (backend == Backend.SHIZUKU) return ShizukuManager.measureSet(context, paths);
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Deletes every path in one round trip when the backend allows it.
     *
     * @return the paths that were handled by the backend.
     */
    public static List<String> deleteAll(Context context, List<String> paths, boolean useElevated) throws IOException {
        return deleteAll(context, paths, useElevated, null);
    }

    public static List<String> deleteAll(Context context, List<String> paths, boolean useElevated,
                                         ShellProgress.Listener progress) throws IOException {
        if (paths == null || paths.isEmpty()) return new ArrayList<>();
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) return RootManager.getInstance(context).deleteAll(paths, progress);
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.deleteAll(context, paths, progress);
                return paths;
            }
        }
        List<String> done = new ArrayList<>();
        for (String path : paths) {
            delete(context, path, false);
            done.add(path);
            if (progress != null) progress.onProgress(done.size(), 0L);
        }
        return done;
    }

    /**
     * Recursive delete that reports one step per directory removed, so the
     * dialog keeps moving instead of jumping from 0% when {@code rm -rf}
     * returns.
     */
    public static void deleteTree(Context context, List<String> paths, boolean useElevated,
                                  ShellProgress.Listener progress) throws IOException {
        if (paths == null || paths.isEmpty()) return;
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.getInstance(context).deleteTree(paths, progress);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.deleteTree(context, paths, progress);
                return;
            }
        }
        // Plain delete still reports per entry, so the dialog keeps moving.
        final int[] done = {0};
        FileUtils.TransferListener perEntry = progress == null ? null : new FileUtils.TransferListener() {
            @Override
            public void onFile(File source, File destination) {
                if (source == null) return;
                progress.onProgress(++done[0], 0L);
            }

            @Override
            public void onBytes(long count) {
            }
        };
        for (String path : paths) {
            FileUtils.deleteTree(new File(path), perEntry);
        }
    }

    /** Directories under {@code paths}; the unit {@link #deleteTree} counts in. */
    public static int countDirsElevated(Context context, List<String> paths) {
        if (paths == null || paths.isEmpty()) return 0;
        Backend backend = active(context);
        try {
            if (backend == Backend.ROOT) return RootManager.getInstance(context).countDirs(paths);
            if (backend == Backend.SHIZUKU) return ShizukuManager.countDirs(context, paths);
        } catch (Exception ignored) {
        }
        return 0;
    }

    public static void mkdir(Context context, String path, boolean useElevated) throws IOException {
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.getInstance(context).mkdir(path);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.mkdir(context, path);
                return;
            }
        }
        File dir = new File(path);
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) throw new IOException("mkdir failed");
    }

    public static void delete(Context context, String path, boolean useElevated) throws IOException {
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.getInstance(context).delete(path);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.delete(context, path);
                return;
            }
        }
        File f = new File(path);
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) delete(context, k.getAbsolutePath(), false);
        }
        if (!f.delete() && f.exists()) throw new IOException("delete failed");
    }

    public static void rename(Context context, String oldPath, String newPath, boolean useElevated) throws IOException {
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.getInstance(context).rename(oldPath, newPath);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.rename(context, oldPath, newPath);
                return;
            }
        }
        if (!new File(oldPath).renameTo(new File(newPath))) throw new IOException("rename failed");
    }

    public static void touch(Context context, String path, boolean useElevated) throws IOException {
        if (useElevated) {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.getInstance(context).touch(path);
                return;
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.touch(context, path);
                return;
            }
        }
        File f = new File(path);
        if (!f.exists() && !f.createNewFile()) throw new IOException("touch failed");
    }

    public static RootManager.ShellResult execute(Context context, String command, int timeout) {
        Backend backend = active(context);
        if (backend == Backend.ROOT) return RootManager.getInstance(context).execute(command, timeout);
        return new RootManager.ShellResult(-1, "", "No shell backend enabled");
    }

    public static boolean touchMtime(Context context, String path, long millis) {
        try {
            if (new File(path).setLastModified(millis)) return true;
        } catch (Exception ignored) {
        }
        try {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                long secs = millis / 1000L;
                return RootManager.getInstance(context)
                        .execute("touch -d @" + secs + " " + RootManager.escapeShellArg(path), 10)
                        .isSuccess();
            }
            if (backend == Backend.SHIZUKU) {
                if (ShizukuManager.isAllowed(path)) {
                    ShizukuManager.touchMtime(context, path, millis);
                    return true;
                }
                return ShizukuManager.touchMtimeShell(path, millis);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static long dirSize(Context context, String path) {
        try {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.ShellResult r = RootManager.getInstance(context).execute(
                        "du -sk " + RootManager.escapeShellArg(path) + " 2>/dev/null", 15);
                if (r.isSuccess() && r.output() != null) {
                    return Long.parseLong(r.output().trim().split("\\s+")[0]) * 1024L;
                }
                return -1;
            }
            if (backend == Backend.SHIZUKU) return ShizukuManager.dirSize(context, path);
        } catch (Exception ignored) {
        }
        return -1;
    }

    public static void warmUp(Context context) {
        try {
            if (active(context) == Backend.SHIZUKU) ShizukuManager.warmUp(context);
        } catch (Exception ignored) {
        }
    }

    public static void uploadFile(Context context, File localSrc, String dstAbs) throws IOException {
        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            copyFile(context, localSrc.getAbsolutePath(), dstAbs, true);
            return;
        }
        if (backend == Backend.SHIZUKU) {
            ShizukuManager.streamTo(context, localSrc, dstAbs);
            return;
        }
        throw new IOException("No elevated access enabled");
    }

    public static void stageFile(Context context, String srcAbs, File dst) throws IOException {
        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            copyFile(context, srcAbs, dst.getAbsolutePath(), true);
            execute(context, "chmod a+r " + RootManager.escapeShellArg(dst.getAbsolutePath()), 30);
            preserveTime(context, srcAbs, dst.getAbsolutePath());
            return;
        }
        if (backend == Backend.SHIZUKU) {
            try (OutputStream os = new FileOutputStream(dst)) {
                ShizukuManager.streamFrom(context, srcAbs, os, RootManager.MAX_STAGE_BYTES);
            }
            long t = ShizukuManager.getMtime(context, srcAbs);
            if (t > 0) {
                //noinspection ResultOfMethodCallIgnored
                dst.setLastModified(t);
            }
            return;
        }
        throw new IOException("No elevated access enabled");
    }

    public static void stageTree(Context context, String srcAbs, File dst) throws IOException {        Backend backend = active(context);
        if (backend == Backend.ROOT) {
            copyDir(context, srcAbs, dst.getAbsolutePath(), true);
            execute(context, "chmod -R a+rX " + RootManager.escapeShellArg(dst.getAbsolutePath()), 30);
            return;
        }
        if (backend == Backend.SHIZUKU) {
            File[] kids = ShizukuManager.listWithStat(context, srcAbs);
            if (kids == null) throw new IOException("Cannot list " + srcAbs);
            if (!dst.isDirectory() && !dst.mkdirs() && !dst.isDirectory()) {
                throw new IOException("Cannot create staging dir");
            }
            for (File k : kids) {
                File d = new File(dst, k.getName());
                if (k.isDirectory()) {
                    stageTree(context, srcAbs + "/" + k.getName(), d);
                } else {
                    try (OutputStream os = new FileOutputStream(d)) {
                        ShizukuManager.streamFrom(context, srcAbs + "/" + k.getName(), os,
                                RootManager.MAX_STAGE_BYTES);
                    }
                    long t = k.lastModified();
                    if (t > 0) {
                        //noinspection ResultOfMethodCallIgnored
                        d.setLastModified(t);
                    }
                }
            }
            return;
        }
        throw new IOException("No elevated access enabled");
    }

    public static void preserveTime(Context context, String srcAbs, String dstAbs) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean("preserve_mtime", true)) return;
            long t = 0;
            try {
                t = new File(srcAbs).lastModified();
            } catch (Exception ignored) {
            }
            if (t <= 0) t = getMtime(context, srcAbs);
            if (t <= 0) return;
            File dst = new File(dstAbs);
            if (dst.exists() && dst.canWrite()) {
                //noinspection ResultOfMethodCallIgnored
                dst.setLastModified(t);
                return;
            }
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager rm = RootManager.getInstance(context);
                long secs = t / 1000L;
                rm.execute("touch -d @" + secs + " " + RootManager.escapeShellArg(dstAbs), 10);
            } else if (backend == Backend.SHIZUKU) {
                ShizukuManager.touchMtime(context, dstAbs, t);
            }
        } catch (Exception ignored) {
        }
    }
}

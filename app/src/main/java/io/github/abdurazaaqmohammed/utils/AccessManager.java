package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
public final class AccessManager {

    public enum Backend { NONE, ROOT, SHIZUKU }

    private AccessManager() {
    }

    public static Backend active(Context context) {
        if (context == null) return Backend.NONE;
        RootManager.WorkingMode mode = RootManager.getInstance(context).getWorkingMode();
        if (mode == RootManager.WorkingMode.ROOT) return Backend.ROOT;
        if (mode == RootManager.WorkingMode.SHIZUKU
                && android.os.Build.VERSION.SDK_INT >= 23) return Backend.SHIZUKU;
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
        File dir = RootStaging.stageDir(context);
        String base = new File(srcAbsPath).getName();
        if (base.isEmpty()) base = "staged_file";
        base = base.replaceAll("[^a-zA-Z0-9_.-]", "_");
        File dst = new File(dir, System.currentTimeMillis() + "_" + base);
        int dup = 0;
        while (dst.exists() && dup < 100) {
            dst = new File(dir, System.currentTimeMillis() + "_" + (dup++) + "_" + base);
        }
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
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            if (!prefs.getBoolean("shizuku_file_ops", false) || !ShizukuManager.ready()) {
                throw new IOException("Shizuku file access is disabled or unavailable");
            }
            try (OutputStream os = new FileOutputStream(dst)) {
                ShizukuManager.streamFrom(context, srcAbsPath, os, RootManager.MAX_STAGE_BYTES);
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
        return dst;
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
            ShizukuManager.streamTo(context, stagedCopy, originalAbsPath);
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
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                long secs = millis / 1000L;
                return RootManager.getInstance(context)
                        .execute("touch -d @" + secs + " " + RootManager.escapeShellArg(path), 10)
                        .isSuccess();
            }
            if (backend == Backend.SHIZUKU) {
                ShizukuManager.touchMtime(context, path, millis);
                return true;
            }
            return new File(path).exists() && new File(path).setLastModified(millis);
        } catch (Exception e) {
            return false;
        }
    }

    public static long dirSize(Context context, String path) {
        try {
            Backend backend = active(context);
            if (backend == Backend.ROOT) {
                RootManager.ShellResult r = RootManager.getInstance(context).execute(
                        "du -sb " + RootManager.escapeShellArg(path) + " 2>/dev/null", 15);
                if (r.isSuccess() && r.output() != null) {
                    return Long.parseLong(r.output().trim().split("\\s+")[0]);
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

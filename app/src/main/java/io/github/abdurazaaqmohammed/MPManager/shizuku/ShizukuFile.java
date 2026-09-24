package io.github.abdurazaaqmohammed.MPManager.shizuku;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * A java.io.File wrapper that performs its filesystem operations through Shizuku (shell uid).
 * Only intended for paths under /storage/emulated/0/Android/data which regular apps cannot
 * list on Android 11+. Subclassing File keeps MainActivity/adapters working unchanged via
 * polymorphism, mirroring the FTPFileWrapper approach used for FTP paths.
 */
public class ShizukuFile extends File {

    public static final String ANDROID_DATA = "/storage/emulated/0/Android/data";
    private static final String ANDROID_DATA_SDCARD = "/sdcard/Android/data";

    private final boolean directory;
    private final long size;

    public ShizukuFile(String pathname, boolean directory, long size) {
        super(pathname);
        this.directory = directory;
        this.size = size;
    }

    public ShizukuFile(String pathname) {
        this(pathname, guessIsDirectory(pathname), 0);
    }

    private static boolean guessIsDirectory(String path) {
        // Root Android/data itself is always a directory; children get real values from listing.
        return path.equals(ANDROID_DATA) || !path.substring(path.lastIndexOf('/') + 1).contains(".");
    }

    public static boolean isAndroidDataPath(String path) {
        if (path == null) return false;
        return path.equals(ANDROID_DATA) || path.startsWith(ANDROID_DATA + "/")
                || path.equals(ANDROID_DATA_SDCARD) || path.startsWith(ANDROID_DATA_SDCARD + "/");
    }

    public static boolean isAndroidDataPath(File f) {
        return f != null && isAndroidDataPath(f.getAbsolutePath());
    }

    /**
     * Entry point used by MainActivity when File.listFiles() fails (Android/data on API 30+).
     * Returns null untouched when this path is not Android/data or Shizuku is not usable,
     * so existing behavior is preserved.
     */
    public static File[] tryList(Context context, File folder) {
        if (!isAndroidDataPath(folder)) return null;
        if (!ShizukuShell.isGranted()) return null;
        List<ShizukuFile> out = new ArrayList<>();
        if (folder.getAbsolutePath().equals(ANDROID_DATA)) {
            // List installed packages that have an external data dir: fast and stable.
            ShizukuShell.Result r = ShizukuShell.exec("ls " + ANDROID_DATA);
            if (!r.success && r.exitCode != 0) return null;
            for (String name : r.stdout.split("\n")) {
                name = name.trim();
                if (!name.isEmpty() && !name.equals("..")) out.add(new ShizukuFile(ANDROID_DATA + "/" + name, true, 0));
            }
            return out.toArray(new ShizukuFile[0]);
        }
        ShizukuShell.Result r = ShizukuShell.exec("ls -lA " + RootManager.escapeShellArg(folder.getAbsolutePath()));
        if (!r.success) return null;
        for (String line : r.stdout.split("\n")) {
            ShizukuFile f = parseLsLine(folder.getAbsolutePath(), line);
            if (f != null) out.add(f);
        }
        return out.toArray(new ShizukuFile[0]);
    }

    /** Minimal ls -l parser: "-rw-rw---- 1 u0_a123 media_rw  1234 2026-01-01 10:00 name". */
    private static ShizukuFile parseLsLine(String parent, String line) {
        if (line == null || line.length() < 12) return null;
        char type = line.charAt(0);
        if (type != '-' && type != 'd' && type != 'l') return null;
        String[] parts = line.split("\\s+", 8);
        if (parts.length < 8) {
            // Some toybox builds omit the date; fall back to everything after perms+links+owner+group+size.
            if (parts.length >= 6) {
                String name = line.substring(line.lastIndexOf(parts[5]) + parts[5].length()).trim();
                if (name.isEmpty()) return null;
                long size;
                try {
                    size = Long.parseLong(parts[4]);
                } catch (NumberFormatException e) {
                    size = 0;
                }
                return new ShizukuFile(join(parent, name), type == 'd', size);
            }
            return null;
        }
        String name = parts[7];
        if (name.equals(".") || name.equals("..")) return null;
        // Strip symlink target suffix: "name -> target"
        int arrow = name.indexOf(" -> ");
        if (arrow > 0) name = name.substring(0, arrow);
        long size;
        try {
            size = Long.parseLong(parts[4]);
        } catch (NumberFormatException e) {
            size = 0;
        }
        return new ShizukuFile(join(parent, name), type == 'd' || type == 'l' && name.indexOf('.') < 0, size);
    }

    private static String join(String parent, String name) {
        return parent.endsWith("/") ? parent + name : parent + "/" + name;
    }

    // ======== File overrides backed by shell ========

    @Override
    public boolean exists() {
        return ShizukuShell.exec("[ -e " + RootManager.escapeShellArg(getAbsolutePath()) + " ]").success;
    }

    @Override
    public boolean isDirectory() {
        // Cache-only: every entry from tryList carries the real value. Shell probing here
        // would fork per entry (ANR when callers loop, e.g. setCurrentFolder).
        return directory;
    }

    @Override
    public boolean isFile() {
        return !directory;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public long length() {
        return size > 0 ? size : super.length();
    }

    @Override
    public boolean isHidden() {
        return getName().startsWith(".");
    }

    @Override
    public String[] list() {
        File[] files = listFiles();
        if (files == null) return null;
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        return names;
    }

    @Override
    public File[] listFiles() {
        return tryList(null, this);
    }

    @Override
    public File getParentFile() {
        String p = getParent();
        if (p == null) return null;
        if (isAndroidDataPath(p)) return new ShizukuFile(p);
        return super.getParentFile();
    }

    @Override
    public boolean mkdir() {
        return ShizukuShell.exec("mkdir " + RootManager.escapeShellArg(getAbsolutePath())).success;
    }

    @Override
    public boolean mkdirs() {
        return ShizukuShell.exec("mkdir -p " + RootManager.escapeShellArg(getAbsolutePath())).success;
    }

    @Override
    public boolean delete() {
        if (RootManager.isPathBlocked(getAbsolutePath())) return false;
        return ShizukuShell.exec("rm -rf " + RootManager.escapeShellArg(getAbsolutePath())).success;
    }

    @Override
    public boolean renameTo(File dest) {
        if (RootManager.isPathBlocked(getAbsolutePath())) return false;
        return ShizukuShell.exec("mv " + RootManager.escapeShellArg(getAbsolutePath()) + " " + RootManager.escapeShellArg(dest.getAbsolutePath())).success;
    }

    /**
     * Copy this file/directory out of Android/data into a normal (app-accessible) File.
     * Used before opening/sharing files that the app process cannot read directly.
     */
    public File materializeTo(Context context) throws IOException {
        File cacheDir = new File(context.getCacheDir(), "shizuku");
        File out = new File(cacheDir, getName());
        if (out.exists()) out.delete();
        cacheDir.mkdirs();
        ShizukuShell.Result r = ShizukuShell.exec("cat " + RootManager.escapeShellArg(getAbsolutePath()) + " > " + RootManager.escapeShellArg(out.getAbsolutePath()));
        if (!r.success) throw new IOException("Shizuku copy failed: " + r.stderr);
        return out;
    }

    @Override
    public int compareTo(File other) {
        if (other instanceof ShizukuFile) return super.compareTo(other);
        // Keep directories-first ordering consistent with the app's File comparator.
        if (isDirectory() != other.isDirectory()) return isDirectory() ? -1 : 1;
        return getName().compareToIgnoreCase(other.getName());
    }
}

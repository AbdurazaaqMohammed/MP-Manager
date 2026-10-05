package io.github.abdurazaaqmohammed.MPManager.shizuku;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * A java.io.File wrapper that performs its filesystem operations through Shizuku (shell uid).
 * Used as the fallback whenever the app cannot reach a path on its own: Android 11+ restricted
 * storage, and system/data partitions that are off-limits to an unprivileged app. Subclassing File
 * keeps MainActivity/adapters working unchanged via polymorphism, mirroring the FTPFileWrapper
 * approach used for FTP paths.
 */
public class ShizukuFile extends File {

    private static final long UNKNOWN_SIZE = -1L;

    private final boolean directory;
    private final long size;
    private final long lastModified;

    public ShizukuFile(String pathname, boolean directory, long size, long lastModified) {
        super(pathname);
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
    }

    public ShizukuFile(String pathname, boolean directory, long size) {
        this(pathname, directory, size, 0L);
    }

    public ShizukuFile(String pathname) {
        this(pathname, guessIsDirectory(pathname), UNKNOWN_SIZE, 0L);
    }

    private static boolean guessIsDirectory(String path) {
        // Only used when constructing a ShizukuFile for an individual path (e.g. getParentFile).
        // Entries coming out of tryList always carry the real value from the ls output.
        return !path.substring(path.lastIndexOf('/') + 1).contains(".");
    }

    /** True for any absolute path, the only kind Shizuku can act on. */
    public static boolean isShellPath(String path) {
        return path != null && path.startsWith("/");
    }

    public static boolean isShellPath(File f) {
        return f != null && isShellPath(f.getAbsolutePath());
    }

    /**
     * Entry point used by MainActivity when File.listFiles() fails. Returns null untouched when
     * Shizuku is not usable, so existing behavior is preserved.
     */
    public static File[] tryList(Context context, File folder) {
        if (!isShellPath(folder)) return null;
        if (!ShizukuShell.isGranted()) return null;
        List<ShizukuFile> out = new ArrayList<>();
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
                    size = UNKNOWN_SIZE;
                }
                return new ShizukuFile(join(parent, name), type == 'd', size, 0L);
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
            size = UNKNOWN_SIZE;
        }
        long mtime = parseLsTime(parts[5], parts[6]);
        return new ShizukuFile(join(parent, name), type == 'd' || type == 'l' && name.indexOf('.') < 0, size, mtime);
    }

    /**
     * Parse the "2026-01-01" + "10:00" pair from {@code ls -l} into epoch millis. Returns 0 when the
     * shape is not recognized: toybox prints "Mon DD HH:MM" for recently modified files and some
     * builds omit the year, and a wrong timestamp is worse than an unknown one.
     */
    private static long parseLsTime(String date, String time) {
        if (date == null || time == null || date.length() < 10) return 0L;
        if (date.charAt(4) != '-' || date.charAt(7) != '-'
                || date.charAt(0) < '0' || date.charAt(0) > '9') return 0L;
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(date + " " + time).getTime();
        } catch (ParseException e) {
            return 0L;
        }
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

    /**
     * Cache-only, like {@link #isDirectory()}: the app cannot stat these paths directly, and
     * FileSorting calls this from its comparator, so a live stat here would fork a shell per
     * comparison and ANR on large directories such as Android/data.
     */
    @Override
    public long lastModified() {
        return lastModified;
    }

    @Override
    public long length() {
        return size != UNKNOWN_SIZE ? size : super.length();
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
        if (isShellPath(p)) return new ShizukuFile(p);
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
     * Copy this file/directory out of a shell-only path into a normal (app-accessible) File.
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
        // Deliberately never probes the other file: TimSort and Arrays.binarySearch call this, and
        // isDirectory() on a plain File blocks on FUSE for paths the app cannot reach. Plain
        // path order also keeps the ordering consistent for binarySearch, and FileSorting re-sorts
        // with real metadata immediately afterwards.
        return super.compareTo(other);
    }
}

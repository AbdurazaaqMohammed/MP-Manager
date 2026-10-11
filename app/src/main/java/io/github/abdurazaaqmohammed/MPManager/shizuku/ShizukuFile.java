package io.github.abdurazaaqmohammed.MPManager.shizuku;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.utils.RootManager;

/**
 * A java.io.File wrapper that performs its filesystem operations through Shizuku (shell uid).
 * Used for paths a regular app cannot list: /storage/emulated/0/Android/data on Android 11+,
 * but also other shell-readable directories such as /storage/emulated and /system. Reading
 * works even where the app can never write. Subclassing File keeps MainActivity/adapters
 * working unchanged via polymorphism, mirroring the FTPFileWrapper approach used for FTP paths.
 */
public class ShizukuFile extends File {

    public static final String ANDROID_DATA = "/storage/emulated/0/Android/data";
    private static final String ANDROID_DATA_SDCARD = "/sdcard/Android/data";

    private boolean directory;
    private final long size;
    /** True for symlinks; {@link #directory} is corrected by target resolution. */
    private boolean isLink;

    public ShizukuFile(String pathname, boolean directory, long size) {
        super(pathname);
        this.directory = directory;
        this.size = size;
    }

    /** Updates the type after symlink target resolution (see tryList). */
    void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public ShizukuFile(String pathname) {
        this(pathname, guessIsDirectory(pathname), 0);
    }

    private static boolean guessIsDirectory(String path) {
        // Root Android/data itself is always a directory.
        if (path.equals(ANDROID_DATA)) return true;
        // Never guess from the name: "my.folder" is a directory and must not be
        // treated as a file. Ask the filesystem; children listed by tryList carry
        // their real type anyway, this only covers paths built by hand.
        try {
            File f = new File(path);
            if (f.isDirectory()) return true;
            if (f.isFile()) return false;
        } catch (Exception ignored) {
        }
        if (ShizukuShell.isGranted()) {
            return ShizukuShell.exec("[ -d " + RootManager.escapeShellArg(path) + " ]").success;
        }
        return false;
    }

    public static boolean isAndroidDataPath(String path) {
        if (path == null) return false;
        return path.equals(ANDROID_DATA) || path.startsWith(ANDROID_DATA + "/")
                || path.equals(ANDROID_DATA_SDCARD) || path.startsWith(ANDROID_DATA_SDCARD + "/");
    }

    public static boolean isAndroidDataPath(File f) {
        return f != null && isAndroidDataPath(f.getAbsolutePath());
    }

    /** True when the file is backed by Shizuku (an Android/data path or any ShizukuFile). */
    public static boolean isShizukuPath(File f) {
        return f instanceof ShizukuFile || isAndroidDataPath(f);
    }

    /**
     * Entry point used by MainActivity when File.listFiles() returns null or an empty array
     * (Android/data on API 30+, /storage/emulated, /system, ...). Lists via the shell when
     * Shizuku is usable; returns null otherwise so existing behavior is preserved. Entries
     * carry their real type/size from {@code ls}, so callers can render them read-only.
     */
    public static File[] tryList(Context context, File folder) {
        if (folder == null) return null;
        if (!ShizukuShell.isGranted()) return null;
        String path = folder.getAbsolutePath();
        List<ShizukuFile> out = new ArrayList<>();
        if (path.equals(ANDROID_DATA)) {
            // List installed packages that have an external data dir: fast and stable.
            ShizukuShell.Result r = ShizukuShell.exec("ls " + ANDROID_DATA);
            if (!r.success && r.exitCode != 0) return null;
            for (String name : r.stdout.split("\n")) {
                name = name.trim();
                if (!name.isEmpty() && !name.equals("..")) out.add(new ShizukuFile(ANDROID_DATA + "/" + name, true, 0));
            }
            return out.toArray(new ShizukuFile[0]);
        }
        // Guard against listing a file as a folder: `ls -lA <file>` prints the
        // file itself, which used to become a phantom child "<file>/<file>"
        // (tapping it then failed with "cat: .../build.prop/build.prop:
        // Not a directory"). Bail out so callers show an error instead.
        ShizukuShell.Result r = ShizukuShell.exec("test -d " + RootManager.escapeShellArg(path)
                + " || exit 4; ls -lA " + RootManager.escapeShellArg(path));
        if (!r.success) return null;
        List<ShizukuFile> links = new ArrayList<>();
        for (String line : r.stdout.split("\n")) {
            ShizukuFile f = parseLsLine(path, line);
            if (f == null) continue;
            // Symlinks need target resolution: a link to a file must open as a
            // file, a link to a dir must navigate. Resolved in one batch below.
            if (f.isLink) links.add(f);
            out.add(f);
        }
        resolveSymlinks(links);
        return out.toArray(new ShizukuFile[0]);
    }

    /**
     * Resolves symlink targets in one shell call. Replaces the old blanket
     * "link == directory" rule (which sent links-to-files into folder listings
     * and from there to the phantom-child crash) without guessing from names.
     */
    private static void resolveSymlinks(List<ShizukuFile> links) {
        if (links.isEmpty() || !ShizukuShell.isGranted()) return;
        try {
            StringBuilder cmd = new StringBuilder();
            for (int i = 0; i < links.size(); i++) {
                if (i > 0) cmd.append(' ');
                String p = RootManager.escapeShellArg(links.get(i).getAbsolutePath());
                cmd.append("{ [ -d ").append(p).append(" ] && echo LINK_DIR_").append(i)
                        .append("; } || { [ -e ").append(p).append(" ] && echo LINK_FILE_").append(i).append("; };");
            }
            ShizukuShell.Result r = ShizukuShell.exec(cmd.toString());
            if (!r.success || r.stdout == null) return;
            for (String line : r.stdout.split("\n")) {
                String t = line.trim();
                try {
                    if (t.startsWith("LINK_DIR_")) {
                        links.get(Integer.parseInt(t.substring("LINK_DIR_".length()))).setDirectory(true);
                    } else if (t.startsWith("LINK_FILE_")) {
                        links.get(Integer.parseInt(t.substring("LINK_FILE_".length()))).setDirectory(false);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
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
        // Type comes from the ls permission column. Symlinks keep directory=true
        // initially (previous behavior) and are corrected to the target type by
        // resolveSymlinks(); never infer the type from the name
        // ("my.folder" is a directory).
        ShizukuFile out = new ShizukuFile(join(parent, name), type == 'd' || type == 'l', size);
        out.isLink = type == 'l';
        return out;
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

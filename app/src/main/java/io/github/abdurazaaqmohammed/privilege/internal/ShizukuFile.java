package io.github.abdurazaaqmohammed.privilege.internal;

import io.github.abdurazaaqmohammed.privilege.internal.ShizukuShell;
import io.github.abdurazaaqmohammed.util.io.PathGuards;
import io.github.abdurazaaqmohammed.util.text.ShellQuote;



import android.content.Context;

import java.io.File;
import java.io.IOException;


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
    private final long lastModified;
    /** True for symlinks; {@link #directory} is corrected by target resolution. */
    private boolean isLink;
    private final String mode;
    private final boolean hasStat;

    public ShizukuFile(String pathname, boolean directory, long size) {
        this(pathname, directory, size, 0L, false, null, false);
    }

    public ShizukuFile(String pathname, boolean directory, long size, long lastModified) {
        this(pathname, directory, size, lastModified, false, null, false);
    }

    public ShizukuFile(String pathname, boolean directory, long size, long lastModified,
                       boolean isLink, String mode, boolean hasStat) {
        super(pathname);
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
        this.isLink = isLink;
        this.mode = mode;
        this.hasStat = hasStat;
    }

    /** Updates the type after symlink target resolution (see tryList). */
    void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public String getMode() {
        return mode;
    }

    public boolean hasStat() {
        return hasStat;
    }

    public ShizukuFile(String pathname) {
        this(pathname, guessIsDirectory(pathname), 0L, 0L, false, null, false);
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
        Boolean binderDir = ShizukuManager.isDirectoryBinderIfConnected(path);
        if (binderDir != null) return binderDir;
        if (ShizukuShell.isGranted()) {
            return ShizukuShell.exec("[ -d " + ShellQuote.escapeShellArg(path) + " ]").success;
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
     * (Android/data on API 30+, /storage/emulated, /system, ...). Prefers the already-alive
     * Shizuku Binder user service for single-digit-millisecond reads, falling back to a single
     * combined shell command only when Binder cannot answer.
     */
    public static File[] tryList(Context context, File folder) {
        if (folder == null) return null;
        if (!ShizukuShell.isGranted()) return null;
        String path = folder.getAbsolutePath();
        File[] viaBinder = ShizukuManager.listBinderAsShizukuFiles(context, path);
        if (viaBinder != null) return viaBinder;
        // Guard against listing a file as a folder or running two separate shell spawns
        // for symlinks: LsListingSupport checks -d, lists, and resolves symlinks in one pass.
        ShizukuShell.Result r = ShizukuShell.exec(LsListingSupport.buildSingleCallLsCommand(path));
        if (!r.success || r.stdout == null) return null;
        String cleanParent = (path.endsWith("/") && path.length() > 1)
                ? path.substring(0, path.length() - 1) : path;
        return LsListingSupport.parseLsWithLinksOutput(cleanParent, r.stdout, true);
    }

    // ======== File overrides backed by shell ========

    @Override
    public boolean exists() {
        Boolean binderExists = ShizukuManager.existsBinderIfConnected(getAbsolutePath());
        if (binderExists != null) return binderExists;
        return ShizukuShell.exec("[ -e " + ShellQuote.escapeShellArg(getAbsolutePath()) + " ]").success;
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

    /** True when the ls entry was a symlink (target type already resolved). */
    public boolean isLink() {
        return isLink;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public long length() {
        return size > 0 ? size : super.length();
    }

    /**
     * The listing already carried the timestamp from {@code ls -l}, so this
     * answers without touching the shell. Leaving it inherited made every listed
     * entry report 0, which pushed the file list into a per-row shell stat and
     * left dates appearing seconds late in big folders such as /vendor.
     */
    @Override
    public long lastModified() {
        return lastModified > 0 ? lastModified : super.lastModified();
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
        return ShizukuShell.exec("mkdir " + ShellQuote.escapeShellArg(getAbsolutePath())).success;
    }

    @Override
    public boolean mkdirs() {
        return ShizukuShell.exec("mkdir -p " + ShellQuote.escapeShellArg(getAbsolutePath())).success;
    }

    @Override
    public boolean delete() {
        if (PathGuards.isPathBlocked(getAbsolutePath())) return false;
        return ShizukuShell.exec("rm -rf " + ShellQuote.escapeShellArg(getAbsolutePath())).success;
    }

    @Override
    public boolean renameTo(File dest) {
        if (PathGuards.isPathBlocked(getAbsolutePath())) return false;
        return ShizukuShell.exec("mv " + ShellQuote.escapeShellArg(getAbsolutePath()) + " " + ShellQuote.escapeShellArg(dest.getAbsolutePath())).success;
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
        // cp rather than "cat > ...": one kernel-side copy instead of the bytes
        // crossing a shell pipe, and the mode is preserved as a side effect.
        ShizukuShell.Result r = ShizukuShell.exec("cp -pf -- " + ShellQuote.escapeShellArg(getAbsolutePath()) + " " + ShellQuote.escapeShellArg(out.getAbsolutePath()));
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

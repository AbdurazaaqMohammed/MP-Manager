package io.github.abdurazaaqmohammed.MPManager.shizuku;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    private final long lastModified;
    /** True for symlinks; {@link #directory} is corrected by target resolution. */
    private boolean isLink;

    public ShizukuFile(String pathname, boolean directory, long size) {
        this(pathname, directory, size, 0L);
    }

    public ShizukuFile(String pathname, boolean directory, long size, long lastModified) {
        super(pathname);
        this.directory = directory;
        this.size = size;
        this.lastModified = lastModified;
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
                + " || exit 4; LC_ALL=C ls -lAH " + RootManager.escapeShellArg(path));
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
        if (links.isEmpty() || !ShizukuShell.isGranted()) {
            for (ShizukuFile link : links) link.setDirectory(false);
            return;
        }
        boolean[] marked = new boolean[links.size()];
        try {
            StringBuilder cmd = new StringBuilder();
            for (int i = 0; i < links.size(); i++) {
                if (i > 0) cmd.append(' ');
                String p = RootManager.escapeShellArg(links.get(i).getAbsolutePath());
                cmd.append("if [ -d ").append(p).append(" ]; then echo LINK_DIR_").append(i)
                        .append("; elif [ -e ").append(p).append(" ] || [ -L ").append(p)
                        .append(" ]; then echo LINK_FILE_").append(i).append("; fi;");
            }
            ShizukuShell.Result r = ShizukuShell.exec(cmd.toString());
            if (r.success && r.stdout != null) {
                for (String line : r.stdout.split("\n")) {
                    String t = line.trim();
                    try {
                        if (t.startsWith("LINK_DIR_")) {
                            marked[Integer.parseInt(t.substring("LINK_DIR_".length()))] = true;
                            links.get(Integer.parseInt(t.substring("LINK_DIR_".length()))).setDirectory(true);
                        } else if (t.startsWith("LINK_FILE_")) {
                            marked[Integer.parseInt(t.substring("LINK_FILE_".length()))] = true;
                            links.get(Integer.parseInt(t.substring("LINK_FILE_".length()))).setDirectory(false);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        for (int i = 0; i < links.size(); i++) {
            if (!marked[i]) links.get(i).setDirectory(false);
        }
    }

    /**
     * Minimal {@code ls -l} parser:
     * {@code -rw-rw---- 1 u0_a123 media_rw 1234 2026-01-01 10:00 name}.
     *
     * <p>The date is kept so {@link #lastModified()} can answer without a shell
     * round trip. The name is taken by character offset rather than by splitting
     * into fixed fields, because the date is one or two tokens depending on the
     * toybox build and names may contain (or repeat) spaces.
     */
    private static ShizukuFile parseLsLine(String parent, String line) {
        if (line == null || line.length() < 12) return null;
        char type = line.charAt(0);
        if (type != '-' && type != 'd' && type != 'l') return null;
        String[] parts = line.split("\\s+", 8);
        if (parts.length < 6) return null;
        long size = parseLongOrZero(parts[4]);
        String date1 = parts[5];
        String date2 = parts.length > 6 ? parts[6] : null;
        int dateTokens;
        long modified;
        if (date1.matches("\\d{4}-\\d{2}-\\d{2}")) {
            if (date2 != null && date2.matches("\\d{2}:\\d{2}")) {
                modified = parseLsTimestamp(date1, date2);
                dateTokens = 2;
            } else {
                modified = parseLsTimestamp(date1, null);
                dateTokens = 1;
            }
        } else if (date1.matches("\\d{2}-\\d{2}-\\d{4}")) {
            modified = parseLsTimestamp(date1, null);
            dateTokens = 1;
        } else if (date1.matches("\\d{2}-\\d{2}") && date2 != null && date2.matches("\\d{2}:\\d{2}")) {
            // toybox prints MM-DD HH:MM only for the current year.
            modified = parseLsTimestamp(Calendar.getInstance().get(Calendar.YEAR) + "-" + date1, date2);
            dateTokens = 2;
        } else if (MONTH_ABBREVIATIONS.contains(date1.replace(".", "").toUpperCase(Locale.ROOT))) {
            // Classic "Jan  1 10:00" / "Jan  1  2024". The year is only printed
            // for older files, so a missing one means the current year.
            String[] trailing = parts.length > 7 ? parts[7].split("\\s+", 2) : new String[0];
            String time = null;
            String year = null;
            int extra = 1;
            if (trailing.length == 2) {
                time = trailing[0].matches("\\d{2}:\\d{2}") ? trailing[0] : null;
                year = trailing[0].matches("\\d{4}") ? trailing[0] : null;
                extra = 2;
            }
            StringBuilder head = new StringBuilder(
                    year != null ? year : String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
            head.append('-').append(String.format(Locale.ROOT, "%02d", monthIndex(date1)));
            head.append('-').append(String.format(Locale.ROOT, "%02d", parseDay(date2)));
            modified = parseLsTimestamp(head.toString(), time);
            dateTokens = 1 + extra;
        } else {
            // Unrecognised (or absent) date column: keep the entry with no date
            // rather than dropping it from the listing.
            modified = 0L;
            dateTokens = 1;
        }
        int nameStart = offsetAfterFields(line, 5 + dateTokens);
        String name = nameStart > line.length() ? "" : line.substring(nameStart).trim();
        if (name.isEmpty() && dateTokens > 0) {
            dateTokens = 0;
            nameStart = offsetAfterFields(line, 5);
            name = nameStart > line.length() ? "" : line.substring(nameStart).trim();
        }
        if (name.isEmpty() || name.equals(".") || name.equals("..")) return null;
        // Strip symlink target suffix: "name -> target"
        int arrow = name.indexOf(" -> ");
        if (arrow > 0) name = name.substring(0, arrow);
        // Type comes from the ls permission column. Symlinks keep directory=true
        // initially and are corrected to the target type by resolveSymlinks();
        // never infer the type from the name.
        ShizukuFile out = new ShizukuFile(join(parent, name), type == 'd' || type == 'l', size, modified);
        out.isLink = type == 'l';
        return out;
    }

    /** Offset just past the {@code count}-th whitespace-separated field. */
    private static int offsetAfterFields(String line, int count) {
        int i = 0;
        int seen = 0;
        while (i < line.length() && seen < count) {
            while (i < line.length() && line.charAt(i) != ' ') i++;
            while (i < line.length() && line.charAt(i) == ' ') i++;
            seen++;
        }
        return i;
    }

    private static final Set<String> MONTH_ABBREVIATIONS = new HashSet<>(Arrays.asList(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"));

    private static final String[] MONTH_NAMES = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN",
            "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};

    private static int monthIndex(String abbr) {
        String key = abbr.replace(".", "").toUpperCase(Locale.ROOT);
        for (int i = 0; i < MONTH_NAMES.length; i++) {
            if (MONTH_NAMES[i].equals(key)) return i + 1;
        }
        return 1;
    }

    private static int parseDay(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return 1;
        }
    }

    private static long parseLongOrZero(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Turns an {@code ls -l} date (plus optional time) into epoch millis; 0 when unusable. */
    private static long parseLsTimestamp(String date, String time) {
        if (date == null) return 0;
        try {
            String[] d = date.trim().split("-");
            if (d.length != 3) return 0;
            Calendar c = Calendar.getInstance();
            c.set(Calendar.MILLISECOND, 0);
            c.set(Integer.parseInt(d[0]), Integer.parseInt(d[1]) - 1, Integer.parseInt(d[2]));
            if (time != null && time.matches("\\d{2}:\\d{2}")) {
                String[] t = time.split(":");
                c.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0]));
                c.set(Calendar.MINUTE, Integer.parseInt(t[1]));
                c.set(Calendar.SECOND, 0);
            } else {
                c.set(Calendar.HOUR_OF_DAY, 12);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 0);
            }
            return c.getTimeInMillis();
        } catch (Exception e) {
            return 0;
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

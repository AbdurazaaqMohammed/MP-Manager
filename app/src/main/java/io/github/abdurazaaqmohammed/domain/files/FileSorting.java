package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.abdurazaaqmohammed.data.prefs.DirSizeCache;

/**
 * Pure file-listing sort logic extracted from MainActivity.
 * Sort mode comes from the caller (see data.prefs.ListPrefs).
 */
public final class FileSorting {

    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;
    public static final int SORT_TYPE = 3;

    private static final int APK_PRIORITY_DIR = 0;
    private static final int APK_PRIORITY_MANIFEST = 1;
    private static final int APK_PRIORITY_CLASSES = 2;
    private static final int APK_PRIORITY_ARSC = 3;
    private static final int APK_PRIORITY_OTHER = 4;

    private FileSorting() {
    }

    public static String getExt(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    public static int compareNaturalIgnoreCase(String a, String b) {
        if (a == b) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        int i = 0, j = 0;
        int na = a.length(), nb = b.length();
        while (i < na && j < nb) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            boolean da = ca >= '0' && ca <= '9';
            boolean db = cb >= '0' && cb <= '9';
            if (da && db) {
                int s1 = i, s2 = j;
                while (s1 < na && a.charAt(s1) == '0') s1++;
                while (s2 < nb && b.charAt(s2) == '0') s2++;
                int e1 = s1;
                while (e1 < na && a.charAt(e1) >= '0' && a.charAt(e1) <= '9') e1++;
                int e2 = s2;
                while (e2 < nb && b.charAt(e2) >= '0' && b.charAt(e2) <= '9') e2++;
                int len1 = e1 - s1;
                int len2 = e2 - s2;
                if (len1 != len2) return len1 < len2 ? -1 : 1;
                for (int k = 0; k < len1; k++) {
                    char d1 = a.charAt(s1 + k);
                    char d2 = b.charAt(s2 + k);
                    if (d1 != d2) return d1 < d2 ? -1 : 1;
                }
                int zeros1 = s1 - i;
                int zeros2 = s2 - j;
                if (zeros1 != zeros2) return zeros1 < zeros2 ? -1 : 1;
                i = e1;
                j = e2;
                continue;
            }
            char la = Character.toLowerCase(Character.toUpperCase(ca));
            char lb = Character.toLowerCase(Character.toUpperCase(cb));
            if (la != lb) return la < lb ? -1 : 1;
            if (ca != cb) {
                int up = Character.toUpperCase(ca) - Character.toUpperCase(cb);
                if (up != 0) return up < 0 ? -1 : 1;
            }
            i++;
            j++;
        }
        if (na == nb) return 0;
        return (na - i) <= 0 ? -1 : 1;
    }

    public static void sortFiles(File[] files, int sortBy, boolean reverse) {
        if (files == null || files.length < 2) return;
        // Reading isDirectory()/length()/lastModified() inside the comparator cost
        // O(n log n) stat syscalls per folder load, which is what made listings
        // such as /vendor feel slow. Read each value once, then sort.
        int n = files.length;
        final long[] sizes = new long[n];
        final long[] times = new long[n];
        final boolean[] dirs = new boolean[n];
        final String[] names = new String[n];
        final String[] exts = new String[n];
        boolean needSizes = sortBy == SORT_SIZE;
        boolean needTimes = sortBy == SORT_DATE;
        boolean needExts = sortBy == SORT_TYPE;
        for (int i = 0; i < n; i++) {
            File f = files[i];
            if (f == null) {
                names[i] = "";
                exts[i] = "";
                continue;
            }
            names[i] = f.getName();
            try {
                dirs[i] = f.isDirectory();
            } catch (Exception ignored) {
            }
            if (needExts) exts[i] = getExt(names[i]);
            if (!dirs[i] || needTimes) {
                try {
                    times[i] = f.lastModified();
                } catch (Exception ignored) {
                }
            }
        }
        // Size sorting needs real folder sizes, which means walking whole trees.
        // Each measured folder goes into the persistent DirSizeCache (validated
        // against the folder's own mtime), so revisits and later app starts skip
        // the walk; only never-measured or changed folders are walked, in
        // parallel, before the actual sort.
        if (needSizes) {
            List<File> dirsToSize = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (dirs[i]) {
                    Long cached = DirSizeCache.peek(files[i]);
                    if (cached != null) {
                        sizes[i] = cached;
                        continue;
                    }
                    Long memory = DIR_SIZE_CACHE.get(pathOf(files[i]));
                    if (memory != null) {
                        sizes[i] = memory;
                        DirSizeCache.record(files[i], memory);
                        continue;
                    }
                    dirsToSize.add(files[i]);
                } else {
                    try {
                        sizes[i] = files[i].length();
                    } catch (Exception ignored) {
                    }
                }
            }
            computeSizesParallel(dirsToSize);
            for (int i = 0; i < n; i++) {
                if (dirs[i]) sizes[i] = dirSize(files[i]);
            }
            if (!dirsToSize.isEmpty()) DirSizeCache.scheduleSave();
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            int ia = a;
            int ib = b;
            int result;
            switch (sortBy) {
                case SORT_SIZE:
                    result = Long.compare(sizes[ia], sizes[ib]);
                    if (result == 0) result = compareNaturalIgnoreCase(names[ia], names[ib]);
                    break;
                case SORT_DATE:
                    result = Long.compare(times[ia], times[ib]);
                    if (result == 0) result = compareNaturalIgnoreCase(names[ia], names[ib]);
                    break;
                case SORT_TYPE:
                    result = exts[ia].compareToIgnoreCase(exts[ib]);
                    if (result == 0) result = compareNaturalIgnoreCase(names[ia], names[ib]);
                    break;
                default:
                    result = compareNaturalIgnoreCase(names[ia], names[ib]);
                    break;
            }
            if (dirs[ia] && !dirs[ib]) return -1;
            if (!dirs[ia] && dirs[ib]) return 1;
            return reverse ? -result : result;
        });
        File[] sorted = new File[n];
        for (int i = 0; i < n; i++) sorted[i] = files[order[i]];
        System.arraycopy(sorted, 0, files, 0, n);
    }

    private static final ConcurrentHashMap<String, Long> DIR_SIZE_CACHE =
            new ConcurrentHashMap<>();
    // Storage-bound work: a handful of concurrent walkers saturates flash I/O
    // without flooding the system with hundreds of stat calls at once.
    private static final ExecutorService SIZE_POOL =
            Executors.newFixedThreadPool(
                    Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())));

    /** Walks every uncached sibling folder in parallel so a size sort stays responsive. */
    private static void computeSizesParallel(List<File> dirs) {
        if (dirs == null || dirs.isEmpty()) return;
        List<Future<?>> futures = new ArrayList<>();
        for (File d : dirs) {
            if (d == null) continue;
            futures.add(SIZE_POOL.submit(() -> recursiveSize(d)));
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception ignored) {
            }
        }
    }

    /** Recursively sums a folder's contents; every visited folder is cached so
     * one walk of a deep tree also covers all of its sub-folders. */
    private static long recursiveSize(File dir) {
        if (dir == null) return 0;
        String key = pathOf(dir);
        if (key == null) return 0;
        Long cached = DIR_SIZE_CACHE.get(key);
        if (cached != null) return cached;
        Long persisted = DirSizeCache.peek(dir);
        if (persisted != null) {
            DIR_SIZE_CACHE.put(key, persisted);
            return persisted;
        }
        long total = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) {
                if (c == null) continue;
                try {
                    total += c.isDirectory() ? recursiveSize(c) : c.length();
                } catch (Exception ignored) {
                }
            }
        }
        DIR_SIZE_CACHE.put(key, total);
        DirSizeCache.record(dir, total);
        return total;
    }

    private static String pathOf(File f) {
        try {
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    private static long dirSize(File dir) {
        if (dir == null) return 0;
        String key = pathOf(dir);
        if (key == null) return 0;
        Long cached = DIR_SIZE_CACHE.get(key);
        if (cached != null) return cached;
        return recursiveSize(dir);
    }

    public static void sortZipEntries(List<ZipEntryInfo> entries, String folderPath, int sortBy, boolean reverse) {
        if (entries == null) return;
        boolean isApk = isZipFolderApk(folderPath);
        Collections.sort(entries, (e1, e2) -> {
            if (e1.getName().equals("..")) return -1;
            if (e2.getName().equals("..")) return 1;
            if (isApk) {
                int p1 = apkEntryPriority(e1);
                int p2 = apkEntryPriority(e2);
                if (p1 != p2) return Integer.compare(p1, p2);
                if (p1 == APK_PRIORITY_CLASSES)
                    return Integer.compare(classesDexIndex(e1.getName()), classesDexIndex(e2.getName()));
                return compareZipEntries(e1, e2, sortBy);
            }
            int result = compareZipEntries(e1, e2, sortBy);
            if (e1.isDirectory() && !e2.isDirectory()) return -1;
            if (!e1.isDirectory() && e2.isDirectory()) return 1;
            return reverse ? -result : result;
        });
    }

    public static boolean isZipFolderApk(String folderPath) {
        if (folderPath == null) return false;
        int bang = folderPath.indexOf('!');
        String zipPath = bang == -1 ? folderPath : folderPath.substring(0, bang);
        return zipPath.toLowerCase().endsWith(".apk");
    }

    private static int apkEntryPriority(ZipEntryInfo entry) {
        if (entry.isDirectory()) return APK_PRIORITY_DIR;
        String name = entry.getName();
        if (name.equalsIgnoreCase("AndroidManifest.xml")) return APK_PRIORITY_MANIFEST;
        if (name.matches("classes\\d*\\.dex")) return APK_PRIORITY_CLASSES;
        if (name.equals("resources.arsc")) return APK_PRIORITY_ARSC;
        return APK_PRIORITY_OTHER;
    }

    private static int classesDexIndex(String name) {
        String num = name.substring("classes".length(), name.length() - ".dex".length());
        if (num.isEmpty()) return 1;
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    private static int compareZipEntries(ZipEntryInfo e1, ZipEntryInfo e2, int sortBy) {
        switch (sortBy) {
            case SORT_SIZE:
                return Long.compare(e1.getSize(), e2.getSize());
            case SORT_DATE:
                return Long.compare(e1.getLastModified(), e2.getLastModified());
            case SORT_TYPE: {
                int ext = getExt(e1.getName()).compareToIgnoreCase(getExt(e2.getName()));
                return ext == 0 ? compareNaturalIgnoreCase(e1.getName(), e2.getName()) : ext;
            }
            default:
                return compareNaturalIgnoreCase(e1.getName(), e2.getName());
        }
    }
}

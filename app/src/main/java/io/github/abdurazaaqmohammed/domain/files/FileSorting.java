package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        if (files == null) return;
        Arrays.sort(files, (f1, f2) -> {
            int result;
            switch (sortBy) {
                case SORT_SIZE:
                    result = Long.compare(f1.length(), f2.length());
                    break;
                case SORT_DATE:
                    result = Long.compare(f1.lastModified(), f2.lastModified());
                    break;
                case SORT_TYPE:
                    String ext1 = getExt(f1.getName());
                    String ext2 = getExt(f2.getName());
                    result = ext1.compareToIgnoreCase(ext2);
                    if (result == 0) result = compareNaturalIgnoreCase(f1.getName(), f2.getName());
                    break;
                default:
                    result = compareNaturalIgnoreCase(f1.getName(), f2.getName());
                    break;
            }
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return reverse ? -result : result;
        });
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

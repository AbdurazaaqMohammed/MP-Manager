package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.Context;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExtractedApksIndex {

    private static final Pattern APK_NAME_PATTERN = Pattern.compile("^(.*?)\\s+v(.*?)\\.apks?$", Pattern.CASE_INSENSITIVE);

    public record Match(File file, String version, int status) {
        public static final int STATUS_SAME = 0;   // Green: Extracted version matches installed
        public static final int STATUS_NEWER = 1;  // Yellow: Extracted version is newer
        public static final int STATUS_OLDER = -1; // Red: Extracted version is older
    }

    public static class Entry {
        public final File file;
        public final String version;

        public Entry(File file, String version) {
            this.file = file;
            this.version = version;
        }
    }

    private ExtractedApksIndex() {}

    /**
     * Scans the Extracted APKs folder and returns a lookup table keyed by identifier (name or pkg).
     */
    public static Map<String, Entry> scanExtractedFolder(Context context) {
        File folder = APKExtractorActivity.getAppFolder(context);
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return Collections.emptyMap();
        }

        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".apk") || lower.endsWith(".apks");
        });

        if (files == null || files.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, Entry> map = new HashMap<>();
        for (File f : files) {
            Matcher m = APK_NAME_PATTERN.matcher(f.getName());
            if (m.matches()) {
                String key = m.group(1).trim().toLowerCase();
                String version = m.group(2).trim();
                map.put(key, new Entry(f, version));
            }
        }
        return map;
    }

    /**
     * Finds matching status for an installed app in the scanned extracted index.
     * @return Match result or null if not found.
     */
    public static Match findMatch(Map<String, Entry> index, AppInfo appInfo) {
        if (index == null || index.isEmpty() || appInfo == null) return null;

        Entry entry = null;
        if (appInfo.name != null) {
            entry = index.get(appInfo.name.trim().toLowerCase());
        }
        if (entry == null && appInfo.packageName != null) {
            entry = index.get(appInfo.packageName.trim().toLowerCase());
        }

        if (entry == null) return null;

        String installedVer = appInfo.versionName != null ? appInfo.versionName.trim() : "";
        int cmp = compareVersions(entry.version, installedVer);
        int status;
        if (cmp == 0) {
            status = Match.STATUS_SAME;
        } else if (cmp > 0) {
            status = Match.STATUS_NEWER;
        } else {
            status = Match.STATUS_OLDER;
        }
        return new Match(entry.file, entry.version, status);
    }

    public static int compareVersions(String v1, String v2) {
        if (v1 == null) v1 = "";
        if (v2 == null) v2 = "";
        if (v1.equalsIgnoreCase(v2)) return 0;

        String[] p1 = v1.split("[.\\-_+]");
        String[] p2 = v2.split("[.\\-_+]");
        int len = Math.max(p1.length, p2.length);

        for (int i = 0; i < len; i++) {
            if (i >= p1.length) return -1;
            if (i >= p2.length) return 1;

            String s1 = p1[i];
            String s2 = p2[i];

            boolean isNum1 = isAllDigits(s1);
            boolean isNum2 = isAllDigits(s2);

            if (isNum1 && isNum2) {
                try {
                    long n1 = Long.parseLong(s1);
                    long n2 = Long.parseLong(s2);
                    if (n1 != n2) return n1 > n2 ? 1 : -1;
                } catch (Exception ignored) {
                    int c = s1.compareToIgnoreCase(s2);
                    if (c != 0) return c > 0 ? 1 : -1;
                }
            } else {
                int c = s1.compareToIgnoreCase(s2);
                if (c != 0) return c > 0 ? 1 : -1;
            }
        }
        return 0;
    }

    private static boolean isAllDigits(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }
}

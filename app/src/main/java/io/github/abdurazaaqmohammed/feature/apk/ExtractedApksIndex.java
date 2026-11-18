package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExtractedApksIndex {

    private static final Pattern PATTERN_SEMVER =
            Pattern.compile("^(.+?)[\\s_\\-]+v?(\\d+(?:\\.\\d+)+)(?:[\\s_\\-].*?)?\\.apks?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_GENERIC_V =
            Pattern.compile("^(.*?)[\\s_\\-]+v(.*?)?\\.apks?$", Pattern.CASE_INSENSITIVE);

    private static final Pattern PATTERN_SINGLE_NUM =
            Pattern.compile("^(.+?)[\\s_\\-]+v?(\\d+)(?:[\\s_\\-].*?)?\\.apks?$", Pattern.CASE_INSENSITIVE);

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

    public static String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]+", "");
    }

    /**
     * Scans the Extracted APKs folder and returns a lookup table keyed by identifier (name or pkg).
     * Maps each normalized identifier to the list of all matching files in that folder.
     */
    public static Map<String, List<Entry>> scanExtractedFolder(Context context) {
        File folder = APKExtractorActivity.getAppFolder(context);
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            return Collections.emptyMap();
        }

        File[] files = folder.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".apk") || lower.endsWith(".apks");
        });

        if (files == null || files.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, List<Entry>> map = new HashMap<>();
        for (File f : files) {
            String name = f.getName();
            String rawName = null;
            String ver = null;

            Matcher m1 = PATTERN_SEMVER.matcher(name);
            if (m1.matches()) {
                rawName = m1.group(1);
                ver = m1.group(2);
            } else {
                Matcher m2 = PATTERN_GENERIC_V.matcher(name);
                if (m2.matches()) {
                    rawName = m2.group(1);
                    ver = m2.group(2);
                } else {
                    Matcher m3 = PATTERN_SINGLE_NUM.matcher(name);
                    if (m3.matches()) {
                        rawName = m3.group(1);
                        ver = m3.group(2);
                    }
                }
            }

            if (rawName != null && ver != null) {
                String key = normalizeKey(rawName);
                if (!key.isEmpty()) {
                    map.computeIfAbsent(key, k -> new ArrayList<>()).add(new Entry(f, ver.trim()));
                }
            }
        }
        return map;
    }

    /**
     * Retrieves all versions found for a specific app (by name or package name).
     */
    public static List<Entry> getEntriesForApp(Map<String, List<Entry>> index, AppInfo appInfo) {
        if (index == null || index.isEmpty() || appInfo == null) return Collections.emptyList();
        List<Entry> list = null;
        if (appInfo.name != null) {
            list = index.get(normalizeKey(appInfo.name));
        }
        if ((list == null || list.isEmpty()) && appInfo.packageName != null) {
            list = index.get(normalizeKey(appInfo.packageName));
        }
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Finds matching status for an installed app targeting the NEWEST extracted version available.
     * @return Match result or null if not found.
     */
    public static Match findMatch(Map<String, List<Entry>> index, AppInfo appInfo) {
        List<Entry> entries = getEntriesForApp(index, appInfo);
        if (entries.isEmpty()) return null;

        // Find the newest version among all extracted entries
        Entry newest = null;
        for (Entry e : entries) {
            if (newest == null || compareVersions(e.version, newest.version) > 0) {
                newest = e;
            }
        }

        if (newest == null) return null;

        String installedVer = appInfo.versionName != null ? appInfo.versionName.trim() : "";
        int cmp = compareVersions(newest.version, installedVer);
        int status;
        if (cmp == 0) {
            status = Match.STATUS_SAME;
        } else if (cmp > 0) {
            status = Match.STATUS_NEWER;
        } else {
            status = Match.STATUS_OLDER;
        }
        return new Match(newest.file, newest.version, status);
    }

    public static int compareVersions(String v1, String v2) {
        if (v1 == null) v1 = "";
        if (v2 == null) v2 = "";
        String s1 = v1.trim();
        String s2 = v2.trim();
        if (s1.equalsIgnoreCase(s2)) return 0;

        List<Long> nums1 = extractVersionNumbers(s1);
        List<Long> nums2 = extractVersionNumbers(s2);

        if (!nums1.isEmpty() && !nums2.isEmpty()) {
            int maxLen = Math.max(nums1.size(), nums2.size());
            for (int i = 0; i < maxLen; i++) {
                long n1 = i < nums1.size() ? nums1.get(i) : 0L;
                long n2 = i < nums2.size() ? nums2.get(i) : 0L;
                if (n1 != n2) {
                    return n1 > n2 ? 1 : -1;
                }
            }
            // If all numeric components are equal, consider them identical!
            // e.g. "9.1.0.8 rename fix" == "9.1.0.8"
            // "2.63.0" == "2.63.0-free"
            // "1.0.0" == "1.0"
            return 0;
        }

        // Fallback for non-numeric versions
        String clean1 = s1.replaceAll("[^a-zA-Z0-9]", "");
        String clean2 = s2.replaceAll("[^a-zA-Z0-9]", "");
        int c = clean1.compareToIgnoreCase(clean2);
        return Integer.compare(c, 0);
    }

    private static List<Long> extractVersionNumbers(String v) {
        List<Long> list = new ArrayList<>();
        if (v == null || v.isEmpty()) return list;
        // Match primary version number pattern, e.g. "9.1.0.8" or "2.63.0" or "9.3"
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)+)").matcher(v);
        String verStr = null;
        if (m.find()) {
            verStr = m.group(1);
        } else {
            Matcher m2 = Pattern.compile("(\\d+)").matcher(v);
            if (m2.find()) {
                verStr = m2.group(1);
            }
        }
        if (verStr != null) {
            String[] parts = verStr.split("\\.");
            for (String p : parts) {
                try {
                    list.add(Long.parseLong(p));
                } catch (Exception ignored) {
                }
            }
        }
        return list;
    }
}

package io.github.abdurazaaqmohammed.plugins.ipc;

import com.google.re2j.Pattern;

/** Bounded plugin metadata; filename expressions use a non-backtracking engine. */
public final class PluginMetadata {
    private static final int MAX_PATTERN_CHARS = 256;

    private PluginMetadata() {
    }

    public static String title(String value, String fallback) {
        String title = value == null ? "" : value.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        if (title.isEmpty()) title = fallback == null ? "" : fallback;
        return title.length() > 128 ? title.substring(0, 128) : title;
    }

    public static boolean filenameMatches(String pattern, String name) {
        if (pattern == null || pattern.isEmpty()) return true;
        if (pattern.length() > MAX_PATTERN_CHARS || name == null || name.length() > 512) return false;
        try {
            return Pattern.matches(pattern, name);
        } catch (com.google.re2j.PatternSyntaxException | IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String settingKey(String packageName, String key) {
        String safe = key == null ? "" : key;
        if (safe.length() > 128) safe = safe.substring(0, 128);
        return "external_setting:" + packageName + ":" + safe;
    }
}

package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.Intent;
import android.os.Bundle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.plugins.themes.ThemePackContracts;

/**
 * Helpers for the host theme extras attached to plugin invocations
 * ({@link PluginContracts#EXTRA_THEME_ID}, {@link PluginContracts#EXTRA_THEME_DARK},
 * {@link PluginContracts#EXTRA_THEME_ACCENT}, {@link PluginContracts#EXTRA_THEME_PALETTE})
 * and for the plugin -> host "apply theme" request
 * ({@link PluginContracts#EXTRA_APPLY_THEME_ID}).
 *
 * <p>The palette is a {@link Bundle} of primitive {@code int} colors keyed
 * by allowlisted names (see {@link #PALETTE_KEYS}); matching is
 * case/separator-insensitive and derived directly from the theme wire
 * contract's color tokens so the two stay in sync automatically. Plugins
 * render their own framework UI from these values; no theme library is
 * required.
 */
public final class PluginTheme {

    /** Allowlisted palette names, derived from {@link ThemePackContracts#COLOR_TOKENS}. */
    public static final String[] PALETTE_KEYS = ThemePackContracts.COLOR_TOKENS;

    /** normalized name -> canonical spelling */
    private static final Map<String, String> CANONICAL = buildCanonical();

    /** Immutable empty result for missing/invalid extras. */
    private static final ThemeInfo EMPTY = new ThemeInfo(null, false, false, 0, Collections.emptyMap());

    private PluginTheme() {
    }

    /** One resolved theme snapshot. All fields are primitive/immutable. */
    public static final class ThemeInfo {

        /** Theme id, or {@code null} when the host sent none. */
        public final String themeId;

        /** True when the host theme is dark. */
        public final boolean dark;

        /** True when {@link #accent} was present in the invocation. */
        public final boolean hasAccent;

        /** Accent color, 0 when {@link #hasAccent} is false. */
        public final int accent;

        private final Map<String, Integer> palette;

        ThemeInfo(String themeId, boolean dark, boolean hasAccent, int accent,
                  Map<String, Integer> palette) {
            this.themeId = themeId;
            this.dark = dark;
            this.hasAccent = hasAccent;
            this.accent = accent;
            this.palette = palette;
        }

        /** True when the allowlisted name resolves to a palette color. */
        public boolean has(String name) {
            String canonical = canonicalKey(name);
            if (canonical == null) return false;
            for (String key : palette.keySet()) {
                if (canonical.equals(canonicalKey(key))) return true;
            }
            return false;
        }

        /** Allowlisted color lookup; {@code fallback} when unknown or absent. */
        public int color(String name, int fallback) {
            String canonical = canonicalKey(name);
            if (canonical == null) return fallback;
            for (Map.Entry<String, Integer> entry : palette.entrySet()) {
                if (!canonical.equals(canonicalKey(entry.getKey()))) continue;
                Integer value = entry.getValue();
                if (value != null) return value;
            }
            return fallback;
        }

        /** Read-only canonical palette view (never null, possibly empty). */
        public Map<String, Integer> palette() {
            return palette;
        }
    }

    /** Read the theme extras from a host invocation intent. Never throws. */
    public static ThemeInfo from(Intent intent) {
        if (intent == null) return EMPTY;
        try {
            return from(intent.getExtras());
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    /** Read the theme extras from an extras bundle. Never throws. */
    public static ThemeInfo from(Bundle extras) {
        if (extras == null) return EMPTY;
        try {
            String themeId = extras.getString(PluginContracts.EXTRA_THEME_ID);
            boolean dark = extras.getBoolean(PluginContracts.EXTRA_THEME_DARK, false);
            boolean hasAccent = extras.containsKey(PluginContracts.EXTRA_THEME_ACCENT);
            int accent = extras.getInt(PluginContracts.EXTRA_THEME_ACCENT, 0);
            Map<String, Integer> palette =
                    readPalette(extras.getBundle(PluginContracts.EXTRA_THEME_PALETTE));
            return new ThemeInfo(themeId, dark, hasAccent, accent, palette);
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    /** Allowlisted color lookup in a raw palette bundle (never throws). */
    public static int color(Bundle palette, String name, int fallback) {
        if (palette == null) return fallback;
        try {
            String canonical = canonicalKey(name);
            if (canonical == null) return fallback;
            for (String raw : palette.keySet()) {
                if (!canonical.equals(canonicalKey(raw))) continue;
                if (!palette.containsKey(raw)) continue;
                try {
                    return palette.getInt(raw);
                } catch (ClassCastException ignored) {
                    return fallback;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /** Canonical allowlist spelling for a raw palette name, or null. */
    public static String canonicalKey(String raw) {
        return CANONICAL.get(normalize(raw));
    }

    /** True when every entry's name is allowlisted and value is an int. */
    public static boolean isPaletteValid(Map<String, Integer> colors) {
        if (colors == null) return true;
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
            if (canonicalKey(entry.getKey()) == null) return false;
            if (entry.getValue() == null) return false;
        }
        return true;
    }

    /** Build an allowlist-filtered palette bundle (canonical keys, ints only). */
    public static Bundle paletteBundle(Map<String, Integer> colors) {
        Bundle out = new Bundle();
        if (colors == null) return out;
        for (Map.Entry<String, Integer> entry : colors.entrySet()) {
            String canonical = canonicalKey(entry.getKey());
            if (canonical == null || entry.getValue() == null) continue;
            out.putInt(canonical, entry.getValue());
        }
        return out;
    }

    /** Attach host theme info to an outgoing intent (host -> plugin). */
    public static void putThemeExtras(Intent out, String themeId, boolean dark,
                                      boolean hasAccent, int accent, Bundle palette) {
        if (out == null) return;
        if (themeId != null && !themeId.isEmpty()) {
            out.putExtra(PluginContracts.EXTRA_THEME_ID, themeId);
        }
        out.putExtra(PluginContracts.EXTRA_THEME_DARK, dark);
        if (hasAccent) {
            out.putExtra(PluginContracts.EXTRA_THEME_ACCENT, accent);
        }
        if (palette != null && !palette.isEmpty()) {
            out.putExtra(PluginContracts.EXTRA_THEME_PALETTE, palette);
        }
    }

    /** Plugin -> host request: apply the theme with this id on the target intent. */
    public static void requestApplyTheme(Intent target, String themeId) {
        if (target == null || themeId == null || themeId.isEmpty()) return;
        target.putExtra(PluginContracts.EXTRA_APPLY_THEME_ID, themeId);
    }

    /** Case/separator-insensitive form (same rule as the theme parser). */
    private static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '_' || c == '-' || c == '.' || c == ' ') continue;
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static Map<String, String> buildCanonical() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : ThemePackContracts.COLOR_TOKENS) {
            map.put(normalize(key), key);
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, Integer> readPalette(Bundle bundle) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (bundle == null) return out;
        for (String raw : bundle.keySet()) {
            String canonical = canonicalKey(raw);
            if (canonical == null) continue;
            if (!bundle.containsKey(raw)) continue;
            try {
                out.put(canonical, bundle.getInt(raw));
            } catch (ClassCastException ignored) {
                // Non-int entry: ignore (allowlist contract expects ints).
            }
        }
        return Collections.unmodifiableMap(out);
    }
}

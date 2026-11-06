package io.github.abdurazaaqmohammed.core.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central registry for ThemePlugins. BaseActivity calls applySaved()
 * so switching themes only touches this registry + prefs.
 */
public final class ThemeRegistry {

    public static final String PREF_KEY = "theme_plugin_id";

    private static final Map<String, ThemePlugin> PLUGINS = new LinkedHashMap<>();
    private static boolean builtInsLoaded;

    private ThemeRegistry() {
    }

    public static synchronized void register(ThemePlugin plugin) {
        if (plugin == null || plugin.id() == null) return;
        PLUGINS.put(plugin.id(), plugin);
    }

    public static synchronized void unregister(String id) {
        PLUGINS.remove(id);
    }

    public static synchronized List<ThemePlugin> getAll() {
        ensureBuiltIns();
        return new ArrayList<>(PLUGINS.values());
    }

    public static synchronized ThemePlugin get(String id) {
        ensureBuiltIns();
        return PLUGINS.get(id);
    }

    public static String getCurrentId(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String id = prefs.getString(PREF_KEY, null);
            if (id != null) return id;
            // One-way bridge from the legacy int "theme" pref (style res id)
            // still written by MainActivity, so migrated activities stay
            // consistent until MainActivity moves to ThemeRegistry.
            if (prefs.contains("theme")) {
                try {
                    int legacy = prefs.getInt("theme", 0);
                    if (legacy == io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Light)
                        return BuiltInThemes.LIGHT_ID;
                    if (legacy == io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Dark)
                        return BuiltInThemes.DARK_ID;
                    if (legacy == io.github.abdurazaaqmohammed.MPManager.R.style.Theme_MyApp_Black)
                        return BuiltInThemes.BLACK_ID;
                } catch (Exception ignored) {
                }
            }
            return BuiltInThemes.SYSTEM_DEFAULT_ID;
        } catch (Exception e) {
            return BuiltInThemes.SYSTEM_DEFAULT_ID;
        }
    }

    public static void setCurrentId(Context context, String id) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context)
                    .edit().putString(PREF_KEY, id).apply();
        } catch (Exception ignored) {
        }
        try {
            AppCompatDelegate.setDefaultNightMode(modeFor(context, id));
        } catch (Exception ignored) {
        }
    }

    public static int modeFor(Context context, String id) {
        ThemePlugin plugin = get(id);
        if (plugin != null) return plugin.nightMode();
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    /**
     * Style res of the currently applied plugin. Lets legacy int-theme
     * readers (status-bar tinting, icon colors) stay correct after migration.
     */
    public static int currentStyleRes(Context context) {
        ThemePlugin plugin = get(getCurrentId(context));
        if (plugin != null) {
            try {
                return plugin.styleRes();
            } catch (Exception ignored) {
            }
        }
        return com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar;
    }

    /** Pref key: accent mode. Null/absent = follow material color.
     *  "preset:<name>" = one of ACCENT_PRESETS; "custom" = ARGB in ACCENT_CUSTOM_PREF_KEY. */
    public static final String ACCENT_PREF_KEY = "accent_color";
    public static final String ACCENT_CUSTOM_PREF_KEY = "accent_custom_color";

    /** Named presets shown as swatches (name -> hex, for the swatch UI). */
    public static final String[][] ACCENT_PRESETS = {
            {"Purple", "#FF6750A4"},
            {"Blue", "#FF1565C0"},
            {"Teal", "#FF00897B"},
            {"Green", "#FF2E7D32"},
            {"Amber", "#FFB26A00"},
            {"Orange", "#FFE65100"},
            {"Red", "#FFC62828"},
            {"Pink", "#FFAD1457"},
    };

    /** ARGB for a preset name, or 0 if unknown. */
    private static int presetArgb(String name) {
        for (String[] preset : ACCENT_PRESETS) {
            if (preset[0].equalsIgnoreCase(name)) {
                return (int) Long.parseLong(preset[1].substring(2), 16);
            }
        }
        return 0;
    }

    /** Generated grid: 36 hues (H0..H35, 10 degrees each) x 6 S/L shades (V0..V5). */
    private static final float[][] SHADES = {
            {0.70f, 0.42f}, {0.62f, 0.48f}, {0.50f, 0.55f},
            {0.38f, 0.62f}, {0.78f, 0.34f}, {0.28f, 0.70f},
    };

    /** Finds the generated overlay closest to the given color: exact hue
     *  bucket (10-degree steps) and nearest saturation/lightness shade. */
    private static int hueOverlayIndex(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float hue;
        float sat;
        float light = (max + min) / 2f;
        if (max == min) {
            hue = 0f;
            sat = 0f;
        } else {
            float d = max - min;
            sat = light > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) hue = ((g - b) / d + (g < b ? 6 : 0)) * 60f;
            else if (max == g) hue = ((b - r) / d + 2) * 60f;
            else hue = ((r - g) / d + 4) * 60f;
        }
        int hueIdx = Math.round(hue / 10f) % 36;
        int bestV = 0;
        float bestDist = Float.MAX_VALUE;
        for (int vi = 0; vi < SHADES.length; vi++) {
            float ds = (SHADES[vi][0] - sat) * 1.5f;
            float dl = SHADES[vi][1] - light;
            float dist = ds * ds + dl * dl;
            if (dist < bestDist) {
                bestDist = dist;
                bestV = vi;
            }
        }
        return hueIdx * SHADES.length + bestV;
    }

    /** Pref key: most-recently-used custom accent colors (JSON int array). */
    public static final String ACCENT_HISTORY_PREF_KEY = "accent_history";
    private static final int ACCENT_HISTORY_MAX = 5;

    /** Moves the color to the front of the recent-accents history (max 5). */
    public static void pushAccentHistory(Context context, int argb) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            List<Integer> history = new ArrayList<>();
            try {
                String json = prefs.getString(ACCENT_HISTORY_PREF_KEY, "[]");
                for (int value : new Gson().fromJson(json, int[].class)) {
                    history.add(value);
                }
            } catch (Exception ignored) {
            }
            history.remove(Integer.valueOf(argb));
            history.add(0, argb);
            while (history.size() > ACCENT_HISTORY_MAX) history.remove(history.size() - 1);
            int[] arr = new int[history.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = history.get(i);
            prefs.edit().putString(ACCENT_HISTORY_PREF_KEY, new Gson().toJson(arr)).apply();
        } catch (Exception ignored) {
        }
    }

    /** @return the most recently used custom accent colors (front = newest). */
    public static List<Integer> getAccentHistory(Context context) {
        List<Integer> out = new ArrayList<>();
        try {
            String json = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(ACCENT_HISTORY_PREF_KEY, "[]");
            for (int value : new Gson().fromJson(json, int[].class)) {
                out.add(value);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** Stacks the user's accent onto the base theme. Call after applySaved()
     *  and before super.onCreate() so inflation picks it up.
     *
     *  Two paths (same visual goal, different mechanics):
     *  - Devices with Material dynamic color support (API 31+, incl. Xiaomi):
     *    feed the user's color as a content-based seed to DynamicColors, which
     *    generates the full Material palette from it.
     *  - Every other device: stack the closest generated accent overlay onto
     *    the base theme with Theme.applyStyle(). The overlay only carries
     *    accent attributes and its parent is an empty overlay, so the base
     *    Dark/Black/Light mode is untouched.
     *
     *  The difference between paths doesn't affect behavior; on Black, dynamic
     *  colors are skipped so surfaces stay pure black. */
    public static void applyAccent(Activity activity) {
        try {
            String mode = PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString(ACCENT_PREF_KEY, null);
            if (mode == null || mode.isEmpty()) return; // follow material color
            int argb;
            if (mode.startsWith("preset:")) {
                argb = presetArgb(mode.substring("preset:".length()));
            } else if ("custom".equals(mode)) {
                argb = PreferenceManager.getDefaultSharedPreferences(activity)
                        .getInt(ACCENT_CUSTOM_PREF_KEY, 0);
            } else if (!"follow".equals(mode)) {
                // Bare preset name (older builds stored just the name).
                argb = presetArgb(mode);
            } else {
                return;
            }
            if (argb == 0) return;

            boolean blackTheme = BuiltInThemes.BLACK_ID.equals(getCurrentId(activity));
            if (!blackTheme && com.google.android.material.color.DynamicColors.isDynamicColorAvailable()) {
                com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(
                        activity,
                        new com.google.android.material.color.DynamicColorsOptions.Builder()
                                .setContentBasedSource(argb)
                                .build());
                return;
            }

            int idx = hueOverlayIndex(argb);
            int overlay = idx < 0 || idx >= AccentOverlays.RES.length
                    ? 0 : AccentOverlays.RES[idx];
            if (overlay != 0) {
                android.content.res.Resources.Theme theme = activity.getTheme();
                if (theme != null) {
                    theme.applyStyle(overlay, true);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Called by BaseActivity before super.onCreate(). */
    public static void applySaved(Activity activity) {
        ensureBuiltIns();
        ThemePlugin plugin;
        synchronized (ThemeRegistry.class) {
            plugin = PLUGINS.get(getCurrentId(activity));
            if (plugin == null && !PLUGINS.isEmpty()) {
                plugin = PLUGINS.values().iterator().next();
            }
        }
        if (plugin == null) return;
        try {
            activity.setTheme(plugin.styleRes());
        } catch (Exception ignored) {
        }
        try {
            AppCompatDelegate.setDefaultNightMode(plugin.nightMode());
        } catch (Exception ignored) {
        }
        try {
            plugin.apply(activity);
        } catch (Exception ignored) {
        }
    }

    private static void ensureBuiltIns() {
        if (builtInsLoaded) return;
        builtInsLoaded = true;
        try {
            BuiltInThemes.registerAll();
        } catch (Exception ignored) {
        }
    }
}

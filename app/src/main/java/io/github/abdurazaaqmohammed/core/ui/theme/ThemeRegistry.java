package io.github.abdurazaaqmohammed.core.ui.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

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

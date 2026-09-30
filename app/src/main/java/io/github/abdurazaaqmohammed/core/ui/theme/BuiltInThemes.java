package io.github.abdurazaaqmohammed.core.ui.theme;

import android.app.Activity;

import androidx.appcompat.app.AppCompatDelegate;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Bundled themes backed by styles.xml. Keeps theme choice out of
 * AndroidManifest hardcodes; manifest keeps one default, BaseActivity
 * overrides it per user selection.
 */
public final class BuiltInThemes {

    public static final String SYSTEM_DEFAULT_ID = "system_default";
    public static final String LIGHT_ID = "myapp_light";
    public static final String DARK_ID = "myapp_dark";
    public static final String BLACK_ID = "myapp_black";

    private BuiltInThemes() {
    }

    public static void registerAll() {
        ThemeRegistry.register(new Simple(SYSTEM_DEFAULT_ID, "System default",
                com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM));
        ThemeRegistry.register(new Simple(LIGHT_ID, "Light",
                R.style.Theme_MyApp_Light, AppCompatDelegate.MODE_NIGHT_NO));
        ThemeRegistry.register(new Simple(DARK_ID, "Dark",
                R.style.Theme_MyApp_Dark, AppCompatDelegate.MODE_NIGHT_YES));
        ThemeRegistry.register(new Simple(BLACK_ID, "Black",
                R.style.Theme_MyApp_Black, AppCompatDelegate.MODE_NIGHT_YES));
    }

    private static final class Simple implements ThemePlugin {
        private final String id;
        private final String name;
        private final int style;
        private final int mode;

        Simple(String id, String name, int style, int mode) {
            this.id = id;
            this.name = name;
            this.style = style;
            this.mode = mode;
        }

        @Override public String id() { return id; }
        @Override public String displayName() { return name; }
        @Override public int styleRes() { return style; }
        @Override public int nightMode() { return mode; }
        @Override public void apply(Activity activity) { }
    }
}

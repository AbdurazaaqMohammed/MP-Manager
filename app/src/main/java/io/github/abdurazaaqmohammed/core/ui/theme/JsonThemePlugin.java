package io.github.abdurazaaqmohammed.core.ui.theme;

import android.app.Activity;

import androidx.appcompat.app.AppCompatDelegate;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * ThemePlugin backed by a JSON file. Reuses the frozen interface unchanged, so
 * external dex/apk theme packs keep working.
 *
 * Colours are not applied through setTheme(): the base style stays a Material3
 * shell and the palette is pushed into ActiveTheme, which ThemeAttrs consults
 * before ?attr. Themes that declare no colors (built-in light/dark) therefore
 * keep the platform's Material You colours.
 */
public final class JsonThemePlugin implements ThemePlugin {

    private final ThemePalette palette;

    public JsonThemePlugin(ThemePalette palette) {
        this.palette = palette;
    }

    public ThemePalette palette() {
        return palette;
    }

    @Override public String id() { return palette.id(); }

    @Override public String displayName() { return palette.name(); }

    @Override
    public int styleRes() {
        String mode = palette.lightMode();
        if ("system".equals(mode)) {
            return com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar;
        }
        return palette.isLight()
                ? R.style.Theme_MyApp_Light
                : R.style.Theme_MyApp_Dark;
    }

    @Override public int nightMode() {
        String mode = palette.lightMode();
        if ("system".equals(mode)) return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        return palette.isLight()
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;
    }

    @Override
    public void apply(Activity activity) {
        ActiveTheme.set(palette);
    }
}

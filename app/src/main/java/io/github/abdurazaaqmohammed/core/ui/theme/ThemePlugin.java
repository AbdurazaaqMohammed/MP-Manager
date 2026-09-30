package io.github.abdurazaaqmohammed.core.ui.theme;

import android.app.Activity;

import androidx.annotation.StyleRes;

/**
 * Contract for freely switchable themes.
 * Bundled themes live in plugins.themes; external APK/dex packs
 * implement this same interface and are loaded via ExternalThemeLoader.
 */
public interface ThemePlugin {

    /** Stable id persisted in prefs, e.g. "myapp_dark". */
    String id();

    /** User-visible name, e.g. "Midnight". */
    String displayName();

    /** Style applied via Activity.setTheme() before super.onCreate(). */
    @StyleRes int styleRes();

    /** AppCompatDelegate night mode, e.g. MODE_NIGHT_YES/NO/FOLLOW_SYSTEM. */
    int nightMode();

    /** Hook for extra per-activity setup. Default is no-op. */
    default void apply(Activity activity) {
    }
}

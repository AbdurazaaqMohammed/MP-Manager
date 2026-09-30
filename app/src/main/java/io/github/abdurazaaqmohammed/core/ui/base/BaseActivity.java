package io.github.abdurazaaqmohammed.core.ui.base;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;

import io.github.abdurazaaqmohammed.core.ui.theme.ActiveTheme;
import io.github.abdurazaaqmohammed.core.ui.theme.PaletteInjector;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemePalette;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;

/**
 * Single place for activity-wide UI behaviour.
 * All feature activities should extend this instead of AppCompatActivity
 * so theme switching, dynamic colors and edge-to-edge change in one file.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Theme must be set before super.onCreate so inflation uses it.
        ThemeRegistry.applySaved(this);
        super.onCreate(savedInstanceState);
        // Dynamic colours only for themes that declare no palette of their own,
        // which is what makes system_default follow the wallpaper. A theme with
        // colours opts out, the way MT Manager does, so a blue wallpaper cannot
        // tint a theme that never asked to be tinted.
        if (!hasOwnPalette()) {
            try {
                DynamicColors.applyToActivityIfAvailable(this);
            } catch (Exception ignored) {
            }
        }
        // After dynamic colours, never before: Material applies its own overlay
        // and would otherwise win the ?attr/color* attributes.
        try {
            PaletteInjector.applyTo(this);
        } catch (Exception ignored) {
        }
    }

    private static boolean hasOwnPalette() {
        ThemePalette palette = ActiveTheme.get();
        return palette != null && palette.hasColors();
    }

    /**
     * Named dpPx (not dp) to avoid clashing with the legacy private dp()
     * helpers still present in migrated activities.
     */
    protected int dpPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }
}

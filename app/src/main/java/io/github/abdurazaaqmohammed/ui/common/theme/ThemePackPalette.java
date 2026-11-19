package io.github.abdurazaaqmohammed.ui.common.theme;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;

import com.google.android.material.color.ColorResourcesOverride;

import java.util.HashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;

/** Bridges exact pack colors to Material's resource-loader path (Android 11+). */
final class ThemePackPalette {
    private record Slot(String token, int color, int style) {}

    private static final Slot[] SLOTS = {
            new Slot("colorPrimary", R.color.theme_pack_primary, R.style.ThemePackPrimary),
            new Slot("colorOnPrimary", R.color.theme_pack_on_primary, R.style.ThemePackOnPrimary),
            new Slot("colorPrimaryContainer", R.color.theme_pack_primary_container, R.style.ThemePackPrimaryContainer),
            new Slot("colorOnPrimaryContainer", R.color.theme_pack_on_primary_container, R.style.ThemePackOnPrimaryContainer),
            new Slot("colorSecondary", R.color.theme_pack_secondary, R.style.ThemePackSecondary),
            new Slot("colorOnSecondary", R.color.theme_pack_on_secondary, R.style.ThemePackOnSecondary),
            new Slot("colorSecondaryContainer", R.color.theme_pack_secondary_container, R.style.ThemePackSecondaryContainer),
            new Slot("colorOnSecondaryContainer", R.color.theme_pack_on_secondary_container, R.style.ThemePackOnSecondaryContainer),
            new Slot("colorTertiary", R.color.theme_pack_tertiary, R.style.ThemePackTertiary),
            new Slot("colorOnTertiary", R.color.theme_pack_on_tertiary, R.style.ThemePackOnTertiary),
            new Slot("colorSurface", R.color.theme_pack_surface, R.style.ThemePackSurface),
            new Slot("colorOnSurface", R.color.theme_pack_on_surface, R.style.ThemePackOnSurface),
            new Slot("colorSurfaceVariant", R.color.theme_pack_surface_variant, R.style.ThemePackSurfaceVariant),
            new Slot("colorOnSurfaceVariant", R.color.theme_pack_on_surface_variant, R.style.ThemePackOnSurfaceVariant),
            new Slot("colorSurfaceContainerLowest", R.color.theme_pack_surface_container_lowest, R.style.ThemePackSurfaceContainerLowest),
            new Slot("colorSurfaceContainerLow", R.color.theme_pack_surface_container_low, R.style.ThemePackSurfaceContainerLow),
            new Slot("colorSurfaceContainer", R.color.theme_pack_surface_container, R.style.ThemePackSurfaceContainer),
            new Slot("colorSurfaceContainerHigh", R.color.theme_pack_surface_container_high, R.style.ThemePackSurfaceContainerHigh),
            new Slot("colorSurfaceContainerHighest", R.color.theme_pack_surface_container_highest, R.style.ThemePackSurfaceContainerHighest),
            new Slot("colorOutline", R.color.theme_pack_outline, R.style.ThemePackOutline),
            new Slot("colorOutlineVariant", R.color.theme_pack_outline_variant, R.style.ThemePackOutlineVariant),
            new Slot("colorError", R.color.theme_pack_error, R.style.ThemePackError),
            new Slot("colorOnError", R.color.theme_pack_on_error, R.style.ThemePackOnError),
    };

    private ThemePackPalette() {}

    @SuppressLint("RestrictedApi") // Material 1.12's resource-loader adapter; no reflection.
    static boolean apply(Activity activity, Map<String, Integer> colors, Integer accent) {
        ColorResourcesOverride override = ColorResourcesOverride.getInstance();
        if (override == null) return false;
        Map<Integer, Integer> replacements = new HashMap<>();
        for (Slot slot : SLOTS) {
            Integer color = colors.get(slot.token());
            if (color == null && "colorPrimary".equals(slot.token())) color = accent;
            if (color != null) replacements.put(slot.color(), color);
        }
        if (replacements.isEmpty()) return true;
        Resources.Theme base = activity.getResources().newTheme();
        base.setTo(activity.getTheme());
        try {
            if (!override.applyIfPossible(activity, replacements)) return false;
        } finally {
            // Material also applies its personalized-color style. Restore the base first
            // so tokens the pack did not specify keep their original values.
            activity.getTheme().setTo(base);
        }
        for (Slot slot : SLOTS) {
            if (replacements.containsKey(slot.color())) activity.getTheme().applyStyle(slot.style(), true);
        }
        return true;
    }
}

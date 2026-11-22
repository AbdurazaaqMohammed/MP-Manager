package io.github.abdurazaaqmohammed.ui.common.theme;

import android.app.Activity;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

import com.google.android.material.color.ThemePackColorLoaderBridge;

import io.github.abdurazaaqmohammed.MPManager.R;

/** Owns one host-generated exact-color loader per Resources (Android 11+). */
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
            new Slot("colorTertiaryContainer", R.color.theme_pack_tertiary_container, R.style.ThemePackTertiaryContainer),
            new Slot("colorOnTertiaryContainer", R.color.theme_pack_on_tertiary_container, R.style.ThemePackOnTertiaryContainer),
            new Slot("colorErrorContainer", R.color.theme_pack_error_container, R.style.ThemePackErrorContainer),
            new Slot("colorOnErrorContainer", R.color.theme_pack_on_error_container, R.style.ThemePackOnErrorContainer),
            new Slot("colorSurfaceInverse", R.color.theme_pack_surface_inverse, R.style.ThemePackSurfaceInverse),
            new Slot("colorOnSurfaceInverse", R.color.theme_pack_on_surface_inverse, R.style.ThemePackOnSurfaceInverse),
            new Slot("colorPrimaryInverse", R.color.theme_pack_primary_inverse, R.style.ThemePackPrimaryInverse),
            new Slot("colorSurfaceDim", R.color.theme_pack_surface_dim, R.style.ThemePackSurfaceDim),
            new Slot("colorSurfaceBright", R.color.theme_pack_surface_bright, R.style.ThemePackSurfaceBright),
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

    /** Per-Resources ownership state (pure, JVM-tested). */
    private static final ThemePackPaletteState STATE = new ThemePackPaletteState();

    private ThemePackPalette() {}

    /**
     * Installs (or refreshes) the pack color overrides on the activity and
     * re-applies the ThemePack* slot styles. The same palette on the same
     * Resources re-applies styles WITHOUT creating a new loader; a changed
     * palette replaces the owned loader (previous one uninstalled first);
     * an empty palette uninstalls it. Returns false when the override cannot
     * be provided (pre-R or factory failure) — the caller keeps its base
     * theme.
     */
    static boolean apply(Activity activity, Map<String, Integer> colors, Integer accent) {
        Map<Integer, Integer> replacements = buildReplacements(colors, accent);
        Resources resources = activity.getResources();
        if (STATE.isCurrent(resources, replacements)) {
            // Same palette: overrides already installed, no new loader.
            applySlotStyles(activity, replacements);
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return replacements.isEmpty();
        }
        uninstallStale(resources, STATE.previous(resources));
        if (replacements.isEmpty()) return true;
        ResourcesLoader loader = ThemePackColorLoaderBridge.create(activity, replacements);
        if (loader == null) return false;
        try {
            resources.addLoaders(loader);
        } catch (Throwable t) {
            return false;
        }
        STATE.adopted(resources, loader, replacements);
        applySlotStyles(activity, replacements);
        return true;
    }

    /**
     * Removes the pack-owned override loader from this activity's resources.
     * Call when no pack is active (the caller re-applies its base theme
     * styles afterwards). Loaders owned by anything else are never touched.
     */
    public static void clear(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        Resources resources = activity.getResources();
        uninstallStale(resources, STATE.previous(resources));
    }

    @androidx.annotation.RequiresApi(30)
    private static void uninstallStale(Resources resources, Object stale) {
        if (stale instanceof ResourcesLoader) {
            Loaders.uninstall(resources, (ResourcesLoader) stale);
        }
    }

    private static Map<Integer, Integer> buildReplacements(Map<String, Integer> colors,
                                                           Integer accent) {
        Map<Integer, Integer> replacements = new HashMap<>();
        for (Slot slot : SLOTS) {
            Integer color = colors.get(slot.token());
            if (color == null && "colorPrimary".equals(slot.token())) color = accent;
            if (color != null) replacements.put(slot.color(), color);
        }
        return replacements;
    }

    private static void applySlotStyles(Activity activity,
                                        Map<Integer, Integer> replacements) {
        for (Slot slot : SLOTS) {
            if (replacements.containsKey(slot.color())) {
                activity.getTheme().applyStyle(slot.style(), true);
            }
        }
    }

    /** All ResourcesLoader touchpoints, only invoked on API 30+. */
    @androidx.annotation.RequiresApi(30)
    private static final class Loaders {
        /** Unregisters the loader; provider FDs are released by GC
         *  finalization (never force-closed while possibly registered). */
        static void uninstall(Resources resources, ResourcesLoader loader) {
            try {
                resources.removeLoaders(loader);
            } catch (Throwable ignored) {
            }
        }
    }
}

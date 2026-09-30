package io.github.abdurazaaqmohammed.core.ui.theme;

import android.app.Activity;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.util.ThemeAttrs;

/**
 * Makes a JSON theme's palette reach every {@code ?attr} in the app, not just the
 * handful of call sites that go through {@link ThemeAttrs}.
 *
 * <p>Why this is needed: the base style stays a Material3 shell and
 * {@code DynamicColors.applyToActivityIfAvailable()} still runs, so layouts that
 * reference {@code ?attr/colorSurface} keep resolving to the wallpaper-derived
 * palette no matter what the JSON says. There is no public API to write an
 * arbitrary attribute onto a live {@link android.content.res.Resources.Theme}, so
 * the palette is injected the same way Material injects harmonized colours: build
 * a small resource table, hand it to a {@link ResourcesLoader}, then apply
 * {@code ThemeOverlay.App.Palette} with {@code force=true} so the Material
 * attributes point at the overridden colours.
 *
 * <p>Ordering matters: the overlay must be applied <em>after</em> dynamic colours,
 * otherwise Material's own overlay wins.
 *
 * <p>Every built-in and imported theme goes through this one path, so a theme
 * needs no generated XML and adding one costs nothing.
 *
 * <p>API 30+ only ({@code addLoaders} and {@code memfd_create}). Below that the
 * overlay is simply not applied and the {@link ThemeAttrs} call sites keep using
 * the palette directly, so Material You still works.
 */
public final class PaletteInjector {

    private static final String TAG = "PaletteInjector";

    /**
     * Palette ids already pushed into a loader. Loaders cannot be removed and the
     * most recently added one has priority, so this only has to stop us rebuilding
     * the table for every activity creation.
     */
    private static String injectedId;

    private PaletteInjector() {
    }

    /**
     * Overlay slots: the placeholder colour each one overrides, the JSON role that
     * feeds it, and the attribute its fallback is read from when the theme does not
     * declare that role. The fallback is what lets a partial theme keep Material You
     * for the roles it leaves out instead of being flattened to the placeholders.
     */
    private static final int[] SLOT_COLORS = {
            R.color.mp_background, R.color.mp_surface, R.color.mp_surface_variant,
            R.color.mp_surface_container, R.color.mp_toolbar, R.color.mp_on_surface,
            R.color.mp_on_surface_variant, R.color.mp_outline, R.color.mp_primary,
            R.color.mp_on_primary, R.color.mp_error
    };

    private static final String[] SLOT_ROLES = {
            "background", "surface", "surfaceVariant", "surfaceContainer", "toolbar",
            "onSurface", "onSurfaceVariant", "outline", "primary", "onPrimary", "error"
    };

    private static final int[] SLOT_FALLBACK_ATTRS = {
            android.R.attr.colorBackground,
            com.google.android.material.R.attr.colorSurface,
            com.google.android.material.R.attr.colorSurfaceVariant,
            com.google.android.material.R.attr.colorSurfaceContainer,
            com.google.android.material.R.attr.colorSurface,
            com.google.android.material.R.attr.colorOnSurface,
            com.google.android.material.R.attr.colorOnSurfaceVariant,
            com.google.android.material.R.attr.colorOutlineVariant,
            com.google.android.material.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorOnPrimary,
            com.google.android.material.R.attr.colorError
    };

    /**
     * Call after dynamic colours have been applied. No-op when the active theme
     * declares no colours (built-in light/dark keep Material You) or when the
     * platform is too old to support resource loaders.
     */
    public static void applyTo(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;

        ThemePalette palette = ActiveTheme.get();
        if (palette == null || !palette.hasColors()) return;

        try {
            if (!palette.id().equals(injectedId)) {
                Resources resources = activity.getResources();
                byte[] table = buildTable(activity, palette, resources);
                if (table.length == 0) return;
                addLoader(resources, table);
                injectedId = palette.id();
            }
            activity.getTheme().applyStyle(R.style.ThemeOverlay_App_Palette, true);
        } catch (Throwable t) {
            // A failed injection must never take the activity down with it; the
            // ThemeAttrs call sites still resolve the palette on their own.
            Log.w(TAG, "palette injection failed, keeping Material You", t);
        }
    }

    /**
     * Resolves every slot to a concrete ARGB -- the theme's value where declared,
     * otherwise the value the attribute currently resolves to -- and writes them
     * into a resource table.
     */
    private static byte[] buildTable(Activity activity, ThemePalette palette, Resources resources) {
        List<ArscColorTable.Entry> entries = new ArrayList<>(SLOT_COLORS.length);
        int typeId = -1;
        int packageId = -1;
        String packageName = null;

        for (int i = 0; i < SLOT_COLORS.length; i++) {
            int resId = SLOT_COLORS[i];
            int fromJson = palette.color(SLOT_ROLES[i]);
            int argb = fromJson != ThemePalette.ABSENT
                    ? fromJson
                    : ThemeAttrs.resolve(activity, SLOT_FALLBACK_ATTRS[i], 0);
            if (argb == 0) continue;

            if (typeId < 0) {
                // The overlay has to reuse the app's own colour type id, otherwise
                // the entry ids below would not line up with R.color.
                int anyId = resId;
                typeId = (anyId >> 16) & 0xFF;
                packageId = (anyId >> 24) & 0xFF;
                packageName = activity.getPackageName();
            }
            entries.add(new ArscColorTable.Entry(
                    resId & 0xFFFF, resources.getResourceEntryName(resId), argb));
        }

        if (entries.isEmpty()) return new byte[0];
        return ArscColorTable.build(packageId, packageName, typeId,
                entries.toArray(new ArscColorTable.Entry[0]));
    }

    private static void addLoader(Resources resources, byte[] table) throws Exception {
        ResourcesLoader loader = new ResourcesLoader();
        java.io.FileDescriptor fd = android.system.Os.memfd_create("mp_palette.arsc", 0);
        if (fd == null) return;
        try {
            try (OutputStream out = new FileOutputStream(fd)) {
                out.write(table);
            }
            try (ParcelFileDescriptor pfd = ParcelFileDescriptor.dup(fd)) {
                loader.addProvider(ResourcesProvider.loadFromTable(pfd, null));
            }
        } finally {
            android.system.Os.close(fd);
        }
        resources.addLoaders(loader);
    }

    /** Test seam: forget which palette was injected so the next call re-injects. */
    static void resetForTest() {
        injectedId = null;
    }
}

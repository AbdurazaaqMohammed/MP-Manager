package io.github.abdurazaaqmohammed.core.ui.theme;

import android.content.Context;

import io.github.abdurazaaqmohammed.core.ui.util.ThemeAttrs;

/**
 * Process-wide handle on the palette of the currently applied theme.
 *
 * Why this exists: {@link ThemeAttrs} is called from Services and floating
 * windows, which have no Activity and therefore cannot read the selection from
 * prefs on every call. ThemeRegistry installs the palette here once per
 * activity creation, and ThemeAttrs reads it before falling back to ?attr.
 */
public final class ActiveTheme {

    private static volatile ThemePalette palette;

    private ActiveTheme() {
    }

    public static void set(ThemePalette p) {
        palette = p;
    }

    public static ThemePalette get() {
        return palette;
    }

    /**
     * @return ARGB for a colour role, or {@link ThemePalette#ABSENT} when the
     * active theme does not declare it -> caller falls back to ?attr.
     */
    public static int role(String role, Context context) {
        ThemePalette p = palette;
        if (p == null) {
            p = resolveFromPrefs(context);
            if (p != null) palette = p;
        }
        return p == null ? ThemePalette.ABSENT : p.color(role);
    }

    /**
     * Whether the active theme is a light one. Drives status bar icon colour,
     * file icon tinting and the editor's syntax theme choice; all three used to
     * compare the raw theme id against BuiltInThemes.LIGHT_ID, which silently
     * mis-resolved for any imported theme.
     */
    public static boolean isLight(Context context) {
        ThemePalette p = palette;
        if (p == null) {
            p = resolveFromPrefs(context);
            if (p != null) palette = p;
        }
        if (p != null) return p.isLight();
        if (context == null) return false;
        try {
            return !isNight(context, ThemeRegistry.getCurrentId(context));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isNight(Context context, String id) {
        return BuiltInThemes.DARK_ID.equals(id)
                || BuiltInThemes.BLACK_ID.equals(id)
                || BuiltInThemes.MT_DARK_ID.equals(id);
    }

    private static ThemePalette resolveFromPrefs(Context context) {
        if (context == null) return null;
        try {
            return ThemeStore.byId(context, ThemeRegistry.getCurrentId(context));
        } catch (Exception e) {
            return null;
        }
    }

    /** Palette-aware wrapper for callers outside ThemeAttrs. */
    public static int surface(Context context) {
        int v = role("surface", context);
        return v != ThemePalette.ABSENT ? v : ThemeAttrs.surface(context);
    }
}

package io.github.abdurazaaqmohammed.core.ui.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.annotation.AttrRes;

import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;

/**
 * Resolve theme attributes instead of hardcoding Color.WHITE / Color.TRANSPARENT
 * (see legacy UIHelper.getTitle/styleEditText). One place to change surface colors.
 */
public final class ThemeAttrs {

    private ThemeAttrs() {
    }

    /** Wrap non-Activity contexts (Service, floating windows) with the current theme. */
    public static Context themed(Context c) {
        if (c instanceof Activity) return c;
        try {
            return new ContextThemeWrapper(c, ThemeRegistry.currentStyleRes(c));
        } catch (Exception e) {
            return c;
        }
    }

    public static int resolve(Context context, @AttrRes int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                return tv.data != 0 ? tv.data : fallback;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    // Role names match the keys accepted by assets/ui_themes/*.json.
    private static final String R_BACKGROUND = "background";
    private static final String R_SURFACE = "surface";
    private static final String R_ON_SURFACE = "onSurface";
    private static final String R_PRIMARY = "primary";
    private static final String R_ON_PRIMARY = "onPrimary";
    private static final String R_ON_SURFACE_VARIANT = "onSurfaceVariant";
    private static final String R_SURFACE_VARIANT = "surfaceVariant";
    private static final String R_OUTLINE = "outline";
    private static final String R_ERROR = "error";
    private static final String R_TOOLBAR = "toolbar";
    private static final String R_SURFACE_CONTAINER = "surfaceContainer";
    private static final String R_SELECTION = "selection";
    private static final String R_ICON_FOLDER = "iconFolder";
    private static final String R_ICON_FILE = "iconFile";

    /** MT Manager's file-list badge colour, used when a theme omits it. */
    private static final int MT_ICON_BADGE = 0xFF151515;

    /**
     * Palette value of the active JSON theme, or
     * {@link io.github.abdurazaaqmohammed.core.ui.theme.ThemePalette#ABSENT}
     * when it declares no colours (built-in light/dark) so the caller keeps
     * the platform Material You colours.
     */
    private static int palette(Context context, String role) {
        try {
            return io.github.abdurazaaqmohammed.core.ui.theme.ActiveTheme.role(role, context);
        } catch (Exception ignored) {
            return io.github.abdurazaaqmohammed.core.ui.theme.ThemePalette.ABSENT;
        }
    }

    private static int pick(Context context, String role, @AttrRes int attr, int fallback) {
        int fromJson = palette(context, role);
        if (fromJson != io.github.abdurazaaqmohammed.core.ui.theme.ThemePalette.ABSENT) {
            return fromJson;
        }
        return resolve(themed(context), attr, fallback);
    }

    /**
     * For roles that have no Material attribute to fall back to, such as the
     * file-list badge colours, which are MT Manager's own flat greys.
     */
    private static int pick(Context context, String role, int fallback) {
        int fromJson = palette(context, role);
        if (fromJson != io.github.abdurazaaqmohammed.core.ui.theme.ThemePalette.ABSENT) {
            return fromJson;
        }
        return fallback;
    }

    public static int onSurface(Context context) {
        return pick(context, R_ON_SURFACE,
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
    }

    public static int surface(Context context) {
        return pick(context, R_SURFACE,
                com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT);
    }

    public static int background(Context context) {
        return pick(context, R_BACKGROUND,
                android.R.attr.colorBackground, Color.TRANSPARENT);
    }

    public static int accent(Context context) {
        return pick(context, R_PRIMARY,
                com.google.android.material.R.attr.colorPrimary, Color.BLACK);
    }

    public static int onPrimary(Context context) {
        return pick(context, R_ON_PRIMARY,
                com.google.android.material.R.attr.colorOnPrimary, Color.WHITE);
    }

    public static int onSurfaceVariant(Context context) {
        return pick(context, R_ON_SURFACE_VARIANT,
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.BLACK);
    }

    public static int surfaceVariant(Context context) {
        return pick(context, R_SURFACE_VARIANT,
                com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY);
    }

    public static int outline(Context context) {
        return pick(context, R_OUTLINE,
                com.google.android.material.R.attr.colorOutline, Color.GRAY);
    }

    public static int error(Context context) {
        return pick(context, R_ERROR,
                com.google.android.material.R.attr.colorError, Color.RED);
    }

    /**
     * Toolbar / status bar background. Material has no such role, so this falls
     * back to the surface rather than pretending there is a system attribute.
     */
    public static int toolbar(Context context) {
        return pick(context, R_TOOLBAR,
                com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT);
    }

    /** Raised container background: dialogs, panels, FABs. */
    public static int surfaceContainer(Context context) {
        return pick(context, R_SURFACE_CONTAINER,
                com.google.android.material.R.attr.colorSurfaceContainer, Color.LTGRAY);
    }

    /**
     * Selected-row background. Deliberately not tied to a Material attribute:
     * selection is drawn by the adapters, and MT Manager's selection is a
     * different hue from its grey surfaces.
     */
    public static int selection(Context context) {
        int fromJson = palette(context, R_SELECTION);
        if (fromJson != io.github.abdurazaaqmohammed.core.ui.theme.ThemePalette.ABSENT) {
            return fromJson;
        }
        return pick(context, R_SURFACE_CONTAINER,
                com.google.android.material.R.attr.colorSurfaceContainer, Color.LTGRAY);
    }

    /**
     * MT Manager draws every file-list badge the same flat dark grey with a
     * grey glyph on top; only the glyph shape tells the types apart.
     */
    public static int iconFolder(Context context) {
        return pick(context, R_ICON_FOLDER, MT_ICON_BADGE);
    }

    public static int iconFile(Context context) {
        return pick(context, R_ICON_FILE, MT_ICON_BADGE);
    }
}

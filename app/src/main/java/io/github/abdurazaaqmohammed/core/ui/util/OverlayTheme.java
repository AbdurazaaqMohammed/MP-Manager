package io.github.abdurazaaqmohammed.core.ui.util;

import android.content.Context;

/**
 * Color palette for Services / floating windows. Resolved once via ThemeAttrs
 * (which wraps the context with the current theme) and cached; no setters.
 */
public final class OverlayTheme {

    private final int bg;
    private final int fg;
    private final int accent;
    private final int secondaryBg;
    private final int outline;

    private OverlayTheme(Context c) {
        this.bg = ThemeAttrs.surface(c);
        this.fg = ThemeAttrs.onSurface(c);
        this.accent = ThemeAttrs.accent(c);
        this.secondaryBg = ThemeAttrs.surfaceVariant(c);
        this.outline = ThemeAttrs.outline(c);
    }

    public static OverlayTheme get(Context c) {
        return new OverlayTheme(ThemeAttrs.themed(c));
    }

    public int bg() {
        return bg;
    }

    public int fg() {
        return fg;
    }

    public int accent() {
        return accent;
    }

    public int secondaryBg() {
        return secondaryBg;
    }

    public int outline() {
        return outline;
    }
}

package io.github.abdurazaaqmohammed.ui.common.theme;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Direct references to the preset accent overlay styles (pre-Android 11 and
 * fallback) so resource shrinking keeps them even when selected at runtime.
 * Order matches {@link ThemeRegistry#ACCENT_PRESETS}.
 */
final class AccentOverlays {
    static final int[] RES = {
            R.style.ThemeOverlay_Accent_Purple,
            R.style.ThemeOverlay_Accent_Blue,
            R.style.ThemeOverlay_Accent_Teal,
            R.style.ThemeOverlay_Accent_Green,
            R.style.ThemeOverlay_Accent_Amber,
            R.style.ThemeOverlay_Accent_Orange,
            R.style.ThemeOverlay_Accent_Red,
            R.style.ThemeOverlay_Accent_Pink,
    };

    private AccentOverlays() { }
}

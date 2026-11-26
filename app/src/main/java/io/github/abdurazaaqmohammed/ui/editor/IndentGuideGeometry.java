package io.github.abdurazaaqmohammed.ui.editor;

/**
 * Pure geometry and color-alpha helpers for drawing vertical indentation guides
 * across wrapped editor rows without Android UI framework dependencies.
 */
public final class IndentGuideGeometry {

    private IndentGuideGeometry() {
    }

    /** Returns whether {@code [startLine, endLine]} overlaps {@code [firstVisibleLine, lastVisibleLine]}. */
    public static boolean isBlockVisible(int startLine, int endLine, int firstVisibleLine, int lastVisibleLine) {
        return endLine >= firstVisibleLine && startLine <= lastVisibleLine;
    }

    /** Clamps {@code value} into {@code [min, max]}, returning {@code min} when {@code max < min}. */
    public static int clampIndex(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Softens fully opaque guide colors slightly in word-wrap mode so wrapped
     * continuation text remains readable underneath vertical guide bars.
     */
    public static int softenWrappedGuideColor(int argb) {
        if ((argb >>> 24) == 0xFF) {
            return (0xCC << 24) | (argb & 0x00FFFFFF);
        }
        return argb;
    }

    /** Computes the top viewport Y coordinate for a vertical block guide segment. */
    public static float resolveGuideTop(float startLineBottomY, int offsetY) {
        return Math.max(0f, startLineBottomY - offsetY);
    }

    /** Computes the bottom viewport Y coordinate for a vertical block guide segment. */
    public static float resolveGuideBottom(float endTargetY, int offsetY, int viewHeight) {
        return Math.min((float) viewHeight, endTargetY - offsetY);
    }
}

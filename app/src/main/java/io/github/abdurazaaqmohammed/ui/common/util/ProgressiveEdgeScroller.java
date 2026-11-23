package io.github.abdurazaaqmohammed.ui.common.util;

/**
 * Utility for calculating progressive edge scrolling speeds during drag-and-drop operations.
 * Starts with low speed for precision and quadratically accelerates the longer the drag is held at the edge.
 */
public final class ProgressiveEdgeScroller {

    private static final int MIN_VELOCITY = 4;
    private static final int MAX_VELOCITY = 48;
    private static final long ACCELERATION_RAMP_MS = 1200L;

    private ProgressiveEdgeScroller() {}

    /**
     * Drop-in replacement for {@code ItemTouchHelper.Callback#interpolateOutOfBoundsScroll}.
     */
    public static int interpolateOutOfBoundsScroll(
            int viewSize,
            int viewSizeOutOfBounds,
            int totalSize,
            long msSinceStartScroll
    ) {
        if (viewSizeOutOfBounds == 0 || viewSize <= 0) return 0;

        int direction = (int) Math.signum(viewSizeOutOfBounds);
        int outOfBoundsAbs = Math.abs(viewSizeOutOfBounds);
        float depthFraction = Math.min(1.0f, (float) outOfBoundsAbs / Math.max(1, viewSize));

        float timeFraction = Math.min(1.0f, (float) msSinceStartScroll / ACCELERATION_RAMP_MS);
        // Quadratic acceleration curve: starts gentle, builds smoothly
        float acceleration = timeFraction * timeFraction;

        float speed = MIN_VELOCITY + (MAX_VELOCITY - MIN_VELOCITY) * (0.3f * depthFraction + 0.7f * acceleration);
        int delta = Math.max(1, Math.round(speed)) * direction;
        return delta;
    }

    /**
     * Calculates step delta for custom horizontal/vertical drag views.
     *
     * @param pos Current pointer coordinate along the scroll axis.
     * @param minBound Starting boundary of viewport (e.g. 0).
     * @param maxBound Ending boundary of viewport (e.g. width or height).
     * @param edgeThreshold Distance from boundary that activates edge scrolling.
     * @param msAtEdge How long the pointer has stayed within the edge zone in milliseconds.
     * @return Positive value to scroll forward/right/down, negative for backward/left/up, or 0.
     */
    public static int computeScrollStep(
            float pos,
            float minBound,
            float maxBound,
            float edgeThreshold,
            long msAtEdge
    ) {
        if (maxBound <= minBound || edgeThreshold <= 0) return 0;

        if (pos <= minBound + edgeThreshold) {
            float dist = Math.max(0, (minBound + edgeThreshold) - pos);
            return interpolateOutOfBoundsScroll((int) edgeThreshold, -(int) Math.max(1, dist), (int) (maxBound - minBound), msAtEdge);
        } else if (pos >= maxBound - edgeThreshold) {
            float dist = Math.max(0, pos - (maxBound - edgeThreshold));
            return interpolateOutOfBoundsScroll((int) edgeThreshold, (int) Math.max(1, dist), (int) (maxBound - minBound), msAtEdge);
        }
        return 0;
    }
}

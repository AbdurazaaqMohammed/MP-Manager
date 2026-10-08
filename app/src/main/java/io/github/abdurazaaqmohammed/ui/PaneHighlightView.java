package io.github.abdurazaaqmohammed.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.color.MaterialColors;

/**
 * Draws the "selected pane" highlight for the dual-pane file list. Two instances
 * are stacked around the pane row: a fill layer placed BEHIND the lists (so the
 * background tint never covers file names) and an overlay layer placed on top
 * (underline / border). Both are driven from the same animator fraction so the
 * highlight visibly slides from one pane to the other when selection changes.
 *
 * All transition geometry is derived from {@code pos} (0 = pane 1, 1 = pane 2),
 * so interrupting an animation mid-way stays visually continuous.
 */
public class PaneHighlightView extends View {

    public static final int MODE_NONE = 0;
    public static final int MODE_BACKGROUND = 1;
    public static final int MODE_UNDERLINE = 2;
    public static final int MODE_BORDER = 3;
    public static final int MODE_BACKGROUND_UNDERLINE = 4;

    public static final String PREF_KEY = "pane_highlight_style";
    public static final String DEFAULT_STYLE = "underline";

    private static final Interpolator EASE = new FastOutSlowInInterpolator();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean fillLayer;
    private final int accentColor;
    private final int fillColor;
    private final float underlineHeight;
    private final float borderStroke;
    private final float cornerRadius;

    private View row, pane1, pane2;
    private int mode = MODE_NONE;
    /** Current drawn position: 0 = pane 1, 1 = pane 2. */
    private float pos;
    /** -1 transitioning to pane 1, 1 to pane 2, 0 idle. */
    private int direction;
    private boolean inTransition;
    private float underlineLeftT, underlineRightT;
    private float foldCollapse, foldExpand;

    public PaneHighlightView(Context context, boolean fillLayer) {
        super(context);
        this.fillLayer = fillLayer;
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
        float density = context.getResources().getDisplayMetrics().density;
        underlineHeight = 3 * density;
        borderStroke = 2 * density;
        cornerRadius = 10 * density;
        fillColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh, 0x33808080);
        accentColor = MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, 0xFF607D8B);
    }

    public static int modeFromString(String value) {
        if ("background".equals(value)) return MODE_BACKGROUND;
        if ("border".equals(value)) return MODE_BORDER;
        if ("background_underline".equals(value)) return MODE_BACKGROUND_UNDERLINE;
        if ("none".equals(value)) return MODE_NONE;
        return MODE_UNDERLINE;
    }

    public void setTargets(View row, View pane1, View pane2) {
        this.row = row;
        this.pane1 = pane1;
        this.pane2 = pane2;
        invalidate();
    }

    public void setMode(int mode) {
        this.mode = mode;
        // GONE when this layer has nothing to draw: avoids needless overdraw.
        setVisibility(hasContent() ? VISIBLE : GONE);
        invalidate();
    }

    private boolean hasContent() {
        if (fillLayer) return mode == MODE_BACKGROUND || mode == MODE_BACKGROUND_UNDERLINE;
        return mode == MODE_UNDERLINE || mode == MODE_BORDER || mode == MODE_BACKGROUND_UNDERLINE;
    }

    public float getPos() {
        return pos;
    }

    public void snapTo(float target) {
        pos = target;
        inTransition = false;
        direction = 0;
        updateDerived();
        invalidate();
    }

    /**
     * Renders one frame of a transition from {@code start} to {@code end}
     * (both 0/1 pane positions) at raw linear animator fraction {@code f};
     * easing is applied here so every visual effect derives from one eased
     * {@code pos} and stays continuous when the animator is interrupted.
     */
    public void setTransition(float start, float end, float f) {
        direction = end > start ? 1 : end < start ? -1 : 0;
        pos = lerp(start, end, EASE.getInterpolation(f));
        inTransition = direction != 0;
        updateDerived();
        invalidate();
    }

    private void updateDerived() {
        // Underline: leading edge leaves immediately, trailing edge follows
        // after a 20% delay — tab-indicator style stretch. Moving right, pos
        // runs 0->1 so the left edge lags; moving left, pos runs 1->0 so the
        // RIGHT edge lags (its fraction is mirrored). Both cases settle exactly
        // on the selected pane with no jump at either end.
        if (direction > 0) {
            underlineLeftT = clamp01((pos - 0.2f) / 0.8f);
            underlineRightT = pos;
        } else if (direction < 0) {
            underlineLeftT = pos;
            underlineRightT = clamp01(pos / 0.8f);
        } else {
            underlineLeftT = pos;
            underlineRightT = pos;
        }
        // Border folds through the pane boundary: source border collapses into
        // the middle line during the first half, target unfolds out of it in
        // the second half, so no line ever sweeps across the file list. The
        // fold is driven by distance traveled (pos mirrored when moving left)
        // so it plays in the same order in both directions.
        float travel = direction < 0 ? 1f - pos : pos;
        foldCollapse = EASE.getInterpolation(clamp01(travel * 2f));
        foldExpand = EASE.getInterpolation(clamp01(travel * 2f - 1f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mode == MODE_NONE || row == null || pane1 == null || pane2 == null
                || pane1.getRight() <= pane1.getLeft()) return;
        // Views share the RelativeLayout parent with the pane row but the panes
        // are children of the row; align coordinates.
        float ox = row.getLeft() - getLeft();
        float oy = row.getTop() - getTop();
        float l1 = pane1.getLeft() + ox, r1 = pane1.getRight() + ox;
        float l2 = pane2.getLeft() + ox, r2 = pane2.getRight() + ox;
        float top = pane1.getTop() + oy, bottom = pane1.getBottom() + oy;

        if (fillLayer) {
            if (mode == MODE_BACKGROUND || mode == MODE_BACKGROUND_UNDERLINE) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fillColor);
                rect.set(lerp(l1, l2, pos), top, lerp(r1, r2, pos), bottom);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            }
            return;
        }

        if (mode == MODE_UNDERLINE || mode == MODE_BACKGROUND_UNDERLINE) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accentColor);
            rect.set(lerp(l1, l2, underlineLeftT), bottom - underlineHeight,
                    lerp(r1, r2, underlineRightT), bottom);
            canvas.drawRoundRect(rect, underlineHeight / 2f, underlineHeight / 2f, paint);
        } else if (mode == MODE_BORDER) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(borderStroke);
            paint.setColor(accentColor);
            float inset = borderStroke / 2f;
            if (inTransition) {
                drawBorderFold(canvas, l1, r1, l2, r2, top, bottom, inset);
            } else {
                rect.set(lerp(l1, l2, pos) + inset, top + inset,
                        lerp(r1, r2, pos) - inset, bottom - inset);
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            }
        }
    }

    private void drawBorderFold(Canvas canvas, float l1, float r1, float l2, float r2,
                                float top, float bottom, float inset) {
        float middle = (r1 + l2) / 2f;
        boolean toRight = direction > 0;
        // Collapse the source border into the middle line, fading as it travels.
        paint.setAlpha((int) (255 * (1f - foldCollapse)));
        rect.set((toRight ? lerp(l1, middle, foldCollapse) : middle) + inset, top + inset,
                (toRight ? middle : lerp(r2, middle, foldCollapse)) - inset, bottom - inset);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        // Unfold the target border out of the middle line, fading in.
        paint.setAlpha((int) (255 * foldExpand));
        rect.set((toRight ? middle : lerp(middle, l1, foldExpand)) + inset, top + inset,
                (toRight ? lerp(middle, r2, foldExpand) : middle) - inset, bottom - inset);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        paint.setAlpha(255);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}

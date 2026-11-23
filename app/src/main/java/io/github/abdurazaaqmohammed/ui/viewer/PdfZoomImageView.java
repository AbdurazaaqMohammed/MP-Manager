package io.github.abdurazaaqmohammed.ui.viewer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * Dedicated high-performance PDF page view supporting:
 * - Edge-to-edge fit-width rendering (no side letterboxing).
 * - Smooth two-finger pinch-to-zoom in and out (1x to 8x).
 * - Double-tap zoom toggle (1x <-> 2.5x).
 * - Smooth vertical scrolling for tall pages at 1x fit-width.
 * - Free panning when zoomed in.
 * - Seamless parent ViewPager2 touch coordination.
 * - Hardware-accelerated inverted dark mode color filtering.
 */
public class PdfZoomImageView extends AppCompatImageView {

    public interface ScaleChangeListener {
        void onScaleChanged(float scale);
    }

    private static final float MIN_SCALE = 1.0f;
    private static final float MAX_SCALE = 8.0f;
    private static final float DOUBLE_TAP_TARGET_SCALE = 2.5f;

    private static final ColorMatrixColorFilter INVERT_FILTER = new ColorMatrixColorFilter(new float[] {
        -1.0f,  0.0f,  0.0f, 0.0f, 255.0f,
         0.0f, -1.0f,  0.0f, 0.0f, 255.0f,
         0.0f,  0.0f, -1.0f, 0.0f, 255.0f,
         0.0f,  0.0f,  0.0f, 1.0f,   0.0f
    });

    private final Matrix baseMatrix = new Matrix();
    private final Matrix suppMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();
    private final float[] matrixValues = new float[9];

    private final PointF lastTouch = new PointF();
    private final PointF touchDown = new PointF();
    private boolean isDragging = false;

    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private ValueAnimator zoomAnimator;
    private ScaleChangeListener scaleChangeListener;

    private boolean isNightMode = false;

    public PdfZoomImageView(@NonNull Context context) {
        super(context);
        init(context);
    }

    public PdfZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public PdfZoomImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ImageView.ScaleType.MATRIX);
        setClickable(true);
        setFocusable(true);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
                cancelZoomAnimation();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }

            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                float current = getCurrentScale();
                if (current * factor > MAX_SCALE) {
                    factor = MAX_SCALE / current;
                } else if (current * factor < 0.75f) {
                    factor = 0.75f / current;
                }
                suppMatrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                clampAndApplyMatrix();
                notifyScaleChanged();
                return true;
            }

            @Override
            public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
                float current = getCurrentScale();
                if (current < MIN_SCALE) {
                    animateScale(current, MIN_SCALE, detector.getFocusX(), detector.getFocusY());
                }
            }
        });

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                performClick();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                float current = getCurrentScale();
                float target = (current > 1.3f) ? MIN_SCALE : DOUBLE_TAP_TARGET_SCALE;
                animateScale(current, target, e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setScaleChangeListener(ScaleChangeListener listener) {
        this.scaleChangeListener = listener;
    }

    public void setNightMode(boolean night) {
        this.isNightMode = night;
        if (night) {
            setColorFilter(INVERT_FILTER);
        } else {
            clearColorFilter();
        }
        invalidate();
    }

    public boolean isNightMode() {
        return isNightMode;
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        updateBaseMatrix();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        updateBaseMatrix();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBaseMatrix();
    }

    /**
     * Calculates the baseline matrix to ensure the PDF page spans 100% of the viewport width
     * (fit-width) with zero lateral letterboxing, top-aligned when taller than the viewport.
     */
    public void updateBaseMatrix() {
        cancelZoomAnimation();
        Drawable d = getDrawable();
        int vw = getWidth();
        int vh = getHeight();
        if (d == null || vw <= 0 || vh <= 0) return;

        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;

        baseMatrix.reset();
        // Fit width edge-to-edge
        float scale = (float) vw / (float) dw;
        baseMatrix.postScale(scale, scale);

        float scaledHeight = dh * scale;
        // If content fits vertically, center it; otherwise top-align (y = 0)
        float translateY = (scaledHeight <= vh) ? (vh - scaledHeight) / 2f : 0f;
        baseMatrix.postTranslate(0f, translateY);

        suppMatrix.reset();
        clampAndApplyMatrix();
        notifyScaleChanged();
    }

    public void resetZoom() {
        cancelZoomAnimation();
        suppMatrix.reset();
        clampAndApplyMatrix();
        notifyScaleChanged();
    }

    public float getCurrentScale() {
        suppMatrix.getValues(matrixValues);
        return matrixValues[Matrix.MSCALE_X];
    }

    private void notifyScaleChanged() {
        if (scaleChangeListener != null) {
            scaleChangeListener.onScaleChanged(getCurrentScale());
        }
    }

    private void cancelZoomAnimation() {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
    }

    private void animateScale(float fromScale, float toScale, float focusX, float focusY) {
        cancelZoomAnimation();
        zoomAnimator = ValueAnimator.ofFloat(fromScale, toScale);
        zoomAnimator.setDuration(240);
        zoomAnimator.setInterpolator(new DecelerateInterpolator());
        zoomAnimator.addUpdateListener(animation -> {
            float s = (float) animation.getAnimatedValue();
            float current = getCurrentScale();
            if (current > 0f) {
                float factor = s / current;
                suppMatrix.postScale(factor, factor, focusX, focusY);
                clampAndApplyMatrix();
                notifyScaleChanged();
            }
        });
        zoomAnimator.start();
    }

    /**
     * Clamps the transformation matrix to prevent pulling outside boundaries.
     */
    private void clampAndApplyMatrix() {
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(suppMatrix);

        Drawable d = getDrawable();
        int vw = getWidth();
        int vh = getHeight();
        if (d != null && vw > 0 && vh > 0) {
            RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
            drawMatrix.mapRect(rect);

            float dx = 0f;
            float dy = 0f;

            // Horizontal bounds
            if (rect.width() <= vw) {
                dx = (vw - rect.width()) / 2f - rect.left;
            } else if (rect.left > 0) {
                dx = -rect.left;
            } else if (rect.right < vw) {
                dx = vw - rect.right;
            }

            // Vertical bounds
            if (rect.height() <= vh) {
                dy = (vh - rect.height()) / 2f - rect.top;
            } else if (rect.top > 0) {
                dy = -rect.top;
            } else if (rect.bottom < vh) {
                dy = vh - rect.bottom;
            }

            if (dx != 0f || dy != 0f) {
                suppMatrix.postTranslate(dx, dy);
                drawMatrix.set(baseMatrix);
                drawMatrix.postConcat(suppMatrix);
            }
        }

        setImageMatrix(drawMatrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = gestureDetector.onTouchEvent(event);
        handled |= scaleDetector.onTouchEvent(event);

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        if (pointerCount > 1) {
            // Multi-touch gestures (two-finger zoom): prevent parent ViewPager2 interception
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            lastTouch.set(event.getX(), event.getY());
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                touchDown.set(event.getX(), event.getY());
                lastTouch.set(event.getX(), event.getY());
                isDragging = false;
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - lastTouch.x;
                float dy = event.getY() - lastTouch.y;
                lastTouch.set(event.getX(), event.getY());

                float scale = getCurrentScale();
                if (scale > 1.05f) {
                    // Zoomed in: pan freely in X and Y
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    suppMatrix.postTranslate(dx, dy);
                    clampAndApplyMatrix();
                    isDragging = true;
                } else {
                    // At 1x fit-width: check if content is taller than viewport for vertical scrolling
                    Drawable d = getDrawable();
                    if (d != null && getHeight() > 0) {
                        RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
                        drawMatrix.mapRect(rect);

                        if (rect.height() > getHeight()) {
                            // If user starts or is dragging vertically, lock into vertical page scroll
                            if (isDragging || Math.abs(dy) > Math.abs(dx)) {
                                if (getParent() != null) {
                                    getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                suppMatrix.postTranslate(0f, dy);
                                clampAndApplyMatrix();
                                isDragging = true;
                            }
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                float current = getCurrentScale();
                if (current < MIN_SCALE) {
                    animateScale(current, MIN_SCALE, getWidth() / 2f, getHeight() / 2f);
                }
                break;
        }

        return true;
    }
}

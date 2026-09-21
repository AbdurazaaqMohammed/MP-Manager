package io.github.abdurazaaqmohammed.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
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

public class ZoomableImageView extends ImageView {

    private final Matrix baseMatrix = new Matrix();
    private final Matrix suppMatrix = new Matrix();
    private final Matrix drawMatrix = new Matrix();
    private final PointF lastPoint = new PointF();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private static final float MAX_SUPP_SCALE = 8f;
    private static final float DOUBLE_TAP_SCALE = 3f;
    private ValueAnimator zoomAnimator;

    public ZoomableImageView(Context context) {
        super(context);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleDoubleTap(e.getX(), e.getY());
                return true;
            }
        });
        init();
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleDoubleTap(e.getX(), e.getY());
                return true;
            }
        });
        init();
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleDoubleTap(e.getX(), e.getY());
                return true;
            }
        });
        init();
    }

    private void init() {
        setScaleType(ImageView.ScaleType.MATRIX);
        setClickable(true);
        setFocusable(true);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        updateBase();
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        updateBase();
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        updateBase();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateBase();
    }

    private void updateBase() {
        cancelZoomAnimation();
        Drawable d = getDrawable();
        int vw = getWidth();
        int vh = getHeight();
        if (d == null || vw <= 0 || vh <= 0) {
            return;
        }
        int dw = d.getIntrinsicWidth();
        int dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) {
            return;
        }
        baseMatrix.reset();
        float scale = Math.min(vw / (float) dw, vh / (float) dh);
        baseMatrix.postScale(scale, scale);
        baseMatrix.postTranslate((vw - dw * scale) / 2f, (vh - dh * scale) / 2f);
        suppMatrix.reset();
        applyDrawMatrix();
    }

    private void applyDrawMatrix() {
        drawMatrix.set(baseMatrix);
        drawMatrix.postConcat(suppMatrix);
        setImageMatrix(drawMatrix);
    }

    public void resetZoom() {
        cancelZoomAnimation();
        suppMatrix.reset();
        applyDrawMatrix();
    }

    public float getCurrentScale() {
        float[] f = new float[9];
        suppMatrix.getValues(f);
        return f[Matrix.MSCALE_X];
    }

    private void cancelZoomAnimation() {
        if (zoomAnimator != null) {
            zoomAnimator.cancel();
            zoomAnimator = null;
        }
    }

    private void toggleDoubleTap(float x, float y) {
        float current = getCurrentScale();
        float target = current > (1f + DOUBLE_TAP_SCALE) / 2f ? 1f : DOUBLE_TAP_SCALE;
        cancelZoomAnimation();
        zoomAnimator = ValueAnimator.ofFloat(current,target);
        zoomAnimator.setDuration(250);
        zoomAnimator.setInterpolator(new DecelerateInterpolator());
        zoomAnimator.addUpdateListener(animation -> {
            float s = (float) animation.getAnimatedValue();
            suppMatrix.reset();
            suppMatrix.postScale(s,s,x,y);
            clampSupp();
        });
        zoomAnimator.start();
    }

    private void clampSupp() {
        float[] f = new float[9];
        suppMatrix.getValues(f);
        float scale = f[Matrix.MSCALE_X];
        if (scale < 1f) {
            suppMatrix.postScale(1f / scale, 1f / scale, getWidth() / 2f, getHeight() / 2f);
        } else if (scale > MAX_SUPP_SCALE) {
            suppMatrix.postScale(MAX_SUPP_SCALE / scale, MAX_SUPP_SCALE / scale, getWidth() / 2f, getHeight() / 2f);
        }
        applyDrawMatrix();
        Drawable d = getDrawable();
        if (d == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        RectF rect = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        drawMatrix.mapRect(rect);
        float dx = 0f;
        float dy = 0f;
        int vw = getWidth();
        int vh = getHeight();
        if (rect.width() <= vw) {
            dx = vw / 2f - rect.centerX();
        } else if (rect.left > 0) {
            dx = -rect.left;
        } else if (rect.right < vw) {
            dx = vw - rect.right;
        }
        if (rect.height() <= vh) {
            dy = vh / 2f - rect.centerY();
        } else if (rect.top > 0) {
            dy = -rect.top;
        } else if (rect.bottom < vh) {
            dy = vh - rect.bottom;
        }
        if (dx != 0f || dy != 0f) {
            suppMatrix.postTranslate(dx, dy);
            applyDrawMatrix();
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            cancelZoomAnimation();
            lastPoint.set(detector.getFocusX(), detector.getFocusY());
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            suppMatrix.postScale(detector.getScaleFactor(), detector.getScaleFactor(),
                    detector.getFocusX(), detector.getFocusY());
            clampSupp();
            return true;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        scaleDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            lastPoint.set(event.getX(), event.getY());
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                cancelZoomAnimation();
                float dx = event.getX() - lastPoint.x;
                float dy = event.getY() - lastPoint.y;
                lastPoint.set(event.getX(), event.getY());
                if (getCurrentScale() > 1f) {
                    suppMatrix.postTranslate(dx, dy);
                    clampSupp();
                }
            }
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            lastPoint.set(event.getX(), event.getY());
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            lastPoint.set(event.getX(), event.getY());
        }
        return true;
    }
}

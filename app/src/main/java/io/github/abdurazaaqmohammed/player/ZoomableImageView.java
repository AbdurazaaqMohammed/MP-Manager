package io.github.abdurazaaqmohammed.player;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

public class ZoomableImageView extends ImageView {

    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();
    private PointF startPoint = new PointF();
    private PointF midPoint = new PointF();
    private float oldDistance;
    private int mode = NONE;
    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private float minScale = 0.5f;
    private float maxScale = 5f;
    private GestureDetector gestureDetector;
    private OnDoubleTapListener doubleTapListener;

    public ZoomableImageView(Context context) {
        super(context);
        init();
    }

    public ZoomableImageView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ZoomableImageView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnDoubleTapListener(OnDoubleTapListener listener) {
        this.doubleTapListener = listener;
    }

    public interface OnDoubleTapListener {
        void onDoubleTap();
    }

    private void init() {
        setScaleType(ImageView.ScaleType.MATRIX);
        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (doubleTapListener != null) {
                    doubleTapListener.onDoubleTap();
                } else {
                    float scale = getCurrentScale();
                    float targetScale = scale > 1.5f ? 1f : 2.5f;
                    matrix.postScale(targetScale / scale, targetScale / scale, e.getX(), e.getY());
                    setImageMatrix(matrix);
                }
                return true;
            }
        });
    }

    public void resetZoom() {
        matrix.reset();
        setImageMatrix(matrix);
        mode = NONE;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                startPoint.set(event.getX(), event.getY());
                mode = DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDistance = spacing(event);
                if (oldDistance > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(midPoint, event);
                    mode = ZOOM;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG) {
                    float dx = event.getX() - startPoint.x;
                    float dy = event.getY() - startPoint.y;
                    matrix.set(savedMatrix);
                    matrix.postTranslate(dx, dy);
                } else if (mode == ZOOM) {
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        float scale = newDist / oldDistance;
                        matrix.set(savedMatrix);
                        matrix.postScale(scale, scale, midPoint.x, midPoint.y);
                    }
                }
                setImageMatrix(matrix);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                float currentScale = getCurrentScale();
                if (currentScale < minScale) {
                    matrix.postScale(minScale / currentScale, minScale / currentScale, getWidth() / 2f, getHeight() / 2f);
                    setImageMatrix(matrix);
                } else if (currentScale > maxScale) {
                    float[] f = new float[9];
                    matrix.getValues(f);
                    float scale = f[Matrix.MSCALE_X];
                    matrix.postScale(maxScale / scale, maxScale / scale, getWidth() / 2f, getHeight() / 2f);
                    setImageMatrix(matrix);
                }
                break;
        }
        return true;
    }

    public float getCurrentScale() {
        float[] f = new float[9];
        matrix.getValues(f);
        return f[Matrix.MSCALE_X];
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }
}

package io.github.abdurazaaqmohammed.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public class CropOverlayView extends View {

    public interface CropListener {
        void onCropChanged(RectF imageRect);
    }

    private final RectF cropRect = new RectF();
    private final RectF imageBounds = new RectF();
    private boolean hasBounds;
    private boolean hasRect;
    private float aspectRatio;
    private CropListener listener;
    private final Paint dimPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint handlePaint = new Paint();
    private int activeHandle = -1;
    private float lastX;
    private float lastY;
    private float handleRadius;
    private float touchSlop;

    public CropOverlayView(Context context) {
        super(context);
        float density = context.getResources().getDisplayMetrics().density;
        handleRadius = 14 * density;
        touchSlop = 48 * density;
        dimPaint.setColor(0xB0000000);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3 * density);
        gridPaint.setColor(0xAAFFFFFF);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1 * density);
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setCropListener(CropListener listener) {
        this.listener = listener;
    }

    public void setAspectRatio(float ratio) {
        aspectRatio = ratio;
        if (hasRect && ratio > 0) {
            fitRatio();
            invalidate();
            notifyListener();
        }
    }

    public void setImageBounds(RectF bounds) {
        if (bounds == null) {
            hasBounds = false;
            return;
        }
        imageBounds.set(bounds);
        hasBounds = true;
        if (hasRect) {
            clampRect();
            invalidate();
            notifyListener();
        }
    }

    public void setImageRect(RectF rect) {
        cropRect.set(rect);
        hasRect = true;
        if (aspectRatio > 0) fitRatio();
        clampRect();
        invalidate();
        notifyListener();
    }

    public void clearRect() {
        hasRect = false;
        invalidate();
        notifyListener();
    }

    public boolean hasCrop() {
        return hasRect;
    }

    public RectF getCropRect() {
        return new RectF(cropRect);
    }

    private void fitRatio() {
        float w = cropRect.width();
        float h = cropRect.height();
        if (w <= 0 || h <= 0) return;
        float cx = cropRect.centerX();
        float cy = cropRect.centerY();
        if (w / h > aspectRatio) {
            w = h * aspectRatio;
        } else {
            h = w / aspectRatio;
        }
        cropRect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    private void notifyListener() {
        if (listener != null) listener.onCropChanged(hasRect ? new RectF(cropRect) : null);
    }

    private int hitHandle(float x, float y) {
        float[][] pts = {
                {cropRect.left, cropRect.top},
                {cropRect.right, cropRect.top},
                {cropRect.left, cropRect.bottom},
                {cropRect.right, cropRect.bottom}};
        for (int i = 0; i < 4; i++) {
            float dx = x - pts[i][0];
            float dy = y - pts[i][1];
            if (dx * dx + dy * dy <= touchSlop * touchSlop) return i;
        }
        return -1;
    }

    private void clampRect() {
        float min = handleRadius * 2f;
        if (cropRect.width() < min) {
            if (activeHandle == 0 || activeHandle == 2) cropRect.left = cropRect.right - min;
            else cropRect.right = cropRect.left + min;
        }
        if (cropRect.height() < min) {
            if (activeHandle == 0 || activeHandle == 1) cropRect.top = cropRect.bottom - min;
            else cropRect.bottom = cropRect.top + min;
        }
        float leftBound = hasBounds ? imageBounds.left : 0;
        float topBound = hasBounds ? imageBounds.top : 0;
        float rightBound = hasBounds ? imageBounds.right : getWidth();
        float bottomBound = hasBounds ? imageBounds.bottom : getHeight();
        cropRect.left = Math.max(leftBound, cropRect.left);
        cropRect.top = Math.max(topBound, cropRect.top);
        cropRect.right = Math.min(rightBound, cropRect.right);
        cropRect.bottom = Math.min(bottomBound, cropRect.bottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!hasRect) return false;
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                activeHandle = hitHandle(x, y);
                if (activeHandle < 0 && cropRect.contains(x, y)) activeHandle = 4;
                else if (activeHandle < 0) return false;
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (activeHandle < 0) return false;
                float dx = x - lastX;
                float dy = y - lastY;
                lastX = x;
                lastY = y;
                if (activeHandle == 4) {
                    cropRect.offset(dx, dy);
                } else {
                    if (activeHandle == 0 || activeHandle == 2) cropRect.left += dx;
                    if (activeHandle == 1 || activeHandle == 3) cropRect.right += dx;
                    if (activeHandle == 0 || activeHandle == 1) cropRect.top += dy;
                    if (activeHandle == 2 || activeHandle == 3) cropRect.bottom += dy;
                    if (aspectRatio > 0) fitRatio();
                }
                clampRect();
                invalidate();
                notifyListener();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activeHandle = -1;
                return true;
            default:
                return false;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasRect) return;
        canvas.drawRect(0, 0, getWidth(), cropRect.top, dimPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, dimPaint);
        canvas.drawRect(cropRect, borderPaint);
        float thirdW = cropRect.width() / 3f;
        float thirdH = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + 2 * thirdW, cropRect.top, cropRect.left + 2 * thirdW, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + 2 * thirdH, cropRect.right, cropRect.top + 2 * thirdH, gridPaint);
        canvas.drawCircle(cropRect.left, cropRect.top, handleRadius, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.top, handleRadius, handlePaint);
        canvas.drawCircle(cropRect.left, cropRect.bottom, handleRadius, handlePaint);
        canvas.drawCircle(cropRect.right, cropRect.bottom, handleRadius, handlePaint);
    }
}

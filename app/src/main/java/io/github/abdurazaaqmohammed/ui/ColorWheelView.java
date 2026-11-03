package io.github.abdurazaaqmohammed.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/**
 * HSV color wheel (hue ring + saturation/value square) extracted from the
 * Toast/Dialog overlay maker so settings and other features can reuse it.
 * Callbacks: {@link #setListener(Runnable)} fires after every change;
 * {@link #getColor(int)} reads the current ARGB.
 */
public class ColorWheelView extends View {
    private float hue;
    private float sat = 1f;
    private float val = 1f;
    private Runnable listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cx;
    private float cy;
    private float ringOuter;
    private float ringWidth;
    private float sqLeft;
    private float sqTop;
    private float sqSize;

    public ColorWheelView(Context ctx) {
        super(ctx);
    }

    public void setListener(Runnable listener) {
        this.listener = listener;
    }

    public void setColor(int argb) {
        float[] hsv = new float[3];
        Color.colorToHSV(argb, hsv);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        invalidate();
        if (listener != null) listener.run();
    }

    public int getColor(int alpha) {
        return Color.HSVToColor(alpha, new float[]{hue, sat, val});
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        float size = Math.min(w, h);
        cx = w / 2f;
        cy = h / 2f;
        ringOuter = size / 2f - 4;
        ringWidth = Math.max(36, size * 0.12f);
        float inner = ringOuter - ringWidth;
        sqSize = (float) (inner * 1.35);
        sqLeft = cx - sqSize / 2f;
        sqTop = cy - sqSize / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int[] hueColors = new int[361];
        for (int i = 0; i <= 360; i++) hueColors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ringWidth);
        paint.setShader(new SweepGradient(cx, cy, hueColors, null));
        canvas.drawCircle(cx, cy, ringOuter - ringWidth / 2f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        int hueColor = Color.HSVToColor(new float[]{hue, 1f, 1f});
        paint.setShader(new LinearGradient(sqLeft, 0, sqLeft + sqSize, 0,
                Color.WHITE, hueColor, Shader.TileMode.CLAMP));
        canvas.drawRect(sqLeft, sqTop, sqLeft + sqSize, sqTop + sqSize, paint);
        paint.setShader(new LinearGradient(0, sqTop, 0, sqTop + sqSize,
                Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP));
        canvas.drawRect(sqLeft, sqTop, sqLeft + sqSize, sqTop + sqSize, paint);
        paint.setShader(null);
        double rad = Math.toRadians(hue);
        float hr = ringOuter - ringWidth / 2f;
        float hx = cx + (float) Math.cos(rad) * hr;
        float hy = cy + (float) Math.sin(rad) * hr;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(hx, hy, ringWidth / 2f - 2, paint);
        float sx = sqLeft + sat * sqSize;
        float sy = sqTop + (1f - val) * sqSize;
        canvas.drawCircle(sx, sy, 12, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            return super.onTouchEvent(event);
        }
        float x = event.getX();
        float y = event.getY();
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist >= ringOuter - ringWidth && dist <= ringOuter + 8) {
            hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
            invalidate();
            if (listener != null) listener.run();
            return true;
        }
        if (x >= sqLeft && x <= sqLeft + sqSize && y >= sqTop && y <= sqTop + sqSize) {
            sat = Math.max(0f, Math.min(1f, (x - sqLeft) / sqSize));
            val = Math.max(0f, Math.min(1f, 1f - (y - sqTop) / sqSize));
            invalidate();
            if (listener != null) listener.run();
            return true;
        }
        return super.onTouchEvent(event);
    }
}

package io.github.abdurazaaqmohammed.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorWheelView extends View {
    private float hue;
    private float sat = 1f;
    private float val = 1f;
    private Runnable listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float cx;
    private float cy;
    private float ringOuter;
    private float ringWidth;
    private float sqLeft;
    private float sqTop;
    private float sqSize;
    private int[] hueColors;
    private SweepGradient sweep;
    private float sweepCx = -1f;
    private float sweepCy = -1f;

    public ColorWheelView(Context ctx) {
        super(ctx);
        init();
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        selectorPaint.setStyle(Paint.Style.STROKE);
        selectorPaint.setStrokeWidth(5f);
        hueColors = new int[361];
        for (int i = 0; i <= 360; i++) hueColors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
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
        fire();
    }

    public int getColor() {
        return getColor(255);
    }

    public int getColor(int alpha) {
        return Color.HSVToColor(alpha, new float[]{hue, sat, val});
    }

    public float getHue() { return hue; }
    public float getSat() { return sat; }
    public float getVal() { return val; }

    private void fire() {
        if (listener != null) listener.run();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (w <= 0 || h <= 0) return;
        float size = Math.min(w, h);
        cx = w / 2f;
        cy = h / 2f;
        ringOuter = size / 2f - 4;
        ringWidth = Math.max(36, size * 0.12f);
        float inner = Math.max(0, ringOuter - ringWidth);
        sqSize = (float) (inner * 1.35);
        // Clamp square so it never overlaps the ring
        float maxSq = (float) (inner * Math.sqrt(2.0));
        if (sqSize > maxSq) sqSize = maxSq;
        sqLeft = cx - sqSize / 2f;
        sqTop = cy - sqSize / 2f;
        sweep = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (ringOuter <= 0 || sqSize <= 0) return;
        if (sweep == null || sweepCx != cx || sweepCy != cy) {
            sweep = new SweepGradient(cx, cy, hueColors, null);
            sweepCx = cx;
            sweepCy = cy;
        }
        paint.setShader(sweep);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ringWidth);
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

        // Hue selector with white + dark outline so it is visible on any color
        double rad = Math.toRadians(hue);
        float hr = ringOuter - ringWidth / 2f;
        float hx = cx + (float) Math.cos(rad) * hr;
        float hy = cy + (float) Math.sin(rad) * hr;
        float sr = Math.max(10, ringWidth / 2f - 2);
        selectorPaint.setColor(Color.BLACK);
        selectorPaint.setStrokeWidth(7f);
        canvas.drawCircle(hx, hy, sr, selectorPaint);
        selectorPaint.setColor(Color.WHITE);
        selectorPaint.setStrokeWidth(4f);
        canvas.drawCircle(hx, hy, sr, selectorPaint);

        float sx = sqLeft + sat * sqSize;
        float sy = sqTop + (1f - val) * sqSize;
        selectorPaint.setColor(Color.BLACK);
        selectorPaint.setStrokeWidth(6f);
        canvas.drawCircle(sx, sy, 14, selectorPaint);
        selectorPaint.setColor(Color.WHITE);
        selectorPaint.setStrokeWidth(3.5f);
        canvas.drawCircle(sx, sy, 14, selectorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_MOVE && action != MotionEvent.ACTION_UP) {
            return super.onTouchEvent(event);
        }
        float x = event.getX();
        float y = event.getY();
        float dx = x - cx;
        float dy = y - cy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        boolean handled = false;
        float tol = Math.max(24, ringWidth * 0.6f);
        if (dist >= ringOuter - ringWidth - tol && dist <= ringOuter + tol) {
            hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
            handled = true;
        } else if (x >= sqLeft - tol / 2 && x <= sqLeft + sqSize + tol / 2
                && y >= sqTop - tol / 2 && y <= sqTop + sqSize + tol / 2) {
            sat = Math.max(0f, Math.min(1f, (x - sqLeft) / sqSize));
            val = Math.max(0f, Math.min(1f, 1f - (y - sqTop) / sqSize));
            handled = true;
        }
        if (handled) {
            invalidate();
            fire();
            if (action == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}

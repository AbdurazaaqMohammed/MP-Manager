package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Tiny scrolling line graph for live monitors. Fixed capacity ring buffer,
 * autoscaled, no dependencies.
 */
public class MiniGraph extends View {

    private final float[] buf = new float[90];
    private int count;
    private float forcedMax = -1;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MiniGraph(Context context, int color) {
        super(context);
        line.setColor(color);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2));
        fill.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setAlpha(48);
        text.setColor(Color.GRAY);
        text.setTextSize(dp(10));
        setMinimumHeight((int) dp(72));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    public void setForcedMax(float max) {
        forcedMax = max;
    }

    public synchronized void push(float v) {
        if (count < buf.length) {
            buf[count++] = v;
        } else {
            System.arraycopy(buf, 1, buf, 0, buf.length - 1);
            buf[buf.length - 1] = v;
        }
        postInvalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float pad = dp(4);
        float top = pad + dp(12);
        float bottom = h - pad;
        float max = forcedMax > 0 ? forcedMax : 1;
        if (forcedMax <= 0) {
            for (int i = 0; i < count; i++) max = Math.max(max, buf[i]);
            if (max <= 0) max = 1;
        }
        Path area = new Path();
        boolean started = false;
        float lastX = 0;
        float lastY = bottom;
        for (int i = 0; i < count; i++) {
            float x = pad + (w - 2 * pad) * i / (float) (buf.length - 1);
            float y = bottom - (bottom - top) * Math.min(1f, buf[i] / max);
            if (!started) {
                area.moveTo(x, bottom);
                area.lineTo(x, y);
                started = true;
            } else {
                area.lineTo(x, y);
            }
            lastX = x;
            lastY = y;
        }
        if (started) {
            area.lineTo(lastX, bottom);
            area.close();
            c.drawPath(area, fill);
            Path stroke = new Path(area);
            c.drawPath(stroke, line);
        }
        float peak = 0;
        for (int i = 0; i < count; i++) peak = Math.max(peak, buf[i]);
        c.drawText("peak " + new java.text.DecimalFormat("0.#").format(peak),
                pad, pad + dp(8), text);
    }
}

package io.github.abdurazaaqmohammed.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CustomProgressBar extends View {
    private Paint paint;
    private int progress = 0; // Progress value from 0 to 100
    private int max = 100; // Maximum value
    private int barColor = Color.BLUE; // Color of the progress bar
    private int backgroundColor = Color.LTGRAY; // Background color

    public CustomProgressBar(Context context) {
        super(context);
        init();
    }

    public CustomProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CustomProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw background
        paint.setColor(backgroundColor);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        // Draw progress
        paint.setColor(barColor);
        float progressWidth = (getWidth() * progress) / max;
        canvas.drawRect(0, 0, progressWidth, getHeight(), paint);
    }

    public void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(progress, max)); // Clamp progress between 0 and max
        invalidate(); // Redraw the view
    }

    public void setMax(int max) {
        this.max = max;
    }

    public void setBarColor(int color) {
        this.barColor = color;
        invalidate();
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        invalidate();
    }
}
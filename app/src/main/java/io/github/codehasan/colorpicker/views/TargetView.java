/*
 * Copyright (c) 2026 Ratul Hasan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 */
package io.github.codehasan.colorpicker.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

public class TargetView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float holePercentage = 0.13f;
    private float mainStrokePct = 0.28f;
    private final int[] locationArray = new int[2];

    public TargetView(Context context) {
        super(context);
        init();
    }

    public TargetView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Sets the capture range which affects the hole size and inner circle size.
     *
     * @param range "small", "medium", or "large"
     */
    public void setCaptureRange(String range) {
        switch (range) {
            case "small":
                holePercentage = 0.13f;
                mainStrokePct = 0.28f;
                break;

            case "medium":
                holePercentage = 0.16f;
                mainStrokePct = 0.25f;
                break;

            case "large":
                holePercentage = 0.19f;
                mainStrokePct = 0.22f;
                break;
        }
        invalidate();
    }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForceDarkAllowed(false);
        }
    }

    /**
     * Returns the center point of the view.
     */
    public PointF getScanOffset() {
        getLocationOnScreen(locationArray);
        return new PointF(locationArray[0] + (getWidth() / 2f), locationArray[1] + (getHeight() / 2f));
    }

    /**
     * Returns the size which can be safely captured without including
     * the TargetView's decorative circles.
     * Calculated to fit within the center hole area.
     */
    public int getSafeCropSize() {
        float size = Math.min(getWidth(), getHeight());
        float holeDiameter = size * (holePercentage * 2);
        return Math.max((int) (holeDiameter * 0.50f), 8);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;

        float size = Math.min(getWidth(), getHeight());

        float thinStrokePct = 0.02f;
        float outerStrokePct = 0.05f;

        float holeRadius = size * holePercentage;

        float thinWidth = size * thinStrokePct;
        float mainWidth = size * mainStrokePct;
        float outerWidth = size * outerStrokePct;

        paint.setStyle(Paint.Style.STROKE);

        // Inner Thin Circle
        paint.setColor(Color.parseColor("#B4A9A9A9"));
        paint.setStrokeWidth(thinWidth);
        float r1 = holeRadius + (thinWidth / 2f);
        canvas.drawCircle(cx, cy, r1, paint);

        // Inner Main Circle
        paint.setColor(Color.parseColor("#C8535353"));
        paint.setStrokeWidth(mainWidth);
        float r2 = holeRadius + thinWidth + (mainWidth / 2f);
        canvas.drawCircle(cx, cy, r2, paint);

        // Outer Medium Circle
        paint.setColor(Color.parseColor("#E6A9A9A9"));
        paint.setStrokeWidth(outerWidth);
        float r3 = holeRadius + thinWidth + mainWidth + (outerWidth / 2f);
        canvas.drawCircle(cx, cy, r3, paint);
    }
}

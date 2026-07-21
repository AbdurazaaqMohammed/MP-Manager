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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.res.ResourcesCompat;

import io.github.abdurazaaqmohammed.MPManager.R;


public class MagnifierView extends View {

    public interface OnInteractionListener {
        void onCloseClicked();

        void onHexClicked(String hex);

        void onCoordsClicked(String coords);
    }

    private OnInteractionListener listener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final Path textPath = new Path();
    private final RectF destRect = new RectF();

    private Bitmap zoomBitmap;

    private final int gridShadowColor = Color.parseColor("#40000000");
    private final int gridMainColor = Color.parseColor("#80FFFFFF");

    private final int darkTextColor = Color.parseColor("#0F0F10");
    private final int lightTextColor = Color.parseColor("#F2F2F4");

    private final int darkBorderColor = Color.parseColor("#666666");
    private final int lightBorderColor = Color.parseColor("#999999");

    // Properties (Dynamic)
    private String hexColor = "#000000";
    private String coords = "0, 0";
    private boolean showGridLines = true;

    // Touch Handling
    private float lastTouchX = 0f;
    private float lastTouchY = 0f;
    private boolean isClickCandidate = false;

    // Hit Boxes
    private final RectF hexTouchRect = new RectF();
    private final RectF coordsTouchRect = new RectF();
    private final RectF closeTouchRect = new RectF();

    private Typeface typeface;

    public MagnifierView(Context context) {
        super(context);
        init(context);
    }

    public MagnifierView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setListener(OnInteractionListener listener) {
        this.listener = listener;
    }

    private void init(Context context) {
        setBackgroundColor(Color.TRANSPARENT);
       // typeface = ResourcesCompat.getFont(context, R.font.product_sans_regular);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setForceDarkAllowed(false);
        }
    }

    public void updateContent(Bitmap bitmap, String color, int x, int y) {
        Bitmap oldBitmap = zoomBitmap;
        zoomBitmap = bitmap;
        if (oldBitmap != null) {
            oldBitmap.recycle();
        }

        hexColor = color;
        coords = x + ", " + y;
        invalidate();
    }

    public void setShowGridLines(boolean show) {
        if (showGridLines != show) {
            showGridLines = show;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (zoomBitmap != null) {
            zoomBitmap.recycle();
            zoomBitmap = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float size = Math.min(getWidth(), getHeight());

        float bezelThickness = size * 0.11f;
        float textSizeNormal = size * 0.09f;
        float textSizeLarge = size * 0.16f;

        // Radii Configuration - leave ~4% margin to prevent clipping
        float maxRadius = (size / 2f) * 0.96f;
        float rOuter = maxRadius;
        float rInner = rOuter - bezelThickness;
        float rCenter = rInner + (bezelThickness / 2f);

        // Calculate Adaptive Text Color
        int parsedColor;
        try {
            parsedColor = Color.parseColor(hexColor);
        } catch (IllegalArgumentException e) {
            parsedColor = Color.BLACK;
        }
        float luminance = (0.299f * Color.red(parsedColor) +
                0.587f * Color.green(parsedColor) +
                0.114f * Color.blue(parsedColor)) / 255f;
        int textColor = luminance > 0.6f ? darkTextColor : lightTextColor;

        // Draw Image Area
        canvas.save();
        try {
            clipPath.reset();
            clipPath.addCircle(cx, cy, rInner, Path.Direction.CW);
            canvas.clipPath(clipPath);
            canvas.drawColor(Color.BLACK);

            if (zoomBitmap != null) {
                paint.setFilterBitmap(false);

                float visibleDiameter = rInner * 2f;
                float pixelSize = zoomBitmap.getWidth() > 1
                        ? visibleDiameter / (zoomBitmap.getWidth() - 1f)
                        : visibleDiameter;

                float totalDrawSize = pixelSize * zoomBitmap.getWidth();
                float centerOffset = (zoomBitmap.getWidth() % 2 == 0) ? pixelSize / 2f : 0f;

                float startX = cx - (totalDrawSize / 2f) - centerOffset;
                float startY = cy - (totalDrawSize / 2f) - centerOffset;

                destRect.set(
                        startX, startY,
                        startX + totalDrawSize,
                        startY + totalDrawSize
                );

                canvas.drawBitmap(zoomBitmap, null, destRect, paint);

                // Draw Dual-Layer Grid (Shadow + Main)
                if (showGridLines) {
                    paint.setStyle(Paint.Style.STROKE);
                    float gridBaseWidth = size * 0.005f;

                    // Layer A: Grid Shadow (Thicker, Dark)
                    paint.setColor(gridShadowColor);
                    paint.setStrokeWidth(gridBaseWidth * 1.5f);
                    drawGridLines(canvas, startX, startY, pixelSize, cx, cy, rInner);

                    // Layer B: Main Grid (Thin, Light)
                    paint.setColor(gridMainColor);
                    paint.setStrokeWidth(gridBaseWidth);
                    drawGridLines(canvas, startX, startY, pixelSize, cx, cy, rInner);
                }

                // Highlight Center Pixel
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(textColor);
                paint.setStrokeWidth(size * 0.01f);
                float halfPixel = pixelSize / 2f;
                canvas.drawRect(
                        cx - halfPixel,
                        cy - halfPixel,
                        cx + halfPixel,
                        cy + halfPixel,
                        paint
                );
            }
        } finally {
            canvas.restore();
        }

        // Draw Main Bezel
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(bezelThickness);
        paint.setColor(parsedColor);
        canvas.drawCircle(cx, cy, rCenter, paint);

        // Draw Bezel Borders
        paint.setStyle(Paint.Style.STROKE);
        float borderWidth = size * 0.005f;
        float borderInset = borderWidth * 0.5f;  // Prevent anti-aliasing overflow

        // Inner border
        paint.setColor(darkBorderColor);
        paint.setStrokeWidth(borderWidth * 2f);
        canvas.drawCircle(cx, cy, rInner - borderWidth - borderInset, paint);

        paint.setColor(lightBorderColor);
        paint.setStrokeWidth(borderWidth);
        canvas.drawCircle(cx, cy, rInner - borderWidth / 2f - borderInset, paint);

        // Outer border
        paint.setColor(darkBorderColor);
        paint.setStrokeWidth(borderWidth * 2f);
        canvas.drawCircle(cx, cy, rOuter + borderWidth, paint);

        paint.setColor(lightBorderColor);
        paint.setStrokeWidth(borderWidth);
        canvas.drawCircle(cx, cy, rOuter + borderWidth * 1.5f, paint);

        // Draw Text Buttons
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(textColor);
     //   paint.setTypeface(typeface);
        paint.setTextAlign(Paint.Align.CENTER);

        // Hex Color
        paint.setTextSize(textSizeNormal);
        drawTextAtAngle(canvas, hexColor, cx, cy, rCenter, 45.0, -145.0, hexTouchRect);

        // Coordinates
        paint.setTextSize(textSizeNormal);
        drawTextAtAngle(canvas, coords, cx, cy, rCenter, 135.0, -50.0, coordsTouchRect);

        // Close 'X'
        paint.setTextSize(textSizeLarge);
        drawTextAtAngle(canvas, "×", cx, cy + (bezelThickness / 12f), rCenter, -90.0, 90.0, closeTouchRect);
    }

    private void drawGridLines(Canvas canvas, float startX, float startY, float pixelSize,
                                float cx, float cy, float rInner) {
        float rSq = rInner * rInner;

        // Vertical Lines - calculate visible segment within circle
        float xPos = startX + pixelSize;
        while (xPos < destRect.right - 0.1f) {
            float dx = xPos - cx;
            if (dx * dx < rSq) {
                float halfChord = (float) Math.sqrt(rSq - dx * dx);
                canvas.drawLine(xPos, cy - halfChord, xPos, cy + halfChord, paint);
            }
            xPos += pixelSize;
        }

        // Horizontal Lines - calculate visible segment within circle
        float yPos = startY + pixelSize;
        while (yPos < destRect.bottom - 0.1f) {
            float dy = yPos - cy;
            if (dy * dy < rSq) {
                float halfChord = (float) Math.sqrt(rSq - dy * dy);
                canvas.drawLine(cx - halfChord, yPos, cx + halfChord, yPos, paint);
            }
            yPos += pixelSize;
        }
    }

    private void drawTextAtAngle(
            Canvas canvas,
            String text,
            float cx,
            float cy,
            float radius,
            double textAngleDegrees,
            double touchAngleDegrees,
            RectF touchRect
    ) {
        textPath.reset();
        textPath.addCircle(cx, cy, radius, Path.Direction.CW);

        float textWidth = paint.measureText(text);
        float circumference = (float) (2 * Math.PI * radius);
        if (textWidth > circumference) return;

        float startOffset = (float) (textAngleDegrees / 360.0 * circumference);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float verticalOffset = -(fm.ascent + fm.descent) / 2f;

        canvas.drawTextOnPath(text, textPath, startOffset, verticalOffset, paint);

        double sweepAngleRad = textWidth / radius;
        double midAngleRad = Math.toRadians(touchAngleDegrees) + sweepAngleRad / 2.0;

        float textHeight = fm.descent - fm.ascent;
        float padding = paint.getTextSize() * 0.5f;

        float minR = radius - textHeight / 2f - padding;
        float maxR = radius + textHeight / 2f + padding;

        float x1 = (float) (cx + (minR * Math.cos(midAngleRad)));
        float y1 = (float) (cy + (minR * Math.sin(midAngleRad)));
        float x2 = (float) (cx + (maxR * Math.cos(midAngleRad)));
        float y2 = (float) (cy + (maxR * Math.sin(midAngleRad)));

        float expandAmount = padding + (textWidth / 4f);

        touchRect.set(
                Math.min(x1, x2) - expandAmount,
                Math.min(y1, y2) - expandAmount,
                Math.max(x1, x2) + expandAmount,
                Math.max(y1, y2) + expandAmount
        );
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                isClickCandidate = true;
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (Math.hypot(event.getX() - lastTouchX, event.getY() - lastTouchY) > 10) {
                    isClickCandidate = false;
                }
                return true;

            case MotionEvent.ACTION_UP:
                if (isClickCandidate) {
                    float x = event.getX();
                    float y = event.getY();
                    if (closeTouchRect.contains(x, y)) {
                        if (listener != null) listener.onCloseClicked();
                    } else if (hexTouchRect.contains(x, y)) {
                        if (listener != null) listener.onHexClicked(hexColor);
                    } else if (coordsTouchRect.contains(x, y)) {
                        if (listener != null) listener.onCoordsClicked(coords);
                    }
                }
                return true;

            default:
                return true;
        }
    }
}

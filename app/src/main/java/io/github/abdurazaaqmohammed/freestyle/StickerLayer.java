package io.github.abdurazaaqmohammed.freestyle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class StickerLayer {

    public Bitmap bitmap;
    public float x;      // Center X on canvas
    public float y;      // Center Y on canvas
    public float width;  // Base width in pixels
    public float height; // Base height in pixels
    public float scaleX = 1.0f; // Stretch or scale X
    public float scaleY = 1.0f; // Stretch or scale Y
    public float rotation = 0.0f; // Degrees around center
    public boolean flippedH = false; // Flipped horizontally

    // Corner handle types
    public static final int HANDLE_NONE = 0;
    public static final int HANDLE_ROTATE = 1;      // Top-Right
    public static final int HANDLE_SCALE = 2;       // Bottom-Right
    public static final int HANDLE_FLIP = 3;        // Top-Left
    public static final int HANDLE_CROP = 4;        // Bottom-Left
    public static final int HANDLE_STRETCH_TOP = 5; // Top middle
    public static final int HANDLE_STRETCH_BOTTOM = 6; // Bottom middle
    public static final int HANDLE_STRETCH_LEFT = 7;   // Left middle
    public static final int HANDLE_STRETCH_RIGHT = 8;  // Right middle

    public StickerLayer(Bitmap bitmap, float startX, float startY, float maxWidth, float maxHeight) {
        this.bitmap = bitmap;
        float bW = bitmap.getWidth();
        float bH = bitmap.getHeight();

        float scale = Math.min(maxWidth / bW, maxHeight / bH);
        if (scale > 1.0f) scale = 1.0f;
        this.width = bW * scale;
        this.height = bH * scale;
        this.x = startX;
        this.y = startY;
    }

    public Matrix getTransformMatrix() {
        Matrix matrix = new Matrix();
        // Translate to origin (-w/2, -h/2)
        matrix.postTranslate(-width / 2f, -height / 2f);
        // Flip
        if (flippedH) {
            matrix.postScale(-1f, 1f);
        }
        // Scale / Stretch
        matrix.postScale(scaleX, scaleY);
        // Rotate
        matrix.postRotate(rotation);
        // Translate to (x, y)
        matrix.postTranslate(x, y);
        return matrix;
    }

    public void draw(Canvas canvas, Paint paint) {
        if (bitmap == null || bitmap.isRecycled()) return;
        Matrix m = getTransformMatrix();
        // Scale bitmap to fit width/height
        Matrix drawMatrix = new Matrix();
        drawMatrix.postScale(width / (float) bitmap.getWidth(), height / (float) bitmap.getHeight());
        drawMatrix.postConcat(m);

        canvas.drawBitmap(bitmap, drawMatrix, paint);
    }

    public PointF[] getTransformedCorners() {
        float halfW = (width * scaleX) / 2f;
        float halfH = (height * scaleY) / 2f;

        // Unrotated corners relative to (x, y)
        float[] pts = new float[]{
                -halfW, -halfH, // Top-Left
                halfW, -halfH,  // Top-Right
                halfW, halfH,   // Bottom-Right
                -halfW, halfH   // Bottom-Left
        };

        Matrix rot = new Matrix();
        rot.postRotate(rotation);
        rot.mapPoints(pts);

        PointF[] corners = new PointF[4];
        for (int i = 0; i < 4; i++) {
            corners[i] = new PointF(pts[i * 2] + x, pts[i * 2 + 1] + y);
        }
        return corners;
    }

    public PointF[] getTransformedEdgeMidpoints() {
        float halfW = (width * scaleX) / 2f;
        float halfH = (height * scaleY) / 2f;

        float[] pts = new float[]{
                0, -halfH,      // Top
                halfW, 0,       // Right
                0, halfH,       // Bottom
                -halfW, 0       // Left
        };

        Matrix rot = new Matrix();
        rot.postRotate(rotation);
        rot.mapPoints(pts);

        PointF[] edges = new PointF[4];
        for (int i = 0; i < 4; i++) {
            edges[i] = new PointF(pts[i * 2] + x, pts[i * 2 + 1] + y);
        }
        return edges;
    }

    public boolean contains(float touchX, float touchY) {
        // Map touch into unrotated, unscaled coordinate system
        float dx = touchX - x;
        float dy = touchY - y;

        // Invert rotation
        double rad = Math.toRadians(-rotation);
        float unrotX = (float) (dx * Math.cos(rad) - dy * Math.sin(rad));
        float unrotY = (float) (dx * Math.sin(rad) + dy * Math.cos(rad));

        float halfW = Math.abs((width * scaleX) / 2f);
        float halfH = Math.abs((height * scaleY) / 2f);

        return Math.abs(unrotX) <= halfW && Math.abs(unrotY) <= halfH;
    }

    public int getTouchedHandle(float touchX, float touchY, float handleRadius) {
        PointF[] corners = getTransformedCorners();
        // Top-Left: Flip
        if (dist(touchX, touchY, corners[0].x, corners[0].y) <= handleRadius * 1.5f) return HANDLE_FLIP;
        // Top-Right: Rotate
        if (dist(touchX, touchY, corners[1].x, corners[1].y) <= handleRadius * 1.5f) return HANDLE_ROTATE;
        // Bottom-Right: Scale
        if (dist(touchX, touchY, corners[2].x, corners[2].y) <= handleRadius * 1.5f) return HANDLE_SCALE;
        // Bottom-Left: Crop
        if (dist(touchX, touchY, corners[3].x, corners[3].y) <= handleRadius * 1.5f) return HANDLE_CROP;

        PointF[] edges = getTransformedEdgeMidpoints();
        // Top: Stretch Top
        if (dist(touchX, touchY, edges[0].x, edges[0].y) <= handleRadius * 1.3f) return HANDLE_STRETCH_TOP;
        // Right: Stretch Right
        if (dist(touchX, touchY, edges[1].x, edges[1].y) <= handleRadius * 1.3f) return HANDLE_STRETCH_RIGHT;
        // Bottom: Stretch Bottom
        if (dist(touchX, touchY, edges[2].x, edges[2].y) <= handleRadius * 1.3f) return HANDLE_STRETCH_BOTTOM;
        // Left: Stretch Left
        if (dist(touchX, touchY, edges[3].x, edges[3].y) <= handleRadius * 1.3f) return HANDLE_STRETCH_LEFT;

        return HANDLE_NONE;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.hypot(dx, dy);
    }
}

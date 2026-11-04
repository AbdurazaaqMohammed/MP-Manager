package io.github.abdurazaaqmohammed.freestyle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.mp.mp_manager.R;

import java.util.ArrayList;
import java.util.List;

public class FreeStyleCanvasView extends View {

    private final List<StickerLayer> layers = new ArrayList<>();
    private StickerLayer activeLayer = null;

    private Paint borderPaint;
    private Paint handlePaint;
    private Paint handleStrokePaint;
    private Paint guideLinePaint;
    private Paint stretchDotPaint;
    private Paint stretchDotStrokePaint;

    private Drawable rotateDrawable;
    private Drawable scaleDrawable;
    private Drawable flipDrawable;
    private Drawable cropDrawable;

    private float handleRadius = 40f; // in px
    private float stretchDotRadius = 20f;

    // Interaction states
    private static final int MODE_NONE = 0;
    private static final int MODE_DRAG = 1;
    private static final int MODE_ROTATE = 2;
    private static final int MODE_SCALE = 3;
    private static final int MODE_STRETCH_TOP = 4;
    private static final int MODE_STRETCH_BOTTOM = 5;
    private static final int MODE_STRETCH_LEFT = 6;
    private static final int MODE_STRETCH_RIGHT = 7;
    private static final int MODE_CROP_SELECT = 8;

    private int currentMode = MODE_NONE;
    private int activeHandle = StickerLayer.HANDLE_NONE;

    private float lastTouchX;
    private float lastTouchY;
    private float initialAngle;
    private float initialDist;
    private float initialScaleX;
    private float initialScaleY;

    // Smart alignment guides
    private boolean showSnapX = false;
    private boolean showSnapY = false;
    private float snapLineX = -1f;
    private float snapLineY = -1f;
    private static final float SNAP_THRESHOLD = 18f; // pixels

    // Crop Mode State
    private boolean isCropMode = false;
    private RectF cropRect = new RectF();
    private Paint cropOverlayPaint;
    private Paint cropBoxPaint;
    private int cropTouchEdge = 0; // 1: L, 2: T, 3: R, 4: B, 5: inside move
    public interface OnCropRequestedListener {
        void onCropRequested(StickerLayer layer);
    }
    private OnCropRequestedListener cropListener;

    public FreeStyleCanvasView(Context context) {
        super(context);
        init();
    }

    public FreeStyleCanvasView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setBackgroundColor(Color.WHITE);

        float density = getResources().getDisplayMetrics().density;
        handleRadius = 22f * density;
        stretchDotRadius = 10f * density;

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.parseColor("#009688")); // Teal
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2.5f * density);
        borderPaint.setPathEffect(new DashPathEffect(new float[]{16f, 10f}, 0));

        handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handlePaint.setColor(Color.WHITE);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setShadowLayer(4f * density, 0, 2f * density, 0x44000000);

        handleStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        handleStrokePaint.setColor(Color.parseColor("#009688"));
        handleStrokePaint.setStyle(Paint.Style.STROKE);
        handleStrokePaint.setStrokeWidth(2f * density);

        stretchDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stretchDotPaint.setColor(Color.parseColor("#00BCD4"));
        stretchDotPaint.setStyle(Paint.Style.FILL);
        stretchDotPaint.setShadowLayer(3f * density, 0, 1f * density, 0x44000000);

        stretchDotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        stretchDotStrokePaint.setColor(Color.WHITE);
        stretchDotStrokePaint.setStyle(Paint.Style.STROKE);
        stretchDotStrokePaint.setStrokeWidth(2f * density);

        guideLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guideLinePaint.setColor(Color.parseColor("#FF5722")); // Deep Orange alignment line
        guideLinePaint.setStyle(Paint.Style.STROKE);
        guideLinePaint.setStrokeWidth(2f * density);
        guideLinePaint.setPathEffect(new DashPathEffect(new float[]{12f, 8f}, 0));

        cropOverlayPaint = new Paint();
        cropOverlayPaint.setColor(0x99000000);

        cropBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cropBoxPaint.setColor(Color.WHITE);
        cropBoxPaint.setStyle(Paint.Style.STROKE);
        cropBoxPaint.setStrokeWidth(3f * density);

        // Load handle icons
        rotateDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_rotate_cw_24);
        scaleDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_scale_handle_24);
        flipDrawable = ContextCompat.getDrawable(getContext(), R.drawable.flip_24px);
        cropDrawable = ContextCompat.getDrawable(getContext(), R.drawable.ic_crop_24px);

        setLayerType(LAYER_TYPE_SOFTWARE, null); // For shadow layer rendering
    }

    public void setOnCropRequestedListener(OnCropRequestedListener listener) {
        this.cropListener = listener;
    }

    public void addLayer(Bitmap bitmap) {
        if (bitmap == null) return;
        post(() -> {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                w = 1080;
                h = 1920;
            }
            float maxW = w * 0.7f;
            float maxH = h * 0.5f;

            // Stagger position slightly for new images
            float offset = (layers.size() % 6) * 40f;
            float cx = w / 2f + offset - 80f;
            float cy = h / 2f + offset - 80f;

            StickerLayer layer = new StickerLayer(bitmap, cx, cy, maxW, maxH);
            layers.add(layer);
            activeLayer = layer;
            invalidate();
        });
    }

    public List<StickerLayer> getLayers() {
        return layers;
    }

    public StickerLayer getActiveLayer() {
        return activeLayer;
    }

    public void setActiveLayer(StickerLayer layer) {
        this.activeLayer = layer;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // Draw all layers in order
        for (StickerLayer layer : layers) {
            layer.draw(canvas, imagePaint);
        }

        // Draw guidelines if snapping
        if (showSnapX && snapLineX >= 0) {
            canvas.drawLine(snapLineX, 0, snapLineX, getHeight(), guideLinePaint);
        }
        if (showSnapY && snapLineY >= 0) {
            canvas.drawLine(0, snapLineY, getWidth(), snapLineY, guideLinePaint);
        }

        // Draw controls for active layer
        if (activeLayer != null && !isCropMode) {
            drawLayerControls(canvas, activeLayer);
        }

        // If in Crop Mode, draw crop overlay
        if (isCropMode && activeLayer != null) {
            drawCropOverlay(canvas);
        }
    }

    private void drawLayerControls(Canvas canvas, StickerLayer layer) {
        PointF[] corners = layer.getTransformedCorners();
        PointF[] edges = layer.getTransformedEdgeMidpoints();

        // 1. Draw dashed bounding box
        Path path = new Path();
        path.moveTo(corners[0].x, corners[0].y);
        path.lineTo(corners[1].x, corners[1].y);
        path.lineTo(corners[2].x, corners[2].y);
        path.lineTo(corners[3].x, corners[3].y);
        path.close();
        canvas.drawPath(path, borderPaint);

        // 2. Draw 4 edge stretch midpoint handles (Cyan dots)
        for (PointF edge : edges) {
            canvas.drawCircle(edge.x, edge.y, stretchDotRadius, stretchDotPaint);
            canvas.drawCircle(edge.x, edge.y, stretchDotRadius, stretchDotStrokePaint);
        }

        // 3. Draw 4 corner handles
        // Top-Left: Flip
        drawHandle(canvas, corners[0].x, corners[0].y, flipDrawable);

        // Top-Right: Rotate
        drawHandle(canvas, corners[1].x, corners[1].y, rotateDrawable);

        // Bottom-Right: Scale
        drawHandle(canvas, corners[2].x, corners[2].y, scaleDrawable);

        // Bottom-Left: Crop
        drawHandle(canvas, corners[3].x, corners[3].y, cropDrawable);
    }

    private void drawHandle(Canvas canvas, float cx, float cy, Drawable icon) {
        canvas.drawCircle(cx, cy, handleRadius, handlePaint);
        canvas.drawCircle(cx, cy, handleRadius, handleStrokePaint);

        if (icon != null) {
            int iconSize = (int) (handleRadius * 1.15f);
            int left = (int) (cx - iconSize / 2f);
            int top = (int) (cy - iconSize / 2f);
            icon.setBounds(left, top, left + iconSize, top + iconSize);
            icon.draw(canvas);
        }
    }

    private void drawCropOverlay(Canvas canvas) {
        // Dim outer areas
        canvas.drawRect(0, 0, getWidth(), cropRect.top, cropOverlayPaint);
        canvas.drawRect(0, cropRect.bottom, getWidth(), getHeight(), cropOverlayPaint);
        canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, cropOverlayPaint);
        canvas.drawRect(cropRect.right, cropRect.top, getWidth(), cropRect.bottom, cropOverlayPaint);

        // White border around crop box
        canvas.drawRect(cropRect, cropBoxPaint);

        // Grid lines (rule of thirds)
        Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0x88FFFFFF);
        gridPaint.setStrokeWidth(2f);
        float thirdW = cropRect.width() / 3f;
        float thirdH = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + thirdW * 2, cropRect.top, cropRect.left + thirdW * 2, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH * 2, cropRect.right, cropRect.top + thirdH * 2, gridPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX();
        float touchY = event.getY();

        if (isCropMode) {
            return handleCropTouch(event, touchX, touchY);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = touchX;
                lastTouchY = touchY;
                showSnapX = false;
                showSnapY = false;

                // Check active layer handles first
                if (activeLayer != null) {
                    activeHandle = activeLayer.getTouchedHandle(touchX, touchY, handleRadius);
                    if (activeHandle != StickerLayer.HANDLE_NONE) {
                        handleActionDown(activeHandle, touchX, touchY);
                        return true;
                    }
                }

                // Check hit on active layer body
                if (activeLayer != null && activeLayer.contains(touchX, touchY)) {
                    currentMode = MODE_DRAG;
                    // Bring to front
                    layers.remove(activeLayer);
                    layers.add(activeLayer);
                    invalidate();
                    return true;
                }

                // Check other layers from top to bottom
                StickerLayer hit = null;
                for (int i = layers.size() - 1; i >= 0; i--) {
                    StickerLayer layer = layers.get(i);
                    if (layer.contains(touchX, touchY)) {
                        hit = layer;
                        break;
                    }
                }

                if (hit != null) {
                    activeLayer = hit;
                    currentMode = MODE_DRAG;
                    layers.remove(activeLayer);
                    layers.add(activeLayer);
                    invalidate();
                    return true;
                }

                // Tap outside clears selection
                activeLayer = null;
                currentMode = MODE_NONE;
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = touchX - lastTouchX;
                float dy = touchY - lastTouchY;

                if (activeLayer == null) return false;

                if (currentMode == MODE_DRAG) {
                    float newX = activeLayer.x + dx;
                    float newY = activeLayer.y + dy;

                    // Snapping logic with other layers and canvas center
                    SnapResult snap = computeSnap(newX, newY, activeLayer);
                    activeLayer.x = snap.x;
                    activeLayer.y = snap.y;

                    showSnapX = snap.snappedX;
                    showSnapY = snap.snappedY;
                    snapLineX = snap.lineX;
                    snapLineY = snap.lineY;

                    if (snap.triggeredVibration) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    }

                    lastTouchX = touchX;
                    lastTouchY = touchY;
                    invalidate();
                } else if (currentMode == MODE_ROTATE) {
                    // Angle relative to center
                    float currentAngle = (float) Math.toDegrees(Math.atan2(touchY - activeLayer.y, touchX - activeLayer.x));
                    float deltaAngle = currentAngle - initialAngle;
                    activeLayer.rotation += deltaAngle;
                    initialAngle = currentAngle;
                    invalidate();
                } else if (currentMode == MODE_SCALE) {
                    // Distance from center
                    float currentDist = (float) Math.hypot(touchX - activeLayer.x, touchY - activeLayer.y);
                    if (initialDist > 0) {
                        float ratio = currentDist / initialDist;
                        float newScaleX = initialScaleX * ratio;
                        float newScaleY = initialScaleY * ratio;
                        // Avoid inverted or overly tiny scale
                        if (newScaleX > 0.1f && newScaleY > 0.1f) {
                            activeLayer.scaleX = newScaleX;
                            activeLayer.scaleY = newScaleY;
                        }
                    }
                    invalidate();
                } else if (currentMode >= MODE_STRETCH_TOP && currentMode <= MODE_STRETCH_RIGHT) {
                    handleStretchMove(touchX, touchY);
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                showSnapX = false;
                showSnapY = false;
                currentMode = MODE_NONE;
                activeHandle = StickerLayer.HANDLE_NONE;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void handleActionDown(int handle, float touchX, float touchY) {
        switch (handle) {
            case StickerLayer.HANDLE_FLIP:
                activeLayer.flippedH = !activeLayer.flippedH;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                break;
            case StickerLayer.HANDLE_ROTATE:
                currentMode = MODE_ROTATE;
                initialAngle = (float) Math.toDegrees(Math.atan2(touchY - activeLayer.y, touchX - activeLayer.x));
                break;
            case StickerLayer.HANDLE_SCALE:
                currentMode = MODE_SCALE;
                initialDist = (float) Math.hypot(touchX - activeLayer.x, touchY - activeLayer.y);
                initialScaleX = activeLayer.scaleX;
                initialScaleY = activeLayer.scaleY;
                break;
            case StickerLayer.HANDLE_CROP:
                startCropMode();
                break;
            case StickerLayer.HANDLE_STRETCH_TOP:
                currentMode = MODE_STRETCH_TOP;
                initialScaleY = activeLayer.scaleY;
                break;
            case StickerLayer.HANDLE_STRETCH_BOTTOM:
                currentMode = MODE_STRETCH_BOTTOM;
                initialScaleY = activeLayer.scaleY;
                break;
            case StickerLayer.HANDLE_STRETCH_LEFT:
                currentMode = MODE_STRETCH_LEFT;
                initialScaleX = activeLayer.scaleX;
                break;
            case StickerLayer.HANDLE_STRETCH_RIGHT:
                currentMode = MODE_STRETCH_RIGHT;
                initialScaleX = activeLayer.scaleX;
                break;
        }
    }

    private void handleStretchMove(float touchX, float touchY) {
        // Project touch into layer coordinate frame (unrotated)
        float dx = touchX - activeLayer.x;
        float dy = touchY - activeLayer.y;
        double rad = Math.toRadians(-activeLayer.rotation);
        float localX = (float) (dx * Math.cos(rad) - dy * Math.sin(rad));
        float localY = (float) (dx * Math.sin(rad) + dy * Math.cos(rad));

        float baseHalfW = activeLayer.width / 2f;
        float baseHalfH = activeLayer.height / 2f;

        if (currentMode == MODE_STRETCH_LEFT || currentMode == MODE_STRETCH_RIGHT) {
            float dist = Math.abs(localX);
            if (baseHalfW > 0) {
                float newScaleX = dist / baseHalfW;
                if (newScaleX > 0.1f) activeLayer.scaleX = newScaleX;
            }
        } else if (currentMode == MODE_STRETCH_TOP || currentMode == MODE_STRETCH_BOTTOM) {
            float dist = Math.abs(localY);
            if (baseHalfH > 0) {
                float newScaleY = dist / baseHalfH;
                if (newScaleY > 0.1f) activeLayer.scaleY = newScaleY;
            }
        }
    }

    private static class SnapResult {
        float x;
        float y;
        boolean snappedX;
        boolean snappedY;
        float lineX;
        float lineY;
        boolean triggeredVibration;
    }

    private SnapResult computeSnap(float targetX, float targetY, StickerLayer layer) {
        SnapResult result = new SnapResult();
        result.x = targetX;
        result.y = targetY;

        float bestDiffX = SNAP_THRESHOLD;
        float bestDiffY = SNAP_THRESHOLD;

        // 1. Check canvas center
        float canvasCenterX = getWidth() / 2f;
        float canvasCenterY = getHeight() / 2f;

        if (Math.abs(targetX - canvasCenterX) < bestDiffX) {
            result.x = canvasCenterX;
            result.snappedX = true;
            result.lineX = canvasCenterX;
            bestDiffX = Math.abs(targetX - canvasCenterX);
        }

        if (Math.abs(targetY - canvasCenterY) < bestDiffY) {
            result.y = canvasCenterY;
            result.snappedY = true;
            result.lineY = canvasCenterY;
            bestDiffY = Math.abs(targetY - canvasCenterY);
        }

        // 2. Check alignment with all other layers
        for (StickerLayer other : layers) {
            if (other == layer) continue;

            // X center alignment (vertical guide line)
            float diffX = Math.abs(targetX - other.x);
            if (diffX < bestDiffX) {
                result.x = other.x;
                result.snappedX = true;
                result.lineX = other.x;
                bestDiffX = diffX;
            }

            // Y center alignment (horizontal guide line)
            float diffY = Math.abs(targetY - other.y);
            if (diffY < bestDiffY) {
                result.y = other.y;
                result.snappedY = true;
                result.lineY = other.y;
                bestDiffY = diffY;
            }
        }

        if (result.snappedX || result.snappedY) {
            if (!showSnapX && !showSnapY) {
                result.triggeredVibration = true;
            }
        }
        return result;
    }

    // --- CROP MODE IMPLEMENTATION ---

    public void startCropMode() {
        if (activeLayer == null || activeLayer.bitmap == null) return;
        isCropMode = true;

        // Reset rotation temporarily for straightforward rectangular crop
        float halfW = (activeLayer.width * activeLayer.scaleX) / 2f;
        float halfH = (activeLayer.height * activeLayer.scaleY) / 2f;
        cropRect.set(activeLayer.x - halfW, activeLayer.y - halfH, activeLayer.x + halfW, activeLayer.y + halfH);

        if (cropListener != null) {
            cropListener.onCropRequested(activeLayer);
        }
        invalidate();
    }

    public boolean isCropMode() {
        return isCropMode;
    }

    public void applyCrop() {
        if (!isCropMode || activeLayer == null || activeLayer.bitmap == null) return;

        float layerHalfW = (activeLayer.width * activeLayer.scaleX) / 2f;
        float layerHalfH = (activeLayer.height * activeLayer.scaleY) / 2f;
        float layerLeft = activeLayer.x - layerHalfW;
        float layerTop = activeLayer.y - layerHalfH;
        float layerW = layerHalfW * 2f;
        float layerH = layerHalfH * 2f;

        // Calculate normalized crop coordinates within activeLayer (0.0 to 1.0)
        float normL = Math.max(0f, (cropRect.left - layerLeft) / layerW);
        float normT = Math.max(0f, (cropRect.top - layerTop) / layerH);
        float normR = Math.min(1f, (cropRect.right - layerLeft) / layerW);
        float normB = Math.min(1f, (cropRect.bottom - layerTop) / layerH);

        if (normR > normL && normB > normT) {
            Bitmap src = activeLayer.bitmap;
            int cropX = Math.round(normL * src.getWidth());
            int cropY = Math.round(normT * src.getHeight());
            int cropW = Math.round((normR - normL) * src.getWidth());
            int cropH = Math.round((normB - normT) * src.getHeight());

            cropX = Math.max(0, Math.min(cropX, src.getWidth() - 1));
            cropY = Math.max(0, Math.min(cropY, src.getHeight() - 1));
            cropW = Math.max(1, Math.min(cropW, src.getWidth() - cropX));
            cropH = Math.max(1, Math.min(cropH, src.getHeight() - cropY));

            try {
                Bitmap cropped = Bitmap.createBitmap(src, cropX, cropY, cropW, cropH);
                activeLayer.bitmap = cropped;
                activeLayer.width = cropRect.width() / activeLayer.scaleX;
                activeLayer.height = cropRect.height() / activeLayer.scaleY;
                activeLayer.x = cropRect.centerX();
                activeLayer.y = cropRect.centerY();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        isCropMode = false;
        invalidate();
    }

    public void cancelCrop() {
        isCropMode = false;
        invalidate();
    }

    private boolean handleCropTouch(MotionEvent event, float touchX, float touchY) {
        float edgeMargin = 40f;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = touchX;
                lastTouchY = touchY;

                // Detect edge or corner of cropRect
                boolean nearLeft = Math.abs(touchX - cropRect.left) < edgeMargin;
                boolean nearRight = Math.abs(touchX - cropRect.right) < edgeMargin;
                boolean nearTop = Math.abs(touchY - cropRect.top) < edgeMargin;
                boolean nearBottom = Math.abs(touchY - cropRect.bottom) < edgeMargin;

                if (nearLeft) cropTouchEdge = 1;
                else if (nearTop) cropTouchEdge = 2;
                else if (nearRight) cropTouchEdge = 3;
                else if (nearBottom) cropTouchEdge = 4;
                else if (cropRect.contains(touchX, touchY)) cropTouchEdge = 5;
                else cropTouchEdge = 0;
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = touchX - lastTouchX;
                float dy = touchY - lastTouchY;

                if (cropTouchEdge == 1) { // Left
                    cropRect.left = Math.min(touchX, cropRect.right - 50f);
                } else if (cropTouchEdge == 2) { // Top
                    cropRect.top = Math.min(touchY, cropRect.bottom - 50f);
                } else if (cropTouchEdge == 3) { // Right
                    cropRect.right = Math.max(touchX, cropRect.left + 50f);
                } else if (cropTouchEdge == 4) { // Bottom
                    cropRect.bottom = Math.max(touchY, cropRect.top + 50f);
                } else if (cropTouchEdge == 5) { // Inside move
                    cropRect.offset(dx, dy);
                }

                lastTouchX = touchX;
                lastTouchY = touchY;
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cropTouchEdge = 0;
                return true;
        }
        return true;
    }

    /**
     * Render final canvas output to a clean full-res bitmap
     */
    public Bitmap renderToBitmap() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return null;

        Bitmap output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        for (StickerLayer layer : layers) {
            layer.draw(canvas, paint);
        }
        return output;
    }
}

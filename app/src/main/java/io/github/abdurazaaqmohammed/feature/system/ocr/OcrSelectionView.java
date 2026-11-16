package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrResult;

/** Fit-center bitmap, crop selection and tappable OCR word geometry in image coordinates. */
final class OcrSelectionView extends View {
    interface Listener { void selectionChanged(String text, boolean hasSelection); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF imageBounds = new RectF();
    private final RectF region = new RectF();
    private final Set<OcrResult.Word> selected = new HashSet<>();
    private final GestureDetector gestures;
    private final ScaleGestureDetector zoomGestures;
    private Bitmap bitmap;
    private OcrResult result;
    private Listener listener;
    private float scale = 1, startX, startY;
    private boolean dragging;
    private float zoom = 1, offsetX, offsetY, lastX, lastY;
    private boolean panning, multiTouch;
    private final int primary;

    OcrSelectionView(Context context) {
        super(context);
        primary = MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.BLUE);
        setFocusable(true);
        setClickable(true);
        setContentDescription(context.getString(io.github.abdurazaaqmohammed.MPManager.R.string.ocr_select_region));
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onDoubleTap(MotionEvent event) {
                zoomTo(zoom > 1.5f ? 1 : 2.5f, event.getX(), event.getY());
                return true;
            }
            @Override public boolean onSingleTapUp(MotionEvent event) {
                if (result == null) return false;
                OcrResult.Word word = wordAt(imageX(event.getX()), imageY(event.getY()));
                if (word != null) {
                    if (!selected.remove(word)) selected.add(word);
                    notifySelection();
                    invalidate();
                    performClick();
                }
                return true;
            }
            @Override public void onLongPress(MotionEvent event) {
                if (result == null) return;
                float x = imageX(event.getX()), y = imageY(event.getY());
                for (OcrResult.Line line : result.lines) {
                    if (!line.box().contains(x, y)) continue;
                    if (selected.containsAll(line.words())) selected.removeAll(line.words());
                    else selected.addAll(line.words());
                    notifySelection();
                    invalidate();
                    break;
                }
            }
        });
        zoomGestures = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                zoomTo(zoom * detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
    }

    void setListener(Listener listener) { this.listener = listener; }
    void setImage(Bitmap image) {
        bitmap = image;
        result = null;
        selected.clear();
        zoom = 1;
        offsetX = offsetY = 0;
        if (image != null) region.set(0, 0, image.getWidth(), image.getHeight());
        requestLayout();
        invalidate();
    }
    void setResult(OcrResult value) {
        result = value;
        selected.clear();
        invalidate();
    }
    void selectAll() {
        selected.clear();
        if (result != null) for (OcrResult.Line line : result.lines) selected.addAll(line.words());
        notifySelection();
        invalidate();
    }
    void clearSelection() { selected.clear(); notifySelection(); invalidate(); }
    void fullImage() {
        if (bitmap != null) region.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        invalidate();
    }
    Rect region() {
        Rect rect = new Rect();
        region.roundOut(rect);
        return rect;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null || bitmap.isRecycled()) return;
        updateImageBounds();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE); // Reset tint/alpha left by overlay geometry.
        canvas.drawBitmap(bitmap, null, imageBounds, paint);
        if (result == null) {
            RectF crop = screenRect(region);
            paint.setColor(0x88000000);
            canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, crop.top, paint);
            canvas.drawRect(imageBounds.left, crop.bottom, imageBounds.right, imageBounds.bottom, paint);
            canvas.drawRect(imageBounds.left, crop.top, crop.left, crop.bottom, paint);
            canvas.drawRect(crop.right, crop.top, imageBounds.right, crop.bottom, paint);
            paint.setColor(primary);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawRect(crop, paint);
            paint.setStyle(Paint.Style.FILL);
            for (float x : new float[]{crop.left, crop.right}) for (float y : new float[]{crop.top, crop.bottom}) {
                canvas.drawCircle(x, y, dp(4), paint);
            }
        } else {
            for (OcrResult.Line line : result.lines) {
                for (OcrResult.Word word : line.words()) {
                    RectF bounds = screenRect(new RectF(word.box().left(), word.box().top(), word.box().right(), word.box().bottom()));
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(selected.contains(word) ? (primary & 0xffffff) | 0x88000000 : 0x18ffffff);
                    canvas.drawRoundRect(bounds, dp(2), dp(2), paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(dp(1));
                    paint.setColor(selected.contains(word) ? primary : 0xaa1976d2);
                    canvas.drawRoundRect(bounds, dp(2), dp(2), paint);
                }
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) return false;
        zoomGestures.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            panning = false;
            multiTouch = false;
            lastX = event.getX(); lastY = event.getY();
        }
        if (event.getPointerCount() > 1) {
            getParent().requestDisallowInterceptTouchEvent(true);
            multiTouch = true;
            dragging = false;
            return true;
        }
        if (multiTouch) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(false);
                if (region.width() < 8 || region.height() < 8) fullImage();
            }
            return true;
        }
        if (result != null) {
            if (zoom > 1) {
                getParent().requestDisallowInterceptTouchEvent(true);
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE
                        && (panning || Math.hypot(event.getX() - lastX, event.getY() - lastY)
                        > ViewConfiguration.get(getContext()).getScaledTouchSlop())) {
                    panning = true;
                    offsetX += event.getX() - lastX; offsetY += event.getY() - lastY;
                    lastX = event.getX(); lastY = event.getY();
                    updateImageBounds();
                    invalidate();
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP) getParent().requestDisallowInterceptTouchEvent(false);
                if (panning) return true;
            }
            return gestures.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!imageBounds.contains(event.getX(), event.getY())) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                startX = imageX(event.getX()); startY = imageY(event.getY());
                region.set(startX, startY, startX, startY);
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                float x = imageX(event.getX()), y = imageY(event.getY());
                region.set(Math.min(startX, x), Math.min(startY, y), Math.max(startX, x), Math.max(startY, y));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                if (region.width() < 8 || region.height() < 8) fullImage();
                performClick();
                break;
            default: return false;
        }
        invalidate();
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (result != null) info.setText(result.text());
    }

    private OcrResult.Word wordAt(float x, float y) {
        OcrResult.Word closest = null;
        float bestArea = Float.MAX_VALUE;
        if (result != null) for (OcrResult.Line line : result.lines) for (OcrResult.Word word : line.words()) {
            if (word.box().contains(x, y) && word.box().width() * word.box().height() < bestArea) {
                bestArea = word.box().width() * word.box().height();
                closest = word;
            }
        }
        return closest;
    }

    private void notifySelection() {
        StringBuilder text = new StringBuilder();
        if (result != null) for (OcrResult.Line line : result.lines) {
            List<String> words = new ArrayList<>();
            for (OcrResult.Word word : line.words()) if (selected.contains(word)) words.add(word.text());
            if (words.isEmpty()) continue;
            if (text.length() > 0) text.append('\n');
            if (selected.containsAll(line.words())) text.append(line.text());
            else text.append(String.join(line.text().contains(" ") ? " " : "", words));
        }
        if (listener != null) listener.selectionChanged(text.toString(), !selected.isEmpty());
    }

    private float imageX(float x) { return Math.max(0, Math.min(bitmap.getWidth(), (x - imageBounds.left) / scale)); }
    private float imageY(float y) { return Math.max(0, Math.min(bitmap.getHeight(), (y - imageBounds.top) / scale)); }
    private RectF screenRect(RectF rect) {
        return new RectF(imageBounds.left + rect.left * scale, imageBounds.top + rect.top * scale,
                imageBounds.left + rect.right * scale, imageBounds.top + rect.bottom * scale);
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }

    private void zoomTo(float value, float focusX, float focusY) {
        float next = Math.max(1, Math.min(6, value));
        float ratio = next / zoom;
        offsetX = offsetX * ratio + (1 - ratio) * (focusX - getWidth() / 2f);
        offsetY = offsetY * ratio + (1 - ratio) * (focusY - getHeight() / 2f);
        zoom = next;
        updateImageBounds();
        invalidate();
    }

    private void updateImageBounds() {
        scale = Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight()) * zoom;
        float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
        float maxX = Math.max(0, (width - getWidth()) / 2), maxY = Math.max(0, (height - getHeight()) / 2);
        offsetX = Math.max(-maxX, Math.min(maxX, offsetX));
        offsetY = Math.max(-maxY, Math.min(maxY, offsetY));
        float left = (getWidth() - width) / 2 + offsetX, top = (getHeight() - height) / 2 + offsetY;
        imageBounds.set(left, top, left + width, top + height);
    }
}

/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.manager;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import io.github.ratul.topactivity.App;
import io.github.ratul.topactivity.services.AccessibilityMonitoringService;
import io.github.ratul.topactivity.services.ViewNodeSnapshot;
import io.github.ratul.topactivity.ui.ViewInspectorDialog;

public class InspectOverlayManager {

    private final Context context;
    private final WindowManager windowManager;
    private InspectOverlayView overlayView;
    private ViewInspectorDialog infoDialog;
    private boolean isInspecting = false;

    public InspectOverlayManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show() {
        if (overlayView != null) return;

        InspectOverlayView overlay = new InspectOverlayView(context, this);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER;

        windowManager.addView(overlay, layoutParams);
        overlayView = overlay;
        isInspecting = true;

        overlay.startDrawingViewBoxes();
    }

    public void inspectViewAt(int x, int y) {
        AccessibilityMonitoringService accessibilityService = AccessibilityMonitoringService.getInstance();
        if (accessibilityService == null) return;

        ViewInspectorDialog existing = infoDialog;
        if (existing != null) {
            existing.hideWindow();

            new Handler(Looper.getMainLooper()).postDelayed(() -> doInspect(x, y, existing, 0), 20);
        } else {
            doInspect(x, y, null, 0);
        }
    }

    private void doInspect(int x, int y, ViewInspectorDialog existing, int retry) {
        AccessibilityMonitoringService accessibilityService = AccessibilityMonitoringService.getInstance();
        if (accessibilityService == null) {
            if (existing != null) existing.showWindow();
            return;
        }

        AccessibilityMonitoringService.FoundView found = accessibilityService.findViewAt(x, y);
        if (found == null) {
            if (existing != null) existing.showWindow();
            return;
        }


        String pkg = found.viewInfo.getPackageName();
        if (pkg != null && pkg.equals(context.getPackageName()) && retry < 3) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> doInspect(x, y, existing, retry + 1), 40);
            return;
        }

        if (overlayView != null) {
            overlayView.setInspectedSnapshot(found.rootSnapshot);
            overlayView.setHighlight(found.viewInfo.getBounds());
        }
        isInspecting = true;

        if (existing != null) {
            existing.updateFoundView(found);
            existing.showWindow();
        } else {
            showInspectorDialog(found);
        }
    }

    public void setHighlight(Rect bounds) {
        if (overlayView != null) overlayView.setHighlight(bounds);
    }

    public boolean hasHighlight() {
        return  (overlayView != null && overlayView.hasHighlight);
    }

    public void clearHighlight() {
        if (overlayView != null) overlayView.clearHighlight();
    }

    public void setInfoDialog(ViewInspectorDialog dialog) {
        this.infoDialog = dialog;
    }

    public void hide() {
        if (infoDialog != null) {
            infoDialog.hideWindow();
            infoDialog = null;
        }
        isInspecting = false;
        if (overlayView != null) {
            overlayView.stopDrawingViewBoxes();
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    public boolean isShowing() {
        return isInspecting;
    }

    private void showInspectorDialog(AccessibilityMonitoringService.FoundView found) {
        new Handler(Looper.getMainLooper()).post(() ->
                new ViewInspectorDialog(App.getInstance(), found, this).show());
    }

    public boolean isInspecting() {
        return isInspecting;
    }

    static class ViewBox {
        final Rect bounds;
        final String className;
        final String resourceId;

        ViewBox(Rect bounds, String className, String resourceId) {
            this.bounds = bounds;
            this.className = className;
            this.resourceId = resourceId;
        }
    }

    static class InspectOverlayView extends FrameLayout {

        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private List<ViewBox> viewBoxes = new ArrayList<>();
        private boolean isDrawing = false;
        private final Runnable drawRunnable = this::drawViewBoxes;
        private int lastViewCount = 0;
        private int insetTop = 0;
        private int insetBottom = 0;
        private final InspectOverlayManager manager;

        private ViewNodeSnapshot inspectedSnapshot;
        private Rect highlightBounds;
        private boolean hasHighlight = false;

        InspectOverlayView(Context context, InspectOverlayManager manager) {
            super(context);
            this.manager = manager;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(Color.GREEN);

            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setColor(Color.TRANSPARENT);

            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(12f);
            textPaint.setFakeBoldText(true);

            textBgPaint.setColor(Color.argb(180, 0, 0, 0));

            highlightPaint.setStyle(Paint.Style.STROKE);
            highlightPaint.setStrokeWidth(4f);
            highlightPaint.setColor(Color.YELLOW);

            setBackgroundColor(Color.TRANSPARENT);

            int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                insetTop = context.getResources().getDimensionPixelSize(resourceId);
            }

            int navResourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (navResourceId > 0) {
                insetBottom = context.getResources().getDimensionPixelSize(navResourceId);
            }
        }

        void setInspectedSnapshot(ViewNodeSnapshot snapshot) {
            this.inspectedSnapshot = snapshot;
            this.lastViewCount = -1;
        }

        void setHighlight(Rect bounds) {
            Rect adjusted = new Rect(bounds);
            if (Build.VERSION.SDK_INT > 32) {
                adjusted.top -= insetTop;
                adjusted.bottom -= insetTop;
            }
            this.highlightBounds = adjusted;
            this.hasHighlight = true;
            invalidate();
        }

        void clearHighlight() {
            this.hasHighlight = false;
            this.highlightBounds = null;
            invalidate();
        }

        void startDrawingViewBoxes() {
            isDrawing = true;
            drawViewBoxes();
        }

        void stopDrawingViewBoxes() {
            isDrawing = false;
            removeCallbacks(drawRunnable);
            viewBoxes = new ArrayList<>();
            invalidate();
        }

        private void drawViewBoxes() {
            if (!isDrawing) return;

            List<ViewBox> newBoxes;
            if (inspectedSnapshot != null) {
                newBoxes = collectViewBoxes(inspectedSnapshot);
            } else {
                AccessibilityMonitoringService accessibilityService = AccessibilityMonitoringService.getInstance();
                if (accessibilityService == null) {
                    postDelayed(drawRunnable, 500);
                    return;
                }
                AccessibilityNodeInfo root = accessibilityService.getRootInActiveWindow();
                if (root == null) {
                    postDelayed(drawRunnable, 500);
                    return;
                }
                newBoxes = collectViewBoxes(root);
                AccessibilityMonitoringService.recycleSubtree(root);
            }

            if (!newBoxes.isEmpty() && newBoxes.size() != lastViewCount) {
                viewBoxes = newBoxes;
                lastViewCount = newBoxes.size();
                invalidate();
            }

            postDelayed(drawRunnable, 500);
        }

        private List<ViewBox> collectViewBoxes(ViewNodeSnapshot node) {
            List<ViewBox> boxes = new ArrayList<>();

            Rect adjustedBounds = new Rect(node.bounds);
            if (Build.VERSION.SDK_INT > 32) {
                // I got no idea what is real cause of this problem this is what work in my android 13 device
                // I checked no problem in android 12 emulator or android 10 phone
                adjustedBounds.top -= insetTop;
                adjustedBounds.bottom -= insetTop;
            }

            if (adjustedBounds.width() > 0 && adjustedBounds.height() > 0) {
                boxes.add(new ViewBox(
                        adjustedBounds,
                        node.className,
                        node.resourceId
                ));
            }

            for (ViewNodeSnapshot child : node.children) {
                boxes.addAll(collectViewBoxes(child));
            }

            return boxes;
        }

        private List<ViewBox> collectViewBoxes(AccessibilityNodeInfo node) {
            List<ViewBox> boxes = new ArrayList<>();
            Rect bounds = new Rect();
            node.getBoundsInScreen(bounds);

            Rect adjustedBounds = new Rect(bounds);
            if (Build.VERSION.SDK_INT > 32) {
                adjustedBounds.top -= insetTop;
                adjustedBounds.bottom -= insetTop;
            }

            if (adjustedBounds.width() > 0 && adjustedBounds.height() > 0) {
                boxes.add(new ViewBox(
                        adjustedBounds,
                        node.getClassName() != null ? node.getClassName().toString() : "Unknown",
                        node.getViewIdResourceName()
                ));
            }

            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue;
                boxes.addAll(collectViewBoxes(child));
                child.recycle();
            }

            return boxes;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!manager.isInspecting()) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_CANCEL:
                    return true;
                case MotionEvent.ACTION_UP:
                    int x = (int) event.getRawX();
                    int y = (int) event.getRawY();
                    manager.inspectViewAt(x, y);
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            for (ViewBox box : viewBoxes) {
                Rect b = box.bounds;

                boolean isSelected = hasHighlight && highlightBounds != null
                        && b.left == highlightBounds.left && b.top == highlightBounds.top
                        && b.right == highlightBounds.right && b.bottom == highlightBounds.bottom;

                if (hasHighlight && !isSelected) {
                    paint.setAlpha(60);
                    textPaint.setAlpha(60);
                    textBgPaint.setAlpha(60);
                } else {
                    paint.setAlpha(255);
                    textPaint.setAlpha(255);
                    textBgPaint.setAlpha(180);
                }

                canvas.drawRect(b.left, b.top, b.right, b.bottom, fillPaint);
                canvas.drawRect(b.left, b.top, b.right, b.bottom, paint);

                String text = box.className;
                int lastDot = text.lastIndexOf('.');
                if (lastDot >= 0) {
                    text = text.substring(lastDot + 1);
                }
                int lastDollar = text.lastIndexOf('$');
                if (lastDollar >= 0) {
                    text = text.substring(lastDollar + 1);
                }

                float textWidth = textPaint.measureText(text);
                float textX = b.left + 4f;
                float textY = b.top - 4f;

                canvas.drawRect(
                        textX - 2f, textY - textPaint.getTextSize() - 2f,
                        textX + textWidth + 4f, textY + 2f,
                        textBgPaint
                );

                canvas.drawText(text, textX, textY, textPaint);

                if (isSelected) {
                    canvas.drawRect(b.left, b.top, b.right, b.bottom, highlightPaint);
                }
            }
        }
    }
}

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
package io.github.codehasan.colorpicker.services;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.core.app.NotificationCompat;
import androidx.core.content.IntentCompat;
import androidx.preference.PreferenceManager;
import androidx.window.layout.WindowMetricsCalculator;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.ServiceState;
import io.github.codehasan.colorpicker.views.MagnifierView;
import io.github.codehasan.colorpicker.views.TargetView;

import java.nio.ByteBuffer;

public class ColorPickerService extends Service implements MagnifierView.OnInteractionListener {

    private WindowManager windowManager;
    private ClipboardManager clipboard;

    private DisplayMetrics displayMetrics;

    // Windows
    private FrameLayout targetLayout;
    private WindowManager.LayoutParams targetParams;
    private TargetView targetView;
    private FrameLayout magnifierLayout;
    private WindowManager.LayoutParams magnifierParams;
    private MagnifierView magnifierView;

    // Screen Capture
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isCapturing = false;

    private Bitmap screenBitmap;
    private int bitmapWidth = 0;
    private int bitmapHeight = 0;

    // Logic Variables
    private int scanX = 0;
    private int scanY = 0;
    private int screenWidth = 0;
    private int screenHeight = 0;

    private final int minGapBetweenEdges = 50;
    private final int maxGapBetweenEdges = 100;

    // Cached preference values
    private final int targetSizeDp = 40;
    private long captureDelayMs = 50L;

    // Preferences
    private SharedPreferences sharedPreferences;

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener =
            (sharedPreferences1, key) -> {
                switch (key) {
                    case PREF_MAGNIFIER_SIZE:
                        if (targetLayout != null && magnifierLayout != null) {
                            removeWindows();
                            setupWindows();
                        }
                        break;

                    case PREF_CAPTURE_SPEED:
                        captureDelayMs = getCaptureDelayMs();
                        break;

                    case PREF_SHOW_GRID_LINES:
                        if (magnifierView != null) {
                            magnifierView.setShowGridLines(getShowGridLines());
                        }
                        break;

                    case PREF_CAPTURE_RANGE:
                        if (targetView != null) {
                            targetView.setCaptureRange(getCaptureRange());
                        }
                        break;
                }
            };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        captureDelayMs = getCaptureDelayMs();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = createNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        int resultCode = intent != null
                ? intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                : Activity.RESULT_CANCELED;
        Intent resultData = intent != null
                ? IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent.class)
                : null;

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            setupWindows();
            startScreenCapture(resultCode, resultData);
            // Set state to true AFTER service is actually initialized
            ServiceState.getInstance().setColorPickerRunning(true);
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @SuppressLint("ClickableViewAccessibility")
    @SuppressWarnings("deprecation")
    private void setupWindows() {
        displayMetrics = new DisplayMetrics();
        android.graphics.Rect bounds =
                WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this).getBounds();
        displayMetrics.widthPixels = bounds.width();
        displayMetrics.heightPixels = bounds.height();

        int[] size = getLogicalFullScreenSize();
        screenWidth = size[0];
        screenHeight = size[1];

        int targetSizePx = Extensions.dp2px(this, targetSizeDp);
        int magnifierSizePx = Extensions.dp2px(this, getMagnifierSizeDp());

        // Create Target View
        targetLayout = new FrameLayout(this);
        targetView = new TargetView(this);
        targetView.setCaptureRange(getCaptureRange());
        targetLayout.addView(
                targetView,
                new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                )
        );

        targetParams = createWindowLayoutParams();
        targetParams.width = targetSizePx;
        targetParams.height = targetSizePx;
        targetParams.x = (screenWidth - targetSizePx) / 2;
        targetParams.y = (screenHeight - targetSizePx) / 2;

        // Create Magnifier View
        magnifierLayout = new FrameLayout(this);
        magnifierView = new MagnifierView(this);
        magnifierView.setListener(this);
        magnifierView.setShowGridLines(getShowGridLines());

        magnifierLayout.addView(
                magnifierView,
                new FrameLayout.LayoutParams(magnifierSizePx, magnifierSizePx)
        );

        magnifierParams = createWindowLayoutParams();
        magnifierParams.x = (screenWidth - magnifierSizePx) / 2;
        magnifierParams.y = targetParams.y - minGapBetweenEdges;

        addTargetDragListener();
        addMagnifierFineTuneListener();

        windowManager.addView(targetLayout, targetParams);
        windowManager.addView(magnifierLayout, magnifierParams);

        targetView.post(() -> {
            updateScanCoordinates();

            float tRadius = targetSizePx / 2f;
            repositionMagnifier(
                    targetParams.x + tRadius,
                    targetParams.y + tRadius,
                    tRadius,
                    magnifierSizePx
            );
            windowManager.updateViewLayout(magnifierLayout, magnifierParams);
        });
    }

    private void removeWindows() {
        if (targetLayout != null) windowManager.removeView(targetLayout);
        if (magnifierLayout != null) windowManager.removeView(magnifierLayout);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addTargetDragListener() {
        final int[] init = new int[2];
        final float[] touch = new float[2];

        targetLayout.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    init[0] = targetParams.x;
                    init[1] = targetParams.y;
                    touch[0] = event.getRawX();
                    touch[1] = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    targetParams.x = init[0] + (int) (event.getRawX() - touch[0]);
                    targetParams.y = init[1] + (int) (event.getRawY() - touch[1]);

                    boolean magChanged = checkDistanceAndRules();

                    windowManager.updateViewLayout(targetLayout, targetParams);
                    if (magChanged) {
                        windowManager.updateViewLayout(magnifierLayout, magnifierParams);
                    }

                    updateScanCoordinates();
                    return true;

                default:
                    return false;
            }
        });
    }

    private boolean checkDistanceAndRules() {
        int tSize = targetLayout.getWidth();
        int mSize = magnifierLayout.getWidth(); // Assuming square

        // Centers
        float tx = targetParams.x + tSize / 2f;
        float ty = targetParams.y + tSize / 2f;
        float mx = magnifierParams.x + mSize / 2f;
        float my = magnifierParams.y + mSize / 2f;

        // Calculate Distance between CENTERS
        float dx = mx - tx;
        float dy = my - ty;
        float centerDistance = (float) Math.sqrt(dx * dx + dy * dy);

        // Radii
        float tRadius = tSize / 2f;
        float mRadius = mSize / 2f;
        float currentGap = centerDistance - tRadius - mRadius;

        int oldMagX = magnifierParams.x;
        int oldMagY = magnifierParams.y;

        // RULE 1: TOWING (If gap > max, pull magnifier closer)
        if (currentGap > maxGapBetweenEdges) {
            double angle = Math.atan2(dy, dx);
            // Desired distance from center to center = radii + maxGap
            float allowedDist = tRadius + mRadius + maxGapBetweenEdges;

            // New Magnifier Center
            float newMx = (float) (tx + Math.cos(angle) * allowedDist);
            float newMy = (float) (ty + Math.sin(angle) * allowedDist);

            // Convert back to Top-Left for Params
            magnifierParams.x = (int) (newMx - mRadius);
            magnifierParams.y = (int) (newMy - mRadius);
        }

        // RULE 2: COLLISION (If gap < min, reposition/jump)
        if (currentGap < minGapBetweenEdges) {
            repositionMagnifier(tx, ty, tRadius, mSize);
        }

        return magnifierParams.x != oldMagX || magnifierParams.y != oldMagY;
    }

    private void repositionMagnifier(float tx, float ty, float tRadius, int mSize) {
        float mRadius = mSize / 2f;

        int[][] directions = {
                {-1, 0}, // Left
                {0, -1}, // Top
                {1, 0},  // Right
                {0, 1}   // Bottom
        };

        // Distances: Try MAX (ideal) first, then MIN (fallback)
        int[] gapsToCheck = {maxGapBetweenEdges, minGapBetweenEdges};

        for (int gap : gapsToCheck) {
            float distFromCenter = tRadius + gap + mRadius;

            for (int[] dir : directions) {
                int pLeft;
                int pTop;

                // Case 1: Moving Horizontally (Left or Right)
                if (dir[0] != 0) {
                    // Calculate X: Target Center + Direction * Distance - Radius
                    float pCenterX = tx + (dir[0] * distFromCenter);
                    pLeft = (int) (pCenterX - mRadius);

                    // Calculate Y: Align with Target Y, but clamp to screen bounds
                    pTop = clamp((int) (ty - mRadius), 0, screenHeight - mSize);
                }
                // Case 2: Moving Vertically (Top or Bottom)
                else {
                    // Calculate Y: Target Center + Direction * Distance - Radius
                    float pCenterY = ty + (dir[1] * distFromCenter);
                    pTop = (int) (pCenterY - mRadius);

                    // Calculate X: Align with Target X, but clamp to screen bounds
                    pLeft = clamp((int) (tx - mRadius), 0, screenWidth - mSize);
                }

                // Ensure the ENTIRE magnifier is within the screen (0 to width/height)
                boolean fitsHorizontally = (pLeft >= 0) && ((pLeft + mSize) <= screenWidth);
                boolean fitsVertically = (pTop >= 0) && ((pTop + mSize) <= screenHeight);

                if (fitsHorizontally && fitsVertically) {
                    // Valid position found! Apply and return immediately.
                    magnifierParams.x = pLeft;
                    magnifierParams.y = pTop;
                    return;
                }
            }
        }
        // If no position fits (e.g., target is deep in a corner),
        // the magnifier stays in its last known valid position.
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addMagnifierFineTuneListener() {
        final float[] touch = new float[2];
        // Accumulate fractional movement for sub-pixel precision
        final float[] accumulated = new float[2];

        magnifierView.setOnTouchListener((view, event) -> {
            view.onTouchEvent(event); // Allow clicking buttons

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touch[0] = event.getRawX();
                    touch[1] = event.getRawY();
                    accumulated[0] = 0f;
                    accumulated[1] = 0f;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - touch[0];
                    float dy = event.getRawY() - touch[1];

                    // Accumulate fractional movement
                    accumulated[0] += dx * 0.1f;
                    accumulated[1] += dy * 0.1f;

                    // Apply integer portion, keep fractional part
                    int moveX = (int) accumulated[0];
                    int moveY = (int) accumulated[1];
                    accumulated[0] -= moveX;
                    accumulated[1] -= moveY;

                    targetParams.x += moveX;
                    targetParams.y += moveY;

                    // Check rules even during fine tuning to keep constraints valid
                    boolean magChanged = checkDistanceAndRules();

                    windowManager.updateViewLayout(targetLayout, targetParams);
                    // Only update magnifier if its position actually changed
                    if (magChanged) {
                        windowManager.updateViewLayout(magnifierLayout, magnifierParams);
                    }
                    updateScanCoordinates();

                    touch[0] = event.getRawX();
                    touch[1] = event.getRawY();
                    return true;

                default:
                    return true;
            }
        });
    }

    @SuppressWarnings("deprecation")
    private WindowManager.LayoutParams createWindowLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        return params;
    }

    private int[] getLogicalFullScreenSize() {
        DisplayMetrics logicalMetrics = getResources().getDisplayMetrics();

        // If widths match, the device isn't scaling. Use real metrics to include system bars.
        if (displayMetrics.widthPixels == logicalMetrics.widthPixels) {
            return new int[]{displayMetrics.widthPixels, displayMetrics.heightPixels};
        }

        // If different, the device is scaling. We must scale the 'Real' height
        // down to match the 'Logical' width.
        float scaleFactor =
                (float) logicalMetrics.widthPixels / (float) displayMetrics.widthPixels;
        int scaledHeight = (int) (displayMetrics.heightPixels * scaleFactor);

        return new int[]{logicalMetrics.widthPixels, scaledHeight};
    }

    private void updateScanCoordinates() {
        android.graphics.PointF offset = targetView.getScanOffset();

        scanX = (int) offset.x;
        scanY = (int) offset.y;
    }

    @SuppressLint("WrongConstant")
    private void startScreenCapture(int resultCode, Intent resultData) {
        MediaProjectionManager mpManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData);

        mediaProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, handler);

        int[] size = getLogicalFullScreenSize();
        int width = size[0];
        int height = size[1];
        int density = getResources().getDisplayMetrics().densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
        );

        isCapturing = true;
        captureLoop();
    }

    private void captureLoop() {
        if (!isCapturing) return;
        try {
            Image image = imageReader != null ? imageReader.acquireLatestImage() : null;
            if (image != null) {
                processImage(image);
            }
        } catch (Exception e) {
            Log.e("ColorPickerService", "Failed to process image", e);
        }
        handler.postDelayed(this::captureLoop, captureDelayMs);
    }

    @SuppressLint("WrongConstant")
    private void processImage(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();

            int requiredWidth = image.getWidth() + rowPadding / pixelStride;
            int requiredHeight = image.getHeight();

            Bitmap bitmap = screenBitmap;
            if (bitmap == null || bitmap.getWidth() != requiredWidth
                    || bitmap.getHeight() != requiredHeight) {
                bitmap = androidx.core.graphics.BitmapKt.createBitmap(requiredWidth, requiredHeight, Bitmap.Config.ARGB_8888 );
                if (screenBitmap != null) {
                    screenBitmap.recycle();
                }
                screenBitmap = bitmap;
                bitmapWidth = requiredWidth;
                bitmapHeight = requiredHeight;
            }

            bitmap.copyPixelsFromBuffer(buffer);

            int safeX = clamp(scanX, 0, bitmap.getWidth() - 1);
            int safeY = clamp(scanY, 0, bitmap.getHeight() - 1);

            int pixelColor = bitmap.getPixel(safeX, safeY);
            String hexColor = String.format("#%06X", (0xFFFFFF & pixelColor));

            int cropSize = targetView.getSafeCropSize();

            int cropX = clamp(safeX - cropSize / 2, 0, bitmap.getWidth() - cropSize);
            int cropY = clamp(safeY - cropSize / 2, 0, bitmap.getHeight() - cropSize);

            Bitmap crop = androidx.core.graphics.BitmapKt.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[cropSize * cropSize];
            bitmap.getPixels(pixels, 0, cropSize, cropX, cropY, cropSize, cropSize);
            crop.setPixels(pixels, 0, cropSize, 0, 0, cropSize, cropSize);

            Bitmap finalCrop = crop;
            String finalHex = hexColor;
            int finalX = safeX;
            int finalY = safeY;
            handler.post(() -> magnifierView.updateContent(finalCrop, finalHex, finalX, finalY));
        } finally {
            image.close();
        }
    }

    @Override
    public void onCloseClicked() {
        stopSelf();
    }

    @Override
    public void onHexClicked(String hex) {
        copyToClipboard(getString(R.string.color), hex);
    }

    @Override
    public void onCoordsClicked(String coords) {
        copyToClipboard(getString(R.string.coordinates), coords);
    }

    private void copyToClipboard(String label, String text) {
        ClipData clip = ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(
                this,
                getString(R.string.copied_to_clipboard, label),
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isCapturing = false;
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();

        if (screenBitmap != null) {
            screenBitmap.recycle();
            screenBitmap = null;
        }

        if (targetLayout != null) windowManager.removeView(targetLayout);
        if (magnifierLayout != null) windowManager.removeView(magnifierLayout);

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        ServiceState.getInstance().setColorPickerRunning(false);
    }

    private Notification createNotification() {
        String channelId = "ColorPickerChannel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(
                    channelId,
                    "Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(chan);
        }
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.color_picker_active))
                .setSmallIcon(R.drawable.ic_logo)
                .build();
    }

    // Preference getters
    private int getMagnifierSizeDp() {
        String size = sharedPreferences.getString(PREF_MAGNIFIER_SIZE, "small");
        if (size == null) size = "small";
        switch (size) {
            case "small":
                return 150;
            case "medium":
                return 200;
            case "large":
                return 250;
            default:
                return 150;
        }
    }

    private long getCaptureDelayMs() {
        String speed = sharedPreferences.getString(PREF_CAPTURE_SPEED, "normal");
        if (speed == null) speed = "normal";
        switch (speed) {
            case "fast":
                return 25L;
            case "normal":
                return 50L;
            case "slow":
                return 100L;
            default:
                return 50L;
        }
    }

    private boolean getShowGridLines() {
        return sharedPreferences.getBoolean(PREF_SHOW_GRID_LINES, true);
    }

    private String getCaptureRange() {
        String range = sharedPreferences.getString(PREF_CAPTURE_RANGE, "small");
        return range != null ? range : "small";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    public static final int NOTIFICATION_ID = 1;

    public static final String PREF_MAGNIFIER_SIZE = "magnifier_size";
    public static final String PREF_CAPTURE_SPEED = "capture_speed";
    public static final String PREF_SHOW_GRID_LINES = "show_grid_lines";
    public static final String PREF_CAPTURE_RANGE = "capture_range";
}

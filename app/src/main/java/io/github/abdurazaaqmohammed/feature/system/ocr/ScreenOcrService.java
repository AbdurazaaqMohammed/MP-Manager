package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.IntentCompat;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.abdurazaaqmohammed.MPManager.R;

/** User-initiated projection session. Captures are converted only on demand, never logged or persisted. */
public final class ScreenOcrService extends Service {
    public static final String ACTION_START = "mpmanager.ocr.START";
    public static final String ACTION_STOP = "mpmanager.ocr.STOP";
    public static final String EXTRA_CODE = "projection_code";
    public static final String EXTRA_DATA = "projection_data";
    private static final String CHANNEL = "screen_ocr_capture";
    private static final int NOTIFICATION = 4210;
    private static WeakReference<ScreenOcrService> current = new WeakReference<>(null);
    private static volatile boolean running;
    private static boolean uiVisible;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean armed = new AtomicBoolean();
    private final AtomicBoolean delivered = new AtomicBoolean();
    private HandlerThread captureThread;
    private Handler capture;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private WindowManager windows;
    private ImageButton bubble;
    private WindowManager.LayoutParams bubbleParams;
    private int captureWidth, captureHeight;
    private volatile long captureAfter;
    private boolean capturing;
    private boolean closing;
    private boolean registeredScreenOff;
    private final Runnable timeout = () -> failCapture(null);

    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { stopSelf(); }
    };
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() { if (!closing) stopSelf(); }
        @Override public void onCapturedContentResize(int width, int height) {
            if (width > 0 && height > 0 && (width != captureWidth || height != captureHeight)) resizeCapture(width, height);
        }
    };

    public static boolean isRunning() { return running; }

    static void setUiVisible(boolean visible) {
        uiVisible = visible;
        ScreenOcrService service = current.get();
        if (service != null) service.updateBubble();
    }

    public static void stop(Context context) { context.stopService(new Intent(context, ScreenOcrService.class)); }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        current = new WeakReference<>(this);
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        captureThread = new HandlerThread("ocr-screen-capture");
        captureThread.start();
        capture = new Handler(captureThread.getLooper());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection != null) return START_NOT_STICKY; // A permission token is never reused.
        int code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED);
        Intent data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent.class);
        if (code != Activity.RESULT_OK || data == null || !Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            createNotification(); // Android 14+: foreground service BEFORE getMediaProjection.
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(code, data);
            projection.registerCallback(projectionCallback, main); // Before any virtual display.
            int[] size = displaySize();
            resizeCapture(size[0], size[1]);
            // Establish the session while the consent is fresh. A null surface
            // means no screen buffers are produced until the user taps capture.
            display = projection.createVirtualDisplay("MPManager-OCR", captureWidth, captureHeight,
                    getResources().getDisplayMetrics().densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    null, null, capture);
            createBubble();
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenOff, filter);
            registeredScreenOff = true;
            running = true;
            updateBubble();
        } catch (Exception | LinkageError error) {
            Toast.makeText(this, getString(R.string.ocr_capture_start_failed, error.getMessage()), Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void createNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(
                    CHANNEL, getString(R.string.screen_ocr_title), NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, ScreenOcrActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, ScreenOcrService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.screen_ocr_24px).setContentTitle(getString(R.string.ocr_capture_active))
                .setContentText(getString(R.string.ocr_capture_notification)).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
                .addAction(R.drawable.ic_close, getString(R.string.ocr_stop), stop);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFICATION, notification.build());
    }

    private void createBubble() {
        bubble = new ImageButton(this);
        bubble.setImageResource(R.drawable.screen_ocr_24px);
        bubble.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        bubble.setContentDescription(getString(R.string.ocr_capture_button));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xff315da8);
        background.setStroke(dp(2), 0xffd8e7ff);
        bubble.setBackground(background);
        bubble.setElevation(dp(8));
        bubble.setPadding(dp(14), dp(14), dp(14), dp(14));
        bubbleParams = new WindowManager.LayoutParams(dp(56), dp(56),
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
        bubbleParams.x = Math.max(0, displaySize()[0] - dp(72));
        bubbleParams.y = dp(180);
        bubble.setOnClickListener(v -> requestCapture());
        bubble.setOnLongClickListener(v -> { stopSelf(); return true; });
        final float[] down = new float[2];
        final int[] position = new int[2];
        final boolean[] dragged = new boolean[1];
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        bubble.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = event.getRawX(); down[1] = event.getRawY();
                position[0] = bubbleParams.x; position[1] = bubbleParams.y;
                dragged[0] = false;
                return false; // Preserve button click and long-press accessibility.
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - down[0], dy = event.getRawY() - down[1];
                if (Math.hypot(dx, dy) > slop) dragged[0] = true;
                if (dragged[0]) {
                    bubble.cancelLongPress();
                    int[] size = displaySize();
                    bubbleParams.x = Math.max(0, Math.min(size[0] - dp(56), position[0] + (int) dx));
                    bubbleParams.y = Math.max(dp(24), Math.min(size[1] - dp(72), position[1] + (int) dy));
                    windows.updateViewLayout(bubble, bubbleParams);
                    return true;
                }
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP && dragged[0]) {
                bubble.setPressed(false);
                return true;
            }
            return false;
        });
        windows.addView(bubble, bubbleParams);
    }

    private void updateBubble() {
        if (bubble != null) bubble.setVisibility(uiVisible || capturing ? View.GONE : View.VISIBLE);
    }

    private void requestCapture() {
        if (capturing || closing || projection == null) return;
        // Drop the previous sensitive frame before collecting another screenshot.
        OcrSession.clearImage();
        capturing = true;
        delivered.set(false);
        updateBubble();
        // Allow the compositor to remove our floating button before accepting a frame.
        captureAfter = System.nanoTime() + 180_000_000L;
        main.postDelayed(() -> {
            if (!capturing || closing) return;
            try {
                armed.set(true);
                display.setSurface(reader.getSurface());
                main.postDelayed(timeout, 5000);
            } catch (Exception error) { failCapture(error); }
        }, 200);
    }

    private void resizeCapture(int width, int height) {
        if (closing || width == captureWidth && height == captureHeight && reader != null) return;
        if (width <= 0 || height <= 0 || (long) width * height > 32_000_000) {
            failCapture(null);
            return;
        }
        ImageReader previous = reader;
        boolean resume = capturing;
        armed.set(false);
        if (display != null) display.setSurface(null);
        captureWidth = width;
        captureHeight = height;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(this::imageAvailable, capture);
        if (display != null) {
            display.resize(width, height, getResources().getDisplayMetrics().densityDpi);
            if (resume) {
                armed.set(true);
                display.setSurface(reader.getSurface());
            }
        }
        // Closing an ImageReader with an acquired image would invalidate the buffer.
        if (previous != null) capture.post(previous::close);
    }

    private void imageAvailable(ImageReader source) {
        Bitmap screenshot = null;
        try (Image image = source.acquireLatestImage()) {
            if (image == null || !armed.get() || delivered.get()
                    || image.getTimestamp() > 0 && image.getTimestamp() < captureAfter) return;
            if (!delivered.compareAndSet(false, true)) return;
            armed.set(false);
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer pixels = plane.getBuffer();
            int width = image.getWidth(), height = image.getHeight();
            int pixelStride = plane.getPixelStride(), rowStride = plane.getRowStride();
            screenshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            // Strip row padding, respecting arbitrary pixel strides instead of
            // treating padded pixels as real screenshot content.
            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = y * rowStride + x * pixelStride;
                    row[x] = Color.argb(255, pixels.get(offset) & 255, pixels.get(offset + 1) & 255,
                            pixels.get(offset + 2) & 255);
                }
                screenshot.setPixels(row, 0, width, 0, y, width, 1);
            }
            Bitmap ready = screenshot;
            main.post(() -> deliverCapture(ready));
        } catch (Exception | OutOfMemoryError error) {
            if (screenshot != null) screenshot.recycle();
            main.post(() -> failCapture(error));
        }
    }

    private void deliverCapture(Bitmap screenshot) {
        if (closing || !capturing) { screenshot.recycle(); return; }
        main.removeCallbacks(timeout);
        if (display != null) display.setSurface(null); // No continuous capture work while idle.
        capturing = false;
        OcrSession.setImage(screenshot);
        // The visible overlay and explicit tap authorize bringing the result UI forward.
        uiVisible = true;
        try {
            startActivity(new Intent(this, ScreenOcrActivity.class).putExtra(ScreenOcrActivity.EXTRA_CAPTURE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception error) {
            uiVisible = false;
            Toast.makeText(this, R.string.ocr_capture_failed, Toast.LENGTH_LONG).show();
        }
        // Begin reading immediately; the result UI can still cancel and choose
        // a tighter crop. This keeps normal copy-on-screen use to one bubble tap.
        OcrSession.recognize(this, new Rect(0, 0, screenshot.getWidth(), screenshot.getHeight()));
        updateBubble();
    }

    private void failCapture(Throwable error) {
        if (!capturing || closing) return;
        main.removeCallbacks(timeout);
        armed.set(false);
        capturing = false;
        if (display != null) display.setSurface(null);
        updateBubble();
        Toast.makeText(this, R.string.ocr_capture_failed, Toast.LENGTH_LONG).show();
    }

    @Override public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (Build.VERSION.SDK_INT < 34) {
            int[] size = displaySize();
            resizeCapture(size[0], size[1]);
        }
    }

    private int[] displaySize() {
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = windows.getMaximumWindowMetrics().getBounds();
            return new int[]{bounds.width(), bounds.height()};
        }
        DisplayMetrics metrics = new DisplayMetrics();
        windows.getDefaultDisplay().getRealMetrics(metrics);
        return new int[]{metrics.widthPixels, metrics.heightPixels};
    }

    @Override public void onDestroy() {
        closing = true;
        running = false;
        current = new WeakReference<>(null);
        armed.set(false);
        main.removeCallbacksAndMessages(null);
        if (registeredScreenOff) unregisterReceiver(screenOff);
        if (bubble != null) {
            try { windows.removeView(bubble); } catch (Exception ignored) {}
            bubble = null;
        }
        if (display != null) display.release();
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop();
        }
        ImageReader oldReader = reader;
        capture.post(() -> {
            if (oldReader != null) oldReader.close();
            captureThread.quitSafely();
        });
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (!uiVisible) OcrSession.clear();
        else OcrSession.releaseEngine();
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}

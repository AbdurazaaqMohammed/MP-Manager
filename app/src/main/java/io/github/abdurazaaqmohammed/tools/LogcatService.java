package io.github.abdurazaaqmohammed.tools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.LogcatReader;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;

/**
 * Owns the logcat capture so it can outlive {@link LogcatActivity}: while
 * this foreground service runs, MP Manager's own log lines keep being
 * captured even when no activity is on screen (its own uid only, unless
 * Root/Shizuku is active). The activity is just a view over
 * {@link #BUFFER}; the buffer and the reader are confined to the main
 * thread (the reader reports on the main looper).
 */
public class LogcatService extends Service implements LogcatReader.Listener {

    public static final String PREF_KEEP_RUNNING = "logcat_keep_running";
    public static final String PREF_AUTO_START = "logcat_auto_start";
    public static final String ACTION_STOP = "io.github.abdurazaaqmohammed.MPManager.LOGCAT_STOP";

    /** Same default display limit as LogFox's logs buffer. */
    public static final int MAX_LINES = 10_000;

    private static final String CHANNEL_ID = "LogcatChannel";
    private static final int NOTIFICATION_ID = 1001;

    /** Lines captured so far (bounded). Main-thread confined. */
    private static final ArrayDeque<LogcatReader.LogLine> BUFFER = new ArrayDeque<>();
    private static final List<LogcatReader.Listener> listeners = new ArrayList<>();
    private static LogcatReader reader;
    private static LogcatReader.Mode mode;
    private static boolean running;

    public static boolean isRunning() {
        return running;
    }

    public static LogcatReader.Mode mode() {
        return mode;
    }

    public static List<LogcatReader.LogLine> snapshotBuffer() {
        return new ArrayList<>(BUFFER);
    }

    public static void clearBuffer() {
        BUFFER.clear();
    }

    public static void addListener(LogcatReader.Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public static void removeListener(LogcatReader.Listener l) {
        listeners.remove(l);
    }

    /** Starts the capture service; no-op while it already runs. */
    public static void start(Context context) {
        if (running) return;
        Intent i = new Intent(context, LogcatService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
        else context.startService(i);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, LogcatService.class));
    }

    /** Same rule as the activity used to apply: working mode decides. */
    public static LogcatReader.Mode determineMode(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            if (rm.getWorkingMode() == RootManager.WorkingMode.ROOT && rm.isRootAvailable()) {
                return LogcatReader.Mode.ROOT;
            }
            if (rm.getWorkingMode() == RootManager.WorkingMode.SHIZUKU && ShizukuManager.ready()) {
                return LogcatReader.Mode.SHIZUKU;
            }
        } catch (Exception ignored) {
        }
        return LogcatReader.Mode.OWN_APP;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        startForeground(NOTIFICATION_ID, buildNotification());
        mode = determineMode(this);
        reader = new LogcatReader(this, mode);
        new Thread(() -> {
            try {
                reader.start(LogcatService.this);
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> onEof(e.getMessage()));
            }
        }, "logcat-start").start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onLines(List<LogcatReader.LogLine> lines) {
        for (LogcatReader.LogLine line : lines) {
            BUFFER.addLast(line);
            while (BUFFER.size() > MAX_LINES) BUFFER.pollFirst();
        }
        for (LogcatReader.Listener l : new ArrayList<>(listeners)) {
            l.onLines(lines);
        }
    }

    @Override
    public void onEof(String error) {
        for (LogcatReader.Listener l : new ArrayList<>(listeners)) {
            l.onEof(error);
        }
        // The reader is dead (process killed, su revoked, ...); release the
        // notification so the next start() spins up a fresh capture.
        stopSelf();
    }

    private Notification buildNotification() {
        createNotificationChannel();
        Intent open = new Intent(this, LogcatActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, LogcatService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.sidebar_logcat))
                .setContentText(getString(R.string.logcat_service_running))
                .setSmallIcon(R.drawable.terminal_24px)
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(R.drawable.stop_circle_24px, getString(R.string.logcat_stop), stopPi)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel_background), NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        listeners.clear();
        if (reader != null) {
            reader.stop();
            reader = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

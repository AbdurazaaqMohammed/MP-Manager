package io.github.abdurazaaqmohammed.tools;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ScreenCaptureService extends Service {

    public static final String ACTION_START =
            "io.github.abdurazaaqmohammed.MPManager.action.SCREEN_CAPTURE_START";
    public static final String ACTION_STOP =
            "io.github.abdurazaaqmohammed.MPManager.action.SCREEN_CAPTURE_STOP";

    private static final String CHANNEL_ID = "screen_capture";
    private static final int NOTIF_ID = 4201;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            try {
                stopForeground(true);
            } catch (Exception ignored) {
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            ensureChannel();
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(this);
            }
            builder.setContentTitle(getString(R.string.rec_capturing))
                    .setContentText(getString(R.string.rec_capturing_sub))
                    .setSmallIcon(R.drawable.video_24px)
                    .setOngoing(true);
            Notification notification = builder.build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIF_ID, notification);
            }
        } catch (Exception ignored) {
        }
        return START_NOT_STICKY;
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager == null) return;
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.rec_capturing), NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        } catch (Exception ignored) {
        }
    }
}

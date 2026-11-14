package io.github.abdurazaaqmohammed.util.misc;

import io.github.abdurazaaqmohammed.util.io.ProgressManager;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;

/** Keeps hidden file-operation workers eligible to run when the UI goes into the background. */
public final class BackgroundOperationService extends Service {
    private static final int ID = 990;
    private static final String CHANNEL = "background_operations";
    private PowerManager.WakeLock wakeLock;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 26 && manager != null)
            manager.createNotificationChannel(new NotificationChannel(CHANNEL,
                    getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW));
        Intent restore = new Intent(this, MainActivity.class).putExtra("restore_progress", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pending = PendingIntent.getActivity(this, ID, restore,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.background_operations))
                .setContentText(getString(R.string.background_operations_message))
                .setContentIntent(pending).setOnlyAlertOnce(true).setOngoing(true).build();
        startForeground(ID, notification);
        if (wakeLock == null) {
            PowerManager power = getSystemService(PowerManager.class);
            if (power != null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":file-operations");
                wakeLock.setReferenceCounted(false);
                // Same upper bound as Android's data-sync service quota. The
                // lock is released as soon as the last hidden operation ends.
                wakeLock.acquire(6L * 60 * 60 * 1000);
            }
        }
        if (!ProgressManager.hasHidden()) stopSelf();
        return START_NOT_STICKY; // Process death cannot safely replay file operations.
    }

    @Override public void onTimeout(int startId, int foregroundServiceType) {
        ProgressManager.cancelAll();
        stopSelf();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}

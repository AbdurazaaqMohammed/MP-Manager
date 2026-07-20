package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import io.github.abdurazaaqmohammed.MPManager.R;

public class FtpForegroundService extends Service {
    private static final String CHANNEL_ID = "TaskChannel";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Intent stopIntent = new Intent(this, StopReceiver.class);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        String ip = intent.getStringExtra("io.github.abdurazaaqmohammed.MPManager.ip");

        Intent copyIntent = new Intent(this, CopyReceiver.class);
        copyIntent.putExtra("io.github.abdurazaaqmohammed.MPManager.ip", ip);
        PendingIntent copyPendingIntent = PendingIntent.getBroadcast(this, 1, copyIntent, PendingIntent.FLAG_IMMUTABLE);


        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("FTP Server")
                .setContentText("FTP Server is running at " + ip)
                .setSmallIcon(R.drawable.cloud_upload_24px)
                .setOngoing(true)
                .addAction(R.drawable.stop_circle_24px, "Stop", stopPendingIntent)
                .addAction(R.drawable.ic_copy_mt, "Copy IP", copyPendingIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Background Task", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

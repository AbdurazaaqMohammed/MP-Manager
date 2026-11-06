package io.github.abdurazaaqmohammed.mcp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Foreground service hosting the MCP HTTP server (mirrors the FTP server
 * service): survives backgrounding, shows the connect URL, and offers a stop
 * action. Holds the server + tool registry statically so the settings activity
 * can observe status.
 */
public class McpServerService extends Service {

    public static final String EXTRA_RESTART = "restart";

    private static final String CHANNEL_ID = "McpChannel";
    private static final int NOTIFICATION_ID = 2;

    private static volatile McpHttpServer server;
    private static volatile McpToolContext toolContext;
    private static volatile McpProtocol protocol;

    public static boolean isRunning() {
        McpHttpServer s = server;
        return s != null && s.isRunning();
    }

    public static long getRequestCount() {
        McpHttpServer s = server;
        return s == null ? 0 : s.getRequestCount();
    }

    public static String currentUrl(Context context) {
        int port = McpPrefs.getPort(context);
        String ip = McpHttpServer.lanAddress();
        return (ip == null ? "127.0.0.1" : ip) + ":" + port + "/mcp";
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must be the very first thing: Android kills the service with
        // ForegroundServiceDidNotStartInTimeException if startForeground() is
        // not called promptly after startForegroundService().
        createNotificationChannel();
        Notification notification = buildNotification();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            // Fall back to the pre-Q signature if the type check rejects it.
            startForeground(NOTIFICATION_ID, notification);
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_RESTART, false)) {
            stopServer();
        }
        if (!isRunning()) {
            startServer();
        }
        updateNotification();
        return START_STICKY;
    }

    private synchronized void startServer() {
        if (isRunning()) return;
        // Fully release the previous socket if a restart was requested; otherwise
        // the new bind would fail with "address in use" and immediately bounce.
        McpHttpServer previous = server;
        server = null;
        if (previous != null) previous.stop();
        File home = McpPrefs.getHome(this);
        if (!home.isDirectory() && !home.mkdirs() && !home.isDirectory()) {
            Toast.makeText(this,
                    getString(R.string.mcp_home_create_failed, home.getPath()), Toast.LENGTH_LONG).show();
        }
        McpPathPolicy policy = new McpPathPolicy(home, McpPrefs.getRulesJson(this));
        toolContext = new McpToolContext(this, policy);
        String version;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                version = String.valueOf(info.getLongVersionCode());
            } else version = info.versionCode+"";
        } catch (PackageManager.NameNotFoundException e) {
            version = "?";
        }
        protocol = new McpProtocol(new McpToolRegistry(toolContext), version, home.getPath());
        String token = McpPrefs.authEnabled(this) ? McpPrefs.getToken(this) : null;
        McpHttpServer httpServer = new McpHttpServer(McpPrefs.getPort(this), token, protocol);
        try {
            httpServer.start();
        } catch (Exception e) {
            Toast.makeText(this,
                    getString(R.string.mcp_start_failed, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        server = httpServer;
    }

    private synchronized void stopServer() {
        McpHttpServer s = server;
        server = null;
        if (s != null) s.stop();
        McpToolContext tc = toolContext;
        toolContext = null;
        if (tc != null) tc.apkSessions.closeAll();
    }

    private void updateNotification() {
        Notification notification = buildNotification();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        String url = "http://" + currentUrl(this);
        Intent stopIntent = new Intent(this, McpStopReceiver.class);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent openIntent = new Intent(this, McpServerActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.mcp_notification_title))
                .setContentText(getString(R.string.mcp_notification_text, url))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.mcp_notification_text, url)))
                .setSmallIcon(R.drawable.cloud_upload_24px)
                .setOngoing(true)
                .setContentIntent(openPendingIntent)
                .addAction(R.drawable.stop_circle_24px, getString(R.string.mcp_stop), stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.mcp_channel_name), NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

package io.github.abdurazaaqmohammed.MPManager.ftp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;

public class StopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent stopIntent = new Intent(context, FtpForegroundService.class);
        context.stopService(stopIntent);
        if(MainActivity.ftpServer != null) {
            MainActivity.ftpServer.stop();
            MainActivity.ftpServer = null;
        }
        Intent uiIntent = new Intent("io.github.abdurazaaqmohammed.FTP_STOPPED");
        context.sendBroadcast(uiIntent);
    }
}
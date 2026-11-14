package io.github.abdurazaaqmohammed.ui.main;

import io.github.abdurazaaqmohammed.feature.ftp.FtpForegroundService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.github.abdurazaaqmohammed.feature.files.FtpController;

public class StopReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent stopIntent = new Intent(context, FtpForegroundService.class);
        context.stopService(stopIntent);
        if(FtpController.ftpServer != null) {
            FtpController.ftpServer.stop();
            FtpController.ftpServer = null;
        }
        Intent uiIntent = new Intent("io.github.abdurazaaqmohammed.FTP_STOPPED");
        context.sendBroadcast(uiIntent);
    }
}

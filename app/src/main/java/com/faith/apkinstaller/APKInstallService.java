package com.faith.apkinstaller;


import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;

import io.github.abdurazaaqmohammed.utils.ApkInstallDialogHelper;

public class APKInstallService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int status = intent.hasExtra(PackageInstaller.EXTRA_STATUS) ?
                intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) :
                0;

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(confirmationIntent);
                }
                break;

            case PackageInstaller.STATUS_SUCCESS:
                sendResult(true);
                break;

            default:
                sendResult(false);
                break;
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    private void sendResult(boolean success) {
        Intent result = new Intent(ApkInstallDialogHelper.ACTION_INSTALL_RESULT);
        result.setPackage(getPackageName());
        result.putExtra(ApkInstallDialogHelper.EXTRA_INSTALL_SUCCESS, success);
        sendBroadcast(result);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

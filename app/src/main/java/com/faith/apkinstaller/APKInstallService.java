package com.faith.apkinstaller;


import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;
import android.util.Log;

import javax.annotation.Nullable;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.IBinder;
import android.util.Log;

public class APKInstallService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int status = intent.hasExtra(PackageInstaller.EXTRA_STATUS) ?
                intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0) :
                0;

        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                //Log.d("AppLog", "Requesting user confirmation for installation");
                Intent confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(confirmationIntent);

                }
                break;

            case PackageInstaller.STATUS_SUCCESS:
                //Log.d("AppLog", "Installation succeeded");
                break;

            default:
                //Log.d("AppLog", "Installation failed");
                break;
        }

        stopSelf();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

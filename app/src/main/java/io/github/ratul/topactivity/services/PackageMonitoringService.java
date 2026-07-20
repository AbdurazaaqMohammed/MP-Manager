/*
 *   Copyright (C) 2022 Ratul Hasan
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.github.ratul.topactivity.services;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import io.github.ratul.topactivity.repository.DataRepository;
import io.github.ratul.topactivity.utils.DatabaseUtil;

public class PackageMonitoringService extends Service {

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private UsageStatsManager usageStats;
    private long scanSpeed = mapPreferenceToScanSpeed("2");

    private final Runnable observerTask = new Runnable() {
        @Override
        public void run() {
            io.github.ratul.topactivity.repository.ServiceState serviceState = DataRepository.getInstance().getAppState();

            if (!serviceState.isRunning()) {
                handler.removeCallbacks(this);
                stopSelf();
                return;
            }

            android.util.Pair<String, String> foreground = getForegroundApp();

            String pkg = foreground.first;
            String cls = foreground.second;
            if (pkg != null && !pkg.isEmpty() && cls != null && !cls.isEmpty()) {
                DataRepository.getInstance().updateData(pkg, cls);
            }
            handler.postDelayed(this, scanSpeed);
        }
    };

    public class LocalBinder extends Binder {
        public PackageMonitoringService getService() {
            return PackageMonitoringService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            usageStats = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        scanSpeed = mapPreferenceToScanSpeed(DatabaseUtil.getScanSpeed());
        handler.removeCallbacks(observerTask);
        handler.post(observerTask);
        return START_STICKY;
    }

    private long mapPreferenceToScanSpeed(String value) {
        switch (value) {
            case "0":
                return 50;
            case "1":
                return 100;
            case "2":
                return 200;
            default:
                return 500;
        }
    }

    private android.util.Pair<String, String> getForegroundApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            long currentTime = System.currentTimeMillis();
            UsageEvents usageEvents = usageStats.queryEvents(currentTime - 5000, currentTime);
            String latestPackage = null;
            String latestClass = null;
            long latestTimestamp = 0;

            UsageEvents.Event event = new UsageEvents.Event();
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                        && event.getTimeStamp() > latestTimestamp) {
                    latestTimestamp = event.getTimeStamp();
                    latestPackage = event.getPackageName();
                    latestClass = event.getClassName();
                }
            }

            return new android.util.Pair<>(latestPackage, latestClass);
        }
        return new android.util.Pair<>(null, null);
    }
}

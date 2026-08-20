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
package io.github.ratul.topactivity;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.core.app.NotificationManagerCompat;
import androidx.multidex.MultiDexApplication;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.manager.NotificationUiManager;
import io.github.ratul.topactivity.ui.ClipboardActivity;

public class App extends MultiDexApplication {

    private static App instance;

    public static final String REPO = "codehasan/Current-Activity";
    public static final String REPO_URL = "https://github.com/" + REPO;
    public static final String API_URL = "https://api.github.com/repos/" + REPO;

    private NotificationManagerCompat notificationManager;
    private ClipboardManager clipboardManager;

    public NotificationManagerCompat getNotificationManager() {
        return notificationManager;
    }

    public ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        notificationManager = NotificationManagerCompat.from(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NotificationUiManager.CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(getString(R.string.notification_channel_description));
            notificationManager.createNotificationChannel(channel);
        }
    }

    public static void copyString(Context context, String str, String msg) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ClipData clip = ClipData.newPlainText(context.getString(R.string.app_name), str);
            instance.clipboardManager.setPrimaryClip(clip);
        } else {
            Intent copyActivity = new Intent(context, ClipboardActivity.class)
                    .putExtra(Intent.EXTRA_TEXT, str)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(copyActivity);
        }
        showToast(context, msg);
    }

    public static void showToast(Context context, String message) {
        try {
            Extensions.showMessage(context, message);
        } catch (Exception ignored) {
            try {
                Extensions.showMessage(instance, message);
            } catch (Exception ignored2) {
                // ignore
            }
        }
    }
}

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
package io.github.ratul.topactivity.manager;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.ratul.topactivity.receivers.NotificationActionReceiver;
import io.github.ratul.topactivity.repository.DataRepository;

public class NotificationUiManager {

    public static final int NOTIFICATION_ID = 62345;
    public static final int PACKAGE_COPY_ACTION_ID = 3429872;
    public static final int CLASS_COPY_ACTION_ID = 3429873;
    public static final int STOP_ACTION_ID = 908435;
    public static final String CHANNEL_ID = "activity_info";

    private final Context context;
    private final NotificationManagerCompat notificationManager;

    private final String packageLabel;
    private final String classLabel;
    private final String stopLabel;
    private final String packageCopied;
    private final String classCopied;

    public NotificationUiManager(Context context) {
        this.context = context;
        this.notificationManager = NotificationManagerCompat.from(context);
        this.packageLabel = context.getString(R.string.package_label);
        this.classLabel = context.getString(R.string.class_label);
        this.stopLabel = context.getString(R.string.stop);
        this.packageCopied = context.getString(R.string.package_copied);
        this.classCopied = context.getString(R.string.class_copied);
    }

    public void show() {
        if (!notificationManager.areNotificationsEnabled()) return;

        io.github.ratul.topactivity.repository.ServiceState serviceState = DataRepository.getInstance().getAppState();
        updateNotification(serviceState.getPkg(), serviceState.getCls());

        DataRepository.getInstance().addListener(state -> {
            if (!state.isRunning()) {
                hide();
                return;
            }
            if (!state.getPkg().isEmpty() && !state.getCls().isEmpty()) {
                updateNotification(state.getPkg(), state.getCls());
            }
        });
    }

    private void hide() {
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @SuppressLint("MissingPermission")
    private void updateNotification(String pkg, String cls) {
        NotificationCompat.Action copyPkg = new NotificationCompat.Action(
                R.drawable.ic_package, packageLabel,
                actionCopyPendingIntent(PACKAGE_COPY_ACTION_ID, pkg, packageCopied)
        );
        NotificationCompat.Action copyClass = new NotificationCompat.Action(
                R.drawable.ic_class, classLabel,
                actionCopyPendingIntent(CLASS_COPY_ACTION_ID, cls, classCopied)
        );
        NotificationCompat.Action stop = new NotificationCompat.Action(
                R.drawable.ic_cancel, stopLabel, actionStopPendingIntent()
        );

        android.app.Notification notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(pkg)
                .setSmallIcon(R.drawable.ic_logo)
                .setContentText(cls)
                .setAutoCancel(false)
                .setOngoing(true)
                .addAction(copyPkg)
                .addAction(copyClass)
                .addAction(stop)
                .build();

        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private PendingIntent actionCopyPendingIntent(int requestCode, String text, String message) {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ACTION, NotificationActionReceiver.ACTION_COPY);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.putExtra(Intent.EXTRA_ASSIST_CONTEXT, message);
        return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private PendingIntent actionStopPendingIntent() {
        Intent intent = new Intent(context, NotificationActionReceiver.class);
        intent.putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ACTION, NotificationActionReceiver.ACTION_STOP);
        return PendingIntent.getBroadcast(
                context, STOP_ACTION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}

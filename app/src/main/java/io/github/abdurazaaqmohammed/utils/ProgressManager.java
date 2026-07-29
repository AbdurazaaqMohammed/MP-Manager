package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.apk.APKLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ProgressManager {
    private final Activity activity;
    private final Handler handler;
    private final boolean indeterminate;
    private AlertDialog dialog;
    private String currentText;
    private int progressVal, maxVal;
    private boolean hidden, dismissed;
    private NotificationManagerCompat nm;

    private static final String CHANNEL_ID = "progress_channel";
    private static final int NOTIFICATION_ID = 1001;

    public ProgressManager(Activity activity, boolean indeterminate) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
        this.indeterminate = indeterminate;
        initChannel();
    }

    public ProgressManager show() {
        handler.post(() -> {
            if (dismissed || (dialog != null && dialog.isShowing())) return;
            View v = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null, false);
            ProgressBar pb = v.findViewById(R.id.progressBar);
            pb.setIndeterminate(indeterminate);
            if (currentText != null) ((TextView) v.findViewById(R.id.dialogTitle)).setText(currentText);
            if (!indeterminate && maxVal > 0) { pb.setMax(maxVal); pb.setProgress(progressVal); }
            dialog = new MaterialAlertDialogBuilder(activity)
                    .setView(v)
                    .setNeutralButton("Hide", (d, w) -> hide())
                    .create();
            dialog.show();
        });
        return this;
    }

    public ProgressManager setText(String text) {
        this.currentText = text;
        handler.post(() -> {
            if (dismissed) return;
            if (dialog != null && dialog.isShowing())
                ((TextView) dialog.findViewById(R.id.dialogTitle)).setText(text);
            if (hidden) updateNotification();
        });
        return this;
    }

    public ProgressManager setProgress(int progress, int max) {
        this.progressVal = progress;
        this.maxVal = max;
        handler.post(() -> {
            if (dismissed) return;
            if (dialog != null && dialog.isShowing()) {
                ProgressBar pb = dialog.findViewById(R.id.progressBar);
                pb.setIndeterminate(false);
                pb.setMax(max);
                pb.setProgress(progress);
            }
            if (hidden) updateNotification();
        });
        return this;
    }

    public void dismiss() {
        handler.post(() -> {
            dismissed = true;
            if (dialog != null) dialog.dismiss();
            cancelNotification();
        });
    }

    public APKLogger getLogger() {
        boolean saveLog = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("logEnabled", false);
        FileWriter fw = null;
        if (saveLog) try {
            File folder = new File(new File(Environment.getExternalStorageDirectory(), "MP Manager"), "logs");
            folder.mkdirs();
            fw = new FileWriter(new File(folder, "log_" + System.currentTimeMillis() + ".txt"), true);
        } catch (IOException ignored) {}
        FileWriter logFw = fw;
        return new APKLogger() {
            @Override public void logMessage(String s) { setText(s); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void logError(String s, Throwable t) { new ErrorUtil(activity).showError(t); if (logFw != null) try { logFw.write(s + "\n"); for (StackTraceElement e : t.getStackTrace()) logFw.write(e.toString() + "\n"); } catch (IOException ignored) {} }
            @Override public void logVerbose(String s) { setText(s); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void close() { if (logFw != null) try { logFw.close(); } catch (IOException ignored) {} }
        };
    }

    private void hide() {
        hidden = true;
        if (dialog != null) dialog.hide();
        showNotification();
    }

    private void initChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Task Progress", NotificationManager.IMPORTANCE_LOW);
            NotificationManager m = activity.getSystemService(NotificationManager.class);
            if (m != null) m.createNotificationChannel(c);
        }
    }

    private void showNotification() {
        nm = NotificationManagerCompat.from(activity);
        nm.notify(NOTIFICATION_ID, buildNotif().build());
    }

    private void updateNotification() {
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotif().build());
    }

    private NotificationCompat.Builder buildNotif() {
        NotificationCompat.Builder b = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setContentTitle("MP Manager")
                .setContentText(currentText != null ? currentText : "Working...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(activity, 0, new Intent(activity, activity.getClass()), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        if (!indeterminate && maxVal > 0) b.setProgress(maxVal, progressVal, false);
        else b.setProgress(0, 0, true);
        return b;
    }

    private void cancelNotification() {
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}

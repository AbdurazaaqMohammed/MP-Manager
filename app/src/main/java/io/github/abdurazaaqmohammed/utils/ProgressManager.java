package io.github.abdurazaaqmohammed.utils;

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
import androidx.appcompat.app.AppCompatActivity;
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
    private final AppCompatActivity activity;
    private final Handler handler;
    private final boolean indeterminate;
    public AlertDialog dialog;
    private String currentText;
    private int progressVal, maxVal;
    private boolean hidden, dismissed;
    private NotificationManagerCompat nm;

    private static final String CHANNEL_ID = "progress_channel";
    private static final int NOTIFICATION_ID = 1001;

    public ProgressManager(AppCompatActivity activity, boolean indeterminate) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
        this.indeterminate = indeterminate;
        initChannel();
    }

    public ProgressManager show() {
        handler.post(() -> {
            if (dismissed || (dialog != null && dialog.isShowing())) return;
            View v = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null, false);
            v.findViewById(R.id.hideButton).setOnClickListener(v1 -> hide());
            ProgressBar pb = v.findViewById(R.id.progressBar);
            pb.setIndeterminate(indeterminate);
            if (currentText != null) ((TextView) v.findViewById(R.id.dialogTitle)).setText(currentText);
            if (!indeterminate && maxVal > 0) { pb.setMax(maxVal); pb.setProgress(progressVal); }
            dialog = new MaterialAlertDialogBuilder(activity).setView(v).show();
        });
        return this;
    }

    public void setText(int id, String append) {
        setText(activity.getString(id, append));
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
            @Override public void   logMessage(String s) { setText(resolveDisplay(s)); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void logError(String s, Throwable t) { new ErrorUtil(activity).showError(t); if (logFw != null) try { logFw.write(s + "\n"); for (StackTraceElement e : t.getStackTrace()) logFw.write(e.toString() + "\n"); } catch (IOException ignored) {} }
            @Override public void logVerbose(String s) { setText(resolveDisplay(s)); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void close() { if (logFw != null) try { logFw.close(); } catch (IOException ignored) {} }
        };
    }

    private String resolveDisplay(String s) {
        if (s == null) return null;
        try {
            if (s.startsWith("Saved to: ")) return activity.getString(R.string.logger_saved_to, s.substring("Saved to: ".length()));
            if (s.startsWith("Writing apk")) return activity.getString(R.string.logger_writing_apk);
            if (s.startsWith("Decompiling to ")) return activity.getString(R.string.reandroid_decompiling_to, s.substring("Decompiling to ".length()).replace(" ...", "").trim());
            if (s.startsWith("Decoding assets/dexopt")) return activity.getString(R.string.reandroid_decoding_dexopt);
            if (s.startsWith("Encoding assets/dexopt")) return activity.getString(R.string.reandroid_encoding_dexopt);
            if (s.startsWith("Scanning dex files for profile")) return activity.getString(R.string.reandroid_scanning_dex);
            if (s.startsWith("Scanning: ")) return activity.getString(R.string.reandroid_scanning_x, s.substring("Scanning: ".length()));
            if (s.startsWith("Searching files: ")) return activity.getString(R.string.reandroid_searching_x, s.substring("Searching files: ".length()));
            if (s.startsWith("Refreshing resource table")) return activity.getString(R.string.reandroid_refreshing_table);
            if (s.startsWith("Sorting files")) return activity.getString(R.string.reandroid_sorting);
            if (s.startsWith("Building dex")) return activity.getString(R.string.reandroid_building_dex);
            if (s.startsWith("Optimizing table")) return activity.getString(R.string.reandroid_optimizing_table);
            if (s.startsWith("Optimizing")) return activity.getString(R.string.reandroid_optimizing);
            if (s.startsWith("Sanitizing paths")) return activity.getString(R.string.reandroid_sanitizing);
            if (s.startsWith("Merging: ")) return activity.getString(R.string.reandroid_merging_x, s.substring("Merging: ".length()));
            if (s.startsWith("Validating resource names")) return activity.getString(R.string.reandroid_validating_names);
            if (s.startsWith("All resource names are valid")) return activity.getString(R.string.reandroid_names_valid);
            if (s.startsWith("Extracting root files")) return activity.getString(R.string.reandroid_extracting_root);
            if (s.startsWith("Dumping signatures")) return activity.getString(R.string.reandroid_dumping_sigs);
            if (s.startsWith("Don't have signature block")) return activity.getString(R.string.reandroid_no_sig_block);
            if (s.startsWith("Signatures dumped to: ")) return activity.getString(R.string.reandroid_signatures_dumped, s.substring("Signatures dumped to: ".length()));
            if (s.startsWith("Confusing zip structure")) return activity.getString(R.string.reandroid_confusing_zip);
            if (s.startsWith("Restoring signatures")) return activity.getString(R.string.reandroid_restoring_sigs);
            if (s.startsWith("Scanning JSON directory")) return activity.getString(R.string.reandroid_scanning_json);
            if (s.startsWith("Scanning XML directory")) return activity.getString(R.string.reandroid_scanning_xml);
            if (s.startsWith("Scanning Raw directory")) return activity.getString(R.string.reandroid_scanning_raw);
            if (s.startsWith("Loading signatures")) return activity.getString(R.string.reandroid_loading_sigs);
            if (s.startsWith("Writing signature block")) return activity.getString(R.string.reandroid_writing_sig_block);
            if (s.startsWith("Decoding res files")) return activity.getString(R.string.reandroid_decoding_res);
            if (s.startsWith("Found apk files: ")) return activity.getString(R.string.reandroid_found_apks, Integer.parseInt(s.substring("Found apk files: ".length()).trim()));
            if (s.startsWith("Initializing android framework")) return activity.getString(R.string.reandroid_init_framework);
            if (s.startsWith("Can not read framework version")) return activity.getString(R.string.reandroid_no_framework_version);
            if (s.startsWith("Removed empty: ")) return activity.getString(R.string.reandroid_removed_empty, s.substring("Removed empty: ".length()));
            if (s.startsWith("Decoding: ")) return activity.getString(R.string.reandroid_decoding_x, s.substring("Decoding: ".length()));
            if (s.startsWith("Decode: ")) return activity.getString(R.string.reandroid_decoding_x, s.substring("Decode: ".length()));
            if (s.startsWith("Loading framework: ")) return activity.getString(R.string.reandroid_loading_framework_x, s.substring("Loading framework: ".length()));
            if (s.startsWith("Loading: ")) return activity.getString(R.string.reandroid_loading_x, s.substring("Loading: ".length()));
            if (s.equals("Loading ...")) return activity.getString(R.string.reandroid_loading);
        } catch (Exception ignored) {}
        return s;
    }

    private void hide() {
        hidden = true;
        if (dialog != null) dialog.hide();
        showNotification();
    }

    private void initChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, activity.getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW);
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
                .setContentTitle(activity.getString(R.string.app_name))
                .setContentText(currentText != null ? currentText : activity.getString(R.string.progress_working))
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

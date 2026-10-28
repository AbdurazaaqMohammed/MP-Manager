package io.github.abdurazaaqmohammed.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
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

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ProgressManager {
    private final AppCompatActivity activity;
    private final Handler handler;
    private final boolean indeterminate;
    public AlertDialog dialog;
    private String currentText;
    private int progressVal, maxVal;
    private boolean hidden, dismissed;
    private boolean showCount;
    private NotificationManagerCompat nm;

    private static final String CHANNEL_ID = "progress_channel";
    private static final int NOTIFICATION_ID = 1001;

    private final long startTime = System.currentTimeMillis();
    private static final List<ProgressManager> activeInstances = new CopyOnWriteArrayList<>();

    public static void restoreHidden() {
        for (ProgressManager pm : activeInstances) {
            if (pm.hidden && !pm.dismissed) {
                pm.restore();
            }
        }
    }

    public static boolean hasHidden() {
        for (ProgressManager pm : activeInstances) {
            if (pm.hidden && !pm.dismissed) return true;
        }
        return false;
    }

    public ProgressManager(AppCompatActivity activity, boolean indeterminate) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
        this.indeterminate = indeterminate;
        initChannel();
    }

    private String currentFileName, currentFrom, currentTo;
    private long lastSpeedCheckTime = 0;
    private long lastTransferredBytes = 0;
    private String currentSpeed = "";

    public ProgressManager setFileInfo(String name, String from, String to) {
        this.currentFileName = name;
        this.currentFrom = from;
        this.currentTo = to;
        handler.post(() -> {
            if (dismissed || dialog == null || !dialog.isShowing()) return;
            updateFileDetails(dialog);
        });
        return this;
    }

    public ProgressManager updateTransferProgress(long transferredBytes, long totalBytes) {
        long now = System.currentTimeMillis();
        if (lastSpeedCheckTime == 0) {
            lastSpeedCheckTime = now;
            lastTransferredBytes = transferredBytes;
        } else if (now - lastSpeedCheckTime >= 600) {
            long deltaBytes = transferredBytes - lastTransferredBytes;
            long deltaMs = now - lastSpeedCheckTime;
            if (deltaMs > 0 && deltaBytes >= 0) {
                double speedMBs = (deltaBytes / 1024.0 / 1024.0) / (deltaMs / 1000.0);
                if (speedMBs >= 100) {
                    currentSpeed = String.format(java.util.Locale.US, "%.0f MB/s", speedMBs);
                } else if (speedMBs >= 1) {
                    currentSpeed = String.format(java.util.Locale.US, "%.1f MB/s", speedMBs);
                } else {
                    double speedKBs = (deltaBytes / 1024.0) / (deltaMs / 1000.0);
                    currentSpeed = String.format(java.util.Locale.US, "%.1f KB/s", speedKBs);
                }
            }
            lastSpeedCheckTime = now;
            lastTransferredBytes = transferredBytes;
        }

        if (totalBytes > 0) {
            int progress = (int) Math.min(Integer.MAX_VALUE, (transferredBytes * 1000L) / totalBytes);
            setProgress(progress, 1000);
        }
        return this;
    }

    private void updateFileDetails(AlertDialog d) {
        if (d == null) return;
        TextView nameTv = d.findViewById(R.id.progressFileName);
        TextView fromTv = d.findViewById(R.id.progressFrom);
        TextView toTv = d.findViewById(R.id.progressTo);
        TextView speedTv = d.findViewById(R.id.progressSpeed);

        if (nameTv != null) {
            if (currentFileName != null && !currentFileName.isEmpty()) {
                nameTv.setVisibility(View.VISIBLE);
                nameTv.setText(currentFileName);
            } else {
                nameTv.setVisibility(View.GONE);
            }
        }
        if (fromTv != null) {
            if (currentFrom != null && !currentFrom.isEmpty()) {
                fromTv.setVisibility(View.VISIBLE);
                fromTv.setText("From: " + currentFrom);
            } else {
                fromTv.setVisibility(View.GONE);
            }
        }
        if (toTv != null) {
            if (currentTo != null && !currentTo.isEmpty()) {
                toTv.setVisibility(View.VISIBLE);
                toTv.setText("To: " + currentTo);
            } else {
                toTv.setVisibility(View.GONE);
            }
        }
        if (speedTv != null) {
            if (currentSpeed != null && !currentSpeed.isEmpty()) {
                speedTv.setVisibility(View.VISIBLE);
                speedTv.setText(currentSpeed);
            } else {
                speedTv.setVisibility(View.GONE);
            }
        }
    }

    public ProgressManager show() {
        handler.post(() -> {
            if (dismissed || (dialog != null && dialog.isShowing())) return;
            View v = LayoutInflater.from(activity).inflate(R.layout.progress_dialog, null, false);
            v.findViewById(R.id.hideButton).setOnClickListener(v1 -> hide());
            ProgressBar pb = v.findViewById(R.id.progressBar);
            pb.setIndeterminate(indeterminate);
            if (currentText != null) ((TextView) v.findViewById(R.id.dialogTitle)).setText(currentText);
            if (!indeterminate && maxVal > 0) {
                pb.setMax(maxVal);
                pb.setProgress(progressVal);
            }
            applyCount(v.findViewById(R.id.progressCount));
            dialog = new MaterialAlertDialogBuilder(activity).setView(v).show();
            updateFileDetails(dialog);
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
            if (dialog != null && dialog.isShowing()) {
                TextView title = dialog.findViewById(R.id.dialogTitle);
                if (title != null) title.setText(text);
                applyCount(dialog.findViewById(R.id.progressCount));
                updateFileDetails(dialog);
            }
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
                applyCount(dialog.findViewById(R.id.progressCount));
                updateFileDetails(dialog);
            }
            if (hidden) updateNotification();
        });
        return this;
    }

    /** Declares the work size up front so the dialog opens already determinate. */
    public ProgressManager setTotal(int total) {
        return setProgress(progressVal, total);
    }

    /**
     * Turns on the "n / total (x%)" line.
     */
    public ProgressManager showCount(boolean show) {
        this.showCount = show;
        handler.post(() -> {
            if (dismissed) return;
            if (dialog != null && dialog.isShowing()) applyCount(dialog.findViewById(R.id.progressCount));
        });
        return this;
    }

    private void applyCount(TextView count) {
        if (count == null || dismissed) return;
        if (indeterminate || maxVal <= 0) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed > 0) {
                count.setVisibility(View.VISIBLE);
                count.setText(elapsed + "s elapsed");
            } else {
                count.setVisibility(View.GONE);
            }
            return;
        }
        int percent = (int) ((progressVal * 100L) / maxVal);
        count.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        sb.append(progressVal).append(" / ").append(maxVal)
                .append(" (").append(Math.min(100, Math.max(0, percent))).append("%)");
        if (progressVal > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingMs = (elapsed / progressVal) * (maxVal - progressVal);
            long sec = remainingMs / 1000;
            if (sec > 0) {
                String eta = sec < 60 ? (sec + "s left") : ((sec / 60) + "m " + (sec % 60) + "s left");
                sb.append(" • ").append(eta);
            }
        }
        count.setText(sb.toString());
    }

    public void dismiss() {
        handler.post(() -> {
            dismissed = true;
            activeInstances.remove(this);
            if (dialog != null) dialog.dismiss();
            cancelNotification();
        });
    }

    public APKLogger getLogger() {
        boolean saveLog = PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("logEnabled", false);
        FileWriter fw = null;
        if (saveLog) try {
            fw = new FileWriter(AppLogs.newLogFile("log"), true);
        } catch (IOException ignored) {}
        FileWriter logFw = fw;
        return new APKLogger() {
            @Override public void logMessage(String s) { setText(s); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void logError(String s, Throwable t) { try { AppLogs.writeCrash(t, activity); } catch (Exception ignored) {} new ErrorUtil(activity).showError(t); if (logFw != null) try { logFw.write(s + "\n"); for (StackTraceElement e : t.getStackTrace()) logFw.write(e.toString() + "\n"); } catch (IOException ignored) {} }
            @Override public void logVerbose(String s) { setText((s)); if (logFw != null) try { logFw.write(s + "\n"); } catch (IOException ignored) {} }
            @Override public void close() { if (logFw != null) try { logFw.close(); } catch (IOException ignored) {} }
        };
    }

    public void hide() {
        hidden = true;
        if (dialog != null) dialog.hide();
        if (!activeInstances.contains(this)) activeInstances.add(this);
        showNotification();
    }

    public void restore() {
        handler.post(() -> {
            if (dismissed) return;
            hidden = false;
            cancelNotification();
            if (dialog != null && !dialog.isShowing()) {
                dialog.show();
            }
        });
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
        Intent intent = new Intent(activity, activity.getClass());
        intent.setAction("io.github.abdurazaaqmohammed.RESTORE_PROGRESS");
        intent.putExtra("restore_progress", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(activity, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder b = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setContentTitle(activity.getString(R.string.app_name))
                .setContentText(currentText != null ? currentText : activity.getString(R.string.progress_working))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setContentIntent(pi);
        if (!indeterminate && maxVal > 0) b.setProgress(maxVal, progressVal, false);
        else b.setProgress(0, 0, true);
        return b;
    }

    private void cancelNotification() {
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }
}

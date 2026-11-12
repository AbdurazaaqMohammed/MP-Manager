package io.github.abdurazaaqmohammed.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.Formatter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Live progress dialog for long running file work (copy, move, delete, extract,
 * compress, …).
 *
 * <p>Callers push state from a worker thread ({@link #setText}, {@link #setProgress},
 * {@link #setFile}, {@link #setPaths}, {@link #addBytes}) and this class owns all
 * rendering: a 400 ms ticker on the main thread samples the counters, derives a
 * smoothed transfer rate and an ETA, and repaints the dialog (or the fallback
 * notification while the dialog is hidden).
 *
 * <p>Bytes are the preferred unit whenever {@link #setBytes} or {@link #addBytes}
 * reported a total, because a byte bar moves smoothly inside one large file
 * instead of jumping once per file. Item counts remain the fallback so every
 * existing caller that only calls {@link #setProgress} keeps working unchanged.
 */
public class ProgressManager {

    /** Repaint cadence. Fast enough to feel live, slow enough to stay cheap. */
    private static final long TICK_MS = 400L;
    /** Ignore samples closer together than this; the rate would be pure noise. */
    private static final long SAMPLE_MIN_MS = 120L;
    /** Weight of the newest sample in the exponential moving average. */
    private static final double SPEED_SMOOTHING = 0.45d;
    /** Determinate byte bars are scaled to this to avoid int overflow past 2 GB. */
    private static final int BYTE_BAR_SCALE = 1000;

    private final AppCompatActivity activity;
    private final Handler handler;
    /** True when the caller has no idea about sizes yet (spinner until proven otherwise). */
    private final boolean indeterminate;

    /**
     * Pause / resume / cancel token for this operation.
     *
     * <p>It exists for every instance, but only operations that opt in via
     * {@link #setControllable(boolean)} show the buttons and consult it, so a
     * job that cannot be stopped safely never offers a Stop button.
     */
    private final TransferControl control = new TransferControl();
    /** Whether the dialog offers pause and cancel. */
    private volatile boolean controllable;
    /** Set while a cancel has been requested but the worker has not stopped yet. */
    private volatile boolean cancelling;

    public AlertDialog dialog;
    private View contentView;

    // ---- pushed by callers, read by the ticker -----------------------------
    private volatile String currentText;
    private volatile String operationLabel;
    private volatile String fileName;
    private volatile String fromPath;
    private volatile String toPath;

    private volatile int progressVal;
    private volatile int maxVal;
    private volatile long bytesDone;
    /** Negative until a byte total is known. */
    private volatile long bytesTotal = -1L;

    private volatile boolean hidden;
    private volatile boolean dismissed;

    // ---- owned by the ticker (main thread only) ----------------------------
    private long startMs = -1L;
    private long lastSampleMs = -1L;
    private long lastSampleBytes;
    private long lastSampleItems;
    private double speedBps;
    private double itemRate;

    private NotificationManagerCompat nm;

    private static final String CHANNEL_ID = "progress_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final List<ProgressManager> activeInstances = new CopyOnWriteArrayList<>();
    /** Every live operation, hidden or not, so an app exit can cancel them all. */
    private static final List<ProgressManager> liveInstances = new CopyOnWriteArrayList<>();

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (dismissed || activity.isFinishing()) {
                dismissed = true;
                stopTicker();
                return;
            }
            sampleRates();
            render();
            handler.postDelayed(this, TICK_MS);
        }
    };

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

    /**
     * Pauses the running operation, or resumes it when it is already paused.
     *
     * <p>Called from the notification action, where there is no dialog to
     * read the current state from.
     */
    public static void togglePauseActive() {
        ProgressManager pm = activeOperation();
        if (pm == null) return;
        if (pm.control.isPaused()) pm.resume();
        else pm.pause();
    }

    /** Cancels the running operation from the notification action. */
    public static void cancelActive() {
        ProgressManager pm = activeOperation();
        if (pm != null) pm.cancel();
    }

    /**
     * Cancels and dismisses every live operation. Used when the app exits so
     * copy/move/delete/extract workers unwind instead of being killed mid-write.
     */
    public static void cancelAll() {
        for (ProgressManager pm : new ArrayList<>(liveInstances)) {
            try {
                pm.cancelForExit();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Cancels this operation even when it does not expose user-facing controls
     * (the exit button must still unwind every worker it can).
     */
    private void cancelForExit() {
        cancelling = true;
        control.cancel();
        dismiss();
    }

    /** The controllable operation that is still running, or null. */
    private static ProgressManager activeOperation() {
        for (ProgressManager pm : activeInstances) {
            if (!pm.dismissed && pm.controllable && !pm.control.isCancelled()) return pm;
        }
        return null;
    }

    public ProgressManager(AppCompatActivity activity, boolean indeterminate) {
        this.activity = activity;
        this.handler = new Handler(Looper.getMainLooper());
        this.indeterminate = indeterminate;
        liveInstances.add(this);
        initChannel();
    }

    // =======================================================================
    // Pause / resume / cancel
    // =======================================================================

    /**
     * The token that carries the user's pause / cancel intent to the worker.
     *
     * <p>Callers pass it to the code that performs the work; that code calls
     * {@link TransferControl#checkpoint()} at safe points so a paused
     * operation really stops and a cancelled one really unwinds.
     */
    public TransferControl control() {
        return control;
    }

    /**
     * Offers the pause and cancel buttons for this operation.
     *
     * <p>Only enable this for work that really honours the control, otherwise
     * the buttons would be a lie.
     */
    public ProgressManager setControllable(boolean controllable) {
        this.controllable = controllable;
        return this;
    }

    public boolean isControllable() {
        return controllable;
    }

    /** True while the operation is paused and waiting for the user. */
    public boolean isPaused() {
        return control.isPaused();
    }

    /** True once the user cancelled and the worker has not finished unwinding. */
    public boolean isCancelling() {
        return cancelling || control.isCancelled();
    }

    /**
     * Pauses the operation. The worker parks at its next checkpoint, so no
     * work continues in the background.
     */
    public void pause() {
        if (!controllable || control.isCancelled()) return;
        control.pause();
        render();
    }

    /** Resumes a paused operation; the worker continues where it stopped. */
    public void resume() {
        if (!controllable) return;
        control.resume();
        render();
    }

    /**
     * Cancels the operation.
     *
     * <p>The dialog stays up showing that the operation is being cancelled and
     * closes itself once the worker has actually stopped, so the user is never
     * told "cancelled" while work is still running.
     */
    public void cancel() {
        if (!controllable || control.isCancelled()) return;
        cancelling = true;
        control.cancel();
        render();
    }

    /**
     * Called by the worker once it has stopped for good. Closes the dialog
     * when the operation was cancelled.
     */
    public void onFinished() {
        cancelling = false;
        if (control.isCancelled()) dismiss();
        else render();
    }

    // =======================================================================
    // Public API
    // =======================================================================

    public ProgressManager show() {
        markStarted();
        handler.post(() -> {
            if (dismissed || (dialog != null && dialog.isShowing())) return;
            contentView = LayoutInflater.from(activity)
                    .inflate(R.layout.progress_dialog, null, false);
            contentView.findViewById(R.id.hideButton).setOnClickListener(v1 -> hide());
            ((ProgressBar) contentView.findViewById(R.id.progressBar)).setIndeterminate(indeterminate);
            dialog = new MaterialAlertDialogBuilder(activity).setView(contentView).show();
            setupControls();
            render();
            startTicker();
        });
        return this;
    }

    /**
     * Wires the pause and cancel buttons, when this operation supports them.
     *
     * <p>The pause button toggles between Pause and Resume so one button
     * covers both directions, and the dialog itself is made non-cancelable:
     * closing it by tapping outside would leave the work running with no way
     * to stop it.
     */
    private void setupControls() {
        View controlRow = contentView.findViewById(R.id.controlRow);
        if (!controllable) {
            controlRow.setVisibility(View.GONE);
            return;
        }
        controlRow.setVisibility(View.VISIBLE);
        contentView.findViewById(R.id.pauseButton).setOnClickListener(v -> {
            if (control.isCancelled()) return;
            if (control.isPaused()) resume();
            else pause();
            render();
        });
        contentView.findViewById(R.id.cancelButton).setOnClickListener(v -> confirmCancel());
        // The dialog must not be dismissed by tapping outside or by Back: the
        // work would keep running with no visible way to stop it. The user can
        // still hide it (the notification carries the same controls) or cancel.
        if (dialog != null) {
            dialog.setCanceledOnTouchOutside(false);
            dialog.setCancelable(false);
        }
    }

    /**
     * Asks before cancelling, because a cancelled copy or delete cannot be
     * undone.
     */
    private void confirmCancel() {
        if (control.isCancelled()) return;
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.progress_cancel_confirm)
                .setMessage(R.string.progress_cancel_confirm_msg)
                .setPositiveButton(R.string.cancel, (d, w) -> cancel())
                .setNegativeButton(R.string.progress_keep_waiting, null)
                .show();
    }

    public void setText(int id, String append) {
        setText(activity.getString(id, append));
    }

    public ProgressManager setText(String text) {
        this.currentText = text;
        return this;
    }

    /**
     * Caption shown above the title, e.g. {@code COPY} or {@code EXTRACT}.
     * Pass 0 or null to hide it.
     */
    public ProgressManager setOperation(int resId) {
        return setOperation(resId == 0 ? null : activity.getString(resId));
    }

    public ProgressManager setOperation(String label) {
        this.operationLabel = label;
        return this;
    }

    /** Name of the item currently being processed, shown on its own line. */
    public ProgressManager setFile(String name) {
        this.fileName = name;
        return this;
    }

    public ProgressManager setFile(File file) {
        return setFile(file == null ? null : file.getName());
    }

    /** Absolute source/destination paths of the current item; either may be null. */
    public ProgressManager setPaths(String from, String to) {
        this.fromPath = from;
        this.toPath = to;
        return this;
    }

    public ProgressManager setPaths(File from, File to) {
        return setPaths(from == null ? null : from.getAbsolutePath(),
                to == null ? null : to.getAbsolutePath());
    }

    public ProgressManager setSource(File from) {
        this.fromPath = from == null ? null : from.getAbsolutePath();
        return this;
    }

    public ProgressManager setDestination(File to) {
        this.toPath = to == null ? null : to.getAbsolutePath();
        return this;
    }

    /** Declares the byte size of the work so speed, ETA and a smooth bar can be shown. */
    public ProgressManager setBytes(long done, long total) {
        markStarted();
        this.bytesDone = Math.max(0L, done);
        this.bytesTotal = total > 0 ? total : -1L;
        return this;
    }

    /** Adds to the byte counter. Safe to call thousands of times per second. */
    public ProgressManager addBytes(long delta) {
        if (delta > 0) bytesDone += delta;
        return this;
    }

    public ProgressManager setProgress(int progress, int max) {
        markStarted();
        this.progressVal = progress;
        this.maxVal = max;
        return this;
    }

    /** Declares the work size up front so the dialog opens already determinate. */
    public ProgressManager setTotal(int total) {
        return setProgress(progressVal, total);
    }

    /** Item counts without touching the bar; used alongside {@link #setBytes}. */
    public ProgressManager setItems(int done, int total) {
        this.progressVal = done;
        this.maxVal = total;
        return this;
    }

    /**
     * Turns on the "n / total (x%)" line. Kept for source compatibility only:
     * the statistics line is now driven by whatever the caller reports and
     * appears as soon as there is anything to say.
     */
    public ProgressManager showCount(boolean show) {
        return this;
    }

    /**
     * Adapter for the copy/extract helpers so a transfer can report itself
     * without knowing anything about dialogs.
     *
     * <p>The returned listener also carries the pause / cancel token, so every
     * loop that takes a listener inherits real pause and stop behaviour without
     * knowing a dialog exists.
     */
    public FileUtils.TransferListener listener() {
        return new FileUtils.TransferListener() {
            @Override
            public void onFile(File source, File destination) {
                // The item being worked on is the destination when there is one
                // (copy/move/extract write it), otherwise the source (delete,
                // compress reading a file).
                File shown = destination != null ? destination : source;
                if (shown != null) fileName = shown.getName();
                if (source != null) fromPath = source.getAbsolutePath();
                if (destination != null) toPath = destination.getAbsolutePath();
            }

            @Override
            public void onBytes(long count) {
                addBytes(count);
            }

            @Override
            public void checkpoint() {
                // Parks the worker while paused and unwinds it when cancelled.
                control.checkpoint();
            }

            @Override
            public TransferControl control() {
                return control;
            }
        };
    }

    public void dismiss() {
        handler.post(() -> {
            dismissed = true;
            // A dismissed dialog must not leave a worker parked in a
            // checkpoint: release it so the operation can finish and unwind.
            if (control.isPaused()) control.resume();
            stopTicker();
            activeInstances.remove(this);
            liveInstances.remove(this);
            if (dialog != null) dialog.dismiss();
            cancelNotification();
            contentView = null;
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
        startTicker();
    }

    public void restore() {
        handler.post(() -> {
            if (dismissed) return;
            hidden = false;
            cancelNotification();
            if (dialog != null && !dialog.isShowing()) {
                dialog.show();
            }
            startTicker();
        });
    }

    // =======================================================================
    // Rate sampling
    // =======================================================================

    private void markStarted() {
        if (startMs < 0) {
            startMs = SystemClock.elapsedRealtime();
            lastSampleMs = startMs;
            lastSampleBytes = bytesDone;
            lastSampleItems = progressVal;
        }
    }

    private void startTicker() {
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, TICK_MS);
    }

    private void stopTicker() {
        handler.removeCallbacks(ticker);
    }

    /**
     * Exponential moving average of bytes (and items) per second. Using a window
     * rather than "total / elapsed" means a transfer that stalls visibly drops to
     * 0 B/s instead of freezing at its historical average.
     */
    private void sampleRates() {
        long now = SystemClock.elapsedRealtime();
        if (lastSampleMs < 0) {
            lastSampleMs = now;
            lastSampleBytes = bytesDone;
            lastSampleItems = progressVal;
            return;
        }
        long dt = now - lastSampleMs;
        if (dt < SAMPLE_MIN_MS) return;

        long dBytes = bytesDone - lastSampleBytes;
        long dItems = (long) progressVal - lastSampleItems;
        // A negative delta means a new operation reused this instance; resync.
        if (dBytes < 0 || dItems < 0) {
            lastSampleMs = now;
            lastSampleBytes = bytesDone;
            lastSampleItems = progressVal;
            return;
        }
        // Guard against a torn read of the 64-bit counter on 32-bit devices.
        if (dBytes > 4L * 1024 * 1024 * 1024) return;

        double instantBytes = dBytes * 1000.0d / dt;
        double instantItems = dItems * 1000.0d / dt;
        speedBps = speedBps <= 0d ? instantBytes
                : speedBps * (1d - SPEED_SMOOTHING) + instantBytes * SPEED_SMOOTHING;
        itemRate = itemRate <= 0d ? instantItems
                : itemRate * (1d - SPEED_SMOOTHING) + instantItems * SPEED_SMOOTHING;

        lastSampleMs = now;
        lastSampleBytes = bytesDone;
        lastSampleItems = progressVal;
    }

    // =======================================================================
    // Rendering
    // =======================================================================

    private void render() {
        if (dismissed) return;
        if (contentView != null && dialog != null && dialog.isShowing()) {
            bind(contentView);
        }
        // The notification mirrors the dialog, including the paused badge and
        // the Pause/Resume label, so a hidden operation still reads correctly.
        if (hidden) updateNotification();
    }

    private boolean hasBytes() {
        return bytesTotal > 0;
    }

    private int percent() {
        if (hasBytes()) return clampPercent((int) (bytesDone * 100L / bytesTotal));
        if (maxVal > 0) return clampPercent((int) (progressVal * 100L / maxVal));
        return 0;
    }

    private static int clampPercent(int p) {
        return Math.min(100, Math.max(0, p));
    }

    private void bind(View v) {


        handler.post(() -> {
            TextView op = v.findViewById(R.id.operationLabel);
            if (isBlank(operationLabel)) {
                op.setVisibility(View.GONE);
            } else {
                op.setVisibility(View.VISIBLE);
                op.setText(operationLabel);
            }

            TextView title = v.findViewById(R.id.dialogTitle);
            title.setText(isBlank(currentText) ? activity.getString(R.string.progress_working) : currentText);

            TextView file = v.findViewById(R.id.currentFile);
            if (isBlank(fileName)) {
                file.setVisibility(View.GONE);
            } else {
                file.setVisibility(View.VISIBLE);
                file.setText(fileName);
            }

            View pathsBox = v.findViewById(R.id.pathsBox);
            TextView fromPathView = v.findViewById(R.id.fromPath);
            TextView toPathView = v.findViewById(R.id.toPath);
            boolean hasFrom = !isBlank(fromPath);
            boolean hasTo = !isBlank(toPath);
            if (!hasFrom && !hasTo) {
                pathsBox.setVisibility(View.GONE);
            } else {
                pathsBox.setVisibility(View.VISIBLE);
                fromPathView.setVisibility(hasFrom ? View.VISIBLE : View.GONE);
                v.findViewById(R.id.fromRow).setVisibility(hasFrom ? View.VISIBLE : View.GONE);
                if (hasFrom) fromPathView.setText(fromPath);
                toPathView.setVisibility(hasTo ? View.VISIBLE : View.GONE);
                v.findViewById(R.id.toRow).setVisibility(hasTo ? View.VISIBLE : View.GONE);
                if (hasTo) toPathView.setText(toPath);
            }

            ProgressBar pb = v.findViewById(R.id.progressBar);
            if (hasBytes()) {
                pb.setIndeterminate(false);
                pb.setMax(BYTE_BAR_SCALE);
                pb.setProgress((int) (BYTE_BAR_SCALE * Math.min(bytesDone, bytesTotal) / bytesTotal));
            } else if (maxVal > 0) {
                pb.setIndeterminate(false);
                pb.setMax(maxVal);
                pb.setProgress(Math.min(progressVal, maxVal));
            } else {
                pb.setIndeterminate(true);
            }

            TextView count = v.findViewById(R.id.progressCount);
            String stats = buildStatsLine();
            if (isBlank(stats)) {
                count.setVisibility(View.GONE);
            } else {
                count.setVisibility(View.VISIBLE);
                count.setText(stats);
            }

            TextView speed = v.findViewById(R.id.transferSpeed);
            String rate = buildSpeedLine();
            if (isBlank(rate)) {
                speed.setVisibility(View.GONE);
            } else {
                speed.setVisibility(View.VISIBLE);
                speed.setText(rate);
            }
            bindControlState(v);
        });
    }

    /**
     * Renders the pause / cancel row.
     *
     * <p>The badge tells the user the truth about the state: while the operation
     * is paused the worker really is parked, and while it is cancelling the
     * worker is unwinding but may still be finishing the item it was on. The
     * buttons disable themselves so a second tap cannot queue another action.
     */
    private void bindControlState(View v) {
        View controlRow = v.findViewById(R.id.controlRow);
        if (!controllable) {
            controlRow.setVisibility(View.GONE);
            return;
        }
        controlRow.setVisibility(View.VISIBLE);

        TextView badge = v.findViewById(R.id.pausedBadge);
        View pauseButton = v.findViewById(R.id.pauseButton);
        View cancelButton = v.findViewById(R.id.cancelButton);

        boolean cancelled = control.isCancelled();
        boolean pausing = control.isPaused();

        if (cancelled) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(R.string.progress_cancelling);
        } else if (pausing) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(R.string.progress_paused);
        } else {
            badge.setVisibility(View.GONE);
        }

        if (pauseButton instanceof TextView) {
            TextView p = (TextView) pauseButton;
            p.setText(pausing ? R.string.resume : R.string.progress_pause);
            p.setEnabled(!cancelled);
            p.setAlpha(cancelled ? 0.4f : 1f);
        }
        if (cancelButton instanceof TextView) {
            TextView c = (TextView) cancelButton;
            c.setEnabled(!cancelled);
            c.setAlpha(cancelled ? 0.4f : 1f);
        }
    }

    /** "42% • 3.4 MB / 8.1 MB • 4.7 MB left" or "128 / 300 (42%) • 172 left". */
    private String buildStatsLine() {
        StringBuilder sb = new StringBuilder();
        if (hasBytes()) {
            sb.append(activity.getString(R.string.progress_stat_bytes,
                    percent(), formatSize(bytesDone), formatSize(bytesTotal)));
            long left = bytesTotal - bytesDone;
            if (left > 0) {
                sb.append(" • ").append(activity.getString(R.string.progress_left_bytes, formatSize(left)));
            }
        } else if (maxVal > 0) {
            sb.append(activity.getString(R.string.progress_stat_items,
                    Math.min(progressVal, maxVal), maxVal, percent()));
            int leftItems = maxVal - Math.min(progressVal, maxVal);
            if (leftItems > 0) {
                sb.append(" • ").append(activity.getString(R.string.progress_left_items, leftItems));
            }
        } else {
            long elapsed = elapsedSeconds();
            if (elapsed <= 0) return "";
            return activity.getString(R.string.progress_stat_elapsed, formatEta(elapsed));
        }
        return sb.toString();
    }

    /** "2.4 MB/s • 12s left", "18/s • 4s left" or just the elapsed time. */
    private String buildSpeedLine() {
        StringBuilder sb = new StringBuilder();
        long eta = -1L;

        if (hasBytes() && speedBps >= 1d) {
            sb.append(activity.getString(R.string.progress_speed_bytes, formatSize((long) speedBps)));
            long left = bytesTotal - bytesDone;
            if (left > 0) eta = (long) (left / speedBps);
        } else if (!hasBytes() && maxVal > 0 && itemRate >= 0.2d) {
            sb.append(activity.getString(R.string.progress_speed_items, Math.round(itemRate)));
            int leftItems = maxVal - Math.min(progressVal, maxVal);
            if (leftItems > 0) eta = (long) (leftItems / itemRate);
        }

        if (eta > 0) {
            if (sb.length() > 0) sb.append(" • ");
            sb.append(activity.getString(R.string.progress_eta, formatEta(eta)));
        }
        return sb.toString();
    }

    private long elapsedSeconds() {
        if (startMs < 0) return 0L;
        return Math.max(0L, (SystemClock.elapsedRealtime() - startMs) / 1000L);
    }

    private String formatSize(long bytes) {
        if (bytes < 0) bytes = 0;
        return Formatter.formatShortFileSize(activity, bytes);
    }

    private static String formatEta(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) {
            long m = seconds / 60;
            long s = seconds % 60;
            return s == 0 ? m + "m" : m + "m " + s + "s";
        }
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    // =======================================================================
    // Hidden-dialog notification
    // =======================================================================

    private void initChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID,
                    activity.getString(R.string.notif_channel_progress), NotificationManager.IMPORTANCE_LOW);
            NotificationManager m = activity.getSystemService(NotificationManager.class);
            if (m != null) m.createNotificationChannel(c);
        }
    }

    private void showNotification() {
        nm = NotificationManagerCompat.from(activity);
        try {
            nm.notify(NOTIFICATION_ID, buildNotif().build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS revoked: the dialog still works, just no fallback.
        }
    }

    private void updateNotification() {
        if (nm == null) return;
        try {
            nm.notify(NOTIFICATION_ID, buildNotif().build());
        } catch (SecurityException ignored) {
        }
    }

    private NotificationCompat.Builder buildNotif() {
        Intent intent = new Intent(activity, activity.getClass());
        intent.setAction("io.github.abdurazaaqmohammed.RESTORE_PROGRESS");
        intent.putExtra("restore_progress", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pi = PendingIntent.getActivity(activity, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String title = isBlank(currentText) ? activity.getString(R.string.progress_working) : currentText;
        StringBuilder body = new StringBuilder();
        if (!isBlank(fileName)) body.append(fileName);
        String stats = buildStatsLine();
        if (!isBlank(stats)) {
            if (body.length() > 0) body.append('\n');
            body.append(stats);
        }
        String rate = buildSpeedLine();
        if (!isBlank(rate)) {
            if (body.length() > 0) body.append(" • ");
            body.append(rate);
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(activity, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body.length() == 0 ? activity.getString(R.string.progress_working) : body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body.length() == 0
                        ? activity.getString(R.string.progress_working) : body.toString()))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);

        if (hasBytes()) {
            b.setProgress(BYTE_BAR_SCALE,
                    (int) (BYTE_BAR_SCALE * Math.min(bytesDone, bytesTotal) / bytesTotal), false);
        } else if (maxVal > 0) {
            b.setProgress(maxVal, Math.min(progressVal, maxVal), false);
        } else {
            b.setProgress(0, 0, true);
        }
        addControlActions(b);
        return b;
    }

    /**
     * Adds the pause / cancel buttons to the notification.
     *
     * <p>A hidden dialog is the one case where the user has no other way to
     * stop the work, so the notification carries the same two actions.
     */
    private void addControlActions(NotificationCompat.Builder b) {
        if (!controllable || control.isCancelled()) return;
        b.addAction(0,
                activity.getString(control.isPaused() ? R.string.resume : R.string.progress_pause),
                actionIntent(ProgressActionReceiver.ACTION_TOGGLE_PAUSE, 1));
        b.addAction(0, activity.getString(R.string.cancel),
                actionIntent(ProgressActionReceiver.ACTION_CANCEL, 2));
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(activity, ProgressActionReceiver.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(activity, requestCode, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void cancelNotification() {
        if (nm != null) {
            try {
                nm.cancel(NOTIFICATION_ID);
            } catch (Exception ignored) {
            }
        }
    }
}

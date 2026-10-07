package io.github.abdurazaaqmohammed.packs.media;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ScreenCapture {

    public static final int REQ_CAPTURE = 9101;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaProjectionManager projectionManager;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder recorder;
    private boolean recording;
    private boolean paused;
    private long startElapsed;
    private long pausedTotal;
    private long pauseStarted;
    private File outFile;
    private Runnable tick;
    private boolean pendingStart;
    private int pendingCountdown;
    private int touchesOrig = -1;

    private TextView status;
    private TextView timerText;
    private TextView sizeText;
    private MaterialButton recBtn;
    private MaterialButton pauseBtn;

    private final List<File> files = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private View.OnTouchListener swipeWatch;

    public void setSwipeWatch(View.OnTouchListener watch) {
        swipeWatch = watch;
    }

    private final int[] resHeights = new int[]{720, 1080, -1};
    private final int[] frameRates = new int[]{24, 30, 60};
    private final int[] bitRates = new int[]{4000000, 8000000, 12000000, 16000000};
    private final int[] countdowns = new int[]{0, 3, 5};

    public ScreenCapture(Context context) {
        this.context = context;
        try {
            projectionManager = (MediaProjectionManager)
                    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        } catch (Exception ignored) {
        }
    }

    public View buildView() {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        status = new TextView(context);
        status.setTextSize(14);
        status.setTypeface(Typeface.MONOSPACE);
        status.setText(RecCommon.T(context, "rec_ready", "Ready"));
        box.addView(status);
        timerText = new TextView(context);
        timerText.setText(PackRes.str("media", R.string.s_00_00, "00:00"));
        timerText.setTextSize(40);
        timerText.setTypeface(Typeface.MONOSPACE);
        timerText.setGravity(Gravity.CENTER);
        box.addView(timerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sizeText = new TextView(context);
        sizeText.setText("");
        sizeText.setGravity(Gravity.CENTER);
        sizeText.setAlpha(0.7f);
        box.addView(sizeText);
        ToolViewFactory.addLabel(box, RecCommon.T(context, "rec_settings", "Settings"));
        String[] resNames = new String[]{"720p", "1080p",
                RecCommon.T(context, "rec_native", "Native")};
        optionMenu(box, RecCommon.T(context, "rec_resolution", "Resolution"), resNames,
                RecCommon.optInt(context, "s_res", 1),
                pos -> RecCommon.putOpt(context, "s_res", pos));
        String[] fpsNames = new String[]{"24", "30", "60"};
        optionMenu(box, RecCommon.T(context, "rec_frame_rate", "Frame rate"), fpsNames,
                RecCommon.optInt(context, "s_fps", 1),
                pos -> RecCommon.putOpt(context, "s_fps", pos));
        String[] brNames = new String[]{"4M", "8M", "12M", "16M"};
        optionMenu(box, RecCommon.T(context, "rec_video_bitrate", "Video bitrate"), brNames,
                RecCommon.optInt(context, "s_br", 1),
                pos -> RecCommon.putOpt(context, "s_br", pos));
        String[] boolNames = new String[]{
                RecCommon.T(context, "rec_off", "Off"), RecCommon.T(context, "rec_on", "On")};
        optionMenu(box, RecCommon.T(context, "rec_audio_track", "Audio track"), boolNames,
                RecCommon.optBool(context, "s_audio", true) ? 1 : 0,
                pos -> RecCommon.putOpt(context, "s_audio", pos == 1));
        optionMenu(box, RecCommon.T(context, "rec_countdown", "Countdown"), new String[]{
                RecCommon.T(context, "rec_off", "Off"), "3s", "5s"},
                RecCommon.optInt(context, "s_count", 0),
                pos -> RecCommon.putOpt(context, "s_count", pos));
        optionMenu(box, RecCommon.T(context, "rec_show_touches", "Show touches"), boolNames,
                RecCommon.optBool(context, "s_touches", false) ? 1 : 0,
                pos -> RecCommon.putOpt(context, "s_touches", pos == 1));
        numberField(box, RecCommon.T(context, "rec_max_length", "Max length (min)")
                        + " — 0 = " + RecCommon.T(context, "rec_off", "Off"),
                RecCommon.optInt(context, "s_max", 0),
                v -> RecCommon.putOpt(context, "s_max", v));
        LinearLayout recRow = ToolViewFactory.makeRow(box);
        recBtn = ToolViewFactory.makeRowButton(recRow,
                RecCommon.T(context, "rec_record", "Record"), 1f);
        pauseBtn = ToolViewFactory.makeRowButton(recRow,
                RecCommon.T(context, "rec_pause", "Pause"), 1f);
        pauseBtn.setEnabled(false);
        recBtn.setOnClickListener(v -> {
            if (recording) stop(true);
            else start();
        });
        pauseBtn.setOnClickListener(v -> togglePause());
        ToolViewFactory.addLabel(box, RecCommon.T(context, "rec_files", "Videos"));
        ListView listView = new ListView(context);
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        if (swipeWatch != null) listView.setOnTouchListener(swipeWatch);
        box.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 200)));
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= files.size()) return;
            RecCommon.openFile(context, files.get(position), "video/*");
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= files.size()) return true;
            fileMenu(files.get(position));
            return true;
        });
        ToolViewFactory.addLabel(box, RecCommon.T(context, "rec_screen_hint",
                "Records everything on your screen. Tap a clip to play it, long-press for more actions."));
        refreshList();
        return box;
    }

    private int maxMinutesRaw() {
        int v = 0;
        try {
            v = RecCommon.optInt(context, "s_max", 0);
        } catch (Exception ignored) {
        }
        return Math.max(0, Math.min(1440, v));
    }

    private void optionMenu(LinearLayout box, String label, String[] values,
                            int current, Consumer<Integer> pick) {
        TextInputLayout layout =
                new TextInputLayout(context, null,
                        com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        layout.setHint(label);
        MaterialAutoCompleteTextView field =
                new MaterialAutoCompleteTextView(
                        layout.getContext());
        field.setInputType(InputType.TYPE_NULL);
        ArrayAdapter<String> ad = new ArrayAdapter<>(context,
                android.R.layout.simple_list_item_1, values);
        field.setAdapter(ad);
        if (current >= 0 && current < values.length) field.setText(values[current], false);
        field.setOnItemClickListener((p, v, pos, id) -> {
            try {
                pick.accept(pos);
            } catch (Exception ignored) {
            }
        });
        layout.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ToolViewFactory.dp(context, 4);
        box.addView(layout, lp);
    }

    private void numberField(LinearLayout box, String label, int current,
                             Consumer<Integer> pick) {
        TextInputLayout layout =
                new TextInputLayout(context);
        layout.setHint(label);
        TextInputEditText field =
                new TextInputEditText(layout.getContext());
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(String.valueOf(Math.max(0, current)));
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            public void afterTextChanged(Editable s) {
                try {
                    int v = Integer.parseInt(s.toString().trim());
                    if (v >= 0 && v <= 1440) pick.accept(v);
                } catch (Exception ignored) {
                }
            }
        });
        layout.addView(field, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ToolViewFactory.dp(context, 4);
        box.addView(layout, lp);
    }

    private boolean audioOn() {
        return RecCommon.optBool(context, "s_audio", true);
    }

    private boolean hasAudioPerm() {
        try {
            return ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void startService() {
        try {
            Intent intent = new Intent(context,
                    Class.forName("io.github.abdurazaaqmohammed.tools.ScreenCaptureService"));
            intent.setAction("io.github.abdurazaaqmohammed.MPManager.action.SCREEN_CAPTURE_START");
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    private void stopService() {
        try {
            Intent intent = new Intent(context,
                    Class.forName("io.github.abdurazaaqmohammed.tools.ScreenCaptureService"));
            intent.setAction("io.github.abdurazaaqmohammed.MPManager.action.SCREEN_CAPTURE_STOP");
            context.startService(intent);
        } catch (Exception ignored) {
        }
    }

    private void start() {
        if (projectionManager == null) {
            status.setText(RecCommon.T(context, "rec_failed_start", "Could not start capture"));
            return;
        }
        if (audioOn() && !hasAudioPerm()) {
            try {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.RECORD_AUDIO}, 9003);
            } catch (Exception ignored) {
            }
            ToolViewFactory.toast(context, RecCommon.T(context, "rec_mic_needed",
                    "Microphone permission needed, then tap Record"));
            return;
        }
        startService();
        pendingStart = true;
        try {
            ((Activity) context).startActivityForResult(
                    projectionManager.createScreenCaptureIntent(), REQ_CAPTURE);
        } catch (Exception e) {
            pendingStart = false;
            stopService();
            status.setText(RecCommon.T(context, "rec_failed_start", "Could not start capture"));
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_CAPTURE || !pendingStart) return;
        pendingStart = false;
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopService();
            status.setText(RecCommon.T(context, "rec_denied", "Capture denied"));
            return;
        }
        int countIdx = RecCommon.optInt(context, "s_count", 0);
        if (countIdx < 0 || countIdx >= countdowns.length) countIdx = 0;
        pendingCountdown = countdowns[countIdx];
        final int rc = resultCode;
        final Intent rd = data;
        if (pendingCountdown > 0) {
            status.setText(RecCommon.T(context, "rec_starting", "Starting"));
            handler.post(new Runnable() {
                int left = pendingCountdown;

                public void run() {
                    if (!pendingStart && left <= 0) return;
                    if (left > 0) {
                        if (timerText != null) timerText.setText(String.valueOf(left));
                        left--;
                        handler.postDelayed(this, 1000);
                    } else {
                        beginCapture(rc, rd);
                    }
                }
            });
        } else {
            beginCapture(rc, rd);
        }
    }

    private void beginCapture(int resultCode, Intent data) {
        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int resIdx = RecCommon.optInt(context, "s_res", 1);
            if (resIdx < 0 || resIdx >= resHeights.length) resIdx = 1;
            int w = dm.widthPixels;
            int h = dm.heightPixels;
            if (resHeights[resIdx] > 0) {
                if (h >= w) {
                    h = resHeights[resIdx];
                    w = Math.max(2, (dm.widthPixels * h / Math.max(1, dm.heightPixels)) / 2 * 2);
                } else {
                    w = resHeights[resIdx];
                    h = Math.max(2, (dm.heightPixels * w / Math.max(1, dm.widthPixels)) / 2 * 2);
                }
            } else {
                w = w / 2 * 2;
                h = h / 2 * 2;
            }
            int fpsIdx = RecCommon.optInt(context, "s_fps", 1);
            if (fpsIdx < 0 || fpsIdx >= frameRates.length) fpsIdx = 1;
            int brIdx = RecCommon.optInt(context, "s_br", 1);
            if (brIdx < 0 || brIdx >= bitRates.length) brIdx = 1;
            int maxMin = maxMinutesRaw();
            recorder = new MediaRecorder();
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            if (audioOn()) recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            if (audioOn()) recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setVideoSize(w, h);
            recorder.setVideoFrameRate(frameRates[fpsIdx]);
            recorder.setVideoEncodingBitRate(bitRates[brIdx]);
            File dir = RecCommon.recordingsDir(context);
            outFile = new File(dir, "screen_" + RecCommon.stamp() + ".mp4");
            recorder.setOutputFile(outFile.getAbsolutePath());
            if (maxMin > 0) {
                recorder.setMaxDuration(maxMin * 60000);
                recorder.setOnInfoListener((mr, what, extra) -> {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stop(true);
                    }
                });
            }
            recorder.prepare();
            projection = projectionManager.getMediaProjection(resultCode, data);
            virtualDisplay = projection.createVirtualDisplay("screen-capture", w, h, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    recorder.getSurface(), null, null);
            recorder.start();
            recording = true;
            paused = false;
            pausedTotal = 0;
            startElapsed = SystemClock.elapsedRealtime();
            applyTouches(true);
            recBtn.setText(RecCommon.T(context, "rec_stop", "Stop"));
            pauseBtn.setEnabled(true);
            pauseBtn.setText(RecCommon.T(context, "rec_pause", "Pause"));
            status.setText(RecCommon.T(context, "rec_recording", "Recording")
                    + " " + outFile.getName());
            timerText.setText(PackRes.str("media", R.string.s_00_00, "00:00"));
            if (tick == null) {
                tick = new Runnable() {
                    public void run() {
                        if (!recording) return;
                        long elapsed = SystemClock.elapsedRealtime() - startElapsed - pausedTotal
                                - (paused ? (SystemClock.elapsedRealtime() - pauseStarted) : 0);
                        if (timerText != null) {
                            timerText.setText(RecCommon.fmtDurH(elapsed));
                        }
                        if (sizeText != null && outFile != null) {
                            sizeText.setText(RecCommon.formatBytes(outFile.length()));
                        }
                        handler.postDelayed(this, 250);
                    }
                };
            }
            handler.post(tick);
        } catch (Exception e) {
            cleanupCapture();
            stopService();
            status.setText(RecCommon.T(context, "rec_failed_start", "Could not start capture"));
        }
    }

    private void applyTouches(boolean on) {
        boolean want = on && RecCommon.optBool(context, "s_touches", false);
        try {
            ContentResolver cr = context.getContentResolver();
            if (on && want) {
                try {
                    touchesOrig = Settings.Global.getInt(cr, "show_touches", 0);
                } catch (Exception ignored) {
                    touchesOrig = 0;
                }
            }
            Settings.Global.putInt(cr, "show_touches",
                    (on && want) ? 1 : (touchesOrig < 0 ? 0 : touchesOrig));
        } catch (Exception ignored) {
        }
    }

    private void togglePause() {
        if (!recording || recorder == null) return;
        if (Build.VERSION.SDK_INT < 24) {
            ToolViewFactory.toast(context, RecCommon.T(context, "rec_pause_old_android",
                    "Pause needs Android 7+"));
            return;
        }
        try {
            if (!paused) {
                recorder.pause();
                paused = true;
                pauseBtn.setText(RecCommon.T(context, "rec_resume", "Resume"));
                status.setText(RecCommon.T(context, "rec_paused", "Paused"));
            } else {
                recorder.resume();
                paused = false;
                status.setText(RecCommon.T(context, "rec_recording", "Recording"));
            }
        } catch (Exception e) {
            ToolViewFactory.toast(context,
                    RecCommon.T(context, "rec_pause_failed", "Pause failed"));
        }
    }

    private void stop(boolean save) {
        try {
            handler.removeCallbacks(tick);
        } catch (Exception ignored) {
        }
        try {
            if (paused && Build.VERSION.SDK_INT >= 24) {
                try {
                    recorder.resume();
                } catch (Exception ignored) {
                }
            }
            recorder.stop();
        } catch (Exception ignored) {
        }
        cleanupCapture();
        applyTouches(false);
        stopService();
        recording = false;
        paused = false;
        recBtn.setText(RecCommon.T(context, "rec_record", "Record"));
        pauseBtn.setEnabled(false);
        pauseBtn.setText(RecCommon.T(context, "rec_pause", "Pause"));
        timerText.setText(PackRes.str("media", R.string.s_00_00, "00:00"));
        sizeText.setText("");
        if (save && outFile != null && outFile.exists() && outFile.length() > 0) {
            status.setText(RecCommon.T(context, "rec_saved", "Saved") + " " + outFile.getName());
        } else {
            if (outFile != null) {
                try {
                    outFile.delete();
                } catch (Exception ignored) {
                }
            }
            status.setText(RecCommon.T(context, "rec_ready", "Ready"));
        }
        outFile = null;
        refreshList();
    }

    private void cleanupCapture() {
        try {
            if (recorder != null) {
                try {
                    recorder.reset();
                } catch (Exception ignored) {
                }
                try {
                    recorder.release();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        recorder = null;
        try {
            if (virtualDisplay != null) virtualDisplay.release();
        } catch (Exception ignored) {
        }
        virtualDisplay = null;
        try {
            if (projection != null) projection.stop();
        } catch (Exception ignored) {
        }
        projection = null;
    }

    private void refreshList() {
        try {
            files.clear();
            names.clear();
            File dir = RecCommon.recordingsDir(context);
            File[] all = dir.listFiles();
            if (all != null) {
                Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : all) {
                    if (f.isDirectory()) continue;
                    if (!f.getName().toLowerCase().endsWith(".mp4")) continue;
                    files.add(f);
                }
            }
            for (File f : files) {
                long d = RecCommon.mediaDuration(f);
                names.add(f.getName() + "  " + (d > 0 ? RecCommon.fmtDur(d) + "  " : "")
                        + RecCommon.formatBytes(f.length()));
            }
            if (adapter != null) adapter.notifyDataSetChanged();
        } catch (Exception ignored) {
        }
    }

    private void fileMenu(final File f) {
        String[] opts = new String[]{
                RecCommon.T(context, "rec_rename", "Rename"),
                RecCommon.T(context, "rec_share", "Share"),
                RecCommon.T(context, "rec_open", "Open"),
                RecCommon.T(context, "rec_delete", "Delete")};
        new MaterialAlertDialogBuilder(context).setTitle(f.getName()).setItems(opts, (d, which) -> {
            if (which == 0) {
                final EditText nameInput = new EditText(context);
                String n = f.getName();
                int dot = n.lastIndexOf('.');
                nameInput.setText(dot > 0 ? n.substring(0, dot) : n);
                new MaterialAlertDialogBuilder(context)
                        .setTitle(RecCommon.T(context, "rec_rename", "Rename"))
                        .setView(nameInput)
                        .setPositiveButton(RecCommon.T(context, "rec_save", "Save"), (dd, w) -> {
                            try {
                                String base = nameInput.getText().toString().trim()
                                        .replaceAll("[^a-zA-Z0-9 _-]+", "");
                                if (base.isEmpty()) return;
                                String ext = "";
                                int dot2 = f.getName().lastIndexOf('.');
                                if (dot2 > 0) ext = f.getName().substring(dot2);
                                File t = new File(f.getParent(), base + ext);
                                if (f.renameTo(t)) refreshList();
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton(RecCommon.T(context, "rec_cancel", "Cancel"), null)
                        .show();
            } else if (which == 1) {
                RecCommon.shareFile(context, f, "video/*");
            } else if (which == 2) {
                RecCommon.openFile(context, f, "video/*");
            } else {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
                refreshList();
            }
        }).show();
    }

    public void onShown() {
    }

    public void onHidden() {
    }

    public boolean isRecording() {
        return recording;
    }

    public void onDestroy() {
        try {
            handler.removeCallbacks(tick);
        } catch (Exception ignored) {
        }
        handler.removeCallbacksAndMessages(null);
        if (recording) stop(true);
        else cleanupCapture();
        applyTouches(false);
        stopService();
    }
}

package io.github.abdurazaaqmohammed.packs.media;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.PagedShell;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class RecorderTool extends BaseToolPlugin {

    private static final int TAB_AUDIO = 1;
    private static final int TAB_VIDEO = 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context host;
    private View audioPage;
    private View videoPage;
    private BottomNavigationView nav;
    private ScreenCapture video;
    private PagedShell shell;

    private MediaRecorder voiceRecorder;
    private MediaPlayer voicePlayer;
    private boolean recordingNow;
    private boolean recordingPaused;
    private long recPausedTotal;
    private long recStartElapsed;
    private long recPauseStarted;
    private List<Float> recAmps = new ArrayList<>();
    private Runnable recTick;
    private boolean playSeeking;
    private List<Float> playAmps = new ArrayList<>();
    private int playDurationMs;
    private RecWaveView recWaveView;
    private RecWaveView playWaveView;
    private TextView recTimerText;
    private TextView playTimeText;
    private SeekBar playSeek;
    private File recCurrentFile;
    private File recOutFile;
    private Runnable playTick;

    private TextView audioStatus;
    private MaterialButton recBtn;
    private MaterialButton pauseBtn;
    private TextView nowPlayingView;
    private MaterialButton playBtn;
    private final List<File> files = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;
    private Runnable refreshAudioList;

    private final int[] audioSources = new int[]{
            MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.VOICE_COMMUNICATION};
    private final int[] sampleRates = new int[]{8000, 16000, 22050, 44100, 48000};
    private final int[] bitRates = new int[]{64000, 96000, 128000, 192000};
    private int maxMinutesRaw() {
        int v = 0;
        try {
            v = RecCommon.optInt(host, "a_max", 0);
        } catch (Exception ignored) {
        }
        return Math.max(0, Math.min(1440, v));
    }

    public RecorderTool() {
        super("recorder", "Recorder", "Record audio and video", ToolCategories.MEDIA);
    }

    private String T(String key, String fallback) {
        return RecCommon.T(host == null ? null : host, key, fallback);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        try {
            return createViewInner(context);
        } catch (Exception e) {
            try {
                Log.e("RecorderTool", "createView failed", e);
            } catch (Exception ignored) {
            }
            LinearLayout box = new LinearLayout(context);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = ToolViewFactory.dp(context, 16);
            box.setPadding(pad, pad, pad, pad);
            TextView err = new TextView(context);
            err.setText("Recorder failed to start:\n" + e);
            err.setTextIsSelectable(true);
            box.addView(err);
            return box;
        }
    }

    private View createViewInner(Context context) {
        host = context;
        nav = new BottomNavigationView(context);
        Menu menu = nav.getMenu();
        navItem(menu, TAB_AUDIO, T("rec_audio", "Audio"),
                new String[]{"mic_24px", "queue_music_24px", "music_24px"},
                android.R.drawable.ic_btn_speak_now);
        navItem(menu, TAB_VIDEO, T("rec_screen", "Screen"),
                new String[]{"video_24px", "videocam_24px"},
                android.R.drawable.ic_menu_camera);
        nav.setLabelVisibilityMode(
                NavigationBarView.LABEL_VISIBILITY_LABELED);
        nav.setSelectedItemId(TAB_AUDIO);
        List<PagedShell.Page> pages = new ArrayList<>();
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return T("rec_audio", "Audio");
            }

            @Override
            public View build(Context ctx) {
                if (audioPage == null) audioPage = buildAudioPage();
                ScrollView sc = new ScrollView(ctx);
                sc.addView(audioPage, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return sc;
            }
        });
        pages.add(new PagedShell.Page() {
            @Override
            public String title() {
                return T("rec_screen", "Screen");
            }

            @Override
            public View build(Context ctx) {
                if (videoPage == null) {
                    video = new ScreenCapture(host);
                    videoPage = video.buildView();
                }
                ScrollView sc = new ScrollView(ctx);
                sc.addView(videoPage, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return sc;
            }

            @Override
            public void shown() {
                try {
                    if (video != null) video.onShown();
                } catch (Exception ignored) {
                }
            }

            @Override
            public void hidden() {
                try {
                    if (video != null) video.onHidden();
                } catch (Exception ignored) {
                }
            }
        });
        shell = new PagedShell(context, pages, null, nav, 0);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            showTab(id == TAB_VIDEO ? 1 : 0);
            return true;
        });
        shell.onSelect(position -> {
            try {
                int id = position == 1 ? TAB_VIDEO : TAB_AUDIO;
                if (nav.getSelectedItemId() != id) nav.setSelectedItemId(id);
            } catch (Exception ignored) {
            }
        });
        showTab(0);
        return shell.view();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (video != null) video.onActivityResult(requestCode, resultCode, data);
        } catch (Exception ignored) {
        }
    }

    private void navItem(Menu menu, int id, String title, String[] candidates, int fallback) {
        MenuItem item = menu.add(0, id, id, title);
        int res = 0;
        try {
            for (String name : candidates) {
                res = host.getResources().getIdentifier(name, "drawable", host.getPackageName());
                if (res != 0) break;
            }
        } catch (Exception ignored) {
        }
        try {
            item.setIcon(ContextCompat.getDrawable(host, res != 0 ? res : fallback));
        } catch (Exception ignored) {
        }
    }

    private void showTab(int tab) {
        try {
            if (shell != null) shell.select(tab);
        } catch (Exception e) {
            try {
                Log.e("RecorderTool", "showTab failed", e);
            } catch (Exception ignored) {
            }
        }
    }

    private void optionMenu(LinearLayout box, String label, String[] values,
                            int current, Consumer<Integer> pick) {
        TextInputLayout layout =
                new TextInputLayout(host, null,
                        com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        layout.setHint(label);
        MaterialAutoCompleteTextView field = new MaterialAutoCompleteTextView(layout.getContext());
        field.setInputType(InputType.TYPE_NULL);
        ArrayAdapter<String> ad = new ArrayAdapter<>(host,
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
        lp.bottomMargin = ToolViewFactory.dp(host, 4);
        box.addView(layout, lp);
    }

    private void numberField(LinearLayout box, String label, int current,
                             Consumer<Integer> pick) {
        TextInputLayout layout =
                new TextInputLayout(host);
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
        lp.bottomMargin = ToolViewFactory.dp(host, 4);
        box.addView(layout, lp);
    }

    private View buildAudioPage() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        final Context context = host;
        audioStatus = ToolViewFactory.makeOutput(box);
        audioStatus.setText(T("rec_ready", "Ready"));
        recTimerText = new TextView(context);
        recTimerText.setText("00:00");
        recTimerText.setTextSize(40);
        recTimerText.setTypeface(Typeface.MONOSPACE);
        recTimerText.setGravity(Gravity.CENTER);
        box.addView(recTimerText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        recWaveView = new RecWaveView(context);
        recWaveView.setMinimumHeight(ToolViewFactory.dp(context, 90));
        box.addView(recWaveView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 90)));
        ToolViewFactory.addLabel(box, T("rec_settings", "Settings"));
        final String[] sourceNames = new String[]{
                T("rec_src_mic", "Microphone"), T("rec_src_camcorder", "Camcorder"),
                T("rec_src_recognition", "Voice recognition"),
                T("rec_src_communication", "Voice call")};
        final String[] formatNames = new String[]{
                T("rec_fmt_aac", "High quality (AAC)"), T("rec_fmt_amr", "Small size (AMR)")};
        final String[] channelNames = new String[]{
                T("rec_mono", "Mono"), T("rec_stereo", "Stereo")};
        String[] rateNames = new String[sampleRates.length];
        for (int i = 0; i < sampleRates.length; i++) rateNames[i] = sampleRates[i] + " Hz";
        String[] bitrateNames = new String[bitRates.length];
        for (int i = 0; i < bitRates.length; i++) bitrateNames[i] = (bitRates[i] / 1000) + "k";
        optionMenu(box, T("rec_source", "Source"), sourceNames,
                RecCommon.optInt(context, "a_source", 0),
                pos -> RecCommon.putOpt(context, "a_source", pos));
        optionMenu(box, T("rec_format", "Format"), formatNames,
                RecCommon.optInt(context, "a_format", 0),
                pos -> RecCommon.putOpt(context, "a_format", pos));
        optionMenu(box, T("rec_sample_rate", "Sample rate"), rateNames,
                RecCommon.optInt(context, "a_rate", 3),
                pos -> RecCommon.putOpt(context, "a_rate", pos));
        optionMenu(box, T("rec_bitrate", "Bitrate"), bitrateNames,
                RecCommon.optInt(context, "a_bitrate", 2),
                pos -> RecCommon.putOpt(context, "a_bitrate", pos));
        optionMenu(box, T("rec_channels", "Channels"), channelNames,
                RecCommon.optInt(context, "a_channels", 1),
                pos -> RecCommon.putOpt(context, "a_channels", pos));
        numberField(box, T("rec_max_length", "Max length (min)") + " - 0 = " + T("rec_off", "Off"),
                RecCommon.optInt(context, "a_max", 0),
                v -> RecCommon.putOpt(context, "a_max", v));
        LinearLayout recRow = ToolViewFactory.makeRow(box);
        recBtn = ToolViewFactory.makeRowButton(recRow, T("rec_record", "Record"), 1f);
        pauseBtn = ToolViewFactory.makeRowButton(recRow, T("rec_pause", "Pause"), 1f);
        pauseBtn.setEnabled(false);
        ToolViewFactory.addLabel(box, T("rec_now_playing", "Now playing"));
        nowPlayingView = ToolViewFactory.makeOutput(box);
        nowPlayingView.setText(T("rec_nothing_loaded", "Nothing loaded"));
        playWaveView = new RecWaveView(context);
        playWaveView.setMinimumHeight(ToolViewFactory.dp(context, 90));
        box.addView(playWaveView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 90)));
        playTimeText = new TextView(context);
        playTimeText.setText("00:00 / 00:00");
        playTimeText.setTypeface(Typeface.MONOSPACE);
        box.addView(playTimeText);
        playSeek = new SeekBar(context);
        playSeek.setMax(0);
        playSeek.setProgress(0);
        box.addView(playSeek);
        playSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser && voicePlayer != null && playDurationMs > 0) {
                    try {
                        voicePlayer.seekTo(progress);
                    } catch (Exception ignored) {
                    }
                    playTimeText.setText(RecCommon.fmtDur(progress) + " / " + RecCommon.fmtDur(playDurationMs));
                    if (playWaveView != null) {
                        playWaveView.setProgress(playDurationMs == 0 ? 0 : progress / (float) playDurationMs);
                    }
                }
            }

            public void onStartTrackingTouch(SeekBar s) {
                playSeeking = true;
            }

            public void onStopTrackingTouch(SeekBar s) {
                playSeeking = false;
            }
        });
        LinearLayout playRow = ToolViewFactory.makeRow(box);
        playBtn = ToolViewFactory.makeRowButton(playRow, T("rec_play", "Play"), 1f);
        final MaterialButton stopPlayBtn = ToolViewFactory.makeRowButton(playRow,
                T("rec_stop", "Stop"), 1f);
        final MaterialButton speedBtn = ToolViewFactory.makeRowButton(playRow, "1x", 1f);
        final float[] speeds = new float[]{1f, 1.25f, 1.5f, 2f};
        final int[] speedIdx = new int[]{0};
        speedBtn.setOnClickListener(v -> {
            speedIdx[0] = (speedIdx[0] + 1) % speeds.length;
            speedBtn.setText(speeds[speedIdx[0]] + "x");
            try {
                if (voicePlayer != null && Build.VERSION.SDK_INT >= 23) {
                    voicePlayer.setPlaybackParams(voicePlayer.getPlaybackParams().setSpeed(speeds[speedIdx[0]]));
                }
            } catch (Exception ignored) {
            }
        });
        final ListView listView = new ListView(context);
        listAdapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(listAdapter);
        box.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 220)));
        refreshAudioList = () -> {
            files.clear();
            names.clear();
            try {
                File legacy = new File(context.getCacheDir(), "recordings");
                File[] old = legacy.listFiles();
                if (old != null && old.length > 0) {
                    File dest = RecCommon.recordingsDir(context);
                    for (File f : old) {
                        if (f.getName().endsWith(".amp")) continue;
                        try {
                            File t = new File(dest, f.getName());
                            if (!t.exists() && f.renameTo(t)) {
                                File a = new File(f.getAbsolutePath() + ".amp");
                                if (a.exists()) {
                                    a.renameTo(new File(t.getAbsolutePath() + ".amp"));
                                }
                            } else {
                                files.add(f);
                            }
                        } catch (Exception ignored) {
                            files.add(f);
                        }
                    }
                }
                File dir = RecCommon.recordingsDir(context);
                File[] all = dir.listFiles();
                if (all != null) {
                    Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File f : all) {
                        if (f.isDirectory() || f.getName().endsWith(".amp")) continue;
                        String n = f.getName().toLowerCase();
                        if (!n.endsWith(".m4a") && !n.endsWith(".3gp")) continue;
                        if (!files.contains(f)) files.add(f);
                    }
                }
                for (File f : files) {
                    long d = RecCommon.mediaDuration(f);
                    names.add(f.getName() + "  " + (d > 0 ? RecCommon.fmtDur(d) + "  " : "")
                            + RecCommon.formatBytes(f.length()));
                }
            } catch (Exception ignored) {
            }
            listAdapter.notifyDataSetChanged();
        };
        refreshAudioList.run();
        recBtn.setOnClickListener(v -> {
            if (recordingNow) {
                stopAudio();
                return;
            }
            startAudio();
        });
        pauseBtn.setOnClickListener(v -> {
            if (!recordingNow || voiceRecorder == null) return;
            if (Build.VERSION.SDK_INT < 24) {
                ToolViewFactory.toast(context,
                        T("rec_pause_old_android", "Pause needs Android 7+"));
                return;
            }
            try {
                if (!recordingPaused) {
                    voiceRecorder.pause();
                    recordingPaused = true;
                    recPauseStarted = SystemClock.elapsedRealtime();
                    pauseBtn.setText(T("rec_resume", "Resume"));
                    audioStatus.setText(T("rec_paused", "Paused"));
                } else {
                    voiceRecorder.resume();
                    recordingPaused = false;
                    recPausedTotal += SystemClock.elapsedRealtime() - recPauseStarted;
                    pauseBtn.setText(T("rec_pause", "Pause"));
                    audioStatus.setText(T("rec_recording", "Recording"));
                }
            } catch (Exception e) {
                ToolViewFactory.toast(context, T("rec_pause_failed", "Pause failed"));
            }
        });
        playBtn.setOnClickListener(v -> {
            try {
                if (voicePlayer != null && voicePlayer.isPlaying()) {
                    voicePlayer.pause();
                    playBtn.setText(T("rec_play", "Play"));
                    return;
                }
                if (voicePlayer != null && playDurationMs > 0) {
                    try {
                        if (Build.VERSION.SDK_INT >= 23) {
                            voicePlayer.setPlaybackParams(
                                    voicePlayer.getPlaybackParams().setSpeed(speeds[speedIdx[0]]));
                        }
                    } catch (Exception ignored) {
                    }
                    voicePlayer.start();
                    playBtn.setText(T("rec_pause", "Pause"));
                    return;
                }
                File f = recCurrentFile != null ? recCurrentFile
                        : (files.isEmpty() ? null : files.get(0));
                if (f == null || !f.exists()) {
                    ToolViewFactory.toast(context, T("rec_no_recordings", "No recordings yet"));
                    return;
                }
                playRecordingFile(f);
            } catch (Exception e) {
                audioStatus.setText(T("rec_play_failed", "Play failed"));
            }
        });
        stopPlayBtn.setOnClickListener(v -> {
            try {
                if (voicePlayer != null) voicePlayer.pause();
            } catch (Exception ignored) {
            }
            try {
                if (voicePlayer != null) voicePlayer.seekTo(0);
            } catch (Exception ignored) {
            }
            playSeek.setProgress(0);
            playTimeText.setText("00:00 / " + RecCommon.fmtDur(playDurationMs));
            if (playWaveView != null) playWaveView.setProgress(0);
            playBtn.setText(T("rec_play", "Play"));
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= files.size()) return;
            playRecordingFile(files.get(position));
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= files.size()) return true;
            audioFileMenu(files.get(position));
            return true;
        });
        ToolViewFactory.addLabel(box, T("rec_tap_hint",
                "Tap a recording to play it, long-press for rename, share, open or delete."));
        return box;
    }

    private void startAudio() {
        final Context context = host;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            try {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.RECORD_AUDIO}, 9002);
            } catch (Exception ignored) {
            }
            ToolViewFactory.toast(context,
                    T("rec_mic_needed", "Microphone permission needed, then tap Record"));
            return;
        }
        try {
            if (voicePlayer != null) {
                try {
                    voicePlayer.stop();
                } catch (Exception ignored) {
                }
            }
            int format = RecCommon.optInt(context, "a_format", 0);
            int rate = sampleRates[RecCommon.optInt(context, "a_rate", 3) % sampleRates.length];
            int bitrate = bitRates[RecCommon.optInt(context, "a_bitrate", 2) % bitRates.length];
            int channels = RecCommon.optInt(context, "a_channels", 1) == 0 ? 1 : 2;
            int source = audioSources[RecCommon.optInt(context, "a_source", 0) % audioSources.length];
            int maxMin = maxMinutesRaw();
            File dir = RecCommon.recordingsDir(context);
            String ext = format == 0 ? ".m4a" : ".3gp";
            recOutFile = new File(dir, "rec_" + RecCommon.stamp() + ext);
            voiceRecorder = new MediaRecorder();
            voiceRecorder.setAudioSource(source);
            if (format == 0) {
                voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                voiceRecorder.setAudioSamplingRate(rate);
                voiceRecorder.setAudioEncodingBitRate(bitrate);
                voiceRecorder.setAudioChannels(channels);
            } else {
                voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            }
            voiceRecorder.setOutputFile(recOutFile.getAbsolutePath());
            if (maxMin > 0) {
                voiceRecorder.setMaxDuration(maxMin * 60000);
                voiceRecorder.setOnInfoListener((mr, what, extra) -> {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        handler.post(this::stopAudio);
                    }
                });
            }
            voiceRecorder.prepare();
            voiceRecorder.start();
            recordingNow = true;
            recordingPaused = false;
            recPausedTotal = 0L;
            recStartElapsed = SystemClock.elapsedRealtime();
            recAmps = new ArrayList<>();
            if (recWaveView != null) recWaveView.reset();
            recBtn.setText(T("rec_stop", "Stop"));
            pauseBtn.setEnabled(true);
            pauseBtn.setText(T("rec_pause", "Pause"));
            audioStatus.setText(T("rec_recording", "Recording") + " " + recOutFile.getName());
            if (recTick == null) {
                recTick = new Runnable() {
                    public void run() {
                        if (!recordingNow || voiceRecorder == null) return;
                        long elapsed = SystemClock.elapsedRealtime() - recStartElapsed - recPausedTotal
                                - (recordingPaused
                                ? (SystemClock.elapsedRealtime() - recPauseStarted) : 0);
                        if (recTimerText != null) recTimerText.setText(RecCommon.fmtDur(elapsed));
                        if (!recordingPaused) {
                            try {
                                float norm = Math.min(1f, voiceRecorder.getMaxAmplitude() / 14000f);
                                if (recWaveView != null) recWaveView.push(norm);
                                recAmps.add(norm);
                            } catch (Exception ignored) {
                            }
                        }
                        handler.postDelayed(this, 200);
                    }
                };
            }
            handler.post(recTick);
        } catch (Exception e) {
            audioStatus.setText(T("rec_record_failed", "Record failed"));
            recordingNow = false;
            recBtn.setText(T("rec_record", "Record"));
            pauseBtn.setEnabled(false);
        }
    }

    private void stopAudio() {
        final Context context = host;
        try {
            handler.removeCallbacks(recTick);
        } catch (Exception ignored) {
        }
        try {
            if (recordingPaused && Build.VERSION.SDK_INT >= 24) {
                try {
                    voiceRecorder.resume();
                } catch (Exception ignored) {
                }
            }
            voiceRecorder.stop();
        } catch (Exception ignored) {
        }
        try {
            voiceRecorder.release();
        } catch (Exception ignored) {
        }
        voiceRecorder = null;
        recordingNow = false;
        recordingPaused = false;
        recBtn.setText(T("rec_record", "Record"));
        pauseBtn.setEnabled(false);
        pauseBtn.setText(T("rec_pause", "Pause"));
        if (recTimerText != null) recTimerText.setText("00:00");
        if (recOutFile != null && recOutFile.exists()) {
            RecCommon.saveAmps(recOutFile, recAmps);
            audioStatus.setText(T("rec_saved", "Saved") + " " + recOutFile.getName());
            recCurrentFile = recOutFile;
            nowPlayingView.setText(recOutFile.getName());
            playAmps = new ArrayList<>(recAmps);
            if (playWaveView != null) {
                playWaveView.setAmps(playAmps);
                playWaveView.setProgress(0);
            }
            playDurationMs = (int) RecCommon.mediaDuration(recOutFile);
            playSeek.setMax(playDurationMs);
            playSeek.setProgress(0);
            playTimeText.setText("00:00 / " + RecCommon.fmtDur(playDurationMs));
            recOutFile = null;
        } else {
            audioStatus.setText(T("rec_saved", "Saved"));
        }
        if (refreshAudioList != null) refreshAudioList.run();
    }

    private void playRecordingFile(File f) {
        final Context context = host;
        try {
            try {
                handler.removeCallbacks(playTick);
            } catch (Exception ignored) {
            }
            if (voicePlayer != null) {
                try {
                    voicePlayer.release();
                } catch (Exception ignored) {
                }
            }
            voicePlayer = new MediaPlayer();
            voicePlayer.setDataSource(f.getAbsolutePath());
            voicePlayer.prepare();
            if (Build.VERSION.SDK_INT >= 23) {
                try {
                    voicePlayer.setPlaybackParams(voicePlayer.getPlaybackParams().setSpeed(1f));
                } catch (Exception ignored) {
                }
            }
            voicePlayer.start();
            recCurrentFile = f;
            playAmps = RecCommon.loadAmps(f);
            if (playWaveView != null) playWaveView.setAmps(playAmps);
            playDurationMs = voicePlayer.getDuration();
            if (playSeek != null) {
                playSeek.setMax(Math.max(1, playDurationMs));
                playSeek.setProgress(0);
            }
            if (playTimeText != null) {
                playTimeText.setText("00:00 / " + RecCommon.fmtDur(playDurationMs));
            }
            audioStatus.setText(T("rec_playing", "Playing") + " " + f.getName());
            if (nowPlayingView != null) {
                nowPlayingView.setText(f.getName() + "  " + RecCommon.fmtDur(playDurationMs)
                        + "  " + RecCommon.formatBytes(f.length()));
            }
            if (playBtn != null) playBtn.setText(T("rec_pause", "Pause"));
            voicePlayer.setOnCompletionListener(mp -> {
                audioStatus.setText(T("rec_ready", "Ready"));
                if (playBtn != null) playBtn.setText(T("rec_play", "Play"));
                if (playSeek != null) playSeek.setProgress(0);
                if (playTimeText != null) {
                    playTimeText.setText("00:00 / " + RecCommon.fmtDur(playDurationMs));
                }
                if (playWaveView != null) playWaveView.setProgress(0);
            });
            if (playTick == null) {
                playTick = new Runnable() {
                    public void run() {
                        try {
                            if (voicePlayer != null && voicePlayer.isPlaying()
                                    && !playSeeking && playSeek != null) {
                                int pos = voicePlayer.getCurrentPosition();
                                playSeek.setProgress(pos);
                                if (playTimeText != null) {
                                    playTimeText.setText(RecCommon.fmtDur(pos) + " / "
                                            + RecCommon.fmtDur(playDurationMs));
                                }
                                if (playWaveView != null) {
                                    playWaveView.setProgress(playDurationMs == 0 ? 0
                                            : pos / (float) playDurationMs);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        handler.postDelayed(this, 250);
                    }
                };
            }
            handler.post(playTick);
        } catch (Exception e) {
            audioStatus.setText(T("rec_play_failed", "Play failed"));
        }
    }

    private void audioFileMenu(final File f) {
        final Context context = host;
        String[] opts = new String[]{
                T("rec_rename", "Rename"), T("rec_share", "Share"),
                T("rec_open", "Open"), T("rec_delete", "Delete")};
        new MaterialAlertDialogBuilder(context).setTitle(f.getName()).setItems(opts, (d, which) -> {
            if (which == 0) {
                final EditText nameInput = new EditText(context);
                String n = f.getName();
                int dot = n.lastIndexOf('.');
                nameInput.setText(dot > 0 ? n.substring(0, dot) : n);
                new MaterialAlertDialogBuilder(context)
                        .setTitle(T("rec_rename", "Rename")).setView(nameInput)
                        .setPositiveButton(T("rec_save", "Save"), (dd, w) -> {
                            try {
                                String base = nameInput.getText().toString().trim()
                                        .replaceAll("[^a-zA-Z0-9 _-]+", "");
                                if (base.isEmpty()) return;
                                String ext = "";
                                int dot2 = f.getName().lastIndexOf('.');
                                if (dot2 > 0) ext = f.getName().substring(dot2);
                                File t = new File(f.getParent(), base + ext);
                                File aOld = new File(f.getAbsolutePath() + ".amp");
                                if (f.renameTo(t)) {
                                    if (aOld.exists()) {
                                        aOld.renameTo(new File(t.getAbsolutePath() + ".amp"));
                                    }
                                    if (recCurrentFile == f) recCurrentFile = t;
                                    if (refreshAudioList != null) refreshAudioList.run();
                                }
                            } catch (Exception ignored) {
                            }
                        })
                        .setNegativeButton(T("rec_cancel", "Cancel"), null).show();
            } else if (which == 1) {
                RecCommon.shareFile(context, f, "audio/*");
            } else if (which == 2) {
                RecCommon.openFile(context, f, "audio/*");
            } else {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
                try {
                    new File(f.getAbsolutePath() + ".amp").delete();
                } catch (Exception ignored) {
                }
                if (recCurrentFile == f) recCurrentFile = null;
                if (refreshAudioList != null) refreshAudioList.run();
            }
        }).show();
    }

    @Override
    public boolean fillViewport() {
        return true;
    }

    @Override
    public void onDestroy() {
        try {
            if (shell != null) shell.destroy();
        } catch (Exception ignored) {
        }
        shell = null;
        try {
            handler.removeCallbacks(recTick);
        } catch (Exception ignored) {
        }
        try {
            handler.removeCallbacks(playTick);
        } catch (Exception ignored) {
        }
        handler.removeCallbacksAndMessages(null);
        try {
            if (recordingNow && voiceRecorder != null) voiceRecorder.stop();
        } catch (Exception ignored) {
        }
        try {
            if (voiceRecorder != null) voiceRecorder.release();
        } catch (Exception ignored) {
        }
        voiceRecorder = null;
        recordingNow = false;
        try {
            if (voicePlayer != null) voicePlayer.release();
        } catch (Exception ignored) {
        }
        voicePlayer = null;
        try {
            if (video != null) video.onDestroy();
        } catch (Exception ignored) {
        }
        video = null;
        recTimerText = null;
        recWaveView = null;
        playWaveView = null;
        playTimeText = null;
        playSeek = null;
        audioPage = null;
        videoPage = null;
    }

    private static class RecWaveView extends View {
        private List<Float> amps = new ArrayList<>();
        private float progress = -1f;
        private final Paint played =
                new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rest =
                new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        RecWaveView(Context ctx) {
            super(ctx);
            played.setColor(0xFF1B73E8);
            rest.setColor(0xFF9E9E9E);
            line.setColor(0x33000000);
            played.setStrokeWidth(4f);
            rest.setStrokeWidth(4f);
        }

        void setAmps(List<Float> a) {
            amps = a == null ? new ArrayList<>() : a;
            invalidate();
        }

        void push(float v) {
            amps.add(Math.max(0f, Math.min(1f, v)));
            if (amps.size() > 400) amps.remove(0);
            invalidate();
        }

        void setProgress(float p) {
            progress = p;
            invalidate();
        }

        void reset() {
            amps = new ArrayList<>();
            progress = -1f;
            invalidate();
        }

        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            canvas.drawRect(0, h / 2f - 1, w, h / 2f + 1, line);
            if (amps.isEmpty()) return;
            int n = Math.min(amps.size(), Math.max(1, w / 6));
            int start = amps.size() - n;
            float playedUntil = progress < 0 ? n : Math.round(progress * n);
            for (int i = 0; i < n; i++) {
                float v = amps.get(start + i);
                float bh = Math.max(4, v * (h - 8));
                float x = i * 6f + 2;
                float top = h / 2f - bh / 2f;
                canvas.drawLine(x, top, x, top + bh, i < playedUntil ? played : rest);
            }
        }
    }
}

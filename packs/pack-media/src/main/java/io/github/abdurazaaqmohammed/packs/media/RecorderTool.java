package io.github.abdurazaaqmohammed.packs.media;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildRecorder().
 *
 * <p>Permission auto-retry is not possible from a pack (the host owns the
 * permission callback), so recording asks again with a retry toast instead.
 * "Locate in file manager" became dependency-free "Open file".
 */
public class RecorderTool extends BaseToolPlugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
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

    public RecorderTool() {
        super("recorder", R.string.recorder_title, R.string.recorder_sub,  ToolCategories.MEDIA);
    }

    private File recordingsDir(Context context) {
        try {
            File d = new File(Environment.getExternalStorageDirectory(), "Recordings");
            d.mkdirs();
            if (d.isDirectory()) return d;
        } catch (Exception ignored) {
        }
        File c = new File(context.getCacheDir(), "recordings");
        try {
            c.mkdirs();
        } catch (Exception ignored) {
        }
        return c;
    }

    private static String fmtDur(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private static long audioDuration(File f) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(f.getAbsolutePath());
            return Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (Exception e) {
            return 0;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static void saveAmps(File audio, List<Float> amps) {
        try {
            FileOutputStream os = new FileOutputStream(audio.getAbsolutePath() + ".amp");
            for (Float v : amps) os.write(Math.max(0, Math.min(255, Math.round(v * 255))));
            os.close();
        } catch (Exception ignored) {
        }
    }

    private static List<Float> loadAmps(File audio) {
        List<Float> out = new ArrayList<>();
        try {
            File f = new File(audio.getAbsolutePath() + ".amp");
            if (!f.exists()) return out;
            FileInputStream in = new FileInputStream(f);
            int b;
            while ((b = in.read()) >= 0) out.add(b / 255f);
            in.close();
        } catch (Exception ignored) {
        }
        return out;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return new java.text.DecimalFormat("0.0").format(kb) + " KB";
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return new java.text.DecimalFormat("0.0").format(mb) + " MB";
        }
        return new java.text.DecimalFormat("0.00").format(mb / 1024.0) + " GB";
    }

    private void shareFile(Context context, File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType(mime);
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(s, context.getString(R.string.recorder_share)));
        } catch (Exception e) {
            ToolViewFactory.toast(context, context.getString(R.string.recorder_share_failed));
        }
    }

    private void openFile(Context context, File f, String mime) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", f);
            Intent v = new Intent(Intent.ACTION_VIEW);
            v.setDataAndType(uri, mime);
            v.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(v, context.getString(R.string.recorder_open_with)));
        } catch (Exception e) {
            ToolViewFactory.toast(context, context.getString(R.string.recorder_no_app_found));
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.recorder_voice_recorder));
        final TextView status = ToolViewFactory.makeOutput(box);
        status.setText(status.getContext().getString(R.string.recorder_ready));
        recTimerText = new TextView(context);
        recTimerText.setText("00:00");
        recTimerText.setTextSize(40);
        recTimerText.setTypeface(Typeface.MONOSPACE);
        recTimerText.setGravity(Gravity.CENTER);
        box.addView(recTimerText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        recWaveView = new RecWaveView(context);
        recWaveView.setMinimumHeight(ToolViewFactory.dp(context, 90));
        box.addView(recWaveView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 90)));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.recorder_quality));
        final Spinner fmtSpinner = new Spinner(context);
        final String[] fmtNames = new String[]{context.getString(R.string.recorder_high_quality_aac), context.getString(R.string.recorder_small_size_amr)};
        ArrayAdapter<String> fmtAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, fmtNames);
        fmtAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fmtSpinner.setAdapter(fmtAdapter);
        box.addView(fmtSpinner);
        LinearLayout recRow = ToolViewFactory.makeRow(box);
        final MaterialButton recBtn = ToolViewFactory.makeRowButton(recRow, recRow.getContext().getString(R.string.recorder_record), 1f);
        final MaterialButton pauseBtn = ToolViewFactory.makeRowButton(recRow, recRow.getContext().getString(R.string.recorder_pause), 1f);
        pauseBtn.setEnabled(false);
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.recorder_now_playing));
        final TextView nowPlaying = ToolViewFactory.makeOutput(box);
        nowPlaying.setText(nowPlaying.getContext().getString(R.string.recorder_nothing_loaded));
        playWaveView = new RecWaveView(context);
        playWaveView.setMinimumHeight(ToolViewFactory.dp(context, 90));
        box.addView(playWaveView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 90)));
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
                    playTimeText.setText(fmtDur(progress) + " / " + fmtDur(playDurationMs));
                    if (playWaveView != null) playWaveView.setProgress(playDurationMs == 0 ? 0 : progress / (float) playDurationMs);
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
        final MaterialButton playBtn = ToolViewFactory.makeRowButton(playRow, playRow.getContext().getString(R.string.recorder_play), 1f);
        final MaterialButton stopPlayBtn = ToolViewFactory.makeRowButton(playRow, playRow.getContext().getString(R.string.metronome_stop), 1f);
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
        final List<File> files = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, names);
        listView.setAdapter(adapter);
        box.addView(listView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 220)));
        final Runnable refreshList = () -> {
            files.clear();
            names.clear();
            try {
                File legacy = new File(context.getCacheDir(), "recordings");
                File[] old = legacy.listFiles();
                if (old != null && old.length > 0) {
                    File dest = recordingsDir(context);
                    for (File f : old) {
                        if (f.getName().endsWith(".amp")) continue;
                        try {
                            File t = new File(dest, f.getName());
                            if (!t.exists() && f.renameTo(t)) {
                                File a = new File(f.getAbsolutePath() + ".amp");
                                if (a.exists()) a.renameTo(new File(t.getAbsolutePath() + ".amp"));
                            } else {
                                files.add(f);
                            }
                        } catch (Exception ignored) {
                            files.add(f);
                        }
                    }
                }
                File dir = recordingsDir(context);
                File[] all = dir.listFiles();
                if (all != null) {
                    Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (File f : all) {
                        if (f.isDirectory() || f.getName().endsWith(".amp")) continue;
                        if (!files.contains(f)) files.add(f);
                    }
                }
                for (File f : files) {
                    long d = audioDuration(f);
                    names.add(f.getName() + "  " + (d > 0 ? fmtDur(d) + "  " : "") + formatBytes(f.length()));
                }
            } catch (Exception ignored) {
            }
            adapter.notifyDataSetChanged();
        };
        refreshList.run();
        final Runnable startRecording = new Runnable() {
            public void run() {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    try {
                        ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 9002);
                    } catch (Exception ignored) {
                    }
                    ToolViewFactory.toast(context, context.getString(R.string.recorder_microphone_permission_needed_t));
                    return;
                }
                try {
                    if (voicePlayer != null) {
                        try {
                            voicePlayer.stop();
                        } catch (Exception ignored) {
                        }
                    }
                    File dir = recordingsDir(context);
                    String ext = fmtSpinner.getSelectedItemPosition() == 0 ? ".m4a" : ".3gp";
                    recOutFile = new File(dir, "rec_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ext);
                    voiceRecorder = new MediaRecorder();
                    voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    if (fmtSpinner.getSelectedItemPosition() == 0) {
                        voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                        voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                        voiceRecorder.setAudioSamplingRate(44100);
                        voiceRecorder.setAudioEncodingBitRate(128000);
                    } else {
                        voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
                        voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
                    }
                    voiceRecorder.setOutputFile(recOutFile.getAbsolutePath());
                    voiceRecorder.prepare();
                    voiceRecorder.start();
                    recordingNow = true;
                    recordingPaused = false;
                    recPausedTotal = 0L;
                    recStartElapsed = SystemClock.elapsedRealtime();
                    recAmps = new ArrayList<>();
                    if (recWaveView != null) recWaveView.reset();
                    recBtn.setText(recBtn.getContext().getString(R.string.metronome_stop));
                    pauseBtn.setEnabled(true);
                    pauseBtn.setText(pauseBtn.getContext().getString(R.string.recorder_pause));
                    status.setText(status.getContext().getString(R.string.recorder_recording) + recOutFile.getName());
                    if (recTick == null) {
                        recTick = new Runnable() {
                            public void run() {
                                if (!recordingNow || voiceRecorder == null) return;
                                long elapsed = SystemClock.elapsedRealtime() - recStartElapsed - recPausedTotal - (recordingPaused ? (SystemClock.elapsedRealtime() - recPauseStarted) : 0);
                                if (recTimerText != null) recTimerText.setText(fmtDur(elapsed));
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
                    status.setText(status.getContext().getString(R.string.recorder_record_failed));
                    recordingNow = false;
                    recBtn.setText(recBtn.getContext().getString(R.string.recorder_record));
                    pauseBtn.setEnabled(false);
                }
            }
        };
        recBtn.setOnClickListener(v -> {
            if (recordingNow) {
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
                recBtn.setText(recBtn.getContext().getString(R.string.recorder_record));
                pauseBtn.setEnabled(false);
                pauseBtn.setText(pauseBtn.getContext().getString(R.string.recorder_pause));
                if (recTimerText != null) recTimerText.setText("00:00");
                if (recOutFile != null && recOutFile.exists()) {
                    saveAmps(recOutFile, recAmps);
                    status.setText(status.getContext().getString(R.string.recorder_saved, recOutFile.getName()));
                    recCurrentFile = recOutFile;
                    nowPlaying.setText(recOutFile.getName());
                    playAmps = new ArrayList<>(recAmps);
                    if (playWaveView != null) {
                        playWaveView.setAmps(playAmps);
                        playWaveView.setProgress(0);
                    }
                    playDurationMs = (int) audioDuration(recOutFile);
                    playSeek.setMax(playDurationMs);
                    playSeek.setProgress(0);
                    playTimeText.setText("00:00 / " + fmtDur(playDurationMs));
                    recOutFile = null;
                } else {
                    status.setText(status.getContext().getString(R.string.recorder_saved_2));
                }
                refreshList.run();
                return;
            }
            startRecording.run();
        });
        pauseBtn.setOnClickListener(v -> {
            if (!recordingNow || voiceRecorder == null) return;
            if (Build.VERSION.SDK_INT < 24) {
                ToolViewFactory.toast(context, context.getString(R.string.recorder_pause_needs_android_7));
                return;
            }
            try {
                if (!recordingPaused) {
                    voiceRecorder.pause();
                    recordingPaused = true;
                    recPauseStarted = SystemClock.elapsedRealtime();
                    pauseBtn.setText(pauseBtn.getContext().getString(R.string.recorder_resume));
                    status.setText(status.getContext().getString(R.string.recorder_paused));
                } else {
                    voiceRecorder.resume();
                    recordingPaused = false;
                    recPausedTotal += SystemClock.elapsedRealtime() - recPauseStarted;
                    pauseBtn.setText(pauseBtn.getContext().getString(R.string.recorder_pause));
                    status.setText(status.getContext().getString(R.string.recorder_recording_2));
                }
            } catch (Exception e) {
                ToolViewFactory.toast(context, context.getString(R.string.recorder_pause_failed));
            }
        });
        playBtn.setOnClickListener(v -> {
            try {
                if (voicePlayer != null && voicePlayer.isPlaying()) {
                    voicePlayer.pause();
                    playBtn.setText(playBtn.getContext().getString(R.string.recorder_play));
                    return;
                }
                if (voicePlayer != null && playDurationMs > 0) {
                    try {
                        if (Build.VERSION.SDK_INT >= 23) voicePlayer.setPlaybackParams(voicePlayer.getPlaybackParams().setSpeed(speeds[speedIdx[0]]));
                    } catch (Exception ignored) {
                    }
                    voicePlayer.start();
                    playBtn.setText(playBtn.getContext().getString(R.string.recorder_pause));
                    return;
                }
                File f = recCurrentFile != null ? recCurrentFile : (files.isEmpty() ? null : files.get(0));
                if (f == null || !f.exists()) {
                    ToolViewFactory.toast(context, context.getString(R.string.recorder_no_recordings_yet));
                    return;
                }
                playRecordingFile(context, f, status, nowPlaying, playBtn);
            } catch (Exception e) {
                status.setText(status.getContext().getString(R.string.recorder_play_failed));
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
            playTimeText.setText("00:00 / " + fmtDur(playDurationMs));
            if (playWaveView != null) playWaveView.setProgress(0);
            playBtn.setText(playBtn.getContext().getString(R.string.recorder_play));
        });
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= files.size()) return;
            playRecordingFile(context, files.get(position), status, nowPlaying, playBtn);
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= files.size()) return true;
            final File f = files.get(position);
            String[] opts = new String[]{context.getString(R.string.recorder_rename), context.getString(R.string.recorder_share), context.getString(R.string.recorder_open), context.getString(R.string.recorder_delete)};
            new MaterialAlertDialogBuilder(context).setTitle(f.getName()).setItems(opts, (d, which) -> {
                if (which == 0) {
                    final EditText nameInput = new EditText(context);
                    String n = f.getName();
                    int dot = n.lastIndexOf('.');
                    nameInput.setText(dot > 0 ? n.substring(0, dot) : n);
                    new MaterialAlertDialogBuilder(context).setTitle("Rename").setView(nameInput).setPositiveButton("Save", (dd, w) -> {
                        try {
                            String base = nameInput.getText().toString().trim().replaceAll("[^a-zA-Z0-9 _-]+", "");
                            if (base.isEmpty()) return;
                            String ext = "";
                            int dot2 = f.getName().lastIndexOf('.');
                            if (dot2 > 0) ext = f.getName().substring(dot2);
                            File t = new File(f.getParent(), base + ext);
                            File aOld = new File(f.getAbsolutePath() + ".amp");
                            if (f.renameTo(t)) {
                                if (aOld.exists()) aOld.renameTo(new File(t.getAbsolutePath() + ".amp"));
                                if (recCurrentFile == f) recCurrentFile = t;
                                refreshList.run();
                            }
                        } catch (Exception ignored) {
                        }
                    }).setNegativeButton("Cancel", null).show();
                } else if (which == 1) {
                    shareFile(context, f, "audio/*");
                } else if (which == 2) {
                    openFile(context, f, "audio/*");
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
                    refreshList.run();
                }
            }).show();
            return true;
        });
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.recorder_tap_a_recording_to_play_it_lon));
        return box;
    }

    private void playRecordingFile(Context context, File f, final TextView status, final TextView nowPlaying, final MaterialButton playBtn) {
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
            playAmps = loadAmps(f);
            if (playWaveView != null) playWaveView.setAmps(playAmps);
            playDurationMs = voicePlayer.getDuration();
            if (playSeek != null) {
                playSeek.setMax(Math.max(1, playDurationMs));
                playSeek.setProgress(0);
            }
            if (playTimeText != null) playTimeText.setText("00:00 / " + fmtDur(playDurationMs));
            status.setText(status.getContext().getString(R.string.recorder_playing) + f.getName());
            if (nowPlaying != null) nowPlaying.setText(f.getName() + "  " + fmtDur(playDurationMs) + "  " + formatBytes(f.length()));
            if (playBtn != null) playBtn.setText(playBtn.getContext().getString(R.string.recorder_pause));
            voicePlayer.setOnCompletionListener(mp -> {
                status.setText(status.getContext().getString(R.string.recorder_ready));
                if (playBtn != null) playBtn.setText(playBtn.getContext().getString(R.string.recorder_play));
                if (playSeek != null) playSeek.setProgress(0);
                if (playTimeText != null) playTimeText.setText("00:00 / " + fmtDur(playDurationMs));
                if (playWaveView != null) playWaveView.setProgress(0);
            });
            if (playTick == null) {
                playTick = new Runnable() {
                    public void run() {
                        try {
                            if (voicePlayer != null && voicePlayer.isPlaying() && !playSeeking && playSeek != null) {
                                int pos = voicePlayer.getCurrentPosition();
                                playSeek.setProgress(pos);
                                if (playTimeText != null) playTimeText.setText(fmtDur(pos) + " / " + fmtDur(playDurationMs));
                                if (playWaveView != null) playWaveView.setProgress(playDurationMs == 0 ? 0 : pos / (float) playDurationMs);
                            }
                        } catch (Exception ignored) {
                        }
                        handler.postDelayed(this, 250);
                    }
                };
            }
            handler.post(playTick);
        } catch (Exception e) {
            status.setText(status.getContext().getString(R.string.recorder_play_failed));
        }
    }

    @Override
    public void onDestroy() {
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
        recTimerText = null;
        recWaveView = null;
        playWaveView = null;
        playTimeText = null;
        playSeek = null;
    }

    private static class RecWaveView extends View {
        private List<Float> amps = new ArrayList<>();
        private float progress = -1f;
        private final android.graphics.Paint played = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint rest = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint line = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
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

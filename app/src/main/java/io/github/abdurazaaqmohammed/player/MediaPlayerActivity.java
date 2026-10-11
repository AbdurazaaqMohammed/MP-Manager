package io.github.abdurazaaqmohammed.player;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import androidx.media3.ui.PlayerView;
import androidx.media3.exoplayer.ExoPlayer;

public class MediaPlayerActivity extends BaseActivity implements
        PlayerManager.PlaybackCallback,
        PlayerManager.VideoSizeChangedListener,
        SurfaceHolder.Callback {

    private PlayerManager playerManager;
    private ExoPlayerManager exoManager;
    private PlayerView exoPlayerView;
    private boolean useExoPlayer;
    private SurfaceView surfaceView;
    private FrameLayout videoContainer;
    private LinearLayout artworkContainer, abRepeatBar, controlsContainer;
    private View toolbar;
    private ImageView artworkView;
    private TextView trackTitle, trackArtist, trackAlbum, currentTime, totalTime, speedLabel, errorMessage, bufferingIndicator;
    private ImageButton btnPlayPause, btnNext, btnPrevious, btnRewind, btnFastForward, btnRepeat, btnShuffle, btnQueue, btnLock, btnSettings, btnAspect, btnUnlock;
    private Button btnSetA, btnSetB;
    private ImageButton btnClearAB;
    private SeekBar seekBar, speedSeekBar;
    private RecyclerView queueList;
    private PlayerQueueAdapter queueAdapter;
    private boolean controlsLocked;
    private boolean controlsVisible = true;
    private boolean userIsSeeking;
    private boolean pausedForBackground;
    private int videoW;
    private int videoH;
    private int aspectMode;
    private int fitRetryCount;
    private SharedPreferences prefs;
    private GestureDetector gestureDetector;
    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::hideControls;
    private final Runnable autoHideUnlockRunnable = this::hideUnlock;

    // ===== GESTURE BRIGHTNESS + VOLUME =====
    private float gestureStartY;
    private int gestureStartBrightness;
    private int gestureStartVolume;
    private boolean gestureLeftSide;
    private boolean gestureInProgress;
    private TextView gestureIndicator;
    private final Handler gestureHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable gestureHideRunnable = () -> {
        if (gestureIndicator != null) gestureIndicator.setVisibility(View.GONE);
    };

    // ===== ROTASI =====
    private int orientationMode = 0;

    // ===== SUBTITLE =====
    private SubtitleView subtitleView;
    private SubtitleTrack currentSubtitle;
    private final List<SubtitleTrack> availableSubtitles = new ArrayList<>();
    private long lastSubtitleUpdate;
    private static final int REQ_PICK_SUBTITLE = 4242;

    // === FIX: handler buat UI delayed update ===
    private final Handler handler = new Handler(Looper.getMainLooper());

    // === UI TIMER untuk sync ExoPlayer → tombol ===
    private final Runnable uiTimer = new Runnable() {
        @Override
        public void run() {
            try {
                if (useExoPlayer && exoManager != null) {
                    updatePlayPauseIcon();
                    long exoPos = exoManager.getCurrentPosition();
                    long exoDur = exoManager.getDuration();
                    if (currentTime != null) currentTime.setText(formatTime(exoPos));
                    if (totalTime != null) totalTime.setText(formatTime(exoDur));
                    if (!userIsSeeking && exoDur > 0 && seekBar != null) {
                        seekBar.setProgress((int) (exoPos * 1000 / exoDur));
                    }
                    updateRepeatIcon();

                    // === FIX: A-B LOOP untuk ExoPlayer ===
                    if (playerManager.hasABRepeat()) {
                        int aPos = playerManager.getABRepeatA();
                        int bPos = playerManager.getABRepeatB();
                        if (bPos > aPos + 1000 && exoPos >= bPos) {
                            exoManager.seekTo(aPos);
                        }
                    }
                }
            } catch (Exception ignored) {}
            autoHideHandler.postDelayed(this, 250);
        }
    };

    private void startUITimer() {
        autoHideHandler.removeCallbacks(uiTimer);
        autoHideHandler.post(uiTimer);
    }

    private void stopUITimer() {
        autoHideHandler.removeCallbacks(uiTimer);
    }

    public static void open(Activity activity) {
        Intent intent = new Intent(activity, MediaPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    public static void openAndPlay(Activity activity, String filePath) {
        Intent intent = new Intent(activity, MediaPlayerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("play_path", filePath);
        activity.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);

        playerManager = PlayerManager.getInstance(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        playerManager.registerCallback(this);
        playerManager.setVideoSizeChangedListener(this);

        initViews();
        setupControls();
        setupSeekBar();
        setupVideoSurface();
        setupQueueList();
        setupBrightnessVolumeGesture();

        String playPath = getIntent().getStringExtra("play_path");
        if (playPath != null) {
            MediaItem item = PlayerManager.buildMediaItem(this, playPath);
            playerManager.play(item);
        } else if (playerManager.getState() == PlayerManager.PlayState.IDLE && playerManager.getQueue().isEmpty()) {
            finish();
        }

        updateUIForCurrentItem();
        updateControlsState();
        updatePlayPauseIcon();
    }

    private void initViews() {
        surfaceView = findViewById(R.id.surfaceView);
        videoContainer = findViewById(R.id.videoContainer);
        artworkContainer = findViewById(R.id.artworkContainer);
        abRepeatBar = findViewById(R.id.abRepeatBar);
        controlsContainer = findViewById(R.id.controlsContainer);
        artworkView = findViewById(R.id.artworkView);
        trackTitle = findViewById(R.id.trackTitle);
        trackArtist = findViewById(R.id.trackArtist);
        trackAlbum = findViewById(R.id.trackAlbum);
        currentTime = findViewById(R.id.currentTime);
        totalTime = findViewById(R.id.totalTime);
        speedLabel = findViewById(R.id.speedLabel);
        errorMessage = findViewById(R.id.errorMessage);
        bufferingIndicator = findViewById(R.id.bufferingIndicator);
        toolbar = findViewById(R.id.toolbar);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnNext = findViewById(R.id.btnNext);
        btnPrevious = findViewById(R.id.btnPrevious);
        btnRewind = findViewById(R.id.btnRewind);
        btnFastForward = findViewById(R.id.btnFastForward);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnShuffle = findViewById(R.id.btnShuffle);
        btnQueue = findViewById(R.id.btnQueue);
        btnLock = findViewById(R.id.btnLock);
        btnSettings = findViewById(R.id.btnSettings);
        btnAspect = findViewById(R.id.btnAspect);
        btnUnlock = findViewById(R.id.btnUnlock);
        btnSetA = findViewById(R.id.btnSetA);
        btnSetB = findViewById(R.id.btnSetB);
        btnClearAB = findViewById(R.id.btnClearAB);

        try {
            subtitleView = findViewById(R.id.subtitleView);
            if (subtitleView != null) applySubtitleStyle();
        } catch (Exception ignored) {}

        // === SOFT SUB: Init ExoPlayer SETELAH subtitleView ===
        exoPlayerView = findViewById(R.id.exoPlayerView);
        exoManager = ExoPlayerManager.getInstance(this);
        exoManager.init(exoPlayerView, subtitleView, new ExoPlayerManager.Listener() {
            @Override public void onStateChanged(boolean isPlaying) {
                runOnUiThread(() -> {
                    updatePlayPauseIcon();
                    if (bufferingIndicator != null) {
                        bufferingIndicator.setVisibility(View.GONE);
                    }
                });
            }
            @Override public void onError(String message) { onError(message); }
        });

        seekBar = findViewById(R.id.seekBar);
        speedSeekBar = findViewById(R.id.speedSeekBar);
        queueList = findViewById(R.id.queueList);
        videoW = playerManager.getLastVideoWidth();
        videoH = playerManager.getLastVideoHeight();
        aspectMode = prefs.getInt("video_aspect_mode", 0);
        videoContainer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            if (r - l != orr - ol || b - t != ob - ot) fitVideo();
        });
        videoContainer.post(this::fitVideo);
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> {
            if (useExoPlayer && exoManager != null) {
                exoManager.playPause();
                // Delay 100ms — tunggu ExoPlayer update state
                handler.postDelayed(this::updatePlayPauseIcon, 100);
            } else {
                playerManager.playPause();
                handler.postDelayed(this::updatePlayPauseIcon, 100);
            }
        });
        btnNext.setOnClickListener(v -> {
            if (useExoPlayer && exoManager != null) exoManager.seekToNext();
            else playerManager.next();
            showControlsTemporarily();
        });
        btnPrevious.setOnClickListener(v -> {
            if (useExoPlayer && exoManager != null) exoManager.seekToPrevious();
            else playerManager.previous();
            showControlsTemporarily();
        });
        btnRewind.setOnClickListener(v -> {
            if (useExoPlayer && exoManager != null) exoManager.seekTo(exoManager.getCurrentPosition() - 10000);
            else playerManager.skipBackward();
            showControlsTemporarily();
        });
        btnFastForward.setOnClickListener(v -> {
            if (useExoPlayer && exoManager != null) exoManager.seekTo(exoManager.getCurrentPosition() + 10000);
            else playerManager.skipForward();
            showControlsTemporarily();
        });

        btnRepeat.setOnClickListener(v -> {
            // === FIX: pakai ExoPlayer kalau aktif ===
            if (useExoPlayer && exoManager != null) {
                int next = (exoManager.getRepeatMode() + 1) % 3;
                exoManager.setRepeatMode(next);  // 0=OFF, 1=ONE, 2=ALL
            } else {
                PlayerManager.RepeatMode[] modes = PlayerManager.RepeatMode.values();
                int next = (playerManager.getRepeatMode().ordinal() + 1) % modes.length;
                playerManager.setRepeatMode(modes[next]);
            }
            updateRepeatIcon();
        });

        btnShuffle.setOnClickListener(v -> {
            if (useExoPlayer && exoManager != null) {
                exoManager.setShuffle(!exoManager.isShuffle());
                btnShuffle.setAlpha(exoManager.isShuffle() ? 1.0f : 0.4f);
            } else {
                playerManager.setShuffle(!playerManager.isShuffle());
                btnShuffle.setAlpha(playerManager.isShuffle() ? 1.0f : 0.4f);
            }
        });

        btnLock.setOnClickListener(v -> setLocked(!controlsLocked));
        btnUnlock.setOnClickListener(v -> setLocked(false));
        btnAspect.setOnClickListener(v -> cycleAspectMode());
        btnSettings.setOnClickListener(v -> showPlayerSettings());
        btnQueue.setOnClickListener(v -> toggleQueue());

        btnAspect.setOnLongClickListener(v -> {
            showOrientationMenu();
            return true;
        });

        btnSettings.setOnLongClickListener(v -> {
            showSubtitleMenu();
            return true;
        });

        // === TOMBOL BACKGROUND PLAY ===
        // Toggle background play pas tap tombol khusus
        // Bisa ditambah nanti di menu settings
    }

    private void setupSeekBar() {
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) {
                    int dur = playerManager.getDuration();
                    if (dur > 0) currentTime.setText(formatTime((long) p * dur / 1000));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { userIsSeeking = true; }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                if (useExoPlayer && exoManager != null) {
                    long dur = exoManager.getDuration();
                    if (dur > 0) exoManager.seekTo(sb.getProgress() * dur / 1000);
                } else {
                    int dur = playerManager.getDuration();
                    if (dur > 0) playerManager.seekTo(sb.getProgress() * dur / 1000);
                }
                userIsSeeking = false;
                showControlsTemporarily();
            }
        });

        // ===== TOMBOL A: Set atau Hapus =====
        btnSetA.setOnClickListener(v -> {
            int pos = useExoPlayer && exoManager != null
                    ? (int) exoManager.getCurrentPosition()
                    : playerManager.getCurrentPosition();
            boolean wasSet = playerManager.hasA();
            boolean nowSet = playerManager.toggleA(pos);
            if (nowSet) {
                btnSetA.setText("A:" + formatTime(pos));
                btnSetB.setText("Set B"); // Reset B label
                if (abRepeatBar != null) abRepeatBar.setVisibility(View.VISIBLE);
                Toast.makeText(this, "A set: " + formatTime(pos) + "\nSekarang set B", Toast.LENGTH_SHORT).show();
            } else {
                btnSetA.setText("Set A");
                btnSetB.setText("Set B");
                if (abRepeatBar != null) abRepeatBar.setVisibility(View.GONE);
                Toast.makeText(this, "A dihapus", Toast.LENGTH_SHORT).show();
            }
        });

        // ===== TOMBOL B: Set atau Hapus =====
        btnSetB.setOnClickListener(v -> {
            int pos = useExoPlayer && exoManager != null
                    ? (int) exoManager.getCurrentPosition()
                    : playerManager.getCurrentPosition();
            if (!playerManager.hasA()) {
                Toast.makeText(this, "Set A dulu!", Toast.LENGTH_SHORT).show();
                return;
            }
            boolean wasSet = playerManager.hasB();
            boolean nowSet = playerManager.toggleB(pos);
            if (nowSet) {
                btnSetB.setText("B:" + formatTime(pos));
                Toast.makeText(this, "B set: " + formatTime(pos) + "\nVideo akan loop A-B", Toast.LENGTH_SHORT).show();
            } else if (wasSet) {
                btnSetB.setText("Set B");
                Toast.makeText(this, "B dihapus — video lanjut normal", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "B harus setelah A (" + formatTime(playerManager.getABRepeatA()) + ")", Toast.LENGTH_SHORT).show();
            }
        });

        // ===== TOMBOL CLEAR AB =====
        btnClearAB.setOnClickListener(v -> {
            playerManager.clearABRepeat();
            btnSetA.setText("Set A");
            btnSetB.setText("Set B");
            btnClearAB.setVisibility(View.GONE);
            if (abRepeatBar != null) abRepeatBar.setVisibility(View.GONE);
            Toast.makeText(this, "A-B cleared", Toast.LENGTH_SHORT).show();
        });

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) {
                    float speed = Math.max(0.25f, p / 100f);
                    if (useExoPlayer && exoManager != null) exoManager.setPlaybackSpeed(speed);
                    else playerManager.setPlaybackSpeed(speed);
                    speedLabel.setText(String.format("%.1fx", speed));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        setupGestureControls();
    }

    private void setupGestureControls() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                if (controlsLocked) return false;
                float screenWidth = getWindow().getDecorView().getWidth();
                if (e.getX() < screenWidth / 3f) playerManager.skipBackward();
                else if (e.getX() > screenWidth * 2f / 3f) playerManager.skipForward();
                else playerManager.playPause();
                showControlsTemporarily();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                // === FIX: cek ExoPlayer juga ===
                boolean isPlayingVideo = playerManager.isPlayingVideo()
                        || (useExoPlayer && exoManager != null);
                if (isPlayingVideo) {
                    if (controlsLocked) { showUnlockOnly(); return true; }
                    if (controlsVisible) hideControls();
                    else showControlsTemporarily();
                }
                return true;
            }
        });
    }

    private void setupBrightnessVolumeGesture() {
        videoContainer.setOnTouchListener((v, event) -> {
            if (gestureDetector != null) gestureDetector.onTouchEvent(event);
            if (controlsLocked) return true;

            int action = event.getActionMasked();
            float screenWidth = v.getWidth();

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    gestureStartY = event.getY();
                    gestureLeftSide = event.getX() < screenWidth / 2f;
                    gestureInProgress = false;
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    gestureStartBrightness = (int) (lp.screenBrightness * 100);
                    if (gestureStartBrightness <= 0) gestureStartBrightness = 50;
                    AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                    gestureStartVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float delta = gestureStartY - event.getY();
                    float absDelta = Math.abs(delta);
                    if (!gestureInProgress) {
                        if (absDelta < 30) return true;
                        gestureInProgress = true;
                    }
                    float sensitivity = Math.max(v.getHeight() * 1.5f, 1f);
                    float changePercent = delta / sensitivity * 100f;

                    if (gestureLeftSide) {
                        int newBright = Math.max(1, Math.min(100,
                                gestureStartBrightness + (int) changePercent));
                        WindowManager.LayoutParams lp2 = getWindow().getAttributes();
                        lp2.screenBrightness = newBright / 100f;
                        getWindow().setAttributes(lp2);
                        showGestureIndicator("☀ " + newBright + "%");
                    } else {
                        AudioManager am2 = (AudioManager) getSystemService(AUDIO_SERVICE);
                        int maxVol = am2.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int newVol = Math.max(0, Math.min(maxVol,
                                gestureStartVolume + (int) (changePercent / 100f * maxVol)));
                        am2.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0);
                        int pct = (int) ((float) newVol / maxVol * 100);
                        showGestureIndicator("🔊 " + pct + "%");
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    gestureInProgress = false;
                    gestureHideHandler.removeCallbacks(gestureHideRunnable);
                    gestureHideHandler.postDelayed(gestureHideRunnable, 800);
                    return true;
            }
            return true;
        });
        videoContainer.setClickable(true);
        videoContainer.setFocusable(true);
    }

    private void showGestureIndicator(String text) {
        if (gestureIndicator == null) {
            gestureIndicator = new TextView(this);
            gestureIndicator.setTextColor(0xFFFFFFFF);
            gestureIndicator.setTextSize(22);
            gestureIndicator.setPadding(50, 30, 50, 30);
            gestureIndicator.setBackgroundColor(0xCC000000);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.CENTER;
            View root = getWindow().getDecorView().findViewById(android.R.id.content);
            if (root instanceof FrameLayout) {
                ((FrameLayout) root).addView(gestureIndicator, lp);
            }
        }
        gestureIndicator.setText(text);
        gestureIndicator.setVisibility(View.VISIBLE);
        gestureHideHandler.removeCallbacks(gestureHideRunnable);
        gestureHideHandler.postDelayed(gestureHideRunnable, 800);
    }

    private void showOrientationMenu() {
        String[] items = {"Auto (Sensor)", "Portrait", "Landscape"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Orientation")
                .setSingleChoiceItems(items, orientationMode, (d, which) -> {
                    orientationMode = which;
                    applyOrientation();
                    d.dismiss();
                })
                .show();
    }

    private void applyOrientation() {
        switch (orientationMode) {
            case 0:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
                Toast.makeText(this, "Auto rotate", Toast.LENGTH_SHORT).show();
                break;
            case 1:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                Toast.makeText(this, "Portrait lock", Toast.LENGTH_SHORT).show();
                break;
            case 2:
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                Toast.makeText(this, "Landscape lock", Toast.LENGTH_SHORT).show();
                break;
        }
    }

    // ==================== SUBTITLE ====================

    private void applySubtitleStyle() {
        if (subtitleView == null) return;
        try {
            float size = prefs.getFloat("subtitle_text_size", 42f);
            int color = prefs.getInt("subtitle_text_color", 0xFFFFFFFF);
            int bgColor = prefs.getInt("subtitle_bg_color", 0x99000000);
            subtitleView.setTextSize(size);
            subtitleView.setTextColor(color);
            subtitleView.setBackgroundColor(bgColor);
        } catch (Exception ignored) {}
    }

    private void autoDetectSubtitles(String videoPath) {
        availableSubtitles.clear();
        if (videoPath == null) return;
        try {
            File videoFile = new File(videoPath);
            File dir = videoFile.getParentFile();
            if (dir == null || !dir.isDirectory()) return;

            String videoName = videoFile.getName();
            int dot = videoName.lastIndexOf('.');
            String baseName = (dot > 0 ? videoName.substring(0, dot) : videoName).toLowerCase();

            File[] files = dir.listFiles();
            if (files == null) return;

            for (File f : files) {
                if (!f.isFile()) continue;
                String name = f.getName().toLowerCase();
                if (!name.endsWith(".srt") && !name.endsWith(".vtt")
                        && !name.endsWith(".ass") && !name.endsWith(".ssa")) continue;
                String fBase = name;
                int d = fBase.lastIndexOf('.');
                if (d > 0) fBase = fBase.substring(0, d);
                if (fBase.startsWith(baseName)) {
                    SubtitleTrack t = SubtitleParser.parse(f);
                    if (t != null && !t.cues.isEmpty()) availableSubtitles.add(t);
                }
            }

            if (!availableSubtitles.isEmpty()) {
                setSubtitleTrack(availableSubtitles.get(0));
            }
        } catch (Exception ignored) {}
    }

    private void setSubtitleTrack(SubtitleTrack track) {
        this.currentSubtitle = track;
        if (subtitleView == null) return;
        if (track == null) {
            subtitleView.setSubtitleText("");
        } else {
            try { Toast.makeText(this, "Subtitle: " + track.name, Toast.LENGTH_SHORT).show(); } catch (Exception ignored) {}
        }
    }

    private void updateSubtitle(long positionMs) {
        if (subtitleView == null) return;
        if (currentSubtitle == null) {
            subtitleView.setSubtitleText("");
            return;
        }
        SubtitleTrack.Cue cue = currentSubtitle.getCueAt(positionMs);
        subtitleView.setSubtitleText(cue != null ? cue.text : "");
    }

    private void showSubtitleMenu() {
        // === FIX: handle ExoPlayer ===
        if (useExoPlayer && exoManager != null) {
            showSubtitleMenuExo();
            return;
        }
        // MediaPlayer (legacy)
        MediaItem item = playerManager.getCurrentItem();
        if (item == null || item.path == null) {
            Toast.makeText(this, "No video loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        autoDetectSubtitles(item.path);

        List<String> options = new ArrayList<>();
        options.add("Off");
        options.add("Pick file…");
        for (SubtitleTrack t : availableSubtitles) {
            String label = t.name;
            if (t.language != null) label += " (" + t.language + ")";
            options.add(label);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Subtitle")
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which == 0) setSubtitleTrack(null);
                    else if (which == 1) pickSubtitleFile();
                    else if (which - 2 < availableSubtitles.size()) {
                        setSubtitleTrack(availableSubtitles.get(which - 2));
                    }
                }).show();
    }

    /** Subtitle menu khusus ExoPlayer — handle soft sub embedded + file eksternal */
    private void showSubtitleMenuExo() {
        MediaItem item = playerManager.getCurrentItem();
        if (item == null || item.path == null) {
            Toast.makeText(this, "No video loaded", Toast.LENGTH_SHORT).show();
            return;
        }

        // Cek subtitle embedded di ExoPlayer
        List<String> options = new ArrayList<>();
        options.add("Off");
        options.add("Pick file…");

        // Subtitle embedded dari ExoPlayer track
        androidx.media3.common.Tracks tracks = exoManager.getCurrentTracks();
        List<String> embeddedLabels = new ArrayList<>();
        List<androidx.media3.common.TrackGroup> embeddedGroups = new ArrayList<>();
        if (tracks != null) {
            for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
                if (group.getType() == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                    for (int i = 0; i < group.length; i++) {
                        if (group.isTrackSupported(i)) {
                            androidx.media3.common.Format fmt = group.getTrackFormat(i);
                            String label = fmt.label != null ? fmt.label : (fmt.language != null ? fmt.language : "Embedded " + (embeddedLabels.size() + 1));
                            embeddedLabels.add(label);
                            embeddedGroups.add(group.getMediaTrackGroup());
                        }
                    }
                }
            }
        }

        for (String label : embeddedLabels) {
            options.add("📺 " + label);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Subtitle")
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        exoManager.disableSubtitle();
                    } else if (which == 1) {
                        pickSubtitleFile();
                    } else if (which - 2 < embeddedGroups.size()) {
                        exoManager.selectSubtitleTrack(which - 2);
                    }
                }).show();
    }

    private void pickSubtitleFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQ_PICK_SUBTITLE);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_SUBTITLE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            try {
                Uri uri = data.getData();
                File cacheFile = new File(getCacheDir(), "picked_subtitle");
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(cacheFile)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                SubtitleTrack t = SubtitleParser.parse(cacheFile);
                if (t != null) setSubtitleTrack(t);
                else Toast.makeText(this, "Invalid subtitle file", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== END SUBTITLE ====================

    private void showControls() {
        if (controlsLocked) return;
        controlsVisible = true;
        toolbar.setVisibility(View.VISIBLE);
        controlsContainer.setVisibility(View.VISIBLE);
        if (playerManager.isPlayingVideo()) {
            autoHideHandler.removeCallbacks(autoHideRunnable);
            autoHideHandler.postDelayed(autoHideRunnable, 5000);
        }
    }

    private void hideControls() {
        controlsVisible = false;
        toolbar.setVisibility(View.GONE);
        controlsContainer.setVisibility(View.GONE);
        queueList.setVisibility(View.GONE);
        autoHideHandler.removeCallbacks(autoHideRunnable);
        hideUnlock();
    }

    private void showUnlockOnly() {
        controlsVisible = false;
        toolbar.setVisibility(View.GONE);
        controlsContainer.setVisibility(View.GONE);
        queueList.setVisibility(View.GONE);
        btnUnlock.setVisibility(View.VISIBLE);
        autoHideHandler.removeCallbacks(autoHideRunnable);
        autoHideHandler.removeCallbacks(autoHideUnlockRunnable);
        autoHideHandler.postDelayed(autoHideUnlockRunnable, 3000);
    }

    private void hideUnlock() {
        autoHideHandler.removeCallbacks(autoHideUnlockRunnable);
        if (btnUnlock != null) btnUnlock.setVisibility(View.GONE);
    }

    private void setLocked(boolean locked) {
        controlsLocked = locked;
        hideUnlock();
        btnLock.setImageResource(locked ? R.drawable.lock_24px : R.drawable.lock_open_24px);
        btnLock.setColorFilter(locked ? 0xFFFF4444 : 0xFFFFFFFF);
        if (locked) hideControls();
        else showControlsTemporarily();
    }

    private void cycleAspectMode() {
        aspectMode = (aspectMode + 1) % 4;
        prefs.edit().putInt("video_aspect_mode", aspectMode).apply();
        fitVideo();
        Toast.makeText(this, getString(R.string.aspect_mode, aspectName()), Toast.LENGTH_SHORT).show();
        showControlsTemporarily();
    }

    private String aspectName() {
        switch (aspectMode) {
            case 1: return getString(R.string.aspect_fill);
            case 2: return "16:9";
            case 3: return "4:3";
            default: return getString(R.string.aspect_fit);
        }
    }

    private void fitVideo() {
        if (videoW <= 0 || videoH <= 0 || surfaceView == null || videoContainer == null) return;
        if (videoContainer.getVisibility() != View.VISIBLE || !playerManager.isPlayingVideo()) return;
        int cw = videoContainer.getWidth();
        int ch = videoContainer.getHeight();
        if (cw <= 0 || ch <= 0) {
            if (fitRetryCount++ < 20) videoContainer.postDelayed(this::fitVideo, 100);
            return;
        }
        fitRetryCount = 0;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
        if (aspectMode == 1) {
            params.width = FrameLayout.LayoutParams.MATCH_PARENT;
            params.height = FrameLayout.LayoutParams.MATCH_PARENT;
        } else {
            float target = aspectMode == 2 ? 16f / 9f : aspectMode == 3 ? 4f / 3f : (float) videoW / videoH;
            float containerRatio = (float) cw / ch;
            if (target > containerRatio) {
                params.width = FrameLayout.LayoutParams.MATCH_PARENT;
                params.height = (int) (cw / target);
            } else {
                params.height = FrameLayout.LayoutParams.MATCH_PARENT;
                params.width = (int) (ch * target);
            }
        }
        surfaceView.setLayoutParams(params);
    }

    private void showControlsTemporarily() { showControls(); }

    private void toggleQueue() {
        if (queueList.getVisibility() == View.VISIBLE) {
            queueList.setVisibility(View.GONE);
            showControlsTemporarily();
        } else {
            queueList.setVisibility(View.VISIBLE);
            if (!controlsVisible) showControlsTemporarily();
        }
    }

    private void updateRepeatIcon() {
        int mode;
        if (useExoPlayer && exoManager != null) {
            mode = exoManager.getRepeatMode();  // 0=OFF, 1=ONE, 2=ALL
        } else {
            mode = playerManager.getRepeatMode().ordinal();
        }
        switch (mode) {
            case 0:  // OFF
                btnRepeat.setAlpha(0.4f);
                btnRepeat.setImageResource(R.drawable.repeat_24px);
                break;
            case 1:  // ONE
                btnRepeat.setAlpha(1.0f);
                btnRepeat.setImageResource(R.drawable.repeat_one_24px);
                break;
            case 2:  // ALL
                btnRepeat.setAlpha(1.0f);
                btnRepeat.setImageResource(R.drawable.repeat_on_24px);
                break;
        }
    }

    private void setupVideoSurface() {
        surfaceView.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        playerManager.setSurface(holder.getSurface());
        if (playerManager.getState() == PlayerManager.PlayState.ERROR
                || playerManager.getState() == PlayerManager.PlayState.STOPPED) {
            playerManager.play();
        }
        surfaceView.post(this::fitVideo);
    }

    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        fitVideo();
    }
    @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        playerManager.setSurface(null);
    }

    private void setupQueueList() {
        queueList.setLayoutManager(new LinearLayoutManager(this));
        updateQueueAdapter();
        queueList.setVisibility(View.GONE);
    }

    private void updateQueueAdapter() {
        queueAdapter = new PlayerQueueAdapter(playerManager.getQueue(), playerManager.getCurrentIndex(),
                new PlayerQueueAdapter.OnItemClickListener() {
                    @Override public void onItemClick(int index) {
                        playerManager.addAndPlay(index);
                        queueList.setVisibility(View.GONE);
                        showControlsTemporarily();
                    }
                    @Override public void onRemoveClick(int index) {
                        playerManager.removeFromQueue(index);
                        updateQueueAdapter();
                    }
                });
        queueList.setAdapter(queueAdapter);
    }

    @Override
    public void onStateChanged(PlayerManager.PlayState newState) {
        runOnUiThread(() -> {
            updatePlayPauseIcon();
            switch (newState) {
                case PLAYING:
                    errorMessage.setVisibility(View.GONE);
                    bufferingIndicator.setVisibility(View.GONE);
                    showControlsTemporarily();
                    break;
                case PREPARING:
                    bufferingIndicator.setVisibility(View.VISIBLE);
                    break;
                case ERROR:
                    errorMessage.setVisibility(View.VISIBLE);
                    errorMessage.setText(R.string.playback_error);
                    showControls();
                    break;
                case ENDED:
                    break;
                default: break;
            }
            updateQueueAdapter();
        });
    }

    private void updatePlayPauseIcon() {
        boolean playing;
        if (useExoPlayer && exoManager != null) {
            playing = exoManager.isPlaying();
        } else {
            playing = playerManager.getState() == PlayerManager.PlayState.PLAYING;
        }
        btnPlayPause.setImageResource(playing
                ? R.drawable.pause_24px : R.drawable.ic_start);
    }

    @Override
    public void onProgress(int position, int duration) {
        runOnUiThread(() -> {
            // Kalau ExoPlayer aktif, update dari ExoPlayer
            if (useExoPlayer && exoManager != null) {
                long exoPos = exoManager.getCurrentPosition();
                long exoDur = exoManager.getDuration();
                currentTime.setText(formatTime(exoPos));
                totalTime.setText(formatTime(exoDur));
                if (!userIsSeeking && exoDur > 0) {
                    seekBar.setProgress((int) (exoPos * 1000 / exoDur));
                }
                return;
            }
            // MediaPlayer
            currentTime.setText(formatTime(position));
            totalTime.setText(formatTime(duration));
            if (!userIsSeeking && duration > 0) {
                seekBar.setProgress((int) ((long) position * 1000 / duration));
            }
            if (playerManager.hasABRepeat()) {
                int ab = (int) ((long) position * 1000 / Math.max(duration, 1));
                View abSeek = findViewById(R.id.abSeekBar);
                if (abSeek instanceof SeekBar) ((SeekBar) abSeek).setProgress(ab);
            }
            long now = System.currentTimeMillis();
            if (now - lastSubtitleUpdate >= 100) {
                lastSubtitleUpdate = now;
                updateSubtitle(position);
            }
            if (position > 0 && position % 5000 < 300) {
                MediaItem item = playerManager.getCurrentItem();
                if (item != null && item.path != null) {
                    prefs.edit().putInt("resume_pos_" + item.path, position).apply();
                }
            }
        });
    }

    @Override
    public void onMediaItemChanged(MediaItem item) {
        runOnUiThread(() -> {
            updateUIForCurrentItem();
            updateQueueAdapter();
            try { startService(new Intent(this, MusicService.class)); } catch (Exception ignored) {}
            if (item != null && item.path != null && item.isVideo) {
                autoDetectSubtitles(item.path);
                // === EXOPLAYER DEFAULT untuk SEMUA video ===
                switchToExoPlayer(item.path);
            } else {
                setSubtitleTrack(null);
                switchToMediaPlayer();
            }
            // Reset tombol A-B kalau ganti video
            btnSetA.setText("Set A");
            btnSetB.setText("Set B");
            btnClearAB.setVisibility(View.GONE);
            if (abRepeatBar != null) abRepeatBar.setVisibility(View.GONE);
        });
    }

    /** Cek apakah file MKV (yang mungkin punya soft sub) */
    private boolean isMkvWithSoftSub(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();
        // Cuma MKV yang punya soft sub
        return lower.endsWith(".mkv") || lower.endsWith(".webm");
    }

    /** Switch ke ExoPlayer — DEFAULT untuk semua video */
    private void switchToExoPlayer(String videoPath) {
        if (exoManager == null || videoPath == null) return;
        useExoPlayer = true;
        // Sembunyikan SurfaceView, tampilkan ExoPlayerView
        if (surfaceView != null) surfaceView.setVisibility(View.GONE);
        if (exoPlayerView != null) exoPlayerView.setVisibility(View.VISIBLE);
        // === STOP TOTAL MediaPlayer (biar gak ada suara "grek-grek") ===
        try {
            playerManager.stop();
        } catch (Exception ignored) {}
        // Play ExoPlayer
        exoManager.play(videoPath);
    }

    /** Switch balik ke MediaPlayer */
    private void switchToMediaPlayer() {
        useExoPlayer = false;
        if (exoPlayerView != null) exoPlayerView.setVisibility(View.GONE);
        if (surfaceView != null) surfaceView.setVisibility(View.VISIBLE);
        if (exoManager != null) exoManager.pause();
    }

    private void updateUIForCurrentItem() {
        MediaItem item = playerManager.getCurrentItem();
        if (item == null) return;

        boolean isVideo = item.isVideo;
        videoContainer.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        artworkContainer.setVisibility(isVideo ? View.GONE : View.VISIBLE);

        if (isVideo) showControlsTemporarily();
        else showControls();

        if (!isVideo) {
            trackTitle.setText(item.title != null ? item.title : getString(R.string.media_unknown));
            trackArtist.setText(item.artist != null ? item.artist : getString(R.string.music_unknown_artist));
            trackAlbum.setText(item.album != null ? item.album : "");

            if (item.path != null) try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
                mmr.setDataSource(this, item.uri);
                byte[] art = mmr.getEmbeddedPicture();
                if (art != null) artworkView.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.length));
                else artworkView.setImageResource(android.R.drawable.ic_menu_gallery);
                mmr.release();
            } catch (Exception e) {
                artworkView.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }

        updateKeepScreenOn();
        updateRepeatIcon();
        updatePlayPauseIcon();
        btnShuffle.setAlpha(playerManager.isShuffle() ? 1.0f : 0.4f);

        float speed = playerManager.getPlaybackSpeed();
        speedSeekBar.setProgress((int) (speed * 100));
        speedLabel.setText(String.format("%.1fx", speed));
    }

    private void updateControlsState() {
        PlayerManager.PlayState state = playerManager.getState();
        boolean enabled = state != PlayerManager.PlayState.IDLE && state != PlayerManager.PlayState.STOPPED;
        controlsContainer.setEnabled(enabled);
    }

    private void updateKeepScreenOn() {
        if (playerManager.isPlayingVideo() || playerManager.isKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onError(String message) {
        runOnUiThread(() -> {
            errorMessage.setVisibility(View.VISIBLE);
            errorMessage.setText(message);
            showControls();
        });
    }

    @Override
    public void onBufferUpdate(int percent) {
        runOnUiThread(() -> {
            // Kalau ExoPlayer aktif, JANGAN tampilkan buffering MediaPlayer
            if (useExoPlayer) {
                bufferingIndicator.setVisibility(View.GONE);
                return;
            }
            if (percent < 100) {
                bufferingIndicator.setVisibility(View.VISIBLE);
                bufferingIndicator.setText(getString(R.string.buffering, percent));
            } else {
                bufferingIndicator.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onVideoSizeChanged(int width, int height) {
        runOnUiThread(() -> {
            if (width > 0 && height > 0) {
                videoW = width;
                videoH = height;
                fitVideo();
            }
        });
    }

    private void showPlayerSettings() {
        String[] items = {
                "🎬 Background Play: " + (playerManager.isVideoBackgroundPlay() ? "ON" : "OFF"),
                getString(R.string.keep_screen_on, (playerManager.isKeepScreenOn() ? "ON" : "OFF")),
                getString(R.string.skip_duration_X, (playerManager.getSkipDuration() / 1000)),
                "Orientation",
                "Subtitle",
                getString(R.string.close_player)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_settings)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:  // Background Play
                            boolean current = playerManager.isVideoBackgroundPlay();
                            playerManager.setVideoBackgroundPlay(!current);
                            Toast.makeText(this, "Background Play: " + (!current ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
                            break;
                        case 1:  // Keep Screen On
                            playerManager.setKeepScreenOn(!playerManager.isKeepScreenOn());
                            updateKeepScreenOn();
                            break;
                        case 2:
                            int[] durations = {5000, 10000, 15000, 30000};
                            String[] labels = {"5s", "10s", "15s", "30s"};
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.skip_duration)
                                    .setSingleChoiceItems(labels, -1, (d2, w) -> {
                                        if (w >= 0 && w < durations.length) {
                                            playerManager.setSkipDuration(durations[w]);
                                            d2.dismiss();
                                        }
                                    }).show();
                            break;
                        case 3: showOrientationMenu(); break;
                        case 4: showSubtitleMenu(); break;
                        case 5: finish(); break;
                    }
                }).show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String playPath = intent.getStringExtra("play_path");
        if (playPath != null) {
            MediaItem item = PlayerManager.buildMediaItem(this, playPath);
            playerManager.play(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        playerManager.incrementUIVisible();
        playerManager.registerCallback(this);
        updateUIForCurrentItem();
        updateControlsState();
        updatePlayPauseIcon();
        // === FIX: start timer untuk sync UI ===
        startUITimer();
        if (pausedForBackground) {
            playerManager.play();
            pausedForBackground = false;
        }
        try { startService(new Intent(this, MusicService.class)); } catch (Exception ignored) {}
        videoContainer.post(this::fitVideo);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopUITimer();
        playerManager.decrementUIVisible();
        autoHideHandler.removeCallbacks(autoHideRunnable);

        boolean backgroundPlay = playerManager.isVideoBackgroundPlay();

        if (backgroundPlay && useExoPlayer && exoManager != null) {
            // === BACKGROUND PLAY: video tetap jalan ===
            // Set flag di MainActivity biar cache gak kehapus
            MainActivity activity = MainActivity.current();
            if (activity != null) {
                activity.setPreviewingFromArchive(true);
                activity.setBackgroundPlayActive(true);  // ← flag baru
            }
            // Start foreground service biar Android gak kill
            try {
                Intent svc = new Intent(this, MusicService.class);
                svc.setAction("BACKGROUND_PLAY");
                startForegroundService(svc);
            } catch (Exception ignored) {}
            // JANGAN pause player
        } else {
            // Normal: pause player
            try {
                playerManager.pause();
                if (exoManager != null) exoManager.pause();
            } catch (Exception ignored) {}
        }
        try { startService(new Intent(this, MusicService.class)); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        // === STOP SEMUA PLAYER (biar gak ada suara nyangkut) ===
        try {
            playerManager.stop();
        } catch (Exception ignored) {}
        try {
            if (exoManager != null) exoManager.stop();
        } catch (Exception ignored) {}
        playerManager.unregisterCallback(this);
        playerManager.setVideoSizeChangedListener(null);
        autoHideHandler.removeCallbacks(autoHideRunnable);
        gestureHideHandler.removeCallbacks(gestureHideRunnable);
        playerManager.setSurface(null);
        super.onDestroy();
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }
}

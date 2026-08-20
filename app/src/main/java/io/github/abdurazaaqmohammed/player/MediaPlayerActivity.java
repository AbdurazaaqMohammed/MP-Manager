package io.github.abdurazaaqmohammed.player;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.github.abdurazaaqmohammed.MPManager.R;

public class MediaPlayerActivity extends AppCompatActivity implements
        PlayerManager.PlaybackCallback,
        PlayerManager.VideoSizeChangedListener,
        SurfaceHolder.Callback {

    private PlayerManager playerManager;
    private SurfaceView surfaceView;
    private FrameLayout videoContainer;
    private LinearLayout artworkContainer, abRepeatBar, controlsContainer;
    private View toolbar;
    private ImageView artworkView;
    private TextView trackTitle, trackArtist, trackAlbum, currentTime, totalTime, speedLabel, errorMessage, bufferingIndicator;
    private ImageButton btnPlayPause, btnNext, btnPrevious, btnRewind, btnFastForward, btnRepeat, btnShuffle, btnMute, btnQueue, btnLock, btnSettings;
    private Button btnSetA, btnSetB;
    private ImageButton btnClearAB;
    private SeekBar seekBar, speedSeekBar, volumeSeekBar;
    private RecyclerView queueList;
    private PlayerQueueAdapter queueAdapter;
    private boolean controlsLocked;
    private boolean controlsVisible = true;
    private boolean userIsSeeking;
    private boolean pausedForBackground;
    private SharedPreferences prefs;
    private GestureDetector gestureDetector;
    private final Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoHideRunnable = this::hideControls;

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
        int themeId = PreferenceManager.getDefaultSharedPreferences(this).getInt("theme", 0);
        if (themeId != 0) setTheme(themeId);
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
        btnMute = findViewById(R.id.btnMute);
        btnQueue = findViewById(R.id.btnQueue);
        btnLock = findViewById(R.id.btnLock);
        btnSettings = findViewById(R.id.btnSettings);
        btnSetA = findViewById(R.id.btnSetA);
        btnSetB = findViewById(R.id.btnSetB);
        btnClearAB = findViewById(R.id.btnClearAB);

        seekBar = findViewById(R.id.seekBar);
        speedSeekBar = findViewById(R.id.speedSeekBar);
        volumeSeekBar = findViewById(R.id.volumeSeekBar);
        queueList = findViewById(R.id.queueList);
    }

    private void setupControls() {
        btnPlayPause.setOnClickListener(v -> playerManager.playPause());
        btnNext.setOnClickListener(v -> { playerManager.next(); showControlsTemporarily(); });
        btnPrevious.setOnClickListener(v -> { playerManager.previous(); showControlsTemporarily(); });
        btnRewind.setOnClickListener(v -> { playerManager.skipBackward(); showControlsTemporarily(); });
        btnFastForward.setOnClickListener(v -> { playerManager.skipForward(); showControlsTemporarily(); });

        btnRepeat.setOnClickListener(v -> {
            PlayerManager.RepeatMode[] modes = PlayerManager.RepeatMode.values();
            int next = (playerManager.getRepeatMode().ordinal() + 1) % modes.length;
            playerManager.setRepeatMode(modes[next]);
            updateRepeatIcon();
        });

        btnShuffle.setOnClickListener(v -> {
            playerManager.setShuffle(!playerManager.isShuffle());
            btnShuffle.setAlpha(playerManager.isShuffle() ? 1.0f : 0.4f);
        });

        btnMute.setOnClickListener(v -> {
            playerManager.setMuted(!playerManager.isMuted());
            btnMute.setImageResource(playerManager.isMuted()
                    ? R.drawable.volume_off_24px
                    : R.drawable.volume_up_24px);
        });

        btnLock.setOnClickListener(v -> {
            controlsLocked = !controlsLocked;
            btnLock.setImageResource(controlsLocked
                    ? R.drawable.lock_24px
                    : R.drawable.lock_open_24px);
            btnLock.setColorFilter(controlsLocked ? 0xFFFF4444 : 0xFFFFFFFF);
            if (controlsLocked) hideControls();
            else showControlsTemporarily();
        });

        btnSettings.setOnClickListener(v -> showPlayerSettings());

        btnQueue.setOnClickListener(v -> toggleQueue());
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
                int dur = playerManager.getDuration();
                if (dur > 0) playerManager.seekTo(sb.getProgress() * dur / 1000);
                userIsSeeking = false;
                showControlsTemporarily();
            }
        });

        btnSetA.setOnClickListener(v -> {
            int pos = playerManager.getCurrentPosition();
            playerManager.setABRepeat(pos, playerManager.getABRepeatB() >= 0 ? playerManager.getABRepeatB() : pos);
            btnSetA.setText("A:" + formatTime(pos));
            abRepeatBar.setVisibility(View.VISIBLE);
        });
        btnSetB.setOnClickListener(v -> {
            int pos = playerManager.getCurrentPosition();
            int a = playerManager.getABRepeatA() >= 0 ? playerManager.getABRepeatA() : pos;
            playerManager.setABRepeat(a, pos);
            btnSetB.setText("B:" + formatTime(pos));
            abRepeatBar.setVisibility(View.VISIBLE);
        });
        btnClearAB.setOnClickListener(v -> {
            playerManager.clearABRepeat();
            abRepeatBar.setVisibility(View.GONE);
        });

        speedSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) {
                    float speed = Math.max(0.25f, p / 100f);
                    playerManager.setPlaybackSpeed(speed);
                    speedLabel.setText(String.format("%.1fx", speed));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        volumeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                if (u) playerManager.setVolume(p / 100f, p / 100f);
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
                if (e.getX() < screenWidth / 3f) {
                    playerManager.skipBackward();
                } else if (e.getX() > screenWidth * 2f / 3f) {
                    playerManager.skipForward();
                } else {
                    playerManager.playPause();
                }
                showControlsTemporarily();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                if (playerManager.isPlayingVideo()) {
                    if (controlsLocked) return true;
                    if (controlsVisible) hideControls();
                    else showControlsTemporarily();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float vX, float vY) {
                if (controlsLocked) return false;
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dx) > Math.abs(vY * 3) && Math.abs(dx) > 100) {
                    if (dx > 0) playerManager.skipBackward();
                    else playerManager.skipForward();
                    showControlsTemporarily();
                    return true;
                }
                return false;
            }
        });

        videoContainer.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });
        videoContainer.setClickable(true);
        videoContainer.setFocusable(true);
    }

    private void showControls() {
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
    }

    private void showControlsTemporarily() {
        showControls();
    }

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
        switch (playerManager.getRepeatMode()) {
            case OFF:
                btnRepeat.setAlpha(0.4f);
                btnRepeat.setImageResource(R.drawable.repeat_24px);
                break;
            case ONE:
                btnRepeat.setAlpha(1.0f);
                btnRepeat.setImageResource(R.drawable.repeat_one_24px);
                break;
            case ALL:
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
        if (playerManager.getState() == PlayerManager.PlayState.ERROR || playerManager.getState() == PlayerManager.PlayState.STOPPED) {
            playerManager.play();
        }
    }

    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}
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
        PlayerManager.PlayState s = playerManager.getState();
        btnPlayPause.setImageResource(s == PlayerManager.PlayState.PLAYING ? R.drawable.pause_24px : R.drawable.ic_start);
    }

    @Override
    public void onProgress(int position, int duration) {
        runOnUiThread(() -> {
            currentTime.setText(formatTime(position));
            totalTime.setText(formatTime(duration));
            if (!userIsSeeking && duration > 0) {
                seekBar.setProgress((int) ((long) position * 1000 / duration));
            }
            if (playerManager.hasABRepeat()) {
                int ab = (int) ((long) position * 1000 / Math.max(duration, 1));
                ((SeekBar) findViewById(R.id.abSeekBar)).setProgress(ab);
            }
        });
    }

    @Override
    public void onMediaItemChanged(MediaItem item) {
        runOnUiThread(() -> {
            updateUIForCurrentItem();
            updateQueueAdapter();
            startService(new Intent(this, MusicService.class));
        });
    }

    private void updateUIForCurrentItem() {
        MediaItem item = playerManager.getCurrentItem();
        if (item == null) return;

        boolean isVideo = item.isVideo;
        videoContainer.setVisibility(isVideo ? View.VISIBLE : View.GONE);
        artworkContainer.setVisibility(isVideo ? View.GONE : View.VISIBLE);

        if (isVideo) {
            showControlsTemporarily();
        } else {
            showControls();
        }

        if (!isVideo) {
            trackTitle.setText(item.title != null ? item.title : "Unknown");
            trackArtist.setText(item.artist != null ? item.artist : "Unknown Artist");
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

        int vol = Math.round(playerManager.isMuted() ? 0 : 100);
        volumeSeekBar.setProgress(vol);

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
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surfaceView.getLayoutParams();
                float videoRatio = (float) width / height;
                float containerRatio = (float) videoContainer.getWidth() / videoContainer.getHeight();
                if (videoRatio > containerRatio) {
                    params.width = FrameLayout.LayoutParams.MATCH_PARENT;
                    params.height = (int) (videoContainer.getWidth() / videoRatio);
                } else {
                    params.height = FrameLayout.LayoutParams.MATCH_PARENT;
                    params.width = (int) (videoContainer.getHeight() * videoRatio);
                }
                surfaceView.setLayoutParams(params);
            }
        });
    }

    private void showPlayerSettings() {
        String[] items = {getString(R.string.keep_screen_on, (playerManager.isKeepScreenOn() ? "ON" : "OFF")),
                getString(R.string.skip_duration_X, (playerManager.getSkipDuration() / 1000)),
                getString(R.string.close_player)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.player_settings)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            playerManager.setKeepScreenOn(!playerManager.isKeepScreenOn());
                            updateKeepScreenOn();
                            break;
                        case 1:
                            int[] durations = {5000, 10000, 15000, 30000};
                            String[] labels = {"5s", "10s", "15s", "30s"};
                            new AlertDialog.Builder(this)
                                    .setTitle(R.string.skip_duration)
                                    .setSingleChoiceItems(labels, -1, (d2, w) -> {
                                        if (w >= 0 && w < durations.length) {
                                            playerManager.setSkipDuration(durations[w]);
                                            d2.dismiss();
                                        }
                                    }).show();
                            break;
                        case 2: finish(); break;
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
        if (pausedForBackground) {
            playerManager.play();
            pausedForBackground = false;
        }
        startService(new Intent(this, MusicService.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        playerManager.decrementUIVisible();
        autoHideHandler.removeCallbacks(autoHideRunnable);
        if (playerManager.isPlayingVideo() && !playerManager.isVideoBackgroundPlay() && playerManager.getState() == PlayerManager.PlayState.PLAYING) {
            playerManager.pause();
            pausedForBackground = true;
        }
        startService(new Intent(this, MusicService.class));
    }

    @Override
    protected void onDestroy() {
        playerManager.unregisterCallback(this);
        playerManager.setVideoSizeChangedListener(null);
        autoHideHandler.removeCallbacks(autoHideRunnable);
        playerManager.setSurface(null);
        super.onDestroy();
    }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }
}

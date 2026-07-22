package io.github.abdurazaaqmohammed.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PlayerManager {

    public enum RepeatMode { OFF, ONE, ALL }
    public enum PlayState { IDLE, PREPARING, PLAYING, PAUSED, STOPPED, ENDED, ERROR }

    private static PlayerManager instance;

    private final Context appContext;
    private MediaPlayer mediaPlayer;
    private PlayState state = PlayState.IDLE;
    private List<MediaItem> queue = new ArrayList<>();
    private int currentIndex = -1;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private boolean shuffle;
    private boolean keepScreenOn = true;
    private float playbackSpeed = 1f;
    private float volumeLeft = 1f;
    private float volumeRight = 1f;
    private boolean isMuted;
    private int skipDuration = 10000;
    private Surface videoSurface;
    private boolean isVideo;
    private int resumePosition;
    private boolean videoBackgroundPlay;
    private int uiVisibleCount;

    private int abRepeatA = -1;
    private int abRepeatB = -1;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private boolean hasAudioFocus;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<PlaybackCallback> callbacks = new ArrayList<>();
    private final Random random = new Random();
    private List<Integer> shuffleOrder;

    public interface PlaybackCallback {
        void onStateChanged(PlayState newState);
        void onProgress(int position, int duration);
        void onMediaItemChanged(MediaItem item);
        void onError(String message);
        void onBufferUpdate(int percent);
    }

    public static synchronized PlayerManager getInstance(Context context) {
        if (instance == null) {
            instance = new PlayerManager(context.getApplicationContext());
        }
        return instance;
    }

    private PlayerManager(Context context) {
        this.appContext = context;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        loadSettings();
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public PlayState getState() { return state; }

    public List<MediaItem> getQueue() { return queue; }

    public int getCurrentIndex() { return currentIndex; }

    public MediaItem getCurrentItem() {
        if (currentIndex >= 0 && currentIndex < queue.size()) return queue.get(currentIndex);
        return null;
    }

    public RepeatMode getRepeatMode() { return repeatMode; }
    public void setRepeatMode(RepeatMode mode) {
        this.repeatMode = mode;
        saveSettings();
    }

    public boolean isShuffle() { return shuffle; }

    public float getPlaybackSpeed() { return playbackSpeed; }
    public boolean isKeepScreenOn() { return keepScreenOn; }
    public void setKeepScreenOn(boolean keep) { this.keepScreenOn = keep; saveSettings(); }
    public boolean isMuted() { return isMuted; }
    public int getSkipDuration() { return skipDuration; }
    public void setSkipDuration(int ms) { this.skipDuration = ms; saveSettings(); }

    public boolean hasABRepeat() { return abRepeatA >= 0 && abRepeatB >= 0; }
    public int getABRepeatA() { return abRepeatA; }
    public int getABRepeatB() { return abRepeatB; }

    public boolean isVideoBackgroundPlay() { return videoBackgroundPlay; }
    public void setVideoBackgroundPlay(boolean enable) { this.videoBackgroundPlay = enable; saveSettings(); }

    public void incrementUIVisible() { uiVisibleCount++; }
    public void decrementUIVisible() { if (uiVisibleCount > 0) uiVisibleCount--; }
    public boolean isUIVisible() { return uiVisibleCount > 0; }

    public void registerCallback(PlaybackCallback cb) { if (!callbacks.contains(cb)) callbacks.add(cb); }
    public void unregisterCallback(PlaybackCallback cb) { callbacks.remove(cb); }

    private void notifyState(PlayState s) {
        state = s;
        for (PlaybackCallback cb : callbacks) cb.onStateChanged(s);
    }

    private void notifyProgress(int pos, int dur) {
        for (PlaybackCallback cb : callbacks) cb.onProgress(pos, dur);
    }

    private void notifyMediaItem(MediaItem item) {
        for (PlaybackCallback cb : callbacks) cb.onMediaItemChanged(item);
    }

    private void notifyError(String msg) {
        for (PlaybackCallback cb : callbacks) cb.onError(msg);
    }

    private void notifyBuffer(int pct) {
        for (PlaybackCallback cb : callbacks) cb.onBufferUpdate(pct);
    }

    public void play(List<MediaItem> items, int startIndex) {
        if (items == null || items.isEmpty()) return;
        queue = new ArrayList<>(items);
        currentIndex = startIndex;
        if (shuffle) buildShuffleOrder();
        playCurrent();
    }

    public void play(MediaItem item) {
        List<MediaItem> single = new ArrayList<>();
        single.add(item);
        play(single, 0);
    }

    public void addAndPlay(int index) {
        if (index >= 0 && index < queue.size()) {
            currentIndex = index;
            playCurrent();
        }
    }

    public void addToQueue(MediaItem item) {
        queue.add(item);
        if (currentIndex < 0) { currentIndex = 0; playCurrent(); }
    }

    public void addToQueue(List<MediaItem> items) {
        queue.addAll(items);
        if (currentIndex < 0 && !queue.isEmpty()) { currentIndex = 0; playCurrent(); }
    }

    public void removeFromQueue(int index) {
        if (index >= 0 && index < queue.size()) {
            queue.remove(index);
            if (index < currentIndex) currentIndex--;
            else if (index == currentIndex) {
                if (queue.isEmpty()) { stop(); currentIndex = -1; }
                else playCurrent();
            }
        }
    }

    public void clearQueue() {
        stop();
        queue.clear();
        currentIndex = -1;
        shuffleOrder = null;
    }

    public void next() {
        if (queue.isEmpty()) return;
        if (repeatMode == RepeatMode.ONE) { seekTo(0); return; }
        int next = getNextIndex();
        if (next >= 0) { currentIndex = next; playCurrent(); }
        else { stop(); notifyState(PlayState.ENDED); }
    }

    public void previous() {
        if (queue.isEmpty()) return;
        int pos = getCurrentPosition();
        if (pos > 3000) { seekTo(0); return; }
        int prev = getPrevIndex();
        if (prev >= 0) { currentIndex = prev; playCurrent(); }
    }

    public void setShuffle(boolean s) {
        shuffle = s;
        if (s) buildShuffleOrder();
        else shuffleOrder = null;
        saveSettings();
    }

    private void buildShuffleOrder() {
        shuffleOrder = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) shuffleOrder.add(i);
        Collections.shuffle(shuffleOrder, random);
    }

    private int getNextIndex() {
        if (shuffle && shuffleOrder != null) {
            int idx = shuffleOrder.indexOf(currentIndex);
            if (idx >= 0 && idx + 1 < shuffleOrder.size()) return shuffleOrder.get(idx + 1);
            if (repeatMode == RepeatMode.ALL) { buildShuffleOrder(); return shuffleOrder.get(0); }
            return -1;
        }
        if (currentIndex + 1 < queue.size()) return currentIndex + 1;
        if (repeatMode == RepeatMode.ALL) return 0;
        if (queue.size() == 1 && currentIndex >= 0 && currentIndex < queue.size()) {
            MediaItem current = queue.get(currentIndex);
            if (current != null && current.path != null) {
                java.io.File currentFile = new java.io.File(current.path);
                java.io.File dir = currentFile.getParentFile();
                if (dir != null && dir.isDirectory()) {
                    java.io.File[] files = dir.listFiles();
                    if (files != null) {
                        boolean found = false;
                        for (int i = 0; i < files.length; i++) {
                            if (files[i].equals(currentFile)) { found = true; continue; }
                            if (found && files[i].isFile()) {
                                String name = files[i].getName().toLowerCase(java.util.Locale.ROOT);
                                if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".flac") || name.endsWith(".ogg") || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".wma") || name.endsWith(".opus") || name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".3gp") || name.endsWith(".ts") || name.endsWith(".flv") || name.endsWith(".wmv")) {
                                    queue.add(buildMediaItem(appContext, files[i].getAbsolutePath()));
                                    return currentIndex + 1;
                                }
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    private int getPrevIndex() {
        if (shuffle && shuffleOrder != null) {
            int idx = shuffleOrder.indexOf(currentIndex);
            if (idx > 0) return shuffleOrder.get(idx - 1);
            return currentIndex;
        }
        if (currentIndex > 0) return currentIndex - 1;
        return currentIndex;
    }

    public void playPause() {
        if (state == PlayState.PLAYING) pause();
        else if (state == PlayState.PAUSED || state == PlayState.IDLE || state == PlayState.ENDED) play();
    }

    public void play() {
        if (mediaPlayer == null && currentIndex >= 0) { playCurrent(); return; }
        if (mediaPlayer != null && (state == PlayState.PAUSED || state == PlayState.IDLE || state == PlayState.ENDED)) {
            requestAudioFocus();
            mediaPlayer.start();
            notifyState(PlayState.PLAYING);
            startProgressUpdater();
            saveResumePosition();
        }
    }

    public void pause() {
        if (mediaPlayer != null && state == PlayState.PLAYING) {
            mediaPlayer.pause();
            notifyState(PlayState.PAUSED);
            stopProgressUpdater();
            saveResumePosition();
        }
    }

    public void stop() {
        stopProgressUpdater();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
            } catch (Exception ignored) {}
            mediaPlayer = null;
        }
        abandonAudioFocus();
        isVideo = false;
        notifyState(PlayState.STOPPED);
    }

    public void seekTo(int msec) {
        if (mediaPlayer != null) {
            msec = Math.max(0, msec);
            try { mediaPlayer.seekTo(msec); } catch (Exception ignored) {}
        }
    }

    public void skipForward() { seekTo(getCurrentPosition() + skipDuration); }
    public void skipBackward() { seekTo(getCurrentPosition() - skipDuration); }

    public int getCurrentPosition() {
        if (mediaPlayer != null) try { return mediaPlayer.getCurrentPosition(); } catch (Exception ignored) {}
        return 0;
    }

    public int getDuration() {
        if (mediaPlayer != null) try { return mediaPlayer.getDuration(); } catch (Exception ignored) {}
        return 0;
    }

    public void setVolume(float left, float right) {
        volumeLeft = left;
        volumeRight = right;
        isMuted = false;
        if (mediaPlayer != null) mediaPlayer.setVolume(left, right);
    }

    public void setMuted(boolean mute) {
        isMuted = mute;
        if (mediaPlayer != null) mediaPlayer.setVolume(mute ? 0 : volumeLeft, mute ? 0 : volumeRight);
    }

    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && mediaPlayer != null) {
            try {
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception ignored) {}
        }
    }

    public void setSurface(Surface surface) {
        this.videoSurface = surface;
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setSurface(surface);
            } catch (Exception ignored) {}
        }
    }

    public boolean isPlayingVideo() { return isVideo; }

    public void setABRepeat(int a, int b) {
        abRepeatA = Math.min(a, b);
        abRepeatB = Math.max(a, b);
    }

    public void clearABRepeat() { abRepeatA = -1; abRepeatB = -1; }

    private void playCurrent() {
        if (currentIndex < 0 || currentIndex >= queue.size()) return;
        MediaItem item = queue.get(currentIndex);
        isVideo = item.isVideo;

        stopProgressUpdater();
        abandonAudioFocus();

        // Create new player BEFORE releasing the old one
        MediaPlayer newPlayer = new MediaPlayer();
        MediaPlayer oldPlayer = mediaPlayer;
        mediaPlayer = newPlayer;

        if (oldPlayer != null) {
            try { oldPlayer.stop(); oldPlayer.reset(); oldPlayer.release(); } catch (Exception ignored) {}
        }
        notifyState(PlayState.STOPPED);

        final MediaPlayer currentPlayer = newPlayer;
        currentPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(item.isVideo ? AudioAttributes.CONTENT_TYPE_MOVIE : AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());

        if (videoSurface != null && isVideo) currentPlayer.setSurface(videoSurface);

        currentPlayer.setOnPreparedListener(mp -> {
            if (mp != mediaPlayer) return;
            if (resumePosition > 0 && resumePosition < mp.getDuration()) mp.seekTo(resumePosition);
            resumePosition = 0;
            requestAudioFocus();
            mp.setVolume(isMuted ? 0 : volumeLeft, isMuted ? 0 : volumeRight);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1f) {
                try { mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(playbackSpeed)); } catch (Exception ignored) {}
            }
            mp.start();
            notifyState(PlayState.PLAYING);
            startProgressUpdater();
            try { appContext.startService(new android.content.Intent(appContext, MusicService.class)); } catch (Exception ignored) {}
        });
        currentPlayer.setOnCompletionListener(mp -> {
            if (mp != mediaPlayer) return;
            stopProgressUpdater();
            if (abRepeatA >= 0 && abRepeatB >= 0) {
                seekTo(abRepeatA);
                return;
            }
            if (repeatMode == RepeatMode.ONE) { seekTo(0); return; }
            int next = getNextIndex();
            if (next >= 0) { currentIndex = next; playCurrent(); }
            else {
                saveResumePosition();
                notifyState(PlayState.ENDED);
            }
        });
        currentPlayer.setOnErrorListener((mp, what, extra) -> {
            if (mp != mediaPlayer) return true;
            //notifyError("Media error: " + what + " / " + extra);
            notifyState(PlayState.ERROR);
            return true;
        });
        currentPlayer.setOnInfoListener((mp, what, extra) -> {
            if (mp != mediaPlayer) return false;
            if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) notifyBuffer(0);
            else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) notifyBuffer(100);
            return false;
        });
        currentPlayer.setOnVideoSizeChangedListener((mp, w, h) -> {
            if (mp != mediaPlayer) return;
            if (videoSizeChangedListener != null) videoSizeChangedListener.onVideoSizeChanged(w, h);
        });

        try {
            notifyState(PlayState.PREPARING);
            notifyMediaItem(item);
            currentPlayer.setDataSource(appContext, item.uri);
            currentPlayer.prepareAsync();
        } catch (IOException | IllegalStateException e) {
            if (currentPlayer == mediaPlayer) {
                notifyError("Cannot play file: " + e.getMessage());
                notifyState(PlayState.ERROR);
            }
        }
    }

    private void requestAudioFocus() {
        if (hasAudioFocus) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setOnAudioFocusChangeListener(focusChange -> {
                        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                            pause();
                            hasAudioFocus = false;
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pause();
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            if (mediaPlayer != null) mediaPlayer.setVolume(0.3f, 0.3f);
                        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                            if (mediaPlayer != null) mediaPlayer.setVolume(isMuted ? 0 : volumeLeft, isMuted ? 0 : volumeRight);
                        }
                    })
                    .build();
            int res = audioManager.requestAudioFocus(audioFocusRequest);
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        } else {
            int res = audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }
    }

    private void abandonAudioFocus() {
        if (!hasAudioFocus) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(null);
        }
        hasAudioFocus = false;
    }

    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (mediaPlayer != null && (state == PlayState.PLAYING || state == PlayState.PAUSED)) {
                try {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();
                    notifyProgress(pos, dur);
                    if (abRepeatA >= 0 && abRepeatB >= 0 && pos >= abRepeatB) {
                        seekTo(abRepeatA);
                    }
                } catch (Exception ignored) {}
                handler.postDelayed(this, 250);
            }
        }
    };

    private void startProgressUpdater() { handler.removeCallbacks(progressUpdater); handler.post(progressUpdater); }
    private void stopProgressUpdater() { handler.removeCallbacks(progressUpdater); }

    private void saveResumePosition() {
        MediaItem item = getCurrentItem();
        if (item != null && state == PlayState.PAUSED) {
            int pos = getCurrentPosition();
            PreferenceManager.getDefaultSharedPreferences(appContext)
                    .edit().putInt("resume_pos_" + item.path, pos).apply();
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
        try { repeatMode = RepeatMode.values()[prefs.getInt("player_repeat", 0)]; } catch (Exception e) { repeatMode = RepeatMode.OFF; }
        shuffle = prefs.getBoolean("player_shuffle", false);
        keepScreenOn = prefs.getBoolean("player_keep_screen_on", true);
        skipDuration = prefs.getInt("player_skip_duration", 10000);
        playbackSpeed = prefs.getFloat("player_speed", 1f);
        videoBackgroundPlay = prefs.getBoolean("player_video_background", false);
    }

    private void saveSettings() {
        PreferenceManager.getDefaultSharedPreferences(appContext)
                .edit()
                .putInt("player_repeat", repeatMode.ordinal())
                .putBoolean("player_shuffle", shuffle)
                .putBoolean("player_keep_screen_on", keepScreenOn)
                .putInt("player_skip_duration", skipDuration)
                .putFloat("player_speed", playbackSpeed)
                .putBoolean("player_video_background", videoBackgroundPlay)
                .apply();
    }

    private VideoSizeChangedListener videoSizeChangedListener;
    public void setVideoSizeChangedListener(VideoSizeChangedListener l) { this.videoSizeChangedListener = l; }
    public interface VideoSizeChangedListener { void onVideoSizeChanged(int width, int height); }

    public static MediaItem buildMediaItem(Context context, String filePath) {
        Uri uri = Uri.fromFile(new java.io.File(filePath));
        String title = null, artist = null, album = null;
        long duration = 0;
        boolean isVideo = false;
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(context, uri);
            String t = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (t != null) title = t;
            String a = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (a != null) artist = a;
            String al = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (al != null) album = al;
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (d != null) try { duration = Long.parseLong(d); } catch (NumberFormatException ignored) {}
            String mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (mime != null) isVideo = mime.startsWith("video/");
            mmr.release();
        } catch (Exception ignored) { try { mmr.release(); } catch (Exception ignored2) {} }
        if (title == null || title.isEmpty()) {
            String name = filePath.substring(filePath.lastIndexOf('/') + 1);
            title = filePath.lastIndexOf('\\') >= 0 ? filePath.substring(filePath.lastIndexOf('\\') + 1) : name;
        }
        return new MediaItem(uri, filePath, title, artist, album, duration, isVideo);
    }
}

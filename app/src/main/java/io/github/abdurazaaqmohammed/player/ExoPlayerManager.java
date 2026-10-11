package io.github.abdurazaaqmohammed.player;

import android.content.Context;
import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * ExoPlayer Manager — engine kedua buat MKV/VTT/SRT embedded.
 */
public class ExoPlayerManager {
    private static ExoPlayerManager instance;
    private final Context appContext;
    private ExoPlayer exoPlayer;
    private PlayerView playerView;
    private SubtitleView externalSubtitleView;
    private List<String> playlist = new ArrayList<>();
    private int currentIndex = -1;
    private int repeatMode = 0;   // 0=OFF, 1=ONE, 2=ALL
    private boolean shuffle = false;

    public interface Listener {
        void onStateChanged(boolean isPlaying);
        void onError(String message);
    }

    private Listener listener;

    public static synchronized ExoPlayerManager getInstance(Context context) {
        if (instance == null) {
            instance = new ExoPlayerManager(context.getApplicationContext());
        }
        return instance;
    }

    private ExoPlayerManager(Context context) {
        this.appContext = context;
    }

    public void init(PlayerView view, SubtitleView subtitleView, Listener listener) {
        this.playerView = view;
        this.externalSubtitleView = subtitleView;
        this.listener = listener;
        if (exoPlayer == null) {
            exoPlayer = new ExoPlayer.Builder(appContext).build();
            playerView.setPlayer(exoPlayer);
            playerView.setUseController(false);

            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (listener != null) {
                        listener.onStateChanged(state == Player.STATE_READY
                                || state == Player.STATE_BUFFERING);
                    }
                }

                @Override
                public void onPlayerError(androidx.media3.common.PlaybackException error) {
                    if (listener != null) listener.onError(error.getMessage());
                }
            });
        }
    }

    public void play(String videoPath) {
        if (exoPlayer == null) return;
        File video = new File(videoPath);
        playlist.clear();
        playlist.add(videoPath);
        currentIndex = 0;

        MediaItem.Builder builder = new MediaItem.Builder().setUri(Uri.fromFile(video));
        exoPlayer.setMediaItem(builder.build());
        exoPlayer.prepare();
        exoPlayer.play();
    }

    public void playPause() {
        if (exoPlayer == null) return;
        if (exoPlayer.isPlaying()) exoPlayer.pause();
        else exoPlayer.play();
    }

    public void pause() {
        if (exoPlayer != null) exoPlayer.pause();
    }

    public void stop() {
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                exoPlayer.release();
            } catch (Exception ignored) {}
            exoPlayer = null;
        }
    }

    public void seekTo(long ms) {
        if (exoPlayer != null) exoPlayer.seekTo(Math.max(0, ms));
    }

    public void seekToNext() {
        if (exoPlayer == null) return;
        if (currentIndex + 1 < playlist.size()) {
            currentIndex++;
            play(playlist.get(currentIndex));
        } else if (repeatMode == 2 && !playlist.isEmpty()) {
            currentIndex = 0;
            play(playlist.get(0));
        }
    }

    public void seekToPrevious() {
        if (exoPlayer == null) return;
        if (currentIndex > 0) {
            currentIndex--;
            play(playlist.get(currentIndex));
        }
    }

    public long getCurrentPosition() {
        return exoPlayer != null ? exoPlayer.getCurrentPosition() : 0;
    }

    public long getDuration() {
        return exoPlayer != null ? exoPlayer.getDuration() : 0;
    }

    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
    }

    public void setPlaybackSpeed(float speed) {
        if (exoPlayer == null) return;
        try {
            exoPlayer.setPlaybackSpeed(speed);
        } catch (Exception ignored) {}
    }

    // === Repeat ===
    public int getRepeatMode() {
        return repeatMode;
    }

    public void setRepeatMode(int mode) {
        this.repeatMode = mode;
        if (exoPlayer == null) return;
        switch (mode) {
            case 0: exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF); break;
            case 1: exoPlayer.setRepeatMode(Player.REPEAT_MODE_ONE); break;
            case 2: exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL); break;
        }
    }

    // === Shuffle ===
    public boolean isShuffle() {
        return shuffle;
    }

    public void setShuffle(boolean on) {
        this.shuffle = on;
        if (exoPlayer == null) return;
        exoPlayer.setShuffleModeEnabled(on);
    }

    public ExoPlayer getExoPlayer() {
        return exoPlayer;
    }

    // === SUBTITLE ===

    /** Dapetin subtitle tracks yang tersedia */
    public androidx.media3.common.Tracks getCurrentTracks() {
        return exoPlayer != null ? exoPlayer.getCurrentTracks() : null;
    }

    /** Disable subtitle */
    public void disableSubtitle() {
        if (exoPlayer == null) return;
        exoPlayer.setTrackSelectionParameters(
            exoPlayer.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
                .build()
        );
    }

    /** Enable subtitle track by index */
    public void selectSubtitleTrack(int index) {
        if (exoPlayer == null) return;
        androidx.media3.common.Tracks tracks = exoPlayer.getCurrentTracks();
        if (tracks == null) return;

        int currentIdx = 0;
        for (androidx.media3.common.Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == androidx.media3.common.C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSupported(i)) {
                        if (currentIdx == index) {
                            androidx.media3.common.TrackSelectionOverride override =
                                new androidx.media3.common.TrackSelectionOverride(group.getMediaTrackGroup(), i);
                            exoPlayer.setTrackSelectionParameters(
                                exoPlayer.getTrackSelectionParameters()
                                    .buildUpon()
                                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(override)
                                    .build()
                            );
                            return;
                        }
                        currentIdx++;
                    }
                }
            }
        }
    }

    /** Enable subtitle default */
    public void enableSubtitle() {
        if (exoPlayer == null) return;
        exoPlayer.setTrackSelectionParameters(
            exoPlayer.getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                .build()
        );
    }
}

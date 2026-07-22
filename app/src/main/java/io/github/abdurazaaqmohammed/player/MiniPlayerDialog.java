package io.github.abdurazaaqmohammed.player;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;

public class MiniPlayerDialog {

    private final Activity activity;
    private Dialog dialog;
    private ImageView artworkView;
    private TextView titleView, artistView, currentTime, totalTime;
    private ImageButton btnPlayPause, btnNext, btnExpand;
    private SeekBar seekBar;
    private final PlayerManager playerManager;
    private boolean isShowing;

    private final PlayerManager.PlaybackCallback callback = new PlayerManager.PlaybackCallback() {
        @Override public void onStateChanged(PlayerManager.PlayState newState) {
            if (dialog != null && dialog.isShowing()) {
                btnPlayPause.setImageResource(newState == PlayerManager.PlayState.PLAYING ? R.drawable.pause_24px : R.drawable.ic_start);
            }
        }
        @Override public void onProgress(int position, int duration) {
            if (dialog != null && dialog.isShowing() && duration > 0) {
                seekBar.setProgress((int) ((long) position * 1000 / duration));
                currentTime.setText(formatTime(position));
                totalTime.setText(formatTime(duration));
            }
        }
        @Override public void onMediaItemChanged(MediaItem item) {
            if (dialog != null && dialog.isShowing()) updateUI(item);
        }
        @Override public void onError(String message) {
            if (activity != null && !activity.isFinishing()) {
                android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_LONG).show();
            }
        }
        @Override public void onBufferUpdate(int percent) {}
    };

    public MiniPlayerDialog(Activity activity) {
        this.activity = activity;
        this.playerManager = PlayerManager.getInstance(activity);
    }

    public void show() {
        if (isShowing) return;
        isShowing = true;
        playerManager.incrementUIVisible();
        playerManager.registerCallback(callback);

        LayoutInflater inflater = LayoutInflater.from(activity);
        android.view.View view = inflater.inflate(R.layout.dialog_mini_player, null);

        artworkView = view.findViewById(R.id.artworkView);
        titleView = view.findViewById(R.id.trackTitle);
        artistView = view.findViewById(R.id.trackArtist);
        currentTime = view.findViewById(R.id.currentTime);
        totalTime = view.findViewById(R.id.totalTime);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnNext = view.findViewById(R.id.btnNext);
        btnExpand = view.findViewById(R.id.btnExpand);
        seekBar = view.findViewById(R.id.seekBar);

        btnPlayPause.setOnClickListener(v1 -> playerManager.playPause());
        btnNext.setOnClickListener(v1 -> playerManager.next());
        btnExpand.setOnClickListener(v1 -> {
            dismiss();
            MediaPlayerActivity.open(activity);
        });
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    int dur = playerManager.getDuration();
                    if (dur > 0) playerManager.seekTo((int) ((long) progress * dur / 1000));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setOnDismissListener(d -> {
                    isShowing = false;
                    playerManager.unregisterCallback(callback);
                })
                .show();

        MediaItem current = playerManager.getCurrentItem();
        if (current != null) {
            updateUI(current);
            currentTime.setText(formatTime(playerManager.getCurrentPosition()));
            totalTime.setText(formatTime(playerManager.getDuration()));
        }
        PlayerManager.PlayState state = playerManager.getState();
        btnPlayPause.setImageResource(state == PlayerManager.PlayState.PLAYING ? R.drawable.pause_24px : R.drawable.ic_start);
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
            isShowing = false;
            playerManager.unregisterCallback(callback);
            playerManager.decrementUIVisible();
        }
    }

    public boolean isShowing() { return isShowing; }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    private void updateUI(MediaItem item) {
        titleView.setText(item.title != null ? item.title : "Unknown");
        artistView.setText(item.artist != null ? item.artist : "Unknown Artist");

        if (item.path != null) try (MediaMetadataRetriever mmr = new MediaMetadataRetriever()) {
            mmr.setDataSource(activity, item.uri);
            byte[] art = mmr.getEmbeddedPicture();
            if (art == null) artworkView.setImageResource(android.R.drawable.ic_menu_gallery);
            else artworkView.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.length));
        } catch (Exception e) {
            artworkView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }
}

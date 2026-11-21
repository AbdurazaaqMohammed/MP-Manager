package io.github.abdurazaaqmohammed.ui.viewer;

import android.app.Activity;
import android.app.Dialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class MiniPlayerDialog {

    private final Activity activity;
    private Dialog dialog;
    private TextView titleView, artistView, currentTime, totalTime;
    private ImageButton btnPlayPause, btnNext, btnExpand, btnMinimize;
    private SeekBar seekBar;
    private final PlayerManager playerManager;
    private boolean isShowing;
    private boolean isMinimizing;
    private boolean isExpanding;

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
                Extensions.showMessage(activity, message);
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
        isMinimizing = false;
        isExpanding = false;
        playerManager.incrementUIVisible();
        playerManager.registerCallback(callback);

        LayoutInflater inflater = LayoutInflater.from(activity);
        View view = inflater.inflate(R.layout.dialog_mini_player, null);

        titleView = view.findViewById(R.id.trackTitle);
        artistView = view.findViewById(R.id.trackArtist);
        currentTime = view.findViewById(R.id.currentTime);
        totalTime = view.findViewById(R.id.totalTime);
        btnPlayPause = view.findViewById(R.id.btnPlayPause);
        btnNext = view.findViewById(R.id.btnNext);
        btnExpand = view.findViewById(R.id.btnExpand);
        btnMinimize = view.findViewById(R.id.btnMinimize);
        seekBar = view.findViewById(R.id.seekBar);

        btnPlayPause.setOnClickListener(v1 -> playerManager.playPause());
        btnNext.setOnClickListener(v1 -> playerManager.next());
        btnExpand.setOnClickListener(v1 -> {
            isExpanding = true;
            dismiss();
            MediaPlayerActivity.open(activity);
        });
        btnMinimize.setOnClickListener(v1 -> {
            isMinimizing = true;
            dismiss();
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
                    playerManager.decrementUIVisible();
                    if (!isMinimizing && !isExpanding) {
                        playerManager.stop();
                    }
                    isMinimizing = false;
                    isExpanding = false;
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
        }
    }

    public boolean isShowing() { return isShowing; }

    private static String formatTime(long ms) {
        long totalSec = ms / 1000;
        return String.format("%d:%02d", totalSec / 60, totalSec % 60);
    }

    private void updateUI(MediaItem item) {
        titleView.setText(item.title != null ? item.title : activity.getString(R.string.media_unknown));
        artistView.setText(item.artist != null ? item.artist : activity.getString(R.string.music_unknown_artist));
    }
}

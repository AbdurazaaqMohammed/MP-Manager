package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.media.AudioManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildVolume().
 */
public class VolumeTool extends BaseToolPlugin {

    public VolumeTool() {
        super("volume", R.string.volume_title, R.string.volume_sub,  ToolCategories.DEVICE);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.volume_volume_panel));
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            TextView t = ToolViewFactory.makeOutput(box);
            t.setText(t.getContext().getString(R.string.volume_audio_service_unavailable));
            return box;
        }
        int[] streams = new int[]{AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION};
        String[] names = new String[]{context.getString(R.string.volume_music), context.getString(R.string.volume_alarm), context.getString(R.string.volume_ring), context.getString(R.string.volume_notify)};
        for (int i = 0; i < streams.length; i++) {
            final int stream = streams[i];
            ToolViewFactory.addLabel(box, names[i]);
            SeekBar bar = new SeekBar(context);
            try {
                bar.setMax(audio.getStreamMaxVolume(stream));
                bar.setProgress(audio.getStreamVolume(stream));
            } catch (Exception ignored) {
            }
            final int[] last = new int[]{bar.getProgress()};
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                    if (fromUser) {
                        try {
                            audio.setStreamVolume(stream, progress, 0);
                        } catch (Exception ignored) {
                        }
                    }
                    last[0] = progress;
                }
                public void onStartTrackingTouch(SeekBar s) {
                }
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            box.addView(bar);
        }
        MaterialButton muteBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.volume_mute_music_stream));
        muteBtn.setOnClickListener(v -> {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                ToolViewFactory.toast(context, context.getString(R.string.volume_music_muted_use_sliders_to_res));
            } catch (Exception e) {
                ToolViewFactory.toast(context, context.getString(R.string.volume_failed));
            }
        });
        return box;
    }
}

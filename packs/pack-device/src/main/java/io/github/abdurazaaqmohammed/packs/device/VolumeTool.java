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
        super("volume", "Volume Panel", "Control all volume streams", ToolCategories.DEVICE);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Volume Panel");
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) {
            TextView t = ToolViewFactory.makeOutput(box);
            t.setText("Audio service unavailable");
            return box;
        }
        int[] streams = new int[]{AudioManager.STREAM_MUSIC, AudioManager.STREAM_ALARM, AudioManager.STREAM_RING, AudioManager.STREAM_NOTIFICATION};
        String[] names = new String[]{"Music", "Alarm", "Ring", "Notify"};
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
        MaterialButton muteBtn = ToolViewFactory.makeButton(box, "Mute music stream");
        muteBtn.setOnClickListener(v -> {
            try {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
                ToolViewFactory.toast(context, "Music muted, use sliders to restore");
            } catch (Exception e) {
                ToolViewFactory.toast(context, "Failed");
            }
        });
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.media;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildMetronome().
 */
public class MetronomeTool extends BaseToolPlugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator toneGenerator;
    private boolean metronomeRunning;
    private Runnable metronomeTick;
    private int metronomeBpm = 120;
    private int metronomeBeat;
    private View metronomeFlash;

    public MetronomeTool() {
        super("metronome", R.string.metronome_title, R.string.metronome_sub,  ToolCategories.MEDIA);
    }

    private ToneGenerator getTone() {
        if (toneGenerator == null) {
            try {
                toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            } catch (Exception ignored) {
            }
        }
        return toneGenerator;
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        metronomeRunning = false;
        metronomeBeat = 0;
        metronomeBpm = 120;
        metronomeTick = null;
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.metronome_metronome));
        TextView bpmLabel = ToolViewFactory.addLabel(box, box.getContext().getString(R.string.metronome_tempo_120_bpm));
        SeekBar bpmBar = new SeekBar(context);
        bpmBar.setMax(210);
        bpmBar.setProgress(90);
        box.addView(bpmBar);
        metronomeFlash = new View(context);
        metronomeFlash.setBackgroundColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#1B73E8")));
        box.addView(metronomeFlash, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 80)));
        TextView beatText = ToolViewFactory.makeOutput(box);
        beatText.setGravity(Gravity.CENTER);
        beatText.setText(beatText.getContext().getString(R.string.metronome_stopped));
        bpmBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                metronomeBpm = 30 + progress;
                bpmLabel.setText(bpmLabel.getContext().getString(R.string.metronome_tempo, metronomeBpm));
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        MaterialButton toggleBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.metronome_start));
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (metronomeRunning) {
                    metronomeRunning = false;
                    toggleBtn.setText(toggleBtn.getContext().getString(R.string.metronome_start));
                    beatText.setText(beatText.getContext().getString(R.string.metronome_stopped));
                    return;
                }
                metronomeRunning = true;
                metronomeBeat = 0;
                toggleBtn.setText(toggleBtn.getContext().getString(R.string.metronome_stop));
                if (metronomeTick == null) {
                    metronomeTick = new Runnable() {
                        public void run() {
                            if (!metronomeRunning) {
                                return;
                            }
                            metronomeBeat++;
                            int beat = ((metronomeBeat - 1) % 4) + 1;
                            beatText.setText(beatText.getContext().getString(R.string.metronome_beat_of, beat));
                            try {
                                ToneGenerator tg = getTone();
                                if (tg != null) {
                                    tg.startTone(beat == 1 ? ToneGenerator.TONE_PROP_BEEP : ToneGenerator.TONE_PROP_BEEP2, 90);
                                }
                            } catch (Exception ignored) {
                            }
                            try {
                                int idle = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.parseColor("#1B73E8"));
                                metronomeFlash.setBackgroundColor(beat == 1 ? Color.parseColor("#D93025") : idle);
                            } catch (Exception ignored) {
                            }
                            long interval = 60000L / Math.max(30, metronomeBpm);
                            handler.postDelayed(this, interval);
                        }
                    };
                }
                handler.post(metronomeTick);
            }
        });
        return box;
    }

    @Override
    public void onDestroy() {
        metronomeRunning = false;
        handler.removeCallbacksAndMessages(null);
        try {
            if (toneGenerator != null) toneGenerator.release();
        } catch (Exception ignored) {
        }
        toneGenerator = null;
        metronomeTick = null;
        metronomeFlash = null;
    }
}

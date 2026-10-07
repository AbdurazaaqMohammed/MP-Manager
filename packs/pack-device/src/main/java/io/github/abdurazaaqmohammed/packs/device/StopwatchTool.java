package io.github.abdurazaaqmohammed.packs.device;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildStopwatch().
 * Holds its own ticker; the host stops it via onDestroy().
 */
public class StopwatchTool extends BaseToolPlugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running;
    private long base;
    private long accum;
    private final List<String> laps = new ArrayList<>();
    private int lapCount;
    private Runnable tick;

    public StopwatchTool() {
        super("stopwatch", "Stopwatch", "Stopwatch with laps", ToolCategories.DEVICE);
    }

    private long elapsed() {
        if (running) {
            return accum + (SystemClock.elapsedRealtime() - base);
        }
        return accum;
    }

    private static String format(long ms) {
        long m = ms / 60000;
        long s = (ms % 60000) / 1000;
        long cs = (ms % 1000) / 10;
        return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        running = false;
        accum = 0;
        laps.clear();
        lapCount = 0;
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("device", R.string.s_stopwatch, "Stopwatch"));
        TextView stopwatchText = ToolViewFactory.makeOutput(box);
        stopwatchText.setTextSize(32);
        stopwatchText.setGravity(Gravity.CENTER);
        stopwatchText.setText(PackRes.str("device", R.string.s_00_00_00, "00:00.00"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton startBtn = ToolViewFactory.makeRowButton(row, PackRes.str("device", R.string.s_start, "Start"), 1f);
        MaterialButton lapBtn = ToolViewFactory.makeRowButton(row, PackRes.str("device", R.string.s_lap, "Lap"), 1f);
        MaterialButton resetBtn = ToolViewFactory.makeRowButton(row, PackRes.str("device", R.string.s_reset, "Reset"), 1f);
        ArrayAdapter<String> lapAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, laps);
        ListView lapList = new ListView(context);
        lapList.setAdapter(lapAdapter);
        box.addView(lapList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 220)));
        tick = new Runnable() {
            public void run() {
                if (running) {
                    stopwatchText.setText(format(elapsed()));
                    handler.postDelayed(this, 30);
                }
            }
        };
        startBtn.setOnClickListener(v -> {
            if (running) {
                accum = elapsed();
                running = false;
                ((Button) v).setText(PackRes.str("device", R.string.s_start, "Start"));
            } else {
                base = SystemClock.elapsedRealtime();
                running = true;
                ((Button) v).setText(PackRes.str("device", R.string.s_pause, "Pause"));
                handler.post(tick);
            }
        });
        lapBtn.setOnClickListener(v -> {
            if (running) {
                lapCount++;
                laps.add(0, "Lap " + lapCount + "  " + format(elapsed()));
                lapAdapter.notifyDataSetChanged();
                ToolViewFactory.vibrateTick(context);
            }
        });
        resetBtn.setOnClickListener(v -> {
            running = false;
            accum = 0L;
            stopwatchText.setText(PackRes.str("device", R.string.s_00_00_00, "00:00.00"));
            laps.clear();
            lapAdapter.notifyDataSetChanged();
            lapCount = 0;
            startBtn.setText(PackRes.str("device", R.string.s_start, "Start"));
        });
        return box;
    }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacksAndMessages(null);
    }
}

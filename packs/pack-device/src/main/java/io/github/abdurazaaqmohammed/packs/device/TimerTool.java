package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildTimer().
 */
public class TimerTool extends BaseToolPlugin {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean timerRunning;
    private long timerRemaining;
    private CountDownTimer countDownTimer;

    public TimerTool() {
        super("timer", "Countdown Timer", "Set alarms and run countdowns", ToolCategories.DEVICE);
    }

    private static long parseLongSafe(String s) {
        try {
            s = s.trim();
            if (s.isEmpty()) {
                return 0;
            }
            return Long.parseLong(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatTimer(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) {
            return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    private void beep() {
        try {
            ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            tg.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 600);
            handler.postDelayed(() -> {
                try {
                    tg.release();
                } catch (Exception ignored) {
                }
            }, 800);
        } catch (Exception ignored) {
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        timerRunning = false;
        timerRemaining = 0;
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Countdown Timer");
        LinearLayout row = ToolViewFactory.makeRow(box);
        EditText hInput = ToolViewFactory.makeRowInput(row, "hh", InputType.TYPE_CLASS_NUMBER, 1f, null);
        EditText mInput = ToolViewFactory.makeRowInput(row, "mm", InputType.TYPE_CLASS_NUMBER, 1f, null);
        EditText sInput = ToolViewFactory.makeRowInput(row, "ss", InputType.TYPE_CLASS_NUMBER, 1f, null);
        TextView timerText = ToolViewFactory.makeOutput(box);
        timerText.setTextSize(32);
        timerText.setGravity(Gravity.CENTER);
        timerText.setText("00:00");
        LinearLayout row2 = ToolViewFactory.makeRow(box);
        MaterialButton startBtn = ToolViewFactory.makeRowButton(row2, "Start", 1f);
        MaterialButton pauseBtn = ToolViewFactory.makeRowButton(row2, "Pause", 1f);
        MaterialButton resetBtn = ToolViewFactory.makeRowButton(row2, "Reset", 1f);
        startBtn.setOnClickListener(v -> {
            if (timerRunning) {
                return;
            }
            long total = timerRemaining;
            if (total <= 0) {
                long h = parseLongSafe(hInput.getText().toString());
                long m = parseLongSafe(mInput.getText().toString());
                long s = parseLongSafe(sInput.getText().toString());
                total = (h * 3600 + m * 60 + s) * 1000;
            }
            if (total <= 0) {
                ToolViewFactory.toast(context, "Enter a duration");
                return;
            }
            timerRunning = true;
            try {
                if (countDownTimer != null) {
                    countDownTimer.cancel();
                }
            } catch (Exception ignored) {
            }
            countDownTimer = new CountDownTimer(total, 200) {
                public void onTick(long left) {
                    timerRemaining = left;
                    timerText.setText(formatTimer(left));
                }
                public void onFinish() {
                    timerRunning = false;
                    timerRemaining = 0;
                    timerText.setText("Done");
                    ToolViewFactory.toast(context, "Time is up");
                    ToolViewFactory.vibrateTick(context);
                    beep();
                }
            };
            countDownTimer.start();
        });
        pauseBtn.setOnClickListener(v -> {
            if (timerRunning && countDownTimer != null) {
                countDownTimer.cancel();
                timerRunning = false;
            }
        });
        resetBtn.setOnClickListener(v -> {
            if (countDownTimer != null) {
                try {
                    countDownTimer.cancel();
                } catch (Exception ignored) {
                }
            }
            timerRunning = false;
            timerRemaining = 0;
            timerText.setText("00:00");
        });
        return box;
    }

    @Override
    public void onDestroy() {
        timerRunning = false;
        try {
            if (countDownTimer != null) countDownTimer.cancel();
        } catch (Exception ignored) {
        }
        handler.removeCallbacksAndMessages(null);
    }
}

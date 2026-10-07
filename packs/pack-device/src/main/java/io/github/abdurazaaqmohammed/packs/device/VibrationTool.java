package io.github.abdurazaaqmohammed.packs.device;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildVibration().
 */
public class VibrationTool extends BaseToolPlugin {

    private Vibrator vibrator;

    public VibrationTool() {
        super("vibration", "Vibration Studio", "Create custom vibrations", ToolCategories.DEVICE);
    }

    private void vibratePattern(long[] pattern) {
        try {
            if (vibrator == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("device", R.string.s_vibration_studio, "Vibration Studio"));
        EditText customInput = ToolViewFactory.makeInput(box, PackRes.str("device", R.string.s_custom_pattern_ms_e_g_0_200_100_400, "Custom pattern ms, e.g. 0,200,100,400"),
                InputType.TYPE_CLASS_TEXT);
        customInput.setText(PackRes.str("device", R.string.s_0_200_100_400, "0,200,100,400"));
        LinearLayout row1 = ToolViewFactory.makeRow(box);
        MaterialButton shortBtn = ToolViewFactory.makeRowButton(row1, PackRes.str("device", R.string.s_short, "Short"), 1f);
        MaterialButton longBtn = ToolViewFactory.makeRowButton(row1, PackRes.str("device", R.string.s_long, "Long"), 1f);
        MaterialButton sosBtn = ToolViewFactory.makeRowButton(row1, PackRes.str("device", R.string.s_sos, "SOS"), 1f);
        LinearLayout row2 = ToolViewFactory.makeRow(box);
        MaterialButton heartbeatBtn = ToolViewFactory.makeRowButton(row2, PackRes.str("device", R.string.s_heartbeat, "Heartbeat"), 1f);
        MaterialButton customBtn = ToolViewFactory.makeRowButton(row2, PackRes.str("device", R.string.s_custom, "Custom"), 1f);
        MaterialButton stopBtn = ToolViewFactory.makeRowButton(row2, PackRes.str("device", R.string.s_stop, "Stop"), 1f);
        shortBtn.setOnClickListener(v -> vibratePattern(new long[]{0, 150}));
        longBtn.setOnClickListener(v -> vibratePattern(new long[]{0, 600}));
        sosBtn.setOnClickListener(v -> vibratePattern(new long[]{0, 150, 150, 150, 150, 150, 300, 400, 200, 400, 200, 400, 300, 150, 150, 150, 150, 150}));
        heartbeatBtn.setOnClickListener(v -> vibratePattern(new long[]{0, 120, 120, 180, 400}));
        customBtn.setOnClickListener(v -> {
            try {
                String[] parts = customInput.getText().toString().trim().split(",");
                long[] pattern = new long[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    pattern[i] = Math.max(0, Long.parseLong(parts[i].trim()));
                }
                vibratePattern(pattern);
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_use_numbers_separated_by_commas, "Use numbers separated by commas"));
            }
        });
        stopBtn.setOnClickListener(v -> {
            try {
                if (vibrator != null) {
                    vibrator.cancel();
                }
            } catch (Exception ignored) {
            }
        });
        return box;
    }

    @Override
    public void onDestroy() {
        try {
            if (vibrator != null) vibrator.cancel();
        } catch (Exception ignored) {
        }
        vibrator = null;
    }
}

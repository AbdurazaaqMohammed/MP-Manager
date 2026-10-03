package io.github.abdurazaaqmohammed.packs.time;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Health;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.List;

/**
 * Extraction of ToolRunnerActivity.buildSleep().
 */
public class SleepTool extends BaseToolPlugin {

    public SleepTool() {
        super("sleep", R.string.sleep_title, R.string.sleep_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.sleep_sleep_cycles));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.sleep_each_cycle_is_90_minutes_wake_));
        EditText wakeInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.sleep_wake_time_hh_mm), InputType.TYPE_CLASS_DATETIME);
        wakeInput.setText("07:00");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton bedBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.sleep_best_bedtimes));
        bedBtn.setOnClickListener(v -> {
            try {
                String[] parts = wakeInput.getText().toString().trim().split(":");
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                List<String> rows = Health.bedtimesForWake(context, hour, minute);
                output.setText(TextUtils.join("\n", rows));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.sleep_use_hh_mm));
            }
        });
        MaterialButton nowBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.sleep_sleeping_now_when_to_wake));
        nowBtn.setOnClickListener(v -> {
            List<String> rows = Health.wakeTimesFromNow(context);
            output.setText(TextUtils.join("\n", rows));
        });
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
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
        super("sleep", "Sleep Cycles", "Bedtimes in 90-minute cycles", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_sleep_cycles, "Sleep Cycles"));
        ToolViewFactory.addLabel(box, PackRes.str("math", R.string.s_each_cycle_is_90_minutes_wake_at_the_end_of_a_cy, "Each cycle is 90 minutes. Wake at the end of a cycle."));
        EditText wakeInput = ToolViewFactory.makeTimeField(box, PackRes.str("math", R.string.s_wake_time, "Wake time"));
        wakeInput.setText(PackRes.str("math", R.string.s_07_00, "07:00"));
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton bedBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_best_bedtimes, "Best bedtimes"));
        bedBtn.setOnClickListener(v -> {
            try {
                String[] parts = wakeInput.getText().toString().trim().split(":");
                int hour = Integer.parseInt(parts[0].trim());
                int minute = Integer.parseInt(parts[1].trim());
                List<String> rows = Health.bedtimesForWake(hour, minute);
                output.setText(TextUtils.join("\n", rows));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_use_hh_mm, "Use HH:mm"));
            }
        });
        MaterialButton nowBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_sleeping_now_when_to_wake, "Sleeping now, when to wake"));
        nowBtn.setOnClickListener(v -> {
            List<String> rows = Health.wakeTimesFromNow();
            output.setText(TextUtils.join("\n", rows));
        });
        return box;
    }
}

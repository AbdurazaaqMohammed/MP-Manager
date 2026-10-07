package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.DateTime;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildTimeCalc().
 */
public class TimeCalcTool extends BaseToolPlugin {

    public TimeCalcTool() {
        super("timecalc", "Duration Calculator", "Add or subtract durations", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_time_calculator, "Time Calculator"));
        ToolViewFactory.addLabel(box, PackRes.str("math", R.string.s_durations_as_ss_mm_ss_or_hh_mm_ss, "Durations as ss, mm:ss or hh:mm:ss."));
        EditText t1 = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_duration_1, "Duration 1"), InputType.TYPE_CLASS_DATETIME);
        t1.setText(PackRes.str("math", R.string.s_01_30_00, "01:30:00"));
        EditText t2 = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_duration_2, "Duration 2"), InputType.TYPE_CLASS_DATETIME);
        t2.setText(PackRes.str("math", R.string.s_00_45_00, "00:45:00"));
        TextView output = ToolViewFactory.makeOutput(box);
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton addBtn = ToolViewFactory.makeRowButton(row, PackRes.str("math", R.string.s_add, "Add"), 1f);
        MaterialButton subBtn = ToolViewFactory.makeRowButton(row, PackRes.str("math", R.string.s_subtract, "Subtract"), 1f);
        addBtn.setOnClickListener(v -> {
            try {
                long r = DateTime.parseDurationToSeconds(t1.getText().toString())
                        + DateTime.parseDurationToSeconds(t2.getText().toString());
                output.setText(DateTime.formatDuration(r) + "  (" + r + "s, " + new DecimalFormat("0.##").format(r / 60.0) + " min)");
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_check_format, "Check format"));
            }
        });
        subBtn.setOnClickListener(v -> {
            try {
                long r = DateTime.parseDurationToSeconds(t1.getText().toString())
                        - DateTime.parseDurationToSeconds(t2.getText().toString());
                output.setText(DateTime.formatDuration(r) + "  (" + r + "s)");
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_check_format, "Check format"));
            }
        });
        return box;
    }
}

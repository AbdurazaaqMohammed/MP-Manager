package io.github.abdurazaaqmohammed.packs.math;

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
        ToolViewFactory.addTitle(box, "Time Calculator");
        ToolViewFactory.addLabel(box, "Durations as ss, mm:ss or hh:mm:ss.");
        EditText t1 = ToolViewFactory.makeInput(box, "Duration 1", InputType.TYPE_CLASS_DATETIME);
        t1.setText("01:30:00");
        EditText t2 = ToolViewFactory.makeInput(box, "Duration 2", InputType.TYPE_CLASS_DATETIME);
        t2.setText("00:45:00");
        TextView output = ToolViewFactory.makeOutput(box);
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton addBtn = ToolViewFactory.makeRowButton(row, "Add", 1f);
        MaterialButton subBtn = ToolViewFactory.makeRowButton(row, "Subtract", 1f);
        addBtn.setOnClickListener(v -> {
            try {
                long r = DateTime.parseDurationToSeconds(t1.getText().toString())
                        + DateTime.parseDurationToSeconds(t2.getText().toString());
                output.setText(DateTime.formatDuration(r) + "  (" + r + "s, " + new DecimalFormat("0.##").format(r / 60.0) + " min)");
            } catch (Exception e) {
                output.setText("Check format");
            }
        });
        subBtn.setOnClickListener(v -> {
            try {
                long r = DateTime.parseDurationToSeconds(t1.getText().toString())
                        - DateTime.parseDurationToSeconds(t2.getText().toString());
                output.setText(DateTime.formatDuration(r) + "  (" + r + "s)");
            } catch (Exception e) {
                output.setText("Check format");
            }
        });
        return box;
    }
}

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
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildPace().
 */
public class PaceTool extends BaseToolPlugin {

    public PaceTool() {
        super("pace", "Pace Calculator", "Running pace and speed", ToolCategories.MATH);
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

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Pace Calculator");
        EditText distInput = ToolViewFactory.makeInput(box, "Distance in km",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        distInput.setText("5");
        LinearLayout row = ToolViewFactory.makeRow(box);
        EditText hInput = ToolViewFactory.makeRowInput(row, "hh", InputType.TYPE_CLASS_NUMBER, 1f, "0");
        EditText mInput = ToolViewFactory.makeRowInput(row, "mm", InputType.TYPE_CLASS_NUMBER, 1f, "25");
        EditText sInput = ToolViewFactory.makeRowInput(row, "ss", InputType.TYPE_CLASS_NUMBER, 1f, "0");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Calculate");
        goBtn.setOnClickListener(v -> {
            try {
                double dist = Double.parseDouble(distInput.getText().toString());
                long secs = parseLongSafe(hInput.getText().toString()) * 3600 + parseLongSafe(mInput.getText().toString()) * 60 + parseLongSafe(sInput.getText().toString());
                if (dist <= 0 || secs <= 0) {
                    output.setText("Enter distance and time");
                    return;
                }
                double secPerKm = secs / dist;
                long totalPaceSecs = Math.round(secPerKm);
                long pm = totalPaceSecs / 60;
                long ps = totalPaceSecs % 60;
                double kmh = dist / (secs / 3600.0);
                String b = "Pace " + pm + ":" + String.format(Locale.US, "%02d", ps) + " per km\n"
                        + "Speed " + new DecimalFormat("0.0").format(kmh) + " km/h\n"
                        + "10K in " + DateTime.formatDuration(Math.round(secPerKm * 10)) + "  Marathon in " + DateTime.formatDuration(Math.round(secPerKm * 42.195));
                output.setText(b);
            } catch (Exception e) {
                output.setText("Check inputs");
            }
        });
        return box;
    }
}

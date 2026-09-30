package io.github.abdurazaaqmohammed.packs.time;

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

/**
 * Extraction of ToolRunnerActivity.buildDateDiff().
 */
public class DateDiffTool extends BaseToolPlugin {

    public DateDiffTool() {
        super("datediff", "Date Difference", "Days and age between dates", ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Date Calculator");
        ToolViewFactory.addLabel(box, "Use yyyy-MM-dd, for example 2024-01-31.");
        EditText d1 = ToolViewFactory.makeInput(box, "Start date", InputType.TYPE_CLASS_DATETIME);
        EditText d2 = ToolViewFactory.makeInput(box, "End date", InputType.TYPE_CLASS_DATETIME);
        String today = DateTime.todayIso();
        d1.setText(today);
        d2.setText(today);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton calcBtn = ToolViewFactory.makeButton(box, "Calculate difference");
        calcBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.diff(d1.getText().toString(), d2.getText().toString()));
            } catch (Exception e) {
                output.setText("Use yyyy-MM-dd");
            }
        });
        MaterialButton ageBtn = ToolViewFactory.makeButton(box, "Age from start date to today");
        ageBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.ageFrom(d1.getText().toString()));
            } catch (IllegalArgumentException e) {
                output.setText("Birth date is in the future");
            } catch (Exception e) {
                output.setText("Use yyyy-MM-dd");
            }
        });
        return box;
    }
}

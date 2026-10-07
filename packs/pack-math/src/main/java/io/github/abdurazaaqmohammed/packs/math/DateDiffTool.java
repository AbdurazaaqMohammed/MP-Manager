package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
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
        super("datediff", "Date Difference", "Days and age between dates", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_date_calculator, "Date Calculator"));
        EditText d1 = ToolViewFactory.makeDateField(box, PackRes.str("math", R.string.s_start_date, "Start date"));
        EditText d2 = ToolViewFactory.makeDateField(box, PackRes.str("math", R.string.s_end_date, "End date"));
        String today = DateTime.todayIso();
        d1.setText(today);
        d2.setText(today);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton calcBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate_difference, "Calculate difference"));
        calcBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.diff(d1.getText().toString(), d2.getText().toString()));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_use_yyyy_mm_dd, "Use yyyy-MM-dd"));
            }
        });
        MaterialButton ageBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_age_from_start_date_to_today, "Age from start date to today"));
        ageBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.ageFrom(d1.getText().toString()));
            } catch (IllegalArgumentException e) {
                output.setText(PackRes.str("math", R.string.s_birth_date_is_in_the_future, "Birth date is in the future"));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_use_yyyy_mm_dd, "Use yyyy-MM-dd"));
            }
        });
        return box;
    }
}

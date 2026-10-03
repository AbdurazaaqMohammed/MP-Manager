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
        super("datediff", R.string.datediff_title, R.string.datediff_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.datediff_date_calculator));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.datediff_use_yyyy_mm_dd_for_example_202));
        EditText d1 = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.datediff_start_date), InputType.TYPE_CLASS_DATETIME);
        EditText d2 = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.datediff_end_date), InputType.TYPE_CLASS_DATETIME);
        String today = DateTime.todayIso();
        d1.setText(today);
        d2.setText(today);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton calcBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.datediff_calculate_difference));
        calcBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.diff(context, d1.getText().toString(), d2.getText().toString()));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.agecalc_use_yyyy_mm_dd_2));
            }
        });
        MaterialButton ageBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.datediff_age_from_start_date_to_today));
        ageBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.ageFrom(context, d1.getText().toString()));
            } catch (IllegalArgumentException e) {
                output.setText(output.getContext().getString(R.string.agecalc_birth_date_is_in_the_future));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.agecalc_use_yyyy_mm_dd_2));
            }
        });
        return box;
    }
}

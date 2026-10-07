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

/**
 * Extraction of ToolRunnerActivity.buildDateAdd().
 */
public class DateAddTool extends BaseToolPlugin {

    public DateAddTool() {
        super("dateadd", "Date Adder", "Add or subtract days", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_date_adder, "Date Adder"));
        EditText dateInput = ToolViewFactory.makeDateField(box, PackRes.str("math", R.string.s_start_date, "Start date"));
        dateInput.setText(DateTime.todayIso());
        EditText daysInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_days_to_add_negative_subtracts, "Days to add (negative subtracts)"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        daysInput.setText(PackRes.str("math", R.string.s_30, "30"));
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(daysInput.getText().toString().trim());
                output.setText(DateTime.addDays(dateInput.getText().toString(), n));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_check_inputs, "Check inputs"));
            }
        });
        return box;
    }
}

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
 * Extraction of ToolRunnerActivity.buildDateAdd().
 */
public class DateAddTool extends BaseToolPlugin {

    public DateAddTool() {
        super("dateadd", R.string.dateadd_title, R.string.dateadd_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.dateadd_date_adder));
        EditText dateInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.dateadd_start_yyyy_mm_dd), InputType.TYPE_CLASS_DATETIME);
        dateInput.setText(DateTime.todayIso());
        EditText daysInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.dateadd_days_to_add_negative_subtracts),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        daysInput.setText("30");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.agecalc_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(daysInput.getText().toString().trim());
                output.setText(DateTime.addDays(context, dateInput.getText().toString(), n));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.dateadd_check_inputs));
            }
        });
        return box;
    }
}

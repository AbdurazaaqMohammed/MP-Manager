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
        ToolViewFactory.addTitle(box, "Date Adder");
        EditText dateInput = ToolViewFactory.makeDateField(box, "Start date");
        dateInput.setText(DateTime.todayIso());
        EditText daysInput = ToolViewFactory.makeInput(box, "Days to add (negative subtracts)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        daysInput.setText("30");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Calculate");
        goBtn.setOnClickListener(v -> {
            try {
                int n = Integer.parseInt(daysInput.getText().toString().trim());
                output.setText(DateTime.addDays(dateInput.getText().toString(), n));
            } catch (Exception e) {
                output.setText("Check inputs");
            }
        });
        return box;
    }
}

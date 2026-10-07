package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.domain.math.Money;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildPercent().
 */
public class PercentTool extends BaseToolPlugin {

    public PercentTool() {
        super("percent", "Percentage Calculator", "Percents, change, shares", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_percentage_calculator, "Percentage Calculator"));
        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton m1 = new RadioButton(context);
        m1.setId(View.generateViewId());
        m1.setText(PackRes.str("math", R.string.s_x_percent_of_y, "X percent of Y"));
        RadioButton m2 = new RadioButton(context);
        m2.setId(View.generateViewId());
        m2.setText(PackRes.str("math", R.string.s_x_is_what_percent_of_y, "X is what percent of Y"));
        RadioButton m3 = new RadioButton(context);
        m3.setId(View.generateViewId());
        m3.setText(PackRes.str("math", R.string.s_percent_change_from_x_to_y, "Percent change from X to Y"));
        modeGroup.addView(m1);
        modeGroup.addView(m2);
        modeGroup.addView(m3);
        modeGroup.check(m1.getId());
        box.addView(modeGroup);
        EditText xInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_x_3, "X"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText yInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_y, "Y"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TextView output = ToolViewFactory.makeOutput(box);
        final int id1 = m1.getId();
        final int id2 = m2.getId();
        Runnable compute = () -> {
            try {
                double x = Double.parseDouble(xInput.getText().toString());
                double y = Double.parseDouble(yInput.getText().toString());
                int mode = modeGroup.getCheckedRadioButtonId();
                DecimalFormat df = new DecimalFormat("0.##");
                if (mode == id1) {
                    output.setText(df.format(Money.percentOf(x, y)));
                } else if (mode == id2) {
                    if (y == 0) {
                        output.setText(PackRes.str("math", R.string.s_y_must_not_be_zero, "Y must not be zero"));
                        return;
                    }
                    output.setText(df.format(Money.whatPercent(x, y)) + "%");
                } else {
                    if (x == 0) {
                        output.setText(PackRes.str("math", R.string.s_x_must_not_be_zero, "X must not be zero"));
                        return;
                    }
                    output.setText(df.format(Money.percentChange(x, y)) + "%");
                }
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_enter_x_and_y, "Enter X and Y"));
            }
        };
        final Runnable computeRef = compute;
        modeGroup.setOnCheckedChangeListener((g, checkedId) -> computeRef.run());
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                computeRef.run();
            }
            public void afterTextChanged(Editable s) {
            }
        };
        xInput.addTextChangedListener(watcher);
        yInput.addTextChangedListener(watcher);
        compute.run();
        return box;
    }
}

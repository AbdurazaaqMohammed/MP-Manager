package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Money;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildGst().
 */
public class GstTool extends BaseToolPlugin {

    public GstTool() {
        super("gst", "Tax Calculator", "Add or remove GST, VAT", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_tax_calculator, "Tax Calculator"));
        EditText amountInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_amount, "Amount"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText rateInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_tax_percent, "Tax percent"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rateInput.setText(PackRes.str("math", R.string.s_18, "18"));
        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton addBtn2 = new RadioButton(context);
        addBtn2.setId(View.generateViewId());
        addBtn2.setText(PackRes.str("math", R.string.s_add_tax, "Add tax"));
        RadioButton remBtn = new RadioButton(context);
        remBtn.setId(View.generateViewId());
        remBtn.setText(PackRes.str("math", R.string.s_remove_tax, "Remove tax"));
        modeGroup.addView(addBtn2);
        modeGroup.addView(remBtn);
        modeGroup.check(addBtn2.getId());
        box.addView(modeGroup);
        TextView output = ToolViewFactory.makeOutput(box);
        final int addId = addBtn2.getId();
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                double amount = Double.parseDouble(amountInput.getText().toString());
                double rate = Double.parseDouble(rateInput.getText().toString());
                DecimalFormat df = new DecimalFormat("0.00");
                if (modeGroup.getCheckedRadioButtonId() == addId) {
                    double[] r = Money.gstAdd(amount, rate);
                    output.setText("Tax " + df.format(r[0]) + "  Total " + df.format(r[1]));
                } else {
                    double[] r = Money.gstRemove(amount, rate);
                    output.setText("Net " + df.format(r[0]) + "  Tax " + df.format(r[1]));
                }
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_check_inputs, "Check inputs"));
            }
        });
        return box;
    }
}

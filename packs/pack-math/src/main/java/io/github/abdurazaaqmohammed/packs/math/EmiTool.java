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

import io.github.abdurazaaqmohammed.domain.math.Money;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildEmi().
 */
public class EmiTool extends BaseToolPlugin {

    public EmiTool() {
        super("emi", "EMI Calculator", "Loans, interest, totals", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_emi_calculator, "EMI Calculator"));
        EditText pInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_loan_amount, "Loan amount"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText rInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_annual_interest_percent, "Annual interest percent"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText nInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_months, "Months"), InputType.TYPE_CLASS_NUMBER);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                double p = Double.parseDouble(pInput.getText().toString());
                double annual = Double.parseDouble(rInput.getText().toString());
                int n = Integer.parseInt(nInput.getText().toString().trim());
                double[] r = Money.emi(p, annual, n);
                DecimalFormat df = new DecimalFormat("0.00");
                output.setText("EMI " + df.format(r[0]) + "  Total " + df.format(r[1]) + "  Interest " + df.format(r[2]));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_invalid_input, "Invalid input"));
            }
        });
        return box;
    }
}

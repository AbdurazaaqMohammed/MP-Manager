package io.github.abdurazaaqmohammed.packs.math;

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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildSavings().
 */
public class SavingsTool extends BaseToolPlugin {

    public SavingsTool() {
        super("savings", R.string.savings_title, R.string.savings_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.savings_savings_goal));
        EditText targetInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.savings_target_amount),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText savedInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.savings_already_saved),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText monthlyInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.savings_monthly_deposit),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText rateInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.savings_annual_percent_0_for_none),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rateInput.setText("0");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.savings_plan));
        goBtn.setOnClickListener(v -> {
            try {
                double target = Double.parseDouble(targetInput.getText().toString());
                double balance = savedInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(savedInput.getText().toString());
                double monthly = Double.parseDouble(monthlyInput.getText().toString());
                double annual = rateInput.getText().toString().isEmpty() ? 0 : Double.parseDouble(rateInput.getText().toString());
                if (monthly <= 0) {
                    output.setText(output.getContext().getString(R.string.savings_monthly_deposit_must_be_positi));
                    return;
                }
                int months = Money.savingsMonths(target, balance, monthly, annual);
                if (months < 0) {
                    output.setText(output.getContext().getString(R.string.savings_goal_unreachable_in_100_years));
                    return;
                }
                // Re-run the projection for the display balance.
                double mr = annual / 1200.0;
                double projected = balance;
                for (int i = 0; i < months; i++) {
                    projected += monthly;
                    projected *= (1 + mr);
                }
                Calendar c = Calendar.getInstance();
                c.add(Calendar.MONTH, months);
                SimpleDateFormat f = new SimpleDateFormat("MMM yyyy", Locale.US);
                output.setText(output.getContext().getString(R.string.savings_months_around, f.format(c.getTime()),
                        new DecimalFormat("0.00").format(projected)));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.compound_check_inputs));
            }
        });
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Money;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildCompound().
 */
public class CompoundTool extends BaseToolPlugin {

    public CompoundTool() {
        super("compound", R.string.compound_title, R.string.compound_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.compound_interest_calculator));
        EditText pInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.compound_initial_amount),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        pInput.setText("10000");
        EditText rInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.compound_annual_percent),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rInput.setText("8");
        EditText yInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.compound_years),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        yInput.setText("5");
        String[] freqs = new String[]{context.getString(R.string.compound_yearly), context.getString(R.string.compound_half_yearly), context.getString(R.string.compound_quarterly), context.getString(R.string.compound_monthly)};
        int[] perYear = new int[]{1, 2, 4, 12};
        Spinner freqSpinner = new Spinner(context);
        ArrayAdapter<String> freqAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, freqs);
        freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        freqSpinner.setAdapter(freqAdapter);
        freqSpinner.setSelection(3);
        box.addView(freqSpinner);
        EditText sipInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.compound_monthly_deposit_0_for_none),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sipInput.setText("0");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.compound_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                double p = Double.parseDouble(pInput.getText().toString());
                double annual = Double.parseDouble(rInput.getText().toString()) / 100.0;
                double years = Double.parseDouble(yInput.getText().toString());
                int n = perYear[freqSpinner.getSelectedItemPosition()];
                double lump = Money.compound(p, annual, years, n);
                double monthly = Double.parseDouble(sipInput.getText().toString());
                double sipFv = monthly > 0 ? Money.sipFutureValue(monthly, annual, years) : 0;
                DecimalFormat df = new DecimalFormat("0.00");
                output.setText(output.getContext().getString(R.string.compound_lump_sum_grows_to) + df.format(lump)
                        + output.getContext().getString(R.string.compound_deposits_grow_to) + df.format(sipFv)
                        + output.getContext().getString(R.string.compound_total) + df.format(lump + sipFv));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.compound_check_inputs));
            }
        });
        return box;
    }
}

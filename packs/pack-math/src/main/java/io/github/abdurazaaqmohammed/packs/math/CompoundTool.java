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
        super("compound", "Interest Calculator", "Compound growth, SIP", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Interest Calculator");
        EditText pInput = ToolViewFactory.makeInput(box, "Initial amount",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        pInput.setText("10000");
        EditText rInput = ToolViewFactory.makeInput(box, "Annual percent",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rInput.setText("8");
        EditText yInput = ToolViewFactory.makeInput(box, "Years",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        yInput.setText("5");
        String[] freqs = new String[]{"Yearly", "Half-yearly", "Quarterly", "Monthly"};
        int[] perYear = new int[]{1, 2, 4, 12};
        Spinner freqSpinner = new Spinner(context);
        ArrayAdapter<String> freqAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, freqs);
        freqAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        freqSpinner.setAdapter(freqAdapter);
        freqSpinner.setSelection(3);
        box.addView(freqSpinner);
        EditText sipInput = ToolViewFactory.makeInput(box, "Monthly deposit, 0 for none",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        sipInput.setText("0");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Calculate");
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
                output.setText("Lump sum grows to " + df.format(lump) + "\nDeposits grow to " + df.format(sipFv) + "\nTotal " + df.format(lump + sipFv));
            } catch (Exception e) {
                output.setText("Check inputs");
            }
        });
        return box;
    }
}

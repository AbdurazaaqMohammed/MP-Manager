package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Primes;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildFraction().
 */
public class FractionTool extends BaseToolPlugin {

    public FractionTool() {
        super("fraction", "Fraction Calc", "Simplify fractions", ToolCategories.MATH);
    }

    private static EditText cell(Context context, LinearLayout row, String def) {
        EditText e = ToolViewFactory.makeRowInput(row, null,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, 1f, def);
        e.setGravity(Gravity.CENTER);
        return e;
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Fraction Calculator");
        LinearLayout row1 = ToolViewFactory.makeRow(box);
        EditText aInput = cell(context, row1, "1");
        TextView slash1 = new TextView(context);
        slash1.setText("—");
        slash1.setGravity(Gravity.CENTER);
        slash1.setTextSize(20);
        row1.addView(slash1, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));
        EditText bInput = cell(context, row1, "2");
        Spinner opSpinner = new Spinner(context);
        ArrayAdapter<String> opAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                new String[]{"+", "-", "x", "div"});
        opAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        opSpinner.setAdapter(opAdapter);
        box.addView(opSpinner);
        LinearLayout row2 = ToolViewFactory.makeRow(box);
        EditText cInput = cell(context, row2, "1");
        TextView slash2 = new TextView(context);
        slash2.setText("—");
        slash2.setGravity(Gravity.CENTER);
        slash2.setTextSize(20);
        row2.addView(slash2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.4f));
        EditText dInput = cell(context, row2, "3");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Calculate");
        goBtn.setOnClickListener(v -> {
            try {
                long a = Long.parseLong(aInput.getText().toString().trim());
                long b = Long.parseLong(bInput.getText().toString().trim());
                long c = Long.parseLong(cInput.getText().toString().trim());
                long d = Long.parseLong(dInput.getText().toString().trim());
                if (b == 0 || d == 0) {
                    output.setText("Denominator cannot be 0");
                    return;
                }
                long num;
                long den;
                int op = opSpinner.getSelectedItemPosition();
                if (op == 0) {
                    num = a * d + c * b;
                    den = b * d;
                } else if (op == 1) {
                    num = a * d - c * b;
                    den = b * d;
                } else if (op == 2) {
                    num = a * c;
                    den = b * d;
                } else {
                    if (c == 0) {
                        output.setText("Cannot divide by zero");
                        return;
                    }
                    num = a * d;
                    den = b * c;
                }
                if (den < 0) {
                    num = -num;
                    den = -den;
                }
                long g = Primes.gcd(num, den);
                num /= g;
                den /= g;
                DecimalFormat df = new DecimalFormat("0.####");
                output.setText(num + " / " + den + "  =  " + df.format((double) num / den));
            } catch (Exception e) {
                output.setText("Enter four integers");
            }
        });
        return box;
    }
}

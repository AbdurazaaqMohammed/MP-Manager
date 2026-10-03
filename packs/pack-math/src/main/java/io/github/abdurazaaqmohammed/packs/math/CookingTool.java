package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildCooking().
 */
public class CookingTool extends BaseToolPlugin {

    public CookingTool() {
        super("cooking", R.string.cooking_title, R.string.cooking_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.cooking_cooking_converter));
        String[] ingredients = new String[]{context.getString(R.string.cooking_water), context.getString(R.string.cooking_milk), context.getString(R.string.cooking_flour), context.getString(R.string.cooking_sugar), context.getString(R.string.cooking_butter), context.getString(R.string.cooking_rice), context.getString(R.string.cooking_oats), context.getString(R.string.cooking_oil)};
        double[] gramsPerCup = new double[]{236.0, 240.0, 120.0, 200.0, 227.0, 185.0, 90.0, 218.0};
        Spinner ingSpinner = new Spinner(context);
        ArrayAdapter<String> ingAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, ingredients);
        ingAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ingSpinner.setAdapter(ingAdapter);
        box.addView(ingSpinner);
        EditText cupsInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.cooking_cups),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        cupsInput.setText("1");
        EditText gramsInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.cooking_grams),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        final boolean[] syncing = new boolean[]{false};
        final Runnable compute = () -> {
            try {
                double gpc = gramsPerCup[ingSpinner.getSelectedItemPosition()];
                String cs = cupsInput.getText().toString().trim();
                if (!cs.isEmpty()) {
                    double grams = Double.parseDouble(cs) * gpc;
                    output.setText(new DecimalFormat("0.#").format(grams) + " g  (" + new DecimalFormat("0.#").format(grams / 28.3495) + " oz)");
                }
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.cooking_enter_cups_or_grams));
            }
        };
        ingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                compute.run();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        cupsInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncing[0] || !cupsInput.hasFocus()) {
                    compute.run();
                    return;
                }
                syncing[0] = true;
                try {
                    double gpc = gramsPerCup[ingSpinner.getSelectedItemPosition()];
                    String cs = s.toString().trim();
                    if (!cs.isEmpty()) {
                        double grams = Double.parseDouble(cs) * gpc;
                        gramsInput.setText(new DecimalFormat("0.##").format(grams));
                    } else {
                        gramsInput.setText("");
                    }
                } catch (Exception ignored) {
                }
                syncing[0] = false;
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        gramsInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncing[0] || !gramsInput.hasFocus()) {
                    return;
                }
                syncing[0] = true;
                try {
                    double gpc = gramsPerCup[ingSpinner.getSelectedItemPosition()];
                    String gs = s.toString().trim();
                    if (!gs.isEmpty()) {
                        double cups = Double.parseDouble(gs) / gpc;
                        cupsInput.setText(new DecimalFormat("0.##").format(cups));
                    } else {
                        cupsInput.setText("");
                    }
                } catch (Exception ignored) {
                }
                syncing[0] = false;
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
        return box;
    }
}

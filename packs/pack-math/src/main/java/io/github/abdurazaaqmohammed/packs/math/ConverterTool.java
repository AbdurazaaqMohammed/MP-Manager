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

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.UnitConverter;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildConverter().
 * Conversion math lives in domain.math.UnitConverter.
 */
public class ConverterTool extends BaseToolPlugin {

    public ConverterTool() {
        super("converter", R.string.converter_title, R.string.converter_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.converter_unit_converter));
        String[] categories = UnitConverter.categories();
        Spinner catSpinner = new Spinner(context);
        ArrayAdapter<String> catAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        catSpinner.setAdapter(catAdapter);
        box.addView(catSpinner);
        Spinner fromSpinner = new Spinner(context);
        box.addView(fromSpinner);
        Spinner toSpinner = new Spinner(context);
        box.addView(toSpinner);
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.converter_value),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.converter_result));
        catSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String[] units = UnitConverter.unitsForCategory(categories[position]);
                ArrayAdapter<String> a1 =
                        new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, units);
                a1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                fromSpinner.setAdapter(a1);
                ArrayAdapter<String> a2 =
                        new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, units);
                a2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                toSpinner.setAdapter(a2);
                if (units.length > 1) {
                    toSpinner.setSelection(1);
                }
                convert(categories[position], fromSpinner, toSpinner, input, output);
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String cat = (String) catSpinner.getSelectedItem();
                if (cat == null) cat = categories[0];
                convert(cat, fromSpinner, toSpinner, input, output);
            }
            public void afterTextChanged(Editable s) {
            }
        };
        input.addTextChangedListener(watcher);
        AdapterView.OnItemSelectedListener convertListener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String cat = (String) catSpinner.getSelectedItem();
                if (cat == null) cat = categories[0];
                convert(cat, fromSpinner, toSpinner, input, output);
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        fromSpinner.setOnItemSelectedListener(convertListener);
        toSpinner.setOnItemSelectedListener(convertListener);
        MaterialButton swapBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.converter_swap_units));
        swapBtn.setOnClickListener(v -> {
            int f = fromSpinner.getSelectedItemPosition();
            int t = toSpinner.getSelectedItemPosition();
            fromSpinner.setSelection(t);
            toSpinner.setSelection(f);
        });
        return box;
    }

    private void convert(String cat, Spinner from, Spinner to, EditText input, TextView output) {
        try {
            String s = input.getText().toString().trim();
            if (s.isEmpty()) {
                output.setText(output.getContext().getString(R.string.converter_result));
                return;
            }
            double v = Double.parseDouble(s);
            String f = from.getSelectedItem() == null ? "" : from.getSelectedItem().toString();
            String t = to.getSelectedItem() == null ? "" : to.getSelectedItem().toString();
            double r = UnitConverter.convert(cat, v, f, t);
            DecimalFormat df = new DecimalFormat("0.######");
            output.setText(df.format(v) + " " + f + " = " + df.format(r) + " " + t);
        } catch (Exception e) {
            output.setText(output.getContext().getString(R.string.converter_invalid_input));
        }
    }
}

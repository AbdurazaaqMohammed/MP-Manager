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

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildCurrency().
 */
public class CurrencyTool extends BaseToolPlugin {

    public CurrencyTool() {
        super("currency", "Currency Converter", "Convert with offline rates", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Currency Converter");
        ToolViewFactory.addLabel(box, "Indicative offline rates, base USD.");
        String[] codes = new String[]{"USD", "EUR", "GBP", "JPY", "INR", "CNY", "AED", "SAR", "PKR", "BDT", "CAD", "AUD"};
        double[] perUsd = new double[]{1.0, 0.92, 0.79, 149.5, 83.2, 7.24, 3.67, 3.75, 278.0, 117.0, 1.36, 1.52};
        Spinner fromCur = new Spinner(context);
        Spinner toCur = new Spinner(context);
        ArrayAdapter<String> curAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, codes);
        curAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromCur.setAdapter(curAdapter);
        toCur.setAdapter(curAdapter);
        toCur.setSelection(1);
        box.addView(fromCur);
        box.addView(toCur);
        EditText amount = ToolViewFactory.makeInput(box, "Amount",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setText("100");
        TextView output = ToolViewFactory.makeOutput(box);
        Runnable convert = () -> {
            try {
                double v = Double.parseDouble(amount.getText().toString().trim());
                int fi = fromCur.getSelectedItemPosition();
                int ti = toCur.getSelectedItemPosition();
                double usd = v / perUsd[fi];
                double result = usd * perUsd[ti];
                DecimalFormat df = new DecimalFormat("0.##");
                output.setText(df.format(v) + " " + codes[fi] + " = " + df.format(result) + " " + codes[ti]);
            } catch (Exception e) {
                output.setText("Enter amount");
            }
        };
        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                convert.run();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        fromCur.setOnItemSelectedListener(listener);
        toCur.setOnItemSelectedListener(listener);
        amount.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                convert.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        MaterialButton swapBtn = ToolViewFactory.makeButton(box, "Swap");
        swapBtn.setOnClickListener(v -> {
            int f = fromCur.getSelectedItemPosition();
            int t = toCur.getSelectedItemPosition();
            fromCur.setSelection(t);
            toCur.setSelection(f);
        });
        convert.run();
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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

import io.github.abdurazaaqmohammed.domain.text.TextCodecs;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Extraction of ToolRunnerActivity.buildBaseConv().
 */
public class BaseConvTool extends BaseToolPlugin {

    public BaseConvTool() {
        super("baseconv", "Base Converter", "Binary, octal, decimal, hex", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_base_converter, "Base Converter"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_number, "Number"), InputType.TYPE_CLASS_TEXT);
        input.setText(PackRes.str("math", R.string.s_255, "255"));
        String[] bases = new String[]{"Binary (2)", "Octal (8)", "Decimal (10)", "Hex (16)"};
        int[] radix = new int[]{2, 8, 10, 16};
        Spinner fromBase = new Spinner(context);
        ArrayAdapter<String> baseAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, bases);
        baseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromBase.setAdapter(baseAdapter);
        fromBase.setSelection(2);
        box.addView(fromBase);
        TextView output = ToolViewFactory.makeOutput(box);
        Runnable compute = () -> {
            try {
                output.setText(TextCodecs.baseConvert(
                        input.getText().toString(), radix[fromBase.getSelectedItemPosition()]));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_invalid_for_selected_base, "Invalid for selected base"));
            }
        };
        final Runnable computeRef = compute;
        fromBase.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                computeRef.run();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                computeRef.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
        return box;
    }
}

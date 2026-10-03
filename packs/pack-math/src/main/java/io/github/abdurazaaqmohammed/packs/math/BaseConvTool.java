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

import io.github.abdurazaaqmohammed.domain.text.TextCodecs;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Extraction of ToolRunnerActivity.buildBaseConv().
 */
public class BaseConvTool extends BaseToolPlugin {

    public BaseConvTool() {
        super("baseconv", R.string.baseconv_title, R.string.baseconv_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.baseconv_base_converter));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.baseconv_number), InputType.TYPE_CLASS_TEXT);
        input.setText("255");
        String[] bases = new String[]{context.getString(R.string.baseconv_binary_2), context.getString(R.string.baseconv_octal_8), context.getString(R.string.baseconv_decimal_10), context.getString(R.string.baseconv_hex_16)};
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
                output.setText(output.getContext().getString(R.string.baseconv_invalid_for_selected_base));
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

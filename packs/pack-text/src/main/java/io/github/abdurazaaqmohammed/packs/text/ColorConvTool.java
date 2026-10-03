package io.github.abdurazaaqmohammed.packs.text;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.Locale;
import java.util.Random;

/**
 * Extraction of ToolRunnerActivity.buildColorConv().
 */
public class ColorConvTool extends BaseToolPlugin {

    public ColorConvTool() {
        super("colorconv", R.string.colorconv_title, R.string.colorconv_sub,  ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.colorconv_color_converter));
        EditText hexInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.colorconv_hex_e_g_1b73e8), InputType.TYPE_CLASS_TEXT);
        hexInput.setText("#1B73E8");
        View swatch = new View(context);
        box.addView(swatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 80)));
        TextView output = ToolViewFactory.makeOutput(box);
        final Runnable compute = () -> {
            try {
                String h = hexInput.getText().toString().trim().replace("#", "");
                if (h.length() == 3) {
                    h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
                }
                int color = Color.parseColor("#" + h);
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                float[] hsv = new float[3];
                Color.RGBToHSV(r, g, b, hsv);
                swatch.setBackgroundColor(color);
                output.setText(output.getContext().getString(R.string.colorconv_rgb_line, r, g, b)
                        + "\n"
                        + output.getContext().getString(R.string.colorconv_hsl_line,
                                Math.round(hsv[0]), Math.round(hsv[1] * 100), Math.round(hsv[2] * 100))
                        + "\n"
                        + output.getContext().getString(R.string.colorconv_hex_line,
                                String.format(Locale.US, "%02X%02X%02X", r, g, b)));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.colorconv_enter_a_valid_hex_color));
            }
        };
        hexInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton randomBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.colorconv_random), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.colorconv_copy), 1f);
        randomBtn.setOnClickListener(v -> {
            Random r = new Random();
            hexInput.setText(String.format(Locale.US, "#%02X%02X%02X", r.nextInt(256), r.nextInt(256), r.nextInt(256)));
        });
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, context.getString(R.string.colorconv_color), output.getText().toString()));
        return box;
    }
}

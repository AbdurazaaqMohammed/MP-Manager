package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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
        super("colorconv", "Color Converter", "HEX, RGB, HSL", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_color_converter, "Color Converter"));
        EditText hexInput = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_hex_e_g_1b73e8, "HEX, e.g. #1B73E8"), InputType.TYPE_CLASS_TEXT);
        hexInput.setText(PackRes.str("text", R.string.s_1b73e8, "#1B73E8"));
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
                String sb = "RGB " + r + ", " + g + ", " + b + "\n"
                        + "HSL " + Math.round(hsv[0]) + ", " + Math.round(hsv[1] * 100) + "%, " + Math.round(hsv[2] * 100) + "%\n"
                        + "HEX #" + String.format(Locale.US, "%02X%02X%02X", r, g, b);
                output.setText(sb);
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_enter_a_valid_hex_color, "Enter a valid HEX color"));
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
        MaterialButton randomBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_random, "Random"), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_copy, "Copy"), 1f);
        randomBtn.setOnClickListener(v -> {
            Random r = new Random();
            hexInput.setText(String.format(Locale.US, "#%02X%02X%02X", r.nextInt(256), r.nextInt(256), r.nextInt(256)));
        });
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, PackRes.str("text", R.string.s_color, "color"), output.getText().toString()));
        return box;
    }
}

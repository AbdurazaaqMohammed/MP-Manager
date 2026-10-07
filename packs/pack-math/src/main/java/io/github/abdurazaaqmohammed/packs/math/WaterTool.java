package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;

import io.github.abdurazaaqmohammed.domain.math.DateTime;
import io.github.abdurazaaqmohammed.domain.math.Health;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildWater().
 */
public class WaterTool extends BaseToolPlugin {

    public WaterTool() {
        super("water", "Water Tracker", "Log daily water intake", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_water_tracker, "Water Tracker"));
        EditText weightInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_weight_in_kg_for_target, "Weight in kg for target"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightInput.setText(PackRes.str("math", R.string.s_70, "70"));
        TextView targetText = ToolViewFactory.makeOutput(box);
        TextView todayText = new TextView(context);
        todayText.setTextSize(40);
        todayText.setGravity(Gravity.CENTER);
        todayText.setTextColor(MaterialColors.getColor(context,
                com.google.android.material.R.attr.colorPrimary, Color.BLACK));
        box.addView(todayText);
        final String todayKey = DateTime.todayIso();
        final int[] drunk = new int[]{0};
        try {
            drunk[0] = context.getSharedPreferences("tools", Context.MODE_PRIVATE).getInt("water_" + todayKey, 0);
        } catch (Exception ignored) {
        }
        final Runnable render = () -> {
            try {
                double w = Double.parseDouble(weightInput.getText().toString());
                int target = Health.waterTargetMl(w);
                targetText.setText("Target " + target + " ml");
                todayText.setText(drunk[0] + " ml");
            } catch (Exception e) {
                targetText.setText(PackRes.str("math", R.string.s_enter_weight, "Enter weight"));
            }
        };
        render.run();
        weightInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton add250 = ToolViewFactory.makeRowButton(row, PackRes.str("math", R.string.s_250, "+250"), 1f);
        MaterialButton add500 = ToolViewFactory.makeRowButton(row, PackRes.str("math", R.string.s_500, "+500"), 1f);
        MaterialButton resetBtn = ToolViewFactory.makeRowButton(row, PackRes.str("math", R.string.s_reset, "Reset"), 1f);
        final Runnable persist = () -> {
            try {
                context.getSharedPreferences("tools", Context.MODE_PRIVATE).edit().putInt("water_" + todayKey, drunk[0]).apply();
            } catch (Exception ignored) {
            }
        };
        add250.setOnClickListener(v -> {
            drunk[0] += 250;
            todayText.setText(drunk[0] + " ml");
            persist.run();
            ToolViewFactory.vibrateTick(context);
        });
        add500.setOnClickListener(v -> {
            drunk[0] += 500;
            todayText.setText(drunk[0] + " ml");
            persist.run();
            ToolViewFactory.vibrateTick(context);
        });
        resetBtn.setOnClickListener(v -> {
            drunk[0] = 0;
            todayText.setText(PackRes.str("math", R.string.s_0_ml, "0 ml"));
            persist.run();
        });
        return box;
    }
}

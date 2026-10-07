package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Health;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildBmi().
 */
public class BmiTool extends BaseToolPlugin {

    public BmiTool() {
        super("bmi", "BMI Calculator", "Calculate body mass index", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_bmi_calculator, "BMI Calculator"));
        EditText heightInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_height_in_cm, "Height in cm"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText weightInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_weight_in_kg, "Weight in kg"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("math", R.string.s_enter_height_and_weight, "Enter height and weight"));
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                double h = Double.parseDouble(heightInput.getText().toString());
                double w = Double.parseDouble(weightInput.getText().toString());
                if (h <= 0 || w <= 0) {
                    output.setText(PackRes.str("math", R.string.s_height_and_weight_must_be_above_zero, "Height and weight must be above zero"));
                    return;
                }
                double bmi = Health.bmi(w, h);
                output.setText("BMI " + new DecimalFormat("0.0").format(bmi) + "  " + Health.bmiCategory(bmi));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_invalid_input, "Invalid input"));
            }
        });
        return box;
    }
}

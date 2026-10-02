package io.github.abdurazaaqmohammed.packs.math;

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
        ToolViewFactory.addTitle(box, "BMI Calculator");
        EditText heightInput = ToolViewFactory.makeInput(box, "Height in cm",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText weightInput = ToolViewFactory.makeInput(box, "Weight in kg",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText("Enter height and weight");
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Calculate");
        goBtn.setOnClickListener(v -> {
            try {
                double h = Double.parseDouble(heightInput.getText().toString());
                double w = Double.parseDouble(weightInput.getText().toString());
                if (h <= 0 || w <= 0) {
                    output.setText("Height and weight must be above zero");
                    return;
                }
                double bmi = Health.bmi(w, h);
                output.setText("BMI " + new DecimalFormat("0.0").format(bmi) + "  " + Health.bmiCategory(bmi));
            } catch (Exception e) {
                output.setText("Invalid input");
            }
        });
        return box;
    }
}

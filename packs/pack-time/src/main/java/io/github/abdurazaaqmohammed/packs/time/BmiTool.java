package io.github.abdurazaaqmohammed.packs.time;

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
        super("bmi", R.string.bmi_title, R.string.bmi_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.bmi_bmi_calculator));
        EditText heightInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bmi_height_in_cm),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText weightInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bmi_weight_in_kg),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.bmi_enter_height_and_weight));
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.agecalc_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                double h = Double.parseDouble(heightInput.getText().toString());
                double w = Double.parseDouble(weightInput.getText().toString());
                if (h <= 0 || w <= 0) {
                    output.setText(output.getContext().getString(R.string.bmi_height_and_weight_must_be_abov));
                    return;
                }
                double bmi = Health.bmi(w, h);
                output.setText(output.getContext().getString(R.string.bmi_bmi) + new DecimalFormat("0.0").format(bmi) + "  " + Health.bmiCategory(context, bmi));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.bmi_invalid_input));
            }
        });
        return box;
    }
}

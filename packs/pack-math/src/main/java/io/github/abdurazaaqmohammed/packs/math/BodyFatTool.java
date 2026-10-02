package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Health;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildBodyFat().
 */
public class BodyFatTool extends BaseToolPlugin {

    public BodyFatTool() {
        super("bodyfat", "Body Fat Estimator", "Estimate with US Navy method", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Body Fat Estimator");
        ToolViewFactory.addLabel(box, "US Navy method, measurements in cm.");
        RadioGroup genderGroup = new RadioGroup(context);
        genderGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton maleBtn = new RadioButton(context);
        maleBtn.setId(View.generateViewId());
        maleBtn.setText("Male");
        RadioButton femaleBtn = new RadioButton(context);
        femaleBtn.setId(View.generateViewId());
        femaleBtn.setText("Female");
        genderGroup.addView(maleBtn);
        genderGroup.addView(femaleBtn);
        genderGroup.check(maleBtn.getId());
        box.addView(genderGroup);
        EditText waistInput = ToolViewFactory.makeInput(box, "Waist cm",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText neckInput = ToolViewFactory.makeInput(box, "Neck cm",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText heightInput = ToolViewFactory.makeInput(box, "Height cm",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText hipInput = ToolViewFactory.makeInput(box, "Hip cm (female only)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        final int maleId = maleBtn.getId();
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Calculate");
        goBtn.setOnClickListener(v -> {
            try {
                double waist = Double.parseDouble(waistInput.getText().toString());
                double neck = Double.parseDouble(neckInput.getText().toString());
                double height = Double.parseDouble(heightInput.getText().toString());
                boolean male = genderGroup.getCheckedRadioButtonId() == maleId;
                double bf;
                if (height <= 0 || neck <= 0) {
                    output.setText("Height and neck must be above zero");
                    return;
                }
                if (male) {
                    if (waist <= neck) {
                        output.setText("Waist must exceed neck");
                        return;
                    }
                    bf = Health.bodyFatMale(waist, neck, height);
                } else {
                    double hip = Double.parseDouble(hipInput.getText().toString());
                    if (waist + hip <= neck) {
                        output.setText("Waist plus hip must exceed neck");
                        return;
                    }
                    bf = Health.bodyFatFemale(waist, hip, neck, height);
                }
                output.setText(new DecimalFormat("0.0").format(bf) + "%  " + Health.bodyFatCategory(male, bf));
            } catch (Exception e) {
                output.setText("Check measurements");
            }
        });
        return box;
    }
}

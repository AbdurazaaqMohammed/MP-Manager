package io.github.abdurazaaqmohammed.packs.time;

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
        super("bodyfat", R.string.bodyfat_title, R.string.bodyfat_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.bodyfat_body_fat_estimator));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.bodyfat_us_navy_method_measurements_in));
        RadioGroup genderGroup = new RadioGroup(context);
        genderGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton maleBtn = new RadioButton(context);
        maleBtn.setId(View.generateViewId());
        maleBtn.setText(maleBtn.getContext().getString(R.string.bmr_male));
        RadioButton femaleBtn = new RadioButton(context);
        femaleBtn.setId(View.generateViewId());
        femaleBtn.setText(femaleBtn.getContext().getString(R.string.bmr_female));
        genderGroup.addView(maleBtn);
        genderGroup.addView(femaleBtn);
        genderGroup.check(maleBtn.getId());
        box.addView(genderGroup);
        EditText waistInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bodyfat_waist_cm),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText neckInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bodyfat_neck_cm),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText heightInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bodyfat_height_cm),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText hipInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bodyfat_hip_cm_female_only),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView output = ToolViewFactory.makeOutput(box);
        final int maleId = maleBtn.getId();
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.agecalc_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                double waist = Double.parseDouble(waistInput.getText().toString());
                double neck = Double.parseDouble(neckInput.getText().toString());
                double height = Double.parseDouble(heightInput.getText().toString());
                boolean male = genderGroup.getCheckedRadioButtonId() == maleId;
                double bf;
                if (height <= 0 || neck <= 0) {
                    output.setText(output.getContext().getString(R.string.bodyfat_height_and_neck_must_be_above_));
                    return;
                }
                if (male) {
                    if (waist <= neck) {
                        output.setText(output.getContext().getString(R.string.bodyfat_waist_must_exceed_neck));
                        return;
                    }
                    bf = Health.bodyFatMale(waist, neck, height);
                } else {
                    double hip = Double.parseDouble(hipInput.getText().toString());
                    if (waist + hip <= neck) {
                        output.setText(output.getContext().getString(R.string.bodyfat_waist_plus_hip_must_exceed_nec));
                        return;
                    }
                    bf = Health.bodyFatFemale(waist, hip, neck, height);
                }
                output.setText(new DecimalFormat("0.0").format(bf) + "%  " + Health.bodyFatCategory(context, male, bf));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.bodyfat_check_measurements));
            }
        });
        return box;
    }
}

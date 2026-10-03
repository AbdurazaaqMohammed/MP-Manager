package io.github.abdurazaaqmohammed.packs.time;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.Health;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildBmr().
 */
public class BmrTool extends BaseToolPlugin {

    public BmrTool() {
        super("bmr", R.string.bmr_title, R.string.bmr_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.bmr_calorie_calculator));
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
        EditText ageInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bmr_age_in_years), InputType.TYPE_CLASS_NUMBER);
        EditText heightInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bmi_height_in_cm),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText weightInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.bmi_weight_in_kg),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String[] activities = new String[]{context.getString(R.string.bmr_sedentary), context.getString(R.string.bmr_light), context.getString(R.string.bmr_moderate), context.getString(R.string.bmr_active), context.getString(R.string.bmr_extra_active)};
        double[] factors = new double[]{1.2, 1.375, 1.55, 1.725, 1.9};
        Spinner actSpinner = new Spinner(context);
        ArrayAdapter<String> actAdapter =
                new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, activities);
        actAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        actSpinner.setAdapter(actAdapter);
        actSpinner.setSelection(2);
        box.addView(actSpinner);
        TextView output = ToolViewFactory.makeOutput(box);
        final int maleId = maleBtn.getId();
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.agecalc_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                int age = Integer.parseInt(ageInput.getText().toString().trim());
                double h = Double.parseDouble(heightInput.getText().toString());
                double w = Double.parseDouble(weightInput.getText().toString());
                boolean male = genderGroup.getCheckedRadioButtonId() == maleId;
                double bmr = Health.bmr(male, age, h, w);
                double tdee = Health.tdee(bmr, factors[actSpinner.getSelectedItemPosition()]);
                DecimalFormat df = new DecimalFormat("0");
                output.setText(output.getContext().getString(R.string.bmr_result, df.format(bmr), df.format(tdee)));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.bmr_enter_age_height_and_weight));
            }
        });
        return box;
    }
}

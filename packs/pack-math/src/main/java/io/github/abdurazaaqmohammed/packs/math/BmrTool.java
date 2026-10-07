package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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
        super("bmr", "Calorie Calculator", "BMR, TDEE, calories", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_calorie_calculator, "Calorie Calculator"));
        RadioGroup genderGroup = new RadioGroup(context);
        genderGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton maleBtn = new RadioButton(context);
        maleBtn.setId(View.generateViewId());
        maleBtn.setText(PackRes.str("math", R.string.s_male, "Male"));
        RadioButton femaleBtn = new RadioButton(context);
        femaleBtn.setId(View.generateViewId());
        femaleBtn.setText(PackRes.str("math", R.string.s_female, "Female"));
        genderGroup.addView(maleBtn);
        genderGroup.addView(femaleBtn);
        genderGroup.check(maleBtn.getId());
        box.addView(genderGroup);
        EditText ageInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_age_in_years, "Age in years"), InputType.TYPE_CLASS_NUMBER);
        EditText heightInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_height_in_cm, "Height in cm"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText weightInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_weight_in_kg, "Weight in kg"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        String[] activities = new String[]{"Sedentary", "Light", "Moderate", "Active", "Extra active"};
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
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                int age = Integer.parseInt(ageInput.getText().toString().trim());
                double h = Double.parseDouble(heightInput.getText().toString());
                double w = Double.parseDouble(weightInput.getText().toString());
                boolean male = genderGroup.getCheckedRadioButtonId() == maleId;
                double bmr = Health.bmr(male, age, h, w);
                double tdee = Health.tdee(bmr, factors[actSpinner.getSelectedItemPosition()]);
                DecimalFormat df = new DecimalFormat("0");
                output.setText("BMR " + df.format(bmr) + " kcal  TDEE " + df.format(tdee) + " kcal");
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_enter_age_height_and_weight, "Enter age, height and weight"));
            }
        });
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.DateTime;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildAgeCalc().
 */
public class AgeCalcTool extends BaseToolPlugin {

    public AgeCalcTool() {
        super("agecalc", "Age Calculator", "Exact age and birthdays", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_age_calculator, "Age Calculator"));
        EditText birthInput = ToolViewFactory.makeDateField(box, PackRes.str("math", R.string.s_birth_date, "Birth date"));
        birthInput.setText(PackRes.str("math", R.string.s_2000_01_01, "2000-01-01"));
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.ageDetails(birthInput.getText().toString()));
            } catch (IllegalArgumentException e) {
                output.setText(PackRes.str("math", R.string.s_birth_date_is_in_the_future, "Birth date is in the future"));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_use_yyyy_mm_dd, "Use yyyy-MM-dd"));
            }
        });
        return box;
    }
}

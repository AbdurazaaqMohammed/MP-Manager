package io.github.abdurazaaqmohammed.packs.time;

import android.content.Context;
import android.text.InputType;
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
        super("agecalc", R.string.agecalc_title, R.string.agecalc_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.agecalc_age_calculator));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.agecalc_use_yyyy_mm_dd));
        EditText birthInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.agecalc_birth_date), InputType.TYPE_CLASS_DATETIME);
        birthInput.setText("2000-01-01");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.agecalc_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                output.setText(DateTime.ageDetails(context, birthInput.getText().toString()));
            } catch (IllegalArgumentException e) {
                output.setText(output.getContext().getString(R.string.agecalc_birth_date_is_in_the_future));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.agecalc_use_yyyy_mm_dd_2));
            }
        });
        return box;
    }
}

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

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildTriangle().
 */
public class TriangleTool extends BaseToolPlugin {

    public TriangleTool() {
        super("triangle", R.string.triangle_title, R.string.triangle_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.triangle_triangle_solver));
        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rightBtn = new RadioButton(context);
        rightBtn.setId(View.generateViewId());
        rightBtn.setText(rightBtn.getContext().getString(R.string.triangle_right_legs));
        RadioButton sssBtn = new RadioButton(context);
        sssBtn.setId(View.generateViewId());
        sssBtn.setText(sssBtn.getContext().getString(R.string.triangle_3_sides));
        modeGroup.addView(rightBtn);
        modeGroup.addView(sssBtn);
        modeGroup.check(rightBtn.getId());
        box.addView(modeGroup);
        EditText s1 = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.triangle_side_a),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s1.setText("3");
        EditText s2 = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.triangle_side_b),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s2.setText("4");
        EditText s3 = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.triangle_side_c_3_sides_mode_only),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s3.setText("5");
        TextView output = ToolViewFactory.makeOutput(box);
        final int rightId = rightBtn.getId();
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.ohm_solve));
        goBtn.setOnClickListener(v -> {
            try {
                DecimalFormat df = new DecimalFormat("0.##");
                if (modeGroup.getCheckedRadioButtonId() == rightId) {
                    double a = Double.parseDouble(s1.getText().toString());
                    double b = Double.parseDouble(s2.getText().toString());
                    double hyp = Math.sqrt(a * a + b * b);
                    double angA = Math.toDegrees(Math.atan2(a, b));
                    output.setText(output.getContext().getString(R.string.triangle_hypotenuse) + df.format(hyp)
                            + output.getContext().getString(R.string.triangle_angles) + df.format(angA)
                            + output.getContext().getString(R.string.triangle_and) + df.format(90 - angA)
                            + output.getContext().getString(R.string.triangle_deg_area) + df.format(a * b / 2)
                            + output.getContext().getString(R.string.triangle_perimeter) + df.format(a + b + hyp));
                } else {
                    double a = Double.parseDouble(s1.getText().toString());
                    double b = Double.parseDouble(s2.getText().toString());
                    double c = Double.parseDouble(s3.getText().toString());
                    if (a + b <= c || a + c <= b || b + c <= a) {
                        output.setText(output.getContext().getString(R.string.triangle_not_a_valid_triangle));
                        return;
                    }
                    double s = (a + b + c) / 2;
                    double area = Math.sqrt(s * (s - a) * (s - b) * (s - c));
                    double angA = Math.toDegrees(Math.acos((b * b + c * c - a * a) / (2 * b * c)));
                    double angB = Math.toDegrees(Math.acos((a * a + c * c - b * b) / (2 * a * c)));
                    output.setText(output.getContext().getString(R.string.triangle_area) + df.format(area)
                            + output.getContext().getString(R.string.triangle_perimeter) + df.format(a + b + c)
                            + output.getContext().getString(R.string.triangle_angles) + df.format(angA)
                            + ", " + df.format(angB) + ", " + df.format(180 - angA - angB)
                            + output.getContext().getString(R.string.triangle_deg));
                }
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.triangle_check_sides));
            }
        });
        return box;
    }
}

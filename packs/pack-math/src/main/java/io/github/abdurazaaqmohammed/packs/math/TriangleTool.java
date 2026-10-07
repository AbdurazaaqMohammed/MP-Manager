package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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
        super("triangle", "Triangle Solver", "Sides and angles", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_triangle_solver, "Triangle Solver"));
        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rightBtn = new RadioButton(context);
        rightBtn.setId(View.generateViewId());
        rightBtn.setText(PackRes.str("math", R.string.s_right_legs, "Right legs"));
        RadioButton sssBtn = new RadioButton(context);
        sssBtn.setId(View.generateViewId());
        sssBtn.setText(PackRes.str("math", R.string.s_3_sides, "3 sides"));
        modeGroup.addView(rightBtn);
        modeGroup.addView(sssBtn);
        modeGroup.check(rightBtn.getId());
        box.addView(modeGroup);
        EditText s1 = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_side_a, "Side a"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s1.setText(PackRes.str("math", R.string.s_3_2, "3"));
        EditText s2 = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_side_b, "Side b"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s2.setText(PackRes.str("math", R.string.s_4, "4"));
        EditText s3 = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_side_c_3_sides_mode_only, "Side c (3-sides mode only)"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        s3.setText(PackRes.str("math", R.string.s_5, "5"));
        TextView output = ToolViewFactory.makeOutput(box);
        final int rightId = rightBtn.getId();
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_solve, "Solve"));
        goBtn.setOnClickListener(v -> {
            try {
                DecimalFormat df = new DecimalFormat("0.##");
                if (modeGroup.getCheckedRadioButtonId() == rightId) {
                    double a = Double.parseDouble(s1.getText().toString());
                    double b = Double.parseDouble(s2.getText().toString());
                    double hyp = Math.sqrt(a * a + b * b);
                    double angA = Math.toDegrees(Math.atan2(a, b));
                    output.setText("Hypotenuse " + df.format(hyp) + "\nAngles " + df.format(angA) + " and " + df.format(90 - angA) + " deg\nArea " + df.format(a * b / 2) + "  Perimeter " + df.format(a + b + hyp));
                } else {
                    double a = Double.parseDouble(s1.getText().toString());
                    double b = Double.parseDouble(s2.getText().toString());
                    double c = Double.parseDouble(s3.getText().toString());
                    if (a + b <= c || a + c <= b || b + c <= a) {
                        output.setText(PackRes.str("math", R.string.s_not_a_valid_triangle, "Not a valid triangle"));
                        return;
                    }
                    double s = (a + b + c) / 2;
                    double area = Math.sqrt(s * (s - a) * (s - b) * (s - c));
                    double angA = Math.toDegrees(Math.acos((b * b + c * c - a * a) / (2 * b * c)));
                    double angB = Math.toDegrees(Math.acos((a * a + c * c - b * b) / (2 * a * c)));
                    output.setText("Area " + df.format(area) + "  Perimeter " + df.format(a + b + c) + "\nAngles " + df.format(angA) + ", " + df.format(angB) + ", " + df.format(180 - angA - angB) + " deg");
                }
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_check_sides, "Check sides"));
            }
        });
        return box;
    }
}

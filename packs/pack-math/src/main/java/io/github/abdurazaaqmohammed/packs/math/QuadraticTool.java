package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildQuadratic().
 */
public class QuadraticTool extends BaseToolPlugin {

    public QuadraticTool() {
        super("quadratic", "Quadratic Solver", "Roots and vertex", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_quadratic_solver, "Quadratic Solver"));
        ToolViewFactory.addLabel(box, PackRes.str("math", R.string.s_solves_a_x_squared_plus_b_x_plus_c_equals_0, "Solves a x squared plus b x plus c equals 0."));
        EditText aInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_a, "a"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        aInput.setText(PackRes.str("math", R.string.s_1, "1"));
        EditText bInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_b, "b"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        bInput.setText(PackRes.str("math", R.string.s_3, "-3"));
        EditText cInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_c, "c"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        cInput.setText(PackRes.str("math", R.string.s_2, "2"));
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_solve, "Solve"));
        goBtn.setOnClickListener(v -> {
            try {
                double a = Double.parseDouble(aInput.getText().toString());
                double b = Double.parseDouble(bInput.getText().toString());
                double c = Double.parseDouble(cInput.getText().toString());
                DecimalFormat df = new DecimalFormat("0.####");
                if (a == 0) {
                    if (b == 0) {
                        output.setText(PackRes.str("math", R.string.s_not_an_equation, "Not an equation"));
                    } else {
                        output.setText("Linear root x = " + df.format(-c / b));
                    }
                    return;
                }
                double disc = b * b - 4 * a * c;
                double vx = -b / (2 * a);
                double vy = a * vx * vx + b * vx + c;
                StringBuilder sb = new StringBuilder();
                sb.append("Discriminant ").append(df.format(disc)).append("\n");
                if (disc > 0) {
                    sb.append("x1 = ").append(df.format((-b + Math.sqrt(disc)) / (2 * a))).append("\n");
                    sb.append("x2 = ").append(df.format((-b - Math.sqrt(disc)) / (2 * a))).append("\n");
                } else if (disc == 0) {
                    sb.append("x = ").append(df.format(-b / (2 * a))).append("\n");
                } else {
                    double re = -b / (2 * a);
                    double im = Math.sqrt(-disc) / (2 * a);
                    sb.append("x1 = ").append(df.format(re)).append(" + ").append(df.format(im)).append("i\n");
                    sb.append("x2 = ").append(df.format(re)).append(" - ").append(df.format(im)).append("i\n");
                }
                sb.append("Vertex (").append(df.format(vx)).append(", ").append(df.format(vy)).append(")");
                output.setText(sb.toString());
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_enter_a_b_and_c, "Enter a, b and c"));
            }
        });
        return box;
    }
}

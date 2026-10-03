package io.github.abdurazaaqmohammed.packs.math;

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
        super("quadratic", R.string.quadratic_title, R.string.quadratic_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.quadratic_quadratic_solver));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.quadratic_solves_a_x_squared_plus_b_x_pl));
        EditText aInput = ToolViewFactory.makeInput(box, "a",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        aInput.setText("1");
        EditText bInput = ToolViewFactory.makeInput(box, "b",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        bInput.setText("-3");
        EditText cInput = ToolViewFactory.makeInput(box, "c",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        cInput.setText("2");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.ohm_solve));
        goBtn.setOnClickListener(v -> {
            try {
                double a = Double.parseDouble(aInput.getText().toString());
                double b = Double.parseDouble(bInput.getText().toString());
                double c = Double.parseDouble(cInput.getText().toString());
                DecimalFormat df = new DecimalFormat("0.####");
                if (a == 0) {
                    if (b == 0) {
                        output.setText(output.getContext().getString(R.string.quadratic_not_an_equation));
                    } else {
                        output.setText(output.getContext().getString(R.string.quadratic_linear_root_x) + df.format(-c / b));
                    }
                    return;
                }
                double disc = b * b - 4 * a * c;
                double vx = -b / (2 * a);
                double vy = a * vx * vx + b * vx + c;
                StringBuilder sb = new StringBuilder();
                sb.append(output.getContext().getString(R.string.quadratic_discriminant)).append(df.format(disc)).append("\n");
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
                sb.append(output.getContext().getString(R.string.quadratic_vertex)).append(df.format(vx)).append(", ").append(df.format(vy)).append(")");
                output.setText(sb.toString());
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.quadratic_enter_a_b_and_c));
            }
        });
        return box;
    }
}

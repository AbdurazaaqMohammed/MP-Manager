package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildGeometry().
 */
public class GeometryTool extends BaseToolPlugin {

    public GeometryTool() {
        super("geometry", "Geometry Calc", "Areas and volumes", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_geometry_calculator, "Geometry Calculator"));
        Spinner shapeSpinner = new Spinner(context);
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                new String[]{"Circle (r)", "Rectangle (w,h)", "Triangle (b,h)", "Cylinder (r,h)", "Sphere (r)"});
        shapeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shapeSpinner.setAdapter(shapeAdapter);
        box.addView(shapeSpinner);
        EditText v1 = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_r_or_width_or_base, "r or width or base"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        v1.setText(PackRes.str("math", R.string.s_5, "5"));
        EditText v2 = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_h_rect_triangle_cylinder, "h (rect, triangle, cylinder)"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        v2.setText(PackRes.str("math", R.string.s_10, "10"));
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, PackRes.str("math", R.string.s_calculate, "Calculate"));
        goBtn.setOnClickListener(v -> {
            try {
                double a = Double.parseDouble(v1.getText().toString());
                String vs = v2.getText().toString().trim();
                double b = vs.isEmpty() ? 0 : Double.parseDouble(vs);
                if (a < 0 || b < 0) {
                    output.setText(PackRes.str("math", R.string.s_lengths_must_not_be_negative, "Lengths must not be negative"));
                    return;
                }
                DecimalFormat df = new DecimalFormat("0.##");
                int shape = shapeSpinner.getSelectedItemPosition();
                StringBuilder sb = new StringBuilder();
                if (shape == 0) {
                    sb.append("Area ").append(df.format(Math.PI * a * a)).append("\nCircumference ").append(df.format(2 * Math.PI * a));
                } else if (shape == 1) {
                    sb.append("Area ").append(df.format(a * b)).append("\nPerimeter ").append(df.format(2 * (a + b)));
                } else if (shape == 2) {
                    sb.append("Area ").append(df.format(a * b / 2));
                } else if (shape == 3) {
                    sb.append("Volume ").append(df.format(Math.PI * a * a * b)).append("\nSurface ").append(df.format(2 * Math.PI * a * (a + b)));
                } else {
                    sb.append("Volume ").append(df.format(4.0 / 3.0 * Math.PI * a * a * a)).append("\nSurface ").append(df.format(4 * Math.PI * a * a));
                }
                output.setText(sb.toString());
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_check_inputs, "Check inputs"));
            }
        });
        return box;
    }
}

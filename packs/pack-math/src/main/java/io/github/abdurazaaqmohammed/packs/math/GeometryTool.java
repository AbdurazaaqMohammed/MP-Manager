package io.github.abdurazaaqmohammed.packs.math;

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
        super("geometry", R.string.geometry_title, R.string.geometry_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.geometry_geometry_calculator));
        Spinner shapeSpinner = new Spinner(context);
        ArrayAdapter<String> shapeAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                new String[]{context.getString(R.string.geometry_circle_r), context.getString(R.string.geometry_rectangle_w_h), context.getString(R.string.geometry_triangle_b_h), context.getString(R.string.geometry_cylinder_r_h), context.getString(R.string.geometry_sphere_r)});
        shapeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shapeSpinner.setAdapter(shapeAdapter);
        box.addView(shapeSpinner);
        EditText v1 = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.geometry_r_or_width_or_base),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        v1.setText("5");
        EditText v2 = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.geometry_h_rect_triangle_cylinder),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        v2.setText("10");
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.compound_calculate));
        goBtn.setOnClickListener(v -> {
            try {
                double a = Double.parseDouble(v1.getText().toString());
                String vs = v2.getText().toString().trim();
                double b = vs.isEmpty() ? 0 : Double.parseDouble(vs);
                if (a < 0 || b < 0) {
                    output.setText(output.getContext().getString(R.string.geometry_lengths_must_not_be_negative));
                    return;
                }
                DecimalFormat df = new DecimalFormat("0.##");
                int shape = shapeSpinner.getSelectedItemPosition();
                StringBuilder sb = new StringBuilder();
                if (shape == 0) {
                    sb.append(output.getContext().getString(R.string.geometry_area)).append(df.format(Math.PI * a * a)).append(output.getContext().getString(R.string.geometry_circumference)).append(df.format(2 * Math.PI * a));
                } else if (shape == 1) {
                    sb.append(output.getContext().getString(R.string.geometry_area)).append(df.format(a * b)).append(output.getContext().getString(R.string.geometry_perimeter)).append(df.format(2 * (a + b)));
                } else if (shape == 2) {
                    sb.append(output.getContext().getString(R.string.geometry_area)).append(df.format(a * b / 2));
                } else if (shape == 3) {
                    sb.append(output.getContext().getString(R.string.geometry_volume)).append(df.format(Math.PI * a * a * b)).append(output.getContext().getString(R.string.geometry_surface)).append(df.format(2 * Math.PI * a * (a + b)));
                } else {
                    sb.append(output.getContext().getString(R.string.geometry_volume)).append(df.format(4.0 / 3.0 * Math.PI * a * a * a)).append(output.getContext().getString(R.string.geometry_surface)).append(df.format(4 * Math.PI * a * a));
                }
                output.setText(sb.toString());
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.compound_check_inputs));
            }
        });
        return box;
    }
}

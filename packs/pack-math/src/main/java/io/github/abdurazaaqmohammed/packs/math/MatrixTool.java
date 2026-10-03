package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
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
 * Extraction of ToolRunnerActivity.buildMatrix().
 */
public class MatrixTool extends BaseToolPlugin {

    public MatrixTool() {
        super("matrix", R.string.matrix_title, R.string.matrix_sub,  ToolCategories.MATH);
    }

    private static EditText numCell(Context context, LinearLayout row, String def) {
        EditText e = ToolViewFactory.makeRowInput(row, null,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED, 1f, def);
        e.setGravity(Gravity.CENTER);
        return e;
    }

    private static String mat2(DecimalFormat df, double a, double b, double c, double d) {
        return "| " + df.format(a) + "  " + df.format(b) + " |\n| " + df.format(c) + "  " + df.format(d) + " |";
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.matrix_matrix_2x2));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.matrix_matrix_a));
        LinearLayout aRow1 = ToolViewFactory.makeRow(box);
        EditText a11 = numCell(context, aRow1, "1");
        EditText a12 = numCell(context, aRow1, "2");
        LinearLayout aRow2 = ToolViewFactory.makeRow(box);
        EditText a21 = numCell(context, aRow2, "3");
        EditText a22 = numCell(context, aRow2, "4");
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.matrix_matrix_b));
        LinearLayout bRow1 = ToolViewFactory.makeRow(box);
        EditText b11 = numCell(context, bRow1, "5");
        EditText b12 = numCell(context, bRow1, "6");
        LinearLayout bRow2 = ToolViewFactory.makeRow(box);
        EditText b21 = numCell(context, bRow2, "7");
        EditText b22 = numCell(context, bRow2, "8");
        Spinner opSpinner = new Spinner(context);
        ArrayAdapter<String> opAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item,
                new String[]{"A + B", "A - B", "A x B", context.getString(R.string.matrix_det_a), context.getString(R.string.matrix_inverse_a), context.getString(R.string.matrix_transpose_a)});
        opAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        opSpinner.setAdapter(opAdapter);
        box.addView(opSpinner);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.matrix_compute));
        goBtn.setOnClickListener(v -> {
            try {
                double x11 = Double.parseDouble(a11.getText().toString());
                double x12 = Double.parseDouble(a12.getText().toString());
                double x21 = Double.parseDouble(a21.getText().toString());
                double x22 = Double.parseDouble(a22.getText().toString());
                DecimalFormat df = new DecimalFormat("0.####");
                int op = opSpinner.getSelectedItemPosition();
                double y11 = 0, y12 = 0, y21 = 0, y22 = 0;
                if (op <= 2) {
                    y11 = Double.parseDouble(b11.getText().toString());
                    y12 = Double.parseDouble(b12.getText().toString());
                    y21 = Double.parseDouble(b21.getText().toString());
                    y22 = Double.parseDouble(b22.getText().toString());
                }
                String result;
                if (op == 0) {
                    result = mat2(df, x11 + y11, x12 + y12, x21 + y21, x22 + y22);
                } else if (op == 1) {
                    result = mat2(df, x11 - y11, x12 - y12, x21 - y21, x22 - y22);
                } else if (op == 2) {
                    result = mat2(df, x11 * y11 + x12 * y21, x11 * y12 + x12 * y22, x21 * y11 + x22 * y21, x21 * y12 + x22 * y22);
                } else if (op == 3) {
                    result = output.getContext().getString(R.string.matrix_det_value) + df.format(x11 * x22 - x12 * x21);
                } else if (op == 4) {
                    double det = x11 * x22 - x12 * x21;
                    if (det == 0) {
                        result = output.getContext().getString(R.string.matrix_singular);
                    } else {
                        result = mat2(df, x22 / det, -x12 / det, -x21 / det, x11 / det);
                    }
                } else {
                    result = mat2(df, x11, x21, x12, x22);
                }
                output.setText(result);
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.matrix_fill_all_cells));
            }
        });
        return box;
    }
}

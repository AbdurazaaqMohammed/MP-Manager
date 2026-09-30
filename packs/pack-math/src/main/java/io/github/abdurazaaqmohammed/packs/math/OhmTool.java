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
 * Extraction of ToolRunnerActivity.buildOhm().
 */
public class OhmTool extends BaseToolPlugin {

    public OhmTool() {
        super("ohm", "Ohm Law Calc", "Solve V, I, R, P", ToolCategories.MATH);
    }

    private static Double parseDoubleOrNull(String s) {
        try {
            s = s.trim();
            if (s.isEmpty()) {
                return null;
            }
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String fmtNull(Double v, DecimalFormat df) {
        return v == null ? "-" : df.format(v);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Ohm Law Solver");
        ToolViewFactory.addLabel(box, "Fill any two values, leave the rest empty.");
        EditText vInput = ToolViewFactory.makeInput(box, "Voltage V",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText iInput = ToolViewFactory.makeInput(box, "Current A",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText rInput = ToolViewFactory.makeInput(box, "Resistance Ohm",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText pInput = ToolViewFactory.makeInput(box, "Power W",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Solve");
        goBtn.setOnClickListener(v -> {
            try {
                Double V = parseDoubleOrNull(vInput.getText().toString());
                Double I = parseDoubleOrNull(iInput.getText().toString());
                Double R = parseDoubleOrNull(rInput.getText().toString());
                Double P = parseDoubleOrNull(pInput.getText().toString());
                for (int k = 0; k < 6; k++) {
                    if (V == null && I != null && R != null) {
                        V = I * R;
                    }
                    if (V == null && P != null && I != null && I != 0) {
                        V = P / I;
                    }
                    if (V == null && P != null && R != null && R > 0) {
                        V = Math.sqrt(P * R);
                    }
                    if (I == null && V != null && R != null && R != 0) {
                        I = V / R;
                    }
                    if (I == null && P != null && V != null && V != 0) {
                        I = P / V;
                    }
                    if (R == null && V != null && I != null && I != 0) {
                        R = V / I;
                    }
                    if (R == null && V != null && P != null && P != 0) {
                        R = V * V / P;
                    }
                    if (P == null && V != null && I != null) {
                        P = V * I;
                    }
                }
                DecimalFormat df = new DecimalFormat("0.####");
                output.setText("V=" + fmtNull(V, df) + "  I=" + fmtNull(I, df) + "  R=" + fmtNull(R, df) + "  P=" + fmtNull(P, df));
            } catch (Exception e) {
                output.setText("Enter at least two values");
            }
        });
        return box;
    }
}

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
        super("ohm", R.string.ohm_title, R.string.ohm_sub,  ToolCategories.MATH);
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
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.ohm_ohm_law_solver));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.ohm_fill_any_two_values_leave_the_));
        EditText vInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.ohm_voltage_v),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText iInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.ohm_current_a),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText rInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.ohm_resistance_ohm),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText pInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.ohm_power_w),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        TextView output = ToolViewFactory.makeOutput(box);
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.ohm_solve));
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
                output.setText(output.getContext().getString(R.string.ohm_enter_at_least_two_values));
            }
        });
        return box;
    }
}

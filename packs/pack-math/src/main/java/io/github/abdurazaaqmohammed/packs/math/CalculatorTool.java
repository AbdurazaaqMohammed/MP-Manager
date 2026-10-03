package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.math.ExpressionEvaluator;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Extraction of ToolRunnerActivity.buildCalculator().
 * Expression parsing lives in domain.math.ExpressionEvaluator.
 */
public class CalculatorTool extends BaseToolPlugin {

    public CalculatorTool() {
        super("calc", R.string.calc_title, R.string.calc_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.calc_calculator));
        EditText display = ToolViewFactory.makeInput(box, "0", InputType.TYPE_CLASS_TEXT);
        display.setTextSize(24);
        display.setTypeface(Typeface.MONOSPACE);
        TextView result = ToolViewFactory.makeOutput(box);
        result.setText("= 0");
        String[][] rows = new String[][]{
                {"C", "(", ")", "DEL"},
                {"7", "8", "9", "div"},
                {"4", "5", "6", "mul"},
                {"1", "2", "3", "sub"},
                {"0", ".", "%", "add"},
                {"sin", "cos", "tan", "eq"},
                {"log", "ln", "sqrt", "pow"},
                {"pi", "e", "^", "ans"}
        };
        final String[] lastAns = new String[]{"0"};
        for (String[] r : rows) {
            LinearLayout row = ToolViewFactory.makeRow(box);
            for (String key : r) {
                String label;
                switch (key) {
                    case "div": label = "\u00f7"; break;
                    case "mul": label = "\u00d7"; break;
                    case "sub": label = "-"; break;
                    case "add": label = "+"; break;
                    case "eq": label = "="; break;
                    case "pow": label = "x^y"; break;
                    case "DEL": label = "\u232b"; break;
                    default: label = key; break;
                }
                MaterialButton b = ToolViewFactory.makeRowButton(row, label, 1f);
                final String k = key;
                b.setOnClickListener(v -> {
                    String cur = display.getText().toString();
                    switch (k) {
                        case "C":
                            display.setText("");
                            result.setText("= 0");
                            break;
                        case "DEL":
                            if (cur.length() > 0) {
                                display.setText(cur.substring(0, cur.length() - 1));
                            }
                            break;
                        case "eq": {
                            String expr = display.getText().toString();
                            try {
                                String out = ExpressionEvaluator.format(ExpressionEvaluator.eval(expr));
                                result.setText("= " + out);
                                lastAns[0] = out;
                            } catch (Exception e) {
                                result.setText(result.getContext().getString(R.string.calc_error));
                            }
                            break;
                        }
                        case "div": display.append("\u00f7"); break;
                        case "mul": display.append("\u00d7"); break;
                        case "sub": display.append("-"); break;
                        case "add": display.append("+"); break;
                        case "ans": display.append(lastAns[0]); break;
                        case "pow": display.append("^"); break;
                        case "sqrt": display.append("sqrt("); break;
                        default: display.append(k); break;
                    }
                    String expr2 = display.getText().toString();
                    if (!expr2.isEmpty()) {
                        try {
                            result.setText("= " + ExpressionEvaluator.format(ExpressionEvaluator.eval(expr2)));
                        } catch (Exception ignored) {
                        }
                    }
                });
            }
        }
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.calc_copy_result));
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, context.getString(R.string.calc_calc), result.getText().toString()));
        return box;
    }
}

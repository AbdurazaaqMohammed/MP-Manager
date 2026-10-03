package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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
 * Extraction of ToolRunnerActivity.buildGpa().
 */
public class GpaTool extends BaseToolPlugin {

    public GpaTool() {
        super("gpa", R.string.gpa_title, R.string.gpa_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.gpa_gpa_calculator));
        String[] grades = new String[]{"A+", "A", "A-", "B+", "B", "B-", "C+", "C", "C-", "D", "F"};
        double[] points = new double[]{4.0, 4.0, 3.7, 3.3, 3.0, 2.7, 2.3, 2.0, 1.7, 1.0, 0.0};
        LinearLayout rowsBox = new LinearLayout(context);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(rowsBox);
        TextView output = ToolViewFactory.makeOutput(box);
        final Runnable compute = () -> {
            try {
                double totalPoints = 0;
                double totalCredits = 0;
                for (int i = 0; i < rowsBox.getChildCount(); i++) {
                    LinearLayout row = (LinearLayout) rowsBox.getChildAt(i);
                    Spinner g = (Spinner) row.getChildAt(0);
                    EditText c = (EditText) row.getChildAt(1);
                    String cs = c.getText().toString().trim();
                    if (cs.isEmpty()) {
                        continue;
                    }
                    double credits = Double.parseDouble(cs);
                    totalPoints += points[g.getSelectedItemPosition()] * credits;
                    totalCredits += credits;
                }
                if (totalCredits <= 0) {
                    output.setText(output.getContext().getString(R.string.gpa_add_courses_with_credits));
                    return;
                }
                output.setText(output.getContext().getString(R.string.gpa_gpa) + new DecimalFormat("0.00").format(totalPoints / totalCredits)
                        + output.getContext().getString(R.string.gpa_credits_total) + new DecimalFormat("0.#").format(totalCredits));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.gpa_check_credits));
            }
        };
        final AdapterView.OnItemSelectedListener gradeListener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                compute.run();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        final Runnable addCourseRow = () -> {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Spinner g = new Spinner(context);
            ArrayAdapter<String> ga = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, grades);
            ga.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            g.setAdapter(ga);
            g.setSelection(1);
            g.setOnItemSelectedListener(gradeListener);
            row.addView(g, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            EditText c = ToolViewFactory.makeRowInput(row, row.getContext().getString(R.string.gpa_credits),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 1f, "3");
            c.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    compute.run();
                }
                public void afterTextChanged(Editable s) {
                }
            });
            MaterialButton del = new MaterialButton(context);
            del.setText("X");
            del.setOnClickListener(v -> {
                rowsBox.removeView(row);
                compute.run();
            });
            row.addView(del, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.5f));
            rowsBox.addView(row);
            compute.run();
        };
        addCourseRow.run();
        addCourseRow.run();
        addCourseRow.run();
        MaterialButton addBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.gpa_add_course));
        addBtn.setOnClickListener(v -> addCourseRow.run());
        return box;
    }
}

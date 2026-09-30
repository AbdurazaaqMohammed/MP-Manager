package io.github.abdurazaaqmohammed.packs.math;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Extraction of ToolRunnerActivity.buildResistor().
 */
public class ResistorTool extends BaseToolPlugin {

    public ResistorTool() {
        super("resistor", "Resistor Decoder", "Decode color bands", ToolCategories.MATH);
    }

    private static String formatOhms(double v) {
        DecimalFormat df = new DecimalFormat("0.##");
        if (v >= 1000000) {
            return df.format(v / 1000000.0) + " MOhm";
        } else if (v >= 1000) {
            return df.format(v / 1000.0) + " kOhm";
        }
        return df.format(v) + " Ohm";
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Resistor Decoder");
        String[] colors = new String[]{"Black", "Brown", "Red", "Orange", "Yellow", "Green", "Blue", "Violet", "Gray", "White", "Gold", "Silver"};
        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton fourBtn = new RadioButton(context);
        fourBtn.setId(View.generateViewId());
        fourBtn.setText("4-band");
        RadioButton fiveBtn = new RadioButton(context);
        fiveBtn.setId(View.generateViewId());
        fiveBtn.setText("5-band");
        modeGroup.addView(fourBtn);
        modeGroup.addView(fiveBtn);
        modeGroup.check(fourBtn.getId());
        box.addView(modeGroup);
        LinearLayout bandsBox = new LinearLayout(context);
        bandsBox.setOrientation(LinearLayout.VERTICAL);
        box.addView(bandsBox);
        TextView output = ToolViewFactory.makeOutput(box);
        final int[] bandCount = new int[]{4};
        final Runnable computeValue = () -> {
            try {
                int n = bandsBox.getChildCount();
                List<Integer> digits = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    Spinner s = (Spinner) bandsBox.getChildAt(i);
                    digits.add(s.getSelectedItemPosition());
                }
                int[] digitVal = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1, -2};
                double[] tolMap = new double[]{20, 1, 2, 20, 20, 0.5, 0.25, 0.1, 0.05, 20, 5, 10};
                double value;
                double tol;
                if (bandCount[0] == 4) {
                    int d1 = digitVal[digits.get(0)];
                    int d2 = digitVal[digits.get(1)];
                    if (d1 < 0 || d2 < 0) {
                        output.setText("Gold/Silver invalid as digits");
                        return;
                    }
                    int mult = digitVal[digits.get(2)];
                    tol = tolMap[digits.get(3)];
                    value = (d1 * 10 + d2) * Math.pow(10, mult);
                } else {
                    int d1 = digitVal[digits.get(0)];
                    int d2 = digitVal[digits.get(1)];
                    int d3 = digitVal[digits.get(2)];
                    if (d1 < 0 || d2 < 0 || d3 < 0) {
                        output.setText("Gold/Silver invalid as digits");
                        return;
                    }
                    int mult = digitVal[digits.get(3)];
                    tol = tolMap[digits.get(4)];
                    value = (d1 * 100 + d2 * 10 + d3) * Math.pow(10, mult);
                }
                output.setText(formatOhms(value) + "  Tol " + tol + "%");
            } catch (Exception e) {
                output.setText("Pick band colors");
            }
        };
        final AdapterView.OnItemSelectedListener bandListener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                computeValue.run();
            }
            public void onNothingSelected(AdapterView<?> parent) {
            }
        };
        final Runnable rebuild = () -> {
            bandsBox.removeAllViews();
            int count = bandCount[0] == 4 ? 4 : 5;
            String[] labels = bandCount[0] == 4 ? new String[]{"Digit 1", "Digit 2", "Multiplier", "Tolerance"} : new String[]{"Digit 1", "Digit 2", "Digit 3", "Multiplier", "Tolerance"};
            int[] defaults = bandCount[0] == 4 ? new int[]{2, 7, 3, 10} : new int[]{2, 7, 3, 3, 10};
            for (int i = 0; i < count; i++) {
                ToolViewFactory.addLabel(bandsBox, labels[i]);
                Spinner s = new Spinner(context);
                ArrayAdapter<String> ca = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, colors);
                ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                s.setAdapter(ca);
                s.setSelection(defaults[i]);
                s.setOnItemSelectedListener(bandListener);
                bandsBox.addView(s);
            }
            computeValue.run();
        };
        modeGroup.setOnCheckedChangeListener((g, checkedId) -> {
            bandCount[0] = (checkedId == fiveBtn.getId()) ? 5 : 4;
            rebuild.run();
        });
        rebuild.run();
        return box;
    }
}

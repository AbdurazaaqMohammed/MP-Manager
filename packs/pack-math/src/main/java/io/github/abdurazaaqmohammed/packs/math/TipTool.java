package io.github.abdurazaaqmohammed.packs.math;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.domain.math.Money;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildTip().
 */
public class TipTool extends BaseToolPlugin {

    public TipTool() {
        super("tip", "Tip Calculator", "Split bills and tip", ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("math", R.string.s_tip_calculator, "Tip Calculator"));
        EditText billInput = ToolViewFactory.makeInput(box, PackRes.str("math", R.string.s_bill_amount, "Bill amount"),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView tipLabel = ToolViewFactory.addLabel(box, PackRes.str("math", R.string.s_tip_15, "Tip: 15%"));
        SeekBar tipBar = new SeekBar(context);
        tipBar.setMax(40);
        tipBar.setProgress(15);
        box.addView(tipBar);
        TextView peopleLabel = ToolViewFactory.addLabel(box, PackRes.str("math", R.string.s_people_1, "People: 1"));
        SeekBar peopleBar = new SeekBar(context);
        peopleBar.setMax(19);
        peopleBar.setProgress(0);
        box.addView(peopleBar);
        TextView output = ToolViewFactory.makeOutput(box);
        final int[] tipPct = new int[]{15};
        final int[] people = new int[]{1};
        Runnable compute = () -> {
            try {
                double bill = Double.parseDouble(billInput.getText().toString());
                double[] r = Money.tipSplit(bill, tipPct[0], people[0]);
                DecimalFormat df = new DecimalFormat("0.00");
                output.setText("Tip " + df.format(r[0]) + "  Total " + df.format(r[1]) + "  Each " + df.format(r[2]));
            } catch (Exception e) {
                output.setText(PackRes.str("math", R.string.s_enter_bill_amount, "Enter bill amount"));
            }
        };
        tipBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tipPct[0] = progress;
                tipLabel.setText("Tip: " + progress + "%");
                compute.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        peopleBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                people[0] = 1 + progress;
                peopleLabel.setText("People: " + people[0]);
                compute.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        billInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        });
        compute.run();
        return box;
    }
}

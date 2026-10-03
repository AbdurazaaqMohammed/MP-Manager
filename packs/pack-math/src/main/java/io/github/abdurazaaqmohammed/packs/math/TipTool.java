package io.github.abdurazaaqmohammed.packs.math;

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
        super("tip", R.string.tip_title, R.string.tip_sub,  ToolCategories.MATH);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.tip_tip_calculator));
        EditText billInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.tip_bill_amount),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView tipLabel = ToolViewFactory.addLabel(box, box.getContext().getString(R.string.tip_tip_15));
        SeekBar tipBar = new SeekBar(context);
        tipBar.setMax(40);
        tipBar.setProgress(15);
        box.addView(tipBar);
        TextView peopleLabel = ToolViewFactory.addLabel(box, box.getContext().getString(R.string.tip_people_1));
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
                output.setText(output.getContext().getString(R.string.tip_tip) + df.format(r[0])
                        + output.getContext().getString(R.string.tip_total) + df.format(r[1])
                        + output.getContext().getString(R.string.tip_each) + df.format(r[2]));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.tip_enter_bill_amount));
            }
        };
        tipBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tipPct[0] = progress;
                tipLabel.setText(tipLabel.getContext().getString(R.string.tip_tip_2) + progress + "%");
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
                peopleLabel.setText(peopleLabel.getContext().getString(R.string.tip_people) + people[0]);
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

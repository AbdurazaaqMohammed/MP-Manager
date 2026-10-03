package io.github.abdurazaaqmohammed.packs.time;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Pilot extraction of ToolRunnerActivity.buildTally().
 * Self-contained: no activity fields, works through ToolPlugin contract.
 */
public class TallyTool extends BaseToolPlugin {

    private int count;

    public TallyTool() {
        super("tally", R.string.tally_title, R.string.tally_sub,  ToolCategories.TIME);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        count = 0;
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.tally_tally_counter));
        TextView output = ToolViewFactory.makeOutput(box);
        output.setTextSize(56);
        output.setText("0");
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton add = ToolViewFactory.makeRowButton(row, "+1", 1f);
        MaterialButton sub = ToolViewFactory.makeRowButton(row, "-1", 1f);
        MaterialButton reset = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.tally_reset), 1f);
        add.setOnClickListener(v -> {
            count++;
            output.setText(String.valueOf(count));
            ToolViewFactory.vibrateTick(context);
        });
        sub.setOnClickListener(v -> {
            count--;
            output.setText(String.valueOf(count));
            ToolViewFactory.vibrateTick(context);
        });
        reset.setOnClickListener(v -> {
            count = 0;
            output.setText("0");
        });
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.random;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.UUID;

/**
 * Extraction of ToolRunnerActivity.buildUuid().
 */
public class UuidTool extends BaseToolPlugin {

    public UuidTool() {
        super("uuid", R.string.uuid_title, R.string.uuid_sub,  ToolCategories.RAND);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.uuid_uuid_generator));
        TextView countLabel = ToolViewFactory.addLabel(box, box.getContext().getString(R.string.uuid_count_5));
        SeekBar countBar = new SeekBar(context);
        countBar.setMax(19);
        countBar.setProgress(4);
        box.addView(countBar);
        TextView output = ToolViewFactory.makeOutput(box);
        final Runnable generate = () -> {
            int n = 1 + countBar.getProgress();
            countLabel.setText(countLabel.getContext().getString(R.string.uuid_count) + n);
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < n; i++) {
                b.append(UUID.randomUUID().toString());
                if (i < n - 1) {
                    b.append("\n");
                }
            }
            output.setText(b.toString());
        };
        countBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                generate.run();
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        generate.run();
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton regenBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.uuid_new), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.password_copy), 1f);
        regenBtn.setOnClickListener(v -> generate.run());
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, context.getString(R.string.uuid_uuid), output.getText().toString()));
        return box;
    }
}

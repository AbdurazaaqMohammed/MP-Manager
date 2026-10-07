package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.text.Lorem;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.Random;

/**
 * Extraction of ToolRunnerActivity.buildLorem().
 */
public class LoremTool extends BaseToolPlugin {

    public LoremTool() {
        super("lorem", "Lorem Generator", "Generate placeholder text", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_lorem_generator, "Lorem Generator"));
        TextView countLabel = ToolViewFactory.addLabel(box, PackRes.str("text", R.string.s_paragraphs_3, "Paragraphs: 3"));
        SeekBar countBar = new SeekBar(context);
        countBar.setMax(9);
        countBar.setProgress(2);
        box.addView(countBar);
        TextView output = ToolViewFactory.makeOutput(box);
        Random loremRandom = new Random();
        final Runnable generate = () -> {
            int paras = 1 + countBar.getProgress();
            countLabel.setText("Paragraphs: " + paras);
            output.setText(Lorem.generate(paras, loremRandom));
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
        MaterialButton regenBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_new, "New"), 1f);
        MaterialButton copyBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_copy, "Copy"), 1f);
        regenBtn.setOnClickListener(v -> generate.run());
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, PackRes.str("text", R.string.s_lorem, "lorem"), output.getText().toString()));
        return box;
    }
}

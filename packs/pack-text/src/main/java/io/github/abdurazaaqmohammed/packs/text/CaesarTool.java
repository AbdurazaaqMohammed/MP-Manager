package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.text.TextCodecs;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Extraction of ToolRunnerActivity.buildCaesar().
 */
public class CaesarTool extends BaseToolPlugin {

    public CaesarTool() {
        super("caesar", "Caesar Cipher", "Shift ciphers, brute force", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_caesar_cipher, "Caesar Cipher"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_text, "Text"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(2);
        TextView shiftLabel = ToolViewFactory.addLabel(box, PackRes.str("text", R.string.s_shift_3, "Shift: 3"));
        SeekBar shiftBar = new SeekBar(context);
        shiftBar.setMax(25);
        shiftBar.setProgress(3);
        box.addView(shiftBar);
        final int[] shift = new int[]{3};
        shiftBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                shift[0] = progress;
                shiftLabel.setText("Shift: " + progress);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("text", R.string.s_result, "Result"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_encrypt, "Encrypt"), 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_decrypt, "Decrypt"), 1f);
        MaterialButton bruteBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_all_shifts, "All shifts"), 1f);
        encBtn.setOnClickListener(v -> output.setText(TextCodecs.caesarShift(input.getText().toString(), shift[0])));
        decBtn.setOnClickListener(v -> output.setText(TextCodecs.caesarShift(input.getText().toString(), 26 - (shift[0] % 26))));
        bruteBtn.setOnClickListener(v -> {
            StringBuilder b = new StringBuilder();
            for (int i = 1; i < 26; i++) {
                b.append(i).append(": ").append(TextCodecs.caesarShift(input.getText().toString(), i)).append("\n");
            }
            output.setText(b.toString().trim());
        });
        return box;
    }
}

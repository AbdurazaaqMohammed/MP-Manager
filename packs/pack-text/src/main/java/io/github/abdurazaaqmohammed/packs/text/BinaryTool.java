package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.text.TextCodecs;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Extraction of ToolRunnerActivity.buildBinaryText().
 */
public class BinaryTool extends BaseToolPlugin {

    public BinaryTool() {
        super("binarytext", "Binary Translator", "Convert text to binary", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_binary_translator, "Binary Translator"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_text_or_binary, "Text or binary"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("text", R.string.s_result, "Result"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_to_binary, "To binary"), 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_to_text, "To text"), 1f);
        encBtn.setOnClickListener(v -> {
            try {
                output.setText(TextCodecs.binaryEncode(input.getText().toString()));
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_error, "Error"));
            }
        });
        decBtn.setOnClickListener(v -> {
            try {
                output.setText(TextCodecs.binaryDecode(input.getText().toString()));
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_use_8_bit_groups_separated_by_spaces, "Use 8-bit groups separated by spaces"));
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, PackRes.str("text", R.string.s_copy_result, "Copy result"));
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, PackRes.str("text", R.string.s_binary, "binary"), output.getText().toString()));
        return box;
    }
}

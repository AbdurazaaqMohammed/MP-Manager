package io.github.abdurazaaqmohammed.packs.text;

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
        ToolViewFactory.addTitle(box, "Binary Translator");
        EditText input = ToolViewFactory.makeInput(box, "Text or binary",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText("Result");
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, "To binary", 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, "To text", 1f);
        encBtn.setOnClickListener(v -> {
            try {
                output.setText(TextCodecs.binaryEncode(input.getText().toString()));
            } catch (Exception e) {
                output.setText("Error");
            }
        });
        decBtn.setOnClickListener(v -> {
            try {
                output.setText(TextCodecs.binaryDecode(input.getText().toString()));
            } catch (Exception e) {
                output.setText("Use 8-bit groups separated by spaces");
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, "Copy result");
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, "binary", output.getText().toString()));
        return box;
    }
}

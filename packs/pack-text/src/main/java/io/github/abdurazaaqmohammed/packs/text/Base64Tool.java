package io.github.abdurazaaqmohammed.packs.text;

import android.content.Context;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

import java.nio.charset.StandardCharsets;

/**
 * Extraction of ToolRunnerActivity.buildBase64().
 */
public class Base64Tool extends BaseToolPlugin {

    public Base64Tool() {
        super("base64", "Base64 Tool", "Encode and decode Base64", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Base64 Tool");
        EditText input = ToolViewFactory.makeInput(box, "Input",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText("Result appears here");
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, "Encode", 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, "Decode", 1f);
        encBtn.setOnClickListener(v -> {
            try {
                String s = input.getText().toString();
                output.setText(Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            } catch (Exception e) {
                output.setText("Error");
            }
        });
        decBtn.setOnClickListener(v -> {
            try {
                String s = input.getText().toString().trim();
                output.setText(new String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8));
            } catch (Exception e) {
                output.setText("Invalid Base64");
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, "Copy result");
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, "base64", output.getText().toString()));
        return box;
    }
}

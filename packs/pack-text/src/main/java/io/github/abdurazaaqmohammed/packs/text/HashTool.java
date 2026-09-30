package io.github.abdurazaaqmohammed.packs.text;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.text.Hashing;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Extraction of ToolRunnerActivity.buildHash().
 */
public class HashTool extends BaseToolPlugin {

    public HashTool() {
        super("hash", "Hash Generator", "MD5, SHA-1, SHA-256", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Hash Generator");
        EditText input = ToolViewFactory.makeInput(box, "Text to hash",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText("Result appears here");
        MaterialButton goBtn = ToolViewFactory.makeButton(box, "Compute MD5 SHA-1 SHA-256 SHA-512");
        goBtn.setOnClickListener(v -> {
            String s = input.getText().toString();
            try {
                output.setText("MD5: " + Hashing.md5(s) + "\n\n"
                        + "SHA-1: " + Hashing.sha1(s) + "\n\n"
                        + "SHA-256: " + Hashing.sha256(s) + "\n\n"
                        + "SHA-512: " + Hashing.sha512(s));
            } catch (Exception e) {
                output.setText("Error: " + e.getMessage());
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, "Copy");
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, "hash", output.getText().toString()));
        return box;
    }
}

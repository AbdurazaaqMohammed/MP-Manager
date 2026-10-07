package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_base64_tool, "Base64 Tool"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_input, "Input"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("text", R.string.s_result_appears_here, "Result appears here"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_encode, "Encode"), 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_decode, "Decode"), 1f);
        encBtn.setOnClickListener(v -> {
            try {
                String s = input.getText().toString();
                output.setText(Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_error, "Error"));
            }
        });
        decBtn.setOnClickListener(v -> {
            try {
                String s = input.getText().toString().trim();
                output.setText(new String(Base64.decode(s, Base64.DEFAULT), StandardCharsets.UTF_8));
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_invalid_base64, "Invalid Base64"));
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, PackRes.str("text", R.string.s_copy_result, "Copy result"));
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, PackRes.str("text", R.string.s_base64, "base64"), output.getText().toString()));
        return box;
    }
}

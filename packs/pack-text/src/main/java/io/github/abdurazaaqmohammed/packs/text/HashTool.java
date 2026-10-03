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
        super("hash", R.string.hash_title, R.string.hash_sub,  ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.hash_hash_generator));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.hash_text_to_hash),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.base64_result_appears_here));
        MaterialButton goBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.hash_compute_md5_sha_1_sha_256_sha_));
        goBtn.setOnClickListener(v -> {
            String s = input.getText().toString();
            try {
                output.setText(output.getContext().getString(R.string.hash_digests,
                        Hashing.md5(s), Hashing.sha1(s), Hashing.sha256(s), Hashing.sha512(s)));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.hash_error) + e.getMessage());
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.colorconv_copy));
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, context.getString(R.string.hash_hash), output.getText().toString()));
        return box;
    }
}

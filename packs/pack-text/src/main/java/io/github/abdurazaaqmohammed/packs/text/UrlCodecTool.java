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
 * Extraction of ToolRunnerActivity.buildUrlCodec().
 */
public class UrlCodecTool extends BaseToolPlugin {

    public UrlCodecTool() {
        super("urlcodec", R.string.urlcodec_title, R.string.urlcodec_sub,  ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.urlcodec_url_encoder));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.base64_input),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.binarytext_result));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton encBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.base64_encode), 1f);
        MaterialButton decBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.base64_decode), 1f);
        encBtn.setOnClickListener(v -> {
            try {
                output.setText(TextCodecs.urlEncode(input.getText().toString()));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.base64_error));
            }
        });
        decBtn.setOnClickListener(v -> {
            try {
                output.setText(TextCodecs.urlDecode(input.getText().toString()));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.urlcodec_invalid_encoding));
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.base64_copy_result));
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, context.getString(R.string.urlcodec_url), output.getText().toString()));
        return box;
    }
}

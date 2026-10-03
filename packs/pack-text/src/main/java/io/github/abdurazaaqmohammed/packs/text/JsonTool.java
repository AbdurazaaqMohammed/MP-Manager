package io.github.abdurazaaqmohammed.packs.text;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.text.Json;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildJson().
 */
public class JsonTool extends BaseToolPlugin {

    public JsonTool() {
        super("json", R.string.json_title, R.string.json_sub,  ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.json_json_formatter));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.json_key_value),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.base64_result_appears_here));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton fmtBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.json_format), 1f);
        MaterialButton minBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.json_minify), 1f);
        MaterialButton validBtn = ToolViewFactory.makeRowButton(row, row.getContext().getString(R.string.json_validate), 1f);
        fmtBtn.setOnClickListener(v -> {
            try {
                output.setText(Json.format(Json.parse(input.getText().toString().trim())));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.json_invalid_json));
            }
        });
        minBtn.setOnClickListener(v -> {
            try {
                output.setText(Json.minify(Json.parse(input.getText().toString().trim())));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.json_invalid_json));
            }
        });
        validBtn.setOnClickListener(v -> {
            try {
                Json.parse(input.getText().toString().trim());
                output.setText(output.getContext().getString(R.string.json_valid_json));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.json_invalid_json));
            }
        });
        return box;
    }
}

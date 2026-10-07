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

import io.github.abdurazaaqmohammed.domain.text.Json;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildJson().
 */
public class JsonTool extends BaseToolPlugin {

    public JsonTool() {
        super("json", "JSON Formatter", "Format, minify, validate", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_json_formatter, "JSON Formatter"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_key_value, "{\"key\":\"value\"}"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("text", R.string.s_result_appears_here, "Result appears here"));
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton fmtBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_format, "Format"), 1f);
        MaterialButton minBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_minify, "Minify"), 1f);
        MaterialButton validBtn = ToolViewFactory.makeRowButton(row, PackRes.str("text", R.string.s_validate, "Validate"), 1f);
        fmtBtn.setOnClickListener(v -> {
            try {
                output.setText(Json.format(Json.parse(input.getText().toString().trim())));
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_invalid_json, "Invalid JSON"));
            }
        });
        minBtn.setOnClickListener(v -> {
            try {
                output.setText(Json.minify(Json.parse(input.getText().toString().trim())));
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_invalid_json, "Invalid JSON"));
            }
        });
        validBtn.setOnClickListener(v -> {
            try {
                Json.parse(input.getText().toString().trim());
                output.setText(PackRes.str("text", R.string.s_valid_json, "Valid JSON"));
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_invalid_json, "Invalid JSON"));
            }
        });
        return box;
    }
}

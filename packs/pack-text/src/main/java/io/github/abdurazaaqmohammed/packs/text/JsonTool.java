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
        super("json", "JSON Formatter", "Format, minify, validate", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "JSON Formatter");
        EditText input = ToolViewFactory.makeInput(box, "{\"key\":\"value\"}",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(4);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText("Result appears here");
        LinearLayout row = ToolViewFactory.makeRow(box);
        MaterialButton fmtBtn = ToolViewFactory.makeRowButton(row, "Format", 1f);
        MaterialButton minBtn = ToolViewFactory.makeRowButton(row, "Minify", 1f);
        MaterialButton validBtn = ToolViewFactory.makeRowButton(row, "Validate", 1f);
        fmtBtn.setOnClickListener(v -> {
            try {
                output.setText(Json.format(Json.parse(input.getText().toString().trim())));
            } catch (Exception e) {
                output.setText("Invalid JSON");
            }
        });
        minBtn.setOnClickListener(v -> {
            try {
                output.setText(Json.minify(Json.parse(input.getText().toString().trim())));
            } catch (Exception e) {
                output.setText("Invalid JSON");
            }
        });
        validBtn.setOnClickListener(v -> {
            try {
                Json.parse(input.getText().toString().trim());
                output.setText("Valid JSON");
            } catch (Exception e) {
                output.setText("Invalid JSON");
            }
        });
        return box;
    }
}

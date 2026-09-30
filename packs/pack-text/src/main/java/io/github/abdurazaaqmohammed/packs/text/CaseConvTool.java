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

import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildCaseConv().
 */
public class CaseConvTool extends BaseToolPlugin {

    public CaseConvTool() {
        super("caseconv", "Case Converter", "Upper, lower, title, reverse", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Case Converter");
        EditText input = ToolViewFactory.makeInput(box, "Text",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText("Result");
        LinearLayout row1 = ToolViewFactory.makeRow(box);
        MaterialButton upperBtn = ToolViewFactory.makeRowButton(row1, "UPPER", 1f);
        MaterialButton lowerBtn = ToolViewFactory.makeRowButton(row1, "lower", 1f);
        LinearLayout row2 = ToolViewFactory.makeRow(box);
        MaterialButton titleBtn = ToolViewFactory.makeRowButton(row2, "Title", 1f);
        MaterialButton sentenceBtn = ToolViewFactory.makeRowButton(row2, "Sentence", 1f);
        LinearLayout row3 = ToolViewFactory.makeRow(box);
        MaterialButton altBtn = ToolViewFactory.makeRowButton(row3, "aLtErNaTe", 1f);
        MaterialButton reverseBtn = ToolViewFactory.makeRowButton(row3, "Reverse", 1f);
        upperBtn.setOnClickListener(v -> output.setText(input.getText().toString().toUpperCase(Locale.US)));
        lowerBtn.setOnClickListener(v -> output.setText(input.getText().toString().toLowerCase(Locale.US)));
        titleBtn.setOnClickListener(v -> output.setText(TextCodecs.toTitleCase(input.getText().toString())));
        sentenceBtn.setOnClickListener(v -> output.setText(TextCodecs.toSentenceCase(input.getText().toString())));
        altBtn.setOnClickListener(v -> output.setText(TextCodecs.toAlternateCase(input.getText().toString())));
        reverseBtn.setOnClickListener(v -> output.setText(TextCodecs.reversed(input.getText().toString())));
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, "Copy result");
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, "case", output.getText().toString()));
        return box;
    }
}

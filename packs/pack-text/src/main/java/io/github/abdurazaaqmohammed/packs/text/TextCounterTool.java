package io.github.abdurazaaqmohammed.packs.text;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.domain.text.TextStats;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;

/**
 * Extraction of ToolRunnerActivity.buildTextCounter().
 */
public class TextCounterTool extends BaseToolPlugin {

    public TextCounterTool() {
        super("textcounter", R.string.textcounter_title, R.string.textcounter_sub,  ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.textcounter_text_counter));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.textcounter_type_or_paste_text),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(5);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.textcounter_chars_0_words_0_lines_0));
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                output.setText(TextStats.summary(s == null ? "" : s.toString()));
            }
            public void afterTextChanged(Editable s) {
            }
        });
        return box;
    }
}

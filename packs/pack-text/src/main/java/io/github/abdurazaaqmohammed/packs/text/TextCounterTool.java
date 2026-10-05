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
        super("textcounter", "Text Counter", "Chars, words, lines", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, "Text Counter");
        EditText input = ToolViewFactory.makeInput(box, "Type or paste text",
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(5);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText("Chars: 0  Words: 0  Lines: 0");
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String t = s == null ? "" : s.toString();
                int words = TextStats.words(t);
                int mins = words / 200;
                int secs = (words % 200) * 60 / 200;
                int noSpaces = t.replace(" ", "").replace("\n", "").replace("\t", "").length();
                output.setText(TextStats.summary(t)
                        + "\nChars (no spaces): " + noSpaces
                        + "\nReading time: ~" + (mins > 0 ? mins + "m " : "") + secs + "s");
            }
            public void afterTextChanged(Editable s) {
            }
        });
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.text;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraction of ToolRunnerActivity.buildRegex().
 */
public class RegexTool extends BaseToolPlugin {

    public RegexTool() {
        super("regex", R.string.regex_title, R.string.regex_sub,  ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.regex_regex_tester));
        EditText patternInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.regex_pattern_e_g_a_z_a_z), InputType.TYPE_CLASS_TEXT);
        patternInput.setText("[a-z]+@[a-z]+");
        CheckBox caseBox = new CheckBox(context);
        caseBox.setText(caseBox.getContext().getString(R.string.regex_ignore_case));
        box.addView(caseBox);
        CheckBox multiBox = new CheckBox(context);
        multiBox.setText(multiBox.getContext().getString(R.string.regex_multiline));
        box.addView(multiBox);
        EditText testInput = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.regex_test_text),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        testInput.setMinLines(3);
        testInput.setText(testInput.getContext().getString(R.string.regex_mail_me_at_joe_example_or_ann_));
        TextView output = ToolViewFactory.makeOutput(box);
        final Runnable compute = () -> {
            try {
                int flags = 0;
                if (caseBox.isChecked()) {
                    flags |= Pattern.CASE_INSENSITIVE;
                }
                if (multiBox.isChecked()) {
                    flags |= Pattern.MULTILINE;
                }
                Pattern p = Pattern.compile(patternInput.getText().toString(), flags);
                Matcher m = p.matcher(testInput.getText().toString());
                int count = 0;
                StringBuilder b = new StringBuilder();
                while (m.find() && count < 10) {
                    count++;
                    b.append(count).append(". ").append(m.group()).append("\n");
                }
                int total = count;
                while (m.find()) {
                    total++;
                }
                if (total == 0) {
                    output.setText(output.getContext().getString(R.string.regex_no_matches));
                } else {
                    output.setText(output.getContext().getString(
                            total == 1 ? R.string.regex_match_one : R.string.regex_matches_many, total)
                            + "\n" + b.toString().trim());
                }
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.regex_invalid_pattern));
            }
        };
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                compute.run();
            }
            public void afterTextChanged(Editable s) {
            }
        };
        patternInput.addTextChangedListener(watcher);
        testInput.addTextChangedListener(watcher);
        caseBox.setOnCheckedChangeListener((b, checked) -> compute.run());
        multiBox.setOnCheckedChangeListener((b, checked) -> compute.run());
        compute.run();
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.text;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

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
        super("regex", "Regex Tester", "Test patterns live", ToolCategories.TEXT);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("text", R.string.s_regex_tester, "Regex Tester"));
        EditText patternInput = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_pattern_e_g_a_z_a_z, "Pattern, e.g. [a-z]+@[a-z]+"), InputType.TYPE_CLASS_TEXT);
        patternInput.setText(PackRes.str("text", R.string.s_a_z_a_z, "[a-z]+@[a-z]+"));
        CheckBox caseBox = new CheckBox(context);
        caseBox.setText(PackRes.str("text", R.string.s_ignore_case, "Ignore case"));
        box.addView(caseBox);
        CheckBox multiBox = new CheckBox(context);
        multiBox.setText(PackRes.str("text", R.string.s_multiline, "Multiline"));
        box.addView(multiBox);
        EditText testInput = ToolViewFactory.makeInput(box, PackRes.str("text", R.string.s_test_text, "Test text"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        testInput.setMinLines(3);
        testInput.setText(PackRes.str("text", R.string.s_mail_me_at_joe_example_or_ann_test, "mail me at joe@example or ann@test"));
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
                    output.setText(PackRes.str("text", R.string.s_no_matches, "No matches"));
                } else {
                    output.setText(total + (total == 1 ? " match" : " matches") + "\n" + b.toString().trim());
                }
            } catch (Exception e) {
                output.setText(PackRes.str("text", R.string.s_invalid_pattern, "Invalid pattern"));
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

package io.github.abdurazaaqmohammed.packs.random;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.domain.text.Passwords;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

import java.text.DecimalFormat;

/**
 * Extraction of ToolRunnerActivity.buildStrength().
 */
public class StrengthTool extends BaseToolPlugin {

    public StrengthTool() {
        super("strength", "Password Strength", "Entropy and crack estimates", ToolCategories.RAND);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("random", R.string.s_password_strength, "Password Strength"));
        EditText input = ToolViewFactory.makeInput(box, PackRes.str("random", R.string.s_password_to_test, "Password to test"),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("random", R.string.s_type_a_password, "Type a password"));
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String p = s.toString();
                if (p.isEmpty()) {
                    output.setText(PackRes.str("random", R.string.s_type_a_password, "Type a password"));
                    return;
                }
                double entropy = Passwords.entropyBits(p);
                String label = Passwords.strengthLabel(entropy);
                double guesses = Math.pow(2, entropy - 1);
                String time = Passwords.guessesToTime(guesses);
                StringBuilder tips = new StringBuilder();
                if (p.length() < 12) {
                    tips.append("Use 12 or more characters. ");
                }
                boolean hasSymbol = false;
                boolean hasDigit = false;
                boolean hasUpper = false;
                boolean hasLower = false;
                for (int i = 0; i < p.length(); i++) {
                    char c = p.charAt(i);
                    if (c >= 'a' && c <= 'z') hasLower = true;
                    else if (c >= 'A' && c <= 'Z') hasUpper = true;
                    else if (c >= '0' && c <= '9') hasDigit = true;
                    else hasSymbol = true;
                }
                if (!hasSymbol) {
                    tips.append("Add symbols. ");
                }
                if (!hasDigit) {
                    tips.append("Add digits. ");
                }
                if (!hasUpper || !hasLower) {
                    tips.append("Mix upper and lower case.");
                }
                output.setText(label + "  (" + new DecimalFormat("0").format(entropy) + " bits)\nCrack estimate " + time + "\n" + tips.toString().trim());
            }
            public void afterTextChanged(Editable s) {
            }
        });
        return box;
    }
}

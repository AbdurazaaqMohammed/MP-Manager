package io.github.abdurazaaqmohammed.packs.random;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import io.github.abdurazaaqmohammed.domain.text.Passwords;
import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildPassword().
 */
public class PasswordTool extends BaseToolPlugin {

    public PasswordTool() {
        super("password", "Password Generator", "Generate secure passwords", ToolCategories.RAND);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("random", R.string.s_password_generator, "Password Generator"));
        TextView lengthLabel = ToolViewFactory.addLabel(box, PackRes.str("random", R.string.s_length_16, "Length: 16"));
        SeekBar lengthBar = new SeekBar(context);
        lengthBar.setMax(60);
        lengthBar.setProgress(12);
        box.addView(lengthBar);
        CheckBox upperBox = new CheckBox(context);
        upperBox.setText(PackRes.str("random", R.string.s_a_z, "A-Z"));
        upperBox.setChecked(true);
        box.addView(upperBox);
        CheckBox lowerBox = new CheckBox(context);
        lowerBox.setText(PackRes.str("random", R.string.s_a_z_2, "a-z"));
        lowerBox.setChecked(true);
        box.addView(lowerBox);
        CheckBox digitBox = new CheckBox(context);
        digitBox.setText(PackRes.str("random", R.string.s_0_9, "0-9"));
        digitBox.setChecked(true);
        box.addView(digitBox);
        CheckBox symbolBox = new CheckBox(context);
        symbolBox.setText(PackRes.str("random", R.string.s_symbols, "Symbols"));
        symbolBox.setChecked(true);
        box.addView(symbolBox);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(PackRes.str("random", R.string.s_press_generate, "Press Generate"));
        lengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int len = 4 + progress;
                lengthLabel.setText("Length: " + len);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        MaterialButton genBtn = ToolViewFactory.makeButton(box, PackRes.str("random", R.string.s_generate, "Generate"));
        genBtn.setOnClickListener(v -> {
            int len = 4 + lengthBar.getProgress();
            try {
                output.setText(Passwords.generate(len, upperBox.isChecked(), lowerBox.isChecked(),
                        digitBox.isChecked(), symbolBox.isChecked()));
            } catch (IllegalArgumentException e) {
                ToolViewFactory.toast(context, PackRes.str("random", R.string.s_pick_at_least_one_set, "Pick at least one set"));
            } catch (Exception e) {
                output.setText(PackRes.str("random", R.string.s_error, "Error"));
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, PackRes.str("random", R.string.s_copy, "Copy"));
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, PackRes.str("random", R.string.s_password, "password"), output.getText().toString()));
        return box;
    }
}

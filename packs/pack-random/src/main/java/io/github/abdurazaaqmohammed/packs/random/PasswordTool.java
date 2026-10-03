package io.github.abdurazaaqmohammed.packs.random;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
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
        super("password", R.string.password_title, R.string.password_sub,  ToolCategories.RAND);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.password_password_generator));
        TextView lengthLabel = ToolViewFactory.addLabel(box, box.getContext().getString(R.string.password_length_16));
        SeekBar lengthBar = new SeekBar(context);
        lengthBar.setMax(60);
        lengthBar.setProgress(12);
        box.addView(lengthBar);
        CheckBox upperBox = new CheckBox(context);
        upperBox.setText("A-Z");
        upperBox.setChecked(true);
        box.addView(upperBox);
        CheckBox lowerBox = new CheckBox(context);
        lowerBox.setText("a-z");
        lowerBox.setChecked(true);
        box.addView(lowerBox);
        CheckBox digitBox = new CheckBox(context);
        digitBox.setText("0-9");
        digitBox.setChecked(true);
        box.addView(digitBox);
        CheckBox symbolBox = new CheckBox(context);
        symbolBox.setText(symbolBox.getContext().getString(R.string.password_symbols));
        symbolBox.setChecked(true);
        box.addView(symbolBox);
        TextView output = ToolViewFactory.makeOutput(box);
        output.setText(output.getContext().getString(R.string.password_press_generate));
        lengthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int len = 4 + progress;
                lengthLabel.setText(lengthLabel.getContext().getString(R.string.password_length) + len);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        MaterialButton genBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.password_generate));
        genBtn.setOnClickListener(v -> {
            int len = 4 + lengthBar.getProgress();
            try {
                output.setText(Passwords.generate(len, upperBox.isChecked(), lowerBox.isChecked(),
                        digitBox.isChecked(), symbolBox.isChecked()));
            } catch (IllegalArgumentException e) {
                ToolViewFactory.toast(context, context.getString(R.string.password_pick_at_least_one_set));
            } catch (Exception e) {
                output.setText(output.getContext().getString(R.string.password_error));
            }
        });
        MaterialButton copyBtn = ToolViewFactory.makeButton(box, box.getContext().getString(R.string.password_copy));
        copyBtn.setOnClickListener(v ->
                ToolViewFactory.copyText(context, context.getString(R.string.password_password), output.getText().toString()));
        return box;
    }
}

package io.github.abdurazaaqmohammed.packs.device;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;

/**
 * Extraction of ToolRunnerActivity.buildMagnifier().
 */
public class MagnifierTool extends BaseToolPlugin {

    public MagnifierTool() {
        super("magnifier", R.string.magnifier_title, R.string.magnifier_sub,  ToolCategories.DEVICE);
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, box.getContext().getString(R.string.magnifier_magnifier));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.magnifier_type_or_paste_text_then_zoom_i));
        EditText input = ToolViewFactory.makeInput(box, box.getContext().getString(R.string.magnifier_text_to_magnify),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setText(input.getContext().getString(R.string.magnifier_hold_the_phone_close_and_read_));
        TextView zoom = new TextView(context);
        zoom.setText(zoom.getContext().getString(R.string.magnifier_hold_the_phone_close_and_read_));
        zoom.setTextSize(32);
        zoom.setPadding(ToolViewFactory.dp(context, 12), ToolViewFactory.dp(context, 12),
                ToolViewFactory.dp(context, 12), ToolViewFactory.dp(context, 12));
        zoom.setBackgroundColor(Color.parseColor("#FFFFFF"));
        zoom.setTextColor(Color.parseColor("#000000"));
        box.addView(zoom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ToolViewFactory.addLabel(box, box.getContext().getString(R.string.magnifier_text_size));
        SeekBar sizeBar = new SeekBar(context);
        sizeBar.setMax(108);
        sizeBar.setProgress(20);
        box.addView(sizeBar);
        CheckBox invertBox = new CheckBox(context);
        invertBox.setText(invertBox.getContext().getString(R.string.magnifier_high_contrast_black_on_yellow));
        box.addView(invertBox);
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                zoom.setText(s.toString());
            }
            public void afterTextChanged(Editable s) {
            }
        });
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                zoom.setTextSize(12 + progress);
            }
            public void onStartTrackingTouch(SeekBar s) {
            }
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        invertBox.setOnCheckedChangeListener((b, checked) -> {
            if (checked) {
                zoom.setBackgroundColor(Color.parseColor("#000000"));
                zoom.setTextColor(Color.parseColor("#FFFF00"));
            } else {
                zoom.setBackgroundColor(Color.parseColor("#FFFFFF"));
                zoom.setTextColor(Color.parseColor("#000000"));
            }
        });
        return box;
    }
}

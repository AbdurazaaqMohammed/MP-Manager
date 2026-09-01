package io.github.abdurazaaqmohammed.ui.views;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;

import io.github.abdurazaaqmohammed.MPManager.R;

public class SortDirectionToggle extends LinearLayout {

    private final TextView ascendingLabel;
    private final TextView descendingLabel;
    private final MaterialSwitch switchView;

    public SortDirectionToggle(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(16, 8, 16, 8);

        ascendingLabel = new TextView(context);
        ascendingLabel.setText(R.string.ascending);
        ascendingLabel.setTextSize(14);

        descendingLabel = new TextView(context);
        descendingLabel.setText(R.string.descending);
        descendingLabel.setTextSize(14);

        switchView = new MaterialSwitch(context);
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        switchParams.setMargins(24, 0, 24, 0);

        addView(descendingLabel);
        addView(switchView, switchParams);
        addView(ascendingLabel);

        setDescending(true);
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                ascendingLabel.setTypeface(null, Typeface.BOLD);
                ascendingLabel.setAlpha(1f);
                descendingLabel.setTypeface(null, Typeface.NORMAL);
                descendingLabel.setAlpha(0.4f);
            } else {
                ascendingLabel.setTypeface(null, Typeface.NORMAL);
                ascendingLabel.setAlpha(0.4f);
                descendingLabel.setTypeface(null, Typeface.BOLD);
                descendingLabel.setAlpha(1f);
            }
        });
    }

    public void setDescending(boolean descending) {
        switchView.setChecked(!descending);
        if (descending) {
            ascendingLabel.setTypeface(null, Typeface.NORMAL);
            ascendingLabel.setAlpha(0.4f);
            descendingLabel.setTypeface(null, Typeface.BOLD);
            descendingLabel.setAlpha(1f);
        } else {
            ascendingLabel.setTypeface(null, Typeface.BOLD);
            ascendingLabel.setAlpha(1f);
            descendingLabel.setTypeface(null, Typeface.NORMAL);
            descendingLabel.setAlpha(0.4f);
        }
    }

    public boolean isDescending() {
        return !switchView.isChecked();
    }
}
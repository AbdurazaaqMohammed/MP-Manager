package io.github.abdurazaaqmohammed.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.IOException;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.SignListAdapter;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;

public class UIHelper {
    private final Context context;
    public UIHelper (Context context) {
        this.context = context;
    }

    public View.OnClickListener showSignSettingsDialog() {
        return v -> {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            String path;
            try {
                path = FileUtils.copyFileFromAssetsAndGetFile("debug.keystore", context).getPath();
            } catch (IOException e) {
                path = "";
            }
            CharSequence[] items1 = new CharSequence[]{
                    "Pick signature file (current: " + new File(settings.getString("keyPath", path)).getName() + ")", "V1", "V2", "V3",
                    "V4"};
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context).setTitle(R.string.signature_options);

            SignListAdapter adapter1 = new SignListAdapter(context, items1, settings.getBoolean("v1", true), settings.getBoolean("v2", true), settings.getBoolean("v3", true), settings.getBoolean("v4", false));
            ListView listView = new ListView(context);
            listView.setAdapter(adapter1);

            builder.setView(listView).setNegativeButton("Cancel", null).setPositiveButton("OK", (dialog, which) -> LegacyUtils.applySharedPrefEditor(settings.edit().putBoolean("v1", adapter1.v1).putBoolean("v2", adapter1.v2)
                    .putBoolean("v3", adapter1.v3).putBoolean("v4", adapter1.v4))).show();
        };
    }

    public static String radioGroupValue(RadioGroup group, String defaultValue) {
        int id = group.getCheckedRadioButtonId();
        if (id == -1) return defaultValue;
        android.widget.RadioButton rb = group.findViewById(id);
        Object tag = rb.getTag();
        return (tag == null) ? defaultValue : tag.toString();
    }

    public android.widget.RadioButton makeRadioButton(String value, String label) {
        android.widget.RadioButton rb = new android.widget.RadioButton(context);
        rb.setText(label);
        rb.setId(android.view.View.generateViewId());
        rb.setTag(value);
        return rb;
    }

    public static void selectRadioByValue(RadioGroup group, String valueToSelect) {
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof android.widget.RadioButton) {
                android.widget.RadioButton rb = (android.widget.RadioButton) child;
                Object tag = rb.getTag();
                if (tag != null && tag.toString().equals(valueToSelect)) {
                    group.check(rb.getId());
                    return;
                }
            }
        }
        if (group.getCheckedRadioButtonId() == -1 && group.getChildCount() > 0) {
            android.widget.RadioButton first = (android.widget.RadioButton) group.getChildAt(0);
            group.check(first.getId());
        }
    }

    public void styleEditText(EditText editText) {
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.GRAY);
    }

    public void scrollTextView(TextView textView) {
        textView.setSingleLine(true);

        textView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        textView.setMarqueeRepeatLimit(-1);
        textView.setHorizontallyScrolling(true);
        textView.setSelected(true); // Start without focus
    }

    public TextView getTitle(String text) {
        TextView title = new TextView(context);
        title.setText(text);
        title.setBackgroundColor(Color.TRANSPARENT);
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(16,16,16,16);
        return title;
    }

    public LinearLayout getProperties() {
        LinearLayout parentLayout = new LinearLayout(context);
        parentLayout.setOrientation(LinearLayout.HORIZONTAL);
        parentLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        LinearLayout firstVerticalLayout = new LinearLayout(context);
        firstVerticalLayout.setOrientation(LinearLayout.VERTICAL);
        firstVerticalLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        String[] labels = {"Name", "Parent", "Type", "Size", "Modified", "Permissions", "Owner", "Group"};
        for (String label : labels) {
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textView.setText(label);
            textView.setTextColor(Color.WHITE);
            textView.setPadding(10, 10, 10, 10);
            firstVerticalLayout.addView(textView);
        }

        LinearLayout secondVerticalLayout = new LinearLayout(context);
        secondVerticalLayout.setOrientation(LinearLayout.VERTICAL);
        secondVerticalLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
        ));

        for (int i = 0; i < labels.length; i++) {
            TextView textView = new TextView(context);
            textView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textView.setTextColor(Color.WHITE);
            textView.setPadding(10, 10, 10, 10);
            secondVerticalLayout.addView(textView);
        }

        parentLayout.addView(firstVerticalLayout);
        parentLayout.addView(secondVerticalLayout);
        return parentLayout;
    }
}
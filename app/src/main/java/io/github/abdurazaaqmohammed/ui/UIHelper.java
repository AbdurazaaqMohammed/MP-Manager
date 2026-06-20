package io.github.abdurazaaqmohammed.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.reandroid.archive.ArchiveFile;

import java.io.File;
import java.io.IOException;

public class UIHelper {
    private final Context context;
    public UIHelper (Context context) {
        this.context = context;
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
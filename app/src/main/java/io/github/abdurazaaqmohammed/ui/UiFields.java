package io.github.abdurazaaqmohammed.ui;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class UiFields {

    private UiFields() {
    }

    public static TextInputLayout box(Context context, String hint) {
        TextInputLayout layout = new TextInputLayout(context);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        if (hint != null) layout.setHint(hint);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    public static TextInputEditText field(TextInputLayout layout, int inputType) {
        TextInputEditText edit = new TextInputEditText(layout.getContext());
        if (inputType != 0) edit.setInputType(inputType);
        layout.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return edit;
    }

    public static TextInputLayout wrap(Context context, EditText edit, String hint, int padDp) {
        if (edit.getParent() instanceof ViewGroup) {
            ((ViewGroup) edit.getParent()).removeView(edit);
        }
        String useHint = hint != null ? hint : (edit.getHint() == null ? null : edit.getHint().toString());
        edit.setHint(null);
        TextInputLayout layout = box(context, useHint);
        layout.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        float density = context.getResources().getDisplayMetrics().density;
        int p = (int) (padDp * density);
        layout.setPadding(p, p / 2, p, p / 2);
        return layout;
    }
}

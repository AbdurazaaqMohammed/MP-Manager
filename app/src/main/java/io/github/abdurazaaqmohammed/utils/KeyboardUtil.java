package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

public final class KeyboardUtil {
    private KeyboardUtil() {
    }

    public static void focusOnShow(AlertDialog dialog, EditText input, boolean selectAll) {
        if (dialog == null || input == null) return;
        try {
            if (dialog.getWindow() != null)
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        } catch (Exception ignored) {
        }
        dialog.setOnShowListener(d -> {
            try {
                input.requestFocus();
                if (selectAll && input.length() > 0) input.setSelection(0, input.length());
                InputMethodManager imm = (InputMethodManager) input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {
            }
        });
    }
}

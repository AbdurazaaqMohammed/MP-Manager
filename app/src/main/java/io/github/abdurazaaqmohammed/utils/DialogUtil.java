package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.core.ui.UIKit;

public class DialogUtil {
    private final Activity context;

    public DialogUtil(Activity c) {
        this.context = c;
    }

    public MaterialAlertDialogBuilder getDialogBuilder() {
        return UIKit.dialog(context);
    }

    public void styleAlertDialog(androidx.appcompat.app.AlertDialog ad) {
        context.runOnUiThread(ad::show);
    }
}

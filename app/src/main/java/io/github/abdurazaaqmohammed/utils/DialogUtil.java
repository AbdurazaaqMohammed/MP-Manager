package io.github.abdurazaaqmohammed.utils;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DialogUtil {
    private final android.app.Activity context;

    public DialogUtil(android.app.Activity c) {
        this.context = c;
    }

    public MaterialAlertDialogBuilder getDialogBuilder() {
        return new MaterialAlertDialogBuilder(context);
    }

    public void styleAlertDialog(AlertDialog ad) {
        context.runOnUiThread(ad::show);
    }
}
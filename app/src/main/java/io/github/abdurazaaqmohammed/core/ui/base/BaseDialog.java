package io.github.abdurazaaqmohammed.core.ui.base;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Single place for dialog construction. Keeps MaterialAlertDialogBuilder
 * usage and view inflation consistent across features.
 */
public final class BaseDialog {

    private BaseDialog() {
    }

    public static MaterialAlertDialogBuilder builder(@NonNull Context context) {
        return new MaterialAlertDialogBuilder(context);
    }

    public static View inflate(@NonNull Context context, @LayoutRes int layout) {
        return LayoutInflater.from(context).inflate(layout, null, false);
    }
}

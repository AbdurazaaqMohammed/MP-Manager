package io.github.abdurazaaqmohammed.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.MPManager.R;

import android.content.res.Resources;

public class DialogUtil {
    private final android.app.Activity context;
    private final int theme;
    private final Resources rss;

    public DialogUtil(android.app.Activity c) {
        this.context = c;

        this.rss = c.getResources(); // rss;
        boolean dark = (rss.getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        this.theme = android.preference.PreferenceManager.getDefaultSharedPreferences(c).getInt("theme",
                dark ? R.style.Theme_MyApp_Dark
                        : R.style.Theme_MyApp_Light);

    }

    public AlertDialog getProgressDialog(boolean indeterminate) {
        View progressView = LayoutInflater.from(context).inflate(R.layout.progress_dialog, null, false);
        ProgressBar progressBar = progressView.findViewById(R.id.progressBar);
        progressBar.setIndeterminate(indeterminate);
        return getDialogBuilder().setView(progressView).create();
    }

    public MaterialAlertDialogBuilder getDialogBuilder() {
        return new MaterialAlertDialogBuilder(context);
    }


    public void styleAlertDialog(AlertDialog ad) {
        context.runOnUiThread(ad::show);
    }
}
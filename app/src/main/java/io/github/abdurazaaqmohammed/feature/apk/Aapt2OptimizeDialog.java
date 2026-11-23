package io.github.abdurazaaqmohammed.feature.apk;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.ui.main.UIHelper;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

final class Aapt2OptimizeDialog {
    private Aapt2OptimizeDialog() {}

    static void show(MainActivity activity, UIHelper uiHelper, File input) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_aapt2_optimize, null);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        CheckBox sign = view.findViewById(R.id.aapt2_autosign);
        sign.setChecked(prefs.getBoolean("autosign", true));
        sign.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean("autosign", checked).apply());
        view.findViewById(R.id.aapt2_sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.aapt2_optimize)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.opt, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final Aapt2Optimizer.Options options;
            try {
                options = new Aapt2Optimizer.Options(text(view, R.id.aapt2_densities),
                        text(view, R.id.aapt2_configurations), text(view, R.id.aapt2_abis),
                        text(view, R.id.aapt2_min_sdk), text(view, R.id.aapt2_target_sdk));
            } catch (IllegalArgumentException e) {
                Extensions.showMessage(activity, e.getMessage());
                return;
            }
            boolean shouldSign = sign.isChecked();
            dialog.dismiss();
            if (shouldSign) SignWrapper.requireAuth(activity, wrapper -> start(activity, input, options, wrapper));
            else start(activity, input, options, null);
        }));
        dialog.show();
    }

    private static String text(View view, int id) {
        TextInputEditText field = view.findViewById(id);
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static void start(MainActivity activity, File input, Aapt2Optimizer.Options options, SignWrapper signer) {
        ProgressManager progress = new ProgressManager(activity, true).show();
        progress.setText(activity.getString(R.string.aapt2_optimize));
        new Thread(() -> {
            File output = null;
            try {
                output = Aapt2Optimizer.optimize(activity, input, options);
                if (signer != null) Aapt2Optimizer.signOutput(activity, output, signer);
                File result = output;
                progress.dismiss();
                activity.handler.post(() -> {
                    activity.refreshAllPanes();
                    Extensions.showMessage(activity, activity.getString(R.string.aapt2_opt_done, result.getName()));
                });
            } catch (Exception e) {
                if (output != null) output.delete();
                progress.dismiss();
                new ErrorUtil(activity).showError(e);
            }
        }, "aapt2-optimize").start();
    }
}

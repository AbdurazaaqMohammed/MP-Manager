package io.github.abdurazaaqmohammed.features.apk;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.AppReinstaller;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.InstallerConfig;

/**
 * Pre-install comparison sheet.
 *
 * <p>Shown depending on the "Show installation info" setting
 * ({@link InstallerConfig#getInstallInfoMode}): always, only when the candidate
 * is signed by a different key than the installed copy, or never.
 *
 * <p>When the signatures differ the install itself will be refused by the
 * system, so the dialog also offers the two things a user can actually do about
 * it: install anyway (for people running Core Patch or a similar signature
 * bypass), or uninstall the existing app first, with its data preserved when
 * root makes that possible.
 */
public final class InstallInfoDialog {

    public static final String CORE_PATCH_URL =
            "https://github.com/LSPosed/CorePatch";

    private InstallInfoDialog() {
    }

    /** True when the sheet should be shown for this candidate. */
    public static boolean shouldShow(Context context, ApkInstallAnalysis analysis) {
        if (analysis == null) return false;
        switch (InstallerConfig.getInstallInfoMode(context)) {
            case InstallerConfig.INFO_MODE_ALWAYS:
                return true;
            case InstallerConfig.INFO_MODE_WHEN_SIGNATURE_DIFFERS:
                return analysis.isDifferentSigner();
            case InstallerConfig.INFO_MODE_NEVER:
            default:
                return false;
        }
    }

    public static void show(Activity activity, DialogUtil dialogUtil, String appName,
                            ApkInstallAnalysis analysis, Runnable onInstall) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_install_info, null, false);
        TextView summary = view.findViewById(R.id.install_info_summary);
        TextView details = view.findViewById(R.id.install_info_details);
        TextView warning = view.findViewById(R.id.install_info_warning);
        TextView uninstallNote = view.findViewById(R.id.install_info_uninstall_note);
        MaterialCheckBox saveData = view.findViewById(R.id.install_info_save_data);
        MaterialCheckBox dontShow = view.findViewById(R.id.install_info_dont_show);
        var corePatch = view.findViewById(R.id.install_info_core_patch);

        summary.setText(activity.getString(R.string.apk_compare_title, appName));
        details.setText(analysis.details(activity));

        boolean different = analysis.isDifferentSigner();
        if (different) {
            warning.setVisibility(View.VISIBLE);
            warning.setText(R.string.apk_compare_signature_warning);
            corePatch.setVisibility(View.VISIBLE);
            corePatch.setOnClickListener(v -> openCorePatch(activity));
        }

        boolean canSave = different && analysis.installed && AppReinstaller.canSaveData(activity);
        uninstallNote.setVisibility(different ? View.VISIBLE : View.GONE);
        if (different) {
            uninstallNote.setText(canSave
                    ? activity.getString(R.string.install_uninstall_note_root)
                    : activity.getString(R.string.install_uninstall_note_no_data));
        }
        if (canSave) {
            saveData.setVisibility(View.VISIBLE);
            saveData.setChecked(true);
        } else {
            saveData.setVisibility(View.GONE);
        }

        MaterialAlertDialogBuilder builder = dialogUtil.getDialogBuilder()
                .setTitle(activity.getString(R.string.apk_compare_title, appName))
                .setView(view)
                .setPositiveButton(R.string.install, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (different && analysis.installed) {
            builder.setNeutralButton(R.string.uninstall_and_install, null);
        }
        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            applyDontShowAgain(activity, dontShow);
            dialog.dismiss();
            onInstall.run();
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            applyDontShowAgain(activity, dontShow);
            dialog.dismiss();
        });
        if (different && analysis.installed) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                boolean keepData = canSave && saveData.isChecked();
                applyDontShowAgain(activity, dontShow);
                dialog.dismiss();
                uninstallThenInstall(activity, analysis, keepData, onInstall);
            });
        }
    }

    private static void applyDontShowAgain(Context context, MaterialCheckBox dontShow) {
        if (dontShow != null && dontShow.isChecked()) {
            InstallerConfig.setInstallInfoMode(context, InstallerConfig.INFO_MODE_NEVER);
        }
    }

    private static void openCorePatch(Activity activity) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(CORE_PATCH_URL));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/LSPosed/CorePatch")));
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Backs the app's data up (root only), removes the package, installs the
     * candidate and restores the data. The backup is always cleaned up.
     */
    private static void uninstallThenInstall(Activity activity, ApkInstallAnalysis analysis,
                                             boolean keepData, Runnable onInstall) {
        String pkg = analysis.packageName;
        if (pkg == null) {
            onInstall.run();
            return;
        }
        new Thread(() -> {
            String backup = null;
            String dataOwner = null;
            if (keepData) {
                dataOwner = AppReinstaller.readDataOwner(activity, pkg);
                backup = AppReinstaller.backupData(activity, pkg);
                if (backup == null) {
                    // Saving is not possible; say so rather than destroying the
                    // data silently.
                    activity.runOnUiThread(() ->
                            new ErrorUtil(activity).showError(
                                    new Exception(activity.getString(R.string.install_save_data_failed))));
                    return;
                }
            }
            final String archive = backup;
            final String owner = dataOwner;
            activity.runOnUiThread(() -> AppReinstaller.uninstall(activity, pkg, () -> {
                onInstall.run();
                if (archive == null) return;
                new Thread(() -> {
                    // Give the package manager a moment to finish removing the
                    // old data directory before putting the archive back.
                    try {
                        Thread.sleep(1200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    AppReinstaller.restoreData(activity, pkg, archive, owner);
                    AppReinstaller.deleteBackup(activity, archive);
                }).start();
            }));
        }).start();
    }
}
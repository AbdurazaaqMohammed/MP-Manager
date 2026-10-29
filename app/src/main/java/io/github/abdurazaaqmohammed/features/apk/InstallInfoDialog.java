package io.github.abdurazaaqmohammed.features.apk;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.AppReinstaller;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.InstallerConfig;
import io.github.abdurazaaqmohammed.utils.SectionCollapse;

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
        View view = buildView(activity, analysis, appName);

        MaterialAlertDialogBuilder builder = dialogUtil.getDialogBuilder()
                .setTitle(activity.getString(R.string.apk_compare_title, appName))
                .setView(view)
                .setPositiveButton(R.string.install, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (analysis.isDifferentSigner() && analysis.installed) {
            builder.setNeutralButton(R.string.uninstall_and_install, null);
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        DialogUtil.keepButtonsHorizontal(dialog);

        MaterialCheckBox dontShow = view.findViewWithTag("dontShow");
        MaterialCheckBox saveData = view.findViewWithTag("saveData");

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            applyDontShowAgain(activity, dontShow);
            dialog.dismiss();
            onInstall.run();
        });
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            applyDontShowAgain(activity, dontShow);
            dialog.dismiss();
        });
        if (analysis.isDifferentSigner() && analysis.installed) {
            boolean canSave = AppReinstaller.canSaveData(activity);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                boolean keepData = canSave && saveData != null && saveData.isChecked();
                applyDontShowAgain(activity, dontShow);
                dialog.dismiss();
                uninstallThenInstall(activity, analysis, keepData, onInstall);
            });
        }
    }

    private static View buildView(Activity activity, ApkInstallAnalysis analysis, String appName) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 20), dp(activity, 8), dp(activity, 20), dp(activity, 8));

        // Summary row: version comparison at a glance, always visible.
        TextView summary = new TextView(activity);
        String candidateVer = (TextUtils.isEmpty(analysis.versionName) ? "" : analysis.versionName)
                + (analysis.versionCode > 0 ? " (" + analysis.versionCode + ")" : "");
        String summaryText = analysis.summary(activity);
        if (!TextUtils.isEmpty(candidateVer) && analysis.installed) {
            summary.setText(candidateVer + "\n" + summaryText);
        } else {
            summary.setText(summaryText);
        }
        summary.setTextSize(15f);
        summary.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(summary, matchWrap());

        root.addView(spacer(activity, 8));

        // Compact Signature and Hash rows (one line each, clickable for details)
        root.addView(buildCompactSignatureRow(activity, analysis), matchWrap());
        root.addView(buildCompactHashRow(activity, analysis), matchWrap());

        if (analysis.isDifferentSigner()) {
            root.addView(spacer(activity, 8));
            root.addView(buildWarningCard(activity, analysis), matchWrap());
        }

        root.addView(spacer(activity, 8));

        // Options on a single horizontal line
        LinearLayout optionsRow = new LinearLayout(activity);
        optionsRow.setOrientation(LinearLayout.HORIZONTAL);
        optionsRow.setGravity(Gravity.CENTER_VERTICAL);
        optionsRow.setLayoutParams(matchWrap());

        boolean canSave = analysis.isDifferentSigner() && analysis.installed && AppReinstaller.canSaveData(activity);
        if (canSave) {
            MaterialCheckBox saveData = new MaterialCheckBox(activity);
            saveData.setTag("saveData");
            saveData.setText(R.string.install_save_data);
            saveData.setChecked(true);
            saveData.setTextSize(13f);
            optionsRow.addView(saveData, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        MaterialCheckBox dontShow = new MaterialCheckBox(activity);
        dontShow.setTag("dontShow");
        dontShow.setText(R.string.install_dont_show_again);
        dontShow.setTextSize(13f);
        if (canSave) {
            optionsRow.addView(dontShow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            optionsRow.addView(dontShow, matchWrap());
        }
        root.addView(optionsRow);

        scroll.addView(root);
        return scroll;
    }

    private static View buildCompactSignatureRow(Activity activity, ApkInstallAnalysis analysis) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(activity, 3), 0, dp(activity, 3));

        TextView label = new TextView(activity);
        label.setText(activity.getString(R.string.signature) + ": ");
        label.setTextSize(14f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label);

        boolean isDiff = analysis.isDifferentSigner();
        TextView status = new TextView(activity);
        status.setText(isDiff ? R.string.signature_different : R.string.signature_identical);
        status.setTextSize(14f);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(isDiff ? 0xFFE53935 : 0xFF43A047);
        status.setClickable(true);
        status.setFocusable(true);
        TypedValue tv = new TypedValue();
        try {
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            status.setBackgroundResource(tv.resourceId);
        } catch (Exception ignored) {}
        status.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 2));
        status.setOnClickListener(v -> showSignatureDetailsDialog(activity, analysis));
        row.addView(status);

        return row;
    }

    private static View buildCompactHashRow(Activity activity, ApkInstallAnalysis analysis) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(activity, 3), 0, dp(activity, 3));

        TextView label = new TextView(activity);
        label.setText("Hash: ");
        label.setTextSize(14f);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label);

        boolean same = analysis.sameBytes;
        TextView status = new TextView(activity);
        status.setText(same ? R.string.apk_compare_same_file : R.string.apk_compare_different_file);
        status.setTextSize(14f);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        status.setTextColor(same ? 0xFF43A047 : 0xFFE53935);
        status.setClickable(true);
        status.setFocusable(true);
        TypedValue tv = new TypedValue();
        try {
            activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            status.setBackgroundResource(tv.resourceId);
        } catch (Exception ignored) {}
        status.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 2));
        status.setOnClickListener(v -> showHashDetailsDialog(activity, analysis));
        row.addView(status);

        return row;
    }

    private static void showSignatureDetailsDialog(Activity activity, ApkInstallAnalysis analysis) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        content.setPadding(pad, pad, pad, pad);

        TextView candidateTitle = new TextView(activity);
        candidateTitle.setText(activity.getString(R.string.apk_compare_title_candidate));
        candidateTitle.setTypeface(Typeface.DEFAULT_BOLD);
        candidateTitle.setTextSize(14f);
        candidateTitle.setTextColor(colorPrimary(activity));
        content.addView(candidateTitle);

        addRow(content, activity.getString(R.string.sig_owner),
                TextUtils.isEmpty(analysis.signerSubject) ? activity.getString(android.R.string.unknownName) : analysis.signerSubject);
        addRow(content, activity.getString(R.string.cert_sha256),
                analysis.signerCertSha256 == null ? activity.getString(android.R.string.unknownName) : analysis.signerCertSha256);

        content.addView(spacer(activity, 12));

        TextView installedTitle = new TextView(activity);
        installedTitle.setText(activity.getString(R.string.apk_compare_title_installed));
        installedTitle.setTypeface(Typeface.DEFAULT_BOLD);
        installedTitle.setTextSize(14f);
        installedTitle.setTextColor(colorPrimary(activity));
        content.addView(installedTitle);

        if (analysis.installed) {
            addRow(content, activity.getString(R.string.sig_owner),
                    TextUtils.isEmpty(analysis.installedSignerSubject) ? activity.getString(android.R.string.unknownName) : analysis.installedSignerSubject);
            addRow(content, activity.getString(R.string.cert_sha256),
                    analysis.installedSignerCertSha256 == null ? activity.getString(android.R.string.unknownName) : analysis.installedSignerCertSha256);
        } else {
            TextView notInst = new TextView(activity);
            notInst.setText(R.string.apk_compare_new_install);
            notInst.setTextSize(13f);
            content.addView(notInst);
        }

        scroll.addView(content);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.install_info_signature)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private static void showHashDetailsDialog(Activity activity, ApkInstallAnalysis analysis) {
        ScrollView scroll = new ScrollView(activity);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 16);
        content.setPadding(pad, pad, pad, pad);

        TextView candidateTitle = new TextView(activity);
        candidateTitle.setText(activity.getString(R.string.apk_compare_title_candidate));
        candidateTitle.setTypeface(Typeface.DEFAULT_BOLD);
        candidateTitle.setTextSize(14f);
        candidateTitle.setTextColor(colorPrimary(activity));
        content.addView(candidateTitle);

        addRow(content, activity.getString(R.string.apk_compare_sha256_label),
                TextUtils.isEmpty(analysis.checksum) ? activity.getString(android.R.string.unknownName) : analysis.checksum);

        content.addView(spacer(activity, 12));

        TextView installedTitle = new TextView(activity);
        installedTitle.setText(activity.getString(R.string.apk_compare_title_installed));
        installedTitle.setTypeface(Typeface.DEFAULT_BOLD);
        installedTitle.setTextSize(14f);
        installedTitle.setTextColor(colorPrimary(activity));
        content.addView(installedTitle);

        if (analysis.installed && !TextUtils.isEmpty(analysis.installedChecksum)) {
            addRow(content, activity.getString(R.string.apk_compare_installed_label), analysis.installedChecksum);
        } else {
            TextView notInst = new TextView(activity);
            notInst.setText(R.string.apk_compare_new_install);
            notInst.setTextSize(13f);
            content.addView(notInst);
        }

        scroll.addView(content);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.install_info_hashes)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .setNegativeButton(android.R.string.copy, (d, w) -> {
                    String textToCopy = "Candidate SHA-256: " + analysis.checksum +
                            (analysis.installed ? "\nInstalled SHA-256: " + analysis.installedChecksum : "");
                    CopyUtil.copyToClipboard(activity, textToCopy);
                })
                .show();
    }

    private static View buildWarningCard(Activity activity, ApkInstallAnalysis analysis) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));

        TextView warning = new TextView(activity);
        warning.setText(R.string.apk_compare_signature_warning);
        warning.setTextColor(errorColor(activity));
        warning.setTextSize(13f);
        content.addView(warning, matchWrap());

        TextView corePatch = new TextView(activity);
        corePatch.setText(R.string.install_core_patch);
        corePatch.setTextColor(colorPrimary(activity));
        corePatch.setTextSize(13f);
        corePatch.setTypeface(Typeface.DEFAULT_BOLD);
        corePatch.setClickable(true);
        corePatch.setPadding(0, dp(activity, 4), 0, dp(activity, 4));
        corePatch.setOnClickListener(v -> openCorePatch(activity));
        content.addView(corePatch, matchWrap());

        boolean canSave = analysis.installed && AppReinstaller.canSaveData(activity);
        TextView uninstallNote = new TextView(activity);
        uninstallNote.setText(canSave
                ? activity.getString(R.string.install_uninstall_note_root)
                : activity.getString(R.string.install_uninstall_note_no_data));
        uninstallNote.setTextSize(12f);
        uninstallNote.setAlpha(0.8f);
        content.addView(uninstallNote, matchWrap());

        MaterialCardView card = new MaterialCardView(activity);
        card.addView(content);
        return card;
    }

    private static void addRow(LinearLayout parent, String label, String value) {
        Context context = parent.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(context, 3), 0, dp(context, 3));
        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(13f);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        int minWidth = dp(context, 130);
        labelView.setMinWidth(minWidth);
        row.addView(labelView, new LinearLayout.LayoutParams(minWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = new TextView(context);
        valueView.setText(TextUtils.isEmpty(value) ? "—" : value);
        valueView.setTextSize(13f);
        valueView.setTextIsSelectable(true);
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, matchWrap());
    }

    private static View spacer(Context context, int dpHeight) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, dpHeight)));
        return v;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
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

    private static String shortHash(String full) {
        if (full == null) return "—";
        String flat = full.replace(":", "");
        return flat.length() <= 24 ? flat : flat.substring(0, 24) + "…";
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int colorPrimary(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(
                androidx.appcompat.R.attr.colorPrimary, value, true)) {
            if (value.resourceId != 0) return ContextCompat.getColor(context, value.resourceId);
            if (value.data != 0) return value.data;
        }
        return 0xFF6750A4;
    }

    /** Material colourError when the theme defines it, red otherwise. */
    private static int errorColor(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorError, value, true)) {
            if (value.resourceId != 0) return ContextCompat.getColor(context, value.resourceId);
            if (value.data != 0) return value.data;
        }
        return 0xFFB3261E;
    }
}
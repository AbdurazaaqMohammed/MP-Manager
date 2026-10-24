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

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.AppReinstaller;
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

        // Signature: dedupe owner/issuer when they match, and show the cert hash.
        root.addView(buildSignatureCard(activity, analysis), matchWrap());
        root.addView(spacer(activity, 8));

        // Hashes of both archives.
        root.addView(buildHashesCard(activity, analysis), matchWrap());

        if (analysis.isDifferentSigner()) {
            root.addView(spacer(activity, 8));
            root.addView(buildWarningCard(activity, analysis), matchWrap());
        }

        root.addView(spacer(activity, 8));

        // Options that stay visible so they are never lost under a collapsed header.
        if (analysis.isDifferentSigner() && analysis.installed && AppReinstaller.canSaveData(activity)) {
            MaterialCheckBox saveData = new MaterialCheckBox(activity);
            saveData.setTag("saveData");
            saveData.setText(R.string.install_save_data);
            saveData.setChecked(true);
            root.addView(saveData, matchWrap());
        }
        MaterialCheckBox dontShow = new MaterialCheckBox(activity);
        dontShow.setTag("dontShow");
        dontShow.setText(R.string.install_dont_show_again);
        root.addView(dontShow, matchWrap());

        scroll.addView(root);
        return scroll;
    }

    private static View buildSignatureCard(Activity activity, ApkInstallAnalysis analysis) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));

        TextView title = new TextView(activity);
        title.setText(R.string.install_info_signature);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(15f);
        content.addView(title);

        String owner = analysis.signerSubject;
        String issuer = analysis.installedSignerSubject;

        addRow(content, activity.getString(R.string.sig_owner),
                TextUtils.isEmpty(owner) ? activity.getString(android.R.string.unknownName) : owner);
        addRow(content, activity.getString(R.string.cert_sha256),
                analysis.signerCertSha256 == null ? activity.getString(android.R.string.unknownName)
                        : shortHash(analysis.signerCertSha256));

        MaterialCardView card = new MaterialCardView(activity);
        card.addView(content);
        SectionCollapse.applyToCard(card,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity),
                "install_info_signature");
        return card;
    }

    private static View buildHashesCard(Activity activity, ApkInstallAnalysis analysis) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));

        TextView title = new TextView(activity);
        title.setText(R.string.install_info_hashes);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(15f);
        content.addView(title);

        addRow(content, activity.getString(R.string.apk_compare_sha256_label),
                TextUtils.isEmpty(analysis.checksum) ? activity.getString(android.R.string.unknownName)
                        : shortHash(analysis.checksum));
        if (analysis.installed && !TextUtils.isEmpty(analysis.installedChecksum)) {
            addRow(content, activity.getString(R.string.apk_compare_installed_label),
                    shortHash(analysis.installedChecksum));
        }
        if (analysis.sameBytes) {
            TextView same = new TextView(activity);
            same.setText(R.string.apk_compare_same_file);
            same.setTextColor(0xFF2E7D32);
            same.setTextSize(12f);
            same.setPadding(0, dp(activity, 3), 0, 0);
            content.addView(same, matchWrap());
        }

        MaterialCardView card = new MaterialCardView(activity);
        card.addView(content);
        SectionCollapse.applyToCard(card,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity),
                "install_info_hashes");
        return card;
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
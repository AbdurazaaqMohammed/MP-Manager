package io.github.abdurazaaqmohammed.feature.apk.privacy;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.apk.APKLogger;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.apk.SignWrapper;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Interactive dialog displaying the Privacy & Invasiveness Audit report,
 * threat taxonomy, root requirement badges, and surgical sanitization pipeline.
 */
public final class PrivacyAuditDialog {

    private PrivacyAuditDialog() {}

    public static void showForApk(MainActivity context, File apkFile, String fileName) {
        if (apkFile == null || !apkFile.isFile()) return;

        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText("Auditing APK privacy...");

        new Thread(() -> {
            try {
                PrivacyAuditReport report = PrivacyScannerEngine.scanApk(apkFile);
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    if (report.threats.isEmpty()) {
                        Extensions.showMessage(context, context.getString(R.string.privacy_no_threats));
                        return;
                    }
                    displayDialog(context, apkFile, report);
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                });
            }
        }).start();
    }

    private static void displayDialog(MainActivity context, File apkFile, PrivacyAuditReport report) {
        int pad = Extensions.dp2px(context, 16);
        int padSmall = Extensions.dp2px(context, 8);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, padSmall, pad, padSmall);

        // Header: Risk Score banner
        LinearLayout scoreBox = new LinearLayout(context);
        scoreBox.setOrientation(LinearLayout.VERTICAL);
        scoreBox.setPadding(padSmall, padSmall, padSmall, padSmall);

        int riskColor = switch (report.getRiskLevel()) {
            case CRITICAL -> Color.parseColor("#D32F2F"); // Red
            case HIGH -> Color.parseColor("#F57C00");     // Orange
            case MODERATE -> Color.parseColor("#FBC02D"); // Yellow
            case LOW -> Color.parseColor("#388E3C");      // Green
        };

        TextView scoreTitle = new TextView(context);
        scoreTitle.setText(String.format("Invasiveness Risk Score: %d/100 (%s)",
                report.overallScore, report.getRiskLevel().name()));
        scoreTitle.setTextSize(17);
        scoreTitle.setTypeface(Typeface.DEFAULT_BOLD);
        scoreTitle.setTextColor(riskColor);
        scoreBox.addView(scoreTitle);

        TextView statsSummary = new TextView(context);
        statsSummary.setText(String.format(
                "• %s\n• %s\n• %s\n• %s",
                context.getString(R.string.privacy_hw_score, report.hardwareScore),
                context.getString(R.string.privacy_track_score, report.trackerScore),
                context.getString(R.string.privacy_surv_score, report.surveillanceScore),
                context.getString(R.string.privacy_log_score, report.logLeakScore)
        ));
        statsSummary.setTextSize(13);
        statsSummary.setPadding(0, padSmall, 0, 0);
        scoreBox.addView(statsSummary);

        root.addView(scoreBox);
        root.addView(createDivider(context));

        // Threats Header
        TextView threatsHeader = new TextView(context);
        threatsHeader.setText(String.format("Detected Threats & Invasiveness Capabilities (%d):", report.threats.size()));
        threatsHeader.setTextSize(14);
        threatsHeader.setTypeface(Typeface.DEFAULT_BOLD);
        threatsHeader.setPadding(0, padSmall, 0, padSmall);
        root.addView(threatsHeader);

        // Threat Items List
        for (DetectedThreat threat : report.threats) {
            LinearLayout itemRow = new LinearLayout(context);
            itemRow.setOrientation(LinearLayout.HORIZONTAL);
            itemRow.setGravity(Gravity.CENTER_VERTICAL);
            itemRow.setPadding(0, padSmall / 2, 0, padSmall / 2);

            MaterialCheckBox cb = new MaterialCheckBox(context);
            cb.setChecked(threat.selectedForExcision);
            cb.setOnCheckedChangeListener((btn, isChecked) -> threat.selectedForExcision = isChecked);
            itemRow.addView(cb);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView itemTitle = new TextView(context);
            itemTitle.setText(String.format("%s %s", threat.getRootBadge(), threat.title));
            itemTitle.setTextSize(14);
            itemTitle.setTypeface(Typeface.DEFAULT_BOLD);
            textCol.addView(itemTitle);

            TextView itemDesc = new TextView(context);
            itemDesc.setText(String.format("%s (%d occurrences)", threat.description, threat.occurrences));
            itemDesc.setTextSize(12);
            textCol.addView(itemDesc);

            itemRow.addView(textCol);
            root.addView(itemRow);
        }

        root.addView(createDivider(context));

        // Auto-sign option
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        MaterialCheckBox autosign = new MaterialCheckBox(context);
        final boolean[] sign = new boolean[]{settings.getBoolean("autosign", true)};
        autosign.setText("Auto-sign output APK after sanitization");
        autosign.setChecked(sign[0]);
        autosign.setOnCheckedChangeListener((bv, checked) -> {
            sign[0] = checked;
            settings.edit().putBoolean("autosign", checked).apply();
        });
        root.addView(autosign);

        // ScrollView container
        int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.70);
        ScrollView scroller = new ScrollView(context);
        scroller.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.privacy_audit_title)
                .setView(scroller)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.privacy_sanitize_btn, (d, w) -> {
                    sanitizeAndSave(context, apkFile, report, sign[0]);
                })
                .show();
    }

    private static void sanitizeAndSave(MainActivity context, File apkFile, PrivacyAuditReport report, boolean doSign) {
        PrivacyOptions options = new PrivacyOptions();
        options.stripLogging = isAnySelected(report.threats, DetectedThreat.Category.LOG_LEAK);
        options.emptyTelephonyIds = isAnySelected(report.threats, DetectedThreat.Category.HARDWARE_FINGERPRINT);
        options.neutralizeAdIds = isAnySelected(report.threats, DetectedThreat.Category.HARDWARE_FINGERPRINT);
        options.maskDeveloperMode = isAnySelected(report.threats, DetectedThreat.Category.SURVEILLANCE);

        File outFile = FileUtils.getUnusedFile(new File(apkFile.getParentFile(),
                FilenameUtils.getBaseName(apkFile.getName()) + "_sanitized.apk"));

        SignWrapper[] wrapper = new SignWrapper[1];

        Runnable doSanitize = () -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    pm.setText("Sanitizing DEX bytecode...");
                    PrivacyStats stats = PrivacyBytecodeEngine.sanitizeApk(apkFile, outFile, options, logger);

                    if (doSign && wrapper[0] != null) {
                        pm.setText("Signing sanitized APK...");
                        wrapper[0].signApk(outFile);
                    }

                    pm.dismiss();
                    context.runOnUiThread(() -> {
                        Extensions.showMessage(context, context.getString(
                                R.string.privacy_done, stats.totalMutations()));
                        context.refreshAllPanes();
                    });
                } catch (Exception e) {
                    pm.dismiss();
                    context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
                }
            }).start();
        };

        if (doSign) {
            SignWrapper.requireAuth(context, sw -> {
                wrapper[0] = sw;
                doSanitize.run();
            });
        } else {
            doSanitize.run();
        }
    }

    private static boolean isAnySelected(List<DetectedThreat> threats, DetectedThreat.Category category) {
        for (DetectedThreat t : threats) {
            if (t.category == category && t.selectedForExcision) return true;
        }
        return false;
    }

    private static View createDivider(MainActivity context) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Extensions.dp2px(context, 1)));
        v.setBackgroundColor(Color.parseColor("#33888888"));
        int m = Extensions.dp2px(context, 8);
        ((LinearLayout.LayoutParams) v.getLayoutParams()).setMargins(0, m, 0, m);
        return v;
    }
}

package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.android.apksig.ApkVerifier;
import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.PairipRemoverUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.SignatureKillerUtil;
import io.github.abdurazaaqmohammed.utils.SignatureStripUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * APK signature workflows: kill verification, strip, health report,
 * certificate viewer, batch signing. Extracted from ApkToolsHandler.
 */
public class ApkSignatureTools {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final boolean pane1;

    public ApkSignatureTools(MainActivity context, DialogUtil dialogUtil, UIHelper uiHelper, boolean pane1) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
    }

    public void killSignatureVerification(File file, String fileName) {
        View layout = LayoutInflater.from(context).inflate(R.layout.dialog_kill_signature, null);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = new boolean[1];
        CompoundButton autosign = layout.findViewById(R.id.autosign);
        autosign.setText(R.string.auto_sign);
        autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());

        String[] killMethods = {"MT", "RePairip"};
        final int[] selectedMethod = {0};
        AutoCompleteTextView methodDropdown = layout.findViewById(R.id.method_dropdown);
        methodDropdown.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_dropdown_item_1line, killMethods));
        methodDropdown.setText(killMethods[0], false);
        methodDropdown.setOnItemClickListener((parent, view, position, id) -> selectedMethod[0] = position);

        layout.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());

        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.kill_signature_verification))
                .setMessage(context.getString(R.string.kill_signature_warning))
                .setView(layout)
                .setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                .setPositiveButton(context.getString(R.string.kill), (dialog2, which3) -> {
                    SignWrapper[] wrapper = new SignWrapper[1];
                    final Runnable doKill;
                    if (selectedMethod[0] == 1) {
                        doKill = () -> {
                            ProgressManager pm = new ProgressManager(context, true).show();
                            new Thread(() -> {
                                try {
                                    File result = PairipRemoverUtil.removePairip(context, file);
                                    if (sign[0]) wrapper[0].signApk(result);
                                    pm.dismiss();
                                    context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        };
                    } else {
                        doKill = () -> {
                            ProgressManager pm = new ProgressManager(context, true).show();
                            new Thread(() -> {
                                try {
                                    File result = SignatureKillerUtil.apply(context, file);
                                    if (sign[0]) wrapper[0].signApk(result);
                                    pm.dismiss();
                                    context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1, false));
                                } catch (Exception e) {
                                    pm.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        };
                    }
                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                        wrapper[0] = sw;
                        doKill.run();
                    }); else doKill.run();
                    }).show());
    }

    public void showCertificateDialog(File apkFile) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                List<X509Certificate> certs = CertUtil.getCertificates(apkFile);
                CharSequence text;
                if (certs == null || certs.isEmpty()) text = context.getString(R.string.no_signature_found);
                else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < certs.size(); i++) {
                        if (i > 0) sb.append("\n\n");
                        sb.append(context.getString(R.string.cert, i + 1)).append('\n');
                        sb.append(CertUtil.describe(certs.get(i)));
                    }
                    text = sb;
                }
                pm.dismiss();
                context.handler.post(() -> dialogUtil.getDialogBuilder()
                        .setTitle(R.string.view_certificate)
                        .setMessage(text)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(android.R.string.copy, (d, w) -> CopyUtil.copyToClipboard(context, text))
                        .show());
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void batchSignApks(List<File> apks) {
        SignWrapper.requireAuth(context, sw -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    for (int i = 0; i < apks.size(); i++) {
                        File apk = apks.get(i);
                        String msg = context.rss.getString(R.string.signing, apk.getName());
                        pm.setText(msg);
                        logger.logMessage(msg);
                        sw.signApk(apk);
                    }
                    pm.dismiss();
                    context.handler.post(() -> {
                        Extensions.showMessage(context, context.rss.getString(R.string.signed, apks.size() + " APKs"));
                        context.loadFolderInPane(apks.get(0).getParentFile(), pane1, false);
                    });
                } catch (Exception e) {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        });
    }

    public void removeSignature(File apk) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            boolean ok = SignatureStripUtil.strip(apk);
            pm.dismiss();
            context.handler.post(() -> {
                Extensions.showMessage(context, context.rss.getString(ok ? R.string.signature_removed : R.string.failed_to_remove_signature));
                if (ok) context.loadFolderInPane(apk.getParentFile(), pane1, false);
            });
        }).start();
    }

    public void showSignatureHealthDialog(File apk) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            String report = buildSignatureHealthReport(apk);
            pm.dismiss();
            context.handler.post(() -> {
                TextView tv = new TextView(context);
                tv.setText(report);
                tv.setTextIsSelectable(true);
                tv.setTextSize(13);
                tv.setTypeface(Typeface.MONOSPACE);
                int pad = dp(16);
                tv.setPadding(pad, pad, pad, pad);
                ScrollView scroll = new ScrollView(context);
                scroll.addView(tv);
                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(R.string.signature_health)
                        .setView(scroll)
                        .setNegativeButton(android.R.string.ok, null)
                        .setNeutralButton(R.string.re_sign, (d, w) -> SignatureKeyDialog.show(context, apk, false))
                        .setPositiveButton(R.string.fix_alignment, (d, w) -> {
                            ProgressManager pm2 = new ProgressManager(context, true).show();
                            new Thread(() -> {
                                try {
                                    ApkZipAlignUtil.ensureInstallable(apk);
                                    pm2.dismiss();
                                    context.handler.post(() -> showSignatureHealthDialog(apk));
                                } catch (Exception e) {
                                    pm2.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        })
                        .show());
            });
        }).start();
    }

    private String buildSignatureHealthReport(File apk) {
        StringBuilder sb = new StringBuilder();
        String issue = ApkZipAlignUtil.installIssue(apk);
        sb.append("Zipalign: ").append(issue == null ? "OK" : issue).append('\n');
        ApkVerifier.Result result = null;
        String verifyError = null;
        try {
            result = new ApkVerifier.Builder(apk).build().verify();
        } catch (Exception e) {
            verifyError = e.getMessage() != null ? e.getMessage() : e.toString();
        }
        String v1;
        String v2;
        if (verifyError != null) {
            v1 = "error: " + verifyError;
            v2 = "error: " + verifyError;
        } else {
            try {
                v1 = result.isVerifiedUsingV1Scheme() ? "verified" : "missing or invalid";
            } catch (Exception e) {
                v1 = "error: " + e.getMessage();
            }
            try {
                v2 = result.isVerifiedUsingV2Scheme() ? "verified" : "missing or invalid";
            } catch (Exception e) {
                v2 = "error: " + e.getMessage();
            }
        }
        sb.append("V1 (JAR): ").append(v1).append('\n');
        sb.append("V2 (APK Signature Scheme v2): ").append(v2).append('\n');
        String sha256 = "none";
        try {
            List<X509Certificate> certs = CertUtil.getCertificatesUnverified(apk);
            if (certs == null || certs.isEmpty()) certs = CertUtil.getCertificates(apk);
            if (certs != null && !certs.isEmpty()) sha256 = CertUtil.getSha256(certs.get(0));
        } catch (Exception e) {
            sha256 = "error: " + (e.getMessage() != null ? e.getMessage() : e.toString());
        }
        sb.append("Cert SHA-256: ").append(sha256);
        return sb.toString();
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

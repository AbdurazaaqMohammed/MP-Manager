package io.github.abdurazaaqmohammed.features.apk;

import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.apksig.ApkVerifier;
import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.SignatureStripUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
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


    public void showCertificateDialog(File apkFile) {
        SignatureInfoDialog.show(context, dialogUtil, apkFile);
    }

    public void batchSignApks(List<File> apks) {
        SignWrapper.requireAuth(context, sw -> {
            ProgressManager pm = new ProgressManager(context, true).show();
            APKLogger logger = pm.getLogger();
            new Thread(() -> {
                try {
                    List<File> signed = new ArrayList<>();
                    for (int i = 0; i < apks.size(); i++) {
                        File apk = apks.get(i);
                        String msg = context.rss.getString(R.string.signing, apk.getName());
                        pm.setText(msg);
                        logger.logMessage(msg);
                        File out = signedOutputFor(apk);
                        sw.signApk(apk, out);
                        signed.add(out);
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

    private static File signedOutputFor(File apk) {
        String name = apk.getName();
        File sibling = new File(apk.getParentFile(),
                name.replaceFirst("\\.(xapk|aspk|apk[sm]|apk)$", "_signed.$1"));
        if (sibling.equals(apk)) sibling = new File(apk.getParentFile(), name + "_signed");
        return FileUtils.getUnusedFile(sibling);
    }

    public void removeSignature(File apk) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            boolean ok = SignatureStripUtil.strip(apk);
            boolean stillSigned = ok && SignatureStripUtil.hasSignature(apk);
            boolean done = ok && !stillSigned;
            pm.dismiss();
            context.handler.post(() -> {
                Extensions.showMessage(context, context.rss.getString(done ? R.string.signature_removed : R.string.failed_to_remove_signature));
                if (done) context.loadFolderInPane(apk.getParentFile(), pane1, false);
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

package io.github.abdurazaaqmohammed.features.apk;

import android.text.TextUtils;
import android.text.format.Formatter;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.android.apksig.ApkSigner;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;
import top.nkbe.nza.sign.GenericSignatureKey;
import top.nkbe.nza.sign.V2V3SchemeSigner;

/**
 * Full-flavor APK data multiplexing flow: detects the stored embedded APK,
 * redirects identical entries (ApkDataMultiplexer), then signs with the raw
 * NeoApk V2/V3 signer — the only signing that preserves the layout.
 */
final class MultiplexDialogs {

    private MultiplexDialogs() {
    }

    static void show(MainActivity context, DialogUtil dialogUtil, boolean pane1,
                     File file, String fileName) {
        new Thread(() -> {
            String hostEntry;
            try {
                hostEntry = ApkDataMultiplexer.detectHostEntry(file);
            } catch (Exception e) {
                hostEntry = null;
            }
            final String detected = hostEntry;
            context.handler.post(() -> {
                if (TextUtils.isEmpty(detected)) {
                    Extensions.showMessage(context, context.getString(R.string.multiplex_no_host));
                    return;
                }
                boolean autosign = PreferenceManager.getDefaultSharedPreferences(context)
                        .getBoolean("autosign", true);
                TextView info = new TextView(context);
                info.setText(context.getString(R.string.multiplex_info, detected));
                info.setTextSize(14);
                int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
                info.setPadding(pad, pad, pad, 0);
                dialogUtil.getDialogBuilder()
                        .setTitle(context.getString(R.string.multiplex_title, fileName))
                        .setView(info)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.multiplex_run, (d, w) -> {
                            if (autosign) {
                                SignWrapper.requireAuth(context, sw -> run(context, pane1, file, fileName, detected, sw));
                            } else {
                                run(context, pane1, file, fileName, detected, null);
                            }
                        })
                        .show();
            });
        }, "MultiplexDetect").start();
    }

    private static void run(MainActivity context, boolean pane1, File file, String fileName,
                            String hostEntry, SignWrapper sw) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                File out = uniqueOutput(file);
                long saved = ApkDataMultiplexer.multiplex(file, out, hostEntry);
                if (sw != null) {
                    ApkSigner.SignerConfig cfg = sw.signerConfig();
                    V2V3SchemeSigner.sign(out, new GenericSignatureKey(
                            cfg.getPrivateKey(), cfg.getCertificates().toArray(new X509Certificate[0])), true, true);
                }
                pm.dismiss();
                context.handler.post(() -> {
                    Extensions.showMessage(context, context.getString(R.string.multiplex_done,
                            out.getName(), Formatter.formatFileSize(context, Math.max(saved, 0))));
                    context.loadFolderInPane(file.getParentFile(), pane1, false);
                });
            } catch (Throwable t) {
                pm.dismiss();
                context.handler.post(() -> new ErrorUtil(context).showError(t));
            }
        }, "Multiplex").start();
    }

    private static File uniqueOutput(File input) {
        String base = input.getName();
        if (base.toLowerCase(Locale.ROOT).endsWith(".apk")) {
            base = base.substring(0, base.length() - 4);
        }
        File out = new File(input.getParentFile(), base + "-multiplexed.apk");
        int i = 1;
        while (out.exists()) {
            out = new File(input.getParentFile(), base + "_" + (++i) + "-multiplexed.apk");
        }
        return out;
    }
}

package io.github.abdurazaaqmohammed.features.apk;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ApkSignatureInfo;
import io.github.abdurazaaqmohammed.utils.CertUtil;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.UiPrefs;

/**
 * Material "Signature information" sheet for an APK: scheme list, verification
 * status, and the full signer certificate. Replaces the plain
 * {@code setMessage(CharSequence)} certificate viewer, which could not colour the
 * failure state or lay the fields out.
 */
public final class SignatureInfoDialog {

    private SignatureInfoDialog() {
    }

    public static void show(AppCompatActivity context, File apk) {
        show(context, new DialogUtil(context), apk);
    }

    public static void show(AppCompatActivity context, DialogUtil dialogUtil, File apk) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            final ApkSignatureInfo info;
            try {
                info = ApkSignatureInfo.analyze(apk);
            } catch (Throwable t) {
                pm.dismiss();
                context.runOnUiThread(() -> {
                    View view = errorView(context, context.getString(android.R.string.unknownName));
                    (dialogUtil != null ? dialogUtil : new DialogUtil(context)).getDialogBuilder()
                            .setTitle(R.string.signature_info)
                            .setView(view)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
                return;
            }
            pm.dismiss();
            context.runOnUiThread(() -> {
                CharSequence all = flatten(context, info);
                // The sheet already lays out every certificate field inline, so the
                // separate "View certificate" copy is redundant; keep just Close/Copy.
                DialogUtil du = dialogUtil != null ? dialogUtil : new DialogUtil(context);
                AlertDialog dialog = du.getDialogBuilder()
                        .setTitle(R.string.signature_info)
                        .setView(build(context, info))
                        .setPositiveButton(R.string.close, null)
                        .setNegativeButton(android.R.string.copy, (d, w) -> CopyUtil.copyToClipboard(context, all))
                        .create();
                dialog.show();
                DialogUtil.keepButtonsHorizontal(dialog);
            });
        }).start();
    }

    private static View errorView(Activity context, String message) {
        TextView tv = new TextView(context);
        tv.setText(message);
        int pad = dp(context, 16);
        tv.setPadding(pad, pad, pad, pad);
        return tv;
    }

    private static View build(Activity context, ApkSignatureInfo info) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 16);
        root.setPadding(pad, pad / 2, pad, pad);

        addRow(root, context.getString(R.string.signature_file), displayName(info.getApk()), false);
        addRow(root, context.getString(R.string.signature_scheme),
                info.getSchemes().isEmpty() ? "—" : info.getSchemes(), false);
        addRow(root, context.getString(R.string.signature_status), statusText(context, info), true);

        List<X509Certificate> certs = info.getCertificates();
        if (certs.isEmpty()) {
            addRow(root, context.getString(R.string.cert_sha256),
                    context.getString(R.string.no_signature_found), false);
        } else {
            for (int i = 0; i < certs.size(); i++) {
                X509Certificate cert = certs.get(i);
                LinearLayout block = new LinearLayout(context);
                block.setOrientation(LinearLayout.VERTICAL);
                int groupPad = dp(context, 8);
                block.setPadding(0, groupPad, 0, groupPad);
                TextView header = new TextView(context);
                header.setText(certs.size() > 1
                        ? context.getString(R.string.cert, i + 1)
                        : context.getString(R.string.signature_signer));
                header.setTypeface(Typeface.DEFAULT_BOLD);
                header.setTextSize(14f);
                block.addView(header);
                addRow(block, context.getString(R.string.sig_owner), CertUtil.formatPrincipal(cert.getSubjectX500Principal()), false);
                addRow(block, context.getString(R.string.sig_serial), cert.getSerialNumber().toString(16), false);
                addRow(block, context.getString(R.string.sig_created), formatDate(cert.getNotBefore().getTime()), false);
                addRow(block, context.getString(R.string.sig_expired), formatDate(cert.getNotAfter().getTime()), false);
                addRow(block, context.getString(R.string.sig_algorithm), cert.getSigAlgName(), false);
                addRow(block, "MD5", ApkSignatureInfo.md5Of(cert), false);
                addRow(block, "SHA-1", ApkSignatureInfo.sha1Of(cert), false);
                addRow(block, "SHA-256", ApkSignatureInfo.sha256Of(cert), false);
                addRow(block, "CRC32", ApkSignatureInfo.crc32Of(cert), false);
                block.setOnClickListener(v -> CopyUtil.copyToClipboard(context, blockToText(block)));
                root.addView(block, matchWrap());
            }
        }

        if (!info.getErrors().isEmpty()) {
            TextView errors = new TextView(context);
            errors.setTextSize(12f);
            errors.setPadding(0, dp(context, 8), 0, 0);
            errors.setTextColor(errorColor(context));
            StringBuilder sb = new StringBuilder();
            for (String error : info.getErrors()) sb.append(error).append('\n');
            errors.setText(sb.toString().trim());
            root.addView(errors, matchWrap());
        }

        scroll.addView(root);
        return scroll;
    }

    private static String statusText(Context context, ApkSignatureInfo info) {
        switch (info.getStatus()) {
            case ApkSignatureInfo.STATUS_VERIFIED:
                return context.getString(R.string.sig_status_verified);
            case ApkSignatureInfo.STATUS_INVALID:
                return context.getString(R.string.sig_status_failed);
            case ApkSignatureInfo.STATUS_NOT_SIGNED:
                return context.getString(R.string.sig_status_none);
            default:
                return context.getString(android.R.string.unknownName);
        }
    }

    private static CharSequence flatten(Context context, ApkSignatureInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("File: ").append(displayName(info.getApk())).append('\n');
        sb.append("Scheme: ").append(info.getSchemes()).append('\n');
        sb.append("Status: ").append(statusText(context, info)).append('\n');
        for (X509Certificate cert : info.getCertificates()) {
            sb.append('\n').append(CertUtil.describe(cert));
        }
        if (!info.getErrors().isEmpty()) {
            sb.append("\n\nErrors:\n");
            for (String error : info.getErrors()) sb.append(error).append('\n');
        }
        return sb.toString();
    }

    private static void addRow(LinearLayout parent, String label, String value, boolean emphasize) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(parent.getContext(), 2), 0, dp(parent.getContext(), 2));
        TextView labelView = new TextView(parent.getContext());
        labelView.setText(label);
        labelView.setTextSize(13f);
        int minWidth = dp(parent.getContext(), 92);
        labelView.setMinWidth(minWidth);
        row.addView(labelView, new LinearLayout.LayoutParams(minWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = new TextView(parent.getContext());
        valueView.setText(TextUtils.isEmpty(value) ? "—" : value);
        valueView.setTextSize(13f);
        valueView.setTextIsSelectable(true);
        if (emphasize) {
            valueView.setTypeface(Typeface.DEFAULT_BOLD);
            valueView.setTextColor(errorColor(parent.getContext()));
        }
        row.addView(valueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, matchWrap());
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static String blockToText(LinearLayout block) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < block.getChildCount(); i++) {
            View child = block.getChildAt(i);
            if (child instanceof TextView) {
                sb.append(((TextView) child).getText()).append('\n');
            } else if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View cell = row.getChildAt(j);
                    if (cell instanceof TextView) sb.append(((TextView) cell).getText()).append(j == 0 ? ": " : "");
                }
                sb.append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static String displayName(File apk) {
        return apk == null ? "—" : apk.getName();
    }

    private static String formatDate(long millis) {
        if (millis <= 0) return "—";
        String formatted = UiPrefs.formatDate("yyyy-MM-dd HH:mm:ss", millis);
        return TextUtils.isEmpty(formatted) ? "—" : formatted;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
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
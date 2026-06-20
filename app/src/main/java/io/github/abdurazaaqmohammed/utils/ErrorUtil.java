package io.github.abdurazaaqmohammed.utils;

import static io.github.abdurazaaqmohammed.MPManager.MainActivity.doesNotHaveStoragePerm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;

public class ErrorUtil {
    private final Activity context;
    private final DialogUtil dialogUtil;
    public ErrorUtil (Activity c) {
        this.context = c;
        dialogUtil = new DialogUtil(c);
    }
    private void copyText(CharSequence text) {
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", text));
        Toast.makeText(context, ("Copied"), Toast.LENGTH_SHORT).show();
    }

    public void showError(Throwable e) {
        final String mainErr = e.toString();
        StringBuilder stackTrace = new StringBuilder().append(mainErr).append('\n');
        for (StackTraceElement line : e.getStackTrace()) stackTrace.append(line).append('\n');
        StringBuilder fullLog = new StringBuilder(stackTrace).append('\n')
                .append("SDK ").append(Build.VERSION.SDK_INT).append('\n')
                .append("MP Manager").append(' ');
        String currentVer;
        try {
            currentVer = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ex) {
            currentVer = "1.6.6.4";
        }
        fullLog.append(currentVer).append('\n').append("Storage permission granted: ").append(!doesNotHaveStoragePerm(context));
        MaterialAlertDialogBuilder b = dialogUtil.getDialogBuilder()
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create issue", (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdurazaaqMohammed/AntiSplit-M/issues/new?title=Crash%20Report&body=" + fullLog))));
        context.runOnUiThread(() -> {
            TextView title = new TextView(context);
            title.setText(mainErr);
            title.setTextSize(20);

            TextView msg = new TextView(context);
            msg.setText(stackTrace);
            ScrollView sv = new ScrollView(context);
            msg.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6)));
            sv.addView(msg);
            new DialogUtil(context).styleAlertDialog(b.setCustomTitle(title).setView(sv).create());
        });
    }
}
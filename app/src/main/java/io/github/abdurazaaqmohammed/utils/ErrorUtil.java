package io.github.abdurazaaqmohammed.utils;

import static io.github.abdurazaaqmohammed.utils.FileUtils.doesNotHaveStoragePerm;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class ErrorUtil {
    private final Activity context;
    private final DialogUtil dialogUtil;
    public ErrorUtil (Activity c) {
        this.context = c;
        dialogUtil = new DialogUtil(c);
    }
    private void copyText(CharSequence text) {
        ((ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("log", text));
        Extensions.showMessage(context, text);
    }

    public void showError(String s) {
        MaterialAlertDialogBuilder b = dialogUtil.getDialogBuilder()
                .setMessage(s)
                .setNegativeButton("Cancel", null)
                .setNeutralButton(R.string.copy_log, (dialog, which) -> copyText(s));
        b.show();
    }

    public void showError(Throwable e) {
        final String mainErr = e.toString();
        StringBuilder stackTrace = new StringBuilder(mainErr).append('\n');
        for (StackTraceElement line : e.getStackTrace()) stackTrace.append(line).append('\n');
        /*StringBuilder fullLog = new StringBuilder*/(stackTrace).append('\n')
                .append("SDK ").append(Build.VERSION.SDK_INT).append('\n')
                .append("MP Manager ").append('v');
        String currentVer;
        try {
            //currentVer = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            currentVer = "1.0";
        } catch (Exception ex) {
            currentVer = "1.0";
        }
        stackTrace.append(currentVer).append('\n').append("Storage permission granted: ").append(!doesNotHaveStoragePerm(context));
        MaterialAlertDialogBuilder b = dialogUtil.getDialogBuilder()
                .setNegativeButton("Cancel", null)
                .setNeutralButton(android.R.string.copy, (dialog, which) -> copyText(stackTrace));
                //.setPositiveButton("Create issue", (dialog, which) -> context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AbdurazaaqMohammed/MP-Manager/issues/new?title=Crash%20Report&body=" + fullLog))));
        context.runOnUiThread(() -> {
//            TextView msg = new TextView(context);
//            msg.setText(stackTrace);
//            ScrollView sv = new ScrollView(context);
//            msg.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (context.getResources().getDisplayMetrics().heightPixels * 0.6)));
//            sv.addView(msg);
            (b.setTitle(mainErr).setMessage(stackTrace)).show();
        });
    }
}
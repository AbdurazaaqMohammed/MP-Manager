package io.github.abdurazaaqmohammed.utils;

import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * UI entry for the SSL pinning bypass: shows what gets patched, offers
 * optional signing, runs {@link SslUnpinning} on a background thread and
 * reports the patch list. Used from the APK info dialog's "More" menu and by
 * the standalone SSL Unpinning tool.
 */
public final class SslUnpinningLauncher {

    private SslUnpinningLauncher() {
    }

    /** APK info dialog "More" entry (signature matches the other launchers). */
    public static void open(io.github.abdurazaaqmohammed.MPManager.MainActivity context, DialogUtil dialogUtil,
                            boolean pane1, File file, String fileName) {
        if (file == null || !file.isFile()) {
            Extensions.showMessage(context, R.string.ssl_unpin_no_apk);
            return;
        }
        final boolean[] sign = {true};
        CheckBox signBox = new CheckBox(context);
        signBox.setText(R.string.ssl_unpin_sign);
        signBox.setChecked(true);
        signBox.setOnCheckedChangeListener((b, checked) -> sign[0] = checked);
        TextView note = new TextView(context);
        note.setText(R.string.ssl_unpin_note);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        note.setPadding(pad, pad / 2, pad, 0);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, 0);
        layout.addView(note);
        layout.addView(signBox);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.ssl_unpin_title)
                .setMessage(context.getString(R.string.ssl_unpin_confirm, file.getName()))
                .setView(layout)
                .setPositiveButton(R.string.ssl_unpin_run, (dialog, which) -> run(context, file, sign[0]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Runs the patch for the given APK and shows the result. */
    public static void run(AppCompatActivity context, File apk, boolean sign) {
        File output = new File(apk.getParentFile(), stripExt(apk.getName()) + "-unpinned.apk");
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                if (sign) {
                    SignWrapper.requireAuth(context, sw -> runPatch(context, apk, output, sw, pm));
                } else {
                    runPatch(context, apk, output, null, pm);
                }
            } catch (Exception e) {
                pm.dismiss();
                showResult(context, output, null, e);
            }
        }).start();
    }

    private static void runPatch(AppCompatActivity context, File apk, File output, SignWrapper signer, ProgressManager pm) {
        Handler handler = new Handler(Looper.getMainLooper());
        try {
            SslUnpinning.Report report = SslUnpinning.unpinApk(apk, output, signer);
            pm.dismiss();
            handler.post(() -> showResult(context, output, report, null));
        } catch (Exception e) {
            pm.dismiss();
            handler.post(() -> showResult(context, output, null, e));
        }
    }

    private static void showResult(AppCompatActivity context, File output, SslUnpinning.Report report, Exception error) {
        TextView text = new TextView(context);
        text.setTextIsSelectable(true);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        text.setPadding(pad, pad, pad, pad);
        if (error != null) {
            text.setText(context.getString(R.string.ssl_unpin_failed, String.valueOf(error.getMessage())));
        } else if (report == null || report.patches.isEmpty()) {
            text.setText(R.string.ssl_unpin_nothing_found);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(context.getString(R.string.ssl_unpin_done, output.getPath()));
            sb.append('\n');
            for (String patch : report.patches) {
                sb.append("• ").append(patch).append('\n');
            }
            text.setText(sb.toString());
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(text);
        new Handler(Looper.getMainLooper()).post(() -> new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.ssl_unpin_title)
                .setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .show());
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

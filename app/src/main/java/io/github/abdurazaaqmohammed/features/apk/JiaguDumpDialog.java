package io.github.abdurazaaqmohammed.features.apk;

import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;

/**
 * Root Jiagu Dumper (tools hub). UI over {@link JiaguDumpEngine}: dump the real
 * DEX files of a running Jiagu-protected app out of process memory, optionally
 * rebuilding the unpacked APK right away when an original APK path is given.
 */
public final class JiaguDumpDialog {

    private JiaguDumpDialog() {}

    public static void show(AppCompatActivity activity) {
        show(activity, null);
    }

    public static void show(AppCompatActivity activity, String prefillPackage) {
        if (!RootManager.getInstance(activity).isRootAvailable()) {
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.jiagu_dumper_title)
                    .setMessage(R.string.jiagu_root_required)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        List<String> packages = JiaguDumpEngine.installedPackages(activity);
        ArrayAdapter<String> suggestions = new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, packages);

        TextInputLayout pkgLayout = new TextInputLayout(activity);
        pkgLayout.setHint(activity.getString(R.string.jiagu_package_hint));
        AutoCompleteTextView pkgInput = new AutoCompleteTextView(activity);
        pkgInput.setAdapter(suggestions);
        pkgInput.setThreshold(1);
        pkgInput.setSingleLine(true);
        pkgLayout.addView(pkgInput);
        if (prefillPackage != null && !prefillPackage.isEmpty()) {
            pkgInput.setText(prefillPackage);
        }
        // "pick from installed apps" button: full package list with app labels
        com.google.android.material.button.MaterialButton pick = new com.google.android.material.button.MaterialButton(
                activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        pick.setText(R.string.jiagu_pick_app);
        pick.setOnClickListener(v -> showAppPicker(activity, packages, pkgInput));
        root.addView(pick);
        root.addView(pkgLayout);

        TextInputLayout waitLayout = new TextInputLayout(activity);
        waitLayout.setHint(activity.getString(R.string.jiagu_wait_hint));
        EditText waitInput = new EditText(activity);
        waitInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        waitInput.setText("15");
        waitLayout.addView(waitInput);
        root.addView(waitLayout);

        TextInputLayout apkLayout = new TextInputLayout(activity);
        apkLayout.setHint(activity.getString(R.string.jiagu_apk_path_hint));
        EditText apkInput = new EditText(activity);
        apkInput.setSingleLine(true);
        apkLayout.addView(apkInput);
        root.addView(apkLayout);

        TextView logView = new TextView(activity);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11);
        logView.setTextIsSelectable(true);
        logView.setMovementMethod(new ScrollingMovementMethod());
        ScrollView logScroll = new ScrollView(activity);
        logScroll.addView(logView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (220 * activity.getResources().getDisplayMetrics().density)));
        root.addView(logScroll);

        StringBuilder logBuf = new StringBuilder();
        AtomicBoolean busy = new AtomicBoolean(false);
        Runnable rerender = () -> {
            logView.setText(logBuf.toString());
            logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
        };
        JiaguDumpEngine.LogSink sink = line -> activity.runOnUiThread(() -> {
            logBuf.append(line).append('\n');
            rerender.run();
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.jiagu_dumper_title)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.jiagu_dump_start, null)
                .create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            if (!busy.compareAndSet(false, true)) return;
            final String pkg = pkgInput.getText() == null ? "" : pkgInput.getText().toString().trim();
            if (!JiaguDumpEngine.isValidPackage(pkg)) {
                pkgLayout.setError(activity.getString(R.string.jiagu_invalid_package));
                busy.set(false);
                return;
            }
            pkgLayout.setError(null);
            int waitSecTmp = 15;
            try {
                waitSecTmp = Math.max(3, Math.min(120, Integer.parseInt(waitInput.getText().toString())));
            } catch (Exception ignored) {
            }
            final int waitSec = waitSecTmp;
            final String apkPath = apkInput.getText() == null ? "" : apkInput.getText().toString().trim();
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            new Thread(() -> {
                try {
                    File outDir = JiaguDumpEngine.dump(activity, pkg, waitSec, sink);
                    if (!TextUtils.isEmpty(apkPath)) {
                        File apk = new File(apkPath);
                        if (!apk.isFile()) {
                            sink.log("APK path not found: " + apkPath);
                        } else {
                            sink.log("== Rebuilding " + apk.getName() + " ...");
                            File rebuilt = JiaguDumpEngine.rebuild(activity, apk, outDir, sink);
                            sink.log("Built " + rebuilt.getAbsolutePath());
                            if (PreferenceManager.getDefaultSharedPreferences(activity)
                                    .getBoolean("autosign", true)) {
                                activity.runOnUiThread(() -> SignWrapper.requireAuth(activity, wrapper -> {
                                    new Thread(() -> {
                                        try {
                                            wrapper.signApk(rebuilt);
                                            sink.log("Signed " + rebuilt.getName());
                                        } catch (Exception e) {
                                            sink.log("Signing failed: " + e.getMessage());
                                        }
                                        busy.set(false);
                                    }, "jiagu-sign").start();
                                }));
                                return;
                            }
                        }
                    }
                } catch (Exception e) {
                    sink.log("FAILED: " + e.getMessage());
                }
                busy.set(false);
                activity.runOnUiThread(() -> {
                    try {
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                    } catch (Exception ignored) {}
                });
            }, "jiagu-dump").start();
        });
    }

    /** Dialog listing installed apps (label + package); tapping fills the package field. */
    private static void showAppPicker(AppCompatActivity activity, List<String> packages,
                                      AutoCompleteTextView target) {
        PackageManager pm = activity.getPackageManager();
        List<String> rows = new ArrayList<>();
        for (String pkg : packages) {
            String label = pkg;
            try {
                CharSequence l = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                if (l != null && l.length() > 0) label = l + "\n" + pkg;
            } catch (Exception ignored) {
            }
            rows.add(label);
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.jiagu_pick_app)
                .setItems(rows.toArray(new String[0]), (d, which) -> target.setText(packages.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}

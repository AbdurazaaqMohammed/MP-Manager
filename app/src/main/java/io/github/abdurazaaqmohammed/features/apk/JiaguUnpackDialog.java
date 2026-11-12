package io.github.abdurazaaqmohammed.features.apk;

import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.APKLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;

/**
 * APK menu entry for the offline Jiagu unpacker: rebuilds an installable APK
 * from the original file plus the DEX dump produced by the root Jiagu Dumper tool.
 */
public final class JiaguUnpackDialog {

    private JiaguUnpackDialog() {}

    public static void show(io.github.abdurazaaqmohammed.MPManager.MainActivity context, File file) {
        new Thread(() -> {
            String pkg = null;
            boolean jiagu;
            try {
                jiagu = JiaguUnpackEngine.isJiagu(file);
                JiaguUnpackEngine.ManifestData md = JiaguUnpackEngine.decodeManifest(file);
                pkg = md.entries != null ? JiaguUnpackEngine.manifestAttr(md.entries, "package") : null;
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
                return;
            }
            if (!jiagu) {
                context.handler.post(() -> new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.jiagu_unpack)
                        .setMessage(R.string.jiagu_not_protected)
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
                return;
            }
            String packageName = pkg;
            File dump = JiaguDumpEngine.latestDump(context, packageName);
            if (dump == null) {
                context.handler.post(() -> new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.jiagu_unpack)
                        .setMessage(context.getString(R.string.jiagu_no_dump, packageName))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.jiagu_open_dumper, (d, w) ->
                                JiaguDumpDialog.show(context, packageName))
                        .show());
                return;
            }
            List<File> dexes = new ArrayList<>();
            File[] children = dump.listFiles();
            if (children != null) {
                for (File c : children) {
                    if (c.isFile() && c.getName().endsWith(".dex")) dexes.add(c);
                }
            }
            if (dexes.isEmpty()) {
                context.handler.post(() -> new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.jiagu_unpack)
                        .setMessage(context.getString(R.string.jiagu_no_dump, packageName))
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
                return;
            }
            int dexCount = dexes.size();
            String dumpPath = dump.getAbsolutePath();
            boolean autosign = PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("autosign", true);
            context.handler.post(() -> new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.jiagu_unpack)
                    .setMessage(context.getString(R.string.jiagu_unpack_confirm, dexCount, dumpPath))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.jiagu_unpack, (d, w) ->
                            run(context, file, dexes, autosign))
                    .show());
        }, "jiagu-check").start();
    }

    private static void run(io.github.abdurazaaqmohammed.MPManager.MainActivity context, File file, List<File> dexes, boolean autosign) {
        ProgressManager pm = new ProgressManager(context, true).show();
        APKLogger logger = pm.getLogger();
        new Thread(() -> {
            try {
                JiaguUnpackEngine.Result result = JiaguUnpackEngine.unpack(context, file, dexes, logger);
                for (String line : result.log) logger.logMessage(line);
                pm.dismiss();
                if (autosign) {
                    context.handler.post(() -> SignWrapper.requireAuth(context, wrapper -> {
                        ProgressManager signPm = new ProgressManager(context, true)
                                .setText(context.getString(R.string.signing, result.apk.getName())).show();
                        new Thread(() -> {
                            try {
                                wrapper.signApk(result.apk);
                                signPm.dismiss();
                                context.handler.post(() -> {
                                    try {
                                        context.refreshAllPanes();
                                    } catch (Exception ignored) {
                                    }
                                });
                            } catch (Exception e) {
                                signPm.dismiss();
                                new ErrorUtil(context).showError(e);
                            }
                        }, "jiagu-sign").start();
                    }));
                } else {
                    context.handler.post(() -> {
                        try {
                            context.refreshAllPanes();
                        } catch (Exception ignored) {
                        }
                    });
                }
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }, "jiagu-unpack").start();
    }
}

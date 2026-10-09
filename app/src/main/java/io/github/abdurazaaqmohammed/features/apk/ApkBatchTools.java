package io.github.abdurazaaqmohammed.features.apk;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.reandroid.apk.APKLogger;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ApkCompareUtil;
import io.github.abdurazaaqmohammed.utils.ApkDeepOptimizer;
import io.github.abdurazaaqmohammed.utils.ApkOptimizer;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * APK compare + batch optimize/sign workflows extracted from ApkToolsHandler.
 */
public class ApkBatchTools {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final boolean pane1;

    public ApkBatchTools(MainActivity context, DialogUtil dialogUtil, UIHelper uiHelper, boolean pane1) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.uiHelper = uiHelper;
        this.pane1 = pane1;
    }

    public void showCompareApksDialog(File f1, File f2) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                String report = ApkCompareUtil.compare(context, f1, f2);
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
                    dialogUtil.getDialogBuilder()
                            .setTitle(R.string.compare_apks)
                            .setView(scroll)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(android.R.string.copy, (d, w) -> CopyUtil.copyToClipboard(context, report))
                            .show();
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void batchOptimizeApks(List<File> apks) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean sign = settings.getBoolean("autosign", true);
        boolean delFiles = settings.getBoolean("delFiles", true);
        if (sign) SignWrapper.requireAuth(context, sw -> runBatchOptimize(apks, delFiles, sw, settings));
        else runBatchOptimize(apks, delFiles, null, settings);
    }

    private void runBatchOptimize(List<File> apks, boolean delFiles, SignWrapper wrapper, SharedPreferences settings) {
        ProgressManager pm = new ProgressManager(context, true).show();
        APKLogger logger = pm.getLogger();
        boolean deepOpt = settings.getBoolean("deep_optimize", false);
        Set<String> filesToDelete = settings.getStringSet("filesToDelete", null);
        new Thread(() -> {
            try {
                for (int i = 0; i < apks.size(); i++) {
                    File apk = apks.get(i);
                    String msg = context.rss.getString(R.string.optimizing, apk.getName());
                    pm.setText(msg);
                    logger.logMessage(msg);
                    File opt = ApkOptimizer.optimize(context, apk, delFiles, settings, settings.getBoolean("ultra_compress", true), logger);
                    if (deepOpt) {
                        logger.logMessage(context.rss.getString(R.string.deep_optimize_running));
                        opt = ApkDeepOptimizer.optimize(context, opt, filesToDelete, settings, logger);
                    }
                    if (wrapper != null) wrapper.signApk(opt);
                }
                pm.dismiss();
                context.handler.post(() -> {
                    Extensions.showMessage(context, context.rss.getString(R.string.opt_done));
                    context.loadFolderInPane(apks.get(0).getParentFile(), pane1, false);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

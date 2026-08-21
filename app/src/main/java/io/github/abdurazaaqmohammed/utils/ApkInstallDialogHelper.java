package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApkInstallDialogHelper {

    private final Activity activity;

    public ApkInstallDialogHelper(Activity activity) {
        this.activity = activity;
    }

    public void installSingleApk(File apkFile) {
        String appName = getAppNameForApk(apkFile);
        ProgressManager pm = showProgress("Installing " + appName + "...");

        RootManager rm = RootManager.getInstance(activity);
        if (rm.isSilentInstallEnabled() && rm.isRootAvailable()) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    rm.installSilent(apkFile.getAbsolutePath());
                    activity.runOnUiThread(() -> {
                        dismissProgress(pm);
                        showInstallCompleteDialog(appName, apkFile);
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        dismissProgress(pm);
                        new ErrorUtil(activity).showError(e);
                    });
                }
            });
            executor.shutdown();
        } else {
            installWithIntent(apkFile, pm);
        }
    }

    public void installSplitApks(List<File> apkFiles, String archiveName) {
        String appName = archiveName != null ? archiveName : "Split APK";
        ProgressManager pm = showProgress("Installing " + appName + "...");
        File firstApk = apkFiles.isEmpty() ? null : apkFiles.get(0);

        RootManager rm = RootManager.getInstance(activity);
        if (rm.isSilentInstallEnabled() && rm.isRootAvailable()) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    java.util.List<String> paths = new java.util.ArrayList<>();
                    for (File f : apkFiles) paths.add(f.getAbsolutePath());
                    rm.installSplitSilent(paths);
                    activity.runOnUiThread(() -> {
                        dismissProgress(pm);
                        showInstallCompleteDialog(appName, firstApk);
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        dismissProgress(pm);
                        new ErrorUtil(activity).showError(e);
                    });
                }
            });
            executor.shutdown();
        } else {
            installSplitWithPackageInstaller(apkFiles, appName, pm, firstApk);
        }
    }

    private void installWithIntent(File apkFile, ProgressManager pm) {
        try {
            android.content.pm.PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
            android.content.pm.PackageInstaller.SessionParams params =
                    new android.content.pm.PackageInstaller.SessionParams(
                            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(apkFile.length());

            int sessionId = pi.createSession(params);
            try (android.content.pm.PackageInstaller.Session session = pi.openSession(sessionId);
                 java.io.OutputStream out = session.openWrite("apk", 0, apkFile.length());
                 java.io.InputStream in = new java.io.FileInputStream(apkFile)) {
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                session.fsync(out);
            }

            android.content.Intent callbackIntent = new android.content.Intent(activity, com.faith.apkinstaller.APKInstallService.class);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getService(
                    activity, 0, callbackIntent, android.app.PendingIntent.FLAG_MUTABLE);

            try (android.content.pm.PackageInstaller.Session commitSession = pi.openSession(sessionId)) {
                commitSession.commit(pendingIntent.getIntentSender());
            }
            dismissProgress(pm);
        } catch (Exception e) {
            dismissProgress(pm);
            new ErrorUtil(activity).showError(e);
        }
    }

    private void installSplitWithPackageInstaller(List<File> apkFiles, String appName, ProgressManager pm, File firstApk) {
        try {
            android.content.pm.PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
            android.content.pm.PackageInstaller.SessionParams params =
                    new android.content.pm.PackageInstaller.SessionParams(
                            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);

            long totalSize = 0;
            for (File f : apkFiles) totalSize += f.length();
            params.setSize(totalSize);

            int sessionId = pi.createSession(params);
            try (android.content.pm.PackageInstaller.Session session = pi.openSession(sessionId)) {
                int idx = 0;
                for (File apkFile : apkFiles) {
                    try (java.io.OutputStream out = session.openWrite("split_" + idx, 0, apkFile.length());
                         java.io.InputStream in = new java.io.FileInputStream(apkFile)) {
                        byte[] buffer = new byte[65536];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        session.fsync(out);
                    }
                    idx++;
                }
            }

            android.content.Intent callbackIntent = new android.content.Intent(activity, com.faith.apkinstaller.APKInstallService.class);
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getService(
                    activity, 0, callbackIntent, android.app.PendingIntent.FLAG_MUTABLE);

            try (android.content.pm.PackageInstaller.Session commitSession = pi.openSession(sessionId)) {
                commitSession.commit(pendingIntent.getIntentSender());
            }
            dismissProgress(pm);
            showInstallCompleteDialog(appName, firstApk);
        } catch (Exception e) {
            dismissProgress(pm);
            new ErrorUtil(activity).showError(e);
        }
    }

    private ProgressManager showProgress(String text) {
        if (activity instanceof AppCompatActivity) {
            ProgressManager pm = new ProgressManager((AppCompatActivity) activity, true);
            pm.setText(text);
            pm.show();
            return pm;
        }
        return null;
    }

    private void dismissProgress(ProgressManager pm) {
        if (pm != null) pm.dismiss();
    }

    private void showInstallCompleteDialog(String appName, File apkFile) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(dp(16), dp(16), dp(16), dp(8));

        TextView icon = new TextView(activity);
        icon.setText("\u2714");
        icon.setTextSize(48);
        icon.setGravity(Gravity.CENTER);
        icon.setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorPrimary, Color.GREEN));
        layout.addView(icon);

        TextView message = new TextView(activity);
        message.setText(appName + " installed successfully!");
        message.setTextSize(15);
        message.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = dp(8);
        layout.addView(message, msgParams);

        String packageName = getPackageNameForApk(apkFile);

        Dialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.installation_complete)
                .setView(layout)
                .setPositiveButton(R.string.launch, (d, w) -> {
                    if (packageName != null) {
                        Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(packageName);
                        if (launchIntent != null) {
                            activity.startActivity(launchIntent);
                        } else {
                            Extensions.showMessage(activity, "Cannot launch app");
                        }
                    }
                })
                .setNegativeButton("Done", null)
                .create();
        dialog.show();
    }

    private String getAppNameForApk(File apkFile) {
        if (apkFile == null) return "APK";
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) {
                CharSequence label = info.applicationInfo.loadLabel(pm);
                if (label != null) return label.toString();
            }
        } catch (Exception ignored) {}
        return apkFile.getName();
    }

    private String getPackageNameForApk(File apkFile) {
        if (apkFile == null) return null;
        try {
            PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) return info.packageName;
        } catch (Exception ignored) {}
        return null;
    }

    private int dp(int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}

package io.github.abdurazaaqmohammed.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.faith.apkinstaller.APKInstallService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApkInstallDialogHelper {

    public static final String ACTION_INSTALL_RESULT = "io.github.abdurazaaqmohammed.APK_INSTALL_RESULT";
    public static final String EXTRA_INSTALL_SUCCESS = "extra_install_success";

    private final Activity activity;

    public ApkInstallDialogHelper(Activity activity) {
        this.activity = activity;
    }

    public void installSingleApk(File apkFile) {
        List<File> single = new ArrayList<>();
        single.add(apkFile);
        installPrivilegedOrFallback(single, getAppNameForApk(apkFile).toString(), apkFile);
    }

    public void installSplitApks(List<File> apkFiles, String archiveName) {
        String appName = archiveName != null ? archiveName : "Split APK";
        File firstApk = apkFiles.isEmpty() ? null : apkFiles.get(0);
        installPrivilegedOrFallback(new ArrayList<>(apkFiles), appName, firstApk);
    }

    private void installPrivilegedOrFallback(List<File> apkFiles, String appName, File firstApk) {
        if (PrivilegedInstaller.isPrivilegedSilentReady(activity)) {
            String installer = InstallerConfig.resolveInstallerPackageName(activity);
            ProgressManager pm = showProgress("Installing " + appName + " (as " + installer + ")...");
            PrivilegedInstaller.installAsync(activity, apkFiles, new PrivilegedInstaller.Callback() {
                @Override
                public void onSuccess(String recorded) {
                    activity.runOnUiThread(() -> {
                        dismissProgress(pm);
                        String verified = firstApk != null
                                ? PrivilegedInstaller.readRecordedInstaller(
                                        activity, getPackageNameForApk(firstApk) != null
                                                ? getPackageNameForApk(firstApk) : "")
                                : recorded;
                        showInstallCompleteDialog(appName + "\nInstaller: " + verified, firstApk);
                    });
                }

                @Override
                public void onError(Exception e) {
                    activity.runOnUiThread(() -> {
                        dismissProgress(pm);
                        new ErrorUtil(activity).showError(e);
                    });
                }
            });
            return;
        }

        if (apkFiles.size() == 1 && apkFiles.get(0) != null) {
            RootManager rm = RootManager.getInstance(activity);
            if (rm.isSilentInstallEnabled() && rm.isRootAvailable()) {
                // Legacy guard: PrivilegedInstaller already checked; kept for safety.
                InstallUtil.installApk(activity, apkFiles.get(0));
                return;
            }
            InstallUtil.installApk(activity, apkFiles.get(0));
        } else {
            ProgressManager pm = showProgress("Installing " + appName + "...");
            installSplitWithPackageInstaller(apkFiles, appName, pm, firstApk);
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void installWithIntent(File apkFile, ProgressManager pm) {
        try {
            PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setSize(apkFile.length());

            int sessionId = pi.createSession(params);
            try (PackageInstaller.Session session = pi.openSession(sessionId);
                 OutputStream out = session.openWrite("apk", 0, apkFile.length());
                 InputStream in = new FileInputStream(apkFile)) {
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                session.fsync(out);
            }

            Intent callbackIntent = new Intent(activity, APKInstallService.class);
            PendingIntent pendingIntent = PendingIntent.getService(
                    activity, 0, callbackIntent, PendingIntent.FLAG_MUTABLE);

            try (PackageInstaller.Session commitSession = pi.openSession(sessionId)) {
                commitSession.commit(pendingIntent.getIntentSender());
            }
            dismissProgress(pm);
        } catch (Exception e) {
            dismissProgress(pm);
            new ErrorUtil(activity).showError(e);
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void installSplitWithPackageInstaller(List<File> apkFiles, String appName, ProgressManager pm, File firstApk) {
        BroadcastReceiver resultReceiver = null;
        try {
            PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
            long totalSize = 0;
            for (File f : apkFiles) totalSize += f.length();
            String targetPkg = getPackageNameForApk(firstApk);
            String installerPkg = InstallerConfig.resolveInstallerPackageName(activity);
            int sessionId = PrivilegedInstaller.createSessionWithMetadata(activity, pi,
                    targetPkg != null ? targetPkg : "",
                    totalSize, installerPkg, PrivilegedInstaller.collectFlags(activity));
            try (PackageInstaller.Session session = pi.openSession(sessionId)) {
                int idx = 0;
                for (File apkFile : apkFiles) {
                    try (OutputStream out = session.openWrite("split_" + idx, 0, apkFile.length());
                         InputStream in = new FileInputStream(apkFile)) {
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

            Intent callbackIntent = new Intent(activity, APKInstallService.class);
            PendingIntent pendingIntent = PendingIntent.getService(activity, 0, callbackIntent, PendingIntent.FLAG_MUTABLE);

            resultReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try { activity.unregisterReceiver(this); } catch (Exception ignored) {}
                    dismissProgress(pm);
                    if (intent.getBooleanExtra(EXTRA_INSTALL_SUCCESS, false)) {
                        showInstallCompleteDialog(appName, firstApk);
                    } else {
                        Extensions.showMessage(activity, activity.getString(R.string.installation_failed));
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ACTION_INSTALL_RESULT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                activity.registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else activity.registerReceiver(resultReceiver, filter);

            try (PackageInstaller.Session commitSession = pi.openSession(sessionId)) {
                commitSession.commit(pendingIntent.getIntentSender());
            }
        } catch (Exception e) {
            if (resultReceiver != null) { try { activity.unregisterReceiver(resultReceiver); } catch (Exception ignored) {} }
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

    private void showInstallCompleteDialog(CharSequence appName, File apkFile) {
        String packageName = getPackageNameForApk(apkFile);

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.installation_complete)
            .setMessage(activity.getString(R.string.installed_successfully, appName))
            .setPositiveButton(R.string.launch, (d, w) -> {
                if (packageName != null) {
                    Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(packageName);
                    if (launchIntent != null) {
                        activity.startActivity(launchIntent);
                    } else {
                        Extensions.showMessage(activity, activity.getString(R.string.install_cannot_launch));
                    }
                }
            })
            .setNegativeButton(android.R.string.ok, null);
        activity.runOnUiThread(b::show);
    }

    private CharSequence getAppNameForApk(File apkFile) {
        if (apkFile == null) return "APK";
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) {
                if (info.applicationInfo != null) {
                    return info.applicationInfo.loadLabel(pm);
                }
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

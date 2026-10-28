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
import io.github.abdurazaaqmohammed.features.apk.ApkInstallAnalysis;
import io.github.abdurazaaqmohammed.features.apk.InstallInfoDialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
        confirmThenInstall(single, getAppNameForApk(apkFile).toString(), apkFile);
    }

    public void installSplitApks(List<File> apkFiles, String archiveName) {
        String appName = archiveName != null ? archiveName : "Split APK";
        File firstApk = baseSplitApk(apkFiles);
        confirmThenInstall(new ArrayList<>(apkFiles), appName, firstApk);
    }

    /**
     * Picks the split that carries the full install manifest for the comparison.
     * Split bundles are walked in zip order, which is not guaranteed to list the
     * base APK first, and a {@code config.*} split has no installable base for
     * {@link PackageManager#getPackageArchiveInfo}. Favour an explicit base name,
     * then the first entry whose archive info yields a package.
     */
    private static File baseSplitApk(List<File> apkFiles) {
        if (apkFiles == null || apkFiles.isEmpty()) return null;
        File explicitBase = null;
        for (File f : apkFiles) {
            if (f == null) continue;
            String name = f.getName().toLowerCase(Locale.ROOT);
            if (name.equals("base.apk") || name.equals("master.apk") || name.equals("main.apk")) {
                explicitBase = f;
                break;
            }
        }
        if (explicitBase != null) return explicitBase;
        return apkFiles.get(0);
    }

    /**
     * Shows the install comparison before installing when the user asked for it
     * ("Show installation info": always / only when the signature differs /
     * never). It reports what the candidate would do to the installed copy -
     * newer, older or identical; same or different signer; matching checksums -
     * and, when the signatures differ, explains that Android will refuse the
     * install, links to Core Patch, and offers uninstall-and-install with an
     * optional data backup.
 */
    private void confirmThenInstall(List<File> apkFiles, String appName, File firstApk) {
        if (firstApk == null) {
            installPrivilegedOrFallback(apkFiles, appName, firstApk);
            return;
        }
        new Thread(() -> {
            ApkInstallAnalysis analysis;
            try {
                analysis = ApkInstallAnalysis.analyze(activity, firstApk);
            } catch (Throwable t) {
                analysis = null;
            }
            final ApkInstallAnalysis result = analysis;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (result == null || !InstallInfoDialog.shouldShow(activity, result)) {
                    installPrivilegedOrFallback(apkFiles, appName, firstApk);
                    return;
                }
                InstallInfoDialog.show(activity, new DialogUtil(activity), appName, result,
                        () -> installPrivilegedOrFallback(apkFiles, appName, firstApk));
            });
        }).start();
    }

    private void installPrivilegedOrFallback(List<File> apkFiles, String appName, File firstApk) {
        if (PrivilegedInstaller.isPrivilegedSilentReady(activity)) {
            String installer = InstallerConfig.resolveInstallerPackageName(activity);
            ProgressManager pm = showProgress("Installing " + appName + " (as " + installer + ")...");
            PrivilegedInstaller.installAsync(activity, apkFiles, new PrivilegedInstaller.Callback() {
                @Override
                public void onSuccess(String recorded) {
                    String pkg = firstApk != null ? getPackageNameForApk(firstApk) : null;
                    awaitRecordedInstaller(pkg, installer, recorded, pm, appName, firstApk);
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

    /**
     * An installer-package session is committed asynchronously, so the recorded
     * value is only final a moment after the installer reports back. Poll it
     * briefly so the confirmation reflects what the system actually stored.
     */
    private void awaitRecordedInstaller(String packageName, String requestedInstaller, String fallback,
                                        ProgressManager pm, String appName, File firstApk) {
        if (packageName == null || packageName.isEmpty()) {
            finishInstall(fallback, pm, appName, firstApk);
            return;
        }
        new Thread(() -> {
            String recorded = fallback;
            for (int i = 0; i < 20; i++) {
                String now = PrivilegedInstaller.readRecordedInstaller(activity, packageName);
                if (now != null && !"?".equals(now)) {
                    recorded = now;
                    if (now.equals(requestedInstaller)) break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            final String shown = recorded;
            activity.runOnUiThread(() -> finishInstall(shown, pm, appName, firstApk));
        }).start();
    }

    private void finishInstall(String recorded, ProgressManager pm, String appName, File firstApk) {
        dismissProgress(pm);
        String message = appName + "\nInstaller: " + recorded;
        String requested = InstallerConfig.resolveInstallerPackageName(activity);
        if (requested != null && !requested.equals(recorded)) {
            String trace = PrivilegedInstaller.lastTrace();
            message += "\nSpoof: " + (trace.isEmpty() ? "system installer" : trace);
        }
        showInstallCompleteDialog(message, firstApk);
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
        PackageInstaller pi = null;
        int sessionId = -1;
        try {
            pi = activity.getPackageManager().getPackageInstaller();
            long totalSize = 0;
            for (File f : apkFiles) totalSize += f.length();
            String targetPkg = getPackageNameForApk(firstApk);
            String installerPkg = InstallerConfig.resolveInstallerPackageName(activity);
            sessionId = PrivilegedInstaller.createSessionWithMetadata(activity, pi,
                    targetPkg, totalSize, installerPkg, PrivilegedInstaller.collectFlags(activity));
            try (PackageInstaller.Session session = pi.openSession(sessionId)) {
                for (Map.Entry<String, File> split : nameSplits(apkFiles).entrySet()) {
                    File apkFile = split.getValue();
                    try (OutputStream out = session.openWrite(split.getKey(), 0, apkFile.length());
                         InputStream in = new FileInputStream(apkFile)) {
                        byte[] buffer = new byte[65536];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        session.fsync(out);
                    }
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
            sessionId = -1;
        } catch (Exception e) {
            if (resultReceiver != null) { try { activity.unregisterReceiver(resultReceiver); } catch (Exception ignored) {} }
            // A session left open leaks until the system times it out; abandon it.
            if (pi != null && sessionId >= 0) {
                try {
                    try (PackageInstaller.Session abandoned = pi.openSession(sessionId)) {
                        abandoned.abandon();
                    }
                } catch (Exception ignored) {}
            }
            dismissProgress(pm);
            new ErrorUtil(activity).showError(e);
        }
    }

    /**
     * Split entries must keep the APK's own split names: PackageInstaller works
     * out which file is the base APK and which are feature splits from those
     * names, so "split_0"/"split_1" made real split sets fail to install.
     */
    private Map<String, File> nameSplits(List<File> apkFiles) {
        Map<String, File> named = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        int fallback = 0;
        for (File apkFile : apkFiles) {
            String name = null;
            try {
                name = apkFile.getName();
            } catch (Exception ignored) {
            }
            if (name == null || name.isEmpty() || !name.toLowerCase(Locale.ROOT).endsWith(".apk")
                    || !used.add(name)) {
                name = "split_" + fallback++;
                while (!used.add(name)) name = "split_" + fallback++;
            }
            named.put(name, apkFile);
        }
        return named;
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

package io.github.abdurazaaqmohammed.utils;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.features.apk.ApkInstallAnalysis;
import io.github.abdurazaaqmohammed.shizuku.IFileService;
import io.github.abdurazaaqmohammed.shizuku.ShizukuConnection;

import com.faith.apkinstaller.APKInstallService;

/**
 * Privileged APK installer with installer-package spoofing.
 *
 * <p>Logic ported from InstallerX Revived (GPL-3.0-only):
 * {@code IBinderAppInstallerRepoImpl} (flag fold + session
 * create/write/commit), {@code SessionParamsExt} (installer package, reason)
 * and {@code ShizukuAppInstallerRepoImpl} (shell/root as the trusted caller).
 *
 * <p>Attribution works because {@code PackageInstallerService#createSessionInternal}
 * short-circuits every installer-ownership check when the caller is root or
 * shell ({@code PackageManagerServiceUtils#isRootOrShell}) and records
 * {@code SessionParams.installerPackageName} as the responsible installer.
 * Only that session field survives: {@code pm install-create -i} sets the
 * caller-package argument instead, which the platform replaces with the shell
 * package for uid 2000 (and some ROMs attribute the install to it), and
 * {@code pm set-installer} is refused for third-party installers
 * ("Caller does not have same cert as new installer package").
 *
 * <p>Routing:
 * <ol>
 *   <li>Root silent ({@code su} + {@code pm}) when root mode + toggle + root.</li>
 *   <li>Shizuku silent through the Shizuku user service, which runs as the
 *       shell uid but owns a {@link Context}, so it can create the session with
 *       {@code setInstallerPackageName}; the {@code pm} transport is kept as a
 *       fallback. APKs are staged through app-readable storage first because
 *       the shell uid cannot read our private cache.</li>
 *   <li>Plain {@link PackageInstaller} session otherwise (system dialog).</li>
 * </ol>
 *
 * <p>Only the installer-of-record field is spoofed. The install *initiator*
 * comes from the session's caller package, which the platform forces to
 * {@code com.android.shell} for root/shell uids, so the service creates its
 * sessions under that caller explicitly (InstallerX does the same for its
 * Shizuku/root authorizers) and additionally claims the spoofed package as the
 * originating package.
 *
 * <p>Safety: installer package names are validated, every shell arg is
 * escaped, staging files use random names under {@code /data/local/tmp} and
 * are always cleaned up, and a signature mismatch against the installed app is
 * reported before anything is staged.
 *
 * <p>SPDX-License-Identifier: GPL-3.0-only
 */
public final class PrivilegedInstaller {

    // PackageManagerHidden INSTALL_* values (InstallerX hidden-api module).
    private static final int FLAG_REPLACE_EXISTING = 0x00000002;
    private static final int FLAG_ALLOW_TEST = 0x00000004;
    private static final int FLAG_ALL_USERS = 0x00000040;
    private static final int FLAG_REQUEST_DOWNGRADE = 0x00000080;
    private static final int FLAG_GRANT_ALL = 0x00000100;
    private static final int FLAG_BYPASS_LOW_TARGET = 0x01000000;

    private static final Pattern SESSION_ID = Pattern.compile("\\[(\\d+)\\]");

    private PrivilegedInstaller() {
    }

    public interface Callback {
        void onSuccess(String installerRecorded);

        void onError(Exception e);
    }

    // ---- routing ----

    public static boolean isRootSilentReady(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            return rm.isSilentInstallEnabled() && rm.isRootAvailable();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isShizukuSilentReady(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            return rm.getWorkingMode() == RootManager.WorkingMode.SHIZUKU
                    && InstallerConfig.isShizukuSilentEnabled(context)
                    && ShizukuShell.isGranted();
        } catch (Exception ignored) {
            return false;
        }
    }

    /** Silent install is possible through either privileged backend. */
    public static boolean isPrivilegedSilentReady(Context context) {
        return isRootSilentReady(context) || isShizukuSilentReady(context);
    }

    public static void installAsync(Context context, List<File> apkFiles, Callback cb) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String recorded = installBlocking(context, apkFiles);
                cb.onSuccess(recorded);
            } catch (Exception e) {
                cb.onError(e);
            }
        });
        executor.shutdown();
    }

    private static volatile String lastTrace = "";

    /** How the last install attributed its installer; shown when the spoof did not stick. */
    public static String lastTrace() {
        return lastTrace;
    }

    public static String installBlocking(Context context, List<File> apkFiles) throws Exception {
        if (apkFiles == null || apkFiles.isEmpty()) throw new IllegalArgumentException("No APKs to install");
        for (File f : apkFiles) {
            if (f == null || !f.exists() || f.length() <= 0)
                throw new IllegalArgumentException("Unreadable APK: " + (f == null ? "null" : f.getPath()));
        }
        String installer = InstallerConfig.resolveInstallerPackageName(context);
        lastTrace = "";

        if (isRootSilentReady(context)) {
            List<String> paths = absPaths(apkFiles);
            RootManager rm = RootManager.getInstance(context);
            rm.installWithInstaller(paths, installer, collectFlags(context));
            lastTrace = "root";
            return installer;
        }
        if (isShizukuSilentReady(context)) {
            shizukuInstallBlocking(context, apkFiles, installer);
            return installer;
        }
        throw new IllegalStateException("No privileged installer available");
    }

    /**
     * Runs the install inside the Shizuku user service, which executes as the
     * shell uid but still owns a {@link Context}. That is the only place where
     * {@code SessionParams#setInstallerPackageName} can be used: the installer
     * recorded by {@code getInstallSourceInfo()} comes from the session params,
     * while {@code pm install-create -i} only sets the caller-package argument,
     * which some ROMs replace with the shell package for uid 2000.
     *
     * @return "ok", "fail:reason", "err:reason" when the service could not run
     * the session at all, or null when the service is unavailable so the caller
     * can fall back to {@code pm}.
     */
    private static String shizukuServiceInstall(Context context, List<String> paths, String installer, int flags) {
        try {
            IFileService service = ShizukuConnection.await(context, 5000);
            if (service == null) {
                ShizukuConnection.unbind(context);
                service = ShizukuConnection.await(context, 5000);
            }
            if (service == null) return null;
            PendingIntent result = PendingIntent.getService(context, 0,
                    new Intent(context, APKInstallService.class), PendingIntent.FLAG_MUTABLE);
            return service.installApks(paths, installer, flags, result.getIntentSender());
        } catch (Throwable t) {
            return "err:" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
    }

    // ---- signature comparison ----

    /**
     * Whether the candidate APK is signed by a different key than the copy of
     * that package already on the device.
     *
     * <p>This used to abort the install outright. It no longer does: a mismatch
     * is information the user should see <em>before</em> deciding, not a fatal
     * error, and it is exactly what Core Patch exists to work around. The
     * comparison now feeds {@code ApkInstallAnalysis}, which shows the warning
     * and offers uninstall-and-reinstall. Unknown results report "matches" so
     * nothing is blocked on a guess.
     */
    public static boolean hasSignatureMismatch(Context context, List<File> apkFiles) {
        try {
            if (apkFiles == null || apkFiles.isEmpty()) return false;
            return ApkInstallAnalysis
                    .analyze(context, apkFiles.get(0)).isDifferentSigner();
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- flags ----

    static int collectFlags(Context context) {
        int flags = FLAG_REPLACE_EXISTING; // InstallerX always forces replace
        if (InstallerConfig.allowTest(context)) flags |= FLAG_ALLOW_TEST;
        if (InstallerConfig.allowDowngrade(context)) flags |= FLAG_REQUEST_DOWNGRADE;
        if (InstallerConfig.grantAllPermissions(context)) flags |= FLAG_GRANT_ALL;
        if (InstallerConfig.bypassLowTargetSdk(context)) flags |= FLAG_BYPASS_LOW_TARGET;
        if (InstallerConfig.installAllUsers(context)) flags |= FLAG_ALL_USERS;
        return flags;
    }

    static List<String> absPaths(List<File> files) {
        List<String> out = new ArrayList<>(files.size());
        for (File f : files) out.add(f.getAbsolutePath());
        return out;
    }

    // ---- root transport (extends RootManager pm flow with spoof) ----

    static String buildCreateCommand(long totalSize, String installerPkg, int flags) {
        StringBuilder sb = new StringBuilder("pm install-create -r");
        if ((flags & FLAG_ALLOW_TEST) != 0) sb.append(" -t");
        if ((flags & FLAG_REQUEST_DOWNGRADE) != 0) sb.append(" -d");
        if ((flags & FLAG_GRANT_ALL) != 0) sb.append(" -g");
        if ((flags & FLAG_BYPASS_LOW_TARGET) != 0
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sb.append(" --bypass-low-target-sdk-block");
        }
        if (installerPkg != null && RootManager.isPackageNameValid(installerPkg)) {
            sb.append(" -i ").append(RootManager.escapeShellArg(installerPkg));
        }
        sb.append(" -S ").append(totalSize);
        return sb.toString();
    }

    /**
     * Some ROMs ship an older {@code pm} that rejects a switch we passed
     * (e.g. {@code --bypass-low-target-sdk-block} pre-Android 14). If the
     * failure is an unknown option, return the command minus that option so
     * the caller can retry; otherwise return null. The installer flag
     * ({@code -i}, supported since the earliest session installs) is dropped
     * last, so attribution is preserved whenever the ROM allows it.
     */
    static String downgradeCreateCommand(String cmd, String combinedOutput) {
        if (cmd == null || combinedOutput == null
                || !combinedOutput.contains("Unknown option")) return null;
        if (cmd.contains(" --bypass-low-target-sdk-block")) {
            return cmd.replace(" --bypass-low-target-sdk-block", "");
        }
        int i = cmd.indexOf(" -i ");
        if (i >= 0) {
            int s = cmd.indexOf(" -S ", i);
            if (s > i) return cmd.substring(0, i) + cmd.substring(s);
            return cmd.substring(0, i);
        }
        return null;
    }

    static String combinedOutput(String out, String err) {
        String a = out == null ? "" : out;
        String b = err == null ? "" : err;
        return a + "\n" + b;
    }

    static String stripInstallerFlag(String cmd) {
        if (cmd == null) return null;
        int i = cmd.indexOf(" -i ");
        if (i < 0) return null;
        int s = cmd.indexOf(" -S ", i);
        if (s > i) return cmd.substring(0, i) + cmd.substring(s);
        return cmd.substring(0, i);
    }

    // ---- shizuku transport ----

    /**
     * The shell uid does not need to own the requested installer package.
     * {@code PackageInstallerService.createSessionInternal} short-circuits every
     * ownership check for {@code isRootOrShell(callingUid)} and takes
     * {@code SessionParams.installerPackageName} ({@code pm}'s {@code -i})
     * verbatim as the responsible installer. So the plain shell transport is
     * already able to attribute the install to any installed package; only the
     * flag has to be passed.
     */
    private static void shizukuInstallBlocking(Context context, List<File> apkFiles, String installer) throws Exception {
        // The shell uid cannot read our private cache, so stage through a
        // shell-readable spot first (app copies out, shell copies in).
        File stageDir = new File(context.getExternalCacheDir() != null
                ? context.getExternalCacheDir() : context.getCacheDir(), "shizuku_install_" + UUID.randomUUID());
        if (!stageDir.mkdirs()) throw new IllegalStateException("Cannot create staging dir");
        List<File> staged = new ArrayList<>();
        try {
            int i = 0;
            Set<String> usedStageNames = new LinkedHashSet<>();
            for (File src : apkFiles) {
                String raw = src.getName();
                String base = raw != null && raw.toLowerCase(Locale.ROOT).endsWith(".apk")
                        ? raw.replaceAll("[^A-Za-z0-9._-]", "_") : null;
                String stageName;
                if (base != null && usedStageNames.add(base)) {
                    stageName = base;
                } else {
                    // Split names must match the APKs' own names, so a duplicate or
                    // unusable one gets a unique partN.apk fallback instead.
                    stageName = "part" + (i++) + ".apk";
                    while (!usedStageNames.add(stageName)) stageName = "part" + (i++) + ".apk";
                }
                File dst = new File(stageDir, stageName);
                copyFile(src, dst);
                staged.add(dst);
            }
            List<String> stagedPaths = absPaths(staged);
            long total = 0;
            for (File f : staged) total += f.length();
            String tag = "_mp" + UUID.randomUUID().toString().replace("-", "");
            List<String> tmpPaths = new ArrayList<>();
            for (int k = 0; k < stagedPaths.size(); k++) tmpPaths.add("/data/local/tmp/" + tag + "_" + k + ".apk");
            String createCmd = buildCreateCommand(total, installer, collectFlags(context));
            try {
                // 1) push staged files where pm can read them
                StringBuilder push = new StringBuilder();
                for (int k = 0; k < stagedPaths.size(); k++) {
                    push.append("cp ").append(RootManager.escapeShellArg(stagedPaths.get(k)))
                            .append(" ").append(RootManager.escapeShellArg(tmpPaths.get(k)))
                            .append(" ; ");
                }
                ShizukuShell.Result pushRes = ShizukuShell.exec(push.toString(), 120);
                if (!pushRes.success) throw new IllegalStateException("Shizuku stage failed: " + pushRes.stderr);

                String serviceResult = shizukuServiceInstall(context, tmpPaths, installer, collectFlags(context));
                if (serviceResult != null && serviceResult.startsWith("ok")) {
                    lastTrace = serviceResult.length() > 2
                            ? "shizuku session (recorded " + serviceResult.substring(3) + ")"
                            : "shizuku session";
                    return;
                }
                if (serviceResult != null && serviceResult.startsWith("fail:")) {
                    // The install actually ran and the system rejected it (e.g.
                    // INSTALL_FAILED_NO_MATCHING_ABIS). A pm retry would fail
                    // the same way, so surface the real reason instead.
                    String reason = serviceResult.substring(5);
                    lastTrace = "shizuku session";
                    throw new IllegalStateException(reason);
                }
                if (serviceResult != null && !serviceResult.startsWith("err:")) {
                    lastTrace = "shizuku session " + serviceResult;
                    throw new IllegalStateException("Shizuku install failed: " + serviceResult);
                }
                lastTrace = "shizuku session "
                        + (serviceResult == null ? "unavailable" : "unavailable (" + serviceResult + ")")
                        + ", pm fallback";

                ShizukuShell.Result createRes = ShizukuShell.exec(createCmd, 60);
                for (int attempt = 0; attempt < 2 && !createRes.success; attempt++) {
                    String retry = downgradeCreateCommand(createCmd,
                            combinedOutput(createRes.stdout, createRes.stderr));
                    if (retry == null) break;
                    createCmd = retry;
                    createRes = ShizukuShell.exec(createCmd, 60);
                }
                if (!createRes.success) {
                    String withoutInstaller = stripInstallerFlag(createCmd);
                    if (withoutInstaller != null) {
                        createCmd = withoutInstaller;
                        createRes = ShizukuShell.exec(createCmd, 60);
                    }
                }
                String session = parseSessionId(createRes.stdout);
                if (!createRes.success || session == null)
                    throw new IllegalStateException("Shizuku install-create failed: "
                            + createRes.stdout + " " + createRes.stderr);

                for (int k = 0; k < staged.size(); k++) {
                    String write = "pm install-write -S " + staged.get(k).length()
                            + " " + session + " " + staged.get(k).getName() + " "
                            + RootManager.escapeShellArg(tmpPaths.get(k));
                    ShizukuShell.Result w = ShizukuShell.exec(write, 120);
                    if (!w.success) {
                        ShizukuShell.exec("pm install-abandon " + session, 15);
                        throw new IllegalStateException("Shizuku install-write failed: " + w.stdout + " " + w.stderr);
                    }
                }
                ShizukuShell.Result commit = ShizukuShell.exec("pm install-commit " + session, 120);
                if (!commit.success || (!commit.stdout.contains("Success") && !commit.stdout.contains("Session")))
                    throw new IllegalStateException("Shizuku install-commit failed: " + commit.stdout + " " + commit.stderr);
            } finally {
                // Always clear /data/local/tmp, even when a write or commit failed.
                StringBuilder cleanup = new StringBuilder();
                for (String tmp : tmpPaths)
                    cleanup.append("rm -f ").append(RootManager.escapeShellArg(tmp)).append(" ; ");
                ShizukuShell.exec(cleanup.toString(), 15);
            }
        } finally {
            for (File f : staged) {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                } catch (Exception ignored) {
                }
            }
            try {
                //noinspection ResultOfMethodCallIgnored
                stageDir.delete();
            } catch (Exception ignored) {
            }
        }
    }

    static String parseSessionId(String output) {
        if (output == null) return null;
        Matcher m = SESSION_ID.matcher(output);
        String last = null;
        while (m.find()) last = m.group(1);
        return last;
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    // ---- non-privileged PackageInstaller session (enhanced) ----

    /**
     * Plain session with InstallerX flag/reason metadata applied on a
     * best-effort basis. The installer attribution is only honoured when the
     * session is created by a privileged caller: on API 34+ PackageManager
     * rejects a spoofed installer name outright ("Package X does not belong to
     * &lt;uid&gt;") instead of silently overwriting it with our own package.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    public static int createSessionWithMetadata(Context context, PackageInstaller installer,
                                                String targetPackage, long totalSize,
                                                String installerPkg, int flags) throws Exception {
        return createSessionWithMetadata(context, installer, targetPackage, totalSize,
                installerPkg, flags, isPrivilegedSilentReady(context));
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    public static int createSessionWithMetadata(Context context, PackageInstaller installer,
                                                String targetPackage, long totalSize,
                                                String installerPkg, int flags,
                                                boolean mayClaimInstaller) throws Exception {
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(totalSize);
        if (targetPackage != null && !targetPackage.trim().isEmpty()
                && RootManager.isPackageNameValid(targetPackage)) {
            try {
                params.setAppPackageName(targetPackage);
            } catch (Exception ignored) {
            }
        }
        if (mayClaimInstaller && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                if (installerPkg != null && !installerPkg.isEmpty()) params.setInstallerPackageName(installerPkg);
            } catch (Exception ignored) {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
            } catch (Exception ignored) {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                params.setInstallReason(PackageManager.INSTALL_REASON_USER);
            } catch (Exception ignored) {
            }
        }
        applyInstallFlags(params, flags);
        try {
            return installer.createSession(params);
        } catch (SecurityException e) {
            // Some ROMs still reject a claimed installer name even from an
            // elevated caller. Retry unspoofed so the install can still proceed.
            if (!mayClaimInstaller || installerPkg == null || installerPkg.isEmpty()) throw e;
            PackageInstaller.SessionParams retry =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            retry.setSize(totalSize);
            if (targetPackage != null && !targetPackage.trim().isEmpty()
                    && RootManager.isPackageNameValid(targetPackage)) {
                try {
                    retry.setAppPackageName(targetPackage);
                } catch (Exception ignored) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    retry.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                } catch (Exception ignored) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    retry.setInstallReason(PackageManager.INSTALL_REASON_USER);
                } catch (Exception ignored) {
                }
            }
            applyInstallFlags(retry, flags);
            return installer.createSession(retry);
        }
    }

    /** Hidden-API write of the INSTALL_* flags onto a session; silently skipped when blocked. */
    public static void applyInstallFlags(PackageInstaller.SessionParams params, int flags) {
        try {
            Field f = PackageInstaller.SessionParams.class.getDeclaredField("installFlags");
            f.setAccessible(true);
            int cur = f.getInt(params);
            f.setInt(params, cur | flags | FLAG_REPLACE_EXISTING);
        } catch (Exception ignored) {
            // Hidden-API blocked on this ROM: session still installs, just
            // without the extra flags. Never fatal.
        }
    }

    /** Read back who the system recorded, for the result dialog. */
    public static String readRecordedInstaller(Context context, String packageName) {
        try {
            PackageManager pm = context.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InstallSourceInfo info = pm.getInstallSourceInfo(packageName);
                if (info != null && info.getInstallingPackageName() != null)
                    return info.getInstallingPackageName();
            }
            String legacy = pm.getInstallerPackageName(packageName);
            return legacy != null ? legacy : "?";
        } catch (Exception ignored) {
            return "?";
        }
    }

    /** Share intent used by the "open Play Store page" affordance. */
    public static Intent playStoreIntent(String packageName) {
        return new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + packageName));
    }
}

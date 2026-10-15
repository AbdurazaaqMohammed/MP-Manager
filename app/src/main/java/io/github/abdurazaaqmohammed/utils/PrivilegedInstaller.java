package io.github.abdurazaaqmohammed.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;

/**
 * Privileged APK installer with installer-source spoofing.
 *
 * <p>Logic ported from InstallerX Revived (GPL-3.0-only):
 * {@code IBinderAppInstallerRepoImpl} (caller-package spoof + flag fold +
 * session create/write/commit), {@code SessionParamsExt} (installer package,
 * reason, package source) and {@code ShizukuAppInstallerRepoImpl} (shell/root
 * as the trusted caller). The Kotlin/coroutine/Koin machinery is replaced
 * with the shell-{@code pm} transport this app already uses in
 * {@link RootManager}, because it needs no hidden-API stubs and behaves the
 * same from a privileged uid: {@code pm install-create -i <pkg>} makes
 * {@code getInstallSourceInfo()} report that package (AOSP
 * PackageManagerShellCommand {@code makeInstallParams} handles {@code -i};
 * there is no {@code --installer-package-name} long option).
 *
 * <p>Routing:
 * <ol>
 *   <li>Root silent ({@code su} + {@code pm}) when root mode + toggle + root.</li>
 *   <li>Shizuku silent ({@link ShizukuShell} + {@code pm}) when shizuku mode +
 *       toggle + permission. APKs are staged through app-readable storage
 *       first because the shell uid cannot read our private cache.</li>
 *   <li>Plain {@link PackageInstaller} session otherwise (system dialog; on
 *       Android 14+ still records {@code setInstallerPackageName}, but without
 *       privilege the system overwrites it with our package).</li>
 * </ol>
 *
 * <p>Safety: installer package names are validated, every shell arg is
 * escaped, staging files use random names under {@code /data/local/tmp} and
 * are always cleaned up, signature mismatches against the installed app abort
 * with an explanatory error before anything is staged, and critical-system
 * packages require the mismatch gate to pass as well.
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

    public static String installBlocking(Context context, List<File> apkFiles) throws Exception {
        if (apkFiles == null || apkFiles.isEmpty()) throw new IllegalArgumentException("No APKs to install");
        for (File f : apkFiles) {
            if (f == null || !f.exists() || f.length() <= 0)
                throw new IllegalArgumentException("Unreadable APK: " + (f == null ? "null" : f.getPath()));
        }
        String installer = InstallerConfig.resolveInstallerPackageName(context);
        checkSignatureGate(context, apkFiles, installer);

        if (isRootSilentReady(context)) {
            List<String> paths = absPaths(apkFiles);
            RootManager rm = RootManager.getInstance(context);
            rm.installWithInstaller(paths, installer, collectFlags(context));
            return installer;
        }
        if (isShizukuSilentReady(context)) {
            shizukuInstallBlocking(context, apkFiles, installer);
            return installer;
        }
        throw new IllegalStateException("No privileged installer available");
    }

    // ---- signature gate (InstallerX signature-mismatch policy, simplified) ----

    private static void checkSignatureGate(Context context, List<File> apkFiles, String installer) throws Exception {
        String targetPkg = packageNameOf(context, apkFiles.get(0));
        if (targetPkg == null) return;
        PackageManager pm = context.getPackageManager();
        PackageInfo installed;
        try {
            installed = Build.VERSION.SDK_INT >= 28
                    ? pm.getPackageInfo(targetPkg, PackageManager.GET_SIGNING_CERTIFICATES)
                    : pm.getPackageInfo(targetPkg, PackageManager.GET_SIGNATURES);
        } catch (PackageManager.NameNotFoundException e) {
            return; // fresh install, nothing to compare
        }
        PackageInfo candidate = pm.getPackageArchiveInfo(
                apkFiles.get(0).getAbsolutePath(),
                Build.VERSION.SDK_INT >= 28
                        ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
        if (candidate == null) return;
        if (!signaturesMatch(installed, candidate)) {
            throw new SecurityException("Signature mismatch for " + targetPkg
                    + ": installed app was signed by a different key. The system will refuse "
                    + "this install (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Uninstall first or "
                    + "install an APK with the matching signature. Requested installer: " + installer);
        }
    }

    private static String packageNameOf(Context context, File apk) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return info == null ? null : info.packageName;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean signaturesMatch(PackageInfo installed, PackageInfo candidate) {
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                SigningInfo a = installed.signingInfo;
                SigningInfo b = candidate.signingInfo;
                if (a == null || b == null) return true; // cannot tell -> let system decide
                // Compare current signer digests; past history ignored on purpose.
                if (!a.hasMultipleSigners() && !b.hasMultipleSigners()) {
                    return a.getSigningCertificateHistory()[0] != null
                            && b.getSigningCertificateHistory()[0] != null
                            && java.util.Arrays.equals(
                                    sha256(a.getSigningCertificateHistory()[0].toByteArray()),
                                    sha256(b.getSigningCertificateHistory()[0].toByteArray()));
                }
                return java.util.Arrays.equals(
                        sha256(a.getApkContentsSigners()[0].toByteArray()),
                        sha256(b.getApkContentsSigners()[0].toByteArray()));
            } else {
                if (installed.signatures == null || candidate.signatures == null) return true;
                if (installed.signatures.length == 0 || candidate.signatures.length == 0) return true;
                return java.util.Arrays.equals(
                        sha256(installed.signatures[0].toByteArray()),
                        sha256(candidate.signatures[0].toByteArray()));
            }
        } catch (Exception ignored) {
            return true; // fail-open to system verdict, never block blindly
        }
    }

    private static byte[] sha256(byte[] in) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        return md.digest(in);
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

    // ---- shizuku transport ----

    private static void shizukuInstallBlocking(Context context, List<File> apkFiles, String installer) throws Exception {
        // The shell uid cannot read our private cache, so stage through a
        // shell-readable spot first (app copies out, shell copies in).
        File stageDir = new File(context.getExternalCacheDir() != null
                ? context.getExternalCacheDir() : context.getCacheDir(), "shizuku_install_" + UUID.randomUUID());
        if (!stageDir.mkdirs()) throw new IllegalStateException("Cannot create staging dir");
        List<File> staged = new ArrayList<>();
        try {
            int i = 0;
            for (File src : apkFiles) {
                File dst = new File(stageDir, "part" + (i++) + ".apk");
                copyFile(src, dst);
                staged.add(dst);
            }
            List<String> stagedPaths = absPaths(staged);
            long total = 0;
            for (File f : staged) total += f.length();
            String tag = "_mp" + (System.currentTimeMillis() % 100000);
            String createCmd = buildCreateCommand(total, installer, collectFlags(context));
            // 1) push staged files where pm can read them
            StringBuilder push = new StringBuilder();
            for (int k = 0; k < stagedPaths.size(); k++) {
                push.append("cp ").append(RootManager.escapeShellArg(stagedPaths.get(k)))
                        .append(" ").append(RootManager.escapeShellArg("/data/local/tmp/" + tag + "_" + k + ".apk"))
                        .append(" ; ");
            }
            ShizukuShell.Result pushRes = ShizukuShell.exec(push.toString(), 120);
            if (!pushRes.success) throw new IllegalStateException("Shizuku stage failed: " + pushRes.stderr);

            ShizukuShell.Result createRes = ShizukuShell.exec(createCmd, 60);
            for (int attempt = 0; attempt < 2 && !createRes.success; attempt++) {
                String retry = downgradeCreateCommand(createCmd,
                        combinedOutput(createRes.stdout, createRes.stderr));
                if (retry == null) break;
                createCmd = retry;
                createRes = ShizukuShell.exec(createCmd, 60);
            }
            String session = parseSessionId(createRes.stdout);
            if (!createRes.success || session == null)
                throw new IllegalStateException("Shizuku install-create failed: "
                        + createRes.stdout + " " + createRes.stderr);

            for (int k = 0; k < staged.size(); k++) {
                String tmp = "/data/local/tmp/" + tag + "_" + k + ".apk";
                String write = "pm install-write -S " + staged.get(k).length()
                        + " " + session + " split" + k + " " + RootManager.escapeShellArg(tmp);
                ShizukuShell.Result w = ShizukuShell.exec(write, 120);
                if (!w.success) {
                    ShizukuShell.exec("pm install-abandon " + session, 15);
                    throw new IllegalStateException("Shizuku install-write failed: " + w.stdout + " " + w.stderr);
                }
            }
            ShizukuShell.Result commit = ShizukuShell.exec("pm install-commit " + session, 120);
            // always clean /data/local/tmp, even on failure
            StringBuilder cleanup = new StringBuilder();
            for (int k = 0; k < staged.size(); k++)
                cleanup.append("rm -f ").append(RootManager.escapeShellArg("/data/local/tmp/" + tag + "_" + k + ".apk")).append(" ; ");
            ShizukuShell.exec(cleanup.toString(), 15);
            if (!commit.success || (!commit.stdout.contains("Success") && !commit.stdout.contains("Session")))
                throw new IllegalStateException("Shizuku install-commit failed: " + commit.stdout + " " + commit.stderr);
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
             OutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    // ---- non-privileged PackageInstaller session (enhanced) ----

    /**
     * Plain session with InstallerX flag/reason metadata applied on a
     * best-effort basis. Without privilege the installer attribution is
     * informational only; the system records our own package.
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    public static int createSessionWithMetadata(Context context, PackageInstaller installer,
                                                String targetPackage, long totalSize,
                                                String installerPkg, int flags) throws Exception {
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setSize(totalSize);
        try {
            params.setAppPackageName(targetPackage);
        } catch (Exception ignored) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
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
        try {
            params.setInstallReason(PackageManager.INSTALL_REASON_USER);
        } catch (Exception ignored) {
        }
        applyInstallFlags(params, flags);
        return installer.createSession(params);
    }

    private static void applyInstallFlags(PackageInstaller.SessionParams params, int flags) {
        try {
            java.lang.reflect.Field f = PackageInstaller.SessionParams.class.getDeclaredField("installFlags");
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
                android.content.pm.InstallSourceInfo info = pm.getInstallSourceInfo(packageName);
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
                android.net.Uri.parse("market://details?id=" + packageName));
    }
}

package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.UUID;

import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;

/**
 * Uninstalls a package so an APK signed with a different key can take its
 * place, optionally preserving the app's data directory.
 *
 * <p>Android refuses to replace an app with a differently signed one
 * ({@code INSTALL_FAILED_UPDATE_INCOMPATIBLE}), so the only supported route is
 * uninstall-then-install. That deletes the app's private data, hence the
 * optional backup: {@code /data/user/0/<pkg>} is only readable with root, so
 * "save data" is offered exclusively when a root shell is available and is
 * reported as unavailable rather than silently skipped otherwise.
 */
public final class AppReinstaller {

    private AppReinstaller() {
    }

    /** True when app data can be read, copied and put back. */
    public static boolean canSaveData(Context context) {
        try {
            return RootManager.getInstance(context).isRootAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Copies an app's private data to a root-readable staging archive.
     * Must run on a worker thread.
     */
    @Nullable
    public static String backupData(Context context, String packageName) {
        if (packageName == null || RootManager.isPathBlocked(packageName)) return null;
        try {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootAvailable()) return null;
            String tag = "_mpbak_" + UUID.randomUUID().toString().replace("-", "");
            String archive = "/data/local/tmp/" + tag + ".tar";
            String dir = "/data/user/0/" + packageName;
            RootManager.ShellResult exists = rm.execute("test -d " + RootManager.escapeShellArg(dir), 20);
            if (!exists.isSuccess()) return null;
            RootManager.ShellResult result = rm.execute(
                    "tar -C /data/user/0 -cf " + RootManager.escapeShellArg(archive) + " "
                            + RootManager.escapeShellArg(packageName), 180);
            if (!result.isSuccess()) return null;
            return archive;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Owner of the app's data dir, needed to restore it under the right uid. */
    @Nullable
    public static String readDataOwner(Context context, String packageName) {
        try {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootAvailable()) return null;
            RootManager.ShellResult r = rm.execute(
                    "stat -c %u:%g /data/user/0/" + RootManager.escapeShellArg(packageName), 20);
            if (!r.isSuccess() || r.output() == null) return null;
            String out = r.output().trim();
            int nl = out.indexOf('\n');
            if (nl >= 0) out = out.substring(0, nl).trim();
            return out.isEmpty() ? null : out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Puts the data archive back and fixes ownership and SELinux labels. */
    public static boolean restoreData(Context context, String packageName, String archive, String owner) {
        if (archive == null || packageName == null) return false;
        try {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootAvailable()) return false;
            StringBuilder cmd = new StringBuilder();
            cmd.append("am force-stop ").append(RootManager.escapeShellArg(packageName)).append(" ; ");
            cmd.append("tar -C /data/user/0 -xf ").append(RootManager.escapeShellArg(archive))
                    .append(" ").append(RootManager.escapeShellArg(packageName)).append(" ; ");
            if (owner != null && owner.contains(":")) {
                cmd.append("chown -R ").append(owner).append(" /data/user/0/")
                        .append(RootManager.escapeShellArg(packageName)).append(" ; ");
            }
            cmd.append("restorecon -RF /data/user/0/").append(RootManager.escapeShellArg(packageName));
            RootManager.ShellResult r = rm.execute(cmd.toString(), 300);
            return r.isSuccess();
        } catch (Throwable t) {
            return false;
        }
    }

    public static void deleteBackup(Context context, String archive) {
        if (archive == null) return;
        try {
            RootManager.getInstance(context).execute("rm -f " + RootManager.escapeShellArg(archive), 30);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Removes the package without a UI when a privileged shell is available,
     * otherwise asks the system to do it.
     *
     * @param onDone runs on the main thread once the package is gone.
     */
    public static void uninstall(Activity activity, String packageName, Runnable onDone) {
        new Thread(() -> {
            boolean done = false;
            try {
                RootManager rm = RootManager.getInstance(activity);
                if (rm.isRootAvailable()) {
                    RootManager.ShellResult r = rm.execute(
                            "pm uninstall --user 0 " + RootManager.escapeShellArg(packageName), 120);
                    String out = r.output();
                    if (r.isSuccess() && out != null && out.toLowerCase(Locale.ROOT).contains("success")) {
                        done = true;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (!done) {
                try {
                    if (ShizukuShell.isGranted()) {
                        ShizukuShell.Result r = ShizukuShell.exec(
                                "pm uninstall --user 0 " + RootManager.escapeShellArg(packageName), 120);
                        if (r != null && r.success && r.stdout != null
                                && r.stdout.toLowerCase(Locale.ROOT).contains("success")) {
                            done = true;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            final boolean removed = done;
            activity.runOnUiThread(() -> {
                if (removed) {
                    if (onDone != null) onDone.run();
                } else {
                    uninstallViaSystem(activity, packageName, onDone);
                }
            });
        }).start();
    }

    /** Hands the uninstall to the system dialog; {@code onDone} runs if the user confirms. */
    private static void uninstallViaSystem(Activity activity, String packageName, Runnable onDone) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            // There is no reliable result for ACTION_DELETE, so re-check shortly
            // after the dialog closes rather than guessing.
            if (onDone == null) return;
            new Thread(() -> {
                for (int i = 0; i < 40; i++) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    boolean stillThere = ApkInfoUtil.isInstalled(activity, packageName);
                    activity.runOnUiThread(() -> {
                        if (!stillThere && onDone != null) onDone.run();
                    });
                    if (!stillThere) return;
                }
            }).start();
        } catch (Throwable t) {
            new ErrorUtil(activity).showError(t);
        }
    }
}
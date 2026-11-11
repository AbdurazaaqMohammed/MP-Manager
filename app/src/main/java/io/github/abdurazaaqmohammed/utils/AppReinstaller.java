package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
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

    private static final String TAG = "AppReinstaller";

    private AppReinstaller() {
    }

    /** True when app data can be read, copied and put back. */
    public static boolean canSaveData(Context context) {
        try {
            return RootManager.getInstance(context).isRootAvailable();
        } catch (Throwable t) {
            Log.w(TAG, "canSaveData check failed", t);
            return false;
        }
    }

    /**
     * Copies an app's private data to a root-readable staging archive.
     * Must run on a worker thread.
     *
     * @return Path to the created staging archive on success.
     * @throws IOException with exact details on failure instead of silently swallowing errors.
     */
    @NonNull
    public static String backupDataOrThrow(Context context, String packageName) throws IOException {
        if (packageName == null || RootManager.isPathBlocked(packageName)) {
            throw new IOException("Package name is null or blocked: " + packageName);
        }
        RootManager rm;
        try {
            rm = RootManager.getInstance(context);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to get RootManager instance", t);
            throw new IOException("Root initialization failed: " + t.getMessage(), t);
        }
        if (!rm.isRootAvailable()) {
            throw new IOException("Root access is not available or was denied.");
        }

        String tag = "_mpbak_" + UUID.randomUUID().toString().replace("-", "");
        String archive = "/data/local/tmp/" + tag + ".tar";

        // Force-stop app first to release sqlite and file locks
        rm.executeFs("am force-stop " + RootManager.escapeShellArg(packageName), 15);

        // Check if data directory exists using filesystem root check (supports global namespace)
        String baseDir = "/data/user/0";
        String dir = baseDir + "/" + packageName;
        if (!rm.isDirectory(dir)) {
            baseDir = "/data/data";
            dir = baseDir + "/" + packageName;
            if (!rm.isDirectory(dir)) {
                // If isDirectory returned false, run a final direct shell probe before aborting
                RootManager.ShellResult probe = rm.executeFs("( [ -e " + RootManager.escapeShellArg(dir) + " ] || ls -d " + RootManager.escapeShellArg(dir) + " ) 2>/dev/null", 15);
                if (!probe.isSuccess() || probe.output() == null || probe.output().trim().isEmpty()) {
                    String err = "Data directory does not exist or cannot be accessed: " + dir;
                    Log.e(TAG, "backupData dir check failed: " + err);
                    throw new IOException(err);
                }
            }
        }

        // Exclude cache and code_cache to prevent locks on transient files and avoid out-of-space errors
        String tarCmd = "tar -C " + baseDir + " --exclude cache --exclude code_cache -cf "
                + RootManager.escapeShellArg(archive) + " " + RootManager.escapeShellArg(packageName);
        RootManager.ShellResult result = rm.executeFs(tarCmd, 180);
        
        // Check if archive was successfully created and is non-empty
        RootManager.ShellResult checkArchive = rm.executeFs("test -s " + RootManager.escapeShellArg(archive), 15);
        if (!checkArchive.isSuccess()) {
            String err = result.error().isEmpty() ? (result.output().isEmpty() ? "tar exited with code " + result.exitCode() : result.output()) : result.error();
            Log.e(TAG, "backupData tar command failed: " + err + " (exit: " + result.exitCode() + ")");
            // Try cleaning up any partial archive
            deleteBackup(context, archive);
            throw new IOException("Archive creation failed: " + err);
        }

        Log.i(TAG, "backupData succeeded: " + archive);
        return archive;
    }

    /**
     * Backwards-compatible wrapper returning null on failure, but logging the exact error.
     */
    @Nullable
    public static String backupData(Context context, String packageName) {
        try {
            return backupDataOrThrow(context, packageName);
        } catch (Throwable t) {
            Log.e(TAG, "backupData failed for " + packageName, t);
            return null;
        }
    }

    /** Owner of the app's data dir, needed to restore it under the right uid. */
    @Nullable
    public static String readDataOwner(Context context, String packageName) {
        try {
            RootManager rm = RootManager.getInstance(context);
            if (!rm.isRootAvailable()) {
                Log.w(TAG, "readDataOwner: root unavailable for " + packageName);
                return null;
            }
            RootManager.ShellResult r = rm.executeFs(
                    "stat -c %u:%g /data/user/0/" + RootManager.escapeShellArg(packageName), 20);
            if (!r.isSuccess() || r.output() == null) {
                // Try fallback to /data/data
                r = rm.executeFs("stat -c %u:%g /data/data/" + RootManager.escapeShellArg(packageName), 20);
                if (!r.isSuccess() || r.output() == null) {
                    Log.w(TAG, "readDataOwner stat failed: " + r.error() + " (exit: " + r.exitCode() + ")");
                    return null;
                }
            }
            String out = r.output().trim();
            int nl = out.indexOf('\n');
            if (nl >= 0) out = out.substring(0, nl).trim();
            return out.isEmpty() ? null : out;
        } catch (Throwable t) {
            Log.e(TAG, "readDataOwner error for " + packageName, t);
            return null;
        }
    }

    /**
     * Puts the data archive back and fixes ownership and SELinux labels.
     * Throws an exception on failure so caller can inform the user of the exact issue.
     */
    public static void restoreDataOrThrow(Context context, String packageName, String archive, String owner) throws IOException {
        if (archive == null || packageName == null) {
            throw new IOException("Invalid arguments: archive=" + archive + ", package=" + packageName);
        }
        RootManager rm;
        try {
            rm = RootManager.getInstance(context);
        } catch (Throwable t) {
            Log.e(TAG, "restoreData: Failed to get RootManager", t);
            throw new IOException("Root initialization failed: " + t.getMessage(), t);
        }
        if (!rm.isRootAvailable()) {
            throw new IOException("Root access is not available or was denied.");
        }

        StringBuilder cmd = new StringBuilder();
        cmd.append("am force-stop ").append(RootManager.escapeShellArg(packageName)).append(" ; ");
        cmd.append("tar -C /data/user/0 -xf ").append(RootManager.escapeShellArg(archive))
                .append(" ").append(RootManager.escapeShellArg(packageName)).append(" ; ");
        if (owner != null && owner.contains(":")) {
            cmd.append("chown -R ").append(owner).append(" /data/user/0/")
                    .append(RootManager.escapeShellArg(packageName)).append(" ; ");
        }
        cmd.append("restorecon -RF /data/user/0/").append(RootManager.escapeShellArg(packageName));

        RootManager.ShellResult r = rm.executeFs(cmd.toString(), 300);
        if (!r.isSuccess()) {
            String err = r.error().isEmpty() ? (r.output().isEmpty() ? "restore command exited with code " + r.exitCode() : r.output()) : r.error();
            Log.e(TAG, "restoreData failed: " + err + " (exit: " + r.exitCode() + ")");
            throw new IOException("Data restore failed: " + err);
        }
        Log.i(TAG, "restoreData succeeded for " + packageName);
    }

    /** Puts the data archive back and fixes ownership and SELinux labels. */
    public static boolean restoreData(Context context, String packageName, String archive, String owner) {
        try {
            restoreDataOrThrow(context, packageName, archive, owner);
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "restoreData failed for " + packageName, t);
            return false;
        }
    }

    public static void deleteBackup(Context context, String archive) {
        if (archive == null) return;
        try {
            RootManager.getInstance(context).execute("rm -f " + RootManager.escapeShellArg(archive), 30);
        } catch (Throwable t) {
            Log.w(TAG, "deleteBackup failed to remove: " + archive, t);
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
                    // Try full uninstall first to clear package signature completely across system
                    RootManager.ShellResult r = rm.execute(
                            "pm uninstall " + RootManager.escapeShellArg(packageName), 120);
                    String out = r.output();
                    if (r.isSuccess() && out != null && out.toLowerCase(Locale.ROOT).contains("success")) {
                        done = true;
                    } else {
                        // Fallback to --user 0 if multi-user or system restrictions require it
                        RootManager.ShellResult rUser0 = rm.execute(
                                "pm uninstall --user 0 " + RootManager.escapeShellArg(packageName), 120);
                        String outUser0 = rUser0.output();
                        if (rUser0.isSuccess() && outUser0 != null && outUser0.toLowerCase(Locale.ROOT).contains("success")) {
                            done = true;
                        } else {
                            Log.w(TAG, "Root pm uninstall output: " + out + " / " + outUser0 + ", error: " + r.error());
                        }
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Root uninstall failed", t);
            }
            if (!done) {
                try {
                    if (ShizukuShell.isGranted()) {
                        ShizukuShell.Result r = ShizukuShell.exec(
                                "pm uninstall --user 0 " + RootManager.escapeShellArg(packageName), 120);
                        if (r != null && r.success && r.stdout != null
                                && r.stdout.toLowerCase(Locale.ROOT).contains("success")) {
                            done = true;
                        } else if (r != null) {
                            Log.w(TAG, "Shizuku pm uninstall stdout: " + r.stdout + ", stderr: " + r.stderr);
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "Shizuku uninstall failed", t);
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
            Log.e(TAG, "uninstallViaSystem failed", t);
            new ErrorUtil(activity).showError(t);
        }
    }
}
package io.github.abdurazaaqmohammed.MPManager.shizuku;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.github.abdurazaaqmohammed.utils.RootManager;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuProcessFactory;
import rikka.shizuku.ShizukuProvider;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * Thin wrapper around the Shizuku binder API for running shell commands as the shell (uid 2000).
 * Used to access /storage/emulated/0/Android/data which is blocked for regular apps on Android 11+.
 */
public final class ShizukuShell {

    private static final String TAG = "ShizukuShell";
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    private static final List<Runnable> BINDER_CALLBACKS = new ArrayList<>();
    private static boolean listenerRegistered;

    private ShizukuShell() {
    }

    /**
     * Registers binder listeners and asks the Shizuku server for its binder.
     * The server pushes the binder to our provider when it observes our process start;
     * if that push raced with (or predates) our process start we never receive it,
     * so explicitly request it via the provider's requestBinderForNonProviderProcess.
     */
    public static void warmUp(Context context) {
        try {
            if (!listenerRegistered) {
                listenerRegistered = true;
                Shizuku.addBinderReceivedListenerSticky(() -> {
                    Log.d(TAG, "binder received, granted=" + isGranted());
                    notifyBinderCallbacks();
                });
                Shizuku.addBinderDeadListener(() -> Log.d(TAG, "binder dead"));
            }
            Log.d(TAG, "warmUp: binder=" + Shizuku.getBinder() + " ping=" + Shizuku.pingBinder());
        } catch (Throwable t) {
            Log.d(TAG, "warmUp listener registration failed", t);
        }
        try {
            ShizukuProvider.requestBinderForNonProviderProcess(context);
        } catch (Throwable t) {
            Log.d(TAG, "requestBinderForNonProviderProcess failed", t);
        }
    }

    /** Callbacks invoked when the binder arrives; e.g. retrying a folder load that failed earlier. */
    public static void onBinderReceived(Runnable callback) {
        synchronized (BINDER_CALLBACKS) {
            BINDER_CALLBACKS.add(callback);
        }
    }

    private static void notifyBinderCallbacks() {
        Runnable[] callbacks;
        synchronized (BINDER_CALLBACKS) {
            callbacks = BINDER_CALLBACKS.toArray(new Runnable[0]);
        }
        for (Runnable r : callbacks) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    /** Shizuku app installed and its binder is alive. */
    public static boolean isAvailable() {
        try {
            boolean ping = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Shizuku.pingBinder();
            Log.d(TAG, "isAvailable: " + ping);
            return ping;
        } catch (Throwable t) {
            Log.d(TAG, "isAvailable failed", t);
            return false;
        }
    }

    /** Shizuku app installed (binder may not be running yet). */
    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static boolean isGranted() {
        if (!isAvailable()) {
            Log.d(TAG, "isGranted: false (binder not available)");
            return false;
        }
        try {
            boolean granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "isGranted: " + granted);
            return granted;
        } catch (Throwable t) {
            Log.d(TAG, "isGranted failed", t);
            return false;
        }
    }

    public static void requestPermission() {
        if (isAvailable() && !isGranted()) {
            Shizuku.requestPermission(0);
        }
    }

    /** Open the Shizuku app so the user can start the service. */
    public static void openShizukuApp(Context context) {
        try {
            context.startActivity(context.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Run a command via Shizuku's shell process. Arguments must already be escaped
     * (see {@link RootManager#escapeShellArg}).
     */
    public static Result exec(String command) {
        return exec(command, DEFAULT_TIMEOUT_SECONDS);
    }

    public static Result exec(String command, long timeoutSeconds) {
        if (!isGranted()) {
            Log.d(TAG, "exec: not granted, refusing: " + command);
            return new Result(false, "", "Shizuku not granted", -1);
        }
        try {
            ShizukuRemoteProcess p =
                    (ShizukuRemoteProcess) new ShizukuProcessFactory()
                            .newProcess(new String[]{"sh", "-c", command}, null, "/");
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread outT = drain(p.getInputStream(), out);
            Thread errT = drain(p.getErrorStream(), err);
            // Process.waitFor(timeout) is broken here: ShizukuRemoteProcess.exitValue() is a binder
            // IPC throwing IllegalArgumentException("process hasn't exited") while still running.
            // waitForTimeout() blocks server-side and returns cleanly.
            boolean finished = p.waitForTimeout(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                try {
                    p.destroy();
                } catch (Throwable ignored) {
                }
                Log.d(TAG, "exec: timeout after " + timeoutSeconds + "s: " + command);
                return new Result(false, out.toString(), "timeout", -1);
            }
            outT.join(1000);
            errT.join(1000);
            Log.d(TAG, "exec: exit=" + p.exitValue() + " cmd=" + command + " stderr=" + err);
            return new Result(p.exitValue() == 0, out.toString(), err.toString(), p.exitValue());
        } catch (Throwable t) {
            Log.d(TAG, "exec failed: " + command, t);
            return new Result(false, "", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(), -1);
        }
    }

    private static Thread drain(InputStream is, StringBuilder sb) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (IOException ignored) {
            }
        });
        t.start();
        return t;
    }

    public static final class Result {
        public final boolean success;
        public final String stdout;
        public final String stderr;
        public final int exitCode;

        Result(boolean success, String stdout, String stderr, int exitCode) {
            this.success = success;
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }
    }
}

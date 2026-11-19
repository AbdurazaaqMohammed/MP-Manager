/*
 * Privilege layer — Dhizuku backend (device-owner delegation).
 *
 * The first real exercise of the plugin design: ONE new file + ONE
 * Registry.register line. It implements only ShellCap — the registry
 * automatically falls through to SHIZUKU / ROOT for file ops and installs
 * it does not support, so no UnsupportedOperationException stubs and no
 * paired isXxxReady() methods.
 */
package io.github.abdurazaaqmohammed.privilege.backend;

import android.content.Context;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;
import com.rosan.dhizuku.api.DhizukuRemoteProcess;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import io.github.abdurazaaqmohammed.privilege.api.ShellSession;
import io.github.abdurazaaqmohammed.privilege.spi.Capability;
import io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend;
import io.github.abdurazaaqmohammed.privilege.spi.ShellCap;

public final class DhizukuBackend implements PrivilegeBackend, ShellCap {

    private static final DhizukuBackend INSTANCE = new DhizukuBackend();

    public static DhizukuBackend get() {
        return INSTANCE;
    }

    private DhizukuBackend() {
    }

    @Override
    public String id() {
        return "dhizuku";
    }

    @Override
    public String label() {
        return "Dhizuku (device owner)";
    }

    @Override
    public int priorityFor(Class<? extends Capability> capability) {
        // Device-owner domain. Yields the shell to SHIZUKU (30) — minimal
        // privilege escalation — but stays above ROOT (10) so persistent
        // delegation is preferred over su when both can serve.
        return 20;
    }

    @Override
    public boolean isAvailable(Context context) {
        try {
            return context != null && Dhizuku.init(context);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean isAuthorized(Context context) {
        try {
            return isAvailable(context) && Dhizuku.isPermissionGranted();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public ShellSession openShell() {
        return DhizukuShellSession.INSTANCE;
    }

    private static final class DhizukuShellSession implements ShellSession {

        static final DhizukuShellSession INSTANCE = new DhizukuShellSession();

        private DhizukuShellSession() {
        }

        @Override
        public Result exec(String command, long timeoutSeconds) {
            try {
                DhizukuRemoteProcess process =
                        Dhizuku.newProcess(new String[]{"/system/bin/sh", "-c", command}, null, null);
                StringBuilder stdout = new StringBuilder();
                StringBuilder stderr = new StringBuilder();
                Thread out = pump(process.getInputStream(), stdout);
                Thread err = pump(process.getErrorStream(), stderr);
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroy();
                    out.interrupt();
                    err.interrupt();
                    return new Result(false, stdout.toString(), stderr.toString(), -1);
                }
                joinQuietly(out, err);
                int exit = process.exitValue();
                return new Result(exit == 0, stdout.toString(), stderr.toString(), exit);
            } catch (Throwable t) {
                return new Result(false, "", String.valueOf(t), -1);
            }
        }

        @Override
        public Result exec(String command) {
            return exec(command, 30);
        }

        private static Thread pump(InputStream in, StringBuilder into) {
            Thread t = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        synchronized (into) {
                            into.append(line).append('\n');
                        }
                    }
                } catch (Exception ignored) {
                }
            });
            t.setDaemon(true);
            t.start();
            return t;
        }

        private static void joinQuietly(Thread... threads) {
            for (Thread t : threads) {
                try {
                    t.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public boolean isGranted() {
            return Dhizuku.isPermissionGranted();
        }

        @Override
        public boolean isAvailable() {
            return Dhizuku.init();
        }

        @Override
        public boolean isInstalled(Context context) {
            return context != null && Dhizuku.init(context);
        }

        @Override
        public void warmUp(Context context) {
            Dhizuku.init(context);
        }

        @Override
        public void requestPermission() {
            Dhizuku.requestPermission(new DhizukuRequestPermissionListener() {
                @Override
                public void onRequestPermission(int grantResult) {
                    // callers watch authorization via isGranted(); keep it fire-and-forget
                }
            });
        }

        @Override
        public void openShizukuApp(Context context) {
            // Dhizuku is a device-owner delegation: there is no helper app to open
        }

        @Override
        public void onBinderReceived(Runnable callback) {
            callback.run();
        }
    }
}

package io.github.abdurazaaqmohammed.privilege.api;

import android.content.Context;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.github.abdurazaaqmohammed.privilege.impl.RootManager;

/** Root script channel for patch engines whose scripts specify the mount namespace. */
public final class RootScriptSession {
    private RootScriptSession() {}

    public static void execute(Context context, String script, boolean privateNamespace,
                               OutputStream output, int timeoutSeconds) throws IOException {
        String shell = privateNamespace ? "unshare -m sh" : "sh";
        Process process = new ProcessBuilder(RootManager.getInstance(context).suBinary(), "--mount-master", "-c", shell).start();
        StringBuilder errors = new StringBuilder();
        AtomicReference<IOException> readError = new AtomicReference<>();
        Thread stdout = new Thread(() -> {
            try (var input = process.getInputStream()) { org.apache.commons.io.IOUtils.copyLarge(input, output); }
            catch (IOException error) { readError.set(error); }
        }, "root-script-output");
        Thread stderr = new Thread(() -> {
            try (var input = process.getErrorStream()) { errors.append(org.apache.commons.io.IOUtils.toString(input, StandardCharsets.UTF_8)); }
            catch (IOException error) { readError.set(error); }
        }, "root-script-errors");
        stdout.start(); stderr.start();
        FutureTask<Integer> exit = new FutureTask<>(process::waitFor);
        Thread waiter = new Thread(exit, "root-script-wait");
        waiter.setDaemon(true);
        waiter.start();
        try {
            try (var input = process.getOutputStream()) { input.write(script.getBytes(StandardCharsets.UTF_8)); }
            int exitCode = exit.get(timeoutSeconds, TimeUnit.SECONDS);
            stdout.join(5000); stderr.join(5000);
            if (stdout.isAlive() || stderr.isAlive()) throw new IOException("Root script output did not close");
            if (readError.get() != null) throw readError.get();
            if (exitCode != 0) throw new IOException("Root script failed: " + errors);
        } catch (TimeoutException error) {
            throw new IOException("Root script timed out", error);
        } catch (ExecutionException error) {
            throw new IOException("Could not wait for root script", error.getCause());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException("Root operation interrupted", error);
        } finally { process.destroy(); exit.cancel(true); }
    }
}

package io.github.abdurazaaqmohammed.privilege.internal;

import io.github.abdurazaaqmohammed.privilege.api.ShellSession.ShellResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reusable interactive root shell session for fast metadata and listing queries.
 *
 * <p>Unlike one-shot {@code su -c} invocations, commands run over an open root
 * shell pipe, eliminating per-command {@code su} / {@code nsenter} process spawns
 * while remaining isolated from long-running transfer batches. Each command runs
 * inside a subshell framed by per-invocation stdout/stderr sentinels, and a
 * timeout watchdog destroys a wedged shell process so subsequent calls respawn
 * cleanly.
 */
final class PersistentRootShell {

    @FunctionalInterface
    interface ProcessLauncher {
        Process launch(boolean globalNs) throws IOException;
    }

    private static final int MAX_STDERR_CHARS = 32 * 1024;
    private static final ScheduledExecutorService WATCHDOG =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "root-shell-watchdog");
                t.setDaemon(true);
                return t;
            });

    private final ProcessLauncher launcher;
    private final Object lock = new Object();
    private final AtomicLong seq = new AtomicLong();

    private Process process;
    private OutputStream stdin;
    private BufferedReader stdout;
    private boolean sessionGlobalNs;

    private volatile String activeErrToken;
    private volatile StringBuilder activeErrBuf;
    private volatile CountDownLatch activeErrLatch;
    private volatile boolean errReset;

    PersistentRootShell(ProcessLauncher launcher) {
        this.launcher = launcher;
    }

    /**
     * Executes {@code command} in the persistent shell under {@code globalNs}.
     * On timeout, stream EOF, or I/O error the backing shell process is killed
     * so stale state cannot bleed into later commands.
     */
    ShellResult exec(String command, int timeoutSeconds, boolean globalNs) {
        if (command == null) return new ShellResult(-1, "", "Empty command");
        int timeout = Math.max(1, timeoutSeconds);
        synchronized (lock) {
            try {
                ensureSessionLocked(globalNs);
                final Process running = this.process;
                String id = seq.incrementAndGet() + "_" + UUID.randomUUID().toString().replace("-", "");
                String outToken = "__MP_OUT_" + id + "__:";
                String errToken = "__MP_ERR_" + id + "__";

                StringBuilder errBuf = new StringBuilder();
                CountDownLatch errLatch = new CountDownLatch(1);
                activeErrBuf = errBuf;
                activeErrToken = errToken;
                activeErrLatch = errLatch;
                errReset = false;

                AtomicBoolean done = new AtomicBoolean(false);
                ScheduledFuture<?> killFuture = WATCHDOG.schedule(() -> {
                    if (done.compareAndSet(false, true)) {
                        destroyProcess(running);
                    }
                }, timeout, TimeUnit.SECONDS);

                try {
                    String script = "(\n" + command + "\n)\n__mp_rc=$?\n"
                            + "printf '%s%d\\n' '" + outToken + "' $__mp_rc\n"
                            + "printf '%s\\n' '" + errToken + "' >&2\n";
                    stdin.write(script.getBytes(StandardCharsets.UTF_8));
                    stdin.flush();

                    StringBuilder out = new StringBuilder();
                    int exitCode = -1;
                    boolean gotSentinel = false;
                    String line;
                    while ((line = stdout.readLine()) != null) {
                        int idx = line.indexOf(outToken);
                        if (idx >= 0) {
                            if (idx > 0) {
                                if (out.length() > 0) out.append('\n');
                                out.append(line, 0, idx);
                            }
                            exitCode = parseExitCode(line.substring(idx + outToken.length()));
                            gotSentinel = true;
                            break;
                        }
                        if (out.length() > 0) out.append('\n');
                        out.append(line);
                    }

                    if (!gotSentinel) {
                        boolean timedOut = done.get();
                        closeSessionLocked();
                        String errText = safeErrText(errBuf);
                        if (timedOut) {
                            return new ShellResult(-1, out.toString(),
                                    errText.isEmpty() ? "Command timed out" : errText);
                        }
                        return new ShellResult(-1, out.toString(),
                                errText.isEmpty() ? "Root shell closed unexpectedly" : errText);
                    }

                    // Drain matching stderr sentinel so errors never leak into the next call.
                    try {
                        errLatch.await(1500, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    String errText = safeErrText(errBuf);
                    if (done.get()) {
                        closeSessionLocked();
                        return new ShellResult(-1, out.toString(),
                                errText.isEmpty() ? "Command timed out" : errText);
                    }
                    return new ShellResult(exitCode, out.toString(), errText);
                } finally {
                    done.set(true);
                    killFuture.cancel(false);
                    activeErrToken = null;
                    activeErrBuf = null;
                    activeErrLatch = null;
                }
            } catch (Exception e) {
                closeSessionLocked();
                return new ShellResult(-1, "", e.getMessage() == null ? "Shell execution failed" : e.getMessage());
            }
        }
    }

    /** Terminates any active persistent shell so the next query spawns a clean session. */
    void reset() {
        synchronized (lock) {
            closeSessionLocked();
        }
    }

    private void ensureSessionLocked(boolean globalNs) throws IOException {
        if (process != null && NativeProcess.isAlive(process) && sessionGlobalNs == globalNs) {
            return;
        }
        closeSessionLocked();
        Process started = launcher.launch(globalNs);
        if (started == null) throw new IOException("Cannot launch root shell");
        this.process = started;
        this.sessionGlobalNs = globalNs;
        this.stdin = started.getOutputStream();
        this.stdout = new BufferedReader(new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8));
        startStderrDrain(started);
        // Keep shell environment predictable without inheriting non-C locale formatting.
        this.stdin.write("export LC_ALL=C PS1='' PS2=''\n".getBytes(StandardCharsets.UTF_8));
        this.stdin.flush();
    }

    private void startStderrDrain(final Process proc) {
        Thread errThread = new Thread(() -> {
            try (BufferedReader err = new BufferedReader(
                    new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = err.readLine()) != null) {
                    String token = activeErrToken;
                    StringBuilder buf = activeErrBuf;
                    CountDownLatch latch = activeErrLatch;
                    if (token != null && line.contains(token)) {
                        if (latch != null) latch.countDown();
                        continue;
                    }
                    if (buf != null && !errReset) {
                        synchronized (buf) {
                            if (buf.length() < MAX_STDERR_CHARS) {
                                if (buf.length() > 0) buf.append('\n');
                                buf.append(line);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            } finally {
                CountDownLatch latch = activeErrLatch;
                if (latch != null) latch.countDown();
            }
        }, "root-shell-stderr");
        errThread.setDaemon(true);
        errThread.start();
    }

    private void closeSessionLocked() {
        errReset = true;
        CountDownLatch latch = activeErrLatch;
        if (latch != null) latch.countDown();
        if (stdin != null) {
            try {
                stdin.write("exit\n".getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            } catch (Exception ignored) {
            }
            try {
                stdin.close();
            } catch (Exception ignored) {
            }
            stdin = null;
        }
        if (stdout != null) {
            try {
                stdout.close();
            } catch (Exception ignored) {
            }
            stdout = null;
        }
        if (process != null) {
            destroyProcess(process);
            process = null;
        }
    }

    private static void destroyProcess(Process p) {
        NativeProcess.destroy(p);
    }

    private static int parseExitCode(String suffix) {
        try {
            return Integer.parseInt(suffix.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private static String safeErrText(StringBuilder errBuf) {
        synchronized (errBuf) {
            return errBuf.toString();
        }
    }
}

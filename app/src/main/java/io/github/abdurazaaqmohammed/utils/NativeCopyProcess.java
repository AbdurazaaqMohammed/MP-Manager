package io.github.abdurazaaqmohammed.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Reads mpio's merged stdout/stderr and requires an explicit successful result. */
final class NativeCopyProcess {
    interface Progress {
        void onProgress(long files, long bytes, String item);
    }

    private NativeCopyProcess() {
    }

    static boolean run(Process process, Progress progress, TransferControl control,
                       ShellProcess.Runner signals) throws IOException {
        AtomicLong pid = new AtomicLong(-1L);
        AtomicBoolean finished = new AtomicBoolean();
        Thread watcher = control == null ? null : new Thread(() -> {
            boolean paused = false;
            boolean cancellationSent = false;
            while (!finished.get() && process.isAlive()) {
                long nativePid = pid.get();
                if (nativePid > 0) {
                    if (control.isCancelled() && !cancellationSent) {
                        // Killing only the su client leaves the root copy running.
                        signal(signals, "kill -CONT " + nativePid + " 2>/dev/null; kill -TERM "
                                + nativePid + " 2>/dev/null");
                        cancellationSent = true;
                    } else if (!cancellationSent && control.isPaused() != paused) {
                        paused = control.isPaused();
                        signal(signals, "kill -" + (paused ? "STOP " : "CONT ")
                                + nativePid + " 2>/dev/null");
                    }
                } else if (control.isCancelled()) {
                    // No root pid yet: su may still be waiting for permission.
                    process.destroy();
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "mpio-copy-control");
        if (watcher != null) {
            watcher.setDaemon(true);
            watcher.start();
        }

        boolean copyDone = false;
        boolean copyError = false;
        boolean cancelled = false;
        StringBuilder diagnostics = new StringBuilder();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("MPIO_PID ")) {
                        try {
                            long reported = Long.parseLong(line.substring(9).trim());
                            if (reported > 1) pid.compareAndSet(-1L, reported);
                        } catch (NumberFormatException ignored) {
                        }
                    } else if (line.startsWith("PROGRESS ")) {
                        String[] parts = line.split("\\s+", 4);
                        if (parts.length >= 3) {
                            try {
                                long files = Long.parseLong(parts[1]);
                                long bytes = Long.parseLong(parts[2]);
                                if (files >= 0 && bytes >= 0 && progress != null) {
                                    progress.onProgress(files, bytes, parts.length == 4 ? parts[3] : "");
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    } else if (line.startsWith("COPY_DONE ")) {
                        long files = counter(line, "done_files=");
                        long bytes = counter(line, "done_bytes=");
                        if (files < 0 || bytes < 0) {
                            copyError = true;
                            append(diagnostics, "Invalid native copy result: " + line);
                        } else {
                            copyDone = true;
                            if (progress != null) progress.onProgress(files, bytes, "");
                        }
                    } else if (line.startsWith("COPY_ERROR")) {
                        copyError = true;
                        append(diagnostics, line);
                    } else if (line.startsWith("CANCELLED")) {
                        cancelled = true;
                    } else if (!line.isEmpty()) {
                        append(diagnostics, line);
                    }
                }
            }
            int exitCode = process.waitFor();
            if (cancelled || (control != null && control.isCancelled())) throw new TransferCancelledException();
            if (exitCode != 0 || !copyDone || copyError) {
                String detail = diagnostics.length() > 0 ? diagnostics.toString()
                        : "exit=" + exitCode + (copyDone ? "" : ", missing COPY_DONE");
                throw new NativeCopyException("Native copy failed: " + detail);
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NativeCopyException("Interrupted while waiting for the native copy", e);
        } catch (IOException e) {
            if (control != null && control.isCancelled()) throw new TransferCancelledException();
            if (e instanceof NativeCopyException) throw e;
            throw new NativeCopyException("Cannot read the native copy result", e);
        } finally {
            finished.set(true);
            if (watcher != null) watcher.interrupt();
            if (process.isAlive()) {
                long nativePid = pid.get();
                if (nativePid > 0) signal(signals, "kill -CONT " + nativePid
                        + " 2>/dev/null; kill -TERM " + nativePid + " 2>/dev/null");
                process.destroyForcibly();
            }
        }
    }

    private static long counter(String line, String key) {
        for (String part : line.split("\\s+")) {
            if (part.startsWith(key)) {
                try {
                    return Long.parseLong(part.substring(key.length()));
                } catch (NumberFormatException ignored) {
                    return -1L;
                }
            }
        }
        return -1L;
    }

    private static void append(StringBuilder diagnostics, String line) {
        if (diagnostics.length() >= 8192) return;
        if (diagnostics.length() > 0) diagnostics.append('\n');
        diagnostics.append(line, 0, Math.min(line.length(), 8192 - diagnostics.length()));
    }

    private static void signal(ShellProcess.Runner signals, String command) {
        if (signals == null) return;
        try {
            signals.run(command);
        } catch (Exception ignored) {
            // A signal may race the native process exiting; its result is checked above.
        }
    }
}

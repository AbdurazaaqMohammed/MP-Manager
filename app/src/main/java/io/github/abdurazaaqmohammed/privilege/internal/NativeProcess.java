package io.github.abdurazaaqmohammed.privilege.internal;

import java.util.concurrent.TimeUnit;

/** Process control that also works below API 26 (isAlive/destroyForcibly/timed wait are newer). */
final class NativeProcess {
    private NativeProcess() {}

    static boolean isAlive(Process process) {
        try { process.exitValue(); return false; }
        catch (IllegalThreadStateException running) { return true; }
    }

    private static final java.lang.reflect.Method DESTROY_FORCIBLY;

    static {
        java.lang.reflect.Method m = null;
        try {
            m = Process.class.getMethod("destroyForcibly");
        } catch (Throwable ignored) {
        }
        DESTROY_FORCIBLY = m;
    }

    static boolean await(Process process, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (isAlive(process)) {
            if (System.nanoTime() >= deadline) return false;
            Thread.sleep(25);
        }
        return true;
    }

    static void destroy(Process process) {
        if (process == null) return;
        if (DESTROY_FORCIBLY != null) {
            try {
                DESTROY_FORCIBLY.invoke(process);
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            process.destroy();
        } catch (Throwable ignored) {
        }
    }
}

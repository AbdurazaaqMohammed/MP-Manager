package io.github.abdurazaaqmohammed.privilege.internal;

import java.util.concurrent.TimeUnit;

/** Process control that also works below API 26 (isAlive/destroyForcibly/timed wait are newer). */
final class NativeProcess {
    private NativeProcess() {}

    static boolean isAlive(Process process) {
        try { process.exitValue(); return false; }
        catch (IllegalThreadStateException running) { return true; }
    }

    static boolean await(Process process, long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (isAlive(process)) {
            if (System.nanoTime() >= deadline) return false;
            Thread.sleep(25);
        }
        return true;
    }
}

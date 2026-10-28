package io.github.abdurazaaqmohammed.utils;

import net.lingala.zip4j.progress.ProgressMonitor;

import java.util.function.Consumer;

/**
 * Turns zip4j's {@link ProgressMonitor} into a byte/item stream for a
 * {@link FileUtils.TransferListener}.
 *
 * <p>zip4j only fills in {@code totalWork} when a task runs in its own thread,
 * but it always calls {@code updateWorkCompleted()} for every chunk it writes,
 * so the absolute counter is trustworthy while the total is not. This pump
 * therefore reports <em>deltas</em> and tolerates the reset each zip4j task
 * performs when it starts ({@code fullReset()} zeroes the counter, which shows
 * up here as a negative step).
 *
 * <p>Polling is the only option: zip4j exposes no callback, and replacing its
 * writers would risk changing the archive layout it produces.
 */
public final class ZipProgressPump {

    /** Fast enough that the 400 ms UI tick always has a fresh value. */
    private static final long POLL_MS = 100L;

    private final ProgressMonitor monitor;
    private final FileUtils.TransferListener byteSink;
    /**
     * Receives the path zip4j says it is working on: the source file while
     * compressing, the written file while extracting. Callers decide which
     * side of the "from/to" pair that is.
     */
    private final Consumer<String> currentPath;
    private final Thread thread;
    private volatile boolean running = true;

    public ZipProgressPump(ProgressMonitor monitor, FileUtils.TransferListener byteSink,
                           Consumer<String> currentPath) {
        this.monitor = monitor;
        this.byteSink = byteSink;
        this.currentPath = currentPath;
        this.thread = new Thread(this::loop, "zip-progress-pump");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        long accounted = 0L;
        String lastSeen = null;
        while (running) {
            try {
                Thread.sleep(POLL_MS);
            } catch (InterruptedException e) {
                return;
            }

            long work = monitor.getWorkCompleted();
            if (work < accounted) {
                // A new zip4j task started and reset its counters; the bytes it
                // already reported stay accounted for, only the base moves.
                accounted = 0L;
            }
            long delta = work - accounted;
            // A torn read of the 64-bit counter on 32-bit devices can look huge.
            if (delta > 0 && delta < 4L * 1024 * 1024 * 1024) {
                accounted = work;
                if (byteSink != null) byteSink.onBytes(delta);
            }

            String current = monitor.getFileName();
            if (current != null && !current.equals(lastSeen)) {
                lastSeen = current;
                if (currentPath != null) currentPath.accept(current);
            }
        }
    }
}

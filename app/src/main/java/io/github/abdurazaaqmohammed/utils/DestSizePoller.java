package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.util.ArrayDeque;
import java.util.function.LongConsumer;

/**
 * Samples how many bytes have landed in the destination of the item currently
 * being copied by an elevated backend.
 *
 * <p>A root/Shizuku copy runs {@code cp -r} as one opaque command, so a single
 * large file or folder would otherwise jump straight from 0% to 100%. The
 * shell's progress markers only fire when an item <em>finishes</em>; this poller
 * fills in the gap in between.
 *
 * <p>It only ever reads the destination through {@link File}, never through the
 * shell, so it costs no extra {@code su} spawn and cannot contend with the copy
 * for the root lock. When the destination is not readable by the app (a copy
 * <em>into</em> {@code /data}) every sample simply returns nothing and the
 * marker-based progress carries the dialog.
 */
public final class DestSizePoller implements Runnable {

    /** Slow enough to stay in the noise of the copy's own I/O. */
    private static final long INTERVAL_MS = 700L;
    /** A wider tree is not worth the metadata traffic; polling stops instead. */
    private static final int MAX_ENTRIES = 4000;

    private final LongConsumer sink;
    private final Thread thread;
    private volatile boolean running = true;
    private volatile File target;
    /** Bytes accounted for by items that already finished. */
    private volatile long base;

    /**
     * @param sink receives the absolute number of bytes done so far
     */
    public DestSizePoller(LongConsumer sink) {
        this.sink = sink;
        this.thread = new Thread(this, "dest-size-poller");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
    }

    /**
     * Points the poller at the destination of the item now in flight.
     *
     * @param destination the file or directory being written
     * @param baseBytes   bytes completed by earlier items
     */
    public void watch(File destination, long baseBytes) {
        this.base = Math.max(0L, baseBytes);
        this.target = destination;
    }

    public void idle() {
        this.target = null;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
            File t = target;
            if (t == null) continue;
            long[] size = new long[1];
            if (!treeSize(t, size)) {
                // Too large to sample cheaply: stop rather than fight the copy
                // for I/O. Markers still report each finished item.
                running = false;
                return;
            }
            if (sink != null) sink.accept(base + size[0]);
        }
    }

    /**
     * @return false when the tree exceeded {@link #MAX_ENTRIES}
     */
    private static boolean treeSize(File root, long[] out) {
        try {
            if (!root.exists()) return true;
            if (root.isFile()) {
                out[0] = root.length();
                return true;
            }
            ArrayDeque<File> stack = new ArrayDeque<>();
            stack.add(root);
            int seen = 0;
            while (!stack.isEmpty()) {
                File dir = stack.poll();
                if (dir == null) continue;
                File[] kids;
                try {
                    kids = dir.listFiles();
                } catch (Exception e) {
                    kids = null;
                }
                if (kids == null) continue;
                for (File kid : kids) {
                    if (++seen > MAX_ENTRIES) return false;
                    try {
                        if (kid.isDirectory()) stack.add(kid);
                        else out[0] += kid.length();
                    } catch (Exception ignored) {
                    }
                }
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }
}

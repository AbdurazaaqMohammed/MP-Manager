package io.github.abdurazaaqmohammed.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared pause / resume / cancel token for one long running file operation.
 *
 * <p>The dialog owns one of these and the worker thread reads it, so a single
 * object carries the user's intent from the UI to the code doing the work.
 *
 * <h3>How pausing works</h3>
 * A worker calls {@link #checkpoint()} where it is safe to stop: between two
 * chunks of a copy, between two archive entries, between two shell
 * invocations. While the operation is paused the checkpoint blocks the worker,
 * so <em>no work continues in the background</em> — the thread is parked, not
 * spinning. Resuming wakes it and the transfer continues from exactly where
 * it stopped, which is what makes a paused copy resumable rather than a
 * restart.
 *
 * <h3>How cancelling works</h3>
 * Cancelling sets a terminal flag, wakes every parked worker and notifies the
 * registered {@link Listener}s. Work that runs outside the Java worker (an
 * elevated {@code su} shell, a zip4j archive) registers a listener that stops
 * it, because a Java flag alone cannot stop another process.
 *
 * <p>Everything is safe to call from any thread.
 */
public final class TransferControl {

    /**
     * Observer of the control's state, used to stop work that does not run on
     * the Java worker thread.
     */
    public interface Listener {
        /** The operation was paused. */
        default void onPause() {
        }

        /** The operation was resumed. */
        default void onResume() {
        }

        /** The operation was cancelled; stop external work now. */
        default void onCancel() {
        }
    }

    private final Object lock = new Object();

    private boolean paused;
    private boolean cancelled;

    /** Worker threads parked in {@link #checkpoint()} wait on this. */
    private int parked;

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /**
     * True while the operation is paused. A cancelled operation is not paused:
     * it is over.
     */
    public boolean isPaused() {
        synchronized (lock) {
            return paused && !cancelled;
        }
    }

    /** True once the user cancelled. Terminal: it never goes back to false. */
    public boolean isCancelled() {
        synchronized (lock) {
            return cancelled;
        }
    }

    /**
     * Pauses the operation. Workers that reach their next
     * {@link #checkpoint()} park until {@link #resume()} is called.
     */
    public void pause() {
        List<Listener> toNotify;
        synchronized (lock) {
            if (cancelled || paused) return;
            paused = true;
            toNotify = new ArrayList<>(listeners);
        }
        for (Listener l : toNotify) {
            try {
                l.onPause();
            } catch (Throwable ignored) {
                // A misbehaving listener must not stop the pause itself.
            }
        }
    }

    /** Resumes a paused operation and wakes every parked worker. */
    public void resume() {
        List<Listener> toNotify;
        synchronized (lock) {
            if (cancelled || !paused) return;
            paused = false;
            lock.notifyAll();
            toNotify = new ArrayList<>(listeners);
        }
        for (Listener l : toNotify) {
            try {
                l.onResume();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Cancels the operation. This is terminal: the control stays cancelled and
     * every later {@link #checkpoint()} throws.
     */
    public void cancel() {
        List<Listener> toNotify;
        synchronized (lock) {
            if (cancelled) return;
            cancelled = true;
            paused = false;
            lock.notifyAll();
            toNotify = new ArrayList<>(listeners);
        }
        // Notify outside the lock: a listener may call back into the control.
        for (Listener l : toNotify) {
            try {
                l.onCancel();
            } catch (Throwable ignored) {
                // Aborting is best effort; the cancellation flag still stops us.
            }
        }
    }

    /** Registers a listener that is told about every state change. */
    public void addListener(Listener listener) {
        if (listener == null) return;
        listeners.add(listener);
        // The state may already have changed between registration and now.
        if (isCancelled()) notifyCancel(listener);
        else if (isPaused()) notifyPause(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Safe point where the worker may be stopped.
     *
     * <p>Blocks while the operation is paused and returns when it is resumed.
     * Throws {@link TransferCancelledException} when the operation was
     * cancelled, which unwinds the worker so no further work happens.
     */
    public void checkpoint() {
        synchronized (lock) {
            while (paused && !cancelled) {
                parked++;
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    parked--;
                    throw new TransferCancelledException();
                }
                parked--;
            }
            if (cancelled) throw new TransferCancelledException();
        }
    }

    /** Number of workers currently parked in {@link #checkpoint()}. */
    public int parkedWorkers() {
        synchronized (lock) {
            return parked;
        }
    }

    private static void notifyPause(Listener l) {
        try {
            l.onPause();
        } catch (Throwable ignored) {
        }
    }

    private static void notifyCancel(Listener l) {
        try {
            l.onCancel();
        } catch (Throwable ignored) {
        }
    }
}

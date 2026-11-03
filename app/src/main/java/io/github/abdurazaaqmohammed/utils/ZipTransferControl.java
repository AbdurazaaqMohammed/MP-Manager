package io.github.abdurazaaqmohammed.utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.progress.ProgressMonitor;

import java.lang.reflect.Field;

/**
 * Bridges the app's pause / cancel token to a zip4j archive operation.
 *
 * <h3>Why this exists</h3>
 * zip4j owns the byte loop, so the app cannot simply stop it from the
 * outside. It does, however, expose exactly the two hooks that are needed:
 * <ul>
 *   <li>{@link ProgressMonitor#updateWorkCompleted(long)} is public and
 *       overridable, and zip4j calls it for every chunk it writes;</li>
 *   <li>{@link ProgressMonitor#setCancelAllTasks(boolean)} and
 *       {@link ProgressMonitor#setPause(boolean)} are public and honoured by
 *       the synchronous task path.</li>
 * </ul>
 *
 * <h3>How it is used</h3>
 * When the monitor can be replaced, it is replaced with a subclass whose
 * {@code updateWorkCompleted} consults the {@link TransferControl} directly.
 * That makes the pause and the cancel <em>authoritative</em>: the worker
 * thread parks inside the archive write loop while paused and unwinds with a
 * {@link TransferCancelledException} the moment the operation is cancelled.
 * Nothing keeps running in the background.
 *
 * <p>When the monitor cannot be replaced, the fallback drives zip4j's own
 * flags. Those work, but they are coarser: the pause is a 150 ms poll rather
 * than a true park, and the cancel is reported as a
 * {@code net.lingala.zip4j.exception.ZipException} instead of the app's own
 * exception. The caller detects a cancellation by asking the control, not by
 * matching exception types.
 */
public final class ZipTransferControl {

    private final TransferControl control;
    /** True when the monitor was replaced and therefore enforces the control itself. */
    private final boolean authoritative;
    private final ProgressMonitor monitor;
    private final TransferControl.Listener listener;

    private ZipTransferControl(TransferControl control, boolean authoritative, ProgressMonitor monitor) {
        this.control = control;
        this.authoritative = authoritative;
        this.monitor = monitor;
        this.listener = new TransferControl.Listener() {
            @Override
            public void onPause() {
                // With an authoritative monitor the pause is handled by the
                // checkpoint itself, so only the fallback needs the flag.
                if (!authoritative) monitor.setPause(true);
            }

            @Override
            public void onResume() {
                monitor.setPause(false);
            }

            @Override
            public void onCancel() {
                monitor.setCancelAllTasks(true);
                // A paused zip4j write loop spins until the pause flag clears,
                // so the flag has to go before the cancel can be observed.
                monitor.setPause(false);
            }
        };
    }

    /**
     * Attaches {@code control} to {@code zf}'s archive work.
     *
     * @return the bridge, or null when there is nothing to control
     */
    public static ZipTransferControl attach(ZipFile zf, TransferControl control) {
        if (zf == null || control == null) return null;
        boolean injected = injectMonitor(zf, control);
        ProgressMonitor monitor = zf.getProgressMonitor();
        ZipTransferControl bridge = new ZipTransferControl(control, injected, monitor);
        control.addListener(bridge.listener);
        return bridge;
    }

    /**
     * Replaces zip4j's monitor with one that consults the control.
     *
     * @return true when the monitor was replaced
     */
    private static boolean injectMonitor(ZipFile zf, TransferControl control) {
        try {
            Field field = ZipFile.class.getDeclaredField("progressMonitor");
            field.setAccessible(true);
            field.set(zf, new ControlledProgressMonitor(control));
            return true;
        } catch (Throwable ignored) {
            // Not available in this zip4j build: the native flags still work.
            return false;
        }
    }

    /** Detaches the bridge so the control stops driving the archive. */
    public void detach() {
        control.removeListener(listener);
        try {
            monitor.setPause(false);
        } catch (Throwable ignored) {
        }
    }

    /**
     * The monitor zip4j writes through, exposed so the progress pump can
     * read the same counters.
     */
    public ProgressMonitor monitor() {
        return monitor;
    }

    /**
     * A zip4j monitor whose progress callback is also the pause / cancel
     * checkpoint. zip4j calls it for every chunk, so the granularity is one
     * buffer — the operation stops almost immediately.
     */
    private static final class ControlledProgressMonitor extends ProgressMonitor {
        private final TransferControl control;

        ControlledProgressMonitor(TransferControl control) {
            this.control = control;
        }

        @Override
        public void updateWorkCompleted(long bytes) {
            // Parks the worker while paused and unwinds it when cancelled.
            control.checkpoint();
            super.updateWorkCompleted(bytes);
        }
    }
}

package io.github.abdurazaaqmohammed.utils;

/**
 * Thrown when the user cancels an operation that is running under a
 * {@link TransferControl}.
 *
 * <p>It is unchecked on purpose: the cancel flag has to travel out of deeply
 * nested I/O helpers (copy loops, archive readers, shell wrappers) whose
 * signatures would otherwise all have to change. Every place that reports a
 * failure to the user checks {@link #isCancellation(Throwable)} first, so a
 * cancelled operation is never presented as an error.
 */
public class TransferCancelledException extends RuntimeException {

    public TransferCancelledException() {
        super("Operation cancelled");
    }

    public TransferCancelledException(String message) {
        super(message);
    }

    /**
     * True when {@code t} (or anything it wraps) is a cancellation.
     *
     * <p>Callers use this to tell "the user pressed cancel" apart from a real
     * failure, because the two look the same once the exception has been
     * wrapped by an I/O layer.
     */
    public static boolean isCancellation(Throwable t) {
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause instanceof TransferCancelledException) return true;
            if (cause == cause.getCause()) break;
        }
        return false;
    }
}

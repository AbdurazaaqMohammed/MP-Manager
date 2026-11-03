package io.github.abdurazaaqmohammed.utils;

/**
 * Marker protocol that lets one long elevated shell process report numeric
 * progress while it runs.
 *
 * <p>Root and Shizuku both execute a batch as a single {@code su}/{@code sh}
 * invocation, and the app can only read that process' stdout. So the generated
 * script prints a marker line after every completed unit of work:
 *
 * <pre>
 *   cp -pf -- /a /b &amp;&amp; echo @@MPPROG 12 5242880
 * </pre>
 *
 * <p>The drain thread strips these lines from the command output (so callers
 * still see exactly the bytes the command produced) and forwards the counters.
 * Without this the dialog could only ever show 0% until the whole batch
 * finished, because a single shell call is opaque from the Java side.
 */
public final class ShellProgress {

    /** {@code @@MPPROG <filesDone> <bytesDone>} — cumulative counters. */
    private static final String PROGRESS = "@@MPPROG ";
    /** Emitted when a chunk of work failed but the batch keeps going. */
    private static final String FAILURE = "@@MPFAIL";
    /**
     * {@code @@MPPID <pid>} — the process id of the shell that runs the batch.
     *
     * <p>It lets the app stop or suspend the work that runs outside the JVM:
     * a Java flag cannot reach a {@code cp} the shell already started, but a
     * signal can.
     */
    private static final String PID = "@@MPPID ";

    public interface Listener {
        /**
         * @param filesDone cumulative items completed so far
         * @param bytesDone cumulative bytes processed so far
         */
        void onProgress(int filesDone, long bytesDone);

        /** One unit of the batch failed; the operation will throw at the end. */
        default void onFailure() {
        }

        /**
         * The batch reported the process id of the shell running it.
         *
         * <p>Called once, at the start of the batch.
         */
        default void onPid(int pid) {
        }
    }

    private ShellProgress() {
    }

    /**
     * The clause that reports the shell's own process id.
     *
     * <p>It has to come first in the batch, so the app knows which process to
     * signal before any real work starts.
     */
    public static String reportPid() {
        return "echo " + PID + "$$";
    }

    /** Builds the echo clause that reports {@code files}/{@code bytes} as done. */
    public static String report(int files, long bytes) {
        return " && echo " + PROGRESS + files + ' ' + bytes;
    }

    /** Same as {@link #report} but keeps the batch running after a failure. */
    public static String reportAlways(int files, long bytes) {
        return " ; echo " + PROGRESS + files + ' ' + bytes;
    }

    /** Builds the failure clause for commands chained with {@code ;}. */
    public static String reportFailure() {
        return " || echo " + FAILURE;
    }

    /**
     * Handles one stdout line.
     *
     * @return true when the line was a marker and must not be treated as
     *         ordinary command output
     */
    public static boolean consume(String line, Listener listener) {
        if (line == null) return false;
        if (line.startsWith(FAILURE)) {
            if (listener != null) listener.onFailure();
            return true;
        }
        if (line.startsWith(PID)) {
            String rest = line.substring(PID.length()).trim();
            try {
                if (!rest.isEmpty() && listener != null) listener.onPid(Integer.parseInt(rest));
            } catch (NumberFormatException ignored) {
            }
            return true;
        }
        if (!line.startsWith(PROGRESS)) return false;
        String rest = line.substring(PROGRESS.length()).trim();
        if (rest.isEmpty()) return true;
        String[] parts = rest.split("\\s+");
        int files = 0;
        long bytes = 0L;
        try {
            if (parts.length > 0) files = Integer.parseInt(parts[0]);
            if (parts.length > 1) bytes = Long.parseLong(parts[1]);
        } catch (NumberFormatException ignored) {
            return true;
        }
        if (listener != null) listener.onProgress(files, bytes);
        return true;
    }
}

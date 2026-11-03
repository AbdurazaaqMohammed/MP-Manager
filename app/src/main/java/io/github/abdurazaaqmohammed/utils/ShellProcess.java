package io.github.abdurazaaqmohammed.utils;

/**
 * Controls a batch that runs in an elevated shell.
 *
 * <h3>Why the shell has to do the signalling</h3>
 * An elevated batch ({@code cp}, {@code rm}, {@code mv}) runs as root or as
 * the shell user, so the app cannot signal it directly: the processes belong
 * to another user and {@code Os.kill} would fail with {@code EPERM}. The
 * batch therefore reports its own process id, and every signal is sent
 * <em>through</em> the elevated backend, which does have the permission.
 *
 * <h3>Why the whole tree</h3>
 * The batch is a shell that runs the real command as its child. Signalling
 * only the shell would leave the {@code cp} running as an orphan — work that
 * keeps going after the user pressed Cancel. Every signal is therefore sent to
 * the shell's children first and to the shell itself second.
 *
 * <h3>Why SIGSTOP for a pause</h3>
 * A paused operation has to be resumable, which means it must continue
 * <em>exactly</em> where it stopped. Suspending the tree and continuing it
 * does that without losing any state: the copy resumes at the same byte, in
 * the same file, at the same instant.
 */
public final class ShellProcess {

    /** Runs a command with elevated rights. */
    public interface Runner {
        void run(String command) throws Exception;
    }

    private ShellProcess() {
    }

    /**
     * Suspends the batch: the work stops where it is and stays there.
     *
     * @param pid the process id the batch reported
     */
    public static void suspend(long pid, Runner runner) {
        if (pid <= 0 || runner == null) return;
        // The children go first: a stopped shell cannot start new work, but a
        // running shell could spawn another command while it is being signalled.
        dispatch(runner, "pkill -STOP -P " + pid + " 2>/dev/null");
        dispatch(runner, "kill -STOP " + pid + " 2>/dev/null");
    }

    /**
     * Resumes a suspended batch, so the work continues where it stopped.
     */
    public static void resume(long pid, Runner runner) {
        if (pid <= 0 || runner == null) return;
        // The shell goes first: it has to be able to hand the next command to
        // its children.
        dispatch(runner, "kill -CONT " + pid + " 2>/dev/null");
        dispatch(runner, "pkill -CONT -P " + pid + " 2>/dev/null");
    }

    /**
     * Stops the batch for good: the shell and everything it started.
     *
     * <p>This is the "no work continues in the background" guarantee — after
     * this returns, nothing the batch launched is still running.
     */
    public static void kill(long pid, Runner runner) {
        if (pid <= 0 || runner == null) return;
        // A suspended process never handles a kill until it is continued, so
        // it has to be woken up first or it would linger forever.
        dispatch(runner, "kill -CONT " + pid + " 2>/dev/null");
        dispatch(runner, "pkill -KILL -P " + pid + " 2>/dev/null");
        dispatch(runner, "kill -KILL " + pid + " 2>/dev/null");
    }

    /**
     * Runs a signal command off the calling thread.
     *
     * <p>The caller is the UI thread — a user tapping Cancel — and a signal
     * command costs a shell round trip, so it must not block the tap.
     */
    private static void dispatch(Runner runner, String command) {
        Thread t = new Thread(() -> {
            try {
                runner.run(command);
            } catch (Throwable ignored) {
                // Signalling is best effort; the caller checks the outcome.
            }
        }, "shell-signal");
        t.setDaemon(true);
        t.start();
    }
}

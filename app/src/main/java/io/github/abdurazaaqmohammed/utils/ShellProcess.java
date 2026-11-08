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
     * Shell helpers that walk a process tree with {@code pgrep -P}.
     *
     * <h3>Why not {@code pkill -P}</h3>
     * Toybox {@code pkill -P} is broken on several Android releases — it exits
     * with "no processes matched" while {@code pgrep -P} on the same parent
     * finds the child (verified on an Android 12 emulator, where a cancelled
     * {@code mv} kept streaming because of exactly this). Enumerating with
     * {@code pgrep -P} and signalling each pid works everywhere.
     *
     * <p>The walk is recursive ({@code killTree}/{@code stopTree} call
     * themselves for every child first, then signal the pid itself), because
     * the batch is a shell whose real work ({@code cp}, {@code mv}, {@code rm})
     * runs one level down, and those children may spawn their own. Signalling
     * the pid last means it cannot spawn anything new once the walk reaches it.
     *
     * <p>Everything runs inside one shell invocation per signal, so the steps
     * cannot race each other the way separate {@code su} spawns did.
     */
    private static final String TREEFN =
            "killTree(){ for c in $(pgrep -P $1 2>/dev/null); do killTree $c; done; "
                    + "kill -KILL $1 2>/dev/null; }; "
            + "stopTree(){ kill -STOP $1 2>/dev/null; "
                    + "for c in $(pgrep -P $1 2>/dev/null); do stopTree $c; done; }; "
            + "contTree(){ for c in $(pgrep -P $1 2>/dev/null); do contTree $c; done; "
                    + "kill -CONT $1 2>/dev/null; }; ";

    /**
     * Suspends the batch: the work stops where it is and stays there.
     *
     * @param pid the process id the batch reported
     */
    public static void suspend(long pid, Runner runner) {
        if (pid <= 0 || runner == null) return;
        // The shell goes first: a stopped shell cannot spawn more work while
        // the rest of the tree is being walked, and the children follow.
        dispatch(runner, TREEFN + "stopTree " + pid);
    }

    /**
     * Resumes a suspended batch, so the work continues where it stopped.
     */
    public static void resume(long pid, Runner runner) {
        if (pid <= 0 || runner == null) return;
        // The shell goes first: it has to be able to hand the next command to
        // its children.
        dispatch(runner, TREEFN + "contTree " + pid);
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
        dispatch(runner, "kill -CONT " + pid + " 2>/dev/null; " + TREEFN + "killTree " + pid);
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

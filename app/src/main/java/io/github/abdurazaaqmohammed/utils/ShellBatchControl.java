package io.github.abdurazaaqmohammed.utils;

/**
 * Drives an elevated shell batch from a {@link TransferControl}.
 *
 * <p>An elevated batch runs outside the JVM, so a Java flag cannot stop it.
 * The batch reports its own process id (see {@link ShellProgress#reportPid()})
 * and this class sends the signals through the elevated backend, which is the
 * only party with the permission to do so.
 *
 * <p>The batch is expected to start with {@link ShellProgress#reportPid()},
 * so the id is known before any real work begins.
 */
public final class ShellBatchControl implements TransferControl.Listener, ShellProgress.Listener {

    private final TransferControl control;
    private final ShellProcess.Runner runner;
    /** The pid the batch reported, or -1 while it has not reported yet. */
    private volatile long pid = -1L;
    private volatile boolean cancelled;

    public ShellBatchControl(TransferControl control, ShellProcess.Runner runner) {
        this.control = control;
        this.runner = runner;
        control.addListener(this);
    }

    /**
     * Forwards the batch's progress markers to {@code delegate} and picks up
     * the process id the batch reports.
     */
    public ShellProgress.Listener wrap(ShellProgress.Listener delegate) {
        return new ShellProgress.Listener() {
            @Override
            public void onProgress(int filesDone, long bytesDone) {
                if (delegate != null) delegate.onProgress(filesDone, bytesDone);
            }

            @Override
            public void onFailure() {
                if (delegate != null) delegate.onFailure();
            }

            @Override
            public void onPid(int reported) {
                pid = reported;
                if (delegate != null) delegate.onPid(reported);
            }
        };
    }

    @Override
    public void onPid(int reported) {
        pid = reported;
    }

    @Override
    public void onProgress(int filesDone, long bytesDone) {
    }

    @Override
    public void onFailure() {
    }

    @Override
    public void onPause() {
        ShellProcess.suspend(pid, runner);
    }

    @Override
    public void onResume() {
        ShellProcess.resume(pid, runner);
    }

    @Override
    public void onCancel() {
        cancelled = true;
        ShellProcess.kill(pid, runner);
    }

    /** True when the operation was cancelled. */
    public boolean wasCancelled() {
        return cancelled || control.isCancelled();
    }

    /** Stops driving the batch and detaches from the control. */
    public void detach() {
        control.removeListener(this);
    }
}

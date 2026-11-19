/*
 * Privilege layer — public API.
 *
 * UI/features may import ONLY this package (PrivilegeManager and the session
 * interfaces), never privilege.spi / privilege.internal
 * or any backend class. See docs/ARCHITECTURE.md for the dependency rules.
 */
package io.github.abdurazaaqmohammed.privilege.api;

import android.content.Context;

/**
 * Shell execution channel (exec / stream), regardless of which backend
 * (root, Shizuku, …) backs it. Never null: when no backend is authorized
 * the registry hands out an explicit-error implementation.
 */
public interface ShellSession {

    Result exec(String command, long timeoutSeconds);

    Result exec(String command);

    /** True when the backing privilege channel is granted and usable. */
    boolean isGranted();

    boolean isAvailable();

    boolean isInstalled(Context context);

    void warmUp(Context context);

    void requestPermission();

    void openShizukuApp(Context context);

    void onBinderReceived(Runnable callback);

    /** Immutable outcome of one command. */
    final class Result {
        public final boolean success;
        public final String stdout;
        public final String stderr;
        public final int exitCode;

        public Result(boolean success, String stdout, String stderr, int exitCode) {
            this.success = success;
            this.stdout = stdout;
            this.stderr = stderr;
            this.exitCode = exitCode;
        }
    }

    /** Immutable outcome of one command, legacy record form (accessors exitCode()/output()/error()). Distinct from {@link Result} until the two are merged. */
    public record ShellResult(int exitCode, String output, String error) {
            public ShellResult(int exitCode, String output, String error) {
                this.exitCode = exitCode;
                this.output = output != null ? output : "";
                this.error = error != null ? error : "";
            }

            public boolean isSuccess() {
                return exitCode == 0;
            }
        }
}

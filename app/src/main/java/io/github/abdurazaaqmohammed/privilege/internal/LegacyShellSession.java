/*
 * Privilege layer — internal adapters over the legacy implementation.
 * Preserve the exact legacy semantics — no behavior change.
 */
package io.github.abdurazaaqmohammed.privilege.internal;

import android.content.Context;

import io.github.abdurazaaqmohammed.privilege.api.ShellSession;
import io.github.abdurazaaqmohammed.privilege.internal.ShizukuShell;

/** M2 shell session: delegates to the legacy Shizuku shell wrapper. */
public class LegacyShellSession implements ShellSession {

    public static final LegacyShellSession INSTANCE = new LegacyShellSession();

    private LegacyShellSession() { }

    @Override
    public Result exec(String command, long timeoutSeconds) {
        ShizukuShell.Result legacy = ShizukuShell.exec(command, timeoutSeconds);
        return new Result(legacy.success, legacy.stdout, legacy.stderr, legacy.exitCode);
    }

    @Override
    public Result exec(String command) {
        ShizukuShell.Result legacy = ShizukuShell.exec(command);
        return new Result(legacy.success, legacy.stdout, legacy.stderr, legacy.exitCode);
    }

    @Override
    public boolean isGranted() {
        return ShizukuShell.isGranted();
    }

    @Override
    public boolean isAvailable() {
        return ShizukuShell.isAvailable();
    }

    @Override
    public boolean isInstalled(Context context) {
        return ShizukuShell.isInstalled(context);
    }

    @Override
    public void warmUp(Context context) {
        ShizukuShell.warmUp(context);
    }

    @Override
    public void requestPermission() {
        ShizukuShell.requestPermission();
    }

    @Override
    public void openShizukuApp(Context context) {
        ShizukuShell.openShizukuApp(context);
    }

    @Override
    public void onBinderReceived(Runnable callback) {
        ShizukuShell.onBinderReceived(callback);
    }
}

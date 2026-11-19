/*
 * Privilege layer — Shizuku backend.
 *
 * Adapts the legacy Shizuku engine (ShizukuShell / ShizukuManager) to the
 * backend SPI. Behavior is identical to the legacy SHIZUKU path of
 * AccessManager.
 */
package io.github.abdurazaaqmohammed.privilege.backend;

import android.content.Context;

import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession;
import io.github.abdurazaaqmohammed.privilege.api.ShellSession;
import io.github.abdurazaaqmohammed.privilege.internal.LegacyShellSession;
import io.github.abdurazaaqmohammed.privilege.internal.ShizukuSessions;
import io.github.abdurazaaqmohammed.privilege.spi.Capability;
import io.github.abdurazaaqmohammed.privilege.spi.FileOpsCap;
import io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend;
import io.github.abdurazaaqmohammed.privilege.spi.ShellCap;

public final class ShizukuBackend implements PrivilegeBackend, ShellCap, FileOpsCap {

    private static final ShizukuBackend INSTANCE = new ShizukuBackend();

    public static ShizukuBackend get() {
        return INSTANCE;
    }

    private ShizukuBackend() {
    }

    @Override
    public String id() {
        return "shizuku";
    }

    @Override
    public String label() {
        return "Shizuku";
    }

    @Override
    public int priorityFor(Class<? extends Capability> capability) {
        // SHIZUKU and DHIZUKU are parallel privilege domains, not a ladder:
        // SHIZUKU owns the shell/file-ops domain (adb-shell semantics),
        // DHIZUKU owns the device-owner domain, ROOT is the last resort.
        return 30; // first choice for every capability it implements
    }

    @Override
    public boolean isAvailable(Context context) {
        try {
            return LegacyShellSession.INSTANCE.isAvailable()
                    && LegacyShellSession.INSTANCE.isInstalled(context);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isAuthorized(Context context) {
        try {
            return LegacyShellSession.INSTANCE.isGranted();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ShellSession openShell() {
        return LegacyShellSession.INSTANCE;
    }

    @Override
    public FileOpsSession openFileOps(Context context) {
        return ShizukuSessions.fileOps(context);
    }
}

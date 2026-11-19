/*
 * Privilege layer — root backend.
 *
 * Adapts the legacy RootManager engine to the backend SPI. Behavior is
 * identical to the legacy ROOT path of AccessManager; when M5 retires the
 * legacy classes these delegations become the implementation.
 */
package io.github.abdurazaaqmohammed.privilege.backend;

import android.content.Context;

import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession;
import io.github.abdurazaaqmohammed.privilege.api.ShellSession;
import io.github.abdurazaaqmohammed.privilege.internal.RootSessions;
import io.github.abdurazaaqmohammed.privilege.internal.RootManager;
import io.github.abdurazaaqmohammed.privilege.spi.Capability;
import io.github.abdurazaaqmohammed.privilege.spi.FileOpsCap;
import io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend;
import io.github.abdurazaaqmohammed.privilege.spi.ShellCap;

public final class RootBackend implements PrivilegeBackend, ShellCap, FileOpsCap {

    private static final RootBackend INSTANCE = new RootBackend();

    public static RootBackend get() {
        return INSTANCE;
    }

    private RootBackend() {
    }

    @Override
    public String id() {
        return "root";
    }

    @Override
    public String label() {
        return "Root (su)";
    }

    @Override
    public int priorityFor(Class<? extends Capability> capability) {
        return 10; // last resort: only when SHIZUKU/DHIZUKU cannot serve
    }

    @Override
    public boolean isAvailable(Context context) {
        try {
            return RootManager.getInstance(context).autoEnableRootIfAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isAuthorized(Context context) {
        try {
            RootManager rm = RootManager.getInstance(context);
            return rm.isRootMode() && rm.isRootAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ShellSession openShell() {
        return RootSessions.shell();
    }

    @Override
    public FileOpsSession openFileOps(Context context) {
        return RootSessions.fileOps(context);
    }
}

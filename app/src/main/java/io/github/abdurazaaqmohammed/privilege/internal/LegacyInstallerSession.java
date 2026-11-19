/*
 * Privilege layer — internal adapters over the legacy implementation.
 * Preserve the exact legacy semantics — no behavior change.
 */
package io.github.abdurazaaqmohammed.privilege.internal;

import android.content.Context;
import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.privilege.api.InstallerSession;
import io.github.abdurazaaqmohammed.privilege.internal.PrivilegedInstaller;

/** M2 installer session: delegates to PrivilegedInstaller. */
public final class LegacyInstallerSession implements InstallerSession {

    public static final LegacyInstallerSession INSTANCE = new LegacyInstallerSession();

    private LegacyInstallerSession() { }

    @Override
    public boolean isRootSilentReady(Context context) {
        return PrivilegedInstaller.isRootSilentReady(context);
    }

    @Override
    public boolean isShizukuSilentReady(Context context) {
        return PrivilegedInstaller.isShizukuSilentReady(context);
    }

    @Override
    public boolean isPrivilegedSilentReady(Context context) {
        return PrivilegedInstaller.isPrivilegedSilentReady(context);
    }

    @Override
    public void installAsync(Context context, List<File> apkFiles,
                             PrivilegedInstaller.Callback cb) {
        PrivilegedInstaller.installAsync(context, apkFiles, cb);
    }

    @Override
    public String installBlocking(Context context, List<File> apkFiles) throws Exception {
        return PrivilegedInstaller.installBlocking(context, apkFiles);
    }

    @Override
    public boolean hasSignatureMismatch(Context context, List<File> apkFiles) {
        return PrivilegedInstaller.hasSignatureMismatch(context, apkFiles);
    }

    @Override
    public String lastTrace() {
        return PrivilegedInstaller.lastTrace();
    }
}

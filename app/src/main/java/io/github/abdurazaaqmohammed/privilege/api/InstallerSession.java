/*
 * Privilege layer — public API.
 */
package io.github.abdurazaaqmohammed.privilege.api;

import android.content.Context;

import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.privilege.internal.PrivilegedInstaller;

/**
 * Silent install / uninstall channel. Never null; readiness probes return
 * false instead of throwing when no backend is authorized.
 *
 * The callback type is still the legacy {@code PrivilegedInstaller.Callback}
 * until the backend SPI defines its own (stage M3/M5 cleanup).
 */
public interface InstallerSession {

    boolean isRootSilentReady(Context context);

    boolean isShizukuSilentReady(Context context);

    boolean isPrivilegedSilentReady(Context context);

    void installAsync(Context context, List<File> apkFiles,
                      PrivilegedInstaller.Callback cb);

    String installBlocking(Context context, List<File> apkFiles) throws Exception;

    boolean hasSignatureMismatch(Context context, List<File> apkFiles);

    String lastTrace();
}

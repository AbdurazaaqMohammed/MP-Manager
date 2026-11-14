/*
 * Privilege layer — service provider interface.
 */
package io.github.abdurazaaqmohammed.privilege.spi;

import io.github.abdurazaaqmohammed.privilege.api.InstallerSession;
import android.content.Context;

/** Backend can silently install packages. */
public interface InstallerCap extends Capability {
    InstallerSession openInstaller(Context context);
}

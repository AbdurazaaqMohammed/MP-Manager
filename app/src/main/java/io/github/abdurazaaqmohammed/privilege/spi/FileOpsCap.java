/*
 * Privilege layer — service provider interface.
 */
package io.github.abdurazaaqmohammed.privilege.spi;

import io.github.abdurazaaqmohammed.privilege.api.FileOpsSession;
import android.content.Context;

/** Backend can perform elevated file operations. */
public interface FileOpsCap extends Capability {
    FileOpsSession openFileOps(Context context);
}

/*
 * Privilege layer — service provider interface.
 */
package io.github.abdurazaaqmohammed.privilege.spi;

import io.github.abdurazaaqmohammed.privilege.api.ShellSession;

/** Backend can execute shell commands. */
public interface ShellCap extends Capability {
    ShellSession openShell();
}

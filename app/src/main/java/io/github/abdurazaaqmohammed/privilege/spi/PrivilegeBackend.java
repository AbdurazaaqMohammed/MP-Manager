/*
 * Privilege layer — service provider interface.
 *
 * A privilege backend is ONE class: it declares an id / label / priority,
 * reports availability and authorization, and implements the capability
 * interfaces (à la carte) for what it actually supports. The registry
 * skips a backend that lacks the requested capability and falls through
 * to the next one. Adding a backend = 1 new file + 1 line in
 * privilege/internal/Registry.
 */
package io.github.abdurazaaqmohammed.privilege.spi;

import android.content.Context;

public interface PrivilegeBackend {

    /** Stable id, e.g. {@code "dhizuku"}, {@code "shizuku"}, {@code "root"}. */
    String id();

    /** Human-readable label for settings UI. */
    String label();

    /** Higher wins when several backends are authorized (DHIZUKU > SHIZUKU > ROOT). */
    int priority();

    /** Installed / present on this device (no user action implied). */
    boolean isAvailable(Context context);

    /** User has granted this backend (or it needs no grant). */
    boolean isAuthorized(Context context);
}

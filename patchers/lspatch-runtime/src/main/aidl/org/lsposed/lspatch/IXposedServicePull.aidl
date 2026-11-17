package org.lsposed.lspatch;

/**
 * On-demand service delivery, LSPatch-owned so it lives entirely in the manager -- the upstream
 * libxposed submodule is consumed pristine and never carries a workaround.
 *
 * A module's own app binds this (resolving it by the REQUEST_PUSH action, not by a hard-coded
 * package, so a cloaked manager is still found) and asks the manager to push it its writable service.
 * The push arrives through the module's ordinary, unmodified libxposed XposedProvider -- the same
 * SEND_BINDER path the manager already uses -- so nothing in the module or in libxposed changes. The
 * companion needs only a copy of this one-method interface and to make the call on process start.
 */
interface IXposedServicePull {

    /**
     * Ask the manager to (re)push the caller's own module service into the caller's XposedProvider.
     * The manager authenticates the caller by {@code Binder.getCallingUid()} and pushes only to that
     * caller's own package, so exposing this grants nothing a module could not already do for itself.
     * Returns whether the caller was recognised as a module the manager serves; the push itself is
     * dispatched asynchronously.
     */
    boolean requestPush();
}

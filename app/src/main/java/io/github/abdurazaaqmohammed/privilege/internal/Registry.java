/*
 * Privilege layer — internal. NOT for direct import by callers.
 *
 * The ONLY file that grows when a backend is added: register it here.
 * Selection is per capability, not a global ladder: SHIZUKU and DHIZUKU
 * coexist as parallel privilege domains (SHIZUKU owns shell/file-ops,
 * DHIZUKU owns device-owner operations), ROOT is the last-resort fallback
 * and NONE the terminal fallback. The user may pin a mode manually in
 * settings.
 */
package io.github.abdurazaaqmohammed.privilege.internal;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.abdurazaaqmohammed.privilege.spi.Capability;
import io.github.abdurazaaqmohammed.privilege.spi.PrivilegeBackend;

public final class Registry {

    private static final List<PrivilegeBackend> BACKENDS = new ArrayList<>();

    private Registry() {
    }

    /** Call once from app startup (and again whenever a new backend lands). */
    public static synchronized void register(PrivilegeBackend backend) {
        if (!BACKENDS.contains(backend)) BACKENDS.add(backend);
    }

    public static synchronized List<PrivilegeBackend> all() {
        return Collections.unmodifiableList(new ArrayList<>(BACKENDS));
    }

    /**
     * The authorized backend that supports the capability and has the
     * highest priorityFor(capability); null when none does.
     */
    public static synchronized <T extends Capability> PrivilegeBackend forCapability(
            Context context, Class<T> capability) {
        PrivilegeBackend best = null;
        for (PrivilegeBackend backend : BACKENDS) {
            if (!backend.isAvailable(context) || !backend.isAuthorized(context)) continue;
            if (!capability.isInstance(backend)) continue;
            if (best == null
                    || backend.priorityFor(capability) > best.priorityFor(capability)) {
                best = backend;
            }
        }
        return best;
    }
}

/*
 * Privilege layer — internal. NOT for direct import by callers.
 *
 * The ONLY file that grows when a backend is added: register it here.
 * Selection order (proposal, see ARCHITECTURE_PLAN.md):
 * DHIZUKU > SHIZUKU > ROOT > NONE — persistent delegation preferred over
 * interactive authorization. The user may pin a mode manually in settings.
 */
package io.github.abdurazaaqmohammed.privilege.internal;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
        BACKENDS.sort(Comparator.comparingInt(PrivilegeBackend::priority).reversed());
    }

    public static synchronized List<PrivilegeBackend> all() {
        return Collections.unmodifiableList(new ArrayList<>(BACKENDS));
    }

    /** First backend that is present, authorized AND supports the capability. */
    public static synchronized <T extends Capability> PrivilegeBackend forCapability(
            Context context, Class<T> capability) {
        for (PrivilegeBackend backend : BACKENDS) {
            if (!backend.isAvailable(context) || !backend.isAuthorized(context)) continue;
            if (capability.isInstance(backend)) return backend;
        }
        return null;
    }
}

package io.github.abdurazaaqmohammed.ui.common.theme;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Pure per-Resources ownership state for the theme pack override loaders
 * (no Android types, JVM-testable). At most one loader is owned per
 * Resources; the weak map releases the entry — and with it the loader —
 * when the Resources goes away, so loaders cannot accumulate beyond the
 * number of live activities. Loaders created by anything else never enter
 * this map, so they are never uninstalled by the palette path.
 */
final class ThemePackPaletteState {

    /** A loader this state recorded plus the palette it carries. */
    static final class Owned {
        final Object loader;
        final Map<Integer, Integer> replacements;

        Owned(Object loader, Map<Integer, Integer> replacements) {
            this.loader = loader;
            this.replacements = replacements;
        }
    }

    private final Map<Object, Owned> owned = new WeakHashMap<>();

    /** True when {@code resources} already carries exactly this palette, so
     *  the caller can re-apply slot styles WITHOUT installing a new loader
     *  (same palette must not grow the loader list). */
    synchronized boolean isCurrent(Object resources, Map<Integer, Integer> replacements) {
        Owned current = owned.get(resources);
        return current != null && !replacements.isEmpty()
                && current.replacements.equals(replacements);
    }

    /** Stops tracking this Resources' loader and returns it for uninstall,
     *  or null when nothing was tracked. Call when the palette changed or
     *  the pack went away. */
    synchronized Object previous(Object resources) {
        Owned current = owned.remove(resources);
        return current == null ? null : current.loader;
    }

    /** Records a newly installed loader as owned by {@code resources},
     *  replacing any previous tracking for it. */
    synchronized void adopted(Object resources, Object loader,
                              Map<Integer, Integer> replacements) {
        owned.put(resources, new Owned(loader, replacements));
    }

    /** Test/diagnostic helper: number of currently tracked Resources. */
    synchronized int ownedCount() {
        return owned.size();
    }
}

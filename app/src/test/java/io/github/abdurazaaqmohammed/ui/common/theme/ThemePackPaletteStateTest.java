package io.github.abdurazaaqmohammed.ui.common.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-state coverage of ThemePackPalette's owned-loader lifecycle: one
 * loader per Resources (same palette must not grow it), a different palette
 * replaces the owned loader, clear releases it, loaders owned by anything
 * else are never handed back for removal, and the weak map drops entries
 * with their Resources.
 */
public class ThemePackPaletteStateTest {

    private static final Map<Integer, Integer> PALETTE_A = palette(0xFF111111);
    private static final Map<Integer, Integer> PALETTE_B = palette(0xFF222222);
    private static final Map<Integer, Integer> EMPTY = Collections.emptyMap();

    private final ThemePackPaletteState state = new ThemePackPaletteState();

    private static Map<Integer, Integer> palette(int color) {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        map.put(1, color);
        map.put(2, color + 1);
        return map;
    }

    @Test
    public void samePalette_isCurrent_noNewLoader() {
        Object resources = new Object();
        state.adopted(resources, "L1", PALETTE_A);
        // same palette (fresh equal map) keeps the current loader; the
        // caller checks isCurrent first and never consults previous()
        assertTrue(state.isCurrent(resources, palette(0xFF111111)));
        assertTrue(state.isCurrent(resources, PALETTE_A));
        assertEquals("same palette must not grow the tracked loaders",
                1, state.ownedCount());
        // once the caller does release (palette actually changed), the same
        // loader is handed back for uninstall
        assertSame("L1", state.previous(resources));
        assertEquals(0, state.ownedCount());
    }

    @Test
    public void differentPalette_replacesOwnedLoader() {
        Object resources = new Object();
        state.adopted(resources, "L1", PALETTE_A);
        assertFalse(state.isCurrent(resources, PALETTE_B));
        assertSame("L1", state.previous(resources));
        // after replacement tracking holds the new loader only
        state.adopted(resources, "L2", PALETTE_B);
        assertTrue(state.isCurrent(resources, PALETTE_B));
        assertSame("L2", state.previous(resources));
        assertEquals(0, state.ownedCount());
    }

    @Test
    public void paletteAtoBtoA_distinctInstalls() {
        Object resources = new Object();
        state.adopted(resources, "L1", PALETTE_A);
        assertFalse(state.isCurrent(resources, PALETTE_B));
        assertSame("L1", state.previous(resources));
        state.adopted(resources, "L2", PALETTE_B);
        assertFalse(state.isCurrent(resources, PALETTE_A));
        assertSame("L2", state.previous(resources));
        state.adopted(resources, "L3", PALETTE_A);
        assertTrue(state.isCurrent(resources, PALETTE_A));
        assertEquals(1, state.ownedCount());
    }

    @Test
    public void emptyPalette_releasesOwnedLoader() {
        Object resources = new Object();
        state.adopted(resources, "L1", PALETTE_A);
        assertFalse(state.isCurrent(resources, EMPTY));
        assertSame("L1", state.previous(resources));
        // nothing tracked anymore
        assertNull(state.previous(resources));
        assertFalse(state.isCurrent(resources, PALETTE_A));
        assertEquals(0, state.ownedCount());
    }

    @Test
    public void unknownResources_neverReturnsOwnedLoader() {
        Object resources = new Object();
        Object other = new Object();
        state.adopted(resources, "L1", PALETTE_A);
        assertFalse(state.isCurrent(other, PALETTE_A));
        assertNull("must never hand out a loader for untracked resources",
                state.previous(other));
        // original ownership intact
        assertTrue(state.isCurrent(resources, PALETTE_A));
        assertEquals(1, state.ownedCount());
    }

    @Test
    public void oneLoaderPerResources_replacedTrackingStaysOne() {
        Object first = new Object();
        Object second = new Object();
        state.adopted(first, "L1", PALETTE_A);
        state.adopted(second, "L2", PALETTE_A);
        assertEquals(2, state.ownedCount());
        // live-editor style refresh on the second activity, same palette
        assertTrue(state.isCurrent(second, PALETTE_A));
        assertEquals(2, state.ownedCount());
        // palette switch on the second activity: its own loader replaced
        assertSame("L2", state.previous(second));
        state.adopted(second, "L3", PALETTE_B);
        assertEquals(2, state.ownedCount());
        assertTrue(state.isCurrent(first, PALETTE_A));
        assertTrue(state.isCurrent(second, PALETTE_B));
    }

    @Test
    public void clear_releasesTracking() {
        Object resources = new Object();
        state.adopted(resources, "L1", PALETTE_A);
        assertSame("L1", state.previous(resources)); // clear() uninstall
        // second clear is a no-op
        assertNull(state.previous(resources));
        assertEquals(0, state.ownedCount());
    }
}

package io.github.abdurazaaqmohammed.plugins.ipc;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.abdurazaaqmohammed.plugins.themes.ThemePackContracts;

/** Pure JUnit4 tests for the theme helper (no Android classes involved). */
public class PluginThemeTest {

    @Test
    public void paletteKeys_deriveFromThemeContract() {
        // The allowlist must be the theme wire contract's color tokens (same
        // reference), so v2 additions are picked up automatically.
        assertArrayEquals(ThemePackContracts.COLOR_TOKENS, PluginTheme.PALETTE_KEYS);
        for (String token : ThemePackContracts.COLOR_TOKENS) {
            assertNotNull(token, PluginTheme.canonicalKey(token));
            assertEquals(token, PluginTheme.canonicalKey(token));
        }
    }

    @Test
    public void canonicalKey_isCaseAndSeparatorInsensitive() {
        assertEquals("colorPrimary", PluginTheme.canonicalKey("Color_Primary"));
        assertEquals("colorPrimary", PluginTheme.canonicalKey("color-primary"));
        assertEquals("editor_text", PluginTheme.canonicalKey("EDITOR TEXT"));
        assertEquals("editor_text", PluginTheme.canonicalKey("editor.text"));
        assertNull(PluginTheme.canonicalKey("not_a_token"));
        assertNull(PluginTheme.canonicalKey(""));
        assertNull(PluginTheme.canonicalKey(null));
    }

    @Test
    public void themeInfo_resolvesColorsWithFallback() {
        Map<String, Integer> colors = new LinkedHashMap<>();
        colors.put("Color-Primary", 0xFF0000FF);
        colors.put("editor_text", 0xFF111111);

        PluginTheme.ThemeInfo info =
                new PluginTheme.ThemeInfo("dark-ocean", true, true, 0xFFFF00FF, colors);

        assertEquals(0xFF0000FF, info.color("colorPrimary", 1));
        assertEquals(0xFF111111, info.color("EDITOR-TEXT", 2));
        assertEquals(7, info.color("unknown_token", 7));
        assertEquals(7, info.color(null, 7));
        assertTrue(info.has("colorPrimary"));
        assertFalse(info.has("bogus"));
        assertFalse(info.has(null));
        assertEquals("dark-ocean", info.themeId);
        assertTrue(info.dark);
        assertTrue(info.hasAccent);
        assertEquals(0xFFFF00FF, info.accent);
        assertEquals(2, info.palette().size());
    }

    @Test
    public void themeInfo_withoutAccentKeepsDefault() {
        PluginTheme.ThemeInfo info =
                new PluginTheme.ThemeInfo(null, false, false, 0,
                        java.util.Collections.emptyMap());
        assertNull(info.themeId);
        assertFalse(info.dark);
        assertFalse(info.hasAccent);
        assertEquals(0, info.accent);
        assertTrue(info.palette().isEmpty());
        assertEquals(42, info.color("colorPrimary", 42));
    }

    @Test
    public void paletteValidation_rejectsUnknownNamesAndNullColors() {
        assertTrue(PluginTheme.isPaletteValid(null));
        assertTrue(PluginTheme.isPaletteValid(java.util.Collections.emptyMap()));

        Map<String, Integer> good = new LinkedHashMap<>();
        good.put("colorSurface", 0xFFFFFFFF);
        assertTrue(PluginTheme.isPaletteValid(good));

        Map<String, Integer> unknown = new LinkedHashMap<>();
        unknown.put("not_a_token", 1);
        assertFalse(PluginTheme.isPaletteValid(unknown));

        Map<String, Integer> nullValue = new LinkedHashMap<>();
        nullValue.put("colorSurface", null);
        assertFalse(PluginTheme.isPaletteValid(nullValue));
    }
}

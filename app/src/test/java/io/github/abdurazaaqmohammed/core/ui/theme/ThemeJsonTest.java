package io.github.abdurazaaqmohammed.core.ui.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ThemeJson is deliberately Android-free so the parser and colour handling are
 * testable on a plain JVM (no Robolectric in this project).
 */
public class ThemeJsonTest {

    @Test
    public void parsesThreeDigitHex() {
        assertEquals(0xFFFF0000, ThemeJson.parseColor("#F00"));
        assertEquals(0xFF00FF00, ThemeJson.parseColor("#0F0"));
        assertEquals(0xFF112233, ThemeJson.parseColor("#123"));
    }

    @Test
    public void parsesSixDigitHex() {
        assertEquals(0xFF303030, ThemeJson.parseColor("#303030"));
        assertEquals(0xFFD8D8D8, ThemeJson.parseColor("#D8D8D8"));
        assertEquals(0xFF1976D2, ThemeJson.parseColor("#1976D2"));
    }

    @Test
    public void parsesEightDigitHex() {
        assertEquals(0x80303030, ThemeJson.parseColor("#80303030"));
        assertEquals(0x00000000, ThemeJson.parseColor("#00000000"));
    }

    @Test
    public void acceptsHashlessAndNamedColors() {
        assertEquals(0xFF303030, ThemeJson.parseColor("303030"));
        assertEquals(0xFF000000, ThemeJson.parseColor("black"));
        assertEquals(0xFFFFFFFF, ThemeJson.parseColor("WHITE"));
        assertEquals(0x00000000, ThemeJson.parseColor("transparent"));
    }

    @Test
    public void rejectsGarbage() {
        assertEquals(ThemePalette.ABSENT, ThemeJson.parseColor("#12"));
        assertEquals(ThemePalette.ABSENT, ThemeJson.parseColor("#1234567"));
        assertEquals(ThemePalette.ABSENT, ThemeJson.parseColor("#GGGGGG"));
        assertEquals(ThemePalette.ABSENT, ThemeJson.parseColor("notacolour"));
        assertEquals(ThemePalette.ABSENT, ThemeJson.parseColor(""));
        assertEquals(ThemePalette.ABSENT, ThemeJson.parseColor(null));
    }

    @Test
    public void builtinIdsKeepHistoricalNames() {
        assertEquals(BuiltInThemes.LIGHT_ID, ThemeJson.idFor("myapp_light.json", true));
        assertEquals(BuiltInThemes.DARK_ID, ThemeJson.idFor("myapp_dark.json", true));
        assertEquals(BuiltInThemes.BLACK_ID, ThemeJson.idFor("myapp_black.json", true));
        assertEquals(BuiltInThemes.MT_DARK_ID, ThemeJson.idFor("mt_dark.json", true));
        assertEquals(BuiltInThemes.SYSTEM_DEFAULT_ID, ThemeJson.idFor("system_default.json", true));
    }

    @Test
    public void importedThemesGetCustomPrefix() {
        assertEquals("custom:neon", ThemeJson.idFor("neon.json", false));
    }

    @Test
    public void parsesMtDarkPalette() {
        String json = "{\"name\":\"MT Dark\",\"nightMode\":\"dark\",\"colors\":{"
                + "\"surface\":\"#303030\",\"primary\":\"#1976D2\",\"error\":\"#F00\"}}";
        ThemePalette p = ThemeJson.parse(json, "mt_dark.json", true);
        assertNotNull(p);
        assertEquals("MT Dark", p.name());
        assertEquals(BuiltInThemes.MT_DARK_ID, p.id());
        assertFalse(p.isLight());
        assertTrue(p.hasColors());
        assertEquals(0xFF303030, p.color("surface"));
        assertEquals(0xFF1976D2, p.color("primary"));
        assertEquals(0xFFFF0000, p.color("error"));
    }

    @Test
    public void undeclaredRoleReportsAbsent() {
        ThemePalette p = ThemeJson.parse(
                "{\"nightMode\":\"dark\",\"colors\":{\"surface\":\"#303030\"}}", "x.json", true);
        assertNotNull(p);
        assertEquals(ThemePalette.ABSENT, p.color("primary"));
        assertEquals(ThemePalette.ABSENT, p.color("nonexistent-role"));
    }

    @Test
    public void themeWithoutColorsIsLegal() {
        // Built-in light/dark declare nightMode only and keep Material You.
        ThemePalette p = ThemeJson.parse("{\"name\":\"Light\",\"nightMode\":\"light\"}", "myapp_light.json", true);
        assertNotNull(p);
        assertTrue(p.isLight());
        assertFalse(p.hasColors());
        assertEquals(ThemePalette.ABSENT, p.color("surface"));
    }

    @Test
    public void lightModeVariants() {
        assertTrue(ThemeJson.isLightMode("light"));
        assertTrue(ThemeJson.isLightMode("no"));
        assertTrue(ThemeJson.isLightMode("false"));
        assertTrue(ThemeJson.isLightMode("LIGHT"));
        assertFalse(ThemeJson.isLightMode("dark"));
        assertFalse(ThemeJson.isLightMode("yes"));
        assertFalse(ThemeJson.isLightMode("system"));
        assertFalse(ThemeJson.isLightMode(null));
    }

    @Test
    public void malformedJsonReturnsNull() {
        assertNull(ThemeJson.parse("{not json", "x.json", true));
        assertNull(ThemeJson.parse("", "x.json", true));
        assertNull(ThemeJson.parse(null, "x.json", true));
    }

    @Test
    public void builtinFlagIsCarriedThrough() {
        ThemePalette builtin = ThemeJson.parse("{\"nightMode\":\"dark\"}", "mt_dark.json", true);
        ThemePalette imported = ThemeJson.parse("{\"nightMode\":\"dark\"}", "mt_dark.json", false);
        assertNotNull(builtin);
        assertNotNull(imported);
        assertTrue(builtin.isBuiltin());
        assertFalse(imported.isBuiltin());
    }

    @Test
    public void unknownColorKeysAreIgnored() {
        ThemePalette p = ThemeJson.parse(
                "{\"colors\":{\"surface\":\"#303030\",\"notARole\":\"#FFFFFF\"}}", "x.json", true);
        assertNotNull(p);
        assertEquals(0xFF303030, p.color("surface"));
        assertEquals(ThemePalette.ABSENT, p.color("notARole"));
    }
}

package io.github.abdurazaaqmohammed.ui.common.theme;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure coverage of the pack-contribution gate that ThemeRegistry.applyAccent
 * uses to decide between the pack palette path and the user accent path:
 * a pack must count as contributing when it defines any Material slot token
 * (its palette would be installed), and must NOT count as contributing when
 * it only ships chrome/editor tokens or nothing — otherwise a colorless
 * active pack would silently suppress the user's accent.
 */
public class ThemePackPaletteContributionTest {

    private static Map<String, Integer> colors(String... tokens) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (String token : tokens) {
            map.put(token, 0xFF112233);
        }
        return map;
    }

    @Test
    public void accentAlone_contributes() {
        // buildReplacements falls back colorPrimary -> accent, so an accent
        // alone always produces a palette.
        assertTrue(ThemePackPalette.contributes(Collections.emptyMap(), 0xFF6750A4));
        assertTrue(ThemePackPalette.contributes(null, 0xFF6750A4));
    }

    @Test
    public void slotToken_contributes() {
        assertTrue(ThemePackPalette.contributes(colors("colorPrimary"), null));
        assertTrue(ThemePackPalette.contributes(colors("colorSurface"), null));
        assertTrue(ThemePackPalette.contributes(colors("colorAccent"), null));
        // Slot token plus accent: still one palette.
        assertTrue(ThemePackPalette.contributes(colors("colorPrimary"), 0xFF6750A4));
    }

    @Test
    public void chromeOrEditorTokensOnly_doesNotContribute() {
        // None of these are palette slots: applying them must not suppress
        // the user accent (chrome tokens flow through packColor(), editor
        // tokens through editorSyntax()).
        assertFalse(ThemePackPalette.contributes(colors(
                "status_bar", "nav_bar", "wallpaper_scrim"), null));
        assertFalse(ThemePackPalette.contributes(colors(
                "editor_keyword", "editor_string", "editor_background"), null));
        assertFalse(ThemePackPalette.contributes(colors(
                "folder_icon_bg", "list_highlight"), null));
    }

    @Test
    public void emptyOrNullColorsAndNoAccent_doesNotContribute() {
        assertFalse(ThemePackPalette.contributes(Collections.emptyMap(), null));
        assertFalse(ThemePackPalette.contributes(null, null));
    }

    @Test
    public void mixedSlotAndNonSlotTokens_contributes() {
        assertTrue(ThemePackPalette.contributes(colors(
                "status_bar", "colorPrimaryContainer"), null));
    }
}

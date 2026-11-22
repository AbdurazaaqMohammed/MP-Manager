package io.github.abdurazaaqmohammed.plugins.themes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Map;

/**
 * Parser coverage for the fixed /theme row payload: token allowlist
 * matching, color/dark/id/name scalar validation, colors/strings JSON
 * column parsing with unknown-entry drops and limit enforcement.
 */
public class ThemePackParserTest {

    // ---------- token matching ----------

    @Test
    public void materialNames_matchInCanonicalAndSnakeForms() {
        assertEquals("colorPrimary", ThemePackParser.canonicalColorToken("colorPrimary"));
        assertEquals("colorPrimary", ThemePackParser.canonicalColorToken("color_primary"));
        assertEquals("colorPrimary", ThemePackParser.canonicalColorToken("COLOR-PRIMARY"));
        assertEquals("colorSurfaceContainerHighest",
                ThemePackParser.canonicalColorToken("color_surface_container_highest"));
        assertFalse(ThemePackParser.isColorToken("scrim"));
        assertFalse(ThemePackParser.isColorToken("inverseOnSurface"));
    }

    @Test
    public void appTokens_match() {
        assertEquals("wallpaper_scrim", ThemePackParser.canonicalColorToken("wallpaper_scrim"));
        assertEquals("wallpaper_scrim", ThemePackParser.canonicalColorToken("WallpaperScrim"));
        assertEquals("folder_icon_bg", ThemePackParser.canonicalColorToken("folder_icon_bg"));
        assertEquals("file_icon_bg", ThemePackParser.canonicalColorToken("file-icon-bg"));
        assertEquals("list_highlight", ThemePackParser.canonicalColorToken("list_highlight"));
        assertEquals("nav_bar", ThemePackParser.canonicalColorToken("nav_bar"));
        assertEquals("status_bar", ThemePackParser.canonicalColorToken("status_bar"));
        assertEquals("editor_background", ThemePackParser.canonicalColorToken("editor_background"));
        assertEquals("editor_text", ThemePackParser.canonicalColorToken("editor_text"));
        assertEquals("editor_line_number_current",
                ThemePackParser.canonicalColorToken("editor_line_number_current"));
        assertEquals("editor_line_number_current",
                ThemePackParser.canonicalColorToken("EditorLineNumberCurrent"));
    }

    @Test
    public void editorTokens_allEightMatch() {
        String[] tokens = {"editor_background", "editor_text", "editor_gutter",
                "editor_line_number", "editor_line_number_current",
                "editor_selection", "editor_current_line", "editor_match_highlight"};
        for (String token : tokens) {
            assertEquals(token, ThemePackParser.canonicalColorToken(token));
            assertEquals(token, ThemePackParser.canonicalColorToken(
                    token.replace('_', '-')));
        }
        // no inert typography/icon/font/layout fields are accepted
        assertNull(ThemePackParser.canonicalColorToken("editor_font_size"));
        assertNull(ThemePackParser.canonicalColorToken("editor_typeface"));
        assertNull(ThemePackParser.canonicalColorToken("editor_tab_width"));
    }

    @Test
    public void unknownTokens_dropped() {
        assertNull(ThemePackParser.canonicalColorToken("colorSuperSecret"));
        assertNull(ThemePackParser.canonicalColorToken(""));
        assertNull(ThemePackParser.canonicalColorToken(null));
        assertNull(ThemePackParser.canonicalLabelKey("tab.somethingelse"));
        assertNull(ThemePackParser.canonicalLabelKey("random"));
    }

    @Test
    public void labelKeys_match() {
        assertEquals("tab.files", ThemePackParser.canonicalLabelKey("tab.files"));
        assertEquals("tab.root", ThemePackParser.canonicalLabelKey("TAB_ROOT"));
        assertEquals("tab.general", ThemePackParser.canonicalLabelKey("tab-general"));
        assertEquals("tab.behavior", ThemePackParser.canonicalLabelKey("tab.behavior"));
        assertEquals("tab.other", ThemePackParser.canonicalLabelKey("tab.other"));
        assertEquals("settings.theme", ThemePackParser.canonicalLabelKey("settings.theme"));
    }

    // ---------- scalars ----------

    @Test
    public void colors_parseAllHexForms() {
        assertEquals(0xFF6750A4, ThemePackParser.parseColorValue("#FF6750A4").intValue());
        assertEquals(0x006750A4, ThemePackParser.parseColorValue("#006750A4").intValue());
        assertEquals(0xFF6750A4, ThemePackParser.parseColorValue("#6750A4").intValue());
        assertEquals(0x000000FF, ThemePackParser.parseColorValue(255).intValue());
        assertNull(ThemePackParser.parseColorValue("#12345"));
        assertNull(ThemePackParser.parseColorValue("zztop"));
        assertNull(ThemePackParser.parseColorValue(null));
        assertNull(ThemePackParser.parseColorValue(""));
    }

    @Test
    public void dark_parsesIntegerAndStringForms() {
        assertEquals(Boolean.TRUE, ThemePackParser.parseDark(1));
        assertEquals(Boolean.FALSE, ThemePackParser.parseDark(0));
        assertEquals(Boolean.TRUE, ThemePackParser.parseDark("1"));
        assertEquals(Boolean.FALSE, ThemePackParser.parseDark("false"));
        assertNull(ThemePackParser.parseDark(null));
        assertNull(ThemePackParser.parseDark(2));
        assertNull(ThemePackParser.parseDark("maybe"));
    }

    @Test
    public void id_requiresNonEmptyBoundedCharset() {
        assertEquals("mytheme_1", ThemePackParser.parseId(" mytheme_1 "));
        assertEquals("My.Theme-2", ThemePackParser.parseId("My.Theme-2"));
        try {
            ThemePackParser.parseId(null);
            fail("null id must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseId("  ");
            fail("empty id must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseId("bad/id");
            fail("slash in id must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseId("bad id with spaces");
            fail("spaces in id must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            StringBuilder longId = new StringBuilder();
            for (int i = 0; i < 101; i++) longId.append('a');
            ThemePackParser.parseId(longId.toString());
            fail("overlong id must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
    }

    @Test
    public void name_sanitizes() {
        assertEquals("Midnight", ThemePackParser.parseName(" Midnight "));
        assertEquals("Midnight", ThemePackParser.parseName("Mid\nnight"));
        assertNull(ThemePackParser.parseName(null));
        assertNull(ThemePackParser.parseName("\t\n"));
        assertEquals(100, ThemePackParser.parseName(repeat("ab", 100)).length());
    }

    // ---------- colors column ----------

    @Test
    public void colors_keepKnownTokens_dropUnknownAndBroken() {
        String json = "{\"colorPrimary\":\"#FF6750A4\",\"color_primary\":\"#112233\","
                + "\"colorSecret\":\"#FF0000\",\"colorOutline\":\"nope\","
                + "\"folder_icon_bg\":4281545523}";
        Map<String, Integer> colors = ThemePackParser.parseColors(json);
        // distinct canonical tokens: colorPrimary (duplicate spelling overwrites)
        // and folder_icon_bg; unknown token and broken value are dropped
        assertEquals(2, colors.size());
        assertEquals(0xFF112233, colors.get("colorPrimary").intValue());
        assertEquals(0xFF333333, colors.get("folder_icon_bg").intValue());
        assertFalse(colors.containsKey("colorSecret"));
        assertFalse(colors.containsKey("colorOutline"));
    }

    @Test
    public void colors_malformedJson_throws() {
        try {
            ThemePackParser.parseColors("{not json");
            fail("malformed colors JSON must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseColors("[1,2]");
            fail("non-object colors JSON must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseColors(repeat("{\"x\":\"#FF000000\",", 40000) + "}");
            fail("oversized colors JSON must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
    }

    @Test
    public void colors_nullOrEmpty_isEmptyMap() {
        assertTrue(ThemePackParser.parseColors(null).isEmpty());
        assertTrue(ThemePackParser.parseColors("").isEmpty());
    }

    // ---------- strings column ----------

    @Test
    public void strings_parseLocales_dropUnknownAndBroken() {
        String json = "{\"tab.files\":{\"en-US\":\"Files\",\"zh-CN\":\"\u6587\u4ef6\"},"
                + "\"tab.root\":{\"en\":\"Root\"},"
                + "\"tab.secret\":{\"en\":\"nope\"},"
                + "\"tab.general\":{\"en-US\":123,\"fr\":\"G\\u00e9n\\u00e9ral\"}}";
        Map<String, Map<String, String>> strings = ThemePackParser.parseStrings(json);
        assertEquals(3, strings.size());
        assertEquals("Files", strings.get("tab.files").get("en-US"));
        assertEquals("\u6587\u4ef6", strings.get("tab.files").get("zh-CN"));
        assertEquals("Root", strings.get("tab.root").get("en"));
        assertFalse(strings.containsKey("tab.secret"));
        // non-string text dropped, the valid sibling locale stays
        assertEquals("G\u00e9n\u00e9ral", strings.get("tab.general").get("fr"));
        assertFalse(strings.get("tab.general").containsKey("en-US"));
    }

    @Test
    public void strings_malformedJsonAndLimits() {
        try {
            ThemePackParser.parseStrings("{oops");
            fail("malformed strings JSON must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseStrings(repeat("{\"tab.files\":{\"en\":\"a\"},", 40000) + "}");
            fail("oversized strings JSON must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
    }

    @Test
    public void strings_badLocaleTagsDropped_andPerLocaleCap() {
        StringBuilder json = new StringBuilder("{\"settings.theme\":{\"en\":\"yes\",");
        json.append("\"en_US\":\"nope\",");      // underscore tag rejected
        json.append("\"toolongtagpart-aaaaaaaaaaaaaaaaaaaaaaaaaa\":\"nope\",");
        // 40 valid two-letter language tags ("aa".."az","ba"..) to hit the cap
        for (int i = 0; i < 40; i++) {
            char first = (char) ('a' + i % 26);
            char second = (char) ('a' + i / 26);
            json.append('"').append(first).append(second).append("\":\"v").append(i).append("\",");
        }
        json.append("\"zz\":\"tail\"}}");
        Map<String, Map<String, String>> strings = ThemePackParser.parseStrings(json.toString());
        Map<String, String> locales = strings.get("settings.theme");
        assertTrue(locales.containsValue("yes"));
        assertFalse(locales.containsKey("en_US"));
        assertEquals(32, locales.size()); // cap enforced; trailing entries dropped
        assertFalse(locales.containsKey("zz"));
    }

    // ---------- editor syntax column (v2) ----------

    @Test
    public void editorSyntax_parseCanonicalCategories() {
        String json = "{\"comment\":\"#FF6A9955\",\"keyword\":\"#FFCC7832\","
                + "\"string\":\"#FF6A8759\",\"number\":\"#FF6897BB\","
                + "\"type\":\"#FFB5B6E3\",\"function\":\"#FF7DCFFF\","
                + "\"operator\":\"#FFFF7EDB\",\"variable\":\"#FFF8F8F2\"}";
        Map<String, Integer> syntax = ThemePackParser.parseEditorSyntax(json);
        assertEquals(8, syntax.size());
        assertEquals(0xFF6A9955, syntax.get("comment").intValue());
        assertEquals(0xFFCC7832, syntax.get("keyword").intValue());
        assertEquals(0xFF6A8759, syntax.get("string").intValue());
        assertEquals(0xFF6897BB, syntax.get("number").intValue());
        assertEquals(0xFFB5B6E3, syntax.get("type").intValue());
        assertEquals(0xFF7DCFFF, syntax.get("function").intValue());
        assertEquals(0xFFFF7EDB, syntax.get("operator").intValue());
        assertEquals(0xFFF8F8F2, syntax.get("variable").intValue());
    }

    @Test
    public void editorSyntax_acceptsCaseAndSeparatorVariants() {
        assertEquals("keyword", ThemePackParser.canonicalSyntaxKey("KEYWORD"));
        assertEquals("function", ThemePackParser.canonicalSyntaxKey("Function"));
        // separators stripped: "key-word" lands on the canonical category
        assertEquals("keyword", ThemePackParser.canonicalSyntaxKey("key-word"));
        assertEquals("operator", ThemePackParser.canonicalSyntaxKey("Keyword.Operator.Assignment"));
    }

    @Test
    public void editorSyntax_mapsTextMateAliases() {
        assertEquals("function", ThemePackParser.canonicalSyntaxKey("entity.name.function"));
        assertEquals("function", ThemePackParser.canonicalSyntaxKey("meta.function-call"));
        assertEquals("type", ThemePackParser.canonicalSyntaxKey("entity.name.class"));
        assertEquals("type", ThemePackParser.canonicalSyntaxKey("storage.class"));
        assertEquals("number", ThemePackParser.canonicalSyntaxKey("constant.numeric"));
        assertEquals("number", ThemePackParser.canonicalSyntaxKey("constant.numeric.hex"));
        assertEquals("operator", ThemePackParser.canonicalSyntaxKey("keyword.operator.assignment"));
        assertEquals("operator", ThemePackParser.canonicalSyntaxKey("punctuation.separator.key-value"));
        assertEquals("variable", ThemePackParser.canonicalSyntaxKey("variable.parameter"));
        assertEquals("string", ThemePackParser.canonicalSyntaxKey("string.quoted.double"));
        assertEquals("string", ThemePackParser.canonicalSyntaxKey("constant.character.escape"));
        assertEquals("comment", ThemePackParser.canonicalSyntaxKey("punctuation.definition.comment"));
        assertEquals("keyword", ThemePackParser.canonicalSyntaxKey("storage.type"));
        assertEquals("keyword", ThemePackParser.canonicalSyntaxKey("keyword.control"));
    }

    @Test
    public void editorSyntax_unknownKeysAndBrokenValuesDropped() {
        String json = "{\"meta.embedded\":\"#FF111111\",\"comment\":\"nope\","
                + "\"boolean_literal\":\"#FF222222\",\"keyword\":\"#FF333333\"}";
        Map<String, Integer> syntax = ThemePackParser.parseEditorSyntax(json);
        // meta.embedded is not an allowlisted alias, boolean_literal is not a
        // category; broken color dropped; only valid entries survive
        assertEquals(1, syntax.size());
        assertEquals(0xFF333333, syntax.get("keyword").intValue());
    }

    @Test
    public void editorSyntax_lastWinsPerCategory() {
        String json = "{\"entity.name.function\":\"#FF111111\",\"function\":\"#FF222222\"}";
        Map<String, Integer> syntax = ThemePackParser.parseEditorSyntax(json);
        assertEquals(1, syntax.size());
        assertEquals(0xFF222222, syntax.get("function").intValue());
    }

    @Test
    public void editorSyntax_malformedOrOversized_throws() {
        try {
            ThemePackParser.parseEditorSyntax("{oops");
            fail("malformed editor_syntax JSON must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseEditorSyntax(repeat("{\"comment\":\"#FF000000\",", 20000) + "}");
            fail("oversized editor_syntax JSON must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
    }

    @Test
    public void editorSyntax_nullOrEmpty_isEmptyMap() {
        assertTrue(ThemePackParser.parseEditorSyntax(null).isEmpty());
        assertTrue(ThemePackParser.parseEditorSyntax("").isEmpty());
        assertTrue(ThemePackParser.parseEditorSyntax("   ").isEmpty());
    }

    // ---------- wallpaper scope (v2) ----------

    @Test
    public void wallpaperScope_defaultsToFiles() {
        assertEquals("files", ThemePackParser.parseWallpaperScope(null));
        assertEquals("files", ThemePackParser.parseWallpaperScope(""));
        assertEquals("files", ThemePackParser.parseWallpaperScope("   "));
    }

    @Test
    public void wallpaperScope_acceptsExactValues() {
        assertEquals("files", ThemePackParser.parseWallpaperScope("files"));
        assertEquals("all", ThemePackParser.parseWallpaperScope("all"));
        assertEquals("none", ThemePackParser.parseWallpaperScope("none"));
    }

    @Test
    public void wallpaperScope_invalidFailsImport() {
        try {
            ThemePackParser.parseWallpaperScope("everything");
            fail("unknown scope must fail");
        } catch (ThemePackParser.ParseException expected) {
        }
        try {
            ThemePackParser.parseWallpaperScope("FILES");
            fail("scope matching is exact, not case-insensitive");
        } catch (ThemePackParser.ParseException expected) {
        }
    }

    // ---------- locale tag sanitation ----------

    @Test
    public void localeTags_validated() {
        assertEquals("en-US", ThemePackParser.sanitizeLocale("en-US"));
        assertEquals("zh-CN", ThemePackParser.sanitizeLocale("zh-CN"));
        assertNull(ThemePackParser.sanitizeLocale("en_US"));
        assertNull(ThemePackParser.sanitizeLocale("en..US"));
        assertNull(ThemePackParser.sanitizeLocale(""));
        assertNull(ThemePackParser.sanitizeLocale(null));
        assertNull(ThemePackParser.sanitizeLocale("en-aaaaaaaaaaaaaaaaaaaaaaaaa"));
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) sb.append(s);
        return sb.toString();
    }
}

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

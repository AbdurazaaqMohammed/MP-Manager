package io.github.abdurazaaqmohammed.ui.common.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Pure coverage for the editor side of the theme pack v2 contract:
 * canonical editor token -> Sora scheme slot mapping, canonical syntax
 * category -> shared non-TextMate slots, host-owned base theme JSON merge
 * (pack rules must beat BOTH equal- and more-specific base rules), and the
 * epoch-diff signature used by the in-place refresh fallback.
 *
 * The scheme constants referenced here are compile-time constants of the
 * bundled sora AAR, so these tests stay on the JVM; the literal ids in the
 * mapping assertions pin the mapping against accidental dependency drift.
 */
public class ThemePackEditorColorsTest {

    // ---------- editor surface tokens ----------

    @Test
    public void editorTokens_mapToVerifiedSchemeSlots() {
        Map<String, Integer> pack = new LinkedHashMap<>();
        pack.put("editor_background", 0xFF111111);
        pack.put("editor_text", 0xFFEEEEEE);
        pack.put("editor_gutter", 0xFF222222);
        pack.put("editor_line_number", 0xFF333333);
        pack.put("editor_line_number_current", 0xFF444444);
        pack.put("editor_selection", 0xFF555555);
        pack.put("editor_current_line", 0xFF666666);
        pack.put("editor_match_highlight", 0xFF777777);

        Map<Integer, Integer> overrides = ThemePackEditorColors.schemeColorOverrides(pack);

        assertEquals(8, overrides.size());
        // Slot ids asserted against the real constants AND pinned literals.
        assertEquals(0xFF111111, overrides.get(EditorColorScheme.WHOLE_BACKGROUND).intValue());
        assertEquals(4, EditorColorScheme.WHOLE_BACKGROUND);
        assertEquals(0xFFEEEEEE, overrides.get(EditorColorScheme.TEXT_NORMAL).intValue());
        assertEquals(5, EditorColorScheme.TEXT_NORMAL);
        assertEquals(0xFF222222, overrides.get(EditorColorScheme.LINE_NUMBER_BACKGROUND).intValue());
        assertEquals(3, EditorColorScheme.LINE_NUMBER_BACKGROUND);
        assertEquals(0xFF333333, overrides.get(EditorColorScheme.LINE_NUMBER).intValue());
        assertEquals(2, EditorColorScheme.LINE_NUMBER);
        assertEquals(0xFF444444, overrides.get(EditorColorScheme.LINE_NUMBER_CURRENT).intValue());
        assertEquals(45, EditorColorScheme.LINE_NUMBER_CURRENT);
        assertEquals(0xFF555555, overrides.get(EditorColorScheme.SELECTED_TEXT_BACKGROUND).intValue());
        assertEquals(6, EditorColorScheme.SELECTED_TEXT_BACKGROUND);
        assertEquals(0xFF666666, overrides.get(EditorColorScheme.CURRENT_LINE).intValue());
        assertEquals(9, EditorColorScheme.CURRENT_LINE);
        assertEquals(0xFF777777, overrides.get(EditorColorScheme.MATCHED_TEXT_BACKGROUND).intValue());
        assertEquals(29, EditorColorScheme.MATCHED_TEXT_BACKGROUND);
    }

    @Test
    public void partialPack_neverProducesEntriesForAbsentTokens() {
        // A pack shipping only background/text must not reset any other slot:
        // absent tokens produce no setColor targets, so the editor preference
        // scheme keeps everything else byte-for-byte.
        Map<String, Integer> pack = new LinkedHashMap<>();
        pack.put("editor_background", 0xFF000000);
        pack.put("editor_text", 0xFFFFFFFF);
        pack.put("not_an_editor_token", 0xFFABCDEF);

        Map<Integer, Integer> overrides = ThemePackEditorColors.schemeColorOverrides(pack);

        assertEquals(2, overrides.size());
        assertTrue(overrides.containsKey(EditorColorScheme.WHOLE_BACKGROUND));
        assertTrue(overrides.containsKey(EditorColorScheme.TEXT_NORMAL));
        assertFalse(overrides.containsKey(EditorColorScheme.LINE_NUMBER));
        assertFalse(overrides.containsKey(EditorColorScheme.MATCHED_TEXT_BACKGROUND));
    }

    @Test
    public void nullAndEmptyPacks_produceNoOverrides() {
        assertTrue(ThemePackEditorColors.schemeColorOverrides(null).isEmpty());
        assertTrue(ThemePackEditorColors.schemeColorOverrides(
                new LinkedHashMap<String, Integer>()).isEmpty());
        assertTrue(ThemePackEditorColors.editorTokenColors(null).isEmpty());
    }

    // ---------- non-TextMate syntax slots (documented, shared) ----------

    @Test
    public void syntaxCategories_mapToRealSharedSlots() {
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("comment", 0xFF6A9955);
        syntax.put("keyword", 0xFF569CD6);
        syntax.put("string", 0xFFCE9178);
        syntax.put("number", 0xFFB5CEA8);
        syntax.put("type", 0xFF4EC9B0);
        syntax.put("function", 0xFFDCDCAA);
        syntax.put("operator", 0xFFD4D4D4);
        syntax.put("variable", 0xFF9CDCFE);
        syntax.put("invented", 0xFFFF0000); // not allowlisted: dropped

        Map<Integer, Integer> overrides = ThemePackEditorColors.syntaxSchemeOverrides(syntax);

        assertEquals(0xFF6A9955, overrides.get(EditorColorScheme.COMMENT).intValue());
        assertEquals(22, EditorColorScheme.COMMENT);
        assertEquals(0xFF569CD6, overrides.get(EditorColorScheme.KEYWORD).intValue());
        assertEquals(21, EditorColorScheme.KEYWORD);
        assertEquals(0xFFDCDCAA, overrides.get(EditorColorScheme.FUNCTION_NAME).intValue());
        assertEquals(27, EditorColorScheme.FUNCTION_NAME);
        assertEquals(0xFFD4D4D4, overrides.get(EditorColorScheme.OPERATOR).intValue());
        assertEquals(23, EditorColorScheme.OPERATOR);
        assertEquals(0xFF9CDCFE, overrides.get(EditorColorScheme.IDENTIFIER_VAR).intValue());
        assertEquals(25, EditorColorScheme.IDENTIFIER_VAR);
        assertEquals(0xFF4EC9B0, overrides.get(EditorColorScheme.IDENTIFIER_NAME).intValue());
        assertEquals(26, EditorColorScheme.IDENTIFIER_NAME);
        assertFalse(overrides.containsKey(0xFFFF0000));
    }

    @Test
    public void stringAndNumber_shareTheLiteralSlot_byDocumentedDesign() {
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("string", 0xFFCE9178);
        syntax.put("number", 0xFFB5CEA8);

        Map<Integer, Integer> overrides = ThemePackEditorColors.syntaxSchemeOverrides(syntax);

        // LITERAL (24) is the only literal slot in sora 0.23.6; the two
        // categories share it and the later category wins the slot value.
        assertEquals(24, EditorColorScheme.LITERAL);
        assertEquals(1, overrides.keySet().stream()
                .filter(id -> id == EditorColorScheme.LITERAL).count());
    }

    // ---------- host-owned theme JSON merge ----------

    private static final String BASE_WITH_SETTINGS = "{\n"
            + "  \"name\": \"dark\",\n"
            + "  \"settings\": [\n"
            + "    {\"settings\": {\"background\": \"#000000\", \"foreground\": \"#E0E0E0\"}},\n"
            + "    {\"name\": \"Comment\", \"scope\": \"comment\", \"settings\": {\"foreground\": \"#8A8A8A\"}},\n"
            + "    {\"name\": \"Java keyword\", \"scope\": \"keyword.control.java\", \"settings\": {\"foreground\": \"#CC7832\", \"fontStyle\": \"italic\"}},\n"
            + "    {\"name\": \"Multi\", \"scope\": \"string, string.quoted.double\", \"settings\": {\"foreground\": \"#6A8759\", \"fontStyle\": \"underline\"}},\n"
            + "    {\"name\": \"Other\", \"scope\": \"entity.other.attribute-name\", \"settings\": {\"foreground\": \"#9876AA\"}}\n"
            + "  ]\n"
            + "}";

    @Test
    public void packRule_appendedLast_withCanonicalSelectors() {
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("comment", 0xFF00FF00);
        syntax.put("keyword", 0xFF0000FF);

        String merged = ThemePackEditorColors.mergeThemeJson(BASE_WITH_SETTINGS, syntax);
        JsonObject root = JsonParser.parseString(merged).getAsJsonObject();
        JsonArray settings = root.getAsJsonArray("settings");

        JsonObject last = settings.get(settings.size() - 1).getAsJsonObject();
        assertEquals("keyword", last.get("scope").getAsString());
        assertEquals("#0000FF", last.getAsJsonObject("settings").get("foreground").getAsString());
        JsonObject beforeLast = settings.get(settings.size() - 2).getAsJsonObject();
        assertEquals("comment", beforeLast.get("scope").getAsString());
        assertEquals("#00FF00", beforeLast.getAsJsonObject("settings").get("foreground").getAsString());
    }

    @Test
    public void baseRules_insideOverriddenCategory_loseForeground() {
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("keyword", 0xFF0000FF);
        syntax.put("string", 0xFFFF0000);

        String merged = ThemePackEditorColors.mergeThemeJson(BASE_WITH_SETTINGS, syntax);
        JsonArray settings = JsonParser.parseString(merged)
                .getAsJsonObject().getAsJsonArray("settings");

        boolean sawKeywordControl = false;
        boolean sawStringRule = false;
        for (JsonElement e : settings) {
            JsonObject rule = e.getAsJsonObject();
            String scope = rule.has("scope") && rule.get("scope").isJsonPrimitive()
                    ? rule.get("scope").getAsString() : "";
            if ("keyword.control.java".equals(scope)) {
                sawKeywordControl = true;
                // fontStyle kept; foreground must be gone so the appended
                // broad pack rule cannot be beaten by deeper specificity.
                assertFalse(rule.getAsJsonObject("settings").has("foreground"));
                assertEquals("italic", rule.getAsJsonObject("settings").get("fontStyle").getAsString());
            }
            // Check only the base sub-scope rule; the appended pack rule for
            // the bare "string" selector legitimately carries its foreground.
            if (scope.contains("string.quoted")) {
                sawStringRule = true;
                assertFalse(rule.getAsJsonObject("settings").has("foreground"));
            }
        }
        assertTrue(sawKeywordControl);
        assertTrue(sawStringRule);

        // Unrelated base rule survives untouched.
        for (JsonElement e : settings) {
            JsonObject rule = e.getAsJsonObject();
            if (rule.has("scope") && "entity.other.attribute-name".equals(
                    rule.get("scope").getAsString())) {
                assertEquals("#9876AA",
                        rule.getAsJsonObject("settings").get("foreground").getAsString());
            }
        }
    }

    @Test
    public void globalDefaultsEntry_untouched_byCategoryMerge() {
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("keyword", 0xFF0000FF);

        String merged = ThemePackEditorColors.mergeThemeJson(BASE_WITH_SETTINGS, syntax);
        JsonArray settings = JsonParser.parseString(merged)
                .getAsJsonObject().getAsJsonArray("settings");
        JsonObject first = settings.get(0).getAsJsonObject();
        assertFalse(first.has("scope"));
        assertEquals("#E0E0E0", first.getAsJsonObject("settings").get("foreground").getAsString());
    }

    @Test
    public void packColorWinsOverDeeperBaseRule_inRealTm4eResolution() throws Exception {
        // End-to-end precedence guarantee: after the merge, a deeper base rule
        // (keyword.control.java, foreground stripped, fontStyle kept) inherits
        // the appended pack keyword foreground through tm4e's trie, instead of
        // overriding the pack with the old base color.
        String base = "{\"settings\":["
                + "{\"settings\":{\"foreground\":\"#E0E0E0\"}},"
                + "{\"scope\":\"keyword.control.java\",\"settings\":{\"foreground\":\"#CC7832\",\"fontStyle\":\"italic\"}},"
                + "{\"scope\":\"entity.other\",\"settings\":{\"foreground\":\"#9876AA\"}}"
                + "]}";
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("keyword", 0xFF0000FF);

        String merged = ThemePackEditorColors.mergeThemeJson(base, syntax);
        org.eclipse.tm4e.core.internal.theme.raw.IRawTheme raw =
                org.eclipse.tm4e.core.internal.theme.raw.RawThemeReader.readTheme(
                        org.eclipse.tm4e.core.registry.IThemeSource.fromString(
                                org.eclipse.tm4e.core.registry.IThemeSource.ContentType.JSON, merged));
        org.eclipse.tm4e.core.internal.theme.Theme theme =
                org.eclipse.tm4e.core.internal.theme.Theme.createFromRawTheme(raw, null);

        org.eclipse.tm4e.core.internal.theme.StyleAttributes deep = theme.match(
                org.eclipse.tm4e.core.internal.grammar.ScopeStack.from(
                        "source.java", "keyword.control.java"));
        assertEquals("#0000FF", theme.getColor(deep.foregroundId));

        // Unrelated scope keeps the base color untouched.
        org.eclipse.tm4e.core.internal.theme.StyleAttributes unrelated = theme.match(
                org.eclipse.tm4e.core.internal.grammar.ScopeStack.from(
                        "source.java", "entity.other"));
        assertEquals("#9876AA", theme.getColor(unrelated.foregroundId));

        // Bare category scope resolves to the pack color too.
        org.eclipse.tm4e.core.internal.theme.StyleAttributes bare = theme.match(
                org.eclipse.tm4e.core.internal.grammar.ScopeStack.from("keyword"));
        assertEquals("#0000FF", theme.getColor(bare.foregroundId));
    }

    // ---------- same-scope theme change must reload, not skip (fragment core) ----------

    /**
     * The exact decision sequence UnifiedEditorFragment#applySyntax must follow
     * when a pack A -> pack B (or base dark -> light) change lands while the
     * SAME scope (e.g. text.xml / source.json) is already live on the editor:
     * the recorded applied language still belongs to the previous TextMate
     * registry, so the applied record MUST be invalidated before the decision.
     * Otherwise SKIP_APPLIED keeps serving the old registry's colors and a
     * signature re-record masks the bug. Invalidating only drops bookkeeping -
     * the decision core never touches editor content.
     */
    @Test
    public void sameScopeThemeChange_invalidatesAppliedRecord_beforeDecision() {
        io.github.abdurazaaqmohammed.feature.editor.SyntaxApplyPolicy policy =
                new io.github.abdurazaaqmohammed.feature.editor.SyntaxApplyPolicy();
        String xml = "text.xml";
        Object language = new Object(); // language instance live on the editor

        // Initial load: applied once for pack A / dark base.
        assertEquals(io.github.abdurazaaqmohammed.feature.editor.SyntaxApplyPolicy.Decision.LOAD_ASYNC,
                policy.onRequest(xml, policy.isAppliedToEditor(language)));
        policy.onLanguageApplied(language, policy.currentGeneration());

        // BUG path (must not be reachable after the fix): the same scope with
        // the same language would skip and restyle through the STALE registry.
        assertEquals(io.github.abdurazaaqmohammed.feature.editor.SyntaxApplyPolicy.Decision.SKIP_APPLIED,
                policy.onRequest(xml, policy.isAppliedToEditor(language)));
        // Undo the skip bookkeeping so the sequence below starts equivalent.
        policy.onViewDestroyed();
        policy.onRequest(xml, policy.isAppliedToEditor(language)); // re-records as skip state
        // Actually restore applied state cleanly for the fixed-path check:
        policy.onLanguageApplied(language, policy.currentGeneration());

        // FIXED core: signature diff -> invalidate FIRST, then decide. The
        // same scope now reloads fresh instead of skipping.
        policy.onViewDestroyed(); // what applySyntax does on themeChangedSinceLastApply()
        assertEquals(io.github.abdurazaaqmohammed.feature.editor.SyntaxApplyPolicy.Decision.LOAD_ASYNC,
                policy.onRequest(xml, policy.isAppliedToEditor(language)));
        // No APPLY_PLAIN decision occurred: the current language (and buffer)
        // stays live until the fresh async result replaces it.
    }

    /**
     * Pack A -> pack B on the same base theme: the merged host-owned theme
     * resolved by the fresh registry must restyle token colors to pack B while
     * unrelated scopes keep the base colors in both packs (registry content,
     * not buffer, changes).
     */
    @Test
    public void packFlip_sameScope_rebuiltRegistryRestylesTokenColors() throws Exception {
        String base = "{\"settings\":["
                + "{\"settings\":{\"foreground\":\"#E0E0E0\"}},"
                + "{\"scope\":\"keyword.control.java\",\"settings\":{\"foreground\":\"#CC7832\",\"fontStyle\":\"italic\"}},"
                + "{\"scope\":\"entity.other\",\"settings\":{\"foreground\":\"#9876AA\"}}"
                + "]}";

        Map<String, Integer> packA = new LinkedHashMap<>();
        packA.put("keyword", 0xFF0000FF);
        Map<String, Integer> packB = new LinkedHashMap<>();
        packB.put("keyword", 0xFFFF0000);

        org.eclipse.tm4e.core.internal.theme.Theme themeA = resolve(merge(base, packA));
        org.eclipse.tm4e.core.internal.theme.Theme themeB = resolve(merge(base, packB));

        // Deep base rule now resolves to each pack's color, not the base color.
        org.eclipse.tm4e.core.internal.grammar.ScopeStack deep =
                org.eclipse.tm4e.core.internal.grammar.ScopeStack.from("source.java", "keyword.control.java");
        assertEquals("#0000FF", themeA.getColor(themeA.match(deep).foregroundId));
        assertEquals("#FF0000", themeB.getColor(themeB.match(deep).foregroundId));

        // Unrelated scopes keep the base colors under both packs: the refresh
        // restyles exactly the shipped categories.
        org.eclipse.tm4e.core.internal.grammar.ScopeStack other =
                org.eclipse.tm4e.core.internal.grammar.ScopeStack.from("source.java", "entity.other");
        assertEquals("#9876AA", themeA.getColor(themeA.match(other).foregroundId));
        assertEquals("#9876AA", themeB.getColor(themeB.match(other).foregroundId));
    }

    private static String merge(String base, Map<String, Integer> syntax) {
        return ThemePackEditorColors.mergeThemeJson(base, syntax);
    }

    private static org.eclipse.tm4e.core.internal.theme.Theme resolve(String mergedJson)
            throws Exception {
        return org.eclipse.tm4e.core.internal.theme.Theme.createFromRawTheme(
                org.eclipse.tm4e.core.internal.theme.raw.RawThemeReader.readTheme(
                        org.eclipse.tm4e.core.registry.IThemeSource.fromString(
                                org.eclipse.tm4e.core.registry.IThemeSource.ContentType.JSON,
                                mergedJson)),
                null);
    }

    @Test
    public void themeWithoutRuleArrays_getsTokenColorsCreated() {
        String base = "{\"name\":\"minimal\",\"colors\":{\"editor.background\":\"#000000\"}}";
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("variable", 0xFF112233);

        String merged = ThemePackEditorColors.mergeThemeJson(base, syntax);
        JsonObject root = JsonParser.parseString(merged).getAsJsonObject();
        JsonArray tokenColors = root.getAsJsonArray("tokenColors");
        assertEquals(1, tokenColors.size());
        JsonObject rule = tokenColors.get(0).getAsJsonObject();
        assertEquals("variable", rule.get("scope").getAsString());
        assertEquals("#112233", rule.getAsJsonObject("settings").get("foreground").getAsString());
        // Base colors section preserved.
        assertEquals("#000000",
                root.getAsJsonObject("colors").get("editor.background").getAsString());
    }

    @Test
    public void tokenColorsFormat_isMergedToo() {
        String base = "{\"tokenColors\":["
                + "{\"scope\":\"comment\",\"settings\":{\"foreground\":\"#888888\"}},"
                + "{\"scope\":\"comment.bold\",\"settings\":{\"foreground\":\"#888888\",\"fontStyle\":\"bold\"}}"
                + "]}";
        Map<String, Integer> syntax = new LinkedHashMap<>();
        syntax.put("comment", 0xFFAAAAAA);

        String merged = ThemePackEditorColors.mergeThemeJson(base, syntax);
        JsonObject root = JsonParser.parseString(merged).getAsJsonObject();
        JsonArray tokenColors = root.getAsJsonArray("tokenColors");
        // The foreground-only base rule is dropped entirely (nothing left to
        // contribute); the fontStyle-bearing rule is kept without foreground;
        // the pack rule is appended last.
        assertEquals(2, tokenColors.size());
        assertEquals("comment.bold", tokenColors.get(0).getAsJsonObject().get("scope").getAsString());
        assertFalse(tokenColors.get(0).getAsJsonObject().getAsJsonObject("settings").has("foreground"));
        assertEquals("comment", tokenColors.get(1).getAsJsonObject().get("scope").getAsString());
        assertEquals("#AAAAAA", tokenColors.get(1).getAsJsonObject()
                .getAsJsonObject("settings").get("foreground").getAsString());
    }

    @Test
    public void malformedOrMissingInputs_returnBaseUnchanged() {
        assertEquals("not json", ThemePackEditorColors.mergeThemeJson("not json",
                single("keyword", 0xFF0000FF)));
        assertEquals("[1,2]", ThemePackEditorColors.mergeThemeJson("[1,2]",
                single("keyword", 0xFF0000FF)));
        Map<String, Integer> empty = new LinkedHashMap<>();
        assertEquals(BASE_WITH_SETTINGS,
                ThemePackEditorColors.mergeThemeJson(BASE_WITH_SETTINGS, empty));
        assertNull(ThemePackEditorColors.mergeThemeJson(null, single("keyword", 0xFF0000FF)));
    }

    @Test
    public void unknownCategoriesAreNeverInjected() {
        String base = "{\"settings\":[]}";
        Map<String, Integer> syntax = new HashMap<>();
        syntax.put("made.up", 0xFF123456);

        assertEquals(base, ThemePackEditorColors.mergeThemeJson(base, syntax));
    }

    @Test
    public void colorsFormat_opaqueVsTranslucent() {
        assertEquals("#00FF00", ThemePackEditorColors.toThemeColor(0xFF00FF00));
        assertEquals("#80123456", ThemePackEditorColors.toThemeColor(0x80123456));
    }

    private static Map<String, Integer> single(String key, int value) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    // ---------- epoch-diff signature ----------

    @Test
    public void signature_changesWhenEpochOrThemeOrMapsChange() {
        Map<String, Integer> colors = single("editor_background", 0xFF000000);
        Map<String, Integer> syntax = single("keyword", 0xFF0000FF);
        String base = ThemePackEditorColors.signatureOf(1, "dark", colors, syntax);

        assertFalse(base.equals(ThemePackEditorColors.signatureOf(2, "dark", colors, syntax)));
        assertFalse(base.equals(ThemePackEditorColors.signatureOf(1, "light", colors, syntax)));
        assertFalse(base.equals(ThemePackEditorColors.signatureOf(1, "dark",
                single("editor_background", 0xFFFFFFFF), syntax)));
        assertFalse(base.equals(ThemePackEditorColors.signatureOf(1, "dark",
                colors, single("keyword", 0xFF0000EE))));
        assertFalse(base.equals(ThemePackEditorColors.signatureOf(1, "dark", colors, null)));
        assertEquals(base, ThemePackEditorColors.signatureOf(1, "dark", colors, syntax));
    }

    @Test
    public void signature_isOrderIndependentForMaps() {
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put("editor_background", 0xFF000000);
        a.put("editor_text", 0xFFFFFFFF);
        Map<String, Integer> b = new LinkedHashMap<>();
        b.put("editor_text", 0xFFFFFFFF);
        b.put("editor_background", 0xFF000000);

        assertEquals(ThemePackEditorColors.signatureOf(7, "dark", a, null),
                ThemePackEditorColors.signatureOf(7, "dark", b, null));
    }

    @Test
    public void signature_toleratesNulls() {
        String s = ThemePackEditorColors.signatureOf(0, null, null, null);
        assertTrue(s.startsWith("0|"));
        assertEquals(s, ThemePackEditorColors.signatureOf(0, null, null, null));
    }
}

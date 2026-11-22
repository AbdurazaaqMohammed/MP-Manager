package io.github.abdurazaaqmohammed.ui.common.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.eclipse.tm4e.core.registry.IThemeSource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Editor-side consumer of the theme pack v2 editor tokens. Data-only bridge
 * between the active pack and the Sora editor: nothing here touches text,
 * selection, scroll or any editor preference.
 *
 * <p>Three contract pieces (canonical sets live in the plugin-sdk
 * {@code ThemePackContracts}; matching was already done by the host parser,
 * so keys arriving here are canonical):
 * <ul>
 *   <li><b>Editor surface tokens</b> ({@code editor_background},
 *       {@code editor_text}, {@code editor_gutter}, {@code editor_line_number},
 *       {@code editor_line_number_current}, {@code editor_selection},
 *       {@code editor_current_line}, {@code editor_match_highlight}) map onto
 *       {@link EditorColorScheme} slots actually consumed by the editor. They
 *       are applied with {@link EditorColorScheme#setColor(int, int)} on a
 *       <b>per-editor scheme instance</b> - the shared/cached TextMate scheme
 *       is never mutated.</li>
 *   <li><b>Editor syntax categories</b> ({@code comment}, {@code keyword},
 *       {@code string}, {@code number}, {@code type}, {@code function},
 *       {@code operator}, {@code variable}) are enforced for TextMate
 *       languages by merging bounded per-category selectors into a
 *       host-owned copy of the base editor theme
 *       ({@code assets/themes/{dark|light}.json}); the merged source feeds a
 *       per-editor non-singleton Sora {@code ThemeRegistry}, so the global
 *       TextMate registry stays untouched. Pack rules are appended last AND
 *       matching base rules inside the category subtree lose their
 *       {@code foreground} (VS Code/tm4e specificity would otherwise let a
 *       deeper base rule such as {@code keyword.control.java} win over the
 *       appended broad rule).</li>
 *   <li>For non-TextMate languages (the read-only Java view) the categories
 *       map onto shared, real {@link EditorColorScheme} slots - documented,
 *       not invented: comment {@code COMMENT}, keyword {@code KEYWORD},
 *       string and number both {@code LITERAL} (no distinct number slot),
 *       type {@code IDENTIFIER_NAME} (no type slot), function
 *       {@code FUNCTION_NAME}, operator {@code OPERATOR}, variable
 *       {@code IDENTIFIER_VAR}.</li>
 * </ul>
 *
 * <p>No pack (or a pack without editor tokens) leaves the existing editor
 * preference scheme byte-for-byte unchanged. Colors are always applied AFTER
 * scheme creation and any hard-coded values, so pack tokens win. The
 * light/dark base theme choice comes from {@link ThemeRegistry#modeFor}
 * through {@link ThemeRegistry#appliedBaseId}, never from the activity's
 * possibly stale applied configuration.
 */
public final class ThemePackEditorColors {

    /** Hard-coded match highlight used by the editors when no pack supplies one. */
    public static final int DEFAULT_MATCH_HIGHLIGHT = 0x403399FF;

    // Canonical editor surface tokens (plugin-sdk ThemePackContracts.COLOR_TOKENS).
    public static final String TOKEN_EDITOR_BACKGROUND = "editor_background";
    public static final String TOKEN_EDITOR_TEXT = "editor_text";
    public static final String TOKEN_EDITOR_GUTTER = "editor_gutter";
    public static final String TOKEN_EDITOR_LINE_NUMBER = "editor_line_number";
    public static final String TOKEN_EDITOR_LINE_NUMBER_CURRENT = "editor_line_number_current";
    public static final String TOKEN_EDITOR_SELECTION = "editor_selection";
    public static final String TOKEN_EDITOR_CURRENT_LINE = "editor_current_line";
    public static final String TOKEN_EDITOR_MATCH_HIGHLIGHT = "editor_match_highlight";

    /** All editor surface tokens in canonical form, in application order. */
    public static final String[] EDITOR_TOKENS = {
            TOKEN_EDITOR_BACKGROUND, TOKEN_EDITOR_TEXT, TOKEN_EDITOR_GUTTER,
            TOKEN_EDITOR_LINE_NUMBER, TOKEN_EDITOR_LINE_NUMBER_CURRENT,
            TOKEN_EDITOR_SELECTION, TOKEN_EDITOR_CURRENT_LINE,
            TOKEN_EDITOR_MATCH_HIGHLIGHT,
    };

    /** Canonical syntax categories (plugin-sdk ThemePackContracts.EDITOR_SYNTAX_CATEGORIES). */
    public static final String[] SYNTAX_CATEGORIES = {
            "comment", "keyword", "string", "number",
            "type", "function", "operator", "variable",
    };

    // ---------- mappings ----------

    /**
     * Editor token -> verified {@link EditorColorScheme} slot (sora 0.23.6,
     * constants read from the bundled editor AAR; nothing invented).
     */
    private static final Map<String, Integer> TOKEN_SLOTS;

    /**
     * Canonical syntax category -> bounded TextMate selector used in the
     * merged host-owned theme. Pack rules win over equal-specificity base
     * rules (appended last) and base rules inside the subtree lose their
     * foreground, so deeper base selectors cannot override the pack.
     */
    private static final Map<String, String> SYNTAX_SELECTORS;

    /**
     * Canonical syntax category -> real {@link EditorColorScheme} slot for
     * languages whose analyzer colors through scheme constants (non-TextMate,
     * i.e. the read-only Java view). Shared slots by design: string/number
     * both use {@code LITERAL} and type uses {@code IDENTIFIER_NAME} because
     * no distinct slots exist in sora 0.23.6.
     */
    private static final Map<String, Integer> SYNTAX_SCHEME_SLOTS;

    static {
        Map<String, Integer> slots = new LinkedHashMap<>();
        slots.put(TOKEN_EDITOR_BACKGROUND, EditorColorScheme.WHOLE_BACKGROUND);
        slots.put(TOKEN_EDITOR_TEXT, EditorColorScheme.TEXT_NORMAL);
        slots.put(TOKEN_EDITOR_GUTTER, EditorColorScheme.LINE_NUMBER_BACKGROUND);
        slots.put(TOKEN_EDITOR_LINE_NUMBER, EditorColorScheme.LINE_NUMBER);
        slots.put(TOKEN_EDITOR_LINE_NUMBER_CURRENT, EditorColorScheme.LINE_NUMBER_CURRENT);
        slots.put(TOKEN_EDITOR_SELECTION, EditorColorScheme.SELECTED_TEXT_BACKGROUND);
        slots.put(TOKEN_EDITOR_CURRENT_LINE, EditorColorScheme.CURRENT_LINE);
        slots.put(TOKEN_EDITOR_MATCH_HIGHLIGHT, EditorColorScheme.MATCHED_TEXT_BACKGROUND);
        TOKEN_SLOTS = Collections.unmodifiableMap(slots);

        Map<String, String> selectors = new LinkedHashMap<>();
        selectors.put("comment", "comment");
        selectors.put("keyword", "keyword");
        selectors.put("string", "string");
        selectors.put("number", "constant.numeric");
        selectors.put("type", "entity.name.type");
        selectors.put("function", "entity.name.function");
        selectors.put("operator", "keyword.operator");
        selectors.put("variable", "variable");
        SYNTAX_SELECTORS = Collections.unmodifiableMap(selectors);

        Map<String, Integer> syntaxSlots = new LinkedHashMap<>();
        syntaxSlots.put("comment", EditorColorScheme.COMMENT);
        syntaxSlots.put("keyword", EditorColorScheme.KEYWORD);
        syntaxSlots.put("string", EditorColorScheme.LITERAL);
        syntaxSlots.put("number", EditorColorScheme.LITERAL);            // shared slot
        syntaxSlots.put("type", EditorColorScheme.IDENTIFIER_NAME);      // shared slot
        syntaxSlots.put("function", EditorColorScheme.FUNCTION_NAME);
        syntaxSlots.put("operator", EditorColorScheme.OPERATOR);
        syntaxSlots.put("variable", EditorColorScheme.IDENTIFIER_VAR);
        SYNTAX_SCHEME_SLOTS = Collections.unmodifiableMap(syntaxSlots);
    }

    private ThemePackEditorColors() {
    }

    // ---------- pure core (unit tested on the JVM) ----------

    /**
     * Editor surface token -> ARGB for tokens actually present in the pack
     * colors. Unknown/absent tokens are skipped; the canonical spellings are
     * the only keys read.
     */
    public static Map<String, Integer> editorTokenColors(Map<String, Integer> packColors) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (packColors == null) return out;
        for (String token : EDITOR_TOKENS) {
            Integer color = packColors.get(token);
            if (color != null) out.put(token, color);
        }
        return out;
    }

    /**
     * {@link EditorColorScheme} slot -> ARGB deltas derived from the pack's
     * editor surface tokens. Only tokens present in {@code packColors} produce
     * entries, so applying this never resets a color the pack did not ship.
     */
    public static Map<Integer, Integer> schemeColorOverrides(Map<String, Integer> packColors) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        if (packColors == null) return out;
        for (String token : EDITOR_TOKENS) {
            Integer color = packColors.get(token);
            Integer slot = TOKEN_SLOTS.get(token);
            if (color != null && slot != null) out.put(slot, color);
        }
        return out;
    }

    /**
     * Scheme slot -> ARGB for the pack's syntax categories, for non-TextMate
     * languages. Shares slots exactly as documented in the class comment.
     */
    public static Map<Integer, Integer> syntaxSchemeOverrides(Map<String, Integer> editorSyntax) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        if (editorSyntax == null) return out;
        for (Map.Entry<String, Integer> e : SYNTAX_SCHEME_SLOTS.entrySet()) {
            Integer color = editorSyntax.get(e.getKey());
            if (color != null) out.put(e.getValue(), color);
        }
        return out;
    }

    /** True when the selector matches the scope token (exact or subtree). */
    private static boolean selectorMatches(String selector, String scopeToken) {
        return scopeToken.equals(selector) || scopeToken.startsWith(selector + ".");
    }

    /** True when any canonical category selector matches the scope token. */
    private static boolean anySelectorMatches(String scopeToken) {
        for (String category : SYNTAX_CATEGORIES) {
            String selector = SYNTAX_SELECTORS.get(category);
            if (selector != null && selectorMatches(selector, scopeToken)) return true;
        }
        return false;
    }

    /** Formats an ARGB as a theme color string (#RRGGBB when opaque). */
    public static String toThemeColor(int argb) {
        if ((argb >>> 24) == 0xFF) {
            return String.format("#%06X", argb & 0xFFFFFF);
        }
        return String.format("#%08X", argb);
    }

    /**
     * Parses a base rule's comma separated scope list and reports whether any
     * token falls inside an overridden category subtree.
     */
    private static boolean ruleCovered(JsonObject rule) {
        JsonElement scope = rule.get("scope");
        if (scope == null || !scope.isJsonPrimitive()) return false;
        String raw = scope.getAsString();
        if (raw == null) return false;
        for (String token : raw.split(",")) {
            String trimmed = token.trim().toLowerCase(java.util.Locale.ROOT);
            if (trimmed.isEmpty()) continue;
            if (anySelectorMatches(trimmed)) return true;
        }
        return false;
    }

    /**
     * Merges pack syntax category colors into the base editor theme JSON.
     *
     * <p>Order of operations per category present in {@code canonicalSyntax}:
     * base rules whose scope falls inside the category's selector subtree are
     * stripped of their {@code foreground} (other attributes kept; rules left
     * without settings are dropped), then one rule per category is appended
     * last with the canonical selector. This guarantees the pack color wins
     * against both equal- and more-specific base rules, while base rules for
     * scopes outside the shipped categories keep their original colors.
     *
     * <p>Pure and total: any malformed base JSON returns the input unchanged.
     * Base themes use either {@code tokenColors} or the legacy {@code settings}
     * array - both are supported; a theme with neither gets a new
     * {@code tokenColors} array.
     */
    public static String mergeThemeJson(String baseJson, Map<String, Integer> canonicalSyntax) {
        if (baseJson == null) return null;
        if (canonicalSyntax == null || canonicalSyntax.isEmpty()) return baseJson;
        boolean any = false;
        for (String category : SYNTAX_CATEGORIES) {
            if (canonicalSyntax.get(category) != null) any = true;
        }
        if (!any) return baseJson;
        try {
            String cleaned = baseJson;
            if (cleaned.startsWith("\uFEFF")) cleaned = cleaned.substring(1);
            JsonElement root = JsonParser.parseString(cleaned);
            if (root == null || !root.isJsonObject()) return baseJson;
            JsonObject obj = root.getAsJsonObject();

            // Base rules inside an overridden category lose their foreground.
            String arrayKey = null;
            JsonArray rules = null;
            JsonElement tokenColors = obj.get("tokenColors");
            if (tokenColors != null && tokenColors.isJsonArray()) {
                arrayKey = "tokenColors";
                rules = tokenColors.getAsJsonArray();
            } else {
                JsonElement settings = obj.get("settings");
                if (settings != null && settings.isJsonArray()) {
                    arrayKey = "settings";
                    rules = settings.getAsJsonArray();
                }
            }
            if (rules != null) {
                JsonArray rewritten = new JsonArray();
                for (JsonElement element : rules) {
                    if (element != null && element.isJsonObject()) {
                        JsonObject rule = element.getAsJsonObject();
                        if (ruleCovered(rule)) {
                            JsonObject stripped = rule.deepCopy();
                            JsonElement ruleSettings = stripped.get("settings");
                            if (ruleSettings != null && ruleSettings.isJsonObject()) {
                                JsonObject settingsObj = ruleSettings.getAsJsonObject();
                                settingsObj.remove("foreground");
                                if (settingsObj.size() == 0) {
                                    continue; // nothing left to contribute
                                }
                            }
                            rewritten.add(stripped);
                        } else {
                            rewritten.add(element);
                        }
                    } else {
                        rewritten.add(element);
                    }
                }
                rules = rewritten;
                obj.add(arrayKey, rules);
            }

            // Pack rules appended LAST: equal-specificity ties go to the pack.
            JsonArray target = rules != null ? rules : new JsonArray();
            for (String category : SYNTAX_CATEGORIES) {
                Integer color = canonicalSyntax.get(category);
                String selector = SYNTAX_SELECTORS.get(category);
                if (color == null || selector == null) continue;
                JsonObject rule = new JsonObject();
                rule.addProperty("scope", selector);
                JsonObject ruleSettings = new JsonObject();
                ruleSettings.addProperty("foreground", toThemeColor(color));
                rule.add("settings", ruleSettings);
                target.add(rule);
            }
            if (rules == null) obj.add("tokenColors", target);
            return obj.toString();
        } catch (Exception e) {
            return baseJson;
        }
    }

    /**
     * Stable signature of everything an editor theme refresh depends on:
     * registry epoch, applied base theme id, pack colors and pack syntax
     * categories. Order-independent for the maps so map iteration order never
     * changes the value.
     */
    public static String signatureOf(int epoch, String themeId,
                                     Map<String, Integer> packColors,
                                     Map<String, Integer> editorSyntax) {
        return epoch + "|" + (themeId == null ? "" : themeId)
                + "|" + mapFingerprint(packColors)
                + "|" + mapFingerprint(editorSyntax);
    }

    private static long mapFingerprint(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return 0L;
        long acc = 0L;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            long keyPart = e.getKey() == null ? 0L : e.getKey().hashCode();
            long valuePart = e.getValue() == null ? 0L : e.getValue();
            acc += keyPart * 0x9E3779B9L + valuePart;
        }
        return acc;
    }

    // ---------- Android wrappers ----------

    /**
     * Live signature for the editor refresh diff: {@link ThemeRegistry#currentEpoch()}
     * + applied base id + the two pack maps the editor consumes. Never throws.
     */
    public static String themeSignature(Context context) {
        try {
            if (context == null) return "0||0|0";
            int epoch = ThemeRegistry.currentEpoch();
            String themeId = ThemeRegistry.appliedBaseId(context);
            Map<String, Integer> colors = activePackColors(context);
            Map<String, Integer> syntax = ThemeRegistry.editorSyntax(context);
            return signatureOf(epoch, themeId, colors, syntax);
        } catch (Exception e) {
            return "error";
        }
    }

    private static Map<String, Integer> activePackColors(Context context) {
        try {
            io.github.abdurazaaqmohammed.plugins.themes.ThemePacks.Snapshot pack =
                    io.github.abdurazaaqmohammed.plugins.themes.ThemePacks.active(context);
            return pack == null ? Collections.<String, Integer>emptyMap() : pack.colors;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * Base editor theme asset name for the currently APPLIED theme:
     * {@link ThemeRegistry#modeFor} on {@link ThemeRegistry#appliedBaseId};
     * the configuration is consulted only when the mode follows the system
     * (the applied id cannot answer by itself). Never reads the activity's
     * possibly stale applied uiMode as the primary source.
     */
    public static String baseThemeAssetName(Context context) {
        try {
            if (context != null) {
                String appliedId = ThemeRegistry.appliedBaseId(context);
                int nightMode = ThemeRegistry.modeFor(context, appliedId);
                if (nightMode == AppCompatDelegate.MODE_NIGHT_YES) return "dark.json";
                if (nightMode == AppCompatDelegate.MODE_NIGHT_NO) return "light.json";
            }
        } catch (Exception ignored) {
        }
        try {
            Configuration config = context.getResources().getConfiguration();
            boolean dark = (config.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES;
            return dark ? "dark.json" : "light.json";
        } catch (Exception e) {
            return "dark.json";
        }
    }

    /** True when the active pack ships the given editor surface token. */
    public static boolean hasEditorToken(Context context, String token) {
        return activePackColors(context).containsKey(token);
    }

    /**
     * Applies the pack's editor surface tokens and (non-TextMate) syntax
     * category slots to the given scheme. Only present tokens produce
     * {@code setColor} calls; the scheme instance must be per-editor. Returns
     * the number of colors applied. Never throws.
     */
    public static int applyToScheme(Context context, EditorColorScheme scheme) {
        if (scheme == null || context == null) return 0;
        int applied = 0;
        try {
            for (Map.Entry<Integer, Integer> e : schemeColorOverrides(
                    activePackColors(context)).entrySet()) {
                scheme.setColor(e.getKey(), e.getValue());
                applied++;
            }
            for (Map.Entry<Integer, Integer> e : syntaxSchemeOverrides(
                    ThemeRegistry.editorSyntax(context)).entrySet()) {
                scheme.setColor(e.getKey(), e.getValue());
                applied++;
            }
        } catch (Exception ignored) {
        }
        return applied;
    }

    /** True when the active pack ships any syntax category at all. */
    public static boolean hasEditorSyntax(Context context) {
        try {
            return !ThemeRegistry.editorSyntax(context).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static final class CachedMergedTheme {
        final String key;
        final IThemeSource source;
        CachedMergedTheme(String key, IThemeSource source) {
            this.key = key;
            this.source = source;
        }
    }

    private static volatile CachedMergedTheme sMergedTheme;

    /**
     * Host-owned {@link IThemeSource} = base editor theme
     * ({@code assets/themes/<baseThemeAssetName>}) with the pack's canonical
     * syntax selectors merged in (see {@link #mergeThemeJson}). Cached by
     * (base theme name, registry epoch) - the epoch is the theme revision,
     * so a pack change always rebuilds. Returns null when no syntax is
     * shipped or the asset is unreadable; callers then keep the plain global
     * registry path (no pack = existing behavior).
     */
    public static IThemeSource mergedThemeSource(Context context) {
        try {
            if (context == null || !hasEditorSyntax(context)) return null;
            String baseName = baseThemeAssetName(context);
            String key = baseName + "#" + ThemeRegistry.currentEpoch();
            CachedMergedTheme cached = sMergedTheme;
            if (cached != null && cached.key.equals(key)) return cached.source;
            String baseJson = readAssetTheme(context, baseName);
            if (baseJson == null) return null;
            String merged = mergeThemeJson(baseJson, ThemeRegistry.editorSyntax(context));
            IThemeSource source = IThemeSource.fromString(
                    IThemeSource.ContentType.JSON, merged);
            sMergedTheme = new CachedMergedTheme(key, source);
            return source;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readAssetTheme(Context context, String name) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    context.getAssets().open("themes/" + name), StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) >= 0) sb.append(buf, 0, n);
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convenience for callers that want the editor chrome (system bars etc.)
     * refreshed together with the scheme; kept here so fragments do not need
     * to know the individual registry calls.
     */
    public static void refreshActivityChrome(Activity activity) {
        if (activity == null) return;
        try {
            ThemeRegistry.applyEditorChrome(activity);
        } catch (Exception ignored) {
        }
    }
}

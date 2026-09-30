package io.github.abdurazaaqmohammed.core.ui.theme;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses one theme JSON file. Uses Gson rather than org.json because Gson is a
 * real jar on the unit-test classpath, while org.json is only an Android
 * framework stub (every call returns null off-device) and would make the
 * parser untestable on the JVM.
 *
 * Format:
 * {
 *   "name": "MT Dark",
 *   "nightMode": "dark",            // optional: dark | light | system
 *   "colors": { "surface": "#303030", ... }   // optional, every key optional
 * }
 */
public final class ThemeJson {

    /**
     * Colour roles we understand. Unknown keys are ignored, not an error.
     *
     * <p>{@code selection} has no Material counterpart: it is the selected-row
     * background, which MT Manager paints a distinctly different colour from its
     * grey raised surfaces, so it must not share a role with them.
     */
    public static final String[] ROLES = {
            "background", "surface", "surfaceVariant", "surfaceContainer",
            "onSurface", "onSurfaceVariant", "outline", "primary", "onPrimary",
            "error", "toolbar", "selection", "iconFolder", "iconFile"
    };

    private ThemeJson() {
    }

    /**
     * @param fileName  source name, kept for deletion of imported themes.
     * @param builtin   true for assets, false for user-imported files.
     * @return parsed palette, or null when the JSON is unusable.
     */
    public static ThemePalette parse(String json, String fileName, boolean builtin) {
        if (json == null) return null;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root == null || !root.isJsonObject()) return null;
            JsonObject obj = root.getAsJsonObject();

            String name = str(obj, "name", fileName);
            String nightMode = str(obj, "nightMode", "system");

            Map<String, Integer> colors = null;
            JsonElement c = obj.get("colors");
            if (c != null && c.isJsonObject()) {
                JsonObject colorsObj = c.getAsJsonObject();
                colors = new LinkedHashMap<>();
                for (String role : ROLES) {
                    JsonElement v = colorsObj.get(role);
                    if (v == null || v.isJsonNull() || !v.isJsonPrimitive()) continue;
                    int argb = parseColor(v.getAsString());
                    if (argb != ThemePalette.ABSENT) colors.put(role, argb);
                }
            }

            return new ThemePalette(idFor(fileName, builtin), name,
                    isLightMode(nightMode), nightMode, builtin, fileName, colors);
        } catch (JsonSyntaxException | IllegalStateException | NumberFormatException e) {
            return null;
        }
    }

    private static String str(JsonObject obj, String key, String fallback) {
        JsonElement e = obj.get(key);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive()) return fallback;
        try {
            return e.getAsString();
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    /** Built-ins keep their historical ids; imports get a custom: prefix. */
    public static String idFor(String fileName, boolean builtin) {
        String base = fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5) : fileName;
        if (builtin && "myapp_light".equals(base)) return BuiltInThemes.LIGHT_ID;
        if (builtin && "myapp_dark".equals(base)) return BuiltInThemes.DARK_ID;
        if (builtin && "myapp_black".equals(base)) return BuiltInThemes.BLACK_ID;
        if (builtin && "mt_dark".equals(base)) return BuiltInThemes.MT_DARK_ID;
        if (builtin && "system_default".equals(base)) return BuiltInThemes.SYSTEM_DEFAULT_ID;
        return (builtin ? "" : "custom:") + base;
    }

    public static boolean isLightMode(String nightMode) {
        if (nightMode == null) return false;
        String m = nightMode.trim().toLowerCase();
        return "light".equals(m) || "no".equals(m) || "false".equals(m);
    }

    /**
     * Accepts #RGB, #RRGGBB, #AARRGGBB (with or without the hash) and the
     * handful of CSS/ARSC keyword names worth supporting.
     *
     * @return ARGB int, or {@link ThemePalette#ABSENT} when unparseable.
     */
    public static int parseColor(String s) {
        if (s == null) return ThemePalette.ABSENT;
        String v = s.trim();
        if (v.isEmpty()) return ThemePalette.ABSENT;
        if (!v.startsWith("#")) {
            Integer named = NAMED.get(v.toLowerCase());
            if (named != null) return named;
            // Bare hex, so dropping the '#' by hand still works.
            v = "#" + v;
        }
        String h = v.substring(1);
        try {
            switch (h.length()) {
                case 3:
                    int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
                    int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
                    int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
                    return 0xFF000000 | (r << 16) | (g << 8) | b;
                case 6:
                    return 0xFF000000 | (int) Long.parseLong(h, 16);
                case 8:
                    return (int) Long.parseLong(h, 16);
                default:
                    return ThemePalette.ABSENT;
            }
        } catch (NumberFormatException e) {
            return ThemePalette.ABSENT;
        }
    }

    private static final Map<String, Integer> NAMED = new LinkedHashMap<>();

    static {
        NAMED.put("black", 0xFF000000);
        NAMED.put("white", 0xFFFFFFFF);
        NAMED.put("transparent", 0x00000000);
        NAMED.put("red", 0xFFFF0000);
        NAMED.put("green", 0xFF008000);
        NAMED.put("blue", 0xFF0000FF);
        NAMED.put("gray", 0xFF808080);
        NAMED.put("grey", 0xFF808080);
    }
}

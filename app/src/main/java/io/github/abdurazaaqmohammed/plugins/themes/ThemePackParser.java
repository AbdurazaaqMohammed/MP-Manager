package io.github.abdurazaaqmohammed.plugins.themes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure, defensive parser for the fixed {@code /theme} row payload
 * (see {@link ThemePackContracts}). No Android imports so it is unit
 * testable on the JVM. Unknown tokens/keys are dropped silently; malformed
 * required data raises {@link ParseException} with a short reason.
 */
public final class ThemePackParser {

    private ThemePackParser() {
    }

    /** Failed a required-field or structural validation. */
    public static final class ParseException extends IllegalArgumentException {
        public ParseException(String message) {
            super(message);
        }
    }

    // normalized token -> canonical spelling
    private static final Map<String, String> COLOR_CANONICAL = canonical(ThemePackContracts.COLOR_TOKENS);
    private static final Map<String, String> LABEL_CANONICAL = canonical(ThemePackContracts.LABEL_KEYS);

    private static Map<String, String> canonical(String[] keys) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String key : keys) map.put(normalize(key), key);
        return map;
    }

    /** Case/separator-insensitive form used for all allowlist matching. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '_' || c == '-' || c == '.' || c == ' ') continue;
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** True when the raw name matches an allowlisted color token. */
    public static boolean isColorToken(String raw) {
        return COLOR_CANONICAL.containsKey(normalize(raw));
    }

    /** Canonical allowlisted spelling for a color token, or null. */
    public static String canonicalColorToken(String raw) {
        return COLOR_CANONICAL.get(normalize(raw));
    }

    /** Canonical allowlisted spelling for a label key, or null. */
    public static String canonicalLabelKey(String raw) {
        return LABEL_CANONICAL.get(normalize(raw));
    }

    // ---------- scalar columns ----------

    /** "#AARRGGBB" / "#RRGGBB" (alpha FF) / numeric ARGB; null when invalid. */
    public static Integer parseColorValue(Object raw) {
        try {
            if (raw == null) return null;
            if (raw instanceof Number) return (int) (((Number) raw).longValue() & 0xFFFFFFFFL);
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) return null;
            if (s.charAt(0) == '#') s = s.substring(1);
            long v;
            if (s.length() == 8) {
                v = Long.parseLong(s, 16);
            } else if (s.length() == 6) {
                v = Long.parseLong(s, 16) | 0xFF000000L;
            } else {
                return null;
            }
            return (int) v;
        } catch (Exception e) {
            return null;
        }
    }

    /** INTEGER 0/1 (string or number); null when absent, null when unparseable. */
    public static Boolean parseDark(Object raw) {
        try {
            if (raw == null) return null;
            if (raw instanceof Number) {
                long v = ((Number) raw).longValue();
                return v == 0 ? Boolean.FALSE : (v == 1 ? Boolean.TRUE : null);
            }
            String s = String.valueOf(raw).trim();
            if (s.equals("1") || s.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (s.equals("0") || s.equalsIgnoreCase("false")) return Boolean.FALSE;
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Required pack id: 1..100 chars of [A-Za-z0-9._-], trimmed. */
    public static String parseId(String raw) {
        if (raw == null) throw new ParseException("Missing id");
        String id = sanitizeText(raw);
        if (id.isEmpty()) throw new ParseException("Missing id");
        if (id.length() > ThemePackContracts.MAX_TEXT_CHARS) {
            throw new ParseException("Id too long");
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (!ok) throw new ParseException("Id has invalid characters");
        }
        return id;
    }

    /** Optional display name: trimmed, control chars stripped, <=100 chars. */
    public static String parseName(String raw) {
        String name = sanitizeText(raw);
        if (name.length() > ThemePackContracts.MAX_TEXT_CHARS) {
            name = name.substring(0, ThemePackContracts.MAX_TEXT_CHARS);
        }
        return name.isEmpty() ? null : name;
    }

    // ---------- JSON columns ----------

    /**
     * colors column: {@code {"colorPrimary":"#FF6750A4", ...}}. Unknown
     * tokens and unparseable colors are dropped. Malformed JSON or over-limit
     * payload throws {@link ParseException}.
     */
    public static Map<String, Integer> parseColors(String json) {
        if (json == null || json.trim().isEmpty()) return new LinkedHashMap<>();
        if (json.length() > ThemePackContracts.MAX_COLORS_JSON_CHARS) {
            throw new ParseException("colors payload too large");
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        JsonObject obj = object(json, "colors");
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String canonical = canonicalColorToken(e.getKey());
            if (canonical == null || e.getValue() == null) continue;
            JsonPrimitive p;
            try {
                p = e.getValue().getAsJsonPrimitive();
            } catch (Exception ignored) {
                continue;
            }
            Integer argb = parseColorValue(p.isBoolean() ? null
                    : p.isNumber() ? (Number) p.getAsNumber() : p.getAsString());
            if (argb != null) out.put(canonical, argb);
        }
        return out;
    }

    /**
     * strings column: {@code {"tab.files":{"en-US":"Files","zh-CN":"..."}, ...}}.
     * Unknown keys, non-string texts, overlong texts, malformed locale tags
     * and extra locales past the per-label cap are dropped. Malformed JSON or
     * over-limit payload throws {@link ParseException}.
     */
    public static Map<String, Map<String, String>> parseStrings(String json) {
        if (json == null || json.trim().isEmpty()) return new LinkedHashMap<>();
        if (json.length() > ThemePackContracts.MAX_STRINGS_JSON_CHARS) {
            throw new ParseException("strings payload too large");
        }
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        JsonObject obj = object(json, "strings");
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            String canonical = canonicalLabelKey(e.getKey());
            if (canonical == null || e.getValue() == null
                    || !e.getValue().isJsonObject()) {
                continue;
            }
            Map<String, String> locales = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> le : e.getValue().getAsJsonObject().entrySet()) {
                if (locales.size() >= ThemePackContracts.MAX_LOCALES_PER_LABEL) break;
                String tag = sanitizeLocale(le.getKey());
                if (tag == null || le.getValue() == null) continue;
                JsonPrimitive p;
                try {
                    p = le.getValue().getAsJsonPrimitive();
                } catch (Exception ignored) {
                    continue;
                }
                if (p.isBoolean() || p.isNumber()) continue;
                String text = sanitizeText(p.getAsString());
                if (text.isEmpty()) continue;
                if (text.length() > ThemePackContracts.MAX_TEXT_CHARS) {
                    text = text.substring(0, ThemePackContracts.MAX_TEXT_CHARS);
                }
                locales.put(tag, text);
            }
            if (!locales.isEmpty()) out.put(canonical, locales);
        }
        return out;
    }

    // ---------- helpers ----------

    private static JsonObject object(String json, String column) {
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root != null && root.isJsonObject()) return root.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ParseException(column + " is not valid JSON");
        }
        throw new ParseException(column + " is not a JSON object");
    }

    /** Trims and strips control characters. Never null. */
    public static String sanitizeText(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c)) continue;
            sb.append(c);
        }
        return sb.toString().trim();
    }

    /** BCP47-ish tag: [A-Za-z0-9]{1,8}(-[A-Za-z0-9]{1,8})*, 1..35 chars. */
    public static String sanitizeLocale(String raw) {
        if (raw == null) return null;
        String tag = raw.trim();
        if (tag.isEmpty() || tag.length() > 35) return null;
        String[] parts = tag.split("-");
        if (parts.length == 0 || parts.length > 6) return null;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 8) return null;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9');
                if (!ok) return null;
            }
        }
        try {
            // Reject tags java.util.Locale cannot round-trip.
            String languageTag = new Locale.Builder().setLanguageTag(tag).build()
                    .toLanguageTag();
            return languageTag.isEmpty() ? null : languageTag;
        } catch (Exception e) {
            return null;
        }
    }
}

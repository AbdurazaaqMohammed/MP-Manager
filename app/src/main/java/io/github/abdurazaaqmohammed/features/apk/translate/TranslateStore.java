package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the translation screen remembers between launches: chosen languages, chosen
 * engine and the online-endpoint settings. Kept in the default preference file, the same
 * store {@code autosign} and the signing keys live in.
 */
public final class TranslateStore {

    public static final String ENGINE_MANUAL = "manual";
    public static final String ENGINE_GLOSSARY = "glossary";
    public static final String ENGINE_ONLINE = "online";

    /** Free endpoint used when the user has not configured one. */
    public static final String DEFAULT_ENDPOINT =
            "https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s";

    private static final String P_LAST_CONFIG = "xlate_last_config";
    private static final String P_SOURCE_BCP47 = "xlate_source_bcp47";
    private static final String P_COPIED_FROM = "xlate_copied_from";
    private static final String P_ENGINE = "xlate_engine";
    private static final String P_ENDPOINT = "xlate_endpoint";
    private static final String P_API_KEY = "xlate_api_key";
    private static final String P_SKIP_FORMATTED = "xlate_skip_formatted";
    private static final String P_BATCH = "xlate_batch";

    private TranslateStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** The config the screen opens on, i.e. the language pack being worked on. */
    public static String lastConfig(Context context) {
        return Locales.normalize(prefs(context).getString(P_LAST_CONFIG, "zh-rCN"));
    }

    public static void setLastConfig(Context context, String qualifier) {
        prefs(context).edit().putString(P_LAST_CONFIG, Locales.normalize(qualifier)).apply();
    }

    /**
     * Language the values currently in the config are written in.
     *
     * <p>Only a fallback: when a config was produced by copying another one, the copied-from
     * config already answers this and {@link #copiedFrom} is consulted first.
     */
    public static String sourceBcp47(Context context) {
        return prefs(context).getString(P_SOURCE_BCP47, "en");
    }

    public static void setSourceBcp47(Context context, String bcp47) {
        prefs(context).edit().putString(P_SOURCE_BCP47, bcp47 == null ? "en" : bcp47).apply();
    }

    /**
     * Remembers which config each created config was copied from, so the glossary and the online
     * endpoint know the language they are reading.
     */
    public static Map<String, String> copiedFrom(Context context) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            String saved = prefs(context).getString(P_COPIED_FROM, null);
            if (saved == null || saved.isEmpty()) return out;
            JSONObject json = new JSONObject(saved);
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = json.optString(key, "");
                if (!key.isEmpty() && !value.isEmpty()) out.put(key, value);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void setCopiedFrom(Context context, Map<String, String> map) {
        // Stored as JSON so one preference key holds the whole map.
        try {
            JSONObject json = new JSONObject();
            if (map != null) {
                for (Map.Entry<String, String> e : map.entrySet()) {
                    json.put(e.getKey(), e.getValue());
                }
            }
            prefs(context).edit().putString(P_COPIED_FROM, json.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static String engine(Context context) {
        return prefs(context).getString(P_ENGINE, ENGINE_MANUAL);
    }

    public static void setEngine(Context context, String engine) {
        prefs(context).edit().putString(P_ENGINE, engine).apply();
    }

    public static String endpoint(Context context) {
        String value = prefs(context).getString(P_ENDPOINT, "");
        return value == null || value.trim().isEmpty() ? DEFAULT_ENDPOINT : value.trim();
    }

    public static void setEndpoint(Context context, String endpoint) {
        prefs(context).edit().putString(P_ENDPOINT, endpoint == null ? "" : endpoint.trim()).apply();
    }

    public static String apiKey(Context context) {
        String value = prefs(context).getString(P_API_KEY, "");
        return value == null ? "" : value.trim();
    }

    public static void setApiKey(Context context, String key) {
        prefs(context).edit().putString(P_API_KEY, key == null ? "" : key.trim()).apply();
    }

    /**
     * Whether to skip values that look like markup. Translation engines routinely corrupt
     * {@code %1$s}, {@code {0}} and inline HTML, so this defaults to on.
     */
    public static boolean skipFormatted(Context context) {
        return prefs(context).getBoolean(P_SKIP_FORMATTED, true);
    }

    public static void setSkipFormatted(Context context, boolean value) {
        prefs(context).edit().putBoolean(P_SKIP_FORMATTED, value).apply();
    }

    /** Whether the online engine may put several strings in one request. */
    public static boolean allowBatch(Context context) {
        return prefs(context).getBoolean(P_BATCH, true);
    }

    public static void setAllowBatch(Context context, boolean value) {
        prefs(context).edit().putBoolean(P_BATCH, value).apply();
    }

    /** The glossary pair key, e.g. {@code en-zh-cn}. */
    public static String pairFor(String sourceBcp47, String targetBcp47) {
        String s = sourceBcp47 == null || sourceBcp47.isEmpty() ? "en" : sourceBcp47;
        String t = targetBcp47 == null || targetBcp47.isEmpty() ? "en" : targetBcp47;
        return s.toLowerCase() + "-" + t.toLowerCase();
    }
}

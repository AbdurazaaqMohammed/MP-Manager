package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

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

    private static final String P_SOURCE_QUALIFIER = "xlate_source_qualifier";
    private static final String P_TARGET_QUALIFIER = "xlate_target_qualifier";
    private static final String P_ENGINE = "xlate_engine";
    private static final String P_ENDPOINT = "xlate_endpoint";
    private static final String P_API_KEY = "xlate_api_key";
    private static final String P_ONLY_MISSING = "xlate_only_missing";
    private static final String P_SKIP_FORMATTED = "xlate_skip_formatted";
    private static final String P_BATCH = "xlate_batch";

    private TranslateStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static String sourceQualifier(Context context) {
        return Locales.normalize(prefs(context).getString(P_SOURCE_QUALIFIER, ""));
    }

    public static void setSourceQualifier(Context context, String qualifier) {
        prefs(context).edit().putString(P_SOURCE_QUALIFIER, Locales.normalize(qualifier)).apply();
    }

    public static String targetQualifier(Context context) {
        return Locales.normalize(prefs(context).getString(P_TARGET_QUALIFIER, "zh-rCN"));
    }

    public static void setTargetQualifier(Context context, String qualifier) {
        prefs(context).edit().putString(P_TARGET_QUALIFIER, Locales.normalize(qualifier)).apply();
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

    public static boolean onlyMissing(Context context) {
        return prefs(context).getBoolean(P_ONLY_MISSING, true);
    }

    public static void setOnlyMissing(Context context, boolean value) {
        prefs(context).edit().putBoolean(P_ONLY_MISSING, value).apply();
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

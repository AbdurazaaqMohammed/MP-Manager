package modder.hub.dexeditor.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Translation API client for the MT-style translation mode. Supports several
 * providers the user can pick from (each with its own API key / endpoint):
 *
 * - LibreTranslate (self-hostable; some instances need a key)
 * - DeepL API (free + pro endpoints)
 * - Google Cloud Translation v2
 * - Any OpenAI-compatible chat completions endpoint (OpenAI, local LLM
 *   servers, aggregators)
 *
 * All requests are synchronous; callers run them on background threads.
 * Batching: DeepL/Google/LibreTranslate take arrays natively, OpenAI gets a
 * small JSON-array prompt.
 */
public final class AutoTranslateClient {

    public static final int PROVIDER_LIBRE = 0;
    public static final int PROVIDER_DEEPL_FREE = 1;
    public static final int PROVIDER_DEEPL_PRO = 2;
    public static final int PROVIDER_GOOGLE = 3;
    public static final int PROVIDER_OPENAI = 4;
    public static final int PROVIDER_COUNT = 5;

    public static final String[] PROVIDER_NAMES = {
            "LibreTranslate",
            "DeepL API (free)",
            "DeepL API (pro)",
            "Google Cloud Translate",
            "OpenAI-compatible",
    };

    public static final String[] PROVIDER_DEFAULT_ENDPOINTS = {
            "https://libretranslate.com/translate",
            "https://api-free.deepl.com/v2/translate",
            "https://api.deepl.com/v2/translate",
            "https://translation.googleapis.com/language/translate/v2",
            "https://api.openai.com/v1/chat/completions",
    };

    public static final int[] PROVIDER_BATCH_SIZES = {25, 50, 50, 100, 10};

    private AutoTranslateClient() {
    }

    public static String defaultEndpoint(int provider) {
        if (provider < 0 || provider >= PROVIDER_DEFAULT_ENDPOINTS.length) return "";
        return PROVIDER_DEFAULT_ENDPOINTS[provider];
    }

    public static int batchSize(int provider) {
        if (provider < 0 || provider >= PROVIDER_BATCH_SIZES.length) return 20;
        return PROVIDER_BATCH_SIZES[provider];
    }

    private static OkHttpClient client() {
        return new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Translates {@code texts} in order. {@code target} is a language code
     * (e.g. "en", "ru"); source is always auto-detect.
     *
     * @return the translations in the same order
     * @throws Exception on network/HTTP/parsing failure with a readable message
     */
    public static List<String> translate(int provider, String endpoint, String apiKey,
                                         String model, String target, List<String> texts)
            throws Exception {
        List<String> out = new ArrayList<>(texts.size());
        int chunk = batchSize(provider);
        for (int start = 0; start < texts.size(); start += chunk) {
            List<String> part = texts.subList(start, Math.min(start + chunk, texts.size()));
            out.addAll(translateChunk(provider, endpoint, apiKey, model, target, part));
        }
        return out;
    }

    private static List<String> translateChunk(int provider, String endpoint, String apiKey,
                                               String model, String target, List<String> texts)
            throws Exception {
        JSONObject body = new JSONObject();
        switch (provider) {
            case PROVIDER_LIBRE: {
                JSONArray q = new JSONArray();
                for (String t : texts) q.put(t);
                body.put("q", q);
                body.put("source", "auto");
                body.put("target", target);
                body.put("format", "text");
                if (apiKey != null && !apiKey.isEmpty()) body.put("api_key", apiKey);
                JSONArray out = postJson(endpoint, body, null);
                List<String> result = new ArrayList<>();
                for (int i = 0; i < out.length(); i++) {
                    result.add(out.getJSONObject(i).optString("translatedText", texts.get(i)));
                }
                return result;
            }
            case PROVIDER_DEEPL_FREE:
            case PROVIDER_DEEPL_PRO: {
                JSONArray q = new JSONArray();
                for (String t : texts) q.put(t);
                body.put("text", q);
                body.put("target_lang", target.toUpperCase());
                okhttp3.Headers headers = okhttp3.Headers.of("Authorization", "DeepL-Auth-Key " + apiKey);
                JSONObject response = new JSONObject(postJsonRaw(endpoint, body, headers, null));
                JSONArray translations = response.getJSONArray("translations");
                List<String> result = new ArrayList<>();
                for (int i = 0; i < translations.length(); i++) {
                    result.add(translations.getJSONObject(i).optString("text", texts.get(i)));
                }
                return result;
            }
            case PROVIDER_GOOGLE: {
                JSONArray q = new JSONArray();
                for (String t : texts) q.put(t);
                body.put("q", q);
                body.put("target", target);
                body.put("format", "text");
                String url = endpoint + (endpoint.contains("?") ? "&" : "?") + "key=" + apiKey;
                JSONObject response = new JSONObject(postJsonRaw(url, body, null, null));
                JSONArray translations = response.getJSONObject("data").getJSONArray("translations");
                List<String> result = new ArrayList<>();
                for (int i = 0; i < translations.length(); i++) {
                    result.add(translations.getJSONObject(i).optString("translatedText", texts.get(i)));
                }
                return result;
            }
            case PROVIDER_OPENAI: {
                JSONArray stringsJson = new JSONArray();
                for (String t : texts) stringsJson.put(t);
                body.put("model", model == null || model.isEmpty() ? "gpt-4o-mini" : model);
                body.put("temperature", 0);
                body.put("response_format", new JSONObject("{\"type\":\"json_object\"}"));
                JSONArray messages = new JSONArray();
                messages.put(new JSONObject().put("role", "system").put("content",
                        "You are a translation engine. Translate every string in the user's JSON array "
                                + "to the language with code '" + target + "'. Respond with JSON: "
                                + "{\"translations\": [\"...\"]} keeping the same order and count. "
                                + "Do not add commentary. Preserve formatting and placeholders."));
                messages.put(new JSONObject().put("role", "user").put("content", stringsJson.toString()));
                body.put("messages", messages);
                okhttp3.Headers headers = okhttp3.Headers.of("Authorization", "Bearer " + apiKey);
                JSONObject response = new JSONObject(postJsonRaw(endpoint, body, headers, null));
                String content = response.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content", "");
                String trimmed = content.trim();
                if (trimmed.startsWith("```")) {
                    trimmed = trimmed.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
                }
                JSONObject parsed = new JSONObject(trimmed);
                JSONArray translations = parsed.optJSONArray("translations");
                if (translations == null) {
                    throw new Exception("Translation service returned unexpected JSON");
                }
                List<String> result = new ArrayList<>();
                for (int i = 0; i < translations.length() && i < texts.size(); i++) {
                    result.add(translations.optString(i, texts.get(i)));
                }
                while (result.size() < texts.size()) result.add(texts.get(result.size()));
                return result;
            }
            default:
                throw new Exception("Unknown translation provider");
        }
    }

    private static JSONArray postJson(String url, JSONObject body,
                                      okhttp3.Headers headers) throws Exception {
        return new JSONArray(postJsonRaw(url, body, headers, null));
    }

    private static String postJsonRaw(String url, JSONObject body,
                                      okhttp3.Headers headers, Integer timeoutOverride) throws Exception {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);
        OkHttpClient client = builder.build();
        Request.Builder request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")));
        if (headers != null) request.headers(headers);
        try (Response response = client.newCall(request.build()).execute()) {
            String text = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                String detail = text;
                try {
                    JSONObject json = new JSONObject(text);
                    if (json.has("error") && json.getJSONObject("error").has("message")) {
                        detail = json.getJSONObject("error").optString("message");
                    } else if (json.has("error")) {
                        detail = String.valueOf(json.get("error"));
                    } else if (json.has("message")) {
                        detail = json.optString("message");
                    }
                } catch (Exception ignored) {
                }
                throw new Exception("HTTP " + response.code() + (detail.isEmpty() ? "" : ": " + detail));
            }
            return text;
        }
    }
}

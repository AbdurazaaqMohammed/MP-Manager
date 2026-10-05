package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Online engine: posts strings to a translation endpoint over HTTP.
 *
 * <p>The default endpoint is the key-less {@code translate.googleapis.com} single-request API,
 * which is what most open-source Android translators use. Users running their own instance (or a
 * paid provider) point {@code xlate_endpoint} at it instead; the placeholder order is fixed and
 * documented in {@link TranslateStore#DEFAULT_ENDPOINT}.
 *
 * <p>Batching joins strings with a newline and splits the answer back apart. That is the only
 * separator every engine tested preserves, and when the split does not line up the batch is
 * retried one string at a time rather than risking scrambled output.
 */
public final class OnlineEngine implements TranslationEngine {

    private static final String TAG = "OnlineEngine";
    private static final int TIMEOUT_MS = 20000;
    private static final int MAX_BATCH = 20;
    private static final String BATCH_SEPARATOR = "\n";

    private final Context context;

    public OnlineEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public String id() {
        return TranslateStore.ENGINE_ONLINE;
    }

    @Override
    public boolean needsNetwork() {
        return true;
    }

    @Override
    public boolean supportsBatch() {
        return true;
    }

    @Override
    public void translate(Context ctx, List<TranslateRow> rows, String sourceTag, String targetTag,
                          Callback callback) throws Exception {
        Context app = ctx != null ? ctx : context;
        String endpoint = TranslateStore.endpoint(app);
        String apiKey = TranslateStore.apiKey(app);
        boolean batch = TranslateStore.allowBatch(app) && supportsBatch();

        // Report everything the engine will not touch first, so the UI can grey those rows out
        // immediately instead of waiting for the whole run.
        List<TranslateRow> pending = new ArrayList<>();
        for (TranslateRow row : rows) {
            if (eligible(row)) {
                pending.add(row);
            } else {
                callback.onSkipped(row, row.isSelected() ? "kept" : "unselected");
            }
        }

        List<List<TranslateRow>> groups = new ArrayList<>();
        if (batch) {
            List<TranslateRow> current = new ArrayList<>();
            for (TranslateRow row : pending) {
                current.add(row);
                if (current.size() >= MAX_BATCH) {
                    groups.add(current);
                    current = new ArrayList<>();
                }
            }
            if (!current.isEmpty()) groups.add(current);
        } else {
            for (TranslateRow row : pending) groups.add(List.of(row));
        }

        int done = 0;
        for (List<TranslateRow> group : groups) {
            String answer = null;
            Exception failure = null;
            try {
                List<String> payload = new ArrayList<>(group.size());
                for (TranslateRow row : group) payload.add(FormatGuard.mask(row.source));
                answer = request(endpoint, apiKey, sourceTag, targetTag, payload);
            } catch (Exception e) {
                failure = e;
            }

            List<String> parts = answer == null ? null : splitAnswer(answer, group.size());
            if (parts == null && group.size() > 1) {
                // Batching is an optimisation only. If the answer cannot be split back apart,
                // redo the group one string per request rather than risk scrambled output.
                callback.onMessage(answer == null ? "retry" : "batch-miss");
                answer = null;
            }

            if (answer != null) {
                for (int i = 0; i < group.size(); i++) {
                    TranslateRow row = group.get(i);
                    accept(row, parts == null ? answer : parts.get(i), callback);
                    done++;
                    callback.onProgress(done, rows.size());
                }
                continue;
            }

            for (TranslateRow row : group) {
                try {
                    String single = request(endpoint, apiKey, sourceTag, targetTag,
                            List.of(FormatGuard.mask(row.source)));
                    accept(row, single, callback);
                } catch (Exception e) {
                    Log.d(TAG, "Request failed", e);
                    callback.onSkipped(row, e.getMessage() == null ? "error" : e.getMessage());
                }
                done++;
                callback.onProgress(done, rows.size());
            }
            if (failure != null && group.size() == 1) {
                Log.d(TAG, "Single request failed", failure);
            }
        }
    }

    /** Rows the engine is allowed to overwrite: selected, untranslated and not already staged. */
    private static boolean eligible(TranslateRow row) {
        if (!row.isSelected()) return false;
        return row.getOrigin() == TranslateRow.Origin.NONE || row.getTranslation().trim().isEmpty();
    }

    /**
     * Stores a translation unless the engine mangled a protected token. Getting this wrong
     * produces an APK that crashes on the first screen, so a violation means "skip", never
     * "write it anyway".
     */
    private void accept(TranslateRow row, String answer, Callback callback) {
        String restored = FormatGuard.unmask(answer, row.source);
        if (restored == null || restored.trim().isEmpty()) {
            callback.onSkipped(row, "empty");
            return;
        }
        String violation = FormatGuard.firstViolation(row.source, restored);
        if (violation != null) {
            Log.d(TAG, "Rejected translation of " + row.key + ", lost token " + violation);
            // Silence would look like the engine ignored the string, so say why it was dropped.
            callback.onSkipped(row, "token:" + violation);
            return;
        }
        row.setTranslation(restored, TranslateRow.Origin.ONLINE);
        callback.onTranslated(row);
    }

    /** @return one answer per input, or {@code null} when the split did not line up. */
    private static List<String> splitAnswer(String answer, int expected) {
        if (expected <= 1) return null;
        String[] parts = answer.split("\r?\n", -1);
        List<String> out = new ArrayList<>(expected);
        for (String part : parts) {
            if (!part.trim().isEmpty()) out.add(part);
        }
        if (out.size() != expected) return null;
        return out;
    }

    /**
     * Performs one request.
     *
     * <p>Both request shapes are supported: an endpoint template with {@code %s} placeholders,
     * and a bare URL that takes the query string appended to it. The API key, when set, is sent
     * both as a query parameter and as a bearer header, which covers the common provider styles.
     */
    private String request(String endpoint, String apiKey, String sourceTag, String targetTag,
                           List<String> texts) throws IOException {
        String joined = String.join(BATCH_SEPARATOR, texts);
        String url;
        if (endpoint.contains("%s")) {
            url = String.format(endpoint,
                    enc(sourceTag), enc(targetTag), enc(joined));
        } else {
            StringBuilder sb = new StringBuilder(endpoint);
            sb.append(endpoint.contains("?") ? '&' : '?');
            sb.append("sl=").append(enc(sourceTag));
            sb.append("&tl=").append(enc(targetTag));
            sb.append("&dt=t");
            sb.append("&q=").append(enc(joined));
            url = sb.toString();
        }
        if (!apiKey.isEmpty()) {
            url += (url.contains("?") ? "&" : "?") + "key=" + enc(apiKey);
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (!apiKey.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            try (InputStream in = connection.getInputStream()) {
                return parse(readAll(in));
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Understands the two shapes in the wild: the Google {@code translate_a/single} reply, which
     * nests the text one array deep, and a bare JSON string or a plain {@code ["a","b"]} array.
     */
    static String parse(String body) throws IOException {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.isEmpty()) throw new IOException("Empty response");
        try {
            JSONArray root = new JSONArray(trimmed);
            JSONArray segments = root.optJSONArray(0);
            if (segments != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < segments.length(); i++) {
                    JSONArray segment = segments.optJSONArray(i);
                    if (segment == null) continue;
                    sb.append(segment.optString(0, ""));
                }
                if (sb.length() > 0) return sb.toString();
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < root.length(); i++) {
                if (i > 0) sb.append(BATCH_SEPARATOR);
                sb.append(root.optString(i, ""));
            }
            return sb.toString();
        } catch (Exception e) {
            if (trimmed.charAt(0) == '"' || trimmed.charAt(0) == '{') {
                try {
                    return new org.json.JSONObject(trimmed).optString("translatedText",
                            new org.json.JSONObject(trimmed).optString("translation", trimmed));
                } catch (Exception ignored) {
                }
            }
            return trimmed;
        }
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }
}

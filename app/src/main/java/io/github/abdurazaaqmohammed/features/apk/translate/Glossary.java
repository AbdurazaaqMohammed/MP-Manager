package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Two-level offline glossary: whole-phrase hits first, then word-level hits.
 *
 * <p>Dictionaries are plain JSON {@code {"pair": "zh-en", "entries": {"Cancel": "取消"}}} and are
 * merged from two places, the user's own file always winning:
 * <ol>
 *   <li>{@code assets/glossary/<pair>.json} - shipped with the app</li>
 *   <li>{@code filesDir/glossary/<pair>.json} - edited/imported by the user</li>
 * </ol>
 */
public final class Glossary {

    private static final String TAG = "Glossary";
    private static final String ASSET_DIR = "glossary/";

    /** Longest phrase the word-level pass will consider, in characters. */
    private static final int MAX_WORD = 24;

    /** Result of a single lookup, so the UI can explain where a translation came from. */
    public static final class Hit {
        public final String text;
        public final boolean phrase;
        public final boolean exact;

        Hit(String text, boolean phrase, boolean exact) {
            this.text = text;
            this.phrase = phrase;
            this.exact = exact;
        }
    }

    public final String pair;
    /** Lower-cased source phrase -> translation. */
    private final Map<String, String> phrases = new LinkedHashMap<>();
    /** Single token -> translation. */
    private final Map<String, String> words = new LinkedHashMap<>();
    /** Folded key -> the casing the author used, so exports stay readable. */
    private final Map<String, String> originalKeys = new LinkedHashMap<>();

    private Glossary(String pair) {
        this.pair = pair;
    }

    /** File name used both for the asset and the user override. */
    public static String fileName(String pair) {
        String safe = pair.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe + ".json";
    }

    public static File userFile(Context context, String pair) {
        return new File(new File(context.getFilesDir(), "glossary"), fileName(pair));
    }

    public static Glossary load(Context context, String pair) {
        Glossary glossary = new Glossary(pair == null ? "" : pair.toLowerCase(Locale.ROOT));
        glossary.readAsset(context, glossary.pair);
        glossary.readUser(context, glossary.pair);
        return glossary;
    }

    private void readAsset(Context context, String pair) {
        if (pair.isEmpty()) return;
        String asset = ASSET_DIR + fileName(pair);
        // Missing asset is normal; malformed JSON is not, but neither is worth crashing over:
        // the engine simply falls back to whatever the user's own file provides.
        try (InputStream in = context.getAssets().open(asset)) {
            read(in);
        } catch (Exception e) {
            Log.d(TAG, "No usable bundled glossary for " + pair + ": " + e.getMessage());
        }
    }

    private void readUser(Context context, String pair) {
        File file = userFile(context, pair);
        if (!file.isFile()) return;
        try (InputStream in = new java.io.FileInputStream(file)) {
            read(in);
        } catch (Exception e) {
            Log.w(TAG, "Bad user glossary " + file, e);
        }
    }

    private void read(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
        JSONObject root;
        try {
            root = new JSONObject(new String(buffer.toByteArray(), StandardCharsets.UTF_8));
        } catch (JSONException e) {
            // org.json throws a checked exception, so it has to become an IOException for the
            // two callers to handle with the rest of the stream failures.
            throw new IOException("Glossary is not valid JSON", e);
        }
        JSONObject entries = root.optJSONObject("entries");
        if (entries == null) return;
        java.util.Iterator<String> it = entries.keys();
        while (it.hasNext()) {
            String key = it.next();
            String value = entries.optString(key, "");
            if (key.isEmpty() || value.isEmpty()) continue;
            put(key, value);
        }
    }

    private void put(String key, String value) {
        String k = key.toLowerCase(Locale.ROOT);
        phrases.put(k, value);
        originalKeys.put(k, key);
        for (String token : tokenize(k)) {
            if (!words.containsKey(token)) words.put(token, value);
        }
    }

    public int phraseCount() {
        return phrases.size();
    }

    public int wordCount() {
        return words.size();
    }

    /** @return an exact whole-string match, or {@code null}. */
    public Hit exact(String source) {
        if (source == null) return null;
        String value = phrases.get(source.trim().toLowerCase(Locale.ROOT));
        return value == null ? null : new Hit(value, true, true);
    }

    /**
     * Best-effort translation without touching the network.
     *
     * @return the translated text and how it was produced, or {@code null} when the glossary
     *         knows nothing usable about the input.
     */
    public Hit lookup(String source) {
        if (source == null || source.trim().isEmpty()) return null;
        Hit exact = exact(source);
        if (exact != null) return exact;

        // Word-level pass: keep every character we do not recognise so placeholders,
        // markup and untranslated fragments survive untouched.
        String lower = source.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        boolean anyWord = false;
        while (i < lower.length()) {
            String matched = null;
            if (isWordStart(lower, i)) {
                int limit = Math.min(lower.length(), i + MAX_WORD);
                for (int end = limit; end > i; end--) {
                    String candidate = lower.substring(i, end);
                    if (!isWordEnd(lower, end) && end != lower.length()) continue;
                    if (words.containsKey(candidate)) {
                        matched = candidate;
                        break;
                    }
                }
            }
            if (matched == null) {
                char c = source.charAt(i);
                out.append(c);
                plain.append(c);
                i++;
                continue;
            }
            String translated = words.get(matched);
            out.append(translated);
            plain.append(matched);
            anyWord = true;
            i += matched.length();
        }
        if (!anyWord) return null;
        // Nothing actually changed, so there is no point offering it as a translation.
        if (plain.toString().contentEquals(out)) return null;
        return new Hit(out.toString(), false, false);
    }

    private static boolean isWordStart(String s, int i) {
        char c = s.charAt(i);
        return Character.isLetter(c);
    }

    private static boolean isWordEnd(String s, int end) {
        if (end >= s.length()) return true;
        char c = s.charAt(end);
        return !Character.isLetter(c);
    }

    /** Splits on anything that is not a letter, so digits and punctuation stay tokens too. */
    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                current.append(c);
            } else if (current.length() > 0) {
                out.add(current.toString());
                current.setLength(0);
            }
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    /** Serialises the merged dictionary back out so it can be shared or backed up. */
    public String toJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("pair", pair);
            JSONObject entries = new JSONObject();
            for (Map.Entry<String, String> e : phrases.entrySet()) {
                // Keys were folded to lower case, export the original casing when we have it.
                entries.put(originalKey(e.getKey()), e.getValue());
            }
            root.put("entries", entries);
            return root.toString(2);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String originalKey(String folded) {
        String original = originalKeys.get(folded);
        return original == null ? folded : original;
    }

    /** Creates a user glossary pre-filled from a bundled one, ready to be edited. */
    public static File seedUserFile(Context context, String pair) throws IOException {
        File file = userFile(context, pair);
        if (file.exists()) return file;
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("Cannot create " + dir);
        }
        Glossary bundled = new Glossary(pair == null ? "" : pair.toLowerCase(Locale.ROOT));
        bundled.readAsset(context, bundled.pair);
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
            out.write(bundled.toJson().getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /** Accepts either {@code {"entries":{...}}} or a bare flat object. */
    public static List<String> validate(String json) throws Exception {
        List<String> problems = new ArrayList<>();
        JSONObject entries;
        try {
            JSONObject root = new JSONObject(json);
            JSONObject nested = root.optJSONObject("entries");
            entries = nested != null ? nested : root;
        } catch (Exception e) {
            throw new IOException("Not valid JSON");
        }
        java.util.Iterator<String> keys = entries.keys();
        int count = 0;
        while (keys.hasNext()) {
            String key = keys.next();
            count++;
            if (entries.isNull(key)) {
                problems.add(key);
                continue;
            }
            if (!(entries.opt(key) instanceof String)) problems.add(key);
        }
        if (count == 0) problems.add("(empty)");
        return problems;
    }
}

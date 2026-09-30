package io.github.abdurazaaqmohammed.domain.text;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Lenient JSON value handling extracted from ToolRunnerActivity.
 */
public final class Json {

    private Json() {
    }

    public static Object parse(String s) throws Exception {
        String t = s == null ? "" : s.trim();
        if (t.startsWith("{")) {
            return new JSONObject(t);
        }
        if (t.startsWith("[")) {
            return new JSONArray(t);
        }
        JSONTokener tokener = new JSONTokener(t);
        Object v = tokener.nextValue();
        while (tokener.more()) {
            char c = tokener.next();
            if (c != 0 && !Character.isWhitespace(c)) {
                throw new JSONException("Trailing data");
            }
        }
        return v;
    }

    public static String format(Object parsed) throws Exception {
        if (parsed instanceof JSONObject) {
            return ((JSONObject) parsed).toString(2);
        } else if (parsed instanceof JSONArray) {
            return ((JSONArray) parsed).toString(2);
        } else {
            return String.valueOf(parsed);
        }
    }

    public static String minify(Object parsed) throws Exception {
        if (parsed instanceof JSONObject) {
            return parsed.toString();
        } else if (parsed instanceof JSONArray) {
            return parsed.toString();
        } else {
            return String.valueOf(parsed);
        }
    }
}

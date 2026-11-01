package io.github.abdurazaaqmohammed.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

/** Small JSON helpers shared by the MCP protocol and tool implementations. */
public final class McpJson {

    private McpJson() {
    }

    /** Parses a body that may be a single request or a batch (array). Null on garbage. */
    public static JsonElement[] parseArrayOrObject(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JsonElement el = JsonParser.parseString(body);
            if (el.isJsonArray()) {
                JsonArray arr = el.getAsJsonArray();
                JsonElement[] out = new JsonElement[arr.size()];
                for (int i = 0; i < arr.size(); i++) out[i] = arr.get(i);
                return out;
            }
            if (el.isJsonObject()) return new JsonElement[]{el};
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonObject obj(String... kv) {
        JsonObject obj = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            obj.addProperty(kv[i], kv[i + 1]);
        }
        return obj;
    }

    public static JsonObject error(int code, String message) {
        JsonObject err = new JsonObject();
        err.addProperty("code", code);
        err.addProperty("message", message);
        return err;
    }

    public static JsonObject errorResponse(JsonElement id, int code, String message) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? JsonNull.INSTANCE : id);
        response.add("error", error(code, message));
        return response;
    }

    public static JsonObject resultResponse(JsonElement id, JsonObject result) {
        JsonObject response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id == null ? JsonNull.INSTANCE : id);
        response.add("result", result);
        return response;
    }

    public static String str(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String reqStr(JsonObject obj, String key) throws McpToolException {
        String value = str(obj, key, null);
        if (value == null) {
            throw new McpToolException("Missing required parameter: " + key);
        }
        return value;
    }

    public static boolean bool(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static int intVal(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static long longVal(JsonObject obj, String key, long fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return fallback;
        try {
            return obj.get(key).getAsLong();
        } catch (Exception e) {
            return fallback;
        }
    }

    public static JsonArray array(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray()) return new JsonArray();
        return obj.getAsJsonArray(key);
    }

    public static JsonObject objectArg(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || !obj.get(key).isJsonObject()) return null;
        return obj.getAsJsonObject(key);
    }

    /** text content block */
    public static JsonObject textContent(String text) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", text);
        return block;
    }

    /** Standard MCP result with a single text block (pretty JSON for structured data). */
    public static JsonObject textResult(JsonElement payload) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        content.add(textContent(payload.toString()));
        result.add("content", content);
        return result;
    }

    public static JsonObject textResult(String text) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        content.add(textContent(text));
        result.add("content", content);
        return result;
    }

    public static JsonObject errorResult(String message) {
        JsonObject result = new JsonObject();
        JsonArray content = new JsonArray();
        content.add(textContent(message));
        result.add("content", content);
        result.addProperty("isError", true);
        return result;
    }

    public static final class McpToolException extends Exception {
        public McpToolException(String message) {
            super(message);
        }
    }
}

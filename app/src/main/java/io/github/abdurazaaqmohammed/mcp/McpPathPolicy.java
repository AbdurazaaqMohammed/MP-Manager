package io.github.abdurazaaqmohammed.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Path access control for MCP tools, modeled after MT Manager: the home folder
 * always has read/write access; everywhere else is governed by an ordered rule
 * list where the first matching rule wins. Rules are managed by the user in the
 * MCP settings UI; the AI can only query them, never elevate its own access.
 *
 * Defaults when no rules match: read-only under the primary external storage
 * root (so the AI can analyze APKs out of the box), deny everywhere else.
 */
public final class McpPathPolicy {

    public static final String MODE_RW = "rw";
    public static final String MODE_RO = "ro";
    public static final String MODE_DENY = "deny";

    public static class Rule {
        public final String path;
        public final String mode;

        public Rule(String path, String mode) {
            this.path = path;
            this.mode = mode;
        }
    }

    private final File home;
    private final List<Rule> rules;
    private final String externalRoot;

    public McpPathPolicy(File home, String rulesJson) {
        this.home = normalize(home);
        this.rules = parseRules(rulesJson);
        String ext = null;
        try {
            ext = android.os.Environment.getExternalStorageDirectory().getCanonicalPath();
        } catch (Exception ignored) {
        }
        this.externalRoot = ext;
    }

    private static List<Rule> parseRules(String json) {
        List<Rule> out = new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json == null ? "[]" : json).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                String path = obj.has("path") ? obj.get("path").getAsString() : null;
                String mode = obj.has("mode") ? obj.get("mode").getAsString() : MODE_DENY;
                if (path != null && !path.isEmpty()) out.add(new Rule(path, mode));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static File normalize(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    public static String normalizePath(String path, File home) {
        if (path == null || path.trim().isEmpty()) return null;
        path = path.trim();
        if (path.equals("~") || path.equals("~/")) return home.getPath();
        if (path.startsWith("~/")) path = new File(home, path.substring(2)).getPath();
        return path;
    }

    /** Resolves a user path against home when relative, then canonicalizes it. */
    public File resolve(String path) {
        File f = new File(path);
        if (!f.isAbsolute()) f = new File(home, path);
        return normalize(f);
    }

    private static boolean isUnder(String path, String dir) {
        if (dir == null) return false;
        if (path.equals(dir)) return true;
        return path.startsWith(dir.endsWith(File.separator) ? dir : dir + File.separator);
    }

    /** @return the matched mode, or null when no rule matched. */
    public String matchedMode(String canonical) {
        if (isUnder(canonical, home.getPath())) return MODE_RW;
        for (Rule rule : rules) {
            File base = new File(rule.path);
            if (!base.isAbsolute()) base = new File(home, rule.path);
            if (isUnder(canonical, normalize(base).getPath())) {
                return rule.mode;
            }
        }
        return null;
    }

    public boolean canRead(String canonical) {
        String mode = matchedMode(canonical);
        if (mode != null) return !MODE_DENY.equals(mode);
        // Default policy: read-only inside primary external storage, except
        // Android/data and Android/obb (sandboxed), deny elsewhere.
        if (isUnder(canonical, externalRoot)) {
            return !isUnder(canonical, externalRoot + "/Android/data")
                    && !isUnder(canonical, externalRoot + "/Android/obb");
        }
        return false;
    }

    public boolean canWrite(String canonical) {
        return MODE_RW.equals(matchedMode(canonical));
    }

    public File getHome() {
        return home;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public static String rulesToJson(List<Rule> rules) {
        JsonArray arr = new JsonArray();
        for (Rule rule : rules) {
            JsonObject obj = new JsonObject();
            obj.addProperty("path", rule.path);
            obj.addProperty("mode", rule.mode);
            arr.add(obj);
        }
        return new Gson().toJson(arr);
    }
}

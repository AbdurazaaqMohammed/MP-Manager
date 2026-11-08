package io.github.abdurazaaqmohammed.features.renpy;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * High-performance, robust bridge for reading and patching Ren'Py save and persistent files.
 */
public final class RenpySaveEngine {

    private RenpySaveEngine() {}

    public static class RenpyVariable implements Serializable {
        public final String rawKey;
        public final String displayName;
        public final String type;
        public Object value;

        public RenpyVariable(String rawKey, String displayName, String type, Object value) {
            this.rawKey = rawKey;
            this.displayName = displayName;
            this.type = type;
            this.value = value;
        }

        public String getDisplayValue() {
            if (value == null) return "None";
            return String.valueOf(value);
        }
    }

    public static class ParseResult {
        public final boolean isSave;
        public final List<RenpyVariable> variables;

        public ParseResult(boolean isSave, List<RenpyVariable> variables) {
            this.isSave = isSave;
            this.variables = variables;
        }
    }

    private static File ensureScriptExtracted(Context context) throws Exception {
        File dir = new File(context.getFilesDir(), "scripts");
        if (!dir.exists()) dir.mkdirs();
        File scriptFile = new File(dir, "renpy_tool.py");
        if (!scriptFile.exists() || scriptFile.length() == 0) {
            try (InputStream in = context.getAssets().open("scripts/renpy_tool.py");
                 OutputStream out = new FileOutputStream(scriptFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }
            scriptFile.setExecutable(true);
        }
        return scriptFile;
    }

    public static ParseResult parse(Context context, File targetFile) throws Exception {
        File script = ensureScriptExtracted(context);
        ProcessBuilder pb = new ProcessBuilder("python3", script.getAbsolutePath(), "read", targetFile.getAbsolutePath());
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        int code = p.waitFor();
        String jsonStr = sb.toString().trim();
        if (code != 0 || !jsonStr.startsWith("{")) {
            throw new IllegalStateException("Failed to parse Ren'Py file: " + jsonStr);
        }

        JSONObject root = new JSONObject(jsonStr);
        if ("error".equals(root.optString("status"))) {
            throw new IllegalStateException(root.optString("message", "Unknown error"));
        }

        boolean isSave = root.optBoolean("is_save", false);
        JSONArray arr = root.getJSONArray("variables");
        List<RenpyVariable> vars = new ArrayList<>(arr.length());

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            String rawKey = obj.getString("raw_key");
            String display = obj.getString("display_name");
            String type = obj.getString("type");
            Object val = obj.isNull("value") ? null : obj.get("value");
            vars.add(new RenpyVariable(rawKey, display, type, val));
        }

        return new ParseResult(isSave, vars);
    }

    public static File saveWithBackup(Context context, File targetFile, Map<String, Object> modifications) throws Exception {
        File script = ensureScriptExtracted(context);

        JSONObject modJson = new JSONObject();
        for (Map.Entry<String, Object> entry : modifications.entrySet()) {
            modJson.put(entry.getKey(), entry.getValue());
        }

        File tmpModFile = File.createTempFile("renpy_mod_", ".json", context.getCacheDir());
        try (FileOutputStream fos = new FileOutputStream(tmpModFile)) {
            fos.write(modJson.toString().getBytes(StandardCharsets.UTF_8));
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("python3", script.getAbsolutePath(), "save",
                    targetFile.getAbsolutePath(), tmpModFile.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            int code = p.waitFor();
            String jsonStr = sb.toString().trim();
            if (code != 0 || !jsonStr.startsWith("{")) {
                throw new IllegalStateException("Failed to save Ren'Py file: " + jsonStr);
            }

            JSONObject root = new JSONObject(jsonStr);
            if ("error".equals(root.optString("status"))) {
                throw new IllegalStateException(root.optString("message", "Unknown error"));
            }

            return new File(root.getString("backup"));
        } finally {
            if (tmpModFile.exists()) {
                tmpModFile.delete();
            }
        }
    }
}

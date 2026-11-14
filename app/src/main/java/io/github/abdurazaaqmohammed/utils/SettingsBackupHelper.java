package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public final class SettingsBackupHelper {

    private static final String[] PREF_FILES = new String[]{
            "com.system.power_preferences",
            "set",
            "editor_prefs",
            "search_prefs",
            "advanced_symbol_prefs",
            "app_details_config",
            "dexPref"
    };

    private SettingsBackupHelper() {
    }

    public static String exportSettingsToJson(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", 1);
        root.put("timestamp", System.currentTimeMillis());

        JSONObject prefsObj = new JSONObject();
        for (String prefName : PREF_FILES) {
            SharedPreferences sp = prefName.equals("com.system.power_preferences")
                    ? PreferenceManager.getDefaultSharedPreferences(context)
                    : context.getSharedPreferences(prefName, Context.MODE_PRIVATE);

            Map<String, ?> all = sp.getAll();
            if (all.isEmpty()) continue;

            JSONObject singlePref = new JSONObject();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val == null) continue;

                JSONObject valWrapper = new JSONObject();
                if (val instanceof Boolean) {
                    valWrapper.put("t", "b");
                    valWrapper.put("v", val);
                } else if (val instanceof Integer) {
                    valWrapper.put("t", "i");
                    valWrapper.put("v", val);
                } else if (val instanceof Long) {
                    valWrapper.put("t", "l");
                    valWrapper.put("v", val);
                } else if (val instanceof Float) {
                    valWrapper.put("t", "f");
                    valWrapper.put("v", ((Float) val).doubleValue());
                } else if (val instanceof String) {
                    valWrapper.put("t", "s");
                    valWrapper.put("v", val);
                } else if (val instanceof Set) {
                    valWrapper.put("t", "ss");
                    JSONArray arr = new JSONArray();
                    for (Object item : (Set<?>) val) {
                        if (item != null) arr.put(item.toString());
                    }
                    valWrapper.put("v", arr);
                }
                singlePref.put(key, valWrapper);
            }
            prefsObj.put(prefName, singlePref);
        }
        root.put("shared_preferences", prefsObj);
        return root.toString(2);
    }

    public static void exportToFile(Context context, File targetFile) throws Exception {
        String json = exportSettingsToJson(context);
        try (OutputStream os = new FileOutputStream(targetFile)) {
            os.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static void importSettingsFromJson(Context context, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!root.has("shared_preferences")) {
            throw new IllegalArgumentException("Invalid backup format: missing shared_preferences");
        }

        JSONObject prefsObj = root.getJSONObject("shared_preferences");
        Iterator<String> prefKeys = prefsObj.keys();
        while (prefKeys.hasNext()) {
            String prefName = prefKeys.next();
            JSONObject singlePref = prefsObj.getJSONObject(prefName);

            SharedPreferences sp = prefName.equals("com.system.power_preferences")
                    ? PreferenceManager.getDefaultSharedPreferences(context)
                    : context.getSharedPreferences(prefName, Context.MODE_PRIVATE);

            SharedPreferences.Editor editor = sp.edit();
            Iterator<String> keys = singlePref.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject valWrapper = singlePref.getJSONObject(key);
                String type = valWrapper.getString("t");
                switch (type) {
                    case "b":
                        editor.putBoolean(key, valWrapper.getBoolean("v"));
                        break;
                    case "i":
                        editor.putInt(key, valWrapper.getInt("v"));
                        break;
                    case "l":
                        editor.putLong(key, valWrapper.getLong("v"));
                        break;
                    case "f":
                        editor.putFloat(key, (float) valWrapper.getDouble("v"));
                        break;
                    case "s":
                        editor.putString(key, valWrapper.getString("v"));
                        break;
                    case "ss":
                        JSONArray arr = valWrapper.getJSONArray("v");
                        Set<String> set = new HashSet<>();
                        for (int i = 0; i < arr.length(); i++) {
                            set.add(arr.getString(i));
                        }
                        editor.putStringSet(key, set);
                        break;
                }
            }
            editor.apply();
        }
    }

    public static void importFromFile(Context context, File sourceFile) throws Exception {
        byte[] bytes = new byte[(int) sourceFile.length()];
        try (InputStream is = new FileInputStream(sourceFile)) {
            int offset = 0;
            int numRead;
            while (offset < bytes.length && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
        }
        String json = new String(bytes, StandardCharsets.UTF_8);
        importSettingsFromJson(context, json);
    }
}

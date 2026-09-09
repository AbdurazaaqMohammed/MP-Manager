package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OverlayProfiles {

    private static final Gson GSON = new Gson();

    private OverlayProfiles() {
    }

    public static List<String> toastNames(Context context) {
        return new ArrayList<>(loadMap(context, "overlay_toast_profiles").keySet());
    }

    public static List<String> dialogNames(Context context) {
        return new ArrayList<>(loadMap(context, "overlay_dialog_profiles").keySet());
    }

    public static OverlayInjectorUtil.ToastOptions getToast(Context context, String name) {
        try {
            String json = loadMap(context, "overlay_toast_profiles").get(name);
            if (json == null) return null;
            return GSON.fromJson(json, OverlayInjectorUtil.ToastOptions.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static OverlayInjectorUtil.DialogOptions getDialog(Context context, String name) {
        try {
            String json = loadMap(context, "overlay_dialog_profiles").get(name);
            if (json == null) return null;
            return GSON.fromJson(json, OverlayInjectorUtil.DialogOptions.class);
        } catch (Exception e) {
            return null;
        }
    }

    public static void putToast(Context context, String name, OverlayInjectorUtil.ToastOptions opts) {
        Map<String, String> map = loadMap(context, "overlay_toast_profiles");
        map.put(name, GSON.toJson(opts));
        saveMap(context, "overlay_toast_profiles", map);
    }

    public static void putDialog(Context context, String name, OverlayInjectorUtil.DialogOptions opts) {
        Map<String, String> map = loadMap(context, "overlay_dialog_profiles");
        map.put(name, GSON.toJson(opts));
        saveMap(context, "overlay_dialog_profiles", map);
    }

    public static void removeToast(Context context, String name) {
        Map<String, String> map = loadMap(context, "overlay_toast_profiles");
        map.remove(name);
        saveMap(context, "overlay_toast_profiles", map);
    }

    public static void removeDialog(Context context, String name) {
        Map<String, String> map = loadMap(context, "overlay_dialog_profiles");
        map.remove(name);
        saveMap(context, "overlay_dialog_profiles", map);
    }

    private static Map<String, String> loadMap(Context context, String key) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String json = prefs.getString(key, "");
            if (json == null || json.isEmpty()) return new LinkedHashMap<>();
            Map<String, String> map = GSON.fromJson(json,
                    new TypeToken<LinkedHashMap<String, String>>() {
                    }.getType());
            return map == null ? new LinkedHashMap<>() : map;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static void saveMap(Context context, String key, Map<String, String> map) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(key, GSON.toJson(map)).apply();
        } catch (Exception ignored) {
        }
    }
}

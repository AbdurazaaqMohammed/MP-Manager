package io.github.abdurazaaqmohammed.plugins.res;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resources of loaded tool-pack APKs.
 *
 * <p>Packs ship their own {@code res/values/strings.xml} so each pack can be
 * translated independently without polluting the host resource table. The host
 * registers the pack's {@link Resources} here when the pack APK is loaded with
 * {@code DexClassLoader}; pack code then looks strings up by name or by its own
 * {@code R.string} id.
 *
 * <p>Loading uses the classic {@code AssetManager.addAssetPath} reflection. If
 * the platform refuses that call (non-SDK interface policy) the pack keeps its
 * English literals as a fallback, so tools still render.
 */
public final class PackRes {

    private static final Map<String, Resources> BY_ID = new LinkedHashMap<>();

    private PackRes() {
    }

    /** Build {@link Resources} for a pack APK, or null when unavailable. */
    public static Resources load(Context host, String apkPath) {
        if (host == null || apkPath == null) return null;
        try {
            AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            Object cookie = addAssetPath.invoke(assets, apkPath);
            if (cookie instanceof Integer && (Integer) cookie == 0) return null;
            Resources hostRes = host.getResources();
            return new Resources(assets, hostRes.getDisplayMetrics(), hostRes.getConfiguration());
        } catch (Throwable t) {
            return null;
        }
    }

    public static synchronized void register(String packId, Resources resources) {
        if (packId == null || resources == null) return;
        BY_ID.put(packId, resources);
    }

    public static synchronized void unregister(String packId) {
        if (packId != null) BY_ID.remove(packId);
    }

    public static synchronized Resources of(String packId) {
        return packId == null ? null : BY_ID.get(packId);
    }

    /** String by pack {@code R.string} id, or the fallback when not registered. */
    public static String str(String packId, int resId, String fallback) {
        Resources r = of(packId);
        if (r == null) return fallback;
        try {
            String s = r.getString(resId);
            return s != null ? s : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** String by resource name within one pack, or the fallback. */
    public static String str(String packId, String key, String fallback) {
        Resources r = of(packId);
        if (r == null) return fallback;
        try {
            int id = r.getIdentifier(key, "string", null);
            if (id == 0) return fallback;
            String s = r.getString(id);
            return s != null ? s : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /**
     * String by name across every loaded pack. Used for tool metadata, whose
     * keys ({@code title_<toolId>}) are unique because tool ids are unique.
     */
    public static synchronized String str(String key, String fallback) {
        if (key == null) return fallback;
        for (Resources r : BY_ID.values()) {
            try {
                int id = r.getIdentifier(key, "string", null);
                if (id != 0) {
                    String s = r.getString(id);
                    if (s != null) return s;
                }
            } catch (Throwable ignored) {
            }
        }
        return fallback;
    }
}

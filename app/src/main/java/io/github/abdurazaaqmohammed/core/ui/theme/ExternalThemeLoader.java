package io.github.abdurazaaqmohammed.core.ui.theme;

import android.content.Context;

import dalvik.system.DexClassLoader;

import java.io.File;

/**
 * Loads full ThemePlugin implementations from external APK/jar/dex files.
 * Convention: host copies the pack to internal storage, verifies it,
 * then calls loadExternal() with the plugin class name declared in theme.json.
 *
 * Example pack layout (zip/apk):
 *   theme.json      {"class": "com.example.theme.NeonTheme", "id": "neon"}
 *   classes.dex     compiled ThemePlugin implementation
 */
public final class ExternalThemeLoader {

    private ExternalThemeLoader() {
    }

    /**
     * @param apkOrDex external pack file (must exist, readable).
     * @param className fully-qualified ThemePlugin implementation class.
     * @return registered plugin, or null on failure.
     */
    public static ThemePlugin loadExternal(Context context, File apkOrDex, String className) {
        if (context == null || apkOrDex == null || className == null) return null;
        if (!apkOrDex.exists() || !apkOrDex.canRead()) return null;
        try {
            File codeCache = context.getDir("theme_code_cache", Context.MODE_PRIVATE);
            File optDir = new File(codeCache, "opt");
            if (!optDir.exists()) optDir.mkdirs();
            DexClassLoader loader = new DexClassLoader(
                    apkOrDex.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    null,
                    context.getClassLoader());
            Class<?> clazz = Class.forName(className, true, loader);
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (instance instanceof ThemePlugin) {
                ThemePlugin plugin = (ThemePlugin) instance;
                ThemeRegistry.register(plugin);
                return plugin;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

package io.github.abdurazaaqmohammed.plugins.ext;

import android.content.Context;

/**
 * Resolves host drawable names contributed by extensions (e.g. "tools_24px")
 * to resource ids. Pack APK resources are never used.
 */
public final class ExtensionIcons {

    private ExtensionIcons() {
    }

    public static int resId(Context context, String name, int fallback) {
        if (context == null || name == null || name.isEmpty()) return fallback;
        try {
            int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
            return id == 0 ? fallback : id;
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

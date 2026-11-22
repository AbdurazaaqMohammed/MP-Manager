package com.google.android.material.color;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.loader.ResourcesLoader;

import java.util.Map;

/** Direct access to Material 1.12's factory so the host can own and remove its loader. */
@SuppressLint("RestrictedApi")
@androidx.annotation.RequiresApi(30)
public final class ThemePackColorLoaderBridge {

    private ThemePackColorLoaderBridge() {
    }

    /**
     * Creates an unregistered override loader carrying the given color
     * replacements ({@code @ColorRes -> ARGB}), or null when unavailable
     * (API 30+ only; callers must gate on {@code Build.VERSION.SDK_INT}).
     */
    public static ResourcesLoader create(Context context,
                                         Map<Integer, Integer> colorReplacements) {
        try {
            return ColorResourcesLoaderCreator.create(context, colorReplacements);
        } catch (Throwable t) {
            return null;
        }
    }
}

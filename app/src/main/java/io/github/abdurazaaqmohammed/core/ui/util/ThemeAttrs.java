package io.github.abdurazaaqmohammed.core.ui.util;

import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;

import androidx.annotation.AttrRes;

/**
 * Resolve theme attributes instead of hardcoding Color.WHITE / Color.TRANSPARENT
 * (see legacy UIHelper.getTitle/styleEditText). One place to change surface colors.
 */
public final class ThemeAttrs {

    private ThemeAttrs() {
    }

    public static int resolve(Context context, @AttrRes int attr, int fallback) {
        try {
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(attr, tv, true)) {
                if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return tv.data;
                }
                return tv.data != 0 ? tv.data : fallback;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public static int onSurface(Context context) {
        return resolve(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
    }

    public static int surface(Context context) {
        return resolve(context, com.google.android.material.R.attr.colorSurface, Color.TRANSPARENT);
    }
}

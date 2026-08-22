package io.github.abdurazaaqmohammed.utils;

import static io.github.abdurazaaqmohammed.utils.LegacyUtils.aboveSdk20;

import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;

public class ColorUtil {
    public static void changeImageColor(Drawable d, int color) {
        DrawableCompat.setTint(d, color);
        //if (aboveSdk20) d.setTint(color);
        //else d.setColorFilter(new LightingColorFilter(Color.BLACK, color));
    }

    public static void setTextViewColor(TextView tv, int color) {
        tv.setBackgroundColor(Color.TRANSPARENT);
        //tv.setTextColor(Color.WHITE);
    }
}
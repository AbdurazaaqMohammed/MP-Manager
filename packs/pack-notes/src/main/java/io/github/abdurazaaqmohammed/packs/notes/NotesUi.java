package io.github.abdurazaaqmohammed.packs.notes;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NotesUi {

    public static final int[] NOTE_COLORS = {
            0xFFEF5350, 0xFFAB47BC, 0xFF5C6BC0, 0xFF29B6F6,
            0xFF26A69A, 0xFF66BB6A, 0xFFFFCA28, 0xFFFF7043,
            0xFF8D6E63, 0xFF78909C};

    public static final int[] ACCENTS = {
            0xFF6750A4, 0xFF00639B, 0xFF386A20, 0xFF8E4E00,
            0xFFB3261E, 0xFF006B5F, 0xFF7D5260, 0xFF4F5B92};

    private NotesUi() {
    }

    public static int dp(Context context, int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int color(Context context, int attr, int fallback) {
        try {
            return MaterialColors.getColor(context, attr, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static int surface(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurface, 0xFFFFFBFE);
    }

    public static int surfaceLow(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFFF7F2FA);
    }

    public static int surfaceHigh(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFFECE6F0);
    }

    public static int surfaceHighest(Context context) {
        return color(context, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFFE6E0E9);
    }

    public static int onSurface(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnSurface, 0xFF1C1B1F);
    }

    public static int onSurfaceVariant(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF49454F);
    }

    public static int outline(Context context) {
        return color(context, com.google.android.material.R.attr.colorOutline, 0xFF79747E);
    }

    public static int primary(Context context) {
        return color(context, com.google.android.material.R.attr.colorPrimary, 0xFF6750A4);
    }

    public static int onPrimary(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF);
    }

    public static int primaryContainer(Context context) {
        return color(context, com.google.android.material.R.attr.colorPrimaryContainer, 0xFFEADDFF);
    }

    public static int onPrimaryContainer(Context context) {
        return color(context, com.google.android.material.R.attr.colorOnPrimaryContainer, 0xFF21005D);
    }

    public static int error(Context context) {
        return color(context, com.google.android.material.R.attr.colorError, 0xFFB3261E);
    }

    public static boolean isNight(Context context) {
        try {
            int mode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static int mix(int a, int b, float ratio) {
        float r = ratio < 0 ? 0 : (ratio > 1 ? 1 : ratio);
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * r),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * r),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * r));
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

public static GradientDrawable round(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable stroke(int fill, int strokeColor, int radiusPx, int strokeWidthPx) {
        GradientDrawable d = round(fill, radiusPx);
        d.setStroke(strokeWidthPx, strokeColor);
        return d;
    }

    public static TextView label(Context context, String text, float sizeSp, int color) {
        TextView t = new TextView(context);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        return t;
    }

    public static String date(Context context, long millis, boolean relative) {
        if (millis <= 0) return "";
        long now = System.currentTimeMillis();
        if (relative && Math.abs(now - millis) < 60_000L) return "just now";
        if (DateUtils.isToday(millis)) {
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(millis));
        }
        if (!relative) return absolute(millis);
        if (isYesterday(millis)) {
            return "Yesterday " + new SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(new Date(millis));
        }
        if (Math.abs(now - millis) < 6L * 24L * 60L * 60L * 1000L) {
            return DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE).toString();
        }
        return absolute(millis);
    }

    private static String absolute(long millis) {
        int year = yearOf(millis);
        String pattern = year == yearOf(System.currentTimeMillis()) ? "d MMM" : "d MMM yyyy";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }

    private static boolean isYesterday(long millis) {
        long day = 24L * 60L * 60L * 1000L;
        return millis / day == System.currentTimeMillis() / day - 1;
    }

    private static int yearOf(long millis) {
        try {
            return Integer.parseInt(new SimpleDateFormat("yyyy", Locale.US).format(new Date(millis)));
        } catch (Exception ignored) {
            return 1970;
        }
    }

    public static LinearLayout column(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context context) {
        LinearLayout l = new LinearLayout(context);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static void add(ViewGroup parent, View view, int width, int height, float weight) {
        parent.addView(view, new LinearLayout.LayoutParams(width, height, weight));
    }

    public static String plural(int count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    public static int resId(Context context, String name) {
        try {
            return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        } catch (Exception ignored) {
            return 0;
        }
    }
}

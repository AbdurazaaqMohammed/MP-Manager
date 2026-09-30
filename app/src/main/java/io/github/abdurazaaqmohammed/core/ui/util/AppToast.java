package io.github.abdurazaaqmohammed.core.ui.util;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Themed Toast replacement for Toast.makeText. Builds the view in code
 * (GradientDrawable, no drawable resources) so ?attr is resolved at runtime
 * against the current theme, which is unreliable in drawable XML on minSdk 19.
 */
public final class AppToast {

    private AppToast() {
    }

    public static void show(Context c, CharSequence msg) {
        show(c, msg, false);
    }

    public static void show(Context c, CharSequence msg, boolean longDuration) {
        try {
            Context themed = ThemeAttrs.themed(c);
            int density = (int) themed.getResources().getDisplayMetrics().density;

            TextView view = new TextView(themed);
            view.setText(msg);
            view.setTextColor(ThemeAttrs.onSurfaceVariant(themed));
            view.setTextSize(14);
            view.setTypeface(Typeface.DEFAULT);
            view.setGravity(Gravity.CENTER);
            int padH = (int) (16 * themed.getResources().getDisplayMetrics().density + 0.5f);
            int padV = (int) (10 * themed.getResources().getDisplayMetrics().density + 0.5f);
            view.setPadding(padH, padV, padH, padV);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ThemeAttrs.surfaceVariant(themed));
            bg.setCornerRadius(12 * themed.getResources().getDisplayMetrics().density);
            bg.setStroke(Math.max(1, density), ThemeAttrs.outline(themed));
            view.setBackground(bg);

            Toast toast = Toast.makeText(themed, "", Toast.LENGTH_SHORT);
            toast.setView(view);
            toast.setDuration(longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
            toast.show();
        } catch (Exception ignored) {
        }
    }
}

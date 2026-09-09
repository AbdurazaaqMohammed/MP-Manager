package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class UiPrefs {

    public static final String[] DATE_PRESETS = {
            "yy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm",
            "MM-dd-yyyy hh:mm a",
            "EEE, dd MMM yyyy HH:mm"
    };

    private UiPrefs() {
    }

    public static int getScale(Context context) {
        try {
            int v = PreferenceManager.getDefaultSharedPreferences(context).getInt("file_list_scale", 100);
            return Math.max(60, Math.min(140, v));
        } catch (Exception e) {
            return 100;
        }
    }

    public static int getMaxLines(Context context) {
        try {
            int v = PreferenceManager.getDefaultSharedPreferences(context).getInt("filename_max_lines", 2);
            return Math.max(1, Math.min(8, v));
        } catch (Exception e) {
            return 2;
        }
    }

    public static String getDatePattern(Context context) {
        try {
            String p = PreferenceManager.getDefaultSharedPreferences(context).getString("date_format", DATE_PRESETS[0]);
            if (p == null || p.isEmpty()) return DATE_PRESETS[0];
            new SimpleDateFormat(p, Locale.getDefault());
            return p;
        } catch (Exception e) {
            return DATE_PRESETS[0];
        }
    }

    public static String formatDate(Context context, long millis) {
        try {
            return new SimpleDateFormat(getDatePattern(context), Locale.getDefault()).format(new Date(millis));
        } catch (Exception e) {
            return new SimpleDateFormat(DATE_PRESETS[0], Locale.getDefault()).format(new Date(millis));
        }
    }

    public static float nameSize(int scale) {
        return 14f * scale / 100f;
    }

    public static float dateSize(int scale) {
        return 10f * scale / 100f;
    }

    public static int iconDp(Context context, int scale) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (30f * scale / 100f * density + 0.5f);
    }

    public static String startupMode(Context context, boolean pane1) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getString(pane1 ? "startup_1" : "startup_2", "home");
        } catch (Exception e) {
            return "home";
        }
    }

    public static String appPathDir(Context context, String def) {
        try {
            String p = PreferenceManager.getDefaultSharedPreferences(context).getString("app_path_dir", "");
            if (p != null && !p.trim().isEmpty()) return p.trim();
        } catch (Exception ignored) {
        }
        return def;
    }

    public static boolean genBackup(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("gen_backup", true);
        } catch (Exception e) {
            return true;
        }
    }
}

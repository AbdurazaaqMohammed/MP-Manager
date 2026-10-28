package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.preference.PreferenceManager;

import com.reandroid.apk.framework.InternalFrameworks;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class UiPrefs {

    public static final String[] DATE_PRESETS = {
            "yy-MM-dd HH:mm",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm",
            "MM-dd-yyyy hh:mm a",
            "EEE, dd MMM yyyy HH:mm"
    };

    public static final String FOLDER_ANIM_KEY = "folder_transition";
    public static final String FOLDER_ANIM_OFF = "off";
    public static final String FOLDER_ANIM_SLIDE = "slide";
    public static final String FOLDER_ANIM_SLIDE_UP = "slide_up";
    public static final String FOLDER_ANIM_FADE = "fade";
    public static final String FOLDER_ANIM_SLIDE_FADE = "slide_fade";
    public static final String FOLDER_ANIM_DEFAULT = FOLDER_ANIM_FADE;

    private UiPrefs() {
    }

    /**
     * Verifying a date pattern means building a {@link SimpleDateFormat}, which is
     * expensive. formatDate() runs once per bound row, so the verdict is memoized
     * instead of re-allocating a throwaway formatter on every call.
     */
    private static final Map<String, Boolean> VALID_DATE_PATTERNS = new ConcurrentHashMap<>();

    private static boolean isDatePatternValid(String pattern) {
        Boolean known = VALID_DATE_PATTERNS.get(pattern);
        if (known != null) return known;
        boolean valid;
        try {
            new SimpleDateFormat(pattern, Locale.getDefault());
            valid = true;
        } catch (Exception e) {
            valid = false;
        }
        VALID_DATE_PATTERNS.put(pattern, valid);
        return valid;
    }

    /**
     * Reusable formatter per thread. SimpleDateFormat is not thread safe and
     * constructing one is costly, so each thread keeps its own instance keyed by
     * the pattern it was built for.
     */
    private static final ThreadLocal<SimpleDateFormat> THREAD_DATE_FORMAT = new ThreadLocal<>();
    private static final ThreadLocal<String> THREAD_DATE_PATTERN = new ThreadLocal<>();

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
            if (isDatePatternValid(p)) return p;
            return DATE_PRESETS[0];
        } catch (Exception e) {
            return DATE_PRESETS[0];
        }
    }

    public static String formatDate(Context context, long millis) {
        return formatDate(getDatePattern(context), millis);
    }

    /** Formats with an already-resolved pattern, reusing this thread's formatter. */
    public static String formatDate(String pattern, long millis) {
        String effective = pattern;
        SimpleDateFormat fmt = THREAD_DATE_FORMAT.get();
        if (fmt == null || !effective.equals(THREAD_DATE_PATTERN.get())) {
            if (!isDatePatternValid(effective)) effective = DATE_PRESETS[0];
            try {
                fmt = new SimpleDateFormat(effective, Locale.getDefault());
            } catch (Exception e) {
                effective = DATE_PRESETS[0];
                try {
                    fmt = new SimpleDateFormat(effective, Locale.getDefault());
                } catch (Exception e2) {
                    return "";
                }
            }
            THREAD_DATE_FORMAT.set(fmt);
            THREAD_DATE_PATTERN.set(effective);
        }
        try {
            return fmt.format(new Date(millis));
        } catch (Exception e) {
            return "";
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

    /** Default working directory base ("MP Manager" on the sdcard). */
    public static String defaultAppDir() {
        return new File(Environment.getExternalStorageDirectory(), "MP Manager").getPath();
    }

    /**
     * Framework resources directory ({@code <app dir>/frameworks}), following the
     * custom app-path preference. Also points REAndroid's framework loader at
     * it so decode/build use the same location the app extracts to.
     */
    public static File frameworkDir(Context context) {
        File fw = new File(appPathDir(context, defaultAppDir()), "frameworks");
        try {
            InternalFrameworks.setAndroidResourceDirectory(fw.getPath());
        } catch (Throwable ignored) {
        }
        return fw;
    }

    public static boolean genBackup(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("gen_backup", true);
        } catch (Exception e) {
            return true;
        }
    }

    public static String folderAnim(Context context) {
        try {
            String v = PreferenceManager.getDefaultSharedPreferences(context).getString(FOLDER_ANIM_KEY, FOLDER_ANIM_DEFAULT);
            if (v == null) return FOLDER_ANIM_DEFAULT;
            switch (v) {
                case FOLDER_ANIM_OFF:
                case FOLDER_ANIM_SLIDE:
                case FOLDER_ANIM_SLIDE_UP:
                case FOLDER_ANIM_FADE:
                case FOLDER_ANIM_SLIDE_FADE:
                    return v;
                default:
                    return FOLDER_ANIM_DEFAULT;
            }
        } catch (Exception e) {
            return FOLDER_ANIM_DEFAULT;
        }
    }

    public static void playFolderTransition(View pane) {
        if (pane == null) return;
        String mode = folderAnim(pane.getContext());
        if (FOLDER_ANIM_OFF.equals(mode)) return;
        try {
            pane.animate().cancel();
            float density = pane.getResources().getDisplayMetrics().density;
            long dur = 170;
            DecelerateInterpolator interp =
                    new DecelerateInterpolator();
            if (FOLDER_ANIM_FADE.equals(mode)) {
                pane.setAlpha(0.35f);
                pane.setTranslationX(0f);
                pane.setTranslationY(0f);
                pane.animate().alpha(1f).setDuration(dur).setInterpolator(interp).start();
            } else if (FOLDER_ANIM_SLIDE_UP.equals(mode)) {
                pane.setAlpha(0.4f);
                pane.setTranslationX(0f);
                pane.setTranslationY(22f * density);
                pane.animate().alpha(1f).translationY(0f).setDuration(dur).setInterpolator(interp).start();
            } else {
                pane.setAlpha(FOLDER_ANIM_SLIDE.equals(mode) ? 0.55f : 0f);
                pane.setTranslationX(26f * density);
                pane.setTranslationY(0f);
                pane.animate().alpha(1f).translationX(0f).setDuration(dur).setInterpolator(interp).start();
            }
        } catch (Exception ignored) {
            try {
                pane.setAlpha(1f);
                pane.setTranslationX(0f);
                pane.setTranslationY(0f);
            } catch (Exception ignored2) {
            }
        }
    }

    public static final String FOLDER_SIZE_KEY = "show_folder_size_mode";
    public static final int FOLDER_SIZE_USER = 0;
    public static final int FOLDER_SIZE_ALL = 1;
    public static final int FOLDER_SIZE_NONE = 2;

    public static int folderSizeMode(Context context) {
        try {
            return PreferenceManager.getDefaultSharedPreferences(context)
                    .getInt(FOLDER_SIZE_KEY, FOLDER_SIZE_USER);
        } catch (Exception ignored) {
            return FOLDER_SIZE_USER;
        }
    }
}

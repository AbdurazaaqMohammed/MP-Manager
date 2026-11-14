package io.github.abdurazaaqmohammed.ui.common.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;
import androidx.preference.PreferenceManager;

/** Semantic view haptics respect both the app toggle and the device's touch-feedback setting. */
public final class Haptics {
    public static final String ENABLED = "haptics_enabled";
    public enum Event {
        SELECTION("haptics_selection", true),
        ACTION("haptics_actions", true),
        LONG_PRESS("haptics_actions", true),
        NAVIGATION("haptics_navigation", false),
        COMPLETION("haptics_completion", false);
        public final String key;
        public final boolean defaultEnabled;
        Event(String key, boolean defaultEnabled) { this.key = key; this.defaultEnabled = defaultEnabled; }
    }
    private Haptics() {}

    public static boolean enabled(Context context, Event event) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(ENABLED, true) && prefs.getBoolean(event.key, event.defaultEnabled);
    }

    public static void perform(View view, Event event) {
        if (view == null || !enabled(view.getContext(), event)) return;
        int feedback = switch (event) {
            case LONG_PRESS -> HapticFeedbackConstants.LONG_PRESS;
            case SELECTION -> Build.VERSION.SDK_INT >= 34 ? HapticFeedbackConstants.SEGMENT_TICK
                    : HapticFeedbackConstants.CLOCK_TICK;
            case COMPLETION -> Build.VERSION.SDK_INT >= 30 ? HapticFeedbackConstants.CONFIRM
                    : HapticFeedbackConstants.VIRTUAL_KEY;
            default -> HapticFeedbackConstants.KEYBOARD_TAP;
        };
        view.performHapticFeedback(feedback); // Never use IGNORE_GLOBAL_SETTING.
    }
}

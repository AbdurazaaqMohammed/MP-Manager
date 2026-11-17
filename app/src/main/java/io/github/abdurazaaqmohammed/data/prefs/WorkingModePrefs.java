package io.github.abdurazaaqmohammed.data.prefs;

import android.content.Context;

import androidx.preference.PreferenceManager;

/**
 * The user's preferred working mode — a settings state, not a privilege
 * implementation detail. Stored in the default SharedPreferences under
 * "working_mode". Migrated from RootManager (S3).
 */
public final class WorkingModePrefs {

    private static final String PREF_KEY = "working_mode";

    public enum WorkingMode {
        NON_ROOT("non_root", "Non-root"),
        ROOT("root", "Root"),
        SHIZUKU("shizuku", "Shizuku");
        // DHIZUKU slot lands with settings wiring (see PrivilegeManager.Backend)

        public final String key;
        public final String label;

        WorkingMode(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private WorkingModePrefs() {
    }

    public static WorkingMode get(Context context) {
        String mode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_KEY, WorkingMode.NON_ROOT.key);
        if (WorkingMode.ROOT.key.equals(mode)) return WorkingMode.ROOT;
        if (WorkingMode.SHIZUKU.key.equals(mode)) return WorkingMode.SHIZUKU;
        return WorkingMode.NON_ROOT;
    }

    public static void set(Context context, WorkingMode mode) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREF_KEY, mode.key)
                .apply();
    }
}

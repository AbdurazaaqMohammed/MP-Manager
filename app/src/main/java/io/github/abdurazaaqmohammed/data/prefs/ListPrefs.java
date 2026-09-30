package io.github.abdurazaaqmohammed.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Listing preferences: per-folder/global sort mode, hidden-file rules.
 * Extracted from MainActivity's inline PreferenceManager reads.
 */
public final class ListPrefs {

    private final Context context;

    public ListPrefs(Context context) {
        // Keep the context only: resolving prefs here would run before the
        // activity is attached when created via field initializer.
        this.context = context;
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public int sortBy(String folderPath) {
        SharedPreferences prefs = prefs();
        return prefs.getInt("sort_by_" + folderPath, prefs.getInt("sort_by", 0));
    }

    public boolean sortReverse(String folderPath) {
        SharedPreferences prefs = prefs();
        return prefs.getBoolean("sort_reverse_" + folderPath, prefs.getBoolean("sort_reverse", false));
    }

    /** Save sort mode globally, or only for one folder when folderPath != null. */
    public void saveSort(String folderPath, int sortBy, boolean reverse) {
        SharedPreferences.Editor editor = prefs().edit();
        if (folderPath != null) {
            editor.putInt("sort_by_" + folderPath, sortBy);
            editor.putBoolean("sort_reverse_" + folderPath, reverse);
        } else {
            editor.putInt("sort_by", sortBy);
            editor.putBoolean("sort_reverse", reverse);
        }
        editor.apply();
    }

    public boolean showSystemHidden() {
        return prefs().getBoolean("show_system_hidden", false);
    }

    public boolean showManualHidden() {
        return prefs().getBoolean("show_manually_hidden", false);
    }

    public Set<String> manuallyHidden() {
        try {
            return prefs().getStringSet("manually_hidden_files", new HashSet<>());
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    public void unhide(String path) {
        Set<String> current = manuallyHidden();
        HashSet<String> values = new HashSet<>(current);
        values.remove(path);
        prefs().edit().putStringSet("manually_hidden_files", values).apply();
    }
}

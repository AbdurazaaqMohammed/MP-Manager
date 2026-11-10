package io.github.abdurazaaqmohammed.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Sidebar pins: provider locations and saved network profiles shown among the
 * storage volumes in the main left drawer.
 *
 * <p>Each pin is one encoded string, the shape the rest of the sidebar uses
 * for its own ids ("bookmark\u0001…", "packtool:…"):</p>
 * <ul>
 *   <li>{@code saf:<treeUri>} — a granted DocumentsProvider tree;</li>
 *   <li>{@code ftp:<profileName>} — an FTP client profile by name.</li>
 *   <li>{@code webdav:<profileName>} / {@code smb:<profileName>} — encrypted network profiles.</li>
 * </ul>
 *
 * <p>A JSON string list (not a StringSet) keeps the pin order stable.</p>
 */
public final class SidebarPins {

    public static final String SAF_PREFIX = "saf:";
    public static final String FTP_PREFIX = "ftp:";
    public static final String WEBDAV_PREFIX = "webdav:";
    public static final String SMB_PREFIX = "smb:";
    private static final String KEY = "sidebar_pinned";

    private SidebarPins() {
    }

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    /** Pins in saved order; safe on any thread. */
    public static List<String> get(Context context) {
        List<String> out = new ArrayList<>();
        try {
            String raw = prefs(context).getString(KEY, "[]");
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                String pin = array.optString(i, null);
                if (pin != null && !pin.isEmpty() && !out.contains(pin)) out.add(pin);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static synchronized void add(Context context, String pin) {
        if (pin == null || pin.isEmpty() || isPinned(context, pin)) return;
        List<String> pins = get(context);
        pins.add(pin);
        save(context, pins);
    }

    public static synchronized void remove(Context context, String pin) {
        List<String> pins = get(context);
        if (pins.remove(pin)) save(context, pins);
    }

    public static boolean isPinned(Context context, String pin) {
        return get(context).contains(pin);
    }

    /** Preserve the pin's position when a saved configuration is renamed. */
    public static synchronized void replace(Context context, String oldPin, String newPin) {
        List<String> pins = get(context);
        int index = pins.indexOf(oldPin);
        if (index < 0 || oldPin.equals(newPin)) return;
        pins.set(index, newPin);
        List<String> unique = new ArrayList<>();
        for (String pin : pins) if (!unique.contains(pin)) unique.add(pin);
        save(context, unique);
    }

    private static void save(Context context, List<String> pins) {
        JSONArray array = new JSONArray();
        for (String pin : pins) array.put(pin);
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }
}

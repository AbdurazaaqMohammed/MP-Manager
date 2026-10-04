package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Stored archive passwords, kept in the order they should be tried.
 *
 * <p>Held in the app's private SharedPreferences. That is deliberately the
 * simplest thing that works and it never leaves the app sandbox, but it is not
 * encrypted at rest: anyone who can read the app's data directory can read these.
 * Worth knowing before putting a password here that matters.
 */
public final class ArchivePasswordStore {

    private static final String PREFS = "archive_passwords";
    private static final String KEY_LIST = "list";
    private static final String KEY_MATCH_ARCHIVE = "match_archive";
    private static final String KEY_MATCH_INDEX = "match_index";
    private static final String KEY_MATCH_TOTAL = "match_total";
    private static final String KEY_MATCH_TIME = "match_time";

    private ArchivePasswordStore() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Passwords in the order they should be tried; never null. */
    public static List<String> list(Context context) {
        List<String> out = new ArrayList<>();
        String raw = prefs(context).getString(KEY_LIST, null);
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static boolean isEmpty(Context context) {
        return list(context).isEmpty();
    }

    /**
     * Adds a password. Duplicates are refused and reported as false, since the
     * list is only useful when trying each candidate once.
     */
    public static boolean add(Context context, String password) {
        if (password == null || password.isEmpty()) return false;
        List<String> current = list(context);
        if (current.contains(password)) return false;
        current.add(password);
        save(context, current);
        return true;
    }

    public static boolean remove(Context context, String password) {
        List<String> current = list(context);
        if (!current.remove(password)) return false;
        save(context, current);
        return true;
    }

    /** Moves an entry one place earlier, so the try order can be tuned. */
    public static boolean moveUp(Context context, int index) {
        List<String> current = list(context);
        if (index <= 0 || index >= current.size()) return false;
        String s = current.remove(index);
        current.add(index - 1, s);
        save(context, current);
        return true;
    }

    public static boolean moveDown(Context context, int index) {
        List<String> current = list(context);
        if (index < 0 || index >= current.size() - 1) return false;
        String s = current.remove(index);
        current.add(index + 1, s);
        save(context, current);
        return true;
    }

    private static void save(Context context, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply();
    }

    public static void clear(Context context) {
        prefs(context).edit().remove(KEY_LIST).apply();
    }

    /** What a successful password lookup produced, for the recent-match line. */
    public static final class Match {
        public final String archiveName;
        public final int index;
        public final int total;
        public final long time;

        Match(String archiveName, int index, int total, long time) {
            this.archiveName = archiveName;
            this.index = index;
            this.total = total;
            this.time = time;
        }
    }

    /**
     * Records which stored password opened an archive, so the UI can show the
     * last success instead of making the user remember it worked.
     */
    public static void recordMatch(Context context, String archiveName, int index, int total) {
        prefs(context).edit()
                .putString(KEY_MATCH_ARCHIVE, archiveName)
                .putInt(KEY_MATCH_INDEX, index)
                .putInt(KEY_MATCH_TOTAL, total)
                .putLong(KEY_MATCH_TIME, System.currentTimeMillis())
                .apply();
    }

    /** The last recorded match, or null when nothing has matched yet. */
    public static Match lastMatch(Context context) {
        SharedPreferences p = prefs(context);
        String archive = p.getString(KEY_MATCH_ARCHIVE, null);
        long time = p.getLong(KEY_MATCH_TIME, 0L);
        if (archive == null || time == 0L) return null;
        return new Match(archive, p.getInt(KEY_MATCH_INDEX, 0),
                p.getInt(KEY_MATCH_TOTAL, 0), time);
    }

    public static void clearMatch(Context context) {
        prefs(context).edit()
                .remove(KEY_MATCH_ARCHIVE)
                .remove(KEY_MATCH_INDEX)
                .remove(KEY_MATCH_TOTAL)
                .remove(KEY_MATCH_TIME)
                .apply();
    }

    /** Test seam: the exact JSON we persist, so it can be checked without Android. */
    static String encode(List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        return arr.toString();
    }
}
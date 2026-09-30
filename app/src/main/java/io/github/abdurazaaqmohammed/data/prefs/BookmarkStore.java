package io.github.abdurazaaqmohammed.data.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bookmark persistence: main list (with legacy formats), labels, groups,
 * per-group lists. Extracted from MainActivity's inline Gson/prefs code.
 * Caching stays with the caller; this class only does IO.
 */
public final class BookmarkStore {

    private final Context context;
    private final Gson gson = new Gson();

    public BookmarkStore(Context context) {
        // Keep the context only: resolving prefs here would run before the
        // activity is attached when created via field initializer.
        this.context = context;
    }

    private SharedPreferences prefs() {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    public ArrayList<File> loadBookmarks() {
        ArrayList<File> out = new ArrayList<>();
        try {
            String raw = prefs().getString("bookmarks", "");
            if (raw == null) raw = "";
            raw = raw.trim();
            if (raw.startsWith("[\"")) {
                List<String> paths = gson.fromJson(raw,
                        new TypeToken<List<String>>() {}.getType());
                if (paths != null) {
                    for (String path : paths) {
                        if (!TextUtils.isEmpty(path)) {
                            File bookmarked = new File(path);
                            if (bookmarked.exists()) out.add(bookmarked);
                        }
                    }
                }
            } else {
                String[] savedBookmarks = raw.replace("[", "").replace("]", "").split(", ");
                for (String bookmark : savedBookmarks) {
                    bookmark = bookmark.trim();
                    if (!TextUtils.isEmpty(bookmark)) {
                        File bookmarked = new File(bookmark);
                        if (bookmarked.exists()) out.add(bookmarked);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void saveBookmarks(List<File> files) {
        try {
            prefs().edit().putString("bookmarks", files.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public Map<String, String> loadLabels() {
        Map<String, String> out = new HashMap<>();
        try {
            String json = prefs().getString("bookmark_labels", "{}");
            Map<String, String> map = gson.fromJson(json,
                    new TypeToken<Map<String, String>>() {}.getType());
            if (map != null) out.putAll(map);
        } catch (Exception ignored) {
        }
        return out;
    }

    public void saveLabels(Map<String, String> labels) {
        try {
            prefs().edit().putString("bookmark_labels", gson.toJson(labels)).apply();
        } catch (Exception ignored) {
        }
    }

    public List<String> loadGroups() {
        List<String> out = new ArrayList<>();
        try {
            String json = prefs().getString("bookmark_groups", "[]");
            List<String> groups = gson.fromJson(json,
                    new TypeToken<List<String>>() {}.getType());
            if (groups != null) out.addAll(groups);
        } catch (Exception ignored) {
        }
        return out;
    }

    public void saveGroups(List<String> groups) {
        try {
            prefs().edit().putString("bookmark_groups", gson.toJson(groups)).apply();
        } catch (Exception ignored) {
        }
    }

    public ArrayList<File> loadGroup(String group) {
        ArrayList<File> out = new ArrayList<>();
        try {
            String json = prefs().getString("bookmarks_group_" + group, "[]");
            List<String> paths = gson.fromJson(json,
                    new TypeToken<List<String>>() {}.getType());
            if (paths == null) return out;
            for (String path : paths) {
                if (!TextUtils.isEmpty(path)) out.add(new File(path));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public void saveGroup(String group, List<File> files) {
        try {
            List<String> paths = new ArrayList<>();
            for (File f : files) paths.add(f.getPath());
            prefs().edit().putString("bookmarks_group_" + group, gson.toJson(paths)).apply();
        } catch (Exception ignored) {
        }
    }

    public int lastTabIndex(int groupCount) {
        int last = prefs().getInt("bookmarks_last_tab", 0);
        if (last == 1) last = 0;
        return Math.min(last, 1 + groupCount);
    }
}

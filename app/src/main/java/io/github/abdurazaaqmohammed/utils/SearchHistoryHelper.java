package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class SearchHistoryHelper {

    public static final String KEY_MAIN = "search_history";
    public static final String KEY_DEX = "dex_search_history";
    public static final String KEY_ARSC_PLUS = "arsc_plus_search_history";
    public static final String KEY_LIMIT = "search_history_limit";
    public static final int DEFAULT_LIMIT = 50;

    private SearchHistoryHelper() {
    }

    public static class Item {
        public String query;
        public boolean pinned;

        public Item(String query, boolean pinned) {
            this.query = query;
            this.pinned = pinned;
        }
    }

    public static int getLimit(Context context) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            int v = prefs.getInt(KEY_LIMIT, DEFAULT_LIMIT);
            if (v <= 0) return DEFAULT_LIMIT;
            return Math.min(v, 500);
        } catch (Exception e) {
            return DEFAULT_LIMIT;
        }
    }

    public static List<Item> load(Context context, String key) {
        List<Item> out = new ArrayList<>();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String raw = prefs.getString(key, "");
            if (raw == null || raw.isEmpty()) return out;
            raw = raw.trim();
            if (raw.startsWith("[")) {
                try {
                    JSONArray arr = new JSONArray(raw);
                    for (int i = 0; i < arr.length(); i++) {
                        Object o = arr.opt(i);
                        if (o instanceof JSONObject) {
                            JSONObject jo = (JSONObject) o;
                            String q = jo.optString("q", null);
                            if (q == null) q = jo.optString("query", null);
                            if (q == null || q.isEmpty()) continue;
                            out.add(new Item(q, jo.optBoolean("p", jo.optBoolean("pinned", false))));
                        } else if (o instanceof String) {
                            String q = (String) o;
                            if (q != null && !q.isEmpty()) out.add(new Item(q, false));
                        }
                    }
                    sortPinnedFirst(out);
                    return out;
                } catch (Exception ignored) {
                }
            }
            String[] parts = raw.split("\n");
            for (String p : parts) {
                String q = p == null ? "" : p.trim();
                if (!q.isEmpty()) out.add(new Item(q, false));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(Context context, String key, List<Item> items) {
        try {
            sortPinnedFirst(items);
            JSONArray arr = new JSONArray();
            for (Item it : items) {
                if (it == null || it.query == null || it.query.isEmpty()) continue;
                JSONObject jo = new JSONObject();
                jo.put("q", it.query);
                jo.put("p", it.pinned);
                arr.put(jo);
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit().putString(key, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static void push(Context context, String key, String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return;
        List<Item> items = load(context, key);
        boolean wasPinned = false;
        for (Item it : items) {
            if (it.query.equals(q)) {
                wasPinned = it.pinned;
                break;
            }
        }
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).query.equals(q)) items.remove(i);
        }
        items.add(0, new Item(q, wasPinned));
        sortPinnedFirst(items);
        trim(items, getLimit(context));
        save(context, key, items);
    }

    public static void remove(Context context, String key, String query) {
        List<Item> items = load(context, key);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).query.equals(query)) items.remove(i);
        }
        save(context, key, items);
    }

    public static void setPinned(Context context, String key, String query, boolean pinned) {
        List<Item> items = load(context, key);
        for (Item it : items) {
            if (it.query.equals(query)) {
                it.pinned = pinned;
                break;
            }
        }
        sortPinnedFirst(items);
        save(context, key, items);
    }

    private static void sortPinnedFirst(List<Item> items) {
        List<Item> pinned = new ArrayList<>();
        List<Item> rest = new ArrayList<>();
        for (Item it : items) {
            if (it.pinned) pinned.add(it);
            else rest.add(it);
        }
        items.clear();
        items.addAll(pinned);
        items.addAll(rest);
    }

    private static void trim(List<Item> items, int limit) {
        if (limit <= 0) limit = DEFAULT_LIMIT;
        int pinnedCount = 0;
        for (Item it : items) if (it.pinned) pinnedCount++;
        int allowed = Math.max(limit, pinnedCount);
        while (items.size() > allowed) {
            int idx = -1;
            for (int i = items.size() - 1; i >= 0; i--) {
                if (!items.get(i).pinned) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) break;
            items.remove(idx);
        }
    }
}

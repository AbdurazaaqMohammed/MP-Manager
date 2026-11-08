package io.github.abdurazaaqmohammed.features.files;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;

public final class RightMenuOrder {

    public static final String FILTER = "filter";
    public static final String SEARCH = "search";
    public static final String XML_SEARCH = "xml_search";
    public static final String SEARCH_RESULTS = "search_results";
    public static final String CLEAR_SEARCH = "clear_search";
    public static final String SELECT_ALL = "select_all";
    public static final String SORT = "sort";
    public static final String SHOW_SYSTEM_HIDDEN = "show_system_hidden";
    public static final String SHOW_MANUAL_HIDDEN = "show_manual_hidden";
    public static final String SHOW_FOLDER_SIZE = "show_folder_size";
    public static final String ADD_BOOKMARK = "add_bookmark";
    public static final String SET_AS_HOME = "set_as_home";
    public static final String SWAP_PANES = "swap_panes";
    public static final String COMPARE_FOLDERS = "compare_folders";
    public static final String PREFERENCES = "preferences";

    public static final String[] DEFAULT_ORDER = {
            FILTER,
            SEARCH,
            XML_SEARCH,
            SEARCH_RESULTS,
            CLEAR_SEARCH,
            SELECT_ALL,
            SORT,
            COMPARE_FOLDERS,
            SHOW_SYSTEM_HIDDEN,
            SHOW_MANUAL_HIDDEN,
            SHOW_FOLDER_SIZE,
            ADD_BOOKMARK,
            SET_AS_HOME,
            SWAP_PANES,
            PREFERENCES
    };

    public record MenuItem(String id, String label) {}

    private RightMenuOrder() {}

    public static List<String> load(Context context) {
        List<String> order = new ArrayList<>();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String saved = prefs.getString("right_menu_order", "");
            if (saved != null && !saved.isEmpty()) {
                for (String id : saved.split(",")) {
                    id = id.trim();
                    if (!id.isEmpty() && !order.contains(id)) order.add(id);
                }
            }
        } catch (Exception ignored) {}

        for (String id : DEFAULT_ORDER) {
            if (!order.contains(id)) order.add(id);
        }
        return order;
    }

    public static void save(Context context, List<String> order) {
        if (order == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(order.get(i));
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("right_menu_order", sb.toString()).apply();
    }

    public static Set<String> getHiddenIds(Context context) {
        try {
            Set<String> set = PreferenceManager.getDefaultSharedPreferences(context)
                    .getStringSet("right_menu_hidden_ids", null);
            if (set != null) return new HashSet<>(set);
        } catch (Exception ignored) {}
        return new HashSet<>();
    }

    public static void setHiddenIds(Context context, Set<String> hiddenIds) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putStringSet("right_menu_hidden_ids", hiddenIds == null ? new HashSet<>() : hiddenIds)
                    .apply();
        } catch (Exception ignored) {}
    }

    public static String labelFor(Context context, String id) {
        if (id == null) return "";
        switch (id) {
            case FILTER: return context.getString(R.string.filter);
            case SEARCH: return context.getString(R.string.search);
            case XML_SEARCH: return context.getString(R.string.xml_search);
            case SEARCH_RESULTS: return context.getString(R.string.search_results);
            case CLEAR_SEARCH: return context.getString(R.string.clear_search_results);
            case SELECT_ALL: return context.getString(R.string.menu_select_all);
            case SORT: return context.getString(R.string.sort);
            case SHOW_SYSTEM_HIDDEN: return context.getString(R.string.menu_show_system_hidden);
            case SHOW_MANUAL_HIDDEN: return context.getString(R.string.menu_show_manual_hidden);
            case SHOW_FOLDER_SIZE: return context.getString(R.string.show_folder_size);
            case ADD_BOOKMARK: return context.getString(R.string.menu_add_bookmark);
            case SET_AS_HOME: return context.getString(R.string.set_as_home);
            case SWAP_PANES: return context.getString(R.string.menu_swap_panes);
            case COMPARE_FOLDERS: return context.getString(R.string.compare_folders);
            case PREFERENCES: return context.getString(R.string.preferences);
            default: return id;
        }
    }

    public static int iconFor(String id) {
        if (id == null) return R.drawable.baseline_info_24;
        switch (id) {
            case FILTER: return R.drawable.baseline_filter_list_24;
            case SEARCH:
            case XML_SEARCH: return R.drawable.baseline_search_24;
            case SEARCH_RESULTS: return R.drawable.ic_eye_mt;
            case CLEAR_SEARCH: return R.drawable.close_24px;
            case SELECT_ALL: return R.drawable.baseline_select_all_24;
            case SORT: return R.drawable.baseline_sort_24;
            case COMPARE_FOLDERS: return R.drawable.baseline_compare_24;
            case SHOW_SYSTEM_HIDDEN:
            case SHOW_MANUAL_HIDDEN: return R.drawable.visibility_off_24px;
            case SHOW_FOLDER_SIZE: return R.drawable.folder_24px;
            case ADD_BOOKMARK: return R.drawable.baseline_bookmark_24;
            case SET_AS_HOME: return R.drawable.baseline_home_24;
            case SWAP_PANES: return R.drawable.baseline_swap_horiz_24;
            case PREFERENCES: return R.drawable.baseline_settings_24;
            default: return R.drawable.baseline_info_24;
        }
    }
}

package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.AppGson;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AppDetailsConfig {

    public static final String PREF_NAME = "app_details_config";
    public static final String KEY_ORDER = "details_order";
    public static final String KEY_HIDDEN = "details_hidden";

    // Item category
    public enum ItemCategory {
        DETAIL,
        ACTION
    }

    public static class ConfigItem {
        public final String id;
        public final int labelResId;
        public final int defaultIconResId;
        public final ItemCategory category;
        public final boolean isExpandablePath;

        public ConfigItem(String id, int labelResId, int defaultIconResId, ItemCategory category, boolean isExpandablePath) {
            this.id = id;
            this.labelResId = labelResId;
            this.defaultIconResId = defaultIconResId;
            this.category = category;
            this.isExpandablePath = isExpandablePath;
        }
    }

    public static final List<ConfigItem> ALL_ITEMS = Arrays.asList(
            // --- Basic Details ---
            new ConfigItem("detail:version", R.string.version_name, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:apk_size", R.string.apk_size, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:signature", R.string.signature, R.drawable.ic_verified_user, ItemCategory.DETAIL, false),
            new ConfigItem("detail:first_install", R.string.first_install_date, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:last_updated", R.string.last_updated_date, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:apk_dir", R.string.apk_dir, R.drawable.folder_24px, ItemCategory.DETAIL, true),
            new ConfigItem("detail:data_dir", R.string.data_dir, R.drawable.folder_24px, ItemCategory.DETAIL, true),
            new ConfigItem("detail:ext_data", R.string.ext_data_dir, R.drawable.folder_24px, ItemCategory.DETAIL, true),
            new ConfigItem("detail:target_sdk", R.string.target_sdk, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:min_sdk", R.string.min_sdk, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:installer", R.string.installer, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:initiator", R.string.install_initiator, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:origin", R.string.install_origin, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),
            new ConfigItem("detail:status", R.string.status_label, R.drawable.baseline_info_24, ItemCategory.DETAIL, false),

            // --- Standard Actions ---
            new ConfigItem("action:extract", R.string.extract_apk, R.drawable.save_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:share", R.string.share_apk, R.drawable.baseline_share_24, ItemCategory.ACTION, false),
            new ConfigItem("action:play_store", R.string.open_in_play_store, R.drawable.baseline_open_in_new_24, ItemCategory.ACTION, false),
            new ConfigItem("action:launch", R.string.launch, R.drawable.baseline_open_in_new_24, ItemCategory.ACTION, false),
            new ConfigItem("action:app_info", R.string.app_info, R.drawable.baseline_info_24, ItemCategory.ACTION, false),
            new ConfigItem("action:uninstall", R.string.uninstall, R.drawable.baseline_delete_24, ItemCategory.ACTION, false),

            // --- Extraction & Extra Actions (formerly under toggles) ---
            new ConfigItem("action:extract_icon", R.string.extract_icon, R.drawable.image_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:extract_res", R.string.extract_resources, R.drawable.inventory_2_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:extract_dex", R.string.extract_dex, R.drawable.baseline_folder_zip_24, ItemCategory.ACTION, false),
            new ConfigItem("action:extract_manifest", R.string.extract_manifest, R.drawable.baseline_text_snippet_24, ItemCategory.ACTION, false),
            new ConfigItem("action:extract_base", R.string.extract_base_apk, R.drawable.apk_document_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:extract_split", R.string.choose_split_apk, R.drawable.baseline_arrow_drop_down_24, ItemCategory.ACTION, false),
            new ConfigItem("action:extract_libs", R.string.extract_libs, R.drawable.baseline_folder_zip_24, ItemCategory.ACTION, false),
            new ConfigItem("action:launch_activity", R.string.launch_activity, R.drawable.baseline_open_in_new_24, ItemCategory.ACTION, false),

            // --- Folder Open Actions ---
            new ConfigItem("action:open_apk_dir", R.string.open_apk_dir, R.drawable.folder_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:open_data_dir", R.string.open_data_dir, R.drawable.folder_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:open_ext_data_dir", R.string.open_ext_data_dir, R.drawable.folder_24px, ItemCategory.ACTION, false),

            // --- Elevated Actions ---
            new ConfigItem("action:clear_data", R.string.clear_app_data, R.drawable.baseline_delete_24, ItemCategory.ACTION, false),
            new ConfigItem("action:force_stop", R.string.force_stop, R.drawable.stop_circle_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:enable_app", R.string.enable_app, R.drawable.baseline_check_circle_24, ItemCategory.ACTION, false),
            new ConfigItem("action:disable_app", R.string.disable_app, R.drawable.visibility_off_24px, ItemCategory.ACTION, false),
            new ConfigItem("action:uninstall_root", R.string.uninstall_roots, R.drawable.ic_delete, ItemCategory.ACTION, false)
    );

    // Default hidden items: the toggled functions + seldom needed fields
    public static final Set<String> DEFAULT_HIDDEN = new HashSet<>(Arrays.asList(
            "detail:initiator",
            "detail:origin",
            "action:extract_icon",
            "action:extract_res",
            "action:extract_dex",
            "action:extract_manifest",
            "action:extract_base",
            "action:extract_split",
            "action:extract_libs",
            "action:launch_activity"
    ));

    public static ConfigItem findItem(String id) {
        for (ConfigItem item : ALL_ITEMS) {
            if (item.id.equals(id)) return item;
        }
        return null;
    }

    public static List<String> getOrder(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ORDER, null);
        List<String> list = new ArrayList<>();
        if (json != null) {
            try {
                Type type = new TypeToken<List<String>>() {}.getType();
                List<String> loaded = AppGson.get().fromJson(json, type);
                if (loaded != null) list.addAll(loaded);
            } catch (Exception ignored) {}
        }
        // Fill missing items
        for (ConfigItem item : ALL_ITEMS) {
            if (!list.contains(item.id)) list.add(item.id);
        }
        return list;
    }

    public static void setOrder(Context context, List<String> order) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_ORDER, AppGson.get().toJson(order)).apply();
    }

    public static Set<String> getHiddenKeys(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet(KEY_HIDDEN, null);
        if (saved == null) {
            return new HashSet<>(DEFAULT_HIDDEN);
        }
        return new HashSet<>(saved);
    }

    public static void setHiddenKeys(Context context, Set<String> hidden) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_HIDDEN, new HashSet<>(hidden)).apply();
    }

    public static boolean isHidden(Context context, String id) {
        return getHiddenKeys(context).contains(id);
    }

    public static void toggleHidden(Context context, String id) {
        Set<String> set = getHiddenKeys(context);
        if (set.contains(id)) set.remove(id);
        else set.add(id);
        setHiddenKeys(context, set);
    }
}

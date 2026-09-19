package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;

public final class FileMenuOrder {

    public static final String COPY = "copy";
    public static final String MOVE = "move";
    public static final String RENAME = "rename";
    public static final String DELETE = "delete";
    public static final String COMPRESS = "compress";
    public static final String PROPERTIES = "properties";
    public static final String SHARE = "share";
    public static final String OPEN_WITH = "open_with";
    public static final String BOOKMARK = "bookmark";
    public static final String CMD = "cmd";
    public static final String CHECK = "check";
    public static final String BATCH_SIGN = "batch_sign";
    public static final String BATCH_OPT = "batch_opt";
    public static final String BATCH_INSTALL = "batch_install";
    public static final String EXTRACT = "extract";
    public static final String CMP_ZIP = "cmp_zip";
    public static final String CMP_ARSC = "cmp_arsc";
    public static final String CMP_TEXT = "cmp_text";
    public static final String CMP_HASH = "cmp_hash";
    public static final String CMP_APK = "cmp_apk";
    public static final String BATCH_CROP = "batch_crop";
    public static final String BATCH_EXIF = "batch_exif";
    public static final String BATCH_STRIP_META = "batch_strip_meta";

    public static final String[] DEFAULT_ORDER = {
            COPY, MOVE, RENAME, DELETE, COMPRESS, PROPERTIES, SHARE, OPEN_WITH,
            BOOKMARK, CMD, CHECK, EXTRACT, BATCH_SIGN, BATCH_OPT, BATCH_INSTALL,
            CMP_ZIP, CMP_ARSC, CMP_TEXT, CMP_HASH, CMP_APK,
            BATCH_CROP, BATCH_EXIF, BATCH_STRIP_META
    };

    private FileMenuOrder() {
    }

    public static List<String> load(Context context) {
        List<String> order = new ArrayList<>();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String saved = prefs.getString("filemenu_order", "");
            if (saved != null && !saved.isEmpty()) {
                for (String id : saved.split(",")) {
                    id = id.trim();
                    if (!id.isEmpty() && !order.contains(id)) order.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        for (String id : DEFAULT_ORDER) {
            if (!order.contains(id)) order.add(id);
        }
        return order;
    }

    public static void save(Context context, List<String> order) {
        try {
            StringBuilder sb = new StringBuilder();
            for (String id : order) {
                if (sb.length() > 0) sb.append(',');
                sb.append(id);
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString("filemenu_order", sb.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static List<MenuItem> sortItems(Context context, List<MenuItem> visible) {
        List<String> order = load(context);
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < order.size(); i++) rank.put(order.get(i), i);
        List<MenuItem> sorted = new ArrayList<>(visible);
        sorted.sort((a, b) -> {
            int ra = rank.containsKey(a.id) ? rank.get(a.id) : 999;
            int rb = rank.containsKey(b.id) ? rank.get(b.id) : 999;
            return Integer.compare(ra, rb);
        });
        return sorted;
    }

    public static String labelFor(Context context, String id, String direction) {
        switch (id) {
            case COPY:
                return "Copy " + direction;
            case MOVE:
                return "Move " + direction;
            case RENAME:
                return "Rename";
            case DELETE:
                return "Delete";
            case COMPRESS:
                return "Compress";
            case PROPERTIES:
                return "Properties";
            case SHARE:
                return "Share";
            case OPEN_WITH:
                return "Open with";
            case BOOKMARK:
                return "Bookmark";
            case CMD:
                return "Command Helper";
            case CHECK:
                return context.getString(R.string.checksums);
            case BATCH_SIGN:
                return context.getString(R.string.batch_sign);
            case BATCH_OPT:
                return context.getString(R.string.batch_optimize);
            case BATCH_INSTALL:
                return context.getString(R.string.batch_install);
            case EXTRACT:
                return context.getString(R.string.extract);
            case CMP_ZIP:
                return "Compare ZIP";
            case CMP_ARSC:
                return "Compare ARSC";
            case CMP_TEXT:
                return "Compare Text";
            case CMP_HASH:
                return context.getString(R.string.compare_hashes);
            case CMP_APK:
                return context.getString(R.string.compare_apks);
            case BATCH_CROP:
                return "Crop images";
            case BATCH_EXIF:
                return "Set EXIF tags";
            case BATCH_STRIP_META:
                return "Remove metadata";
            default:
                return id;
        }
    }

    public static int iconFor(Context context, String id, boolean moveDisabled, boolean zipDisabled) {
        switch (id) {
            case COPY:
                return R.drawable.baseline_content_copy_24;
            case MOVE:
                return R.drawable.baseline_content_cut_24;
            case RENAME:
                return R.drawable.baseline_drive_file_rename_outline_24;
            case DELETE:
                return R.drawable.baseline_delete_24;
            case COMPRESS:
                return R.drawable.baseline_compress_24;
            case PROPERTIES:
                return R.drawable.baseline_info_24;
            case SHARE:
                return R.drawable.baseline_share_24;
            case OPEN_WITH:
                return R.drawable.baseline_open_in_new_24;
            case BOOKMARK:
                return android.R.drawable.ic_input_get;
            case CMD:
                return R.drawable.terminal_24px;
            case CHECK:
                return R.drawable.tag_24px;
            case BATCH_SIGN:
            case BATCH_OPT:
            case BATCH_INSTALL:
                return R.drawable.apk_document_24px;
            case EXTRACT:
                return R.drawable.baseline_compress_24;
            case CMP_ZIP:
            case CMP_ARSC:
                return R.drawable.baseline_swap_horiz_24;
            case CMP_TEXT:
            case CMP_HASH:
            case CMP_APK:
                return R.drawable.baseline_swap_horiz_24;
            case BATCH_CROP:
                return R.drawable.edit_24px;
            case BATCH_EXIF:
                return R.drawable.baseline_text_snippet_24;
            case BATCH_STRIP_META:
                return R.drawable.baseline_delete_24;
            default:
                return 0;
        }
    }

    public static class MenuItem {
        public final String id;
        public final String label;

        public MenuItem(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}

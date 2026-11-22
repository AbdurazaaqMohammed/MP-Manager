package io.github.abdurazaaqmohammed.plugins.ipc;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Ordering and visibility for native hook slots without an existing menu customizer. */
public final class HookOrder {
    private static final String PREFS = "plugin_hook_order";
    private static final Gson GSON = new Gson();

    private HookOrder() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static List<String> read(Context context, String key) {
        try {
            String[] values = GSON.fromJson(prefs(context).getString(key, "[]"), String[].class);
            List<String> out = new ArrayList<>();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !out.contains(value)) out.add(value);
                }
            }
            return out;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public static boolean isHidden(Context context, String hookId, String id) {
        return read(context, hookId + ".hidden").contains(id);
    }

    public static List<ExternalActions.Entry> sort(Context context, String hookId,
                                                   List<ExternalActions.Entry> entries) {
        List<ExternalActions.Entry> out = new ArrayList<>(entries);
        List<String> order = read(context, hookId + ".order");
        out.sort(Comparator.comparingInt((ExternalActions.Entry entry) -> {
            int index = order.indexOf(entry.id);
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparing(entry -> entry.id));
        return out;
    }

    public static void setHidden(Context context, String hookId, String id, boolean hidden) {
        List<String> ids = read(context, hookId + ".hidden");
        ids.remove(id);
        if (hidden) ids.add(id);
        prefs(context).edit().putString(hookId + ".hidden", GSON.toJson(ids)).apply();
        ExternalActions.clearAllCaches();
    }

    public static void move(Context context, String hookId, String id, int direction) {
        List<String> order = read(context, hookId + ".order");
        for (ExternalActions.Entry entry : HookRegistry.allEntries(context, hookId)) {
            if (!order.contains(entry.id)) order.add(entry.id);
        }
        int from = order.indexOf(id);
        int to = from + direction;
        if (from >= 0 && to >= 0 && to < order.size()) Collections.swap(order, from, to);
        prefs(context).edit().putString(hookId + ".order", GSON.toJson(order)).apply();
    }

    /** Per-plugin controls supplement the existing sidebar/file/editor/APK order screens. */
    public static void showManager(Activity activity, String packageName) {
        List<ExternalActions.Entry> entries = HookRegistry.packageEntries(activity, packageName);
        if (entries.isEmpty()) return;
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            ExternalActions.Entry entry = entries.get(i);
            labels[i] = activity.getString(isHidden(activity, entry.hookId, entry.id)
                            ? io.github.abdurazaaqmohammed.MPManager.R.string.plugin_action_hidden
                            : io.github.abdurazaaqmohammed.MPManager.R.string.plugin_action_slot,
                    entry.title, entry.hookId);
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(io.github.abdurazaaqmohammed.MPManager.R.string.plugin_hook_actions)
                .setItems(labels, (dialog, which) -> {
                    ExternalActions.Entry entry = entries.get(which);
                    boolean hidden = isHidden(activity, entry.hookId, entry.id);
                    List<String> actions = new ArrayList<>();
                    actions.add(activity.getString(hidden
                            ? io.github.abdurazaaqmohammed.MPManager.R.string.plugin_show_action
                            : io.github.abdurazaaqmohammed.MPManager.R.string.plugin_hide_action));
                    if (PluginContracts.HOOK_TOOLBAR_ACTIONS.equals(entry.hookId)
                            || PluginContracts.HOOK_ELF_DIALOG_EXTRA.equals(entry.hookId)) {
                        actions.add(activity.getString(io.github.abdurazaaqmohammed.MPManager.R.string.plugin_move_up));
                        actions.add(activity.getString(io.github.abdurazaaqmohammed.MPManager.R.string.plugin_move_down));
                    }
                    new MaterialAlertDialogBuilder(activity).setTitle(entry.title)
                            .setItems(actions.toArray(new String[0]), (d, action) -> {
                                if (action == 0) setHidden(activity, entry.hookId, entry.id, !hidden);
                                else move(activity, entry.hookId, entry.id, action == 1 ? -1 : 1);
                                showManager(activity, packageName);
                            }).setNegativeButton(android.R.string.cancel, null).show();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    public static void removePackage(Context context, String packageName) {
        String prefix = "ext:" + packageName + "/";
        SharedPreferences.Editor editor = prefs(context).edit();
        for (String key : prefs(context).getAll().keySet()) {
            List<String> ids = read(context, key);
            if (ids.removeIf(id -> id.startsWith(prefix))) editor.putString(key, GSON.toJson(ids));
        }
        editor.apply();
    }

}

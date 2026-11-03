package io.github.abdurazaaqmohammed.features.apk;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.ext.ApkMoreAction;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;

public final class ApkMoreMenuOrder {

    public static final String SIGN = "sign";
    public static final String OPT = "opt";
    public static final String DECOMPILE = "decompile";
    public static final String REFACTOR = "refactor";
    public static final String PROTECT = "protect";
    public static final String CLONE = "clone";
    public static final String ADD_TOAST = "add_toast";
    public static final String REMOVE_TOASTS = "remove_toasts";
    public static final String REMOVE_SIG = "remove_sig";
    public static final String SIG_HEALTH = "sig_health";
    public static final String MANIFEST_TOGGLES = "manifest_toggles";
    public static final String PERMISSIONS = "permissions";
    public static final String NPATCH = "npatch";
    public static final String MULTIPLEX = "multiplex";
    public static final String IL2CPP = "il2cpp";
    public static final String PATCH_LIBS = "patch_libs";
    public static final String SSL_UNPIN = "ssl_unpin";

    public static final String[] DEFAULT_ORDER = {
            SIGN, OPT, DECOMPILE, REFACTOR, PROTECT, CLONE,
            ADD_TOAST, REMOVE_TOASTS, REMOVE_SIG, SIG_HEALTH,
            MANIFEST_TOGGLES, PERMISSIONS, NPATCH, MULTIPLEX,
            IL2CPP, PATCH_LIBS, SSL_UNPIN
    };

    public record MenuItem(String id, String label) {}

    private ApkMoreMenuOrder() {}

    public static List<String> load(Context context) {
        List<String> order = new ArrayList<>();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String saved = prefs.getString("apk_more_menu_order", "");
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

        try {
            for (ApkMoreAction ext : ExtensionRegistry.apkActions()) {
                if (ext != null) {
                    String id = ext.id();
                    if (id != null && !order.contains(id)) order.add(id);
                }
            }
        } catch (Exception ignored) {}

        try {
            for (ExternalActions.Entry e : ExternalActions.apkEntries(context)) {
                if (e != null && e.id != null && !order.contains(e.id)) order.add(e.id);
            }
        } catch (Exception ignored) {}

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
                .putString("apk_more_menu_order", sb.toString()).apply();
    }

    public static Set<String> getHiddenIds(Context context) {
        try {
            Set<String> set = PreferenceManager.getDefaultSharedPreferences(context)
                    .getStringSet("apk_more_menu_hidden_ids", null);
            if (set != null) return new HashSet<>(set);
        } catch (Exception ignored) {}
        return new HashSet<>();
    }

    public static void setHiddenIds(Context context, Set<String> hiddenIds) {
        try {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putStringSet("apk_more_menu_hidden_ids", hiddenIds == null ? new HashSet<>() : hiddenIds)
                    .apply();
        } catch (Exception ignored) {}
    }

    public static String labelFor(Context context, String id) {
        if (id == null) return "";
        switch (id) {
            case SIGN: return context.getString(R.string.sign_apk);
            case OPT: return context.getString(R.string.optimize_apk);
            case DECOMPILE: return context.getString(R.string.decompile_reandroid_apkeditor);
            case REFACTOR: return context.getString(R.string.refactor_obfuscated_resource_names);
            case PROTECT: return context.getString(R.string.protect_reandroid_apkeditor);
            case CLONE: return context.getString(R.string.clone_apk);
            case ADD_TOAST: return context.getString(R.string.add_toast_dialog);
            case REMOVE_TOASTS: return context.getString(R.string.remove_all_toasts);
            case REMOVE_SIG: return context.getString(R.string.remove_signature);
            case SIG_HEALTH: return context.getString(R.string.signature_health);
            case MANIFEST_TOGGLES: return context.getString(R.string.manifest_toggles);
            case PERMISSIONS: return context.getString(R.string.permissions);
            case NPATCH: return context.getString(R.string.npatch_integrated);
            case MULTIPLEX: return context.getString(R.string.multiplex_apk);
            case IL2CPP: return context.getString(R.string.il2cpp_dump_native);
            case PATCH_LIBS: return context.getString(R.string.patch_native_libs);
            case SSL_UNPIN: return context.getString(R.string.ssl_unpin_title);
            default: {
                for (ApkMoreAction ext : ExtensionRegistry.apkActions()) {
                    if (ext != null && id.equals(ext.id())) {
                        return ext.title() == null || ext.title().isEmpty() ? ext.id() : ext.title();
                    }
                }
                for (ExternalActions.Entry e : ExternalActions.apkEntries(context)) {
                    if (e != null && id.equals(e.id)) {
                        return e.title == null || e.title.isEmpty() ? e.id : e.title;
                    }
                }
                return id;
            }
        }
    }

    public static int iconFor(String id) {
        if (id == null) return R.drawable.baseline_info_24;
        switch (id) {
            case SIGN: return R.drawable.baseline_check_circle_24;
            case OPT: return R.drawable.gavel_24px;
            case DECOMPILE: return R.drawable.code_24px;
            case REFACTOR: return R.drawable.baseline_drive_file_rename_outline_24;
            case PROTECT: return R.drawable.lock_24px;
            case CLONE: return R.drawable.control_point_duplicate_24px;
            case ADD_TOAST: return R.drawable.add_24px;
            case REMOVE_TOASTS: return R.drawable.baseline_remove_circle_24;
            case REMOVE_SIG: return R.drawable.lock_open_24px;
            case SIG_HEALTH: return R.drawable.baseline_info_24;
            case MANIFEST_TOGGLES: return R.drawable.baseline_settings_24;
            case PERMISSIONS: return R.drawable.baseline_info_24;
            case NPATCH: return R.drawable.tools_24px;
            case MULTIPLEX: return R.drawable.stacks_24px;
            case IL2CPP: return R.drawable.terminal_24px;
            case PATCH_LIBS: return R.drawable.tools_24px;
            case SSL_UNPIN: return R.drawable.lock_open_24px;
            default: return R.drawable.baseline_info_24;
        }
    }
}

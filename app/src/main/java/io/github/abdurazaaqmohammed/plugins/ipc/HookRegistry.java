package io.github.abdurazaaqmohammed.plugins.ipc;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Manifest-only contributions. Discovering a hook never executes plugin code. */
public final class HookRegistry {
    private static final long CACHE_TTL_MS = 60_000;
    private static final int MAX_ENTRIES = 256;
    private static final int MAX_PACKAGE_ENTRIES = 32;
    private static long cachedAt;
    private static List<ExternalActions.Entry> cached;

    private HookRegistry() {
    }

    public static synchronized void invalidate() {
        cached = null;
        cachedAt = 0;
    }

    public static List<ExternalActions.Entry> entries(Context context, String hookId) {
        List<ExternalActions.Entry> out = new ArrayList<>();
        if (!PluginHooks.isActionHook(hookId)) return out;
        for (ExternalActions.Entry entry : all(context)) {
            if (hookId.equals(entry.hookId)
                    && !PluginHost.isDisabled(context, entry.plugin.packageName)
                    && !HookOrder.isHidden(context, hookId, entry.id)) out.add(entry);
        }
        return out;
    }

    public static List<ExternalActions.Entry> allEntries(Context context, String hookId) {
        List<ExternalActions.Entry> out = new ArrayList<>();
        if (!PluginHooks.isActionHook(hookId)) return out;
        for (ExternalActions.Entry entry : all(context)) {
            if (hookId.equals(entry.hookId)) out.add(entry);
        }
        return out;
    }

    public static List<ExternalActions.Entry> visibleEntries(Context context, String hookId) {
        return HookOrder.sort(context, hookId, entries(context, hookId));
    }

    /** Includes hidden and disabled contributions for management, but not unknown hooks. */
    public static List<ExternalActions.Entry> packageEntries(Context context, String packageName) {
        List<ExternalActions.Entry> out = new ArrayList<>();
        for (ExternalActions.Entry entry : all(context)) {
            if (entry.plugin.packageName.equals(packageName)) out.add(entry);
        }
        return out;
    }

    private static synchronized List<ExternalActions.Entry> all(Context context) {
        long now = SystemClock.elapsedRealtime();
        if (cached != null && now - cachedAt < CACHE_TTL_MS) return new ArrayList<>(cached);
        List<ExternalActions.Entry> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        java.util.Map<String, Integer> packageCounts = new java.util.HashMap<>();
        List<PluginHost.ExternalPlugin> plugins = PluginHost.queryIncludingDisabled(
                context, PluginContracts.ACTION_HOOK);
        plugins.sort(Comparator.comparing((PluginHost.ExternalPlugin p) -> p.packageName)
                .thenComparing(p -> p.className));
        for (PluginHost.ExternalPlugin plugin : plugins) {
            if (!supportsVersion(plugin)) continue;
            for (String hook : declaredHooks(plugin)) {
                if (!PluginHooks.isActionHook(hook)) continue;
                int count = packageCounts.getOrDefault(plugin.packageName, 0);
                if (out.size() >= MAX_ENTRIES || count >= MAX_PACKAGE_ENTRIES) break;
                String id = PluginHooks.entryId(plugin.packageName, plugin.pluginId,
                        plugin.className, hook);
                if (id == null || !seen.add(id)) continue;
                String title = title(plugin);
                out.add(new ExternalActions.Entry(plugin, id, title,
                        PluginContracts.ACTION_HOOK, hook));
                packageCounts.put(plugin.packageName, count + 1);
            }
        }
        cached = Collections.unmodifiableList(out);
        cachedAt = now;
        return new ArrayList<>(out);
    }

    public static boolean supportsVersion(PluginHost.ExternalPlugin plugin) {
        try {
            Bundle meta = plugin.meta;
            Object value = meta == null ? null : meta.get(PluginContracts.META_CONTRACT_VERSION);
            if (value == null) return true;
            if (value instanceof Integer) return ((Integer) value) == PluginContracts.CONTRACT_VERSION;
            return value instanceof String
                    && String.valueOf(PluginContracts.CONTRACT_VERSION).equals(value);
        } catch (Exception ignored) {
            return false;
        }
    }

    static List<String> declaredHooks(PluginHost.ExternalPlugin plugin) {
        return PluginHooks.parse(ExternalActions.metaString(plugin, PluginContracts.META_HOOKS, ""));
    }

    private static String title(PluginHost.ExternalPlugin plugin) {
        String title = ExternalActions.metaString(plugin, PluginContracts.META_TITLE, "");
        if (title.isEmpty()) title = plugin.label == null ? plugin.packageName : plugin.label.toString();
        return PluginMetadata.title(title, plugin.packageName);
    }
}

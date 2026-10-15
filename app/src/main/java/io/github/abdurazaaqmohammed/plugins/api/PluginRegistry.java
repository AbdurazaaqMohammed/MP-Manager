package io.github.abdurazaaqmohammed.plugins.api;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import io.github.abdurazaaqmohammed.tools.ToolRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates custom ToolPlugins plus a bridge over legacy ToolRegistry,
 * so ToolsHub/ToolRunner can migrate incrementally: custom plugins first,
 * legacy ids fall back to ToolRegistry metadata.
 */
public final class PluginRegistry {

    private static final Map<String, ToolPlugin> CUSTOM = new LinkedHashMap<>();

    /** toolId -> owning packId; empty for non-pack (host-registered) tools. */
    private static final Map<String, String> PACK_OF = new LinkedHashMap<>();

    private PluginRegistry() {
    }

    public static synchronized void register(ToolPlugin plugin) {
        if (plugin == null || plugin.id() == null) return;
        CUSTOM.put(plugin.id(), plugin);
    }

    /** Register a pack tool together with its owning pack id. */
    public static synchronized void register(ToolPlugin plugin, String packId) {
        if (plugin == null || plugin.id() == null) return;
        CUSTOM.put(plugin.id(), plugin);
        if (packId != null && !packId.isEmpty()) PACK_OF.put(plugin.id(), packId);
    }

    public static synchronized void unregister(String id) {
        CUSTOM.remove(id);
        PACK_OF.remove(id);
    }

    /** Distinct loaded packs (in registration order) and their tools. */
    public static synchronized List<PackTools> installedPacks() {
        List<PackTools> packs = new ArrayList<>();
        for (ToolPlugin plugin : CUSTOM.values()) {
            String packId = PACK_OF.get(plugin.id());
            if (packId == null) continue;
            PackTools pack = null;
            for (PackTools existing : packs) {
                if (existing.packId.equals(packId)) {
                    pack = existing;
                    break;
                }
            }
            if (pack == null) {
                pack = new PackTools(packId);
                packs.add(pack);
            }
            pack.tools.add(plugin);
        }
        return packs;
    }

    /** A loaded pack and the tools it contributed to the registry. */
    public static final class PackTools {
        public final String packId;
        public final List<ToolPlugin> tools = new ArrayList<>();

        PackTools(String packId) {
            this.packId = packId;
        }
    }

    /** Custom plugins only (new architecture). */
    public static synchronized List<ToolPlugin> customPlugins() {
        return new ArrayList<>(CUSTOM.values());
    }

    /** Custom plugin by id, or null when still on the legacy path. */
    public static synchronized ToolPlugin findCustom(String id) {
        if (id == null) return null;
        return CUSTOM.get(id);
    }

    /** Find custom plugin, else bridge to ToolRegistry entry. */
    public static ToolPlugin findById(final Context context, String id) {
        if (id == null) return null;
        synchronized (PluginRegistry.class) {
            ToolPlugin custom = CUSTOM.get(id);
            if (custom != null) return custom;
        }
        final ToolRegistry.ToolItem item = ToolRegistry.findById(context, id);
        if (item == null) return null;
        return new BaseToolPlugin(item.id(), item.title(), item.subtitle(), item.category()) {
            @Override public int iconRes(Context ctx) { return item.iconRes(); }
            @Override public View createView(Context ctx, ViewGroup container) {
                // Legacy tools are still rendered by ToolRunnerActivity branches.
                // Returning null signals "use legacy path".
                return null;
            }
        };
    }

    public static String[] categoriesInOrder(Context context) {
        return ToolRegistry.categoriesInOrder(context);
    }
}

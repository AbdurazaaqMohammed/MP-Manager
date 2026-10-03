package io.github.abdurazaaqmohammed.plugins.api;

import android.content.Context;

import io.github.abdurazaaqmohammed.MPManager.R;
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

    private PluginRegistry() {
    }

    public static synchronized void register(ToolPlugin plugin) {
        if (plugin == null || plugin.id() == null) return;
        CUSTOM.put(plugin.id(), plugin);
    }

    public static synchronized void unregister(String id) {
        CUSTOM.remove(id);
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
            @Override public android.view.View createView(Context ctx, android.view.ViewGroup container) {
                // Legacy tools are still rendered by ToolRunnerActivity branches.
                // Returning null signals "use legacy path".
                return null;
            }
        };
    }

    public static String[] categoriesInOrder() {
        return ToolRegistry.categoriesInOrder();
    }

    /**
     * Display name for a {@code ToolCategories} constant.
     *
     * <p>The constants stay English on purpose: they are used as map keys and
     * compared by {@code ToolsHubActivity.buildRows}, so localizing them in
     * place would break grouping. Only the rendered label is localized.
     * An unknown category is returned unchanged.
     */
    public static String categoryLabel(Context context, String category) {
        if (context == null || category == null) return category;
        int res;
        switch (category) {
            case ToolCategories.NETWORK:
                res = R.string.cat_network; break;
            case ToolCategories.STORAGE:
                res = R.string.cat_storage; break;
            case ToolCategories.DEVICE:
                res = R.string.cat_device; break;
            case ToolCategories.MATH:
                res = R.string.cat_math; break;
            case ToolCategories.TIME:
                res = R.string.cat_time; break;
            case ToolCategories.TEXT:
                res = R.string.cat_text; break;
            case ToolCategories.MEDIA:
                res = R.string.cat_media; break;
            case ToolCategories.RAND:
                res = R.string.cat_random; break;
            default: return category;
        }
        return context.getString(res);
    }
}

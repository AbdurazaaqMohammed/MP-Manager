package io.github.abdurazaaqmohammed.feature.system;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure rules for the sidebar's explicit tool selection: the persistent set of
 * tools added from the Tools Hub, canonical ids, hide keys shared with
 * {@code SidebarAdapter}, and the render state those combine into. Free of
 * Android types so the semantics stay unit-testable.
 */
public final class SidebarToolSelection {

    /** Preference holding the ids of tools explicitly added to the sidebar from the hub. */
    public static final String PREF_ADDED_TOOLS = "sidebar_added_tools";

    /** Sidebar row id prefix for installed pack tools. */
    public static final String PACK_TOOL_PREFIX = "packtool:";

    /** Sidebar row id prefix for host-registered custom (non-pack) plugin tools. */
    public static final String CUSTOM_TOOL_PREFIX = "custom:";

    /** Hidden-item key prefix for tool rows, matching {@code SidebarAdapter.entryKey}. */
    public static final String HIDDEN_KEY_PREFIX = "TOOL\u0001";

    private SidebarToolSelection() {}

    /** Canonical, non-blank added ids; legacy aliases are upgraded in place. */
    public static Set<String> sanitizeAdded(Set<String> raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) return out;
        for (String id : raw) {
            if (id == null || id.trim().isEmpty()) continue;
            out.add(ToolRegistry.canonicalId(id.trim()));
        }
        return out;
    }

    /** True when {@code id} is in the explicit added set (canonicalized). */
    public static boolean isAdded(Set<String> added, String id) {
        return added != null && id != null && added.contains(ToolRegistry.canonicalId(id));
    }

    /** Adds {@code id} if absent; returns true when the set changed. Never removes. */
    public static boolean ensureAdded(Set<String> added, String id) {
        if (added == null || id == null || id.trim().isEmpty()) return false;
        String canonical = ToolRegistry.canonicalId(id.trim());
        if (added.contains(canonical)) return false;
        added.add(canonical);
        return true;
    }

    /** Saved drag order ids, canonicalized and deduped; blanks dropped. */
    public static List<String> canonicalOrder(List<String> savedOrder) {
        List<String> out = new ArrayList<>();
        if (savedOrder == null) return out;
        for (String id : savedOrder) {
            if (id == null || id.trim().isEmpty()) continue;
            String canonical = ToolRegistry.canonicalId(id.trim());
            if (!out.contains(canonical)) out.add(canonical);
        }
        return out;
    }

    /**
     * Render order: the saved custom order first (it wins), then section
     * defaults, then unordered explicit adds, so a fresh add lands at the
     * end and round-trips through the saved order on the next drag.
     */
    public static List<String> mergeOrder(List<String> savedOrder, List<String> defaults, Set<String> added) {
        List<String> out = canonicalOrder(savedOrder);
        if (defaults != null) {
            for (String id : defaults) {
                if (id != null && !out.contains(id)) out.add(id);
            }
        }
        for (String id : sanitizeAdded(added)) {
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    /** Hidden-item key for a tool row, as stored in {@code sidebar_hidden_items}. */
    public static String hiddenKey(String id) {
        return HIDDEN_KEY_PREFIX + id;
    }

    /**
     * Stored hidden ids canonicalized: legacy aliases under the {@code TOOL}
     * prefix are upgraded so hides survive the id migration. Other keys
     * (e.g. {@code settings}, pinned rows) pass through untouched.
     */
    public static Set<String> canonicalizeHidden(Set<String> raw) {
        Set<String> out = new HashSet<>();
        if (raw == null) return out;
        for (String key : raw) {
            if (key == null) continue;
            if (key.startsWith(HIDDEN_KEY_PREFIX)) {
                out.add(hiddenKey(ToolRegistry.canonicalId(key.substring(HIDDEN_KEY_PREFIX.length()))));
            } else {
                out.add(key);
            }
        }
        return out;
    }

    /**
     * Whether a row's membership is governed by the explicit added set rather
     * than being shipped (default registry tools) or automatic (extension
     * actions, pack rows while the plugins setting is on).
     */
    public static boolean membershipIsExplicit(String rowId, boolean registryTool, boolean shippedDefault) {
        if (rowId == null) return false;
        if (registryTool) return !shippedDefault;
        return rowId.startsWith(PACK_TOOL_PREFIX) || rowId.startsWith(CUSTOM_TOOL_PREFIX);
    }

    /**
     * Render state the sidebar shows: shipped/automatic membership or an
     * explicit add, and not organize-hidden. {@code autoShown} is true for
     * default registry tools and extensions, and mirrors the "show plugins"
     * setting for pack rows; custom rows only render once added.
     */
    public static boolean isRendered(boolean explicitMembership, boolean added, boolean autoShown, boolean hidden) {
        return (autoShown || (explicitMembership && added)) && !hidden;
    }
}

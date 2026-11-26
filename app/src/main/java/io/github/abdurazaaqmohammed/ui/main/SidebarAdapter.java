package io.github.abdurazaaqmohammed.ui.main;

import io.github.abdurazaaqmohammed.util.misc.ColorUtil;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionIcons;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.SidebarAction;
import io.github.abdurazaaqmohammed.util.text.FileSize;
import io.github.abdurazaaqmohammed.util.io.StorageUtil;
import io.github.abdurazaaqmohammed.feature.system.SidebarToolSelection;
import io.github.abdurazaaqmohammed.feature.system.ToolRegistry;

public class SidebarAdapter extends RecyclerView.Adapter<SidebarAdapter.ViewHolder> {

    public enum EntryType {
        HEADER, STORAGE, TOOL, BOOKMARK, PIN
    }

    public record SidebarEntry(EntryType type, String section, String id, String label, int icon,
                               File file, StorageUtil.StorageInfo storage) {

        public String dragPayload() {
                return type.name() + "\u0001" + section + "\u0001" + (id == null ? "" : id);
            }
        }

    private record EntrySnapshot(EntryType type, String section, String id, String label, int icon,
                                 boolean collapsed, boolean organize, boolean hidden,
                                 long totalBytes, long usedBytes, long freeBytes,
                                 String description, boolean animate) {}

    public interface Callbacks {
        void onEntryClicked(SidebarEntry entry, View view);

        void onHeaderToggle(SidebarEntry entry);

        void onEntryStorageLongPressed(SidebarEntry entry, View view);

        void onEntryLongPressed(SidebarEntry entry, View view);

        void onEntryDragHandleTouched(SidebarEntry entry, View view);

        void onEntryHideRequested(SidebarEntry entry);
    }

    private final Context context;
    private final Callbacks callbacks;
    private static final String BOOKMARK_GROUP_PREFIX = "bookmark_group:";
    /** id prefix of a pack tool row inside the plugins section. */
    public static final String PACK_TOOL_PREFIX = SidebarToolSelection.PACK_TOOL_PREFIX;
    private final List<SidebarEntry> entries = new ArrayList<>();
    private List<EntrySnapshot> lastSnapshots = new ArrayList<>();
    private final Map<String, Boolean> collapsed = new LinkedHashMap<>();
    private final List<String> sectionOrder = new ArrayList<>();
    private final List<StorageUtil.StorageInfo> storage = new ArrayList<>();
    private final List<File> defaultBookmarks = new ArrayList<>();
    private final Map<String, List<File>> groups = new LinkedHashMap<>();
    private boolean showBookmarks;
    private boolean showGroups;
    private boolean showPlugins = true;
    private boolean organizeMode;
    private final List<String> toolOrder = new ArrayList<>();
    /** Canonical ids of hub tools the user explicitly added (see SidebarToolSelection). */
    private final Set<String> addedTools = new HashSet<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Set<String> hiddenItems = new HashSet<>();
    private final List<SidebarEntry> pinned = new ArrayList<>();
    private String animateToggleSection;

    public SidebarAdapter(Context context, Callbacks callbacks) {
        this.context = context;
        this.callbacks = callbacks;
        sectionOrder.add("storage");
        sectionOrder.add("bookmarks");
        sectionOrder.add("tools");
        sectionOrder.add("plugins");
    }

    private List<EntrySnapshot> captureSnapshots() {
        List<EntrySnapshot> out = new ArrayList<>(entries.size());
        for (SidebarEntry entry : entries) {
            StorageUtil.StorageInfo info = entry.storage;
            boolean isCollapsed = entry.type == EntryType.HEADER && Boolean.TRUE.equals(collapsed.get(entry.section));
            boolean anim = entry.type == EntryType.HEADER && entry.section.equals(animateToggleSection);
            out.add(new EntrySnapshot(
                    entry.type, entry.section, entry.id, entry.label, entry.icon,
                    isCollapsed, organizeMode, isHidden(entry),
                    info != null ? info.totalBytes : -2L,
                    info != null ? info.usedBytes : -2L,
                    info != null ? info.freeBytes : -2L,
                    info != null ? info.description : null,
                    anim));
        }
        return out;
    }

    private void dispatchEntriesDiff() {
        final List<EntrySnapshot> oldList = lastSnapshots;
        final List<EntrySnapshot> newList = captureSnapshots();
        lastSnapshots = newList;
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return oldList.size(); }

            @Override
            public int getNewListSize() { return newList.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                EntrySnapshot a = oldList.get(oldPos);
                EntrySnapshot b = newList.get(newPos);
                return a.type == b.type && Objects.equals(a.section, b.section) && Objects.equals(a.id, b.id);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return oldList.get(oldPos).equals(newList.get(newPos));
            }
        }).dispatchUpdatesTo(this);
    }

    public void setData(List<String> order, boolean showBookmarks, boolean showGroups,
            List<StorageUtil.StorageInfo> storage, List<File> defaultBookmarks,
            Map<String, List<File>> groups, Map<String, String> labels, boolean showPlugins) {
        sectionOrder.clear();
        if (order != null) {
            for (String section : order) {
                if (section != null && !sectionOrder.contains(section)) sectionOrder.add(section);
            }
        }
        for (String section : new String[]{"storage", "bookmarks", "tools", "plugins"}) {
            if (!sectionOrder.contains(section)) sectionOrder.add(section);
        }
        this.showBookmarks = showBookmarks;
        this.showGroups = showGroups;
        this.showPlugins = showPlugins;
        if (!showGroups) {
            sectionOrder.removeIf(section -> section.startsWith(BOOKMARK_GROUP_PREFIX));
        }
        if (groups != null) {
            for (String group : groups.keySet()) {
                String section = BOOKMARK_GROUP_PREFIX + group;
                if (showGroups && !sectionOrder.contains(section)) sectionOrder.add(section);
            }
        }
        this.storage.clear();
        if (storage != null) this.storage.addAll(storage);
        this.defaultBookmarks.clear();
        if (defaultBookmarks != null) this.defaultBookmarks.addAll(defaultBookmarks);
        this.groups.clear();
        if (groups != null) {
            for (Map.Entry<String, List<File>> entry : groups.entrySet()) {
                this.groups.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        this.labels.clear();
        if (labels != null) this.labels.putAll(labels);
        rebuild();
    }

    public void refreshStorage(List<StorageUtil.StorageInfo> infos) {
        storage.clear();
        if (infos != null) storage.addAll(infos);
        rebuild();
    }

    /**
     * Rows shown after the storage volumes: pinned provider locations and FTP
     * client profiles. Entries come prebuilt from the controller (their labels
     * need provider queries), already typed {@link EntryType#PIN} with the
     * "storage" section.
     */
    public void setPinned(List<SidebarEntry> rows) {
        pinned.clear();
        if (rows != null) pinned.addAll(rows);
        rebuild();
    }

    public void setOrganizeMode(boolean enabled) {
        organizeMode = enabled;
        rebuild();
    }

    public void setHiddenItems(Set<String> items) {
        hiddenItems.clear();
        hiddenItems.addAll(SidebarToolSelection.canonicalizeHidden(items));
    }

    public Set<String> getHiddenItems() {
        return new HashSet<>(hiddenItems);
    }

    /** Explicitly added hub tools (canonical ids, packtool:/custom: prefixed rows). */
    public void setAddedTools(Set<String> ids) {
        addedTools.clear();
        addedTools.addAll(SidebarToolSelection.sanitizeAdded(ids));
    }

    public Set<String> getAddedTools() {
        return new HashSet<>(addedTools);
    }

    public void hideEntry(SidebarEntry entry) {
        if (entry == null || entry.type == EntryType.HEADER) return;
        hiddenItems.add(entryKey(entry));
        rebuild();
    }

    public void unhideEntry(SidebarEntry entry) {
        if (entry == null) return;
        hiddenItems.remove(entryKey(entry));
        rebuild();
    }

    public boolean isHidden(SidebarEntry entry) {
        return entry != null && hiddenItems.contains(entryKey(entry));
    }

    private String entryKey(SidebarEntry entry) {
        return entry.type.name() + "\u0001" + entry.id;
    }

    public void setToolOrder(List<String> order) {
        toolOrder.clear();
        if (order != null) toolOrder.addAll(order);
    }

    public List<String> getToolOrder() {
        List<String> result = new ArrayList<>();
        for (SidebarEntry entry : entries) {
            if (entry.type == EntryType.TOOL) result.add(entry.id);
        }
        return result;
    }

    public void setCollapsedState(String section, boolean value) {
        collapsed.put(section, value);
    }

    public void rebuild() {
        entries.clear();
        for (String section : sectionOrder) {
            if ("bookmarks".equals(section) && !showBookmarks) continue;
            if (section.startsWith(BOOKMARK_GROUP_PREFIX) && !showGroups) continue;
            entries.add(new SidebarEntry(EntryType.HEADER, section, section,
                    sectionTitle(section), 0, null, null));
            if (Boolean.TRUE.equals(collapsed.get(section))) continue;
            if ("storage".equals(section)) {
                for (StorageUtil.StorageInfo info : storage) {
                    entries.add(new SidebarEntry(EntryType.STORAGE, section, info.path,
                            info.name, 0, null, info));
                }
                for (SidebarEntry pin : pinned) {
                    if (organizeMode || !hiddenItems.contains(entryKey(pin))) entries.add(pin);
                }
            } else if ("bookmarks".equals(section)) {
                addBookmarks("bookmarks", section, defaultBookmarks);
            } else if (section.startsWith(BOOKMARK_GROUP_PREFIX)) {
                String group = section.substring(BOOKMARK_GROUP_PREFIX.length());
                addBookmarks(group, section, groups.getOrDefault(group, new ArrayList<>()));
            } else if ("plugins".equals(section)) {
                int before = entries.size();
                addPluginsAndTools();
                // Nothing followed the header (no extras, packs hidden and no
                // external plugin actions): drop the empty section header.
                if (entries.size() == before) entries.remove(before - 1);
            } else {
                addTools();
            }
        }
        dispatchEntriesDiff();
    }

    private String sectionTitle(String section) {
        if ("storage".equals(section)) return context.getString(R.string.storage);
        if ("bookmarks".equals(section)) return context.getString(R.string.bookmarks);
        if ("plugins".equals(section)) return context.getString(R.string.tools_hub_advanced_plugins);
        if (section.startsWith(BOOKMARK_GROUP_PREFIX)) return section.substring(BOOKMARK_GROUP_PREFIX.length());
        return context.getString(R.string.tools_section);
    }

    private void addBookmarks(String group, String section, List<File> files) {
        for (File file : files) {
            String label = labels.get(file.getPath());
            if (TextUtils.isEmpty(label)) label = file.getName();
            SidebarEntry entry = new SidebarEntry(EntryType.BOOKMARK, section,
                    "bookmark\u0001" + group + "\u0001" + file.getPath(), label,
                    file.isDirectory() ? R.drawable.folder_24px : R.drawable.baseline_bookmark_24,
                    file, null);
            if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
        }
    }

    public void setShowPlugins(boolean enabled) {
        showPlugins = enabled;
    }

    /**
     * Plugins/Tools section body: built-in extra tools first (dev/auxiliary
     * utilities kept out of the main Tools section, plus any hub-only tools
     * the user explicitly added), then pack tools when the "Show plugins in
     * sidebar" setting is on (explicitly added packs always show), then
     * custom plugin tools chosen from the hub, then external plugin sidebar
     * actions. Flat rows, no group headers; each row uses the tool's own
     * icon. Ordering round-trips through the shared toolOrder like built-in
     * tools; newly installed entries are appended.
     */
    private void addPluginsAndTools() {
        List<String> order = SidebarToolSelection.mergeOrder(toolOrder,
                ToolRegistry.sidebarIds("plugins"), addedTools);
        for (String id : order) {
            if (isRenderableRegistryTool(id, "plugins")) addRegisteredTool("plugins", id);
        }
        addPluginGroups();
        addCustomTools();
        addExternalPluginActions();
    }

    private static boolean isPluginsSectionTool(String id) {
        return ToolRegistry.isInSidebarSection(id, "plugins");
    }

    /**
     * Registry tool rendered in a section: shipped default or explicitly
     * added from the hub. Added tools without a shipped section
     * (storagemanager, adb, jiagu_dumper) land in the advanced plugins
     * section.
     */
    private boolean isRenderableRegistryTool(String id, String section) {
        if (ToolRegistry.isInSidebarSection(id, section)) {
            return ToolRegistry.isDefaultSidebarTool(id) || addedTools.contains(id);
        }
        return "plugins".equals(section) && addedTools.contains(id)
                && ToolRegistry.sidebarSectionOf(id) == null;
    }

    /**
     * Pack tools part of the section: one row per installed pack tool. Gated
     * by the "Show plugins in sidebar" setting, except tools the user added
     * from the hub, which always show; the rest of the section shows
     * regardless.
     */
    private void addPluginGroups() {
        List<PluginRegistry.PackTools> packs = PluginRegistry.installedPacks();
        if (packs.isEmpty()) return;
        // index of "packtool:<pack>.<tool>" for order lookups.
        Map<String, ToolPlugin> byId = new LinkedHashMap<>();
        for (PluginRegistry.PackTools pack : packs) {
            for (ToolPlugin tool : pack.tools) {
                if (tool == null || tool.id() == null) continue;
                // OCR has a discoverable host entry even before its runtime is installed.
                if (ToolRegistry.findById(context, tool.id()) != null) continue;
                byId.put(PACK_TOOL_PREFIX + pack.packId + "." + tool.id(), tool);
            }
        }
        List<String> order = new ArrayList<>();
        for (String id : toolOrder) if (byId.containsKey(id) && !order.contains(id)) order.add(id);
        for (String id : byId.keySet()) if (!order.contains(id)) order.add(id);
        for (String id : order) {
            if (!showPlugins && !addedTools.contains(id)) continue;
            ToolPlugin tool = byId.get(id);
            String label = tool.title(context);
            if (label == null || label.isEmpty()) label = tool.id();
            int icon;
            try {
                icon = tool.iconRes(context);
            } catch (Exception e) {
                icon = R.drawable.tools_24px;
            }
            SidebarEntry entry = new SidebarEntry(EntryType.TOOL, "plugins", id, label,
                    icon, null, null);
            if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
        }
    }

    /**
     * Host-registered custom (non-pack, non-registry) plugin tools the user
     * added from the hub. Rows use a {@code custom:<pluginId>} id, follow the
     * saved drag order like pack rows, and launch through the shared tool
     * runner.
     */
    private void addCustomTools() {
        Map<String, ToolPlugin> byId = new LinkedHashMap<>();
        for (ToolPlugin plugin : PluginRegistry.customPlugins()) {
            if (plugin == null || plugin.id() == null) continue;
            if (ToolRegistry.findById(context, plugin.id()) != null) continue;
            byId.put(SidebarToolSelection.CUSTOM_TOOL_PREFIX + plugin.id(), plugin);
        }
        List<String> order = new ArrayList<>();
        for (String id : toolOrder) if (byId.containsKey(id) && !order.contains(id)) order.add(id);
        for (String id : byId.keySet()) if (!order.contains(id)) order.add(id);
        for (String id : order) {
            if (!addedTools.contains(id)) continue;
            ToolPlugin plugin = byId.get(id);
            String label = plugin.title(context);
            if (label == null || label.isEmpty()) label = plugin.id();
            int icon;
            try {
                icon = plugin.iconRes(context);
            } catch (Exception e) {
                icon = R.drawable.tools_24px;
            }
            SidebarEntry entry = new SidebarEntry(EntryType.TOOL, "plugins", id, label,
                    icon, null, null);
            if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
        }
    }

    /**
     * Core file-manager tools; the main Tools section of the sidebar.
     *
     * A saved custom order still wins; hub-only tools the user explicitly
     * added are appended. Metadata comes from the same registry as the hub,
     * so adding a tool can never leave one entry point behind.
     */
    private void addTools() {
        List<String> order = SidebarToolSelection.mergeOrder(toolOrder,
                ToolRegistry.sidebarIds("tools"), addedTools);
        for (String id : order) {
            if ("tools".equals(id)) addTool(id, R.string.sidebar_tools, R.drawable.tools_24px);
            else if (isRenderableRegistryTool(id, "tools")) addRegisteredTool("tools", id);
        }
    }

    private void addRegisteredTool(String section, String id) {
        ToolRegistry.ToolItem tool = ToolRegistry.findById(context, id);
        if (tool == null || !ToolRegistry.isAvailable(context, id)) return;
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, section, id, tool.title(),
                tool.iconRes(), null, null);
        if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
    }

    private void addTool(String id, int text, int icon) {
        addToolTo("tools", id, text, icon);
    }

    private void addToolTo(String section, String id, int text, int icon) {
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, section, id, context.getString(text), icon, null, null);
        if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
    }

    /**
     * External (out-of-process) plugin sidebar actions and in-process
     * extension actions, shown after the pack tools. Ids already in the
     * saved order come first; entries installed after that order was
     * written are appended once, then round-trip like built-ins.
     */
    private void addExternalPluginActions() {
        List<String> order = new ArrayList<>();
        for (String id : toolOrder) {
            if (!order.contains(id) && !isPluginsSectionTool(id) && !id.startsWith(PACK_TOOL_PREFIX)) order.add(id);
        }
        for (String id : order) {
            SidebarAction inProc = ExtensionRegistry.findSidebar(id);
            if (inProc != null) {
                addToolEntry("plugins", id, inProc);
                continue;
            }
            ExternalActions.Entry ext =
                    ExternalActions.findById(ExternalActions.sidebarEntries(context), id);
            if (ext != null) addExternalTool("plugins", ext);
        }
        for (ExternalActions.Entry e : ExternalActions.sidebarEntries(context)) {
            if (e == null || e.id == null) continue;
            if (!presentAsTool(e.id)) addExternalTool("plugins", e);
        }
        for (SidebarAction ext : ExtensionRegistry.sidebarActions()) {
            if (ext == null || ext.id() == null) continue;
            if (!presentAsTool(ext.id())) addToolEntry("plugins", ext.id(), ext);
        }
    }

    private boolean presentAsTool(String id) {
        for (SidebarEntry se : entries) {
            if (se.type == EntryType.TOOL && id.equals(se.id())) return true;
        }
        return false;
    }

    private void addToolEntry(String section, String id, SidebarAction ext) {
        String label = ext.title() == null || ext.title().isEmpty() ? id : ext.title();
        int icon = ExtensionIcons.resId(context, ext.iconName(), R.drawable.tools_24px);
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, section, id, label, icon, null, null);
        if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
    }

    private void addExternalTool(String section, ExternalActions.Entry e) {
        String label = e.title == null || e.title.isEmpty() ? e.id : e.title;
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, section, e.id,
                label, R.drawable.tools_24px, null, null);
        if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
    }

    public void setCollapsed(String section, boolean value) {
        collapsed.put(section, value);
        animateToggleSection = section;
        rebuild();
    }

    public boolean isCollapsed(String section) {
        return Boolean.TRUE.equals(collapsed.get(section));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public int getCount() {
        return getItemCount();
    }

    public SidebarEntry getEntry(int position) {
        return position >= 0 && position < entries.size() ? entries.get(position) : null;
    }

    public SidebarEntry findByPayload(String payload) {
        if (payload == null) return null;
        for (SidebarEntry entry : entries) if (payload.equals(entry.dragPayload())) return entry;
        return null;
    }

    public List<String> getSectionOrder() {
        return new ArrayList<>(sectionOrder);
    }

    public void moveSection(String source, String target) {
        if (source == null || target == null || source.equals(target)) return;
        int from = sectionOrder.indexOf(source);
        int to = sectionOrder.indexOf(target);
        if (from < 0 || to < 0) return;
        String s = sectionOrder.remove(from);
        sectionOrder.add(to, s);
        rebuild();
    }

    public boolean moveEntry(SidebarEntry source, SidebarEntry target) {
        if (source == null || target == null || !source.section.equals(target.section)) return false;
        int from = indexOf(source);
        int to = indexOf(target);
        if (from < 0 || to < 0 || from == to) return false;
        SidebarEntry moved = entries.remove(from);
        if (to > from) to--;
        if (target.type == EntryType.HEADER) to = indexOf(target) + 1;
        entries.add(Math.max(0, Math.min(to, entries.size())), moved);
        if (moved.type == EntryType.BOOKMARK) syncBookmarkSources();
        if (moved.type == EntryType.TOOL) syncToolOrder();
        dispatchEntriesDiff();
        return true;
    }

    private void syncBookmarkSources() {
        List<File> orderedDefault = new ArrayList<>();
        Map<String, List<File>> orderedGroups = new LinkedHashMap<>();
        for (String group : groups.keySet()) orderedGroups.put(group, new ArrayList<>());
        for (SidebarEntry entry : entries) {
            if (entry.type != EntryType.BOOKMARK || entry.file == null) continue;
            String[] parts = entry.id.split("\\u0001", -1);
            if (parts.length < 3) continue;
            if (parts[1].equals("bookmarks")) orderedDefault.add(entry.file);
            else orderedGroups.computeIfAbsent(parts[1], key -> new ArrayList<>()).add(entry.file);
        }
        defaultBookmarks.clear();
        defaultBookmarks.addAll(orderedDefault);
        groups.clear();
        groups.putAll(orderedGroups);
    }

    private void syncToolOrder() {
        toolOrder.clear();
        for (SidebarEntry entry : entries) {
            if (entry.type == EntryType.TOOL) toolOrder.add(entry.id);
        }
    }

    private int indexOf(SidebarEntry entry) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i) == entry) return i;
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        SidebarEntry entry = getEntry(position);
        EntryType type = entry.type;
        if (type == EntryType.HEADER) return 0;
        if (type == EntryType.STORAGE || (type == EntryType.PIN && entry.storage != null && !organizeMode)) return 1;
        return 2;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view;
        if (viewType == 0) view = inflater.inflate(R.layout.item_sidebar_header, parent, false);
        else if (viewType == 1) view = inflater.inflate(R.layout.item_storage, parent, false);
        else view = inflater.inflate(R.layout.item_dropdown_option, parent, false);
        ViewHolder holder = new ViewHolder(view, viewType);
        view.setTag(holder);
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SidebarEntry entry = getEntry(position);
        if (entry == null) return;
        int type = holder.viewType;
        if (type == 0) {
            holder.headerTitle.setText(entry.label);
            holder.headerDragHandle.setVisibility(organizeMode ? View.VISIBLE : View.INVISIBLE);
            holder.headerDragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && callbacks != null) {
                    callbacks.onEntryDragHandleTouched(entry, v);
                }
                return true;
            });
            boolean groupCollapsed = Boolean.TRUE.equals(this.collapsed.get(entry.section));
            Drawable d = ResourcesCompat.getDrawable(context.getResources(),
                    groupCollapsed ? R.drawable.arrow_drop_up_24px : R.drawable.arrow_drop_down_24px, null);
            if (d != null) holder.headerToggle.setImageDrawable(d.mutate());
            holder.headerToggle.setContentDescription(context.getString(
                    groupCollapsed ? R.string.sidebar_expand : R.string.sidebar_collapse));
            ((View) holder.headerToggle.getParent()).setOnClickListener(v -> {
                if (callbacks != null) callbacks.onHeaderToggle(entry);
            });
            holder.headerToggle.animate().cancel();
            holder.headerToggle.setRotation(0f);
            if (entry.section.equals(animateToggleSection)) {
                animateToggleSection = null;
                holder.headerToggle.setAlpha(0.45f);
                holder.headerToggle.setScaleX(0.88f);
                holder.headerToggle.setScaleY(0.88f);
                holder.headerToggle.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start();
            } else {
                holder.headerToggle.setAlpha(1f);
                holder.headerToggle.setScaleX(1f);
                holder.headerToggle.setScaleY(1f);
            }
            return;
        }
        View view = holder.itemView;
        if (type == 1) {
            StorageUtil.StorageInfo info = entry.storage;
            holder.storageName.setText(info.name);
            Drawable glyph = null;
            if (entry.type == EntryType.PIN) {
                if (entry.file != null || entry.icon == R.drawable.folder_24px) {
                    glyph = FileIconSpec.FOLDER.drawable(context);
                } else {
                    glyph = ResourcesCompat.getDrawable(context.getResources(), entry.icon, context.getTheme());
                    if (glyph != null) {
                        glyph = glyph.mutate();
                        DrawableCompat.setTint(glyph,
                                MaterialColors.getColor(holder.storageName, android.R.attr.colorPrimary));
                    }
                }
                if (glyph != null) {
                    int size = (int) (20 * context.getResources().getDisplayMetrics().density + 0.5f);
                    glyph.setBounds(0, 0, size, size);
                }
            }
            holder.storageName.setCompoundDrawablesRelative(glyph, null, null, null);
            holder.storageName.setCompoundDrawablePadding((int) (8 * context.getResources().getDisplayMetrics().density));
            boolean known = info.totalBytes >= 0 && info.usedBytes >= 0 && info.freeBytes >= 0;
            ((View) holder.storageProgress.getParent()).setVisibility(known ? View.VISIBLE : View.GONE);
            holder.storageUsedPercent.setText(context.getString(R.string.usedpt, info.usedPercent()));
            if (known) {
                holder.storageFreeSpace.setVisibility(View.VISIBLE);
                holder.storageFreeSpace.setText(context.getString(R.string.used,
                        FileSize.getHumanReadableFileSize(info.usedBytes), FileSize.getHumanReadableFileSize(info.freeBytes)));
            } else {
                if (info.freeBytes >= 0) {
                    holder.storageFreeSpace.setVisibility(View.VISIBLE);
                    holder.storageFreeSpace.setText(context.getString(
                            R.string.network_storage_free, FileSize.getHumanReadableFileSize(info.freeBytes)));
                } else if (TextUtils.isEmpty(info.description)) {
                    holder.storageFreeSpace.setVisibility(View.GONE);
                } else {
                    holder.storageFreeSpace.setVisibility(View.VISIBLE);
                    holder.storageFreeSpace.setText(info.description);
                }
            }
            holder.storageProgress.setProgress(info.usedPercent());
            view.setOnClickListener(v -> {
                if (callbacks != null) callbacks.onEntryClicked(entry, v);
            });
            view.setOnLongClickListener(v -> {
                if (callbacks == null) return false;
                if (entry.type == EntryType.PIN) callbacks.onEntryLongPressed(entry, v);
                else callbacks.onEntryStorageLongPressed(entry, v);
                return true;
            });
            return;
        }
        boolean hidden = isHidden(entry);
        boolean isPinFolder = entry.type == EntryType.PIN && (entry.file != null || entry.icon == R.drawable.folder_24px);
        boolean useFileArt = !hidden && ((entry.type == EntryType.BOOKMARK && entry.file != null) || isPinFolder);
        if (useFileArt) {
            ImageViewCompat.setImageTintList(holder.optionIcon, null);
            FileIconSpec spec = isPinFolder
                    ? FileIconSpec.FOLDER
                    : FileIconSpec.forPath(entry.file.getPath(), !entry.file.isFile());
            holder.optionIcon.setImageDrawable(spec.drawable(context));
        } else {
            holder.optionIcon.setImageResource(hidden ? R.drawable.visibility_off_24px : entry.icon);
        }
        holder.optionIcon.setEnabled(!hidden);
        holder.optionIcon.setAlpha(hidden ? 0.55f : 1f);
        holder.optionText.setText(entry.label);
        holder.optionText.setEnabled(!hidden);
        holder.optionText.setAlpha(hidden ? 0.55f : 1f);
        view.setAlpha(hidden ? 0.7f : 1f);
        view.setTranslationY(0f);
        holder.optionDragHandle.setVisibility(organizeMode && entry.type != EntryType.STORAGE ? View.VISIBLE : View.INVISIBLE);
        holder.optionHideButton.setVisibility(organizeMode && entry.type != EntryType.STORAGE ? View.VISIBLE : View.INVISIBLE);
        holder.optionHideButton.setContentDescription(context.getString(hidden ? R.string.restore_sidebar_item : R.string.hide));
        holder.optionHideButton.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onEntryHideRequested(entry);
        });
        holder.optionDragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && callbacks != null) {
                callbacks.onEntryDragHandleTouched(entry, v);
            }
            return true;
        });
        if (!useFileArt) {
            TypedValue value = new TypedValue();
            Resources.Theme theme = context.getTheme();
            theme.resolveAttribute(android.R.attr.colorPrimary, value, true);
            ImageViewCompat.setImageTintList(holder.optionIcon, ColorStateList.valueOf(value.data));
            Drawable drawable = holder.optionIcon.getDrawable();
            if (drawable != null) {
                drawable = drawable.mutate();
                ColorUtil.changeImageColor(drawable, value.data);
                holder.optionIcon.setImageDrawable(drawable);
            }
        }
        view.setOnClickListener(v -> {
            if (callbacks != null) callbacks.onEntryClicked(entry, v);
        });
        view.setOnLongClickListener(v -> {
            if (callbacks != null) callbacks.onEntryLongPressed(entry, v);
            return true;
        });
        int surfaceColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainer, Color.TRANSPARENT);
        if (view instanceof MaterialCardView card) card.setCardBackgroundColor(surfaceColor);
    }

    /** Legacy row-binding helper retained for direct instrumentation callers. */
    @NonNull
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        int type = getItemViewType(position);
        ViewHolder holder = (convertView != null && convertView.getTag() instanceof ViewHolder vh
                && vh.viewType == type) ? vh : onCreateViewHolder(parent, type);
        onBindViewHolder(holder, position);
        return holder.itemView;
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder {
        final int viewType;
        final TextView headerTitle;
        final ImageView headerDragHandle;
        final ImageView headerToggle;
        final TextView storageName;
        final TextView storageUsedPercent;
        final TextView storageFreeSpace;
        final ProgressBar storageProgress;
        final ImageView optionIcon;
        final TextView optionText;
        final ImageView optionDragHandle;
        final ImageButton optionHideButton;

        ViewHolder(@NonNull View itemView, int viewType) {
            super(itemView);
            this.viewType = viewType;
            if (viewType == 0) {
                headerTitle = itemView.findViewById(R.id.sidebarSectionTitle);
                headerDragHandle = itemView.findViewById(R.id.sidebarSectionDragHandle);
                headerToggle = itemView.findViewById(R.id.sidebarSectionToggle);
                storageName = null;
                storageUsedPercent = null;
                storageFreeSpace = null;
                storageProgress = null;
                optionIcon = null;
                optionText = null;
                optionDragHandle = null;
                optionHideButton = null;
            } else if (viewType == 1) {
                headerTitle = null;
                headerDragHandle = null;
                headerToggle = null;
                storageName = itemView.findViewById(R.id.tvStorageName);
                storageUsedPercent = itemView.findViewById(R.id.tvUsedPercent);
                storageFreeSpace = itemView.findViewById(R.id.tvUsedFree);
                storageProgress = itemView.findViewById(R.id.pbUsed);
                optionIcon = null;
                optionText = null;
                optionDragHandle = null;
                optionHideButton = null;
            } else {
                headerTitle = null;
                headerDragHandle = null;
                headerToggle = null;
                storageName = null;
                storageUsedPercent = null;
                storageFreeSpace = null;
                storageProgress = null;
                optionIcon = itemView.findViewById(R.id.optionIcon);
                optionText = itemView.findViewById(R.id.optionText);
                optionDragHandle = itemView.findViewById(R.id.optionDragHandle);
                optionHideButton = itemView.findViewById(R.id.optionHideButton);
            }
        }
    }
}

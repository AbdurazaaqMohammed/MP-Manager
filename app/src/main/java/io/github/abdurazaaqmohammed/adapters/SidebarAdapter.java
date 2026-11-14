package io.github.abdurazaaqmohammed.adapters;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionIcons;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.SidebarAction;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.StorageUtil;

public class SidebarAdapter extends ArrayAdapter<SidebarAdapter.SidebarEntry> {

    public enum EntryType {
        HEADER, STORAGE, TOOL, BOOKMARK, PIN
    }

    public record SidebarEntry(EntryType type, String section, String id, String label, int icon,
                               File file, StorageUtil.StorageInfo storage) {

        public String dragPayload() {
                return type.name() + "\u0001" + section + "\u0001" + (id == null ? "" : id);
            }
        }

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
    public static final String PACK_TOOL_PREFIX = "packtool:";
    private final List<SidebarEntry> entries = new ArrayList<>();
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
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Set<String> hiddenItems = new HashSet<>();
    private final List<SidebarEntry> pinned = new ArrayList<>();
    private String animateToggleSection;

    public SidebarAdapter(Context context, Callbacks callbacks) {
        super(context, R.layout.item_dropdown_option);
        this.context = context;
        this.callbacks = callbacks;
        sectionOrder.add("storage");
        sectionOrder.add("bookmarks");
        sectionOrder.add("tools");
        sectionOrder.add("plugins");
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
        if (items != null) hiddenItems.addAll(items);
    }

    public Set<String> getHiddenItems() {
        return new HashSet<>(hiddenItems);
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
        notifyDataSetChanged();
    }

    private String sectionTitle(String section) {
        if ("storage".equals(section)) return context.getString(R.string.storage);
        if ("bookmarks".equals(section)) return context.getString(R.string.bookmarks);
        if ("plugins".equals(section)) return context.getString(R.string.sidebar_plugins);
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
     * utilities kept out of the main Tools section), then pack tools when the
     * "Show plugins in sidebar" setting is on, then external plugin sidebar
     * actions. Flat rows, no group headers; each row uses the tool's own
     * icon. Ordering round-trips through the shared toolOrder like built-in
     * tools; newly installed entries are appended.
     */
    private void addPluginsAndTools() {
        List<String> order = new ArrayList<>();
        for (String id : toolOrder) if (isPluginsSectionTool(id) && !order.contains(id)) order.add(id);
        for (String id : PLUGINS_SECTION_TOOLS) if (!order.contains(id)) order.add(id);
        for (String id : order) {
            switch (id) {
                case "mcp_server": addToolTo("plugins", id, R.string.mcp_server, R.drawable.code_24px); break;
                case "logcat": addToolTo("plugins", id, R.string.sidebar_logcat, R.drawable.terminal_24px); break;
                case "color_picker": addToolTo("plugins", id, R.string.color_picker, R.drawable.colorize_24px); break;
                case "layout": addToolTo("plugins", id, R.string.sidebar_layout_inspector, R.drawable.ic_inspect); break;
                case "wifi": addToolTo("plugins", id, R.string.sidebar_wifi, R.drawable.wifi_24px); break;
                case "free_style": addToolTo("plugins", id, R.string.free_style, R.drawable.image_24px); break;
                case "frida_console":
                    // Root-only: the console drives frida-inject through su.
                    if (RootManager.getInstance(context).isRootAvailable()) {
                        addToolTo("plugins", id, R.string.frida_console, R.drawable.terminal_24px);
                    }
                    break;
                case "command_shell":
                    addToolTo("plugins", id, R.string.command_shell, R.drawable.terminal_24px);
                    break;
            }
        }
        addPluginGroups();
        addExternalPluginActions();
    }

    /** Built-in tools that live in the Plugins/Tools section. */
    private static final String[] PLUGINS_SECTION_TOOLS =
            {"command_shell", "mcp_server", "logcat", "color_picker", "layout", "wifi", "free_style", "frida_console"};

    private static boolean isPluginsSectionTool(String id) {
        for (String tool : PLUGINS_SECTION_TOOLS) if (tool.equals(id)) return true;
        return false;
    }

    /**
     * Pack tools part of the section: one row per installed pack tool. Gated
     * by the "Show plugins in sidebar" setting; the rest of the section shows
     * regardless.
     */
    private void addPluginGroups() {
        if (!showPlugins) return;
        List<PluginRegistry.PackTools> packs = PluginRegistry.installedPacks();
        if (packs.isEmpty()) return;
        // index of "packtool:<pack>.<tool>" for order lookups.
        Map<String, ToolPlugin> byId = new LinkedHashMap<>();
        for (PluginRegistry.PackTools pack : packs) {
            for (ToolPlugin tool : pack.tools) {
                if (tool == null || tool.id() == null) continue;
                byId.put(PACK_TOOL_PREFIX + pack.packId + "." + tool.id(), tool);
            }
        }
        List<String> order = new ArrayList<>();
        for (String id : toolOrder) if (byId.containsKey(id) && !order.contains(id)) order.add(id);
        for (String id : byId.keySet()) if (!order.contains(id)) order.add(id);
        for (String id : order) {
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
     * Core file-manager tools; the main Tools section of the sidebar.
     *
     * <p>The advanced system tools (App Ops, SetEdit) sit at the bottom of the
     * section with Tools Hub closing it; Partition Dumper is not offered here
     * at all — it lives in the Tools Hub. A saved custom order still wins, but
     * unknown ids (partition_dumper) simply render nothing.
     */
    private void addTools() {
        String[] defaults = {"extract", "documents", "recycle_bin", "ftp_server", "network_client", "partition_dumper", "app_ops", "set_edit", "tools"};
        List<String> order = new ArrayList<>();
        for (String savedId : toolOrder) {
            String id = savedId.equals("ftp_client") || savedId.equals("webdav_client") || savedId.equals("smb_client")
                    ? "network_client" : savedId;
            if (!order.contains(id)) order.add(id);
        }
        for (String id : defaults) if (!order.contains(id)) order.add(id);
        for (String id : order) {
            switch (id) {
                case "extract": addTool(id, R.string.sidebar_extract, R.drawable.apk_document_24px); break;
                case "documents": addTool(id, R.string.sidebar_documents, R.drawable.folder_24px); break;
                case "partition_dumper": addTool(id, R.string.tool_partition_dumper, R.drawable.ic_disk_drive_24px); break;
                case "app_ops": addTool(id, R.string.app_ops, R.drawable.ic_shield_24); break;
                case "set_edit": addTool(id, R.string.set_edit, R.drawable.baseline_settings_24); break;
                case "recycle_bin": addTool(id, R.string.recycle_bin, R.drawable.baseline_delete_24); break;
                case "ftp_server": addTool(id, R.string.ftp_server, R.drawable.cloud_upload_24px); break;
                case "network_client": addTool(id, R.string.network_connections, R.drawable.cloud_download_24px); break;
                case "tools": addTool(id, R.string.sidebar_tools, R.drawable.tools_24px); break;
            }
        }
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
    public int getCount() {
        return entries.size();
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
        notifyDataSetChanged();
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

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        SidebarEntry entry = getEntry(position);
        int type = getItemViewType(position);
        if (type == 0) {
            View view = convertView;
            if (view == null) view = LayoutInflater.from(context).inflate(R.layout.item_sidebar_header, parent, false);
            TextView title = view.findViewById(R.id.sidebarSectionTitle);
            title.setText(entry.label);
            ImageView dragHandle = view.findViewById(R.id.sidebarSectionDragHandle);
            dragHandle.setVisibility(organizeMode ? View.VISIBLE : View.INVISIBLE);
            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    callbacks.onEntryDragHandleTouched(entry, v);
                }
                return true;
            });
            ImageView toggle = view.findViewById(R.id.sidebarSectionToggle);
            boolean groupCollapsed = Boolean.TRUE.equals(this.collapsed.get(entry.section));
            Drawable d = ResourcesCompat.getDrawable(context.getResources(), groupCollapsed ? R.drawable.arrow_drop_up_24px : R.drawable.arrow_drop_down_24px, null);
            toggle.setImageDrawable(d.mutate());
            toggle.setContentDescription(context.getString(groupCollapsed ? R.string.sidebar_expand : R.string.sidebar_collapse));
            ((View) toggle.getParent()).setOnClickListener(v -> callbacks.onHeaderToggle(entry));
            toggle.animate().cancel();
            toggle.setRotation(0f);
            if (entry.section.equals(animateToggleSection)) {
                animateToggleSection = null;
                toggle.setAlpha(0.45f);
                toggle.setScaleX(0.88f);
                toggle.setScaleY(0.88f);
                toggle.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start();
            } else {
                toggle.setAlpha(1f);
                toggle.setScaleX(1f);
                toggle.setScaleY(1f);
            }
            return view;
        }
        if (type == 1) {
            View view = convertView;
            if (view == null) view = LayoutInflater.from(context).inflate(R.layout.item_storage, parent, false);
            StorageUtil.StorageInfo info = entry.storage;
            TextView name = view.findViewById(R.id.tvStorageName);
            name.setText(info.name);
            Drawable glyph = entry.type == EntryType.PIN ? ResourcesCompat.getDrawable(context.getResources(), entry.icon, context.getTheme()) : null;
            if (glyph != null) {
                int size = (int) (20 * context.getResources().getDisplayMetrics().density + 0.5f);
                glyph = glyph.mutate();
                glyph.setBounds(0, 0, size, size);
                androidx.core.graphics.drawable.DrawableCompat.setTint(glyph,
                        MaterialColors.getColor(name, android.R.attr.colorPrimary));
            }
            name.setCompoundDrawablesRelative(glyph, null, null, null);
            name.setCompoundDrawablePadding((int) (8 * context.getResources().getDisplayMetrics().density));
            boolean known = info.totalBytes >= 0 && info.usedBytes >= 0 && info.freeBytes >= 0;
            ((View) view.findViewById(R.id.pbUsed).getParent()).setVisibility(known ? View.VISIBLE : View.GONE);
            ((TextView) view.findViewById(R.id.tvUsedPercent)).setText(context.getString(R.string.usedpt, info.usedPercent()));
            TextView freeSpace = view.findViewById(R.id.tvUsedFree);
            if (known) {
                freeSpace.setText(context.getString(R.string.used,
                        FileSize.getHumanReadableFileSize(info.usedBytes), FileSize.getHumanReadableFileSize(info.freeBytes)));
            } else {
                if (info.freeBytes >= 0) freeSpace.setText(context.getString(R.string.network_storage_free, FileSize.getHumanReadableFileSize(info.freeBytes)));
                else if(TextUtils.isEmpty(info.description)) freeSpace.setVisibility(View.GONE);
                else freeSpace.setText(info.description);
            }
            ((ProgressBar) view.findViewById(R.id.pbUsed)).setProgress(info.usedPercent());
            view.setOnClickListener(v -> callbacks.onEntryClicked(entry, v));
            view.setOnLongClickListener(v -> {
                if (entry.type == EntryType.PIN) callbacks.onEntryLongPressed(entry, v);
                else callbacks.onEntryStorageLongPressed(entry, v);
                return true;
            });
            return view;
        }
        View view = convertView;
        if (view == null) view = LayoutInflater.from(context).inflate(R.layout.item_dropdown_option, parent, false);
        ImageView icon = view.findViewById(R.id.optionIcon);
        TextView text = view.findViewById(R.id.optionText);
        ImageView dragHandle = view.findViewById(R.id.optionDragHandle);
        ImageButton hideButton = view.findViewById(R.id.optionHideButton);
        boolean hidden = isHidden(entry);
        icon.setImageResource(hidden ? R.drawable.visibility_off_24px : entry.icon);
        icon.setEnabled(!hidden);
        icon.setAlpha(hidden ? 0.55f : 1f);
        text.setText(entry.label);
        text.setEnabled(!hidden);
        text.setAlpha(hidden ? 0.55f : 1f);
        view.setAlpha(hidden ? 0.7f : 1f);
        view.setTranslationY(0f);
        dragHandle.setVisibility(organizeMode && entry.type != EntryType.STORAGE ? View.VISIBLE : View.INVISIBLE);
        hideButton.setVisibility(organizeMode && entry.type != EntryType.STORAGE ? View.VISIBLE : View.INVISIBLE);
        hideButton.setContentDescription(context.getString(hidden ? R.string.restore_sidebar_item : R.string.hide));
        hideButton.setOnClickListener(v -> callbacks.onEntryHideRequested(entry));
        dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                callbacks.onEntryDragHandleTouched(entry, v);
            }
            return true;
        });
        TypedValue value = new TypedValue();
        Resources.Theme theme = context.getTheme();
        theme.resolveAttribute(android.R.attr.colorPrimary, value, true);
        Drawable drawable = icon.getDrawable();
        if (drawable != null) {
            drawable = drawable.mutate();
            ColorUtil.changeImageColor(drawable, value.data);
            icon.setImageDrawable(drawable);
        }
        view.setOnClickListener(v -> callbacks.onEntryClicked(entry, v));
        view.setOnLongClickListener(v -> {
            callbacks.onEntryLongPressed(entry, v);
            return true;
        });
        int surfaceColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainer, Color.TRANSPARENT);
        if (view instanceof MaterialCardView card) card.setCardBackgroundColor(surfaceColor);
        return view;
    }
}

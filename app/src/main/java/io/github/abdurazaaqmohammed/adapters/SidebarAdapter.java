package io.github.abdurazaaqmohammed.adapters;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
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


import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionIcons;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.SidebarAction;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.StorageUtil;

public class SidebarAdapter extends ArrayAdapter<SidebarAdapter.SidebarEntry> {

    public enum EntryType {
        HEADER, STORAGE, TOOL, BOOKMARK
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
    private final List<SidebarEntry> entries = new ArrayList<>();
    private final Map<String, Boolean> collapsed = new LinkedHashMap<>();
    private final List<String> sectionOrder = new ArrayList<>();
    private final List<StorageUtil.StorageInfo> storage = new ArrayList<>();
    private final List<File> defaultBookmarks = new ArrayList<>();
    private final Map<String, List<File>> groups = new LinkedHashMap<>();
    private boolean showBookmarks;
    private boolean showGroups;
    private boolean organizeMode;
    private final List<String> toolOrder = new ArrayList<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Set<String> hiddenItems = new HashSet<>();
    private String animateToggleSection;

    public SidebarAdapter(Context context, Callbacks callbacks) {
        super(context, R.layout.item_sidebar_row);
        this.context = context;
        this.callbacks = callbacks;
        sectionOrder.add("storage");
        sectionOrder.add("network");
        sectionOrder.add("bookmarks");
        sectionOrder.add("tools");
    }

    public void setData(List<String> order, boolean showBookmarks, boolean showGroups,
            List<StorageUtil.StorageInfo> storage, List<File> defaultBookmarks,
            Map<String, List<File>> groups, Map<String, String> labels) {
        sectionOrder.clear();
        if (order != null) {
            for (String section : order) {
                if (section != null && !sectionOrder.contains(section)) sectionOrder.add(section);
            }
        }
        for (String section : new String[]{"storage", "network", "bookmarks", "tools"}) {
            if (!sectionOrder.contains(section)) sectionOrder.add(section);
        }
        this.showBookmarks = showBookmarks;
        this.showGroups = showGroups;
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
            int headerIndex = entries.size();
            entries.add(new SidebarEntry(EntryType.HEADER, section, section,
                    sectionTitle(section), 0, null, null));
            if (Boolean.TRUE.equals(collapsed.get(section))) continue;
            if ("storage".equals(section)) {
                // The root volume reads like any other volume: a phone icon plus its usage bar,
                // which is how MT Manager lists it.
                StorageUtil.StorageInfo root = StorageUtil.getRootInfo(context);
                if (root != null) {
                    entries.add(new SidebarEntry(EntryType.STORAGE, section, root.path,
                            root.name, 0, null, root));
                }
                for (StorageUtil.StorageInfo info : storage) {
                    entries.add(new SidebarEntry(EntryType.STORAGE, section, info.path,
                            info.name, 0, null, info));
                }
            } else if ("network".equals(section)) {
                // Remote connections live under their own heading, the way MT Manager keeps
                // network volumes apart from the local ones.
                addRemoteEntry(section);
            } else if ("bookmarks".equals(section)) {
                addBookmarks("bookmarks", section, defaultBookmarks);
            } else if (section.startsWith(BOOKMARK_GROUP_PREFIX)) {
                String group = section.substring(BOOKMARK_GROUP_PREFIX.length());
                addBookmarks(group, section, groups.getOrDefault(group, new ArrayList<>()));
            } else {
                addTools();
            }
            // An expanded section that produced no rows drops its heading too, the way MT Manager
            // hides an empty group. A collapsed one keeps it: that heading is how it gets reopened.
            if (entries.size() == headerIndex + 1 && !Boolean.TRUE.equals(collapsed.get(section))) {
                entries.remove(headerIndex);
            }
        }
        notifyDataSetChanged();
    }

    private String sectionTitle(String section) {
        if ("storage".equals(section)) return context.getString(R.string.sidebar_local);
        if ("network".equals(section)) return context.getString(R.string.sidebar_network);
        if ("bookmarks".equals(section)) return context.getString(R.string.bookmarks);
        if (section.startsWith(BOOKMARK_GROUP_PREFIX)) return section.substring(BOOKMARK_GROUP_PREFIX.length());
        return context.getString(R.string.tools_section);
    }

    /**
     * Remote storage stays a plain shortcut: it is a connection rather than a mounted volume, so
     * there is nothing to report a usage bar for.
     */
    private void addRemoteEntry(String section) {
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, section, "remote",
                context.getString(R.string.remote_connections),
                R.drawable.cloud_download_24px, null, null);
        if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
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

    private void addTools() {
        String[] defaults = {"extract", "ftp_server", "color_picker", "layout", "smali_reference", "activity_log", "password_manager", "recycle_bin", "tools", "settings"};
        List<String> order = new ArrayList<>();
        for (String id : toolOrder) if (!order.contains(id)) order.add(id);
        for (String id : defaults) if (!order.contains(id)) order.add(id);
        for (String id : order) {
            switch (id) {
                case "extract": addTool(id, R.string.sidebar_extract, R.drawable.apk_document_24px); break;
                case "ftp_server": addTool(id, R.string.ftp_server, R.drawable.cloud_upload_24px); break;
                case "color_picker": addTool(id, R.string.color_picker, R.drawable.colorize_24px); break;
                case "layout": addTool(id, R.string.sidebar_layout_inspector, R.drawable.ic_inspect); break;
                case "smali_reference": addTool(id, R.string.smali_reference, R.drawable.terminal_24px); break;
                case "activity_log": addTool(id, R.string.activity_log, R.drawable.tag_24px); break;
                case "password_manager": addTool(id, R.string.password_manager, R.drawable.lock_24px); break;
                case "recycle_bin": addTool(id, R.string.recycle_bin, R.drawable.baseline_delete_24); break;
                case "tools": addTool(id, R.string.sidebar_tools, R.drawable.tools_24px); break;
                case "settings": addTool(id, R.string.settings, R.drawable.baseline_settings_24); break;
                default: {
                    // Third-party sidebar actions (ids unknown to the switch).
                    SidebarAction inProc = ExtensionRegistry.findSidebar(id);
                    if (inProc != null) {
                        addToolEntry(id, inProc);
                        break;
                    }
                    ExternalActions.Entry ext =
                            ExternalActions.findById(ExternalActions.sidebarEntries(context), id);
                    if (ext != null) addExternalTool(ext);
                    break;
                }
            }
        }
        // External (out-of-process) actions discovered after the saved order
        // was written are appended once, then round-trip like built-ins.
        for (ExternalActions.Entry e : ExternalActions.sidebarEntries(context)) {
            if (e == null || e.id == null) continue;
            boolean present = false;
            for (SidebarEntry se : entries) {
                if (se.type == EntryType.TOOL && e.id.equals(se.id())) {
                    present = true;
                    break;
                }
            }
            if (!present) addExternalTool(e);
        }
        // Actions installed after the saved order was written are appended once;
        // from then on they round-trip through toolOrder like built-ins.
        for (SidebarAction ext : ExtensionRegistry.sidebarActions()) {
            if (ext == null || ext.id() == null) continue;
            boolean present = false;
            for (SidebarEntry e : entries) {
                if (e.type == EntryType.TOOL && ext.id().equals(e.id())) {
                    present = true;
                    break;
                }
            }
            if (!present) addToolEntry(ext.id(), ext);
        }
    }

    private void addTool(String id, int text, int icon) {
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, "tools", id, context.getString(text), icon, null, null);
        if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
    }

    private void addToolEntry(String id, SidebarAction ext) {
        String label = ext.title() == null || ext.title().isEmpty() ? id : ext.title();
        int icon = ExtensionIcons.resId(context, ext.iconName(), R.drawable.tools_24px);
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, "tools", id, label, icon, null, null);
        if (organizeMode || !hiddenItems.contains(entryKey(entry))) entries.add(entry);
    }

    private void addExternalTool(ExternalActions.Entry e) {
        String label = e.title == null || e.title.isEmpty() ? e.id : e.title;
        SidebarEntry entry = new SidebarEntry(EntryType.TOOL, "tools", e.id,
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

    /**
     * Not inherited on purpose.
     *
     * <p>{@code getCount} is overridden to report {@link #entries} while ArrayAdapter keeps its own
     * backing list, which stays empty. Framework paths that call {@code getItem} - accessibility,
     * for one - would therefore index an empty list and throw. Reading through {@link #entries}
     * makes the two consistent.
     */
    @Override
    public SidebarEntry getItem(int position) {
        return getEntry(position);
    }

    public SidebarEntry getEntry(int position) {
        return position >= 0 && position < entries.size() ? entries.get(position) : null;
    }

    public SidebarEntry findByPayload(String payload) {
        if (payload == null) return null;
        for (SidebarEntry entry : entries) if (payload.equals(entry.dragPayload())) return entry;
        return null;
    }

    public void moveSection(String source, String target) {
        if (source == null || target == null || source.equals(target)) return;
        int from = sectionOrder.indexOf(source);
        int to = sectionOrder.indexOf(target);
        if (from < 0 || to < 0) return;
        Collections.swap(sectionOrder, from, to);
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
        EntryType type = getEntry(position).type;
        if (type == EntryType.HEADER) return 0;
        if (type == EntryType.STORAGE) return 1;
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
            boolean collapsed = isCollapsed(entry.section);
            Drawable d = ResourcesCompat.getDrawable(context.getResources(), collapsed ? R.drawable.arrow_drop_down_24px : R.drawable.arrow_drop_up_24px, null);
            toggle.setImageDrawable(d.mutate());
            // MT Manager draws the caret pointing up while a section is open, so the arrow reads
            // as "collapse me" instead of "expand me"; the row keeps a downward arrow when folded.
            toggle.setContentDescription(context.getString(collapsed ? R.string.sidebar_expand : R.string.sidebar_collapse));
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
            ((TextView) view.findViewById(R.id.tvStorageName)).setText(info.name);
            ((TextView) view.findViewById(R.id.tvUsedPercent)).setText(context.getString(R.string.usedpt, info.usedPercent()));
            ((TextView) view.findViewById(R.id.tvUsedFree)).setText(context.getString(R.string.used,
                    FileSize.getHumanReadableFileSize(info.usedBytes), FileSize.getHumanReadableFileSize(info.freeBytes)));
            ((ProgressBar) view.findViewById(R.id.pbUsed)).setProgress(info.usedPercent());
            // Root gets the handset glyph, the primary volume the card glyph and anything else a
            // folder - the same pairing MT Manager uses, so the three rows stay distinguishable.
            ImageView storageIcon = view.findViewById(R.id.ivStorageIcon);
            if ("/".equals(info.path)) {
                storageIcon.setImageResource(R.drawable.baseline_phone_android_24);
            } else if (info.path != null && info.path.startsWith("/storage/emulated/0")) {
                storageIcon.setImageResource(R.drawable.baseline_insert_drive_file_24);
            } else {
                storageIcon.setImageResource(R.drawable.folder_24px);
            }
            view.setOnClickListener(v -> callbacks.onEntryClicked(entry, v));
            view.setOnLongClickListener(v -> {
                callbacks.onEntryStorageLongPressed(entry, v);
                return true;
            });
            return view;
        }
        View view = convertView;
        if (view == null) view = LayoutInflater.from(context).inflate(R.layout.item_sidebar_row, parent, false);
        ImageView icon = view.findViewById(R.id.optionIcon);
        TextView text = view.findViewById(R.id.optionText);
        TextView subtitle = view.findViewById(R.id.optionSubtitle);
        ImageView dragHandle = view.findViewById(R.id.optionDragHandle);
        ImageButton hideButton = view.findViewById(R.id.optionHideButton);
        boolean hidden = isHidden(entry);
        icon.setImageResource(hidden ? R.drawable.visibility_off_24px : entry.icon);
        icon.setEnabled(!hidden);
        icon.setAlpha(hidden ? 0.55f : 1f);
        text.setText(entry.label);
        text.setEnabled(!hidden);
        text.setAlpha(hidden ? 0.55f : 1f);
        // Bookmarks carry their location on a second line, the way MT Manager lists them; tools
        // have no meaningful path so the line stays collapsed.
        File bookmark = entry.file;
        File location = bookmark == null ? null
                : (bookmark.isDirectory() ? bookmark : bookmark.getParentFile());
        if (entry.type == EntryType.BOOKMARK && location != null) {
            subtitle.setText(location.getAbsolutePath());
            subtitle.setVisibility(View.VISIBLE);
        } else {
            subtitle.setVisibility(View.GONE);
        }
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
        // Left at the layout's colour rather than tinted to the accent: the badge behind it is
        // dark, and an accent-coloured glyph on it has poor contrast in the light theme.
        Drawable drawable = icon.getDrawable();
        if (drawable != null) icon.setImageDrawable(drawable.mutate());
        view.setOnClickListener(v -> callbacks.onEntryClicked(entry, v));
        view.setOnLongClickListener(v -> {
            callbacks.onEntryLongPressed(entry, v);
            return true;
        });
        return view;
    }
}

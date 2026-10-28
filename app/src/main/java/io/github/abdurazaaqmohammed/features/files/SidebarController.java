package io.github.abdurazaaqmohammed.features.files;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;

import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.reflect.TypeToken;

import io.github.abdurazaaqmohammed.utils.AppGson;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RecycleBin;
import io.github.codehasan.colorpicker.extensions.Extensions;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.SidebarAdapter;
import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.SidebarAction;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust;
import io.github.abdurazaaqmohammed.tools.StorageManagerActivity;
import io.github.abdurazaaqmohammed.tools.ToolRunnerActivity;
import io.github.abdurazaaqmohammed.tools.ToolsHubActivity;
import io.github.abdurazaaqmohammed.tools.WifiManagerActivity;
import io.github.abdurazaaqmohammed.utils.StorageUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sidebar drawer: setup, organize-mode drag & drop, section order,
 * storage refresh, tool shortcuts. Extracted from MainActivity;
 * bookmark-menu glue goes through MainActivity bookmark accessors
 * until the bookmarks UI slice moves.
 */
public class SidebarController {

    private final MainActivity activity;
    private SidebarAdapter sidebarAdapter;
    private ListView sidebarList;
    private SwipeRefreshLayout sidebarRefresh;
    private boolean sidebarOrganizeMode;
    private View organizeDragView;
    private View organizeDragCard;
    private SidebarAdapter.SidebarEntry organizeDragEntry;
    private float organizeDownX;
    private float organizeDownY;
    private boolean organizeDragStarted;
    private final Runnable organizeLongPressRunnable = () -> {
        if (!sidebarOrganizeMode || organizeDragStarted || organizeDragView == null || organizeDragEntry == null) return;
        organizeDragStarted = true;
        startSidebarDrag(organizeDragView, organizeDragEntry);
    };
    private BroadcastReceiver storageRefreshReceiver;
    /**
     * False until the first drawer open: gathering sidebar data costs
     * StorageManager/StatFs I/O, per-bookmark stats and PackageManager
     * queries, so it never runs on the cold-start path. See
     * {@link #ensureSidebarData()}.
     */
    private boolean sidebarDataReady;

    public SidebarController(MainActivity activity) {
        this.activity = activity;
    }

    public void setupSidebar() {
        activity.findViewById(R.id.storageContainer).setVisibility(View.GONE);
        sidebarList = activity.findViewById(R.id.sidebarList);
        sidebarAdapter = new SidebarAdapter(activity, new SidebarAdapter.Callbacks() {
            @Override
            public void onEntryClicked(SidebarAdapter.SidebarEntry entry, View view) {
                if (sidebarOrganizeMode) {
                    if (entry != null && entry.type() != SidebarAdapter.EntryType.HEADER) startSidebarDrag(view, entry);
                    return;
                }
                openSidebarEntry(entry);
            }

            @Override
            public void onHeaderToggle(SidebarAdapter.SidebarEntry entry) {
                boolean collapsed = !sidebarAdapter.isCollapsed(entry.section());
                sidebarAdapter.setCollapsed(entry.section(), collapsed);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
                Set<String> saved = new HashSet<>(prefs.getStringSet("sidebar_collapsed_sections", Collections.emptySet()));
                if (collapsed) saved.add(entry.section());
                else saved.remove(entry.section());
                prefs.edit().putStringSet("sidebar_collapsed_sections", saved).apply();
            }

            @Override
            public void onEntryStorageLongPressed(SidebarAdapter.SidebarEntry entry, View view) {
                PopupMenu menu = new PopupMenu(activity, view);
                menu.getMenu().add(0, R.string.manage_storage, 0, R.string.manage_storage);
                menu.getMenu().add(0, R.string.open_location, 1, R.string.open_location);
                menu.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == R.string.manage_storage) {
                        activity.startActivity(new Intent(activity, StorageManagerActivity.class));
                    } else {
                        activity.loadFolderInPane(new File(entry.storage().path), activity.lastPaneSelected == 1);
                        activity.closeSidebarDrawer();
                    }
                    return true;
                });
                menu.show();
            }

            @Override
            public void onEntryLongPressed(SidebarAdapter.SidebarEntry entry, View view) {
                if (!sidebarOrganizeMode && entry.type() == SidebarAdapter.EntryType.BOOKMARK) {
                    showSidebarBookmarkMenu(entry, view);
                } else if (!sidebarOrganizeMode && "recycle_bin".equals(entry.id())) {
                    showRecycleBinOptionsDialog();
                }
            }

            @Override
            public void onEntryDragHandleTouched(SidebarAdapter.SidebarEntry entry, View view) {
                if (sidebarOrganizeMode) startSidebarDrag(view, entry);
            }

            @Override
            public void onEntryHideRequested(SidebarAdapter.SidebarEntry entry) {
                if (entry.type() == SidebarAdapter.EntryType.HEADER) {
                    sidebarAdapter.setCollapsed(entry.section(), true);
                    return;
                }
                if (sidebarAdapter.isHidden(entry)) sidebarAdapter.unhideEntry(entry);
                else sidebarAdapter.hideEntry(entry);
                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                        .putStringSet("sidebar_hidden_items", sidebarAdapter.getHiddenItems()).apply();
            }
        });
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        List<String> sectionOrder = new ArrayList<>();
        try {
            List<String> saved = AppGson.get().fromJson(prefs.getString("sidebar_section_order", "[]"),
                    new TypeToken<List<String>>() {}.getType());
            if (saved != null) sectionOrder.addAll(saved);
        } catch (Exception ignored) {
        }
        for (String section : new String[]{"storage", "bookmarks", "tools", "plugins"}) {
            if (!sectionOrder.contains(section)) sectionOrder.add(section);
        }
        if (prefs.getBoolean("sidebar_show_bookmark_groups", false)) {
            for (String group : activity.bookmarkGroups()) {
                String section = "bookmark_group:" + group;
                if (!sectionOrder.contains(section)) sectionOrder.add(section);
            }
        }
        List<String> toolOrder = new ArrayList<>();
        try {
            List<String> saved = AppGson.get().fromJson(prefs.getString("sidebar_tool_order", "[]"),
                    new TypeToken<List<String>>() {}.getType());
            if (saved != null) toolOrder.addAll(saved);
        } catch (Exception ignored) {
        }
        sidebarAdapter.setToolOrder(toolOrder);
        sidebarAdapter.setHiddenItems(prefs.getStringSet("sidebar_hidden_items", Collections.emptySet()));
        Set<String> collapsed = prefs.getStringSet("sidebar_collapsed_sections", Collections.emptySet());
        for (String section : new String[]{"storage", "bookmarks", "tools", "plugins"}) {
            sidebarAdapter.setCollapsedState(section, collapsed.contains(section));
        }
        // Data intentionally NOT loaded here: ensureSidebarData() fills the
        // adapter on first drawer open (async), keeping cold start free of
        // storage stats and PackageManager queries.
        sidebarList.setAdapter(sidebarAdapter);
        sidebarList.setOnItemClickListener((parent, view, position, id) -> {
            SidebarAdapter.SidebarEntry entry = sidebarAdapter.getEntry(position);
            if (sidebarOrganizeMode) {
                if (entry != null && entry.type() != SidebarAdapter.EntryType.HEADER) {
                    startSidebarDrag(view, entry);
                }
                return;
            }
            if (entry != null) openSidebarEntry(entry);
        });
        sidebarList.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!sidebarOrganizeMode) return false;
            SidebarAdapter.SidebarEntry entry = sidebarAdapter.getEntry(position);
            if (entry == null || entry.type() == SidebarAdapter.EntryType.STORAGE) return false;
            startSidebarDrag(view, entry);
            return true;
        });
        sidebarList.setOnDragListener((v, event) -> handleSidebarDrag(event));
        sidebarRefresh = activity.findViewById(R.id.sidebarRefresh);
        if (sidebarRefresh != null) {
            sidebarRefresh.setOnRefreshListener(() -> {
                refreshSidebar(getSidebarSectionOrder());
                sidebarRefresh.setRefreshing(false);
            });
        }
        ImageButton organizeButton = activity.findViewById(R.id.sidebarOrganizeButton);
        organizeButton.setOnLongClickListener(v -> {
            Snackbar.make(activity.findViewById(android.R.id.content), R.string.organize_sidebar, Snackbar.LENGTH_SHORT).show();
            return false;
        });
        organizeButton.setOnClickListener(v -> setSidebarOrganizeMode(!sidebarOrganizeMode));
        registerStorageRefreshReceiver();
    }

    private boolean handleOrganizeTouch(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            organizeDragStarted = false;
            organizeDownX = event.getX();
            organizeDownY = event.getY();
            int position = sidebarList.pointToPosition((int) event.getX(), (int) event.getY());
            organizeDragEntry = sidebarAdapter.getEntry(position);
            int childIndex = position - sidebarList.getFirstVisiblePosition();
            organizeDragView = childIndex >= 0 && childIndex < sidebarList.getChildCount()
                    ? sidebarList.getChildAt(childIndex) : null;
            organizeDragCard = organizeDragView;
            if (organizeDragEntry != null && organizeDragEntry.type() != SidebarAdapter.EntryType.HEADER
                    && organizeDragView != null) {
                activity.handler.postDelayed(organizeLongPressRunnable, ViewConfiguration.getLongPressTimeout());
            }
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE && !organizeDragStarted
                && Math.hypot(event.getX() - organizeDownX, event.getY() - organizeDownY)
                > ViewConfiguration.get(activity).getScaledTouchSlop()) {
            activity.handler.removeCallbacks(organizeLongPressRunnable);
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            if (!organizeDragStarted) activity.handler.removeCallbacks(organizeLongPressRunnable);
            return false;
        }
        return organizeDragStarted;
    }

    private void startSidebarDrag(View view, SidebarAdapter.SidebarEntry entry) {
        View card = view;
        while (card.getParent() instanceof View && card.getParent() != sidebarList) {
            card = (View) card.getParent();
        }
        organizeDragCard = card;
        ClipData data = ClipData.newPlainText("sidebar", entry.dragPayload());
        View.DragShadowBuilder shadow = new View.DragShadowBuilder(card);
        card.setAlpha(0.45f);
        if (Build.VERSION.SDK_INT >= 24) {
            card.startDragAndDrop(data, shadow, null, 0);
        } else {
            card.startDrag(data, shadow, null, 0);
        }
    }

    private void setSidebarOrganizeMode(boolean enabled) {
        sidebarOrganizeMode = enabled;
        if (sidebarAdapter != null) sidebarAdapter.setOrganizeMode(enabled);
        if (sidebarRefresh != null) {
            sidebarRefresh.setRefreshing(false);
            sidebarRefresh.setEnabled(!enabled);
        }
        if (sidebarList != null) {
            sidebarList.setAlpha(enabled ? 0.85f : 1.0f);
            if (enabled) sidebarList.setOnTouchListener((v, event) -> handleOrganizeTouch(event));
            else sidebarList.setOnTouchListener(null);
        }
        if (!enabled) {
            activity.handler.removeCallbacks(organizeLongPressRunnable);
            organizeDragStarted = false;
            organizeDragView = null;
            organizeDragCard = null;
            organizeDragEntry = null;
        }
        ImageButton organizeButton = activity.findViewById(R.id.sidebarOrganizeButton);
        organizeButton.setImageResource(enabled ? R.drawable.baseline_check_circle_24 : R.drawable.drag_handle_24px);
        String msg = activity.getString(enabled ? R.string.organize_sidebar : R.string.done_organizing_sidebar);
        organizeButton.setContentDescription(msg);
        Snackbar.make(activity.findViewById(android.R.id.content), msg, Snackbar.LENGTH_SHORT).show();
    }

    public List<String> getSidebarSectionOrder() {
        List<String> order = new ArrayList<>();
        try {
            List<String> saved = AppGson.get().fromJson(PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString("sidebar_section_order", "[]"), new TypeToken<List<String>>() {}.getType());
            if (saved != null) order.addAll(saved);
        } catch (Exception ignored) {
        }
        for (String section : new String[]{"storage", "bookmarks", "tools", "plugins"}) {
            if (!order.contains(section)) order.add(section);
        }
        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean("sidebar_show_bookmark_groups", false)) {
            for (String group : activity.bookmarkGroups()) {
                String section = "bookmark_group:" + group;
                if (!order.contains(section)) order.add(section);
            }
        }
        return order;
    }

    public void refreshSidebar(List<String> sectionOrder) {
        if (sidebarAdapter == null || !sidebarDataReady) return;
        try {
            SidebarData data = loadSidebarData(sectionOrder);
            if (data == null) return;
            sidebarAdapter.setData(data.order, data.showBookmarks, data.showGroups,
                    data.storage, data.bookmarks, data.groups, data.labels,
                    data.showPlugins);
        } catch (Exception ignored) {
        }
    }

    /**
     * Surprise-arrival refresh (resume, media broadcast, root probe): same as
     * {@link #refreshSidebar} but the I/O runs off the main thread. Explicit
     * user actions (pull-to-refresh, organize) keep the synchronous variant.
     */
    public void refreshSidebarAsync(List<String> sectionOrder) {
        if (sidebarAdapter == null || !sidebarDataReady) return;
        new Thread(() -> {
            final SidebarData data;
            try {
                data = loadSidebarData(sectionOrder);
            } catch (Exception ignored) {
                return;
            }
            if (data == null) return;
            try {
                activity.handler.post(() -> {
                    try {
                        if (sidebarAdapter == null || activity.isFinishing() || activity.isDestroyed()) return;
                        sidebarAdapter.setData(data.order, data.showBookmarks, data.showGroups,
                                data.storage, data.bookmarks, data.groups, data.labels,
                                data.showPlugins);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }, "sidebar-refresh").start();
    }

    /**
     * First-open path: marks data ready immediately (coalescing concurrent
     * triggers), gathers everything off the main thread, then binds on it.
     * The drawer opens right away; rows populate moments later.
     */
    public void ensureSidebarData() {
        if (sidebarAdapter == null || sidebarDataReady) return;
        sidebarDataReady = true;
        new Thread(() -> {
            try {
                // Warm the plugin caches off-main so the bind below rarely
                // hits PackageManager on the UI thread.
                try {
                    ExternalActions.sidebarEntries(activity);
                } catch (Exception ignored) {
                }
                try {
                    ExtensionRegistry.sidebarActions();
                } catch (Exception ignored) {
                }
            } catch (Exception ignored) {
            }
            final SidebarData data;
            try {
                data = loadSidebarData(getSidebarSectionOrder());
            } catch (Exception ignored) {
                return;
            }
            if (data == null) return;
            try {
                activity.handler.post(() -> {
                    try {
                        if (sidebarAdapter == null || activity.isFinishing() || activity.isDestroyed()) return;
                        sidebarAdapter.setData(data.order, data.showBookmarks, data.showGroups,
                                data.storage, data.bookmarks, data.groups, data.labels,
                                data.showPlugins);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }, "sidebar-load").start();
    }

    /** Raw sidebar inputs; safe on any thread, never touches views. */
    private SidebarData loadSidebarData(List<String> sectionOrder) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        SidebarData data = new SidebarData();
        data.order = sectionOrder;
        try {
            data.showBookmarks = prefs.getBoolean("sidebar_show_bookmarks", true);
        } catch (Exception ignored) {
            data.showBookmarks = true;
        }
        try {
            data.showGroups = prefs.getBoolean("sidebar_show_bookmark_groups", false);
        } catch (Exception ignored) {
            data.showGroups = false;
        }
        try {
            data.showPlugins = prefs.getBoolean("sidebar_show_plugins", false);
        } catch (Exception ignored) {
            data.showPlugins = true;
        }
        data.groups = new LinkedHashMap<>();
        try {
            for (String group : activity.bookmarkGroups()) data.groups.put(group, activity.groupBookmarks(group));
        } catch (Exception ignored) {
        }
        data.storage = new ArrayList<>();
        try {
            List<StorageUtil.StorageInfo> infos = StorageUtil.getStorageInfos(activity);
            if (infos != null) data.storage = infos;
        } catch (Exception ignored) {
        }
        data.bookmarks = new ArrayList<>();
        try {
            ArrayList<File> marks = activity.getBookmarks();
            if (marks != null) data.bookmarks = marks;
        } catch (Exception ignored) {
        }
        data.labels = new LinkedHashMap<>();
        try {
            Map<String, String> labels = activity.bookmarkLabels();
            if (labels != null) data.labels.putAll(labels);
        } catch (Exception ignored) {
        }
        return data;
    }

    private static final class SidebarData {
        List<String> order;
        boolean showBookmarks;
        boolean showGroups;
        boolean showPlugins = true;
        List<StorageUtil.StorageInfo> storage;
        List<File> bookmarks;
        Map<String, List<File>> groups;
        Map<String, String> labels;
    }

    private void openSidebarEntry(SidebarAdapter.SidebarEntry entry) {
        if (sidebarOrganizeMode) return;
        if (entry == null || entry.type() == SidebarAdapter.EntryType.HEADER) return;
        if (entry.type() == SidebarAdapter.EntryType.STORAGE) {
            activity.loadFolderInPaneAsync(new File(entry.storage().path), activity.lastPaneSelected == 1, true);
        } else if (entry.type() == SidebarAdapter.EntryType.BOOKMARK) {
            File file = entry.file();
            activity.loadFolderInPaneAsync(file.isFile() ? file.getParentFile() : file, activity.lastPaneSelected == 1, true);
        } else {
            openSidebarTool(entry.id());
        }
        activity.closeSidebarDrawer();
    }

    private void openSidebarTool(String id) {
        switch (id) {
            case "extract":
                activity.startActivityForResult(new Intent(activity, APKExtractorActivity.class), 11);
                break;
            case "recycle_bin":
                showRecycleBinDialog();
                break;
            case "ftp_server":
                activity.showFtpServerDialog();
                break;
            case "ftp_client":
                activity.showFtpClientDialog();
                break;
            case "wifi":
                activity.startActivity(new Intent(activity, WifiManagerActivity.class));
                break;
            case "settings":
                activity.showSettingsDialog();
                break;
            case "tools":
                activity.startActivity(new Intent(activity, ToolsHubActivity.class));
                break;
            case "color_picker":
                activity.openColorPickerTool();
                break;
            case "layout":
                activity.openLayoutInspectorTool();
                break;
            default: {
                if (id != null && id.startsWith(SidebarAdapter.PACK_TOOL_PREFIX)) {
                    openPackTool(id.substring(SidebarAdapter.PACK_TOOL_PREFIX.length()));
                    break;
                }
                if (id != null && id.startsWith("ext:")) {
                    openExternalSidebar(id);
                    break;
                }
                SidebarAction ext = ExtensionRegistry.findSidebar(id);
                if (ext != null) {
                    try {
                        ext.open(activity);
                    } catch (Exception ignored) {
                    }
                }
                break;
            }
        }
    }

    /** Launches an installed pack tool in the shared runner activity. */
    private void openPackTool(String packAndToolId) {
        int dot = packAndToolId.indexOf('.');
        if (dot <= 0) return;
        String toolId = packAndToolId.substring(dot + 1);
        ToolPlugin tool = PluginRegistry.findCustom(toolId);
        String title = tool != null ? tool.title(activity) : null;
        if (title == null || title.isEmpty()) title = toolId;
        Intent intent = new Intent(activity, ToolRunnerActivity.class);
        intent.putExtra("tool_id", toolId);
        intent.putExtra("tool_title", title);
        activity.startActivity(intent);
    }

    /** Launches an external (out-of-process) sidebar plugin after consent. */
    private void openExternalSidebar(String id) {
        try {
            ExternalActions.Entry found = ExternalActions.findById(
                    ExternalActions.sidebarEntries(activity), id);
            if (found == null) return;
            Intent intent = PluginHost.explicitIntent(found.plugin,
                    PluginContracts.ACTION_SIDEBAR_OPEN);
            intent.putExtra(PluginContracts.EXTRA_PLUGIN_ID, found.plugin.pluginId);
            PluginTrust.ensureTrusted(activity, found.plugin, () -> {
                try {
                    activity.startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private boolean handleSidebarDrag(DragEvent event) {
        if (event.getAction() == DragEvent.ACTION_DRAG_STARTED) {
            return event.getClipDescription() != null;
        }
        if (event.getAction() == DragEvent.ACTION_DRAG_ENDED) {
            if (organizeDragCard != null) organizeDragCard.setAlpha(1f);
            for (int i = 0; i < sidebarList.getChildCount(); i++) {
                sidebarList.getChildAt(i).setAlpha(1f);
            }
            organizeDragCard = null;
            return true;
        }
        if (event.getAction() == DragEvent.ACTION_DROP && sidebarAdapter != null) {
            String payload = event.getClipData() == null || event.getClipData().getItemCount() == 0
                    ? null : event.getClipData().getItemAt(0).getText().toString();
            SidebarAdapter.SidebarEntry source = sidebarAdapter.findByPayload(payload);
            SidebarAdapter.SidebarEntry target = sidebarAdapter.getEntry(sidebarList.pointToPosition(
                    (int) event.getX(), (int) event.getY()));
            if (source == null || target == null) return false;
            if (source.type() == SidebarAdapter.EntryType.HEADER) {
                String targetSection = target.section();
                if (target.type() != SidebarAdapter.EntryType.HEADER) return false;
                if (!source.section().equals(targetSection)) {
                    sidebarAdapter.moveSection(source.section(), targetSection);
                    List<String> order = new ArrayList<>();
                    for (int i = 0; i < sidebarAdapter.getCount(); i++) {
                        SidebarAdapter.SidebarEntry entry = sidebarAdapter.getEntry(i);
                        if (entry.type() == SidebarAdapter.EntryType.HEADER && !order.contains(entry.section())) order.add(entry.section());
                    }
                    PreferenceManager.getDefaultSharedPreferences(activity).edit()
                            .putString("sidebar_section_order", AppGson.get().toJson(order)).apply();
                }
                return true;
            }
            if (!source.section().equals(target.section())) return false;
            if (source.type() == SidebarAdapter.EntryType.BOOKMARK) {
                persistSidebarBookmarkMove(source, target);
            }
            if (sidebarAdapter.moveEntry(source, target) && source.type() == SidebarAdapter.EntryType.TOOL) {
                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                        .putString("sidebar_tool_order", AppGson.get().toJson(sidebarAdapter.getToolOrder())).apply();
            }
            return true;
        }
        return true;
    }

    private void showSidebarBookmarkMenu(SidebarAdapter.SidebarEntry entry, View anchor) {
        String[] parts = entry.id().split("\\u0001", -1);
        if (parts.length < 3) return;
        int index = activity.findBookmarkIndex(parts[1], parts[2]);
        if (index < 0) return;
        activity.showBookmarkMenu(parts[1], index, anchor);
    }

    private void persistSidebarBookmarkMove(SidebarAdapter.SidebarEntry source, SidebarAdapter.SidebarEntry target) {
        String[] sourceParts = source.id().split("\\u0001", -1);
        String[] targetParts = target.id().split("\\u0001", -1);
        if (sourceParts.length < 3 || targetParts.length < 3 || !sourceParts[1].equals(targetParts[1])) return;
        activity.moveBookmark(sourceParts[1], sourceParts[2], targetParts[2]);
    }

    public void unregister() {
        try {
            if (storageRefreshReceiver != null) {
                activity.unregisterReceiver(storageRefreshReceiver);
                storageRefreshReceiver = null;
            }
        } catch (Exception ignored) {
        }
    }

    private void registerStorageRefreshReceiver() {
        try {
            if (storageRefreshReceiver != null) return;
            storageRefreshReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        refreshSidebarAsync(getSidebarSectionOrder());
                    } catch (Exception ignored) {
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_REMOVED);
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
            filter.addDataScheme("file");
            if (Build.VERSION.SDK_INT > 32) activity.registerReceiver(storageRefreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else activity.registerReceiver(storageRefreshReceiver, filter);
        } catch (Exception ignored) {
        }
    }

    private int recycleBinSortMode = 0; // 0 = Deletion time (desc), 1 = Modified time (desc), 2 = Name (asc)
    private final SimpleDateFormat recycleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public void showRecycleBinDialog() {
        List<RecycleBin.Entry> allEntries = RecycleBin.entries(activity);
        List<RecycleBin.Entry> filteredEntries = new ArrayList<>(allEntries);
        Set<RecycleBin.Entry> selectedEntries = new HashSet<>();

        int pad = (int) (12 * activity.getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        // Header view
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pad, (int) (8 * activity.getResources().getDisplayMetrics().density), pad, (int) (8 * activity.getResources().getDisplayMetrics().density));

        TextView titleText = new TextView(activity);
        titleText.setTextSize(18);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        titleText.setText(activity.getString(R.string.recycle_bin) + " (" + allEntries.size() + ")");
        header.addView(titleText);

        ImageButton filterBtn = new ImageButton(activity, null, android.R.attr.borderlessButtonStyle);
        filterBtn.setImageResource(R.drawable.baseline_search_24);
        filterBtn.setContentDescription(activity.getString(R.string.filter));
        header.addView(filterBtn);

        ImageButton sortBtn = new ImageButton(activity, null, android.R.attr.borderlessButtonStyle);
        sortBtn.setImageResource(R.drawable.baseline_sort_24);
        sortBtn.setContentDescription(activity.getString(R.string.sort_by));
        header.addView(sortBtn);

        root.addView(header);

        // Filter / search input row
        EditText searchInput = new EditText(activity);
        searchInput.setHint(activity.getString(R.string.filter));
        searchInput.setSingleLine(true);
        searchInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_search_24, 0, 0, 0);
        searchInput.setCompoundDrawablePadding((int) (8 * activity.getResources().getDisplayMetrics().density));
        searchInput.setPadding(pad, (int) (8 * activity.getResources().getDisplayMetrics().density), pad, (int) (8 * activity.getResources().getDisplayMetrics().density));
        searchInput.setVisibility(View.GONE);
        root.addView(searchInput);

        filterBtn.setOnClickListener(v -> {
            if (searchInput.getVisibility() == View.VISIBLE) {
                searchInput.setVisibility(View.GONE);
                searchInput.setText("");
            } else {
                searchInput.setVisibility(View.VISIBLE);
                searchInput.requestFocus();
            }
        });

        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        list.setLayoutParams(listParams);
        root.addView(list);

        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(pad, (int) (4 * activity.getResources().getDisplayMetrics().density), pad, (int) (4 * activity.getResources().getDisplayMetrics().density));

        Button actionLeftBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        actionLeftBtn.setText(activity.getString(R.string.empty_recycle_bin));
        actionLeftBtn.setTextSize(13);
        bottomBar.addView(actionLeftBtn);

        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1.0f));
        bottomBar.addView(spacer);

        Button actionRightBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        actionRightBtn.setText(activity.getString(R.string.close));
        actionRightBtn.setTextSize(13);
        bottomBar.addView(actionRightBtn);

        root.addView(bottomBar);

        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setView(root)
                .create();

        final RecyclerView.Adapter<?>[] adapterHolder = new RecyclerView.Adapter<?>[1];

        Runnable sortAndRefresh = () -> {
            Collections.sort(filteredEntries, (a, b) -> {
                if (recycleBinSortMode == 0) { // Deletion time (newest first)
                    return Long.compare(b.deletedAt, a.deletedAt);
                } else if (recycleBinSortMode == 1) { // Modified time (newest first)
                    return Long.compare(b.lastModified, a.lastModified);
                } else { // Name (alphabetical)
                    return a.displayName().compareToIgnoreCase(b.displayName());
                }
            });
            if (adapterHolder[0] != null) adapterHolder[0].notifyDataSetChanged();
            if (selectedEntries.isEmpty()) {
                titleText.setText(activity.getString(R.string.recycle_bin) + " (" + filteredEntries.size() + ")");
                actionLeftBtn.setText(activity.getString(R.string.empty_recycle_bin));
                actionRightBtn.setText(activity.getString(R.string.close));
            } else {
                titleText.setText(activity.getString(R.string.selected_count, selectedEntries.size()));
                actionLeftBtn.setText(activity.getString(R.string.restore_all));
                actionRightBtn.setText(activity.getString(R.string.delete_all));
            }
        };

        sortBtn.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(activity, sortBtn);
            popup.getMenu().add(0, 0, 0, activity.getString(R.string.sort_by_deletion_time));
            popup.getMenu().add(0, 1, 1, activity.getString(R.string.sort_by_modified_time));
            popup.getMenu().add(0, 2, 2, activity.getString(R.string.sort_by_name));
            popup.setOnMenuItemClickListener(item -> {
                recycleBinSortMode = item.getItemId();
                sortAndRefresh.run();
                return true;
            });
            popup.show();
        });

        actionLeftBtn.setOnClickListener(v -> {
            if (!selectedEntries.isEmpty()) {
                // Restore selected
                List<RecycleBin.Entry> toRestore = new ArrayList<>(selectedEntries);
                for (RecycleBin.Entry e : toRestore) {
                    RecycleBin.restoreEntry(activity, e);
                }
                selectedEntries.clear();
                allEntries.clear();
                allEntries.addAll(RecycleBin.entries(activity));
                filterList(searchInput.getText().toString(), allEntries, filteredEntries, adapterHolder[0], dialog, titleText);
                sortAndRefresh.run();
                Extensions.showMessage(activity, R.string.restored);
            } else {
                // Empty bin
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.empty_recycle_bin)
                        .setMessage(R.string.empty_recycle_bin_confirm)
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            ProgressManager pm = new ProgressManager(activity, true);
                            pm.setText(activity.getString(R.string.deleting));
                            pm.setOperation(R.string.op_deleting);
                            pm.setSource(RecycleBin.binDir(activity));
                            pm.show();
                            new Thread(() -> {
                                RecycleBin.emptyBin(activity);
                                activity.runOnUiThread(() -> {
                                    pm.dismiss();
                                    allEntries.clear();
                                    filteredEntries.clear();
                                    selectedEntries.clear();
                                    sortAndRefresh.run();
                                    Extensions.showMessage(activity, R.string.recycle_bin_emptied);
                                });
                            }).start();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });

        actionRightBtn.setOnClickListener(v -> {
            if (!selectedEntries.isEmpty()) {
                // Delete selected
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.delete_forever)
                        .setMessage(activity.getString(R.string.confirm_delete_f, selectedEntries.size() + " items"))
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            List<RecycleBin.Entry> toDel = new ArrayList<>(selectedEntries);
                            ProgressManager pm = new ProgressManager(activity, false);
                            pm.setText(activity.getString(R.string.deleting));
                            pm.setOperation(R.string.op_deleting);
                            pm.setTotal(toDel.size());
                            pm.show();
                            new Thread(() -> {
                                for (int i = 0; i < toDel.size(); i++) {
                                    RecycleBin.Entry entry = toDel.get(i);
                                    pm.setFile(entry.displayName());
                                    pm.setPaths(entry.originPath != null ? entry.originPath
                                            : entry.file.getAbsolutePath(), null);
                                    RecycleBin.deleteEntry(activity, entry);
                                    pm.setProgress(i + 1, toDel.size());
                                }
                                activity.runOnUiThread(() -> {
                                    pm.dismiss();
                                    selectedEntries.clear();
                                    allEntries.clear();
                                    allEntries.addAll(RecycleBin.entries(activity));
                                    filterList(searchInput.getText().toString(), allEntries, filteredEntries, adapterHolder[0], dialog, titleText);
                                    sortAndRefresh.run();
                                    Extensions.showMessage(activity, R.string.item_deleted);
                                });
                            }).start();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                dialog.dismiss();
            }
        });

        RecyclerView.Adapter<?> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(pad, (int) (8 * activity.getResources().getDisplayMetrics().density), pad, (int) (8 * activity.getResources().getDisplayMetrics().density));

                CheckBox cb = new CheckBox(activity);
                cb.setVisibility(View.GONE);
                row.addView(cb);

                ImageView icon = new ImageView(activity);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                        (int) (32 * activity.getResources().getDisplayMetrics().density),
                        (int) (32 * activity.getResources().getDisplayMetrics().density));
                icon.setLayoutParams(iconLp);
                row.addView(icon);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textLp.setMarginStart(pad);
                textCol.setLayoutParams(textLp);

                TextView title = new TextView(activity);
                title.setTextSize(14);
                title.setTypeface(null, Typeface.BOLD);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(title);

                TextView sub = new TextView(activity);
                sub.setTextSize(11);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(sub);

                row.addView(textCol);
                return new RecyclerView.ViewHolder(row) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                RecycleBin.Entry entry = filteredEntries.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                CheckBox cb = (CheckBox) row.getChildAt(0);
                ImageView icon = (ImageView) row.getChildAt(1);
                LinearLayout textCol = (LinearLayout) row.getChildAt(2);
                TextView title = (TextView) textCol.getChildAt(0);
                TextView sub = (TextView) textCol.getChildAt(1);

                title.setText(entry.displayName());

                // Subtitle: modified date in white, deleted date in red (#FF5252)
                String modStr = recycleDateFormat.format(new Date(entry.lastModified));
                String delStr = recycleDateFormat.format(new Date(entry.deletedAt));
                SpannableStringBuilder ssb = new SpannableStringBuilder();
                int modStart = 0;
                ssb.append(modStr);
                ssb.setSpan(new ForegroundColorSpan(Color.WHITE), modStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.append("    ");
                int delStart = ssb.length();
                ssb.append(delStr);
                ssb.setSpan(new ForegroundColorSpan(0xFFFF5252), delStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sub.setText(ssb);

                if (entry.file.isDirectory()) {
                    icon.setImageResource(R.drawable.folder_24px);
                } else {
                    icon.setImageResource(R.drawable.baseline_insert_drive_file_24);
                }

                if (!selectedEntries.isEmpty()) {
                    cb.setVisibility(View.VISIBLE);
                    cb.setChecked(selectedEntries.contains(entry));
                } else {
                    cb.setVisibility(View.GONE);
                }

                row.setOnClickListener(v -> {
                    if (!selectedEntries.isEmpty()) {
                        if (selectedEntries.contains(entry)) {
                            selectedEntries.remove(entry);
                        } else {
                            selectedEntries.add(entry);
                        }
                        sortAndRefresh.run();
                    } else {
                        showRecycleBinItemActions(entry, () -> {
                            allEntries.clear();
                            allEntries.addAll(RecycleBin.entries(activity));
                            filterList(searchInput.getText().toString(), allEntries, filteredEntries, adapterHolder[0], dialog, titleText);
                            sortAndRefresh.run();
                        });
                    }
                });

                row.setOnLongClickListener(v -> {
                    if (selectedEntries.contains(entry)) {
                        selectedEntries.remove(entry);
                    } else {
                        selectedEntries.add(entry);
                    }
                    sortAndRefresh.run();
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return filteredEntries.size();
            }
        };
        adapterHolder[0] = adapter;
        list.setAdapter(adapter);

        // Apply initial sort (by deletion time desc)
        sortAndRefresh.run();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                filterList(s.toString(), allEntries, filteredEntries, adapter, dialog, titleText);
                sortAndRefresh.run();
            }
        });

        activity.dialogUtil.styleAlertDialog(dialog);
    }

    private void filterList(String query, List<RecycleBin.Entry> all, List<RecycleBin.Entry> filtered,
                            RecyclerView.Adapter<?> adapter, AlertDialog dialog, TextView titleText) {
        filtered.clear();
        String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
        for (RecycleBin.Entry e : all) {
            if (q.isEmpty() || e.displayName().toLowerCase(Locale.ROOT).contains(q)
                    || (e.originPath != null && e.originPath.toLowerCase(Locale.ROOT).contains(q))) {
                filtered.add(e);
            }
        }
        if (titleText != null) {
            titleText.setText(activity.getString(R.string.recycle_bin) + " (" + filtered.size() + ")");
        }
    }

    private void showRecycleBinItemActions(RecycleBin.Entry entry, Runnable onUpdated) {
        String modStr = recycleDateFormat.format(new Date(entry.lastModified));
        String delStr = recycleDateFormat.format(new Date(entry.deletedAt));
        String reasonStr = "overwrite".equals(entry.reason)
                ? activity.getString(R.string.recycle_bin_reason_overwrite)
                : activity.getString(R.string.recycle_bin_reason_delete);

        StringBuilder details = new StringBuilder();
        if (entry.originPath != null && !entry.originPath.isEmpty()) {
            details.append(activity.getString(R.string.deleted_from_x, entry.originPath)).append("\n");
        }
        details.append(activity.getString(R.string.status_label)).append(": ").append(reasonStr).append("\n");
        details.append(activity.getString(R.string.show_last_updated)).append(": ").append(modStr).append("\n");
        details.append(activity.getString(R.string.sort_by_deletion_time)).append(": ").append(delStr);

        String[] options = {
                activity.getString(R.string.delete_forever),
                activity.getString(R.string.rename),
                activity.getString(R.string.restore)
        };
        new AlertDialog.Builder(activity)
                .setTitle(entry.displayName())
                .setMessage(details.toString())
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        RecycleBin.deleteEntry(activity, entry);
                        Extensions.showMessage(activity, R.string.item_deleted);
                        if (onUpdated != null) onUpdated.run();
                    } else if (which == 1) {
                        showRenameBinnedItemDialog(entry, onUpdated);
                    } else if (which == 2) {
                        RecycleBin.RestoreResult res = RecycleBin.restoreEntry(activity, entry);
                        if (res != null && res.restoredFile != null) {
                            if (res.wasRenamed) {
                                String msg = activity.getString(R.string.restored_renamed_toast, res.restoredFile.getName());
                                View anchor = activity.findViewById(R.id.drawer_layout);
                                if (anchor != null) {
                                    Snackbar sb = Snackbar.make(anchor, msg, 3000);
                                    sb.getView().setOnTouchListener((v, ev) -> {
                                        sb.dismiss();
                                        return true;
                                    });
                                    sb.show();
                                } else {
                                    Extensions.showMessage(activity, msg);
                                }
                            } else {
                                Extensions.showMessage(activity, activity.getString(R.string.restore_to_x, res.restoredFile.getParent()));
                            }
                            activity.loadFolderInPane(res.restoredFile.getParentFile(), activity.lastPaneSelected == 1);
                            if (onUpdated != null) onUpdated.run();
                        } else {
                            Extensions.showMessage(activity, R.string.restore_failed);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRenameBinnedItemDialog(RecycleBin.Entry entry, Runnable onUpdated) {
        EditText input = new EditText(activity);
        input.setText(entry.displayName());
        input.setSingleLine(true);
        input.setSelection(0, entry.displayName().lastIndexOf('.') > 0 ? entry.displayName().lastIndexOf('.') : entry.displayName().length());
        new AlertDialog.Builder(activity)
                .setTitle(R.string.rename)
                .setView(input)
                .setPositiveButton(R.string.rename, (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(entry.displayName())) {
                        if (RecycleBin.renameEntry(activity, entry, newName)) {
                            Extensions.showMessage(activity, R.string.renamed);
                            if (onUpdated != null) onUpdated.run();
                        } else {
                            Extensions.showMessage(activity, R.string.failed);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void showRecycleBinOptionsDialog() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        SwitchCompat toggle = new SwitchCompat(activity);
        toggle.setText(activity.getString(R.string.enable_recycle_bin));
        boolean enabled = !RecycleBin.MODE_NEVER.equals(RecycleBin.mode(activity));
        toggle.setChecked(enabled);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            RecycleBin.setMode(activity, isChecked ? RecycleBin.MODE_ALWAYS : RecycleBin.MODE_NEVER);
        });
        layout.addView(toggle);

        TextView retentionLabel = new TextView(activity);
        retentionLabel.setText(activity.getString(R.string.recycle_bin_retention));
        retentionLabel.setTypeface(null, Typeface.BOLD);
        retentionLabel.setPadding(0, pad, 0, (int) (8 * activity.getResources().getDisplayMetrics().density));
        layout.addView(retentionLabel);

        int[] dayOptions = {1, 7, 30, 0};
        String[] dayLabels = {
                activity.getString(R.string.days_x, 1),
                activity.getString(R.string.days_x, 7),
                activity.getString(R.string.days_x, 30),
                activity.getString(R.string.forever)
        };
        int currentDays = RecycleBin.retentionDays(activity);
        int checkedIdx = 2; // default 30
        for (int i = 0; i < dayOptions.length; i++) {
            if (dayOptions[i] == currentDays) {
                checkedIdx = i;
                break;
            }
        }

        RadioGroup group = new RadioGroup(activity);
        for (int i = 0; i < dayOptions.length; i++) {
            RadioButton rb = new RadioButton(activity);
            rb.setText(dayLabels[i]);
            rb.setId(i);
            if (i == checkedIdx) rb.setChecked(true);
            group.addView(rb);
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            if (checkedId >= 0 && checkedId < dayOptions.length) {
                RecycleBin.setRetentionDays(activity, dayOptions[checkedId]);
            }
        });
        layout.addView(group);

        Button purgeBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        purgeBtn.setText("PURGE EXPIRED NOW");
        purgeBtn.setOnClickListener(v -> {
            int purged = RecycleBin.purgeExpired(activity);
            Extensions.showMessage(activity, activity.getString(R.string.recycle_bin_purged_x, purged));
        });
        layout.addView(purgeBtn);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.recycle_bin_options)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}

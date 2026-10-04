package io.github.abdurazaaqmohammed.features.files;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.PopupMenu;

import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.SidebarAdapter;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.SidebarAction;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust;
import io.github.abdurazaaqmohammed.tools.StorageManagerActivity;
import io.github.abdurazaaqmohammed.tools.SmaliReferenceActivity;
import io.github.abdurazaaqmohammed.tools.ToolsHubActivity;
import io.github.abdurazaaqmohammed.tools.WifiManagerActivity;
import io.github.abdurazaaqmohammed.utils.StorageUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
                Set<String> saved = new java.util.HashSet<>(prefs.getStringSet("sidebar_collapsed_sections", Collections.emptySet()));
                if (collapsed) saved.add(entry.section());
                else saved.remove(entry.section());
                prefs.edit().putStringSet("sidebar_collapsed_sections", saved).apply();
            }

            @Override
            public void onEntryStorageLongPressed(SidebarAdapter.SidebarEntry entry, View view) {
                PopupMenu menu = new PopupMenu(activity, view);
                menu.getMenu().add(R.string.manage_storage);
                menu.getMenu().add(R.string.open_location);
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
            List<String> saved = new Gson().fromJson(prefs.getString("sidebar_section_order", "[]"),
                    new TypeToken<List<String>>() {}.getType());
            if (saved != null) sectionOrder.addAll(saved);
        } catch (Exception ignored) {
        }
        for (String section : new String[]{"storage", "bookmarks", "tools"}) {
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
            List<String> saved = new Gson().fromJson(prefs.getString("sidebar_tool_order", "[]"),
                    new TypeToken<List<String>>() {}.getType());
            if (saved != null) toolOrder.addAll(saved);
        } catch (Exception ignored) {
        }
        sidebarAdapter.setToolOrder(toolOrder);
        sidebarAdapter.setHiddenItems(prefs.getStringSet("sidebar_hidden_items", Collections.emptySet()));
        java.util.Set<String> collapsed = prefs.getStringSet("sidebar_collapsed_sections", Collections.emptySet());
        for (String section : new String[]{"storage", "bookmarks", "tools"}) {
            sidebarAdapter.setCollapsedState(section, collapsed.contains(section));
        }
        refreshSidebar(sectionOrder);
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
            return true;
        }
        return true;
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
            List<String> saved = new Gson().fromJson(PreferenceManager.getDefaultSharedPreferences(activity)
                    .getString("sidebar_section_order", "[]"), new TypeToken<List<String>>() {}.getType());
            if (saved != null) order.addAll(saved);
        } catch (Exception ignored) {
        }
        for (String section : new String[]{"storage", "bookmarks", "tools"}) {
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
        if (sidebarAdapter == null) return;
        try {
            Map<String, List<File>> groups = new LinkedHashMap<>();
            for (String group : activity.bookmarkGroups()) groups.put(group, activity.groupBookmarks(group));
            List<StorageUtil.StorageInfo> storage = new ArrayList<>();
            try {
                storage = StorageUtil.getStorageInfos(activity);
            } catch (Exception ignored) {
            }
            sidebarAdapter.setData(sectionOrder,
                    PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("sidebar_show_bookmarks", true),
                    PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("sidebar_show_bookmark_groups", false),
                    storage, activity.getBookmarks(), groups, activity.bookmarkLabels());
        } catch (Exception ignored) {
        }
    }

    private void openSidebarEntry(SidebarAdapter.SidebarEntry entry) {
        if (sidebarOrganizeMode) return;
        if (entry == null || entry.type() == SidebarAdapter.EntryType.HEADER) return;
        if (entry.type() == SidebarAdapter.EntryType.STORAGE) {
            activity.loadFolderInPane(new File(entry.storage().path), activity.lastPaneSelected == 1);
        } else if (entry.type() == SidebarAdapter.EntryType.BOOKMARK) {
            File file = entry.file();
            activity.loadFolderInPane(file.isFile() ? file.getParentFile() : file, activity.lastPaneSelected == 1);
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
            case "ftp_server":
                activity.showFtpServerDialog();
                break;
            case "ftp_client":
                activity.showFtpClientDialog();
                break;
            case "remote":
                activity.showRemoteConnectionsDialog();
                break;
            case "root":
                activity.openFilesystemRoot();
                break;
            case "settings":
                activity.showSettingsDialog();
                break;
            case "smali_reference":
                activity.startActivity(new Intent(activity, SmaliReferenceActivity.class));
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
                            .putString("sidebar_section_order", new Gson().toJson(order)).apply();
                }
                return true;
            }
            if (!source.section().equals(target.section())) return false;
            if (source.type() == SidebarAdapter.EntryType.BOOKMARK) {
                persistSidebarBookmarkMove(source, target);
            }
            if (sidebarAdapter.moveEntry(source, target) && source.type() == SidebarAdapter.EntryType.TOOL) {
                PreferenceManager.getDefaultSharedPreferences(activity).edit()
                        .putString("sidebar_tool_order", new Gson().toJson(sidebarAdapter.getToolOrder())).apply();
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
                    activity.handler.post(() -> {
                        try {
                            refreshSidebar(getSidebarSectionOrder());
                        } catch (Exception ignored) {
                        }
                    });
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
}

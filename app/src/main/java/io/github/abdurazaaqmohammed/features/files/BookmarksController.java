package io.github.abdurazaaqmohammed.features.files;

import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.BookmarksAdapter;
import io.github.abdurazaaqmohammed.core.ui.util.ViewPagers;
import io.github.abdurazaaqmohammed.data.prefs.BookmarkStore;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

/**
 * Bookmarks drawer UI: lists, groups, pager, labels, batch ops, drag reorder.
 * Extracted from MainActivity. Persistence lives in data.prefs.BookmarkStore.
 */
public class BookmarksController {

    private final MainActivity activity;
    private final BookmarkStore store;

    private ArrayList<File> bookmarks;
    private final List<String> bookmarkGroups = new ArrayList<>();
    private View.OnTouchListener bookmarksSwipeDownCloseListener;
    private final Map<String, String> bookmarkLabels = new java.util.HashMap<>();
    private BookmarkListController mainBookmarkController;
    private final Map<String, BookmarkListController> groupControllers = new LinkedHashMap<>();
    private BookmarkListController batchController;
    private final Set<Integer> batchSelected = new HashSet<>();
    private boolean bookmarkDragging;

    private BookmarksAdapter bookmarksAdapter;
    private TabLayout bookmarksTabs;
    private ViewPager2 bookmarksPager;
    private TabLayoutMediator bookmarksMediator;
    private ListView bookmarksList;
    private ListView historyList;

    public BookmarksController(MainActivity activity) {
        this.activity = activity;
        this.store = new BookmarkStore(activity);
    }

    public class BookmarkListController implements BookmarksAdapter.Callbacks {
        final String key; // "bookmarks" or a group name
        final ListView listView;
        final ArrayList<File> items;
        final BookmarksAdapter adapter;

        BookmarkListController(String key, ListView listView, ArrayList<File> items) {
            this.key = key;
            this.listView = listView;
            this.items = items;
            this.adapter = new BookmarksAdapter(activity, items, this);
        }

        @Override
        public String labelOf(File file) {
            String label = bookmarkLabels.get(file.getPath());
            return label != null ? label : file.getName();
        }

        @Override
        public void onDragHandleTouched(View handle, int position, MotionEvent initialEvent) {
            startBookmarkDrag(this, handle, position, initialEvent);
        }

        @Override
        public boolean isBatchMode() {
            return batchController == this;
        }

        @Override
        public boolean isBatchSelected(int position) {
            return batchSelected.contains(position);
        }

        @Override
        public boolean isDragging() {
            return bookmarkDragging;
        }
    }

    /**
     * Builds lists, tabs, pager and batch-bar wiring. Called from onCreate
     * with the history page (owned by MainActivity for navigation refresh).
     */
    public void setup(ListView historyList) {
        this.historyList = historyList;
        ListView list = this.bookmarksList = new ListView(activity);
        list.setDivider(null);
        list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setOnTouchListener(getSwipeDownCloseListener());
        mainBookmarkController = new BookmarkListController("bookmarks", list, getBookmarks());
        bookmarksAdapter = mainBookmarkController.adapter;
        list.setAdapter(bookmarksAdapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (handleBookmarkListClick(mainBookmarkController, position)) return;
            File bookmarked = mainBookmarkController.items.get(position);
            activity.loadFolderInPane(bookmarked.isFile() ? bookmarked.getParentFile() : bookmarked, activity.lastPaneSelected == 1);
            activity.closeBookmarksDrawer();
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showBookmarkItemMenu(mainBookmarkController, position);
            return true;
        });

        loadBookmarkGroups();
        loadBookmarkLabels();
        bookmarksTabs = activity.findViewById(R.id.bookmarksTabs);
        bookmarksPager = activity.findViewById(R.id.bookmarksPager);
        rebuildBookmarksPager();
        bookmarksTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                PreferenceManager.getDefaultSharedPreferences(activity)
                        .edit().putInt("bookmarks_last_tab", tab.getPosition()).apply();
                if (batchController != null) exitBookmarkBatchMode();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        activity.findViewById(R.id.bookmarksMenuButton).setOnClickListener(this::showBookmarksBarMenu);
        activity.findViewById(R.id.bookmarksCloseButton).setOnClickListener(v -> activity.closeBookmarksDrawer());
        activity.findViewById(R.id.batchCopy).setOnClickListener(v -> batchCopyOrMove(true));
        activity.findViewById(R.id.batchMove).setOnClickListener(v -> batchCopyOrMove(false));
        activity.findViewById(R.id.batchDelete).setOnClickListener(v -> batchDeleteSelected());
        activity.findViewById(R.id.batchCancel).setOnClickListener(v -> exitBookmarkBatchMode());
        applyBookmarksBarHeight(PreferenceManager.getDefaultSharedPreferences(activity).getInt("bookmarks_bar_pct", 35));
    }

    public View.OnTouchListener getSwipeDownCloseListener() {
        if (bookmarksSwipeDownCloseListener != null) return bookmarksSwipeDownCloseListener;
        bookmarksSwipeDownCloseListener = (v, event) -> {
            if (!(v instanceof ListView listView)) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setTag(listView.getFirstVisiblePosition() == 0
                            && (listView.getChildCount() == 0 || listView.getChildAt(0).getTop() >= 0)
                            ? event.getRawY() : null);
                    break;
                case MotionEvent.ACTION_MOVE: {
                    Object start = v.getTag();
                    if (start instanceof Float && event.getRawY() - (Float) start
                            > 80 * activity.getResources().getDisplayMetrics().density) {
                        v.setTag(null);
                        activity.closeBookmarksDrawer();
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setTag(null);
                    break;
            }
            return false;
        };
        return bookmarksSwipeDownCloseListener;
    }

    public ArrayList<File> getBookmarks() {
        if (bookmarks == null) {
            bookmarks = store.loadBookmarks();
        }
        return bookmarks;
    }

    public List<String> bookmarkGroups() {
        return bookmarkGroups;
    }

    public Map<String, String> bookmarkLabels() {
        return bookmarkLabels;
    }

    public ArrayList<File> groupBookmarks(String group) {
        return store.loadGroup(group);
    }

    public void addBookmark(File toBookmark) {
        if (PreferenceManager.getDefaultSharedPreferences(activity).getBoolean("ask_bookmark_tab", false)) {
            showBookmarkTargetDialog(toBookmark);
            return;
        }
        addBookmarkToTab(store.lastTabIndex(bookmarkGroups.size()), toBookmark);
    }

    private int getLastBookmarkTabIndex() {
        return store.lastTabIndex(bookmarkGroups.size());
    }

    private void addBookmarkToTab(int tabIndex, File file) {
        if (tabIndex >= 2 && tabIndex - 2 < bookmarkGroups.size()) {
            String group = bookmarkGroups.get(tabIndex - 2);
            List<File> items = store.loadGroup(group);
            items.add(file);
            store.saveGroup(group, items);
            refreshGroupList(group);
        } else {
            getBookmarks().add(file);
            if (bookmarksAdapter != null) bookmarksAdapter.notifyDataSetChanged();
        }
        activity.refreshSidebar(activity.getSidebarSectionOrder());
        Extensions.showMessage(activity, activity.rss.getString(R.string.added_to_bookmarks, file));
    }

    private void showBookmarkTargetDialog(File file) {
        List<String> targets = new ArrayList<>();
        targets.add(activity.rss.getString(R.string.bookmarks));
        targets.addAll(bookmarkGroups);
        int[] selected = {0};
        UIKit.dialog(activity)
                .setTitle(activity.rss.getString(R.string.bookmarks))
                .setSingleChoiceItems(targets.toArray(new String[0]), 0, (d, w) -> selected[0] = w)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> addBookmarkToTab(selected[0], file))
                .show();
    }

    private void refreshGroupList(String group) {
        BookmarkListController controller = groupControllers.get(group);
        if (controller != null) {
            controller.items.clear();
            controller.items.addAll(store.loadGroup(group));
            controller.adapter.notifyDataSetChanged();
        }
        activity.refreshSidebar(activity.getSidebarSectionOrder());
    }

    private void rebuildBookmarksPager() {
        groupControllers.clear();
        List<ListView> pages = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        pages.add(bookmarksList);
        titles.add(activity.rss.getString(R.string.bookmarks));
        pages.add(historyList);
        titles.add(activity.rss.getString(R.string.history));
        for (String group : bookmarkGroups) {
            pages.add(createGroupListView(group));
            titles.add(group);
        }

        bookmarksPager.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new RecyclerView.ViewHolder(pages.get(viewType)) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            }

            @Override
            public int getItemCount() {
                return pages.size();
            }

            @Override
            public int getItemViewType(int position) {
                return position;
            }
        });
        bookmarksPager.setOffscreenPageLimit(pages.size());
        ViewPagers.reduceDragSensitivity(bookmarksPager);

        bookmarksTabs.removeAllTabs();
        if (bookmarksMediator != null) bookmarksMediator.detach();
        bookmarksMediator = new TabLayoutMediator(bookmarksTabs, bookmarksPager,
                (tab, position) -> tab.setText(titles.get(position)));
        bookmarksMediator.attach();

        int lastTab = PreferenceManager.getDefaultSharedPreferences(activity).getInt("bookmarks_last_tab", 0);
        if (bookmarksTabs.getTabAt(lastTab) != null) bookmarksTabs.getTabAt(lastTab).select();
    }

    private ListView createGroupListView(String group) {
        ListView listView = new ListView(activity);
        listView.setDivider(null);
        listView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listView.setOnTouchListener(getSwipeDownCloseListener());
        BookmarkListController controller = new BookmarkListController(group, listView, store.loadGroup(group));
        groupControllers.put(group, controller);
        listView.setAdapter(controller.adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (handleBookmarkListClick(controller, position)) return;
            File bookmarked = controller.items.get(position);
            activity.loadFolderInPane(bookmarked.isFile() ? bookmarked.getParentFile() : bookmarked, activity.lastPaneSelected == 1);
            activity.closeBookmarksDrawer();
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            showBookmarkItemMenu(controller, position);
            return true;
        });
        return listView;
    }

    private void showBookmarksBarMenu(View anchor) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(activity, anchor);
        String ag = activity.rss.getString(R.string.add_group);
        String ah = activity.rss.getString(R.string.adjust_height);
        popup.getMenu().add(ag);
        popup.getMenu().add(ah);
        popup.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (android.text.TextUtils.isEmpty(title)) ;
            else if (ag.contentEquals(title)) showAddGroupDialog();
            else showAdjustHeightDialog();
            return true;
        });
        popup.show();
    }

    private void showAddGroupDialog() {
        EditText input = new EditText(activity);
        input.setHint(activity.rss.getString(R.string.group_name));
        UIKit.dialog(activity)
            .setTitle(activity.rss.getString(R.string.add_group))
            .setView(UiFields.wrap(activity, input, null, 16))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) {
                    Extensions.showMessage(activity, R.string.group_name_e);
                    return;
                }
                if (bookmarkGroups.contains(name)) {
                    Extensions.showMessage(activity, R.string.group_already_exists);
                    return;
                }
                 bookmarkGroups.add(name);
                 store.saveGroups(bookmarkGroups);
                 rebuildBookmarksPager();
                 activity.refreshSidebar(activity.getSidebarSectionOrder());

                bookmarksPager.post(() -> {
                    int tab = bookmarkGroups.size() + 1;
                    if (bookmarksTabs.getTabAt(tab) != null) bookmarksTabs.getTabAt(tab).select();
                });
            }).show();
    }

    private void showAdjustHeightDialog() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
        int currentPct = settings.getInt("bookmarks_bar_pct", 35);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_adjust_height, null);
        TextInputEditText input = view.findViewById(R.id.heightInput);
        SeekBar seekBar = view.findViewById(R.id.heightSeekBar);
        input.setText(String.valueOf(currentPct));
        seekBar.setProgress(currentPct - 15);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = progress + 15;
                input.setText(String.valueOf(pct));
                input.setSelection(input.length());
                applyBookmarksBarHeight(pct); // live preview while scrolling
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    int pct = Integer.parseInt(s.toString());
                    if (pct >= 15 && pct <= 90) {
                        seekBar.setProgress(pct - 15);
                        applyBookmarksBarHeight(pct);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AlertDialog heightDialog = UIKit.dialog(activity)
                .setTitle(activity.getString(R.string.adjust_height))
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    try {
                        int pct = Math.max(15, Math.min(90, Integer.parseInt(input.getText().toString().trim())));
                        settings.edit().putInt("bookmarks_bar_pct", pct).apply();
                        applyBookmarksBarHeight(pct);
                    } catch (NumberFormatException ignored) {
                    }
                }).create();
        // Cancel restores the saved height after any live preview
        heightDialog.setOnDismissListener(d -> applyBookmarksBarHeight(settings.getInt("bookmarks_bar_pct", 35)));
        heightDialog.show();
    }

    private boolean handleBookmarkListClick(BookmarkListController controller, int position) {
        if (batchController == controller) {
            if (batchSelected.contains(position)) batchSelected.remove(position);
            else batchSelected.add(position);
            controller.adapter.notifyDataSetChanged();
            return true;
        }
        return false;
    }

    private void showBookmarkItemMenu(BookmarkListController controller, int position) {
        showBookmarkItemMenu(controller, position, controller.listView);
    }

    public void showBookmarkMenu(String key, int index, View anchor) {
        BookmarkListController controller = key.equals("bookmarks")
                ? mainBookmarkController : groupControllers.get(key);
        if (controller == null) return;
        showBookmarkItemMenu(controller, index, anchor);
    }

    private void showBookmarkItemMenu(BookmarkListController controller, int position, View anchor) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(activity, anchor);
        String edit = activity.rss.getString(R.string.edit_bookmark);
        String move = activity.rss.getString(R.string.move);
        String delete = activity.rss.getString(R.string.delete);
        String batch = activity.rss.getString(R.string.batch_operations);
        popup.getMenu().add(edit);
        popup.getMenu().add(move);
        popup.getMenu().add(delete);
        popup.getMenu().add(batch);
        popup.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            if (delete.contentEquals(title)) confirmDeleteBookmark(controller, position);
            else if (move.contentEquals(title)) showMoveBookmarkDialog(controller, position);
            else if (batch.contentEquals(title)) enterBookmarkBatchMode(controller);
            else showEditBookmarkDialog(controller, position);
            return true;
        });
        popup.show();
    }

    private void showEditBookmarkDialog(BookmarkListController controller, int position) {
        File file = controller.items.get(position);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_edit_bookmark, null);
        TextInputEditText nameInput = view.findViewById(R.id.bookmarkNameInput);
        TextInputEditText pathInput = view.findViewById(R.id.bookmarkPathInput);
        nameInput.setText(controller.labelOf(file));
        pathInput.setText(file.getPath());
        UIKit.dialog(activity)
                .setTitle(activity.rss.getString(R.string.edit_bookmark))
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newName = nameInput.getText().toString().trim();
                    File newFile = new File(pathInput.getText().toString().trim());
                    File finalFile = newFile.getPath().equals(file.getPath()) ? file : newFile;
                    if (finalFile != file) {
                        bookmarkLabels.remove(file.getPath());
                        controller.items.set(position, finalFile);
                    }
                    if (!newName.isEmpty() && !newName.equals(finalFile.getName()))
                        bookmarkLabels.put(finalFile.getPath(), newName);
                     else
                         bookmarkLabels.remove(finalFile.getPath());
                     store.saveLabels(bookmarkLabels);
                     persistBookmarkOrder(controller);
                     controller.adapter.notifyDataSetChanged();
                     activity.refreshSidebar(activity.getSidebarSectionOrder());
                 }).show();
    }

    private void showMoveBookmarkDialog(BookmarkListController controller, int position) {
        List<String> targets = bookmarkTargetTabs(controller);
        if (targets.isEmpty()) {
            Extensions.showMessage(activity, R.string.no_other_groups);
            return;
        }
        List<Integer> indices = bookmarkTargetIndices(controller);
        int[] selected = {0};
        UIKit.dialog(activity)
                .setTitle(activity.rss.getString(R.string.move))
                .setSingleChoiceItems(targets.toArray(new String[0]), 0, (d, w) -> selected[0] = w)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    File file = controller.items.remove(position);
                    addBookmarkToTab(indices.get(selected[0]), file);
                    persistBookmarkOrder(controller);
                    controller.adapter.notifyDataSetChanged();
                }).show();
    }

    private List<String> bookmarkTargetTabs(BookmarkListController source) {
        List<String> targets = new ArrayList<>();
        if (!source.key.equals("bookmarks")) targets.add(activity.rss.getString(R.string.bookmarks));
        for (String group : bookmarkGroups)
            if (!group.equals(source.key)) targets.add(group);
        return targets;
    }

    private List<Integer> bookmarkTargetIndices(BookmarkListController source) {
        List<Integer> indices = new ArrayList<>();
        if (!source.key.equals("bookmarks")) indices.add(0);
        for (int i = 0; i < bookmarkGroups.size(); i++)
            if (!bookmarkGroups.get(i).equals(source.key)) indices.add(2 + i);
        return indices;
    }

    private void confirmDeleteBookmark(BookmarkListController controller, int position) {
        UIKit.dialog(activity)
                .setMessage(activity.rss.getString(R.string.confirm_delete_bookmark, controller.items.get(position)))
                .setTitle(R.string.warning)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(activity.rss.getString(R.string.delete), (dialog, which) -> {
                    File removed = controller.items.remove(position);
                     bookmarkLabels.remove(removed.getPath());
                     store.saveLabels(bookmarkLabels);
                     persistBookmarkOrder(controller);
                     controller.adapter.notifyDataSetChanged();
                     activity.refreshSidebar(activity.getSidebarSectionOrder());
                 }).show();
    }

    private void enterBookmarkBatchMode(BookmarkListController controller) {
        batchController = controller;
        batchSelected.clear();
        controller.adapter.notifyDataSetChanged();
        activity.findViewById(R.id.bookmarksBatchBar).setVisibility(View.VISIBLE);
    }

    private void exitBookmarkBatchMode() {
        bookmarkDragging = false; // end any active drag session
        BookmarkListController controller = batchController;
        batchController = null;
        batchSelected.clear();
        if (controller != null) controller.adapter.notifyDataSetChanged();
        activity.findViewById(R.id.bookmarksBatchBar).setVisibility(View.GONE);
    }

    private void batchDeleteSelected() {
        BookmarkListController source = batchController;
        if (source == null || batchSelected.isEmpty()) return;
        List<Integer> positions = new ArrayList<>(batchSelected);
        positions.sort(Collections.reverseOrder());
        for (int position : positions) {
            File removed = source.items.remove(position);
            bookmarkLabels.remove(removed.getPath());
        }
         store.saveLabels(bookmarkLabels);
         persistBookmarkOrder(source);
         source.adapter.notifyDataSetChanged();
         activity.refreshSidebar(activity.getSidebarSectionOrder());
         exitBookmarkBatchMode();
    }

    private void batchCopyOrMove(boolean copy) {
        BookmarkListController source = batchController;
        if (source == null || batchSelected.isEmpty()) return;
        List<String> targets = bookmarkTargetTabs(source);
        List<Integer> indices = bookmarkTargetIndices(source);
        if (targets.isEmpty()) {
            Extensions.showMessage(activity, R.string.no_other_groups);
            return;
        }
        List<Integer> positions = new ArrayList<>(batchSelected);
        Collections.sort(positions);
        int[] selected = {0};
        UIKit.dialog(activity)
                .setTitle(copy ? activity.rss.getString(android.R.string.copy) : activity.rss.getString(R.string.move))
                .setSingleChoiceItems(targets.toArray(new String[0]), 0, (d, w) -> selected[0] = w)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int targetIndex = indices.get(selected[0]);
                    for (int position : positions) {
                        File file = source.items.get(position);
                        addBookmarkToTab(targetIndex, file); // labels are path-keyed, so they follow automatically
                    }
                    if (!copy) {
                        for (int p = positions.size() - 1; p >= 0; p--) source.items.remove((int) positions.get(p));
                        persistBookmarkOrder(source);
                    }
                    source.adapter.notifyDataSetChanged();
                    exitBookmarkBatchMode();
                }).show();
    }

    private void startBookmarkDrag(BookmarkListController controller, View handle, int position, MotionEvent downEvent) {
        ListView listView = controller.listView;
        View row = (View) handle.getParent();
        float density = activity.getResources().getDisplayMetrics().density;
        int rowH = row.getHeight() > 0 ? row.getHeight() : (int) (48 * density + 0.5f);
        int[] dragPos = {position};
        int[] startSlot = {position};
        int[] lastFirst = {listView.getFirstVisiblePosition()};
        float[] lastRawY = {downEvent.getRawY()};
        int[] lastTop = {row.getTop()};
        float[] dragOffset = {0f};
        bookmarkDragging = true;
        listView.requestDisallowInterceptTouchEvent(true);
        handle.getParent().requestDisallowInterceptTouchEvent(true);
        ViewParent pagerParent = listView.getParent();
        if (pagerParent != null) pagerParent.requestDisallowInterceptTouchEvent(true);

        handle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    float dy = event.getRawY() - lastRawY[0];
                    lastRawY[0] = event.getRawY();
                    // compensate list scrolling so the held row stays glued to the finger
                    int topDelta = row.getTop() - lastTop[0];
                    lastTop[0] = row.getTop();
                    dragOffset[0] += dy - topDelta;
                    row.setTranslationY(dragOffset[0]); // the held row ONLY follows the finger

                    // which slot is the held row's center over?
                    int first = listView.getFirstVisiblePosition();
                    startSlot[0] += first - lastFirst[0];
                    lastFirst[0] = first;
                    int childIndex = dragPos[0] - first;
                    if (childIndex < 0 || childIndex >= listView.getChildCount()) return true;
                    float visualCenter = row.getTop() + dragOffset[0] + rowH / 2f;
                    int targetChild = (int) Math.floor(visualCenter / rowH);
                    int maxChild = Math.min(listView.getChildCount() - 1,
                            controller.items.size() - 1 - first);
                    targetChild = Math.max(0, Math.min(maxChild, targetChild));

                    // silent data swaps toward the target slot
                    while (targetChild > childIndex && dragPos[0] + 1 < controller.items.size()) {
                        controller.items.add(dragPos[0] + 1, controller.items.remove(dragPos[0]));
                        dragPos[0]++;
                        childIndex++;
                    }
                    while (targetChild < childIndex && dragPos[0] > 0) {
                        controller.items.add(dragPos[0] - 1, controller.items.remove(dragPos[0]));
                        dragPos[0]--;
                        childIndex--;
                    }

                    // displacement invariant: every slot between the anchor and the current slot is
                    // offset exactly one row in the drag direction - overlaps are impossible
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        View child = listView.getChildAt(i);
                        if (child == row) continue;
                        float t;
                        if (childIndex > startSlot[0] && i >= startSlot[0] && i < childIndex) t = -rowH;
                        else if (childIndex < startSlot[0] && i > childIndex && i <= startSlot[0]) t = rowH;
                        else t = 0;
                        if (child.getTranslationY() != t)
                            child.animate().translationY(t).setDuration(120)
                                    .setInterpolator(new DecelerateInterpolator()).start();
                    }

                    // auto-scroll near the edges
                    int[] location = new int[2];
                    listView.getLocationOnScreen(location);
                    float fingerY = event.getRawY() - location[1];
                    if (fingerY < rowH) listView.smoothScrollBy(-rowH / 2, 120);
                    else if (fingerY > listView.getHeight() - rowH) listView.smoothScrollBy(rowH / 2, 120);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    bookmarkDragging = false;
                    for (int i = 0; i < listView.getChildCount(); i++) {
                        listView.getChildAt(i).setTranslationY(0);
                    }
                    persistBookmarkOrder(controller);
                    controller.adapter.notifyDataSetChanged(); // normalize rows + restore standard handle listeners
                    return true;
                }
            }
            return false;
        });
    }

    private void persistBookmarkOrder(BookmarkListController controller) {
        if (controller.key.equals("bookmarks")) {
            store.saveBookmarks(controller.items);
        } else {
            store.saveGroup(controller.key, controller.items);
        }
    }

    private void loadBookmarkLabels() {
        bookmarkLabels.clear();
        bookmarkLabels.putAll(store.loadLabels());
    }

    private void applyBookmarksBarHeight(int percent) {
        View drawer = activity.findViewById(R.id.bookmarks_drawer);
        drawer.getLayoutParams().height = (int) (activity.getResources().getDisplayMetrics().heightPixels * (percent / 100.0));
        drawer.requestLayout();
    }

    private void loadBookmarkGroups() {
        bookmarkGroups.clear();
        bookmarkGroups.addAll(store.loadGroups());
    }

    public int findBookmarkIndex(String key, String path) {
        BookmarkListController controller = key.equals("bookmarks")
                ? mainBookmarkController : groupControllers.get(key);
        if (controller == null) return -1;
        for (int i = 0; i < controller.items.size(); i++) {
            if (path.equals(controller.items.get(i).getPath())) return i;
        }
        return -1;
    }

    public void moveBookmark(String key, String fromPath, String toPath) {
        BookmarkListController controller = key.equals("bookmarks")
                ? mainBookmarkController : groupControllers.get(key);
        if (controller == null) return;
        int from = -1;
        int to = -1;
        for (int i = 0; i < controller.items.size(); i++) {
            if (fromPath.equals(controller.items.get(i).getPath())) from = i;
            if (toPath.equals(controller.items.get(i).getPath())) to = i;
        }
        if (from < 0 || to < 0 || from == to) return;
        File file = controller.items.remove(from);
        if (to > from) to--;
        controller.items.add(to, file);
        persistBookmarkOrder(controller);
        controller.adapter.notifyDataSetChanged();
    }
}

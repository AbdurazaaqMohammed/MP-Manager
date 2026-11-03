package io.github.abdurazaaqmohammed.features.files;

import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.GravityCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Builds the right (more-options) drawer: filter, search, sort, pane and
 * settings shortcuts. Extracted from {@link MainActivity} so the drawer is a
 * self-contained concern like {@link SidebarController}.
 */
public class RightMenuController {

    private final MainActivity activity;
    private final FileSearchController fileSearch;
    private final SortFilterController sortFilter;

    public RightMenuController(MainActivity activity,
                               FileSearchController fileSearch,
                               SortFilterController sortFilter) {
        this.activity = activity;
        this.fileSearch = fileSearch;
        this.sortFilter = sortFilter;
    }

    /** (Re)builds every drawer row; called on open and once after startup. */
    public void build() {
        View refreshBtn = activity.findViewById(R.id.rightMenuRefreshButton);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(v -> {
                if (activity.drawerLayout != null && activity.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    activity.drawerLayout.closeDrawer(GravityCompat.END);
                }
                activity.reloadPane(true, true);
                activity.reloadPane(false, true);
            });
        }

        View editBtn = activity.findViewById(R.id.rightMenuEditButton);
        if (editBtn != null) {
            editBtn.setOnClickListener(v -> RightMenuCustomizer.show(activity, this::build));
        }

        LinearLayout container = activity.findViewById(R.id.rightMenuContainer);
        if (container == null) return;
        container.removeAllViews();

        List<String> order = RightMenuOrder.load(activity);
        Set<String> hidden = RightMenuOrder.getHiddenIds(activity);
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        RecyclerView.Adapter curAdapter = activity.getCurrentPane().getAdapter();
        boolean inSearchMode = curAdapter instanceof MainFilesArrayAdapter && ((MainFilesArrayAdapter) curAdapter).isSearchResultsMode();

        for (String id : order) {
            if (hidden.contains(id)) continue;
            switch (id) {
                case RightMenuOrder.FILTER:
                    addRightDrawerItem(container, R.drawable.baseline_filter_list_24, activity.getString(R.string.filter), v -> {
                        LinearLayout topBar = activity.findViewById(R.id.topBar);
                        LinearLayout pathLayout = (LinearLayout) topBar.getChildAt(1);
                        TextInputLayout filterBox = sortFilter.filterBarBox();
                        EditText filterBar = filterBox.getEditText();
                        if (pathLayout.getVisibility() == View.VISIBLE) {
                            pathLayout.setVisibility(View.GONE);
                            filterBox.setVisibility(View.VISIBLE);
                            if (filterBar != null) {
                                filterBar.requestFocus();
                                filterBar.post(() -> {
                                    try {
                                        InputMethodManager imm = (InputMethodManager) activity.getSystemService(MainActivity.INPUT_METHOD_SERVICE);
                                        if (imm != null) imm.showSoftInput(filterBar, InputMethodManager.SHOW_IMPLICIT);
                                    } catch (Exception ignored) {}
                                });
                            }
                        } else {
                            pathLayout.setVisibility(View.VISIBLE);
                            filterBox.setVisibility(View.GONE);
                            if (filterBar != null) filterBar.setText("");
                            try {
                                InputMethodManager imm = (InputMethodManager) activity.getSystemService(MainActivity.INPUT_METHOD_SERVICE);
                                if (imm != null) imm.hideSoftInputFromWindow(topBar.getWindowToken(), 0);
                            } catch (Exception ignored) {}
                        }
                    });
                    break;
                case RightMenuOrder.SEARCH:
                    addRightDrawerItem(container, R.drawable.baseline_search_24, activity.getString(R.string.search), v -> activity.showSearchDialog());
                    break;
                case RightMenuOrder.XML_SEARCH:
                    addRightDrawerItem(container, R.drawable.baseline_search_24, activity.getString(R.string.xml_search), v -> fileSearch.showXmlSearchDialog());
                    break;
                case RightMenuOrder.SEARCH_RESULTS:
                    if (fileSearch.hasSearchResults()) {
                        addRightDrawerItem(container, R.drawable.ic_eye_mt, activity.getString(R.string.search_results), v -> fileSearch.showLastSearchResultsDialog());
                    }
                    break;
                case RightMenuOrder.CLEAR_SEARCH:
                    if (inSearchMode) {
                        addRightDrawerItem(container, R.drawable.close_24px, activity.getString(R.string.clear_search_results), v -> activity.clearSearchResults(activity.lastPaneSelected == 1));
                    }
                    break;
                case RightMenuOrder.SELECT_ALL:
                    addRightDrawerItem(container, R.drawable.baseline_select_all_24, activity.getString(R.string.menu_select_all), v -> {
                        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
                        if (a instanceof MainFilesArrayAdapter) ((MainFilesArrayAdapter) a).selectAll();
                    });
                    break;
                case RightMenuOrder.SORT:
                    addRightDrawerItem(container, R.drawable.baseline_sort_24, activity.getString(R.string.sort), v -> activity.showSortDialog());
                    break;
                case RightMenuOrder.SHOW_SYSTEM_HIDDEN:
                    addRightDrawerCheckbox(container, R.drawable.visibility_off_24px,
                            activity.getString(R.string.menu_show_system_hidden),
                            prefs.getBoolean("show_system_hidden", false),
                            (buttonView, isChecked) -> {
                                prefs.edit().putBoolean("show_system_hidden", isChecked).apply();
                                activity.reloadCurrentFolder();
                            });
                    break;
                case RightMenuOrder.SHOW_MANUAL_HIDDEN:
                    addRightDrawerCheckbox(container, R.drawable.visibility_off_24px,
                            activity.getString(R.string.menu_show_manual_hidden),
                            prefs.getBoolean("show_manually_hidden", false),
                            (buttonView, isChecked) -> {
                                prefs.edit().putBoolean("show_manually_hidden", isChecked).apply();
                                activity.reloadCurrentFolder();
                            });
                    break;
                case RightMenuOrder.SHOW_FOLDER_SIZE:
                    addRightDrawerItem(container, R.drawable.folder_24px, activity.getString(R.string.show_folder_size), v -> {
                        String[] folderSizeLabels = {
                                activity.getString(R.string.folder_size_user_space),
                                activity.getString(R.string.folder_size_all),
                                activity.getString(R.string.folder_size_none)
                        };
                        int currentMode = io.github.abdurazaaqmohammed.utils.UiPrefs.folderSizeMode(activity);
                        if (currentMode < 0 || currentMode > 2) currentMode = io.github.abdurazaaqmohammed.utils.UiPrefs.FOLDER_SIZE_USER;
                        androidx.appcompat.app.AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.show_folder_size)
                                .setSingleChoiceItems(folderSizeLabels, currentMode, (d, which) -> {
                                    prefs.edit().putInt(io.github.abdurazaaqmohammed.utils.UiPrefs.FOLDER_SIZE_KEY, which).apply();
                                    d.dismiss();
                                    activity.reloadCurrentFolder();
                                })
                                .create();
                        dialog.setCanceledOnTouchOutside(true);
                        dialog.show();
                        android.view.Window win = dialog.getWindow();
                        if (win != null) {
                            android.util.DisplayMetrics dm = activity.getResources().getDisplayMetrics();
                            int width = (int) (280 * dm.density);
                            win.setLayout(Math.min((int) (dm.widthPixels * 0.75f), width), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                        }
                    });
                    break;
                case RightMenuOrder.ADD_BOOKMARK:
                    addRightDrawerItem(container, R.drawable.baseline_bookmark_24, activity.getString(R.string.menu_add_bookmark), v -> {
                        boolean isPane1 = activity.lastPaneSelected == 1;
                        File toBookmark = isPane1 ? activity.pane1Folder : activity.pane2Folder;
                        activity.addBookmark(toBookmark);
                        Extensions.showMessage(activity, activity.rss.getString(R.string.added_to_bookmarks, toBookmark.getName()));
                    });
                    break;
                case RightMenuOrder.SET_AS_HOME:
                    addRightDrawerItem(container, R.drawable.baseline_home_24, activity.getString(R.string.set_as_home), v -> {
                        boolean isPane1 = activity.lastPaneSelected == 1;
                        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                                .putString(isPane1 ? "home1" : "home2", (isPane1 ? activity.pane1Folder : activity.pane2Folder).getPath())
                                .apply();
                        Extensions.showMessage(activity, activity.rss.getString(R.string.set_as_home));
                    });
                    break;
                case RightMenuOrder.SWAP_PANES:
                    addRightDrawerItem(container, R.drawable.baseline_swap_horiz_24, activity.getString(R.string.menu_swap_panes), v -> {
                        File temp = activity.pane1Folder;
                        activity.pane1Folder = activity.pane2Folder;
                        activity.pane2Folder = temp;
                        activity.loadFolderInPane(activity.pane1Folder, true);
                        activity.loadFolderInPane(activity.pane2Folder, false);
                    });
                    break;
                case RightMenuOrder.PREFERENCES:
                    addRightDrawerItem(container, R.drawable.baseline_settings_24, activity.getString(R.string.preferences), v -> activity.showSettingsDialog());
                    break;
            }
        }
    }

    private void addRightDrawerCheckbox(LinearLayout container, int iconRes, String title, boolean initialValue, android.widget.CompoundButton.OnCheckedChangeListener onChecked) {
        LinearLayout itemLayout = new LinearLayout(activity);
        itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (48 * activity.getResources().getDisplayMetrics().density)));
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (14 * activity.getResources().getDisplayMetrics().density);
        itemLayout.setPadding(padH, 0, padH, 0);
        TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        itemLayout.setBackgroundResource(outValue.resourceId);

        ImageView icon = new ImageView(activity);
        int iconSize = (int) (22 * activity.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd((int) (12 * activity.getResources().getDisplayMetrics().density));
        icon.setLayoutParams(iconLp);
        icon.setImageResource(iconRes);
        TypedValue colorOnSurface = new TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, colorOnSurface, true);
        icon.setColorFilter(colorOnSurface.data);
        itemLayout.addView(icon);

        TextView text = new TextView(activity);
        text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        text.setText(title);
        text.setMaxLines(1);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(colorOnSurface.data);
        itemLayout.addView(text);

        com.google.android.material.checkbox.MaterialCheckBox checkBox = new com.google.android.material.checkbox.MaterialCheckBox(activity);
        LinearLayout.LayoutParams checkLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        checkLp.setMarginStart((int) (6 * activity.getResources().getDisplayMetrics().density));
        checkBox.setLayoutParams(checkLp);
        checkBox.setChecked(initialValue);
        checkBox.setOnCheckedChangeListener(onChecked);
        itemLayout.addView(checkBox);

        itemLayout.setOnClickListener(v -> checkBox.toggle());

        container.addView(itemLayout);
    }

    private void addRightDrawerItem(LinearLayout container, int iconRes, String title, View.OnClickListener onClick) {
        LinearLayout itemLayout = new LinearLayout(activity);
        itemLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (48 * activity.getResources().getDisplayMetrics().density)));
        itemLayout.setOrientation(LinearLayout.HORIZONTAL);
        itemLayout.setGravity(Gravity.CENTER_VERTICAL);
        int padH = (int) (14 * activity.getResources().getDisplayMetrics().density);
        itemLayout.setPadding(padH, 0, padH, 0);
        TypedValue outValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        itemLayout.setBackgroundResource(outValue.resourceId);

        ImageView icon = new ImageView(activity);
        int iconSize = (int) (22 * activity.getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd((int) (12 * activity.getResources().getDisplayMetrics().density));
        icon.setLayoutParams(iconLp);
        icon.setImageResource(iconRes);
        TypedValue colorOnSurface = new TypedValue();
        activity.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, colorOnSurface, true);
        icon.setColorFilter(colorOnSurface.data);
        itemLayout.addView(icon);

        TextView text = new TextView(activity);
        text.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        text.setText(title);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        text.setTextColor(colorOnSurface.data);
        itemLayout.addView(text);

        itemLayout.setOnClickListener(v -> {
            if (activity.drawerLayout != null && activity.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                activity.drawerLayout.closeDrawer(GravityCompat.END);
                activity.drawerLayout.postDelayed(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        onClick.onClick(v);
                    }
                }, 220);
            } else {
                onClick.onClick(v);
            }
        });

        container.addView(itemLayout);
    }
}

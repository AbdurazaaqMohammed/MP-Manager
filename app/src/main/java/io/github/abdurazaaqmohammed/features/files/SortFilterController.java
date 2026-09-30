package io.github.abdurazaaqmohammed.features.files;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.core.ui.util.PopupMenus;
import io.github.abdurazaaqmohammed.data.prefs.ListPrefs;
import io.github.abdurazaaqmohammed.domain.files.FileSorting;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.views.SortDirectionToggle;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Listing sort/filter/hidden-files behaviour extracted from MainActivity.
 * Pure sort math lives in domain.files.FileSorting, prefs in
 * data.prefs.ListPrefs; this class owns the dialogs and pane rebinding.
 */
public class SortFilterController {

    private final MainActivity activity;
    private final ListPrefs prefs;
    private String currentPane1Filter = "";
    private String currentPane2Filter = "";

    public SortFilterController(MainActivity activity) {
        this.activity = activity;
        this.prefs = new ListPrefs(activity);
    }

    public void setupFilterBar() {
        LinearLayout topBar = activity.findViewById(R.id.topBar);
        com.google.android.material.textfield.TextInputLayout filterBox =
                UiFields.box(activity, "Filter...");
        EditText filterBar = UiFields.field(filterBox, 0);
        filterBox.setVisibility(View.GONE);
        filterBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        filterBar.setSingleLine(true);
        topBar.addView(filterBox, 2);

        filterBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (activity.lastPaneSelected == 1) currentPane1Filter = s.toString().toLowerCase();
                else currentPane2Filter = s.toString().toLowerCase();
                applyFilterToCurrentPane();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public void applyFilterToCurrentPane() {
        boolean isPane1 = activity.lastPaneSelected == 1;
        String filter = isPane1 ? currentPane1Filter : currentPane2Filter;
        RecyclerView pane = activity.findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);

        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) return;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) pane.getAdapter();

        if (adapter.isInZip) {
            List<ZipEntryInfo> entries = activity.paneZipEntries(isPane1);
            if (entries == null) return;
            List<ZipEntryInfo> filtered = new ArrayList<>();
            for (ZipEntryInfo e : entries) {
                if (e.getName().toLowerCase().contains(filter) || e.getName().equals("..")) {
                    filtered.add(e);
                }
            }
            pane.setAdapter(new MainFilesArrayAdapter(activity, filtered.toArray(new ZipEntryInfo[0]), null, isPane1, true,
                    adapter.currentZipPath));
        } else {
            File[] files = activity.paneFiles(isPane1);
            if (files == null) return;
            List<File> filtered = new ArrayList<>();
            for (File f : files) {
                if (f.getName().toLowerCase().contains(filter) || f.getName().equals("..")) {
                    filtered.add(f);
                }
            }
            pane.setAdapter(new MainFilesArrayAdapter(activity, filtered.toArray(new File[0]),
                    (isPane1 ? activity.pane1Folder : activity.pane2Folder).getParentFile(), isPane1, false, null));
        }
    }

    public boolean isNotHidden(File f) {
        return (prefs.showSystemHidden() || (!f.isHidden() && !f.getName().startsWith(".")))
                && (prefs.showManualHidden() || !prefs.manuallyHidden().contains(f.getPath()));
    }

    public boolean isNotHidden(ZipEntryInfo e) {
        return (prefs.showSystemHidden() || !e.getName().startsWith("."))
                && (prefs.showManualHidden() || !prefs.manuallyHidden().contains(e.getFullPath()));
    }

    public void showSortDialog() {
        boolean isPane1 = activity.lastPaneSelected == 1;
        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) return;
        MainFilesArrayAdapter adapter = (MainFilesArrayAdapter) activity.getCurrentPane().getAdapter();
        String currentPath = adapter.isInZip ? adapter.currentZipPath
                : (isPane1 ? activity.pane1Folder.getPath() : activity.pane2Folder.getPath());

        int sortBy = prefs.sortBy(currentPath);
        boolean reverse = prefs.sortReverse(currentPath);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        String[] sortOptions = {activity.rss.getString(R.string.name), activity.rss.getString(R.string.size),
                activity.rss.getString(R.string.sort_date), activity.rss.getString(R.string.type)};
        RadioGroup radioGroup = new RadioGroup(activity);
        for (int i = 0; i < sortOptions.length; i++) {
            RadioButton rb = new RadioButton(activity);
            rb.setText(sortOptions[i]);
            rb.setId(i);
            radioGroup.addView(rb);
        }
        radioGroup.check(sortBy);
        layout.addView(radioGroup);

        CheckBox cbOnlyThisFolder = new CheckBox(activity);
        cbOnlyThisFolder.setText(activity.rss.getString(R.string.only_for_this_folder));
        layout.addView(cbOnlyThisFolder);

        SortDirectionToggle directionToggle = new SortDirectionToggle(activity);
        directionToggle.setDescending(reverse);
        layout.addView(directionToggle);

        activity.dialogUtil.getDialogBuilder()
                .setTitle(activity.getString(R.string.sort))
                .setView(layout)
                .setPositiveButton(activity.getString(R.string.apply), (dialog, which) -> {
                    int selectedSort = radioGroup.getCheckedRadioButtonId();
                    boolean selectedReverse = directionToggle.isDescending();
                    prefs.saveSort(cbOnlyThisFolder.isChecked() ? currentPath : null,
                            selectedSort, selectedReverse);
                    activity.reloadCurrentFolder();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void showEditHiddenFilesDialog() {
        List<String> hiddenList = new ArrayList<>(prefs.manuallyHidden());

        ListView listView = new ListView(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1, hiddenList);
        listView.setAdapter(adapter);

        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setTitle(R.string.edit_hidden_files)
                .setView(listView)
                .setPositiveButton(R.string.done, (d, w) -> activity.reloadCurrentFolder())
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String path = hiddenList.get(position);
            hiddenList.remove(position);
            prefs.unhide(path);
            adapter.notifyDataSetChanged();
            Extensions.showMessage(activity, activity.getString(R.string.unhidden, path));
        });

        dialog.show();
    }

    public void sortFiles(File[] files, String folderPath) {
        FileSorting.sortFiles(files, prefs.sortBy(folderPath), prefs.sortReverse(folderPath));
    }

    public void sortZipEntries(List<ZipEntryInfo> entries, String folderPath) {
        FileSorting.sortZipEntries(entries, folderPath, prefs.sortBy(folderPath), prefs.sortReverse(folderPath));
    }

    public static void forceShowIcons(android.widget.PopupMenu popupMenu) {
        PopupMenus.forceShowIcons(popupMenu);
    }
}

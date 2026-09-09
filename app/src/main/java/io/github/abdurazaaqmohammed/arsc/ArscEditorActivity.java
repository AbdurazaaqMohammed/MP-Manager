package io.github.abdurazaaqmohammed.arsc;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ValueType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class ArscEditorActivity extends AppCompatActivity {

    public static final String MODE_PLUS = "plus";
    public static final String MODE_EDITOR = "editor";
    public static final String MODE_TRANSLATE = "translate";
    public static final String MODE_QUERIER = "querier";

    private static final String[] SEARCH_TYPES = {"xml", "resource id", "string", "integer", "color"};

    ArscData data;
    String mode = MODE_PLUS;
    boolean dirty = false;
    boolean batchRemove = false;

    private MaterialToolbar toolbar;
    private ArscTreeAdapter explorerAdapter;
    private RecyclerView explorerRv;
    private RecyclerView historyRv;
    private HistoryAdapter historyAdapter;
    private EditText searchInput;
    private AutoCompleteTextView searchTypeTv;
    private EditText searchPathInput;
    private RecyclerView searchResultsRv;
    private SearchAdapter searchAdapter;
    private EditText stringsFilter;
    private RecyclerView stringsRv;
    private StringsAdapter stringsAdapter;
    private View explorerPage;
    private View historyPage;
    private View searchPage;
    private View stringsPage;
    private LinearLayout batchBar;
    private TextView batchLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(getIntent().getIntExtra("theme", prefs.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light)));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        mode = getIntent().getStringExtra("arscMode");
        if (mode == null) mode = MODE_PLUS;
        String path = getIntent().getStringExtra("path");
        String apkPath = getIntent().getStringExtra("apkPath");
        String entryPath = getIntent().getStringExtra("zipEntryPath");
        if (path == null) {
            finish();
            return;
        }
        buildShell(new File(path).getName());
        loadAsync(new File(path), apkPath == null ? null : new File(apkPath), entryPath);
    }

    private void buildShell(String fileName) {
        FrameLayout root = new FrameLayout(this);
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(modeTitle());
        toolbar.setSubtitle(fileName);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        Menu menu = toolbar.getMenu();
        menu.add(0, 1, 0, "Save").setIcon(R.drawable.save_24px).setShowAsAction(1);
        menu.add(0, 2, 0, "More").setIcon(R.drawable.baseline_more_vert_24).setShowAsAction(1);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                saveNow();
                return true;
            }
            showMainMenu();
            return true;
        });
        main.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TabLayout tabs = new TabLayout(this);
        String[] titles = {"EXPLORER", "HISTORY", "SEARCH", "STRINGS"};
        for (String t : titles) tabs.addTab(tabs.newTab().setText(t));
        main.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout pages = new FrameLayout(this);
        explorerPage = buildExplorerPage();
        historyPage = buildHistoryPage();
        searchPage = buildSearchPage();
        stringsPage = buildStringsPage();
        pages.addView(explorerPage);
        pages.addView(historyPage);
        pages.addView(searchPage);
        pages.addView(stringsPage);
        main.addView(pages, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        batchBar = new LinearLayout(this);
        batchBar.setOrientation(LinearLayout.HORIZONTAL);
        batchBar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(8);
        batchBar.setPadding(pad, pad, pad, pad);
        batchBar.setVisibility(View.GONE);
        MaterialButton batchCancel = new MaterialButton(this);
        batchCancel.setText("✕");
        batchCancel.setOnClickListener(v -> exitBatchMode());
        MaterialButton batchSelect = new MaterialButton(this);
        batchSelect.setText("⛶");
        batchSelect.setOnClickListener(v -> {
            explorerAdapter.selectAll(true);
            updateBatchLabel();
        });
        batchLabel = new TextView(this);
        batchLabel.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = pad;
        MaterialButton batchGo = new MaterialButton(this);
        batchGo.setText("Save");
        batchGo.setOnClickListener(v -> onBatchGo());
        batchBar.addView(batchCancel);
        batchBar.addView(batchSelect);
        batchBar.addView(batchLabel, labelParams);
        batchBar.addView(batchGo);
        batchGo.setTag("go");
        main.addView(batchBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) {
                showPage(tab.getPosition());
            }

            public void onTabUnselected(TabLayout.Tab tab) {
            }

            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        showPage(MODE_TRANSLATE.equals(mode) ? 3 : 0);
        if (MODE_TRANSLATE.equals(mode) && tabs.getTabAt(3) != null) tabs.getTabAt(3).select();
    }

    private String modeTitle() {
        if (MODE_TRANSLATE.equals(mode)) return "Arsc Translation";
        if (MODE_EDITOR.equals(mode)) return "Arsc Editor";
        if (MODE_QUERIER.equals(mode)) return "Arsc Editor plus";
        return "Arsc Editor plus";
    }

    private void showPage(int index) {
        explorerPage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        historyPage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        searchPage.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        stringsPage.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
    }

    private View buildExplorerPage() {
        explorerRv = new RecyclerView(this);
        explorerRv.setLayoutManager(new LinearLayoutManager(this));
        explorerAdapter = new ArscTreeAdapter(this, new ArscTreeAdapter.Listener() {
            public void onNodeClick(ArscData.Node node) {
                if (!node.dir && node.tag instanceof ResourceEntry) {
                    showEntryDialog((ResourceEntry) node.tag);
                }
            }

            public void onNodeLongClick(ArscData.Node node, View anchor) {
                if (node.dir) showFolderMenu(node, anchor);
                else showFileMenu(node, anchor);
            }
        });
        explorerAdapter.setSelectionWatcher(this::updateBatchLabel);
        explorerRv.setAdapter(explorerAdapter);
        return explorerRv;
    }

    private View buildHistoryPage() {
        historyRv = new RecyclerView(this);
        historyRv.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter();
        historyRv.setAdapter(historyAdapter);
        return historyRv;
    }

    private View buildSearchPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        page.setPadding(pad, pad, pad, pad);
        com.google.android.material.textfield.TextInputLayout queryBox = UiFields.box(this, "Search");
        searchInput = UiFields.field(queryBox, InputType.TYPE_CLASS_TEXT);
        page.addView(queryBox);
        com.google.android.material.textfield.TextInputLayout typeBox = UiFields.box(this, "Search type");
        searchTypeTv = new AutoCompleteTextView(this);
        searchTypeTv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, SEARCH_TYPES));
        searchTypeTv.setText(SEARCH_TYPES[0], false);
        searchTypeTv.setInputType(InputType.TYPE_NULL);
        typeBox.addView(searchTypeTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        typeParams.topMargin = dp(8);
        page.addView(typeBox, typeParams);
        com.google.android.material.textfield.TextInputLayout pathBox = UiFields.box(this, "Path (pkg/type, empty = all)");
        searchPathInput = UiFields.field(pathBox, InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pathParams.topMargin = dp(8);
        page.addView(pathBox, pathParams);
        MaterialButton goBtn = new MaterialButton(this);
        goBtn.setText("Search");
        goBtn.setOnClickListener(v -> runSearch());
        LinearLayout.LayoutParams goParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        goParams.topMargin = dp(8);
        page.addView(goBtn, goParams);
        searchResultsRv = new RecyclerView(this);
        searchResultsRv.setLayoutManager(new LinearLayoutManager(this));
        searchAdapter = new SearchAdapter();
        searchResultsRv.setAdapter(searchAdapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = dp(8);
        page.addView(searchResultsRv, listParams);
        return page;
    }

    private View buildStringsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        page.setPadding(pad, pad, pad, pad);
        com.google.android.material.textfield.TextInputLayout filterBox = UiFields.box(this, "Filter strings");
        stringsFilter = UiFields.field(filterBox, InputType.TYPE_CLASS_TEXT);
        stringsFilter.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
                refreshStrings();
            }

            public void afterTextChanged(Editable s) {
            }
        });
        page.addView(filterBox);
        stringsRv = new RecyclerView(this);
        stringsRv.setLayoutManager(new LinearLayoutManager(this));
        stringsAdapter = new StringsAdapter();
        stringsRv.setAdapter(stringsAdapter);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        listParams.topMargin = dp(8);
        page.addView(stringsRv, listParams);
        return page;
    }

    private void loadAsync(File arsc, File apk, String entryPath) {
        Extensions.showMessage(this, "Loading resources.arsc…");
        new Thread(() -> {
            try {
                ArscData loaded = ArscData.load(arsc, apk, entryPath);
                runOnUiThread(() -> {
                    data = loaded;
                    rebuildTree();
                    refreshStrings();
                    historyAdapter.refresh();
                    if (MODE_QUERIER.equals(mode)) {
                        ArscQuerier.showDialog(this);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new ErrorUtil(this).showError(e);
                    finish();
                });
            }
        }).start();
    }

    void rebuildTree() {
        if (data == null) return;
        explorerAdapter.setRoots(data.buildTree());
    }

    void markDirty() {
        dirty = true;
        toolbar.setSubtitle((data != null && data.arscFile != null ? data.arscFile.getName() : "") + " *");
    }

    private void saveNow() {
        if (data == null) return;
        Extensions.showMessage(this, "Saving…");
        new Thread(() -> {
            try {
                data.save();
                runOnUiThread(() -> {
                    dirty = false;
                    toolbar.setSubtitle(data.arscFile.getName());
                    rebuildTree();
                    refreshStrings();
                    historyAdapter.refresh();
                    Extensions.showMessage(this, "Saved");
                });
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    private void showMainMenu() {
        PopupMenu menu = new PopupMenu(this, toolbar);
        if (!MODE_EDITOR.equals(mode)) menu.getMenu().add("Resource querier");
        menu.getMenu().add("Batch export");
        menu.getMenu().add("Batch import");
        menu.getMenu().add("Backup");
        menu.getMenu().add("Exit");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Resource querier")) ArscQuerier.toggleFloat(this);
            else if (title.equals("Batch export")) enterBatchMode(false);
            else if (title.equals("Batch import")) batchImport(null, null);
            else if (title.equals("Backup")) backupNow();
            else confirmExit();
            return true;
        });
        menu.show();
    }

    private void backupNow() {
        if (data == null) return;
        new Thread(() -> {
            try {
                File bak = data.backup();
                runOnUiThread(() -> Extensions.showMessage(this, "Backup: " + bak.getName()));
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    private void confirmExit() {
        if (!dirty) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved changes")
                .setMessage("Save before exit?")
                .setPositiveButton("Save", (d, w) -> {
                    saveNow();
                    finish();
                })
                .setNegativeButton("Discard", (d, w) -> finish())
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (explorerAdapter != null && explorerAdapter.isBatchMode()) {
            exitBatchMode();
            return;
        }
        confirmExit();
    }

    private void showEntryDialog(ResourceEntry re) {
        String xml = data.entryXml(re);
        TextView preview = new TextView(this);
        preview.setText(xml);
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextSize(13);
        preview.setTextIsSelectable(true);
        int pad = dp(12);
        preview.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(preview);
        new MaterialAlertDialogBuilder(this)
                .setTitle(re.getType() + "/" + re.getName())
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Edit", (d, w) -> showEditDialog(re, false))
                .show();
    }

    void showEditDialog(ResourceEntry re, boolean translate) {
        if (translate) {
            showTranslateDialog(re);
            return;
        }
        Entry e = data.defaultEntry(re);
        if (e == null) {
            Extensions.showMessage(this, "No editable value");
            return;
        }
        ValueType type;
        try {
            type = e.getValueType();
        } catch (Exception ex) {
            Extensions.showMessage(this, "Unknown value type");
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);
        TextView info = new TextView(this);
        info.setText(re.getHexId() + "  " + type.name());
        info.setTextSize(12);
        root.addView(info);
        EditText input;
        if (type == ValueType.BOOLEAN) {
            AutoCompleteTextView boolTv = new AutoCompleteTextView(this);
            boolTv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new String[]{"true", "false"}));
            boolean current = false;
            try {
                current = e.getValueAsBoolean();
            } catch (Exception ignored) {
            }
            boolTv.setText(current ? "true" : "false", false);
            boolTv.setInputType(InputType.TYPE_NULL);
            com.google.android.material.textfield.TextInputLayout boolBox = UiFields.box(this, "Value");
            boolBox.addView(boolTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            root.addView(boolBox);
            input = boolTv;
        } else {
            com.google.android.material.textfield.TextInputLayout box = UiFields.box(this, "Value");
            EditText field = UiFields.field(box,
                    type == ValueType.STRING ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT);
            String current = "";
            try {
                current = e.getValueAsString();
                if (current == null) current = e.getResValue().decodeValue();
            } catch (Exception ignored) {
            }
            field.setText(current == null ? "" : current);
            root.addView(box);
            input = field;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit " + re.getName())
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() == null ? "" : input.getText().toString();
                    String oldDesc = describeValue(e);
                    if (data.setEntryValue(e, text)) {
                        data.pushHistory("Edit " + re.getType() + "/" + re.getName(), null);
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        historyAdapter.refresh();
                        Extensions.showMessage(this, "Updated (was: " + oldDesc + ")");
                    } else {
                        Extensions.showMessage(this, "Invalid value for " + type.name());
                    }
                }).show();
    }

    private static String describeValue(Entry e) {
        try {
            String s = e.getValueAsString();
            if (s != null) return s;
            return e.getResValue().decodeValue();
        } catch (Exception ex) {
            return "?";
        }
    }

    private void showTranslateDialog(ResourceEntry re) {
        List<ArscData.ConfigValue> configs = data.configValues(re);
        if (configs.isEmpty()) {
            showEditDialog(re, false);
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);
        List<EditText> fields = new ArrayList<>();
        for (ArscData.ConfigValue cv : configs) {
            com.google.android.material.textfield.TextInputLayout box = UiFields.box(this,
                    cv.qualifiers.isEmpty() ? "default" : cv.qualifiers);
            EditText field = UiFields.field(box, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            field.setText(cv.value);
            root.addView(box);
            fields.add(field);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Translate " + re.getName())
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save", (d, w) -> {
                    boolean ok = true;
                    for (int i = 0; i < configs.size(); i++) {
                        String text = fields.get(i).getText() == null ? "" : fields.get(i).getText().toString();
                        if (!data.setEntryValue(configs.get(i).entry, text)) ok = false;
                    }
                    if (ok) {
                        data.pushHistory("Translate " + re.getName(), null);
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        historyAdapter.refresh();
                        Extensions.showMessage(this, "Saved");
                    } else {
                        Extensions.showMessage(this, "Some values invalid");
                    }
                }).show();
    }

    private void showFolderMenu(ArscData.Node node, View anchor) {
        String[] parts = node.key.split("/");
        String pkgName = parts.length > 0 ? parts[0] : "";
        String type = parts.length > 1 ? parts[1] : "";
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Search");
        menu.getMenu().add("Add");
        menu.getMenu().add("Import");
        menu.getMenu().add("Delete");
        menu.getMenu().add("Batch export");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Search")) {
                searchPathInput.setText(node.key);
                searchInput.setText("");
                showPage(2);
            } else if (title.equals("Add")) {
                promptAddEntry(pkgName, type);
            } else if (title.equals("Import")) {
                pickZipForImport(pkgName, type);
            } else if (title.equals("Delete")) {
                confirmDeleteType(pkgName, type);
            } else {
                enterBatchMode(false);
            }
            return true;
        });
        menu.show();
    }

    private void showFileMenu(ArscData.Node node, View anchor) {
        if (!(node.tag instanceof ResourceEntry)) return;
        ResourceEntry re = (ResourceEntry) node.tag;
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Rename");
        menu.getMenu().add("Copy");
        menu.getMenu().add("Delete");
        menu.getMenu().add("Batch remove");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Rename")) promptRename(re);
            else if (title.equals("Copy")) promptCopy(re);
            else if (title.equals("Delete")) confirmDeleteEntry(re);
            else enterBatchMode(true);
            return true;
        });
        menu.show();
    }

    private void promptAddEntry(String pkgName, String type) {
        EditText input = new EditText(this);
        String prefix = type.isEmpty() ? "" : type + "-";
        input.setText(prefix);
        input.setSelection(prefix.length());
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Add to " + type)
                .setView(UiFields.wrap(this, input, "Name", 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty() || name.equals(prefix)) {
                        Extensions.showMessage(this, "Enter a name");
                        return;
                    }
                    new Thread(() -> {
                        ResourceEntry created = data.addEntry(pkgName, type, name);
                        runOnUiThread(() -> {
                            if (created != null) {
                                markDirty();
                                rebuildTree();
                                refreshStrings();
                                historyAdapter.refresh();
                                Extensions.showMessage(this, "Added " + name);
                            } else {
                                Extensions.showMessage(this, "Add failed");
                            }
                        });
                    }).start();
                }).show();
    }

    private void promptRename(ResourceEntry re) {
        EditText input = new EditText(this);
        input.setText(re.getName());
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Rename")
                .setView(UiFields.wrap(this, input, "Name", 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty() || name.equals(re.getName())) return;
                    if (data.renameEntry(re, name)) {
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        historyAdapter.refresh();
                    } else {
                        Extensions.showMessage(this, "Rename failed");
                    }
                }).show();
    }

    private void promptCopy(ResourceEntry re) {
        EditText input = new EditText(this);
        input.setText(re.getName() + "_copy");
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Copy to")
                .setView(UiFields.wrap(this, input, "Name", 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (data.copyEntry(re, name)) {
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        historyAdapter.refresh();
                        Extensions.showMessage(this, "Copied to " + name);
                    } else {
                        Extensions.showMessage(this, "Copy failed");
                    }
                }).show();
    }

    private void confirmDeleteEntry(ResourceEntry re) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + re.getName() + "?")
                .setMessage("The entry will be removed from resources.arsc after save.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (d, w) -> {
                    if (data.deleteEntry(re)) {
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        historyAdapter.refresh();
                    } else {
                        Extensions.showMessage(this, "Delete failed");
                    }
                }).show();
    }

    private void confirmDeleteType(String pkgName, String type) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + type + "?")
                .setMessage("All entries of this type will be removed after save.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (d, w) -> {
                    int n = data.deleteType(pkgName, type);
                    if (n > 0) {
                        data.pushHistory("Delete type " + type + " (" + n + ")", null);
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        historyAdapter.refresh();
                        Extensions.showMessage(this, "Deleted " + n + " entries");
                    } else {
                        Extensions.showMessage(this, "Nothing deleted");
                    }
                }).show();
    }

    private void pickZipForImport(String pkgName, String type) {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.extensions = new String[]{".zip", ".apk", ".apks", ".xapk"};
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle("Select zip");
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            importZip(new File(files[0]), pkgName, type);
        });
        picker.show();
    }

    private void importZip(File zip, String pkgName, String type) {
        Extensions.showMessage(this, "Importing…");
        new Thread(() -> {
            try {
                File tmp = new File(getCacheDir(), "arsc_import_" + System.currentTimeMillis() + ".arsc");
                boolean found = false;
                try (net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(zip)) {
                    for (net.lingala.zip4j.model.FileHeader fh : zf.getFileHeaders()) {
                        if (!fh.isDirectory() && fh.getFileName().endsWith("resources.arsc")) {
                            zf.extractFile(fh, getCacheDir().getAbsolutePath(), tmp.getName());
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    runOnUiThread(() -> Extensions.showMessage(this, "No resources.arsc in zip"));
                    return;
                }
                int count = data.importFromArsc(tmp, pkgName, type);
                try {
                    tmp.delete();
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    if (count > 0) {
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        historyAdapter.refresh();
                    }
                    Extensions.showMessage(this, "Imported " + count + " entries");
                });
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    private void batchImport(String pkgName, String type) {
        pickZipForImport(pkgName, type);
    }

    void enterBatchMode(boolean remove) {
        batchRemove = remove;
        explorerAdapter.setBatchMode(true);
        batchBar.setVisibility(View.VISIBLE);
        MaterialButton go = batchBar.findViewWithTag("go");
        if (go != null) go.setText(remove ? "Delete" : "Save");
        updateBatchLabel();
    }

    void exitBatchMode() {
        explorerAdapter.setBatchMode(false);
        batchBar.setVisibility(View.GONE);
    }

    void updateBatchLabel() {
        if (batchLabel != null) batchLabel.setText("Selected: " + explorerAdapter.getBatchSelected());
    }

    private void onBatchGo() {
        if (batchRemove) {
            List<ArscData.Node> leaves = explorerAdapter.getCheckedLeaves();
            if (leaves.isEmpty()) {
                Extensions.showMessage(this, "Nothing selected");
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete " + leaves.size() + " entries?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Delete", (d, w) -> {
                        int n = 0;
                        for (ArscData.Node node : leaves) {
                            if (node.tag instanceof ResourceEntry && data.deleteEntry((ResourceEntry) node.tag)) n++;
                        }
                        if (n > 0) {
                            data.pushHistory("Batch remove " + n + " entries", null);
                            markDirty();
                            rebuildTree();
                            refreshStrings();
                            historyAdapter.refresh();
                        }
                        exitBatchMode();
                        Extensions.showMessage(this, "Removed " + n);
                    }).show();
            return;
        }
        List<ArscData.Node> leaves = explorerAdapter.getCheckedLeaves();
        List<ArscData.Node> dirs = explorerAdapter.getCheckedDirs();
        if (leaves.isEmpty() && dirs.isEmpty()) {
            Extensions.showMessage(this, "Nothing selected");
            return;
        }
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle("Select directory");
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            promptBatchExportName(new File(files[0]), leaves, dirs);
        });
        picker.show();
    }

    private void promptBatchExportName(File dir, List<ArscData.Node> leaves, List<ArscData.Node> dirs) {
        EditText input = new EditText(this);
        input.setText("arsc_export");
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Export name")
                .setView(UiFields.wrap(this, input, "Name", 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Save", (d, w) -> {
                    String base = input.getText() == null ? "" : input.getText().toString().trim();
                    if (base.isEmpty()) base = "arsc_export";
                    final String baseName = base;
                    new Thread(() -> {
                        try {
                            List<ResourceEntry> all = new ArrayList<>();
                            for (ArscData.Node node : leaves) {
                                if (node.tag instanceof ResourceEntry) all.add((ResourceEntry) node.tag);
                            }
                            for (ArscData.Node dirNode : dirs) {
                                collectLeaves(dirNode, all);
                            }
                            data.exportEntries(all, dir, baseName);
                            runOnUiThread(() -> {
                                exitBatchMode();
                                Extensions.showMessage(this, "Exported to " + dir.getPath());
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> new ErrorUtil(this).showError(e));
                        }
                    }).start();
                }).show();
    }

    private static void collectLeaves(ArscData.Node node, List<ResourceEntry> out) {
        for (ArscData.Node child : node.children) {
            if (child.dir) collectLeaves(child, out);
            else if (child.tag instanceof ResourceEntry) out.add((ResourceEntry) child.tag);
        }
    }

    private void runSearch() {
        if (data == null) return;
        String q = searchInput.getText() == null ? "" : searchInput.getText().toString();
        String t = searchTypeTv.getText() == null ? SEARCH_TYPES[0] : searchTypeTv.getText().toString();
        String p = searchPathInput.getText() == null ? "" : searchPathInput.getText().toString();
        final String query = q;
        final String type = t;
        final String path = p;
        Extensions.showMessage(this, "Searching…");
        new Thread(() -> {
            List<ArscData.SearchHit> hits = data.search(query, type, path);
            runOnUiThread(() -> {
                searchAdapter.setHits(hits);
                Extensions.showMessage(this, hits.size() + " results");
            });
        }).start();
    }

    void openSearchWith(String query, String path) {
        searchInput.setText(query == null ? "" : query);
        searchPathInput.setText(path == null ? "" : path);
        showPage(2);
        runSearch();
    }

    void querierStringSearch(String query) {
        searchInput.setText(query == null ? "" : query);
        searchTypeTv.setText("string", false);
        searchPathInput.setText("");
        showPage(2);
        runSearch();
    }

    private void refreshStrings() {
        if (data == null || stringsAdapter == null) return;
        String q = stringsFilter.getText() == null ? "" : stringsFilter.getText().toString();
        new Thread(() -> {
            List<ResourceEntry> list = data.stringEntries(q);
            runOnUiThread(() -> stringsAdapter.setItems(list));
        }).start();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        ArscQuerier.hideFloat(this);
        super.onDestroy();
    }

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {
        private List<ArscData.HistoryEntry> items = new ArrayList<>();

        void refresh() {
            items = data == null ? new ArrayList<>() : new ArrayList<>(data.history);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ArscEditorActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, pad, pad, pad);
            TextView text = new TextView(ArscEditorActivity.this);
            text.setTextSize(14);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            MaterialButton revertBtn = new MaterialButton(ArscEditorActivity.this);
            revertBtn.setText("Revert");
            row.addView(text, textParams);
            row.addView(revertBtn);
            return new Holder(row, text, revertBtn);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ArscData.HistoryEntry entry = items.get(position);
            holder.text.setText(new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date(entry.time)) + "  " + entry.desc);
            if (entry.revert == null) {
                holder.revert.setVisibility(View.GONE);
            } else {
                holder.revert.setVisibility(View.VISIBLE);
                holder.revert.setOnClickListener(v -> {
                    try {
                        entry.revert.run();
                        markDirty();
                        rebuildTree();
                        refreshStrings();
                        refresh();
                        Extensions.showMessage(ArscEditorActivity.this, "Reverted");
                    } catch (Exception e) {
                        new ErrorUtil(ArscEditorActivity.this).showError(e);
                    }
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final MaterialButton revert;

            Holder(@NonNull View itemView, TextView text, MaterialButton revert) {
                super(itemView);
                this.text = text;
                this.revert = revert;
            }
        }
    }

    class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.Holder> {
        private List<ArscData.SearchHit> hits = new ArrayList<>();

        void setHits(List<ArscData.SearchHit> newHits) {
            hits = newHits == null ? new ArrayList<>() : newHits;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ArscEditorActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, dp(8), pad, dp(8));
            TextView line = new TextView(ArscEditorActivity.this);
            line.setTextSize(15);
            line.setTypeface(null, Typeface.BOLD);
            TextView detail = new TextView(ArscEditorActivity.this);
            detail.setTextSize(13);
            detail.setTypeface(Typeface.MONOSPACE);
            row.addView(line);
            row.addView(detail);
            return new Holder(row, line, detail);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ArscData.SearchHit hit = hits.get(position);
            holder.line.setText(hit.entry.getPackageName() + "/" + hit.line);
            holder.detail.setText(hit.detail == null ? "" : hit.detail);
            holder.itemView.setOnClickListener(v -> showEntryDialog(hit.entry));
        }

        @Override
        public int getItemCount() {
            return hits.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView line;
            final TextView detail;

            Holder(@NonNull View itemView, TextView line, TextView detail) {
                super(itemView);
                this.line = line;
                this.detail = detail;
            }
        }
    }

    class StringsAdapter extends RecyclerView.Adapter<StringsAdapter.Holder> {
        private List<ResourceEntry> items = new ArrayList<>();

        void setItems(List<ResourceEntry> newItems) {
            items = newItems == null ? new ArrayList<>() : newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ArscEditorActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, dp(8), pad, dp(8));
            TextView name = new TextView(ArscEditorActivity.this);
            name.setTextSize(14);
            name.setTypeface(null, Typeface.BOLD);
            TextView value = new TextView(ArscEditorActivity.this);
            value.setTextSize(13);
            value.setSingleLine(true);
            value.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name);
            row.addView(value);
            return new Holder(row, name, value);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ResourceEntry re = items.get(position);
            holder.name.setText(re.getPackageName() + "/string/" + re.getName());
            holder.value.setText(data.entryDisplay(re));
            holder.itemView.setOnClickListener(v -> {
                if (MODE_TRANSLATE.equals(mode)) showEditDialog(re, true);
                else showEntryDialog(re);
            });
            holder.itemView.setOnLongClickListener(v -> {
                showEditDialog(re, MODE_TRANSLATE.equals(mode));
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView value;

            Holder(@NonNull View itemView, TextView name, TextView value) {
                super(itemView);
                this.name = name;
                this.value = value;
            }
        }
    }
}

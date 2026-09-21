package io.github.abdurazaaqmohammed.arsc;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ValueType;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class ArscEditorPlusActivity extends AppCompatActivity {

    public static final String MODE_PLUS = "plus";
    public static final String MODE_EDITOR = "editor";
    public static final String MODE_TRANSLATE = "translate";
    public static final String MODE_QUERIER = "querier";

    private static final String[] SEARCH_TYPES = {"xml", "resource id", "string", "integer", "color"};

    ArscData data;
    String mode = MODE_PLUS;
    boolean dirty = false;
    boolean batchRemove = false;
    boolean savedThisSession = false;

    private MaterialToolbar toolbar;
    private TabLayout tabs;
    private ViewPager2 pager;
    static final int REQ_TEXT = 1757;
    private ArscTreeAdapter explorerAdapter;
    private RecyclerView explorerRv;
    private RecyclerView historyRv;
    private HistoryAdapter historyAdapter;
    private TextView searchInfo;
    private RecyclerView searchResultsRv;
    private SearchAdapter searchAdapter;
    private String lastSearchQuery = "";
    private String lastSearchType = "xml";
    private String lastSearchPath = "";
    private RecyclerView stringsRv;
    private StringsAdapter stringsAdapter;
    private TextView stringsApplyBtn;
    private String stringsFilterQuery = "";
    private boolean stringsMatchCase;
    private boolean stringsRegex;
    private boolean stringsExact;
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
        menu.add(0, R.id.arsc_menu_save, 0, "Save").setIcon(R.drawable.save_24px).setShowAsAction(1);
        menu.add(0, R.id.arsc_menu_more, 0, "More").setIcon(R.drawable.baseline_more_vert_24).setShowAsAction(1);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.arsc_menu_save) {
                saveNow();
                return true;
            }
            showMainMenu();
            return true;
        });
        main.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabs = new TabLayout(this);
        main.addView(tabs, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        explorerPage = buildExplorerPage();
        historyPage = buildHistoryPage();
        searchPage = buildSearchPage();
        stringsPage = buildStringsPage();
        pager = new ViewPager2(this);
        pager.setOffscreenPageLimit(3);
        pager.setAdapter(new PagesAdapter(Arrays.asList(explorerPage, historyPage, searchPage, stringsPage)));
        main.addView(pager, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        String[] titles = {"EXPLORER", "HISTORY", "SEARCH", "STRINGS"};
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position])).attach();
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
        batchGo.setText(R.string.save);
        batchGo.setOnClickListener(v -> onBatchGo());
        batchBar.addView(batchCancel);
        batchBar.addView(batchSelect);
        batchBar.addView(batchLabel, labelParams);
        batchBar.addView(batchGo);
        batchGo.setTag("go");
        main.addView(batchBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if (MODE_TRANSLATE.equals(mode)) pager.setCurrentItem(3, false);
    }

    private String modeTitle() {
        if (MODE_TRANSLATE.equals(mode)) return "Arsc Translation";
        if (MODE_EDITOR.equals(mode)) return "Arsc Editor";
        return "Arsc Editor plus";
    }

    void selectPage(int index) {
        if (pager != null) pager.setCurrentItem(index, false);
    }

    private View buildExplorerPage() {
        explorerRv = new RecyclerView(this);
        explorerRv.setLayoutManager(new LinearLayoutManager(this));
        explorerAdapter = new ArscTreeAdapter(this, new ArscTreeAdapter.Listener() {
            public void onNodeClick(ArscData.Node node) {
                if (node.dir && node.tag instanceof TypeBlock) {
                    openTextPage((TypeBlock) node.tag, null);
                }
            }

            public void onNodeLongClick(ArscData.Node node, View anchor) {
                if (!node.dir) return;
                if (node.tag instanceof TypeBlock) showConfigMenu(node, (TypeBlock) node.tag, anchor);
                else showFolderMenu(node, anchor);
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
        searchInfo = new TextView(this);
        searchInfo.setTextSize(13);
        page.addView(searchInfo);
        MaterialButton goBtn = new MaterialButton(this);
        goBtn.setText(R.string.new_search);
        goBtn.setOnClickListener(v -> openSearchDialog(lastSearchPath));
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
        updateSearchInfo();
        return page;
    }

    private void updateSearchInfo() {
        if (searchInfo == null) return;
        int n = searchAdapter == null ? 0 : searchAdapter.getItemCount();
        if (!TextUtils.isEmpty(lastSearchQuery)) {
            searchInfo.setText(n + " results - " + lastSearchQuery + " [" + lastSearchType + "]"
                    + (TextUtils.isEmpty(lastSearchQuery) ? "" : " in " + lastSearchPath));
        }
    }

    void openSearchDialog(String initialPath) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);
        TextInputLayout queryBox = UiFields.box(this, "Search");
        EditText query = UiFields.field(queryBox, InputType.TYPE_CLASS_TEXT);
        query.setText(lastSearchQuery == null ? "" : lastSearchQuery);
        root.addView(queryBox);
        TextInputLayout typeBox = UiFields.box(this, "Search type");
        MaterialAutoCompleteTextView typeTv = new MaterialAutoCompleteTextView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, SEARCH_TYPES);
        typeTv.setAdapter(adapter);
        typeTv.setText(lastSearchType == null ? SEARCH_TYPES[0] : lastSearchType, false);
        adapter.getFilter().filter(null);
        typeTv.setTextSize(16);
        typeTv.setThreshold(0);
        typeTv.setInputType(InputType.TYPE_NULL);
        typeTv.setCursorVisible(false);
        typeTv.setOnClickListener(v -> {
            adapter.getFilter().filter(null);
            typeTv.showDropDown();
        });
        typeTv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                adapter.getFilter().filter(null);
                typeTv.showDropDown();
            }
        });
        typeBox.addView(typeTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        typeBox.setEndIconMode(TextInputLayout.END_ICON_DROPDOWN_MENU);
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        typeParams.topMargin = dp(8);
        root.addView(typeBox, typeParams);
        TextInputLayout pathBox = UiFields.box(this, "Path (pkg/type, empty = all)");
        EditText path = UiFields.field(pathBox, InputType.TYPE_CLASS_TEXT);
        path.setText(initialPath != null ? initialPath : (lastSearchPath == null ? "" : lastSearchPath));
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pathParams.topMargin = dp(8);
        root.addView(pathBox, pathParams);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Search resources")
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    lastSearchQuery = query.getText() == null ? "" : query.getText().toString();
                    String picked = typeTv.getText() == null ? "" : typeTv.getText().toString();
                    if (!Arrays.asList(SEARCH_TYPES).contains(picked)) {
                        Extensions.showMessage(this, "Pick a search type");
                        return;
                    }
                    lastSearchType = picked;
                    lastSearchPath = path.getText() == null ? "" : path.getText().toString();
                    selectPage(2);
                    runSearch();
                }).show();
    }

    private TextView stringsActionRow(String text, int icon, boolean bold) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextSize(13);
        if (bold) row.setTypeface(null, Typeface.BOLD);
        int pad = dp(10);
        row.setPadding(pad, pad, pad, pad);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
        row.setCompoundDrawablePadding(dp(8));
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        return row;
    }

    private View buildStringsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        TextView reloadRow = stringsActionRow("Reload", R.drawable.baseline_refresh_24, false);
        reloadRow.setOnClickListener(v -> {
            stringsFilterQuery = "";
            stringsMatchCase = false;
            stringsRegex = false;
            stringsExact = false;
            stringsAdapter.clearStaged();
            updateStringsApply();
            refreshStrings();
        });
        TextView filterRow = stringsActionRow("Filter", R.drawable.baseline_filter_list_24, false);
        filterRow.setOnClickListener(v -> showStringsFilterDialog());
        TextView replaceRow = stringsActionRow("Replace", R.drawable.find_replace_24px, false);
        replaceRow.setOnClickListener(v -> showStringsReplaceDialog());
        page.addView(reloadRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(filterRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(replaceRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stringsApplyBtn = stringsActionRow("Apply changes", R.drawable.baseline_check_circle_24, true);
        stringsApplyBtn.setVisibility(View.GONE);
        stringsApplyBtn.setOnClickListener(v -> applyStringsStaged());
        page.addView(stringsApplyBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stringsRv = new RecyclerView(this);
        stringsRv.setLayoutManager(new LinearLayoutManager(this));
        stringsAdapter = new StringsAdapter();
        stringsRv.setAdapter(stringsAdapter);
        page.addView(stringsRv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return page;
    }

    private void updateStringsApply() {
        if (stringsApplyBtn == null || stringsAdapter == null) return;
        if (stringsAdapter.hasStaged()) {
            stringsApplyBtn.setVisibility(View.VISIBLE);
            stringsApplyBtn.setText(getString(R.string.apply_changes_x, stringsAdapter.stagedCount()));
        } else {
            stringsApplyBtn.setVisibility(View.GONE);
        }
    }

    private static boolean matchOne(String text, String find, boolean matchCase, boolean regex, boolean exact) {
        if (text == null || find == null) return false;
        try {
            if (regex) {
                Pattern p = matchCase
                        ? Pattern.compile(find)
                        : Pattern.compile(find, Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(text);
                return exact ? m.matches() : m.find();
            }
            if (exact) return matchCase ? text.equals(find) : text.equalsIgnoreCase(find);
            return matchCase
                    ? text.contains(find)
                    : text.toLowerCase(Locale.US).contains(find.toLowerCase(Locale.US));
        } catch (Exception e) {
            return false;
        }
    }

    private static String replaceOne(String text, String find, String repl, boolean matchCase, boolean regex, boolean exact) {
        if (text == null || find == null || repl == null) return text;
        try {
            if (regex) {
                Pattern p = matchCase
                        ? Pattern.compile(find)
                        : Pattern.compile(find, Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(text);
                if (exact) return m.matches() ? m.replaceAll(repl) : text;
                return m.replaceAll(repl);
            }
            if (exact) return text;
            if (matchCase) return text.replace(find, repl);
            Matcher m = Pattern.compile(
                    Pattern.quote(find), Pattern.CASE_INSENSITIVE).matcher(text);
            return m.replaceAll(Matcher.quoteReplacement(repl));
        } catch (Exception e) {
            return text;
        }
    }

    @SuppressLint("SetTextI18n")
    private void showStringsFilterDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);
        TextInputLayout box = UiFields.box(this, "Filter");
        EditText input = UiFields.field(box, InputType.TYPE_CLASS_TEXT);
        input.setText(stringsFilterQuery == null ? "" : stringsFilterQuery);
        root.addView(box);
        MaterialCheckBox cbCase = new MaterialCheckBox(this);
        cbCase.setText(R.string.match_case);
        cbCase.setChecked(stringsMatchCase);
        MaterialCheckBox cbRegex = new MaterialCheckBox(this);
        cbRegex.setText(R.string.regex);
        cbRegex.setChecked(stringsRegex);
        MaterialCheckBox cbExact = new MaterialCheckBox(this);
        cbExact.setText(R.string.match_exactly);
        cbExact.setChecked(stringsExact);
        root.addView(cbCase);
        root.addView(cbRegex);
        root.addView(cbExact);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Filter strings")
                .setView(root)
                .setNegativeButton("Clear", (d, w) -> {
                    stringsFilterQuery = "";
                    stringsMatchCase = false;
                    stringsRegex = false;
                    stringsExact = false;
                    refreshStrings();
                })
                .setPositiveButton("Apply", (d, w) -> {
                    String q = input.getText() == null ? "" : input.getText().toString();
                    if (cbRegex.isChecked() && !q.isEmpty()) {
                        try {
                            Pattern.compile(q);
                        } catch (Exception e) {
                            Extensions.showMessage(this, "Bad regex");
                            return;
                        }
                    }
                    stringsFilterQuery = q;
                    stringsMatchCase = cbCase.isChecked();
                    stringsRegex = cbRegex.isChecked();
                    stringsExact = cbExact.isChecked();
                    refreshStrings();
                }).show();
    }

    private void showStringsReplaceDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);
        TextInputLayout findBox = UiFields.box(this, "Find");
        EditText findInput = UiFields.field(findBox, InputType.TYPE_CLASS_TEXT);
        root.addView(findBox);
        TextInputLayout replBox = UiFields.box(this, "Replace with");
        EditText replInput = UiFields.field(replBox, InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams replParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        replParams.topMargin = dp(8);
        root.addView(replBox, replParams);
        CheckBox cbCase = new MaterialCheckBox(this);
        cbCase.setText(R.string.match_case);
        cbCase.setChecked(true);
        CheckBox cbRegex = new MaterialCheckBox(this);
        cbRegex.setText(R.string.regex);
        CheckBox cbExact = new MaterialCheckBox(this);
        cbExact.setText(R.string.match_exactly);
        root.addView(cbCase);
        root.addView(cbRegex);
        root.addView(cbExact);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Replace in all strings")
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Replace", (d, w) -> {
                    String find = findInput.getText() == null ? "" : findInput.getText().toString();
                    String repl = replInput.getText() == null ? "" : replInput.getText().toString();
                    if (find.isEmpty()) return;
                    boolean mc = cbCase.isChecked();
                    boolean rx = cbRegex.isChecked();
                    boolean ex = cbExact.isChecked();
                    if (rx) {
                        try {
                            Pattern.compile(find);
                        } catch (Exception e) {
                            Extensions.showMessage(this, "Bad regex");
                            return;
                        }
                    }
                    stageStringsReplace(find, repl, mc, rx, ex);
                }).show();
    }

    private void stageStringsReplace(String find, String repl, boolean matchCase, boolean regex, boolean exact) {
        if (data == null) return;
        Extensions.showMessage(this, "Matching…");
        new Thread(() -> {
            Map<ResourceEntry, String> staged = new LinkedHashMap<>();
            try {
                for (ResourceEntry re : data.stringEntries("")) {
                    if (re == null) continue;
                    Entry e = data.defaultEntry(re);
                    if (e == null || e.isNull()) continue;
                    ValueType vt = null;
                    try {
                        vt = e.getValueType();
                    } catch (Exception ignored) {
                    }
                    if (vt != ValueType.STRING) continue;
                    String current = null;
                    try {
                        current = e.getValueAsString();
                    } catch (Exception ignored) {
                    }
                    if (current == null) continue;
                    if (!matchOne(current, find, matchCase, regex, exact)) continue;
                    String next = exact && !regex ? repl : replaceOne(current, find, repl, matchCase, regex, exact);
                    if (!next.equals(current)) staged.put(re, next);
                }
            } catch (Exception ignored) {
            }
            final int n = staged.size();
            runOnUiThread(() -> {
                stringsAdapter.setStaged(staged);
                updateStringsApply();
                Extensions.showMessage(this, n + " staged - tap Apply changes");
            });
        }).start();
    }

    private void showStageDialog(ResourceEntry re) {
        EditText input = new EditText(this);
        input.setText(stringsAdapter.pendingValue(re));
        input.setSingleLine(false);
        String name = re.getName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(name)
                .setView(UiFields.wrap(this, input, "Value", 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() == null ? "" : input.getText().toString();
                    stringsAdapter.stage(re, text);
                    updateStringsApply();
                }).show();
    }

    private void applyStringsStaged() {
        if (data == null || !stringsAdapter.hasStaged()) return;
        final Map<ResourceEntry, String> staged = stringsAdapter.stagedCopy();
        Extensions.showMessage(this, "Applying…");
        new Thread(() -> {
            int updated = 0;
            int invalid = 0;
            for (Map.Entry<ResourceEntry, String> kv : staged.entrySet()) {
                try {
                    Entry e = data.defaultEntry(kv.getKey());
                    if (e == null || e.isNull()) {
                        invalid++;
                        continue;
                    }
                    if (data.setEntryValue(e, kv.getValue())) updated++;
                    else invalid++;
                } catch (Exception ex) {
                    invalid++;
                }
            }
            final int done = updated;
            final int bad = invalid;
            runOnUiThread(() -> {
                stringsAdapter.clearStaged();
                updateStringsApply();
                if (done > 0) {
                    data.pushHistory("Strings replace (" + done + ")", null);
                    markDirty();
                    rebuildTree();
                    historyAdapter.refresh();
                }
                refreshStrings();
                Extensions.showMessage(this, "Updated " + done + ", invalid " + bad);
            });
        }).start();
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
        saveNow(null);
    }

    private void saveNow(Runnable onDone) {
        if (data == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Extensions.showMessage(this, "Saving…");
        new Thread(() -> {
            try {
                data.save();
                runOnUiThread(() -> {
                    dirty = false;
                    if (data.apkFile != null) savedThisSession = true;
                    toolbar.setSubtitle(data.arscFile.getName());
                    rebuildTree();
                    refreshStrings();
                    historyAdapter.refresh();
                    Extensions.showMessage(this, "Saved");
                    if (onDone != null) onDone.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    void finishWithApkResult() {
        if (data != null && data.apkFile != null && savedThisSession && data.arscFile != null) {
            Intent result = new Intent();
            result.setData(Uri.fromFile(data.arscFile));
            setResult(757, result);
        }
        finish();
    }

    private void showMainMenu() {
        View anchor = toolbar.findViewById(R.id.arsc_menu_more);
        PopupMenu menu = anchor != null
                ? new PopupMenu(this, anchor)
                : new PopupMenu(this, toolbar, Gravity.END);
        if (!MODE_EDITOR.equals(mode)) menu.getMenu().add("Resource querier");
        menu.getMenu().add("Search");
        menu.getMenu().add("Batch export");
        menu.getMenu().add("Batch import");
        menu.getMenu().add("Export strings");
        menu.getMenu().add("Import strings");
        menu.getMenu().add("Backup");
        menu.getMenu().add("Exit");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "Resource querier" -> ArscQuerier.toggleFloat(this);
                case "Search" -> openSearchDialog("");
                case "Batch export" -> enterBatchMode(false);
                case "Batch import" -> batchImport(null, null);
                case "Export strings" -> exportStrings();
                case "Import strings" -> importStrings();
                case "Backup" -> backupNow();
                default -> confirmExit();
            }
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
            finishWithApkResult();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved changes")
                .setMessage("Save before exit?")
                .setPositiveButton("Save", (d, w) -> saveNow(this::finishWithApkResult))
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
        try {
            if (e.isComplex()) {
                Extensions.showMessage(this, "Complex value - open its config in the TEXT tab");
                return;
            }
        } catch (Exception ignored) {
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
            MaterialAutoCompleteTextView boolTv = new MaterialAutoCompleteTextView(this);
            boolTv.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new String[]{"true", "false"}));
            boolean current = false;
            try {
                current = e.getValueAsBoolean();
            } catch (Exception ignored) {
            }
            boolTv.setText(Boolean.toString(current), false);
            boolTv.setInputType(InputType.TYPE_NULL);
            TextInputLayout boolBox = UiFields.box(this, "Value");
            boolBox.addView(boolTv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            root.addView(boolBox);
            input = boolTv;
        } else {
            TextInputLayout box = UiFields.box(this, "Value");
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
            TextInputLayout box = UiFields.box(this,
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
        menu.getMenu().add("Batch remove");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "Search" -> openSearchDialog(node.key);
                case "Add" -> promptAddEntry(pkgName, type);
                case "Import" -> pickZipForImport(pkgName, type);
                case "Delete" -> confirmDeleteType(pkgName, type);
                case "Batch remove" -> enterBatchMode(true);
                default -> enterBatchMode(false);
            }
            return true;
        });
        menu.show();
    }

    private void showConfigMenu(ArscData.Node node, TypeBlock tb, View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add("Open in text editor");
        menu.getMenu().add("Search in type");
        menu.getMenu().add("Export config");
        menu.getMenu().add("Delete config");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            switch (title) {
                case "Open in text editor" -> openTextPage(tb, null);
                case "Search in type" -> openSearchDialog(node.key);
                case "Export config" -> exportEntriesNow(ArscData.resourcesOf(tb));
                default -> confirmDeleteConfig(tb);
            }
            return true;
        });
        menu.show();
    }

    private void confirmDeleteConfig(TypeBlock tb) {
        String label = ArscData.configLabel(tb);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + label + "?")
                .setMessage("All entries of this config will be removed after save.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Delete", (d, w) -> {
                    int n = data.deleteTypeBlock(tb);
                    if (n > 0) {
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

    void openTextPage(TypeBlock tb, String highlightName) {
        if (tb == null || data == null) return;
        String label = ArscData.configLabel(tb);
        String pkgName = "";
        try {
            if (tb.getPackageBlock() != null) pkgName = tb.getPackageBlock().getName();
        } catch (Exception ignored) {
        }
        ArscTextActivity.sessionData = data;
        ArscTextActivity.sessionBlock = tb;
        ArscTextActivity.sessionTitle = pkgName.isEmpty() ? label : pkgName + "/" + label;
        ArscTextActivity.sessionHighlight = highlightName;
        startActivityForResult(new Intent(this, ArscTextActivity.class), REQ_TEXT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == REQ_TEXT && resultCode == RESULT_OK) {
            markDirty();
            rebuildTree();
            refreshStrings();
            historyAdapter.refresh();
        }
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
                try (ZipFile zf = new ZipFile(zip)) {
                    for (FileHeader fh : zf.getFileHeaders()) {
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

    private void exportStrings() {
        if (data == null) return;
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle("Select directory");
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            File dir = new File(files[0]);
            Extensions.showMessage(this, "Exporting…");
            new Thread(() -> {
                try {
                    File out = data.exportStringsXml(dir);
                    runOnUiThread(() -> Extensions.showMessage(this, "Exported " + out.getName()));
                } catch (Exception e) {
                    runOnUiThread(() -> new ErrorUtil(this).showError(e));
                }
            }).start();
        });
        picker.show();
    }

    private void importStrings() {
        if (data == null) return;
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.extensions = new String[]{".xml"};
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle("Select strings file");
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            File xml = new File(files[0]);
            Extensions.showMessage(this, "Importing…");
            new Thread(() -> {
                try {
                    int n = data.importStringsXml(xml);
                    runOnUiThread(() -> {
                        if (n > 0) {
                            markDirty();
                            rebuildTree();
                            refreshStrings();
                            historyAdapter.refresh();
                        }
                        Extensions.showMessage(this, "Imported " + n + " values");
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> new ErrorUtil(this).showError(e));
                }
            }).start();
        });
        picker.show();
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
        if (batchLabel != null) batchLabel.setText(getString(R.string.selected_i, explorerAdapter.getBatchSelected()));
    }

    private List<ResourceEntry> collectBatchEntries() {
        List<ResourceEntry> out = new ArrayList<>();
        HashSet<Integer> seen = new HashSet<>();
        for (ArscData.Node n : explorerAdapter.getCheckedNodes()) {
            try {
                if (n.tag instanceof TypeBlock) {
                    for (ResourceEntry re : ArscData.resourcesOf((TypeBlock) n.tag)) {
                        if (re != null && seen.add(re.getResourceId())) out.add(re);
                    }
                } else if (n.tag instanceof SpecTypePair) {
                    for (ResourceEntry re : ArscData.resourcesOf((SpecTypePair) n.tag)) {
                        if (re != null && seen.add(re.getResourceId())) out.add(re);
                    }
                } else if (n.tag instanceof PackageBlock) {
                    for (SpecTypePair spec : ((PackageBlock) n.tag).listSpecTypePairs()) {
                        for (ResourceEntry re : ArscData.resourcesOf(spec)) {
                            if (re != null && seen.add(re.getResourceId())) out.add(re);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private void onBatchGo() {
        List<ResourceEntry> all = collectBatchEntries();
        if (all.isEmpty()) {
            Extensions.showMessage(this, "Nothing selected");
            return;
        }
        if (batchRemove) {
            final List<ResourceEntry> entries = all;
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete " + entries.size() + " entries?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Delete", (d, w) -> {
                        int n = 0;
                        for (ResourceEntry re : entries) {
                            if (data.deleteEntry(re)) n++;
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
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle("Select directory");
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            promptBatchExportName(new File(files[0]), all);
        });
        picker.show();
    }

    void exportEntriesNow(List<ResourceEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            Extensions.showMessage(this, "Nothing to export");
            return;
        }
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle("Select directory");
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            promptBatchExportName(new File(files[0]), entries);
        });
        picker.show();
    }

    private void promptBatchExportName(File dir, List<ResourceEntry> entries) {
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
                    final List<ResourceEntry> all = new ArrayList<>(entries);
                    new Thread(() -> {
                        try {
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

    private void runSearch() {
        if (data == null) return;
        final String query = lastSearchQuery == null ? "" : lastSearchQuery;
        final String type = lastSearchType == null ? SEARCH_TYPES[0] : lastSearchType;
        final String path = lastSearchPath == null ? "" : lastSearchPath;
        Extensions.showMessage(this, "Searching…");
        new Thread(() -> {
            List<ArscData.SearchHit> hits = data.search(query, type, path);
            runOnUiThread(() -> {
                searchAdapter.setHits(hits);
                updateSearchInfo();
                Extensions.showMessage(this, hits.size() + " results");
            });
        }).start();
    }

    void openSearchWith(String query, String path) {
        lastSearchQuery = query == null ? "" : query;
        lastSearchPath = path == null ? "" : path;
        selectPage(2);
        runSearch();
    }

    void querierStringSearch(String query) {
        lastSearchQuery = query == null ? "" : query;
        lastSearchType = "string";
        lastSearchPath = "";
        selectPage(2);
        runSearch();
    }

    void openTextForEntry(ResourceEntry re) {
        if (re == null) return;
        TypeBlock tb = null;
        try {
            Entry e = data.defaultEntry(re);
            if (e != null) tb = e.getTypeBlock();
        } catch (Exception ignored) {
        }
        if (tb == null) {
            Extensions.showMessage(this, "No editable value");
            return;
        }
        String name = null;
        try {
            name = re.getName();
        } catch (Exception ignored) {
        }
        openTextPage(tb, name);
    }

    void openTextForHit(ArscData.SearchHit hit) {
        if (hit == null) return;
        TypeBlock tb = null;
        try {
            if (hit.sample != null) tb = hit.sample.getTypeBlock();
        } catch (Exception ignored) {
        }
        if (tb == null) {
            try {
                Entry e = data.defaultEntry(hit.entry);
                if (e != null) tb = e.getTypeBlock();
            } catch (Exception ignored) {
            }
        }
        if (tb == null) {
            Extensions.showMessage(this, "No editable value");
            return;
        }
        String name = null;
        try {
            name = hit.entry.getName();
        } catch (Exception ignored) {
        }
        openTextPage(tb, name);
    }

    private void refreshStrings() {
        if (data == null || stringsAdapter == null) return;
        final String q = stringsFilterQuery == null ? "" : stringsFilterQuery;
        final boolean mc = stringsMatchCase;
        final boolean rx = stringsRegex;
        final boolean ex = stringsExact;
        new Thread(() -> {
            List<ResourceEntry> list = data.stringEntries("");
            Map<ResourceEntry, String> values = new LinkedHashMap<>();
            for (ResourceEntry re : list) {
                if (re == null) continue;
                String name;
                try {
                    name = re.getName();
                } catch (Exception e) {
                    continue;
                }
                String v = data.entryDisplay(re);
                if (q.isEmpty() || matchOne(name, q, mc, rx, ex) || matchOne(v, q, mc, rx, ex)) {
                    values.put(re, v == null ? "" : v);
                }
            }
            List<ResourceEntry> out = new ArrayList<>(values.keySet());
            out.sort((a, b) -> {
                String va = values.get(a);
                String vb = values.get(b);
                int c = va.compareToIgnoreCase(vb);
                if (c != 0) return c;
                try {
                    return a.getName().compareToIgnoreCase(b.getName());
                } catch (Exception e) {
                    return 0;
                }
            });
            runOnUiThread(() -> stringsAdapter.setItems(out));
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

    static class PagesAdapter extends RecyclerView.Adapter<PagesAdapter.PageHolder> {
        private final List<View> pages;

        PagesAdapter(List<View> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout frame = new FrameLayout(parent.getContext());
            frame.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new PageHolder(frame);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            View page = pages.get(position);
            if (page.getParent() instanceof ViewGroup && page.getParent() != holder.frame) {
                ((ViewGroup) page.getParent()).removeView(page);
            }
            if (holder.frame.getChildCount() == 0) {
                holder.frame.addView(page, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        static class PageHolder extends RecyclerView.ViewHolder {
            final FrameLayout frame;

            PageHolder(@NonNull FrameLayout frame) {
                super(frame);
                this.frame = frame;
            }
        }
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
            LinearLayout row = new LinearLayout(ArscEditorPlusActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, pad, pad, pad);
            TextView text = new TextView(ArscEditorPlusActivity.this);
            text.setTextSize(14);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            MaterialButton revertBtn = new MaterialButton(ArscEditorPlusActivity.this);
            revertBtn.setText(R.string.revert);
            row.addView(text, textParams);
            row.addView(revertBtn);
            return new Holder(row, text, revertBtn);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ArscData.HistoryEntry entry = items.get(position);
            holder.text.setText(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(entry.time)) + "  " + entry.desc);
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
                        Extensions.showMessage(ArscEditorPlusActivity.this, "Reverted");
                    } catch (Exception e) {
                        new ErrorUtil(ArscEditorPlusActivity.this).showError(e);
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
            LinearLayout row = new LinearLayout(ArscEditorPlusActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, dp(8), pad, dp(8));
            TextView line = new TextView(ArscEditorPlusActivity.this);
            line.setTextSize(15);
            line.setTypeface(null, Typeface.BOLD);
            TextView detail = new TextView(ArscEditorPlusActivity.this);
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
            holder.itemView.setOnClickListener(v -> openTextForHit(hit));
        }

        @Override
        public int getItemCount() {
            return hits.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
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
        private static final int COLOR_STAGED = 0xFF2E7D32;
        private List<ResourceEntry> items = new ArrayList<>();
        private final Map<ResourceEntry, String> staged = new LinkedHashMap<>();

        void setItems(List<ResourceEntry> newItems) {
            items = newItems == null ? new ArrayList<>() : newItems;
            notifyDataSetChanged();
        }

        void stage(ResourceEntry re, String newValue) {
            String current = data == null ? "" : data.entryDisplay(re);
            if (newValue == null || newValue.equals(current)) staged.remove(re);
            else staged.put(re, newValue);
            notifyDataSetChanged();
        }

        void setStaged(Map<ResourceEntry, String> next) {
            staged.clear();
            if (next != null) staged.putAll(next);
            notifyDataSetChanged();
        }

        boolean hasStaged() {
            return !staged.isEmpty();
        }

        int stagedCount() {
            return staged.size();
        }

        Map<ResourceEntry, String> stagedCopy() {
            return new LinkedHashMap<>(staged);
        }

        void clearStaged() {
            staged.clear();
            notifyDataSetChanged();
        }

        String pendingValue(ResourceEntry re) {
            String v = staged.get(re);
            return v != null ? v : (data == null ? "" : data.entryDisplay(re));
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ArscEditorPlusActivity.this);
            row.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(12);
            row.setPadding(pad, dp(8), pad, dp(8));
            TextView name = new TextView(ArscEditorPlusActivity.this);
            name.setTextSize(14);
            name.setTypeface(null, Typeface.BOLD);
            TextView value = new TextView(ArscEditorPlusActivity.this);
            value.setTextSize(13);
            value.setSingleLine(true);
            value.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name);
            row.addView(value);
            return new Holder(row, name, value);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            ResourceEntry re = items.get(position);
            holder.name.setText(re.getPackageName() + "/string/" + re.getName());
            boolean isStaged = staged.containsKey(re);
            holder.value.setText(isStaged ? staged.get(re) : data.entryDisplay(re));
            if (holder.value.getTag() == null) holder.value.setTag(holder.value.getCurrentTextColor());
            holder.value.setTextColor(isStaged ? COLOR_STAGED : (int) holder.value.getTag());
            holder.itemView.setOnClickListener(v -> {
                if (MODE_TRANSLATE.equals(mode)) showEditDialog(re, true);
                else showStageDialog(re);
            });
            holder.itemView.setOnLongClickListener(v -> {
                openTextForEntry(re);
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

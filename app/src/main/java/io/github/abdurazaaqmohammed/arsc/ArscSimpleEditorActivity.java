package io.github.abdurazaaqmohammed.arsc;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ValueType;

import org.json.JSONArray;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class ArscSimpleEditorActivity extends AppCompatActivity {

    private static final String HIST_KEY = "arsc_simple_search_hist";

    ArscData data;
    boolean dirty = false;
    boolean savedThisSession = false;

    private MaterialToolbar toolbar;
    private String fileName = "resources.arsc";
    private LinearLayout filterWrap;
    private EditText filterInput;
    private RecyclerView rv;
    private SimpleAdapter adapter;
    private final Deque<Screen> stack = new ArrayDeque<>();


    abstract static class Screen {
        abstract String title();
        boolean filterable() { return false; }
    }

    final class Root extends Screen {
        @Override String title() { return getString(R.string.arsc_editor); }
    }

    static final class Types extends Screen {
        final String pkg;
        Types(String pkg) { this.pkg = pkg; }
        @Override String title() { return pkg; }
        @Override boolean filterable() { return true; }
    }

    static final class Configs extends Screen {
        final String pkg;
        final String type;
        Configs(String pkg, String type) { this.pkg = pkg; this.type = type; }
        @Override String title() { return type; }
        @Override boolean filterable() { return true; }
    }

    static final class Entries extends Screen {
        final String pkg;
        final String type;
        final TypeBlock block;
        final String label;
        Entries(String pkg, String type, TypeBlock block, String label) {
            this.pkg = pkg; this.type = type; this.block = block; this.label = label;
        }
        @Override String title() { return label == null || label.isEmpty() ? type : label; }
        @Override boolean filterable() { return true; }
    }

    final class Pool extends Screen {
        @Override String title() { return getString(R.string.arsc_string_pool); }
        @Override boolean filterable() { return true; }
    }

    final class Results extends Screen {
        final String query;
        final int kind;
        final List<ArscData.SimpleHit> hits;
        Results(String query, int kind, List<ArscData.SimpleHit> hits) {
            this.query = query; this.kind = kind; this.hits = hits;
        }
        @Override String title() { return getString(R.string.search); }
        @Override boolean filterable() { return true; }
    }


    static final class Row {
        static final int SIMPLE = 0;
        static final int TYPE = 1;
        static final int ENTRY = 2;
        final int kind;
        final String left;
        final String line1;
        final String line2;
        final Object tag;
        Row(int kind, String left, String line1, String line2, Object tag) {
            this.kind = kind; this.left = left; this.line1 = line1; this.line2 = line2; this.tag = tag;
        }
        static Row simple(String text, Object tag) {
            return new Row(SIMPLE, null, text, null, tag);
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(getIntent().getIntExtra("theme", prefs.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light)));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        String path = getIntent().getStringExtra("path");
        String apkPath = getIntent().getStringExtra("apkPath");
        String entryPath = getIntent().getStringExtra("zipEntryPath");
        if (path == null) {
            finish();
            return;
        }
        fileName = new File(path).getName();
        buildShell();
        loadAsync(new File(path), apkPath == null ? null : new File(apkPath), entryPath);
    }

    private void buildShell() {
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.arsc_editor));
        toolbar.setSubtitle(fileName);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        Menu menu = toolbar.getMenu();
        menu.add(0, R.id.arsc_menu_save, 0, getString(R.string.arsc_save)).setIcon(R.drawable.save_24px).setShowAsAction(1);
        menu.add(0, R.id.arsc_menu_more, 0, getString(R.string.arsc_more)).setIcon(R.drawable.baseline_more_vert_24).setShowAsAction(1);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.arsc_menu_save) {
                saveNow();
                return true;
            }
            showMainMenu();
            return true;
        });
        main.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        filterWrap = new LinearLayout(this);
        filterWrap.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        filterWrap.setPadding(pad, pad, pad, 0);
        com.google.android.material.textfield.TextInputLayout box = UiFields.box(this, "Filter");
        filterInput = UiFields.field(box, InputType.TYPE_CLASS_TEXT);
        filterInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { render(false); }
            public void afterTextChanged(Editable s) { }
        });
        filterWrap.addView(box);
        filterWrap.setVisibility(View.GONE);
        main.addView(filterWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        adapter = new SimpleAdapter();
        rv.setAdapter(adapter);
        main.addView(rv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(main);
    }

    private void loadAsync(File arsc, File apk, String entryPath) {
        Extensions.showMessage(this, getString(R.string.arsc_loading));
        new Thread(() -> {
            try {
                ArscData loaded = ArscData.load(arsc, apk, entryPath);
                runOnUiThread(() -> {
                    data = loaded;
                    stack.clear();
                    stack.push(new Root());
                    render(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new ErrorUtil(this).showError(e);
                    finish();
                });
            }
        }).start();
    }


    private Screen current() {
        return stack.peek();
    }

    private void push(Screen s) {
        stack.push(s);
        filterInput.setText("");
        render(true);
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.pop();
            filterInput.setText("");
            render(true);
            return;
        }
        confirmExit();
    }

    private void render(boolean clearFilter) {
        Screen s = current();
        if (s == null) return;
        toolbar.setTitle(s.title());
        if (filterWrap != null) filterWrap.setVisibility(s.filterable() ? View.VISIBLE : View.GONE);
        String filter = filterInput.getText() == null ? "" : filterInput.getText().toString();
        if (s instanceof Root) {
            adapter.setRows(rootRows());
        } else if (s instanceof Types) {
            adapter.setRows(typeRows(((Types) s).pkg, filter));
        } else if (s instanceof Configs) {
            adapter.setRows(configRows((Configs) s, filter));
        } else if (s instanceof Entries es) {
            Extensions.showMessage(this, getString(R.string.arsc_loading_short));
            new Thread(() -> {
                List<Row> rows = entryRows(es, filter);
                runOnUiThread(() -> {
                    if (current() == es) adapter.setRows(rows);
                });
            }).start();
        } else if (s instanceof Pool) {
            Extensions.showMessage(this, getString(R.string.arsc_loading_short));
            new Thread(() -> {
                List<Row> rows = poolRows(filter);
                runOnUiThread(() -> {
                    if (current() == s) adapter.setRows(rows);
                });
            }).start();
        } else if (s instanceof Results rs) {
            adapter.setRows(resultRows(rs, filter));
        }
    }

    private List<Row> rootRows() {
        List<Row> out = new ArrayList<>();
        out.add(Row.simple("String pool", "pool"));
        out.add(Row.simple("Search resource value", "search_value"));
        out.add(Row.simple("Search by ID", "search_id"));
        try {
            if (data != null) {
                for (PackageBlock pkg : data.table.listPackages()) {
                    out.add(Row.simple(pkg.getName(), pkg));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<Row> typeRows(String pkgName, String filter) {
        List<Row> out = new ArrayList<>();
        String lq = filter == null ? "" : filter.toLowerCase(Locale.US);
        try {
            PackageBlock pkg = data.packageByName(pkgName);
            if (pkg != null) {
                for (SpecTypePair spec : pkg.listSpecTypePairs()) {
                    String typeName = spec.getTypeName();
                    if (!lq.isEmpty() && (typeName == null || !typeName.toLowerCase(Locale.US).contains(lq))) continue;
                    out.add(new Row(Row.TYPE, ArscData.typeIdHex(pkg, spec), typeName, null, spec));
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<Row> configRows(Configs s, String filter) {
        List<Row> out = new ArrayList<>();
        String lq = filter == null ? "" : filter.toLowerCase(Locale.US);
        for (TypeBlock tb : data.configsOf(s.pkg, s.type)) {
            String label = ArscData.configLabel(tb);
            if (!lq.isEmpty() && !label.toLowerCase(Locale.US).contains(lq)) continue;
            out.add(Row.simple(label, tb));
        }
        return out;
    }

    private List<Row> entryRows(Entries s, String filter) {
        List<Row> out = new ArrayList<>();
        String lq = filter == null ? "" : filter.toLowerCase(Locale.US).trim();
        try {
            List<Entry> entries = s.block.listEntries(true);
            for (Entry e : entries) {
                if (e == null || e.isNull()) continue;
                String name;
                try {
                    name = e.getName();
                } catch (Exception ex) {
                    continue;
                }
                if (name == null) continue;
                String value = ArscData.entryValue(e);
                if (!lq.isEmpty()
                        && !name.toLowerCase(Locale.US).contains(lq)
                        && (value == null || !value.toLowerCase(Locale.US).contains(lq))) continue;
                out.add(new Row(Row.ENTRY, ArscData.entryIdHex(e), name, value, e));
                if (out.size() >= 10000) break;
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private List<Row> poolRows(String filter) {
        List<Row> out = new ArrayList<>();
        for (ArscData.PoolString ps : data.poolStrings(filter == null ? "" : filter.trim())) {
            out.add(new Row(Row.ENTRY, String.format(Locale.US, "%04X", ps.index & 0xFFFF), ps.text, null, ps));
        }
        return out;
    }

    private List<Row> resultRows(Results rs, String filter) {
        List<Row> out = new ArrayList<>();
        String lq = filter == null ? "" : filter.toLowerCase(Locale.US).trim();
        for (ArscData.SimpleHit h : rs.hits) {
            String name;
            try {
                name = h.re.getType() + "/" + h.re.getName();
            } catch (Exception e) {
                continue;
            }
            if (!lq.isEmpty()
                    && !name.toLowerCase(Locale.US).contains(lq)
                    && (h.value == null || !h.value.toLowerCase(Locale.US).contains(lq))) continue;
            out.add(new Row(Row.ENTRY, ArscData.entryIdHex(h.entry), name, h.value, h));
        }
        return out;
    }


    private void onRowClick(Row row) {
        Screen s = current();
        if (s instanceof Root) {
            Object tag = row.tag;
            if ("pool".equals(tag)) push(new Pool());
            else if ("search_value".equals(tag)) showSearchValueDialog();
            else if ("search_id".equals(tag)) showSearchIdDialog();
            else if (tag instanceof PackageBlock) push(new Types(((PackageBlock) tag).getName()));
            return;
        }
        if (s instanceof Types) {
            if (row.tag instanceof SpecTypePair spec) {
                push(new Configs(((Types) s).pkg, spec.getTypeName()));
            }
            return;
        }
        if (s instanceof Configs cs) {
            if (row.tag instanceof TypeBlock tb) {
                push(new Entries(cs.pkg, cs.type, tb, ArscData.configLabel(tb)));
            }
            return;
        }
        if (s instanceof Entries) {
            if (row.tag instanceof Entry e) {
                ResourceEntry re = null;
                try {
                    re = e.getResourceEntry();
                } catch (Exception ignored) {
                }
                showEntryDetail(e, re);
            }
            return;
        }
        if (s instanceof Pool) {
            if (row.tag instanceof ArscData.PoolString) {
                showPoolEdit((ArscData.PoolString) row.tag);
            }
            return;
        }
        if (s instanceof Results) {
            if (row.tag instanceof ArscData.SimpleHit h) {
                showEntryDetail(h.entry, h.re);
            }
        }
    }


    private void showEntryDetail(Entry e, ResourceEntry re) {
        String title;
        try {
            title = (re != null ? re.getType() + "/" + re.getName() : e.getName());
        } catch (Exception ex) {
            title = "Entry";
        }
        String hex;
        try {
            hex = String.format(Locale.US, "0x%08X", e.getResourceId());
        } catch (Exception ex) {
            hex = "";
        }
        String typeName;
        try {
            typeName = String.valueOf(e.getValueType());
        } catch (Exception ex) {
            typeName = "?";
        }
        TextView preview = new TextView(this);
        preview.setText(hex + "  " + typeName + "\n" + ArscData.entryValue(e));
        preview.setTypeface(Typeface.MONOSPACE);
        preview.setTextSize(13);
        preview.setTextIsSelectable(true);
        int pad = dp(12);
        preview.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(preview);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.edit), (d, w) -> showEditEntry(e, re))
                .show();
    }

    void showEditEntry(Entry e, ResourceEntry re) {
        if (e == null) return;
        ValueType type;
        try {
            type = e.getValueType();
        } catch (Exception ex) {
            Extensions.showMessage(this, getString(R.string.arsc_unknown_type));
            return;
        }
        if (type == null) {
            Extensions.showMessage(this, getString(R.string.arsc_unknown_type));
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);
        TextView info = new TextView(this);
        try {
            info.setText(String.format(Locale.US, "0x%08X", e.getResourceId()) + "  " + type.name());
        } catch (Exception ignored) {
            info.setText(type.name());
        }
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
        String resolvedName;
        try {
            resolvedName = re != null ? re.getName() : e.getName();
        } catch (Exception ex) {
            resolvedName = "entry";
        }
        final String entryName = resolvedName;
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.arsc_edit_x, entryName))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() == null ? "" : input.getText().toString();
                    if (data.setEntryValue(e, text)) {
                        try {
                            data.pushHistory("Edit " + entryName, null);
                        } catch (Exception ignored) {
                        }
                        markDirty();
                        render(false);
                        Extensions.showMessage(this, getString(R.string.arsc_updated));
                    } else {
                        Extensions.showMessage(this, getString(R.string.arsc_invalid_value, type.name()));
                    }
                }).show();
    }

    private void showPoolEdit(ArscData.PoolString ps) {
        EditText input = new EditText(this);
        input.setText(ps.text);
        input.setSingleLine(false);
        new MaterialAlertDialogBuilder(this)
                .setTitle(String.format(Locale.US, "String %04X", ps.index & 0xFFFF))
                .setView(UiFields.wrap(this, input, "Value", 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() == null ? "" : input.getText().toString();
                    if (data.setPoolString(ps.index, text)) {
                        markDirty();
                        render(false);
                        Extensions.showMessage(this, getString(R.string.arsc_updated));
                    } else {
                        Extensions.showMessage(this, getString(R.string.arsc_update_failed));
                    }
                }).show();
    }

    private void showSearchValueDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);

        com.google.android.material.textfield.TextInputLayout box = UiFields.box(this, "Search");
        MaterialAutoCompleteTextView query = new MaterialAutoCompleteTextView(this);
        query.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, loadHistory()));
        query.setThreshold(1);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(query, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        box.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU);
        root.addView(box);
        query.setOnClickListener(v -> query.showDropDown());
        query.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) query.showDropDown();
        });

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton rbString = new RadioButton(this);
        rbString.setText("string");
        rbString.setChecked(true);
        RadioButton rbInt = new RadioButton(this);
        rbInt.setText("integer");
        RadioButton rbHex = new RadioButton(this);
        rbHex.setText("hex");
        group.addView(rbString);
        group.addView(rbInt);
        group.addView(rbHex);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        groupParams.topMargin = dp(8);
        root.addView(group, groupParams);

        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.arsc_search_value))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.search), (d, w) -> {
                    String q = query.getText() == null ? "" : query.getText().toString();
                    if (q.trim().isEmpty()) {
                        Extensions.showMessage(this, getString(R.string.arsc_enter_search));
                        return;
                    }
                    int kind = rbInt.isChecked() ? 1 : (rbHex.isChecked() ? 2 : 0);
                    pushHistory(q);
                    runSimpleSearch(q, kind);
                }).show();
    }

    private void showSearchIdDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.arsc_hex_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.arsc_search_id))
                .setView(UiFields.wrap(this, input, "Hex ID", 16))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.go), (d, w) -> {
                    String hex = input.getText() == null ? "" : input.getText().toString();
                    ResourceEntry re = data == null ? null : data.findByHexId(hex);
                    if (re == null) {
                        Extensions.showMessage(this, getString(R.string.arsc_not_found));
                        return;
                    }
                    Entry e = data.defaultEntry(re);
                    if (e == null) {
                        try {
                            for (Entry x : re) {
                                if (x != null && !x.isNull()) {
                                    e = x;
                                    break;
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (e == null) {
                        Extensions.showMessage(this, getString(R.string.arsc_no_value));
                        return;
                    }
                    showEntryDetail(e, re);
                }).show();
    }

    private void runSimpleSearch(String query, int kind) {
        if (data == null) return;
        Extensions.showMessage(this, getString(R.string.arsc_searching));
        new Thread(() -> {
            List<ArscData.SimpleHit> hits = data.searchSimple(query, kind);
            runOnUiThread(() -> {
                push(new Results(query, kind, hits));
                Extensions.showMessage(this, getString(R.string.arsc_results_n, hits.size()));
            });
        }).start();
    }


    private List<String> loadHistory() {
        List<String> out = new ArrayList<>();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String raw = prefs.getString(HIST_KEY, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, null);
                if (s != null) out.add(s);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void saveHistory(List<String> hist) {
        try {
            JSONArray arr = new JSONArray();
            for (String s : hist) arr.put(s);
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(HIST_KEY, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void pushHistory(String q) {
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) return;
        List<String> hist = loadHistory();
        hist.remove(query);
        hist.add(0, query);
        while (hist.size() > 30) hist.remove(hist.size() - 1);
        saveHistory(hist);
    }


    void markDirty() {
        dirty = true;
        toolbar.setSubtitle(fileName + " *");
    }

    private void saveNow() {
        saveNow(null);
    }

    private void saveNow(Runnable onDone) {
        if (data == null) {
            if (onDone != null) onDone.run();
            return;
        }
        Extensions.showMessage(this, getString(R.string.arsc_saving));
        new Thread(() -> {
            try {
                data.save();
                runOnUiThread(() -> {
                    dirty = false;
                    if (data.apkFile != null) savedThisSession = true;
                    toolbar.setSubtitle(fileName);
                    render(false);
                    Extensions.showMessage(this, getString(R.string.arsc_saved));
                    if (onDone != null) onDone.run();
                });
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    void finishWithApkResult() {
        if (data != null && data.apkFile != null && savedThisSession && data.arscFile != null) {
            android.content.Intent result = new android.content.Intent();
            result.setData(android.net.Uri.fromFile(data.arscFile));
            setResult(757, result);
        }
        finish();
    }

    private void showMainMenu() {
        View anchor = toolbar.findViewById(R.id.arsc_menu_more);
        PopupMenu menu = anchor != null
                ? new PopupMenu(this, anchor)
                : new PopupMenu(this, toolbar, Gravity.END);
        menu.getMenu().add("Backup");
        menu.getMenu().add("Exit");
        menu.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals("Backup")) backupNow();
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
                runOnUiThread(() -> Extensions.showMessage(this, getString(R.string.arsc_backup_x, bak.getName())));
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
                .setTitle(getString(R.string.unsaved_changes))
                .setMessage(getString(R.string.arsc_save_before_exit))
                .setPositiveButton(getString(R.string.save), (d, w) -> saveNow(this::finishWithApkResult))
                .setNegativeButton(getString(R.string.discard), (d, w) -> finish())
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }


    class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.Holder> {
        private final List<Row> rows = new ArrayList<>();

        void setRows(List<Row> next) {
            rows.clear();
            if (next != null) rows.addAll(next);
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).kind;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TypedValue tv = new TypedValue();
            getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            if (viewType == Row.TYPE) {
                LinearLayout row = new LinearLayout(ArscSimpleEditorActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = dp(16);
                row.setPadding(pad, dp(14), pad, dp(14));
                TextView id = new TextView(ArscSimpleEditorActivity.this);
                id.setTextSize(15);
                id.setTypeface(Typeface.MONOSPACE);
                id.setMinWidth(dp(64));
                TextView name = new TextView(ArscSimpleEditorActivity.this);
                name.setTextSize(16);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(np);
                np.leftMargin = dp(12);
                row.addView(id);
                row.addView(name, np);
                row.setBackgroundResource(tv.resourceId);
                return new Holder(row, id, name, null);
            }
            if (viewType == Row.ENTRY) {
                LinearLayout row = new LinearLayout(ArscSimpleEditorActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = dp(16);
                row.setPadding(pad, dp(10), pad, dp(10));
                TextView id = new TextView(ArscSimpleEditorActivity.this);
                id.setTextSize(15);
                id.setTypeface(Typeface.MONOSPACE);
                id.setMinWidth(dp(56));
                LinearLayout col = new LinearLayout(ArscSimpleEditorActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                TextView name = new TextView(ArscSimpleEditorActivity.this);
                name.setTextSize(15);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                TextView value = new TextView(ArscSimpleEditorActivity.this);
                value.setTextSize(13);
                value.setSingleLine(true);
                value.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                row.setLayoutParams(cp);
                col.addView(name);
                col.addView(value);
                cp.leftMargin = dp(12);
                row.addView(id);
                row.addView(col, cp);
                row.setBackgroundResource(tv.resourceId);
                return new Holder(row, id, name, value);
            }
            TextView text = new TextView(ArscSimpleEditorActivity.this);
            text.setTextSize(16);
            int pad = dp(16);
            text.setPadding(pad, dp(14), pad, dp(14));
            text.setSingleLine(true);
            text.setEllipsize(android.text.TextUtils.TruncateAt.END);
            text.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            text.setBackgroundResource(tv.resourceId);
            return new Holder(text, null, text, null);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = rows.get(position);
            if (row.kind == Row.TYPE) {
                holder.id.setText(row.left == null ? "" : row.left);
                holder.name.setText(row.line1 == null ? "" : row.line1);
            } else if (row.kind == Row.ENTRY) {
                holder.id.setText(row.left == null ? "" : row.left);
                holder.name.setText(row.line1 == null ? "" : row.line1);
                if (holder.value != null) {
                    if (row.line2 == null) {
                        holder.value.setVisibility(View.GONE);
                    } else {
                        holder.value.setVisibility(View.VISIBLE);
                        holder.value.setText(row.line2);
                    }
                }
            } else {
                holder.name.setText(row.line1 == null ? "" : row.line1);
            }
            holder.itemView.setOnClickListener(v -> onRowClick(row));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView id;
            final TextView name;
            final TextView value;
            Holder(@NonNull View itemView, TextView id, TextView name, TextView value) {
                super(itemView);
                this.id = id;
                this.name = name;
                this.value = value;
            }
        }
    }

}

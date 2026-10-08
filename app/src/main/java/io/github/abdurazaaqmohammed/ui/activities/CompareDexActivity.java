package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.features.dex.DexCompareEngine;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.SearchHistoryDropdown;
import io.github.abdurazaaqmohammed.utils.SearchHistoryHelper;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Compares two sets of APK/DEX files at the smali level. The first screen lists
 * the classes that were added, removed or changed; tapping one opens a side by
 * side or unified diff of its smali.
 */
public class CompareDexActivity extends BaseActivity {

    public static final String EXTRA_FILES1 = "files1";
    public static final String EXTRA_FILES2 = "files2";
    public static final String EXTRA_NAMES1 = "names1";
    public static final String EXTRA_NAMES2 = "names2";
    public static final String EXTRA_IGNORE_DEBUG = "ignore_debug_info";
    public static final String EXTRA_IGNORE_OPTIMIZATIONS = "ignore_optimizations";
    public static final String EXTRA_IGNORE_REGISTERS = "ignore_register_count";
    public static final String EXTRA_IGNORE_NOP = "ignore_nop_instructions";

    private static final int COLOR_ADDED = 0xFF4CAF50;
    private static final int COLOR_REMOVED = 0xFFF44336;
    private static final int BG_DELETED = 0x33F44336;
    private static final int BG_INSERTED = 0x3334A853;

    /** How whitespace and case are treated while matching lines. */
    private enum IgnoreMode {NONE, TRIM, WHITESPACE, WHITESPACE_EMPTY}

    /** Layout used by the diff view. */
    private enum ViewMode {AUTO, SIDE_BY_SIDE, UNIFIED}

    private final DexCompareEngine engine = new DexCompareEngine();
    private final List<DexCompareEngine.ComparedClass> allClasses = new ArrayList<>();
    private final List<DexCompareEngine.ComparedClass> shownClasses = new ArrayList<>();
    private final List<DiffLine> diffLines = new ArrayList<>();
    /** Positions inside {@link #diffLines} that are actual differences. */
    private final List<Integer> diffPositions = new ArrayList<>();
    private DexCompareEngine.Options compareOptions = new DexCompareEngine.Options();

    private boolean hideAdded;
    private boolean hideRemoved;
    private boolean hideChanged;
    private IgnoreMode ignoreMode = IgnoreMode.NONE;
    private boolean matchCase = true;
    private ViewMode viewMode = ViewMode.SIDE_BY_SIDE;

    private boolean diffScreen;
    private String currentType;
    private int lastSearchIndex = -1;

    private MaterialToolbar toolbar;
    private View listContainer;
    private View diffContainer;
    private TextView summaryView;
    private ProgressBar progressBar;
    private RecyclerView classListView;
    private RecyclerView diffListView;
    private TreeAdapter treeAdapter;
    private DiffAdapter diffAdapter;
    private View searchBar;
    private EditText searchInput;
    private String activeSearchQuery = "";
    private String names1 = "";
    private String names2 = "";

    public static Intent intent(Context context, List<File> files1, List<File> files2) {
        ArrayList<String> paths1 = new ArrayList<>();
        ArrayList<String> paths2 = new ArrayList<>();
        ArrayList<String> names1 = new ArrayList<>();
        ArrayList<String> names2 = new ArrayList<>();
        for (File file : files1) {
            paths1.add(file.getAbsolutePath());
            names1.add(file.getName());
        }
        for (File file : files2) {
            paths2.add(file.getAbsolutePath());
            names2.add(file.getName());
        }
        return new Intent(context, CompareDexActivity.class)
                .putStringArrayListExtra(EXTRA_FILES1, paths1)
                .putStringArrayListExtra(EXTRA_FILES2, paths2)
                .putStringArrayListExtra(EXTRA_NAMES1, names1)
                .putStringArrayListExtra(EXTRA_NAMES2, names2);
    }

    /** Same as {@link #intent(Context, List, List)} plus the ignore options. */
    public static Intent intent(Context context, List<File> files1, List<File> files2,
                                DexCompareEngine.Options options) {
        Intent intent = intent(context, files1, files2);
        if (options != null) {
            intent.putExtra(EXTRA_IGNORE_DEBUG, options.ignoreDebugInfo)
                    .putExtra(EXTRA_IGNORE_OPTIMIZATIONS, options.ignoreOptimizations)
                    .putExtra(EXTRA_IGNORE_REGISTERS, options.ignoreRegisterCount)
                    .putExtra(EXTRA_IGNORE_NOP, options.ignoreNopInstructions);
        }
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare_dex);

        toolbar = findViewById(R.id.compare_dex_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        }
        toolbar.setNavigationOnClickListener(v -> handleBack());

        listContainer = findViewById(R.id.dex_list_container);
        diffContainer = findViewById(R.id.dex_diff_container);
        summaryView = findViewById(R.id.dex_list_summary);
        progressBar = findViewById(R.id.dex_compare_progress);
        classListView = findViewById(R.id.dex_class_list);
        diffListView = findViewById(R.id.dex_diff_list);
        searchBar = findViewById(R.id.dex_search_bar);
        searchInput = findViewById(R.id.dex_search_input);

        compareOptions = new DexCompareEngine.Options(
                getIntent().getBooleanExtra(EXTRA_IGNORE_DEBUG, false),
                getIntent().getBooleanExtra(EXTRA_IGNORE_OPTIMIZATIONS, false),
                getIntent().getBooleanExtra(EXTRA_IGNORE_REGISTERS, false),
                getIntent().getBooleanExtra(EXTRA_IGNORE_NOP, false));
        engine.setOptions(compareOptions);

        treeAdapter = new TreeAdapter();
        classListView.setLayoutManager(new LinearLayoutManager(this));
        classListView.setAdapter(treeAdapter);

        diffAdapter = new DiffAdapter();
        diffListView.setLayoutManager(new LinearLayoutManager(this));
        diffListView.setAdapter(diffAdapter);

        setupSearchBar();

        names1 = joinNames(getIntent().getStringArrayListExtra(EXTRA_NAMES1));
        names2 = joinNames(getIntent().getStringArrayListExtra(EXTRA_NAMES2));
        updateListSubtitle();
        startCompare();
    }

    // ==========================================
    // Bottom search bar
    // ==========================================

    private void setupSearchBar() {
        findViewById(R.id.dex_search_next).setOnClickListener(v -> stepSearch(1));
        findViewById(R.id.dex_search_prev).setOnClickListener(v -> stepSearch(-1));
        findViewById(R.id.dex_search_close).setOnClickListener(v -> closeSearchBar());
        findViewById(R.id.dex_search_history).setOnClickListener(v -> showSearchHistory(v));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            stepSearch(1);
            return true;
        });
    }

    /** Shows the search bar, pre-filled with the last searched string. */
    private void openSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        List<SearchHistoryHelper.Item> history =
                SearchHistoryHelper.load(this, SearchHistoryHelper.KEY_COMPARE_DEX);
        String last = history.isEmpty() ? "" : history.get(0).query;
        searchInput.setText(last);
        searchInput.setSelection(last.length());
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void closeSearchBar() {
        searchBar.setVisibility(View.GONE);
        lastSearchIndex = -1;
        activeSearchQuery = "";
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
    }

    private void showSearchHistory(View anchor) {
        List<SearchHistoryHelper.Item> history =
                SearchHistoryHelper.load(this, SearchHistoryHelper.KEY_COMPARE_DEX);
        if (history.isEmpty()) {
            Extensions.showMessage(this, R.string.history);
            return;
        }
        SearchHistoryDropdown.show(this, anchor, history, new SearchHistoryDropdown.Listener() {
            @Override
            public void onSelect(String query) {
                searchInput.setText(query);
                searchInput.setSelection(query.length());
                stepSearch(1);
            }

            @Override
            public void onChanged(List<SearchHistoryHelper.Item> items) {
                SearchHistoryHelper.save(CompareDexActivity.this,
                        SearchHistoryHelper.KEY_COMPARE_DEX, items);
            }
        });
    }

    /** Moves to the next ({@code direction} &gt; 0) or previous match, wrapping around. */
    private void stepSearch(int direction) {
        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (TextUtils.isEmpty(query) || diffLines.isEmpty()) {
            Extensions.showMessage(this, R.string.no_matches_found);
            return;
        }
        if (!query.equals(activeSearchQuery)) {
            activeSearchQuery = query;
            lastSearchIndex = direction > 0 ? -1 : diffLines.size();
            SearchHistoryHelper.push(this, SearchHistoryHelper.KEY_COMPARE_DEX, query);
        }
        String needle = matchCase ? query : query.toLowerCase(Locale.ROOT);
        int size = diffLines.size();
        int found = -1;
        for (int offset = 0; offset < size; offset++) {
            int index = direction > 0
                    ? Math.floorMod(lastSearchIndex + 1 + offset, size)
                    : Math.floorMod(lastSearchIndex - 1 - offset, size);
            DiffLine line = diffLines.get(index);
            if (contains(line.oldLine, needle) || contains(line.newLine, needle)) {
                found = index;
                break;
            }
        }
        if (found < 0) {
            Extensions.showMessage(this, R.string.no_matches_found);
            return;
        }
        lastSearchIndex = found;
        scrollToDiffLine(found);
    }

    private static String joinNames(List<String> names) {
        if (names == null || names.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(name);
        }
        return sb.toString();
    }

    private void updateListSubtitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) return;
        actionBar.setTitle(R.string.compare_dex);
        String subtitle = names1;
        if (names2 != null && !names2.isEmpty()) subtitle = names1 + "  ⟷  " + names2;
        actionBar.setSubtitle(subtitle);
    }

    // ==========================================
    // Loading
    // ==========================================

    private void startCompare() {
        List<String> paths1 = getIntent().getStringArrayListExtra(EXTRA_FILES1);
        List<String> paths2 = getIntent().getStringArrayListExtra(EXTRA_FILES2);
        if (paths1 == null || paths1.isEmpty() || paths2 == null || paths2.isEmpty()) {
            Extensions.showMessage(this, R.string.dex_no_classes_selected);
            finish();
            return;
        }
        summaryView.setText(R.string.dex_comparing);
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                File cacheDir = new File(getCacheDir(), "dexcompare");
                // both sides are independent; load them concurrently
                Exception[] loadError = new Exception[1];
                Thread leftThread = new Thread(() -> {
                    try {
                        engine.loadLeft(toFiles(paths1), cacheDir, (done, total) ->
                                runOnUiThread(() ->
                                        summaryView.setText(getString(R.string.dex_comparing_loading, names1))));
                    } catch (Exception e) {
                        loadError[0] = e;
                    }
                });
                Thread rightThread = new Thread(() -> {
                    try {
                        engine.loadRight(toFiles(paths2), cacheDir, (done, total) ->
                                runOnUiThread(() ->
                                        summaryView.setText(getString(R.string.dex_comparing_loading, names2))));
                    } catch (Exception e) {
                        loadError[0] = e;
                    }
                });
                leftThread.start();
                rightThread.start();
                leftThread.join();
                rightThread.join();
                if (loadError[0] != null) throw loadError[0];
                List<DexCompareEngine.ComparedClass> differences = engine.computeDifferences((done, total) ->
                        runOnUiThread(() -> summaryView.setText(getString(R.string.dex_compare_progress,
                                total <= 0 ? 100 : done * 100 / total))));
                runOnUiThread(() -> {
                    allClasses.clear();
                    allClasses.addAll(differences);
                    applyFilter();
                    progressBar.setVisibility(View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    new ErrorUtil(CompareDexActivity.this).showError(e);
                });
            }
        }).start();
    }

    private static List<File> toFiles(List<String> paths) {
        List<File> files = new ArrayList<>();
        for (String path : paths) files.add(new File(path));
        return files;
    }

    private void applyFilter() {
        shownClasses.clear();
        int added = 0, removed = 0, changed = 0;
        for (DexCompareEngine.ComparedClass item : allClasses) {
            switch (item.status) {
                case ADDED -> added++;
                case REMOVED -> removed++;
                case CHANGED -> changed++;
            }
            if (item.status == DexCompareEngine.Status.ADDED && hideAdded) continue;
            if (item.status == DexCompareEngine.Status.REMOVED && hideRemoved) continue;
            if (item.status == DexCompareEngine.Status.CHANGED && hideChanged) continue;
            shownClasses.add(item);
        }
        if (allClasses.isEmpty()) {
            summaryView.setText(R.string.no_differences_found);
        } else {
            summaryView.setText(getString(R.string.dex_compare_summary, added, removed, changed));
        }
        buildTree();
        treeAdapter.notifyDataSetChanged();
    }

    // ==========================================
    // Diff view
    // ==========================================

    private void openDiff(DexCompareEngine.ComparedClass item) {
        currentType = item.type;
        diffScreen = true;
        lastSearchIndex = -1;
        activeSearchQuery = "";
        closeSearchBar();
        listContainer.setVisibility(View.GONE);
        diffContainer.setVisibility(View.VISIBLE);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(item.displayName());
            actionBar.setSubtitle(null);
        }
        invalidateOptionsMenu();
        rebuildDiff();
    }

    private void rebuildDiff() {
        if (currentType == null) return;
        progressBar.setVisibility(View.VISIBLE);
        diffListView.setVisibility(View.GONE);
        final String type = currentType;
        new Thread(() -> {
            try {
                String left = engine.smaliLeft(type);
                String right = engine.smaliRight(type);
                final List<DiffLine> built = buildDiff(left, right);
                runOnUiThread(() -> {
                    if (!type.equals(currentType)) return;
                    diffLines.clear();
                    diffLines.addAll(built);
                    diffPositions.clear();
                    for (int i = 0; i < diffLines.size(); i++) {
                        if (diffLines.get(i).tag != DiffRow.Tag.EQUAL) diffPositions.add(i);
                    }
                    diffAdapter.setUnified(isUnified());
                    diffAdapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    diffListView.setVisibility(View.VISIBLE);
                    lastSearchIndex = -1;
                    activeSearchQuery = "";
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    new ErrorUtil(CompareDexActivity.this).showError(e);
                });
            }
        }).start();
    }

    private boolean isUnified() {
        if (viewMode == ViewMode.UNIFIED) return true;
        if (viewMode == ViewMode.SIDE_BY_SIDE) return false;
        return getResources().getConfiguration().screenWidthDp < 600;
    }

    private List<DiffLine> buildDiff(String left, String right) {
        List<String> oldLines = splitLines(left);
        List<String> newLines = splitLines(right);
        boolean dropBlanks = ignoreMode == IgnoreMode.WHITESPACE_EMPTY;
        // Keep the original 1-based numbers of every kept line so ignored lines
        // (debug info, register counts, nops, blanks) don't shift the numbering.
        int[] oldNumbers = new int[oldLines.size() + 1];
        int[] newNumbers = new int[newLines.size() + 1];
        List<String> oldForDiff = significantLines(oldLines, oldNumbers, dropBlanks);
        List<String> newForDiff = significantLines(newLines, newNumbers, dropBlanks);
        DiffRowGenerator generator = DiffRowGenerator.create()
                .showInlineDiffs(false)
                .reportLinesUnchanged(true)
                .equalizer(equalizer())
                .build();
        List<DiffRow> rows = generator.generateDiffRows(oldForDiff, newForDiff);

        List<DiffLine> result = new ArrayList<>(rows.size());
        int oldIndex = 0;
        int newIndex = 0;
        for (DiffRow row : rows) {
            DiffRow.Tag tag = row.getTag();
            int oldNum = 0;
            int newNum = 0;
            switch (tag) {
                case EQUAL -> {
                    oldNum = oldNumbers[oldIndex];
                    newNum = newNumbers[newIndex];
                    oldIndex++;
                    newIndex++;
                }
                case CHANGE -> {
                    oldNum = oldNumbers[oldIndex];
                    newNum = newNumbers[newIndex];
                    oldIndex++;
                    newIndex++;
                }
                case DELETE -> {
                    oldNum = oldNumbers[oldIndex];
                    oldIndex++;
                }
                case INSERT -> {
                    newNum = newNumbers[newIndex];
                    newIndex++;
                }
            }
            result.add(new DiffLine(tag, row.getOldLine(), row.getNewLine(), oldNum, newNum));
        }
        return result;
    }

    /** Keeps the lines the compare options/blank mode don't ignore, recording their original numbers. */
    private List<String> significantLines(List<String> lines, int[] numbers, boolean dropBlanks) {
        List<String> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (compareOptions.ignorable(line)) continue;
            if (dropBlanks && line.trim().isEmpty()) continue;
            numbers[out.size()] = i + 1;
            out.add(line);
        }
        return out;
    }

    private BiPredicate<String, String> equalizer() {
        return (a, b) -> {
            if (a.equals(b)) return true;
            if (compareOptions.any()) {
                boolean ignoredA = compareOptions.ignorable(a);
                boolean ignoredB = compareOptions.ignorable(b);
                if (ignoredA || ignoredB) return ignoredA && ignoredB;
                if (compareOptions.ignoreOptimizations
                        && compareOptions.canonicalize(a).equals(compareOptions.canonicalize(b))) {
                    return true;
                }
            }
            String x = a;
            String y = b;
            switch (ignoreMode) {
                case TRIM -> {
                    x = x.trim();
                    y = y.trim();
                }
                case WHITESPACE, WHITESPACE_EMPTY -> {
                    x = stripWhitespace(x);
                    y = stripWhitespace(y);
                }
                default -> {
                }
            }
            if (!matchCase) {
                x = x.toLowerCase(Locale.ROOT);
                y = y.toLowerCase(Locale.ROOT);
            }
            return x.equals(y);
        };
    }

    private static String stripWhitespace(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static List<String> splitLines(String text) {
        if (text == null) text = "";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        // Drop the single trailing empty line produced by a final newline.
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    // ==========================================
    // Search / jump
    // ==========================================

    private boolean contains(String text, String needle) {
        if (text == null) return false;
        return (matchCase ? text : text.toLowerCase(Locale.ROOT)).contains(needle);
    }

    private void showJumpDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("1…" + diffLines.size());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.jump_to_line)
                .setView(input)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            CharSequence text = input.getText();
            if (TextUtils.isEmpty(text)) {
                input.setError(getString(R.string.enter_line_to_jump_to));
                return;
            }
            try {
                int line = Integer.parseInt(text.toString().trim());
                if (line < 1 || line > diffLines.size()) throw new NumberFormatException();
                scrollToDiffLine(line - 1);
                dialog.dismiss();
            } catch (NumberFormatException e) {
                input.setError(getString(R.string.value_is_out_of_range));
            }
        });
    }

    private void scrollToDiffLine(int index) {
        if (index < 0 || index >= diffLines.size()) return;
        if (diffListView.getLayoutManager() instanceof LinearLayoutManager lm) {
            lm.scrollToPositionWithOffset(index, 0);
        } else {
            diffListView.scrollToPosition(index);
        }
    }

    // ==========================================
    // Difference navigation (app bar arrows)
    // ==========================================

    /** Scrolls to the first difference below the top of the view, wrapping to the start. */
    private void gotoNextDifference() {
        if (diffPositions.isEmpty()) return;
        int first = firstVisibleDiffLine();
        for (int position : diffPositions) {
            if (position > first) {
                scrollToDiffLine(position);
                return;
            }
        }
        scrollToDiffLine(diffPositions.get(0));
    }

    /** Scrolls to the last difference above the top of the view, wrapping to the end. */
    private void gotoPreviousDifference() {
        if (diffPositions.isEmpty()) return;
        int first = firstVisibleDiffLine();
        for (int i = diffPositions.size() - 1; i >= 0; i--) {
            int position = diffPositions.get(i);
            if (position < first) {
                scrollToDiffLine(position);
                return;
            }
        }
        scrollToDiffLine(diffPositions.get(diffPositions.size() - 1));
    }

    private int firstVisibleDiffLine() {
        if (diffListView.getLayoutManager() instanceof LinearLayoutManager lm) {
            return lm.findFirstVisibleItemPosition();
        }
        return 0;
    }

    // ==========================================
    // Navigation / menu
    // ==========================================

    private void handleBack() {
        if (diffScreen) {
            diffScreen = false;
            closeSearchBar();
            diffContainer.setVisibility(View.GONE);
            listContainer.setVisibility(View.VISIBLE);
            updateListSubtitle();
            invalidateOptionsMenu();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(diffScreen ? R.menu.menu_compare_dex_diff : R.menu.menu_compare_dex_list, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (diffScreen) {
            MenuItem auto = menu.findItem(R.id.action_view_auto);
            MenuItem side = menu.findItem(R.id.action_view_side);
            MenuItem unified = menu.findItem(R.id.action_view_unified);
            if (auto != null) auto.setChecked(viewMode == ViewMode.AUTO);
            if (side != null) side.setChecked(viewMode == ViewMode.SIDE_BY_SIDE);
            if (unified != null) unified.setChecked(viewMode == ViewMode.UNIFIED);

            MenuItem ignoreNone = menu.findItem(R.id.action_ignore_none);
            MenuItem ignoreTrim = menu.findItem(R.id.action_ignore_trim);
            MenuItem ignoreWs = menu.findItem(R.id.action_ignore_whitespace);
            MenuItem ignoreWsEmpty = menu.findItem(R.id.action_ignore_whitespace_empty);
            if (ignoreNone != null) ignoreNone.setChecked(ignoreMode == IgnoreMode.NONE);
            if (ignoreTrim != null) ignoreTrim.setChecked(ignoreMode == IgnoreMode.TRIM);
            if (ignoreWs != null) ignoreWs.setChecked(ignoreMode == IgnoreMode.WHITESPACE);
            if (ignoreWsEmpty != null) ignoreWsEmpty.setChecked(ignoreMode == IgnoreMode.WHITESPACE_EMPTY);

            MenuItem match = menu.findItem(R.id.action_match_case);
            if (match != null) match.setChecked(matchCase);

            MenuItem prevDiff = menu.findItem(R.id.action_prev_diff);
            MenuItem nextDiff = menu.findItem(R.id.action_next_diff);
            if (prevDiff != null) prevDiff.setEnabled(!diffPositions.isEmpty());
            if (nextDiff != null) nextDiff.setEnabled(!diffPositions.isEmpty());
        } else {
            MenuItem added = menu.findItem(R.id.action_hide_added);
            MenuItem removed = menu.findItem(R.id.action_hide_removed);
            MenuItem changed = menu.findItem(R.id.action_hide_changed);
            if (added != null) added.setChecked(hideAdded);
            if (removed != null) removed.setChecked(hideRemoved);
            if (changed != null) changed.setChecked(hideChanged);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            handleBack();
            return true;
        }
        if (id == R.id.action_compare_dex_exit) {
            finish();
            return true;
        }
        if (id == R.id.action_hide_added) {
            hideAdded = !hideAdded;
            item.setChecked(hideAdded);
            applyFilter();
            return true;
        }
        if (id == R.id.action_hide_removed) {
            hideRemoved = !hideRemoved;
            item.setChecked(hideRemoved);
            applyFilter();
            return true;
        }
        if (id == R.id.action_hide_changed) {
            hideChanged = !hideChanged;
            item.setChecked(hideChanged);
            applyFilter();
            return true;
        }
        if (id == R.id.action_view_auto) {
            item.setChecked(true);
            setViewMode(ViewMode.AUTO);
            return true;
        }
        if (id == R.id.action_view_side) {
            item.setChecked(true);
            setViewMode(ViewMode.SIDE_BY_SIDE);
            return true;
        }
        if (id == R.id.action_view_unified) {
            item.setChecked(true);
            setViewMode(ViewMode.UNIFIED);
            return true;
        }
        if (id == R.id.action_ignore_none) {
            item.setChecked(true);
            setIgnoreMode(IgnoreMode.NONE);
            return true;
        }
        if (id == R.id.action_ignore_trim) {
            item.setChecked(true);
            setIgnoreMode(IgnoreMode.TRIM);
            return true;
        }
        if (id == R.id.action_ignore_whitespace) {
            item.setChecked(true);
            setIgnoreMode(IgnoreMode.WHITESPACE);
            return true;
        }
        if (id == R.id.action_ignore_whitespace_empty) {
            item.setChecked(true);
            setIgnoreMode(IgnoreMode.WHITESPACE_EMPTY);
            return true;
        }
        if (id == R.id.action_match_case) {
            matchCase = !matchCase;
            item.setChecked(matchCase);
            rebuildDiff();
            return true;
        }
        if (id == R.id.action_diff_search) {
            openSearchBar();
            return true;
        }
        if (id == R.id.action_diff_jump) {
            showJumpDialog();
            return true;
        }
        if (id == R.id.action_prev_diff) {
            gotoPreviousDifference();
            return true;
        }
        if (id == R.id.action_next_diff) {
            gotoNextDifference();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setViewMode(ViewMode mode) {
        viewMode = mode;
        diffAdapter.setUnified(isUnified());
        diffAdapter.notifyDataSetChanged();
        invalidateOptionsMenu();
    }

    private void setIgnoreMode(IgnoreMode mode) {
        ignoreMode = mode;
        invalidateOptionsMenu();
        rebuildDiff();
    }

    // ==========================================
    // Adapters
    // ==========================================

    private static final class DiffLine {
        final DiffRow.Tag tag;
        final String oldLine;
        final String newLine;
        final int oldNumber;
        final int newNumber;

        DiffLine(DiffRow.Tag tag, String oldLine, String newLine, int oldNumber, int newNumber) {
            this.tag = tag;
            this.oldLine = oldLine;
            this.newLine = newLine;
            this.oldNumber = oldNumber;
            this.newNumber = newNumber;
        }
    }

    // ==========================================
    // Class tree (folders for the class path)
    // ==========================================

    /** One row of the tree: either a package folder or a compared class. */
    private static final class Node {
        String name;
        boolean dir;
        boolean expanded;
        int depth;
        Node parent;
        DexCompareEngine.ComparedClass item;
        List<Node> children = new ArrayList<>();
    }

    private final List<Node> treeRoots = new ArrayList<>();
    private final List<Node> visibleNodes = new ArrayList<>();

    /** Rebuilds the collapsible folder tree from the classes that pass the filter. */
    private void buildTree() {
        Map<String, Node> all = new HashMap<>();
        treeRoots.clear();
        for (DexCompareEngine.ComparedClass item : shownClasses) {
            String[] parts = item.displayName().split("/");
            Node parent = null;
            String path = "";
            for (int i = 0; i < parts.length; i++) {
                boolean last = i == parts.length - 1;
                path = path.isEmpty() ? parts[i] : path + "/" + parts[i];
                Node node = all.get(path);
                if (node == null) {
                    node = new Node();
                    node.name = parts[i];
                    node.dir = !last;
                    all.put(path, node);
                    if (parent == null) {
                        treeRoots.add(node);
                    } else {
                        node.parent = parent;
                        parent.children.add(node);
                    }
                } else if (!last) {
                    node.dir = true;
                }
                parent = node;
            }
            Node leaf = all.get(path);
            if (leaf != null && !leaf.dir) leaf.item = item;
        }
        sortNodes(treeRoots);
        compactTree(treeRoots);
        visibleNodes.clear();
        addVisibleNodes(treeRoots, 0);
    }

    /** Same ordering as the DexEditor/ARSC trees: folders first, then case-insensitive. */
    private static void sortNodes(List<Node> nodes) {
        Collections.sort(nodes, (a, b) -> {
            if (a.dir != b.dir) return a.dir ? -1 : 1;
            return a.name.compareToIgnoreCase(b.name);
        });
        for (Node node : nodes) sortNodes(node.children);
    }

    /** Merges chains of single folders (a/b/c → a.b.c) like the DexEditor tree does. */
    private static void compactTree(List<Node> nodes) {
        for (Node node : nodes) {
            if (!node.dir) continue;
            List<Node> children = node.children;
            while (children.size() == 1 && children.get(0).dir) {
                Node single = children.get(0);
                node.name = node.name + "." + single.name;
                node.children = new ArrayList<>(single.children);
                for (Node child : node.children) child.parent = node;
                children = node.children;
            }
            compactTree(children);
        }
    }

    private void addVisibleNodes(List<Node> nodes, int depth) {
        for (Node node : nodes) {
            node.depth = depth;
            visibleNodes.add(node);
            if (node.dir && node.expanded) addVisibleNodes(node.children, depth + 1);
        }
    }

    private static void collectVisible(List<Node> nodes, int depth, List<Node> into) {
        for (Node node : nodes) {
            node.depth = depth;
            into.add(node);
            if (node.dir && node.expanded) collectVisible(node.children, depth + 1, into);
        }
    }

    private static boolean isDescendant(Node parent, Node node) {
        Node current = node.parent;
        while (current != null) {
            if (current == parent) return true;
            current = current.parent;
        }
        return false;
    }

    private void toggleNode(Node node, int position) {
        node.expanded = !node.expanded;
        if (node.expanded) {
            List<Node> added = new ArrayList<>();
            collectVisible(node.children, node.depth + 1, added);
            if (!added.isEmpty()) {
                visibleNodes.addAll(position + 1, added);
                treeAdapter.notifyItemRangeInserted(position + 1, added.size());
            }
        } else {
            int removed = 0;
            int next = position + 1;
            while (next < visibleNodes.size() && isDescendant(node, visibleNodes.get(next))) {
                visibleNodes.remove(next);
                removed++;
            }
            if (removed > 0) treeAdapter.notifyItemRangeRemoved(position + 1, removed);
        }
        treeAdapter.notifyItemChanged(position);
    }

    /** Folder/class list in the DexEditor style: collapsible folders for the class path. */
    private final class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.Holder> {

        private static final int TYPE_DIR = 0;
        private static final int TYPE_CLASS = 1;

        private GradientDrawable folderBg;
        private GradientDrawable classBg;

        private void ensureDrawables(float density) {
            if (folderBg == null) {
                folderBg = new GradientDrawable();
                folderBg.setCornerRadius(5 * density);
                folderBg.setColor(0xFF252525);
            }
            if (classBg == null) {
                classBg = new GradientDrawable();
                classBg.setCornerRadius(100 * density);
                classBg.setColor(0xFF3860AF);
            }
        }

        class Holder extends RecyclerView.ViewHolder {
            final View row;
            final View arrow;
            final View iconBg;
            final ImageView icon;
            final TextView symbol;
            final TextView name;

            Holder(View view) {
                super(view);
                row = view;
                arrow = view.findViewById(R.id.node_arrow);
                iconBg = view.findViewById(R.id.node_icon_bg);
                icon = view.findViewById(R.id.node_icon);
                symbol = view.findViewById(R.id.node_symbol);
                name = view.findViewById(R.id.node_name);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return visibleNodes.get(position).dir ? TYPE_DIR : TYPE_CLASS;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_dex_compare_node, parent, false);
            Holder holder = new Holder(view);
            holder.itemView.setOnClickListener(v -> {
                int position = holder.getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                Node node = visibleNodes.get(position);
                if (node.dir) {
                    toggleNode(node, position);
                } else if (node.item != null) {
                    openDiff(node.item);
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Node node = visibleNodes.get(position);
            float density = getResources().getDisplayMetrics().density;
            ensureDrawables(density);

            int left = (int) ((4 + node.depth * 24) * density);
            int vert = (int) (4 * density);
            holder.row.setPadding(left, vert, (int) (16 * density), vert);

            if (node.dir) {
                holder.arrow.setVisibility(View.VISIBLE);
                holder.arrow.setRotation(node.expanded ? 45f : 0f);
                holder.icon.setVisibility(View.VISIBLE);
                holder.icon.setImageResource(R.drawable.ic_folder_mt);
                holder.symbol.setVisibility(View.GONE);
                holder.iconBg.setVisibility(View.VISIBLE);
                holder.iconBg.setBackground(folderBg);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    holder.iconBg.setElevation(5 * density);
                }
                holder.name.setTextColor(MaterialColors.getColor(
                        CompareDexActivity.this, com.google.android.material.R.attr.colorOnSurface,
                        0xFFFFFFFF));
                holder.name.setText(node.name);
                return;
            }

            holder.arrow.setVisibility(View.GONE);
            holder.icon.setVisibility(View.GONE);
            holder.symbol.setVisibility(View.VISIBLE);
            holder.symbol.setText("C");
            holder.symbol.setTypeface(Typeface.MONOSPACE);
            holder.iconBg.setVisibility(View.VISIBLE);
            holder.iconBg.setBackground(classBg);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.iconBg.setElevation(5 * density);
            }
            int color;
            if (node.item != null && node.item.status == DexCompareEngine.Status.ADDED) {
                color = COLOR_ADDED;
            } else if (node.item != null && node.item.status == DexCompareEngine.Status.REMOVED) {
                color = COLOR_REMOVED;
            } else {
                color = MaterialColors.getColor(CompareDexActivity.this,
                        com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF);
            }
            holder.name.setTextColor(color);
            holder.name.setText(node.name);
        }

        @Override
        public int getItemCount() {
            return visibleNodes.size();
        }
    }

    private final class DiffAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_SIDE = 0;
        private static final int TYPE_UNIFIED = 1;
        private boolean unified;

        void setUnified(boolean unified) {
            this.unified = unified;
        }

        @Override
        public int getItemViewType(int position) {
            return unified ? TYPE_UNIFIED : TYPE_SIDE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_UNIFIED) {
                return new UnifiedHolder(inflater.inflate(R.layout.item_dex_diff_unified, parent, false));
            }
            return new SideHolder(inflater.inflate(R.layout.item_dex_diff_side, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DiffLine line = diffLines.get(position);
            if (holder instanceof SideHolder side) {
                side.numOld.setText(line.oldNumber == 0 ? "" : String.valueOf(line.oldNumber));
                side.numNew.setText(line.newNumber == 0 ? "" : String.valueOf(line.newNumber));
                side.old.setText(line.oldLine);
                side.newText.setText(line.newLine);
                side.old.setBackgroundColor(backgroundColor(line.tag, true));
                side.newText.setBackgroundColor(backgroundColor(line.tag, false));
            } else if (holder instanceof UnifiedHolder unifiedHolder) {
                bindUnified(unifiedHolder, line);
            }
        }

        private int backgroundColor(DiffRow.Tag tag, boolean oldSide) {
            switch (tag) {
                case DELETE -> {
                    return oldSide ? BG_DELETED : 0;
                }
                case INSERT -> {
                    return oldSide ? 0 : BG_INSERTED;
                }
                case CHANGE -> {
                    return oldSide ? BG_DELETED : BG_INSERTED;
                }
                default -> {
                    return 0;
                }
            }
        }

        private void bindUnified(UnifiedHolder holder, DiffLine line) {
            switch (line.tag) {
                case EQUAL -> {
                    showOld(holder, line, "", 0);
                    holder.rowNew.setVisibility(View.GONE);
                }
                case DELETE -> {
                    showOld(holder, line, "-", BG_DELETED);
                    holder.rowNew.setVisibility(View.GONE);
                }
                case INSERT -> {
                    holder.rowOld.setVisibility(View.GONE);
                    showNew(holder, line, "+", BG_INSERTED);
                }
                default -> {
                    showOld(holder, line, "-", BG_DELETED);
                    showNew(holder, line, "+", BG_INSERTED);
                }
            }
        }

        private void showOld(UnifiedHolder holder, DiffLine line, String marker, int background) {
            holder.rowOld.setVisibility(View.VISIBLE);
            holder.numOld.setText(line.oldNumber == 0 ? "" : String.valueOf(line.oldNumber));
            holder.markerOld.setText(marker);
            holder.textOld.setText(line.oldLine);
            holder.rowOld.setBackgroundColor(background);
        }

        private void showNew(UnifiedHolder holder, DiffLine line, String marker, int background) {
            holder.rowNew.setVisibility(View.VISIBLE);
            holder.numNew.setText(line.newNumber == 0 ? "" : String.valueOf(line.newNumber));
            holder.markerNew.setText(marker);
            holder.textNew.setText(line.newLine);
            holder.rowNew.setBackgroundColor(background);
        }

        @Override
        public int getItemCount() {
            return diffLines.size();
        }

        class SideHolder extends RecyclerView.ViewHolder {
            final TextView numOld;
            final TextView old;
            final TextView numNew;
            final TextView newText;

            SideHolder(View view) {
                super(view);
                numOld = view.findViewById(R.id.side_num_old);
                old = view.findViewById(R.id.side_old);
                numNew = view.findViewById(R.id.side_num_new);
                newText = view.findViewById(R.id.side_new);
            }
        }

        class UnifiedHolder extends RecyclerView.ViewHolder {
            final View rowOld;
            final View rowNew;
            final TextView numOld;
            final TextView markerOld;
            final TextView textOld;
            final TextView numNew;
            final TextView markerNew;
            final TextView textNew;

            UnifiedHolder(View view) {
                super(view);
                rowOld = view.findViewById(R.id.unified_row_old);
                rowNew = view.findViewById(R.id.unified_row_new);
                numOld = view.findViewById(R.id.unified_num_old);
                markerOld = view.findViewById(R.id.unified_marker_old);
                textOld = view.findViewById(R.id.unified_text_old);
                numNew = view.findViewById(R.id.unified_num_new);
                markerNew = view.findViewById(R.id.unified_marker_new);
                textNew = view.findViewById(R.id.unified_text_new);
            }
        }
    }
}

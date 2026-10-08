package io.github.abdurazaaqmohammed.ui.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.SearchHistoryDropdown;
import io.github.abdurazaaqmohammed.utils.SearchHistoryHelper;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * Compares two text files (or zip entries) line by line. It uses the same diff
 * UI as {@link CompareDexActivity}: line numbers, side by side / unified layout,
 * difference navigation, search and jump to line.
 */
public class CompareTextActivity extends BaseActivity {

    private static final int BG_DELETED = 0x33F44336;
    private static final int BG_INSERTED = 0x3334A853;

    /** How whitespace and case are treated while matching lines. */
    private enum IgnoreMode {NONE, TRIM, WHITESPACE, WHITESPACE_EMPTY}

    /** Layout used by the diff view. */
    private enum ViewMode {AUTO, SIDE_BY_SIDE, UNIFIED}

    private final List<DiffLine> diffLines = new ArrayList<>();
    /** Positions inside {@link #diffLines} that are actual differences. */
    private final List<Integer> diffPositions = new ArrayList<>();

    private IgnoreMode ignoreMode = IgnoreMode.NONE;
    private boolean matchCase = true;
    private ViewMode viewMode = ViewMode.SIDE_BY_SIDE;

    private int lastSearchIndex = -1;
    private String activeSearchQuery = "";
    private String names1 = "";
    private String names2 = "";
    /** Raw contents of both sides, kept so option changes rebuild without re-reading. */
    private String textLeft;
    private String textRight;

    private MaterialToolbar toolbar;
    private View diffContainer;
    private ProgressBar progressBar;
    private RecyclerView diffListView;
    private DiffAdapter diffAdapter;
    private View searchBar;
    private EditText searchInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare_text);

        toolbar = findViewById(R.id.compare_text_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        diffContainer = findViewById(R.id.text_diff_container);
        progressBar = findViewById(R.id.text_compare_progress);
        diffListView = findViewById(R.id.text_diff_list);
        searchBar = findViewById(R.id.text_search_bar);
        searchInput = findViewById(R.id.text_search_input);

        diffAdapter = new DiffAdapter();
        diffListView.setLayoutManager(new LinearLayoutManager(this));
        diffListView.setAdapter(diffAdapter);

        setupSearchBar();

        names1 = nameOf(getIntent().getStringExtra("file1"));
        names2 = nameOf(getIntent().getStringExtra("file2"));
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setTitle(R.string.compare_text);
            String subtitle = names1;
            if (names2 != null && !names2.isEmpty()) subtitle = names1 + "  ⟷  " + names2;
            bar.setSubtitle(subtitle);
        }
        startCompare();
    }

    /** The file or zip-entry name shown in the toolbar subtitle. */
    private static String nameOf(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    // ==========================================
    // Loading
    // ==========================================

    private void startCompare() {
        Intent intent = getIntent();
        String path1 = intent.getStringExtra("file1");
        String path2 = intent.getStringExtra("file2");
        boolean isZip1 = intent.getBooleanExtra("isZip1", false);
        boolean isZip2 = intent.getBooleanExtra("isZip2", false);
        String zip1 = intent.getStringExtra("zip1");
        String zip2 = intent.getStringExtra("zip2");

        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String left = readText(path1, isZip1, zip1);
                String right = readText(path2, isZip2, zip2);
                textLeft = left;
                textRight = right;
                final List<DiffLine> built = buildDiff(left, right);
                runOnUiThread(() -> {
                    diffLines.clear();
                    diffLines.addAll(built);
                    diffPositions.clear();
                    for (int i = 0; i < diffLines.size(); i++) {
                        if (diffLines.get(i).tag != DiffRow.Tag.EQUAL) diffPositions.add(i);
                    }
                    diffAdapter.setUnified(isUnified());
                    diffAdapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    diffContainer.setVisibility(View.VISIBLE);
                    lastSearchIndex = -1;
                    activeSearchQuery = "";
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    new ErrorUtil(CompareTextActivity.this).showError(e);
                });
            }
        }).start();
    }

    private String readText(String path, boolean isZip, String zipPath) throws Exception {
        StringBuilder sb = new StringBuilder();
        if (isZip) {
            try (ZipFile zf = new ZipFile(zipPath)) {
                ZipEntry ze = zf.getEntry(path);
                if (ze != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(zf.getInputStream(ze)))) {
                        appendLines(sb, reader);
                    }
                }
            }
        } else {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path))))) {
                appendLines(sb, reader);
            }
        }
        return sb.toString();
    }

    private static void appendLines(StringBuilder sb, BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
    }

    // ==========================================
    // Diff building
    // ==========================================

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
        // (blanks) don't shift the numbering.
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

    /** Keeps the lines the blank mode doesn't ignore, recording their original numbers. */
    private List<String> significantLines(List<String> lines, int[] numbers, boolean dropBlanks) {
        List<String> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (dropBlanks && line.trim().isEmpty()) continue;
            numbers[out.size()] = i + 1;
            out.add(line);
        }
        return out;
    }

    private BiPredicate<String, String> equalizer() {
        return (a, b) -> {
            if (a.equals(b)) return true;
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

    private void setupSearchBar() {
        findViewById(R.id.text_search_next).setOnClickListener(v -> stepSearch(1));
        findViewById(R.id.text_search_prev).setOnClickListener(v -> stepSearch(-1));
        findViewById(R.id.text_search_close).setOnClickListener(v -> closeSearchBar());
        findViewById(R.id.text_search_history).setOnClickListener(v -> showSearchHistory(v));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            stepSearch(1);
            return true;
        });
    }

    /** Shows the search bar, pre-filled with the last searched string. */
    private void openSearchBar() {
        searchBar.setVisibility(View.VISIBLE);
        List<SearchHistoryHelper.Item> history =
                SearchHistoryHelper.load(this, SearchHistoryHelper.KEY_COMPARE_TEXT);
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
                SearchHistoryHelper.load(this, SearchHistoryHelper.KEY_COMPARE_TEXT);
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
                SearchHistoryHelper.save(CompareTextActivity.this,
                        SearchHistoryHelper.KEY_COMPARE_TEXT, items);
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
            SearchHistoryHelper.push(this, SearchHistoryHelper.KEY_COMPARE_TEXT, query);
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
    // Difference navigation
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
    // Menu
    // ==========================================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_compare_dex_diff, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
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
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        if (id == R.id.action_compare_dex_exit) {
            finish();
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

    private void rebuildDiff() {
        if (textLeft == null || textRight == null) return;
        progressBar.setVisibility(View.VISIBLE);
        diffListView.setVisibility(View.GONE);
        final String left = textLeft;
        final String right = textRight;
        new Thread(() -> {
            try {
                // Only the generator options change here, so the raw text is reused.
                final List<DiffLine> built = buildDiff(left, right);
                runOnUiThread(() -> {
                    diffLines.clear();
                    diffLines.addAll(built);
                    diffPositions.clear();
                    for (int i = 0; i < diffLines.size(); i++) {
                        if (diffLines.get(i).tag != DiffRow.Tag.EQUAL) diffPositions.add(i);
                    }
                    diffAdapter.notifyDataSetChanged();
                    progressBar.setVisibility(View.GONE);
                    diffListView.setVisibility(View.VISIBLE);
                    lastSearchIndex = -1;
                    activeSearchQuery = "";
                    invalidateOptionsMenu();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    diffListView.setVisibility(View.VISIBLE);
                    new ErrorUtil(CompareTextActivity.this).showError(e);
                });
            }
        }).start();
    }

    // ==========================================
    // Adapter
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

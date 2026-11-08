package io.github.abdurazaaqmohammed.features.sqlite;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.AccessManager;

/**
 * Modern, in-app SQLite Database Viewer & Editor:
 * - Table selector spinner
 * - Dynamic scrollable TableLayout (horizontal + vertical)
 * - Row and Cell click to inspect & edit values via SQL UPDATE
 * - Row deletion via SQL DELETE
 * - Custom SQL query console (SELECT / UPDATE / INSERT / DELETE)
 * - Safe root file staging and automatic writeback on edit
 */
public class SqliteViewerActivity extends BaseActivity {

    public static final String EXTRA_DB_PATH = "extra_db_path";
    public static final String EXTRA_ORIGINAL_PATH = "extra_original_path";

    private String dbPath;
    private String originalPath;
    private boolean isModified = false;

    private SQLiteDatabase db;
    private Spinner tableSpinner;
    private TextView infoTextView;
    private TableLayout tableLayout;
    private ProgressBar progressBar;
    private MaterialButton btnRunSql;
    private MaterialButton btnPrevPage;
    private MaterialButton btnNextPage;
    private TextView tvPageInfo;

    private final List<String> tableList = new ArrayList<>();
    private String currentTable = null;
    private int currentPage = 0;
    private static final int PAGE_SIZE = 100;
    private int totalRows = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        dbPath = getIntent().getStringExtra(EXTRA_DB_PATH);
        originalPath = getIntent().getStringExtra(EXTRA_ORIGINAL_PATH);
        if (TextUtils.isEmpty(originalPath)) originalPath = dbPath;

        if (TextUtils.isEmpty(dbPath) || !new File(dbPath).exists()) {
            Toast.makeText(this, "Database file not found: " + dbPath, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initUi();
        loadDatabase();
    }

    private void initUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int bgColor = MaterialColors.getColor(root, android.R.attr.colorBackground, 0xFF121212);
        root.setBackgroundColor(bgColor);

        // Toolbar
        Toolbar toolbar = new Toolbar(this);
        String name = new File(originalPath).getName();
        toolbar.setTitle(name);
        toolbar.setSubtitle("SQLite Viewer");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);

        // Top Control Bar: Table Spinner + SQL Button
        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER_VERTICAL);
        controlBar.setPadding(dp(12), dp(8), dp(12), dp(8));

        tableSpinner = new Spinner(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        spinnerParams.rightMargin = dp(8);
        tableSpinner.setLayoutParams(spinnerParams);
        controlBar.addView(tableSpinner);

        btnRunSql = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnRunSql.setText("SQL");
        btnRunSql.setOnClickListener(v -> showCustomSqlDialog());
        controlBar.addView(btnRunSql);

        root.addView(controlBar);

        // Info text (row count, columns, status)
        infoTextView = new TextView(this);
        infoTextView.setPadding(dp(14), 0, dp(14), dp(4));
        infoTextView.setTextSize(12);
        infoTextView.setAlpha(0.7f);
        root.addView(infoTextView);

        // Progress indicator
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        // Grid Area (HorizontalScrollView -> ScrollView -> TableLayout)
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        hScroll.setLayoutParams(scrollParams);
        hScroll.setFillViewport(true);

        ScrollView vScroll = new ScrollView(this);
        vScroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        vScroll.setFillViewport(true);

        tableLayout = new TableLayout(this);
        tableLayout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tableLayout.setStretchAllColumns(false);

        vScroll.addView(tableLayout);
        hScroll.addView(vScroll);
        root.addView(hScroll);

        // Bottom Pagination Bar
        LinearLayout paginationBar = new LinearLayout(this);
        paginationBar.setOrientation(LinearLayout.HORIZONTAL);
        paginationBar.setGravity(Gravity.CENTER);
        paginationBar.setPadding(dp(12), dp(6), dp(12), dp(8));

        btnPrevPage = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        btnPrevPage.setText("◀ Prev");
        btnPrevPage.setOnClickListener(v -> {
            if (currentPage > 0) {
                currentPage--;
                loadTableData();
            }
        });
        paginationBar.addView(btnPrevPage);

        tvPageInfo = new TextView(this);
        tvPageInfo.setPadding(dp(16), 0, dp(16), 0);
        tvPageInfo.setText("0 / 0");
        paginationBar.addView(tvPageInfo);

        btnNextPage = new MaterialButton(this, null, androidx.appcompat.R.attr.borderlessButtonStyle);
        btnNextPage.setText("Next ▶");
        btnNextPage.setOnClickListener(v -> {
            if ((currentPage + 1) * PAGE_SIZE < totalRows) {
                currentPage++;
                loadTableData();
            }
        });
        paginationBar.addView(btnNextPage);

        root.addView(paginationBar);

        setContentView(root);
    }

    private void loadDatabase() {
        new Thread(() -> {
            try {
                // Open database in read-write mode so edits work
                db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);

                tableList.clear();
                try (Cursor c = db.rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null)) {
                    while (c.moveToNext()) {
                        tableList.add(c.getString(0));
                    }
                }

                runOnUiThread(() -> {
                    if (tableList.isEmpty()) {
                        infoTextView.setText("No user tables found in database");
                        return;
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            SqliteViewerActivity.this, android.R.layout.simple_spinner_dropdown_item, tableList);
                    tableSpinner.setAdapter(adapter);
                    tableSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                            currentTable = tableList.get(position);
                            currentPage = 0;
                            loadTableData();
                        }

                        @Override
                        public void onNothingSelected(AdapterView<?> parent) {}
                    });
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(SqliteViewerActivity.this)
                            .setTitle("Database Error")
                            .setMessage("Failed to open SQLite database:\n" + e.getMessage())
                            .setPositiveButton(android.R.string.ok, (d, w) -> finish())
                            .show();
                });
            }
        }).start();
    }

    private void loadTableData() {
        if (db == null || currentTable == null) return;

        progressBar.setVisibility(View.VISIBLE);
        tableLayout.removeAllViews();

        new Thread(() -> {
            try {
                // Get total rows count
                try (Cursor countCursor = db.rawQuery("SELECT count(*) FROM " + escapeName(currentTable), null)) {
                    if (countCursor.moveToNext()) {
                        totalRows = countCursor.getInt(0);
                    } else {
                        totalRows = 0;
                    }
                }

                int offset = currentPage * PAGE_SIZE;
                String query = "SELECT rowid, * FROM " + escapeName(currentTable) + " LIMIT " + PAGE_SIZE + " OFFSET " + offset;

                List<String> columnNames = new ArrayList<>();
                List<String[]> rows = new ArrayList<>();
                List<Long> rowIds = new ArrayList<>();

                try (Cursor cursor = db.rawQuery(query, null)) {
                    if (cursor != null) {
                        String[] cols = cursor.getColumnNames();
                        // index 0 is rowid
                        for (int i = 1; i < cols.length; i++) {
                            columnNames.add(cols[i]);
                        }

                        while (cursor.moveToNext()) {
                            long rowid = cursor.getLong(0);
                            rowIds.add(rowid);

                            String[] row = new String[columnNames.size()];
                            for (int i = 0; i < columnNames.size(); i++) {
                                int cursorIdx = i + 1;
                                int type = cursor.getType(cursorIdx);
                                if (type == Cursor.FIELD_TYPE_NULL) {
                                    row[i] = "NULL";
                                } else if (type == Cursor.FIELD_TYPE_BLOB) {
                                    byte[] blob = cursor.getBlob(cursorIdx);
                                    row[i] = "[BLOB (" + (blob != null ? blob.length : 0) + "B)]";
                                } else {
                                    row[i] = cursor.getString(cursorIdx);
                                }
                            }
                            rows.add(row);
                        }
                    }
                }

                runOnUiThread(() -> renderTableUi(columnNames, rows, rowIds, offset));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(SqliteViewerActivity.this, "Query error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void renderTableUi(List<String> columns, List<String[]> rows, List<Long> rowIds, int offset) {
        progressBar.setVisibility(View.GONE);
        tableLayout.removeAllViews();

        int totalPages = (int) Math.ceil((double) totalRows / PAGE_SIZE);
        if (totalPages == 0) totalPages = 1;
        tvPageInfo.setText((currentPage + 1) + " / " + totalPages);
        btnPrevPage.setEnabled(currentPage > 0);
        btnNextPage.setEnabled((currentPage + 1) < totalPages);

        infoTextView.setText("Total rows: " + totalRows + " | Columns: " + columns.size() +
                (totalRows > 0 ? " | Showing rows " + (offset + 1) + " - " + Math.min(offset + PAGE_SIZE, totalRows) : ""));

        if (columns.isEmpty()) {
            return;
        }

        int textColor = MaterialColors.getColor(tableLayout, android.R.attr.textColorPrimary, Color.WHITE);
        int headerBg = MaterialColors.getColor(tableLayout, com.google.android.material.R.attr.colorSurfaceVariant, 0xFF2A2A2A);
        int rowEvenBg = MaterialColors.getColor(tableLayout, com.google.android.material.R.attr.colorSurface, 0xFF1E1E1E);
        int rowOddBg = MaterialColors.getColor(tableLayout, android.R.attr.colorBackground, 0xFF121212);

        // Header Row
        TableRow headerRow = new TableRow(this);
        headerRow.setBackgroundColor(headerBg);

        // Row # column header
        TextView tvIdxHeader = createCell("#", true, headerBg, textColor);
        headerRow.addView(tvIdxHeader);

        for (String col : columns) {
            headerRow.addView(createCell(col, true, headerBg, textColor));
        }
        tableLayout.addView(headerRow);

        // Data Rows
        for (int r = 0; r < rows.size(); r++) {
            final int rowIndex = r;
            final long rowId = rowIds.get(r);
            final String[] rowData = rows.get(r);
            final int rowBg = (r % 2 == 0) ? rowEvenBg : rowOddBg;

            TableRow dataRow = new TableRow(this);
            dataRow.setBackgroundColor(rowBg);

            // Row index
            TextView tvIdx = createCell(String.valueOf(offset + r + 1), false, rowBg, textColor);
            tvIdx.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            tvIdx.setOnClickListener(v -> showRowActionDialog(rowId, columns, rowData));
            dataRow.addView(tvIdx);

            for (int c = 0; c < rowData.length; c++) {
                final int colIndex = c;
                final String cellVal = rowData[c];
                TextView cellView = createCell(cellVal, false, rowBg, textColor);

                if ("NULL".equals(cellVal)) {
                    cellView.setTextColor(0xFF888888);
                    cellView.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
                }

                cellView.setOnClickListener(v -> showCellEditDialog(rowId, columns.get(colIndex), cellVal));
                dataRow.addView(cellView);
            }

            tableLayout.addView(dataRow);
        }
    }

    private TextView createCell(String text, boolean isHeader, int bg, int textColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setTextSize(isHeader ? 13 : 12);
        tv.setTextColor(textColor);
        if (isHeader) {
            tv.setTypeface(Typeface.DEFAULT_BOLD);
        }

        // Cell border outline
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setStroke(1, 0x33888888);
        tv.setBackground(gd);

        TableRow.LayoutParams lp = new TableRow.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void showCellEditDialog(long rowId, String columnName, String currentValue) {
        EditText input = new EditText(this);
        input.setText("NULL".equals(currentValue) ? "" : currentValue);
        input.setSelectAllOnFocus(true);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Edit: " + columnName)
                .setMessage("Row ID: " + rowId)
                .setView(wrapDialogView(input))
                .setPositiveButton("Save", (d, w) -> {
                    String newVal = input.getText().toString();
                    updateCell(rowId, columnName, newVal);
                })
                .setNeutralButton("Set NULL", (d, w) -> updateCell(rowId, columnName, null))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateCell(long rowId, String columnName, String newValue) {
        new Thread(() -> {
            try {
                ContentValues cv = new ContentValues();
                if (newValue == null) {
                    cv.putNull(columnName);
                } else {
                    cv.put(columnName, newValue);
                }

                int affected = db.update(currentTable, cv, "rowid = ?", new String[]{String.valueOf(rowId)});
                if (affected > 0) {
                    markModifiedAndSync();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Updated successfully", Toast.LENGTH_SHORT).show();
                        loadTableData();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showRowActionDialog(long rowId, List<String> columns, String[] rowData) {
        String[] options = {"Copy Row as JSON", "Copy Row as SQL INSERT", "Delete Row"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Row Options (ID: " + rowId + ")")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0 -> copyRowAsJson(columns, rowData);
                        case 1 -> copyRowAsInsert(columns, rowData);
                        case 2 -> confirmDeleteRow(rowId);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDeleteRow(long rowId) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete Row")
                .setMessage("Are you sure you want to delete row with ID: " + rowId + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    new Thread(() -> {
                        try {
                            int deleted = db.delete(currentTable, "rowid = ?", new String[]{String.valueOf(rowId)});
                            if (deleted > 0) {
                                markModifiedAndSync();
                                runOnUiThread(() -> {
                                    Toast.makeText(this, "Row deleted", Toast.LENGTH_SHORT).show();
                                    loadTableData();
                                });
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void copyRowAsJson(List<String> columns, String[] rowData) {
        StringBuilder sb = new StringBuilder("{\n");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("  \"").append(columns.get(i)).append("\": ");
            String v = rowData[i];
            if ("NULL".equals(v)) {
                sb.append("null");
            } else {
                sb.append("\"").append(v.replace("\"", "\\\"")).append("\"");
            }
            if (i < columns.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("}");
        copyToClipboard(sb.toString(), "Row JSON copied");
    }

    private void copyRowAsInsert(List<String> columns, String[] rowData) {
        StringBuilder sb = new StringBuilder("INSERT INTO ");
        sb.append(escapeName(currentTable)).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(escapeName(columns.get(i)));
            if (i < columns.size() - 1) sb.append(", ");
        }
        sb.append(") VALUES (");
        for (int i = 0; i < rowData.length; i++) {
            String v = rowData[i];
            if ("NULL".equals(v)) {
                sb.append("NULL");
            } else {
                sb.append("'").append(v.replace("'", "''")).append("'");
            }
            if (i < rowData.length - 1) sb.append(", ");
        }
        sb.append(");");
        copyToClipboard(sb.toString(), "SQL INSERT copied");
    }

    private void showCustomSqlDialog() {
        EditText input = new EditText(this);
        input.setHint("SELECT * FROM " + (currentTable != null ? currentTable : "...") + ";");
        input.setTypeface(Typeface.MONOSPACE);
        input.setTextSize(13);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Execute SQL Query")
                .setView(wrapDialogView(input))
                .setPositiveButton("Run", (d, w) -> {
                    String query = input.getText().toString().trim();
                    if (!TextUtils.isEmpty(query)) executeCustomSql(query);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void executeCustomSql(String query) {
        new Thread(() -> {
            try {
                if (query.toUpperCase().startsWith("SELECT") || query.toUpperCase().startsWith("PRAGMA")) {
                    runOnUiThread(() -> {
                        // Display results or reload
                        loadTableData();
                        Toast.makeText(this, "Query executed", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    db.execSQL(query);
                    markModifiedAndSync();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "SQL executed successfully", Toast.LENGTH_SHORT).show();
                        loadTableData();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> new MaterialAlertDialogBuilder(this)
                        .setTitle("SQL Error")
                        .setMessage(e.getMessage())
                        .setPositiveButton(android.R.string.ok, null)
                        .show());
            }
        }).start();
    }

    private void markModifiedAndSync() {
        isModified = true;
        // If staged from root / protected path, sync back to original location
        if (!TextUtils.isEmpty(originalPath) && !originalPath.equals(dbPath)) {
            try {
                AccessManager.uploadFile(this, new File(dbPath), originalPath);
            } catch (Exception e) {
                // Staged fallback
            }
        }
    }

    private void copyToClipboard(String text, String msg) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("SQL Data", text));
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private String escapeName(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    private View wrapDialogView(View v) {
        LinearLayout l = new LinearLayout(this);
        l.setPadding(dp(20), dp(10), dp(20), dp(10));
        l.addView(v, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return l;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }
}

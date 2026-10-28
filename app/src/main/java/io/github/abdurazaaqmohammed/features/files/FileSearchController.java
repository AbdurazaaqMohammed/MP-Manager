package io.github.abdurazaaqmohammed.features.files;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.ResourceTableParser;
import com.apk.axml.aXMLDecoder;
import com.apk.axml.serializableItems.ResEntry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.domain.files.ContentHit;
import io.github.abdurazaaqmohammed.domain.files.FileSearch;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.KeyboardUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SearchHistoryDropdown;
import io.github.abdurazaaqmohammed.utils.SearchHistoryHelper;
import io.github.abdurazaaqmohammed.utils.AbxUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * File search + find-in-files dialogs extracted from MainActivity.
 * Matching/scanning math lives in domain.files.FileSearch.
 */
public class FileSearchController {

    private final MainActivity activity;
    private List<File> lastSearchResults = null;
    private File lastSearchStartDir = null;
    private boolean lastSearchIsPane1 = true;
    private Parcelable searchResultsScrollState = null;

    private List<ZipEntryInfo> lastZipResults = null;
    private File lastZipFile = null;
    private String lastZipSubPath = null;

    private List<ContentHit> lastXmlHits = null;
    private String lastXmlQuery = null;
    private boolean lastXmlRegex = false;
    private boolean lastXmlMatchCase = false;
    private enum SearchType { NONE, REGULAR, ZIP, XML }
    private SearchType lastSearchType = SearchType.NONE;

    public boolean hasSearchResults() {
        return (lastSearchType == SearchType.REGULAR && lastSearchResults != null && !lastSearchResults.isEmpty())
                || (lastSearchType == SearchType.ZIP && lastZipResults != null && !lastZipResults.isEmpty())
                || (lastSearchType == SearchType.XML && lastXmlHits != null && !lastXmlHits.isEmpty());
    }

    public File getLastSearchStartDir() {
        return lastSearchStartDir != null ? lastSearchStartDir : lastZipFile;
    }

    private AlertDialog activeSearchDialog = null;
    private boolean shouldRestoreSearchDialogOnResume = false;

    public void onActivityResume() {
        if (shouldRestoreSearchDialogOnResume && hasSearchResults()) {
            if (activeSearchDialog == null || !activeSearchDialog.isShowing()) {
                showLastSearchResultsDialog();
            }
        }
    }

    public void clearLastSearchResults() {
        lastSearchResults = null;
        lastSearchStartDir = null;
        lastZipResults = null;
        lastZipFile = null;
        lastZipSubPath = null;
        lastXmlHits = null;
        lastXmlQuery = null;
        lastSearchType = SearchType.NONE;
        shouldRestoreSearchDialogOnResume = false;
        searchResultsScrollState = null;
        if (activeSearchDialog != null && activeSearchDialog.isShowing()) {
            try {
                activeSearchDialog.dismiss();
            } catch (Exception ignored) {}
        }
        activeSearchDialog = null;
    }

    public void showLastSearchResultsDialog() {
        if (lastSearchType == SearchType.XML && lastXmlHits != null && !lastXmlHits.isEmpty()) {
            showContentHitsDialog(lastXmlHits, lastXmlQuery, lastXmlRegex, lastXmlMatchCase);
        } else if (lastSearchType == SearchType.ZIP && lastZipResults != null && !lastZipResults.isEmpty()) {
            showZipSearchResultsDialog(lastZipFile, lastZipSubPath, lastZipResults, lastSearchIsPane1);
        } else if (lastSearchType == SearchType.REGULAR && lastSearchResults != null && !lastSearchResults.isEmpty()) {
            showSearchResultsDialog(lastSearchStartDir, lastSearchResults, lastSearchIsPane1);
        } else if (lastXmlHits != null && !lastXmlHits.isEmpty()) {
            showContentHitsDialog(lastXmlHits, lastXmlQuery, lastXmlRegex, lastXmlMatchCase);
        } else if (lastZipResults != null && !lastZipResults.isEmpty()) {
            showZipSearchResultsDialog(lastZipFile, lastZipSubPath, lastZipResults, lastSearchIsPane1);
        } else if (lastSearchResults != null && !lastSearchResults.isEmpty()) {
            showSearchResultsDialog(lastSearchStartDir, lastSearchResults, lastSearchIsPane1);
        }
    }

    public FileSearchController(MainActivity activity) {
        this.activity = activity;
    }

    public void showSearchDialog() {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_search_files, null);
        AutoCompleteTextView searchQuery = dialogView.findViewById(R.id.searchQuery);
        ImageView searchHistoryDropdown = dialogView.findViewById(R.id.searchHistoryDropdown);
        CheckBox searchSubfolders = dialogView.findViewById(R.id.searchSubfolders);
        TextView advancedSearchToggle = dialogView.findViewById(R.id.advancedSearchToggle);
        LinearLayout advancedSearchLayout = dialogView.findViewById(R.id.advancedSearchLayout);
        CheckBox matchCase = dialogView.findViewById(R.id.matchCase);
        CheckBox useRegex = dialogView.findViewById(R.id.useRegex);
        EditText textInsideFile = dialogView.findViewById(R.id.textInsideFile);
        EditText minFileSize = dialogView.findViewById(R.id.minFileSize);
        EditText maxFileSize = dialogView.findViewById(R.id.maxFileSize);

        advancedSearchToggle.setOnClickListener(v -> {
            boolean isVisible = advancedSearchLayout.getVisibility() == View.VISIBLE;
            advancedSearchLayout.setVisibility(isVisible ? View.GONE : View.VISIBLE);
            advancedSearchToggle.setText(isVisible ? "Advanced Search ▼" : "Advanced Search ▲");
        });

        List<SearchHistoryHelper.Item> historyItems =
                SearchHistoryHelper.load(activity, SearchHistoryHelper.KEY_MAIN);

        String lastMain = SearchHistoryHelper.lastQuery(activity, SearchHistoryHelper.KEY_MAIN);
        if (searchQuery.length() == 0 && !lastMain.isEmpty()) {
            searchQuery.setText(lastMain);
            searchQuery.setSelection(lastMain.length());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_dropdown_item_1line,
                new ArrayList<>());
        searchQuery.setAdapter(adapter);

        searchHistoryDropdown.setOnClickListener(v -> {
            List<SearchHistoryHelper.Item> hist =
                    SearchHistoryHelper.load(activity, SearchHistoryHelper.KEY_MAIN);
            if (hist.isEmpty()) {
                Extensions.showMessage(activity, R.string.no_files_found);
                return;
            }
            SearchHistoryDropdown.show(activity, searchQuery, hist,
                    new SearchHistoryDropdown.Listener() {
                        @Override
                        public void onSelect(String query) {
                            searchQuery.setText(query);
                            searchQuery.setSelection(query.length());
                        }
                        @Override
                        public void onChanged(List<SearchHistoryHelper.Item> items) {
                            SearchHistoryHelper.save(activity, SearchHistoryHelper.KEY_MAIN, items);
                        }
                    });
        });

        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setTitle(activity.getString(android.R.string.search_go))
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(activity.getString(android.R.string.search_go), null) // Prevent auto-dismiss
                .create();

        dialog.setOnShowListener(d -> {
            searchQuery.requestFocus();
            try {
                if (dialog.getWindow() != null)
                    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                InputMethodManager imm =
                        (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(searchQuery, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) {
            }
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                CharSequence q = searchQuery.getText();
                if (TextUtils.isEmpty(q)) {
                    Extensions.showMessage(activity, R.string.search_query_needed);
                    return;
                }
                String query = q.toString();
                SearchHistoryHelper.push(activity, SearchHistoryHelper.KEY_MAIN, query);

                boolean subfolders = searchSubfolders.isChecked();
                boolean mCase = matchCase.isChecked();
                boolean regex = useRegex.isChecked();
                String textInside = textInsideFile.getText().toString();
                long minSize = -1;
                long maxSize = -1;
                try {
                    if (!TextUtils.isEmpty(minFileSize.getText()))
                        minSize = Long.parseLong(minFileSize.getText().toString());
                    if (!TextUtils.isEmpty(maxFileSize.getText()))
                        maxSize = Long.parseLong(maxFileSize.getText().toString());
                } catch (NumberFormatException ignored) {
                }

                dialog.dismiss();
                executeSearch(query, subfolders, mCase, regex, textInside, minSize, maxSize);
            });
        });
        activity.dialogUtil.styleAlertDialog(dialog);
        dialog.show();
    }

    private void executeSearch(String query, boolean subfolders, boolean mCase, boolean regex,
            String textInside, long minSize, long maxSize) {
        boolean isPane1 = activity.lastPaneSelected == 1;
        File startDir = isPane1 ? activity.pane1Folder : activity.pane2Folder;

        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter adapter)) {
            Extensions.showMessage(activity, R.string.search_ftp_unsupported);
            return;
        }
        if (adapter.isInZip) {
            File zipFile = null;
            if (adapter.values != null && adapter.values.length > 0 && adapter.values[0] instanceof ZipEntryInfo) {
                zipFile = ((ZipEntryInfo) adapter.values[0]).getZipFile();
            }
            if (zipFile == null) {
                Extensions.showMessage(activity, R.string.search_zip_unsupported);
                return;
            }
            executeZipSearch(zipFile, adapter.currentZipPath, query, subfolders, mCase, regex, textInside, minSize, maxSize, isPane1);
            return;
        }
        if (regex) {
            try {
                Pattern.compile(query, mCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                Extensions.showMessage(activity, R.string.invalid_regex);
                return;
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(activity.rss.getString(R.string.searching));

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * activity.getResources().getDisplayMetrics().density + 0.5f);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        TextView folderView = new TextView(activity);
        folderView.setEllipsize(TextUtils.TruncateAt.START);
        folderView.setSingleLine(true);
        folderView.setText(startDir.getAbsolutePath());
        folderView.setTextSize(13);
        layout.addView(folderView);

        TextView checkingView = new TextView(activity);
        checkingView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        checkingView.setSingleLine(true);
        checkingView.setText("...");
        checkingView.setTextSize(12);
        checkingView.setPadding(0, 8, 0, 8);
        layout.addView(checkingView);

        TextView countView = new TextView(activity);
        countView.setText("Found: 0");
        countView.setTextSize(14);
        countView.setTypeface(null, Typeface.BOLD);
        layout.addView(countView);

        TextView previewView = new TextView(activity);
        previewView.setTextSize(12);
        previewView.setMaxLines(5);
        previewView.setEllipsize(TextUtils.TruncateAt.END);
        previewView.setPadding(0, 8, 0, 0);
        layout.addView(previewView);

        builder.setView(layout);
        builder.setCancelable(false);

        boolean[] cancelled = {false};
        boolean[] stopped = {false};

        builder.setPositiveButton("Stop", (d, which) -> {
            stopped[0] = true;
            cancelled[0] = true;
        });

        builder.setNegativeButton(android.R.string.cancel, (d, which) -> {
            cancelled[0] = true;
        });

        AlertDialog progressDialog = builder.create();
        progressDialog.show();

        final String finalQuery = query;
        List<String> matchedNames = new ArrayList<>();
        long[] lastUiUpdate = {0};

        new Thread(() -> {
            List<File> results = FileSearch.searchByName(startDir, finalQuery, subfolders,
                    mCase, regex, textInside, minSize, maxSize,
                    dir -> {
                        try {
                            if (AccessManager.fileOpsOn(activity)
                                    && !TextUtils.isEmpty(textInside)) {
                                return null;
                            }
                            if (AccessManager.fileOpsOn(activity)) {
                                return AccessManager.listWithStat(activity, dir.getAbsolutePath());
                            }
                        } catch (Exception ignored) {
                        }
                        return null;
                    },
                    new FileSearch.SearchCallback() {
                        @Override
                        public void onFolderEntered(File folder) {
                            long now = System.currentTimeMillis();
                            if (now - lastUiUpdate[0] > 100) {
                                lastUiUpdate[0] = now;
                                String path = folder.getAbsolutePath();
                                activity.handler.post(() -> folderView.setText(path));
                            }
                        }

                        @Override
                        public void onFileChecking(File file) {
                            long now = System.currentTimeMillis();
                            if (now - lastUiUpdate[0] > 100) {
                                lastUiUpdate[0] = now;
                                String name = file.getName();
                                activity.handler.post(() -> checkingView.setText(name));
                            }
                        }

                        @Override
                        public void onFileFound(File file, int totalFound) {
                            synchronized (matchedNames) {
                                if (matchedNames.size() < 10) {
                                    matchedNames.add(file.getName());
                                } else {
                                    matchedNames.remove(0);
                                    matchedNames.add(file.getName());
                                }
                                StringBuilder sb = new StringBuilder();
                                for (String n : matchedNames) {
                                    if (sb.length() > 0) sb.append("\n");
                                    sb.append("• ").append(n);
                                }
                                String preview = sb.toString();
                                activity.handler.post(() -> {
                                    countView.setText("Found: " + totalFound);
                                    previewView.setText(preview);
                                });
                            }
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled[0];
                        }
                    });

            activity.handler.post(() -> {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {
                }

                if (cancelled[0] && !stopped[0]) {
                    // Aborted via Cancel
                    return;
                }

                if (results.isEmpty()) {
                    Extensions.showMessage(activity, R.string.no_files_found);
                } else {
                    lastSearchResults = results;
                    lastSearchStartDir = startDir;
                    lastSearchIsPane1 = isPane1;
                    showSearchResultsDialog(startDir, results, isPane1);
                }
            });
        }).start();
    }

    public void showSearchResultsDialog(File startDir, List<File> results, boolean isPane1) {
        lastSearchResults = results;
        lastSearchStartDir = startDir;
        lastSearchIsPane1 = isPane1;
        lastSearchType = SearchType.REGULAR;

        List<File> allResults = new ArrayList<>(results);
        List<File> filteredResults = new ArrayList<>(results);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16);
        header.setPadding(pad, pad, pad, dp(8));

        TextView titleText = new TextView(activity);
        titleText.setText("search results (" + filteredResults.size() + ")");
        titleText.setTextSize(18);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(titleText);

        ImageButton filterBtn = new ImageButton(activity, null, android.R.attr.borderlessButtonStyle);
        filterBtn.setImageResource(R.drawable.baseline_search_24);
        filterBtn.setContentDescription(activity.getString(R.string.filter));
        header.addView(filterBtn);
        root.addView(header);

        EditText searchInput = new EditText(activity);
        searchInput.setHint(activity.getString(R.string.filter));
        searchInput.setSingleLine(true);
        searchInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_search_24, 0, 0, 0);
        searchInput.setCompoundDrawablePadding(dp(8));
        searchInput.setPadding(pad, dp(8), pad, dp(8));
        searchInput.setVisibility(View.GONE);
        root.addView(searchInput);

        filterBtn.setOnClickListener(v -> {
            if (searchInput.getVisibility() == View.VISIBLE) {
                searchInput.setVisibility(View.GONE);
                searchInput.setText("");
            } else {
                searchInput.setVisibility(View.VISIBLE);
                searchInput.requestFocus();
            }
        });

        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        list.setLayoutParams(listParams);
        root.addView(list);

        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(12), dp(4), dp(12), dp(4));

        Button expandBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        expandBtn.setText("⤢ Open in Pane");
        expandBtn.setTextSize(13);
        bottomBar.addView(expandBtn);

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1.0f);
        spacer.setLayoutParams(spacerLp);
        bottomBar.addView(spacer);

        Button clearBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        clearBtn.setText("CLEAR");
        clearBtn.setTextSize(13);
        bottomBar.addView(clearBtn);

        Button closeBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        closeBtn.setText("CLOSE");
        closeBtn.setTextSize(13);
        bottomBar.addView(closeBtn);

        root.addView(bottomBar);

        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setView(root)
                .create();

        activeSearchDialog = dialog;
        shouldRestoreSearchDialogOnResume = true;

        dialog.setOnDismissListener(d -> {
            if (activeSearchDialog == dialog) {
                activeSearchDialog = null;
            }
        });

        expandBtn.setOnClickListener(v -> {
            shouldRestoreSearchDialogOnResume = false;
            dialog.dismiss();
            openSearchResultsInPane(startDir, results, isPane1);
        });

        clearBtn.setOnClickListener(v -> {
            shouldRestoreSearchDialogOnResume = false;
            searchResultsScrollState = null;
            clearLastSearchResults();
            dialog.dismiss();
        });

        closeBtn.setOnClickListener(v -> {
            shouldRestoreSearchDialogOnResume = false;
            if (list.getLayoutManager() != null) {
                searchResultsScrollState = list.getLayoutManager().onSaveInstanceState();
            }
            dialog.dismiss();
        });

        RecyclerView.Adapter<?> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(8), dp(16), dp(8));

                ImageView icon = new ImageView(activity);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(28), dp(28));
                icon.setLayoutParams(iconLp);
                icon.setImageResource(R.drawable.baseline_search_24);
                row.addView(icon);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textLp.setMarginStart(dp(12));
                textCol.setLayoutParams(textLp);

                TextView title = new TextView(activity);
                title.setTextSize(14);
                title.setTypeface(null, Typeface.BOLD);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(title);

                TextView sub = new TextView(activity);
                sub.setTextSize(12);
                sub.setTextColor(Color.GRAY);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.START);
                textCol.addView(sub);

                row.addView(textCol);
                return new RecyclerView.ViewHolder(row) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                File f = filteredResults.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                ImageView icon = (ImageView) row.getChildAt(0);
                LinearLayout textCol = (LinearLayout) row.getChildAt(1);
                TextView title = (TextView) textCol.getChildAt(0);
                TextView sub = (TextView) textCol.getChildAt(1);

                title.setText(f.getName());
                sub.setText(f.getParent() != null ? f.getParent() : "");
                if (f.isDirectory()) {
                    icon.setImageResource(R.drawable.folder_24px);
                } else {
                    icon.setImageResource(R.drawable.ic_link_24);
                }

                row.setOnClickListener(v -> {
                    if (list.getLayoutManager() != null) {
                        searchResultsScrollState = list.getLayoutManager().onSaveInstanceState();
                    }
                    if (f.isDirectory()) {
                        activity.clearGreen(isPane1);
                        activity.setSelectedPane(isPane1 ? 1 : 2);
                        activity.loadFolderInPaneAsync(f, isPane1);
                    } else {
                        MainFilesArrayAdapter a = activity.getCurrentFilesAdapter();
                        if (a == null) a = activity.paneFilesAdapter(isPane1);
                        if (a != null && a.getFileOpener() != null) {
                            a.getFileOpener().handleFileClick(f, f.getName());
                        }
                    }
                });
            }

            @Override
            public int getItemCount() {
                return filteredResults.size();
            }
        };
        list.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                filteredResults.clear();
                for (File f : allResults) {
                    if (q.isEmpty() || f.getName().toLowerCase(Locale.ROOT).contains(q)
                            || (f.getParent() != null && f.getParent().toLowerCase(Locale.ROOT).contains(q))) {
                        filteredResults.add(f);
                    }
                }
                titleText.setText("search results (" + filteredResults.size() + ")");
                adapter.notifyDataSetChanged();
            }
        });

        if (searchResultsScrollState != null) {
            list.post(() -> {
                if (list.getLayoutManager() != null && searchResultsScrollState != null) {
                    list.getLayoutManager().onRestoreInstanceState(searchResultsScrollState);
                }
            });
        }

        activity.dialogUtil.styleAlertDialog(dialog);
        dialog.show();
    }

    public void openSearchResultsInPane(File startDir, List<File> results, boolean isPane1) {
        lastSearchResults = results;
        lastSearchStartDir = startDir;
        lastSearchIsPane1 = isPane1;
        File[] resArray = results.toArray(new File[0]);
        activity.setCurrentFolder(startDir.getPath() + " - Search results", Arrays.asList(resArray));
        RecyclerView pane = activity.findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);
        MainFilesArrayAdapter adapter = new MainFilesArrayAdapter(activity, resArray, startDir, isPane1, false, null);
        adapter.setSearchResultsMode(true);
        pane.setAdapter(adapter);
    }

    private void executeZipSearch(File zipFile, String currentSubPath, String query,
            boolean subfolders, boolean mCase, boolean regex, String textInside,
            long minSize, long maxSize, boolean isPane1) {
        if (regex) {
            try {
                Pattern.compile(query, mCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                Extensions.showMessage(activity, R.string.invalid_regex);
                return;
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setTitle(activity.rss.getString(R.string.searching));

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (18 * activity.getResources().getDisplayMetrics().density + 0.5f);
        layout.setPadding(pad, pad / 2, pad, pad / 2);

        TextView folderView = new TextView(activity);
        folderView.setEllipsize(TextUtils.TruncateAt.START);
        folderView.setSingleLine(true);
        folderView.setText(zipFile.getName() + (currentSubPath != null ? "!" + currentSubPath : ""));
        folderView.setTextSize(13);
        layout.addView(folderView);

        TextView checkingView = new TextView(activity);
        checkingView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        checkingView.setSingleLine(true);
        checkingView.setText("...");
        checkingView.setTextSize(12);
        checkingView.setPadding(0, 8, 0, 8);
        layout.addView(checkingView);

        TextView countView = new TextView(activity);
        countView.setText("Found: 0");
        countView.setTextSize(14);
        countView.setTypeface(null, Typeface.BOLD);
        layout.addView(countView);

        TextView previewView = new TextView(activity);
        previewView.setTextSize(12);
        previewView.setMaxLines(5);
        previewView.setEllipsize(TextUtils.TruncateAt.END);
        previewView.setPadding(0, 8, 0, 0);
        layout.addView(previewView);

        builder.setView(layout);
        builder.setCancelable(false);

        boolean[] cancelled = {false};
        boolean[] stopped = {false};

        builder.setPositiveButton("Stop", (d, which) -> {
            stopped[0] = true;
            cancelled[0] = true;
        });

        builder.setNegativeButton(android.R.string.cancel, (d, which) -> {
            cancelled[0] = true;
        });

        AlertDialog progressDialog = builder.create();
        progressDialog.show();

        List<String> matchedNames = new ArrayList<>();
        long[] lastUiUpdate = {0};

        new Thread(() -> {
            List<ZipEntryInfo> results = FileSearch.searchZipEntries(zipFile, currentSubPath, query,
                    subfolders, mCase, regex, textInside, minSize, maxSize,
                    new FileSearch.SearchCallback() {
                        @Override
                        public void onFolderEntered(File folder) {
                            long now = System.currentTimeMillis();
                            if (now - lastUiUpdate[0] > 100) {
                                lastUiUpdate[0] = now;
                                String path = folder.getPath();
                                activity.handler.post(() -> folderView.setText(path));
                            }
                        }

                        @Override
                        public void onFileChecking(File file) {
                            long now = System.currentTimeMillis();
                            if (now - lastUiUpdate[0] > 100) {
                                lastUiUpdate[0] = now;
                                String name = file.getName();
                                activity.handler.post(() -> checkingView.setText(name));
                            }
                        }

                        @Override
                        public void onFileFound(File file, int totalFound) {
                            synchronized (matchedNames) {
                                if (matchedNames.size() < 10) {
                                    matchedNames.add(file.getName());
                                } else {
                                    matchedNames.remove(0);
                                    matchedNames.add(file.getName());
                                }
                                StringBuilder sb = new StringBuilder();
                                for (String n : matchedNames) {
                                    if (sb.length() > 0) sb.append("\n");
                                    sb.append("• ").append(n);
                                }
                                String preview = sb.toString();
                                activity.handler.post(() -> {
                                    countView.setText("Found: " + totalFound);
                                    previewView.setText(preview);
                                });
                            }
                        }

                        @Override
                        public boolean isCancelled() {
                            return cancelled[0];
                        }
                    });

            activity.handler.post(() -> {
                try {
                    progressDialog.dismiss();
                } catch (Exception ignored) {
                }

                if (cancelled[0] && !stopped[0]) {
                    return;
                }

                if (results.isEmpty()) {
                    Extensions.showMessage(activity, R.string.no_files_found);
                } else {
                    lastZipResults = results;
                    lastZipFile = zipFile;
                    lastZipSubPath = currentSubPath;
                    lastSearchIsPane1 = isPane1;
                    showZipSearchResultsDialog(zipFile, currentSubPath, results, isPane1);
                }
            });
        }).start();
    }

    public void showZipSearchResultsDialog(File zipFile, String currentSubPath, List<ZipEntryInfo> results, boolean isPane1) {
        lastZipResults = results;
        lastZipFile = zipFile;
        lastZipSubPath = currentSubPath;
        lastSearchIsPane1 = isPane1;
        lastSearchType = SearchType.ZIP;

        List<ZipEntryInfo> allResults = new ArrayList<>(results);
        List<ZipEntryInfo> filteredResults = new ArrayList<>(results);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16);
        header.setPadding(pad, pad, pad, dp(8));

        TextView titleText = new TextView(activity);
        titleText.setText("search results (" + filteredResults.size() + ")");
        titleText.setTextSize(18);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(titleText);

        ImageButton filterBtn = new ImageButton(activity, null, android.R.attr.borderlessButtonStyle);
        filterBtn.setImageResource(R.drawable.baseline_search_24);
        filterBtn.setContentDescription(activity.getString(R.string.filter));
        header.addView(filterBtn);
        root.addView(header);

        EditText searchInput = new EditText(activity);
        searchInput.setHint(activity.getString(R.string.filter));
        searchInput.setSingleLine(true);
        searchInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_search_24, 0, 0, 0);
        searchInput.setCompoundDrawablePadding(dp(8));
        searchInput.setPadding(pad, dp(8), pad, dp(8));
        searchInput.setVisibility(View.GONE);
        root.addView(searchInput);

        filterBtn.setOnClickListener(v -> {
            if (searchInput.getVisibility() == View.VISIBLE) {
                searchInput.setVisibility(View.GONE);
                searchInput.setText("");
            } else {
                searchInput.setVisibility(View.VISIBLE);
                searchInput.requestFocus();
            }
        });

        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        list.setLayoutParams(listParams);
        root.addView(list);

        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(12), dp(4), dp(12), dp(4));

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1.0f);
        spacer.setLayoutParams(spacerLp);
        bottomBar.addView(spacer);

        Button clearBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        clearBtn.setText("CLEAR");
        clearBtn.setTextSize(13);
        bottomBar.addView(clearBtn);

        Button closeBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        closeBtn.setText("CLOSE");
        closeBtn.setTextSize(13);
        bottomBar.addView(closeBtn);

        root.addView(bottomBar);

        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setView(root)
                .create();

        activeSearchDialog = dialog;
        shouldRestoreSearchDialogOnResume = true;

        dialog.setOnDismissListener(d -> {
            if (activeSearchDialog == dialog) {
                activeSearchDialog = null;
            }
        });

        clearBtn.setOnClickListener(v -> {
            shouldRestoreSearchDialogOnResume = false;
            searchResultsScrollState = null;
            clearLastSearchResults();
            dialog.dismiss();
        });

        closeBtn.setOnClickListener(v -> {
            shouldRestoreSearchDialogOnResume = false;
            if (list.getLayoutManager() != null) {
                searchResultsScrollState = list.getLayoutManager().onSaveInstanceState();
            }
            dialog.dismiss();
        });

        RecyclerView.Adapter<?> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(activity);
                row.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(8), dp(16), dp(8));

                ImageView icon = new ImageView(activity);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(28), dp(28));
                icon.setLayoutParams(iconLp);
                icon.setImageResource(R.drawable.baseline_search_24);
                row.addView(icon);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textLp.setMarginStart(dp(12));
                textCol.setLayoutParams(textLp);

                TextView title = new TextView(activity);
                title.setTextSize(14);
                title.setTypeface(null, Typeface.BOLD);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                textCol.addView(title);

                TextView sub = new TextView(activity);
                sub.setTextSize(12);
                sub.setTextColor(Color.GRAY);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.START);
                textCol.addView(sub);

                row.addView(textCol);
                return new RecyclerView.ViewHolder(row) {};
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ZipEntryInfo entry = filteredResults.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                ImageView icon = (ImageView) row.getChildAt(0);
                LinearLayout textCol = (LinearLayout) row.getChildAt(1);
                TextView title = (TextView) textCol.getChildAt(0);
                TextView sub = (TextView) textCol.getChildAt(1);

                title.setText(entry.getName());
                sub.setText(entry.getFullPath());
                if (entry.isDirectory()) {
                    icon.setImageResource(R.drawable.folder_24px);
                } else {
                    icon.setImageResource(R.drawable.ic_link_24);
                }

                row.setOnClickListener(v -> {
                    if (list.getLayoutManager() != null) {
                        searchResultsScrollState = list.getLayoutManager().onSaveInstanceState();
                    }
                    if (entry.isDirectory()) {
                        activity.clearGreen(isPane1);
                        activity.setSelectedPane(isPane1 ? 1 : 2);
                        activity.loadZipFolderInPane(entry.getZipFile(), entry.getFullPath(), isPane1, false);
                    } else {
                        MainFilesArrayAdapter a = activity.getCurrentFilesAdapter();
                        if (a == null) a = activity.paneFilesAdapter(isPane1);
                        if (a != null && a.getFileOperations() != null) {
                            a.getFileOperations().handleZipEntryClick(entry);
                        }
                    }
                });
            }

            @Override
            public int getItemCount() {
                return filteredResults.size();
            }
        };
        list.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                filteredResults.clear();
                for (ZipEntryInfo entry : allResults) {
                    if (q.isEmpty() || entry.getName().toLowerCase(Locale.ROOT).contains(q)
                            || entry.getFullPath().toLowerCase(Locale.ROOT).contains(q)) {
                        filteredResults.add(entry);
                    }
                }
                titleText.setText("search results (" + filteredResults.size() + ")");
                adapter.notifyDataSetChanged();
            }
        });

        if (searchResultsScrollState != null) {
            list.post(() -> {
                if (list.getLayoutManager() != null && searchResultsScrollState != null) {
                    list.getLayoutManager().onRestoreInstanceState(searchResultsScrollState);
                }
            });
        }

        activity.dialogUtil.styleAlertDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    public void showFindInFilesDialog() {
        boolean isPane1 = activity.lastPaneSelected == 1;
        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
        File targetFile = null;
        if (a instanceof MainFilesArrayAdapter adapter) {
            if (adapter.isInZip && adapter.values != null && adapter.values.length > 0 && adapter.values[0] instanceof ZipEntryInfo) {
                targetFile = ((ZipEntryInfo) adapter.values[0]).getZipFile();
            }
        }
        if (targetFile == null) {
            targetFile = isPane1 ? activity.pane1Folder : activity.pane2Folder;
        }
        if (targetFile == null) {
            Extensions.showMessage(activity, R.string.find_needs_folder);
            return;
        }
        File startDir = targetFile;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad / 2, pad, 0);
        TextInputLayout box = UiFields.box(activity, "Text to find");
        EditText queryInput = UiFields.field(box, InputType.TYPE_CLASS_TEXT);
        root.addView(box);
        CheckBox cbSubfolders = new CheckBox(activity);
        cbSubfolders.setText(activity.getString(R.string.search_subfolders));
        cbSubfolders.setChecked(true);
        root.addView(cbSubfolders);
        CheckBox cbCase = new CheckBox(activity);
        cbCase.setText(activity.getString(R.string.match_case));
        CheckBox cbRegex = new CheckBox(activity);
        cbRegex.setText(activity.getString(R.string.regex));
        root.addView(cbCase);
        root.addView(cbRegex);
        TextView scope = new TextView(activity);
        scope.setText(startDir.getPath());
        scope.setTextSize(12);
        root.addView(scope);
        AlertDialog findDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.find_in_files))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.search_go, (d, w) -> {
                    String q = queryInput.getText() == null ? "" : queryInput.getText().toString();
                    if (q.isEmpty()) {
                        Extensions.showMessage(activity, R.string.search_query_needed);
                        return;
                    }
                    runFindInFiles(startDir, q, cbSubfolders.isChecked(), cbCase.isChecked(), cbRegex.isChecked());
                }).create();
        KeyboardUtil.focusOnShow(findDialog, queryInput, false);
        findDialog.show();
    }

    private void runFindInFiles(File startDir, String query, boolean subfolders, boolean matchCase, boolean regex) {
        if (regex) {
            try {
                Pattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                Extensions.showMessage(activity, R.string.invalid_regex);
                return;
            }
        }
        ProgressManager pm = new ProgressManager(activity, true).show();
        pm.setText(activity.rss.getString(R.string.searching));
        new Thread(() -> {
            List<ContentHit> hits = FileSearch.findInFiles(startDir, query, subfolders, matchCase, regex,
                    scanned -> {
                        if (scanned % 50 == 0 && pm.dialog != null && pm.dialog.isShowing()) {
                            pm.setText(scanned + " files…");
                        }
                    });
            pm.dismiss();
            activity.handler.post(() -> {
                if (hits.isEmpty()) {
                    Extensions.showMessage(activity, R.string.no_files_found);
                    return;
                }
                showContentHitsDialog(hits, query, regex, matchCase);
            });
        }).start();
    }

    public void showXmlSearchDialog() {
        boolean isPane1 = activity.lastPaneSelected == 1;
        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
        File targetFile = null;
        if (a instanceof MainFilesArrayAdapter adapter) {
            if (adapter.isInZip && adapter.values != null && adapter.values.length > 0 && adapter.values[0] instanceof ZipEntryInfo) {
                targetFile = ((ZipEntryInfo) adapter.values[0]).getZipFile();
            }
        }
        if (targetFile == null) {
            targetFile = isPane1 ? activity.pane1Folder : activity.pane2Folder;
        }

        File searchTarget = targetFile;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad / 2, pad, 0);

        TextInputLayout box = UiFields.box(activity, "Text to find");
        EditText queryInput = UiFields.field(box, InputType.TYPE_CLASS_TEXT);
        if (lastXmlQuery != null && !lastXmlQuery.isEmpty()) {
            queryInput.setText(lastXmlQuery);
            queryInput.setSelection(lastXmlQuery.length());
        }
        root.addView(box);

        CheckBox cbSubfolders = new CheckBox(activity);
        cbSubfolders.setText(activity.getString(R.string.search_subfolders));
        cbSubfolders.setChecked(true);
        root.addView(cbSubfolders);

        AlertDialog xmlDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.xml_search))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String q = queryInput.getText() == null ? "" : queryInput.getText().toString().trim();
                    if (q.isEmpty()) {
                        Extensions.showMessage(activity, R.string.search_query_needed);
                        return;
                    }
                    runXmlSearch(searchTarget, q, cbSubfolders.isChecked(), false, false);
                }).create();
        KeyboardUtil.focusOnShow(xmlDialog, queryInput, false);
        xmlDialog.show();
    }

    private void runXmlSearch(File searchTarget, String query, boolean searchSubfolders, boolean matchCase, boolean regex) {
        if (regex) {
            try {
                Pattern.compile(query, matchCase ? 0 : Pattern.CASE_INSENSITIVE);
            } catch (Exception e) {
                Extensions.showMessage(activity, R.string.invalid_regex);
                return;
            }
        }
        ProgressManager pm = new ProgressManager(activity, true).show();
        pm.setText(activity.rss.getString(R.string.searching));
        new Thread(() -> {
            List<ContentHit> hits = FileSearch.searchInXmlFiles(searchTarget, query, searchSubfolders, matchCase, regex,
                    scanned -> {
                        if (scanned % 50 == 0 && pm.dialog != null && pm.dialog.isShowing()) {
                            pm.setText(scanned + " XML files…");
                        }
                    });
            pm.dismiss();
            activity.handler.post(() -> {
                if (hits.isEmpty()) {
                    Extensions.showMessage(activity, R.string.no_files_found);
                    return;
                }
                lastXmlHits = hits;
                lastXmlQuery = query;
                lastXmlRegex = regex;
                lastXmlMatchCase = matchCase;
                lastSearchType = SearchType.XML;
                showContentHitsDialog(hits, query, regex, matchCase);
            });
        }).start();
    }

    private static class XmlFileGroup {
        final String displayName;
        final String fileName;
        final String directoryPath;
        final List<ContentHit> hits = new ArrayList<>();

        XmlFileGroup(String displayName) {
            this.displayName = displayName;
            int lastSlash = displayName.lastIndexOf('/');
            if (lastSlash >= 0) {
                this.fileName = displayName.substring(lastSlash + 1);
                this.directoryPath = "/" + displayName.substring(0, lastSlash);
            } else {
                this.fileName = displayName;
                this.directoryPath = "/";
            }
        }
    }

    private void showContentHitsDialog(List<ContentHit> hits, String query, boolean regex, boolean matchCase) {
        LinkedHashMap<String, XmlFileGroup> groupsMap = new LinkedHashMap<>();
        for (ContentHit hit : hits) {
            String path = (hit.displayPath() != null && !hit.displayPath().isEmpty()) ? hit.displayPath() : hit.file().getName();
            XmlFileGroup group = groupsMap.get(path);
            if (group == null) {
                group = new XmlFileGroup(path);
                groupsMap.put(path, group);
            }
            group.hits.add(hit);
        }
        List<XmlFileGroup> allGroups = new ArrayList<>(groupsMap.values());
        List<XmlFileGroup> filteredGroups = new ArrayList<>(allGroups);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16);
        header.setPadding(pad, pad, pad, dp(8));

        TextView titleText = new TextView(activity);
        titleText.setText("search results (" + filteredGroups.size() + ")");
        titleText.setTextSize(18);
        titleText.setTypeface(null, Typeface.BOLD);
        titleText.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        header.addView(titleText);

        ImageButton filterBtn = new ImageButton(activity, null, android.R.attr.borderlessButtonStyle);
        filterBtn.setImageResource(R.drawable.baseline_search_24);
        filterBtn.setContentDescription(activity.getString(R.string.filter));
        header.addView(filterBtn);
        root.addView(header);

        EditText searchInput = new EditText(activity);
        searchInput.setHint(activity.getString(R.string.filter));
        searchInput.setSingleLine(true);
        searchInput.setCompoundDrawablesWithIntrinsicBounds(R.drawable.baseline_search_24, 0, 0, 0);
        searchInput.setCompoundDrawablePadding(dp(8));
        searchInput.setPadding(pad, dp(8), pad, dp(8));
        searchInput.setVisibility(View.GONE);
        root.addView(searchInput);

        filterBtn.setOnClickListener(v -> {
            if (searchInput.getVisibility() == View.VISIBLE) {
                searchInput.setVisibility(View.GONE);
                searchInput.setText("");
            } else {
                searchInput.setVisibility(View.VISIBLE);
                searchInput.requestFocus();
            }
        });

        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        list.setLayoutParams(listParams);
        root.addView(list);

        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(dp(12), dp(4), dp(12), dp(4));

        View spacer = new View(activity);
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1.0f);
        spacer.setLayoutParams(spacerLp);
        bottomBar.addView(spacer);

        Button clearBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        clearBtn.setText("CLEAR");
        clearBtn.setTextSize(13);
        bottomBar.addView(clearBtn);

        Button closeBtn = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        closeBtn.setText("CLOSE");
        closeBtn.setTextSize(13);
        bottomBar.addView(closeBtn);

        root.addView(bottomBar);

        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setView(root)
                .create();

        clearBtn.setOnClickListener(v -> {
            shouldRestoreSearchDialogOnResume = false;
            lastXmlHits = null;
            lastXmlQuery = null;
            dialog.dismiss();
        });

        closeBtn.setOnClickListener(v -> {
            shouldRestoreSearchDialogOnResume = false;
            dialog.dismiss();
        });

        activeSearchDialog = dialog;
        shouldRestoreSearchDialogOnResume = true;

        dialog.setOnDismissListener(d -> {
            if (activeSearchDialog == dialog) {
                activeSearchDialog = null;
            }
        });
        RecyclerView.Adapter<?> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(activity);
                row.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (12 * activity.getResources().getDisplayMetrics().density + 0.5f);
                row.setPadding(pad, dp(8), pad, dp(8));

                ImageView icon = new ImageView(activity);
                int iconSize = (int) (24 * activity.getResources().getDisplayMetrics().density + 0.5f);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconParams.rightMargin = (int) (12 * activity.getResources().getDisplayMetrics().density + 0.5f);
                icon.setLayoutParams(iconParams);
                icon.setImageResource(R.drawable.code_24px);
                icon.setColorFilter(0xFF3860AF);
                row.addView(icon);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                textCol.setLayoutParams(colParams);

                TextView title = new TextView(activity);
                title.setTextSize(14);
                title.setTypeface(null, Typeface.BOLD);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.MIDDLE);

                TextView sub = new TextView(activity);
                sub.setTextSize(12);
                sub.setTextColor(0xFF888888);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.START);

                textCol.addView(title);
                textCol.addView(sub);
                row.addView(textCol);

                return new RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                XmlFileGroup group = filteredGroups.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                LinearLayout textCol = (LinearLayout) row.getChildAt(1);
                TextView title = (TextView) textCol.getChildAt(0);
                TextView sub = (TextView) textCol.getChildAt(1);

                String countSuffix = group.hits.size() > 1 ? " (" + group.hits.size() + ")" : "";
                title.setText(group.fileName + countSuffix);
                sub.setText(group.directoryPath);

                row.setOnClickListener(v -> {
                    if (!group.hits.isEmpty()) {
                        openContentHit(group.hits.get(0), query, regex, matchCase);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return filteredGroups.size();
            }
        };
        list.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                filteredGroups.clear();
                for (XmlFileGroup g : allGroups) {
                    if (q.isEmpty() || g.fileName.toLowerCase(Locale.ROOT).contains(q)
                            || g.directoryPath.toLowerCase(Locale.ROOT).contains(q)) {
                        filteredGroups.add(g);
                    }
                }
                titleText.setText("search results (" + filteredGroups.size() + ")");
                adapter.notifyDataSetChanged();
            }
        });

        activity.dialogUtil.styleAlertDialog(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void openContentHit(ContentHit hit, String query, boolean regex, boolean matchCase) {
        new Thread(() -> {
            try {
                File target = hit.file();
                String entryPath = hit.displayPath();
                Intent intent = new Intent(activity, TextEditorActivity.class);
                String searchQuery = query;
                boolean isRegex = regex;
                if (!isRegex && (query.contains("*") || query.contains("?"))) {
                    Pattern p = FileSearch.compilePattern(query, matchCase, false);
                    if (p != null) {
                        searchQuery = p.pattern();
                        isRegex = true;
                    }
                }
                intent.putExtra("search", searchQuery);
                intent.putExtra("searchRegex", isRegex);
                intent.putExtra("searchMatchCase", matchCase);

                boolean isArchive = target.isFile() && (target.getName().toLowerCase().endsWith(".apk")
                        || target.getName().toLowerCase().endsWith(".zip"))
                        && entryPath != null && !entryPath.isEmpty() && !entryPath.equals(target.getName());

                if (isArchive) {
                    try (ZipFile zf = new ZipFile(target)) {
                        ZipEntry ze = zf.getEntry(entryPath);
                        if (ze == null) return;
                        File cacheDir = new File(activity.getCacheDir(), "xml_search_" + System.currentTimeMillis());
                        cacheDir.mkdirs();
                        String entryFileName = new File(entryPath).getName();
                        File tempFile = new File(cacheDir, entryFileName);
                        try (InputStream is = zf.getInputStream(ze);
                             FileOutputStream fos = new FileOutputStream(tempFile)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                        }

                        intent.putExtra("path", tempFile.getAbsolutePath());
                        intent.putExtra("zf", target.getAbsolutePath());
                        intent.putExtra("zipEntryPath", entryPath);

                        boolean isAxml = false;
                        try (InputStream is = new FileInputStream(tempFile)) {
                            isAxml = FileUtils.isAxml(is);
                        }
                        if (isAxml) {
                            intent.putExtra("axml", true);
                            ZipEntry rssEntry = zf.getEntry("resources.arsc");
                            if (rssEntry != null) {
                                File tmpRss = new File(cacheDir, "resources.arsc");
                                try (InputStream ris = zf.getInputStream(rssEntry);
                                     FileOutputStream rfos = new FileOutputStream(tmpRss)) {
                                    byte[] buf = new byte[8192];
                                    int n;
                                    while ((n = ris.read(buf)) != -1) rfos.write(buf, 0, n);
                                }
                                intent.putExtra("rssPath", tmpRss.getAbsolutePath());
                                try (InputStream is2 = new FileInputStream(tempFile);
                                     InputStream ris2 = new FileInputStream(tmpRss)) {
                                    ResourceTableParser rtp = new ResourceTableParser(ris2);
                                    List<ResEntry> entries = rtp.parse();
                                    aXMLDecoder decoder = new aXMLDecoder(is2, entries);
                                    intent.putExtra(Intent.EXTRA_TEXT, decoder.decodeAsString().trim());
                                } catch (Exception ignored) {
                                }
                            } else {
                                try (InputStream is2 = new FileInputStream(tempFile)) {
                                    aXMLDecoder decoder = new aXMLDecoder(is2);
                                    intent.putExtra(Intent.EXTRA_TEXT, decoder.decodeAsString().trim());
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                } else {
                    File file = target;
                    intent.putExtra("path", file.getAbsolutePath());
                    boolean isAxml = false;
                    boolean isAbx = false;
                    try (InputStream is = FileUtils.getInputStream(file)) {
                        isAbx = FileUtils.isAbx(is);
                    } catch (Exception ignored) {}
                    if (!isAbx) {
                        try (InputStream is = FileUtils.getInputStream(file)) {
                            isAxml = FileUtils.isAxml(is);
                        } catch (Exception ignored) {}
                    }
                    if (isAbx) {
                        try (InputStream is = FileUtils.getInputStream(file)) {
                            intent.putExtra("abx", true);
                            intent.putExtra(Intent.EXTRA_TEXT, AbxUtil.decodeToString(is).trim());
                        } catch (Exception ignored) {}
                    } else if (isAxml) {
                        try (InputStream is = FileUtils.getInputStream(file)) {
                            intent.putExtra("axml", true);
                            intent.putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is).decodeAsString().trim());
                        } catch (Exception ignored) {}
                    }
                }
                activity.runOnUiThread(() -> activity.startActivityForResult(intent, 757));
            } catch (Exception e) {
                new ErrorUtil(activity).showError(e);
            }
        }).start();
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}

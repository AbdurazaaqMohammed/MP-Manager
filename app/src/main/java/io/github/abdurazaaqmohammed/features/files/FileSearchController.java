package io.github.abdurazaaqmohammed.features.files;

import android.content.Intent;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputLayout;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.domain.files.ContentHit;
import io.github.abdurazaaqmohammed.domain.files.FileSearch;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SearchHistoryDropdown;
import io.github.abdurazaaqmohammed.utils.SearchHistoryHelper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

/**
 * File search + find-in-files dialogs extracted from MainActivity.
 * Matching/scanning math lives in domain.files.FileSearch.
 */
public class FileSearchController {

    private final MainActivity activity;

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
            Extensions.showMessage(activity, R.string.search_zip_unsupported);
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

        ProgressManager pm = new ProgressManager(activity, true).show();
        pm.setText(activity.rss.getString(R.string.searching));
        final String finalQuery = query;

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
                    });
            pm.dismiss();
            activity.handler.post(() -> {
                if (results.isEmpty()) Extensions.showMessage(activity, R.string.no_files_found);
                else {
                    File[] resArray = results.toArray(new File[0]);
                    activity.setCurrentFolder(startDir.getPath() + " (Search Results)", Arrays.asList(resArray));
                    RecyclerView pane = activity.findViewById(isPane1 ? R.id.listViewPane1 : R.id.listViewPane2);
                    pane.setAdapter(new MainFilesArrayAdapter(activity, resArray, startDir, isPane1, false, null));
                }
            });
        }).start();
    }

    public void showFindInFilesDialog() {
        boolean isPane1 = activity.lastPaneSelected == 1;
        RecyclerView.Adapter a = activity.getCurrentPane().getAdapter();
        if (!(a instanceof MainFilesArrayAdapter)) {
            Extensions.showMessage(activity, R.string.find_needs_folder);
            return;
        }
        if (((MainFilesArrayAdapter) a).isInZip) {
            Extensions.showMessage(activity, R.string.find_needs_folder);
            return;
        }
        File startDir = isPane1 ? activity.pane1Folder : activity.pane2Folder;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad / 2, pad, 0);
        TextInputLayout box = UiFields.box(activity, "Text to find");
        EditText queryInput = UiFields.field(box, InputType.TYPE_CLASS_TEXT);
        root.addView(box);
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
        UIKit.dialog(activity)
                .setTitle(activity.getString(R.string.find_in_files))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.search_go, (d, w) -> {
                    String q = queryInput.getText() == null ? "" : queryInput.getText().toString();
                    if (q.isEmpty()) {
                        Extensions.showMessage(activity, R.string.search_query_needed);
                        return;
                    }
                    runFindInFiles(startDir, q, cbCase.isChecked(), cbRegex.isChecked());
                }).show();
    }

    private void runFindInFiles(File startDir, String query, boolean matchCase, boolean regex) {
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
            List<ContentHit> hits = FileSearch.findInFiles(startDir, query, matchCase, regex,
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

    private void showContentHitsDialog(List<ContentHit> hits, String query, boolean regex, boolean matchCase) {
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        AlertDialog dialog = activity.dialogUtil.getDialogBuilder()
                .setTitle(activity.getString(R.string.matches_x, hits.size()))
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        list.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (12 * activity.getResources().getDisplayMetrics().density + 0.5f);
                row.setPadding(pad, dp(8), pad, dp(8));
                TextView title = new TextView(activity);
                title.setTextSize(14);
                title.setSingleLine(true);
                title.setEllipsize(TextUtils.TruncateAt.END);
                TextView sub = new TextView(activity);
                sub.setTextSize(12);
                sub.setTypeface(Typeface.MONOSPACE);
                sub.setSingleLine(true);
                sub.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(title);
                row.addView(sub);
                return new RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ContentHit hit = hits.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                ((TextView) row.getChildAt(0)).setText(hit.file().getName() + " :" + hit.line());
                ((TextView) row.getChildAt(1)).setText(hit.snippet());
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    activity.startActivity(new Intent(activity, TextEditorActivity.class)
                            .putExtra("path", hit.file().getAbsolutePath())
                            .putExtra("search", query)
                            .putExtra("searchRegex", regex)
                            .putExtra("searchMatchCase", matchCase));
                });
            }

            @Override
            public int getItemCount() {
                return hits.size();
            }
        });
        activity.dialogUtil.styleAlertDialog(dialog);
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}

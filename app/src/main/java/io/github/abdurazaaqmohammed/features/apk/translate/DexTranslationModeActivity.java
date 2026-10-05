package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.features.apk.ApkResultHandler;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * MT Manager's dex translation mode.
 *
 * <p>The dex counterpart of {@link ArscTranslationModeActivity}, reached from the "Open with"
 * list on a dex entry. The picker before this screen decides which dex files are in play; from
 * there the screen is deliberately the same shape as the arsc one - one literal per line, a tap
 * opens the editor, the five-icon bar along the bottom - because a translator should not have to
 * learn two interfaces for the same job.
 *
 * <p>What differs is the data. An arsc config has one value per key, so a staged translation maps
 * onto it directly. A dex literal is interned in a constant pool and referenced from wherever it
 * is used, so one edit changes every occurrence in every selected dex, and applying means a smali
 * round trip over the whole file ({@link DexStringsTranslator}). That is why this screen says
 * nothing about which classes are affected: it cannot know cheaply, and MT does not either.
 *
 * <p>Applying hands the APK back through {@code setResult(757)} so the file list keeps owning the
 * backup, the zip injection and the signing prompt, exactly as the arsc path does.
 */
public class DexTranslationModeActivity extends BaseActivity {

    private MaterialToolbar toolbar;
    private LinearProgressIndicator progress;
    private TextView configLine;
    private TextView engineLine;
    private TextView pathLine;

    private TranslateRowAdapter entryAdapter;
    private final List<TranslateRow> allRows = new ArrayList<>();
    private DexStringsTranslator translator;
    private List<File> dexFiles = new ArrayList<>();
    private String apkPath;
    private String engineId = TranslateStore.ENGINE_MANUAL;
    private Locale uiLocale = Locale.getDefault();
    private boolean loading;
    private boolean modified;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        ArrayList<String> paths = intent.getStringArrayListExtra("dex_paths");
        apkPath = intent.getStringExtra("apkPath");
        if (paths == null || paths.isEmpty()) {
            finish();
            return;
        }
        for (String p : paths) dexFiles.add(new File(p));
        uiLocale = Locale.getDefault();
        engineId = TranslateStore.engine(this);

        setContentView(R.layout.activity_dex_translation_mode);
        toolbar = findViewById(R.id.xlate_toolbar);
        toolbar.inflateMenu(R.menu.menu_dex_translation_mode);
        toolbar.setOnMenuItemClickListener(this::onMenu);
        toolbar.setNavigationOnClickListener(v -> confirmExit());

        progress = findViewById(R.id.xlate_progress);
        progress.setVisibility(View.GONE);
        pathLine = findViewById(R.id.xlate_path_line);
        configLine = findViewById(R.id.xlate_config_line);
        engineLine = findViewById(R.id.xlate_engine_line);

        setupList();
        load();
    }

    private void setupList() {
        RecyclerView list = findViewById(R.id.xlate_strings_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        entryAdapter = new TranslateRowAdapter(new TranslateRowAdapter.Listener() {
            @Override
            public void onSelectionChanged() {
                refreshCounts();
            }

            @Override
            public void onRowClicked(TranslateRow row) {
                showRowEditor(row);
            }
        });
        list.setAdapter(entryAdapter);

        findViewById(R.id.xlate_bar_select_all).setOnClickListener(v -> {
            entryAdapter.selectAll(true);
            refreshCounts();
        });
        findViewById(R.id.xlate_bar_edit).setOnClickListener(v -> editFirstSelected());
        findViewById(R.id.xlate_bar_multi).setOnClickListener(v -> {
            entryAdapter.setMultiSelect(!entryAdapter.isMultiSelect());
            refreshCounts();
        });
        findViewById(R.id.xlate_bar_translate).setOnClickListener(v -> runEngine());
        findViewById(R.id.xlate_bar_filter).setOnClickListener(v -> {
            EditText search = findViewById(R.id.xlate_strings_search);
            search.setVisibility(search.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        });

        EditText search = findViewById(R.id.xlate_strings_search);
        search.addTextChangedListener(watcher(this::refreshFilter));
    }

    /**
     * Reads every literal out of the selected dex files.
     *
     * <p>Two passes, because a multidex APK holds tens of thousands of literals and almost none of
     * them are prose: the structural ones are dropped before they reach the list. Filtering while
     * loading rather than in the adapter keeps the adapter from holding tens of thousands of rows
     * nobody will scroll to.
     */
    private void load() {
        if (loading) return;
        loading = true;
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                DexStringsTranslator t = DexStringsTranslator.open(
                        apkPath == null ? null : new File(apkPath), pathsOf(dexFiles));
                List<String> all = t.literals();
                List<TranslateRow> rows = new ArrayList<>();
                int skipped = 0;
                for (String literal : all) {
                    if (LiteralFilter.isProse(literal)) {
                        // key == the literal itself: a dex string has no name to show, and the
                        // original is what the screen lists.
                        rows.add(new TranslateRow(literal, "string", "", literal));
                    } else {
                        skipped++;
                    }
                }
                rows.sort(String::compareTo);
                final DexStringsTranslator opened = t;
                final int hidden = skipped;
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    translator = opened;
                    allRows.clear();
                    allRows.addAll(rows);
                    entryAdapter.submit(rows);
                    modified = false;
                    updateHeader(hidden);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    new ErrorUtil(this).showError(e);
                });
            }
        }).start();
    }

    private static List<String> pathsOf(List<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getAbsolutePath());
        return out;
    }

    private void refreshFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<TranslateRow> filtered = new ArrayList<>();
        for (TranslateRow row : allRows) {
            if (q.isEmpty() || row.searchKey().contains(q)) filtered.add(row);
        }
        entryAdapter.submit(filtered);
        refreshCounts();
    }

    private void updateHeader(int hiddenByFilter) {
        if (translator == null) return;
        configLine.setText(getString(R.string.xlate_dex_entries, translator.entries().size()));
        String engine = getString(R.string.xlate_engine_line, engineLabel(), sourceLabel());
        // Says how many literals the filter hid. On an arsc config that number is small; here it
        // is usually the majority, and a user who expected 30000 rows deserves to know why not.
        engineLine.setText(hiddenByFilter > 0
                ? engine + "  ·  " + getString(R.string.xlate_dex_hidden, hiddenByFilter)
                : engine);
        pathLine.setText(apkPath == null ? translator.entrySummary() : new File(apkPath).getName());
        refreshCounts();
    }

    private String sourceLabel() {
        return Locales.byBcp47(TranslateStore.sourceBcp47(this)).label;
    }

    private String engineLabel() {
        return switch (engineId) {
            case TranslateStore.ENGINE_GLOSSARY -> getString(R.string.engine_glossary);
            case TranslateStore.ENGINE_ONLINE -> getString(R.string.engine_online);
            default -> getString(R.string.engine_manual);
        };
    }

    private void markModified() {
        if (modified) return;
        modified = true;
        refreshCounts();
    }

    /** Counter under the title, matching the arsc screen; selected count while multi-selecting. */
    private void refreshCounts() {
        if (toolbar == null) return;
        if (entryAdapter.isMultiSelect()) {
            toolbar.setSubtitle(getString(R.string.xlate_multi_count, entryAdapter.selectedCount()));
            return;
        }
        int staged = 0;
        for (TranslateRow row : allRows) if (row.isApplicable()) staged++;
        String counter = getString(R.string.xlate_strings_count,
                entryAdapter.getItemCount(), staged, allRows.size());
        toolbar.setSubtitle((modified ? "* " : "") + counter);
    }

    // ---------------------------------------------------------------- editing

    private void showRowEditor(TranslateRow row) {
        if (row == null) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_xlate_row_edit, null);
        ((TextView) view.findViewById(R.id.xlate_detail_key)).setText(row.current);
        ((TextView) view.findViewById(R.id.xlate_detail_source)).setText(row.current);
        List<String> tokens = FormatGuard.tokens(row.current);
        ((TextView) view.findViewById(R.id.xlate_detail_tokens)).setText(tokens.isEmpty()
                ? getString(R.string.xlate_no_tokens)
                : getString(R.string.xlate_tokens, String.join("  ", tokens)));

        TextInputEditText input = view.findViewById(R.id.xlate_detail_input);
        input.setText(row.getTranslation());
        input.setSelection(input.getText() == null ? 0 : input.getText().length());

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_edit_title)
                .setView(view)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    row.setTranslation(input.getText() == null ? "" : input.getText().toString(),
                            TranslateRow.Origin.MANUAL);
                    markModified();
                    entryAdapter.notifyDataSetChanged();
                    refreshCounts();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void editFirstSelected() {
        List<TranslateRow> targets = entryAdapter.actionable();
        if (targets.isEmpty()) {
            Extensions.showMessage(this, R.string.xlate_nothing_to_translate);
            return;
        }
        showRowEditor(targets.get(0));
    }

    // ---------------------------------------------------------------- engine

    private TranslationEngine engine() {
        return switch (engineId) {
            case TranslateStore.ENGINE_GLOSSARY -> new GlossaryEngine(this);
            case TranslateStore.ENGINE_ONLINE -> new OnlineEngine(this);
            default -> new ManualEngine();
        };
    }

    private void runEngine() {
        if (translator == null || allRows.isEmpty()) {
            Extensions.showMessage(this, R.string.xlate_nothing_to_translate);
            return;
        }
        TranslationEngine chosen = engine();
        if (chosen.needsNetwork()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.engine_online)
                    .setMessage(R.string.xlate_network_warning)
                    .setPositiveButton(R.string.xlate_run, (d, w) -> startEngine(chosen))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        startEngine(chosen);
    }

    private void startEngine(TranslationEngine chosen) {
        // The actionable set, so multi-select scopes the run; otherwise the visible list.
        List<TranslateRow> snapshot = entryAdapter.isMultiSelect()
                ? entryAdapter.actionable()
                : new ArrayList<>(allRows);
        if (snapshot.isEmpty()) {
            Extensions.showMessage(this, R.string.xlate_nothing_to_translate);
            return;
        }
        ProgressManager pm = new ProgressManager(this, true).show();
        progress.setVisibility(View.VISIBLE);
        progress.setMax(Math.max(snapshot.size(), 1));
        // A dex literal is not stored per language, so there is no target config to derive a
        // language from. Both ends are the configured source language: an engine that maps one
        // language to itself is a no-op, and the engines here already skip rows they cannot
        // improve. What a run really does here is fill in the rows an engine can handle.
        final String language = TranslateStore.sourceBcp47(this);
        final String source = language;
        final String target = language;
        new Thread(() -> {
            try {
                chosen.translate(this, snapshot, source, target, new TranslationEngine.Callback() {
                    @Override
                    public void onProgress(int done, int total) {
                        pm.setText(getString(R.string.xlate_progress, done, total));
                        progress.setMax(Math.max(total, 1));
                        progress.setProgressCompat(done, true);
                    }

                    @Override
                    public void onTranslated(TranslateRow row) {
                    }

                    @Override
                    public void onSkipped(TranslateRow row, String reason) {
                    }

                    @Override
                    public void onMessage(String message) {
                        pm.setText(message);
                    }
                });
                pm.dismiss();
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    markModified();
                    entryAdapter.notifyDataSetChanged();
                    refreshCounts();
                    Extensions.showMessage(this, getString(R.string.xlate_engine_done, engineLabel()));
                });
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    new ErrorUtil(this).showError(e);
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- apply

    private void applyTranslations() {
        if (translator == null) return;
        // allRows, not the filtered view: an active search must not silently narrow what is saved.
        List<TranslateRow> applicable = new ArrayList<>();
        for (TranslateRow row : allRows) {
            if (row.isApplicable()) applicable.add(row);
        }
        if (applicable.isEmpty()) {
            Extensions.showMessage(this, R.string.xlate_nothing_to_apply);
            return;
        }
        String violation = firstViolation(applicable);
        if (violation != null) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.xlate_tokens_lost_title)
                    .setMessage(getString(R.string.xlate_tokens_lost, violation))
                    .setPositiveButton(R.string.xlate_apply_anyway, (d, w) -> doApply(applicable))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        doApply(applicable);
    }

    private String firstViolation(List<TranslateRow> rows) {
        for (TranslateRow row : rows) {
            String bad = FormatGuard.firstViolation(row.current, row.getTranslation());
            if (bad != null) return bad;
        }
        return null;
    }

    private void doApply(List<TranslateRow> applicable) {
        final Map<String, String> replacements = new LinkedHashMap<>();
        for (TranslateRow row : applicable) replacements.put(row.current, row.getTranslation());

        ProgressManager pm = new ProgressManager(this, true).show();
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                int written = translator.apply(replacements);
                pm.dismiss();
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    modified = false;
                    Extensions.showMessage(this, getString(R.string.xlate_applied, written, applicable.size()));
                    if (apkPath == null) {
                        // Loose dex files were rewritten where they lie; nothing to inject.
                        load();
                        return;
                    }
                    // Inside an APK the file list owns the backup, the zip injection and signing.
                    Intent result = new Intent();
                    result.setData(Uri.fromFile(new File(apkPath)));
                    result.putExtra("zipFilePath", apkPath);
                    result.putExtra("zipEntryPath", "");
                    setResult(ApkResultHandler.REQUEST_MODIFIED_ENTRY, result);
                    finish();
                });
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    new ErrorUtil(this).showError(e);
                });
            }
        }).start();
    }

    // ---------------------------------------------------------------- menu

    private boolean onMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.xlate_menu_apply) {
            applyTranslations();
        } else if (id == R.id.xlate_menu_source_language) {
            showSourceLanguagePicker();
        } else if (id == R.id.xlate_menu_settings) {
            showSettings();
        } else if (id == R.id.xlate_menu_revert) {
            entryAdapter.revertAll();
            markModified();
        }
        return true;
    }

    private void showSourceLanguagePicker() {
        List<Locales.Lang> all = Locales.all();
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) labels[i] = all.get(i).label;
        int checked = 0;
        String current = TranslateStore.sourceBcp47(this);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).bcp47.equalsIgnoreCase(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_source_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    TranslateStore.setSourceBcp47(this, all.get(which).bcp47);
                    updateHeader(0);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSettings() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_settings)
                .setMessage(R.string.xlate_dex_settings_note)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ---------------------------------------------------------------- exit

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    private void confirmExit() {
        if (!modified) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_unsaved)
                .setPositiveButton(R.string.xlate_apply, (d, w) -> applyTranslations())
                .setNeutralButton(R.string.xlate_discard, (d, w) -> finish())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static TextWatcher watcher(Consumer<String> sink) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                sink.accept(editable == null ? "" : editable.toString());
            }
        };
    }
}
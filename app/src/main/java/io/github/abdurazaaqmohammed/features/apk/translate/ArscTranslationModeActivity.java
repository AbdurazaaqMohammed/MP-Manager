package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.ClipData;
import android.content.ClipboardManager;
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
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.features.apk.ApkResultHandler;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * MT Manager's translation mode.
 *
 * <p>Opening it parses {@code resources.arsc} and lists every language series the APK was built
 * from - each {@code res/values-xx/strings.xml} it was compiled with becomes one config - so the
 * language is the first thing you choose and its strings are the second. The two live in one
 * activity because they share a single parsed resource table.
 *
 * <p>Building a language pack follows MT's documented order:
 * <ol>
 *   <li>Long-press a series and <b>copy</b> it, typing a new qualifier such as {@code -zh-rCN}.
 *       Every entry is carried over with its original value, so the copy still reads as the
 *       source language.</li>
 *   <li>Open the new series and <b>translate</b> it. This is the "the text under -zh-rCN is not
 *       Chinese yet" state MT describes.</li>
 *   <li>Apply. Inside an APK the validated table is handed back through {@code setResult(757)},
 *       so the file list keeps owning the backup, the zip injection and the signing prompt.</li>
 * </ol>
 */
public class ArscTranslationModeActivity extends BaseActivity {

    /** Which of the two pages is showing. */
    private enum Page { LANGUAGES, ENTRIES }

    private File apk;
    private String zipEntryPath = "resources.arsc";
    private ApkStringsTranslator translator;

    private MaterialToolbar toolbar;
    private LinearProgressIndicator progress;
    private View languagesPage;
    private View entriesPage;
    private LocaleAdapter localeAdapter;
    private TranslateRowAdapter entryAdapter;
    private TextView configLine;
    private TextView engineLine;
    private TextView countView;

    private Page page = Page.LANGUAGES;
    private String configQualifier = "";
    private String engineId = TranslateStore.ENGINE_MANUAL;
    private Locale uiLocale = Locale.getDefault();
    /** config -> config it was copied from, so an engine knows what language it is reading. */
    private final Map<String, String> copiedFrom = new LinkedHashMap<>();

    private final List<LocaleRow> allLocales = new ArrayList<>();
    private final List<TranslateRow> allRows = new ArrayList<>();
    private boolean loading;
    private boolean modified;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        String arscPath = intent.getStringExtra("path");
        String apkPath = intent.getStringExtra("apkPath");
        String entry = intent.getStringExtra("zipEntryPath");
        if (entry != null && !entry.isEmpty()) zipEntryPath = entry;
        if (arscPath == null && apkPath == null) {
            finish();
            return;
        }
        apk = apkPath == null ? null : new File(apkPath);
        uiLocale = Locale.getDefault();

        engineId = TranslateStore.engine(this);
        configQualifier = TranslateStore.lastConfig(this);
        copiedFrom.putAll(TranslateStore.copiedFrom(this));

        setContentView(R.layout.activity_arsc_translation_mode);
        toolbar = findViewById(R.id.xlate_toolbar);
        toolbar.inflateMenu(R.menu.menu_arsc_translation_mode);
        toolbar.setOnMenuItemClickListener(this::onMenu);

        progress = findViewById(R.id.xlate_progress);
        progress.setVisibility(View.GONE);

        languagesPage = findViewById(R.id.xlate_languages_page);
        entriesPage = findViewById(R.id.xlate_entries_page);
        configLine = findViewById(R.id.xlate_config_line);
        engineLine = findViewById(R.id.xlate_engine_line);
        countView = findViewById(R.id.xlate_count);

        setupLanguagesPage();
        setupEntriesPage();

        if (apk != null && !apk.isFile()) {
            Extensions.showMessage(this, R.string.file_no_longer_available);
            finish();
            return;
        }
        load(arscPath);
    }

    // ---------------------------------------------------------------- page 1

    private void setupLanguagesPage() {
        RecyclerView list = findViewById(R.id.xlate_languages_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        localeAdapter = new LocaleAdapter(new LocaleAdapter.Listener() {
            @Override
            public void onOpen(LocaleRow row) {
                openEntries(row);
            }

            @Override
            public void onLongPress(LocaleRow row) {
                showConfigActions(row);
            }
        });
        list.setAdapter(localeAdapter);
        findViewById(R.id.xlate_add_config).setOnClickListener(v -> showCopyConfigDialog(null));
        EditText search = findViewById(R.id.xlate_languages_search);
        search.addTextChangedListener(watcher(this::refreshLocaleFilter));
    }

    /**
     * Rebuilds the language list off the UI thread: it walks every string resource once per
     * config to count what is filled in, which is far too slow to do inline.
     */
    private void reloadLocales() {
        if (translator == null || loading) return;
        loading = true;
        progress.setVisibility(View.VISIBLE);
        final ApkStringsTranslator current = translator;
        final Map<String, String> origins = new LinkedHashMap<>(copiedFrom);
        final Locale ui = uiLocale;
        new Thread(() -> {
            List<LocaleRow> rows = new ArrayList<>();
            try {
                List<String> configs = current.configs().configs();
                // The default config is the fallback for every locale, so it always belongs on
                // the list even when a table carries no values at all.
                if (!configs.contains("")) configs.add(0, "");
                for (String qualifier : configs) {
                    String name = qualifier.isEmpty()
                            ? getString(R.string.xlate_config_default)
                            : Locales.displayName(qualifier, ui);
                    String from = origins.get(Locales.normalize(qualifier));
                    rows.add(LocaleRow.of(qualifier, name,
                            current.configs().stats(qualifier), from != null, from));
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    new ErrorUtil(this).showError(e);
                });
                return;
            }
            runOnUiThread(() -> {
                loading = false;
                progress.setVisibility(View.GONE);
                allLocales.clear();
                allLocales.addAll(rows);
                localeAdapter.submit(rows);
                if (translator != null) {
                    String pkg = translator.configs().packageName();
                    toolbar.setSubtitle(pkg == null ? "" : pkg);
                }
                if (page == Page.LANGUAGES) showPage(Page.LANGUAGES);
            });
        }).start();
    }

    private void refreshLocaleFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<LocaleRow> filtered = new ArrayList<>();
        for (LocaleRow row : allLocales) {
            if (q.isEmpty() || row.searchKey().contains(q)) filtered.add(row);
        }
        localeAdapter.submit(filtered);
    }

    // ---------------------------------------------------------------- page 2

    private void setupEntriesPage() {
        RecyclerView list = findViewById(R.id.xlate_strings_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        entryAdapter = new TranslateRowAdapter(new TranslateRowAdapter.Listener() {
            @Override
            public void onRowChanged(TranslateRow row) {
                // Fires per keystroke, so only the cheap dirty marker is refreshed here.
                markModified();
            }

            @Override
            public void onSelectionChanged() {
                markModified();
                refreshCounts();
            }

            @Override
            public void onRowClicked(TranslateRow row) {
                showRowDetail(row);
            }
        });
        list.setAdapter(entryAdapter);
        findViewById(R.id.xlate_select_all).setOnClickListener(v -> {
            entryAdapter.selectAll(true);
            markModified();
        });
        findViewById(R.id.xlate_clear_staged).setOnClickListener(v -> {
            entryAdapter.revertAll();
            markModified();
        });
        findViewById(R.id.xlate_translate).setOnClickListener(v -> runEngine());
        findViewById(R.id.xlate_apply).setOnClickListener(v -> applyTranslations());
        EditText search = findViewById(R.id.xlate_strings_search);
        search.addTextChangedListener(watcher(this::refreshEntryFilter));
    }

    private void openEntries(LocaleRow row) {
        if (row == null) return;
        configQualifier = row.qualifier;
        TranslateStore.setLastConfig(this, configQualifier);
        showPage(Page.ENTRIES);
        reloadRows();
    }

    private void reloadRows() {
        if (translator == null || loading) return;
        loading = true;
        progress.setVisibility(View.VISIBLE);
        final ApkStringsTranslator current = translator;
        final String config = configQualifier;
        final boolean skipFormatted = TranslateStore.skipFormatted(this);
        new Thread(() -> {
            List<TranslateRow> rows;
            try {
                rows = current.rows(config, skipFormatted);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    progress.setVisibility(View.GONE);
                    new ErrorUtil(this).showError(e);
                });
                return;
            }
            runOnUiThread(() -> {
                loading = false;
                progress.setVisibility(View.GONE);
                allRows.clear();
                allRows.addAll(rows);
                entryAdapter.resetDirty();
                entryAdapter.submit(rows);
                modified = false;
                updateEntryHeader();
            });
        }).start();
    }

    private void refreshEntryFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<TranslateRow> filtered = new ArrayList<>();
        for (TranslateRow row : allRows) {
            if (q.isEmpty() || row.searchKey().contains(q)) filtered.add(row);
        }
        entryAdapter.submit(filtered);
        refreshCounts();
    }

    private void updateEntryHeader() {
        String name = Locales.normalize(configQualifier).isEmpty()
                ? getString(R.string.xlate_config_default)
                : Locales.displayName(configQualifier, uiLocale);
        configLine.setText(name + "   (" + Locales.normalize(configQualifier) + ")");
        engineLine.setText(getString(R.string.xlate_engine_line, engineLabel(), sourceLabel()));
        refreshCounts();
    }

    private String sourceLabel() {
        String from = copiedFrom.get(Locales.normalize(configQualifier));
        if (from != null && !from.isEmpty()) {
            return Locales.displayName(from, uiLocale);
        }
        return Locales.byBcp47(TranslateStore.sourceBcp47(this)).label;
    }

    private void markModified() {
        if (!modified) {
            modified = true;
            toolbar.setSubtitle("*");
        }
    }

    private void refreshCounts() {
        if (countView == null) return;
        int staged = 0;
        for (TranslateRow row : allRows) {
            if (row.isApplicable()) staged++;
        }
        countView.setText(getString(R.string.xlate_strings_count,
                entryAdapter.getItemCount(), staged, allRows.size()));
    }

    // ---------------------------------------------------------------- paging

    private void showPage(Page next) {
        page = next;
        boolean languages = next == Page.LANGUAGES;
        languagesPage.setVisibility(languages ? View.VISIBLE : View.GONE);
        entriesPage.setVisibility(languages ? View.GONE : View.VISIBLE);
        toolbar.setNavigationIcon(languages ? null
                : androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setTitle(languages ? R.string.translation_mode : R.string.xlate_edit_strings);
        toolbar.setNavigationOnClickListener(languages ? null : v -> leaveEntries());
        // Only the entries page has anything to run or configure.
        for (int i = 0; i < toolbar.getMenu().size(); i++) {
            toolbar.getMenu().getItem(i).setVisible(!languages);
        }
        if (languages) {
            toolbar.setSubtitle(translator == null ? ""
                    : String.valueOf(translator.configs().packageName()));
        } else {
            updateEntryHeader();
        }
    }

    /** Leaving the entries page goes back to the language list; a save prompt comes first. */
    private void leaveEntries() {
        if (modified) {
            confirmDiscard(new Runnable() {
                @Override
                public void run() {
                    modified = false;
                    showPage(Page.LANGUAGES);
                    reloadLocales();
                }
            });
            return;
        }
        showPage(Page.LANGUAGES);
        reloadLocales();
    }

    // ---------------------------------------------------------------- loading

    private void load(String arscPath) {
        loading = true;
        progress.setVisibility(View.VISIBLE);
        final String path = arscPath;
        final File apkFile = apk;
        new Thread(() -> {
            ApkStringsTranslator opened = null;
            String error = null;
            try {
                opened = apkFile != null
                        ? ApkStringsTranslator.openFromApk(this, apkFile)
                        : ApkStringsTranslator.openArsc(new File(path));
            } catch (Exception e) {
                error = e.getMessage();
            }
            ApkStringsTranslator ready = opened;
            String message = error;
            runOnUiThread(() -> {
                loading = false;
                progress.setVisibility(View.GONE);
                if (ready == null) {
                    Extensions.showMessage(this, R.string.xlate_no_resource_table);
                    findViewById(R.id.xlate_add_config).setEnabled(false);
                    toolbar.setSubtitle(message == null ? "" : message);
                    return;
                }
                translator = ready;
                showPage(Page.LANGUAGES);
                reloadLocales();
            });
        }).start();
    }

    // ---------------------------------------------------------------- configs

    /** MT's long-press menu on a language series. */
    private void showConfigActions(LocaleRow row) {
        if (translator == null || row == null) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(getString(R.string.xlate_copy_config));
        actions.add(() -> showCopyConfigDialog(row.qualifier));
        if (!row.isDefault()) {
            labels.add(getString(R.string.xlate_delete_config));
            actions.add(() -> confirmDeleteConfig(row.qualifier));
        }
        String[] items = labels.toArray(new String[0]);
        new MaterialAlertDialogBuilder(this)
                .setTitle(row.name)
                .setItems(items, (dialog, which) -> {
                    dialog.dismiss();
                    actions.get(which).run();
                })
                .setNeutralButton(R.string.xlate_add_config, (d, w) -> showCopyConfigDialog(null))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * MT's two ways of creating a language pack, both ending in the same qualifier prompt:
     * copy an existing config (which carries its values over), or add an empty one.
     *
     * @param fromQualifier config to copy, or null to add an empty config
     */
    private void showCopyConfigDialog(String fromQualifier) {
        if (translator == null) return;
        List<String> sources = new ArrayList<>(translator.configs().configs());
        if (fromQualifier != null) {
            promptForQualifier(getString(R.string.xlate_copy_config), fromQualifier,
                    target -> createConfigFrom(fromQualifier, target));
            return;
        }
        if (sources.isEmpty()) {
            promptForQualifier(getString(R.string.xlate_add_config), "",
                    target -> addEmptyConfig(target));
            return;
        }
        String[] labels = new String[sources.size()];
        for (int i = 0; i < sources.size(); i++) labels[i] = displayName(sources.get(i));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_copy_from)
                .setItems(labels, (dialog, which) -> {
                    dialog.dismiss();
                    promptForQualifier(getString(R.string.xlate_copy_config), sources.get(which),
                            target -> createConfigFrom(sources.get(which), target));
                })
                .setNeutralButton(R.string.xlate_add_empty, (d, w) ->
                        promptForQualifier(getString(R.string.xlate_add_config), "", this::addEmptyConfig))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addEmptyConfig(String qualifier) {
        if (!translator.configs().addEmptyConfig(qualifier)) {
            Extensions.showMessage(this, R.string.xlate_config_failed);
            return;
        }
        TranslateStore.setLastConfig(this, qualifier);
        Extensions.showMessage(this, R.string.xlate_config_added);
        reloadLocales();
    }

    private void createConfigFrom(String from, String to) {
        int copied = translator.configs().copyConfig(from, to);
        if (copied < 0) {
            Extensions.showMessage(this, R.string.xlate_config_failed);
            return;
        }
        copiedFrom.put(Locales.normalize(to), Locales.normalize(from));
        TranslateStore.setCopiedFrom(this, copiedFrom);
        TranslateStore.setLastConfig(this, to);
        Extensions.showMessage(this, getString(R.string.xlate_config_copied, copied));
        reloadLocales();
    }

    private void confirmDeleteConfig(String qualifier) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_delete_config)
                .setMessage(getString(R.string.xlate_delete_config_confirm, displayName(qualifier)))
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (!translator.configs().deleteConfig(qualifier)) {
                        Extensions.showMessage(this, R.string.xlate_config_failed);
                        return;
                    }
                    copiedFrom.remove(Locales.normalize(qualifier));
                    TranslateStore.setCopiedFrom(this, copiedFrom);
                    reloadLocales();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptForQualifier(String title, String suggestion, Consumer<String> onEntered) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_xlate_qualifier, null);
        TextInputEditText input = view.findViewById(R.id.xlate_qualifier_input);
        input.setText(suggestion.isEmpty() ? "-zh-rCN" : suggestion);
        if (!suggestion.isEmpty() && input.getText() != null) {
            input.setSelection(input.getText().length());
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String typed = input.getText() == null ? "" : input.getText().toString();
                    String qualifier = normalizeTypedQualifier(typed);
                    if (qualifier == null) {
                        Extensions.showMessage(this, R.string.xlate_bad_qualifier);
                        return;
                    }
                    if (translator.configs().exists(qualifier)) {
                        Extensions.showMessage(this, R.string.xlate_config_exists);
                        return;
                    }
                    onEntered.accept(qualifier);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Accepts what a user would naturally type for a qualifier.
     *
     * @return the canonical form, or {@code null} when it is not one aapt would understand
     */
    static String normalizeTypedQualifier(String typed) {
        if (typed == null) return null;
        String q = typed.trim();
        while (q.startsWith("-")) q = q.substring(1);
        if (q.toLowerCase().startsWith("values")) {
            int dash = q.indexOf('-');
            q = dash < 0 ? "" : q.substring(dash + 1);
        }
        q = q.replace('_', '-');
        if (q.isEmpty()) return null;
        if (!q.matches("(?i)[a-z]{2,3}(-[a-z]{2,4})?(-r[a-z]{2})?(-[a-z]{4})?")) return null;
        // "-zh-rCN" is stored as "zh-rCN"; the leading dash is display sugar only.
        return Locales.normalize(q);
    }

    private String displayName(String qualifier) {
        String q = Locales.normalize(qualifier);
        return q.isEmpty() ? getString(R.string.xlate_config_default)
                : Locales.displayName(q, uiLocale);
    }

    // ---------------------------------------------------------------- engines

    private String engineLabel() {
        return switch (engineId) {
            case TranslateStore.ENGINE_GLOSSARY -> getString(R.string.engine_glossary);
            case TranslateStore.ENGINE_ONLINE -> getString(R.string.engine_online);
            default -> getString(R.string.engine_manual);
        };
    }

    private TranslationEngine engine() {
        return switch (engineId) {
            case TranslateStore.ENGINE_GLOSSARY -> new GlossaryEngine(this);
            case TranslateStore.ENGINE_ONLINE -> new OnlineEngine(this);
            default -> new ManualEngine();
        };
    }

    private void showRowDetail(TranslateRow row) {
        if (row == null) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_xlate_row, null);
        ((TextView) view.findViewById(R.id.xlate_detail_key)).setText(row.key);
        ((TextView) view.findViewById(R.id.xlate_detail_source)).setText(row.current);
        List<String> tokens = FormatGuard.tokens(row.current);
        ((TextView) view.findViewById(R.id.xlate_detail_tokens)).setText(tokens.isEmpty()
                ? getString(R.string.xlate_no_tokens)
                : getString(R.string.xlate_tokens, String.join("  ", tokens)));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_row_detail)
                .setView(view)
                .setPositiveButton(R.string.xlate_copy_translation, (d, w) -> copy(row.getTranslation()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        List<TranslateRow> snapshot = new ArrayList<>(allRows);
        ProgressManager pm = new ProgressManager(this, true).show();
        progress.setVisibility(View.VISIBLE);
        progress.setMax(Math.max(snapshot.size(), 1));
        final String target = ApkStringsTranslator.bcp47For(configQualifier);
        String picked = TranslateStore.sourceBcp47(this);
        String from = copiedFrom.get(Locales.normalize(configQualifier));
        if (from != null && !from.isEmpty()) picked = ApkStringsTranslator.bcp47For(from);
        final String source = picked;
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
                    entryAdapter.refreshValues();
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
        // allRows, not the filtered view: the active search must not silently narrow what is saved.
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
        ProgressManager pm = new ProgressManager(this, true).show();
        pm.setText(getString(R.string.xlate_writing, applicable.size()));
        final int total = applicable.size();
        new Thread(() -> {
            int written;
            File arsc;
            try {
                written = translator.apply(applicable);
                translator.save();
                arsc = translator.data().arscFile;
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
                return;
            }
            pm.dismiss();
            runOnUiThread(() -> {
                modified = false;
                Extensions.showMessage(this, getString(R.string.xlate_applied, written, total));
                if (apk == null) {
                    // A loose arsc was edited where it lies; nothing to inject.
                    reloadRows();
                    reloadLocales();
                    return;
                }
                // Inside an APK the file list owns the backup, the zip injection and signing.
                // It reads this file asynchronously after the user confirms, so it must survive.
                Intent result = new Intent();
                result.setData(Uri.fromFile(arsc));
                result.putExtra("zipFilePath", apk.getAbsolutePath());
                result.putExtra("zipEntryPath", zipEntryPath);
                setResult(ApkResultHandler.REQUEST_MODIFIED_ENTRY, result);
                finish();
            });
        }).start();
    }

    // ---------------------------------------------------------------- menu

    private boolean onMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.xlate_menu_translate) {
            runEngine();
        } else if (id == R.id.xlate_menu_add_config) {
            showCopyConfigDialog(null);
        } else if (id == R.id.xlate_menu_copy_config) {
            showCopyConfigDialog(configQualifier);
        } else if (id == R.id.xlate_menu_delete_config) {
            confirmDeleteConfig(configQualifier);
        } else if (id == R.id.xlate_menu_source_language) {
            showSourceLanguagePicker();
        } else if (id == R.id.xlate_menu_glossary) {
            showGlossaryMenu();
        } else if (id == R.id.xlate_menu_settings) {
            showSettings();
        } else if (id == R.id.xlate_menu_revert) {
            confirmRevert();
        } else {
            return false;
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
                    updateEntryHeader();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String glossaryPair() {
        String from = copiedFrom.get(Locales.normalize(configQualifier));
        String source = from == null || from.isEmpty()
                ? TranslateStore.sourceBcp47(this)
                : ApkStringsTranslator.bcp47For(from);
        return TranslateStore.pairFor(source, ApkStringsTranslator.bcp47For(configQualifier));
    }

    private void showGlossaryMenu() {
        if (translator == null) return;
        String pair = glossaryPair();
        String[] items = {
                getString(R.string.xlate_glossary_edit),
                getString(R.string.xlate_glossary_export),
                getString(R.string.xlate_glossary_stats, pair)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_glossary)
                .setItems(items, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) {
                        editGlossary(pair);
                    } else if (which == 1) {
                        exportGlossary(pair);
                    } else {
                        showGlossaryStats(pair);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void editGlossary(String pair) {
        try {
            File file = Glossary.seedUserFile(this, pair);
            startActivity(new Intent(this, TextEditorActivity.class)
                    .putExtra("path", file.getAbsolutePath()));
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void exportGlossary(String pair) {
        try {
            Glossary glossary = Glossary.load(this, pair);
            File file = new File(getCacheDir(), "glossary_export.json");
            FileUtils.copyFile(new ByteArrayInputStream(
                    glossary.toJson().getBytes(StandardCharsets.UTF_8)), file);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                            .setType("application/json")
                            .putExtra(Intent.EXTRA_STREAM, uri),
                    getString(R.string.xlate_glossary_export)));
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void showGlossaryStats(String pair) {
        Glossary glossary = Glossary.load(this, pair);
        Extensions.showMessage(this, getString(R.string.xlate_glossary_stats, pair)
                + " " + glossary.phraseCount() + "/" + glossary.wordCount());
    }

    private void showSettings() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_xlate_settings, null);
        TextInputEditText endpoint = view.findViewById(R.id.xlate_endpoint_input);
        TextInputEditText apiKey = view.findViewById(R.id.xlate_api_key_input);
        MaterialCheckBox batch = view.findViewById(R.id.xlate_batch_check);
        MaterialCheckBox skipFormatted = view.findViewById(R.id.xlate_skip_formatted_check);
        endpoint.setText(TranslateStore.endpoint(this));
        apiKey.setText(TranslateStore.apiKey(this));
        batch.setChecked(TranslateStore.allowBatch(this));
        skipFormatted.setChecked(TranslateStore.skipFormatted(this));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_settings)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    TranslateStore.setEndpoint(this, text(endpoint));
                    TranslateStore.setApiKey(this, text(apiKey));
                    TranslateStore.setAllowBatch(this, batch.isChecked());
                    TranslateStore.setSkipFormatted(this, skipFormatted.isChecked());
                    reloadRows();
                })
                .setNeutralButton(R.string.xlate_engine, (d, w) -> showEnginePicker())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showEnginePicker() {
        String[] labels = {
                getString(R.string.engine_manual),
                getString(R.string.engine_glossary),
                getString(R.string.engine_online)
        };
        String[] ids = {
                TranslateStore.ENGINE_MANUAL,
                TranslateStore.ENGINE_GLOSSARY,
                TranslateStore.ENGINE_ONLINE
        };
        int checked = 0;
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(engineId)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_engine)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    engineId = ids[which];
                    TranslateStore.setEngine(this, engineId);
                    updateEntryHeader();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    private void confirmRevert() {
        if (!modified) {
            Extensions.showMessage(this, R.string.xlate_nothing_to_revert);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_revert)
                .setMessage(R.string.xlate_revert_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> reloadRows())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmExit() {
        if (!modified) {
            finish();
            return;
        }
        confirmDiscard(this::finish);
    }

    /** Offers to keep the staged translations before throwing them away. */
    private void confirmDiscard(Runnable onDiscard) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_unsaved)
                .setPositiveButton(R.string.xlate_apply, (d, w) -> applyTranslations())
                .setNeutralButton(R.string.xlate_discard, (d, w) -> {
                    modified = false;
                    onDiscard.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void copy(String value) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("string", value));
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    @Override
    public void onBackPressed() {
        if (page == Page.ENTRIES) {
            leaveEntries();
            return;
        }
        if (modified) {
            confirmExit();
            return;
        }
        super.onBackPressed();
    }

    /** Minimal {@link TextWatcher} that only cares about the final text. */
    private static TextWatcher watcher(Consumer<String> sink) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                sink.accept(s == null ? "" : s.toString());
            }
        };
    }
}

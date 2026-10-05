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
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
 * XML translation mode for a single APK, reached from the file list by long-pressing an APK.
 *
 * <p>Two halves, because they solve different problems:
 * <ul>
 *   <li><b>XML</b> lists every {@code *.xml} inside the APK and decodes binary AXML into readable
 *       text using the APK's own resource table, so references come back as {@code @string/foo}
 *       rather than {@code @7f0d0023}. Any entry can be opened in the text editor, which
 *       re-encodes it on save and hands the result back to the file list.</li>
 *   <li><b>Strings</b> edits the compiled string resources. In a built APK those live in
 *       {@code resources.arsc} and not in any file, so a translation is staged per key and then
 *       written into the config for the target language, created if the APK never shipped it.
 *       Three engines can fill the stage: an offline glossary, an online endpoint, or nothing at
 *       all with the human typing.</li>
 * </ul>
 *
 * <p>Nothing here mutates the APK in place. Applying writes a validated arsc copy and returns it
 * through {@code setResult(757, ...)}, which is the contract the arsc and dex editors already use:
 * the file list shows the "APK updated" prompt, takes the backup, injects the file and offers
 * signing.
 */
public class ApkXmlTranslationActivity extends BaseActivity {

    private File apk;
    private ApkXmlCatalog catalog;
    /** Built on first use: parsing the resource table costs seconds on a large APK. */
    private ApkXmlCatalog decodingCatalog;
    private ApkStringsTranslator translator;

    private MaterialToolbar toolbar;
    private ViewPager2 pager;

    private View xmlPage;
    private View stringsPage;
    private ApkXmlEntryAdapter xmlAdapter;
    private TranslateRowAdapter stringsAdapter;
    private TextView xmlCount;
    private TextView stringsCount;
    private TextView langLine;
    private TextView engineLine;
    private LinearProgressIndicator progress;

    private String sourceQualifier = "";
    private String targetQualifier = "zh-rCN";
    private String engineId = TranslateStore.ENGINE_MANUAL;
    private final List<TranslateRow> allRows = new ArrayList<>();
    private boolean applying;
    private boolean rebuilding;
    private boolean modified;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String apkPath = getIntent().getStringExtra("apkPath");
        if (apkPath == null) apkPath = getIntent().getStringExtra("path");
        if (apkPath == null) {
            finish();
            return;
        }
        apk = new File(apkPath);
        if (!apk.isFile()) {
            Extensions.showMessage(this, R.string.file_no_longer_available);
            finish();
            return;
        }

        sourceQualifier = TranslateStore.sourceQualifier(this);
        targetQualifier = TranslateStore.targetQualifier(this);
        engineId = TranslateStore.engine(this);

        setContentView(R.layout.activity_apk_xml_translation);
        toolbar = findViewById(R.id.xlate_toolbar);
        toolbar.setSubtitle(apk.getName());
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> confirmExit());
        toolbar.inflateMenu(R.menu.menu_apk_xml_translation);
        toolbar.setOnMenuItemClickListener(this::onMenu);

        pager = findViewById(R.id.xlate_pager);
        xmlPage = LayoutInflater.from(this).inflate(R.layout.page_apk_xml_readable, pager, false);
        stringsPage = LayoutInflater.from(this).inflate(R.layout.page_apk_strings, pager, false);
        pager.setAdapter(new PageAdapter());
        pager.setOffscreenPageLimit(1);

        TabLayout tabs = findViewById(R.id.xlate_tabs);
        // Not a ternary: Tab has both setText(CharSequence) and setText(int), and an int-valued
        // conditional makes the call ambiguous.
        new TabLayoutMediator(tabs, pager, (tab, position) -> {
            if (position == 0) tab.setText(R.string.xlate_tab_xml);
            else tab.setText(R.string.xlate_tab_strings);
        }).attach();

        setupXmlPage();
        setupStringsPage();
        loadCatalog();
    }

    /** Hosts the two pre-inflated pages; a fragment adapter would need two fragment classes. */
    private final class PageAdapter extends RecyclerView.Adapter<PageHolder> {

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.widget.FrameLayout container = new android.widget.FrameLayout(parent.getContext());
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return new PageHolder(container);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder holder, int position) {
            // A recycled holder may already hold the other page, so re-attach rather than
            // assume an empty container.
            View page = position == 0 ? xmlPage : stringsPage;
            if (holder.page == page) return;
            holder.container.removeAllViews();
            holder.container.addView(page);
            holder.page = page;
        }

        @Override
        public int getItemCount() {
            return 2;
        }
    }

    private static final class PageHolder extends RecyclerView.ViewHolder {
        final ViewGroup container;
        View page;

        PageHolder(ViewGroup container) {
            super(container);
            this.container = container;
        }
    }

    // ---------------------------------------------------------------- pages

    private void setupXmlPage() {
        RecyclerView list = xmlPage.findViewById(R.id.xlate_xml_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        xmlAdapter = new ApkXmlEntryAdapter((item, position) -> showXmlEntry(item));
        list.setAdapter(xmlAdapter);
        xmlCount = xmlPage.findViewById(R.id.xlate_xml_count);
        EditText search = xmlPage.findViewById(R.id.xlate_xml_search);
        search.addTextChangedListener(watcher(this::refreshXmlFilter));
    }

    private void setupStringsPage() {
        RecyclerView list = stringsPage.findViewById(R.id.xlate_strings_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        stringsAdapter = new TranslateRowAdapter(new TranslateRowAdapter.Listener() {
            @Override
            public void onRowChanged(TranslateRow row) {
                // Fires on every keystroke, so only the cheap dirty marker is refreshed here;
                // recounting thousands of rows while typing would drop frames.
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
        list.setAdapter(stringsAdapter);

        langLine = stringsPage.findViewById(R.id.xlate_lang_line);
        engineLine = stringsPage.findViewById(R.id.xlate_engine_line);
        stringsCount = stringsPage.findViewById(R.id.xlate_count);
        progress = stringsPage.findViewById(R.id.xlate_progress);
        progress.setVisibility(View.GONE);

        stringsPage.findViewById(R.id.xlate_lang_bar).setOnClickListener(v -> showLanguagePicker());
        stringsPage.findViewById(R.id.xlate_select_all).setOnClickListener(v -> {
            stringsAdapter.selectAll(true);
            modified = true;
        });
        stringsPage.findViewById(R.id.xlate_clear_staged).setOnClickListener(v -> {
            stringsAdapter.clearStaged();
            modified = true;
        });
        stringsPage.findViewById(R.id.xlate_apply).setOnClickListener(v -> applyTranslations());
        EditText search = stringsPage.findViewById(R.id.xlate_strings_search);
        search.addTextChangedListener(watcher(this::refreshStringsFilter));
        updateSubtitle();
    }

    // ---------------------------------------------------------------- loading

    private void loadCatalog() {
        Extensions.showMessage(this, R.string.loading_arsc);
        new Thread(() -> {
            try {
                ApkXmlCatalog opened = ApkXmlCatalog.open(apk, false);
                ApkStringsTranslator openedTranslator = null;
                String loadError = null;
                try {
                    openedTranslator = ApkStringsTranslator.open(this, apk);
                } catch (Exception e) {
                    loadError = e.getMessage();
                }
                ApkStringsTranslator ready = openedTranslator;
                String error = loadError;
                runOnUiThread(() -> {
                    catalog = opened;
                    translator = ready;
                    xmlAdapter.submit(catalog.items());
                    xmlCount.setText(getString(R.string.xlate_xml_count, catalog.items().size()));
                    if (translator == null) {
                        onStringsUnavailable(error);
                        return;
                    }
                    if (sourceQualifier.isEmpty()
                            || !translator.existingQualifiers().contains(sourceQualifier)) {
                        sourceQualifier = translator.guessSourceQualifier();
                        TranslateStore.setSourceQualifier(this, sourceQualifier);
                    }
                    rebuildRows();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    new ErrorUtil(this).showError(e);
                    finish();
                });
            }
        }).start();
    }

    /** The APK has no usable resource table, so the strings half has nothing to work on. */
    private void onStringsUnavailable(String error) {
        stringsAdapter.submit(new ArrayList<>());
        langLine.setText(R.string.xlate_no_resource_table);
        engineLine.setText(error == null ? "" : error);
        stringsPage.findViewById(R.id.xlate_apply).setEnabled(false);
        stringsPage.findViewById(R.id.xlate_lang_bar).setEnabled(false);
    }

    /**
     * Rebuilds the row list off the UI thread: reading the arsc means walking every string
     * resource and every locale config it ships, which is far too slow to do inline.
     */
    private void rebuildRows() {
        if (translator == null) return;
        if (targetQualifier.equals(sourceQualifier)) {
            // Translating a language onto itself would overwrite the original strings.
            targetQualifier = nextQualifier(sourceQualifier);
        }
        final ApkStringsTranslator current = translator;
        final String src = sourceQualifier;
        final String dst = targetQualifier;
        final boolean onlyMissing = TranslateStore.onlyMissing(this);
        final boolean skipFormatted = TranslateStore.skipFormatted(this);
        if (rebuilding) return;
        rebuilding = true;
        progress.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<TranslateRow> rows;
            try {
                rows = current.rows(src, dst, onlyMissing, skipFormatted);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    rebuilding = false;
                    progress.setVisibility(View.GONE);
                    new ErrorUtil(this).showError(e);
                });
                return;
            }
            runOnUiThread(() -> {
                rebuilding = false;
                progress.setVisibility(View.GONE);
                allRows.clear();
                allRows.addAll(rows);
                stringsAdapter.resetDirty();
                stringsAdapter.submit(rows);
                modified = false;
                updateSubtitle();
            });
        }).start();
    }

    private String nextQualifier(String current) {
        List<Locales.Lang> all = Locales.all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).qualifier.equals(current)) {
                return all.get((i + 1) % all.size()).qualifier;
            }
        }
        return "zh-rCN";
    }

    // ---------------------------------------------------------------- xml tab

    private void refreshXmlFilter(String query) {
        if (catalog == null) return;
        List<ApkXmlCatalog.Item> filtered = ApkXmlCatalog.filter(catalog.items(), query);
        xmlAdapter.submit(filtered);
        xmlCount.setText(getString(R.string.xlate_xml_count, filtered.size()));
    }

    private void showXmlEntry(ApkXmlCatalog.Item item) {
        if (item == null) return;
        String[] options = {
                getString(R.string.xlate_view_decoded),
                getString(R.string.xlate_open_editor)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.path)
                .setItems(options, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) viewDecoded(item);
                    else openInEditor(item);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void viewDecoded(ApkXmlCatalog.Item item) {
        Extensions.showMessage(this, R.string.xlate_decoding);
        new Thread(() -> {
            try {
                // Symbolic reference names need the resource table. It is parsed once and kept,
                // because doing it per tap makes every preview feel broken on a large APK.
                if (decodingCatalog == null) decodingCatalog = ApkXmlCatalog.open(apk, true);
                String text = decodingCatalog.decode(item.path);
                runOnUiThread(() -> showDecodedDialog(item, text));
            } catch (Exception e) {
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    private void showDecodedDialog(ApkXmlCatalog.Item item, String text) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_xlate_xml, null);
        ((TextView) view.findViewById(R.id.xlate_xml_body)).setText(text);
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.path)
                .setView(view)
                .setPositiveButton(R.string.xlate_open_editor, (d, w) -> openInEditor(item))
                .setNeutralButton(R.string.copy, (d, w) -> copy(text))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Opens an entry in the text editor. The editor re-encodes AXML on save and returns the
     * modified copy through {@code setResult}, which is how the dex editor already injects its
     * changes back into an open archive.
     */
    private void openInEditor(ApkXmlCatalog.Item item) {
        try {
            File staged = catalog.stage(this, item.path);
            if (staged == null) {
                Extensions.showMessage(this, R.string.file_no_longer_available);
                return;
            }
            Intent intent = new Intent(this, TextEditorActivity.class)
                    .putExtra("path", staged.getAbsolutePath());
            if (item.binary) {
                File table = catalog.stageResourceTable(this);
                if (table != null) intent.putExtra("rssPath", table.getAbsolutePath());
                intent.putExtra("axml", true);
            }
            startActivityForResult(intent, ApkResultHandler.REQUEST_MODIFIED_ENTRY);
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    private void copy(String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("xml", text));
        } catch (Exception e) {
            new ErrorUtil(this).showError(e);
        }
    }

    // ---------------------------------------------------------------- strings tab

    private void refreshStringsFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<TranslateRow> filtered = new ArrayList<>();
        for (TranslateRow row : allRows) {
            if (q.isEmpty() || row.searchKey().contains(q)) filtered.add(row);
        }
        stringsAdapter.submit(filtered);
        updateSubtitle();
    }

    private void markModified() {
        if (!modified) {
            modified = true;
            toolbar.setSubtitle(apk.getName() + " *");
        }
    }

    private void updateSubtitle() {
        if (langLine == null) return;
        langLine.setText(getString(R.string.xlate_lang_line,
                Locales.byQualifier(sourceQualifier).label,
                Locales.byQualifier(targetQualifier).label));
        engineLine.setText(getString(R.string.xlate_engine_line, engineLabel()));
        int staged = 0;
        for (TranslateRow row : allRows) {
            if (row.isDirty() && !row.getTranslation().trim().isEmpty()) staged++;
        }
        stringsCount.setText(getString(R.string.xlate_strings_count,
                stringsAdapter.getItemCount(), staged, allRows.size()));
        toolbar.setSubtitle(modified ? apk.getName() + " *" : apk.getName());
    }

    /** Recounts staged rows without touching the language or engine lines. */
    private void refreshCounts() {
        if (stringsCount == null) return;
        int staged = 0;
        for (TranslateRow row : allRows) {
            if (row.isDirty() && !row.getTranslation().trim().isEmpty()) staged++;
        }
        stringsCount.setText(getString(R.string.xlate_strings_count,
                stringsAdapter.getItemCount(), staged, allRows.size()));
    }

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
        ((TextView) view.findViewById(R.id.xlate_detail_source)).setText(row.source);
        List<String> tokens = FormatGuard.tokens(row.source);
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

    // ---------------------------------------------------------------- engine run

    private void runEngine() {
        if (translator == null) return;
        if (stringsAdapter.getItemCount() == 0) {
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
        List<TranslateRow> snapshot = new ArrayList<>(stringsAdapter.rows());
        ProgressManager pm = new ProgressManager(this, true).show();
        progress.setVisibility(View.VISIBLE);
        progress.setMax(Math.max(snapshot.size(), 1));
        String sourceTag = ApkStringsTranslator.bcp47For(sourceQualifier);
        String targetTag = ApkStringsTranslator.bcp47For(targetQualifier);
        TranslationEngine.Callback callback = new TranslationEngine.Callback() {
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
        };
        new Thread(() -> {
            try {
                chosen.translate(this, snapshot, sourceTag, targetTag, callback);
                pm.dismiss();
                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    modified = true;
                    stringsAdapter.refreshValues();
                    updateSubtitle();
                    Extensions.showMessage(this,
                            getString(R.string.xlate_engine_done, engineLabel()));
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
        if (translator == null || applying) return;
        // allRows, not the adapter's filtered list: applying should write everything staged and
        // ticked off, otherwise the active search box would silently narrow what gets saved.
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

    /** @return the first protected token a staged translation dropped, or {@code null}. */
    private String firstViolation(List<TranslateRow> rows) {
        for (TranslateRow row : rows) {
            String bad = FormatGuard.firstViolation(row.source, row.getTranslation());
            if (bad != null) return bad;
        }
        return null;
    }

    private void doApply(List<TranslateRow> applicable) {
        applying = true;
        ProgressManager pm = new ProgressManager(this, true).show();
        pm.setText(getString(R.string.xlate_writing, applicable.size()));
        new Thread(() -> {
            int written;
            File arsc;
            try {
                written = translator.apply(applicable);
                translator.save();
                arsc = translator.data().arscFile;
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> {
                    applying = false;
                    new ErrorUtil(this).showError(e);
                });
                return;
            }
            String zipPath = apk.getAbsolutePath();
            int total = applicable.size();
            pm.dismiss();
            runOnUiThread(() -> {
                applying = false;
                modified = false;
                toolbar.setSubtitle(apk.getName());
                if (written == 0) {
                    Extensions.showMessage(this, R.string.xlate_nothing_to_apply);
                    return;
                }
                // Hand the validated arsc back to the file list, which owns the "APK updated"
                // prompt, the backup, the injection into the zip and the optional signing.
                // It reads this file asynchronously after the user confirms, so the working
                // copy must survive; ApkStringsTranslator.open() re-extracts on the next run.
                Intent result = new Intent();
                result.setData(Uri.fromFile(arsc));
                result.putExtra("zipFilePath", zipPath);
                result.putExtra("zipEntryPath", ApkXmlCatalog.ARSC);
                setResult(ApkResultHandler.REQUEST_MODIFIED_ENTRY, result);
                Extensions.showMessage(this, getString(R.string.xlate_applied, written, total));
                finish();
            });
        }).start();
    }

    // ---------------------------------------------------------------- menu

    private boolean onMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.xlate_menu_translate) {
            runEngine();
        } else if (id == R.id.xlate_menu_languages) {
            showLanguagePicker();
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

    private void showLanguagePicker() {
        if (translator == null) return;
        List<Locales.Lang> all = Locales.all();
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) labels[i] = all.get(i).label;
        int checked = 0;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).qualifier.equals(targetQualifier)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_target_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    targetQualifier = all.get(which).qualifier;
                    TranslateStore.setTargetQualifier(this, targetQualifier);
                    rebuildRows();
                })
                .setNeutralButton(R.string.xlate_source_language,
                        (dialog, which) -> showSourceLanguagePicker())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSourceLanguagePicker() {
        // Only languages the APK actually ships are useful as a source.
        List<Locales.Lang> options = new ArrayList<>();
        List<String> present = translator.existingQualifiers();
        for (Locales.Lang lang : Locales.all()) {
            if (present.contains(lang.qualifier)) options.add(lang);
        }
        if (options.isEmpty()) options.add(Locales.byQualifier(""));
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).label;
        int checked = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).qualifier.equals(sourceQualifier)) checked = i;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_source_language)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    sourceQualifier = options.get(which).qualifier;
                    TranslateStore.setSourceQualifier(this, sourceQualifier);
                    rebuildRows();
                })
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
                    updateSubtitle();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showGlossaryMenu() {
        String pair = TranslateStore.pairFor(
                ApkStringsTranslator.bcp47For(sourceQualifier),
                ApkStringsTranslator.bcp47For(targetQualifier));
        String[] items = {
                getString(R.string.xlate_glossary_edit),
                getString(R.string.xlate_glossary_export),
                getString(R.string.xlate_glossary_stats, pair)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_glossary)
                .setItems(items, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 0) editGlossary(pair);
                    else if (which == 1) exportGlossary(pair);
                    else showGlossaryStats(pair);
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
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".provider", file);
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
        MaterialCheckBox onlyMissing = view.findViewById(R.id.xlate_only_missing_check);
        MaterialCheckBox skipFormatted = view.findViewById(R.id.xlate_skip_formatted_check);
        endpoint.setText(TranslateStore.endpoint(this));
        apiKey.setText(TranslateStore.apiKey(this));
        batch.setChecked(TranslateStore.allowBatch(this));
        onlyMissing.setChecked(TranslateStore.onlyMissing(this));
        skipFormatted.setChecked(TranslateStore.skipFormatted(this));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_settings)
                .setView(view)
                .setPositiveButton(R.string.save, (d, w) -> {
                    TranslateStore.setEndpoint(this, text(endpoint));
                    TranslateStore.setApiKey(this, text(apiKey));
                    TranslateStore.setAllowBatch(this, batch.isChecked());
                    TranslateStore.setOnlyMissing(this, onlyMissing.isChecked());
                    TranslateStore.setSkipFormatted(this, skipFormatted.isChecked());
                    rebuildRows();
                })
                .setNeutralButton(R.string.xlate_engine, (d, w) -> showEnginePicker())
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
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    modified = false;
                    rebuildRows();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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

    // ---------------------------------------------------------------- plumbing

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ApkResultHandler.REQUEST_MODIFIED_ENTRY) return;
        if (resultCode != RESULT_OK || data == null) return;
        // An AXML entry came back edited. The read-only tab has nothing to refresh, so just
        // report it; injecting the file back into the APK stays the file list's job.
        String entry = data.getStringExtra("zipEntryPath");
        Extensions.showMessage(this,
                getString(R.string.xlate_entry_saved, entry == null ? apk.getName() : entry));
    }

    @Override
    public void onBackPressed() {
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

package io.github.abdurazaaqmohammed.features.apk.translate;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.aXMLDecoder;
import com.apk.axml.aXMLEncoder;
import com.apk.axml.serializableItems.XMLEntry;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.features.apk.ApkResultHandler;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * MT Manager's translation mode for an APK's own layout files.
 *
 * <p>Unlike {@link ArscTranslationModeActivity} this screen does not read a string table. It walks
 * every {@code res/layout*} entry, decodes it and lifts out the literals that were written straight
 * into the attributes - {@code android:text="Welcome!"} rather than {@code @string/welcome} - which
 * is the residue that survives every other localisation pass and the thing a 汉化 job has to pick
 * up by hand otherwise.
 *
 * <p>The list is grouped by archive entry, one band per file and a source | translation pair under
 * it, so a scan over hundreds of layouts stays readable. Rows carry the entry path and their index
 * in that file's decoded XML, which is what lets Apply rewrite only the touched attributes.
 *
 * <p>Applying writes each modified layout under the cache dir mirroring its archive path and hands
 * the set back through {@code setResult(757)}; the file list still owns the backup, the zip
 * injection and the signing prompt.
 */
public class XmlTranslationModeActivity extends BaseActivity {

    /** 汉化 is the whole point of this screen, so the target is not a user choice. */
    private static final String TARGET_BCP47 = "zh-CN";

    private static final Set<String> TEXT_ATTRS = new HashSet<>(Arrays.asList(
            "text", "hint", "contentDescription", "description", "label", "title"));

    private MaterialToolbar toolbar;
    private LinearProgressIndicator progress;
    private TextView counts;
    private GroupedTranslateRowAdapter adapter;

    private final List<TranslateRow> allRows = new ArrayList<>();
    private final Map<String, List<XMLEntry>> decoded = new LinkedHashMap<>();

    private File apkFile;
    private String engineId;
    private boolean loading;
    private boolean modified;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String apkPath = getIntent().getStringExtra("apkPath");
        if (apkPath == null) {
            finish();
            return;
        }
        apkFile = new File(apkPath);
        if (!apkFile.isFile()) {
            finish();
            return;
        }
        engineId = TranslateStore.engine(this);

        setContentView(R.layout.activity_xml_translation_mode);
        toolbar = findViewById(R.id.xlate_toolbar);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> confirmExit());
        toolbar.inflateMenu(R.menu.menu_xml_translation_mode);
        toolbar.setOnMenuItemClickListener(this::onMenu);

        progress = findViewById(R.id.xlate_progress);
        counts = findViewById(R.id.xlate_counts);

        setupList();
        load();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    private void setupList() {
        RecyclerView list = findViewById(R.id.xlate_strings_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupedTranslateRowAdapter(this::showRowEditor);
        list.setAdapter(adapter);
    }

    // ---------------------------------------------------------------- scan

    private void load() {
        loading = true;
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        new Thread(() -> {
            try {
                List<TranslateRow> rows = scan();
                runOnUiThread(() -> {
                    loading = false;
                    progress.setIndeterminate(false);
                    progress.setVisibility(View.GONE);
                    allRows.clear();
                    allRows.addAll(rows);
                    adapter.submit(rows);
                    refreshCounts();
                    if (rows.isEmpty()) Extensions.showMessage(this, R.string.xlate_no_layout_text);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading = false;
                    progress.setIndeterminate(false);
                    progress.setVisibility(View.GONE);
                    new ErrorUtil(this).showError(e);
                });
            }
        }).start();
    }

    /**
     * Every literal written straight into a layout attribute, ordered by archive entry.
     *
     * <p>Files whose literals are all lifted are kept in {@link #decoded} alongside the rows, so
     * Apply can rewrite them without a second pass over the APK.
     */
    private List<TranslateRow> scan() throws Exception {
        List<TranslateRow> rows = new ArrayList<>();
        decoded.clear();
        try (ZipFile zip = new ZipFile(apkFile)) {
            List<FileHeader> headers = zip.getFileHeaders();
            if (headers == null) return rows;
            List<FileHeader> layouts = new ArrayList<>();
            for (FileHeader header : headers) {
                if (header != null && !header.isDirectory() && isLayoutEntry(header.getFileName())) {
                    layouts.add(header);
                }
            }
            layouts.sort(Comparator.comparing(FileHeader::getFileName));

            for (FileHeader header : layouts) {
                String entryPath = header.getFileName();
                List<XMLEntry> entries;
                try (InputStream in = zip.getInputStream(header)) {
                    entries = new aXMLDecoder(in).decode();
                }
                boolean any = false;
                for (int i = 0; i < entries.size(); i++) {
                    XMLEntry entry = entries.get(i);
                    String attr = attributeName(entry);
                    if (attr == null || !TEXT_ATTRS.contains(attr)) continue;
                    if (!isProse(entry.getValue())) continue;
                    TranslateRow row = new TranslateRow(entryPath + "#" + i, attr, "", entry.getValue());
                    row.filePath = entryPath;
                    row.xmlIndex = i;
                    rows.add(row);
                    any = true;
                }
                if (any) decoded.put(entryPath, entries);
            }
        }
        return rows;
    }

    /** {@code res/layout} and every qualifier variant, never {@code res/xml} or {@code res/menu}. */
    private static boolean isLayoutEntry(String name) {
        if (name == null || !name.endsWith(".xml")) return false;
        String path = name.startsWith("res/") ? name.substring(4) : name;
        return path.startsWith("layout")
                && (path.length() == "layout".length()
                || path.charAt("layout".length()) == '/'
                || path.charAt("layout".length()) == '-');
    }

    /** The attribute name out of a decoded line such as {@code "        android:text"}. */
    private static String attributeName(XMLEntry entry) {
        if (!"=\"".equals(entry.getMiddleTag())) return null;
        String tag = entry.getTag().trim();
        int space = tag.lastIndexOf(' ');
        String full = space >= 0 ? tag.substring(space + 1) : tag;
        int colon = full.indexOf(':');
        return colon >= 0 ? full.substring(colon + 1) : full;
    }

    /**
     * Whether a value is prose a translator should see.
     *
     * <p>References render as {@code @XXXXXXXX}, theme attributes as {@code ?...}, and already
     * Chinese text has nothing left to do here - so a value counts only when it carries a letter
     * outside the CJK blocks and survives the format/markup filter.
     */
    private static boolean isProse(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.isEmpty()) return false;
        if (v.startsWith("@") || v.startsWith("?")) return false;
        if (v.matches("#[0-9a-fA-F]{3,8}")) return false;
        if (TranslateRow.looksFormatted(v)) return false;
        for (int i = 0; i < v.length(); ) {
            int cp = v.codePointAt(i);
            if (Character.isLetter(cp) && !isCjk(cp)) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isCjk(int cp) {
        return (cp >= 0x4E00 && cp <= 0x9FFF)
                || (cp >= 0x3400 && cp <= 0x4DBF)
                || (cp >= 0x3040 && cp <= 0x30FF)
                || (cp >= 0xAC00 && cp <= 0xD7AF);
    }

    // ---------------------------------------------------------------- row editor

    private void showRowEditor(TranslateRow row) {
        if (row == null) return;
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_xlate_row_edit, null);
        ((TextView) view.findViewById(R.id.xlate_detail_key))
                .setText(row.filePath + "  ·  " + row.type);
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
                    row.setTranslation(text(input), TranslateRow.Origin.MANUAL);
                    modified = true;
                    adapter.notifyDataSetChanged();
                    refreshCounts();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- engine

    private TranslationEngine engine() {
        // A manual run would do nothing on this screen: the point of it is bulk translation, so
        // an unset engine falls through to the online one.
        if (TranslateStore.ENGINE_GLOSSARY.equals(engineId)) return new GlossaryEngine(this);
        return new OnlineEngine(this);
    }

    private String engineLabel() {
        return TranslateStore.ENGINE_GLOSSARY.equals(engineId)
                ? getString(R.string.engine_glossary)
                : getString(R.string.engine_online);
    }

    private void runEngine() {
        if (loading || allRows.isEmpty()) {
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
        progress.setIndeterminate(false);
        progress.setMax(Math.max(snapshot.size(), 1));
        final String source = TranslateStore.sourceBcp47(this);
        new Thread(() -> {
            try {
                chosen.translate(this, snapshot, source, TARGET_BCP47, new TranslationEngine.Callback() {
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
                    modified = true;
                    adapter.notifyDataSetChanged();
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
        if (loading) return;
        // allRows, never a filtered view: nothing here filters, but the rule still holds.
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
            File root = new File(getCacheDir(), "xml_xlate_out");
            try {
                deleteRecursive(root);
                if (!root.mkdirs() && !root.isDirectory()) {
                    throw new IOException("Cannot create " + root);
                }
                Map<String, List<TranslateRow>> byFile = new LinkedHashMap<>();
                for (TranslateRow row : applicable) {
                    byFile.computeIfAbsent(row.filePath, k -> new ArrayList<>()).add(row);
                }
                for (Map.Entry<String, List<TranslateRow>> group : byFile.entrySet()) {
                    List<XMLEntry> entries = decoded.get(group.getKey());
                    if (entries == null) continue;
                    for (TranslateRow row : group.getValue()) {
                        if (row.xmlIndex >= 0 && row.xmlIndex < entries.size()) {
                            entries.get(row.xmlIndex).setValue(escapeAttr(row.getTranslation()));
                        }
                    }
                    byte[] bytes = new aXMLEncoder().encodeString(entries, this);
                    File out = new File(root, group.getKey());
                    File parent = out.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Cannot create " + parent);
                    }
                    try (FileOutputStream os = new FileOutputStream(out)) {
                        os.write(bytes);
                    }
                }
                File marker = new File(root, ".done");
                try (FileOutputStream os = new FileOutputStream(marker)) {
                    os.write(1);
                }
                pm.dismiss();
                runOnUiThread(() -> {
                    modified = false;
                    Extensions.showMessage(this, getString(R.string.xlate_applied, total, total));
                    Intent result = new Intent();
                    result.setData(Uri.fromFile(marker));
                    result.putExtra("zipFilePath", apkFile.getAbsolutePath());
                    result.putExtra("zipEntryPaths", byFile.keySet().toArray(new String[0]));
                    result.putExtra("modifiedRoot", root.getAbsolutePath());
                    setResult(ApkResultHandler.REQUEST_MODIFIED_ENTRY, result);
                    finish();
                });
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> new ErrorUtil(this).showError(e));
            }
        }).start();
    }

    /** Values live inside an attribute, so anything that would end the attribute has to go. */
    private static String escapeAttr(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void deleteRecursive(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    // ---------------------------------------------------------------- menu

    private boolean onMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.xlate_menu_run) {
            runEngine();
        } else if (id == R.id.xlate_menu_apply) {
            applyTranslations();
        } else if (id == R.id.xlate_menu_source_language) {
            showSourceLanguagePicker();
        } else if (id == R.id.xlate_menu_settings) {
            showSettings();
        } else if (id == R.id.xlate_menu_revert) {
            confirmRevert();
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
                    refreshCounts();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                    refreshCounts();
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
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    modified = false;
                    allRows.clear();
                    decoded.clear();
                    adapter.submit(new ArrayList<>());
                    refreshCounts();
                    load();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmExit() {
        if (loading || !modified) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.xlate_unsaved)
                .setPositiveButton(R.string.xlate_apply, (d, w) -> applyTranslations())
                .setNeutralButton(R.string.xlate_discard, (d, w) -> {
                    modified = false;
                    finish();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------------------------------------------------------------- counts

    private void refreshCounts() {
        if (counts == null) return;
        if (allRows.isEmpty()) {
            counts.setVisibility(View.GONE);
            return;
        }
        int staged = 0;
        Set<String> files = new HashSet<>();
        for (TranslateRow row : allRows) {
            if (row.isDirty()) staged++;
            if (row.filePath != null) files.add(row.filePath);
        }
        counts.setVisibility(View.VISIBLE);
        counts.setText(getString(R.string.xlate_xml_count, allRows.size(), staged, files.size())
                + "  ·  " + engineLabel().toLowerCase(Locale.ROOT));
    }
}

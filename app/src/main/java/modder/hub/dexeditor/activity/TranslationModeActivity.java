package modder.hub.dexeditor.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.SearchHistoryHelper;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.adapter.StringAdapter;
import modder.hub.dexeditor.smali.SharedSmaliUtils;
import modder.hub.dexeditor.utils.AutoTranslateClient;
import modder.hub.dexeditor.utils.ClassTree;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

/**
 * Standalone MT-Manager-style DEX translation mode: a two-column
 * original/translation editor over one or more dex files, with MT's menu
 * (Save, Save to dictionary, Dictionary manager, Filter, Replace, Check,
 * Language, Exit) and bottom bar (select all, invert selection, auto
 * translate, jump to string, untranslated filter).
 *
 * Loads its own dexes, so it does not depend on the Dex Editor Plus session
 * state. Multi-select via long-press; back press or deselect-all exits.
 *
 * Auto translation calls an external translation API (LibreTranslate, DeepL,
 * Google Cloud Translate or any OpenAI-compatible endpoint) configured by the
 * user with an API key.
 *
 * Saving rewrites string literals through the smali round-trip
 * (getSmaliByType -> replace -> assemble), persists every selected dex, and
 * hands the result back to the caller with the dex editor's 757 result
 * contract so MainActivity offers the usual "update the APK?" prompt with
 * auto-sign.
 */
public class TranslationModeActivity extends BaseActivity {

    private static final Pattern LITERAL_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final String FILTER_HISTORY_KEY = "translation_filter_history";
    private static final String PREF_LAST_FILTER = "translation_last_filter";
    private static final String PREF_AT_PROVIDER = "at_provider";
    private static final String PREF_AT_KEY = "at_key";
    private static final String PREF_AT_ENDPOINT = "at_endpoint";
    private static final String PREF_AT_MODEL = "at_model";
    private static final int REQUEST_AUTO_TRANSLATE = 4242;

    private List<String> dexPaths;
    private String zipFilePath;
    private String zipEntryPath;

    private ClassTree classTree;
    private final List<String> originals = new ArrayList<>();
    private StringAdapter adapter;
    private String lastTapped;
    private String language = "en";
    private boolean dirty;
    private boolean loaded;

    // auto-translation state
    private java.util.Iterator<String> autoTranslateQueue;
    private String autoTranslateCurrent;
    private int autoTranslateDone;
    private boolean autoTranslateActive;
    private volatile boolean autoTranslateCancelled;
    private AlertDialog autoTranslateDialog;
    private TextView autoTranslateProgressText;
    private int autoTranslateTotal;
    private String autoTranslatePackageName;

    private FastScrollerRecyclerView list;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translation_mode);

        Intent launch = getIntent();
        dexPaths = launch.getStringArrayListExtra("SelectedDexFiles");
        zipFilePath = launch.getStringExtra("zipFilePath");
        zipEntryPath = launch.getStringExtra("zipEntryPath");

        toolbar = findViewById(R.id.translation_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.translation_mode);
            getSupportActionBar().setSubtitle(R.string.loading);
        }
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> confirmExit());

        list = findViewById(R.id.translation_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setMinItemsForScroller(50);
        adapter = new StringAdapter(originals, this::onStringTapped, onSurfaceColor());
        adapter.setLongClickListener(this::onStringLongTapped);
        adapter.setModifiedCountListener((modified, total) -> {
            if (getSupportActionBar() != null && !adapter.isSelectionMode()) {
                getSupportActionBar().setSubtitle(modified + "/" + total);
            }
        });
        adapter.setSelectionListener((selectionMode, count) -> {
            if (getSupportActionBar() == null) return;
            if (selectionMode) {
                getSupportActionBar().setSubtitle(getString(R.string.translation_selected_n, count));
            } else {
                getSupportActionBar().setSubtitle(
                        adapter.getModifiedStrings().size() + "/" + originals.size());
            }
        });
        int highlight = themeColor(com.google.android.material.R.attr.colorSecondaryContainer, 0x556750A4);
        adapter.setSelectionHighlightColor(mergeAlpha(highlight, 0x66));
        list.setAdapter(adapter);

        // Bottom bar order: select all > invert selection > auto translate >
        // jump to string > untranslated filter. Long-press shows descriptions.
        bindBottomButton(R.id.btn_select_all, R.string.translation_select_all,
                v -> {
                    if (adapter.isSelectionMode()
                            && adapter.getSelected().size() >= displayedCount()) {
                        adapter.clearSelection(); // deselect all exits the mode
                    } else {
                        adapter.selectAll();
                    }
                });
        bindBottomButton(R.id.btn_invert, R.string.translation_invert,
                v -> adapter.invertSelection());
        bindBottomButton(R.id.btn_translate, R.string.translation_auto,
                v -> showAutoTranslateDialog());
        bindBottomButton(R.id.btn_jump, R.string.translation_jump,
                v -> showJumpDialog());
        bindBottomButton(R.id.btn_untranslated, R.string.translation_show_untranslated,
                v -> {
                    StringAdapter.FilterSpec spec = adapter.getCurrentSpec();
                    spec.status = spec.status == 2 ? 0 : 2;
                    spec.active = spec.status != 0
                            || (spec.query != null && !spec.query.isEmpty());
                    adapter.applyFilter(spec);
                    Extensions.showMessage(this, spec.status == 2
                            ? getString(R.string.translation_showing_untranslated)
                            : getString(R.string.translation_showing_all));
                });

        loadStrings();
    }

    private int displayedCount() {
        return adapter.getItemCount();
    }

    private void bindBottomButton(int id, int descRes, View.OnClickListener click) {
        View button = findViewById(id);
        button.setOnClickListener(click);
        button.setOnLongClickListener(v -> {
            Extensions.showMessage(this, getString(descRes));
            return true;
        });
    }

    private boolean onMenuItem(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_save) {
            saveTranslations();
            return true;
        } else if (id == R.id.action_save_dictionary) {
            saveDictionary();
            return true;
        } else if (id == R.id.action_dictionary_manager) {
            showDictionaryManager(false);
            return true;
        } else if (id == R.id.action_filter) {
            showFilterDialog();
            return true;
        } else if (id == R.id.action_replace) {
            showReplaceDialog();
            return true;
        } else if (id == R.id.action_check) {
            showCheckDialog();
            return true;
        } else if (id == R.id.action_language) {
            showLanguageDialog();
            return true;
        } else if (id == R.id.action_exit) {
            confirmExit();
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.menu_translation_mode, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (onMenuItem(item)) return true;
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------
    // loading
    // ------------------------------------------------------------------

    private void loadStrings() {
        if (dexPaths == null || dexPaths.isEmpty()) {
            showError(getString(R.string.file_no_longer_available));
            finish();
            return;
        }
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                if (classTree == null) {
                    classTree = new ClassTree(dexPaths, getCacheDir().getAbsolutePath());
                }
                List<String> strings = classTree.getAllStrings();
                java.util.Collections.sort(strings);
                runOnUiThread(() -> {
                    originals.clear();
                    originals.addAll(strings);
                    loaded = !strings.isEmpty();
                    adapter.refresh();
                    pm.dismiss();
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setSubtitle(
                                adapter.getModifiedStrings().size() + "/" + originals.size());
                    }
                    if (strings.isEmpty()) {
                        showError(getString(R.string.translation_no_strings));
                    }
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    pm.dismiss();
                    showError(String.valueOf(e.getMessage()));
                });
            }
        }).start();
    }

    private void onStringTapped(String original) {
        lastTapped = original;
        if (adapter.isSelectionMode()) {
            adapter.toggleSelected(original);
        } else {
            showEditDialog(indexOf(original));
        }
    }

    private boolean onStringLongTapped(String original) {
        lastTapped = original;
        if (!adapter.isSelectionMode()) {
            adapter.enterSelectionMode(original);
        } else {
            adapter.toggleSelected(original);
        }
        return true;
    }

    private int indexOf(String original) {
        return originals.indexOf(original);
    }

    // ------------------------------------------------------------------
    // edit
    // ------------------------------------------------------------------

    /** MT-style: original read-only on top, editable translation below, and a
     *  Translate button that fills the field via the configured API service. */
    private void showEditDialog(int index) {
        if (index < 0 || index >= originals.size()) return;
        final String original = originals.get(index);
        final TextView originalView = new TextView(this);
        originalView.setText(original);
        originalView.setTextIsSelectable(true);
        originalView.setTextColor(onSurfaceColor());

        final EditText translationInput = new EditText(this);
        translationInput.setText(adapter.getPendingValue(original));
        translationInput.setSelection(translationInput.getText().length());

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(originalView);
        container.addView(wrap(translationInput, getString(R.string.translation_hint)));

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.translation_string_n, index + 1))
                .setView(container)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.translate, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            adapter.setTranslation(original, translationInput.getText().toString());
            dirty = true;
            dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            translateSingle(original, translationInput, dialog.getButton(AlertDialog.BUTTON_NEUTRAL));
        });
        showKeyboard(translationInput);
    }

    /** API-translates one string and fills the edit field when done. */
    private void translateSingle(String text, EditText target, android.widget.Button button) {
        if (!ensureServiceConfigured()) return;
        button.setEnabled(false);
        button.setText(R.string.translating);
        new Thread(() -> {
            try {
                android.content.SharedPreferences prefs =
                        getSharedPreferences("translation_prefs", 0);
                int provider = prefs.getInt(PREF_AT_PROVIDER, 0);
                List<String> out = AutoTranslateClient.translate(
                        provider,
                        endpointFor(provider),
                        prefs.getString(PREF_AT_KEY, ""),
                        prefs.getString(PREF_AT_MODEL, ""),
                        language,
                        java.util.Collections.singletonList(text));
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText(R.string.translate);
                    if (!out.isEmpty()) {
                        target.setText(out.get(0));
                        target.setSelection(target.getText().length());
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    button.setEnabled(true);
                    button.setText(R.string.translate);
                    showError(String.valueOf(e.getMessage()));
                });
            }
        }).start();
    }

    private LinearLayout wrap(EditText editText, String hint) {
        LinearLayout holder = new LinearLayout(this);
        holder.setOrientation(LinearLayout.VERTICAL);
        holder.addView(editText, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        editText.setHint(hint);
        return holder;
    }

    private void showKeyboard(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
    }

    // ------------------------------------------------------------------
    // auto translation (bottom bar): API-based, all strings or the
    // multi-select selection; service options with API key and warnings
    // ------------------------------------------------------------------

    private boolean ensureServiceConfigured() {
        android.content.SharedPreferences prefs = getSharedPreferences("translation_prefs", 0);
        if (prefs.contains(PREF_AT_PROVIDER)) return true;
        showServiceOptionsDialog(null);
        return false;
    }

    /** Service options: provider, endpoint, API key, model, target info, and a
     *  privacy warning. Passing a runnable runs it after the config is saved. */
    private void showServiceOptionsDialog(Runnable onConfigured) {
        android.content.SharedPreferences prefs = getSharedPreferences("translation_prefs", 0);
        int provider = prefs.getInt(PREF_AT_PROVIDER, 0);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad / 2, pad, 0);

        MaterialAutoCompleteTextView providerView = new MaterialAutoCompleteTextView(this);
        providerView.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, AutoTranslateClient.PROVIDER_NAMES));
        providerView.setText(AutoTranslateClient.PROVIDER_NAMES[provider], false);
        TextInputLayout providerLayout = dropdownLayout(providerView,
                getString(R.string.translation_provider));
        root.addView(providerLayout);

        TextInputEditText endpointInput = new TextInputEditText(this);
        endpointInput.setText(prefs.getString(PREF_AT_ENDPOINT,
                AutoTranslateClient.defaultEndpoint(provider)));
        TextInputLayout endpointLayout = filledLayout(endpointInput,
                getString(R.string.translation_endpoint));
        root.addView(endpointLayout);

        TextInputEditText keyInput = new TextInputEditText(this);
        keyInput.setText(prefs.getString(PREF_AT_KEY, ""));
        keyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextInputLayout keyLayout = filledLayout(keyInput, getString(R.string.translation_api_key));
        root.addView(keyLayout);

        TextInputEditText modelInput = new TextInputEditText(this);
        modelInput.setText(prefs.getString(PREF_AT_MODEL, "gpt-4o-mini"));
        modelInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        TextInputLayout modelLayout = filledLayout(modelInput, getString(R.string.translation_model));
        root.addView(modelLayout);

        Runnable syncProviderUi = () -> {
            int p = indexIn(AutoTranslateClient.PROVIDER_NAMES, providerView.getText().toString());
            if (p >= 0) {
                if (endpointLayout.getEditText() != null
                        && endpointLayout.getEditText().getText().length() == 0) {
                    endpointLayout.getEditText().setText(AutoTranslateClient.defaultEndpoint(p));
                }
                modelLayout.setVisibility(p == AutoTranslateClient.PROVIDER_OPENAI
                        ? View.VISIBLE : View.GONE);
            }
        };
        providerView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                syncProviderUi.run();
            }
        });
        syncProviderUi.run();

        TextView warning = new TextView(this);
        warning.setText(R.string.translation_warning);
        warning.setTextSize(12);
        warning.setTextColor(0xFFB3261E);
        warning.setPadding(0, pad / 2, 0, 0);
        root.addView(warning);

        TextView targetInfo = new TextView(this);
        targetInfo.setText(getString(R.string.translation_target_info, language));
        targetInfo.setTextSize(12);
        targetInfo.setPadding(0, pad / 4, 0, 0);
        root.addView(targetInfo);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_service_options)
                .setView(root)
                .setPositiveButton(R.string.save, (d, w) -> {
                    int p = indexIn(AutoTranslateClient.PROVIDER_NAMES, providerView.getText().toString());
                    if (p < 0) p = 0;
                    prefs.edit()
                            .putInt(PREF_AT_PROVIDER, p)
                            .putString(PREF_AT_ENDPOINT, String.valueOf(endpointInput.getText()).trim())
                            .putString(PREF_AT_KEY, String.valueOf(keyInput.getText()).trim())
                            .putString(PREF_AT_MODEL, String.valueOf(modelInput.getText()).trim())
                            .apply();
                    Extensions.showMessage(this, getString(R.string.translation_service_saved));
                    if (onConfigured != null) onConfigured.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static int indexIn(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equalsIgnoreCase(value)) return i;
        }
        return -1;
    }

    private String endpointFor(int provider) {
        android.content.SharedPreferences prefs = getSharedPreferences("translation_prefs", 0);
        String saved = prefs.getString(PREF_AT_ENDPOINT, "");
        return saved.isEmpty() ? AutoTranslateClient.defaultEndpoint(provider) : saved;
    }

    private void showAutoTranslateDialog() {
        if (!loaded) {
            toast(R.string.translation_not_loaded);
            return;
        }
        if (autoTranslateActive) {
            toast(R.string.translation_auto_running);
            return;
        }
        if (!ensureServiceConfigured()) return;

        // Scope: the multi-select selection when active, else every string.
        final List<String> targets = new ArrayList<>();
        if (adapter.isSelectionMode() && !adapter.getSelected().isEmpty()) {
            for (String original : originals) {
                if (adapter.getSelected().contains(original)) targets.add(original);
            }
        } else {
            targets.addAll(originals);
        }
        String scope = adapter.isSelectionMode() && !adapter.getSelected().isEmpty()
                ? getString(R.string.translation_scope_selection)
                : getString(R.string.translation_scope_all);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_auto_confirm_title)
                .setMessage(getString(R.string.translation_auto_confirm,
                        targets.size(), scope, language))
                .setPositiveButton(R.string.translation_start, (d, w) -> startAutoTranslate(targets))
                .setNeutralButton(R.string.translation_service_options, (d, w) ->
                        showServiceOptionsDialog(() -> startAutoTranslate(targets)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startAutoTranslate(List<String> targets) {
        android.content.SharedPreferences prefs = getSharedPreferences("translation_prefs", 0);
        final int provider = prefs.getInt(PREF_AT_PROVIDER, 0);
        final String endpoint = endpointFor(provider);
        final String apiKey = prefs.getString(PREF_AT_KEY, "");
        final String model = prefs.getString(PREF_AT_MODEL, "");

        autoTranslateCancelled = false;
        autoTranslateActive = true;
        autoTranslateDone = 0;
        autoTranslateTotal = targets.size();

        LinearLayout progressView = new LinearLayout(this);
        progressView.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        progressView.setPadding(pad, pad, pad, pad);
        autoTranslateProgressText = new TextView(this);
        autoTranslateProgressText.setText(
                getString(R.string.translation_auto_progress, 0, targets.size()));
        progressView.addView(autoTranslateProgressText);

        autoTranslateDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_auto_running_title)
                .setView(progressView)
                .setNegativeButton(android.R.string.cancel, (d, w) -> autoTranslateCancelled = true)
                .setCancelable(false)
                .show();

        new Thread(() -> {
            try {
                int chunkSize = AutoTranslateClient.batchSize(provider);
                for (int start = 0; start < targets.size(); start += chunkSize) {
                    if (autoTranslateCancelled) break;
                    int end = Math.min(start + chunkSize, targets.size());
                    List<String> chunk = targets.subList(start, end);
                    final List<String> out = AutoTranslateClient.translate(provider, endpoint,
                            apiKey, model, language, new ArrayList<>(chunk));
                    runOnUiThread(() -> {
                        for (int i = 0; i < chunk.size() && i < out.size(); i++) {
                            adapter.setTranslation(chunk.get(i), out.get(i));
                        }
                        dirty = true;
                    });
                    autoTranslateDone = end;
                    final int doneF = autoTranslateDone;
                    runOnUiThread(() -> {
                        if (autoTranslateProgressText != null) {
                            autoTranslateProgressText.setText(
                                    getString(R.string.translation_auto_progress, doneF, autoTranslateTotal));
                        }
                    });
                }
                final int done = autoTranslateDone;
                runOnUiThread(() -> {
                    if (autoTranslateDialog != null) autoTranslateDialog.dismiss();
                    autoTranslateActive = false;
                    toast(getString(R.string.translation_auto_finished, done));
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (autoTranslateDialog != null) autoTranslateDialog.dismiss();
                    autoTranslateActive = false;
                    showError(String.valueOf(e.getMessage()));
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Kept for the old PROCESS_TEXT flow compatibility; API translation no
        // longer needs activity results.
        if (requestCode == REQUEST_AUTO_TRANSLATE) {
            autoTranslateActive = false;
        }
    }

    // ------------------------------------------------------------------
    // save
    // ------------------------------------------------------------------

    private void saveTranslations() {
        // Identical pairs carry no dex change; skip them so unchanged classes
        // are not re-assembled.
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : adapter.getModifiedStrings().entrySet()) {
            if (!entry.getValue().equals(entry.getKey())) {
                changes.put(entry.getKey(), entry.getValue());
            }
        }
        if (changes.isEmpty()) {
            toast(R.string.translation_nothing_to_save);
            return;
        }
        if (classTree == null) {
            showError(getString(R.string.translation_not_loaded));
            return;
        }
        ProgressManager pm = new ProgressManager(this, false).show();
        new Thread(() -> {
            try {
                int dexVersion = classTree.getOpenedDexVersion();
                List<com.android.tools.smali.dexlib2.iface.ClassDef> classes =
                        new ArrayList<>(classTree.classMap.values());
                int total = classes.size();
                int done = 0;
                final int[] changed = {0};
                for (com.android.tools.smali.dexlib2.iface.ClassDef classDef : classes) {
                    try {
                        String smali = classTree.getSmaliByType(classDef);
                        Matcher m = LITERAL_PATTERN.matcher(smali);
                        StringBuffer sb = new StringBuffer();
                        int count = 0;
                        while (m.find()) {
                            String literal = m.group(1);
                            String replacement = changes.get(literal);
                            if (replacement != null) {
                                count++;
                                m.appendReplacement(sb, Matcher.quoteReplacement(
                                        "\"" + replacement + "\""));
                            } else {
                                m.appendReplacement(sb, Matcher.quoteReplacement("\"" + literal + "\""));
                            }
                        }
                        m.appendTail(sb);
                        if (count > 0) {
                            String modified = sb.toString();
                            com.android.tools.smali.dexlib2.iface.ClassDef newDef =
                                    com.android.tools.smali.smali2.Smali.assemble(
                                            modified, SharedSmaliUtils.ASSEMBLE_OPTIONS, dexVersion);
                            classTree.saveClassDefIfChanged(newDef, modified);
                            changed[0]++;
                        }
                    } catch (Exception ignored) {
                    }
                    done++;
                    final int fp = done, ft = total;
                    runOnUiThread(() -> pm.setProgress(fp, ft));
                }
                classTree.saveAllDexFiles(new ClassTree.DexSaveProgress() {
                    @Override
                    public void onProgress(int progress, int total1) {
                        runOnUiThread(() -> pm.setProgress(progress, total1));
                    }

                    @Override
                    public void onTitle(String title) {
                    }

                    @Override
                    public void onMessage(String message) {
                        runOnUiThread(() -> pm.setText(message));
                    }
                });
                dirty = false;
                runOnUiThread(() -> {
                    pm.dismiss();
                    toast(getString(R.string.translation_saved, changed[0]));
                    returnResultAndFinish();
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    pm.dismiss();
                    showError(String.valueOf(e.getMessage()));
                });
            }
        }).start();
    }

    /** Same result contract as Dex Editor Plus: MainActivity offers the
     *  "update the APK?" prompt (with auto-sign) for archive-backed dexes. */
    private void returnResultAndFinish() {
        Intent result = new Intent();
        if (dexPaths != null && !dexPaths.isEmpty()) {
            result.setData(Uri.fromFile(new File(dexPaths.get(0))));
            result.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        if (zipFilePath != null) result.putExtra("zipFilePath", zipFilePath);
        if (zipEntryPath != null) result.putExtra("zipEntryPath", zipEntryPath);
        setResult(757, result);
        finish();
    }

    // ------------------------------------------------------------------
    // dictionaries
    // ------------------------------------------------------------------

    private File dictionaryDir() {
        File dir = new File(new File(Environment.getExternalStorageDirectory(), UiPrefs.appPathDir(this, UiPrefs.defaultAppDir())), "dictionaries");
        return !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory() ? null : dir;
    }

    private String dictionaryBase() {
        String source = zipFilePath != null ? zipFilePath
                : (dexPaths != null && !dexPaths.isEmpty() ? dexPaths.get(0) : "dex");
        String name = new File(source).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void saveDictionary() {
        Map<String, String> changes = adapter.getModifiedStrings();
        if (changes.isEmpty()) {
            toast(R.string.translation_nothing_to_save);
            return;
        }
        File dir = dictionaryDir();
        if (dir == null) {
            showError(getString(R.string.translation_dict_dir_failed));
            return;
        }
        try {
            JSONObject json = new JSONObject(changes);
            File out = new File(dir, dictionaryBase() + "_" + language + ".json");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            toast(getString(R.string.translation_dictionary_saved, out.getName()));
        } catch (Exception e) {
            showError(String.valueOf(e.getMessage()));
        }
    }

    private List<File> listDictionaries() {
        File dir = dictionaryDir();
        List<File> out = new ArrayList<>();
        if (dir == null) return out;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith(dictionaryBase() + "_") && f.getName().endsWith(".json")) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    /** @param applyOnly true hides the delete action (bottom-bar quick apply). */
    private void showDictionaryManager(boolean applyOnly) {
        List<File> dictionaries = listDictionaries();
        if (dictionaries.isEmpty()) {
            toast(R.string.translation_no_dictionaries);
            return;
        }
        CharSequence[] names = new CharSequence[dictionaries.size()];
        for (int i = 0; i < dictionaries.size(); i++) names[i] = dictionaries.get(i).getName();
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_dictionary_manager)
                .setItems(names, (dialog, which) -> applyDictionary(dictionaries.get(which)));
        if (!applyOnly) {
            builder.setNeutralButton(R.string.delete, null);
        }
        AlertDialog dialog = builder.setNegativeButton(android.R.string.cancel, null).create();
        dialog.show();
        if (!applyOnly) {
            dialog.getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete)
                        .setMessage(getString(R.string.translation_delete_dictionary, names[position]))
                        .setPositiveButton(R.string.delete, (d, w) -> {
                            dictionaries.get((int) id).delete();
                            dialog.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }
    }

    private void applyDictionary(File file) {
        try {
            StringBuilder sb = new StringBuilder();
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
            JSONObject json = new JSONObject(sb.toString());
            Map<String, String> loaded = new LinkedHashMap<>();
            Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                loaded.put(key, json.optString(key));
            }
            adapter.loadTranslations(loaded);
            dirty = true;
            toast(getString(R.string.translation_dictionary_applied, loaded.size()));
        } catch (Exception e) {
            showError(String.valueOf(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------
    // filter (history + scope + match options)
    // ------------------------------------------------------------------

    private void showFilterDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("translation_prefs", 0);
        List<SearchHistoryHelper.Item> history = SearchHistoryHelper.load(this, FILTER_HISTORY_KEY);
        String lastSpecJson = prefs.getString(PREF_LAST_FILTER, null);

        StringAdapter.FilterSpec last = null;
        if (lastSpecJson != null) {
            try {
                JSONObject json = new JSONObject(lastSpecJson);
                last = new StringAdapter.FilterSpec();
                last.query = json.optString("q", "");
                last.status = json.optInt("status", 0);
                last.exclude = json.optBoolean("exclude", false);
                last.matchCase = json.optBoolean("mc", false);
                last.regex = json.optBoolean("re", false);
                last.exact = json.optBoolean("ex", false);
            } catch (Exception ignored) {
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad / 2, pad, 0);

        // Query with search-history dropdown, auto-filled with the last query.
        final MaterialAutoCompleteTextView queryView = new MaterialAutoCompleteTextView(this);
        queryView.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                history.stream().map(i -> i.query).toArray(String[]::new)));
        queryView.setText(last != null ? last.query : "");
        TextInputLayout queryLayout = dropdownLayout(queryView,
                getString(R.string.translation_filter_query));
        root.addView(queryLayout);

        // Scope dropdown: all / translated / untranslated.
        final String[] scopes = {getString(R.string.translation_scope_all),
                getString(R.string.translation_scope_translated),
                getString(R.string.translation_scope_untranslated)};
        final MaterialAutoCompleteTextView scopeView = new MaterialAutoCompleteTextView(this);
        scopeView.setAdapter(new android.widget.ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, scopes));
        int scopeIndex = last != null ? last.status : 0;
        scopeView.setText(scopes[scopeIndex], false);
        TextInputLayout scopeLayout = dropdownLayout(scopeView,
                getString(R.string.translation_filter_scope));
        root.addView(scopeLayout);

        // Option checkboxes.
        LinearLayout optionsRow1 = new LinearLayout(this);
        optionsRow1.setOrientation(LinearLayout.HORIZONTAL);
        final MaterialCheckBox excludeBox = new MaterialCheckBox(this);
        excludeBox.setText(R.string.translation_exclude);
        excludeBox.setChecked(last != null && last.exclude);
        excludeBox.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        optionsRow1.addView(excludeBox);
        final MaterialCheckBox caseBox = new MaterialCheckBox(this);
        caseBox.setText(R.string.match_case);
        caseBox.setChecked(last != null && last.matchCase);
        caseBox.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        optionsRow1.addView(caseBox);
        root.addView(optionsRow1);

        LinearLayout optionsRow2 = new LinearLayout(this);
        optionsRow2.setOrientation(LinearLayout.HORIZONTAL);
        final MaterialCheckBox regexBox = new MaterialCheckBox(this);
        regexBox.setText(R.string.translation_regex);
        regexBox.setChecked(last != null && last.regex);
        regexBox.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        optionsRow2.addView(regexBox);
        final MaterialCheckBox exactBox = new MaterialCheckBox(this);
        exactBox.setText(R.string.translation_exactly);
        exactBox.setChecked(last != null && last.exact);
        exactBox.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        optionsRow2.addView(exactBox);
        root.addView(optionsRow2);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_strings)
                .setView(root)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    StringAdapter.FilterSpec spec = new StringAdapter.FilterSpec();
                    spec.query = String.valueOf(queryView.getText());
                    String scope = String.valueOf(scopeView.getText());
                    spec.status = scope.equals(scopes[1]) ? 1 : scope.equals(scopes[2]) ? 2 : 0;
                    spec.exclude = excludeBox.isChecked();
                    spec.matchCase = caseBox.isChecked();
                    spec.regex = regexBox.isChecked();
                    spec.exact = exactBox.isChecked();
                    spec.active = !spec.query.trim().isEmpty() || spec.status != 0;
                    adapter.applyFilter(spec);
                    // history: dedupe, most recent first
                    List<SearchHistoryHelper.Item> updated = new ArrayList<>();
                    String query = spec.query.trim();
                    if (!query.isEmpty()) updated.add(new SearchHistoryHelper.Item(query, false));
                    for (SearchHistoryHelper.Item item : history) {
                        if (!item.query.equals(query)) updated.add(item);
                    }
                    SearchHistoryHelper.save(this, FILTER_HISTORY_KEY, updated);
                    // persist the whole spec for next-time autofill
                    try {
                        JSONObject json = new JSONObject();
                        json.put("q", spec.query);
                        json.put("status", spec.status);
                        json.put("exclude", spec.exclude);
                        json.put("mc", spec.matchCase);
                        json.put("re", spec.regex);
                        json.put("ex", spec.exact);
                        prefs.edit().putString(PREF_LAST_FILTER, json.toString()).apply();
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(R.string.clear, (d, w) -> {
                    adapter.applyFilter(new StringAdapter.FilterSpec());
                    prefs.edit().remove(PREF_LAST_FILTER).apply();
                })
                .show();
        showKeyboard(queryView);
    }

    private TextInputLayout dropdownLayout(MaterialAutoCompleteTextView view, String hint) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private TextInputLayout filledLayout(EditText view, String hint) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    // ------------------------------------------------------------------
    // replace (Material + match/case/regex/exactly options)
    // ------------------------------------------------------------------

    private void showReplaceDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);

        final EditText findInput = new EditText(this);
        findInput.setHint(R.string.find);
        TextInputLayout findLayout = filledLayout(findInput, getString(R.string.find));
        container.addView(findLayout);

        final EditText replaceInput = new EditText(this);
        replaceInput.setHint(R.string.replace_with);
        TextInputLayout replaceLayout = filledLayout(replaceInput, getString(R.string.replace_with));
        container.addView(replaceLayout);

        LinearLayout optionsRow1 = new LinearLayout(this);
        optionsRow1.setOrientation(LinearLayout.HORIZONTAL);
        final MaterialCheckBox matchCaseBox = new MaterialCheckBox(this);
        matchCaseBox.setText(R.string.match_case);
        matchCaseBox.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        optionsRow1.addView(matchCaseBox);
        final MaterialCheckBox regexBox = new MaterialCheckBox(this);
        regexBox.setText(R.string.translation_regex);
        regexBox.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        optionsRow1.addView(regexBox);
        container.addView(optionsRow1);

        final MaterialCheckBox exactBox = new MaterialCheckBox(this);
        exactBox.setText(R.string.translation_exactly);
        container.addView(exactBox);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.replace_in_all_strings)
                .setView(container)
                .setPositiveButton(R.string.replace, (d, w) -> {
                    String find = String.valueOf(findInput.getText());
                    String replace = String.valueOf(replaceInput.getText());
                    if (find.isEmpty()) return;
                    int count = applyReplaceAll(find, replace,
                            matchCaseBox.isChecked(), regexBox.isChecked(), exactBox.isChecked());
                    if (count == 0) {
                        toast(R.string.translation_nothing_matched);
                    } else {
                        dirty = true;
                        toast(getString(R.string.translation_replaced, count));
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Applies find/replace over every string's current translation (or the
     *  original when untranslated). @return how many strings changed. */
    private int applyReplaceAll(String find, String replace, boolean matchCase,
                                boolean regex, boolean exact) {
        int changed = 0;
        Pattern pattern = regex ? Pattern.compile(find,
                matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) : null;
        for (String original : originals) {
            String current = adapter.getPendingValue(original);
            String updated;
            if (exact) {
                boolean eq = matchCase ? current.equals(find)
                        : current.equalsIgnoreCase(find);
                updated = eq ? replace : current;
            } else if (regex) {
                Matcher m = pattern.matcher(current);
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    m.appendReplacement(sb, replace);
                }
                m.appendTail(sb);
                updated = sb.toString();
            } else {
                updated = matchCase
                        ? current.replace(find, replace)
                        : Pattern.compile(Pattern.quote(find), Pattern.CASE_INSENSITIVE)
                                .matcher(current).replaceAll(Matcher.quoteReplacement(replace));
            }
            if (!updated.equals(current)) {
                adapter.setTranslation(original, updated);
                changed++;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // check / language / jump
    // ------------------------------------------------------------------

    private void showCheckDialog() {
        int total = originals.size();
        int translated = 0;
        String firstUntranslated = null;
        for (String original : originals) {
            if (adapter.hasTranslation(original)) {
                translated++;
            } else if (firstUntranslated == null) {
                firstUntranslated = original;
            }
        }
        final String jumpTarget = firstUntranslated;
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_check)
                .setMessage(getString(R.string.translation_check_message, total, translated, total - translated))
                .setPositiveButton(android.R.string.ok, null);
        if (jumpTarget != null) {
            builder.setNeutralButton(R.string.translation_jump_first, (d, w) -> {
                int index = indexOf(jumpTarget);
                if (index >= 0) {
                    list.scrollToPosition(index);
                    showEditDialog(index);
                }
            });
        }
        builder.show();
    }

    private void showLanguageDialog() {
        final EditText input = new EditText(this);
        input.setText(language);
        input.setHint(R.string.translation_language_hint);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_language)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        language = input.getText().toString().trim().isEmpty()
                                ? "en" : input.getText().toString().trim())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showJumpDialog() {
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.translation_jump_hint, originals.size()));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_jump)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        int n = Integer.parseInt(input.getText().toString().trim());
                        if (n >= 1 && n <= originals.size()) {
                            list.scrollToPosition(n - 1);
                            if (!adapter.isSelectionMode()) showEditDialog(n - 1);
                        }
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmExit() {
        if (adapter.isSelectionMode()) {
            adapter.exitSelectionMode();
            return;
        }
        if (!dirty) {
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unsaved_changes)
                .setMessage(R.string.save_before_exit)
                .setPositiveButton(R.string.save, (d, w) -> saveTranslations())
                .setNegativeButton(R.string.discard, (d, w) -> finish())
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("SameParameterValue")
    private void showError(String message) {
        Toast.makeText(this, message == null ? "Error" : message, Toast.LENGTH_LONG).show();
    }

    private int themeColor(int attr, int fallback) {
        try {
            android.util.TypedValue value = new android.util.TypedValue();
            if (getTheme().resolveAttribute(attr, value, true)) {
                if (value.resourceId != 0) {
                    int color = androidx.core.content.ContextCompat.getColor(this, value.resourceId);
                    if (color != 0) return color;
                }
                if (value.data != 0) return value.data;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private int onSurfaceColor() {
        return themeColor(com.google.android.material.R.attr.colorOnSurface, 0xFF1C1B1F);
    }

    private static int mergeAlpha(int argb, int alpha) {
        return (argb & 0xFFFFFF) | (alpha << 24);
    }

    @Override
    public void onBackPressed() {
        if (adapter.isSelectionMode()) {
            adapter.exitSelectionMode();
            return;
        }
        if (autoTranslateActive) {
            autoTranslateCancelled = true;
        }
        confirmExit();
    }
}

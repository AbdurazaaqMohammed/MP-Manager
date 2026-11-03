package modder.hub.dexeditor.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
import modder.hub.dexeditor.adapter.StringAdapter;
import modder.hub.dexeditor.smali.SharedSmaliUtils;
import modder.hub.dexeditor.smali.SmaliHelper;
import modder.hub.dexeditor.utils.ClassTree;

/**
 * Standalone MT-Manager-style DEX translation mode: a two-column
 * original/translation editor over one or more dex files, with MT's menu
 * (Save, Save to dictionary, Dictionary manager, Filter, Replace, Check,
 * Language, Exit) and bottom bar (jump, copy, edit, apply dictionary,
 * show untranslated). Loads its own dexes, so it does not depend on the Dex
 * Editor Plus session state.
 *
 * Saving rewrites string literals through the smali round-trip
 * (getSmaliByType -> replace -> assemble), persists every selected dex, and
 * hands the result back to the caller with the dex editor's 757 result
 * contract so MainActivity offers the usual "update the APK?" prompt with
 * auto-sign.
 */
public class TranslationModeActivity extends BaseActivity {

    private static final Pattern LITERAL_PATTERN = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    private List<String> dexPaths;
    private String zipFilePath;
    private String zipEntryPath;

    private ClassTree classTree;
    private final List<String> originals = new ArrayList<>();
    private StringAdapter adapter;
    private String lastTapped;
    private String language = "en";
    private boolean dirty;
    private boolean untranslatedOnly;
    private boolean loaded;

    private RecyclerView list;
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
        adapter = new StringAdapter(originals, this::onStringTapped, onSurfaceColor());
        adapter.setTranslationMode(true);
        adapter.setModifiedCountListener((modified, total) -> {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(modified + "/" + total);
            }
        });
        list.setAdapter(adapter);

        findViewById(R.id.btn_jump).setOnClickListener(v -> showJumpDialog());
        findViewById(R.id.btn_copy).setOnClickListener(v -> copyOriginal());
        findViewById(R.id.btn_edit).setOnClickListener(v -> {
            if (lastTapped == null) {
                toast(R.string.translation_tap_first);
            } else {
                showEditDialog(indexOf(lastTapped));
            }
        });
        findViewById(R.id.btn_translate).setOnClickListener(v -> showAutoTranslateDialog());
        findViewById(R.id.btn_untranslated).setOnClickListener(v -> {
            untranslatedOnly = !untranslatedOnly;
            adapter.setUntranslatedOnly(untranslatedOnly);
            toast(untranslatedOnly ? R.string.translation_showing_untranslated
                    : R.string.translation_showing_all);
        });

        loadStrings();
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
        showEditDialog(indexOf(original));
    }

    private int indexOf(String original) {
        return originals.indexOf(original);
    }

    // ------------------------------------------------------------------
    // edit
    // ------------------------------------------------------------------

    /** MT-style: original read-only on top, editable translation below. */
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
                .setNeutralButton(android.R.string.search_go, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            adapter.markModified(original, translationInput.getText().toString());
            dirty = true;
            dialog.dismiss();
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            // Re-enter the same dialog after returning from the translator.
            dialog.dismiss();
            translateViaApp(original);
            showEditDialog(indexOf(original));
        });
        showKeyboard(translationInput);
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

    /** Sends the string to the user-selected translation app (PROCESS_TEXT),
     *  mirroring the smali editor's translate action. */
    private void translateViaApp(String text) {
        try {
            android.content.SharedPreferences prefs =
                    getSharedPreferences("SelectedTranslationPackageName", 0);
            String packageName = prefs.getString("selectedPackage", "");
            if (packageName.isEmpty()) {
                toast(R.string.translation_select_app);
                return;
            }
            Intent intent = new Intent("android.intent.action.PROCESS_TEXT");
            intent.setType("text/plain");
            intent.putExtra("android.intent.extra.PROCESS_TEXT", text);
            intent.putExtra("android.intent.extra.PROCESS_TEXT_READONLY", true);
            intent.setPackage(packageName);
            startActivity(intent);
        } catch (Exception e) {
            toast(R.string.translation_select_app);
        }
    }

    // ------------------------------------------------------------------
    // auto translation (MT globe button): sends every untranslated string to
    // the chosen PROCESS_TEXT translation app and collects the results
    // ------------------------------------------------------------------

    private static final int REQUEST_AUTO_TRANSLATE = 4242;
    private java.util.Iterator<String> autoTranslateQueue;
    private String autoTranslateCurrent;
    private int autoTranslateDone;
    private boolean autoTranslateActive;
    private long autoTranslateStartedAt;

    private void showAutoTranslateDialog() {
        if (!loaded) {
            toast(R.string.translation_not_loaded);
            return;
        }
        android.content.pm.PackageManager pm = getPackageManager();
        Intent processText = new Intent("android.intent.action.PROCESS_TEXT");
        processText.addCategory("android.intent.category.DEFAULT");
        processText.setType("text/plain");
        List<android.content.pm.ResolveInfo> services = pm.queryIntentActivities(processText, 0);
        if (services.isEmpty()) {
            toast(R.string.translation_no_services);
            return;
        }
        android.content.SharedPreferences prefs =
                getSharedPreferences("SelectedTranslationPackageName", 0);
        String saved = prefs.getString("selectedPackage", "");
        String[] names = new String[services.size()];
        int checked = -1;
        for (int i = 0; i < services.size(); i++) {
            android.content.pm.ResolveInfo info = services.get(i);
            names[i] = info.activityInfo.applicationInfo.loadLabel(pm) + " - "
                    + info.loadLabel(pm);
            if (info.activityInfo.packageName.equals(saved)) checked = i;
        }
        final int[] selected = {checked};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.translation_service_picker)
                .setSingleChoiceItems(names, checked, (dialog, which) -> selected[0] = which)
                .setPositiveButton(R.string.translation_start, (dialog, which) -> {
                    if (selected[0] < 0) {
                        toast(R.string.translation_select_app);
                        return;
                    }
                    prefs.edit().putString("selectedPackage",
                            services.get(selected[0]).activityInfo.packageName).apply();
                    startAutoTranslate(services.get(selected[0]).activityInfo.packageName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startAutoTranslate(String packageName) {
        List<String> queue = new ArrayList<>();
        for (String original : originals) {
            if (!adapter.getModifiedStrings().containsKey(original)) queue.add(original);
        }
        if (queue.isEmpty()) {
            toast(R.string.translation_all_translated);
            return;
        }
        autoTranslateQueue = queue.iterator();
        autoTranslateActive = true;
        autoTranslateDone = 0;
        autoTranslateStartedAt = System.currentTimeMillis();
        fireNextAutoTranslate(packageName);
    }

    private void fireNextAutoTranslate(String packageName) {
        if (!autoTranslateActive || autoTranslateQueue == null || !autoTranslateQueue.hasNext()) {
            finishAutoTranslate();
            return;
        }
        autoTranslateCurrent = autoTranslateQueue.next();
        try {
            Intent intent = new Intent("android.intent.action.PROCESS_TEXT");
            intent.setType("text/plain");
            intent.putExtra("android.intent.extra.PROCESS_TEXT", autoTranslateCurrent);
            intent.putExtra("android.intent.extra.PROCESS_TEXT_READONLY", false);
            intent.setPackage(packageName);
            startActivityForResult(intent, REQUEST_AUTO_TRANSLATE);
        } catch (Exception e) {
            autoTranslateActive = false;
            showError(String.valueOf(e.getMessage()));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_AUTO_TRANSLATE || !autoTranslateActive) return;
        String result = null;
        if (data != null) {
            result = data.getStringExtra("android.intent.extra.PROCESS_TEXT");
        }
        if (result == null || result.trim().isEmpty() || result.equals(autoTranslateCurrent)) {
            // The service gave nothing usable; keep the string untranslated.
        } else {
            adapter.markModified(autoTranslateCurrent, result);
            dirty = true;
            autoTranslateDone++;
            if (getSupportActionBar() != null) {
                getSupportActionBar().setSubtitle(autoTranslateDone + "/"
                        + originals.size() + " (auto)");
            }
        }
        // Continue with the next untranslated string until exhausted.
        fireNextAutoTranslate(getSharedPreferences("SelectedTranslationPackageName", 0)
                .getString("selectedPackage", ""));
    }

    private void finishAutoTranslate() {
        autoTranslateActive = false;
        if (autoTranslateDone > 0) {
            toast(getString(R.string.translation_auto_done, autoTranslateDone,
                    (System.currentTimeMillis() - autoTranslateStartedAt) / 1000));
        } else {
            toast(R.string.translation_auto_none);
        }
    }

    // ------------------------------------------------------------------
    // menu actions
    // ------------------------------------------------------------------

    private void saveTranslations() {
        Map<String, String> changes = adapter.getModifiedStrings();
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
                            if (replacement != null) count++;
                            m.appendReplacement(sb, Matcher.quoteReplacement(
                                    "\"" + (replacement != null ? replacement : literal) + "\""));
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
                    final int fp = done, ft = total, fc = changed[0];
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

    private File dictionaryDir() {
        File dir = new File(Environment.getExternalStorageDirectory(), "MP2/dictionaries");
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return null;
        return dir;
    }

    private String dictionaryBase() {
        String source = zipFilePath != null ? zipFilePath : (dexPaths != null && !dexPaths.isEmpty() ? dexPaths.get(0) : "dex");
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
            // Long-press deletes the dictionary, like the translator-app picker.
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
                while ((read = in.read(buffer)) != -1) sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
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

    private void showFilterDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.type_to_filter);
        if (adapter.getCurrentFilter() != null) input.setText(adapter.getCurrentFilter());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_strings)
                .setView(input)
                .setPositiveButton(R.string.apply, (d, w) -> adapter.setFilter(input.getText().toString()))
                .setNegativeButton(R.string.clear, (d, w) -> adapter.setFilter(null))
                .show();
    }

    private void showReplaceDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        final EditText findInput = new EditText(this);
        findInput.setHint(R.string.find);
        final EditText replaceInput = new EditText(this);
        replaceInput.setHint(R.string.replace_with);
        container.addView(wrap(findInput, null));
        container.addView(wrap(replaceInput, null));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.replace_in_all_strings)
                .setView(container)
                .setPositiveButton(R.string.replace, (d, w) -> {
                    String find = findInput.getText().toString();
                    String replace = replaceInput.getText().toString();
                    if (find.isEmpty()) return;
                    Map<String, String> updated = new LinkedHashMap<>();
                    for (String original : originals) {
                        String current = adapter.getPendingValue(original);
                        if (current.contains(find)) {
                            updated.put(original, current.replace(find, replace));
                        }
                    }
                    if (updated.isEmpty()) {
                        toast(R.string.translation_nothing_matched);
                        return;
                    }
                    adapter.loadTranslations(updated);
                    dirty = true;
                    toast(getString(R.string.translation_replaced, updated.size()));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCheckDialog() {
        int total = originals.size();
        int translated = 0;
        String firstUntranslated = null;
        for (String original : originals) {
            if (adapter.getModifiedStrings().containsKey(original)) {
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
                            showEditDialog(n - 1);
                        }
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void copyOriginal() {
        if (lastTapped == null) {
            toast(R.string.translation_tap_first);
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("string", lastTapped));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void confirmExit() {
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
        int resolved = themeColor(com.google.android.material.R.attr.colorOnSurface, 0);
        if (resolved != 0) return resolved;
        return 0xFF1C1B1F;
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

    @Override
    public void onBackPressed() {
        confirmExit();
    }
}

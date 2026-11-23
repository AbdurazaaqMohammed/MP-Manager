package io.github.abdurazaaqmohammed.ui.editor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.common.base.BaseActivity;
import io.github.abdurazaaqmohammed.ui.editor.symbols.SymbolDrawerActivity;
import modder.hub.dexeditor.activity.EditFloatingMenusActivity;

public class EditorSettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        SearchView searchView = new SearchView(this);
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint(getString(R.string.search));
        searchView.setBackgroundResource(android.R.color.transparent);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        searchView.setPadding(pad, pad / 2, pad, pad / 2);

        FrameLayout container = new FrameLayout(this);
        container.setId(View.generateViewId());

        root.addView(searchView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(container, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        setContentView(root);

        SettingsFragment fragment = new SettingsFragment();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(container.getId(), fragment)
                .commit();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                fragment.filterPreferences(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                fragment.filterPreferences(newText);
                return true;
            }
        });

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.editor_settings);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final int REQUEST_CODE_IMPORT_SYNTAX = 3001;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("editor_prefs");
            setPreferencesFromResource(R.xml.editor_preferences, rootKey);

            Preference importSyntaxPref = findPreference("pref_import_syntax");
            if (importSyntaxPref != null) {
                importSyntaxPref.setOnPreferenceClickListener(pref -> {
                    Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("*/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, REQUEST_CODE_IMPORT_SYNTAX);
                    return true;
                });
            }

            Preference floatingMenu = findPreference("edit_floating_menus");
            if (floatingMenu != null) {
                floatingMenu.setOnPreferenceClickListener(pref -> {
                    startActivity(new Intent(getActivity(), EditFloatingMenusActivity.class));
                    return true;
                });
            }

            EditTextPreference tabSizePref = findPreference("pref_tab_size");
            if (tabSizePref != null) {
                tabSizePref.setOnBindEditTextListener(et -> et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER));
            }

            PreferenceCategory aiCategory = findPreference("gemini_ai_category");
            if (aiCategory != null) {
                if (requireActivity().getIntent().getBooleanExtra("from_dex", false)) {
                    setupAiPreferences();
                } else {
                    getPreferenceScreen().removePreference(aiCategory);
                }
            }
        }

        public void filterPreferences(String query) {
            PreferenceScreen screen = getPreferenceScreen();
            if (screen == null) return;
            boolean empty = query == null || query.trim().isEmpty();
            String q = empty ? "" : query.trim().toLowerCase();

            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                if (p instanceof PreferenceCategory) {
                    PreferenceCategory cat = (PreferenceCategory) p;
                    int visibleCount = 0;
                    for (int j = 0; j < cat.getPreferenceCount(); j++) {
                        Preference child = cat.getPreference(j);
                        boolean matches = empty || matchesQuery(child, q);
                        child.setVisible(matches);
                        if (matches) visibleCount++;
                    }
                    cat.setVisible(empty || visibleCount > 0);
                } else {
                    p.setVisible(empty || matchesQuery(p, q));
                }
            }
        }

        private boolean matchesQuery(Preference p, String q) {
            CharSequence title = p.getTitle();
            CharSequence summary = p.getSummary();
            return (title != null && title.toString().toLowerCase().contains(q)) ||
                   (summary != null && summary.toString().toLowerCase().contains(q));
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_CODE_IMPORT_SYNTAX && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                importSyntaxFile(data.getData());
            }
        }

        private void importSyntaxFile(Uri uri) {
            try {
                Context ctx = requireContext();
                String fileName = "syntax_" + System.currentTimeMillis() + ".tmLanguage.json";
                android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
                if (c != null) {
                    int nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIdx >= 0 && c.moveToFirst()) {
                        String name = c.getString(nameIdx);
                        if (name != null && !name.isEmpty()) fileName = name;
                    }
                    c.close();
                }

                java.io.File dir = new java.io.File(ctx.getFilesDir(), "syntaxes");
                if (!dir.exists()) dir.mkdirs();
                java.io.File dest = new java.io.File(dir, fileName);

                try (java.io.InputStream in = ctx.getContentResolver().openInputStream(uri);
                     java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                }
                Toast.makeText(ctx, "Imported syntax: " + fileName, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to import syntax: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void setupAiPreferences() {
            EditTextPreference apiKeyPref = findPreference("gemini_api_key");
            if (apiKeyPref != null) {
                apiKeyPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    String apiKey = ((String) newValue).trim();
                    if (apiKey.isEmpty()) {
                        Toast.makeText(requireContext(), "Input key", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    preference.setSummary(formatApiKeySummary(apiKey));
                    return true;
                });
                String currentApiKey = apiKeyPref.getText();
                if (currentApiKey != null) {
                    apiKeyPref.setSummary(formatApiKeySummary(currentApiKey));
                }
            }

            Preference refreshPref = findPreference("gemini_refresh_models");
            if (refreshPref != null) {
                refreshPref.setOnPreferenceClickListener(preference -> {
                    refreshGeminiModels();
                    return true;
                });
            }

            Preference websitePref = findPreference("gemini_website");
            if (websitePref != null) {
                websitePref.setOnPreferenceClickListener(preference -> {
                    openGeminiWebsite();
                    return true;
                });
            }

            refreshGeminiModels(false);
        }

        private void refreshGeminiModels() {
            refreshGeminiModels(true);
        }

        private void refreshGeminiModels(boolean manual) {
            Context ctx = getContext();
            if (ctx == null) return;
            if (modder.hub.dexeditor.fragment.SettingsFragment.getChatGptApiKey(ctx).trim().isEmpty()) {
                if (manual) Toast.makeText(ctx, "Set API key first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (manual) Toast.makeText(ctx, "Fetching models", Toast.LENGTH_SHORT).show();
            modder.hub.dexeditor.fragment.SettingsFragment.fetchGeminiModels(ctx, models -> {
                Activity a = getActivity();
                if (a == null || !isAdded()) return;
                a.runOnUiThread(() -> {
                    if (models.isEmpty()) {
                        if (manual) Toast.makeText(a, "No Gemini models found", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ListPreference modelPref = findPreference("gemini_model");
                    if (modelPref != null) {
                        String[] vals = models.toArray(new String[0]);
                        modelPref.setEntries(vals);
                        modelPref.setEntryValues(vals);
                        String cur = modelPref.getValue();
                        if (cur == null || !models.contains(cur)) {
                            String fallback = modder.hub.dexeditor.fragment.SettingsFragment.getGeminiModel(a);
                            modelPref.setValue(models.contains(fallback) ? fallback : models.get(0));
                        }
                    }
                    if (manual) Toast.makeText(a, models.size() + " models loaded", Toast.LENGTH_SHORT).show();
                });
            });
        }

        private static String formatApiKeySummary(String apiKey) {
            return apiKey.isEmpty() ? "API Key not set" :
                    "••••••••" + apiKey.substring(Math.max(0, apiKey.length() - 4));
        }

        private void openGeminiWebsite() {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://aistudio.google.com/app/u/0/apikey")));
            } catch (Exception e) {
                Toast.makeText(requireContext(), "No browser found", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            String key = preference.getKey();
            if ("pref_bottom_bar_buttons".equals(key)) {
                showBottomBarManagementDialog();
                return true;
            }
            return super.onPreferenceTreeClick(preference);
        }

        private void showBottomBarManagementDialog() {
            startActivity(new Intent(getContext(), SymbolDrawerActivity.class));
        }
    }
}

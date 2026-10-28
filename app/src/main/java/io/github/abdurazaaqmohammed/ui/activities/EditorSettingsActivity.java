package io.github.abdurazaaqmohammed.ui.activities;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import android.widget.Toast;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;


import android.zero.studio.widget.editor.symbolinput.SymbolManagerActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import modder.hub.dexeditor.activity.EditFloatingMenusActivity;

public class EditorSettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();

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
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("editor_prefs");
            setPreferencesFromResource(R.xml.editor_preferences, rootKey);

            Preference floatingMenu = findPreference("edit_floating_menus");
            if (floatingMenu != null) {
                floatingMenu.setOnPreferenceClickListener(pref -> {
                    startActivity(new Intent(getActivity(), EditFloatingMenusActivity.class));
                    return true;
                });
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
            startActivity(new Intent(getContext(), SymbolManagerActivity.class)
                    .putExtra(SymbolManagerActivity.EXTRA_THEME_STYLE,
                            ThemeRegistry.currentStyleRes(getContext())));
        }
    }
}

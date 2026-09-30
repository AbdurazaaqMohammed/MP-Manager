package io.github.abdurazaaqmohammed.features.files;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuCustomizer;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.core.ui.UIKit;
import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.SettingAction;
import io.github.abdurazaaqmohammed.plugins.ext.SettingToggle;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.ThemePickerDialog;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.UpdateUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import net.lingala.zip4j.model.enums.CompressionLevel;

import rikka.shizuku.Shizuku;

/**
 * Settings dialog + sections extracted from MainActivity.
 */
public class SettingsController {

    private final MainActivity activity;

    public SettingsController(MainActivity activity) {
        this.activity = activity;
    }

    /**
     * Which of the four hard-coded buttons to light up. Imported themes have no
     * button of their own, so they fall back to the light arm (kept on the
     * fallback side of the ternary in showSettingsDialog's caller chain).
     */
    private int checkedThemeButton() {
        String id = ThemeRegistry.getCurrentId(activity);
        if (BuiltInThemes.SYSTEM_DEFAULT_ID.equals(id)) return R.id.systemThemeButton;
        if (BuiltInThemes.DARK_ID.equals(id)) return R.id.darkThemeButton;
        if (BuiltInThemes.BLACK_ID.equals(id)) return R.id.blackThemeButton;
        if (BuiltInThemes.MT_DARK_ID.equals(id)) return R.id.darkThemeButton;
        return R.id.lightThemeButton;
    }

    public void showSettingsDialog() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);
        ScrollView settingsDialog = (ScrollView) LayoutInflater.from(activity).inflate(R.layout.dialog_settings, null);

        MaterialButtonToggleGroup themeButtons = settingsDialog.findViewById(R.id.themeToggleGroup);
        themeButtons.check(checkedThemeButton());
        settingsDialog.findViewById(R.id.moreThemesButton)
                .setOnClickListener(v -> ThemePickerDialog.show(activity));
        for (int i = 0; i < themeButtons.getChildCount(); i++) {
            View child = themeButtons.getChildAt(i);
            if (child instanceof MaterialButton) {
                child.setOnLongClickListener(v3 -> {
                    // Built-ins just show their name; an imported theme is not
                    // reachable from this group, so deletion lives in the picker.
                    int buttonId = v3.getId();
                    if (buttonId == R.id.lightThemeButton) {
                        Extensions.showMessage(activity, R.string.light_theme);
                    } else if (buttonId == R.id.darkThemeButton) {
                        Extensions.showMessage(activity, R.string.dark_theme);
                    } else if (buttonId == R.id.blackThemeButton) {
                        Extensions.showMessage(activity, R.string.black_theme);
                    } else if (buttonId == R.id.systemThemeButton) {
                        Extensions.showMessage(activity, R.string.system_theme);
                    }
                    return true;
                });
            }
        }

        // Guard against re-entry: check() below re-fires this same listener.
        final boolean[] suppress = {false};
        themeButtons.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || suppress[0]) return;
            suppress[0] = true;
            try {
                String pluginId;
                boolean system = false;
                if (checkedId == R.id.lightThemeButton) {
                    themeButtons.check(R.id.lightThemeButton);
                    pluginId = BuiltInThemes.LIGHT_ID;
                } else if (checkedId == R.id.darkThemeButton) {
                    themeButtons.check(R.id.darkThemeButton);
                    pluginId = BuiltInThemes.DARK_ID;
                } else if (checkedId == R.id.blackThemeButton) {
                    themeButtons.check(R.id.blackThemeButton);
                    pluginId = BuiltInThemes.BLACK_ID;
                } else {
                    system = true;
                    themeButtons.check(R.id.systemThemeButton);
                    pluginId = BuiltInThemes.SYSTEM_DEFAULT_ID;
                }
                activity.setSystemTheme(system);

                ThemeRegistry.setCurrentId(activity, pluginId);
                activity.theme = ThemeRegistry.currentStyleRes(activity);
                // Keep legacy int pref in sync for any remaining readers.
                settings.edit().putInt("theme", activity.theme).apply();
                // setCurrentId -> setDefaultNightMode already triggers a rebuild on some
                // devices; recreating here from inside the click callback races it and
                // leaves the old theme on screen. Post it instead.
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) activity.recreate();
                });
            } finally {
                suppress[0] = false;
            }
        });

        CompoundButton logSwitch = settingsDialog.findViewById(R.id.logToggle);
        logSwitch.setChecked(activity.isLogEnabled());
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> activity.setLogEnabled(isChecked));

        CompoundButton playerModeSwitch = settingsDialog.findViewById(R.id.playerModeToggle);
        playerModeSwitch.setChecked(settings.getBoolean("player_open_activity", false));
        playerModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("player_open_activity", isChecked).apply());

        CompoundButton fixMimeTypeToggle = settingsDialog.findViewById(R.id.fixMimeTypeToggle);
        fixMimeTypeToggle.setChecked(settings.getBoolean("fix_mime_type", false));
        fixMimeTypeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("fix_mime_type", isChecked).apply());

        CompoundButton askBookmarkTabToggle = settingsDialog.findViewById(R.id.askBookmarkTabToggle);
        askBookmarkTabToggle.setChecked(settings.getBoolean("ask_bookmark_tab", false));
        askBookmarkTabToggle.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("ask_bookmark_tab", isChecked).apply());

        CompoundButton sidebarBookmarksToggle = settingsDialog.findViewById(R.id.sidebarBookmarksToggle);
        sidebarBookmarksToggle.setChecked(settings.getBoolean("sidebar_show_bookmarks", true));
        sidebarBookmarksToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean("sidebar_show_bookmarks", isChecked).apply();
            activity.refreshSidebar(activity.getSidebarSectionOrder());
        });

        CompoundButton sidebarBookmarkGroupsToggle = settingsDialog.findViewById(R.id.sidebarBookmarkGroupsToggle);
        sidebarBookmarkGroupsToggle.setChecked(settings.getBoolean("sidebar_show_bookmark_groups", false));
        sidebarBookmarkGroupsToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean("sidebar_show_bookmark_groups", isChecked).apply();
            activity.refreshSidebar(activity.getSidebarSectionOrder());
        });

        EditText searchHistoryLimitEt = settingsDialog.findViewById(R.id.searchHistoryLimitEt);
        if (searchHistoryLimitEt != null) {
            int limit = settings.getInt("search_history_limit", 50);
            searchHistoryLimitEt.setText(String.valueOf(limit));
        }

        CheckBox autosign = settingsDialog.findViewById(R.id.autosign);
        autosign.setChecked(settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", isChecked).apply());
        settingsDialog.findViewById(R.id.sign_settings).setOnClickListener(activity.uiHelper.showSignSettingsDialog());
        setupAppearanceSettings(settingsDialog, settings);
        setupLanguageSettings(settingsDialog);
        setupFolderSettings(settingsDialog, settings);
        setupFileOpsSettings(settingsDialog, settings);
        setupAccessSettings(settingsDialog, settings);

        View checkUpdateNow = settingsDialog.findViewById(R.id.checkUpdateNow);
        CompoundButton updateSwitch = settingsDialog.findViewById(R.id.checkUpdatesToggle);
        updateSwitch.setChecked(activity.isCheckForUpdates());
        updateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> checkUpdateNow.setVisibility((activity.setCheckForUpdates(isChecked)) ? View.GONE : View.VISIBLE));
        checkUpdateNow.setVisibility(activity.isCheckForUpdates() ? View.GONE : View.VISIBLE);
        checkUpdateNow.setOnClickListener(v1 -> UpdateUtil.checkForUpdates(true, activity));
        settingsDialog.findViewById(R.id.about).setOnClickListener(v -> activity.uiHelper.showAboutDialog());
        setupPluginSettings(settingsDialog, settings);
        AlertDialog settingsAlert = UIKit.dialog(activity).setTitle(activity.getString(R.string.settings)).setView(settingsDialog).create();
        settingsAlert.setOnDismissListener(d -> {
            saveSuCommand(settingsDialog);
            saveDateFormat(settingsDialog);
            saveSearchHistoryLimit(settingsDialog);
            refreshFileLists();
        });
        settingsAlert.show();
    }

    /**
     * Appends the third-party "Plugins" section: one switch per
     * {@link SettingToggle} (persisted in DefaultSharedPreferences under the
     * key the plugin declares) and one button per {@link SettingAction}.
     * No section is added when no plugin contributes settings.
     */
    private void setupPluginSettings(ScrollView root, SharedPreferences settings) {
        List<SettingToggle> toggles = ExtensionRegistry.settingToggles();
        List<SettingAction> actions = ExtensionRegistry.settingActions();
        List<ExternalActions.Entry> external = new ArrayList<>();
        try {
            external = ExternalActions.settingEntries(activity);
        } catch (Exception ignored) {
        }
        if ((toggles == null || toggles.isEmpty())
                && (actions == null || actions.isEmpty())
                && (external == null || external.isEmpty())) return;
        View rootView = root.findViewById(R.id.settingsRoot);
        ViewGroup container = rootView instanceof ViewGroup ? (ViewGroup) rootView : root;
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);

        com.google.android.material.card.MaterialCardView card =
                new com.google.android.material.card.MaterialCardView(activity);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = (int) (5 * density);
        card.setLayoutParams(cardParams);
        card.setRadius(8 * density);
        card.setCardElevation(0);

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        card.addView(box);

        TextView header = new TextView(activity);
        header.setText(activity.getString(R.string.plugins_section));
        header.setTextSize(16);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        box.addView(header);

        if (toggles != null) {
            for (SettingToggle toggle : toggles) {
                if (toggle == null || toggle.key() == null) continue;
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = (int) (8 * density);
                row.setLayoutParams(rowParams);

                LinearLayout texts = new LinearLayout(activity);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                texts.setLayoutParams(textsParams);
                TextView title = new TextView(activity);
                title.setText(toggle.title() == null ? toggle.id() : toggle.title());
                title.setTextSize(14);
                texts.addView(title);
                if (toggle.summary() != null && !toggle.summary().isEmpty()) {
                    TextView summary = new TextView(activity);
                    summary.setText(toggle.summary());
                    summary.setTextSize(12);
                    summary.setAlpha(0.7f);
                    texts.addView(summary);
                }
                row.addView(texts);

                MaterialSwitch sw = new MaterialSwitch(activity);
                sw.setChecked(settings.getBoolean(toggle.key(), toggle.defaultValue()));
                final String key = toggle.key();
                sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                        settings.edit().putBoolean(key, isChecked).apply());
                row.addView(sw);
                box.addView(row);
            }
        }

        if (actions != null) {
            for (SettingAction action : actions) {
                if (action == null) continue;
                if (action.summary() != null && !action.summary().isEmpty()) {
                    TextView summary = new TextView(activity);
                    summary.setText(action.title() + "\n" + action.summary());
                    summary.setTextSize(12);
                    summary.setAlpha(0.7f);
                    LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    sp.topMargin = (int) (8 * density);
                    summary.setLayoutParams(sp);
                    box.addView(summary);
                }
                MaterialButton btn = new MaterialButton(activity);
                btn.setText(action.buttonText() == null || action.buttonText().isEmpty()
                        ? (action.title() == null ? action.id() : action.title())
                        : action.buttonText());
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.topMargin = (int) (4 * density);
                btn.setLayoutParams(bp);
                btn.setOnClickListener(v -> {
                    try {
                        action.run(activity);
                    } catch (Exception ignored) {
                    }
                });
                box.addView(btn);
            }
        }

        // External (out-of-process) setting entries discovered by intent.
        try {
            for (ExternalActions.Entry e : external) {
                if (e == null) continue;
                final String key = ExternalActions.settingKey(e);
                final boolean isAction = ExternalActions.settingIsAction(e);
                if (!isAction) {
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowParams.topMargin = (int) (8 * density);
                    row.setLayoutParams(rowParams);
                    TextView title = new TextView(activity);
                    title.setText(e.title == null || e.title.isEmpty() ? e.id : e.title);
                    title.setTextSize(14);
                    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    title.setLayoutParams(titleParams);
                    row.addView(title);
                    MaterialSwitch sw = new MaterialSwitch(activity);
                    sw.setChecked(settings.getBoolean(key, false));
                    sw.setOnCheckedChangeListener((buttonView, isChecked) ->
                            settings.edit().putBoolean(key, isChecked).apply());
                    row.addView(sw);
                    box.addView(row);
                }
                MaterialButton setup = new MaterialButton(activity);
                setup.setText(isAction
                        ? (e.title == null || e.title.isEmpty() ? e.id : e.title)
                        : "Setup");
                LinearLayout.LayoutParams setupParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                setupParams.topMargin = (int) (4 * density);
                setup.setLayoutParams(setupParams);
                setup.setOnClickListener(v -> openExternalSetting(e, key, isAction, settings));
                box.addView(setup);
            }
        } catch (Exception ignored) {
        }

        container.addView(card);
    }

    /** Opens an external setting config screen after consent; persists booleans. */
    private void openExternalSetting(ExternalActions.Entry e, String key,
                                     boolean isAction, SharedPreferences settings) {
        try {
            Intent intent = PluginHost.explicitIntent(e.plugin,
                    PluginContracts.ACTION_SETTING_CONFIG);
            intent.putExtra(PluginContracts.EXTRA_PLUGIN_ID, e.plugin.pluginId);
            intent.putExtra(PluginContracts.EXTRA_KEY, key);
            if (!isAction) {
                intent.putExtra(PluginContracts.EXTRA_VALUE, settings.getBoolean(key, false));
            }
            PluginTrust.ensureTrusted(activity, e.plugin, () ->
                    activity.launchExternalSetting(intent, result -> {
                        try {
                            if (isAction || result.getResultCode() != android.app.Activity.RESULT_OK
                                    || result.getData() == null) return;
                            android.os.Bundle extras = result.getData().getExtras();
                            if (extras == null || !extras.containsKey(PluginContracts.EXTRA_VALUE)) return;
                            Object v = extras.get(PluginContracts.EXTRA_VALUE);
                            if (v instanceof Boolean) {
                                settings.edit().putBoolean(key, (Boolean) v).apply();
                            } else if (v != null) {
                                settings.edit().putString(key, String.valueOf(v)).apply();
                            }
                        } catch (Exception ignored) {
                        }
                    }));
        } catch (Exception ignored) {
        }
    }

    private void setupLanguageSettings(ScrollView root) {
        AutoCompleteTextView languageTv = root.findViewById(R.id.languageTv);
        String[] langTags = {"", "en", "ru", "zh-CN"};
        String[] langLabels = {activity.getString(R.string.language_system), "English", "Русский", "中文 (简体)"};
        languageTv.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, langLabels));
        String current = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        int selected = 0;
        for (int i = 0; i < langTags.length; i++) {
            if (langTags[i].equals(current)) {
                selected = i;
                break;
            }
        }
        languageTv.setText(langLabels[selected], false);
        languageTv.setOnItemClickListener((p, v, pos, id) ->
                AppCompatDelegate.setApplicationLocales(langTags[pos].isEmpty()
                        ? LocaleListCompat.getEmptyLocaleList()
                        : LocaleListCompat.forLanguageTags(langTags[pos])));
    }

    private void setupAppearanceSettings(ScrollView root, SharedPreferences settings) {
        TextView sizeLabel = root.findViewById(R.id.fileSizeLabel);
        SeekBar sizeSeek = root.findViewById(R.id.fileSizeSeek);
        TextView linesLabel = root.findViewById(R.id.fileLinesLabel);
        SeekBar linesSeek = root.findViewById(R.id.fileLinesSeek);
        AutoCompleteTextView dateTv = root.findViewById(R.id.dateFormatTv);
        LinearLayout previewHolder = root.findViewById(R.id.fileSizePreview);
        View previewRow = LayoutInflater.from(activity).inflate(R.layout.list_file, previewHolder, false);
        TextView pvName = previewRow.findViewById(R.id.fileName);
        TextView pvDate = previewRow.findViewById(R.id.fileDate);
        ImageView pvIcon = previewRow.findViewById(R.id.fileIcon);
        pvName.setText(activity.getString(R.string.preview_sample_name));
        pvIcon.setImageResource(R.drawable.baseline_insert_drive_file_24);
        previewHolder.addView(previewRow);

        int scale = UiPrefs.getScale(activity);
        sizeSeek.setProgress(scale - 60);
        int lines = UiPrefs.getMaxLines(activity);
        linesSeek.setProgress(lines - 1);

        Runnable updatePreview = () -> {
            int sc = 60 + sizeSeek.getProgress();
            int ln = 1 + linesSeek.getProgress();
            sizeLabel.setText(activity.getString(R.string.file_list_size, sc));
            linesLabel.setText(activity.getString(R.string.filename_max_lines, ln));
            pvName.setTextSize(UiPrefs.nameSize(sc));
            pvName.setMaxLines(ln);
            pvName.setEllipsize(TextUtils.TruncateAt.END);
            pvDate.setTextSize(UiPrefs.dateSize(sc));
            String pattern = dateTv.getText() != null ? dateTv.getText().toString() : "";
            String dateText;
            try {
                dateText = new SimpleDateFormat(
                        pattern.isEmpty() ? UiPrefs.DATE_PRESETS[0] : pattern,
                        Locale.getDefault()).format(new Date());
            } catch (Exception e) {
                dateText = UiPrefs.formatDate(activity, System.currentTimeMillis());
            }
            pvDate.setText(dateText + " 1.2 MB");
            int px = UiPrefs.iconDp(activity, sc);
            ViewGroup.LayoutParams lp = pvIcon.getLayoutParams();
            if (lp != null) {
                lp.width = px;
                lp.height = px;
                pvIcon.setLayoutParams(lp);
            }
        };

        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updatePreview.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                settings.edit().putInt("file_list_scale", 60 + s.getProgress()).apply();
                refreshFileLists();
            }
        });
        linesSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updatePreview.run();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                settings.edit().putInt("filename_max_lines", 1 + s.getProgress()).apply();
                refreshFileLists();
            }
        });

        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, UiPrefs.DATE_PRESETS);
        dateTv.setAdapter(dateAdapter);
        dateTv.setText(UiPrefs.getDatePattern(activity), false);
        dateTv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                updatePreview.run();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        updatePreview.run();
    }

    private void saveDateFormat(ScrollView root) {
        try {
            AutoCompleteTextView dateTv = root.findViewById(R.id.dateFormatTv);
            String pattern = dateTv.getText() != null ? dateTv.getText().toString().trim() : "";
            if (pattern.isEmpty()) return;
            new SimpleDateFormat(pattern, Locale.getDefault());
            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putString("date_format", pattern).apply();
        } catch (Exception ignored) {
        }
    }

    private void saveSearchHistoryLimit(ScrollView root) {
        try {
            EditText et = root.findViewById(R.id.searchHistoryLimitEt);
            if (et == null || et.getText() == null) return;
            String s = et.getText().toString().trim();
            if (s.isEmpty()) return;
            int v = Integer.parseInt(s);
            if (v < 5) v = 5;
            if (v > 500) v = 500;
            PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt("search_history_limit", v).apply();
        } catch (Exception ignored) {
        }
    }

    private void setupFolderSettings(ScrollView root, SharedPreferences settings) {
        AutoCompleteTextView startup1 = root.findViewById(R.id.startupTv1);
        AutoCompleteTextView startup2 = root.findViewById(R.id.startupTv2);
        String[] labels = {activity.getString(R.string.opt_home_folder), activity.getString(R.string.opt_last_opened)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, labels);
        startup1.setAdapter(adapter);
        startup2.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, labels));
        startup1.setText("last".equals(UiPrefs.startupMode(activity, true)) ? labels[1] : labels[0], false);
        startup2.setText("last".equals(UiPrefs.startupMode(activity, false)) ? labels[1] : labels[0], false);
        startup1.setOnItemClickListener((p, v, pos, id) ->
                settings.edit().putString("startup_1", pos == 1 ? "last" : "home").apply());
        startup2.setOnItemClickListener((p, v, pos, id) ->
                settings.edit().putString("startup_2", pos == 1 ? "last" : "home").apply());

        TextView homeTv1 = root.findViewById(R.id.homePathTv1);
        TextView homeTv2 = root.findViewById(R.id.homePathTv2);
        homeTv1.setText(activity.getHomeDir1() != null ? activity.getHomeDir1().getPath() : "");
        homeTv2.setText(activity.getHomeDir2() != null ? activity.getHomeDir2().getPath() : "");
        root.findViewById(R.id.pickHomeBtn1).setOnClickListener(v ->
                pickDirInto(homeTv1, chosen -> {
                    settings.edit().putString("home1", chosen).apply();
                    activity.setHomeDir1(new File(chosen));
                }));
        root.findViewById(R.id.pickHomeBtn2).setOnClickListener(v ->
                pickDirInto(homeTv2, chosen -> {
                    settings.edit().putString("home2", chosen).apply();
                    activity.setHomeDir2(new File(chosen));
                }));

        TextView appPathTv = root.findViewById(R.id.appPathTv);
        appPathTv.setText(UiPrefs.appPathDir(activity,
                new File(Environment.getExternalStorageDirectory(), "MP Manager").getPath()));
        root.findViewById(R.id.pickAppPathBtn).setOnClickListener(v ->
                pickDirInto(appPathTv, chosen ->
                        settings.edit().putString("app_path_dir", chosen).apply()));
    }

    public interface DirPicked {
        void onPicked(String path);
    }

    private void pickDirInto(TextView label, DirPicked cb) {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        props.root = Environment.getExternalStorageDirectory();
        FilePickerDialog picker = new FilePickerDialog(activity, props);
        picker.setTitle(activity.getString(R.string.pick_folder));
        picker.setDialogSelectionListener(files -> {
            if (files != null && files.length > 0 && files[0] != null) {
                label.setText(files[0]);
                cb.onPicked(files[0]);
            }
        });
        picker.show();
    }

    private void setupFileOpsSettings(ScrollView root, SharedPreferences settings) {
        CompoundButton backupSwitch = root.findViewById(R.id.backupSwitch);
        backupSwitch.setChecked(settings.getBoolean("gen_backup", true));
        backupSwitch.setOnCheckedChangeListener((v, checked) ->
                settings.edit().putBoolean("gen_backup", checked).apply());

        CompoundButton preserveSwitch = root.findViewById(R.id.preserveSwitch);
        preserveSwitch.setChecked(settings.getBoolean("preserve_mtime", true));
        preserveSwitch.setOnCheckedChangeListener((v, checked) ->
                settings.edit().putBoolean("preserve_mtime", checked).apply());

        root.findViewById(R.id.customizeMenuBtn).setOnClickListener(v ->
                FileMenuCustomizer.show(activity));

        CompoundButton fileMenuTwoColumnSwitch = root.findViewById(R.id.fileMenuTwoColumnSwitch);
        fileMenuTwoColumnSwitch.setChecked(FileMenuOrder.isTwoColumn(activity));
        fileMenuTwoColumnSwitch.setOnCheckedChangeListener((v, checked) ->
                FileMenuOrder.setTwoColumn(activity, checked));

        AutoCompleteTextView compressTv = root.findViewById(R.id.compressLevelTv);
        List<String> levels = new ArrayList<>();
        for (CompressionLevel cl : CompressionLevel.values()) {
            levels.add(cl.name());
        }
        compressTv.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, levels));
        compressTv.setText(settings.getString("compressLevel",
                CompressionLevel.NO_COMPRESSION.name()), false);
        compressTv.setOnItemClickListener((p, v, pos, id) ->
                settings.edit().putString("compressLevel", levels.get(pos)).apply());
    }

    private void setupAccessSettings(ScrollView root, SharedPreferences settings) {
        RootManager rootManager = RootManager.getInstance(activity);
        AutoCompleteTextView workingModeTv = root.findViewById(R.id.workingModeTv);
        MaterialSwitch rootStatusSwitch = root.findViewById(R.id.rootStatusSwitch);
        MaterialSwitch shizukuStatusSwitch = root.findViewById(R.id.shizukuStatusSwitch);
        MaterialButton grantShizukuBtn = root.findViewById(R.id.grantShizukuBtn);
        MaterialSwitch silentInstallToggle = root.findViewById(R.id.silentInstallToggle);
        MaterialSwitch rootFileOpsToggle = root.findViewById(R.id.rootFileOpsToggle);
        MaterialSwitch shizukuFileOpsToggle = root.findViewById(R.id.shizukuFileOpsToggle);
        MaterialSwitch rootExtractorToggle = root.findViewById(R.id.rootExtractorToggle);
        MaterialButton rebootMenuBtn = root.findViewById(R.id.rebootMenuBtn);
        TextInputEditText suCommandEt = root.findViewById(R.id.suCommandEt);
        suCommandEt.setText(settings.getString("su_command", ""));

        String labelNonRoot = activity.rss.getString(R.string.non_root);
        String labelRoot = activity.rss.getString(R.string.root);
        String labelShizuku = activity.rss.getString(R.string.shizuku_mode);
        boolean shizukuSupported = Build.VERSION.SDK_INT >= 23;
        List<String> modeLabels = new ArrayList<>();
        modeLabels.add(labelNonRoot);
        modeLabels.add(labelRoot);
        if (shizukuSupported) modeLabels.add(labelShizuku);
        workingModeTv.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, modeLabels));

        RootManager.WorkingMode current = rootManager.getWorkingMode();
        if (current == RootManager.WorkingMode.ROOT) workingModeTv.setText(labelRoot, false);
        else if (current == RootManager.WorkingMode.SHIZUKU && shizukuSupported) {
            workingModeTv.setText(labelShizuku, false);
        } else workingModeTv.setText(labelNonRoot, false);

        rootStatusSwitch.setChecked(false);
        Runnable refreshShizukuRow = () -> {
            boolean running = ShizukuManager.isRunning();
            boolean granted = ShizukuManager.hasPermission();
            shizukuStatusSwitch.setChecked(running && granted);
            if (!shizukuSupported) {
                shizukuStatusSwitch.setText(activity.getString(R.string.shizuku_unsupported));
            } else if (granted) {
                shizukuStatusSwitch.setText(activity.getString(R.string.shizuku_ready));
            } else if (running) {
                shizukuStatusSwitch.setText(activity.getString(R.string.shizuku_running_no_perm));
            } else {
                shizukuStatusSwitch.setText(activity.getString(R.string.shizuku_not_running));
            }
            grantShizukuBtn.setVisibility(
                    rootManager.getWorkingMode() == RootManager.WorkingMode.SHIZUKU
                            && running && !granted ? View.VISIBLE : View.GONE);
        };
        refreshShizukuRow.run();

        Runnable applyModeUi = () -> {
            RootManager.WorkingMode mode = rootManager.getWorkingMode();
            boolean isRoot = mode == RootManager.WorkingMode.ROOT;
            boolean isSh = mode == RootManager.WorkingMode.SHIZUKU;
            silentInstallToggle.setEnabled(isRoot);
            rootFileOpsToggle.setEnabled(isRoot);
            shizukuFileOpsToggle.setEnabled(isSh);
            rootExtractorToggle.setEnabled(isRoot);
            rebootMenuBtn.setVisibility(isRoot && rootStatusSwitch.isChecked() ? View.VISIBLE : View.GONE);
            refreshShizukuRow.run();
        };
        applyModeUi.run();

        workingModeTv.setOnItemClickListener((parent, view, position, id) -> {
            String picked = modeLabels.get(position);
            if (picked.equals(labelRoot)) {
                workingModeTv.setText(labelRoot, false);
                Extensions.showMessage(activity, "Checking root…");
                new Thread(() -> {
                    boolean ok = rootManager.isRootAvailable();
                    activity.handler.post(() -> {
                        if (ok) {
                            rootManager.setWorkingMode(RootManager.WorkingMode.ROOT);
                            rootStatusSwitch.setChecked(true);
                        } else {
                            Extensions.showMessage(activity, R.string.root_denied_msg);
                            rootStatusSwitch.setChecked(false);
                            workingModeTv.setText(rootManager.getWorkingMode() == RootManager.WorkingMode.SHIZUKU
                                    ? labelShizuku : labelNonRoot, false);
                        }
                        applyModeUi.run();
                    });
                }).start();
                return;
            }
            if (picked.equals(labelShizuku)) {
                if (!shizukuSupported || !ShizukuManager.isRunning()) {
                    Extensions.showMessage(activity, R.string.shizuku_not_running);
                    workingModeTv.setText(labelNonRoot, false);
                    return;
                }
                rootManager.setWorkingMode(RootManager.WorkingMode.SHIZUKU);
                rootStatusSwitch.setChecked(false);
                applyModeUi.run();
                new Thread(() -> ShizukuManager.warmUp(activity)).start();
                if (!ShizukuManager.hasPermission()) {
                    requestShizukuPerm(refreshShizukuRow);
                }
                return;
            }
            rootManager.setWorkingMode(RootManager.WorkingMode.NON_ROOT);
            rootStatusSwitch.setChecked(false);
            applyModeUi.run();
        });

        grantShizukuBtn.setOnClickListener(v -> requestShizukuPerm(refreshShizukuRow));

        silentInstallToggle.setChecked(settings.getBoolean("silent_install", false));
        silentInstallToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("silent_install", checked).apply());

        rootFileOpsToggle.setChecked(settings.getBoolean("root_file_ops", false));
        rootFileOpsToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("root_file_ops", checked).apply());

        shizukuFileOpsToggle.setChecked(settings.getBoolean("shizuku_file_ops", false));
        shizukuFileOpsToggle.setOnCheckedChangeListener((v, checked) -> {
            settings.edit().putBoolean("shizuku_file_ops", checked).apply();
            if (checked) new Thread(() -> ShizukuManager.warmUp(activity)).start();
        });

        rootExtractorToggle.setChecked(settings.getBoolean("root_extractor", false));
        rootExtractorToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("root_extractor", checked).apply());

        rebootMenuBtn.setOnClickListener(v -> showRebootDialog());
    }

    private void requestShizukuPerm(Runnable onResult) {
        final Shizuku.OnRequestPermissionResultListener[] holder =
                new Shizuku.OnRequestPermissionResultListener[1];
        holder[0] = (code, result) -> {
            ShizukuManager.removePermissionListener(holder[0]);
            activity.handler.post(() -> {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    new Thread(() -> ShizukuManager.warmUp(activity)).start();
                } else {
                    Extensions.showMessage(activity, R.string.shizuku_running_no_perm);
                }
                onResult.run();
            });
        };
        ShizukuManager.requestPermission(activity, holder[0]);
    }

    private void saveSuCommand(ScrollView root) {
        try {
            TextInputEditText suCommandEt = root.findViewById(R.id.suCommandEt);
            String cmd = suCommandEt.getText() != null ? suCommandEt.getText().toString().trim() : "";
            if (!cmd.isEmpty() && !cmd.matches("^[A-Za-z0-9_./-]+$")) {
                Extensions.showMessage(activity, "Invalid su command, keeping previous");
                return;
            }
            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putString("su_command", cmd).apply();
            RootManager.getInstance(activity).refreshRootCache();
        } catch (Exception ignored) {
        }
    }

    private void refreshFileLists() {
        try {
            for (int id : new int[]{R.id.listViewPane1, R.id.listViewPane2}) {
                RecyclerView pane = activity.findViewById(id);
                if (pane != null && pane.getAdapter() != null) {
                    pane.getAdapter().notifyDataSetChanged();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void showRebootDialog() {
        RootManager rootManager = RootManager.getInstance(activity);
        if (!rootManager.isRootMode()) {
            Extensions.showMessage(activity, R.string.root_mode_is_disabled);
            return;
        }

        String[] options = {activity.getString(R.string.reboot), activity.getString(R.string.reboot_recovery), activity.getString(R.string.reboot_bootloader), activity.getString(R.string.power_off)};

        UIKit.dialog(activity)
                .setTitle(R.string.reboot_options)
                .setItems(options, (dialog, which) -> {
                    String message;
                    switch (which) {
                        case 0: message = activity.getString(R.string.reboot_the_device_now); break;
                        case 1: message = activity.getString(R.string.reboot_into_recovery_mode); break;
                        case 2: message = activity.getString(R.string.reboot_into_bootloader_fastboot); break;
                        case 3: message = activity.getString(R.string.power_off_the_device); break;
                        default: return;
                    }
                    UIKit.dialog(activity)
                            .setTitle(options[which])
                            .setMessage(message)
                            .setPositiveButton(activity.getString(R.string.confirm), (d2, w2) -> {
                                try {
                                    switch (which) {
                                        case 0: rootManager.reboot(null); break;
                                        case 1: rootManager.reboot("recovery"); break;
                                        case 2: rootManager.reboot("bootloader"); break;
                                        case 3: rootManager.reboot("-p"); break;
                                    }
                                    Extensions.showMessage(activity, "Rebooting...");
                                } catch (Exception e) {
                                    Extensions.showMessage(activity, "Reboot failed: " + e.getMessage());
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}

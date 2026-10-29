package io.github.abdurazaaqmohammed.features.files;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResult;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuCustomizer;
import io.github.abdurazaaqmohammed.adapters.main.FileMenuOrder;
import io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.core.ui.util.AppFont;
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.SettingAction;
import io.github.abdurazaaqmohammed.plugins.ext.SettingToggle;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust;
import io.github.abdurazaaqmohammed.ui.PaneHighlightView;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.InstallerConfig;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.SectionCollapse;
import io.github.abdurazaaqmohammed.utils.RecycleBin;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.UpdateUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import net.lingala.zip4j.model.enums.CompressionLevel;

import rikka.shizuku.Shizuku;

public class SettingsController {

    public interface ExternalSettingLauncher {
        void launch(Intent intent, Consumer<ActivityResult> callback);
    }

    private final AppCompatActivity activity;
    private final MainActivity host;
    private final ExternalSettingLauncher externalSettingLauncher;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public SettingsController(MainActivity activity) {
        this(activity, activity, activity::launchExternalSetting);
    }

    public SettingsController(AppCompatActivity activity, MainActivity host,
                              ExternalSettingLauncher externalSettingLauncher) {
        this.activity = activity;
        this.host = host;
        this.externalSettingLauncher = externalSettingLauncher;
    }

    /**
     * The theme is read once per process in a handful of places (including the text editor
     * themes and the file icons), so a live switch leaves parts of the app on the old theme.
     * Ask for a restart instead of pretending the change is complete.
     */
    private void promptThemeRestart() {
        try {
            Snackbar snackbar = Snackbar.make(
                    activity.findViewById(android.R.id.content),
                    R.string.restart_to_apply_theme, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.restart, v -> restartApp(activity));
            snackbar.show();
        } catch (Exception ignored) {
            activity.recreate();
        }
    }

    private static void restartApp(Context context) {
        try {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launch == null) return;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pending = PendingIntent.getActivity(context, 0x5448, launch,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                am.set(AlarmManager.RTC, System.currentTimeMillis() + 150, pending);
            } else {
                context.startActivity(launch);
            }
        } catch (Exception ignored) {
        }
        Process.killProcess(Process.myPid());
        System.exit(0);
    }

    public void attach(ScrollView settingsDialog) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(activity);

        MaterialButtonToggleGroup themeButtons = settingsDialog.findViewById(R.id.themeToggleGroup);
        String currentThemeId = ThemeRegistry.getCurrentId(activity);
        if (BuiltInThemes.LIGHT_ID.equals(currentThemeId)) {
            themeButtons.check(R.id.lightThemeButton);
        } else if (BuiltInThemes.DARK_ID.equals(currentThemeId)) {
            themeButtons.check(R.id.darkThemeButton);
        } else if (BuiltInThemes.BLACK_ID.equals(currentThemeId)) {
            themeButtons.check(R.id.blackThemeButton);
        } else {
            themeButtons.check(R.id.systemThemeButton);
        }
        for (int i = 0; i < themeButtons.getChildCount(); i++) {
            View child = themeButtons.getChildAt(i);
            if (child instanceof MaterialButton) {
                child.setOnLongClickListener(v3 -> {
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

        themeButtons.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
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
                applySystemThemeFlag(system);
                ThemeRegistry.setCurrentId(activity, pluginId);
                settings.edit().putInt("theme", ThemeRegistry.currentStyleRes(activity)).apply();
                promptThemeRestart();
            }
        });

        CompoundButton logSwitch = settingsDialog.findViewById(R.id.logToggle);
        logSwitch.setChecked(isLogEnabled());
        logSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> setLogEnabled(isChecked));

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
            refreshSidebar();
        });

        CompoundButton sidebarBookmarkGroupsToggle = settingsDialog.findViewById(R.id.sidebarBookmarkGroupsToggle);
        sidebarBookmarkGroupsToggle.setChecked(settings.getBoolean("sidebar_show_bookmark_groups", false));
        sidebarBookmarkGroupsToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean("sidebar_show_bookmark_groups", isChecked).apply();
            refreshSidebar();
        });

        CompoundButton sidebarPluginsToggle = settingsDialog.findViewById(R.id.sidebarPluginsToggle);
        sidebarPluginsToggle.setChecked(settings.getBoolean("sidebar_show_plugins", false));
        sidebarPluginsToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.edit().putBoolean("sidebar_show_plugins", isChecked).apply();
            refreshSidebar();
        });

        EditText searchHistoryLimitEt = settingsDialog.findViewById(R.id.searchHistoryLimitEt);
        if (searchHistoryLimitEt != null) {
            int limit = settings.getInt("search_history_limit", 50);
            searchHistoryLimitEt.setText(String.valueOf(limit));
        }

        CheckBox autosign = settingsDialog.findViewById(R.id.autosign);
        autosign.setChecked(settings.getBoolean("autosign", true));
        autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", isChecked).apply());
        settingsDialog.findViewById(R.id.sign_settings).setOnClickListener(v -> SignatureKeyDialog.show(activity));
        setupAppearanceSettings(settingsDialog, settings);
        setupPaneHighlightSettings(settingsDialog, settings);
        setupFontSettings(settingsDialog, settings);
        setupLanguageSettings(settingsDialog);
        setupFolderSettings(settingsDialog, settings);
        setupFileOpsSettings(settingsDialog, settings);
        setupAccessSettings(settingsDialog, settings);

        View checkUpdateNow = settingsDialog.findViewById(R.id.checkUpdateNow);
        CompoundButton updateSwitch = settingsDialog.findViewById(R.id.checkUpdatesToggle);
        boolean checkForUpdates = isCheckForUpdates();
        updateSwitch.setChecked(checkForUpdates);
        updateSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setCheckForUpdates(isChecked);
            checkUpdateNow.setVisibility(isChecked ? View.GONE : View.VISIBLE);
        });
        checkUpdateNow.setVisibility(checkForUpdates ? View.GONE : View.VISIBLE);
        checkUpdateNow.setOnClickListener(v1 -> UpdateUtil.checkForUpdates(true, activity));
        settingsDialog.findViewById(R.id.about).setOnClickListener(v -> new UIHelper(activity).showAboutDialog());
        setupPluginSettings(settingsDialog, settings);
        setupCollapsibleSections(settingsDialog);
    }

    public void persist(ScrollView root) {
        saveSuCommand(root);
        saveDateFormat(root);
        saveSearchHistoryLimit(root);
        refreshFileLists();
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

        MaterialCardView card =
                new MaterialCardView(activity);
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
        header.setTypeface(null, Typeface.BOLD);
        box.addView(header);

        if (toggles != null) {
            for (SettingToggle toggle : toggles) {
                if (toggle == null || toggle.key() == null) continue;
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
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
                String tl = TextUtils.isEmpty(e.title) ? e.id : e.title;
                if (!isAction) {
                    LinearLayout row = new LinearLayout(activity);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    rowParams.topMargin = (int) (8 * density);
                    row.setLayoutParams(rowParams);
                    TextView title = new TextView(activity);
                    title.setText(tl);
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
                setup.setText(isAction ? tl : activity.getString(R.string.setup));
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
        card.setId(R.id.sectionPluginsCard);
    }

    /**
     * Makes every titled settings card collapsible, restoring the persisted
     * collapsed state for each. Runs after all sections (including the dynamic
     * Plugins card) are attached.
     */
    private void setupCollapsibleSections(ScrollView root) {
        SectionCollapse.applyToSection(root, R.id.sectionAppearanceCard, "appearance");
        SectionCollapse.applyToSection(root, R.id.sectionFoldersCard, "folders");
        SectionCollapse.applyToSection(root, R.id.sectionFileOpsCard, "fileops");
        SectionCollapse.applyToSection(root, R.id.sectionElevatedCard, "elevated");
        SectionCollapse.applyToSection(root, R.id.sectionBehaviorCard, "behavior");
        SectionCollapse.applyToSection(root, R.id.sectionSigningCard, "signing");
        SectionCollapse.applyToSection(root, R.id.sectionUpdateCard, "update");
        SectionCollapse.applyToSection(root, R.id.sectionPluginsCard, "plugins");
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
                    externalSettingLauncher.launch(intent, result -> {
                        try {
                            if (isAction || result.getResultCode() != Activity.RESULT_OK
                                    || result.getData() == null) return;
                            Bundle extras = result.getData().getExtras();
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
        String[] langTags = {"", "ar", "en", "es", "fa", "ru", "th", "zh-CN"};
        String[] langLabels = {activity.getString(R.string.language_system), "العربية", "English", "Español", "فارسی", "Русский", "ภาษาไทย", "中文 (简体)"};
        languageTv.setAdapter(dropdownAdapter(langLabels));
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

    /**
     * Exposed-dropdown adapter that never filters. A plain
     * {@link ArrayAdapter} filters its list by the current text, so once e.g.
     * "System default" is selected, tapping the field only offers that one
     * matching entry and the other languages vanish. These dropdowns are
     * pick-one lists, not search fields, so all items must always show.
     */
    private ArrayAdapter<String> dropdownAdapter(String[] items) {
        return dropdownAdapter(new ArrayList<>(Arrays.asList(items)));
    }

    private ArrayAdapter<String> dropdownAdapter(List<String> items) {
        List<String> all = new ArrayList<>(items);
        return new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(all)) {
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults r = new FilterResults();
                        r.values = new ArrayList<>(all);
                        r.count = all.size();
                        return r;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results != null && results.values instanceof List) {
                            for (Object o : (List<?>) results.values) add((String) o);
                        }
                        notifyDataSetChanged();
                    }
                };
            }
        };
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

        AutoCompleteTextView folderAnimTv = root.findViewById(R.id.folderAnimTv);
        if (folderAnimTv != null) {
            String[] animValues = {UiPrefs.FOLDER_ANIM_OFF, UiPrefs.FOLDER_ANIM_SLIDE, UiPrefs.FOLDER_ANIM_SLIDE_UP, UiPrefs.FOLDER_ANIM_FADE, UiPrefs.FOLDER_ANIM_SLIDE_FADE};
            String[] animLabels = {
                    activity.getString(R.string.folder_anim_off),
                    activity.getString(R.string.folder_anim_slide),
                    activity.getString(R.string.folder_anim_slide_up),
                    activity.getString(R.string.folder_anim_fade),
                    activity.getString(R.string.folder_anim_slide_fade)};
            folderAnimTv.setAdapter(dropdownAdapter(animLabels));
            String currentAnim = UiPrefs.folderAnim(activity);
            for (int i = 0; i < animValues.length; i++) {
                if (animValues[i].equals(currentAnim)) {
                    folderAnimTv.setText(animLabels[i], false);
                    break;
                }
            }
            folderAnimTv.setOnItemClickListener((p, v, pos, id) ->
                    settings.edit().putString(UiPrefs.FOLDER_ANIM_KEY, animValues[pos]).apply());
        }

        ArrayAdapter<String> dateAdapter = dropdownAdapter(UiPrefs.DATE_PRESETS);
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

    private void setupPaneHighlightSettings(ScrollView root, SharedPreferences settings) {
        AutoCompleteTextView highlightTv = root.findViewById(R.id.paneHighlightTv);
        String[] values = {"none", "background", "underline", "border", "background_underline"};
        String[] labels = {
                activity.getString(R.string.highlight_none),
                activity.getString(R.string.highlight_background),
                activity.getString(R.string.highlight_underline),
                activity.getString(R.string.highlight_border),
                activity.getString(R.string.highlight_background_underline)};
        highlightTv.setAdapter(dropdownAdapter(labels));
        String current = settings.getString(PaneHighlightView.PREF_KEY, PaneHighlightView.DEFAULT_STYLE);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                highlightTv.setText(labels[i], false);
                break;
            }
        }
        highlightTv.setOnItemClickListener((p, v, pos, id) -> {
            settings.edit().putString(PaneHighlightView.PREF_KEY, values[pos]).apply();
            if (host != null) host.reloadPaneHighlight();
        });

        MaterialSwitch animateSwitch = root.findViewById(R.id.paneHighlightAnimateSwitch);
        animateSwitch.setChecked(settings.getBoolean(PaneHighlightController.ANIMATE_PREF_KEY, true));
        animateSwitch.setOnCheckedChangeListener((v, checked) -> {
            settings.edit().putBoolean(PaneHighlightController.ANIMATE_PREF_KEY, checked).apply();
            if (host != null) host.reloadPaneHighlight();
        });
    }

    private final Map<String, Typeface> fontPreviewCache = new HashMap<>();

    private void setupFontSettings(ScrollView root, SharedPreferences settings) {
        AutoCompleteTextView fontTv = root.findViewById(R.id.fontTv);
        List<File> systemFonts = AppFont.systemFonts();
        List<String> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        values.add("");
        labels.add(activity.getString(R.string.font_default_label));
        for (File font : systemFonts) {
            values.add(font.getAbsolutePath());
            labels.add(AppFont.displayName(font));
        }

        // Each entry previews in its own font. Like every dropdown here, the
        // adapter must not filter: see dropdownAdapter().
        final List<String> fontLabels = new ArrayList<>(labels);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_dropdown_item_1line, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView) v).setTypeface(previewTypeface(values.get(position)));
                return v;
            }

            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults r = new FilterResults();
                        r.values = new ArrayList<>(fontLabels);
                        r.count = fontLabels.size();
                        return r;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results != null && results.values instanceof List) {
                            for (Object o : (List<?>) results.values) add((String) o);
                        }
                        notifyDataSetChanged();
                    }
                };
            }
        };
        fontTv.setAdapter(adapter);

        String current = settings.getString(AppFont.PREF_KEY, "");
        int currentIdx = values.indexOf(current);
        if (currentIdx >= 0) fontTv.setText(labels.get(currentIdx), false);
        else if (!current.isEmpty()) fontTv.setText(new File(current).getName(), false);
        else fontTv.setText(labels.get(0), false);

        fontTv.setOnItemClickListener((p, v, pos, id) -> {
            settings.edit().putString(AppFont.PREF_KEY, values.get(pos)).apply();
            AppFont.invalidate();
            activity.recreate();
        });

        root.findViewById(R.id.pickFontBtn).setOnClickListener(v -> pickCustomFont(settings));
    }

    private Typeface previewTypeface(String path) {
        if (path == null || path.isEmpty()) return null;
        Typeface tf = fontPreviewCache.get(path);
        if (tf == null) {
            try {
                tf = Typeface.createFromFile(path);
            } catch (Exception e) {
                tf = null;
            }
            fontPreviewCache.put(path, tf);
        }
        return tf;
    }

    private void pickCustomFont(SharedPreferences settings) {
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.FILE_SELECT;
        props.root = Environment.getExternalStorageDirectory();
        props.extensions = new String[]{"ttf", "otf"};
        FilePickerDialog picker = new FilePickerDialog(activity, props);
        picker.setTitle(activity.getString(R.string.app_font));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) return;
            try {
                File src = new File(files[0]);
                File dir = new File(activity.getFilesDir(), "fonts");
                dir.mkdirs();
                File dst = new File(dir, src.getName());
                if (dst.exists()) dst.delete();
                FileUtils.copyFile(new FileInputStream(src), dst);
                // Fail fast on corrupt/unsupported files instead of breaking the app.
                Typeface.createFromFile(dst);
                settings.edit().putString(AppFont.PREF_KEY, dst.getAbsolutePath()).apply();
                AppFont.invalidate();
                activity.recreate();
            } catch (Exception e) {
                Extensions.showMessage(activity, R.string.font_load_failed);
            }
        });
        picker.show();
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
        ArrayAdapter<String> adapter = dropdownAdapter(labels);
        startup1.setAdapter(adapter);
        startup2.setAdapter(dropdownAdapter(labels));
        startup1.setText("last".equals(UiPrefs.startupMode(activity, true)) ? labels[1] : labels[0], false);
        startup2.setText("last".equals(UiPrefs.startupMode(activity, false)) ? labels[1] : labels[0], false);
        startup1.setOnItemClickListener((p, v, pos, id) ->
                settings.edit().putString("startup_1", pos == 1 ? "last" : "home").apply());
        startup2.setOnItemClickListener((p, v, pos, id) ->
                settings.edit().putString("startup_2", pos == 1 ? "last" : "home").apply());

        TextView homeTv1 = root.findViewById(R.id.homePathTv1);
        TextView homeTv2 = root.findViewById(R.id.homePathTv2);
        homeTv1.setText(homeDirPath(1));
        homeTv2.setText(homeDirPath(2));
        root.findViewById(R.id.pickHomeBtn1).setOnClickListener(v ->
                pickDirInto(homeTv1, chosen -> {
                    settings.edit().putString("home1", chosen).apply();
                    if (host != null) host.setHomeDir1(new File(chosen));
                }));
        root.findViewById(R.id.pickHomeBtn2).setOnClickListener(v ->
                pickDirInto(homeTv2, chosen -> {
                    settings.edit().putString("home2", chosen).apply();
                    if (host != null) host.setHomeDir2(new File(chosen));
                }));

        TextView appPathTv = root.findViewById(R.id.appPathTv);
        appPathTv.setText(UiPrefs.appPathDir(activity, UiPrefs.defaultAppDir()));
        root.findViewById(R.id.pickAppPathBtn).setOnClickListener(v ->
                pickDirInto(appPathTv, chosen -> {
                    settings.edit().putString("app_path_dir", chosen).apply();
                    // Logs and framework resources follow the new base immediately.
                    UiPrefs.frameworkDir(activity);
                }));
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

        AutoCompleteTextView recycleBinModeTv = root.findViewById(R.id.recycleBinModeTv);
        List<String> binModes = new ArrayList<>();
        binModes.add(activity.getString(R.string.ask));
        binModes.add(activity.getString(R.string.always));
        binModes.add(activity.getString(R.string.never));
        recycleBinModeTv.setAdapter(dropdownAdapter(binModes));
        String savedBinMode = RecycleBin.mode(activity);
        int binModeIdx = RecycleBin.MODE_ALWAYS.equals(savedBinMode) ? 1
                : RecycleBin.MODE_NEVER.equals(savedBinMode) ? 2 : 0;
        recycleBinModeTv.setText(binModes.get(binModeIdx), false);
        recycleBinModeTv.setOnItemClickListener((p, v, pos, id) ->
                RecycleBin.setMode(activity, pos == 1 ? RecycleBin.MODE_ALWAYS
                        : pos == 2 ? RecycleBin.MODE_NEVER : RecycleBin.MODE_ASK));

        AutoCompleteTextView retentionTv = root.findViewById(R.id.recycleBinRetentionTv);
        int[] retentionChoices = {1, 7, 30, 0};
        List<String> retentionLabels = new ArrayList<>();
        for (int d : retentionChoices) {
            retentionLabels.add(d == 0 ? activity.getString(R.string.forever)
                    : activity.getString(R.string.days_x, d));
        }
        retentionTv.setAdapter(dropdownAdapter(retentionLabels));
        int savedDays = RecycleBin.retentionDays(activity);
        int selected = 2;
        for (int i = 0; i < retentionChoices.length; i++) {
            if (retentionChoices[i] == savedDays) selected = i;
        }
        retentionTv.setText(retentionLabels.get(selected), false);
        retentionTv.setOnItemClickListener((p, v, pos, id) ->
                RecycleBin.setRetentionDays(activity, retentionChoices[pos]));

        TextView recycleBinPathTv = root.findViewById(R.id.recycleBinPathTv);
        recycleBinPathTv.setText(RecycleBin.binDir(activity).getPath());
        root.findViewById(R.id.pickRecycleBinBtn).setOnClickListener(v ->
                pickDirInto(recycleBinPathTv, chosen -> {
                    RecycleBin.setBinDir(activity, chosen);
                    recycleBinPathTv.setText(RecycleBin.binDir(activity).getPath());
                }));

        CompoundButton preserveSwitch = root.findViewById(R.id.preserveSwitch);
        preserveSwitch.setChecked(settings.getBoolean("preserve_mtime", true));
        preserveSwitch.setOnCheckedChangeListener((v, checked) ->
                settings.edit().putBoolean("preserve_mtime", checked).apply());

        View customizeMenuBtn = root.findViewById(R.id.customizeMenuBtn);
        customizeMenuBtn.setOnClickListener(v -> FileMenuCustomizer.show(activity));

        CompoundButton fileMenuTwoColumnSwitch = root.findViewById(R.id.fileMenuTwoColumnSwitch);
        fileMenuTwoColumnSwitch.setChecked(FileMenuOrder.isTwoColumn(activity));
        fileMenuTwoColumnSwitch.setOnCheckedChangeListener((v, checked) ->
                FileMenuOrder.setTwoColumn(activity, checked));

        AutoCompleteTextView compressTv = root.findViewById(R.id.compressLevelTv);
        List<String> levels = new ArrayList<>();
        for (CompressionLevel cl : CompressionLevel.values()) {
            levels.add(cl.name());
        }
        compressTv.setAdapter(dropdownAdapter(levels));
        compressTv.setText(settings.getString("compressLevel",
                CompressionLevel.NO_COMPRESSION.name()), false);
        compressTv.setOnItemClickListener((p, v, pos, id) ->
                settings.edit().putString("compressLevel", levels.get(pos)).apply());
    }

    private void setupAccessSettings(ScrollView root, SharedPreferences settings) {
        RootManager rootManager = RootManager.getInstance(activity);
        AutoCompleteTextView workingModeTv = root.findViewById(R.id.workingModeTv);
        View groupRoot = root.findViewById(R.id.groupRoot);
        View groupShizuku = root.findViewById(R.id.groupShizuku);
        View groupShizukuOptions = root.findViewById(R.id.groupShizukuOptions);
        View groupFileOps = root.findViewById(R.id.groupFileOps);
        MaterialButton grantShizukuBtn = root.findViewById(R.id.grantShizukuBtn);
        MaterialSwitch silentInstallToggle = root.findViewById(R.id.silentInstallToggle);
        MaterialSwitch shizukuSilentToggle = root.findViewById(R.id.shizukuSilentToggle);
        AutoCompleteTextView installerSourceTv = root.findViewById(R.id.installerSourceTv);
        AutoCompleteTextView installInfoTv = root.findViewById(R.id.installInfoTv);
        View installerCustomPkgLayout = root.findViewById(R.id.installerCustomPkgLayout);
        TextInputEditText installerCustomPkgEt = root.findViewById(R.id.installerCustomPkgEt);
        MaterialSwitch installAllowTestToggle = root.findViewById(R.id.installAllowTestToggle);
        MaterialSwitch installAllowDowngradeToggle = root.findViewById(R.id.installAllowDowngradeToggle);
        MaterialSwitch installGrantAllToggle = root.findViewById(R.id.installGrantAllToggle);
        MaterialSwitch installBypassLowTargetToggle = root.findViewById(R.id.installBypassLowTargetToggle);
        MaterialSwitch installAllUsersToggle = root.findViewById(R.id.installAllUsersToggle);
        MaterialSwitch rootFileOpsToggle = root.findViewById(R.id.rootFileOpsToggle);
        MaterialSwitch shizukuFileOpsToggle = root.findViewById(R.id.shizukuFileOpsToggle);
        MaterialSwitch rootExtractorToggle = root.findViewById(R.id.rootExtractorToggle);
        MaterialButton rebootMenuBtn = root.findViewById(R.id.rebootMenuBtn);
        TextInputEditText suCommandEt = root.findViewById(R.id.suCommandEt);
        suCommandEt.setText(settings.getString("su_command", ""));

        String labelNonRoot = activity.getString(R.string.non_root);
        String labelRoot = activity.getString(R.string.root);
        String labelShizuku = activity.getString(R.string.shizuku_mode);
        boolean shizukuSupported = Build.VERSION.SDK_INT >= 23;
        List<String> modeLabels = new ArrayList<>();
        modeLabels.add(labelNonRoot);
        modeLabels.add(labelRoot);
        if (shizukuSupported) modeLabels.add(labelShizuku);
        workingModeTv.setAdapter(dropdownAdapter(modeLabels));

        RootManager.WorkingMode current = rootManager.getWorkingMode();
        if (current == RootManager.WorkingMode.ROOT) workingModeTv.setText(labelRoot, false);
        else if (current == RootManager.WorkingMode.SHIZUKU && shizukuSupported) {
            workingModeTv.setText(labelShizuku, false);
        } else workingModeTv.setText(labelNonRoot, false);

        Runnable refreshShizukuRow = () -> {
            boolean running = shizukuSupported && ShizukuManager.isRunning();
            boolean granted = running && ShizukuManager.hasPermission();
            grantShizukuBtn.setVisibility(
                    rootManager.getWorkingMode() == RootManager.WorkingMode.SHIZUKU
                            && running && !granted ? View.VISIBLE : View.GONE);
        };

        Runnable applyModeUi = () -> {
            RootManager.WorkingMode mode = rootManager.getWorkingMode();
            boolean isRoot = mode == RootManager.WorkingMode.ROOT;
            boolean isSh = mode == RootManager.WorkingMode.SHIZUKU && shizukuSupported;
            boolean rootOk = isRoot && rootManager.isRootAvailable();
            boolean shizukuRunning = isSh && ShizukuManager.isRunning();
            boolean shizukuReady = shizukuRunning && ShizukuManager.hasPermission();

            // Status is shown in the mode field itself; the separate status switches only
            // duplicated what the dropdown already says.
            String status;
            if (isRoot) {
                status = labelRoot + " - " + (rootOk
                        ? activity.getString(R.string.elevated_status_available)
                        : activity.getString(R.string.elevated_status_unavailable));
            } else if (isSh) {
                status = labelShizuku + " - " + shizukuStatus(shizukuSupported, ShizukuManager.isRunning(),
                        ShizukuManager.hasPermission());
            } else {
                status = labelNonRoot;
            }
            workingModeTv.setText(status, false);

            groupRoot.setVisibility(rootOk ? View.VISIBLE : View.GONE);
            groupShizuku.setVisibility(isSh && ShizukuManager.isRunning() ? View.VISIBLE : View.GONE);
            groupShizukuOptions.setVisibility(shizukuReady ? View.VISIBLE : View.GONE);
            groupFileOps.setVisibility(rootOk || shizukuReady ? View.VISIBLE : View.GONE);

            silentInstallToggle.setEnabled(rootOk);
            shizukuSilentToggle.setEnabled(shizukuReady);
            rootFileOpsToggle.setEnabled(rootOk);
            shizukuFileOpsToggle.setEnabled(shizukuReady);
            rootExtractorToggle.setEnabled(rootOk);
            rebootMenuBtn.setVisibility(rootOk ? View.VISIBLE : View.GONE);
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
                    handler.post(() -> {
                        if (ok) {
                            rootManager.setWorkingMode(RootManager.WorkingMode.ROOT);
                        } else {
                            Extensions.showMessage(activity, R.string.root_denied_msg);
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
                applyModeUi.run();
                new Thread(() -> ShizukuManager.warmUp(activity)).start();
                if (!ShizukuManager.hasPermission()) {
                    requestShizukuPerm(applyModeUi);
                }
                return;
            }
            rootManager.setWorkingMode(RootManager.WorkingMode.NON_ROOT);
            applyModeUi.run();
        });

        grantShizukuBtn.setOnClickListener(v -> requestShizukuPerm(applyModeUi));

        silentInstallToggle.setChecked(settings.getBoolean("silent_install", false));
        silentInstallToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("silent_install", checked).apply());

        shizukuSilentToggle.setChecked(settings.getBoolean("shizuku_silent_install", false));
        shizukuSilentToggle.setOnCheckedChangeListener((v, checked) -> {
            settings.edit().putBoolean("shizuku_silent_install", checked).apply();
            if (checked) new Thread(() -> ShizukuManager.warmUp(activity)).start();
        });

        String labelSelf = activity.getString(R.string.installer_source_self);
        String labelInitiator = activity.getString(R.string.installer_source_initiator);
        String labelPlay = activity.getString(R.string.installer_source_play);
        String labelShell = activity.getString(R.string.installer_source_shell);
        String labelCustom = activity.getString(R.string.installer_source_custom);
        List<String> sourceLabels = new ArrayList<>();
        sourceLabels.add(labelSelf);
        sourceLabels.add(labelInitiator);
        sourceLabels.add(labelPlay);
        sourceLabels.add(labelShell);
        sourceLabels.add(labelCustom);
        installerSourceTv.setAdapter(dropdownAdapter(sourceLabels));
        String currentSource = InstallerConfig.getMode(activity);
        if (InstallerConfig.MODE_PLAY.equals(currentSource)) installerSourceTv.setText(labelPlay, false);
        else if (InstallerConfig.MODE_SHELL.equals(currentSource)) installerSourceTv.setText(labelShell, false);
        else if (InstallerConfig.MODE_CUSTOM.equals(currentSource)) installerSourceTv.setText(labelCustom, false);
        else if (InstallerConfig.MODE_INITIATOR.equals(currentSource)) installerSourceTv.setText(labelInitiator, false);
        else installerSourceTv.setText(labelSelf, false);
        Runnable refreshCustomPkg = () -> installerCustomPkgLayout.setVisibility(
                InstallerConfig.MODE_CUSTOM.equals(InstallerConfig.getMode(activity))
                        ? View.VISIBLE : View.GONE);
        refreshCustomPkg.run();
        installerSourceTv.setOnItemClickListener((parent, view, position, id) -> {
            String picked = sourceLabels.get(position);
            if (picked.equals(labelPlay)) InstallerConfig.setMode(activity, InstallerConfig.MODE_PLAY);
            else if (picked.equals(labelShell)) InstallerConfig.setMode(activity, InstallerConfig.MODE_SHELL);
            else if (picked.equals(labelCustom)) InstallerConfig.setMode(activity, InstallerConfig.MODE_CUSTOM);
            else if (picked.equals(labelInitiator)) InstallerConfig.setMode(activity, InstallerConfig.MODE_INITIATOR);
            else InstallerConfig.setMode(activity, InstallerConfig.MODE_SELF);
            refreshCustomPkg.run();
        });
        installerCustomPkgEt.setText(InstallerConfig.getCustomPackage(activity));
        installerCustomPkgEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                String v = s == null ? "" : s.toString().trim();
                if (!v.isEmpty() && !RootManager.isPackageNameValid(v)) {
                    installerCustomPkgEt.setError("Invalid package name");
                    return;
                }
                settings.edit().putString("installer_custom_pkg", v).apply();
            }
        });

        String labelNever = activity.getString(R.string.install_info_never);
        String labelWhenDiffers = activity.getString(R.string.install_info_when_differs);
        String labelAlways = activity.getString(R.string.install_info_always);
        List<String> installInfoLabels = new ArrayList<>();
        installInfoLabels.add(labelWhenDiffers);
        installInfoLabels.add(labelAlways);
        installInfoLabels.add(labelNever);
        if (installInfoTv != null) {
            installInfoTv.setAdapter(dropdownAdapter(installInfoLabels));
            String infoMode = InstallerConfig.getInstallInfoMode(activity);
            if (InstallerConfig.INFO_MODE_ALWAYS.equals(infoMode)) installInfoTv.setText(labelAlways, false);
            else if (InstallerConfig.INFO_MODE_NEVER.equals(infoMode)) installInfoTv.setText(labelNever, false);
            else installInfoTv.setText(labelWhenDiffers, false);
            installInfoTv.setOnItemClickListener((parent, view, position, id) -> {
                String picked = installInfoLabels.get(position);
                if (picked.equals(labelAlways)) InstallerConfig.setInstallInfoMode(activity, InstallerConfig.INFO_MODE_ALWAYS);
                else if (picked.equals(labelNever)) InstallerConfig.setInstallInfoMode(activity, InstallerConfig.INFO_MODE_NEVER);
                else InstallerConfig.setInstallInfoMode(activity, InstallerConfig.INFO_MODE_WHEN_SIGNATURE_DIFFERS);
            });
        }

        installAllowTestToggle.setChecked(settings.getBoolean("install_allow_test", false));
        installAllowTestToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("install_allow_test", checked).apply());
        installAllowDowngradeToggle.setChecked(settings.getBoolean("install_allow_downgrade", false));
        installAllowDowngradeToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("install_allow_downgrade", checked).apply());
        installGrantAllToggle.setChecked(settings.getBoolean("install_grant_all", false));
        installGrantAllToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("install_grant_all", checked).apply());
        installBypassLowTargetToggle.setChecked(settings.getBoolean("install_bypass_low_target", false));
        installBypassLowTargetToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("install_bypass_low_target", checked).apply());
        installAllUsersToggle.setChecked(settings.getBoolean("install_all_users", false));
        installAllUsersToggle.setOnCheckedChangeListener((v, checked) -> settings.edit().putBoolean("install_all_users", checked).apply());

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

    private String shizukuStatus(boolean supported, boolean running, boolean granted) {
        if (!supported) return activity.getString(R.string.shizuku_unsupported);
        if (granted) return activity.getString(R.string.shizuku_ready);
        if (running) return activity.getString(R.string.shizuku_running_no_perm);
        return activity.getString(R.string.shizuku_not_running);
    }

    private void requestShizukuPerm(Runnable onResult) {
        final Shizuku.OnRequestPermissionResultListener[] holder =
                new Shizuku.OnRequestPermissionResultListener[1];
        holder[0] = (code, result) -> {
            ShizukuManager.removePermissionListener(holder[0]);
            handler.post(() -> {
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
        if (host == null) return;
        try {
            for (int id : new int[]{R.id.listViewPane1, R.id.listViewPane2}) {
                RecyclerView pane = host.findViewById(id);
                if (pane != null && pane.getAdapter() != null) {
                    pane.getAdapter().notifyDataSetChanged();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void refreshSidebar() {
        if (host == null) return;
        try {
            host.refreshSidebar(host.getSidebarSectionOrder());
        } catch (Exception ignored) {
        }
    }

    private void applySystemThemeFlag(boolean system) {
        if (host != null) host.setSystemTheme(system);
    }

    private boolean isLogEnabled() {
        if (host != null) return host.isLogEnabled();
        return PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean("logEnabled", false);
    }

    private void setLogEnabled(boolean enabled) {
        if (host != null) {
            host.setLogEnabled(enabled);
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putBoolean("logEnabled", enabled).apply();
    }

    private boolean isCheckForUpdates() {
        if (host != null) return host.isCheckForUpdates();
        return PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean("checkForUpdates", true);
    }

    private void setCheckForUpdates(boolean enabled) {
        if (host != null) {
            host.setCheckForUpdates(enabled);
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(activity).edit()
                .putBoolean("checkForUpdates", enabled).apply();
    }

    private String homeDirPath(int pane) {
        if (host != null) {
            File dir = pane == 1 ? host.getHomeDir1() : host.getHomeDir2();
            return dir != null ? dir.getPath() : "";
        }
        String key = pane == 1 ? "home1" : "home2";
        String path = PreferenceManager.getDefaultSharedPreferences(activity).getString(key, null);
        return TextUtils.isEmpty(path) ? Environment.getExternalStorageDirectory().getPath() : path;
    }

    private void showRebootDialog() {
        RootManager rootManager = RootManager.getInstance(activity);
        if (!rootManager.isRootMode()) {
            Extensions.showMessage(activity, R.string.root_mode_is_disabled);
            return;
        }

        String[] options = {activity.getString(R.string.reboot), activity.getString(R.string.reboot_recovery), activity.getString(R.string.reboot_bootloader), activity.getString(R.string.power_off)};

        new MaterialAlertDialogBuilder(activity)
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
                    new MaterialAlertDialogBuilder(activity)
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

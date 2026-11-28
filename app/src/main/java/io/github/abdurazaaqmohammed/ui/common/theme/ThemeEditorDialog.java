package io.github.abdurazaaqmohammed.ui.common.theme;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.files.SettingsController;
import io.github.abdurazaaqmohammed.plugins.themes.LocalThemes;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePackContracts;
import io.github.abdurazaaqmohammed.plugins.themes.ThemePacks;
import io.github.abdurazaaqmohammed.ui.main.ColorWheelView;
import io.github.abdurazaaqmohammed.ui.main.FileIconArt;
import io.github.abdurazaaqmohammed.ui.main.FileIconSpec;
import io.github.abdurazaaqmohammed.ui.main.LauncherIcons;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;

/**
 * In-app theme manager/editor ("My themes"): create local themes from the
 * current theme or presets, edit named colors with {@link ColorWheelView},
 * pick light/dark, attach a custom wallpaper through SAF, configure custom
 * icons for EVERY supported file type ({@link ThemePackContracts#ICON_KINDS},
 * including the {@code parent_folder} row and optional light/dark variants)
 * through a dedicated subdialog, choose a bundled launcher icon
 * ({@link LauncherIcons}), preview everything live (editable before Apply),
 * then save/apply/edit/delete, plus portable JSON export/import through SAF.
 * All persistence goes through {@link LocalThemes} (which uses the shared
 * ThemePacks local backend); no APK building or JSON editing is ever shown
 * to the user.
 *
 * <p>Integration: {@code ThemeEditorDialog.show(activity,
 * externalSettingLauncher, onApplied)} — {@code launcher} is the settings
 * SAF bridge ({@link SettingsController.ExternalSettingLauncher}),
 * {@code onApplied} runs after a local theme was saved AND applied.
 */
public final class ThemeEditorDialog {

    private ThemeEditorDialog() {
    }

    /** Public entry point (the settings hookup calls this). */
    public static void show(AppCompatActivity activity,
                            SettingsController.ExternalSettingLauncher launcher,
                            Runnable onApplied) {
        showManager(activity, launcher, onApplied);
    }

    // ---------- manager (list of saved local themes) ----------

    /**
     * Manager dialog currently shown. Apply/Delete flows reopen the manager
     * with a refreshed list while the first manager dialog is still open
     * behind the actions dialog — without this bookkeeping the user ends up
     * with two stacked manager dialogs and a stale list underneath.
     * Weak so a dismissed dialog is never pinned by this class.
     */
    private static final java.lang.ref.WeakReference<AlertDialog> NO_MANAGER =
            new java.lang.ref.WeakReference<>(null);
    private static java.lang.ref.WeakReference<AlertDialog> sManagerDialog = NO_MANAGER;

    private static void dismissPreviousManager() {
        AlertDialog previous = sManagerDialog.get();
        sManagerDialog = NO_MANAGER;
        if (previous != null) {
            try {
                previous.dismiss();
            } catch (Exception ignored) {
            }
        }
    }

    private static void showManager(AppCompatActivity activity,
                                    SettingsController.ExternalSettingLauncher launcher,
                                    Runnable onApplied) {
        final Context app = activity.getApplicationContext();
        AppExecutors.io().execute(() -> {
            final List<LocalThemes.LocalTheme> themes = LocalThemes.list(app);
            AppExecutors.runOnUiIfAlive(activity, () -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                // A reopen (Apply/Delete refresh) replaces the stale manager.
                dismissPreviousManager();
                LinearLayout root = new LinearLayout(activity);
                root.setOrientation(LinearLayout.VERTICAL);
                ListView list = new ListView(activity);
                list.setDivider(null);
                list.setAdapter(new ManagerAdapter(activity, themes));
                list.setOnItemClickListener((parent, view, position, id) ->
                        showThemeActions(activity, launcher, onApplied,
                                themes.get(position)));
                root.addView(list, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

                MaterialAlertDialogBuilder builder =
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.my_themes)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setNeutralButton(R.string.theme_editor_new, null)
                                .setView(root);
                if (themes.isEmpty()) {
                    builder.setMessage(R.string.theme_editor_empty);
                }
                AlertDialog dialog = builder.create();
                dialog.show();
                sManagerDialog = new java.lang.ref.WeakReference<>(dialog);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                        showNewThemeMenu(activity, launcher, onApplied, dialog));
            });
        });
    }

    private static void showNewThemeMenu(AppCompatActivity activity,
                                         SettingsController.ExternalSettingLauncher launcher,
                                         Runnable onApplied, AlertDialog parent) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.theme_editor_new)
                .setItems(new CharSequence[]{
                                activity.getString(R.string.theme_editor_from_current),
                                activity.getString(R.string.theme_editor_preset_light),
                                activity.getString(R.string.theme_editor_preset_dark),
                                activity.getString(R.string.theme_editor_import),
                        },
                        (d, which) -> {
                            if (parent != null) parent.dismiss();
                            if (which == 0) {
                                LocalThemes.Draft draft =
                                        LocalThemes.draftFromSnapshot(activity, null);
                                draft.name = activity.getString(
                                        R.string.theme_editor_default_name);
                                showEditor(activity, launcher, onApplied, draft);
                            } else if (which == 1) {
                                showEditor(activity, launcher, onApplied,
                                        LocalThemes.newDraft(
                                                activity.getString(R.string.theme_editor_default_name),
                                                false));
                            } else if (which == 2) {
                                showEditor(activity, launcher, onApplied,
                                        LocalThemes.newDraft(
                                                activity.getString(R.string.theme_editor_default_name),
                                                true));
                            } else {
                                pickImportFile(activity, launcher, onApplied);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void showThemeActions(AppCompatActivity activity,
                                         SettingsController.ExternalSettingLauncher launcher,
                                         Runnable onApplied,
                                         LocalThemes.LocalTheme theme) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(theme.name == null ? theme.id : theme.name)
                .setItems(new CharSequence[]{
                                activity.getString(R.string.theme_editor_apply),
                                activity.getString(R.string.theme_editor_edit),
                                activity.getString(R.string.theme_editor_export),
                                activity.getString(R.string.theme_editor_duplicate),
                                activity.getString(R.string.theme_editor_delete),
                        },
                        (d, which) -> {
                            if (which == 0) {
                                applyTheme(activity, launcher, onApplied, theme.id);
                            } else if (which == 1) {
                                AppExecutors.io().execute(() -> {
                                    LocalThemes.Draft draft =
                                            LocalThemes.draftFrom(activity, theme.id);
                                    AppExecutors.runOnUiIfAlive(activity, () -> {
                                        if (activity.isFinishing()) return;
                                        showEditor(activity, launcher, onApplied, draft);
                                    });
                                });
                            } else if (which == 2) {
                                pickExportFile(activity, launcher, theme);
                            } else if (which == 3) {
                                LocalThemes.Draft draft =
                                        LocalThemes.duplicate(activity, theme);
                                draft.name = (theme.name == null ? "" : theme.name)
                                        + " (" + activity.getString(
                                        R.string.theme_editor_copy_suffix) + ")";
                                showEditor(activity, launcher, onApplied, draft);
                            } else {
                                confirmDelete(activity, launcher, onApplied, theme);
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void confirmDelete(AppCompatActivity activity,
                                      SettingsController.ExternalSettingLauncher launcher,
                                      Runnable onApplied,
                                      LocalThemes.LocalTheme theme) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.theme_editor_delete)
                .setMessage(activity.getString(R.string.theme_editor_delete_confirm,
                        theme.name == null ? theme.id : theme.name))
                .setPositiveButton(R.string.theme_editor_delete, (d, w) -> {
                    AppExecutors.io().execute(() -> {
                        boolean ok = LocalThemes.delete(activity, theme.id);
                        AppExecutors.runOnUiIfAlive(activity, () -> {
                            toast(activity, ok ? R.string.theme_editor_deleted
                                    : R.string.theme_editor_error);
                            if (ok) ThemeRegistry.reload(activity);
                            showManager(activity, launcher, onApplied);
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void applyTheme(AppCompatActivity activity,
                                   SettingsController.ExternalSettingLauncher launcher,
                                   Runnable onApplied, String id) {
        AppExecutors.io().execute(() -> {
            boolean ok = LocalThemes.activate(activity, id);
            AppExecutors.runOnUiIfAlive(activity, () -> {
                if (!ok) {
                    toast(activity, R.string.theme_editor_error);
                    return;
                }
                ThemeRegistry.reload(activity);
                toast(activity, R.string.theme_editor_applied);
                if (onApplied != null) onApplied.run();
                showManager(activity, launcher, onApplied);
            });
        });
    }

    // ---------- editor ----------

    private static void showEditor(AppCompatActivity activity,
                                   SettingsController.ExternalSettingLauncher launcher,
                                   Runnable onApplied, LocalThemes.Draft draft) {
        EditorState state = new EditorState(activity, launcher, onApplied, draft);
        state.show();
    }

    /** Everything one editor dialog needs; keeps lambdas small. */
    private static final class EditorState {
        final AppCompatActivity activity;
        final SettingsController.ExternalSettingLauncher launcher;
        final Runnable onApplied;
        final LocalThemes.Draft draft;
        final ThemePreviewView preview;
        AlertDialog dialog;
        EditText nameField;
        final java.util.List<ImageRow> imageRows = new java.util.ArrayList<>();
        TextView fileTypeSummary;
        final Map<String, View> chipSwatches = new java.util.LinkedHashMap<>();
        final Map<String, TextView> chipCaptions = new java.util.LinkedHashMap<>();

        EditorState(AppCompatActivity activity,
                    SettingsController.ExternalSettingLauncher launcher,
                    Runnable onApplied, LocalThemes.Draft draft) {
            this.activity = activity;
            this.launcher = launcher;
            this.onApplied = onApplied;
            this.draft = draft;
            this.preview = new ThemePreviewView(activity, draft);
        }

        void show() {
            int pad = dp(activity, 16);
            int padSmall = dp(activity, 8);
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), dp(activity, 4));
            scroll.addView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // Live preview (editable before Apply: follows every edit).
            preview.setContentDescription(
                    activity.getString(R.string.theme_editor_preview_description));
            root.addView(preview, marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 240), 0, 0, 0, padSmall));

            // Name.
            nameField = new EditText(activity);
            nameField.setSingleLine(true);
            nameField.setHint(R.string.theme_editor_name_hint);
            nameField.setText(draft.name);
            nameField.setContentDescription(
                    activity.getString(R.string.theme_editor_name_hint));
            nameField.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    draft.name = String.valueOf(s);
                }
            });
            root.addView(nameField, marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, padSmall));

            // Light / dark toggle.
            MaterialButtonToggleGroup toggle = new MaterialButtonToggleGroup(activity);
            MaterialButton light = outlinedButton(activity, R.string.theme_editor_light);
            MaterialButton dark = outlinedButton(activity, R.string.theme_editor_dark);
            light.setId(View.generateViewId());
            dark.setId(View.generateViewId());
            toggle.addView(light);
            toggle.addView(dark);
            toggle.setSingleSelection(true);
            toggle.setSelectionRequired(true);
            toggle.check(draft.dark ? dark.getId() : light.getId());
            toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                draft.dark = checkedId == dark.getId();
                preview.invalidate();
            });
            root.addView(toggle, marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, padSmall));

            // Colors: horizontally scrollable chip strip.
            addSectionHeader(root, R.string.theme_editor_section_colors);
            HorizontalScrollViewHost colorScroller = new HorizontalScrollViewHost(activity);
            colorScroller.setHorizontalScrollBarEnabled(false);
            LinearLayout colorRow = new LinearLayout(activity);
            colorRow.setOrientation(LinearLayout.HORIZONTAL);
            for (String token : CHIP_TOKENS) {
                colorRow.addView(colorChip(activity, token));
            }
            colorScroller.addView(colorRow, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(colorScroller, marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, padSmall));
            TextView colorMore = new TextView(activity);
            colorMore.setText(R.string.theme_editor_colors_hint);
            colorMore.setTextSize(12);
            colorMore.setPadding(dp(activity, 4), 0, 0, 0);
            root.addView(colorMore);

            // Images.
            addSectionHeader(root, R.string.theme_editor_section_images);
            imageRows.clear();
            addImageRow(root, R.string.theme_editor_wallpaper,
                    LocalThemes.ImageSlot.WALLPAPER);
            root.addView(fileTypeIconsRow(), marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(activity, 8), 0, 0));
            root.addView(wallpaperScopeRow(), marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, padSmall, 0, 0));

            // Launcher icon choice (shared bundled choices).
            addSectionHeader(root, R.string.theme_editor_section_launcher);
            root.addView(launcherRow(), marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, 0));

            TextView note = new TextView(activity);
            note.setText(R.string.theme_editor_apply_note);
            note.setTextSize(12);
            note.setPadding(dp(activity, 4), dp(activity, 8), 0, 0);
            root.addView(note);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.theme_editor_title)
                    .setView(scroll)
                    .setPositiveButton(R.string.theme_editor_save, null)
                    .setNegativeButton(android.R.string.cancel, (d, w) ->
                            LocalThemes.discard(draft))
                    .setNeutralButton(R.string.theme_editor_apply, null);
            dialog = builder.create();
            dialog.setOnCancelListener(d -> LocalThemes.discard(draft));
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                    save(false));
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                    save(true));
        }

        private void save(boolean apply) {
            if (nameField != null) draft.name = String.valueOf(nameField.getText());
            if (draft.name == null || draft.name.trim().isEmpty()) {
                toast(activity, R.string.theme_editor_name_required);
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(false);
            AppExecutors.io().execute(() -> {
                String id = apply
                        ? LocalThemes.saveAndApply(activity, draft)
                        : LocalThemes.save(activity, draft);
                LocalThemes.Draft fresh = id != null && !apply
                        ? LocalThemes.draftFrom(activity, id) : null;
                AppExecutors.runOnUiIfAlive(activity, () -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    if (id == null) {
                        toast(activity, R.string.theme_editor_error);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setEnabled(true);
                        return;
                    }
                    toast(activity, apply ? R.string.theme_editor_applied
                            : R.string.theme_editor_saved);
                    if (dialog != null) dialog.dismiss();
                    if (apply) {
                        ThemeRegistry.reload(activity);
                        if (onApplied != null) onApplied.run();
                    } else {
                        // Saved: continue editing against the persisted state.
                        showEditor(activity, launcher, onApplied, fresh);
                    }
                });
            });
        }

        // ----- color chips -----

        /** One labeled chip: rounded swatch + short human-readable caption. */
        private View colorChip(AppCompatActivity activity, String token) {
            LinearLayout chip = new LinearLayout(activity);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    dp(activity, 52), ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.rightMargin = dp(activity, 6);

            View swatch = new View(activity);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(activity, 10));
            Integer value = draft.colors.get(token);
            bg.setColor(value == null ? 0xFF757575 : value);
            bg.setStroke(dp(activity, 1), 0x66808080);
            swatch.setBackground(bg);
            chip.addView(swatch, new LinearLayout.LayoutParams(
                    dp(activity, 44), dp(activity, 44)));

            TextView caption = new TextView(activity);
            caption.setTextSize(10);
            caption.setGravity(Gravity.CENTER_HORIZONTAL);
            caption.setMaxLines(1);
            caption.setText(colorLabel(activity, token));
            chip.addView(caption, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            chip.setContentDescription(activity.getString(
                    R.string.theme_editor_color_description,
                    colorLabel(activity, token),
                    value == null ? activity.getString(R.string.theme_editor_default)
                            : hex(value)));
            chip.setOnClickListener(v -> showColorEditor(token, swatch, caption));
            chipSwatches.put(token, swatch);
            chipCaptions.put(token, caption);
            return chip;
        }

        /** Updates a chip's swatch/caption after the wheel dialog applied. */
        private void updateChip(String token) {
            View swatch = chipSwatches.get(token);
            TextView caption = chipCaptions.get(token);
            if (swatch == null) return;
            Integer value = draft.colors.get(token);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(activity, 10));
            bg.setColor(value == null ? 0xFF757575 : value);
            bg.setStroke(dp(activity, 1), 0x66808080);
            swatch.setBackground(bg);
            swatch.setContentDescription(value == null
                    ? activity.getString(R.string.theme_editor_default) : hex(value));
            if (caption != null) {
                caption.setText(value == null
                        ? activity.getString(R.string.theme_editor_default)
                        : hex(value));
            }
            preview.invalidate();
        }

        /** Full color editor: wheel + hex + reset to default. */
        private void showColorEditor(String token, View chipSwatch, TextView chipCaption) {
            Integer initial = draft.colors.get(token);
            int pad = dp(activity, 16);
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(pad, pad / 2, pad, 0);

            ColorWheelView wheel = new ColorWheelView(activity);
            root.addView(wheel, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 280)));

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View swatch = new View(activity);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(
                    dp(activity, 48), dp(activity, 48));
            swatchParams.rightMargin = pad / 2;
            row.addView(swatch, swatchParams);
            final EditText hexInput = new EditText(activity);
            hexInput.setSingleLine(true);
            hexInput.setHint("#AARRGGBB");
            row.addView(hexInput, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(row);

            final int[] chosen = {initial == null ? 0xFF6750A4 : initial};
            Runnable sync = () -> {
                int color = chosen[0];
                swatch.setBackgroundColor(color);
                swatch.setContentDescription(hex(color));
                String text = hex(color);
                if (!text.equalsIgnoreCase(String.valueOf(hexInput.getText()))) {
                    hexInput.setText(text);
                }
            };
            wheel.setListener(() -> {
                chosen[0] = wheel.getColor(Color.alpha(chosen[0]));
                sync.run();
            });
            hexInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void afterTextChanged(Editable s) {
                    String text = s.toString().trim();
                    if (text.startsWith("#")) text = text.substring(1);
                    if (text.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) {
                        try {
                            int parsed = (int) Long.parseLong(text, 16);
                            if (text.length() == 6) {
                                parsed |= chosen[0] & 0xFF000000;
                            }
                            if (parsed != chosen[0]) {
                                chosen[0] = parsed;
                                wheel.setColor(parsed);
                                swatch.setBackgroundColor(parsed);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
            wheel.setColor(initial == null ? 0xFF6750A4 : initial);
            sync.run();

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                    .setTitle(colorLabel(activity, token))
                    .setView(root)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        draft.colors.put(token, chosen[0]);
                        updateChip(token);
                    })
                    .setNegativeButton(android.R.string.cancel, null);
            if (initial != null) {
                builder.setNeutralButton(R.string.theme_editor_reset, (d, w) -> {
                    draft.colors.remove(token);
                    updateChip(token);
                });
            }
            builder.show();
        }

        // ----- image rows -----

        /** Label + pick + clear for one image slot (wallpaper row; the file
         *  type icons live in their own subdialog to keep this one light). */
        private void addImageRow(LinearLayout root, int labelRes,
                                 LocalThemes.ImageSlot slot) {
            ImageRow row = new ImageRow(activity, labelRes, slot);
            imageRows.add(row);
            root.addView(row.view, marginParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(activity, 8), 0, 0));
            row.sync();
        }

        private final class ImageRow {
            final LinearLayout view;
            final TextView label;
            final LocalThemes.ImageSlot slot;
            final int labelRes;

            ImageRow(AppCompatActivity activity, int labelRes,
                     LocalThemes.ImageSlot slot) {
                this.slot = slot;
                this.labelRes = labelRes;
                this.view = new LinearLayout(activity);
                this.view.setOrientation(LinearLayout.HORIZONTAL);
                this.view.setGravity(Gravity.CENTER_VERTICAL);
                this.label = new TextView(activity);
                this.label.setTextSize(16);
                this.view.addView(label, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                MaterialButton pick = outlinedButton(activity,
                        slot == LocalThemes.ImageSlot.WALLPAPER
                                ? R.string.theme_editor_wallpaper_pick
                                : R.string.theme_editor_icon_pick);
                pick.setOnClickListener(v -> pickImage(EditorState.this, slot, () -> {
                    sync();
                    preview.invalidate();
                    updateFileTypeSummary();
                }));
                this.view.addView(pick);
                MaterialButton clear = outlinedButton(activity,
                        R.string.theme_editor_clear);
                clear.setOnClickListener(v -> {
                    LocalThemes.clearImage(draft, slot);
                    sync();
                    preview.invalidate();
                    updateFileTypeSummary();
                });
                this.view.addView(clear);
                this.clearButton = clear;
            }

            final View clearButton;

            void sync() {
                boolean has = slot == LocalThemes.ImageSlot.WALLPAPER
                        ? draft.hasWallpaper() : draft.hasIcon(slot.iconKey);
                clearButton.setEnabled(has);
                label.setText(activity.getString(labelRes) + "\n"
                        + activity.getString(has ? R.string.theme_editor_image_set
                        : R.string.theme_editor_image_none));
            }
        }

        // ----- file type icons -----

        /** One row + summary: opens the per-file-type icon subdialog. */
        private View fileTypeIconsRow() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout text = new LinearLayout(activity);
            text.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(activity);
            label.setText(R.string.theme_editor_file_type_icons);
            label.setTextSize(16);
            text.addView(label);
            fileTypeSummary = new TextView(activity);
            fileTypeSummary.setTextSize(12);
            text.addView(fileTypeSummary);
            row.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton choose = outlinedButton(activity, R.string.theme_editor_choose);
            choose.setOnClickListener(v -> showFileTypeIconsDialog());
            row.addView(choose);
            updateFileTypeSummary();
            return row;
        }

        /** Summary text under the row label: how many kinds/variants are set. */
        private void updateFileTypeSummary() {
            if (fileTypeSummary == null) return;
            int kinds = 0;
            for (String kind : ThemePackContracts.ICON_KINDS) {
                if (draft.hasIcon(kind)) kinds++;
            }
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            keys.addAll(draft.stagedIconKeys());
            keys.addAll(draft.retainedIconKeys());
            int variants = 0;
            for (String key : keys) {
                if (!LocalThemes.ImageSlot.isBaseIconKey(key) && draft.hasIcon(key)) {
                    variants++;
                }
            }
            fileTypeSummary.setText(variants > 0
                    ? activity.getString(R.string.theme_editor_icons_summary_variants,
                            kinds, ThemePackContracts.ICON_KINDS.length, variants)
                    : activity.getString(R.string.theme_editor_icons_summary,
                            kinds, ThemePackContracts.ICON_KINDS.length));
        }

        /**
         * Subdialog listing one labeled chooser/editor row per contract icon
         * kind (including parent_folder), each with a live draft thumbnail,
         * Pick and Clear, plus a Base/Light/Dark variant toggle that retargets
         * every row to {@code <kind>}, {@code <kind>_light} or
         * {@code <kind>_dark}. Changes land in the shared draft immediately
         * (staged/cleared per key) and feed both the live preview and the
         * main dialog summary; nothing persists until the editor is saved.
         */
        private void showFileTypeIconsDialog() {
            ListView list = new ListView(activity);
            list.setDivider(null);
            final IconKindAdapter adapter = new IconKindAdapter();
            list.addHeaderView(adapter.headerView(list), null, false);
            list.setAdapter(adapter);
            AlertDialog sub = new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.theme_editor_file_type_icons)
                    .setView(list)
                    .setPositiveButton(R.string.theme_editor_done,
                            (d, w) -> updateFileTypeSummary())
                    .create();
            sub.show();
        }

        /** Rows for {@link ThemePackContracts#ICON_KINDS} in the subdialog. */
        private final class IconKindAdapter extends BaseAdapter {
            /** 0 = base key, 1 = _light, 2 = _dark. */
            int variant = 0;
            private final LruCache<String, Bitmap> thumbs = new LruCache<>(32);
            private final View header;

            IconKindAdapter() {
                header = buildHeader();
            }

            private String variantSuffix() {
                return variant == 1 ? ThemePackContracts.ICON_VARIANT_LIGHT
                        : variant == 2 ? ThemePackContracts.ICON_VARIANT_DARK : "";
            }

            private String keyFor(String kind) {
                return kind + variantSuffix();
            }

            private View buildHeader() {
                LinearLayout header = new LinearLayout(activity);
                header.setOrientation(LinearLayout.VERTICAL);
                int pad = dp(activity, 8);
                header.setPadding(pad, pad, pad, 0);
                MaterialButtonToggleGroup toggle = new MaterialButtonToggleGroup(activity);
                MaterialButton base = outlinedButton(activity,
                        R.string.theme_editor_icon_variant_base);
                MaterialButton light = outlinedButton(activity,
                        R.string.theme_editor_icon_variant_light);
                MaterialButton dark = outlinedButton(activity,
                        R.string.theme_editor_icon_variant_dark);
                base.setId(View.generateViewId());
                light.setId(View.generateViewId());
                dark.setId(View.generateViewId());
                toggle.addView(base);
                toggle.addView(light);
                toggle.addView(dark);
                toggle.setSingleSelection(true);
                toggle.setSelectionRequired(true);
                toggle.check(base.getId());
                toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (!isChecked) return;
                    variant = checkedId == light.getId() ? 1
                            : checkedId == dark.getId() ? 2 : 0;
                    thumbs.evictAll();
                    notifyDataSetChanged();
                });
                header.addView(toggle, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView hint = new TextView(activity);
                hint.setText(R.string.theme_editor_icon_variants_hint);
                hint.setTextSize(12);
                hint.setPadding(0, dp(activity, 8), 0, 0);
                header.addView(hint, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                return header;
            }

            View headerView(ViewGroup parent) {
                return header;
            }

            @Override public int getCount() {
                return ThemePackContracts.ICON_KINDS.length;
            }

            @Override public Object getItem(int position) {
                return ThemePackContracts.ICON_KINDS[position];
            }

            @Override public long getItemId(int position) {
                return position;
            }

            @Override public View getView(int position, View convertView,
                                          ViewGroup parent) {
                final String kind = ThemePackContracts.ICON_KINDS[position];
                final String key = keyFor(kind);
                Holder holder;
                if (convertView instanceof LinearLayout
                        && convertView.getTag() instanceof Holder) {
                    holder = (Holder) convertView.getTag();
                } else {
                    holder = buildRow();
                }
                holder.label.setText(iconLabel(activity, kind));
                boolean has = draft.hasIcon(key);
                holder.state.setText(activity.getString(has
                        ? R.string.theme_editor_image_set
                        : R.string.theme_editor_image_none));
                holder.clear.setEnabled(has);
                Bitmap bitmap = has ? thumbnail(key, kind)
                        : variant != 0 && draft.hasIcon(kind) ? thumbnail(kind, kind) : null;
                if (bitmap != null) {
                    holder.thumb.setImageDrawable(null);
                    holder.thumb.setImageBitmap(bitmap);
                } else {
                    holder.thumb.setImageBitmap(null);
                    holder.thumb.setImageDrawable(fallbackDrawable(kind));
                }
                holder.pick.setOnClickListener(v ->
                        pickIconImage(EditorState.this, key, () -> {
                            thumbs.evictAll();
                            notifyDataSetChanged();
                            preview.invalidate();
                            updateFileTypeSummary();
                        }));
                holder.clear.setOnClickListener(v -> {
                    LocalThemes.clearIcon(draft, key);
                    thumbs.evictAll();
                    notifyDataSetChanged();
                    preview.invalidate();
                    updateFileTypeSummary();
                });
                holder.row.setContentDescription(activity.getString(
                        R.string.theme_editor_icon_row_description,
                        holder.label.getText(), holder.state.getText()));
                return holder.row;
            }

            private Holder buildRow() {
                Holder holder = new Holder();
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = dp(activity, 10);
                row.setPadding(pad, pad / 2, pad, pad / 2);
                ImageView thumb = new ImageView(activity);
                thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);
                row.addView(thumb, new LinearLayout.LayoutParams(
                        dp(activity, 36), dp(activity, 36)));
                LinearLayout text = new LinearLayout(activity);
                text.setOrientation(LinearLayout.VERTICAL);
                int textPad = dp(activity, 10);
                text.setPadding(textPad, 0, 0, 0);
                TextView label = new TextView(activity);
                label.setTextSize(15);
                text.addView(label);
                TextView state = new TextView(activity);
                state.setTextSize(11);
                text.addView(state);
                row.addView(text, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                MaterialButton pick = outlinedButton(activity,
                        R.string.theme_editor_icon_pick);
                row.addView(pick);
                MaterialButton clear = outlinedButton(activity,
                        R.string.theme_editor_clear);
                row.addView(clear);
                holder.row = row;
                holder.thumb = thumb;
                holder.label = label;
                holder.state = state;
                holder.pick = pick;
                holder.clear = clear;
                row.setTag(holder);
                return holder;
            }

            private static final class Holder {
                LinearLayout row;
                ImageView thumb;
                TextView label;
                TextView state;
                MaterialButton pick;
                MaterialButton clear;
            }

            /** Small cached decode of the staged/retained icon file. */
            private Bitmap thumbnail(String key, String kind) {
                File file = draft.stagedIconFile(key) != null
                        ? draft.stagedIconFile(key)
                        : LocalThemes.resolveStoredImage(activity,
                        draft.retainedIconName(key));
                if (file == null || !file.isFile()) return null;
                String cacheKey = file.getAbsolutePath() + '@' + file.lastModified();
                Bitmap cached = thumbs.get(cacheKey);
                if (cached != null) return cached;
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = 1;
                    while (Math.max(bounds.outWidth, bounds.outHeight)
                            / options.inSampleSize > 96) {
                        options.inSampleSize *= 2;
                    }
                    Bitmap decoded = BitmapFactory.decodeFile(
                            file.getAbsolutePath(), options);
                    if (decoded != null) thumbs.put(cacheKey, decoded);
                    return decoded;
                } catch (Exception e) {
                    return null;
                }
            }

            /** Built-in artwork shown while a kind has no custom icon. */
            private android.graphics.drawable.Drawable fallbackDrawable(String kind) {
                try {
                    if ("parent_folder".equals(kind)) {
                        return FileIconArt.parentFolderDrawable(activity);
                    }
                    return FileIconArt.drawable(activity, builtInSpec(kind));
                } catch (Exception e) {
                    return null;
                }
            }
        }

        /** Localized label per contract icon kind (shared file-type names). */
        private static int iconLabelRes(String kind) {
            switch (kind) {
                case "folder": return R.string.folder;
                case "parent_folder": return R.string.theme_editor_parent_folder;
                case "apk": return R.string.shortcut_icon_apk;
                case "image": return R.string.image;
                case "video": return R.string.shortcut_icon_video;
                case "audio": return R.string.shortcut_icon_audio;
                case "arsc": return R.string.shortcut_icon_resources;
                case "dex": return R.string.shortcut_icon_dex;
                case "archive": return R.string.shortcut_icon_archive;
                case "sticker": return R.string.shortcut_icon_sticker;
                case "pdf": return R.string.shortcut_icon_pdf;
                case "text": return R.string.shortcut_icon_text;
                case "java": return R.string.shortcut_icon_java;
                case "kotlin": return R.string.shortcut_icon_kotlin;
                case "xml": return R.string.shortcut_icon_xml;
                case "json": return R.string.shortcut_icon_json;
                case "python": return R.string.shortcut_icon_python;
                case "smali": return R.string.shortcut_icon_smali;
                case "js": return R.string.shortcut_icon_js;
                case "elf": return R.string.shortcut_icon_elf;
                case "disk": return R.string.shortcut_icon_disk;
                case "database": return R.string.shortcut_icon_database;
                case "font": return R.string.shortcut_icon_font;
                case "binary": return R.string.shortcut_icon_binary;
                case "proto": return R.string.shortcut_icon_data;
                case "document": return R.string.shortcut_icon_document;
                case "sheet": return R.string.shortcut_icon_sheet;
                case "slide": return R.string.shortcut_icon_slide;
                case "script": return R.string.shortcut_icon_script;
                case "backup": return R.string.shortcut_icon_backup;
                case "certificate": return R.string.shortcut_icon_certificate;
                case "file": return R.string.file;
                default: return R.string.file;
            }
        }

        private static String iconLabel(AppCompatActivity activity, String kind) {
            return activity.getString(iconLabelRes(kind));
        }

        /** Built-in spec per base kind (thumbnail fallback art). */
        private static FileIconSpec builtInSpec(String kind) {
            switch (kind) {
                case "folder": case "parent_folder": return FileIconSpec.FOLDER;
                case "apk": return FileIconSpec.APK;
                case "image": return FileIconSpec.IMAGE;
                case "video": return FileIconSpec.VIDEO;
                case "audio": return FileIconSpec.AUDIO;
                case "arsc": return FileIconSpec.ARSC;
                case "dex": return FileIconSpec.DEX;
                case "archive": return FileIconSpec.ARCHIVE;
                case "sticker": return FileIconSpec.STICKER;
                case "pdf": return FileIconSpec.PDF;
                case "text": return FileIconSpec.TEXT;
                case "java": return FileIconSpec.JAVA;
                case "kotlin": return FileIconSpec.KOTLIN;
                case "xml": return FileIconSpec.XML;
                case "json": return FileIconSpec.JSON;
                case "python": return FileIconSpec.PYTHON;
                case "smali": return FileIconSpec.SMALI;
                case "js": return FileIconSpec.JS;
                case "elf": return FileIconSpec.ELF;
                case "disk": return FileIconSpec.DISK;
                case "database": return FileIconSpec.DATABASE;
                case "font": return FileIconSpec.FONT;
                case "binary": return FileIconSpec.BINARY;
                case "proto": return FileIconSpec.PROTO;
                case "document": return FileIconSpec.DOCUMENT;
                case "sheet": return FileIconSpec.SHEET;
                case "slide": return FileIconSpec.SLIDE;
                case "script": return FileIconSpec.SCRIPT;
                case "backup": return FileIconSpec.BACKUP;
                case "certificate": return FileIconSpec.CERTIFICATE;
                default: return FileIconSpec.FILE;
            }
        }

        /** Wallpaper scope: files | all | none. */
        private View wallpaperScopeRow() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(activity);
            label.setText(R.string.theme_editor_wallpaper_scope);
            label.setTextSize(16);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton choose = outlinedButton(activity, R.string.theme_editor_choose);
            choose.setOnClickListener(v -> {
                CharSequence[] options = new CharSequence[]{
                        activity.getString(R.string.theme_editor_scope_files),
                        activity.getString(R.string.theme_editor_scope_all),
                        activity.getString(R.string.theme_editor_scope_none)};
                int checked = ThemePackContracts.WALLPAPER_SCOPE_ALL.equals(draft.wallpaperScope) ? 1
                        : ThemePackContracts.WALLPAPER_SCOPE_NONE.equals(draft.wallpaperScope) ? 2 : 0;
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.theme_editor_wallpaper_scope)
                        .setSingleChoiceItems(options, checked, (d, which) -> {
                            draft.wallpaperScope = which == 1
                                    ? ThemePackContracts.WALLPAPER_SCOPE_ALL
                                    : which == 2
                                    ? ThemePackContracts.WALLPAPER_SCOPE_NONE
                                    : ThemePackContracts.WALLPAPER_SCOPE_FILES;
                            d.dismiss();
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            });
            row.addView(choose);
            return row;
        }

        /** Bundled launcher icon choice via the shared LauncherIcons API. */
        private View launcherRow() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(activity);
            label.setText(R.string.theme_editor_launcher);
            label.setTextSize(16);
            row.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton choose = outlinedButton(activity, R.string.theme_editor_choose);
            choose.setOnClickListener(v -> showLauncherChooser());
            row.addView(choose);
            return row;
        }

        private void showLauncherChooser() {
            LauncherIcons.Choice[] choices = LauncherIcons.choices();
            CharSequence[] names = new CharSequence[choices.length + 1];
            int selected = 0;
            names[0] = activity.getString(R.string.theme_editor_launcher_none);
            for (int i = 0; i < choices.length; i++) {
                names[i + 1] = activity.getString(choices[i].labelRes);
                if (choices[i].id.equals(draft.launcherId)) selected = i + 1;
            }
            final int[] picked = {selected};
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.theme_editor_launcher)
                    .setSingleChoiceItems(names, selected, (d, which) -> picked[0] = which)
                    .setPositiveButton(android.R.string.ok, (d, w) ->
                            draft.launcherId = picked[0] == 0 ? null
                                    : choices[picked[0] - 1].id)
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }
    }

    /** Rounded chip backgrounds are plain GradientDrawables; no custom View. */

    // ---------- SAF bridges ----------

    /**
     * SAF pick into one draft image slot (the wallpaper row); {@code
     * onStaged} refreshes the calling surface on the UI thread after the
     * async staging settles (success or rejection).
     */
    private static void pickImage(EditorState state, LocalThemes.ImageSlot slot,
                                  Runnable onStaged) {
        if (state == null || state.launcher == null) {
            toast(state == null ? null : state.activity, R.string.theme_editor_error);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            state.launcher.launch(intent, result -> {
                if (result == null || result.getData() == null
                        || result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) return;
                Context app = state.activity.getApplicationContext();
                LocalThemes.ImageSlot imageSlot = slot;
                AppExecutors.io().execute(() -> {
                    boolean ok = LocalThemes.stageImage(app, state.draft,
                            imageSlot, uri);
                    AppExecutors.runOnUiIfAlive(state.activity, () -> {
                        if (!ok) toast(state.activity,
                                R.string.theme_editor_image_rejected);
                        state.preview.invalidate();
                        if (onStaged != null) onStaged.run();
                    });
                });
            });
        } catch (ActivityNotFoundException e) {
            toast(state.activity, R.string.theme_editor_error);
        }
    }

    /**
     * SAF pick for one exact contract icon key (base kind or
     * {@code _light}/{@code _dark} variant); staging keeps the exact key so
     * variants never collapse onto the base kind.
     */
    private static void pickIconImage(EditorState state, String iconKey,
                                      Runnable onStaged) {
        if (state == null || state.launcher == null) {
            toast(state == null ? null : state.activity, R.string.theme_editor_error);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/png");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            state.launcher.launch(intent, result -> {
                if (result == null || result.getData() == null
                        || result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) return;
                Context app = state.activity.getApplicationContext();
                AppExecutors.io().execute(() -> {
                    boolean ok = LocalThemes.stageIcon(app, state.draft,
                            iconKey, uri);
                    AppExecutors.runOnUiIfAlive(state.activity, () -> {
                        if (!ok) toast(state.activity,
                                R.string.theme_editor_image_rejected);
                        state.preview.invalidate();
                        if (onStaged != null) onStaged.run();
                    });
                });
            });
        } catch (ActivityNotFoundException e) {
            toast(state.activity, R.string.theme_editor_error);
        }
    }

    private static void pickExportFile(AppCompatActivity activity,
                                       SettingsController.ExternalSettingLauncher launcher,
                                       LocalThemes.LocalTheme theme) {
        if (launcher == null) {
            toast(activity, R.string.theme_editor_error);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        String safe = (theme.name == null ? "theme" : theme.name)
                .replaceAll("[^A-Za-z0-9._ -]", "").trim();
        intent.putExtra(Intent.EXTRA_TITLE,
                (safe.isEmpty() ? "theme" : safe) + LocalThemes.EXPORT_EXTENSION);
        try {
            launcher.launch(intent, result -> {
                if (result == null || result.getData() == null
                        || result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) return;
                AppExecutors.io().execute(() -> {
                    boolean ok = false;
                    try (OutputStream out = activity.getContentResolver()
                            .openOutputStream(uri)) {
                        ok = out != null && LocalThemes.exportTheme(activity, theme, out);
                    } catch (Exception ignored) {
                    }
                    final boolean success = ok;
                    AppExecutors.runOnUiIfAlive(activity, () -> toast(activity,
                            success ? R.string.theme_editor_exported
                                    : R.string.theme_editor_error));
                });
            });
        } catch (ActivityNotFoundException e) {
            toast(activity, R.string.theme_editor_error);
        }
    }

    private static void pickImportFile(AppCompatActivity activity,
                                       SettingsController.ExternalSettingLauncher launcher,
                                       Runnable onApplied) {
        if (launcher == null) {
            toast(activity, R.string.theme_editor_error);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/json", "application/octet-stream", "text/plain"});
        try {
            launcher.launch(intent, result -> {
                if (result == null || result.getData() == null
                        || result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }
                Uri uri = result.getData().getData();
                if (uri == null) return;
                AppExecutors.io().execute(() -> {
                    LocalThemes.ImportResult imported;
                    try (InputStream in = activity.getContentResolver()
                            .openInputStream(uri)) {
                        imported = LocalThemes.importTheme(activity, in);
                    } catch (Exception e) {
                        imported = new LocalThemes.ImportResult(null, false, "io");
                    }
                    final LocalThemes.ImportResult finalResult = imported;
                    AppExecutors.runOnUiIfAlive(activity, () -> {
                        if (finalResult.theme != null) {
                            toast(activity, R.string.theme_editor_imported);
                            showManager(activity, launcher, onApplied);
                        } else {
                            toast(activity, finalResult.rejected
                                    ? R.string.theme_editor_import_rejected
                                    : R.string.theme_editor_error);
                        }
                    });
                });
            });
        } catch (ActivityNotFoundException e) {
            toast(activity, R.string.theme_editor_error);
        }
    }

    // ---------- manager adapter ----------

    private static final class ManagerAdapter extends BaseAdapter {
        private final AppCompatActivity activity;
        private final List<LocalThemes.LocalTheme> themes;

        ManagerAdapter(AppCompatActivity activity, List<LocalThemes.LocalTheme> themes) {
            this.activity = activity;
            this.themes = themes;
        }

        @Override public int getCount() {
            return themes.size();
        }

        @Override public Object getItem(int position) {
            return themes.get(position);
        }

        @Override public long getItemId(int position) {
            return position;
        }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            LocalThemes.LocalTheme theme = themes.get(position);
            LinearLayout row;
            TextView title;
            TextView subtitle;
            View swatch;
            if (convertView instanceof LinearLayout
                    && convertView.getTag() instanceof RowHolder) {
                row = (LinearLayout) convertView;
                RowHolder holder = (RowHolder) convertView.getTag();
                swatch = holder.swatch;
                title = holder.title;
                subtitle = holder.subtitle;
            } else {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = dp(activity, 12);
                row.setPadding(pad, pad, pad, pad);
                swatch = new View(activity);
                row.addView(swatch, new LinearLayout.LayoutParams(dp(activity, 40),
                        dp(activity, 40)));
                LinearLayout text = new LinearLayout(activity);
                text.setOrientation(LinearLayout.VERTICAL);
                int textPad = dp(activity, 12);
                text.setPadding(textPad, 0, 0, 0);
                title = new TextView(activity);
                title.setTextSize(16);
                text.addView(title);
                subtitle = new TextView(activity);
                subtitle.setTextSize(12);
                text.addView(subtitle);
                row.addView(text, new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                RowHolder holder = new RowHolder();
                holder.swatch = swatch;
                holder.title = title;
                holder.subtitle = subtitle;
                row.setTag(holder);
            }
            Integer primary = theme.colors.get("colorPrimary");
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(primary == null ? 0xFF6750A4 : primary);
            swatch.setBackground(bg);
            boolean active = LocalThemes.isActive(activity, theme.id);
            title.setText(theme.name == null ? theme.id : theme.name);
            subtitle.setText(activity.getString(active
                            ? R.string.theme_editor_subtitle_applied
                            : theme.dark ? R.string.theme_editor_subtitle_dark
                            : R.string.theme_editor_subtitle_light));
            row.setContentDescription(activity.getString(
                    R.string.theme_editor_row_description, title.getText(),
                    subtitle.getText()));
            return row;
        }

        private static final class RowHolder {
            View swatch;
            TextView title;
            TextView subtitle;
        }
    }

    // ---------- live preview ----------

    /**
     * Compact, understandable file-browser mockup painted from the draft:
     * status bar, app bar, three list rows using distinct per-type icon keys
     * (folder / apk / image), a FAB and a navigation bar. Redraws on every
     * draft edit.
     */
    private static final class ThemePreviewView extends View {
        private final LocalThemes.Draft draft;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap wallpaper;
        private Bitmap folderIcon;
        private Bitmap apkIcon;
        private Bitmap imageIcon;
        private String wallpaperKey;
        private String folderKey;
        private String apkKey;
        private String imageKey;

        ThemePreviewView(Context context, LocalThemes.Draft draft) {
            super(context);
            this.draft = draft;
            textPaint.setTextSize(12 * getResources().getDisplayMetrics().density);
        }

        private int color(String token, int fallback) {
            Integer value = draft.colors.get(token);
            return value == null ? fallback : value;
        }

        @Override protected void onDraw(Canvas canvas) {
            int surface = color("colorSurface", 0xFFF3EDF7);
            int onSurface = color("colorOnSurface", 0xFF1D1B20);
            int onSurfaceVariant = color("colorOnSurfaceVariant", 0xFF49454F);
            int primary = color("colorPrimary", 0xFF6750A4);
            int onPrimary = color("colorOnPrimary", 0xFFFFFFFF);
            int container = color("colorPrimaryContainer", 0xFFEADDFF);
            int onContainer = color("colorOnPrimaryContainer", 0xFF21005D);
            int low = color("colorSurfaceContainerLow", 0xFFF7F2FA);
            int highlight = color("list_highlight", 0x1428627E);
            int nav = color("nav_bar", surface);
            int status = color("status_bar", surface);
            int folderTint = color("folder_icon_bg", 0x33FFB74D);
            int fileTint = color("file_icon_bg", 0x336750A4);
            int scrim = color("wallpaper_scrim", 0);

            float w = getWidth();
            float h = getHeight();
            float density = getResources().getDisplayMetrics().density;
            float statusBar = 6 * density;
            float appBar = 26 * density;
            float navBar = 10 * density;

            refreshBitmaps();
            paint.setColor(surface);
            canvas.drawRect(0, 0, w, h, paint);
            if (wallpaper != null && !ThemePackContracts.WALLPAPER_SCOPE_NONE
                    .equals(draft.wallpaperScope)) {
                paint.setFilterBitmap(true);
                drawCover(canvas, wallpaper, 0, 0, w, h);
                if (scrim != 0) {
                    scrimPaint.setColor(scrim);
                    canvas.drawRect(0, 0, w, h, scrimPaint);
                }
            }

            // Status bar + app bar.
            paint.setColor(status);
            canvas.drawRect(0, 0, w, statusBar, paint);
            paint.setColor(primary);
            canvas.drawRect(0, statusBar, w, statusBar + appBar, paint);
            textPaint.setColor(onPrimary);
            textPaint.setFakeBoldText(true);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("MP-Manager", 12 * density,
                    statusBar + appBar * 0.66f, textPaint);

            // List rows: one row per distinct per-type icon key so custom
            // folder/apk/image icons are all visible in the draft preview.
            float rowTop = statusBar + appBar + 8 * density;
            float rowHeight = 34 * density;
            float margin = 8 * density;
            String[] names = {"Downloads", "Package.apk", "Photo.jpg"};
            String[] icons = {"folder", "apk", "image"};
            String[] fallbackGlyphs = {"\uD83D\uDCC1", "\uD83D\uDCE6", "\uD83D\uDDBC"};
            Bitmap[] customIcons = {folderIcon, apkIcon, imageIcon};
            for (int i = 0; i < names.length; i++) {
                rect.set(margin, rowTop, w - margin, rowTop + rowHeight);
                paint.setColor(i == 0 ? blend(highlight, low) : low);
                canvas.drawRoundRect(rect, 8 * density, 8 * density, paint);
                float iconSize = 20 * density;
                float iconLeft = margin + 8 * density;
                float iconTop = rowTop + (rowHeight - iconSize) / 2f;
                drawIconTile(canvas, iconLeft, iconTop, iconSize,
                        i == 0 ? folderTint : fileTint, customIcons[i],
                        onSurface, fallbackGlyphs[i]);
                textPaint.setFakeBoldText(false);
                textPaint.setColor(onSurface);
                canvas.drawText(names[i], iconLeft + iconSize + 10 * density,
                        rowTop + rowHeight * 0.62f, textPaint);
                textPaint.setColor(onSurfaceVariant);
                canvas.drawText(i == 0 ? "12 items" : "8 items",
                        w - margin - 56 * density,
                        rowTop + rowHeight * 0.62f, textPaint);
                rowTop += rowHeight + 6 * density;
            }

            // FAB.
            float fab = 24 * density;
            paint.setColor(container);
            canvas.drawCircle(w - margin - fab, h - navBar - fab - 8 * density,
                    fab / 2f, paint);
            textPaint.setColor(onContainer);
            textPaint.setFakeBoldText(true);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("+", w - margin - fab,
                    h - navBar - fab - 8 * density + 4 * density, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);

            // Navigation bar.
            paint.setColor(nav);
            canvas.drawRect(0, h - navBar, w, h, paint);
        }

        private int blend(int overlay, int base) {
            int alpha = Color.alpha(overlay);
            if (alpha == 0) return base;
            float ratio = alpha / 255f;
            float r = Color.red(overlay) * ratio + Color.red(base) * (1 - ratio);
            float g = Color.green(overlay) * ratio + Color.green(base) * (1 - ratio);
            float b = Color.blue(overlay) * ratio + Color.blue(base) * (1 - ratio);
            return Color.rgb((int) r, (int) g, (int) b);
        }

        private void drawIconTile(Canvas canvas, float left, float top, float size,
                                  int tint, Bitmap custom, int glyphColor, String glyph) {
            paint.setColor(tint);
            rect.set(left, top, left + size, top + size);
            float radius = 5 * getResources().getDisplayMetrics().density;
            canvas.drawRoundRect(rect, radius, radius, paint);
            if (custom != null) {
                paint.setFilterBitmap(true);
                drawCover(canvas, custom, left + 1, top + 1, size - 2, size - 2);
                return;
            }
            textPaint.setColor(glyphColor);
            textPaint.setFakeBoldText(false);
            textPaint.setTextAlign(Paint.Align.CENTER);
            float density = getResources().getDisplayMetrics().density;
            canvas.drawText(glyph, left + size / 2f, top + size * 0.72f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }

        /** Draws src scaled to cover dst (center crop). */
        private void drawCover(Canvas canvas, Bitmap src, float left, float top,
                               float width, float height) {
            if (src.getWidth() <= 0 || src.getHeight() <= 0) return;
            float scale = Math.max(width / src.getWidth(), height / src.getHeight());
            float dw = src.getWidth() * scale;
            float dh = src.getHeight() * scale;
            float dx = left + (width - dw) / 2f;
            float dy = top + (height - dh) / 2f;
            canvas.save();
            canvas.clipRect(left, top, left + width, top + height);
            canvas.drawBitmap(src, null, new RectF(dx, dy, dx + dw, dy + dh), paint);
            canvas.restore();
        }

        /** (Re)decodes wallpaper/icons when the draft's image changed. */
        private void refreshBitmaps() {
            BitmapAndKey next = decodeFor(
                    draft.stagedWallpaperFile() != null ? draft.stagedWallpaperFile()
                            : LocalThemes.resolveStoredImage(getContext(),
                            draft.retainedWallpaperName()),
                    wallpaper, wallpaperKey);
            wallpaper = next.bitmap;
            wallpaperKey = next.key;

            next = decodeIcon("folder", folderIcon, folderKey);
            folderIcon = next.bitmap;
            folderKey = next.key;

            next = decodeIcon("apk", apkIcon, apkKey);
            apkIcon = next.bitmap;
            apkKey = next.key;

            next = decodeIcon("image", imageIcon, imageKey);
            imageIcon = next.bitmap;
            imageKey = next.key;
        }

        private BitmapAndKey decodeIcon(String key, Bitmap current, String currentKey) {
            String variant = key + (draft.dark
                    ? ThemePackContracts.ICON_VARIANT_DARK
                    : ThemePackContracts.ICON_VARIANT_LIGHT);
            if (draft.hasIcon(variant)) key = variant;
            File staged = draft.stagedIconFile(key);
            if (staged != null) return decodeFor(staged, current, currentKey);
            return decodeFor(LocalThemes.resolveStoredImage(getContext(),
                    draft.retainedIconName(key)), current, currentKey);
        }

        private static BitmapAndKey decodeFor(File file, Bitmap current, String key) {
            String path = file == null ? null : file.getAbsolutePath();
            if (path == null) return new BitmapAndKey(null, null);
            if (path.equals(key)) return new BitmapAndKey(current, key);
            Bitmap decoded = decodeBounded(file, 512);
            return new BitmapAndKey(decoded, decoded == null ? null : path);
        }

        private static final class BitmapAndKey {
            final Bitmap bitmap;
            final String key;

            BitmapAndKey(Bitmap bitmap, String key) {
                this.bitmap = bitmap;
                this.key = key;
            }
        }

        private static Bitmap decodeBounded(File file, int maxSide) {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize
                        > maxSide) {
                    options.inSampleSize *= 2;
                }
                return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ---------- misc ----------

    /** Chip strip order (primary colors first). */
    private static final String[] CHIP_TOKENS = {
            "colorPrimary", "colorOnPrimary", "colorPrimaryContainer",
            "colorOnPrimaryContainer", "colorSecondary", "colorSecondaryContainer",
            "colorTertiary", "colorSurface", "colorOnSurface",
            "colorSurfaceContainer", "colorOnSurfaceVariant", "colorOutline",
            "nav_bar", "status_bar", "folder_icon_bg", "file_icon_bg",
    };

    /** HorizontalScrollView constructor without theme attribute noise. */
    private static final class HorizontalScrollViewHost
            extends android.widget.HorizontalScrollView {
        HorizontalScrollViewHost(Context context) {
            super(context);
        }
    }

    private static MaterialButton outlinedButton(AppCompatActivity activity, int textRes) {
        MaterialButton button = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(textRes);
        return button;
    }

    private static LinearLayout.LayoutParams marginParams(int width, int height,
                                                          int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private static void addSectionHeader(LinearLayout root, int textRes) {
        TextView header = new TextView(root.getContext());
        header.setText(textRes);
        header.setTextSize(14);
        header.setTypeface(null, Typeface.BOLD);
        int pad = dp(root.getContext(), 12);
        header.setPadding(pad, pad, pad, dp(root.getContext(), 4));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static String colorLabel(Context context, String token) {
        switch (token) {
            case "nav_bar":
                return context.getString(R.string.theme_editor_color_nav_bar);
            case "status_bar":
                return context.getString(R.string.theme_editor_color_status_bar);
            case "list_highlight":
                return context.getString(R.string.theme_editor_color_list_highlight);
            case "folder_icon_bg":
                return context.getString(R.string.theme_editor_color_folder_bg);
            case "file_icon_bg":
                return context.getString(R.string.theme_editor_color_file_bg);
            case "wallpaper_scrim":
                return context.getString(R.string.theme_editor_color_scrim);
            default: break;
        }
        if (token.startsWith("editor_")) {
            String rest = token.substring("editor_".length());
            return context.getString(R.string.theme_editor_color_editor_slot,
                    capitalize(rest.replace('_', ' ')));
        }
        String name = token.startsWith("color")
                ? token.substring("color".length()) : token;
        return capitalize(name.replace('_', ' '));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String hex(int argb) {
        return String.format(Locale.US, "#%08X", argb);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static void toast(AppCompatActivity activity, int res) {
        if (activity == null) return;
        try {
            Toast.makeText(activity, res, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }
}

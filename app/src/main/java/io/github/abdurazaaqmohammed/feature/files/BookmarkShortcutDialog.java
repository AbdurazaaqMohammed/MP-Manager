package io.github.abdurazaaqmohammed.feature.files;

import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;

import io.github.abdurazaaqmohammed.ui.common.util.Haptics;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.FileIconLoader;
import io.github.abdurazaaqmohammed.ui.main.FileIconSpec;
import io.github.abdurazaaqmohammed.ui.main.ColorWheelView;
import io.github.abdurazaaqmohammed.ui.UiFields;
import io.github.abdurazaaqmohammed.ui.editor.FilePickerDialog;
import io.github.abdurazaaqmohammed.util.io.SafFs;
import io.github.codehasan.colorpicker.extensions.Extensions;

/** Configures, previews and publishes file/folder bookmark shortcuts without decoding on the UI thread. */
public final class BookmarkShortcutDialog {
    private final MainActivity activity;
    private final File file;
    private final String label;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService images = Executors.newSingleThreadExecutor();
    private AlertDialog dialog;
    private FileIconSpec spec;
    private int foreground, background, imageGeneration;
    private Bitmap image;
    private boolean directory, tintImage, backgroundEdited;
    private boolean iconEdited, foregroundEdited, settingDefaults;
    private ImageView preview;
    private MaterialSwitch backgroundToggle;

    public BookmarkShortcutDialog(MainActivity activity, File file, String label) {
        this.activity = activity;
        this.file = file;
        this.label = label;
        directory = file.isDirectory() || (!file.isFile() && !SafFs.isSaf(file));
        spec = FileIconSpec.forPath(file.getPath(), directory, activity.theme);
        foreground = spec.foreground();
        background = spec.background();
    }

    public void show() {
        LinearLayout content = column();
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setText(label == null || label.isEmpty() ? file.getName() : label);
        var nameBox = UiFields.wrap(activity, name, activity.getString(R.string.name), 0);
        content.addView(nameBox);

        RadioGroup modes = new RadioGroup(activity);
        RadioButton launcher = new RadioButton(activity);
        launcher.setId(View.generateViewId());
        launcher.setText(R.string.shortcut_mode_launcher);
        RadioButton icon = new RadioButton(activity);
        icon.setId(View.generateViewId());
        icon.setText(R.string.shortcut_mode_app_icon);
        icon.setEnabled(Build.VERSION.SDK_INT >= 25);
        modes.addView(launcher);
        modes.addView(icon);
        modes.check(launcher.getId());
        content.addView(modes);
        TextView explanation = new TextView(activity);
        explanation.setText(R.string.shortcut_launcher_description);
        content.addView(explanation);
        modes.setOnCheckedChangeListener((group, checked) -> {
            explanation.setText(checked == launcher.getId() ? R.string.shortcut_launcher_description : R.string.shortcut_app_icon_description);
            Haptics.perform(group, Haptics.Event.SELECTION);
        });

        preview = new ImageView(activity);
        preview.setContentDescription(activity.getString(R.string.file_icon));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(96), dp(96));
        previewParams.gravity = Gravity.CENTER_HORIZONTAL;
        previewParams.topMargin = dp(12);
        content.addView(preview, previewParams);
        button(content, R.string.shortcut_choose_icon, this::chooseIcon);
        button(content, R.string.shortcut_choose_image, this::chooseImage);
        button(content, R.string.shortcut_icon_color, () -> chooseColor(foreground, color -> {
            iconEdited = true;
            foregroundEdited = true;
            foreground = color;
            tintImage = image != null;
            updatePreview();
        }));
        backgroundToggle = new MaterialSwitch(activity);
        backgroundToggle.setText(R.string.shortcut_background_enabled);
        backgroundToggle.setChecked(true);
        content.addView(backgroundToggle);
        backgroundToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!settingDefaults) { backgroundEdited = true; iconEdited = true; }
            updatePreview();
        });
        button(content, R.string.shortcut_background_color, () -> chooseColor(background, color -> {
            iconEdited = true;
            background = color;
            backgroundEdited = true;
            backgroundToggle.setChecked(true);
            updatePreview();
        }));
        button(content, R.string.shortcut_reset_icon, this::resetIcon);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.plugin_create_shortcut)
                .setView(scroll).setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.create, null).create();
        dialog.setOnDismissListener(d -> {
            ++imageGeneration;
            images.shutdownNow();
        });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) { nameBox.setError(activity.getString(R.string.shortcut_name_required)); return; }
            boolean pin = modes.getCheckedRadioButtonId() == launcher.getId();
            if (pin && !ShortcutManagerCompat.isRequestPinShortcutSupported(activity)) {
                Extensions.showMessage(activity, R.string.plugin_shortcut_unsupported);
                return;
            }
            try {
                Bitmap bitmap = BookmarkShortcuts.render(activity, spec, foreground, background,
                        backgroundToggle.isChecked(), image, tintImage);
                boolean created = BookmarkShortcuts.create(activity,
                        BookmarkShortcuts.info(activity, file, directory, title, bitmap), pin);
                if (!created) {
                    Extensions.showMessage(activity, pin ? R.string.plugin_shortcut_unsupported : R.string.shortcut_limit_reached);
                    return;
                }
                Haptics.perform(v, Haptics.Event.ACTION);
                Extensions.showMessage(activity, pin ? R.string.plugin_shortcut_done : R.string.shortcut_app_icon_added);
                dialog.dismiss();
            } catch (RuntimeException failure) {
                Extensions.showMessage(activity, activity.getString(R.string.dns_shortcut_failed));
            }
        });
        updatePreview();
        // Bookmarks restored from preferences no longer carry root/SAF stat.
        images.execute(() -> {
            boolean resolved = directory;
            try {
                resolved = SafFs.isSaf(file) ? SafFs.statWrap(file.getPath()).isDirectory()
                        : PrivilegeManager.Files.isDirectory(activity, file.getAbsolutePath());
            } catch (Exception ignored) {}
            boolean result = resolved;
            handler.post(() -> {
                if (!dialog.isShowing()) return;
                directory = result;
                if (!iconEdited) resetIcon();
            });
        });
    }

    private void resetIcon() {
        iconEdited = false;
        foregroundEdited = false;
        ++imageGeneration;
        image = null;
        tintImage = false;
        backgroundEdited = false;
        spec = FileIconSpec.forPath(file.getPath(), directory, activity.theme);
        foreground = spec.foreground();
        background = spec.background();
        settingDefaults = true;
        backgroundToggle.setChecked(true);
        settingDefaults = false;
        backgroundEdited = false;
        updatePreview();
        if (!directory && (spec == FileIconSpec.IMAGE || spec == FileIconSpec.VIDEO)) {
            boolean video = spec == FileIconSpec.VIDEO;
            loadImage(() -> video ? FileIconLoader.loadVideoThumbnail(file.getPath())
                    : FileIconLoader.loadImageThumbnail(file.getPath()), false);
        }
    }

    private void updatePreview() {
        if (preview == null || backgroundToggle == null) return;
        preview.setImageBitmap(BookmarkShortcuts.render(activity, spec, foreground, background,
                backgroundToggle.isChecked(), image, tintImage));
    }

    private void chooseIcon() {
        List<FileIconSpec> icons = FileIconSpec.all();
        RecyclerView grid = new RecyclerView(activity);
        grid.setLayoutManager(new GridLayoutManager(activity, 3));
        AlertDialog picker = new MaterialAlertDialogBuilder(activity).setTitle(R.string.shortcut_choose_icon)
                .setView(grid).setNegativeButton(android.R.string.cancel, null).create();
        grid.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                TextView item = new TextView(activity);
                item.setGravity(Gravity.CENTER);
                item.setPadding(dp(4), dp(10), dp(4), dp(10));
                item.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(100)));
                return new RecyclerView.ViewHolder(item) {};
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                FileIconSpec candidate = icons.get(position);
                TextView item = (TextView) holder.itemView;
                item.setText(candidate.label());
                var drawable = candidate.drawable(activity);
                drawable.setBounds(0, 0, dp(40), dp(40));
                item.setCompoundDrawables(null, drawable, null, null);
                item.setOnClickListener(v -> {
                    ++imageGeneration;
                    image = null;
                    tintImage = false;
                    spec = candidate;
                    iconEdited = true;
                    updatePreview();
                    Haptics.perform(v, Haptics.Event.SELECTION);
                    picker.dismiss();
                });
            }
            @Override public int getItemCount() { return icons.size(); }
        });
        picker.show();
    }

    private void chooseImage() {
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.shortcut_choose_image)
                .setItems(new String[]{activity.getString(R.string.shortcut_image_file_picker),
                        activity.getString(R.string.shortcut_image_system_picker)}, (d, which) -> {
                    if (which == 0) {
                        FilePickerDialog.Properties properties = new FilePickerDialog.Properties();
                        properties.root = Environment.getExternalStorageDirectory();
                        properties.extensions = new String[]{"png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif"};
                        FilePickerDialog picker = new FilePickerDialog(activity, properties);
                        picker.setTitle(activity.getString(R.string.shortcut_choose_image));
                        picker.setDialogSelectionListener(paths -> {
                            if (paths != null && paths.length > 0) loadImage(() -> FileIconLoader.loadImageThumbnail(paths[0]), true);
                        });
                        picker.show();
                    } else {
                        activity.launchExternalFile(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), result -> {
                            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                            Uri uri = result.getData().getData();
                            if (uri != null && dialog.isShowing()) loadImage(() -> decodeUri(uri), true);
                        });
                    }
                }).show();
    }

    private Bitmap decodeUri(Uri uri) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(input, null, options); }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null;
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        try (InputStream input = activity.getContentResolver().openInputStream(uri)) { return BitmapFactory.decodeStream(input, null, options); }
    }

    private interface ImageSource { Bitmap load() throws Exception; }
    private void loadImage(ImageSource source, boolean reportFailure) {
        if (images.isShutdown()) return;
        int generation = ++imageGeneration;
        if (reportFailure) iconEdited = true;
        images.execute(() -> {
            Bitmap loaded = null;
            try { loaded = source.load(); } catch (Exception | OutOfMemoryError ignored) {}
            Bitmap result = loaded;
            handler.post(() -> {
                if (generation != imageGeneration || !dialog.isShowing()) return;
                if (result == null) { if (reportFailure) Extensions.showMessage(activity, R.string.shortcut_image_failed); return; }
                image = result;
                tintImage = foregroundEdited;
                if (!backgroundEdited) {
                    settingDefaults = true;
                    backgroundToggle.setChecked(false);
                    settingDefaults = false;
                }
                updatePreview();
            });
        });
    }

    private void chooseColor(int initial, IntConsumer apply) {
        LinearLayout content = column();
        ColorWheelView wheel = new ColorWheelView(activity);
        content.addView(wheel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
        EditText hex = new EditText(activity);
        hex.setSingleLine(true);
        content.addView(UiFields.wrap(activity, hex, "#RRGGBB", 0));
        boolean[] syncing = {false};
        wheel.setListener(() -> {
            if (syncing[0]) return;
            syncing[0] = true;
            hex.setText(String.format(Locale.ROOT, "#%06X", wheel.getColor(255) & 0xffffff));
            syncing[0] = false;
        });
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncing[0] || !s.toString().matches("#[0-9a-fA-F]{6}")) return;
                syncing[0] = true;
                wheel.setColor(Color.parseColor(s.toString()));
                syncing[0] = false;
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        wheel.setColor(initial);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.color_picker).setView(scroll)
                .setPositiveButton(R.string.apply, (d, w) -> apply.accept(wheel.getColor(255)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(8), dp(16), dp(8));
        return column;
    }
    private void button(LinearLayout content, int text, Runnable action) {
        MaterialButton button = new MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setOnClickListener(v -> {
            Haptics.perform(v, Haptics.Event.ACTION);
            action.run();
        });
        content.addView(button, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private int dp(int size) { return Math.round(size * activity.getResources().getDisplayMetrics().density); }
}

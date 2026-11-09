package io.github.abdurazaaqmohammed.features.dumper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.features.system.SystemImageEngine;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

/** Themed partition browser; destructive operations are always separately confirmed. */
public final class PartitionDumperDialog {
    private PartitionDumperDialog() {}

    public static void clearCache() { /* Discovery is refreshed on each open, including locale changes. */ }

    public static void show(AppCompatActivity activity) {
        if (!alive(activity)) return;
        var prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!prefs.getBoolean("pref_warned_dumper_danger", false)) {
            View view = LayoutInflater.from(activity).inflate(R.layout.dialog_safety_warning, null, false);
            ((TextView) view.findViewById(R.id.tvWarningMessage)).setText(R.string.dumper_open_warning);
            CheckBox checkbox = view.findViewById(R.id.cbDontShowAgain);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.safety_warning_title).setView(view)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.warning_proceed, (d, w) -> {
                        if (checkbox.isChecked()) prefs.edit().putBoolean("pref_warned_dumper_danger", true).apply();
                        load(activity);
                    }).show();
        } else load(activity);
    }

    private static boolean alive(AppCompatActivity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static void load(AppCompatActivity activity) {
        ProgressManager progress = new ProgressManager(activity, false).show();
        progress.setText(activity.getString(R.string.tools_hub_loading));
        new Thread(() -> {
            try {
                List<PartitionItem> partitions = PartitionResolver.queryPartitions(activity);
                String slot = PartitionResolver.getActiveSlot();
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (!alive(activity)) return;
                    if (partitions.isEmpty()) Extensions.showMessage(activity, R.string.partition_root_required);
                    else browse(activity, partitions, slot);
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity,
                            activity.getString(R.string.partition_discovery_failed, error.getMessage()));
                });
            }
        }, "partition-discovery").start();
    }

    private static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 20);
        view.setPadding(pad, 0, pad, pad);
        return view;
    }

    private static TextView text(Context context, CharSequence value, boolean secondary) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(secondary ? 13 : 16);
        view.setTextColor(MaterialColors.getColor(context, secondary
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface, 0));
        int pad = Extensions.dp2px(context, 6);
        view.setPadding(0, pad, 0, pad);
        return view;
    }

    private static String slotLabel(Context context, PartitionItem item) {
        String slot = context.getString(item.slot == PartitionItem.Slot.A ? R.string.partition_slot_a
                : item.slot == PartitionItem.Slot.B ? R.string.partition_slot_b : R.string.partition_shared);
        return item.isActiveSlot ? context.getString(R.string.partition_active_slot_format, slot) : slot;
    }

    private static void browse(AppCompatActivity activity, List<PartitionItem> partitions, String activeSlot) {
        LinearLayout content = column(activity);
        content.addView(text(activity, activity.getString(R.string.partition_device_summary,
                android.os.Build.MODEL, android.os.Build.DEVICE,
                TextUtils.isEmpty(activeSlot) ? activity.getString(R.string.partition_unspecified)
                        : activeSlot.toUpperCase(Locale.ROOT)), true));
        EditText search = new EditText(activity);
        search.setHint(R.string.partition_search_hint);
        search.setSingleLine(true);
        content.addView(search);
        TextView count = text(activity, activity.getString(R.string.partition_count_summary, partitions.size()), true);
        content.addView(count);
        RecyclerView list = new RecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setItemAnimator(null);
        int height = Math.max(Extensions.dp2px(activity, 96),
                (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.6f) - Extensions.dp2px(activity, 160));
        content.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        Map<String, List<PartitionItem>> groups = new LinkedHashMap<>();
        for (PartitionItem item : partitions) {
            String base = item.rawName.replaceFirst("_[ab]$", "");
            groups.computeIfAbsent(base, ignored -> new ArrayList<>()).add(item);
        }
        List<String> names = new ArrayList<>(groups.keySet());
        List<String> visible = new ArrayList<>(names);
        RecyclerView.Adapter<PartitionHolder> adapter = new RecyclerView.Adapter<>() {
            @NonNull @Override public PartitionHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                View row = LayoutInflater.from(activity).inflate(android.R.layout.simple_list_item_2, parent, false);
                android.util.TypedValue background = new android.util.TypedValue();
                activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
                row.setBackgroundResource(background.resourceId);
                row.setMinimumHeight(Extensions.dp2px(activity, 64));
                return new PartitionHolder(row);
            }
            @Override public void onBindViewHolder(@NonNull PartitionHolder holder, int position) {
                String name = visible.get(position);
                List<PartitionItem> items = groups.get(name);
                holder.title.setText(name);
                holder.title.setTypeface(Typeface.MONOSPACE);
                holder.title.setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurface, 0));
                StringBuilder summary = new StringBuilder();
                for (PartitionItem item : items) {
                    if (summary.length() > 0) summary.append(" · ");
                    summary.append(activity.getString(R.string.partition_slot_size, slotLabel(activity, item),
                            FileSize.getHumanReadableFileSize(item.sizeBytes)));
                }
                holder.summary.setText(summary);
                holder.summary.setTextColor(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurfaceVariant, 0));
                holder.itemView.setOnClickListener(v -> actions(activity, name, items));
            }
            @Override public int getItemCount() { return visible.size(); }
        };
        list.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int length) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                visible.clear();
                int matchedPartitions = 0;
                for (String name : names) if (name.toLowerCase(Locale.ROOT).contains(query)) {
                    visible.add(name);
                    matchedPartitions += groups.get(name).size();
                }
                adapter.notifyDataSetChanged();
                count.setText(visible.isEmpty() ? activity.getString(R.string.partition_no_matches)
                        : activity.getString(R.string.partition_count_summary, matchedPartitions));
            }
        });
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.partition_dumper_title).setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.partition_refresh, (d, w) -> load(activity)).show();
    }

    private static final class PartitionHolder extends RecyclerView.ViewHolder {
        final TextView title, summary;
        PartitionHolder(View view) {
            super(view);
            title = view.findViewById(android.R.id.text1);
            summary = view.findViewById(android.R.id.text2);
        }
    }

    private static void actions(AppCompatActivity activity, String name, List<PartitionItem> items) {
        String[] labels = {activity.getString(R.string.partition_opt_copy), activity.getString(R.string.partition_opt_flash),
                activity.getString(R.string.partition_info_title)};
        new MaterialAlertDialogBuilder(activity).setTitle(activity.getString(R.string.partition_action_dialog_title, name))
                .setItems(labels, (d, action) -> chooseSlots(activity, items, selected -> {
                    if (action == 0) chooseDumpFolder(activity, selected);
                    else if (action == 1) pickImage(activity, selected);
                    else for (PartitionItem item : selected) details(activity, item);
                }, action != 2)).setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void chooseSlots(AppCompatActivity activity, List<PartitionItem> items,
                                    Consumer<List<PartitionItem>> result, boolean both) {
        if (items.size() == 1) { result.accept(items); return; }
        List<String> labels = new ArrayList<>();
        for (PartitionItem item : items) labels.add(slotLabel(activity, item));
        if (both) labels.add(activity.getString(R.string.partition_both_slots));
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.partition_select_slot)
                .setItems(labels.toArray(new String[0]), (d, which) ->
                        result.accept(which == items.size() ? items : List.of(items.get(which))))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private static void details(AppCompatActivity activity, PartitionItem item) {
        String value = activity.getString(R.string.partition_details_format, item.friendlyName, item.rawName,
                item.blockPath, item.targetDev, FileSize.getHumanReadableFileSize(item.sizeBytes),
                slotLabel(activity, item), activity.getString(item.isDynamic ? R.string.partition_logical : R.string.partition_physical),
                item.description);
        LinearLayout content = column(activity);
        TextView info = text(activity, value, false);
        info.setTextIsSelectable(true);
        content.addView(info);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(activity).setTitle(R.string.partition_info_title).setView(scroll)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.partition_copy_details, (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.partition_info_title), value));
                    Extensions.showMessage(activity, R.string.partition_details_copied);
                }).show();
    }

    private static File defaultDirectory(AppCompatActivity activity) {
        if (activity instanceof MainActivity main) {
            File folder = new File(main.shownPath());
            if (folder.isDirectory() && folder.canWrite()) return folder;
        }
        return new File(Environment.getExternalStorageDirectory(), "Download");
    }

    private static FilePickerDialog picker(AppCompatActivity activity, boolean folder) {
        FilePickerDialog.Properties properties = new FilePickerDialog.Properties();
        properties.selection_mode = FilePickerDialog.SINGLE_MODE;
        properties.selection_type = folder ? FilePickerDialog.DIR_SELECT : FilePickerDialog.FILE_SELECT;
        properties.root = Environment.getExternalStorageDirectory();
        properties.offset = defaultDirectory(activity);
        if (!folder) properties.extensions = new String[]{"img", "bin"};
        return new FilePickerDialog(activity, properties);
    }

    public static void showDumpConfirmDialog(AppCompatActivity activity, PartitionItem item) {
        if (alive(activity) && item != null) chooseDumpFolder(activity, List.of(item));
    }

    private static void chooseDumpFolder(AppCompatActivity activity, List<PartitionItem> items) {
        FilePickerDialog picker = picker(activity, true);
        picker.setTitle(activity.getString(R.string.partition_dump_dest_label));
        picker.setDialogSelectionListener(paths -> {
            if (paths == null || paths.length == 0) return;
            File directory = new File(paths[0]);
            EditText filename = new EditText(activity);
            filename.setSingleLine(true);
            filename.setText(items.get(0).outputFileName);
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity).setTitle(R.string.partition_opt_copy)
                    .setMessage(activity.getString(R.string.partition_backup_confirm, items.size(), directory.getAbsolutePath()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.partition_copy_btn, null);
            if (items.size() == 1) builder.setView(io.github.abdurazaaqmohammed.ui.UiFields.wrap(activity,
                    filename, activity.getString(R.string.partition_dump_filename_label), 20));
            AlertDialog confirmation = builder.show();
            confirmation.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = filename.getText().toString().trim();
                if (items.size() == 1 && (name.isEmpty() || name.equals(".") || name.equals("..")
                        || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0)) {
                    filename.setError(activity.getString(R.string.partition_invalid_filename));
                    return;
                }
                confirmation.dismiss();
                dump(activity, items, directory, items.size() == 1 ? name : null);
            });
        });
        picker.show();
    }

    private static void dump(AppCompatActivity activity, List<PartitionItem> items, File directory, String filename) {
        ProgressManager progress = new ProgressManager(activity, true).show();
        progress.setText(activity.getString(R.string.partition_batch_start));
        new Thread(() -> {
            int completed = 0;
            String failure = "";
            for (PartitionItem item : items) {
                if (progress.isCancelling()) break;
                File output = null;
                boolean reserved = false;
                Process process = null;
                try {
                    if (!directory.isDirectory()) throw new IOException(activity.getString(R.string.partition_invalid_directory));
                    if (item.sizeBytes > 0 && new StatFs(directory.getAbsolutePath()).getAvailableBytes() < item.sizeBytes)
                        throw new IOException(activity.getString(R.string.partition_insufficient_space));
                    output = FileUtils.getUnusedFile(new File(directory, filename != null ? filename : item.outputFileName));
                    while (!output.createNewFile()) output = FileUtils.getUnusedFile(output);
                    reserved = true;
                    process = root("cat " + RootManager.escapeShellArg(item.blockPath));
                    StringBuilder errors = new StringBuilder();
                    Thread drain = drainErrors(process, errors);
                    long copied = 0;
                    long lastUpdate = 0;
                    try (InputStream input = new BufferedInputStream(process.getInputStream(), 1024 * 1024);
                         OutputStream out = new BufferedOutputStream(new FileOutputStream(output), 1024 * 1024)) {
                        byte[] buffer = new byte[1024 * 1024];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (progress.isCancelling()) throw new IOException(activity.getString(R.string.partition_operation_cancelled));
                            out.write(buffer, 0, count);
                            copied += count;
                            if (System.currentTimeMillis() - lastUpdate > 250) {
                                lastUpdate = System.currentTimeMillis();
                                long bytes = copied;
                                activity.runOnUiThread(() -> progress.setText(activity.getString(R.string.partition_dumping_progress,
                                        item.rawName, FileSize.getHumanReadableFileSize(bytes),
                                        FileSize.getHumanReadableFileSize(item.sizeBytes), "")));
                            }
                        }
                    }
                    int code = process.waitFor();
                    drain.join();
                    if (code != 0 || copied == 0 || (item.sizeBytes > 0 && copied != item.sizeBytes))
                        throw new IOException(activity.getString(R.string.partition_incomplete_dump, item.rawName, errors.toString()));
                    completed++;
                } catch (Exception error) {
                    if (process != null) process.destroy();
                    if (reserved) output.delete(); // only the newly created, incomplete backup
                    failure = error.getMessage();
                    break;
                }
            }
            int count = completed;
            String error = failure;
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (!alive(activity)) return;
                if (activity instanceof MainActivity main) main.refreshPane(true);
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.partition_backup_result_title)
                        .setMessage(activity.getString(R.string.partition_backup_result, count, items.size(), directory.getAbsolutePath())
                                + (TextUtils.isEmpty(error) ? "" : "\n\n" + error))
                        .setPositiveButton(android.R.string.ok, null).show();
            });
        }, "partition-backup").start();
    }

    public static void showFlashDialog(AppCompatActivity activity, PartitionItem item) {
        if (alive(activity) && item != null) pickImage(activity, List.of(item));
    }

    private static void pickImage(AppCompatActivity activity, List<PartitionItem> targets) {
        FilePickerDialog picker = picker(activity, false);
        picker.setTitle(activity.getString(R.string.partition_flash_src_label));
        picker.setDialogSelectionListener(paths -> {
            if (paths != null && paths.length > 0) inspectFlash(activity, targets, new File(paths[0]));
        });
        picker.show();
    }

    private static void inspectFlash(AppCompatActivity activity, List<PartitionItem> targets, File image) {
        ProgressManager progress = new ProgressManager(activity, false).show();
        progress.setText(activity.getString(R.string.partition_inspecting));
        new Thread(() -> {
            try {
                if (!image.isFile() || image.length() == 0) throw new IOException(activity.getString(R.string.partition_flash_empty_file));
                var identity = SystemImageEngine.identifyImage(image);
                boolean sparse = "sparse".equals(identity.typeKey);
                var sparseInfo = sparse ? SystemImageEngine.readSparseInfo(image) : null;
                if (sparse && sparseInfo == null) throw new IOException(activity.getString(R.string.partition_invalid_sparse));
                if (sparse) SystemImageEngine.validateSparseImage(image);
                long effectiveSize = sparse ? sparseInfo.uncompressedSizeBytes : image.length();
                StringBuilder targetNames = new StringBuilder();
                boolean risk = false;
                for (PartitionItem item : targets) {
                    if (item.sizeBytes > 0 && effectiveSize > item.sizeBytes)
                        throw new IOException(activity.getString(R.string.partition_flash_size_exceeded,
                                FileSize.getHumanReadableFileSize(effectiveSize), FileSize.getHumanReadableFileSize(item.sizeBytes)));
                    risk |= (item.sizeBytes > 0 && effectiveSize < item.sizeBytes)
                            || SystemImageEngine.isTypeMismatch(item, identity, SystemImageEngine.identifyImage(new File(item.blockPath)));
                    if (targetNames.length() > 0) targetNames.append('\n');
                    targetNames.append(item.rawName).append(" ( ").append(item.blockPath).append(" )");
                }
                boolean acknowledge = risk;
                long length = image.length(), modified = image.lastModified();
                String message = activity.getString(R.string.partition_flash_review, image.getAbsolutePath(),
                        FileSize.getHumanReadableFileSize(effectiveSize), imageType(activity, identity.typeKey), targetNames.toString());
                if (sparse) message += "\n\n" + activity.getString(R.string.partition_sparse_explanation);
                if (risk) message += "\n\n" + activity.getString(R.string.partition_compatibility_warning);
                String review = message;
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (!alive(activity)) return;
                    LinearLayout content = column(activity);
                    content.addView(text(activity, review, false));
                    CheckBox checkbox = new CheckBox(activity);
                    checkbox.setText(R.string.partition_flash_ack_risk);
                    if (acknowledge) content.addView(checkbox);
                    ScrollView scroll = new ScrollView(activity);
                    scroll.addView(content);
                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.partition_flash_confirm_title).setView(scroll)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.partition_flash_btn, (d, w) -> flash(activity, targets, image, sparse, length, modified));
                    if (sparse) builder.setNeutralButton(R.string.partition_flash_unsparse_to_file, (d, w) -> unsparse(activity, image));
                    AlertDialog dialog = builder.show();
                    if (acknowledge) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                        checkbox.setOnCheckedChangeListener((button, checked) -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));
                    }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity, activity.getString(R.string.partition_flash_failed, error.getMessage()));
                });
            }
        }, "partition-inspect").start();
    }

    private static String imageType(Context context, String key) {
        int resource = switch (key) {
            case "boot", "boot_gki" -> R.string.partition_image_boot;
            case "init_boot" -> R.string.partition_image_init_boot;
            case "recovery" -> R.string.partition_image_recovery;
            case "vendor_boot" -> R.string.partition_image_vendor_boot;
            case "vbmeta" -> R.string.partition_image_vbmeta;
            case "dtbo" -> R.string.partition_image_dtbo;
            case "sparse" -> R.string.partition_image_sparse;
            case "ext4" -> R.string.partition_image_ext4;
            case "erofs" -> R.string.partition_image_erofs;
            default -> R.string.partition_image_unknown;
        };
        return context.getString(resource);
    }

    private static void flash(AppCompatActivity activity, List<PartitionItem> targets, File image,
                              boolean sparse, long length, long modified) {
        ProgressManager progress = new ProgressManager(activity, false).show();
        progress.setText(activity.getString(R.string.partition_flash_start));
        new Thread(() -> {
            String error = null;
            int completed = 0;
            try {
                if (image.length() != length || image.lastModified() != modified)
                    throw new IOException(activity.getString(R.string.partition_image_changed));
                for (PartitionItem target : targets) {
                    Process process = root("dd of=" + RootManager.escapeShellArg(target.blockPath) + " bs=1048576 conv=fsync");
                    try {
                        StringBuilder errors = new StringBuilder();
                        Thread drain = drainErrors(process, errors);
                        try (OutputStream out = new BufferedOutputStream(process.getOutputStream(), 1024 * 1024)) {
                            if (sparse) SystemImageEngine.unsparseToStream(image, out, new SystemImageEngine.ProgressCallback() {
                                @Override public void onProgress(String message, int percent) {
                                    activity.runOnUiThread(() -> progress.setProgress(percent, 100));
                                }
                                @Override public void onError(String message) {}
                                @Override public void onSuccess(File result) {}
                            });
                            else {
                                try (InputStream input = new FileInputStream(image)) {
                                    byte[] buffer = new byte[1024 * 1024];
                                    int count;
                                    while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
                                }
                            }
                        }
                        int code = process.waitFor();
                        drain.join();
                        if (code != 0) throw new IOException(activity.getString(R.string.partition_write_failed, target.rawName, errors.toString()));
                        completed++;
                    } finally { process.destroy(); }
                }
            } catch (Exception failure) { error = failure.getMessage(); }
            String failure = error;
            int count = completed;
            activity.runOnUiThread(() -> {
                progress.dismiss();
                if (!alive(activity)) return;
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(failure == null ? R.string.title_success : R.string.error)
                        .setMessage(activity.getString(R.string.partition_flash_result, count, targets.size())
                                + (failure == null ? "\n\n" + activity.getString(R.string.partition_reboot_message) : "\n\n" + failure))
                        .setPositiveButton(android.R.string.ok, null).show();
            });
        }, "partition-flash").start();
    }

    private static Process root(String command) throws IOException {
        return Runtime.getRuntime().exec(new String[]{"su", "-c", command});
    }

    /** Drain stderr concurrently: waiting on dd with a full stderr pipe otherwise hangs. */
    private static Thread drainErrors(Process process, StringBuilder errors) {
        Thread thread = new Thread(() -> {
            try (InputStream stream = process.getErrorStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (errors.length() < 8192) errors.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {}
        }, "partition-stderr");
        thread.start();
        return thread;
    }

    private static void unsparse(AppCompatActivity activity, File image) {
        ProgressManager progress = new ProgressManager(activity, false).show();
        progress.setText(activity.getString(R.string.partition_inspecting));
        new Thread(() -> {
            File output = null;
            try {
                output = FileUtils.getUnusedFile(new File(image.getParentFile(), image.getName() + "_raw.img"));
                SystemImageEngine.unsparseToFile(image, output, null);
                File result = output;
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity,
                            activity.getString(R.string.partition_unsparse_success, result.getAbsolutePath(), FileSize.getHumanReadableFileSize(result.length())));
                });
            } catch (Exception error) {
                if (output != null) output.delete();
                activity.runOnUiThread(() -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity, activity.getString(R.string.partition_unsparse_failed, error.getMessage()));
                });
            }
        }, "partition-unsparse").start();
    }
}

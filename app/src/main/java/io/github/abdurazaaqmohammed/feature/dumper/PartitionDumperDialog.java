package io.github.abdurazaaqmohammed.feature.dumper;

import io.github.abdurazaaqmohammed.ui.UiFields;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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

import io.github.abdurazaaqmohammed.ui.main.FileIconSpec;
import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.feature.system.SystemImageEngine;
import io.github.abdurazaaqmohammed.ui.editor.FilePickerDialog;
import io.github.abdurazaaqmohammed.util.text.FileSize;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.text.ShellQuote;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.abdurazaaqmohammed.util.misc.AppExecutors;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

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
        AppExecutors.io().execute(() -> {
            try {
                List<PartitionItem> partitions = PartitionResolver.queryPartitions(activity);
                String slot = PartitionResolver.getActiveSlot();
                AppExecutors.runOnUiIfAlive(activity, () -> {
                    progress.dismiss();
                    if (!alive(activity)) return;
                    if (partitions.isEmpty()) Extensions.showMessage(activity, R.string.partition_root_required);
                    else browse(activity, partitions, slot);
                });
            } catch (Exception error) {
                AppExecutors.runOnUiIfAlive(activity, () -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity,
                            activity.getString(R.string.partition_discovery_failed, error.getMessage()));
                });
            }
        });
    }

    private static LinearLayout column(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(context, 16);
        view.setPadding(pad, Extensions.dp2px(context, 6), pad, pad);
        return view;
    }

    private static TextView text(Context context, CharSequence value, boolean secondary) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(secondary ? 13 : 15);
        view.setTextColor(MaterialColors.getColor(context, secondary
                ? com.google.android.material.R.attr.colorOnSurfaceVariant
                : com.google.android.material.R.attr.colorOnSurface, 0));
        int pad = Extensions.dp2px(context, 6);
        view.setPadding(0, pad, 0, pad);
        return view;
    }

    private static MaterialCardView makeCard(Context context, int bgColor, int strokeColor, int radiusDp) {
        MaterialCardView card = new MaterialCardView(context);
        card.setRadius(Extensions.dp2px(context, radiusDp));
        card.setCardElevation(0f);
        card.setCardBackgroundColor(bgColor);
        card.setStrokeWidth(Math.max(1, Extensions.dp2px(context, 1)));
        card.setStrokeColor(strokeColor);
        return card;
    }

    private static TextView makePillBadge(Context context, CharSequence label,
                                          int bgColor, int textColor, boolean bold) {
        TextView pill = new TextView(context);
        pill.setText(label);
        pill.setTextSize(11.5f);
        pill.setTextColor(textColor);
        pill.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        pill.setMaxLines(1);
        pill.setEllipsize(TextUtils.TruncateAt.END);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Extensions.dp2px(context, 8));
        bg.setColor(bgColor);
        pill.setBackground(bg);
        int h = Extensions.dp2px(context, 8);
        int v = Extensions.dp2px(context, 3);
        pill.setPaddingRelative(h, v, h, v);
        return pill;
    }

    private static String slotLabel(Context context, PartitionItem item) {
        String slot = context.getString(item.slot == PartitionItem.Slot.A ? R.string.partition_slot_a
                : item.slot == PartitionItem.Slot.B ? R.string.partition_slot_b : R.string.partition_shared);
        return item.isActiveSlot ? context.getString(R.string.partition_active_slot_format, slot) : slot;
    }

    private static String activeSlotLabel(Context context, String activeSlot) {
        if (TextUtils.isEmpty(activeSlot)) return context.getString(R.string.partition_unspecified);
        String clean = activeSlot.trim().toLowerCase(Locale.ROOT);
        if ("a".equals(clean) || "_a".equals(clean)) {
            return context.getString(R.string.partition_active_slot_format, context.getString(R.string.partition_slot_a));
        }
        if ("b".equals(clean) || "_b".equals(clean)) {
            return context.getString(R.string.partition_active_slot_format, context.getString(R.string.partition_slot_b));
        }
        return activeSlot.toUpperCase(Locale.ROOT);
    }

    private static MaterialCardView createDeviceHeaderCard(AppCompatActivity activity, String activeSlot,
                                                           int surfaceHigh, int outlineColor,
                                                           int primaryContainer, int onPrimaryContainer,
                                                           int neutralPillBg, int onSurface, int onSurfaceVariant) {
        MaterialCardView card = makeCard(activity, surfaceHigh, outlineColor, 16);
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pH = Extensions.dp2px(activity, 12);
        int pV = Extensions.dp2px(activity, 10);
        row.setPaddingRelative(pH, pV, pH, pV);

        ImageView diskIcon = new ImageView(activity);
        diskIcon.setImageDrawable(FileIconSpec.DISK.drawable(activity));
        int iconSize = Extensions.dp2px(activity, 32);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMarginEnd(Extensions.dp2px(activity, 10));
        row.addView(diskIcon, iconLp);

        LinearLayout metaCol = new LinearLayout(activity);
        metaCol.setOrientation(LinearLayout.VERTICAL);
        TextView modelView = new TextView(activity);
        modelView.setText(Build.MODEL);
        modelView.setTextSize(14f);
        modelView.setTypeface(Typeface.DEFAULT_BOLD);
        modelView.setTextColor(onSurface);
        modelView.setMaxLines(1);
        modelView.setEllipsize(TextUtils.TruncateAt.END);
        metaCol.addView(modelView);

        TextView deviceView = new TextView(activity);
        deviceView.setText(Build.DEVICE);
        deviceView.setTextSize(11.5f);
        deviceView.setTypeface(Typeface.MONOSPACE);
        deviceView.setTextColor(onSurfaceVariant);
        deviceView.setMaxLines(1);
        deviceView.setEllipsize(TextUtils.TruncateAt.END);
        metaCol.addView(deviceView);

        LinearLayout.LayoutParams metaLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        metaLp.setMarginEnd(Extensions.dp2px(activity, 8));
        row.addView(metaCol, metaLp);

        boolean hasSlot = !TextUtils.isEmpty(activeSlot);
        TextView slotPill = makePillBadge(activity, activeSlotLabel(activity, activeSlot),
                hasSlot ? primaryContainer : neutralPillBg,
                hasSlot ? onPrimaryContainer : onSurfaceVariant, hasSlot);
        row.addView(slotPill);

        card.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private static void browse(AppCompatActivity activity, List<PartitionItem> partitions, String activeSlot) {
        int onSurface = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        int onSurfaceVariant = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.LTGRAY);
        int surfaceLow = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFF1F2229);
        int surfaceHigh = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xFF282C35);
        int surfaceHighest = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF333844);
        int primaryContainer = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorPrimaryContainer, 0xFF2B4C7E);
        int onPrimaryContainer = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE);
        int outlineRaw = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOutlineVariant, 0x44888888);
        int outlineSubtle = ColorUtils.setAlphaComponent(outlineRaw, 0x99);

        LinearLayout content = column(activity);
        MaterialCardView headerCard = createDeviceHeaderCard(activity, activeSlot, surfaceHigh, outlineSubtle,
                primaryContainer, onPrimaryContainer, surfaceHighest, onSurface, onSurfaceVariant);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headerLp.bottomMargin = Extensions.dp2px(activity, 10);
        content.addView(headerCard, headerLp);

        TextInputLayout searchBox = UiFields.box(activity, null);
        searchBox.setHintEnabled(false);
        float radius = Extensions.dp2px(activity, 14);
        searchBox.setBoxCornerRadii(radius, radius, radius, radius);
        searchBox.setStartIconDrawable(R.drawable.search_24px);
        searchBox.setStartIconTintList(ColorStateList.valueOf(onSurfaceVariant));
        searchBox.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        TextInputEditText search = new TextInputEditText(searchBox.getContext());
        search.setHint(R.string.partition_search_hint);
        search.setHintTextColor(onSurfaceVariant);
        search.setTextColor(onSurface);
        search.setTextSize(13.5f);
        search.setSingleLine(true);
        search.setEllipsize(TextUtils.TruncateAt.END);
        search.setPaddingRelative(Extensions.dp2px(activity, 4), Extensions.dp2px(activity, 10),
                Extensions.dp2px(activity, 8), Extensions.dp2px(activity, 10));
        searchBox.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(searchBox);

        TextView count = new TextView(activity);
        count.setText(activity.getString(R.string.partition_count_summary, partitions.size()));
        count.setTextSize(12f);
        count.setTypeface(Typeface.DEFAULT_BOLD);
        count.setTextColor(onSurfaceVariant);
        count.setPaddingRelative(Extensions.dp2px(activity, 4), Extensions.dp2px(activity, 10),
                Extensions.dp2px(activity, 4), Extensions.dp2px(activity, 6));
        content.addView(count);

        FastScrollerRecyclerView list = new FastScrollerRecyclerView(activity);
        list.setLayoutManager(new LinearLayoutManager(activity));
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setPadding(0, Extensions.dp2px(activity, 2), 0, Extensions.dp2px(activity, 4));
        int height = Math.max(Extensions.dp2px(activity, 180),
                Math.min(Extensions.dp2px(activity, 440),
                        (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.50f) - Extensions.dp2px(activity, 80)));
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
                MaterialCardView card = makeCard(activity, surfaceLow, outlineSubtle, 14);
                card.setClickable(true);
                card.setFocusable(true);
                RecyclerView.LayoutParams cardLp = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cardLp.bottomMargin = Extensions.dp2px(activity, 8);
                card.setLayoutParams(cardLp);

                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int padH = Extensions.dp2px(activity, 12);
                int padV = Extensions.dp2px(activity, 10);
                row.setPaddingRelative(padH, padV, padH, padV);

                ImageView icon = new ImageView(activity);
                icon.setId(android.R.id.icon);
                icon.setImageDrawable(FileIconSpec.DISK.drawable(activity));
                int iconSize = Extensions.dp2px(activity, 34);
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.setMarginEnd(Extensions.dp2px(activity, 12));
                row.addView(icon, iconLp);

                LinearLayout textCol = new LinearLayout(activity);
                textCol.setOrientation(LinearLayout.VERTICAL);

                TextView title = new TextView(activity);
                title.setId(android.R.id.text1);
                title.setTextSize(15f);
                title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                title.setTextColor(onSurface);
                title.setMaxLines(1);
                title.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(title);

                LinearLayout slotsBox = new LinearLayout(activity);
                slotsBox.setId(android.R.id.list);
                slotsBox.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams slotsLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                slotsLp.topMargin = Extensions.dp2px(activity, 5);
                textCol.addView(slotsBox, slotsLp);

                row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                ImageView chevron = new ImageView(activity);
                chevron.setImageResource(R.drawable.chevron_right_24px);
                chevron.setColorFilter(onSurfaceVariant);
                chevron.setAlpha(0.65f);
                int chevSize = Extensions.dp2px(activity, 20);
                LinearLayout.LayoutParams chevLp = new LinearLayout.LayoutParams(chevSize, chevSize);
                chevLp.setMarginStart(Extensions.dp2px(activity, 8));
                row.addView(chevron, chevLp);

                card.addView(row, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new PartitionHolder(card);
            }

            @Override public void onBindViewHolder(@NonNull PartitionHolder holder, int position) {
                String name = visible.get(position);
                List<PartitionItem> items = groups.get(name);
                holder.title.setText(name);
                holder.slotsBox.removeAllViews();
                for (int i = 0; i < items.size(); i++) {
                    PartitionItem item = items.get(i);
                    String label = activity.getString(R.string.partition_slot_size,
                            slotLabel(activity, item), FileSize.getHumanReadableFileSize(item.sizeBytes));
                    TextView pill = makePillBadge(activity, label,
                            item.isActiveSlot ? primaryContainer : surfaceHighest,
                            item.isActiveSlot ? onPrimaryContainer : onSurfaceVariant,
                            item.isActiveSlot);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) lp.topMargin = Extensions.dp2px(activity, 4);
                    holder.slotsBox.addView(pill, lp);
                }
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
        final TextView title;
        final LinearLayout slotsBox;
        PartitionHolder(View view) {
            super(view);
            title = view.findViewById(android.R.id.text1);
            slotsBox = view.findViewById(android.R.id.list);
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
        int surfaceLow = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorSurfaceContainerLow, 0xFF1F2229);
        int outlineRaw = MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOutlineVariant, 0x44888888);
        MaterialCardView card = makeCard(activity, surfaceLow, ColorUtils.setAlphaComponent(outlineRaw, 0x99), 14);
        TextView info = text(activity, value, false);
        info.setTextSize(13.5f);
        info.setLineSpacing(Extensions.dp2px(activity, 3), 1f);
        int cardPad = Extensions.dp2px(activity, 14);
        info.setPadding(cardPad, cardPad, cardPad, cardPad);
        info.setTextIsSelectable(true);
        card.addView(info, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
            if (items.size() == 1) builder.setView(UiFields.wrap(activity,
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
        AppExecutors.io().execute(() -> {
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
                    process = root("cat " + ShellQuote.escapeShellArg(item.blockPath));
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
                                AppExecutors.runOnUiIfAlive(activity, () -> progress.setText(activity.getString(R.string.partition_dumping_progress,
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
            AppExecutors.runOnUiIfAlive(activity, () -> {
                progress.dismiss();
                if (!alive(activity)) return;
                if (activity instanceof MainActivity main) main.refreshPane(true);
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.partition_backup_result_title)
                        .setMessage(activity.getString(R.string.partition_backup_result, count, items.size(), directory.getAbsolutePath())
                                + (TextUtils.isEmpty(error) ? "" : "\n\n" + error))
                        .setPositiveButton(android.R.string.ok, null).show();
            });
        });
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
        AppExecutors.io().execute(() -> {
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
                AppExecutors.runOnUiIfAlive(activity, () -> {
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
                AppExecutors.runOnUiIfAlive(activity, () -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity, activity.getString(R.string.partition_flash_failed, error.getMessage()));
                });
            }
        });
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
        AppExecutors.io().execute(() -> {
            String error = null;
            int completed = 0;
            try {
                if (image.length() != length || image.lastModified() != modified)
                    throw new IOException(activity.getString(R.string.partition_image_changed));
                for (PartitionItem target : targets) {
                    Process process = root("dd of=" + ShellQuote.escapeShellArg(target.blockPath) + " bs=1048576 conv=fsync");
                    try {
                        StringBuilder errors = new StringBuilder();
                        Thread drain = drainErrors(process, errors);
                        try (OutputStream out = new BufferedOutputStream(process.getOutputStream(), 1024 * 1024)) {
                            if (sparse) SystemImageEngine.unsparseToStream(image, out, new SystemImageEngine.ProgressCallback() {
                                @Override public void onProgress(String message, int percent) {
                                    AppExecutors.runOnUiIfAlive(activity, () -> progress.setProgress(percent, 100));
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
            AppExecutors.runOnUiIfAlive(activity, () -> {
                progress.dismiss();
                if (!alive(activity)) return;
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(failure == null ? R.string.title_success : R.string.error)
                        .setMessage(activity.getString(R.string.partition_flash_result, count, targets.size())
                                + (failure == null ? "\n\n" + activity.getString(R.string.partition_reboot_message) : "\n\n" + failure))
                        .setPositiveButton(android.R.string.ok, null).show();
            });
        });
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
        AppExecutors.io().execute(() -> {
            File output = null;
            try {
                output = FileUtils.getUnusedFile(new File(image.getParentFile(), image.getName() + "_raw.img"));
                SystemImageEngine.unsparseToFile(image, output, null);
                File result = output;
                AppExecutors.runOnUiIfAlive(activity, () -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity,
                            activity.getString(R.string.partition_unsparse_success, result.getAbsolutePath(), FileSize.getHumanReadableFileSize(result.length())));
                });
            } catch (Exception error) {
                if (output != null) output.delete();
                AppExecutors.runOnUiIfAlive(activity, () -> {
                    progress.dismiss();
                    if (alive(activity)) Extensions.showMessage(activity, activity.getString(R.string.partition_unsparse_failed, error.getMessage()));
                });
            }
        });
    }
}

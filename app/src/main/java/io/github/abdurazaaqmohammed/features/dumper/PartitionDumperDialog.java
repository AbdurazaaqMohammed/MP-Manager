package io.github.abdurazaaqmohammed.features.dumper;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.graphics.Typeface;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.boot.BootImageEngine;
import io.github.abdurazaaqmohammed.boot.BootInspectManager;
import io.github.abdurazaaqmohammed.features.system.SystemImageEngine;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.views.FastScrollerRecyclerView;

/**
 * Universal Android Partition Dumper dialog with A/B slot switching in the header
 * and instant visual indicator for differences between Slot A and Slot B.
 */
public final class PartitionDumperDialog {

    private PartitionDumperDialog() {
    }

    public static void show(AppCompatActivity activity) {
        if (activity == null || activity.isFinishing()) return;

        ProgressManager pm = new ProgressManager(activity, false).show();
        pm.setText(activity.getString(R.string.tools_hub_loading));

        new Thread(() -> {
            try {
                List<PartitionItem> partitions = PartitionResolver.queryPartitions(activity);
                String activeSlot = PartitionResolver.getActiveSlot();
                List<String> slotDiffs = PartitionResolver.findSlotDifferences();
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    if (partitions.isEmpty()) {
                        Extensions.showMessage(activity, activity.getString(R.string.partition_root_required));
                        return;
                    }
                    displayMainDialog(activity, partitions, activeSlot, slotDiffs);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(activity, "Error discovering partitions: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void displayMainDialog(AppCompatActivity activity, List<PartitionItem> allPartitions,
                                          String activeSlot, List<String> slotDiffs) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(activity, 14);
        root.setPadding(pad, pad, pad, pad);

        // Check if device uses A/B slots
        boolean hasSlotA = false;
        boolean hasSlotB = false;
        for (PartitionItem p : allPartitions) {
            if (p.slot == PartitionItem.Slot.A) hasSlotA = true;
            if (p.slot == PartitionItem.Slot.B) hasSlotB = true;
        }
        boolean isDeviceAB = hasSlotA || hasSlotB;

        // Current selected slot: defaults to active slot or Slot A
        final PartitionItem.Slot[] selectedSlot = {
                "b".equals(activeSlot) ? PartitionItem.Slot.B : PartitionItem.Slot.A
        };

        // Filter state: 0=All, 1=Common, 2=Dynamic, 3=DiffOnly
        final int[] currentCat = {0};
        final Runnable[] applyFiltersRef = new Runnable[1];

        // Header slot switch row
        if (isDeviceAB) {
            LinearLayout slotHeader = new LinearLayout(activity);
            slotHeader.setOrientation(LinearLayout.HORIZONTAL);
            slotHeader.setGravity(Gravity.CENTER_VERTICAL);
            slotHeader.setPadding(0, 0, 0, Extensions.dp2px(activity, 6));

            TextView slotTitle = new TextView(activity);
            slotTitle.setText(isAr ? "السلوت المحدد:" : "Target Slot:");
            slotTitle.setTextSize(13f);
            slotTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            slotTitle.setPadding(0, 0, Extensions.dp2px(activity, 8), 0);
            slotHeader.addView(slotTitle);

            String labelA = activity.getString(R.string.partition_slot_a);
            if ("a".equals(activeSlot)) labelA += activity.getString(R.string.partition_slot_active_tag);
            MaterialButton btnSlotA = createHeaderTabButton(activity, labelA, selectedSlot[0] == PartitionItem.Slot.A);

            String labelB = activity.getString(R.string.partition_slot_b);
            if ("b".equals(activeSlot)) labelB += activity.getString(R.string.partition_slot_active_tag);
            MaterialButton btnSlotB = createHeaderTabButton(activity, labelB, selectedSlot[0] == PartitionItem.Slot.B);

            slotHeader.addView(btnSlotA);
            slotHeader.addView(btnSlotB);
            root.addView(slotHeader);

            btnSlotA.setOnClickListener(v -> {
                selectedSlot[0] = PartitionItem.Slot.A;
                btnSlotA.setStrokeColorResource(R.color.md_theme_primary);
                btnSlotB.setStrokeColorResource(android.R.color.transparent);
                if (applyFiltersRef[0] != null) applyFiltersRef[0].run();
            });

            btnSlotB.setOnClickListener(v -> {
                selectedSlot[0] = PartitionItem.Slot.B;
                btnSlotB.setStrokeColorResource(R.color.md_theme_primary);
                btnSlotA.setStrokeColorResource(android.R.color.transparent);
                if (applyFiltersRef[0] != null) applyFiltersRef[0].run();
            });

            // Red differences line directly under the header (hidden if no differences)
            if (slotDiffs != null && !slotDiffs.isEmpty()) {
                TextView diffBanner = new TextView(activity);
                diffBanner.setTextColor(0xFFD32F2F); // Deep Red
                diffBanner.setTextSize(12f);
                diffBanner.setTypeface(null, android.graphics.Typeface.BOLD);
                diffBanner.setBackgroundColor(0x1AD32F2F); // Light red highlight
                int bPad = Extensions.dp2px(activity, 8);
                diffBanner.setPadding(bPad, bPad, bPad, bPad);
                diffBanner.setText(activity.getString(R.string.partition_diff_banner, slotDiffs.size()));

                LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bannerParams.setMargins(0, 0, 0, Extensions.dp2px(activity, 8));
                diffBanner.setLayoutParams(bannerParams);

                diffBanner.setOnClickListener(v -> {
                    showDiffDetailsDialog(activity, allPartitions, slotDiffs, () -> {
                        currentCat[0] = 3; // Show only diffs
                        if (applyFiltersRef[0] != null) applyFiltersRef[0].run();
                    });
                });

                root.addView(diffBanner);
            }
        }

        // Search input
        EditText searchInput = new EditText(activity);
        searchInput.setHint(R.string.partition_search_hint);
        searchInput.setTextSize(14f);
        root.addView(searchInput);

        // Filter chips bar (All, Common, Dynamic)
        LinearLayout filterBar = new LinearLayout(activity);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setPadding(0, Extensions.dp2px(activity, 4), 0, Extensions.dp2px(activity, 8));

        MaterialButton btnAll = createFilterButton(activity, isAr ? "الكل" : "All", true);
        MaterialButton btnCommon = createFilterButton(activity, isAr ? "الرئيسية" : "Common", false);
        MaterialButton btnDynamic = createFilterButton(activity, isAr ? "الديناميكية" : "Dynamic", false);

        filterBar.addView(btnAll);
        filterBar.addView(btnCommon);
        filterBar.addView(btnDynamic);
        root.addView(filterBar);

        // Partitions list with FastScroller
        List<PartitionItem> displayedItems = new ArrayList<>();
        FastScrollerRecyclerView recyclerView = new FastScrollerRecyclerView(activity);
        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Extensions.dp2px(activity, 380)));

        class PartitionViewHolder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView title;
            final TextView subtitle;
            final TextView badgeSlot;
            final MaterialButton btnDump;

            PartitionViewHolder(@NonNull View itemView, ImageView icon, TextView title, TextView subtitle, TextView badgeSlot, MaterialButton btnDump) {
                super(itemView);
                this.icon = icon;
                this.title = title;
                this.subtitle = subtitle;
                this.badgeSlot = badgeSlot;
                this.btnDump = btnDump;
            }
        }

        AlertDialog[] dialogRef = new AlertDialog[1];

        RecyclerView.Adapter<PartitionViewHolder> adapter = new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public PartitionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(parent.getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int rPad = Extensions.dp2px(parent.getContext(), 8);
                row.setPadding(rPad, rPad, rPad, rPad);
                row.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                ImageView iv = new ImageView(parent.getContext());
                int iconSize = Extensions.dp2px(parent.getContext(), 36);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconParams.setMarginEnd(Extensions.dp2px(parent.getContext(), 10));
                iv.setLayoutParams(iconParams);
                iv.setImageResource(R.drawable.ic_disk_drive_24px);
                row.addView(iv);

                LinearLayout col = new LinearLayout(parent.getContext());
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                col.setLayoutParams(colParams);

                LinearLayout titleLine = new LinearLayout(parent.getContext());
                titleLine.setOrientation(LinearLayout.HORIZONTAL);
                titleLine.setGravity(Gravity.CENTER_VERTICAL);

                TextView tvTitle = new TextView(parent.getContext());
                tvTitle.setTextSize(14f);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                titleLine.addView(tvTitle);

                TextView tvBadge = new TextView(parent.getContext());
                tvBadge.setTextSize(10f);
                tvBadge.setTextColor(0xFF2E7D32);
                tvBadge.setPadding(Extensions.dp2px(parent.getContext(), 6), 0, 0, 0);
                titleLine.addView(tvBadge);
                col.addView(titleLine);

                TextView tvSub = new TextView(parent.getContext());
                tvSub.setTextSize(12f);
                tvSub.setTextColor(0xFF757575);
                col.addView(tvSub);
                row.addView(col);

                MaterialButton action = new MaterialButton(parent.getContext(), null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                action.setText(isAr ? "إجراءات" : "Actions");
                action.setTextSize(11f);
                action.setInsetTop(0);
                action.setInsetBottom(0);
                action.setMinHeight(0);
                action.setMinimumHeight(0);
                int bPad = Extensions.dp2px(parent.getContext(), 6);
                action.setPadding(bPad, bPad, bPad, bPad);
                row.addView(action);

                return new PartitionViewHolder(row, iv, tvTitle, tvSub, tvBadge, action);
            }

            @Override
            public void onBindViewHolder(@NonNull PartitionViewHolder holder, int position) {
                PartitionItem item = displayedItems.get(position);
                holder.title.setText(item.friendlyName);

                String sizeStr = item.sizeBytes > 0 ? FileSize.getHumanReadableFileSize(item.sizeBytes) : (isAr ? "ديناميكي" : "Dynamic");
                String subText = item.rawName + " (" + sizeStr + ") • " + item.blockPath;
                if (!TextUtils.isEmpty(item.description)) {
                    subText += "\n" + item.description;
                }
                holder.subtitle.setText(subText);

                if (item.isActiveSlot) {
                    holder.badgeSlot.setVisibility(View.VISIBLE);
                    holder.badgeSlot.setText(activity.getString(R.string.partition_slot_active_tag).trim());
                } else if (item.isDynamic) {
                    holder.badgeSlot.setVisibility(View.VISIBLE);
                    holder.badgeSlot.setText(isAr ? "ديناميكي" : "Dynamic");
                } else {
                    holder.badgeSlot.setVisibility(View.GONE);
                }

                View.OnClickListener itemClick = v -> {
                    showPartitionActionDialog(activity, item, dialogRef[0]);
                };
                holder.btnDump.setOnClickListener(itemClick);
                holder.itemView.setOnClickListener(itemClick);
            }

            @Override
            public int getItemCount() {
                return displayedItems.size();
            }
        };

        recyclerView.setAdapter(adapter);
        root.addView(recyclerView);

        // Core filter method
        applyFiltersRef[0] = () -> {
            String q = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
            displayedItems.clear();
            for (PartitionItem p : allPartitions) {
                // Slot filter: if A is selected, show Slot A + SHARED. If B is selected, show Slot B + SHARED.
                if (isDeviceAB) {
                    if (selectedSlot[0] == PartitionItem.Slot.A) {
                        if (p.slot == PartitionItem.Slot.B) continue;
                    } else if (selectedSlot[0] == PartitionItem.Slot.B) {
                        if (p.slot == PartitionItem.Slot.A) continue;
                    }
                }

                // Category filter
                if (currentCat[0] == 1 && p.group != PartitionItem.Group.COMMON) continue;
                if (currentCat[0] == 2 && !p.isDynamic) continue;
                if (currentCat[0] == 3) {
                    // Differences filter: only show partitions that differ between A and B
                    boolean isDiff = false;
                    if (slotDiffs != null) {
                        for (String diff : slotDiffs) {
                            if (p.rawName.startsWith(diff)) {
                                isDiff = true;
                                break;
                            }
                        }
                    }
                    if (!isDiff) continue;
                }

                // Search query match
                if (!q.isEmpty()) {
                    boolean match = p.rawName.toLowerCase(Locale.ROOT).contains(q)
                            || p.friendlyName.toLowerCase(Locale.ROOT).contains(q)
                            || p.description.toLowerCase(Locale.ROOT).contains(q)
                            || p.blockPath.toLowerCase(Locale.ROOT).contains(q);
                    // Smart aliases
                    if (!match) {
                        if (q.contains("recov") || q.contains("ريكفر")) {
                            match = p.rawName.contains("recovery") || p.friendlyName.toLowerCase().contains("recovery");
                        } else if (q.contains("boot") || q.contains("بوت") || q.contains("كيرنل")) {
                            match = p.rawName.contains("boot") || p.friendlyName.toLowerCase().contains("boot");
                        } else if (q.contains("system") || q.contains("سيستم") || q.contains("نظام")) {
                            match = p.rawName.contains("system") || p.friendlyName.toLowerCase().contains("system");
                        } else if (q.contains("vendor") || q.contains("فيندور")) {
                            match = p.rawName.contains("vendor") || p.friendlyName.toLowerCase().contains("vendor");
                        } else if (q.contains("vbmeta") || q.contains("حماي")) {
                            match = p.rawName.contains("vbmeta") || p.friendlyName.toLowerCase().contains("vbmeta");
                        }
                    }
                    if (!match) continue;
                }
                displayedItems.add(p);
            }
            adapter.notifyDataSetChanged();
        };

        // Initialize default view
        applyFiltersRef[0].run();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFiltersRef[0].run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        View.OnClickListener catClick = v -> {
            btnAll.setStrokeColorResource(v == btnAll ? R.color.md_theme_primary : android.R.color.transparent);
            btnCommon.setStrokeColorResource(v == btnCommon ? R.color.md_theme_primary : android.R.color.transparent);
            btnDynamic.setStrokeColorResource(v == btnDynamic ? R.color.md_theme_primary : android.R.color.transparent);

            if (v == btnCommon) currentCat[0] = 1;
            else if (v == btnDynamic) currentCat[0] = 2;
            else currentCat[0] = 0;
            applyFiltersRef[0].run();
        };

        btnAll.setOnClickListener(catClick);
        btnCommon.setOnClickListener(catClick);
        btnDynamic.setOnClickListener(catClick);

        dialogRef[0] = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.partition_dumper_title))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        DialogUtil.keepButtonsHorizontal(dialogRef[0]);
    }

    private static void showDiffDetailsDialog(AppCompatActivity activity, List<PartitionItem> allPartitions,
                                              List<String> slotDiffs, Runnable onFilterDifferences) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String diffName : slotDiffs) {
            count++;
            String friendly = diffName;
            for (PartitionItem p : allPartitions) {
                if (p.rawName.startsWith(diffName)) {
                    friendly = p.friendlyName;
                    break;
                }
            }
            sb.append("• ").append(friendly).append(" (").append(diffName).append(")\n");
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.partition_diff_dialog_title, count))
                .setMessage(activity.getString(R.string.partition_diff_dialog_msg, sb.toString().trim()))
                .setNegativeButton(android.R.string.ok, null);

        if (onFilterDifferences != null) {
            builder.setPositiveButton(R.string.partition_diff_filter_btn, (d, w) -> onFilterDifferences.run());
        }

        AlertDialog ad = builder.show();
        DialogUtil.keepButtonsHorizontal(ad);
    }

    private static MaterialButton createHeaderTabButton(Context context, String text, boolean selected) {
        MaterialButton btn = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setTextSize(12f);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        int padH = Extensions.dp2px(context, 10);
        int padV = Extensions.dp2px(context, 6);
        btn.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Extensions.dp2px(context, 8));
        btn.setLayoutParams(lp);
        if (selected) {
            btn.setStrokeColorResource(R.color.md_theme_primary);
        }
        return btn;
    }

    private static MaterialButton createFilterButton(Context context, String text, boolean selected) {
        MaterialButton btn = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btn.setText(text);
        btn.setTextSize(11f);
        btn.setInsetTop(0);
        btn.setInsetBottom(0);
        btn.setMinHeight(0);
        btn.setMinimumHeight(0);
        int padH = Extensions.dp2px(context, 8);
        int padV = Extensions.dp2px(context, 4);
        btn.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(Extensions.dp2px(context, 6));
        btn.setLayoutParams(lp);
        if (selected) {
            btn.setStrokeColorResource(R.color.md_theme_primary);
        }
        return btn;
    }

    public static void showPartitionActionDialog(AppCompatActivity activity, PartitionItem item, AlertDialog parentDialogToDismiss) {
        if (activity == null || activity.isFinishing() || item == null) return;

        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(activity, 20);
        root.setPadding(pad, Extensions.dp2px(activity, 14), pad, pad);

        // Header details
        TextView tvSub = new TextView(activity);
        tvSub.setTextSize(13f);
        tvSub.setTextColor(0xFF757575);
        String sizeStr = item.sizeBytes > 0 ? FileSize.getHumanReadableFileSize(item.sizeBytes) : (isAr ? "ديناميكي" : "Dynamic");
        String headerDetails = (isAr ? "المسار: " : "Node: ") + item.blockPath + " • " + sizeStr;
        if (item.isActiveSlot) {
            headerDetails += " • " + activity.getString(R.string.partition_slot_active_tag).trim();
        }
        tvSub.setText(headerDetails);
        root.addView(tvSub);

        // Live hardware detected type line
        TextView tvDetected = new TextView(activity);
        tvDetected.setTextSize(12f);
        tvDetected.setTextColor(0xFF1976D2);
        tvDetected.setTypeface(null, Typeface.BOLD);
        tvDetected.setText(activity.getString(R.string.partition_detected_type, isAr ? "جاري الفحص..." : "Inspecting..."));
        tvDetected.setPadding(0, Extensions.dp2px(activity, 4), 0, Extensions.dp2px(activity, 16));
        root.addView(tvDetected);

        new Thread(() -> {
            SystemImageEngine.ImageIdentity id = SystemImageEngine.identifyImage(new File(item.blockPath));
            activity.runOnUiThread(() -> {
                tvDetected.setText(activity.getString(R.string.partition_detected_type, id.getDisplayName(isAr)));
            });
        }).start();

        AlertDialog[] dialogHolder = new AlertDialog[1];

        // 1. Copy / Backup (Dump)
        root.addView(createActionCard(activity,
                R.drawable.ic_disk_drive_24px,
                activity.getString(R.string.partition_opt_copy),
                activity.getString(R.string.partition_opt_copy_desc),
                0xFF1976D2,
                v -> {
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    showDumpConfirmDialog(activity, item);
                }));

        // 2. Flash Image to Partition
        root.addView(createActionCard(activity,
                R.drawable.ic_flash_on_24px,
                activity.getString(R.string.partition_opt_flash),
                activity.getString(R.string.partition_opt_flash_desc),
                0xFFE65100,
                v -> {
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    showFlashDialog(activity, item);
                }));

        ScrollView sv = new ScrollView(activity);
        sv.addView(root);

        dialogHolder[0] = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.partition_action_dialog_title, item.friendlyName))
                .setView(sv)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        DialogUtil.keepButtonsHorizontal(dialogHolder[0]);
    }

    private static MaterialCardView createActionCard(AppCompatActivity activity, int iconRes, String title,
                                                     String description, int accentColor, View.OnClickListener onClick) {
        MaterialCardView card = new MaterialCardView(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Extensions.dp2px(activity, 10));
        card.setLayoutParams(lp);
        card.setCardElevation(Extensions.dp2px(activity, 1));
        card.setRadius(Extensions.dp2px(activity, 12));
        card.setStrokeWidth(Extensions.dp2px(activity, 1));
        card.setStrokeColor(0x22888888);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Extensions.dp2px(activity, 12);
        row.setPadding(pad, pad, pad, pad);

        ImageView iv = new ImageView(activity);
        iv.setImageResource(iconRes);
        iv.setColorFilter(accentColor);
        int iconSize = Extensions.dp2px(activity, 30);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.setMargins(0, 0, Extensions.dp2px(activity, 12), 0);
        iv.setLayoutParams(iconLp);
        row.addView(iv);

        LinearLayout textCol = new LinearLayout(activity);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(title);
        tvTitle.setTextSize(14f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        textCol.addView(tvTitle);

        TextView tvDesc = new TextView(activity);
        tvDesc.setText(description);
        tvDesc.setTextSize(12f);
        tvDesc.setTextColor(0xFF757575);
        tvDesc.setPadding(0, Extensions.dp2px(activity, 2), 0, 0);
        textCol.addView(tvDesc);

        row.addView(textCol);
        card.addView(row);
        card.setOnClickListener(onClick);
        return card;
    }

    public static void showDumpConfirmDialog(AppCompatActivity activity, PartitionItem item) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(activity, 16);
        root.setPadding(pad, pad, pad, pad);

        // Partition info details
        TextView info = new TextView(activity);
        String sizeStr = item.sizeBytes > 0 ? FileSize.getHumanReadableFileSize(item.sizeBytes) : (isAr ? "ديناميكي" : "Dynamic");
        info.setText(item.friendlyName + "\n" +
                (isAr ? "المسار الحقيقي: " : "Device Node: ") + item.blockPath + " (" + item.targetDev + ")\n" +
                (isAr ? "الحجم المقدر: " : "Estimated Size: ") + sizeStr);
        info.setTextSize(13f);
        info.setPadding(0, 0, 0, Extensions.dp2px(activity, 12));
        root.addView(info);

        // Destination folder
        TextView destLabel = new TextView(activity);
        destLabel.setText(R.string.partition_dump_dest_label);
        destLabel.setTextSize(12f);
        root.addView(destLabel);

        File defaultDir = new File(Environment.getExternalStorageDirectory(), "Download");
        if (activity instanceof MainActivity ma) {
            try {
                String sp = ma.shownPath();
                if (!TextUtils.isEmpty(sp)) {
                    File cur = new File(sp);
                    if (cur.isDirectory() && cur.canWrite()) {
                        defaultDir = cur;
                    }
                }
            } catch (Exception ignored) {}
        }

        EditText destInput = new EditText(activity);
        destInput.setText(defaultDir.getAbsolutePath());
        destInput.setTextSize(13f);
        root.addView(destInput);

        // Filename
        TextView fileLabel = new TextView(activity);
        fileLabel.setText(R.string.partition_dump_filename_label);
        fileLabel.setTextSize(12f);
        fileLabel.setPadding(0, Extensions.dp2px(activity, 8), 0, 0);
        root.addView(fileLabel);

        EditText fileInput = new EditText(activity);
        fileInput.setText(item.outputFileName);
        fileInput.setTextSize(14f);
        root.addView(fileInput);

        // Free space info
        TextView freeSpaceText = new TextView(activity);
        try {
            StatFs stat = new StatFs(defaultDir.getAbsolutePath());
            long freeBytes = stat.getAvailableBytes();
            freeSpaceText.setText((isAr ? "المساحة المتوفرة: " : "Available Free Space: ") + FileSize.getHumanReadableFileSize(freeBytes));
            freeSpaceText.setTextSize(12f);
            freeSpaceText.setTextColor(freeBytes < item.sizeBytes ? 0xFFD32F2F : 0xFF757575);
        } catch (Exception ignored) {
        }
        freeSpaceText.setPadding(0, Extensions.dp2px(activity, 4), 0, 0);
        root.addView(freeSpaceText);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.partition_copy_title, item.rawName))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.partition_copy_btn, (d, w) -> {
                    String dPath = destInput.getText().toString().trim();
                    String fName = fileInput.getText().toString().trim();
                    if (TextUtils.isEmpty(dPath) || TextUtils.isEmpty(fName)) return;

                    File outDir = new File(dPath);
                    if (!outDir.exists()) outDir.mkdirs();
                    File outFile = new File(outDir, fName);

                    executePartitionDump(activity, item, outFile);
                })
                .show();
        DialogUtil.keepButtonsHorizontal(dialog);
    }

    private static void executePartitionDump(AppCompatActivity activity, PartitionItem item, File outFile) {
        ProgressManager pm = new ProgressManager(activity, true).show();
        pm.setText(activity.getString(R.string.partition_dumping_progress, item.rawName, "0 MB",
                item.sizeBytes > 0 ? FileSize.getHumanReadableFileSize(item.sizeBytes) : "?", "0 MB/s"));

        new Thread(() -> {
            Process p = null;
            long copied = 0;
            long total = item.sizeBytes > 0 ? item.sizeBytes : 1;
            long startTime = System.currentTimeMillis();
            try {
                String cmd = "cat '" + item.blockPath + "'";
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});

                try (InputStream is = new BufferedInputStream(p.getInputStream(), 1024 * 1024);
                     BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outFile), 1024 * 1024)) {

                    byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
                    int read;
                    long lastUpdate = 0;
                    boolean hasNonZero = false;

                    while ((read = is.read(buffer)) != -1) {
                        if (pm.isCancelling()) {
                            p.destroy();
                            os.close();
                            outFile.delete();
                            activity.runOnUiThread(pm::dismiss);
                            return;
                        }

                        if (!hasNonZero) {
                            for (int i = 0; i < read; i++) {
                                if (buffer[i] != 0) {
                                    hasNonZero = true;
                                    break;
                                }
                            }
                        }

                        os.write(buffer, 0, read);
                        copied += read;

                        long now = System.currentTimeMillis();
                        if (now - lastUpdate > 300) {
                            lastUpdate = now;
                            long elapsedSec = Math.max(1, (now - startTime) / 1000);
                            long speedBps = copied / elapsedSec;
                            String speedStr = FileSize.getHumanReadableFileSize(speedBps) + "/s";
                            String copiedStr = FileSize.getHumanReadableFileSize(copied);
                            String totalStr = item.sizeBytes > 0 ? FileSize.getHumanReadableFileSize(total) : copiedStr;

                            final long currentCopied = copied;
                            activity.runOnUiThread(() -> {
                                pm.setText(activity.getString(R.string.partition_dumping_progress,
                                        item.rawName, copiedStr, totalStr, speedStr));
                                if (item.sizeBytes > 0) {
                                    pm.setProgress((int) (currentCopied / (1024 * 1024)), (int) (total / (1024 * 1024)));
                                }
                            });
                        }
                    }
                    os.flush();

                    if (!hasNonZero) {
                        os.close();
                        outFile.delete();
                        p.destroy();
                        activity.runOnUiThread(() -> {
                            pm.dismiss();
                            boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
                            new MaterialAlertDialogBuilder(activity)
                                    .setTitle(activity.getString(R.string.title_warning))
                                    .setMessage(isAr ? "تم إلغاء السحب: هذا البارتشن فارغ تماماً (ممتلئ بنسبة 100% بالأصفار فقط 0x00 ولا يحتوي على أي بيانات)!"
                                                     : "Dump canceled: Partition is completely empty (100% all zeros) and contains no data!")
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                        });
                        return;
                    }
                }

                p.waitFor();

                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    if (activity instanceof MainActivity ma) {
                        ma.reloadCurrentFolder();
                    }
                    showDumpSuccessDialog(activity, outFile);
                });

            } catch (Exception e) {
                if (p != null) p.destroy();
                outFile.delete();
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(activity, "Dump failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void showDumpSuccessDialog(AppCompatActivity activity, File outFile) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.title_success)
                .setMessage(activity.getString(R.string.partition_dump_success, outFile.getAbsolutePath()))
                .setNegativeButton(android.R.string.ok, null);

        if (activity instanceof MainActivity ma) {
            b.setPositiveButton(R.string.partition_open_img, (d, w) -> {
                if (BootImageEngine.isBootImage(outFile)) {
                    BootInspectManager.openBootImage(ma, outFile, true);
                } else {
                    ma.loadFolderInPane(outFile.getParentFile(), true);
                }
            });
            b.setNeutralButton(R.string.partition_show_in_folder, (d, w) -> {
                File parent = outFile.getParentFile();
                if (parent != null) {
                    ma.loadFolderInPane(parent, true);
                }
            });
        }

        AlertDialog ad = b.show();
        DialogUtil.keepButtonsHorizontal(ad);
    }

    public static void showFlashDialog(AppCompatActivity activity, PartitionItem item) {
        if (activity == null || activity.isFinishing() || item == null) return;
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(activity, 16);
        root.setPadding(pad, pad, pad, pad);

        // Partition info details
        TextView info = new TextView(activity);
        String sizeStr = item.sizeBytes > 0 ? FileSize.getHumanReadableFileSize(item.sizeBytes) : (isAr ? "ديناميكي" : "Dynamic");
        info.setText(item.friendlyName + "\n" +
                (isAr ? "المسار الحقيقي: " : "Device Node: ") + item.blockPath + " (" + item.targetDev + ")\n" +
                (isAr ? "السعة القصوى للقسم: " : "Partition Capacity: ") + sizeStr);
        info.setTextSize(13f);
        root.addView(info);

        // Live hardware detected type line
        TextView tvLiveType = new TextView(activity);
        tvLiveType.setTextSize(12f);
        tvLiveType.setTextColor(0xFF1976D2);
        tvLiveType.setTypeface(null, Typeface.BOLD);
        tvLiveType.setText(activity.getString(R.string.partition_detected_type, isAr ? "جاري الفحص..." : "Inspecting..."));
        tvLiveType.setPadding(0, Extensions.dp2px(activity, 4), 0, Extensions.dp2px(activity, 10));
        root.addView(tvLiveType);

        SystemImageEngine.ImageIdentity[] livePartitionId = new SystemImageEngine.ImageIdentity[1];
        Runnable[] validateRef = new Runnable[1];
        new Thread(() -> {
            SystemImageEngine.ImageIdentity id = SystemImageEngine.identifyImage(new File(item.blockPath));
            livePartitionId[0] = id;
            activity.runOnUiThread(() -> {
                tvLiveType.setText(activity.getString(R.string.partition_detected_type, id.getDisplayName(isAr)));
                if (validateRef[0] != null) validateRef[0].run();
            });
        }).start();

        // Source file label
        TextView srcLabel = new TextView(activity);
        srcLabel.setText(R.string.partition_flash_src_label);
        srcLabel.setTextSize(12f);
        srcLabel.setTypeface(null, Typeface.BOLD);
        root.addView(srcLabel);

        // Current directory determination
        File defaultDir = new File(Environment.getExternalStorageDirectory(), "Download");
        if (activity instanceof MainActivity ma) {
            try {
                String sp = ma.shownPath();
                if (!TextUtils.isEmpty(sp)) {
                    File cur = new File(sp);
                    if (cur.isDirectory() && cur.canWrite()) {
                        defaultDir = cur;
                    }
                }
            } catch (Exception ignored) {}
        }

        // Row with EditText and Browse button
        LinearLayout srcRow = new LinearLayout(activity);
        srcRow.setOrientation(LinearLayout.HORIZONTAL);
        srcRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText srcInput = new EditText(activity);
        srcInput.setHint(R.string.partition_flash_path_hint);
        srcInput.setTextSize(13f);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        srcInput.setLayoutParams(inputLp);
        srcRow.addView(srcInput);

        MaterialButton btnBrowse = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnBrowse.setText(R.string.partition_browse_file);
        btnBrowse.setTextSize(11f);
        btnBrowse.setInsetTop(0);
        btnBrowse.setInsetBottom(0);
        btnBrowse.setMinHeight(0);
        btnBrowse.setMinimumHeight(0);
        int bPad = Extensions.dp2px(activity, 8);
        btnBrowse.setPadding(bPad, bPad, bPad, bPad);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnLp.setMarginStart(Extensions.dp2px(activity, 8));
        btnBrowse.setLayoutParams(btnLp);
        srcRow.addView(btnBrowse);

        root.addView(srcRow);

        // Quick .img chips from current folder
        File[] localFiles = defaultDir.listFiles();
        List<File> availableImgFiles = new ArrayList<>();
        if (localFiles != null) {
            for (File f : localFiles) {
                if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".img")) {
                    availableImgFiles.add(f);
                }
            }
        }

        if (!availableImgFiles.isEmpty()) {
            TextView chipsLabel = new TextView(activity);
            chipsLabel.setText(R.string.partition_flash_select_available);
            chipsLabel.setTextSize(11f);
            chipsLabel.setTextColor(0xFF757575);
            chipsLabel.setPadding(0, Extensions.dp2px(activity, 6), 0, Extensions.dp2px(activity, 2));
            root.addView(chipsLabel);

            HorizontalScrollView hsv = new HorizontalScrollView(activity);
            LinearLayout chipRow = new LinearLayout(activity);
            chipRow.setOrientation(LinearLayout.HORIZONTAL);

            for (File img : availableImgFiles) {
                MaterialButton chip = new MaterialButton(activity, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
                chip.setText(img.getName() + " (" + FileSize.getHumanReadableFileSize(img.length()) + ")");
                chip.setTextSize(11f);
                chip.setInsetTop(0);
                chip.setInsetBottom(0);
                chip.setMinHeight(0);
                chip.setMinimumHeight(0);
                int cPad = Extensions.dp2px(activity, 6);
                chip.setPadding(cPad, cPad, cPad, cPad);
                LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                cParams.setMarginEnd(Extensions.dp2px(activity, 6));
                chip.setLayoutParams(cParams);

                chip.setOnClickListener(v -> srcInput.setText(img.getAbsolutePath()));
                chipRow.addView(chip);
            }
            hsv.addView(chipRow);
            root.addView(hsv);
        }

        // Live validation status message container
        LinearLayout statusContainer = new LinearLayout(activity);
        statusContainer.setOrientation(LinearLayout.VERTICAL);
        statusContainer.setPadding(0, Extensions.dp2px(activity, 8), 0, Extensions.dp2px(activity, 4));
        statusContainer.setVisibility(View.GONE);

        TextView statusText = new TextView(activity);
        statusText.setTextSize(12f);
        statusContainer.addView(statusText);

        // Sparse action button (Unsparse to Raw File)
        MaterialButton btnUnsparseFile = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnUnsparseFile.setText(R.string.partition_flash_unsparse_to_file);
        btnUnsparseFile.setTextSize(11f);
        btnUnsparseFile.setInsetTop(0);
        btnUnsparseFile.setInsetBottom(0);
        btnUnsparseFile.setMinHeight(0);
        btnUnsparseFile.setMinimumHeight(0);
        int uPad = Extensions.dp2px(activity, 6);
        btnUnsparseFile.setPadding(uPad, uPad, uPad, uPad);
        LinearLayout.LayoutParams uParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        uParams.setMargins(0, Extensions.dp2px(activity, 6), 0, Extensions.dp2px(activity, 4));
        btnUnsparseFile.setLayoutParams(uParams);
        btnUnsparseFile.setVisibility(View.GONE);
        statusContainer.addView(btnUnsparseFile);

        root.addView(statusContainer);

        // Risk acknowledgment checkbox (for small files or forced flashing)
        CheckBox cbRisk = new CheckBox(activity);
        cbRisk.setText(R.string.partition_flash_force_small_ack);
        cbRisk.setTextSize(12f);
        cbRisk.setTextColor(0xFFD32F2F);
        cbRisk.setTypeface(null, Typeface.BOLD);
        cbRisk.setVisibility(View.GONE);
        root.addView(cbRisk);

        final File pickerDir = defaultDir;
        btnBrowse.setOnClickListener(v -> {
            showImgFilePicker(activity, pickerDir, chosenFile -> {
                srcInput.setText(chosenFile.getAbsolutePath());
            });
        });

        AlertDialog[] dialogRef = new AlertDialog[1];
        final boolean[] isSmallerFile = {false};
        final boolean[] isTypeMismatch = {false};
        final boolean[] isSparseImg = {false};
        final SystemImageEngine.ImageIdentity[] diagnosedFileId = new SystemImageEngine.ImageIdentity[1];

        // Unsparse to File action
        btnUnsparseFile.setOnClickListener(v -> {
            String path = srcInput.getText().toString().trim();
            File srcFile = new File(path);
            if (!srcFile.exists()) return;

            String baseName = srcFile.getName();
            int dotIdx = baseName.lastIndexOf('.');
            if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);
            File outRawFile = new File(srcFile.getParentFile(), baseName + "_raw.img");

            ProgressManager unsparsePm = new ProgressManager(activity, false).show();
            unsparsePm.setText(activity.getString(R.string.partition_unsparse_progress, srcFile.getName(), "0%", 0));

            new Thread(() -> {
                try {
                    SystemImageEngine.unsparseToFile(srcFile, outRawFile, new SystemImageEngine.ProgressCallback() {
                        @Override
                        public void onProgress(String message, int percent) {
                            activity.runOnUiThread(() -> {
                                unsparsePm.setText(activity.getString(R.string.partition_unsparse_progress, srcFile.getName(), message, percent));
                                unsparsePm.setProgress(percent, 100);
                            });
                        }

                        @Override
                        public void onError(String error) {
                            activity.runOnUiThread(() -> {
                                unsparsePm.dismiss();
                                Extensions.showMessage(activity, "Error: " + error);
                            });
                        }

                        @Override
                        public void onSuccess(File result) {
                            activity.runOnUiThread(() -> {
                                unsparsePm.dismiss();
                                Toast.makeText(activity, activity.getString(R.string.partition_unsparse_success,
                                        result.getAbsolutePath(), FileSize.getHumanReadableFileSize(result.length())), Toast.LENGTH_LONG).show();
                                srcInput.setText(result.getAbsolutePath());
                            });
                        }
                    });
                } catch (Exception e) {
                    activity.runOnUiThread(() -> {
                        unsparsePm.dismiss();
                        Extensions.showMessage(activity, "Unsparse failed: " + e.getMessage());
                    });
                }
            }).start();
        });

        // Validation runner
        Runnable validate = () -> {
            String path = srcInput.getText().toString().trim();
            isSmallerFile[0] = false;
            isTypeMismatch[0] = false;
            isSparseImg[0] = false;
            diagnosedFileId[0] = null;
            btnUnsparseFile.setVisibility(View.GONE);

            if (TextUtils.isEmpty(path)) {
                statusContainer.setVisibility(View.GONE);
                cbRisk.setVisibility(View.GONE);
                if (dialogRef[0] != null) {
                    MaterialButton btn = (MaterialButton) dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) btn.setEnabled(false);
                }
                return;
            }

            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                statusContainer.setVisibility(View.VISIBLE);
                statusText.setTextColor(0xFFD32F2F);
                statusText.setText(R.string.partition_flash_file_not_found);
                cbRisk.setVisibility(View.GONE);
                if (dialogRef[0] != null) {
                    MaterialButton btn = (MaterialButton) dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) btn.setEnabled(false);
                }
                return;
            }

            if (file.length() == 0) {
                statusContainer.setVisibility(View.VISIBLE);
                statusText.setTextColor(0xFFD32F2F);
                statusText.setText(R.string.partition_flash_empty_file);
                cbRisk.setVisibility(View.GONE);
                if (dialogRef[0] != null) {
                    MaterialButton btn = (MaterialButton) dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) btn.setEnabled(false);
                }
                return;
            }

            // Inspect header identity and sparse info
            SystemImageEngine.ImageIdentity id = SystemImageEngine.identifyImage(file);
            diagnosedFileId[0] = id;
            boolean isSparse = "sparse".equals(id.typeKey);
            isSparseImg[0] = isSparse;
            SystemImageEngine.SparseInfo sparseInfo = isSparse ? SystemImageEngine.readSparseInfo(file) : null;
            long effectiveSize = (sparseInfo != null) ? sparseInfo.uncompressedSizeBytes : file.length();

            // 1. Physical partition capacity check (Exceeded)
            if (item.sizeBytes > 0 && effectiveSize > item.sizeBytes) {
                statusContainer.setVisibility(View.VISIBLE);
                statusText.setTextColor(0xFFD32F2F);
                statusText.setText(activity.getString(R.string.partition_flash_size_exceeded,
                        FileSize.getHumanReadableFileSize(effectiveSize),
                        FileSize.getHumanReadableFileSize(item.sizeBytes)));
                cbRisk.setVisibility(View.GONE);
                if (dialogRef[0] != null) {
                    MaterialButton btn = (MaterialButton) dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) btn.setEnabled(false);
                }
                return;
            }

            // 2. Risk checks: Type mismatch & Smaller file
            boolean typeMismatch = SystemImageEngine.isTypeMismatch(item, id, livePartitionId[0]);
            boolean smallerFile = item.sizeBytes > 0 && effectiveSize < item.sizeBytes;
            isTypeMismatch[0] = typeMismatch;
            isSmallerFile[0] = smallerFile;

            if (typeMismatch || smallerFile) {
                statusContainer.setVisibility(View.VISIBLE);
                statusText.setTextColor(0xFFD32F2F);

                StringBuilder sb = new StringBuilder();
                if (typeMismatch) {
                    sb.append(activity.getString(R.string.partition_flash_type_mismatch_banner,
                            id.getDisplayName(isAr),
                            item.friendlyName,
                            item.rawName));
                    if (smallerFile) {
                        sb.append("\n\n• ").append(activity.getString(R.string.partition_flash_size_too_small,
                                FileSize.getHumanReadableFileSize(effectiveSize),
                                FileSize.getHumanReadableFileSize(item.sizeBytes)));
                    }
                } else {
                    sb.append(activity.getString(R.string.partition_flash_size_too_small,
                            FileSize.getHumanReadableFileSize(effectiveSize),
                            FileSize.getHumanReadableFileSize(item.sizeBytes)));
                    sb.append("\n• ").append(activity.getString(R.string.partition_detected_type, id.getDisplayName(isAr)));
                }

                if (isSparse) {
                    sb.append("\n\n").append(activity.getString(R.string.partition_flash_sparse_prompt,
                            FileSize.getHumanReadableFileSize(effectiveSize)));
                    btnUnsparseFile.setVisibility(View.VISIBLE);
                }

                statusText.setText(sb.toString());

                cbRisk.setText(typeMismatch ? R.string.partition_flash_type_mismatch_ack : R.string.partition_flash_force_small_ack);
                cbRisk.setVisibility(View.VISIBLE);

                if (dialogRef[0] != null) {
                    MaterialButton btn = (MaterialButton) dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                    if (btn != null) btn.setEnabled(cbRisk.isChecked());
                }
                return;
            }

            // 3. Exact 1:1 Match or unconstrained dynamic partition
            statusContainer.setVisibility(View.VISIBLE);
            statusText.setTextColor(0xFF2E7D32);

            StringBuilder matchSb = new StringBuilder();
            if (item.sizeBytes > 0 && effectiveSize == item.sizeBytes) {
                matchSb.append(activity.getString(R.string.partition_flash_exact_match,
                        FileSize.getHumanReadableFileSize(effectiveSize)));
            } else {
                matchSb.append(activity.getString(R.string.partition_flash_match_ok,
                        FileSize.getHumanReadableFileSize(effectiveSize)));
            }
            matchSb.append("\n• ").append(activity.getString(R.string.partition_detected_type, id.getDisplayName(isAr)));

            if (isSparse) {
                matchSb.append("\n\n").append(activity.getString(R.string.partition_flash_sparse_prompt,
                        FileSize.getHumanReadableFileSize(effectiveSize)));
                btnUnsparseFile.setVisibility(View.VISIBLE);
            }

            statusText.setText(matchSb.toString());
            cbRisk.setVisibility(View.GONE);

            if (dialogRef[0] != null) {
                MaterialButton btn = (MaterialButton) dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                if (btn != null) btn.setEnabled(true);
            }
        };
        validateRef[0] = validate;

        srcInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validate.run();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        cbRisk.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (dialogRef[0] != null) {
                MaterialButton btn = (MaterialButton) dialogRef[0].getButton(AlertDialog.BUTTON_POSITIVE);
                if (btn != null) btn.setEnabled(isChecked);
            }
        });

        dialogRef[0] = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.partition_flash_dialog_title, item.rawName))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.partition_flash_btn, (d, w) -> {
                    String path = srcInput.getText().toString().trim();
                    File sourceFile = new File(path);
                    if (!sourceFile.exists()) return;

                    SystemImageEngine.SparseInfo sInfo = isSparseImg[0] ? SystemImageEngine.readSparseInfo(sourceFile) : null;
                    long effSize = (sInfo != null) ? sInfo.uncompressedSizeBytes : sourceFile.length();

                    // If type mismatch or smaller file -> show High-Danger Confirmation Alert
                    if (isTypeMismatch[0] || (isSmallerFile[0] && item.sizeBytes > 0 && effSize < item.sizeBytes)) {
                        String dangerMsg;
                        if (isTypeMismatch[0]) {
                            SystemImageEngine.ImageIdentity fId = diagnosedFileId[0];
                            String typeName = (fId != null) ? fId.getDisplayName(isAr) : "—";
                            dangerMsg = activity.getString(R.string.partition_flash_type_mismatch_confirm,
                                    typeName,
                                    item.friendlyName + " (" + item.rawName + ")",
                                    item.friendlyName,
                                    item.blockPath);
                            if (isSmallerFile[0] && item.sizeBytes > 0 && effSize < item.sizeBytes) {
                                long diff = item.sizeBytes - effSize;
                                dangerMsg += "\n\n⚠️ " + activity.getString(R.string.partition_flash_danger_confirm_msg,
                                        FileSize.getHumanReadableFileSize(effSize),
                                        FileSize.getHumanReadableFileSize(item.sizeBytes),
                                        FileSize.getHumanReadableFileSize(diff),
                                        item.friendlyName,
                                        item.blockPath);
                            }
                        } else {
                            long diff = item.sizeBytes - effSize;
                            dangerMsg = activity.getString(R.string.partition_flash_danger_confirm_msg,
                                    FileSize.getHumanReadableFileSize(effSize),
                                    FileSize.getHumanReadableFileSize(item.sizeBytes),
                                    FileSize.getHumanReadableFileSize(diff),
                                    item.friendlyName,
                                    item.blockPath);
                        }

                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.partition_flash_danger_title)
                                .setMessage(dangerMsg)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.partition_flash_force_btn, (confD, confW) -> {
                                    executePartitionFlash(activity, item, sourceFile, isSparseImg[0]);
                                })
                                .show();
                    } else {
                        // Standard confirmation dialog
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(R.string.partition_flash_confirm_title)
                                .setMessage(activity.getString(R.string.partition_flash_confirm_msg,
                                        sourceFile.getAbsolutePath() + " (" + FileSize.getHumanReadableFileSize(effSize) + ")",
                                        item.friendlyName,
                                        item.blockPath))
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton(R.string.partition_flash_btn, (confD, confW) -> {
                                    executePartitionFlash(activity, item, sourceFile, isSparseImg[0]);
                                })
                                .show();
                    }
                })
                .show();

        DialogUtil.keepButtonsHorizontal(dialogRef[0]);

        // Initial validation
        validate.run();
    }

    private static void executePartitionFlash(AppCompatActivity activity, PartitionItem item, File sourceFile, boolean isSparse) {
        ProgressManager pm = new ProgressManager(activity, false).show();
        pm.setText(activity.getString(R.string.partition_flashing_progress, item.rawName, "0 MB",
                FileSize.getHumanReadableFileSize(sourceFile.length()), "0 MB/s"));

        new Thread(() -> {
            Process p = null;
            long written = 0;
            long total = sourceFile.length();
            long startTime = System.currentTimeMillis();
            try {
                // Ensure target blockdev is read-write
                try {
                    Process rwProc = Runtime.getRuntime().exec(new String[]{"su", "-c", "blockdev --setrw '" + item.blockPath + "'"});
                    rwProc.waitFor();
                } catch (Exception ignored) {}

                // Stream into dd via root
                String cmd = "dd of='" + item.blockPath + "' bs=1048576";
                p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});

                if (isSparse) {
                    try (BufferedOutputStream os = new BufferedOutputStream(p.getOutputStream(), 1024 * 1024)) {
                        SystemImageEngine.unsparseToStream(sourceFile, os, new SystemImageEngine.ProgressCallback() {
                            @Override
                            public void onProgress(String message, int percent) {
                                activity.runOnUiThread(() -> {
                                    pm.setText(activity.getString(R.string.partition_flashing_progress, item.rawName, message, "", ""));
                                    pm.setProgress(percent, 100);
                                });
                            }

                            @Override
                            public void onError(String error) {}

                            @Override
                            public void onSuccess(File result) {}
                        });
                        os.flush();
                    }
                } else {
                    try (FileInputStream is = new FileInputStream(sourceFile);
                         BufferedOutputStream os = new BufferedOutputStream(p.getOutputStream(), 1024 * 1024)) {

                        byte[] buffer = new byte[1024 * 1024]; // 1MB buffer
                        int read;
                        long lastUpdate = 0;

                        while ((read = is.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                            written += read;

                            long now = System.currentTimeMillis();
                            if (now - lastUpdate > 300) {
                                lastUpdate = now;
                                long elapsedSec = Math.max(1, (now - startTime) / 1000);
                                long speedBps = written / elapsedSec;
                                String speedStr = FileSize.getHumanReadableFileSize(speedBps) + "/s";
                                String writtenStr = FileSize.getHumanReadableFileSize(written);
                                String totalStr = FileSize.getHumanReadableFileSize(total);

                                final long currentWritten = written;
                                activity.runOnUiThread(() -> {
                                    pm.setText(activity.getString(R.string.partition_flashing_progress,
                                            item.rawName, writtenStr, totalStr, speedStr));
                                    if (total > 0) {
                                        pm.setProgress((int) (currentWritten / (1024 * 1024)), (int) (total / (1024 * 1024)));
                                    }
                                });
                            }
                        }
                        os.flush();
                    }
                }

                p.waitFor();

                // Force sync dirty blocks to hardware flash memory
                try {
                    Process syncProc = Runtime.getRuntime().exec(new String[]{"su", "-c", "sync"});
                    syncProc.waitFor();
                } catch (Exception ignored) {}

                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    if (activity instanceof MainActivity ma) {
                        ma.reloadCurrentFolder();
                    }
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.title_success)
                            .setMessage(activity.getString(R.string.partition_flash_success, item.friendlyName))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });

            } catch (Exception e) {
                if (p != null) p.destroy();
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(activity, "Flashing failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void showImgFilePicker(AppCompatActivity activity, File initialDir, Consumer<File> onFileSelected) {
        final File[] currentDir = {initialDir != null && initialDir.isDirectory() ? initialDir : Environment.getExternalStorageDirectory()};
        AlertDialog[] pickerDialog = new AlertDialog[1];

        Runnable buildList = new Runnable() {
            @Override
            public void run() {
                File dir = currentDir[0];
                List<String> names = new ArrayList<>();
                List<File> fileTargets = new ArrayList<>();

                if (dir.getParentFile() != null && dir.getParentFile().canRead()) {
                    names.add("📁 .. (Up)");
                    fileTargets.add(dir.getParentFile());
                }

                File[] entries = dir.listFiles();
                if (entries != null) {
                    List<File> dirs = new ArrayList<>();
                    List<File> imgs = new ArrayList<>();
                    for (File f : entries) {
                        if (f.isDirectory()) dirs.add(f);
                        else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".img")) imgs.add(f);
                    }
                    Collections.sort(dirs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    Collections.sort(imgs, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                    for (File d : dirs) {
                        names.add("📁 " + d.getName() + "/");
                        fileTargets.add(d);
                    }
                    for (File img : imgs) {
                        names.add("⚡ " + img.getName() + " (" + FileSize.getHumanReadableFileSize(img.length()) + ")");
                        fileTargets.add(img);
                    }
                }

                MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(activity)
                        .setTitle(dir.getAbsolutePath())
                        .setItems(names.toArray(new String[0]), (dialog, which) -> {
                            File target = fileTargets.get(which);
                            if (target.isDirectory()) {
                                currentDir[0] = target;
                                run();
                            } else {
                                if (onFileSelected != null) {
                                    onFileSelected.accept(target);
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null);

                if (pickerDialog[0] != null) pickerDialog[0].dismiss();
                pickerDialog[0] = b.show();
                DialogUtil.keepButtonsHorizontal(pickerDialog[0]);
            }
        };

        buildList.run();
    }
}

package io.github.abdurazaaqmohammed.features.dumper;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.os.StatFs;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

/**
 * Universal Android Partition Dumper dialog with A/B slot switching in the header
 * and instant visual indicator for differences between Slot A and Slot B.
 */
public final class PartitionDumperDialog {

    private PartitionDumperDialog() {
    }

    private static List<PartitionItem> sCachedPartitions;
    private static String sCachedActiveSlot;
    private static final String PREF_WARNED_DUMPER = "pref_warned_dumper_danger";

    public static void clearCache() {
        sCachedPartitions = null;
        sCachedActiveSlot = null;
    }

    public static void show(AppCompatActivity activity) {
        if (activity == null || activity.isFinishing()) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        if (!prefs.getBoolean(PREF_WARNED_DUMPER, false)) {
            View view = LayoutInflater.from(activity).inflate(R.layout.dialog_safety_warning, null, false);
            TextView tvMsg = view.findViewById(R.id.tvWarningMessage);
            CheckBox cbDontShow = view.findViewById(R.id.cbDontShowAgain);
            tvMsg.setText(R.string.dumper_open_warning);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(R.string.safety_warning_title)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.warning_proceed, (d, w) -> {
                        if (cbDontShow.isChecked()) {
                            prefs.edit().putBoolean(PREF_WARNED_DUMPER, true).apply();
                        }
                        loadAndDisplay(activity);
                    })
                    .show();
            return;
        }
        loadAndDisplay(activity);
    }

    private static void loadAndDisplay(AppCompatActivity activity) {

        if (sCachedPartitions != null && !sCachedPartitions.isEmpty()) {
            displayMainDialog(activity, sCachedPartitions, sCachedActiveSlot);
            return;
        }

        ProgressManager pm = new ProgressManager(activity, false).show();
        pm.setText(activity.getString(R.string.tools_hub_loading));

        new Thread(() -> {
            try {
                List<PartitionItem> partitions = PartitionResolver.queryPartitions(activity);
                String activeSlot = "";
                for (PartitionItem p : partitions) {
                    if (p.isActiveSlot) {
                        if (p.slot == PartitionItem.Slot.A) activeSlot = "a";
                        else if (p.slot == PartitionItem.Slot.B) activeSlot = "b";
                        break;
                    }
                }
                if (activeSlot.isEmpty()) {
                    activeSlot = PartitionResolver.getActiveSlot();
                }
                sCachedPartitions = partitions;
                sCachedActiveSlot = activeSlot;
                final String finalActiveSlot = activeSlot;
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    if (partitions.isEmpty()) {
                        Extensions.showMessage(activity, activity.getString(R.string.partition_root_required));
                        return;
                    }
                    displayMainDialog(activity, partitions, finalActiveSlot);
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    Extensions.showMessage(activity, "Error discovering partitions: " + e.getMessage());
                });
            }
        }).start();
    }

    public static class GroupedPartition {
        public final String baseName;
        public PartitionItem slotA;
        public PartitionItem slotB;
        public PartitionItem shared;

        public GroupedPartition(String baseName) {
            this.baseName = baseName;
        }

        public boolean hasBothSlots() {
            return slotA != null && slotB != null;
        }

        public String getSlotText(boolean isAr) {
            if (hasBothSlots()) {
                return isAr ? "السلوت: A & B" : "Slot: A & B";
            } else if (slotA != null) {
                return isAr ? "السلوت: A" : "Slot: A";
            } else if (slotB != null) {
                return isAr ? "السلوت: B" : "Slot: B";
            } else if (shared != null) {
                if (shared.sizeBytes > 0) {
                    return FileSize.getHumanReadableFileSize(shared.sizeBytes);
                }
                return isAr ? "قسم عام" : "Shared";
            }
            return isAr ? "عام" : "Shared";
        }

        public PartitionItem getActiveSlotItem(String activeSlot) {
            if ("b".equalsIgnoreCase(activeSlot) && slotB != null) return slotB;
            if (slotA != null) return slotA;
            if (slotB != null) return slotB;
            return shared;
        }

        public PartitionItem getOtherSlotItem(String activeSlot) {
            if ("b".equalsIgnoreCase(activeSlot) && slotA != null) return slotA;
            if (slotB != null) return slotB;
            return slotA != null ? slotA : shared;
        }

        public PartitionItem getPrimaryItem() {
            if (slotA != null) return slotA;
            if (slotB != null) return slotB;
            return shared;
        }
    }

    private static void displayMainDialog(AppCompatActivity activity, List<PartitionItem> allPartitions,
                                          String activeSlot) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");

        // Group partitions by base name
        Map<String, GroupedPartition> map = new LinkedHashMap<>();
        for (PartitionItem p : allPartitions) {
            String raw = p.rawName;
            String base = raw;
            if (base.endsWith("_a") || base.endsWith("_b")) {
                base = base.substring(0, base.length() - 2);
            }
            GroupedPartition gp = map.computeIfAbsent(base, GroupedPartition::new);
            if (p.slot == PartitionItem.Slot.A) gp.slotA = p;
            else if (p.slot == PartitionItem.Slot.B) gp.slotB = p;
            else gp.shared = p;
        }

        // Whitelist for primary partitions matching the image
        List<String> PRIMARY_WHITELIST = List.of(
                "init_boot",
                "boot",
                "vendor_boot",
                "recovery",
                "dtbo"
        );

        List<GroupedPartition> primaryGrouped = new ArrayList<>();
        List<GroupedPartition> moreGrouped = new ArrayList<>();

        for (String key : PRIMARY_WHITELIST) {
            GroupedPartition gp = map.remove(key);
            if (gp != null) {
                primaryGrouped.add(gp);
            }
        }

        moreGrouped.addAll(map.values());
        moreGrouped.sort((a, b) -> a.baseName.compareToIgnoreCase(b.baseName));

        // Create Fullscreen Dialog
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF11141B);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Top Toolbar
        int statusBarHeight = 0;
        int resId = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            statusBarHeight = activity.getResources().getDimensionPixelSize(resId);
        }
        if (statusBarHeight <= 0) {
            statusBarHeight = Extensions.dp2px(activity, 32);
        }
        int topBarPaddingTop = statusBarHeight + Extensions.dp2px(activity, 10);

        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Extensions.dp2px(activity, 16), topBarPaddingTop,
                Extensions.dp2px(activity, 16), Extensions.dp2px(activity, 10));

        ImageView btnClose = new ImageView(activity);
        int closeSize = Extensions.dp2px(activity, 32);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(closeSize, closeSize);
        closeLp.setMarginEnd(Extensions.dp2px(activity, 14));
        btnClose.setLayoutParams(closeLp);
        btnClose.setImageResource(R.drawable.ic_close);
        btnClose.setColorFilter(0xFFFFFFFF);
        int cPad = Extensions.dp2px(activity, 4);
        btnClose.setPadding(cPad, cPad, cPad, cPad);
        android.util.TypedValue outValBg = new android.util.TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValBg, true)) {
            btnClose.setBackgroundResource(outValBg.resourceId);
        }
        btnClose.setOnClickListener(v -> dialog.dismiss());
        topBar.addView(btnClose);

        LinearLayout titleCol = new LinearLayout(activity);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = new TextView(activity);
        tvTitle.setText(isAr ? "بارتيشن النظام" : "System Partition");
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(tvTitle);

        TextView tvSubtitle = new TextView(activity);
        tvSubtitle.setText(isAr ? "نسخ احتياطي / تفليش الأقسام والكيرنل" : "Backup / flash partitions & kernel");
        tvSubtitle.setTextColor(0xFF8A96A0);
        tvSubtitle.setTextSize(13f);
        tvSubtitle.setPadding(0, Extensions.dp2px(activity, 2), 0, 0);
        titleCol.addView(tvSubtitle);

        topBar.addView(titleCol);

        ImageView btnRefresh = new ImageView(activity);
        btnRefresh.setLayoutParams(new LinearLayout.LayoutParams(closeSize, closeSize));
        btnRefresh.setImageResource(R.drawable.baseline_refresh_24);
        btnRefresh.setColorFilter(0xFFFFFFFF);
        btnRefresh.setPadding(cPad, cPad, cPad, cPad);
        if (outValBg.resourceId != 0) btnRefresh.setBackgroundResource(outValBg.resourceId);
        btnRefresh.setOnClickListener(v -> {
            clearCache();
            dialog.dismiss();
            show(activity);
        });
        topBar.addView(btnRefresh);

        root.addView(topBar);

        // Scrollable content
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(scrollLp);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padH = Extensions.dp2px(activity, 16);
        content.setPadding(padH, Extensions.dp2px(activity, 8), padH, Extensions.dp2px(activity, 24));

        // 📱 Device Info Card
        content.addView(createDeviceInfoCard(activity, activeSlot, isAr));

        // Section: "Available Partitions"
        TextView secTitle = new TextView(activity);
        secTitle.setText(isAr ? "الأقسام المتاحة" : "Available Partitions");
        secTitle.setTextColor(0xFFFFFFFF);
        secTitle.setTextSize(16f);
        secTitle.setTypeface(null, Typeface.BOLD);
        secTitle.setPadding(0, Extensions.dp2px(activity, 8), 0, Extensions.dp2px(activity, 12));
        content.addView(secTitle);

        // Primary Cards (Rendered immediately: usually ~5 cards)
        for (GroupedPartition gp : primaryGrouped) {
            content.addView(createPartitionCard(activity, gp, activeSlot, isAr, dialog));
        }

        // More Expandable Section (Lazy loaded on click)
        if (!moreGrouped.isEmpty()) {
            LinearLayout moreCard = new LinearLayout(activity);
            moreCard.setOrientation(LinearLayout.HORIZONTAL);
            moreCard.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable moreBg = new GradientDrawable();
            moreBg.setColor(0xFF1A202C);
            moreBg.setCornerRadius(Extensions.dp2px(activity, 16));
            moreCard.setBackground(moreBg);
            int mPadH = Extensions.dp2px(activity, 16);
            int mPadV = Extensions.dp2px(activity, 14);
            moreCard.setPadding(mPadH, mPadV, mPadH, mPadV);
            LinearLayout.LayoutParams moreCardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            moreCardLp.setMargins(0, Extensions.dp2px(activity, 6), 0, Extensions.dp2px(activity, 6));
            moreCard.setLayoutParams(moreCardLp);

            LinearLayout moreTextCol = new LinearLayout(activity);
            moreTextCol.setOrientation(LinearLayout.VERTICAL);
            moreTextCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvMoreTitle = new TextView(activity);
            tvMoreTitle.setText(isAr ? "المزيد (" + moreGrouped.size() + " بارتيشن)" : "More (" + moreGrouped.size() + " partitions)");
            tvMoreTitle.setTextColor(0xFFFFFFFF);
            tvMoreTitle.setTextSize(15f);
            tvMoreTitle.setTypeface(null, Typeface.BOLD);
            moreTextCol.addView(tvMoreTitle);

            TextView tvMoreSub = new TextView(activity);
            tvMoreSub.setText(isAr ? "اضغط لعرض أو إخفاء باقي أقسام النظام والفيرموير" : "Tap to expand/collapse remaining partitions");
            tvMoreSub.setTextColor(0xFF7E8E9E);
            tvMoreSub.setTextSize(12f);
            tvMoreSub.setPadding(0, Extensions.dp2px(activity, 2), 0, 0);
            moreTextCol.addView(tvMoreSub);

            moreCard.addView(moreTextCol);

            TextView tvChevron = new TextView(activity);
            tvChevron.setText("▼");
            tvChevron.setTextColor(0xFF8A98A8);
            tvChevron.setTextSize(16f);
            moreCard.addView(tvChevron);

            content.addView(moreCard);

            // Container for all other partitions
            LinearLayout moreContainer = new LinearLayout(activity);
            moreContainer.setOrientation(LinearLayout.VERTICAL);
            moreContainer.setVisibility(View.GONE);

            // State tracking for lazy population
            final boolean[] isMoreInflated = new boolean[]{false};

            // Toggle Expand / Collapse with Lazy Loading
            moreCard.setOnClickListener(v -> {
                if (moreContainer.getVisibility() == View.GONE) {
                    if (!isMoreInflated[0]) {
                        isMoreInflated[0] = true;

                        // First row: Search Bar for More
                        LinearLayout searchCard = new LinearLayout(activity);
                        searchCard.setOrientation(LinearLayout.HORIZONTAL);
                        searchCard.setGravity(Gravity.CENTER_VERTICAL);
                        GradientDrawable searchBg = new GradientDrawable();
                        searchBg.setColor(0xFF1E2533);
                        searchBg.setCornerRadius(Extensions.dp2px(activity, 14));
                        searchBg.setStroke(Extensions.dp2px(activity, 1), 0xFF2D3748);
                        searchCard.setBackground(searchBg);
                        searchCard.setPadding(Extensions.dp2px(activity, 14), 0, Extensions.dp2px(activity, 12), 0);
                        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, Extensions.dp2px(activity, 46));
                        searchLp.setMargins(0, Extensions.dp2px(activity, 4), 0, Extensions.dp2px(activity, 10));
                        searchCard.setLayoutParams(searchLp);

                        TextView tvSearchIcon = new TextView(activity);
                        tvSearchIcon.setText("🔍");
                        tvSearchIcon.setTextSize(14f);
                        searchCard.addView(tvSearchIcon);

                        EditText etMoreSearch = new EditText(activity);
                        etMoreSearch.setHint(isAr ? "بحث في باقي الأقسام..." : "Search partitions...");
                        etMoreSearch.setHintTextColor(0xFF718096);
                        etMoreSearch.setTextColor(0xFFFFFFFF);
                        etMoreSearch.setTextSize(13.5f);
                        etMoreSearch.setBackground(null);
                        etMoreSearch.setSingleLine(true);
                        etMoreSearch.setPadding(Extensions.dp2px(activity, 10), 0, Extensions.dp2px(activity, 6), 0);
                        etMoreSearch.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        searchCard.addView(etMoreSearch);

                        TextView btnClearSearch = new TextView(activity);
                        btnClearSearch.setText("✕");
                        btnClearSearch.setTextColor(0xFFA0AEC0);
                        btnClearSearch.setTextSize(14f);
                        btnClearSearch.setPadding(Extensions.dp2px(activity, 6), Extensions.dp2px(activity, 6),
                                Extensions.dp2px(activity, 6), Extensions.dp2px(activity, 6));
                        btnClearSearch.setVisibility(View.GONE);
                        btnClearSearch.setOnClickListener(v2 -> etMoreSearch.setText(""));
                        searchCard.addView(btnClearSearch);

                        moreContainer.addView(searchCard);

                        for (GroupedPartition gp : moreGrouped) {
                            View card = createPartitionCard(activity, gp, activeSlot, isAr, dialog);
                            card.setTag(gp.baseName.toLowerCase(Locale.ROOT));
                            moreContainer.addView(card);
                        }

                        TextView tvMoreEmpty = new TextView(activity);
                        tvMoreEmpty.setText(isAr ? "لا توجد أقسام مطابقة للبحث" : "No matching partitions found");
                        tvMoreEmpty.setTextColor(0xFF718096);
                        tvMoreEmpty.setTextSize(13f);
                        tvMoreEmpty.setGravity(Gravity.CENTER);
                        tvMoreEmpty.setPadding(0, Extensions.dp2px(activity, 18), 0, Extensions.dp2px(activity, 18));
                        tvMoreEmpty.setVisibility(View.GONE);
                        moreContainer.addView(tvMoreEmpty);

                        etMoreSearch.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                                String q = (s != null) ? s.toString().trim().toLowerCase(Locale.ROOT) : "";
                                btnClearSearch.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                                int visibleCount = 0;
                                for (int i = 0; i < moreContainer.getChildCount(); i++) {
                                    View child = moreContainer.getChildAt(i);
                                    Object tag = child.getTag();
                                    if (tag instanceof String name) {
                                        boolean match = q.isEmpty() || name.contains(q);
                                        child.setVisibility(match ? View.VISIBLE : View.GONE);
                                        if (match) visibleCount++;
                                    }
                                }
                                tvMoreEmpty.setVisibility(visibleCount == 0 ? View.VISIBLE : View.GONE);
                            }

                            @Override
                            public void afterTextChanged(Editable s) {}
                        });
                    }

                    moreContainer.setVisibility(View.VISIBLE);
                    tvChevron.setText("▲");
                    tvMoreSub.setText(isAr ? "اضغط لإخفاء الأقسام الإضافية" : "Tap to collapse remaining partitions");
                } else {
                    moreContainer.setVisibility(View.GONE);
                    tvChevron.setText("▼");
                    tvMoreSub.setText(isAr ? "اضغط لعرض أو إخفاء باقي أقسام النظام والفيرموير" : "Tap to expand/collapse remaining partitions");
                }
            });

            content.addView(moreContainer);
        }

        scrollView.addView(content);
        root.addView(scrollView);

        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(0xFF11141B));
            w.setStatusBarColor(0xFF11141B);
            w.setNavigationBarColor(0xFF11141B);
        }
        dialog.show();
    }

    private static View createDeviceInfoCard(Context context, String activeSlot, boolean isAr) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A202C);
        bg.setCornerRadius(Extensions.dp2px(context, 16));
        card.setBackground(bg);
        int pad = Extensions.dp2px(context, 16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, Extensions.dp2px(context, 4), 0, Extensions.dp2px(context, 14));
        card.setLayoutParams(lp);

        TextView tvHeader = new TextView(context);
        tvHeader.setText(isAr ? "📱 معلومات الجهاز" : "📱 Device Info");
        tvHeader.setTextColor(0xFFFFFFFF);
        tvHeader.setTextSize(15f);
        tvHeader.setTypeface(null, Typeface.BOLD);
        card.addView(tvHeader);

        TextView tvDevice = new TextView(context);
        String devName = android.os.Build.MODEL + " (" + android.os.Build.DEVICE + ")";
        tvDevice.setText((isAr ? "الجهاز: " : "Device: ") + devName);
        tvDevice.setTextColor(0xFFB0BEC5);
        tvDevice.setTextSize(13f);
        tvDevice.setPadding(0, Extensions.dp2px(context, 6), 0, 0);
        card.addView(tvDevice);

        LinearLayout slotLine = new LinearLayout(context);
        slotLine.setOrientation(LinearLayout.HORIZONTAL);
        slotLine.setPadding(0, Extensions.dp2px(context, 4), 0, 0);

        TextView tvSlotLabel = new TextView(context);
        tvSlotLabel.setText(isAr ? "السلوت النشط: " : "Active Slot: ");
        tvSlotLabel.setTextColor(0xFFB0BEC5);
        tvSlotLabel.setTextSize(13f);
        slotLine.addView(tvSlotLabel);

        TextView tvSlotVal = new TextView(context);
        String slotDisplay = (!TextUtils.isEmpty(activeSlot)) ? activeSlot.toUpperCase(Locale.ROOT) : (isAr ? "غير محدد" : "N/A");
        tvSlotVal.setText(slotDisplay);
        tvSlotVal.setTextColor(0xFF4CAF50); // Bright Green
        tvSlotVal.setTypeface(null, Typeface.BOLD);
        tvSlotVal.setTextSize(13f);
        slotLine.addView(tvSlotVal);

        card.addView(slotLine);
        return card;
    }

    private static View createPartitionCard(AppCompatActivity activity, GroupedPartition gp,
                                           String activeSlot, boolean isAr, Dialog parentDialog) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1A202C);
        bg.setCornerRadius(Extensions.dp2px(activity, 16));
        card.setBackground(bg);
        int padH = Extensions.dp2px(activity, 16);
        int padV = Extensions.dp2px(activity, 13);
        card.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, Extensions.dp2px(activity, 10));
        card.setLayoutParams(cardLp);

        // Green Indicator Dot
        View dot = new View(activity);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFF4CAF50);
        dot.setBackground(dotBg);
        int dotSize = Extensions.dp2px(activity, 10);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotLp.setMarginEnd(Extensions.dp2px(activity, 12));
        dot.setLayoutParams(dotLp);
        card.addView(dot);

        // Partition Name and Slot Subtitle
        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(activity);
        tvName.setText(gp.baseName);
        tvName.setTextColor(0xFFFFFFFF);
        tvName.setTextSize(15f);
        tvName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvName.setSingleLine(true);
        tvName.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(tvName);

        TextView tvSub = new TextView(activity);
        tvSub.setText(gp.getSlotText(isAr));
        tvSub.setTextColor(0xFF7E8E9E);
        tvSub.setTextSize(12f);
        tvSub.setPadding(0, Extensions.dp2px(activity, 2), 0, 0);
        col.addView(tvSub);

        card.addView(col);

        // Right Action Buttons
        LinearLayout btnContainer = new LinearLayout(activity);
        btnContainer.setOrientation(LinearLayout.HORIZONTAL);
        btnContainer.setGravity(Gravity.CENTER_VERTICAL);

        // [ Backup ] Button
        TextView btnBackup = new TextView(activity);
        btnBackup.setText(isAr ? "نسخ احتياطي" : "Backup");
        btnBackup.setTextColor(0xFFFFFFFF);
        btnBackup.setTextSize(12f);
        btnBackup.setTypeface(null, Typeface.BOLD);
        GradientDrawable backupBg = new GradientDrawable();
        backupBg.setColor(0xFF303845);
        backupBg.setCornerRadius(Extensions.dp2px(activity, 20));
        btnBackup.setBackground(backupBg);
        int bPadH = Extensions.dp2px(activity, 14);
        int bPadV = Extensions.dp2px(activity, 7);
        btnBackup.setPadding(bPadH, bPadV, bPadH, bPadV);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bLp.setMarginEnd(Extensions.dp2px(activity, 8));
        btnBackup.setLayoutParams(bLp);
        btnBackup.setOnClickListener(v -> onBackupClicked(activity, gp, activeSlot, isAr));
        btnContainer.addView(btnBackup);

        // [ Flash ] Button
        TextView btnFlash = new TextView(activity);
        btnFlash.setText(isAr ? "تفليش" : "Flash");
        btnFlash.setTextColor(0xFFFF8A80);
        btnFlash.setTextSize(12f);
        btnFlash.setTypeface(null, Typeface.BOLD);
        GradientDrawable flashBg = new GradientDrawable();
        flashBg.setColor(0xFF4A2328);
        flashBg.setCornerRadius(Extensions.dp2px(activity, 20));
        btnFlash.setBackground(flashBg);
        btnFlash.setPadding(bPadH, bPadV, bPadH, bPadV);
        btnFlash.setOnClickListener(v -> onFlashClicked(activity, gp, activeSlot, isAr));
        btnContainer.addView(btnFlash);

        card.addView(btnContainer);

        // Clicking the card itself shows details dialog
        card.setOnClickListener(v -> {
            PartitionItem target = gp.getActiveSlotItem(activeSlot);
            if (target != null) showPartitionInfoDialog(activity, target);
        });

        return card;
    }

    private interface SlotChoiceListener {
        void onChoice(int choice); // 0=Slot A, 1=Slot B, 2=Both
    }

    private static void showSlotSelectionDialog(AppCompatActivity activity, String actionTitle,
                                                String partitionName, boolean allowBoth, boolean isAr,
                                                SlotChoiceListener listener) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF222834);
        bg.setCornerRadius(Extensions.dp2px(activity, 20));
        layout.setBackground(bg);
        int pad = Extensions.dp2px(activity, 20);
        layout.setPadding(pad, pad, pad, pad);

        // Title: e.g. Backup — init_boot
        TextView tvTitle = new TextView(activity);
        tvTitle.setText(actionTitle + " — " + partitionName);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        layout.addView(tvTitle);

        // Subtitle: Select Slot
        TextView tvSub = new TextView(activity);
        tvSub.setText(isAr ? "اختر السلوت" : "Select Slot");
        tvSub.setTextColor(0xFF8E9DAE);
        tvSub.setTextSize(13f);
        tvSub.setPadding(0, Extensions.dp2px(activity, 4), 0, Extensions.dp2px(activity, 16));
        layout.addView(tvSub);

        // Slot buttons builder
        View.OnClickListener btnClick = v -> {
            dialog.dismiss();
            int choice = (int) v.getTag();
            if (listener != null) listener.onChoice(choice);
        };

        layout.addView(createSlotCardButton(activity, isAr ? "السلوت A" : "Slot A", 0, btnClick));
        layout.addView(createSlotCardButton(activity, isAr ? "السلوت B" : "Slot B", 1, btnClick));

        if (allowBoth) {
            layout.addView(createSlotCardButton(activity, isAr ? "كلا السلوتين (A & B)" : "Both Slots (A & B)", 2, btnClick));
        }

        // Cancel button
        LinearLayout cancelRow = new LinearLayout(activity);
        cancelRow.setOrientation(LinearLayout.HORIZONTAL);
        cancelRow.setGravity(Gravity.END);
        cancelRow.setPadding(0, Extensions.dp2px(activity, 8), 0, 0);

        TextView btnCancel = new TextView(activity);
        btnCancel.setText(isAr ? "إلغاء" : "Cancel");
        btnCancel.setTextColor(0xFFB0BEC5);
        btnCancel.setTextSize(14f);
        btnCancel.setTypeface(null, Typeface.BOLD);
        btnCancel.setPadding(Extensions.dp2px(activity, 12), Extensions.dp2px(activity, 8),
                Extensions.dp2px(activity, 12), Extensions.dp2px(activity, 8));
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        cancelRow.addView(btnCancel);

        layout.addView(cancelRow);

        dialog.setContentView(layout);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.90);
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private static View createSlotCardButton(Context context, String text, int tag, View.OnClickListener onClick) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(15f);
        btn.setTypeface(null, Typeface.NORMAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A3140);
        bg.setCornerRadius(Extensions.dp2px(context, 12));
        btn.setBackground(bg);
        int padH = Extensions.dp2px(context, 16);
        int padV = Extensions.dp2px(context, 14);
        btn.setPadding(padH, padV, padH, padV);
        btn.setTag(tag);
        btn.setOnClickListener(onClick);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, Extensions.dp2px(context, 10));
        btn.setLayoutParams(lp);
        return btn;
    }

    private static void onBackupClicked(AppCompatActivity activity, GroupedPartition gp, String activeSlot, boolean isAr) {
        if (gp.hasBothSlots()) {
            showSlotSelectionDialog(activity, isAr ? "نسخ احتياطي" : "Backup", gp.baseName, true, isAr, choice -> {
                if (choice == 0) {
                    showDumpConfirmDialog(activity, gp.slotA != null ? gp.slotA : gp.getPrimaryItem());
                } else if (choice == 1) {
                    showDumpConfirmDialog(activity, gp.slotB != null ? gp.slotB : gp.getPrimaryItem());
                } else {
                    List<PartitionItem> items = new ArrayList<>();
                    if (gp.slotA != null) items.add(gp.slotA);
                    if (gp.slotB != null) items.add(gp.slotB);
                    promptBatchDumpFolder(activity, items, null);
                }
            });
        } else {
            showDumpConfirmDialog(activity, gp.getPrimaryItem());
        }
    }

    private static void onFlashClicked(AppCompatActivity activity, GroupedPartition gp, String activeSlot, boolean isAr) {
        if (gp.hasBothSlots()) {
            showSlotSelectionDialog(activity, isAr ? "تفليش" : "Flash", gp.baseName, true, isAr, choice -> {
                if (choice == 0) {
                    pickFileAndFlash(activity, gp.slotA != null ? gp.slotA : gp.getPrimaryItem());
                } else if (choice == 1) {
                    pickFileAndFlash(activity, gp.slotB != null ? gp.slotB : gp.getPrimaryItem());
                } else {
                    pickFileAndFlashBoth(activity, gp.slotA, gp.slotB);
                }
            });
        } else {
            pickFileAndFlash(activity, gp.getPrimaryItem());
        }
    }

    private static void pickFileAndFlash(AppCompatActivity activity, PartitionItem target) {
        if (activity == null || target == null) return;
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");

        io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.Properties props =
                new io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.Properties();
        props.selection_mode = io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.SINGLE_MODE;
        props.selection_type = io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.FILE_SELECT;
        props.root = Environment.getExternalStorageDirectory();
        props.offset = new File(Environment.getExternalStorageDirectory(), "Download");
        props.extensions = new String[]{"img", "bin"};

        io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog picker =
                new io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog(activity, props);
        picker.setTitle(isAr ? "اختر ملف الصورة (.img)" : "Select Image File (.img)");
        picker.setDialogSelectionListener(files -> {
            if (files != null && files.length > 0) {
                File chosen = new File(files[0]);
                confirmAndExecuteFlash(activity, target, chosen);
            }
        });
        picker.show();
    }

    private static void pickFileAndFlashBoth(AppCompatActivity activity, PartitionItem slotA, PartitionItem slotB) {
        if (activity == null) return;
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");

        io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.Properties props =
                new io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.Properties();
        props.selection_mode = io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.SINGLE_MODE;
        props.selection_type = io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog.FILE_SELECT;
        props.root = Environment.getExternalStorageDirectory();
        props.offset = new File(Environment.getExternalStorageDirectory(), "Download");
        props.extensions = new String[]{"img", "bin"};

        io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog picker =
                new io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog(activity, props);
        picker.setTitle(isAr ? "اختر ملف الصورة للتفليش في كلا السلوتين (.img)" : "Select Image File for Both Slots (.img)");
        picker.setDialogSelectionListener(files -> {
            if (files != null && files.length > 0) {
                File chosen = new File(files[0]);
                confirmAndExecuteFlashBoth(activity, slotA, slotB, chosen);
            }
        });
        picker.show();
    }

    private static void confirmAndExecuteFlash(AppCompatActivity activity, PartitionItem target, File imgFile) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        new MaterialAlertDialogBuilder(activity)
                .setTitle("⚠️ " + (isAr ? "تحذير تفليش البارتيشن" : "Partition Flash Warning"))
                .setMessage((isAr ? "أنت على وشك تفليش الملف التالي إلى البارتيشن:\n\n" : "You are about to flash this image to partition:\n\n")
                        + "• " + (isAr ? "البارتيشن: " : "Partition: ") + target.rawName + "\n"
                        + "• " + (isAr ? "المسار: " : "Block Device: ") + target.blockPath + "\n"
                        + "• " + (isAr ? "ملف الصورة: " : "Image File: ") + imgFile.getAbsolutePath()
                        + " (" + FileSize.getHumanReadableFileSize(imgFile.length()) + ")\n\n"
                        + (isAr ? "⚠️ تحذير: تفليش ملف تالف أو غير متوافق قد يسبب عدم إقلاع الهاتف (Bootloop). هل أنت متأكد من الاستمرار؟"
                        : "⚠️ Warning: Flashing an incompatible or corrupt image can cause a bootloop. Are you sure you want to proceed?"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(isAr ? "تفليش الآن" : "Flash Now", (d, w) -> {
                    executeFlashDirect(activity, target, imgFile);
                })
                .show();
    }

    private static void confirmAndExecuteFlashBoth(AppCompatActivity activity, PartitionItem slotA, PartitionItem slotB, File imgFile) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        new MaterialAlertDialogBuilder(activity)
                .setTitle("⚠️ " + (isAr ? "تحذير تفليش كلا السلوتين" : "Dual Slot Flash Warning"))
                .setMessage((isAr ? "أنت على وشك تفليش الصورة إلى كلا السلوتين (A & B):\n\n" : "You are about to flash this image to BOTH slots (A & B):\n\n")
                        + "• Slot A: " + (slotA != null ? slotA.blockPath : "N/A") + "\n"
                        + "• Slot B: " + (slotB != null ? slotB.blockPath : "N/A") + "\n"
                        + "• " + (isAr ? "ملف الصورة: " : "Image File: ") + imgFile.getAbsolutePath() + "\n\n"
                        + (isAr ? "⚠️ تحذير: هذه العملية لا يمكن التراجع عنها. هل أنت متأكد؟"
                        : "⚠️ Warning: This operation cannot be undone. Are you sure?"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(isAr ? "تفليش السلوتين" : "Flash Both", (d, w) -> {
                    executeFlashDirectDual(activity, slotA, slotB, imgFile);
                })
                .show();
    }

    private static void executeFlashDirect(AppCompatActivity activity, PartitionItem target, File imgFile) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        ProgressManager pm = new ProgressManager(activity, false).show();
        pm.setText(isAr ? "جاري تفليش " + target.rawName + "..." : "Flashing " + target.rawName + "...");

        new Thread(() -> {
            boolean success = false;
            String errorMsg = "";
            try {
                String cmd = "dd if='" + imgFile.getAbsolutePath() + "' of='" + target.blockPath + "' bs=4M";
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                int code = p.waitFor();
                success = (code == 0);
            } catch (Exception e) {
                errorMsg = e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalErr = errorMsg;
            activity.runOnUiThread(() -> {
                pm.dismiss();
                if (finalSuccess) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(isAr ? "تم التفليش بنجاح" : "Flash Succeeded")
                            .setMessage(isAr ? "تم تفليش " + target.rawName + " بنجاح!\nيرجى إعادة تشغيل الجهاز لتطبيق التغييرات."
                                    : "Successfully flashed " + target.rawName + "!\nPlease reboot your device to apply changes.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    Extensions.showMessage(activity, isAr ? "فشل التفليش: " + finalErr : "Flash failed: " + finalErr);
                }
            });
        }).start();
    }

    private static void executeFlashDirectDual(AppCompatActivity activity, PartitionItem slotA, PartitionItem slotB, File imgFile) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        ProgressManager pm = new ProgressManager(activity, false).show();
        pm.setText(isAr ? "جاري تفليش كلا السلوتين..." : "Flashing both slots...");

        new Thread(() -> {
            boolean success = true;
            String errorMsg = "";
            try {
                if (slotA != null) {
                    Process p1 = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd if='" + imgFile.getAbsolutePath() + "' of='" + slotA.blockPath + "' bs=4M"});
                    if (p1.waitFor() != 0) success = false;
                }
                if (slotB != null) {
                    Process p2 = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd if='" + imgFile.getAbsolutePath() + "' of='" + slotB.blockPath + "' bs=4M"});
                    if (p2.waitFor() != 0) success = false;
                }
            } catch (Exception e) {
                success = false;
                errorMsg = e.getMessage();
            }

            final boolean finalSuccess = success;
            final String finalErr = errorMsg;
            activity.runOnUiThread(() -> {
                pm.dismiss();
                if (finalSuccess) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle(isAr ? "تم تفليش السلوتين بنجاح" : "Dual Flash Succeeded")
                            .setMessage(isAr ? "تم تفليش كلا السلوتين (A & B) بنجاح!\nيرجى إعادة تشغيل الجهاز لتطبيق التغييرات."
                                    : "Successfully flashed both slots (A & B)!\nPlease reboot your device to apply changes.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                } else {
                    Extensions.showMessage(activity, isAr ? "فشل تفليش السلوتين: " + finalErr : "Dual flash failed: " + finalErr);
                }
            });
        }).start();
    }

    private static void showPartitionInfoDialog(AppCompatActivity activity, PartitionItem item) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(activity, 16);
        layout.setPadding(pad, pad, pad, pad);

        ScrollView sv = new ScrollView(activity);
        sv.addView(layout);

        addInfoRow(activity, layout, isAr ? "الاسم الشائع:" : "Friendly Name:", item.friendlyName);
        addInfoRow(activity, layout, isAr ? "اسم البارتيشن:" : "Partition Name:", item.rawName);
        addInfoRow(activity, layout, isAr ? "مسار البلوك:" : "Block Path:", item.blockPath);
        addInfoRow(activity, layout, isAr ? "الجهاز الهدف:" : "Target Node:", item.targetDev);

        String sizeStr = item.sizeBytes > 0
                ? FileSize.getHumanReadableFileSize(item.sizeBytes) + " (" + String.format(Locale.ROOT, "%,d", item.sizeBytes) + " bytes)"
                : (isAr ? "غير محدد / ديناميكي" : "Unspecified / Dynamic");
        addInfoRow(activity, layout, isAr ? "الحجم:" : "Size:", sizeStr);

        String slotStr;
        if (item.slot == PartitionItem.Slot.A) slotStr = isAr ? "السلوت A" : "Slot A";
        else if (item.slot == PartitionItem.Slot.B) slotStr = isAr ? "السلوت B" : "Slot B";
        else slotStr = isAr ? "مشترك (عام)" : "Shared";
        if (item.isActiveSlot) slotStr += (isAr ? " (النشط حالياً)" : " (Currently Active)");
        addInfoRow(activity, layout, isAr ? "السلوت:" : "Slot:", slotStr);

        addInfoRow(activity, layout, isAr ? "النوع:" : "Type:",
                item.isDynamic ? (isAr ? "ديناميكي (Logical / Dynamic)" : "Logical / Dynamic")
                        : (isAr ? "فيزيائي (Physical)" : "Physical"));

        if (!TextUtils.isEmpty(item.description)) {
            addInfoRow(activity, layout, isAr ? "الوصف:" : "Description:", item.description);
        }

        AlertDialog ad = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.partition_info_title))
                .setIcon(R.drawable.ic_circle_exclamation_24)
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(activity.getString(R.string.partition_copy_details), (d, w) -> {
                    String text = (isAr ? "معلومات البارتيشن: " : "Partition Info: ") + item.friendlyName + "\n"
                            + "Partition: " + item.rawName + "\n"
                            + "Block: " + item.blockPath + " -> " + item.targetDev + "\n"
                            + "Size: " + sizeStr + "\n"
                            + (item.description != null ? "Desc: " + item.description : "");
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Partition", text));
                        Extensions.showMessage(activity, isAr ? "تم نسخ التفاصيل للحافظة" : "Copied details to clipboard");
                    }
                })
                .show();
        DialogUtil.keepButtonsHorizontal(ad);
    }

    private static void addInfoRow(Context context, LinearLayout parent, String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 0, 0, Extensions.dp2px(context, 8));

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextSize(12f);
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setTextColor(0xFF2E7D32);
        row.addView(tvLabel);

        TextView tvVal = new TextView(context);
        tvVal.setText(value);
        tvVal.setTextSize(13f);
        tvVal.setTextIsSelectable(true);
        row.addView(tvVal);

        parent.addView(row);
    }

    private static void promptBatchDumpFolder(AppCompatActivity activity, List<PartitionItem> items, AlertDialog mainDialog) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Extensions.dp2px(activity, 16);
        root.setPadding(pad, pad, pad, pad);

        TextView prompt = new TextView(activity);
        prompt.setText(isAr ? "تفريغ " + items.size() + " من الأقسام المحددة إلى المجلد:"
                : "Dump " + items.size() + " selected partitions to folder:");
        prompt.setTextSize(13f);
        prompt.setPadding(0, 0, 0, Extensions.dp2px(activity, 8));
        root.addView(prompt);

        File defaultDir = new File(Environment.getExternalStorageDirectory(), "Download");
        if (activity instanceof MainActivity ma) {
            try {
                String sp = ma.shownPath();
                if (!TextUtils.isEmpty(sp)) {
                    File cur = new File(sp);
                    if (cur.isDirectory() && cur.canWrite()) defaultDir = cur;
                }
            } catch (Exception ignored) {}
        }

        EditText destInput = new EditText(activity);
        destInput.setText(defaultDir.getAbsolutePath());
        destInput.setTextSize(13f);
        root.addView(destInput);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.partition_batch_dump, items.size()))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.partition_copy_btn, (d, w) -> {
                    String dPath = destInput.getText().toString().trim();
                    if (TextUtils.isEmpty(dPath)) return;
                    File outDir = new File(dPath);
                    if (!outDir.exists()) outDir.mkdirs();
                    executeBatchDump(activity, items, outDir);
                })
                .show();
        DialogUtil.keepButtonsHorizontal(dialog);
    }

    private static void executeBatchDump(AppCompatActivity activity, List<PartitionItem> items, File outDir) {
        boolean isAr = Locale.getDefault().getLanguage().startsWith("ar");
        ProgressManager pm = new ProgressManager(activity, true).show();
        pm.setText(isAr ? "بدء تفريغ الأقسام..." : "Starting batch partition dump...");

        new Thread(() -> {
            int successCount = 0;
            int total = items.size();
            for (int i = 0; i < total; i++) {
                if (pm.isCancelling()) break;
                PartitionItem item = items.get(i);
                int index = i + 1;
                File outFile = new File(outDir, item.outputFileName);
                activity.runOnUiThread(() -> {
                    pm.setText(String.format(Locale.ROOT, "[%d/%d] %s: %s", index, total,
                            isAr ? "جاري التفريغ" : "Dumping", item.rawName));
                });

                Process p = null;
                try {
                    String cmd = "cat '" + item.blockPath + "'";
                    p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    try (InputStream is = new BufferedInputStream(p.getInputStream(), 1024 * 1024);
                         BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(outFile), 1024 * 1024)) {
                        byte[] buffer = new byte[1024 * 1024];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            if (pm.isCancelling()) {
                                p.destroy();
                                os.close();
                                outFile.delete();
                                break;
                            }
                            os.write(buffer, 0, read);
                        }
                    }
                    if (p != null) p.waitFor();
                    if (!pm.isCancelling() && outFile.exists() && outFile.length() > 0) {
                        successCount++;
                    }
                } catch (Exception e) {
                    if (p != null) p.destroy();
                }
            }

            int finalCount = successCount;
            activity.runOnUiThread(() -> {
                pm.dismiss();
                if (activity instanceof MainActivity ma) ma.refreshPane(true);
                new MaterialAlertDialogBuilder(activity)
                        .setTitle(isAr ? "اكتمل التفريغ المتعدد" : "Batch Dump Complete")
                        .setMessage(isAr ? "تم تفريغ " + finalCount + " من أصل " + total + " بارتيشن بنجاح إلى:\n" + outDir.getAbsolutePath()
                                : "Successfully dumped " + finalCount + " of " + total + " partitions to:\n" + outDir.getAbsolutePath())
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            });
        }).start();
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
                }

                p.waitFor();

                activity.runOnUiThread(() -> {
                    pm.dismiss();
                    if (activity instanceof MainActivity ma) {
                        ma.reloadCurrentFolder();
                        ma.refreshPane(true);
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
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.title_success)
                .setMessage(activity.getString(R.string.partition_dump_success, outFile.getAbsolutePath()))
                .setPositiveButton(android.R.string.ok, null)
                .show();
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

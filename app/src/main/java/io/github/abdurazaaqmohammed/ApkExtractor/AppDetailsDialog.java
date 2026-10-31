package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.ClipData;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.AppRecyclerViewAdapter;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class AppDetailsDialog {

    private AppDetailsDialog() {
    }

    private static boolean isEditMode = false;

    public static void show(APKExtractorActivity act, AppInfo ai, AppRecyclerViewAdapter adapter) {
        AlertDialog[] dialogRef = new AlertDialog[1];

        ScrollView scroll = new ScrollView(act);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(act, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        // Header
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(act);
        int iconSize = dp(act, 52);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        if (ai.icon != null) icon.setImageDrawable(ai.icon);
        header.addView(icon);

        LinearLayout titles = new LinearLayout(act);
        titles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.setMarginStart(dp(act, 12));
        titles.setLayoutParams(tp);
        TextView nameView = new TextView(act);
        nameView.setText(ai.name);
        nameView.setTextSize(19);
        nameView.setTypeface(nameView.getTypeface(), Typeface.BOLD);
        titles.addView(nameView);
        TextView pkgView = new TextView(act);
        pkgView.setText(ai.packageName);
        pkgView.setTextSize(13);
        pkgView.setTextIsSelectable(true);
        titles.addView(pkgView);
        header.addView(titles);

        // Pencil / Edit button
        ImageButton editBtn = new ImageButton(act);
        editBtn.setImageResource(R.drawable.edit_24px);
        editBtn.setBackgroundResource(getSelectableBackground(act));
        int btnPad = dp(act, 8);
        editBtn.setPadding(btnPad, btnPad, btnPad, btnPad);
        editBtn.setContentDescription(act.getString(R.string.edit_app_details));
        TypedValue primaryColor = new TypedValue();
        act.getTheme().resolveAttribute(android.R.attr.colorPrimary, primaryColor, true);
        editBtn.setImageTintList(ColorStateList.valueOf(primaryColor.data));
        header.addView(editBtn);

        root.addView(header);

        // Asynchronously load icon if missing
        if (ai.icon == null && ai.packageName != null) {
            new Thread(() -> {
                try {
                    PackageManager pm = act.getPackageManager();
                    Drawable d = act.iconCache.getDisk(act, ai.packageName, ai.getVersionCode());
                    if (d == null) {
                        try {
                            ApplicationInfo info = ai.appInfo != null ? ai.appInfo : pm.getApplicationInfo(ai.packageName, 0);
                            if (info != null) {
                                d = info.loadIcon(pm);
                                act.iconCache.put(act, ai.packageName, ai.getVersionCode(), d);
                            }
                        } catch (Exception ignored) {}
                    }
                    if (d != null) {
                        ai.icon = d;
                        Drawable finalD = d;
                        act.handler.post(() -> {
                            try {
                                icon.setImageDrawable(finalD);
                                if (adapter != null) adapter.notifyItemChanged(adapter.filteredAppInfoList.indexOf(ai));
                            } catch (Exception ignored) {}
                        });
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        addDivider(act, root);

        // Container that switches between Normal Mode and Edit Mode
        LinearLayout contentContainer = new LinearLayout(act);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentContainer);

        Runnable[] renderContent = new Runnable[1];

        renderContent[0] = () -> {
            contentContainer.removeAllViews();
            if (!isEditMode) {
                editBtn.setImageResource(R.drawable.edit_24px);
                renderNormalMode(act, contentContainer, ai, adapter, dialogRef[0]);
            } else {
                editBtn.setImageResource(R.drawable.baseline_check_circle_24);
                renderEditMode(act, contentContainer, ai, renderContent[0]);
            }
        };

        editBtn.setOnClickListener(v -> {
            isEditMode = !isEditMode;
            renderContent[0].run();
        });

        // Initialize view
        isEditMode = false;
        renderContent[0].run();

        AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setView(scroll)
                .setNegativeButton(R.string.close, null)
                .create();
        dialogRef[0] = dialog;
        act.styleAlertDialog(dialog);

        View.OnLongClickListener copy = v -> {
            if (v instanceof TextView tv && !TextUtils.isEmpty(tv.getText())) CopyUtil.copyToClipboard(dialog, tv.getText());
            return true;
        };
        nameView.setOnLongClickListener(copy);
        pkgView.setOnLongClickListener(copy);
    }

    private static void renderNormalMode(APKExtractorActivity act, LinearLayout container, AppInfo ai, AppRecyclerViewAdapter adapter, AlertDialog dialog) {
        List<String> order = AppDetailsConfig.getOrder(act);
        Set<String> hidden = AppDetailsConfig.getHiddenKeys(act);

        boolean hasAddedActionHeader = false;

        for (String id : order) {
            if (hidden.contains(id)) continue;
            AppDetailsConfig.ConfigItem item = AppDetailsConfig.findItem(id);
            if (item == null) continue;

            if (item.category == AppDetailsConfig.ItemCategory.DETAIL) {
                renderDetailRow(act, container, id, ai, dialog);
            } else {
                if (!hasAddedActionHeader) {
                    addDivider(act, container);
                    TextView actionsTitle = new TextView(act);
                    actionsTitle.setText(R.string.actions_label);
                    actionsTitle.setTextSize(13);
                    actionsTitle.setPadding(0, 0, 0, dp(act, 4));
                    container.addView(actionsTitle);
                    hasAddedActionHeader = true;
                }
                renderActionRow(act, container, id, ai, adapter, dialog);
            }
        }
    }

    private static void renderDetailRow(APKExtractorActivity act, LinearLayout container, String id, AppInfo ai, AlertDialog dialog) {
        View.OnLongClickListener copy = v -> {
            if (v instanceof TextView tv && !TextUtils.isEmpty(tv.getText())) CopyUtil.copyToClipboard(dialog, tv.getText());
            return true;
        };

        switch (id) {
            case "detail:version": {
                String version = (ai.versionName == null || ai.versionName.isEmpty()) ? "—" : "v" + ai.versionName;
                TextView v = addRow(act, container, act.getString(R.string.version_name), version + " (" + ai.getVersionCode() + ")", null, false);
                v.setOnLongClickListener(copy);
                break;
            }
            case "detail:apk_size": {
                if (ai.totalSize > 0) {
                    String size;
                    if (ai.isSplit && ai.splitCount > 0) {
                        size = act.getString(R.string.base_label) + " "
                                + Formatter.formatFileSize(act, ai.baseSize) + " • "
                                + act.getString(R.string.splits_label) + " (" + ai.splitCount + ") "
                                + Formatter.formatFileSize(act, ai.totalSize - ai.baseSize) + " • "
                                + act.getString(R.string.total_label) + " "
                                + Formatter.formatFileSize(act, ai.totalSize);
                    } else {
                        size = Formatter.formatFileSize(act, ai.baseSize > 0 ? ai.baseSize : ai.totalSize);
                    }
                    TextView v = addRow(act, container, act.getString(R.string.apk_size), size, null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:signature": {
                TextView sigValue = addRow(act, container, act.getString(R.string.signature),
                        ai.sigType == null ? "…" : ai.sigType, null, false);
                if (ai.filePath != null) {
                    View sigParent = (View) sigValue.getParent();
                    if (sigParent != null) {
                        sigParent.setClickable(true);
                        sigParent.setFocusable(true);
                        sigParent.setBackgroundResource(getSelectableBackground(act));
                        View.OnClickListener openSig = v -> io.github.abdurazaaqmohammed.features.apk.SignatureInfoDialog.show(act, new File(ai.filePath));
                        sigParent.setOnClickListener(openSig);
                        sigValue.setOnClickListener(openSig);
                    }
                }
                if (ai.sigType == null && ai.filePath != null) {
                    new Thread(() -> {
                        String sig;
                        try {
                            sig = APKExtractorActivity.resolveSigType(new File(ai.filePath));
                        } catch (Exception e) {
                            sig = "Unknown";
                        }
                        ai.sigType = sig;
                        String finalSig = sig;
                        act.handler.post(() -> {
                            try { sigValue.setText(finalSig); } catch (Exception ignored) {}
                        });
                    }).start();
                }
                sigValue.setOnLongClickListener(copy);
                break;
            }
            case "detail:first_install": {
                if (ai.firstInstalled != null && !ai.firstInstalled.isEmpty()) {
                    TextView v = addRow(act, container, act.getString(R.string.first_install_date), ai.firstInstalled, null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:last_updated": {
                if (ai.lastUpdated != null && !ai.lastUpdated.isEmpty()) {
                    TextView v = addRow(act, container, act.getString(R.string.last_updated_date), ai.lastUpdated, null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:apk_dir": {
                if (ai.apkDir != null && !ai.apkDir.isEmpty()) {
                    TextView v = addRow(act, container, act.getString(R.string.apk_dir), ai.apkDir, ai.apkDir, true);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:data_dir": {
                if (ai.dataDir != null && !ai.dataDir.isEmpty()) {
                    TextView v = addRow(act, container, act.getString(R.string.data_dir), ai.dataDir, ai.dataDir, true);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:ext_data": {
                if (ai.extDataDir != null && !ai.extDataDir.isEmpty()) {
                    TextView v = addRow(act, container, act.getString(R.string.ext_data_dir), ai.extDataDir, ai.extDataDir, true);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:target_sdk": {
                if (ai.targetSdk > 0) {
                    TextView v = addRow(act, container, act.getString(R.string.target_sdk), String.valueOf(ai.targetSdk), null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:min_sdk": {
                if (ai.minSdk >= 0) {
                    TextView v = addRow(act, container, act.getString(R.string.min_sdk), String.valueOf(ai.minSdk), null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:installer": {
                if (ai.installer != null && !ai.installer.isEmpty()) {
                    TextView v = addRow(act, container, act.getString(R.string.installer), installerDisplay(act, ai.installer), null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:initiator": {
                if (ai.installInitiator != null && !ai.installInitiator.isEmpty() && !ai.installInitiator.equals(ai.installer)) {
                    TextView v = addRow(act, container, act.getString(R.string.install_initiator), installerDisplay(act, ai.installInitiator), null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:origin": {
                if (ai.installOrigin != null && !ai.installOrigin.isEmpty()) {
                    TextView v = addRow(act, container, act.getString(R.string.install_origin), ai.installOrigin, null, false);
                    v.setOnLongClickListener(copy);
                }
                break;
            }
            case "detail:status": {
                TextView v = addRow(act, container, act.getString(R.string.status_label),
                        ai.enabled ? act.getString(R.string.enabled_state) : act.getString(R.string.disabled_state), null, false);
                v.setOnLongClickListener(copy);
                break;
            }
        }
    }

    private static void renderActionRow(APKExtractorActivity act, LinearLayout container, String id, AppInfo ai, AppRecyclerViewAdapter adapter, AlertDialog dialog) {
        Integer actionId = getActionId(id);
        if (actionId == null) return;

        // Skip root actions if root not available
        if (actionId >= 200 && actionId <= 204) {
            RootManager rm = RootManager.getInstance(act);
            if (!rm.isRootExtractorEnabled() || !rm.isRootAvailable()) return;
        }

        // Special split condition
        if (id.equals("action:extract_split") && !ai.isSplit) return;

        AppDetailsConfig.ConfigItem item = AppDetailsConfig.findItem(id);
        if (item == null) return;

        LayoutInflater inflater = LayoutInflater.from(act);
        View row = inflater.inflate(R.layout.item_dropdown_option, container, false);
        ImageView rowIcon = row.findViewById(R.id.optionIcon);
        TextView rowText = row.findViewById(R.id.optionText);

        int iconRes = item.defaultIconResId;
        if (id.equals("action:extract") && ai.isSplit && !act.antisplit) {
            iconRes = R.drawable.archive_24px;
        }
        rowIcon.setImageResource(iconRes);
        rowText.setText(item.labelResId);

        row.setOnClickListener(v -> {
            try { dialog.dismiss(); } catch (Exception ignored) {}
            act.handleAppAction(actionId, ai, adapter);
        });
        container.addView(row);
    }

    private static Integer getActionId(String id) {
        switch (id) {
            case "action:extract": return 0;
            case "action:share": return 1;
            case "action:play_store": return 111;
            case "action:launch": return 100;
            case "action:app_info": return 101;
            case "action:uninstall": return 102;
            case "action:extract_icon": return 7;
            case "action:extract_res": return 2;
            case "action:extract_dex": return 3;
            case "action:extract_manifest": return 4;
            case "action:extract_base": return 5;
            case "action:extract_split": return 103;
            case "action:extract_libs": return 6;
            case "action:launch_activity": return 107;
            case "action:open_apk_dir": return 108;
            case "action:open_data_dir": return 109;
            case "action:open_ext_data_dir": return 110;
            case "action:clear_data": return 200;
            case "action:force_stop": return 201;
            case "action:enable_app": return 202;
            case "action:disable_app": return 203;
            case "action:uninstall_root": return 204;
            default: return null;
        }
    }

    // --- Edit Mode (Organize & Toggle Visibility) ---
    private static void renderEditMode(APKExtractorActivity act, LinearLayout container, AppInfo ai, Runnable refreshCallback) {
        List<String> order = AppDetailsConfig.getOrder(act);
        Set<String> hidden = AppDetailsConfig.getHiddenKeys(act);

        TextView info = new TextView(act);
        info.setText(R.string.edit_app_details);
        info.setTextSize(14);
        info.setTypeface(info.getTypeface(), Typeface.BOLD);
        info.setPadding(0, 0, 0, dp(act, 8));
        TypedValue primaryColor = new TypedValue();
        act.getTheme().resolveAttribute(android.R.attr.colorPrimary, primaryColor, true);
        info.setTextColor(primaryColor.data);
        container.addView(info);

        LayoutInflater inflater = LayoutInflater.from(act);

        for (int i = 0; i < order.size(); i++) {
            final String id = order.get(i);
            AppDetailsConfig.ConfigItem item = AppDetailsConfig.findItem(id);
            if (item == null) continue;

            View row = inflater.inflate(R.layout.item_dropdown_option, container, false);
            ImageView rowIcon = row.findViewById(R.id.optionIcon);
            TextView rowText = row.findViewById(R.id.optionText);
            ImageButton hideBtn = row.findViewById(R.id.optionHideButton);
            ImageView dragHandle = row.findViewById(R.id.optionDragHandle);

            boolean isHidden = hidden.contains(id);

            rowIcon.setImageResource(isHidden ? R.drawable.visibility_off_24px : item.defaultIconResId);
            rowIcon.setAlpha(isHidden ? 0.45f : 1f);
            rowText.setText(item.labelResId);
            rowText.setAlpha(isHidden ? 0.45f : 1f);
            row.setAlpha(isHidden ? 0.6f : 1f);

            hideBtn.setVisibility(View.VISIBLE);
            hideBtn.setImageResource(isHidden ? R.drawable.visibility_off_24px : R.drawable.ic_eye_mt);
            hideBtn.setOnClickListener(v -> {
                AppDetailsConfig.toggleHidden(act, id);
                refreshCallback.run();
            });

            dragHandle.setVisibility(View.VISIBLE);

            // Drag and drop setup for reordering
            row.setTag(id);
            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    ClipData data = ClipData.newPlainText("id", id);
                    View.DragShadowBuilder shadow = new View.DragShadowBuilder(row);
                    row.startDragAndDrop(data, shadow, id, 0);
                    return true;
                }
                return false;
            });

            row.setOnDragListener((v, event) -> {
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return true;
                    case DragEvent.ACTION_DROP:
                        String sourceId = (String) event.getLocalState();
                        String targetId = (String) v.getTag();
                        if (sourceId != null && targetId != null && !sourceId.equals(targetId)) {
                            int srcIdx = order.indexOf(sourceId);
                            int tgtIdx = order.indexOf(targetId);
                            if (srcIdx >= 0 && tgtIdx >= 0) {
                                order.remove(srcIdx);
                                order.add(tgtIdx, sourceId);
                                AppDetailsConfig.setOrder(act, order);
                                refreshCallback.run();
                            }
                        }
                        return true;
                    default:
                        return true;
                }
            });

            container.addView(row);
        }
    }

    private static String installerDisplay(APKExtractorActivity act, String pkg) {
        if (pkg == null || pkg.isEmpty()) return pkg;
        try {
            PackageManager pm = act.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
            CharSequence label = info == null ? null : pm.getApplicationLabel(info);
            if (label != null && !label.toString().equals(pkg)) return label + " (" + pkg + ")";
        } catch (Exception ignored) {}
        return pkg;
    }

    private static TextView addRow(APKExtractorActivity act, LinearLayout root, String label, String value, String tapPath, boolean isExpandable) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int vPad = dp(act, 6);
        row.setPadding(0, vPad, 0, vPad);

        TextView labelView = new TextView(act);
        labelView.setText(label);
        labelView.setTextSize(14);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(labelView);

        TextView valueView = new TextView(act);
        valueView.setText(value == null ? "" : value);
        valueView.setTextSize(14);
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setTextIsSelectable(true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
        vp.setMarginStart(dp(act, 8));
        valueView.setLayoutParams(vp);
        row.addView(valueView);

        if (tapPath != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackgroundResource(getSelectableBackground(act));
            row.setOnClickListener(v -> act.openInFileManager(tapPath));
        }

        root.addView(row);
        return valueView;
    }

    private static void addDivider(APKExtractorActivity act, LinearLayout root) {
        View divider = new View(act);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(act, 1));
        int m = dp(act, 8);
        lp.setMargins(0, m, 0, m);
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(0x33FFFFFF);
        root.addView(divider);
    }

    private static int getSelectableBackground(Context context) {
        TypedValue tv = new TypedValue();
        try {
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            return tv.resourceId;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int dp(Context act, int dp) {
        return (int) (dp * act.getResources().getDisplayMetrics().density + 0.5f);
    }
}

package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.AppRecyclerViewAdapter;
import io.github.abdurazaaqmohammed.utils.CopyUtil;

import java.io.File;

public final class AppDetailsDialog {

    private AppDetailsDialog() {
    }

    public static void show(APKExtractorActivity act, AppInfo ai, AppRecyclerViewAdapter adapter) {
        ScrollView scroll = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(act, 16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

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
        root.addView(header);

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
                                adapter.notifyItemChanged(adapter.filteredAppInfoList.indexOf(ai));
                            } catch (Exception ignored) {}
                        });
                    }
                } catch (Exception ignored) {}
            }).start();
        }

        addDivider(act, root);

        String version = (ai.versionName == null || ai.versionName.isEmpty()) ? "—" : "v" + ai.versionName;
        addRow(act, root, act.getString(R.string.version_name), version + " (" + ai.getVersionCode() + ")", null);

        if (ai.totalSize > 0) {
            String size;
            if (ai.isSplit && ai.splitCount > 0) {
                Context c = act;
                size = act.getString(R.string.base_label) + " "
                        + Formatter.formatFileSize(c, ai.baseSize) + " • "
                        + act.getString(R.string.splits_label) + " (" + ai.splitCount + ") "
                        + Formatter.formatFileSize(c, ai.totalSize - ai.baseSize) + " • "
                        + act.getString(R.string.total_label) + " "
                        + Formatter.formatFileSize(c, ai.totalSize);
            } else {
                size = Formatter.formatFileSize(act, ai.baseSize > 0 ? ai.baseSize : ai.totalSize);
            }
            addRow(act, root, act.getString(R.string.apk_size), size, null);
        }

        TextView sigValue = addRow(act, root, act.getString(R.string.signature),
                ai.sigType == null ? "…" : ai.sigType, null);
        if (ai.filePath != null) {
            View sigParent = (View) sigValue.getParent();
            if (sigParent != null) {
                sigParent.setClickable(true);
                sigParent.setFocusable(true);
                TypedValue tv = new TypedValue();
                try {
                    act.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
                    sigParent.setBackgroundResource(tv.resourceId);
                } catch (Exception ignored) {}
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

        if (ai.firstInstalled != null && !ai.firstInstalled.isEmpty())
            addRow(act, root, act.getString(R.string.first_install_date), ai.firstInstalled, null);
        if (ai.lastUpdated != null && !ai.lastUpdated.isEmpty())
            addRow(act, root, act.getString(R.string.last_updated_date), ai.lastUpdated, null);
        if (ai.apkDir != null && !ai.apkDir.isEmpty())
            addRow(act, root, act.getString(R.string.apk_dir), ai.apkDir, ai.apkDir);
        if (ai.dataDir != null && !ai.dataDir.isEmpty())
            addRow(act, root, act.getString(R.string.data_dir), ai.dataDir, ai.dataDir);
        if (ai.extDataDir != null && !ai.extDataDir.isEmpty())
            addRow(act, root, act.getString(R.string.ext_data_dir), ai.extDataDir, ai.extDataDir);
        if (ai.targetSdk > 0)
            addRow(act, root, act.getString(R.string.target_sdk), String.valueOf(ai.targetSdk), null);
        if (ai.minSdk >= 0)
            addRow(act, root, act.getString(R.string.min_sdk), String.valueOf(ai.minSdk), null);
        TextView installerValue = null;
        TextView initiatorValue = null;
        TextView originValue = null;
        if (ai.installer != null && !ai.installer.isEmpty()) {
            installerValue = addRow(act, root, act.getString(R.string.installer), installerDisplay(act, ai.installer), null);
        }
        if (ai.installInitiator != null && !ai.installInitiator.isEmpty()
                && !ai.installInitiator.equals(ai.installer)) {
            initiatorValue = addRow(act, root, act.getString(R.string.install_initiator), installerDisplay(act, ai.installInitiator), null);
        }
        if (ai.installOrigin != null && !ai.installOrigin.isEmpty()) {
            originValue = addRow(act, root, act.getString(R.string.install_origin), ai.installOrigin, null);
        }
        addRow(act, root, act.getString(R.string.status_label),
                ai.enabled ? act.getString(R.string.enabled_state) : act.getString(R.string.disabled_state), null);

        addDivider(act, root);
        TextView actionsTitle = new TextView(act);
        actionsTitle.setText(R.string.actions_label);
        actionsTitle.setTextSize(13);
        actionsTitle.setPadding(0, 0, 0, dp(act, 4));
        root.addView(actionsTitle);

        LinearLayout actionsBox = new LinearLayout(act);
        actionsBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(actionsBox);

        AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setView(scroll)
                .setNegativeButton(R.string.close, null)
                .create();
        act.styleAlertDialog(dialog);

        APKExtractorActivity.AppActions actions = act.buildAppActions(ai);
        LayoutInflater inflater = LayoutInflater.from(act);
        for (int i = 0; i < actions.displayList.size(); i++) {
            final int actionId = actions.actionIds.get(i);
            View row = inflater.inflate(R.layout.item_dropdown_option, actionsBox, false);
            ImageView rowIcon = row.findViewById(R.id.optionIcon);
            TextView rowText = row.findViewById(R.id.optionText);
            rowIcon.setImageResource(actions.iconList.get(i));
            rowText.setText(actions.displayList.get(i));
            row.setOnClickListener(v -> {
                try { dialog.dismiss(); } catch (Exception ignored) {}
                act.handleAppAction(actionId, ai, adapter);
            });
            actionsBox.addView(row);
        }

        View.OnLongClickListener copy = v -> {
            if (v instanceof TextView tv && !TextUtils.isEmpty(tv.getText())) CopyUtil.copyToClipboard(dialog, tv.getText());
            return true;
        };
        nameView.setOnLongClickListener(copy);
        pkgView.setOnLongClickListener(copy);
        sigValue.setOnLongClickListener(copy);
        if (installerValue != null) installerValue.setOnLongClickListener(copy);
        if (initiatorValue != null) initiatorValue.setOnLongClickListener(copy);
        if (originValue != null) originValue.setOnLongClickListener(copy);
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

    private static TextView addRow(APKExtractorActivity act, LinearLayout root, String label, String value, String tapPath) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
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
        valueView.setMaxLines(3);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f);
        vp.setMarginStart(dp(act, 8));
        valueView.setLayoutParams(vp);
        row.addView(valueView);

        if (tapPath != null) {
            row.setClickable(true);
            row.setFocusable(true);
            TypedValue tv = new TypedValue();
            try {
                act.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
                row.setBackgroundResource(tv.resourceId);
            } catch (Exception ignored) {}
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

    private static int dp(APKExtractorActivity act, int dp) {
        return (int) (dp * act.getResources().getDisplayMetrics().density + 0.5f);
    }
}

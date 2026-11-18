package io.github.abdurazaaqmohammed.ui.main;

import io.github.abdurazaaqmohammed.feature.apk.ExtractedApksIndex;

import static android.view.View.GONE;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.util.hash.HashUtil;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.Gravity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.github.abdurazaaqmohammed.feature.apk.APKExtractorActivity;
import io.github.abdurazaaqmohammed.feature.apk.AppInfo;
import io.github.abdurazaaqmohammed.MPManager.R;

public class AppRecyclerViewAdapter extends RecyclerView.Adapter<AppRecyclerViewAdapter.ViewHolder> implements Filterable {

    public final APKExtractorActivity apkExtractorActivity;
    public final List<AppInfo> appInfoList;
    public final List<AppInfo> filteredAppInfoList;
    private Map<String, List<ExtractedApksIndex.Entry>> extractedIndex;

    private Comparator<AppInfo> currentComparator;
    private AppInfoFilter filter;

    public void setExtractedIndex(Map<String, List<ExtractedApksIndex.Entry>> index) {
        this.extractedIndex = index;
        notifyDataSetChanged();
    }

    public AppRecyclerViewAdapter(APKExtractorActivity activity, List<AppInfo> list) {
        this.apkExtractorActivity = activity;
        this.appInfoList = list;
        this.filteredAppInfoList = new ArrayList<>(list);
    }

    public void setComparator(Comparator<AppInfo> comparator) {
        this.currentComparator = comparator;
    }

    @Override
    public int getItemCount() {
        return filteredAppInfoList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new ViewHolder(v);
    }

    public final HashSet<String> selectedItems = new HashSet<>();

    public void toggleSelection(int position) {
        if (position < 0 || position >= filteredAppInfoList.size()) return;
        String pkg = filteredAppInfoList.get(position).packageName;
        if (pkg == null) return;
        if (selectedItems.contains(pkg)) selectedItems.remove(pkg);
        else selectedItems.add(pkg);
        notifyItemChanged(position);
    }

    public boolean isSelected(int position) {
        if (position < 0 || position >= filteredAppInfoList.size()) return false;
        String pkg = filteredAppInfoList.get(position).packageName;
        return pkg != null && selectedItems.contains(pkg);
    }

    public List<Integer> selectedPositions() {
        List<Integer> out = new ArrayList<>();
        if (selectedItems.isEmpty()) return out;
        for (int i = 0; i < filteredAppInfoList.size(); i++) {
            String pkg = filteredAppInfoList.get(i).packageName;
            if (pkg != null && selectedItems.contains(pkg)) out.add(i);
        }
        return out;
    }

    public void clearSelection() {
        selectedItems.clear();
        apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
        notifyDataSetChanged();
    }

    public void addItem(AppInfo appInfo) {
        if (!appInfoList.contains(appInfo)) appInfoList.add(appInfo);
        filteredAppInfoList.add(appInfo);
        notifyItemInserted(filteredAppInfoList.size() - 1);
    }

    public void reset() {
        selectedItems.clear();
        filteredAppInfoList.clear();
        notifyDataSetChanged();
    }

    public void setItems(List<AppInfo> items) {
        selectedItems.clear();
        List<AppInfo> copy = items == null ? new ArrayList<>() : new ArrayList<>(items);
        appInfoList.clear();
        appInfoList.addAll(copy);
        filteredAppInfoList.clear();
        filteredAppInfoList.addAll(appInfoList);
        notifyDataSetChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppInfo appInfo = filteredAppInfoList.get(position);

        MaterialCardView cardView = holder.cardView;
        if (isSelected(position)) {
            TypedValue primaryColor = new TypedValue();
            cardView.getContext().getTheme().resolveAttribute(android.R.attr.colorPrimary, primaryColor, true);
            cardView.setStrokeColor(primaryColor.data);
            cardView.setStrokeWidth(6);

            TypedValue colorPrimaryContainer = new TypedValue();
            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, colorPrimaryContainer, true);
            cardView.setCardBackgroundColor(colorPrimaryContainer.data);
        } else {
            TypedValue typedValue = new TypedValue();
            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true);
            cardView.setStrokeColor(typedValue.data);
            cardView.setStrokeWidth(1);

            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
            cardView.setCardBackgroundColor(typedValue.data);
            cardView.setBackgroundColor(typedValue.data);
        }

        APKExtractorActivity act = apkExtractorActivity;
        holder.appName.setText(appInfo.name);
        holder.appName.setVisibility(View.VISIBLE);
        StringBuilder sub = new StringBuilder(appInfo.packageName);
        if (appInfo.versionName != null && !appInfo.versionName.isEmpty()) {
            sub.append(" • v").append(appInfo.versionName);
        }
        if (appInfo.totalSize > 0) {
            sub.append(" • ").append(Formatter.formatFileSize(
                    holder.packageNameView.getContext(),
                    appInfo.totalSize));
        }
        holder.packageNameView.setText(sub.toString());
        holder.packageNameView.setMovementMethod(null);
        holder.packageNameView.setVisibility(View.VISIBLE);
        holder.appIconView.setVisibility(View.VISIBLE);
        holder.splitIconView.setVisibility(appInfo.isSplit ? View.VISIBLE : GONE);

        if (appInfo.icon != null) {
            holder.appIconView.setImageDrawable(appInfo.icon);
        } else {
            Drawable mem = act.iconCache.getMemory(appInfo.packageName, appInfo.getVersionCode());
            if (mem != null) {
                appInfo.icon = mem;
                holder.appIconView.setImageDrawable(mem);
            } else {
                holder.appIconView.setImageDrawable(null);
                final String expectedPkg = appInfo.packageName;
                final int expectedVc = appInfo.getVersionCode();
                holder.appIconView.setTag(expectedPkg);
                new Thread(() -> {
                    Drawable diskOrLoaded = act.iconCache.getDisk(act, expectedPkg, expectedVc);
                    if (diskOrLoaded == null) {
                        try {
                            PackageManager pm = act.getPackageManager();
                            ApplicationInfo info = appInfo.appInfo;
                            if (info == null) {
                                info = pm.getApplicationInfo(expectedPkg, 0);
                                appInfo.appInfo = info;
                            }
                            if (info != null) {
                                diskOrLoaded = info.loadIcon(pm);
                                act.iconCache.put(act, expectedPkg, expectedVc, diskOrLoaded);
                            }
                        } catch (Exception ignored) {}
                    }
                    if (diskOrLoaded != null) {
                        final Drawable res = diskOrLoaded;
                        appInfo.icon = res;
                        act.handler.post(() -> {
                            if (expectedPkg.equals(holder.appIconView.getTag())) {
                                holder.appIconView.setImageDrawable(res);
                            }
                        });
                    }
                }).start();
            }
        }

        // Corner status badge indicating extracted state
        ExtractedApksIndex.Match match =
                ExtractedApksIndex.findMatch(extractedIndex, appInfo);
        if (match != null && match.file() != null && match.file().exists()) {
            holder.cornerBadgeView.setVisibility(View.VISIBLE);
            int color;
            if (match.status() == ExtractedApksIndex.Match.STATUS_SAME) {
                color = 0xFF4CAF50; // Green (identical version)
            } else if (match.status() == ExtractedApksIndex.Match.STATUS_NEWER) {
                color = 0xFFFFC107; // Yellow (extracted is newer)
            } else {
                color = 0xFFF44336; // Red (extracted is older)
            }
            holder.cornerBadgeView.setColorFilter(color, PorterDuff.Mode.SRC_IN);
            final File targetFile = match.file();
            holder.cornerBadgeView.setOnClickListener(v -> {
                Intent result = new Intent();
                result.putExtra("dirToLoad", targetFile.getParent());
                result.putExtra("highlightName", targetFile.getName());
                apkExtractorActivity.setResult(Activity.RESULT_OK, result);
                apkExtractorActivity.finish();
            });
            holder.cornerBadgeView.setOnLongClickListener(v -> {
                showVersionsPopup(appInfo);
                return true;
            });
        } else {
            holder.cornerBadgeView.setVisibility(View.GONE);
            holder.cornerBadgeView.setOnClickListener(null);
            holder.cornerBadgeView.setOnLongClickListener(null);
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (!selectedItems.isEmpty()) {
                toggleSelection(pos);
                if (selectedItems.isEmpty()) {
                    apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
                }
            } else {
                apkExtractorActivity.showAppDetails(filteredAppInfoList.get(pos), this);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return true;
            apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.VISIBLE);
            toggleSelection(pos);
            return true;
        });
    }

    @Override
    public Filter getFilter() {
        if (filter == null) filter = new AppInfoFilter();
        return filter;
    }

    private class AppInfoFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            if (TextUtils.isEmpty(constraint)) {
                results.values = new ArrayList<>(appInfoList);
            } else {
                List<AppInfo> filtered = new ArrayList<>();
                String pattern = constraint.toString().toLowerCase().trim();
                for (AppInfo info : appInfoList) {
                    if (info.name.toLowerCase().contains(pattern) || info.packageName.toLowerCase().contains(pattern)) {
                        filtered.add(info);
                    }
                }
                results.values = filtered;
            }
            if (currentComparator != null) {
                List<AppInfo> sorted = (List<AppInfo>) results.values;
                Collections.sort(sorted, currentComparator);
                results.values = sorted;
            }
            results.count = ((List<AppInfo>) results.values).size();
            return results;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredAppInfoList.clear();
            if (results.values != null) filteredAppInfoList.addAll((List<AppInfo>) results.values);
            notifyDataSetChanged();
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final TextView appName;
        final TextView packageNameView;
        final ImageView appIconView;
        final View splitIconView;
        final ImageView cornerBadgeView;

        ViewHolder(View v) {
            super(v);
            cardView = (MaterialCardView) v;
            appName = v.findViewById(R.id.appName);
            packageNameView = v.findViewById(R.id.package_name_view);
            appIconView = v.findViewById(R.id.icon_view);
            splitIconView = v.findViewById(R.id.badge_view);
            cornerBadgeView = v.findViewById(R.id.corner_status_badge);
        }
    }

    private void showVersionsPopup(AppInfo appInfo) {
        if (appInfo == null) return;
        List<ExtractedApksIndex.Entry> entries = ExtractedApksIndex.getEntriesForApp(extractedIndex, appInfo);
        if (entries.isEmpty()) return;

        // Sort entries by version descending (newest on top)
        List<ExtractedApksIndex.Entry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, (a, b) -> ExtractedApksIndex.compareVersions(b.version, a.version));

        ScrollView scroll = new ScrollView(apkExtractorActivity);
        LinearLayout container = new LinearLayout(apkExtractorActivity);
        container.setOrientation(LinearLayout.VERTICAL);
        int padH = (int) (16 * apkExtractorActivity.getResources().getDisplayMetrics().density);
        int padV = (int) (12 * apkExtractorActivity.getResources().getDisplayMetrics().density);
        container.setPadding(padH, padV, padH, padV);

        String installedVer = appInfo.versionName != null ? appInfo.versionName.trim() : "";
        List<ImageView> sameVersionStatusViews = new ArrayList<>();
        List<File> sameVersionFiles = new ArrayList<>();

        for (ExtractedApksIndex.Entry entry : sorted) {
            final File file = entry.file;
            LinearLayout row = new LinearLayout(apkExtractorActivity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int rowPadV = (int) (10 * apkExtractorActivity.getResources().getDisplayMetrics().density);
            row.setPadding(0, rowPadV, 0, rowPadV);

            ImageView statusIcon = new ImageView(apkExtractorActivity);
            int iconSize = (int) (24 * apkExtractorActivity.getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMarginEnd((int) (12 * apkExtractorActivity.getResources().getDisplayMetrics().density));
            statusIcon.setLayoutParams(iconParams);

            int cmp = ExtractedApksIndex.compareVersions(entry.version, installedVer);
            if (cmp > 0) {
                // Extracted is newer: Up arrow (Yellow)
                statusIcon.setImageResource(R.drawable.ic_arrow_up_mt);
                statusIcon.setColorFilter(0xFFFFC107, PorterDuff.Mode.SRC_IN);
            } else if (cmp < 0) {
                // Extracted is older: Down arrow (Red)
                statusIcon.setImageResource(R.drawable.ic_arrow_down_mt);
                statusIcon.setColorFilter(0xFFF44336, PorterDuff.Mode.SRC_IN);
            } else {
                // Same version: start with empty circle, then calculate hash
                statusIcon.setImageResource(R.drawable.ic_circle_outline_24);
                statusIcon.setColorFilter(0xFF888888, PorterDuff.Mode.SRC_IN);
                sameVersionStatusViews.add(statusIcon);
                sameVersionFiles.add(file);
            }
            row.addView(statusIcon);

            LinearLayout textCol = new LinearLayout(apkExtractorActivity);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textColParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            textCol.setLayoutParams(textColParams);

            TextView titleView = new TextView(apkExtractorActivity);
            titleView.setText(file.getName());
            titleView.setTextSize(14f);
            textCol.addView(titleView);

            TextView subView = new TextView(apkExtractorActivity);
            subView.setText("v" + entry.version + " (" + Formatter.formatFileSize(apkExtractorActivity, file.length()) + ")");
            subView.setTextSize(12f);
            subView.setAlpha(0.7f);
            textCol.addView(subView);

            row.addView(textCol);

            // Clicking any row jumps to that specific extracted version file in MainActivity
            row.setOnClickListener(v -> {
                Intent result = new Intent();
                result.putExtra("dirToLoad", file.getParent());
                result.putExtra("highlightName", file.getName());
                apkExtractorActivity.setResult(Activity.RESULT_OK, result);
                apkExtractorActivity.finish();
            });

            container.addView(row);
        }

        scroll.addView(container);

        AlertDialog dialog = new MaterialAlertDialogBuilder(apkExtractorActivity)
                .setTitle(apkExtractorActivity.getString(R.string.extracted_versions) + " - " + appInfo.name)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .create();
        dialog.show();

        // Calculate SHA-256 hash asynchronously ONLY after long click dialog opens
        if (!sameVersionStatusViews.isEmpty() && appInfo.filePath != null && new File(appInfo.filePath).exists()) {
            new Thread(() -> {
                try {
                    File installedFile = new File(appInfo.filePath);
                    String installedHash = HashUtil.hashFile(installedFile, "SHA-256");
                    for (int i = 0; i < sameVersionFiles.size(); i++) {
                        File extractedFile = sameVersionFiles.get(i);
                        ImageView iv = sameVersionStatusViews.get(i);
                        try {
                            String extractedHash = HashUtil.hashApkOrArchiveBase(extractedFile, "SHA-256");
                            boolean match = installedHash != null && installedHash.equalsIgnoreCase(extractedHash);
                            apkExtractorActivity.runOnUiThread(() -> {
                                if (match) {
                                    iv.setImageResource(R.drawable.ic_circle_filled_green_24);
                                    iv.clearColorFilter();
                                } else {
                                    iv.setImageResource(R.drawable.ic_circle_outline_24);
                                    iv.setColorFilter(0xFF888888, PorterDuff.Mode.SRC_IN);
                                }
                            });
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }).start();
        }
    }
}
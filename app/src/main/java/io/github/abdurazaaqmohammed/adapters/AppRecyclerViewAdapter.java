package io.github.abdurazaaqmohammed.adapters;

import static android.view.View.GONE;

import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.ApkExtractor.AppInfo;
import io.github.abdurazaaqmohammed.MPManager.R;

public class AppRecyclerViewAdapter extends RecyclerView.Adapter<AppRecyclerViewAdapter.ViewHolder> implements Filterable {

    public final APKExtractorActivity apkExtractorActivity;
    public final List<AppInfo> appInfoList;
    public final List<AppInfo> filteredAppInfoList;

    private Comparator<AppInfo> currentComparator;
    private AppInfoFilter filter;

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
            cardView.getContext().getTheme().resolveAttribute(com.google.android.material.R.attr.colorPrimary, primaryColor, true);
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
            sub.append(" • ").append(android.text.format.Formatter.formatFileSize(
                    holder.packageNameView.getContext(),
                    appInfo.totalSize));
        }
        holder.packageNameView.setText(sub.toString());
        holder.packageNameView.setMovementMethod(null);
        holder.packageNameView.setVisibility(View.VISIBLE);
        holder.appIconView.setVisibility(View.VISIBLE);
        holder.appIconView.setImageDrawable(appInfo.icon);
        holder.splitIconView.setVisibility(appInfo.isSplit ? View.VISIBLE : GONE);

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

        ViewHolder(View v) {
            super(v);
            cardView = (MaterialCardView) v;
            appName = v.findViewById(R.id.appName);
            packageNameView = v.findViewById(R.id.package_name_view);
            appIconView = v.findViewById(R.id.icon_view);
            splitIconView = v.findViewById(R.id.badge_view);
        }
    }
}
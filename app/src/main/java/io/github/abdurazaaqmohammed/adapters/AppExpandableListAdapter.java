package io.github.abdurazaaqmohammed.adapters;


import static android.view.View.GONE;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import android.util.TypedValue;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.ApkExtractor.AppInfo;
import io.github.abdurazaaqmohammed.MPManager.R;

public class AppExpandableListAdapter extends BaseExpandableListAdapter {

    public final APKExtractorActivity apkExtractorActivity;
    public final List<AppInfo> appInfoList;
    public final List<AppInfo> filteredAppInfoList;

    private Comparator<AppInfo> currentComparator = null;

    public AppExpandableListAdapter(APKExtractorActivity apkExtractorActivity, List<AppInfo> appInfoList) {
        this.apkExtractorActivity = apkExtractorActivity;
        this.appInfoList = appInfoList;
        this.filteredAppInfoList = new ArrayList<>(appInfoList);
    }

    public void setComparator(Comparator<AppInfo> comparator) {
        this.currentComparator = comparator;
    }

    @Override
    public int getGroupCount() {
        return filteredAppInfoList.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return filteredAppInfoList.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return filteredAppInfoList.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    AppInfoFilter filter = null;

    public Filter getFilter() {
        if (filter == null) {
            filter = new AppInfoFilter();
        }
        return filter;
    }

    private class AppInfoFilter extends Filter {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();

            if (TextUtils.isEmpty(constraint)) {
                results.values = new ArrayList<>(appInfoList);
            } else {
                List<AppInfo> filteredItems = new ArrayList<>();
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (AppInfo appInfo : appInfoList) {
                    if (appInfo.name.toLowerCase().contains(filterPattern) ||
                            appInfo.packageName.toLowerCase().contains(filterPattern)) {
                        filteredItems.add(appInfo);
                    }
                }
                results.values = filteredItems;
            }

            if (currentComparator != null) {
                List<AppInfo> sortedList = (List<AppInfo>) results.values;
                Collections.sort(sortedList, currentComparator);
                results.values = sortedList;
            }

            results.count = ((List<AppInfo>) results.values).size();
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            filteredAppInfoList.clear();
            if (results.values != null) {
                filteredAppInfoList.addAll((List<AppInfo>) results.values);
            }
            notifyDataSetChanged();
        }
    }

    public HashSet<Integer> selectedItems = new HashSet<>();

    public void toggleSelection(int position) {
        if (selectedItems.contains(position)) selectedItems.remove(position);
        else selectedItems.add(position);
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedItems.clear();
        apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
        notifyDataSetChanged();
    }

    public void addItem(AppInfo appInfo) {
        if (!appInfoList.contains(appInfo)) appInfoList.add(appInfo);
        filteredAppInfoList.add(appInfo);
        notifyDataSetChanged();
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        GroupViewHolder viewHolder;
        AppInfo appInfo = (AppInfo) getGroup(groupPosition);

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(apkExtractorActivity);
            convertView = inflater.inflate(R.layout.list_item, parent, false);

            viewHolder = new GroupViewHolder();
            viewHolder.appName = convertView.findViewById(R.id.appName);
            viewHolder.packageNameView = convertView.findViewById(R.id.package_name_view);
            viewHolder.appIconView = convertView.findViewById(R.id.icon_view);
            viewHolder.splitIconView = convertView.findViewById(R.id.badge_view);
            viewHolder.extractIconView = convertView.findViewById(R.id.extract);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (GroupViewHolder) convertView.getTag();
        }

        MaterialCardView cardView = (MaterialCardView) convertView;
        if (selectedItems.contains(groupPosition)) {
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
            convertView.setBackgroundColor(typedValue.data);
        }

        viewHolder.appName.setText(appInfo.name);
        viewHolder.packageNameView.setText(appInfo.packageName);
        viewHolder.appIconView.setImageDrawable(appInfo.icon);
        viewHolder.splitIconView.setVisibility(appInfo.isSplit ? View.VISIBLE : GONE);


        convertView.setOnClickListener(v -> {
            if (!selectedItems.isEmpty()) {
                toggleSelection(groupPosition);
                if (selectedItems.isEmpty()) {
                    apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
                }
            } else {
                apkExtractorActivity.showListViewDropdown(viewHolder.extractIconView, groupPosition, this);
            }
        });

        convertView.setOnLongClickListener(v -> {
            apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.VISIBLE);
            toggleSelection(groupPosition);
            return true;
        });

        viewHolder.extractIconView.setOnClickListener(v -> {
            if (!selectedItems.isEmpty()) {
                toggleSelection(groupPosition);
                if (selectedItems.isEmpty()) {
                    apkExtractorActivity.findViewById(R.id.confirmButton).setVisibility(View.INVISIBLE);
                }
            } else {
                apkExtractorActivity.showListViewDropdown(viewHolder.extractIconView, groupPosition, this);
            }
        });

        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        return convertView;
    }

    static class GroupViewHolder {
        TextView appName;
        TextView packageNameView;
        ImageView appIconView;
        View splitIconView;
        ImageView extractIconView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
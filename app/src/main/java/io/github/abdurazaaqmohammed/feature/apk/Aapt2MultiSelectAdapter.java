package io.github.abdurazaaqmohammed.feature.apk;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * Multi-choice adapter rendered inside the exposed dropdown menus. Values not
 * present in the APK are never added here, so users can only pick real specs.
 */
final class Aapt2MultiSelectAdapter extends BaseAdapter implements Filterable {
    private final Context context;
    private final List<String> all;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<String> visible = new ArrayList<>();

    Aapt2MultiSelectAdapter(Context context, List<String> values) {
        this.context = context;
        this.all = new ArrayList<>(values);
        this.visible.addAll(this.all);
    }

    void setSelected(List<String> values) {
        selected.clear();
        if (values != null) {
            for (String value : values) {
                String match = match(value);
                if (match != null) selected.add(match);
            }
        }
        notifyDataSetChanged();
    }

    List<String> getSelected() {
        return new ArrayList<>(selected);
    }

    String getValue() {
        return String.join(",", selected);
    }

    void toggleAt(int position) {
        if (position < 0 || position >= visible.size()) return;
        String value = visible.get(position);
        if (!selected.remove(value)) selected.add(value);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return visible.size();
    }

    @Override
    public String getItem(int position) {
        return visible.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.aapt2_dropdown_item, parent, false);
        }
        String value = getItem(position);
        CheckBox check = view.findViewById(R.id.aapt2_dropdown_check);
        TextView label = view.findViewById(R.id.aapt2_dropdown_label);
        label.setText(value);
        check.setChecked(selected.contains(value));
        return view;
    }

    private String match(String value) {
        if (value == null) return null;
        for (String candidate : all) {
            if (candidate.equalsIgnoreCase(value)) return candidate;
        }
        return null;
    }

    /** AutoCompleteTextView applies a filter after setText; keep every row visible. */
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.values = new ArrayList<>(all);
                results.count = all.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                visible.clear();
                visible.addAll((List<String>) results.values);
                notifyDataSetChanged();
            }
        };
    }
}

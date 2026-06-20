package io.github.abdurazaaqmohammed.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;
import io.github.abdurazaaqmohammed.MPManager.R;

public class DropdownAdapter extends BaseAdapter {
    private final Context context;
    private final List<String> options;
    private final List<Integer> icons;

    public DropdownAdapter(Context context, List<String> options, List<Integer> icons) {
        this.context = context;
        this.options = options;
        this.icons = icons;
    }

    @Override
    public int getCount() {
        return options.size();
    }

    @Override
    public Object getItem(int position) {
        return options.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_dropdown_option, parent, false);
        }
        
        ImageView icon = convertView.findViewById(R.id.optionIcon);
        TextView text = convertView.findViewById(R.id.optionText);
        
        icon.setImageResource(icons.get(position));
        text.setText(options.get(position));
        
        return convertView;
    }
}

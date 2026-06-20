package io.github.abdurazaaqmohammed.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.MPManager.R;

public class ExtractOptionAdapter extends BaseAdapter {

    private final Context context;
    private final String[] options;
    private final int[] icons;

    public ExtractOptionAdapter(Context context, String[] options, int[] icons) {
        this.context = context;
        this.options = options;
        this.icons = icons;
    }

    @Override
    public int getCount() {
        return options.length;
    }

    @Override
    public Object getItem(int position) {
        return options[position];
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

        icon.setImageResource(icons[position]);
        text.setText(options[position]);

        return convertView;
    }
}

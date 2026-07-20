package io.github.abdurazaaqmohammed.adapters;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.util.ArrayList;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ColorUtil;

public class BookmarksAdapter extends ArrayAdapter<File> {
    private final MainActivity context;
    public final ArrayList<File> values;

    public BookmarksAdapter(MainActivity context, ArrayList<File> values) {
        super(context, android.R.layout.simple_list_item_1, values);
        this.context = context;
        this.values = values;
    }
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_file, parent, false);
        }
        File file = values.get(position);
        TextView fileNameView = convertView.findViewById(R.id.fileName);
        ImageView fileIconView = convertView.findViewById(R.id.fileIcon);
        TextView fileDateView = convertView.findViewById(R.id.fileDate);

        fileNameView.setText(file.getName());
        fileDateView.setVisibility(View.GONE);

        if (file.isFile()) {
            fileIconView.setImageResource(android.R.drawable.ic_menu_add);
            // TODO: Appropriate file icons, fetching from apk, image thumbnail, etc.
        } else {
            fileIconView.setImageDrawable(ResourcesCompat.getDrawable(context.rss, R.drawable.folder__61764____the_noun_project, context.getTheme()));
            ColorUtil.changeImageColor(fileIconView.getDrawable(), Color.WHITE);
        }
        return convertView;
    }
}
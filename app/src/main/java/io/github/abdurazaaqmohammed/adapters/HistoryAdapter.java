package io.github.abdurazaaqmohammed.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import android.text.TextUtils;

import java.io.File;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ColorUtil;

public class HistoryAdapter extends ArrayAdapter<MainActivity.NavigationHistoryEntry> {
    private final MainActivity context;

    public HistoryAdapter(MainActivity context, List<MainActivity.NavigationHistoryEntry> values) {
        super(context, android.R.layout.simple_list_item_1, values);
        this.context = context;
    }

    public void setData(List<MainActivity.NavigationHistoryEntry> values) {
        clear();
        addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_file, parent, false);
        }
        MainActivity.NavigationHistoryEntry entry = getItem(position);
        if (entry == null) return convertView;
        TextView fileNameView = convertView.findViewById(R.id.fileName);
        ImageView fileIconView = convertView.findViewById(R.id.fileIcon);
        TextView fileDateView = convertView.findViewById(R.id.fileDate);

        File file = entry.file();
        String fileName = file.getName();
        String path = file.getPath();
        if (entry.isZip() && !TextUtils.isEmpty(entry.zipPath())) {
            fileName = new File(entry.zipPath()).getName() + " (" + fileName + ")";
            path = path + "!/" + entry.zipPath();
        }
        fileNameView.setText(fileName);
        fileDateView.setText(path);
        fileDateView.setContentDescription(context.rss.getString(R.string.filepath));

        fileIconView.setImageDrawable(ResourcesCompat.getDrawable(context.rss,
                entry.isZip() ? R.drawable.baseline_folder_zip_24 : R.drawable.folder__61764____the_noun_project,
                context.getTheme()));
        ColorUtil.changeImageColor(fileIconView.getDrawable(), (context.theme == R.style.Theme_MyApp_Light) ? Color.BLACK : Color.WHITE);
        return convertView;
    }
}

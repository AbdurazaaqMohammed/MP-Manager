package io.github.abdurazaaqmohammed.adapters;

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
import java.util.ArrayList;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.features.files.NavigationHistoryEntry;
import io.github.abdurazaaqmohammed.core.ui.util.ThemeAttrs;
import io.github.abdurazaaqmohammed.utils.ColorUtil;

public class HistoryAdapter extends ArrayAdapter<NavigationHistoryEntry> {
    private final MainActivity context;

    public HistoryAdapter(MainActivity context, List<NavigationHistoryEntry> values) {
        super(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        this.context = context;
        if (values != null) addAll(values);
    }

    public void setData(List<NavigationHistoryEntry> values) {
        setNotifyOnChange(false);
        clear();
        if (values != null) addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.list_file, parent, false);
        }
        NavigationHistoryEntry entry = getItem(position);
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
        ColorUtil.changeImageColor(fileIconView.getDrawable(), ThemeAttrs.onSurface(context));
        return convertView;
    }
}

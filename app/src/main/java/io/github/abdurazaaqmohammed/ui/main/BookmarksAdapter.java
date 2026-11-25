package io.github.abdurazaaqmohammed.ui.main;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;

import io.github.abdurazaaqmohammed.MPManager.R;


public class BookmarksAdapter extends ArrayAdapter<File> {

    public interface Callbacks {
        String labelOf(File file);

        boolean isBatchMode();

        boolean isBatchSelected(int position);
    }

    private final MainActivity context;
    public final ArrayList<File> values;
    private final Callbacks callbacks;

    public BookmarksAdapter(MainActivity context, ArrayList<File> values, Callbacks callbacks) {
        super(context, R.layout.item_bookmark, values);
        this.context = context;
        this.values = values;
        this.callbacks = callbacks;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_bookmark, parent, false);
        }
        File file = values.get(position);
        TextView fileNameView = convertView.findViewById(R.id.bookmarkName);
        ImageView fileIconView = convertView.findViewById(R.id.bookmarkIcon);
        ImageView dragHandle = convertView.findViewById(R.id.bookmarkDragHandle);

        String label = callbacks != null ? callbacks.labelOf(file) : file.getName();
        fileNameView.setText(label);

        fileIconView.setColorFilter(null);
        fileIconView.setImageDrawable(FileIconSpec.forPath(file.getPath(), !file.isFile(), context.theme).drawable(context));

        boolean batch = callbacks != null && callbacks.isBatchMode();
        convertView.setBackgroundColor(batch && callbacks.isBatchSelected(position)
                ? MaterialColors.getColor(convertView, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY)
                : Color.TRANSPARENT);

        if (dragHandle != null) {
            dragHandle.setVisibility(View.GONE);
        }

        return convertView;
    }
}

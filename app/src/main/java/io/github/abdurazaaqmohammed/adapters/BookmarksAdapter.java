package io.github.abdurazaaqmohammed.adapters;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;


public class BookmarksAdapter extends ArrayAdapter<File> {

    public interface Callbacks {
        String labelOf(File file);

        void onDragHandleTouched(View handle, int position, MotionEvent initialEvent);

        boolean isBatchMode();

        boolean isBatchSelected(int position);

        boolean isDragging();
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

        String ext = "." + FilenameUtils.getExtension(file.getName()).toLowerCase(Locale.ROOT);
        if (file.isFile()) {
            int ic;
            if (ext.equals(".apk")) ic = R.drawable.apk_document_24px;
            else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) ic = R.drawable.image_24px;
            else if (FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) ic = R.drawable.video_24px;
            else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) ic = R.drawable.music_24px;
            else if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) ic = R.drawable.baseline_text_snippet_24;
            else if (FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) ic = R.drawable.baseline_folder_zip_24;
            else ic = R.drawable.baseline_insert_drive_file_24;
            fileIconView.setImageResource(ic);
        } else {
            fileIconView.setImageResource(R.drawable.folder_24px);
        }
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
        ColorUtil.changeImageColor(fileIconView.getDrawable(), typedValue.data);

        boolean batch = callbacks != null && callbacks.isBatchMode();
        convertView.setBackgroundColor(batch && callbacks.isBatchSelected(position)
                ? MaterialColors.getColor(convertView, com.google.android.material.R.attr.colorPrimaryContainer, Color.LTGRAY)
                : Color.TRANSPARENT);

        boolean dragging = callbacks != null && callbacks.isDragging();
        if (dragHandle != null) {
            dragHandle.setVisibility(batch && !dragging ? View.VISIBLE : View.GONE);
            if (batch && !dragging) {
                dragHandle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN && callbacks != null) {
                        callbacks.onDragHandleTouched(v, position, event);
                        return true;
                    }
                    return false;
                });
            }
        }
        return convertView;
    }
}

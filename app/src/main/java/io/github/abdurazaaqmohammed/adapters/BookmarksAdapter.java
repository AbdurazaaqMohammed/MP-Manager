package io.github.abdurazaaqmohammed.adapters;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;

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

        String fileName = file.getName();
        String ext = FilenameUtils.getExtension(fileName);
        ext = "." + ext.toLowerCase(Locale.ROOT);
        fileNameView.setText(fileName);
        fileDateView.setText(file.getPath());
        fileDateView.setContentDescription(context.rss.getString(R.string.filepath));

        if (file.isFile()) {
            int ic;
            if(ext.equals(".apk")) ic = R.drawable.apk_document_24px;
            else if(FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) ic = R.drawable.image_24px;
            else if(FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) ic = R.drawable.video_24px;
            else if(FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS)) ic = R.drawable.music_24px;
            else if(FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)) ic = R.drawable.baseline_text_snippet_24;
            else if(FileUtils.matchExt(ext, FileUtils.ARCHIVE_EXTS)) ic = R.drawable.baseline_folder_zip_24;
            else ic = R.drawable.baseline_insert_drive_file_24;
            fileIconView.setImageResource(ic);
        } else {
            fileIconView.setImageDrawable(ResourcesCompat.getDrawable(context.rss, R.drawable.folder__61764____the_noun_project, context.getTheme()));
        }
        ColorUtil.changeImageColor(fileIconView.getDrawable(), (context.theme == R.style.Theme_MyApp_Light) ? Color.BLACK : Color.WHITE);
        return convertView;
    }
}
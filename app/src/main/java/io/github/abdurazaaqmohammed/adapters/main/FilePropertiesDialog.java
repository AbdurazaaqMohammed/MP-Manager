package io.github.abdurazaaqmohammed.adapters.main;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;

public class FilePropertiesDialog {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final ChecksumDialogs checksumDialogs;

    public FilePropertiesDialog(MainActivity context, DialogUtil dialogUtil, ChecksumDialogs checksumDialogs) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.checksumDialogs = checksumDialogs;
    }

    public void show(boolean multi, int position, Object[] values, Set<Integer> selectedPositions,
                     boolean isInZip, File file, ZipEntryInfo entry, String fileName, String displayName) {
        View propView = LayoutInflater.from(context).inflate(R.layout.dialog_properties, null);
        TextView propTitle = propView.findViewById(R.id.propertyTitle);
        TextView propSubtitle = propView.findViewById(R.id.propertySubtitle);
        ImageView propIcon = propView.findViewById(R.id.propertyIcon);
        LinearLayout propRows = propView.findViewById(R.id.propertyRows);
        LinearLayout checksumSection = propView.findViewById(R.id.checksumSection);
        LinearLayout checksumRows = propView.findViewById(R.id.checksumRows);

        propTitle.setText(displayName);

        boolean isFolder = isInZip ? entry.isDirectory() : file.isDirectory();
        String typeStr = multi ? context.getString(R.string.items, selectedPositions.size())
                : (isFolder ? context.getString(R.string.folder) : context.getString(R.string.file));
        propSubtitle.setText(typeStr);

        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        boolean hasIcon = false;
        if (!isInZip && !multi && file.isFile() && FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bmp != null) {
                propIcon.setImageBitmap(Bitmap.createScaledBitmap(bmp, dp(48), dp(48), true));
                propIcon.setColorFilter(null);
                hasIcon = true;
            }
        }
        if (!hasIcon) {
            propIcon.setImageResource(isFolder ? R.drawable.folder_24px : R.drawable.baseline_insert_drive_file_24);
            propIcon.setColorFilter(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        }
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, Color.GRAY));
        propIcon.setBackground(iconBg);
        int iconPad = dp(10);
        propIcon.setPadding(iconPad, iconPad, iconPad, iconPad);

        addPropertyRow(propRows, context.getString(R.string.name), displayName);
        if (!isInZip && file.getParentFile() != null)
            addPropertyRow(propRows, context.getString(R.string.parent), file.getParentFile().getName());
        addPropertyRow(propRows, context.getString(R.string.type), typeStr);

        long size = 0;
        if (multi) {
            for (int p : selectedPositions) {
                Object o = values[p];
                if (o instanceof File) size += ((File) o).length();
                else if (o instanceof ZipEntryInfo) size += ((ZipEntryInfo) o).getSize();
            }
        } else size = isInZip ? entry.getSize() : file.length();
        TextView sizeValue = addPropertyRow(propRows, context.getString(R.string.size), Formatter.formatFileSize(context, size));

        if (!isInZip) {
            addPropertyRow(propRows, context.getString(R.string.modified),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm").format(file.lastModified()));
            if (!multi && file.isDirectory()) {
                TextView sizeText = sizeValue;
                new Thread(() -> {
                    long folderSize = getFolderSize(file, null);
                    context.handler.post(() -> sizeText.setText(FileSize.getHumanReadableFileSize(folderSize)));
                }).start();
            }
        }

        if (!isInZip && !multi && file.isFile()) {
            propView.findViewById(R.id.computeChecksums).setOnClickListener(btn -> {
                checksumRows.removeAllViews();
                checksumDialogs.startChecksumComputation(checksumRows, file);
            });
            checksumDialogs.startChecksumComputation(checksumRows, file);
        } else {
            checksumSection.setVisibility(View.GONE);
        }

        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.properties))
                .setView(propView)
                .setPositiveButton(android.R.string.ok, null)
                .create());
    }

    private long getFolderSize(File file, TextView toUpdate) {
        File[] files = file.listFiles();
        if (files == null)
            return 0;
        long length = 0;
        for (File f : files) {
            if (f.isFile()) {
                length += f.length();
            } else if (f.isDirectory()) {
                length += getFolderSize(f, toUpdate);
            }
        }
        if (toUpdate != null)
            toUpdate.setText(FileSize.getHumanReadableFileSize(length));
        return length;
    }

    private TextView addPropertyRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        labelView.setTextColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.WHITE));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));

        TextView valueView = new TextView(context);
        valueView.setText(value == null ? "—" : value);
        valueView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        valueView.setTextIsSelectable(true);
        valueView.setOnLongClickListener(v -> {
            CopyUtil.copyToClipboard(context, valueView.getText());
            return true;
        });
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));

        row.addView(labelView);
        row.addView(valueView);
        container.addView(row);
        return valueView;
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

package io.github.abdurazaaqmohammed.adapters.main;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import com.google.android.material.color.MaterialColors;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;

public class FilePropertiesDialog {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final ChecksumDialogs checksumDialogs;
    private final PermissionsEditorHelper permissionsEditor;

    public FilePropertiesDialog(MainActivity context, DialogUtil dialogUtil, ChecksumDialogs checksumDialogs) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.checksumDialogs = checksumDialogs;
        this.permissionsEditor = new PermissionsEditorHelper(context);
    }

    public void show(boolean multi, Object[] values, Set<Integer> selectedPositions,
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
            TextView modifiedView = addPropertyRow(propRows, context.getString(R.string.modified),
                    new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(file.lastModified()));
            modifiedView.setOnClickListener(v -> showEditLastModifiedDialog(file, modifiedView));
            if (!multi && file.isFile()) {
                addPropertyRow(propRows, context.getString(R.string.reported_mime), MimeUtil.getReportedMimeType(context, file));
                TextView realMimeView = addPropertyRow(propRows, context.getString(R.string.real_mime), "…");
                new Thread(() -> {
                    String realMime = MimeUtil.getRealMimeType(file);
                    context.handler.post(() -> realMimeView.setText(realMime != null ? realMime : "—"));
                }).start();
            }
            if (!multi && file.isDirectory()) {
                TextView sizeText = sizeValue;
                new Thread(() -> {
                    long folderSize = getFolderSize(file, null);
                    context.handler.post(() -> sizeText.setText(FileSize.getHumanReadableFileSize(folderSize)));
                }).start();
            }
        } else if (!multi && !entry.isDirectory()) {
            long modTime = entry.getLastModified();
            if (modTime > 0) {
                addPropertyRow(propRows, context.getString(R.string.modified),
                        new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(modTime));
            }
        }

        RootManager rm = RootManager.getInstance(context);
        if (!isInZip && !multi && rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
            TextView permsTitle = new TextView(context);
            permsTitle.setText(R.string.root_permissions);
            permsTitle.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            permsTitle.setPadding(0, dp(12), 0, dp(4));
            propRows.addView(permsTitle);

            View permsEditor = permissionsEditor.createPermissionsEditor("0000");
            propRows.addView(permsEditor);

            new Thread(() -> {
                String perms = rm.getPermissions(file.getAbsolutePath());
                context.handler.post(() -> {
                    if (perms != null) {
                        int[] bits = parsePermsForEditor(perms);
                        String numeric = String.valueOf(bits[0] * 1000 + bits[1] * 100 + bits[2] * 10 + bits[3]);
                        propRows.removeView(permsEditor);
                        View refreshedEditor = permissionsEditor.createPermissionsEditor(numeric);
                        propRows.addView(refreshedEditor, propRows.getChildCount() - 1);
                    }
                });
            }).start();

            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                    .setTitle(context.getString(R.string.properties))
                    .setView(wrapInScroll(propView))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        String permsStr = String.valueOf(permissionsEditor.getNumericValue());
                        if (!permsStr.equals("0")) {
                            new Thread(() -> {
                                try {
                                    rm.chmod(file.getAbsolutePath(), permsStr);
                                    Extensions.showMessage(context, R.string.permissions_updated);
                                } catch (Exception e) {
                                    context.handler.post(() ->
                                            new ErrorUtil(context).showError(e));
                                }
                            }).start();
                        }
                    })
                    .create());
            return;
        }

        if (!isInZip && !multi && file.isFile()) {
            propView.findViewById(R.id.computeChecksums).setOnClickListener(btn -> {
                checksumRows.removeAllViews();
                checksumDialogs.startChecksumComputation(checksumRows, file);
            });
            checksumDialogs.startChecksumComputation(checksumRows, file);
        } else if (isInZip && !multi && !entry.isDirectory()) {
            checksumDialogs.addCrc32Row(checksumRows, entry.computeCrc32());
            propView.findViewById(R.id.computeChecksums).setOnClickListener(btn -> {
                checksumRows.removeAllViews();
                checksumDialogs.startZipEntryChecksumComputation(checksumRows, entry.getZipFile(), entry.getFullPath());
            });
        } else {
            checksumSection.setVisibility(View.GONE);
        }

        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.properties))
                .setView(wrapInScroll(propView))
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

    private void showEditLastModifiedDialog(File file, TextView modifiedView) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(file.lastModified());
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);
        int day = cal.get(Calendar.DAY_OF_MONTH);
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        new DatePickerDialog(context, (datePicker, y, m, d) -> new TimePickerDialog(context, (timePicker, h, min) -> {
            Calendar result = Calendar.getInstance();
            result.set(y, m, d, h, min, 0);
            long newTime = result.getTimeInMillis();
            RootManager rm = RootManager.getInstance(context);
            if (rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
                new Thread(() -> {
                    try {
                        String touchTime = String.format(Locale.US, "%04d%02d%02d%02d%02d.%02d",
                                y, m + 1, d, h, min, 0);
                        rm.execute("touch -t " + touchTime + " '" + file.getAbsolutePath() + "'");
                        context.handler.post(() -> {
                            modifiedView.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(newTime));
                            Extensions.showMessage(context, "Last modified updated");
                        });
                    } catch (Exception e) { new ErrorUtil(context).showError(e); }
                }).start();
            } else {
                if (file.setLastModified(newTime)) {
                    modifiedView.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(newTime));
                } else {
                    Extensions.showMessage(context, "Failed to update last modified");
                }
            }
        }, hour, minute, true).show(), year, month, day).show();
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

    private int[] parsePermsForEditor(String perms) {
        int[] result = new int[4];
        if (TextUtils.isEmpty(perms)) return result;
        try {
            String clean = perms.trim();
            int i = Integer.parseInt(String.valueOf(clean.charAt(0)));
            int i1 = Integer.parseInt(String.valueOf(clean.charAt(1)));
            int i2 = Integer.parseInt(String.valueOf(clean.charAt(2)));
            if (clean.length() == 3) {
                result[1] = i;
                result[2] = i1;
                result[3] = i2;
                result[0] = 0;
            } else if (clean.length() == 4) {
                result[0] = i;
                result[1] = i1;
                result[2] = i2;
                result[3] = Integer.parseInt(String.valueOf(clean.charAt(3)));
            }
        } catch (NumberFormatException e) {
            result = new int[4];
        }
        return result;
    }

    private View wrapInScroll(View content) {
        ScrollView sv = new ScrollView(context);
        sv.addView(content);
        return sv;
    }
}

package io.github.abdurazaaqmohammed.adapters.main;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.media.MediaScannerConnection;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import com.google.android.material.textfield.TextInputLayout;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.data.prefs.DirSizeCache;
import io.github.abdurazaaqmohammed.utils.CopyUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.KeyboardUtil;
import io.github.abdurazaaqmohammed.utils.RenameHelper;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;

public class FilePropertiesDialog {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final ChecksumDialogs checksumDialogs;

    public FilePropertiesDialog(MainActivity context, DialogUtil dialogUtil, ChecksumDialogs checksumDialogs) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.checksumDialogs = checksumDialogs;
    }

    public void show(boolean multi, Object[] values, Set<Integer> selectedPositions,
                     boolean isInZip, File file, ZipEntryInfo entry, String fileName, String displayName) {
        View propView = LayoutInflater.from(context).inflate(R.layout.dialog_properties, null);
        TextView propTitle = propView.findViewById(R.id.propertyTitle);
        TextView propSubtitle = propView.findViewById(R.id.propertySubtitle);
        ImageView propIcon = propView.findViewById(R.id.propertyIcon);
        LinearLayout propRows = propView.findViewById(R.id.propertyRows);

        propTitle.setText(displayName);
        propTitle.setOnLongClickListener(v -> {
            String pathToCopy;
            if (isInZip) {
                pathToCopy = (entry != null && entry.getFullPath() != null) ? entry.getFullPath() : fileName;
            } else if (file != null) {
                pathToCopy = file.getAbsolutePath();
            } else {
                pathToCopy = displayName;
            }
            CopyUtil.copyToClipboard(context, pathToCopy);
            return true;
        });

        boolean isFolder = isInZip ? entry.isDirectory() : file.isDirectory();
        String typeStr = multi ? context.getString(R.string.items, selectedPositions.size())
                : (isFolder ? context.getString(R.string.folder) : context.getString(R.string.file));
        propSubtitle.setText(typeStr);

        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        boolean isImage = !isInZip && !multi && file != null && file.isFile() && FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS);
        boolean hasIcon = false;
        if (isImage) {
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bmp != null) {
                propIcon.setImageBitmap(Bitmap.createScaledBitmap(bmp, dp(48), dp(48), true));
                propIcon.setColorFilter(null);
                hasIcon = true;
            }
        }
        if (!hasIcon) {
            propIcon.setImageResource(isFolder ? R.drawable.folder_24px : R.drawable.baseline_insert_drive_file_24);
            propIcon.setColorFilter(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        }
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setShape(GradientDrawable.OVAL);
        iconBg.setColor(MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, Color.GRAY));
        propIcon.setBackground(iconBg);
        int iconPad = dp(10);
        propIcon.setPadding(iconPad, iconPad, iconPad, iconPad);

        final TextView[] nameHolder = new TextView[1];
        final AlertDialog[] selfDialog = new AlertDialog[1];
        if (!isInZip && !multi) {
            nameHolder[0] = addPropertyRow(propRows, context.getString(R.string.name), displayName,
                    R.drawable.baseline_drive_file_rename_outline_24,
                    v -> showRenameFromProperties(file, fileName, nameHolder[0], propTitle, selfDialog));
        } else {
            nameHolder[0] = addPropertyRow(propRows, context.getString(R.string.name), displayName);
        }
        if (!isInZip && file.getParentFile() != null) {
            String parentPath = file.getParentFile().getAbsolutePath();
            if (!parentPath.endsWith("/")) parentPath += "/";
            addPropertyRow(propRows, context.getString(R.string.parent), parentPath);
        }
        if (!isInZip && !multi && file != null) {
            String symTarget = getSymlinkTarget(file);
            if (symTarget != null) {
                final TextView[] symHolder = new TextView[1];
                symHolder[0] = addPropertyRow(propRows, "Symlink", symTarget,
                        R.drawable.edit_24px,
                        v -> showEditSymlinkDialog(file, symHolder[0]));
            }
        }

        long size = 0;
        if (multi) {
            if (values != null && selectedPositions != null) {
                for (int p : selectedPositions) {
                    if (p < 0 || p >= values.length) continue;
                    Object o = values[p];
                    if (o instanceof File f) {
                        Long cached = DirSizeCache.peek(f);
                        size += (cached != null) ? cached : f.length();
                    } else if (o instanceof ZipEntryInfo) {
                        size += ((ZipEntryInfo) o).getSize();
                    }
                }
            }
        } else {
            if (isInZip) {
                size = entry.getSize();
            } else if (file != null) {
                Long cached = DirSizeCache.peek(file);
                size = (cached != null) ? cached : file.length();
            }
        }
        TextView sizeValue = addPropertyRow(propRows, context.getString(R.string.size), FileSize.getHumanReadableFileSize(size));

        final List<File> selectedFilesList = new ArrayList<>();
        if (!isInZip) {
            if (multi) {
                if (values != null && selectedPositions != null) {
                    for (int p : selectedPositions) {
                        if (p >= 0 && p < values.length && values[p] instanceof File f) {
                            selectedFilesList.add(f);
                        }
                    }
                }
            } else if (file != null) {
                selectedFilesList.add(file);
            }
        }
        boolean hasAnyImages = false;
        if (!isInZip) {
            for (File sf : selectedFilesList) {
                if (sf.isFile() && FileUtils.matchExt('.' + FilenameUtils.getExtension(sf.getName()).toLowerCase(), FileUtils.IMAGE_EXTS)) {
                    hasAnyImages = true;
                    break;
                }
            }
        }

        if (!isInZip && !selectedFilesList.isEmpty()) {
            final TextView[] modifiedHolder = new TextView[1];
            long displayMtime = file != null ? file.lastModified() : selectedFilesList.get(0).lastModified();
            final TextView modifiedView = addPropertyRow(propRows, context.getString(R.string.modified),
                    UiPrefs.formatDate(context, displayMtime),
                    R.drawable.edit_24px,
                    v -> showEditLastModifiedDialog(selectedFilesList, modifiedHolder[0]));
            if (displayMtime > 0) modifiedView.setTag(formatFullTimestamp(displayMtime));
            modifiedHolder[0] = modifiedView;
            if (displayMtime <= 0 && !multi && file != null) {
                new Thread(() -> {
                    long m = 0;
                    try {
                        m = AccessManager.getMtime(context, file.getAbsolutePath());
                    } catch (Exception ignored) {
                    }
                    if (m <= 0 && isImage) {
                        try {
                            String path = resolveAccessiblePath(file.getAbsolutePath());
                            androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(path);
                            String dt = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME);
                            m = parseExifDate(dt);
                        } catch (Exception ignored) {}
                    }
                    if (m <= 0) return;
                    long finalM = m;
                    context.handler.post(() -> {
                        try {
                            modifiedView.setText(UiPrefs.formatDate(context, finalM));
                            modifiedView.setTag(formatFullTimestamp(finalM));
                        } catch (Exception ignored) {
                        }
                    });
                }).start();
            }

            if (hasAnyImages) {
                final TextView[] dateTakenHolder = new TextView[1];
                String dateTakenDisplay = "—";
                String fullDateTaken = null;
                File sampleImg = null;
                for (File sf : selectedFilesList) {
                    if (sf.isFile() && FileUtils.matchExt('.' + FilenameUtils.getExtension(sf.getName()).toLowerCase(), FileUtils.IMAGE_EXTS)) {
                        sampleImg = sf;
                        break;
                    }
                }
                if (sampleImg != null) {
                    try {
                        String samplePath = resolveAccessiblePath(sampleImg.getAbsolutePath());
                        androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(samplePath);
                        String dtOriginal = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL);
                        if (!TextUtils.isEmpty(dtOriginal)) {
                            long millis = parseExifDate(dtOriginal);
                            if (millis > 0) {
                                dateTakenDisplay = UiPrefs.formatDate(context, millis);
                                fullDateTaken = formatFullTimestamp(millis);
                            } else {
                                dateTakenDisplay = dtOriginal;
                                fullDateTaken = dtOriginal;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                final TextView dateTakenView = addPropertyRow(propRows, context.getString(R.string.date_taken),
                        dateTakenDisplay,
                        R.drawable.edit_24px,
                        v -> showEditDateTakenDialog(selectedFilesList, dateTakenHolder[0]));
                if (fullDateTaken != null) dateTakenView.setTag(fullDateTaken);
                dateTakenHolder[0] = dateTakenView;
            }

            boolean hasDirectories = false;
            if (multi) {
                for (File f : selectedFilesList) {
                    if (f.isDirectory()) {
                        hasDirectories = true;
                        break;
                    }
                }
            } else if (file != null && file.isDirectory()) {
                hasDirectories = true;
            }

            if (hasDirectories) {
                new Thread(() -> {
                    long totalComputedSize = 0;
                    for (File f : selectedFilesList) {
                        if (f.isDirectory()) {
                            totalComputedSize += getFolderSize(f, null);
                        } else {
                            totalComputedSize += f.length();
                        }
                    }
                    long finalTotal = totalComputedSize;
                    if (!multi && file != null && file.isDirectory()) {
                        DirSizeCache.record(file, finalTotal);
                        DirSizeCache.scheduleSave();
                    }
                    context.handler.post(() -> sizeValue.setText(FileSize.getHumanReadableFileSize(finalTotal)));
                }).start();
            }
        } else if (!multi && !entry.isDirectory()) {
            long modTime = entry.getLastModified();
            if (modTime > 0) {
                addPropertyRow(propRows, context.getString(R.string.modified),
                        UiPrefs.formatDate(context, modTime));
            }
        }

        RootManager rm = RootManager.getInstance(context);
        boolean canEditPerms = !isInZip && !multi && rm.isRootFileOpsEnabled() && rm.isRootAvailable();

        if (!isInZip && !multi && file != null) {
            final TextView[] permViewHolder = new TextView[1];
            View.OnClickListener openPermsAction = v -> {
                if (canEditPerms) {
                    showPermissionsDialog(file, rm, permViewHolder[0]);
                } else {
                    Extensions.showMessage(context, R.string.storage_root_needed);
                }
            };

            TextView permView = addPropertyRow(propRows, context.getString(R.string.permissions), "…",
                    R.drawable.edit_24px, openPermsAction);
            permViewHolder[0] = permView;
            permView.setTypeface(Typeface.MONOSPACE);

            final TextView[] ownerViewHolder = new TextView[1];
            View.OnClickListener openOwnerAction = v -> {
                if (canEditPerms) {
                    showOwnerDialog(file, rm, ownerViewHolder[0]);
                } else {
                    Extensions.showMessage(context, R.string.storage_root_needed);
                }
            };
            TextView ownerView = addPropertyRow(propRows, context.getString(R.string.owner), "…",
                    R.drawable.edit_24px, openOwnerAction);
            ownerViewHolder[0] = ownerView;
            ownerView.setTypeface(Typeface.MONOSPACE);

            new Thread(() -> {
                String symbolic = null;
                String numeric = null;
                String ownerStr = null;
                if (canEditPerms) {
                    numeric = rm.getPermissions(file.getAbsolutePath());
                    ownerStr = rm.getOwner(file.getAbsolutePath());
                }
                if (numeric == null || numeric.isEmpty()) {
                    try {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                                Runtime.getRuntime().exec(new String[]{"stat", "-c", "%a", file.getAbsolutePath()}).getInputStream()));
                        numeric = reader.readLine();
                        if (numeric != null) numeric = numeric.trim();
                    } catch (Exception ignored) {}
                }
                if (ownerStr == null || ownerStr.isEmpty()) {
                    try {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                                Runtime.getRuntime().exec(new String[]{"stat", "-c", "%U:%G", file.getAbsolutePath()}).getInputStream()));
                        ownerStr = reader.readLine();
                        if (ownerStr != null) ownerStr = ownerStr.trim();
                    } catch (Exception ignored) {}
                }
                if (numeric != null && (numeric.length() == 3 || numeric.length() == 4)) {
                    symbolic = PermissionsEditorHelper.numericToSymbolic(numeric);
                }
                final String permText = (symbolic != null && numeric != null) ? (symbolic + " (" + numeric + ")") : "—";
                final String finalOwner = (ownerStr != null && !ownerStr.isEmpty()) ? ownerStr : "—";
                context.handler.post(() -> {
                    permView.setText(permText);
                    ownerView.setText(finalOwner);
                });
            }).start();
        }

        var dialogBuilder = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.properties))
                .setView(wrapInScroll(propView))
                .setPositiveButton(R.string.close, null);

        if (!isInZip && !multi && file != null && file.isFile()) {
            dialogBuilder.setNeutralButton(R.string.mime, (d, w) -> showMimeDialog(file));
        }

        selfDialog[0] = dialogBuilder.create();
        dialogUtil.styleAlertDialog(selfDialog[0]);
    }

    private void showOwnerDialog(File file, RootManager rm, TextView ownerView) {
        new Thread(() -> {
            String currentOwner = rm.getOwner(file.getAbsolutePath());
            if (currentOwner == null || currentOwner.isEmpty()) currentOwner = "root:root";
            File parent = file.getParentFile();
            java.util.LinkedHashSet<String> candidateOwners = new java.util.LinkedHashSet<>();
            candidateOwners.add(currentOwner);

            if (parent != null) {
                try {
                    // Query available owners/groups in this directory using stat
                    RootManager.ShellResult result = rm.executeFs("stat -c '%U:%G' " + RootManager.escapeShellArg(parent.getAbsolutePath()) + "/* 2>/dev/null | sort -u | head -n 30", 30);
                    if (result.isSuccess() && result.output() != null) {
                        for (String line : result.output().split("\\r?\\n")) {
                            line = line.trim();
                            if (!line.isEmpty() && line.contains(":")) candidateOwners.add(line);
                        }
                    }
                } catch (Exception ignored) {}
            }
            // Standard common Android owners
            candidateOwners.add("root:root");
            candidateOwners.add("media_rw:media_rw");
            candidateOwners.add("system:system");
            candidateOwners.add("shell:shell");

            final List<String> ownerList = new ArrayList<>(candidateOwners);
            final String finalCurrent = currentOwner;
            int selectedIdx = ownerList.indexOf(currentOwner);
            if (selectedIdx == -1) selectedIdx = 0;
            final int initialSelected = selectedIdx;
            final int[] checkedItem = new int[]{initialSelected};

            context.handler.post(() -> {
                AlertDialog ownerDialog = dialogUtil.getDialogBuilder()
                        .setTitle(context.getString(R.string.owner))
                        .setSingleChoiceItems(ownerList.toArray(new String[0]), initialSelected, (d, which) -> checkedItem[0] = which)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (checkedItem[0] >= 0 && checkedItem[0] < ownerList.size()) {
                                String selectedOwner = ownerList.get(checkedItem[0]);
                                new Thread(() -> {
                                    try {
                                        rm.executeFs("chown " + selectedOwner + " " + RootManager.escapeShellArg(file.getAbsolutePath()), 30);
                                        rm.invalidateListCache();
                                        context.handler.post(() -> {
                                            if (ownerView != null) ownerView.setText(selectedOwner);
                                            Extensions.showMessage(context, R.string.owner_updated);
                                        });
                                    } catch (Exception e) {
                                        context.handler.post(() -> new ErrorUtil(context).showError(e));
                                    }
                                }).start();
                            }
                        })
                        .create();
                dialogUtil.styleAlertDialog(ownerDialog);
                ownerDialog.show();
            });
        }).start();
    }

    private void showPermissionsDialog(File file, RootManager rm, TextView permView) {
        PermissionsEditorHelper editor = new PermissionsEditorHelper(context);
        View editorView = editor.createPermissionsEditor("0000");

        new Thread(() -> {
            String perms = rm.getPermissions(file.getAbsolutePath());
            context.handler.post(() -> {
                if (perms != null && !perms.isEmpty()) {
                    editor.applyNumericPerms(perms);
                }
            });
        }).start();

        AlertDialog permDialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.permissions))
                .setView(wrapInScroll(editorView))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (!editor.isReady()) return;
                    String permsStr = String.valueOf(editor.getNumericValue());
                    new Thread(() -> {
                        try {
                            rm.chmodFs(file.getAbsolutePath(), permsStr);
                            rm.invalidateListCache();
                            String updatedSymbolic = PermissionsEditorHelper.numericToSymbolic(permsStr);
                            final String newDisplay = updatedSymbolic + " (" + permsStr + ")";
                            context.handler.post(() -> {
                                if (permView != null) permView.setText(newDisplay);
                                Extensions.showMessage(context, R.string.permissions_updated);
                            });
                        } catch (Exception e) {
                            context.handler.post(() -> new ErrorUtil(context).showError(e));
                        }
                    }).start();
                })
                .create();
        dialogUtil.styleAlertDialog(permDialog);
    }

    private void showMimeDialog(File file) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, dp(12), pad, dp(12));

        addPropertyRow(layout, context.getString(R.string.reported_mime), MimeUtil.getReportedMimeType(context, file));
        TextView realMimeView = addPropertyRow(layout, context.getString(R.string.real_mime), "…");

        new Thread(() -> {
            String realMime = MimeUtil.getRealMimeType(file);
            context.handler.post(() -> realMimeView.setText(realMime != null ? realMime : "—"));
        }).start();

        AlertDialog mimeDialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.mime))
                .setView(wrapInScroll(layout))
                .setPositiveButton(R.string.close, null)
                .create();
        dialogUtil.styleAlertDialog(mimeDialog);
    }

    private long getFolderSize(File file, TextView toUpdate) {
        File[] files = file.listFiles();
        if (files == null) {
            try {
                if (AccessManager.fileOpsOn(context)) {
                    long size = AccessManager.dirSize(context, file.getAbsolutePath());
                    if (size >= 0) {
                        if (toUpdate != null) {
                            context.handler.post(() -> toUpdate.setText(FileSize.getHumanReadableFileSize(size)));
                        }
                        return size;
                    }
                }
            } catch (Exception ignored) {
            }
            return 0;
        }
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

    private void showEditLastModifiedDialog(List<File> files, TextView modifiedView) {
        if (files == null || files.isEmpty()) return;
        File firstFile = files.get(0);
        long currentMtime = lastKnownMtime(firstFile);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        String initialText = sdf.format(new java.util.Date(currentMtime));

        TextInputLayout box = UiFields.box(context, null);
        EditText input = UiFields.field(box, InputType.TYPE_CLASS_DATETIME);
        input.setText(initialText);
        input.setSingleLine(true);
        box.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        box.setEndIconDrawable(R.drawable.ic_calendar_today_24);
        box.setEndIconContentDescription(R.string.mtime_option_picker);

        box.setEndIconOnClickListener(v -> {
            long baseTime = parseDateTimeInput(input.getText() != null ? input.getText().toString().trim() : "");
            if (baseTime <= 0) baseTime = currentMtime;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(baseTime);
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            new DatePickerDialog(context, (datePicker, y, m, d) -> new TimePickerDialog(context, (timePicker, h, min) -> {
                Calendar res = Calendar.getInstance();
                res.set(y, m, d, h, min, 0);
                input.setText(sdf.format(new java.util.Date(res.getTimeInMillis())));
            }, hour, minute, true).show(), year, month, day).show();
        });

        int pad = dp(16);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, dp(8), pad, dp(8));
        container.addView(box);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.mtime_edit_title))
                .setView(container)
                .setNeutralButton(R.string.current_time, (d, w) -> {
                    long now = System.currentTimeMillis();
                    applyMtime(files, modifiedView, now);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    long parsed = parseDateTimeInput(text);
                    if (parsed > 0) {
                        applyMtime(files, modifiedView, parsed);
                    } else {
                        Extensions.showMessage(context, context.getString(R.string.mtime_failed));
                    }
                })
                .create();
        KeyboardUtil.focusOnShow(dialog, input, true);
        dialogUtil.styleAlertDialog(dialog);
    }

    private static long parseDateTimeInput(String str) {
        if (TextUtils.isEmpty(str)) return -1;
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy:MM:dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
                sdf.setLenient(false);
                java.util.Date date = sdf.parse(str);
                if (date != null) return date.getTime();
            } catch (Exception ignored) {}
        }
        try {
            long millis = Long.parseLong(str);
            if (millis > 0) return millis;
        } catch (Exception ignored) {}
        return -1;
    }

    private long lastKnownMtime(File file) {
        try {
            long m = file.lastModified();
            if (m > 0) return m;
            if (AccessManager.fileOpsOn(context)) {
                long em = AccessManager.getMtime(context, file.getAbsolutePath());
                if (em > 0) return em;
            }
        } catch (Exception ignored) {
        }
        return System.currentTimeMillis();
    }

    private void applyMtime(List<File> files, TextView modifiedView, long newTime) {
        new Thread(() -> {
            int successCount = 0;
            for (File file : files) {
                if (FileUtils.matchExt('.' + FilenameUtils.getExtension(file.getName()).toLowerCase(), FileUtils.IMAGE_EXTS)) {
                    try {
                        androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, formatExifDate(newTime));
                        exif.saveAttributes();
                    } catch (Exception ignored) {}
                }
                try {
                    if (AccessManager.touchMtime(context, file.getAbsolutePath(), newTime)) {
                        successCount++;
                    }
                } catch (Exception ignored) {}
                scanMediaFile(context, file.getAbsolutePath());
            }
            int finalSuccess = successCount;
            context.handler.post(() -> {
                if (finalSuccess > 0) {
                    modifiedView.setText(UiPrefs.formatDate(context, newTime));
                    Extensions.showMessage(context, context.getString(R.string.mtime_updated));
                } else {
                    Extensions.showMessage(context, context.getString(R.string.mtime_failed));
                }
            });
        }).start();
    }

    private void showEditDateTakenDialog(List<File> files, TextView dateTakenView) {
        if (files == null || files.isEmpty()) return;
        long current = -1;
        for (File f : files) {
            if (f.isFile() && FileUtils.matchExt('.' + FilenameUtils.getExtension(f.getName()).toLowerCase(), FileUtils.IMAGE_EXTS)) {
                try {
                    androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(f.getAbsolutePath());
                    String dt = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL);
                    current = parseExifDate(dt);
                    if (current > 0) break;
                } catch (Exception ignored) {}
            }
        }
        if (current <= 0) current = System.currentTimeMillis();

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        String initialText = sdf.format(new java.util.Date(current));

        TextInputLayout box = UiFields.box(context, null);
        EditText input = UiFields.field(box, InputType.TYPE_CLASS_DATETIME);
        input.setText(initialText);
        input.setSingleLine(true);
        box.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        box.setEndIconDrawable(R.drawable.ic_calendar_today_24);
        box.setEndIconContentDescription(R.string.mtime_option_picker);

        long finalCurrent = current;
        box.setEndIconOnClickListener(v -> {
            long baseTime = parseDateTimeInput(input.getText() != null ? input.getText().toString().trim() : "");
            if (baseTime <= 0) baseTime = finalCurrent;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(baseTime);
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);

            new DatePickerDialog(context, (datePicker, y, m, d) -> new TimePickerDialog(context, (timePicker, h, min) -> {
                Calendar res = Calendar.getInstance();
                res.set(y, m, d, h, min, 0);
                input.setText(sdf.format(new java.util.Date(res.getTimeInMillis())));
            }, hour, minute, true).show(), year, month, day).show();
        });

        int pad = dp(16);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(pad, dp(8), pad, dp(8));
        container.addView(box);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.date_taken))
                .setView(container)
                .setNeutralButton(R.string.current_time, (d, w) -> {
                    long now = System.currentTimeMillis();
                    applyExifDateTaken(files, dateTakenView, now);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    long parsed = parseDateTimeInput(text);
                    if (parsed > 0) {
                        applyExifDateTaken(files, dateTakenView, parsed);
                    } else {
                        Extensions.showMessage(context, context.getString(R.string.mtime_failed));
                    }
                })
                .create();
        KeyboardUtil.focusOnShow(dialog, input, true);
        dialogUtil.styleAlertDialog(dialog);
    }

    private void applyExifDateTaken(List<File> files, TextView dateTakenView, long millis) {
        new Thread(() -> {
            int imageCount = 0;
            for (File file : files) {
                if (!file.isFile() || !FileUtils.matchExt('.' + FilenameUtils.getExtension(file.getName()).toLowerCase(), FileUtils.IMAGE_EXTS)) {
                    continue;
                }
                try {
                    long originalMtime = file.lastModified();
                    if (originalMtime <= 0) {
                        try {
                            originalMtime = AccessManager.getMtime(context, file.getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                    String accessiblePath = resolveAccessiblePath(file.getAbsolutePath());
                    androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(accessiblePath);
                    if (millis > 0) {
                        String formatted = formatExifDate(millis);
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, formatted);
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, formatted);
                    } else {
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL, null);
                        exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_DIGITIZED, null);
                    }
                    exif.saveAttributes();
                    if (originalMtime > 0) {
                        try {
                            AccessManager.touchMtime(context, file.getAbsolutePath(), originalMtime);
                            if (!accessiblePath.equals(file.getAbsolutePath())) {
                                AccessManager.touchMtime(context, accessiblePath, originalMtime);
                            }
                        } catch (Exception ignored) {}
                    }
                    scanMediaFile(context, file.getAbsolutePath());
                    imageCount++;
                } catch (Exception ignored) {}
            }
            int finalCount = imageCount;
            context.handler.post(() -> {
                if (finalCount > 0) {
                    if (dateTakenView != null) {
                        dateTakenView.setText(millis > 0 ? UiPrefs.formatDate(context, millis) : "—");
                        if (millis > 0) dateTakenView.setTag(formatFullTimestamp(millis));
                        else dateTakenView.setTag(null);
                    }
                    Extensions.showMessage(context, context.getString(R.string.mtime_updated));
                } else {
                    Extensions.showMessage(context, context.getString(R.string.mtime_failed));
                }
            });
        }).start();
    }

    private static long parseExifDate(String exifDate) {
        if (android.text.TextUtils.isEmpty(exifDate)) return -1;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US);
            java.util.Date d = sdf.parse(exifDate.trim());
            return d != null ? d.getTime() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String formatExifDate(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US);
        return sdf.format(new java.util.Date(millis));
    }

    private static String formatFullTimestamp(long millis) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
        return sdf.format(new java.util.Date(millis));
    }

    private static String resolveAccessiblePath(String path) {
        if (path == null) return null;
        if (path.startsWith("/data/media/0/")) {
            return "/storage/emulated/0/" + path.substring("/data/media/0/".length());
        } else if (path.startsWith("/data/media/")) {
            return "/storage/emulated/" + path.substring("/data/media/".length());
        }
        return path;
    }

    private static void scanMediaFile(MainActivity context, String path) {
        if (context == null || path == null) return;
        try {
            MediaScannerConnection.scanFile(context, new String[]{path}, null, null);
            if (path.startsWith("/data/media/0/")) {
                String emulated = "/storage/emulated/0/" + path.substring("/data/media/0/".length());
                MediaScannerConnection.scanFile(context, new String[]{emulated}, null, null);
            } else if (path.startsWith("/data/media/")) {
                String emulated = "/storage/emulated/" + path.substring("/data/media/".length());
                MediaScannerConnection.scanFile(context, new String[]{emulated}, null, null);
            }
        } catch (Exception ignored) {}
    }

    private TextView addPropertyRow(LinearLayout container, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        labelView.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));

        TextView valueView = new TextView(context);
        valueView.setText(value == null ? "—" : value);
        valueView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        valueView.setOnLongClickListener(v -> {
            Object tag = valueView.getTag();
            CharSequence toCopy = (tag instanceof CharSequence cs && !TextUtils.isEmpty(cs)) ? cs : valueView.getText();
            CopyUtil.copyToClipboard(context, toCopy);
            return true;
        });
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));

        row.addView(labelView);
        row.addView(valueView);
        container.addView(row);
        return valueView;
    }

    private TextView addPropertyRow(LinearLayout container, String label, String value,
                                    int actionIcon, View.OnClickListener action) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        labelView.setTextColor(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.3f));
        row.addView(labelView);

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.HORIZONTAL);
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.7f));
        row.addView(inner);

        TextView valueView = new TextView(context);
        valueView.setText(value == null ? "—" : value);
        valueView.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        valueView.setOnLongClickListener(v -> {
            Object tag = valueView.getTag();
            CharSequence toCopy = (tag instanceof CharSequence cs && !TextUtils.isEmpty(cs)) ? cs : valueView.getText();
            CopyUtil.copyToClipboard(context, toCopy);
            return true;
        });
        valueView.setOnClickListener(action);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        inner.addView(valueView);

        ImageView icon = new ImageView(context);
        icon.setImageResource(actionIcon);
        icon.setColorFilter(MaterialColors.getColor(context, android.R.attr.colorPrimary, Color.WHITE));
        int iconSize = dp(20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconSize, iconSize);
        lp.setMarginStart(dp(8));
        lp.gravity = Gravity.CENTER_VERTICAL;
        icon.setLayoutParams(lp);
        icon.setClickable(true);
        icon.setFocusable(true);
        icon.setOnClickListener(action);
        inner.addView(icon);

        container.addView(row);
        return valueView;
    }

    private void showRenameFromProperties(File file, String fileName, TextView nameView,
                                           TextView titleView, AlertDialog[] selfDialog) {
        View rnm = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
        EditText renameInput = rnm.findViewById(R.id.m_et_edittext);
        renameInput.setText(fileName);
        boolean isFile = !file.isDirectory();
        RenameHelper.selectBaseName(renameInput, isFile);
        renameInput.setOnLongClickListener(v -> {
            RenameHelper.selectBaseName(renameInput, isFile);
            return true;
        });
        AlertDialog renameDialog = dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.rename_1, fileName))
                .setView(rnm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String s = renameInput.getText() == null ? "" : renameInput.getText().toString();
                    if (s.isEmpty() || s.equals(fileName)) return;
                    File ogFolder = file.getParentFile();
                    if (ogFolder == null) {
                        Extensions.showMessage(context, context.rss.getString(R.string.failed_to_renamex, fileName));
                        return;
                    }
                    File dest = new File(ogFolder, s);
                    boolean pane1 = context.lastPaneSelected == 1;
                    Runnable updateViews = () -> {
                        try {
                            if (nameView != null) nameView.setText(s);
                        } catch (Exception ignored) {
                        }
                        try {
                            if (titleView != null) titleView.setText(s);
                        } catch (Exception ignored) {
                        }
                    };
                    Runnable finish = () -> {
                        updateViews.run();
                        try {
                            if (selfDialog != null && selfDialog[0] != null) selfDialog[0].dismiss();
                        } catch (Exception ignored) {
                        }
                    };
                    if (dest.exists() && !RenameHelper.sameFile(file, dest)) {
                        RenameHelper.askSwapName(context, dialogUtil, file, dest, s, pane1, finish);
                        return;
                    }
                    if (RenameHelper.renameFile(context, file, dest)) {
                        context.refreshPaneAfterChange(pane1, s);
                        context.flashPaneItem(pane1, s);
                        finish.run();
                    } else {
                        Extensions.showMessage(context, context.rss.getString(R.string.failed_to_renamex, fileName));
                    }
                })
                .create();
        KeyboardUtil.focusOnShow(renameDialog, renameInput, false);
        dialogUtil.styleAlertDialog(renameDialog);
    }

    private int dp(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private String getSymlinkTarget(File file) {
        if (file == null) return null;
        if (file instanceof io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile sf) {
            if (sf.isLink()) {
                try {
                    if (io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.isGranted()) {
                        io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.Result res =
                                io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.exec(
                                        "readlink " + RootManager.escapeShellArg(file.getAbsolutePath()), 5);
                        if (res.success && res.stdout != null && !res.stdout.trim().isEmpty()) {
                            return res.stdout.trim();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                java.nio.file.Path p = file.toPath();
                if (java.nio.file.Files.isSymbolicLink(p)) {
                    return java.nio.file.Files.readSymbolicLink(p).toString();
                }
            } catch (Exception ignored) {
            }
        }
        try {
            RootManager rm = RootManager.getInstance();
            if (rm != null && rm.isRootAvailable()) {
                RootManager.ShellResult res = rm.executeFs("readlink " + RootManager.escapeShellArg(file.getAbsolutePath()), 5);
                if (res.isSuccess() && res.output() != null) {
                    String out = res.output().trim();
                    if (!out.isEmpty()) return out;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void showEditSymlinkDialog(File file, TextView symTargetView) {
        if (file == null) return;
        String currentTarget = symTargetView != null && symTargetView.getText() != null
                ? symTargetView.getText().toString() : "";
        View view = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
        EditText input = view.findViewById(R.id.m_et_edittext);
        input.setText(currentTarget);
        if (!currentTarget.isEmpty()) {
            input.setSelection(currentTarget.length());
        }

        AlertDialog editDialog = dialogUtil.getDialogBuilder()
                .setTitle("Edit Symlink")
                .setView(view)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String newTarget = input.getText() == null ? "" : input.getText().toString().trim();
                    if (newTarget.isEmpty() || newTarget.equals(currentTarget)) return;
                    new Thread(() -> {
                        boolean updated = false;
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            try {
                                java.nio.file.Path p = file.toPath();
                                java.nio.file.Files.deleteIfExists(p);
                                java.nio.file.Files.createSymbolicLink(p, java.nio.file.Paths.get(newTarget));
                                updated = true;
                            } catch (Exception ignored) {}
                        }
                        if (!updated) {
                            try {
                                RootManager rm = RootManager.getInstance();
                                if (rm != null && rm.isRootAvailable()) {
                                    String cmd = "ln -snf " + RootManager.escapeShellArg(newTarget) + " " + RootManager.escapeShellArg(file.getAbsolutePath());
                                    RootManager.ShellResult res = rm.executeFs(cmd, 10);
                                    if (res.isSuccess()) {
                                        rm.invalidateListCache();
                                        updated = true;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                        if (!updated) {
                            try {
                                if (io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.isGranted()) {
                                    String cmd = "ln -snf " + RootManager.escapeShellArg(newTarget) + " " + RootManager.escapeShellArg(file.getAbsolutePath());
                                    io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.Result res =
                                            io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.exec(cmd, 10);
                                    if (res.success) {
                                        updated = true;
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        final boolean success = updated;
                        context.handler.post(() -> {
                            if (success) {
                                if (symTargetView != null) symTargetView.setText(newTarget);
                                boolean pane1 = context.lastPaneSelected == 1;
                                context.refreshPaneAfterChange(pane1, file.getName());
                                Extensions.showMessage(context, "Symlink updated");
                            } else {
                                Extensions.showMessage(context, "Failed to update symlink");
                            }
                        });
                    }).start();
                })
                .create();
        KeyboardUtil.focusOnShow(editDialog, input, false);
        dialogUtil.styleAlertDialog(editDialog);
    }

    private View wrapInScroll(View content) {
        ScrollView sv = new ScrollView(context);
        sv.addView(content);
        return sv;
    }
}

package io.github.abdurazaaqmohammed.adapters.main;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
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
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.codehasan.colorpicker.extensions.Extensions;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.textfield.TextInputLayout;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.ui.UiFields;
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
        LinearLayout checksumSection = propView.findViewById(R.id.checksumSection);
        LinearLayout checksumRows = propView.findViewById(R.id.checksumRows);

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
        if (!isInZip && file.getParentFile() != null)
            addPropertyRow(propRows, context.getString(R.string.parent), file.getParentFile().getName());
        addPropertyRow(propRows, context.getString(R.string.type), typeStr);

        long size = 0;
        if (multi) {
            if (values != null && selectedPositions != null) {
                for (int p : selectedPositions) {
                    if (p < 0 || p >= values.length) continue;
                    Object o = values[p];
                    if (o instanceof File) size += ((File) o).length();
                    else if (o instanceof ZipEntryInfo) size += ((ZipEntryInfo) o).getSize();
                }
            }
        } else size = isInZip ? entry.getSize() : file.length();
        TextView sizeValue = addPropertyRow(propRows, context.getString(R.string.size), Formatter.formatFileSize(context, size));

        if (!isInZip) {
            final TextView[] modifiedHolder = new TextView[1];
            final TextView modifiedView = addPropertyRow(propRows, context.getString(R.string.modified),
                    UiPrefs.formatDate(context, file.lastModified()),
                    R.drawable.edit_24px,
                    v -> showEditLastModifiedDialog(file, modifiedHolder[0]));
            modifiedHolder[0] = modifiedView;
            if (file.lastModified() <= 0) {
                new Thread(() -> {
                    long m = 0;
                    try {
                        m = AccessManager.getMtime(context, file.getAbsolutePath());
                    } catch (Exception ignored) {
                    }
                    if (m <= 0 && isImage) {
                        try {
                            androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
                            String dt = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME);
                            m = parseExifDate(dt);
                        } catch (Exception ignored) {}
                    }
                    if (m <= 0) return;
                    long finalM = m;
                    context.handler.post(() -> {
                        try {
                            modifiedView.setText(UiPrefs.formatDate(context, finalM));
                        } catch (Exception ignored) {
                        }
                    });
                }).start();
            }

            if (isImage) {
                final TextView[] dateTakenHolder = new TextView[1];
                String dateTakenDisplay = "—";
                try {
                    androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
                    String dtOriginal = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL);
                    if (!TextUtils.isEmpty(dtOriginal)) {
                        long millis = parseExifDate(dtOriginal);
                        dateTakenDisplay = millis > 0 ? UiPrefs.formatDate(context, millis) : dtOriginal;
                    }
                } catch (Exception ignored) {}
                final TextView dateTakenView = addPropertyRow(propRows, context.getString(R.string.date_taken),
                        dateTakenDisplay,
                        R.drawable.edit_24px,
                        v -> showEditDateTakenDialog(file, dateTakenHolder[0]));
                dateTakenHolder[0] = dateTakenView;
            }
            if (!multi && file.isFile()) {
                addPropertyRow(propRows, context.getString(R.string.reported_mime), MimeUtil.getReportedMimeType(context, file));
                TextView realMimeView = addPropertyRow(propRows, context.getString(R.string.real_mime), "…");
                new Thread(() -> {
                    String realMime = MimeUtil.getRealMimeType(file);
                    context.handler.post(() -> realMimeView.setText(realMime != null ? realMime : "—"));
                }).start();
            }
            if (!multi && file.isDirectory()) {
                new Thread(() -> {
                    long folderSize = getFolderSize(file, null);
                    context.handler.post(() -> sizeValue.setText(FileSize.getHumanReadableFileSize(folderSize)));
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
        // Built up front so the OK handler can apply the mode; the dialog itself
        // is created once at the end so the checksum section is always present.
        // Returning early from the permissions block used to skip it entirely,
        // which is why checksums vanished in root mode.
        final PermissionsEditorHelper editor = canEditPerms ? new PermissionsEditorHelper(context) : null;
        // Guards against saving before stat loaded, which previously chmodded
        // to 0 - or silently did nothing at all.
        final boolean[] permsLoaded = {false};
        if (canEditPerms) {
            TextView permsTitle = new TextView(context);
            permsTitle.setText(R.string.root_permissions);
            permsTitle.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            permsTitle.setPadding(0, dp(12), 0, dp(4));
            propRows.addView(permsTitle);

            View permsEditorView = editor.createPermissionsEditor("0000");
            propRows.addView(permsEditorView);

            new Thread(() -> {
                String perms = rm.getPermissions(file.getAbsolutePath());
                context.handler.post(() -> {
                    if (perms == null || perms.isEmpty()) {
                        Extensions.showMessage(context, R.string.permissions_unavailable);
                        return;
                    }
                    editor.applyNumericPerms(perms);
                    permsLoaded[0] = true;
                });
            }).start();
        }

        Map<String, String> propHashes = new HashMap<>();
        ChecksumDialogs.HashProvider propSupplier = () -> propHashes;
        // A root-only file cannot be stat()ed by the app, so isFile() is false for
        // it even though it is very much a file. needsStaging() answers via the
        // backend and keeps the checksum section available for those paths.
        boolean needsStaging = !isInZip && !multi && !file.isDirectory()
                && RootStaging.needsStaging(context, file);
        if (!isInZip && !multi && (file.isFile() || needsStaging)) {
            if (needsStaging) {
                // Root-only file: hash a staged copy (HashUtil needs a FileInputStream).
                Runnable runStagedChecksum = () -> {
                    checksumRows.removeAllViews();
                    TextView hint = new TextView(context);
                    hint.setText(R.string.checksum_reading_elevated);
                    checksumRows.addView(hint);
                    new Thread(() -> {
                        File staged = null;
                        try {
                            staged = RootStaging.stageForRead(context, file.getAbsolutePath());
                            final Map<String, String> hashes = HashUtil.hashAll(staged);
                            context.handler.post(() -> {
                                checksumRows.removeAllViews();
                                showHashes(checksumRows, hashes, propHashes);
                                checksumDialogs.addVerifySection(checksumRows, propSupplier);
                            });
                        } catch (Exception e) {
                            context.handler.post(() -> {
                                checksumRows.removeAllViews();
                                TextView err = new TextView(context);
                                err.setText(context.getString(R.string.checksum_unavailable,
                                        e.getMessage() == null ? e.toString() : e.getMessage()));
                                checksumRows.addView(err);
                            });
                        } finally {
                            // The staged copy is disposable; nothing else holds it.
                            if (staged != null) {
                                //noinspection ResultOfMethodCallIgnored
                                staged.delete();
                            }
                        }
                    }).start();
                };
                propView.findViewById(R.id.computeChecksums).setOnClickListener(btn -> runStagedChecksum.run());
                runStagedChecksum.run();
            } else {
                propView.findViewById(R.id.computeChecksums).setOnClickListener(btn -> {
                    checksumRows.removeAllViews();
                    checksumDialogs.startChecksumComputation(checksumRows, file, propHashes);
                    checksumDialogs.addVerifySection(checksumRows, propSupplier);
                });
                checksumDialogs.startChecksumComputation(checksumRows, file, propHashes);
                checksumDialogs.addVerifySection(checksumRows, propSupplier);
            }
        } else if (isInZip && !multi && !entry.isDirectory()) {
            Map<String, String> zipHashes = new HashMap<>();
            long entryCrc = entry.computeCrc32();
            if (entryCrc >= 0) zipHashes.put("CRC32", HashUtil.crc32Hex(entryCrc));
            checksumDialogs.addCrc32Row(checksumRows, entryCrc);
            checksumDialogs.addVerifySection(checksumRows, () -> zipHashes);
            propView.findViewById(R.id.computeChecksums).setOnClickListener(btn -> {
                checksumRows.removeAllViews();
                checksumDialogs.addCrc32Row(checksumRows, entry.computeCrc32());
                checksumDialogs.startZipEntryChecksumComputation(checksumRows, entry.getZipFile(), entry.getFullPath(), zipHashes);
                checksumDialogs.addVerifySection(checksumRows, () -> zipHashes);
            });
        } else {
            checksumSection.setVisibility(View.GONE);
        }

        selfDialog[0] = dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.properties))
                .setView(wrapInScroll(propView))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    if (editor == null) return;
                    if (!permsLoaded[0] || !editor.isReady()) {
                        Extensions.showMessage(context, R.string.permissions_unavailable);
                        return;
                    }
                    String permsStr = String.valueOf(editor.getNumericValue());
                    new Thread(() -> {
                        try {
                            rm.chmodFs(file.getAbsolutePath(), permsStr);
                            rm.invalidateListCache();
                            context.handler.post(() ->
                                    Extensions.showMessage(context, R.string.permissions_updated));
                        } catch (Exception e) {
                            context.handler.post(() ->
                                    new ErrorUtil(context).showError(e));
                        }
                    }).start();
                })
                .create();
        dialogUtil.styleAlertDialog(selfDialog[0]);
    }

    /** Fills the checksum rows from an already-computed set of digests. */
    private void showHashes(LinearLayout container, Map<String, String> hashes,
                            Map<String, String> out) {
        for (String algo : HashUtil.ALGORITHMS) {
            TextView tv = checksumDialogs.addChecksumRow(container, algo);
            String value = hashes.get(algo);
            tv.setText(value == null ? "—" : value);
        }
        if (out != null) {
            out.clear();
            out.putAll(hashes);
        }
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

    private void showEditLastModifiedDialog(File file, TextView modifiedView) {
        long currentMtime = lastKnownMtime(file);
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
                    applyMtime(file, modifiedView, now);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    long parsed = parseDateTimeInput(text);
                    if (parsed > 0) {
                        applyMtime(file, modifiedView, parsed);
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

    private void applyMtime(File file, TextView modifiedView, long newTime) {
        new Thread(() -> {
            if (FileUtils.matchExt('.' + FilenameUtils.getExtension(file.getName()).toLowerCase(), FileUtils.IMAGE_EXTS)) {
                try {
                    androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
                    exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME, formatExifDate(newTime));
                    exif.saveAttributes();
                } catch (Exception ignored) {}
            }
            boolean ok = false;
            try {
                ok = AccessManager.touchMtime(context, file.getAbsolutePath(), newTime);
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
                return;
            }
            boolean finalOk = ok;
            context.handler.post(() -> {
                if (finalOk) {
                    modifiedView.setText(UiPrefs.formatDate(context, newTime));
                    Extensions.showMessage(context, context.getString(R.string.mtime_updated));
                } else {
                    Extensions.showMessage(context, context.getString(R.string.mtime_failed));
                }
            });
        }).start();
    }

    private void showEditDateTakenDialog(File file, TextView dateTakenView) {
        long current = -1;
        try {
            androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
            String dt = exif.getAttribute(androidx.exifinterface.media.ExifInterface.TAG_DATETIME_ORIGINAL);
            current = parseExifDate(dt);
        } catch (Exception ignored) {}
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
                    applyExifDateTaken(file, dateTakenView, now);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    long parsed = parseDateTimeInput(text);
                    if (parsed > 0) {
                        applyExifDateTaken(file, dateTakenView, parsed);
                    } else {
                        Extensions.showMessage(context, context.getString(R.string.mtime_failed));
                    }
                })
                .create();
        KeyboardUtil.focusOnShow(dialog, input, true);
        dialogUtil.styleAlertDialog(dialog);
    }

    private void applyExifDateTaken(File file, TextView dateTakenView, long millis) {
        new Thread(() -> {
            try {
                long originalMtime = file.lastModified();
                if (originalMtime <= 0) {
                    try {
                        originalMtime = AccessManager.getMtime(context, file.getAbsolutePath());
                    } catch (Exception ignored) {}
                }
                androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
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
                    } catch (Exception ignored) {}
                }
                context.handler.post(() -> {
                    if (dateTakenView != null) {
                        dateTakenView.setText(millis > 0 ? UiPrefs.formatDate(context, millis) : "—");
                    }
                    Extensions.showMessage(context, context.getString(R.string.mtime_updated));
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
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
        valueView.setTextIsSelectable(true);
        valueView.setOnLongClickListener(v -> {
            CopyUtil.copyToClipboard(context, valueView.getText());
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

    private View wrapInScroll(View content) {
        ScrollView sv = new ScrollView(context);
        sv.addView(content);
        return sv;
    }
}

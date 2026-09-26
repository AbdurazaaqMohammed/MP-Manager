package io.github.abdurazaaqmohammed.adapters.main;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.text.ClipboardManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.aXMLDecoder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.reandroid.apkeditor.Util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.DialogAdapter;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.arsc.ArscEditorPlusActivity;
import io.github.abdurazaaqmohammed.arsc.ArscEditorActivity;
import io.github.abdurazaaqmohammed.listeners.SwipeTouchListener;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.activities.CompareTextActivity;
import io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareArscDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareZipDialog;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.JpegMetaStrip;
import io.github.abdurazaaqmohammed.utils.JpegtranJni;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
import io.github.abdurazaaqmohammed.utils.NativeToolManager;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RenameUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class MainFilesArrayAdapter extends RecyclerView.Adapter<MainFilesArrayAdapter.ViewHolder> {

    private final MainActivity context;
    public final Object[] values;
    public final boolean isInZip;
    public final String currentZipPath;
    public final boolean pane1; //THIS IS WHETHER THE ADAPTER IS FOR PANE 1 OR 2 NOT THE LAST CLICKED PANE
    private final DialogUtil dialogUtil;
    private final UIHelper uiHelper;
    private final FileIconLoader iconLoader;
    private final ApkManifestEditor manifestEditor;
    private final ChecksumDialogs checksumDialogs;
    private final FilePropertiesDialog propertiesDialog;
    private final FileOperationsHelper fileOps;
    private final ApkToolsHandler apkTools;
    private final CommandHelper commandHelper;

    public void setMultiSelectMode(boolean multiSelectMode) {
        context.setMultiSelectModeUI(isMultiSelectMode = multiSelectMode);
    }

    private boolean isMultiSelectMode = false;

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    private final Set<Integer> selectedPositions = new HashSet<>();
    private Integer rangeStartPosition = null;

    private static Object[] getNewValues(Object[] values, File parentFile) {
        Object[] letUpDir = new File[values.length + 1];
        letUpDir[0] = parentFile;
        System.arraycopy(values, 0, letUpDir, 1, values.length);
        return letUpDir;
    }

    private static List<Object> getNewValues(List<Object> values, Object parentFile) {
        ArrayList<Object> letUpDir = new ArrayList<>(values.size() + 1);
        letUpDir.add(parentFile);
        letUpDir.addAll(values);
        return letUpDir;
    }

    private File[] getOldValues() {
        int newLength = values.length - 1;
        File[] oldValues = new File[newLength];
        System.arraycopy(values, 1, oldValues, 0, newLength);
        return oldValues;
    }

    /** Entries currently shown (including the up-dir at index 0), for callers that must not re-list. */
    public File[] getShownFiles() {
        if (!(values instanceof File[])) return null;
        return (File[]) values;
    }

    public MainFilesArrayAdapter(MainActivity context, Object[] values, Object parent, boolean pane1, boolean isInZip,
            String currentZipPath) {
        this.values = isInZip ? values : getNewValues(values, (File) parent);
        this.context = context;
        this.pane1 = pane1;
        this.isInZip = isInZip;
        this.currentZipPath = currentZipPath;
        dialogUtil = context.dialogUtil;
        uiHelper = context.uiHelper;
        iconLoader = new FileIconLoader(context, isInZip);
        manifestEditor = new ApkManifestEditor(context, dialogUtil, uiHelper);
        checksumDialogs = new ChecksumDialogs(context, dialogUtil);
        propertiesDialog = new FilePropertiesDialog(context, dialogUtil, checksumDialogs);
        fileOps = new FileOperationsHelper(context, dialogUtil, this);
        apkTools = new ApkToolsHandler(context, dialogUtil, uiHelper, pane1, manifestEditor);
        commandHelper = new CommandHelper(context);
    }

    @Override
    public int getItemCount() { return values.length; }

    public Object getItem(int position) { return values[position]; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView fileNameView, fileDateView;
        final ImageView fileIconView;
        ViewHolder(View v) {
            super(v);
            fileNameView = v.findViewById(R.id.fileName);
            fileIconView = v.findViewById(R.id.fileIcon);
            fileDateView = v.findViewById(R.id.fileDate);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.list_file, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final View convertView = holder.itemView;
        position = holder.getBindingAdapterPosition();
        if (position < 0 || position >= values.length) return;
        Object item = values[position];
        final ViewHolder bindHolder = holder;
        final Object boundItem = item;
        File file;
        ZipEntryInfo entry;
        String fileName;

        holder.fileNameView.setText("");
        holder.fileDateView.setText("");
        holder.fileIconView.setImageDrawable(null);

        int scale = UiPrefs.getScale(context);
        holder.fileNameView.setTextSize(UiPrefs.nameSize(scale));
        holder.fileNameView.setMaxLines(UiPrefs.getMaxLines(context));
        holder.fileNameView.setEllipsize(TextUtils.TruncateAt.END);
        int iconPx = UiPrefs.iconDp(context, scale);
        ViewGroup.LayoutParams iconParams = holder.fileIconView.getLayoutParams();
        if (iconParams != null) {
            iconParams.width = iconPx;
            iconParams.height = iconPx;
            holder.fileIconView.setLayoutParams(iconParams);
        }

        if (isInZip) {
            entry = (ZipEntryInfo) item;
            iconLoader.setupZipEntryView(entry, holder.fileIconView, holder.fileDateView);
            file = null;
            holder.fileNameView.setText(fileName = entry.getName());
        } else {
            entry = null;
            file = (File) item;
            iconLoader.setupFileView(file, holder.fileIconView, holder.fileDateView);
            holder.fileNameView.setText(fileName = (position == 0 ? ".." : file.getName()));
        }

        convertView.setBackgroundColor(selectedPositions.contains(position) ? Color.DKGRAY : Color.TRANSPARENT);
        int finalPosition = position;
        new Thread(() -> {
            View.OnClickListener originalClickListener;
            if(isInZip && finalPosition == 0 && entry.getFullPath() == null) {
                originalClickListener = v -> context.loadFolderInPane(entry.getZipFile().getParentFile(), pane1);
            } else {
                originalClickListener = isMultiSelectMode ? v -> {
                    context.setSelectedPane(pane1 ? 1 : 2);
                    handleMultiSelect(finalPosition);
                } : !isInZip && file.isFile() ?
                    v -> {
                        context.setSelectedPane(pane1 ? 1 : 2);
                        context.setCurrentFolder(file.getParentFile(), getOldValues());
                        handleFileClick(file, fileName);
                    } : (View.OnClickListener) v -> {
                    context.setSelectedPane(pane1 ? 1 : 2);
                    if (isInZip)
                        fileOps.handleZipEntryClick(entry);
                    else
                        context.loadFolderInPane(file, pane1);
                };
            }

            View.OnLongClickListener originalLongClickListener = v -> {
                context.setSelectedPane(pane1 ? 1 : 2);
                if (isInZip) {
                    context.setCurrentFolder(currentZipPath, Arrays.asList(values));
                } else
                    context.setCurrentFolder(file.getParentFile(), getOldValues());

                boolean multi = !selectedPositions.isEmpty();
                String direction = pane1 ? "->" : "<-";
                List<FileMenuOrder.MenuItem> visibleMenu = new ArrayList<>();
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.COPY, FileMenuOrder.labelFor(context, FileMenuOrder.COPY, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.MOVE, FileMenuOrder.labelFor(context, FileMenuOrder.MOVE, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.RENAME, FileMenuOrder.labelFor(context, FileMenuOrder.RENAME, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.DELETE, FileMenuOrder.labelFor(context, FileMenuOrder.DELETE, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.COMPRESS, FileMenuOrder.labelFor(context, FileMenuOrder.COMPRESS, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.PROPERTIES, FileMenuOrder.labelFor(context, FileMenuOrder.PROPERTIES, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.SHARE, FileMenuOrder.labelFor(context, FileMenuOrder.SHARE, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.OPEN_WITH, FileMenuOrder.labelFor(context, FileMenuOrder.OPEN_WITH, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.BOOKMARK, FileMenuOrder.labelFor(context, FileMenuOrder.BOOKMARK, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMD, FileMenuOrder.labelFor(context, FileMenuOrder.CMD, direction)));
                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CHECK, FileMenuOrder.labelFor(context, FileMenuOrder.CHECK, direction)));

                if (multi && !isInZip) {
                    boolean allApks = true;
                    for (int bp : selectedPositions) {
                        Object selected = values[bp];
                        if (!(selected instanceof File) || !((File) selected).getName().toLowerCase(Locale.ENGLISH).endsWith(".apk")) {
                            allApks = false;
                            break;
                        }
                    }
                    if (allApks) {
                        visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.BATCH_SIGN, FileMenuOrder.labelFor(context, FileMenuOrder.BATCH_SIGN, direction)));
                        visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.BATCH_OPT, FileMenuOrder.labelFor(context, FileMenuOrder.BATCH_OPT, direction)));
                        visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.BATCH_INSTALL, FileMenuOrder.labelFor(context, FileMenuOrder.BATCH_INSTALL, direction)));
                    }
                    boolean hasImage = false;
                    for (int bp : selectedPositions) {
                        Object selected = values[bp];
                        if (selected instanceof File && FileUtils.isImageFile(((File) selected).getName())) {
                            hasImage = true;
                            break;
                        }
                    }
                    if (hasImage) {
                        visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.BATCH_CROP, FileMenuOrder.labelFor(context, FileMenuOrder.BATCH_CROP, direction)));
                        visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.BATCH_EXIF, FileMenuOrder.labelFor(context, FileMenuOrder.BATCH_EXIF, direction)));
                        visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.BATCH_STRIP_META, FileMenuOrder.labelFor(context, FileMenuOrder.BATCH_STRIP_META, direction)));
                    }
                }

                if (!multi && !isInZip && !file.isDirectory() && ArchiveUtil.isSupportedArchive(fileName)) {
                    visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.EXTRACT, FileMenuOrder.labelFor(context, FileMenuOrder.EXTRACT, direction)));
                }

                RecyclerView.Adapter a = ((RecyclerView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
                Object compareFile1 = null;
                Object compareFile2 = null;
                if(a instanceof MainFilesArrayAdapter otherPaneAdapter) {
                    if (selectedPositions.size() == 1 && otherPaneAdapter.selectedPositions.size() == 1) {
                        compareFile1 = values[selectedPositions.iterator().next()];
                        compareFile2 = otherPaneAdapter.values[otherPaneAdapter.selectedPositions.iterator().next()];
                        String name1 = compareFile1 instanceof File ? ((File)compareFile1).getName() : ((ZipEntryInfo)compareFile1).getName();
                        String name2 = compareFile2 instanceof File ? ((File)compareFile2).getName() : ((ZipEntryInfo)compareFile2).getName();

                        String ext1 = FilenameUtils.getExtension(name1).toLowerCase();
                        String ext2 = FilenameUtils.getExtension(name2).toLowerCase();

                        boolean isZip1 = ext1.equals("zip") || ext1.equals("apk") || ext1.equals("jar");
                        boolean isZip2 = ext2.equals("zip") || ext2.equals("apk") || ext2.equals("jar");
                        boolean isArsc1 = ext1.equals("arsc") || ext1.equals("apk");
                        boolean isArsc2 = ext2.equals("arsc") || ext2.equals("apk");

                        if (isZip1 && isZip2) visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_ZIP, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_ZIP, direction)));
                        if (isArsc1 && isArsc2) visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_ARSC, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_ARSC, direction)));
                        if (!isZip1 && !isZip2 && !ext1.equals("arsc") && !ext2.equals("arsc")) {
                            visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_TEXT, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_TEXT, direction)));
                            if (compareFile1 instanceof File && compareFile2 instanceof File
                                    && !((File) compareFile1).isDirectory() && !((File) compareFile2).isDirectory())
                                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_HASH, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_HASH, direction)));
                        }
                        if (ext1.equals("apk") && ext2.equals("apk")
                                && compareFile1 instanceof File && compareFile2 instanceof File)
                            visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_APK, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_APK, direction)));
                    }
                }

                List<FileMenuOrder.MenuItem> menuItems = FileMenuOrder.sortItems(context, visibleMenu);
                String[] items = new String[menuItems.size()];
                String[] itemIds = new String[menuItems.size()];
                for (int mi = 0; mi < menuItems.size(); mi++) {
                    items[mi] = menuItems.get(mi).label();
                    itemIds[mi] = menuItems.get(mi).id();
                }

                final Object finalCompareFile1 = compareFile1;
                final Object finalCompareFile2 = compareFile2;

                final boolean twoColumnMenu = FileMenuOrder.isTwoColumn(context);
                View menuView = LayoutInflater.from(context).inflate(R.layout.dialog_file_menu, null);
                ((TextView) menuView.findViewById(R.id.fileMenuTitle)).setText(fileName);
                RecyclerView menuList = menuView.findViewById(R.id.fileMenuList);
                final BottomSheetDialog menuSheet;
                final AlertDialog menuDialog;
                if (twoColumnMenu) {
                    View handle = menuView.findViewById(R.id.fileMenuHandle);
                    if (handle != null) handle.setVisibility(View.GONE);
                    menuList.setLayoutManager(new GridLayoutManager(context, 2));
                    float density = context.getResources().getDisplayMetrics().density;
                    int edge = (int) (12 * density + 0.5f);
                    menuList.setPadding(edge, menuList.getPaddingTop(), edge, menuList.getPaddingBottom());
                    menuSheet = null;
                    menuDialog = new MaterialAlertDialogBuilder(context).setView(menuView).create();
                } else {
                    menuList.setLayoutManager(new LinearLayoutManager(context));
                    menuSheet = new BottomSheetDialog(context);
                    menuDialog = null;
                }
                menuList.setAdapter(new DialogAdapter(context, menuItems, isInZip, twoColumnMenu, position1 -> {
                    if (menuSheet != null) menuSheet.dismiss();
                    if (menuDialog != null) menuDialog.dismiss();
                    try {
                        String actionId = itemIds[position1];
                        switch (actionId) {
                            case FileMenuOrder.CMP_TEXT:
                                context.startActivity(new Intent(context, CompareTextActivity.class)
                                        .putExtra("file1", finalCompareFile1 instanceof File ? ((File) finalCompareFile1).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile1).getFullPath())
                                        .putExtra("file2", finalCompareFile2 instanceof File ? ((File) finalCompareFile2).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile2).getFullPath())
                                        .putExtra("isZip1", finalCompareFile1 instanceof ZipEntryInfo)
                                        .putExtra("isZip2", finalCompareFile2 instanceof ZipEntryInfo)
                                        .putExtra("zip1", finalCompareFile1 instanceof ZipEntryInfo ? ((ZipEntryInfo) finalCompareFile1).getZipFile().getAbsolutePath() : null)
                                        .putExtra("zip2", finalCompareFile2 instanceof ZipEntryInfo ? ((ZipEntryInfo) finalCompareFile2).getZipFile().getAbsolutePath() : null)
                                );
                                return;
                            case FileMenuOrder.CMP_ZIP:
                                new CompareZipDialog(context,
                                        finalCompareFile1 instanceof File ? (File) finalCompareFile1 : ((ZipEntryInfo) finalCompareFile1).getZipFile(),
                                        finalCompareFile2 instanceof File ? (File) finalCompareFile2 : ((ZipEntryInfo) finalCompareFile2).getZipFile()
                                ).show();
                                return;
                            case FileMenuOrder.CMP_ARSC:
                                new CompareArscDialog(context,
                                        finalCompareFile1 instanceof File ? ((File) finalCompareFile1).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile1).getZipFile().getAbsolutePath(),
                                        finalCompareFile2 instanceof File ? ((File) finalCompareFile2).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile2).getZipFile().getAbsolutePath()
                                ).show();
                                return;
                            case FileMenuOrder.CMP_HASH:
                                checksumDialogs.showCompareHashesDialog((File) finalCompareFile1, (File) finalCompareFile2);
                                return;
                            case FileMenuOrder.CHECK:
                                if (isInZip) {
                                    if (multi) {
                                        Extensions.showMessage(context, R.string.checksums_for_multiple_zip_entries_not_supported);
                                    } else {
                                        ZipEntryInfo zipEntry = (ZipEntryInfo) item;
                                        if (!zipEntry.isDirectory()) {
                                            checksumDialogs.showZipEntryChecksumsDialog(zipEntry);
                                        }
                                    }
                                    return;
                                }
                                List<File> checksumFiles = new ArrayList<>();
                                if (multi) {
                                    for (int cmdPos : selectedPositions) checksumFiles.add((File) values[cmdPos]);
                                } else {
                                    checksumFiles.add(file);
                                }
                                checksumDialogs.showChecksumsDialog(checksumFiles);
                                return;
                            case FileMenuOrder.CMP_APK:
                                apkTools.showCompareApksDialog((File) finalCompareFile1, (File) finalCompareFile2);
                                return;
                            case FileMenuOrder.BATCH_SIGN: {
                                List<File> apks = new ArrayList<>();
                                for (int bp : selectedPositions) apks.add((File) values[bp]);
                                apkTools.batchSignApks(apks);
                                return;
                            }
                            case FileMenuOrder.BATCH_OPT: {
                                List<File> apks = new ArrayList<>();
                                for (int bp : selectedPositions) apks.add((File) values[bp]);
                                apkTools.batchOptimizeApks(apks);
                                return;
                            }
                            case FileMenuOrder.BATCH_INSTALL: {
                                for (int bp : selectedPositions) InstallUtil.installApkWithDialog(context, (File) values[bp]);
                                return;
                            }
                            case FileMenuOrder.BATCH_CROP: {
                                List<File> images = selectedImageFiles();
                                if (images.isEmpty()) {
                                    Extensions.showMessage(context, R.string.no_images_selected);
                                    return;
                                }
                                showBatchCropDialog(images);
                                return;
                            }
                            case FileMenuOrder.BATCH_EXIF: {
                                List<File> images = selectedJpegFiles();
                                if (images.isEmpty()) {
                                    Extensions.showMessage(context, R.string.no_jpeg_files_selected);
                                    return;
                                }
                                showBatchExifDialog(images);
                                return;
                            }
                            case FileMenuOrder.BATCH_STRIP_META: {
                                List<File> images = selectedJpegFiles();
                                if (images.isEmpty()) {
                                    Extensions.showMessage(context, R.string.no_jpeg_files_selected);
                                    return;
                                }
                                confirmBatchStrip(images);
                                return;
                            }
                            case FileMenuOrder.CMD:
                                if (isInZip) {
                                    Extensions.showMessage(context, R.string.command_helper_not_supported_for_zip_entries);
                                    return;
                                }
                                ArrayList<String> cmdFilePaths = new ArrayList<>();
                                if (multi) {
                                    for (int cmdPos : selectedPositions) cmdFilePaths.add(((File) values[cmdPos]).getAbsolutePath());
                                } else {
                                    cmdFilePaths.add(file.getAbsolutePath());
                                }
                                commandHelper.showCommandHelperDialog(cmdFilePaths);
                                return;
                            case FileMenuOrder.EXTRACT:
                                if (isInZip || multi) return;
                                fileOps.extractArchive(file);
                                return;
                            default:
                                switch (actionId) {
                                    case FileMenuOrder.COPY:
                                        if (multi) {
                                            List<Object> itemsToCopy = new ArrayList<>();
                                            for (int f : selectedPositions) itemsToCopy.add(values[f]);
                                            fileOps.copyItemsAsync(itemsToCopy);
                                        } else fileOps.copyAsync(item);
                                        break;
                                    case FileMenuOrder.MOVE:
                                        if (context.pane1Folder == context.pane2Folder) {
                                            break;
                                        }
                                        fileOps.moveAsync(item);
                                        break;
                                    case FileMenuOrder.RENAME:
                                        showRenameDialog(finalPosition, file, entry, fileName, multi);
                                        break;
                                    case FileMenuOrder.DELETE:
                                        showDeleteDialog(finalPosition, file, entry, multi);
                                        break;
                                    case FileMenuOrder.COMPRESS:
                                        showCompressDialog(file, fileName, multi);
                                        break;
                                    case FileMenuOrder.PROPERTIES:
                                        propertiesDialog.show(multi, values, selectedPositions, isInZip, file, entry, fileName, getFilesToDisplay(multi, finalPosition).toString());
                                        break;
                                    case FileMenuOrder.SHARE:
                                        withReadableCopy(file, readable -> {
                                            Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
                                            String shareMime = MimeUtil.getMimeTypeForAction(context, readable);
                                            context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(shareMime != null ? shareMime : "application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share " + fileName));
                                        });
                                        break;
                                    case FileMenuOrder.OPEN_WITH:
                                        showOpenWithDialog(file, fileName);
                                        break;
                                    case FileMenuOrder.BOOKMARK:
                                        if (!isInZip) context.addBookmark(file);
                                        break;
                                }
                                break;
                        }
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                }));
                if (menuSheet != null) {
                    menuSheet.setContentView(menuView);
                    context.runOnUiThread(menuSheet::show);
                } else {
                    context.runOnUiThread(menuDialog::show);
                }
                return true;
            };
            context.handler.post(() -> {
                int currentPos = bindHolder.getBindingAdapterPosition();
                if (currentPos < 0 || currentPos >= values.length) return;
                if (values[currentPos] != boundItem) return;
                convertView.setOnTouchListener(new SwipeTouchListener(
                        context,
                        originalClickListener,
                        originalLongClickListener,
                        finalPosition,
                        MainFilesArrayAdapter.this,
                        pane1 ? 1 : 2));
            });
        }).start();

    }

    public void openWithForFile(File file, String fileName) {
        showOpenWithDialog(file, fileName);
    }

    private static boolean isJpegPath(String name) {
        String lower = name.toLowerCase(Locale.ENGLISH);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    private static boolean isPngPath(String name) {
        return name.toLowerCase(Locale.ENGLISH).endsWith(".png");
    }

    private List<File> selectedImageFiles() {
        List<File> out = new ArrayList<>();
        for (int p : selectedPositions) {
            Object o = values[p];
            if (o instanceof File f) {
                if (f.isFile() && FileUtils.isImageFile(f.getName())) out.add(f);
            }
        }
        return out;
    }

    private List<File> selectedJpegFiles() {
        List<File> out = new ArrayList<>();
        for (File f : selectedImageFiles()) {
            if (isJpegPath(f.getName())) out.add(f);
        }
        return out;
    }

    private void backupImage(File f) {
        try {
            FileUtils.copyFile(f, new File(f.getParent(), f.getName() + ".bak"));
        } catch (Exception ignored) {
        }
    }

    private int[] imageDims(File f) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        return new int[]{bounds.outWidth, bounds.outHeight};
    }

    private void finishBatchOp(String doneText) {
        clearSelection();
        context.loadFolderInPane(pane1 ? context.pane1Folder : context.pane2Folder, pane1);
        Extensions.showMessage(context, doneText);
    }

    private void showBatchCropDialog(List<File> images) {
        int minW = Integer.MAX_VALUE;
        int minH = Integer.MAX_VALUE;
        for (File f : images) {
            int[] dims = imageDims(f);
            if (dims[0] > 0) minW = Math.min(minW, dims[0]);
            if (dims[1] > 0) minH = Math.min(minH, dims[1]);
        }
        if (minW == Integer.MAX_VALUE) {
            Extensions.showMessage(context, "Cannot read images");
            return;
        }
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad / 2, pad, 0);
        EditText wInput = new EditText(context);
        wInput.setHint("Width");
        wInput.setText(String.valueOf(minW));
        wInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        wInput.setSingleLine(true);
        root.addView(wInput);
        EditText hInput = new EditText(context);
        hInput.setHint("Height");
        hInput.setText(String.valueOf(minH));
        hInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        hInput.setSingleLine(true);
        root.addView(hInput);
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.crop_X_imgs,  images.size()))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.crop, (d, w) -> {
                    int reqW;
                    int reqH;
                    try {
                        reqW = Integer.parseInt(wInput.getText().toString().trim());
                        reqH = Integer.parseInt(hInput.getText().toString().trim());
                        if (reqW <= 0 || reqH <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        Extensions.showMessage(context, R.string.enter_width_and_height);
                        return;
                    }
                    ArrayList<File> targets = new ArrayList<>(images);
                    boolean needJni = false;
                    for (File f : targets) {
                        if (isJpegPath(f.getName())) {
                            needJni = true;
                            break;
                        }
                    }
                    if (needJni && !NativeToolManager.loadJpegtranJni(context)) {
                        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                                .setTitle(R.string.crop_quality)
                                .setMessage(R.string.jpegtran_info)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setNeutralButton(R.string.standard_crop, (dd, ww) -> runBatchCrop(targets, reqW, reqH, true))
                                .setPositiveButton(R.string.lossless, (dd, ww) -> NativeToolManager.ensureJpegtran(context,
                                        new NativeToolManager.ReadyCallback() {
                                            public void onReady() {
                                                runBatchCrop(targets, reqW, reqH, false);
                                            }

                                            public void onError(String message) {
                                                Extensions.showMessage(context, message);
                                            }
                                        })).create());
                    } else {
                        runBatchCrop(targets, reqW, reqH, false);
                    }
                }).create());
    }

    private void runBatchCrop(List<File> images, int reqW, int reqH, boolean forceLossy) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                boolean useJni = NativeToolManager.loadJpegtranJni(context);
                int done = 0;
                int skipped = 0;
                for (File f : images) {
                    pm.setText(context.rss.getString(R.string.processing_x, f.getName()));
                    int[] dims = imageDims(f);
                    if (dims[0] <= 0 || dims[1] <= 0) {
                        skipped++;
                        continue;
                    }
                    int w = Math.min(reqW, dims[0]);
                    int h = Math.min(reqH, dims[1]);
                    int x = (dims[0] - w) / 2;
                    int y = (dims[1] - h) / 2;
                    backupImage(f);
                    if (isJpegPath(f.getName()) && useJni && !forceLossy) {
                        x -= x % 16;
                        y -= y % 16;
                        w -= w % 16;
                        h -= h % 16;
                        if (w <= 0 || h <= 0) {
                            skipped++;
                            continue;
                        }
                        File tmp = new File(context.getCacheDir(), "batchcrop_" + System.currentTimeMillis() + ".jpg");
                        String[] err = new String[1];
                        int rc = JpegtranJni.transform(f.getAbsolutePath(), tmp.getAbsolutePath(),
                                JpegtranJni.OP_CROP, w, h, x, y, err);
                        if (rc != 0) {
                            tmp.delete();
                            skipped++;
                            continue;
                        }
                        FileUtils.copyFile(tmp, f);
                        tmp.delete();
                        try {
                            ExifInterface exif =
                                    new ExifInterface(f.getAbsolutePath());
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                            exif.saveAttributes();
                        } catch (Exception ignored) {
                        }
                    } else {
                        boolean jpeg = isJpegPath(f.getName());
                        BitmapFactory.Options bitmapOpts = new BitmapFactory.Options();
                        bitmapOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath(), bitmapOpts);
                        if (src == null) {
                            skipped++;
                            continue;
                        }
                        int cx = Math.max(0, Math.min(x, src.getWidth() - 1));
                        int cy = Math.max(0, Math.min(y, src.getHeight() - 1));
                        int cw = Math.max(1, Math.min(w, src.getWidth() - cx));
                        int ch = Math.max(1, Math.min(h, src.getHeight() - cy));
                        Bitmap out = Bitmap.createBitmap(src, cx, cy, cw, ch);
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            out.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
                                    jpeg ? 95 : 100, fos);
                        } catch (Exception e) {
                            skipped++;
                            continue;
                        } finally {
                            if (out != src) out.recycle();
                            src.recycle();
                        }
                        if (jpeg) {
                            try {
                                ExifInterface exif =
                                        new ExifInterface(f.getAbsolutePath());
                                exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                                        String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                                exif.saveAttributes();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    done++;
                }
                pm.dismiss();
                int doneCount = done;
                int skippedCount = skipped;
                context.handler.post(() -> finishBatchOp(context.getString(R.string.cropped_xskippedx, doneCount, skippedCount)));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showBatchExifDialog(List<File> images) {
        String[] tags = {
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_DATETIME};
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad / 2, pad, 0);
        TextView hint = new TextView(context);
        hint.setTextSize(13);
        hint.setText(context.getString(R.string.hintbatchexif, images.size()));
        root.addView(hint);
        List<EditText> inputs = new ArrayList<>();
        for (String tag : tags) {
            TextView label = new TextView(context);
            label.setTextSize(13);
            label.setText(tag);
            root.addView(label);
            EditText input = new EditText(context);
            input.setSingleLine(true);
            root.addView(input);
            inputs.add(input);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.set_exif_tags)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String[] vals = new String[tags.length];
                    for (int i = 0; i < tags.length; i++) {
                        vals[i] = inputs.get(i).getText() == null ? "" : inputs.get(i).getText().toString();
                    }
                    runBatchExif(new ArrayList<>(images), tags, vals);
                }).create());
    }

    private void runBatchExif(List<File> images, String[] tags, String[] vals) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            int done = 0;
            for (File f : images) {
                pm.setText(context.rss.getString(R.string.processing_x, f.getName()));
                try {
                    backupImage(f);
                    ExifInterface exif =
                            new ExifInterface(f.getAbsolutePath());
                    for (int i = 0; i < tags.length; i++) {
                        if (!vals[i].isEmpty()) exif.setAttribute(tags[i], vals[i]);
                    }
                    exif.saveAttributes();
                    done++;
                } catch (Exception ignored) {
                }
            }
            pm.dismiss();
            int doneCount = done;
            context.handler.post(() -> finishBatchOp( context.rss.getString(R.string.updated_i_of_i, doneCount, images.size())));
        }).start();
    }

    private void confirmBatchStrip(List<File> images) {
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.remove_metadata)
                .setMessage(context.getString(R.string.strip_metadata_info, images.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove, (d, w) -> runBatchStrip(new ArrayList<>(images)))
                .create());
    }

    private void runBatchStrip(List<File> images) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            int done = 0;
            for (File f : images) {
                pm.setText(context.rss.getString(R.string.processing_x, f.getName()));
                try {
                    backupImage(f);
                    JpegMetaStrip.stripFile(f);
                    done++;
                } catch (Exception ignored) {
                }
            }
            pm.dismiss();
            int doneCount = done;
            context.handler.post(() -> finishBatchOp(context.getString(R.string.removed_metadata_fromxofx, doneCount, images.size())));
        }).start();
    }

    private void showOpenWithDialog(File file, String fileName) {
        List<String> actionNames = new ArrayList<>(Arrays.asList(
                context.getString(R.string.text_editor),
                context.getString(R.string.archive_viewer),
                context.getString(R.string.image_viewer),
                context.getString(R.string.hex_editor),
                context.getString(R.string.media_player),
                context.getString(R.string.apk_info)));
        List<Integer> actionIcons = new ArrayList<>(Arrays.asList(
                R.drawable.baseline_text_snippet_24,
                R.drawable.baseline_folder_zip_24,
                R.drawable.image_24px,
                R.drawable.ic_hash_mt,
                R.drawable.video_24px,
                R.drawable.apk_document_24px));
        List<Runnable> actionHandlers = new ArrayList<>(Arrays.asList(
                () -> {
                    if (!file.isFile()) {
                        Extensions.showMessage(context, R.string.cannot_open_item);
                        return;
                    }
                    openTextEditorRootAware(file);
                },
                () -> {
                    String lowerName = fileName.toLowerCase(Locale.ROOT);
                    boolean zipBased = lowerName.endsWith(".zip") || lowerName.endsWith(".apk")
                            || lowerName.endsWith(".jar") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk");
                    if (!file.isFile() || !zipBased) {
                        Extensions.showMessage(context, R.string.not_supported_archive);
                        return;
                    }
                    withReadableCopy(file, readable -> context.loadZipFolderInPane(readable, "", pane1, true));
                },
                () -> withReadableCopy(file, readable -> context.openImageViewer(readable.getAbsolutePath())),
                () -> {
                    if (!file.isFile()) {
                        Extensions.showMessage(context, R.string.cannot_open_item);
                        return;
                    }
                    openHexEditorRootAware(file);
                },
                () -> withReadableCopy(file, readable -> context.playMediaFile(readable.getAbsolutePath())),
                () -> {
                    String lowerExt = fileName.toLowerCase(Locale.ROOT);
                    if (!lowerExt.endsWith(".apk") && !lowerExt.endsWith(".apks") && !lowerExt.endsWith(".xapk")) {
                        Extensions.showMessage(context, R.string.not_an_apk);
                        return;
                    }
                    withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, fileName));
                }));
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".woff") || lower.endsWith(".woff2")) {
            actionNames.add(context.getString(R.string.font_preview));
            actionIcons.add(R.drawable.uppercase_24px);
            actionHandlers.add(() -> showFontPreview(file, fileName));
        }
        if (lower.endsWith(".arsc")) {
            actionNames.add(context.getString(R.string.arsc_functions));
            actionIcons.add(R.drawable.apk_document_24px);
            actionHandlers.add(() -> withReadableCopy(file, readable -> showArscOpenWith(readable, null, "resources.arsc")));
        }
        if (lower.endsWith(".xml")) {
            actionNames.add(context.getString(R.string.xml_functions));
            actionIcons.add(R.drawable.code_24px);
            actionHandlers.add(() -> showXmlFunctions(file, fileName));
        }
        String keyExt = FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
        if (keyExt.equals("jks") || keyExt.equals("keystore") || keyExt.equals("p12")
                || keyExt.equals("pfx") || keyExt.equals("pk8") || keyExt.equals("pem")) {
            actionNames.add(context.getString(R.string.import_signature));
            actionIcons.add(R.drawable.lock_24px);
            actionHandlers.add(() -> importSignature(file, fileName));
        }

        GridView gridView = new GridView(context);
        gridView.setNumColumns(3);
        gridView.setBackgroundColor(Color.TRANSPARENT);
        gridView.setPadding(16, 16, 16, 16);
        gridView.setVerticalSpacing(24);
        gridView.setAdapter(new ArrayAdapter<>(context, 0, actionNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);

                ImageView iconView = new ImageView(context);
                iconView.setImageResource(actionIcons.get(position));
                int iconSize = (int) (40 * context.getResources().getDisplayMetrics().density + 0.5f);
                iconView.setLayoutParams(new ViewGroup.LayoutParams(iconSize, iconSize));
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                ColorUtil.changeImageColor(iconView.getDrawable(), typedValue.data);

                TextView labelView = new TextView(context);
                labelView.setText(actionNames.get(position));
                labelView.setTextSize(12);
                labelView.setGravity(Gravity.CENTER);

                item.addView(iconView);
                item.addView(labelView);
                return item;
            }
        });

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView reportedView = new TextView(context);
        reportedView.setTextSize(12);
        TextView actualView = new TextView(context);
        actualView.setTextSize(12);
        content.addView(reportedView);
        content.addView(actualView);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SwitchMaterial useActualSwitch =
                new SwitchMaterial(context);
        useActualSwitch.setText(R.string.use_actual_mime);
        useActualSwitch.setChecked(settings.getBoolean("fix_mime_type", false));
        useActualSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.edit().putBoolean("fix_mime_type", isChecked).apply());
        content.addView(useActualSwitch);
        content.addView(gridView);
        try {
            reportedView.setText(context.getString(R.string.reported_mime) + ": "
                    + MimeUtil.getReportedMimeType(context, file));
        } catch (Exception ignored) {
        }
        new Thread(() -> {
            String real = MimeUtil.getRealMimeType(file);
            context.handler.post(() -> {
                try {
                    actualView.setText(context.getString(R.string.real_mime) + ": " + (real != null ? real : "—"));
                } catch (Exception ignored) {
                }
            });
        }).start();

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.open_with) + ": " + fileName)
                .setView(content)
                .setNeutralButton(context.rss.getString(R.string.more), (d, w) -> withReadableCopy(file, readable -> showAppsForMime(readable, fileName, useActualSwitch.isChecked())))
                .create();

        gridView.setOnItemClickListener((parent1, view1, position1, id1) -> {
            dialog.dismiss();
            try {
                actionHandlers.get(position1).run();
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private static String defaultAppKey(String mime) {
        return "openwith_default_" + mime.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
    }

    private void launchAppForMime(ResolveInfo info, Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClassName(info.activityInfo.packageName, info.activityInfo.name);
        context.startActivity(intent);
    }

    private void showAppsForMime(File file, String fileName, boolean useActual) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        String mime = useActual ? MimeUtil.getRealMimeType(file) : null;
        if (mime == null) mime = MimeUtil.getReportedMimeType(context, file);
        if (mime == null) mime = "application/octet-stream";
        final String chosenMime = mime;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String def = prefs.getString(defaultAppKey(chosenMime), null);
        PackageManager pm = context.getPackageManager();
        if (def != null) {
            ComponentName cn = ComponentName.unflattenFromString(def);
            if (cn != null) {
                try {
                    pm.getActivityInfo(cn, 0);
                    Intent probe = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, chosenMime);
                    probe.setComponent(cn);
                    List<ResolveInfo> stillThere = pm.queryIntentActivities(probe, 0);
                    if (!stillThere.isEmpty()) {
                        probe.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(probe);
                        return;
                    }
                } catch (Exception ignored) {
                }
                prefs.edit().remove(defaultAppKey(chosenMime)).apply();
            }
        }
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_VIEW).setDataAndType(uri, chosenMime),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (apps.isEmpty()) {
            Extensions.showMessage(context, R.string.no_apps_found);
            return;
        }
        apps.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.open_with) + ": " + fileName + " (" + chosenMime + ")")
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        list.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (12 * context.getResources().getDisplayMetrics().density + 0.5f);
                row.setPadding(pad, pad, pad, pad);
                ImageView icon = new ImageView(context);
                int s = (int) (40 * context.getResources().getDisplayMetrics().density + 0.5f);
                icon.setLayoutParams(new LinearLayout.LayoutParams(s, s));
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tp.leftMargin = pad;
                TextView name = new TextView(context);
                name.setTextSize(15);
                TextView sub = new TextView(context);
                sub.setTextSize(12);
                texts.addView(name);
                texts.addView(sub);
                row.addView(icon);
                row.addView(texts, tp);
                return new RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ResolveInfo info = apps.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                ImageView icon = (ImageView) row.getChildAt(0);
                TextView name = (TextView) texts.getChildAt(0);
                TextView sub = (TextView) texts.getChildAt(1);
                try {
                    icon.setImageDrawable(info.loadIcon(pm));
                } catch (Exception ignored) {
                }
                String label = String.valueOf(info.loadLabel(pm));
                name.setText(label);
                String currentDef = prefs.getString(defaultAppKey(chosenMime), null);
                boolean isDef = currentDef != null && currentDef.equals(
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString());
                sub.setText(isDef ? context.getString(R.string.default_app, label) : info.activityInfo.packageName);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    try {
                        launchAppForMime(info, uri, chosenMime);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
                row.setOnLongClickListener(v -> {
                    PopupMenu popup = new PopupMenu(context, row);
                    String flat = new ComponentName(
                            info.activityInfo.packageName, info.activityInfo.name).flattenToString();
                    if (isDef) {
                        popup.getMenu().add(context.getString(R.string.clear_default));
                    } else {
                        popup.getMenu().add(context.getString(R.string.set_as_default));
                    }
                    popup.setOnMenuItemClickListener(item -> {
                        if (isDef) prefs.edit().remove(defaultAppKey(chosenMime)).apply();
                        else prefs.edit().putString(defaultAppKey(chosenMime), flat).apply();
                        notifyDataSetChanged();
                        return true;
                    });
                    popup.show();
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private void showFontPreview(File file, String fileName) {
        withReadableCopy(file, readable -> {
            try {
                Typeface tf = Typeface.createFromFile(readable);
                LinearLayout root = new LinearLayout(context);
                root.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
                root.setPadding(pad, pad, pad, pad);
                TextView sample = new TextView(context);
                sample.setText("ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !?@#");
                sample.setTypeface(tf);
                sample.setTextSize(22);
                root.addView(sample);
                TextView meta = new TextView(context);
                meta.setText(fileName + " · " + readable.length() + " bytes");
                meta.setTextSize(13);
                root.addView(meta);
                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(context.getString(R.string.font_preview) + ": " + fileName)
                        .setView(root)
                        .setPositiveButton(android.R.string.ok, null)
                        .create());
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
    }

    private void showXmlFunctions(File file, String fileName) {
        withReadableCopy(file, readable -> {
            boolean binary = false;
            try (InputStream is = FileUtils.getInputStream(readable)) {
                binary = FileUtils.isAxml(is);
            } catch (Exception ignored) {
            }
            String[] items = binary
                    ? new String[]{context.getString(R.string.open_as_text), context.getString(R.string.decode_open)}
                    : new String[]{context.getString(R.string.open_as_text), context.getString(R.string.format_xml)};
            final boolean isBinary = binary;
            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                    .setTitle(context.getString(R.string.xml_functions) + ": " + fileName)
                    .setSingleChoiceItems(items, -1, (dialog, which) -> {
                        dialog.dismiss();
                        if (which == 0) {
                            context.startActivity(rootAwareEditorIntent(readable, file)
                                    .putExtra("path", readable.getPath()));
                        } else if (isBinary) {
                            try (InputStream is2 = FileUtils.getInputStream(readable)) {
                                context.startActivity(rootAwareEditorIntent(readable, file)
                                        .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2).decodeAsString().trim())
                                        .putExtra("axml", true)
                                        .putExtra("path", readable.getPath()));
                            } catch (Exception e) {
                                new ErrorUtil(context).showError(e);
                            }
                        } else {
                            formatXmlFile(readable, file, fileName);
                        }
                    }).create());
        });
    }

    private void formatXmlFile(File readable, File original, String fileName) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                Document doc;
                try (InputStream is = FileUtils.getInputStream(readable)) {
                    doc = dbf.newDocumentBuilder().parse(is);
                }
                Transformer transformer =
                        TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                File out = new File(context.getCacheDir(), System.currentTimeMillis() + "_formatted.xml");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    transformer.transform(new DOMSource(doc),
                            new StreamResult(fos));
                }
                if (!readable.getAbsolutePath().equals(original.getAbsolutePath())) {
                    AccessManager.copyFile(context, out.getAbsolutePath(), original.getAbsolutePath(), true);
                } else {
                    FileUtils.copyFile(out, readable);
                }
                out.delete();
                pm.dismiss();
                context.handler.post(() -> {
                    Extensions.showMessage(context, R.string.xml_formatted);
                    context.loadFolderInPane(original.getParentFile(), pane1);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void importSignature(File file, String fileName) {
        File keysDir = new File(Environment.getExternalStorageDirectory()
                + File.separator + "MT2" + File.separator + "keys");
        new Thread(() -> {
            try {
                if (!keysDir.isDirectory() && !keysDir.mkdirs() && !keysDir.isDirectory()) {
                    throw new IOException("Cannot create keys dir");
                }
                File dest = FileUtils.getUnusedFile(new File(keysDir, fileName));
                try (InputStream is = FileUtils.getInputStream(file);
                     FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                }
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                Set<String> paths = new HashSet<>(prefs.getStringSet("signature_key_paths", new HashSet<>()));
                paths.add(dest.getAbsolutePath());
                prefs.edit().putStringSet("signature_key_paths", paths).putString("keyPath", dest.getAbsolutePath()).apply();
                context.handler.post(() -> Extensions.showMessage(context, R.string.signature_file_set));
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void handleFileClick(File file, String fileName) {
        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        if (fileName.endsWith(".txt") || fileName.endsWith(".json")
            || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
            || fileName.endsWith(".gradle") || fileName.endsWith(".properties")) {
            openTextEditorRootAware(file);
        } else if(HashUtil.isChecksumFile(fileName)) {
            withReadableCopy(file, checksumDialogs::showHashVerifyDialog);
        } else if(fileName.endsWith(".xml")) {
            withReadableCopy(file, readable -> {
                try (InputStream is = FileUtils.getInputStream(readable)) {
                    if (FileUtils.isAxml(is)) try (InputStream is2 = FileUtils.getInputStream(readable)) {
                        context.startActivity(rootAwareEditorIntent(readable, file)
                                .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2).decodeAsString().trim())
                                .putExtra("axml", true)
                                .putExtra("path", readable.getPath()));
                    }
                    else context.startActivity(rootAwareEditorIntent(readable, file)
                            .putExtra("path", readable.getPath()));
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            });
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            withReadableCopy(file, readable -> context.openImageViewer(readable.getPath()));
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            withReadableCopy(file, readable -> context.playMediaFile(readable.getPath()));
        } else if ((ext.equals(".apk"))) {
            withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, fileName));
        } else {
            String bak = ".bak";
            if(ext.equals(bak)) {
                View et = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
                context.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                EditText tv = et.findViewById(R.id.m_et_edittext);
                String newName = fileName.replace(bak, "");
                tv.setText(newName);
                tv.requestFocus();
                tv.post(() -> {
                    tv.setSelection(0, newName.indexOf(FilenameUtils.getExtension(newName)) - 1);
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT);
                });
                new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.restore_backup)
                .setView(et)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    String bakPath = file.getPath();
                    String origPath = bakPath.replace(bak, "");
                    restoreBakRootAware(file, new File(origPath), fileName);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
            } else if (fileName.endsWith(".zip")) {
                withReadableCopy(file, readable -> context.loadZipFolderInPane(readable, "", pane1, true));
            } else if (fileName.endsWith(".arsc")) {
                withReadableCopy(file, readable -> showArscOpenWith(readable, null, "resources.arsc"));
            } else if (ArchiveUtil.isSupportedArchive(fileName)) {
                dialogUtil.styleAlertDialog(
                        dialogUtil.getDialogBuilder().setSingleChoiceItems(new CharSequence[] { context.rss.getString(R.string.extract), context.rss.getString(R.string.open_with) }, -1, (dialog, which) -> {
                            dialog.dismiss();
                            if (which == 0) withReadableCopy(file, fileOps::extractArchive);
                            else showOpenWithDialog(file, fileName);
                        }).create());
            } else if (fileName.endsWith(".apks") || fileName.endsWith(".xapk") || fileName.endsWith(".aspk") || fileName.endsWith(".apkm")) {
                withReadableCopy(file, readable -> showSplitApkMenu(readable, fileName));
            } else if (fileName.endsWith(".dex")) {
                fileOps.showDexOptionsDialog(file, null, null, fileName);
            } else {
                showOpenWithDialog(file, fileName);
            }
        }
    }

    private interface ReadableCallback {
        void onReady(File readable) throws Exception;
    }

    private void withReadableCopy(File file, ReadableCallback cb) {
        try {
            if (file != null && file.exists() && file.canRead()) {
                cb.onReady(file);
                return;
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        if (!AccessManager.fileOpsOn(context)) {
            Extensions.showMessage(context, R.string.cannot_open_permission_denied);
            return;
        }
        Extensions.showMessage(context, R.string.reading_with_elevated_access);
        new Thread(() -> {
            try {
                File staged = RootStaging.stageForRead(context, file.getAbsolutePath());
                context.handler.post(() -> {
                    try {
                        cb.onReady(staged);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    private Intent rootAwareEditorIntent(File readable, File original) {
        Intent i = new Intent(context, TextEditorActivity.class);
        if (readable != null && original != null
                && !readable.getAbsolutePath().equals(original.getAbsolutePath())) {
            i.putExtra("rootOriginalPath", original.getAbsolutePath());
            Extensions.showMessage(context, R.string.opened_with_root);
        }
        return i;
    }

    private void openTextEditorRootAware(File file) {
        withReadableCopy(file, readable ->
                context.startActivity(rootAwareEditorIntent(readable, file)
                        .putExtra("path", readable.getAbsolutePath())));
    }

    private void openHexEditorRootAware(File file) {
        withReadableCopy(file, readable -> {
            Intent i = new Intent(context, HexEditorActivity.class)
                    .putExtra("path", readable.getAbsolutePath());
            if (!readable.getAbsolutePath().equals(file.getAbsolutePath())) {
                i.putExtra("rootOriginalPath", file.getAbsolutePath());
                Extensions.showMessage(context, R.string.opened_with_root);
            }
            context.startActivity(i);
        });
    }

    private void restoreBakRootAware(File bakFile, File origFile, String fileName) {
        String bakPath = bakFile.getPath();
        String origPath = origFile.getPath();
        boolean useElevated = AccessManager.fileOpsOn(context)
                && (RootStaging.needsStaging(context, bakPath) || RootStaging.needsStaging(context, origPath));
        if (useElevated) {
            new Thread(() -> {
                try {
                    boolean origExists = AccessManager.exists(context, origPath) || origFile.exists();
                    if (origExists) AccessManager.rename(context, origPath, origPath + "_tmp_.bak", true);
                    AccessManager.rename(context, bakPath, origPath, true);
                    if (origExists) AccessManager.rename(context, origPath + "_tmp_.bak", bakPath, true);
                    context.handler.post(() -> context.loadFolderInPane(
                            bakFile.getParentFile() != null ? bakFile.getParentFile() : new File("/"), pane1));
                } catch (Exception e) {
                    context.handler.post(() -> new ErrorUtil(context).showError(e));
                }
            }).start();
            return;
        }
        boolean origExists = origFile.exists();
        File tmpFile = new File(origPath + "_tmp_.bak");
        if (origExists) {
            //noinspection ResultOfMethodCallIgnored
            origFile.renameTo(tmpFile);
        }
        //noinspection ResultOfMethodCallIgnored
        bakFile.renameTo(new File(origPath));
        if (origExists) {
            //noinspection ResultOfMethodCallIgnored
            tmpFile.renameTo(new File(bakPath));
        }
        context.handler.post(() -> context.loadFolderInPane(
                bakFile.getParentFile() != null ? bakFile.getParentFile() : new File("/"), pane1));
    }

    private void showSplitApkMenu(File readable, String displayName) {
        String[] items = new String[] { context.rss.getString(R.string.install), context.rss.getString(R.string.view), context.rss.getString(R.string.sign), context.rss.getString(R.string.antisplit_merge_to_apk) };
        dialogUtil.styleAlertDialog(
                dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog, which) -> {
                    dialog.dismiss();
                    try {
                        switch (which) {
                            case 0:
                                if (LegacyUtils.aboveSdk20) {
                                    new Thread(() -> {
                                        try (ZipFile zf = new ZipFile(readable)) {
                                            List<File> apkFiles = new ArrayList<>();
                                            File tmpDir = new File(context.getCacheDir(), "split_install_" + System.currentTimeMillis());
                                            //noinspection ResultOfMethodCallIgnored
                                            tmpDir.mkdirs();
                                            for (FileHeader fh : zf.getFileHeaders()) {
                                                if (fh.getFileName().endsWith(".apk")) {
                                                    File tmpApk = new File(tmpDir, new File(fh.getFileName()).getName());
                                                    try (InputStream is = zf.getInputStream(fh);
                                                         FileOutputStream fos = new FileOutputStream(tmpApk)) {
                                                        byte[] buf = new byte[65536];
                                                        int n;
                                                        while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                                                    }
                                                    apkFiles.add(tmpApk);
                                                }
                                            }
                                            if (!apkFiles.isEmpty()) {
                                                InstallUtil.installSplitApksWithDialog(context, apkFiles, displayName);
                                            } else {
                                                context.runOnUiThread(() -> Extensions.showMessage(context, R.string.no_apk_files_found));
                                            }
                                        } catch (Exception e) {
                                            context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
                                        }
                                    }).start();
                                } else {
                                    Extensions.showMessage(context, "Installing split APKs is not supported on this version of Android :(");
                                    context.handler.postDelayed(() -> Extensions.showMessage(context, "You could try merging the APK then installing it"), 1500);
                                }
                                break;
                            case 1:
                                context.loadZipFolderInPane(readable, "", pane1, true);
                                break;
                            case 2:
                                SignatureKeyDialog.show(context, readable, true);
                                break;
                            case 3:
                                MergeUtil.showAntisplitDialog(readable, context);
                                break;
                        }
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                }).create());
    }

    private void showArscOpenWith(File arscFile, File apkFile, String entryPath) {
        String[] options = {context.rss.getString(R.string.arsc_editor_plus), context.rss.getString(R.string.arsc_editor), context.rss.getString(R.string.translation_mode), context.rss.getString(R.string.resource_querier)};
        String[] modes = {
                ArscEditorPlusActivity.MODE_PLUS,
                ArscEditorPlusActivity.MODE_EDITOR,
                ArscEditorPlusActivity.MODE_TRANSLATE,
                ArscEditorPlusActivity.MODE_QUERIER};
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.open_with)
                .setSingleChoiceItems(options, -1, (dialog, which) -> {
                    dialog.dismiss();
                    Class<?> target = ArscEditorPlusActivity.MODE_EDITOR.equals(modes[which])
                            ? ArscEditorActivity.class
                            : ArscEditorPlusActivity.class;
                    Intent arscIntent = new Intent(context, target)
                            .putExtra("path", arscFile.getAbsolutePath())
                            .putExtra("apkPath", apkFile == null ? null : apkFile.getAbsolutePath())
                            .putExtra("zipEntryPath", entryPath)
                            .putExtra("arscMode", modes[which]);
                    // Inside an archive the editor only edits the extracted copy and
                    // returns it via setResult(757); MainActivity then shows the
                    // "APK/ZIP updated" prompt and injects the file itself.
                    if (apkFile != null) context.startActivityForResult(arscIntent, 757);
                    else context.startActivity(arscIntent);
                }).create());
    }

    private CharSequence getFilesToDisplay(boolean multi, int position) {
        if (multi) {
            StringBuilder sb = new StringBuilder();
            for (int i : selectedPositions) sb.append(',').append(isInZip ? ((ZipEntryInfo) values[i]).getName() : ((File) values[i]).getName());
            return sb.deleteCharAt(0);
        }
        return isInZip ? ((ZipEntryInfo) values[position]).getName() : ((File) values[position]).getName();
    }

    private void showRenameDialog(int position, File file, ZipEntryInfo entry, String fileName, boolean multi) {
        if (multi) {
            RenameUtil.showMultiRenameDialog(context, selectedPositions, isInZip, values, pane1, currentZipPath);
            return;
        }
        MaterialAlertDialogBuilder renameDialog = dialogUtil.getDialogBuilder();
        View rnm = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
        EditText renameInput = rnm.findViewById(R.id.m_et_edittext);
        renameInput.setText(fileName);
        renameInput.requestFocus();
        renameInput.post(() -> {
            renameInput.setSelection(0, (isInZip ? !entry.isDirectory() : file.isFile()) && fileName.contains(".") ? fileName.indexOf(FilenameUtils.getExtension(fileName)) - 1 : fileName.length());
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(renameInput, InputMethodManager.SHOW_IMPLICIT);
        });
        renameDialog
                .setTitle(context.rss.getString(R.string.rename_1, fileName))
                .setView(rnm)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(android.R.string.paste, (dialog1, which) -> {
                    int selectionStart = renameInput.getSelectionStart();
                    int selectionEnd = renameInput.getSelectionEnd();
                    if (selectionStart != selectionEnd) {
                        renameInput.getText().delete(selectionStart, selectionEnd);
                    }
                    CharSequence text = ((ClipboardManager) context
                            .getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                    if (!TextUtils.isEmpty(text))
                        renameInput.getText().insert(selectionStart, text);
                })
                .setPositiveButton(android.R.string.ok, (dialog3, which) -> {
                    String s = renameInput.getText().toString();
                    if(isInZip) {
                        File zipFile = entry.getZipFile();
                        try (ZipFile zf = new ZipFile(zipFile)) {
                            String entryName = entry.getName();
                            if (entry.isDirectory()) {
                                Map<String, String> map = new HashMap<>();

                                for(FileHeader fh : zf.getFileHeaders()) {
                                    String fhFileName = fh.getFileName();
                                    if(fhFileName.startsWith(entryName)) map.put(fhFileName, fhFileName.replace(entryName, s));
                                }
                                if(!map.isEmpty()) zf.renameFiles(map);
                            } else zf.renameFile((entryName), s);
                            context.loadZipFolderInPane(zipFile, currentZipPath, pane1, false);
                        } catch (Exception e) {
                            new ErrorUtil(context).showError(e);
                        }
                    } else {
                        File ogFolder = file.getParentFile();
                        if (AccessManager.fileOpsOn(context)) {
                            try {
                                AccessManager.rename(context, file.getAbsolutePath(), new File(ogFolder, s).getAbsolutePath(), true);
                                context.loadFolderInPane(ogFolder, pane1);
                            } catch (Exception e) {
                                if (file.renameTo(new File(ogFolder, s))) context.loadFolderInPane(ogFolder, pane1);
                                else Extensions.showMessage(context, context.rss.getString(R.string.failed_to_renamex, fileName));
                            }
                        } else {
                            if (file.renameTo(new File(ogFolder, s))) context.loadFolderInPane(ogFolder, pane1);
                            else Extensions.showMessage(context, context.rss.getString(R.string.failed_to_renamex, fileName));
                        }
                    }
                });
        AlertDialog ad = renameDialog.create();
        dialogUtil.styleAlertDialog(ad);
        ad.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v6 -> {
                    int selectionStart = renameInput.getSelectionStart();
                    int selectionEnd = renameInput.getSelectionEnd();
                    if (selectionStart != selectionEnd) {
                        renameInput.getText().delete(selectionStart, selectionEnd);
                    }
                    CharSequence text = ((ClipboardManager) context
                            .getSystemService(Context.CLIPBOARD_SERVICE)).getText();
                    if (!TextUtils.isEmpty(text))
                        renameInput.getText().insert(selectionStart, text);
                });
    }

    private void showDeleteDialog(int position, File file, ZipEntryInfo entry, boolean multi) {
        ProgressManager pm = new ProgressManager(context, true);
        MaterialAlertDialogBuilder deleteDialog = dialogUtil.getDialogBuilder();
        CharSequence filesToDisplay = getFilesToDisplay(multi, position);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean[] sign = new boolean[1];
        File zipFile = isInZip ? entry.getZipFile() : null;
        if(isInZip && zipFile.getName().endsWith(".apk")) {
            LinearLayout ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.item_modified_dialog, null);
            ll.<TextView>findViewById(R.id.modifiedText).setText(context.rss.getString(R.string.confirm_delete_f, filesToDisplay));
            CheckBox autosign = ll.findViewById(R.id.autosign);
            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
            ll.findViewById(R.id.sign_settings).setOnClickListener(uiHelper.showSignSettingsDialog());
            deleteDialog.setView(ll);
        } else deleteDialog.setMessage(context.rss.getString(R.string.confirm_delete_f, filesToDisplay));
        deleteDialog.setTitle(context.rss.getString(R.string.warning)).setPositiveButton(context.rss.getString(R.string.yes), (dialog3, which) -> {
            SignWrapper[] wrapper = new SignWrapper[1];
            Runnable doDelete = () -> {
                pm.show();
                new Thread(() -> {
                    try {
                        if(isInZip) FileUtils.copyFile(zipFile, new File(zipFile.getParent(), zipFile.getName() + ".bak"));
                        boolean useElevatedForDelete = AccessManager.fileOpsOn(context);
                        if (multi) {
                            if (!isInZip) {
                                File selectedFile = null;
                                for (int i : selectedPositions) {
                                    selectedFile = (File) values[i];
                                    File finalSelectedFile1 = selectedFile;
                                    if (finalSelectedFile1 != null)
                                        pm.setText(context.rss.getString(R.string.deleting, finalSelectedFile1.getName()));

                                    if (useElevatedForDelete) {
                                        try {
                                            AccessManager.delete(context, selectedFile.getAbsolutePath(), true);
                                            continue;
                                        } catch (Exception ignored) {}
                                    }
                                    if (selectedFile.isDirectory())
                                        Util.deleteDir(selectedFile);
                                    else
                                        selectedFile.delete();
                                }
                                if (selectedFile != null) {
                                    File finalSelectedFile = selectedFile;
                                    context.handler.post(() -> {
                                        clearSelection();
                                        context.loadFolderInPane(finalSelectedFile.getParentFile(), pane1);
                                    });
                                } else context.handler.post(this::clearSelection);
                            } else {
                                List<ZipEntryInfo> selected = new ArrayList<>();
                                for (int i : selectedPositions) selected.add((ZipEntryInfo) values[i]);
                                fileOps.deleteZipEntry(selected.toArray(new ZipEntryInfo[0]));
                                if (sign[0]) wrapper[0].signApk(zipFile);
                                context.handler.post(this::clearSelection);
                            }
                        } else if (!isInZip) {
                            int total = (int) Util.countInsideFolder(file).total();
                            pm.setProgress(0, total);
                            pm.setText(context.rss.getString(R.string.deleting, file.getName()));

                            if (useElevatedForDelete) {
                                try {
                                    AccessManager.delete(context, file.getAbsolutePath(), true);
                                } catch (Exception e) {
                                    if (file.isDirectory()) Util.deleteDir(file, pm, total);
                                    else file.delete();
                                }
                            } else {
                                if (file.isDirectory()) Util.deleteDir(file, pm, total);
                                else file.delete();
                            }
                            context.handler.post(() -> {
                                clearSelection();
                                context.loadFolderInPane(file.getParentFile(), pane1);
                            });
                        } else {
                            fileOps.deleteZipEntry(entry);
                            if (sign[0]) wrapper[0].signApk(zipFile);
                            context.handler.post(this::clearSelection);
                        }
                        pm.dismiss();
                    } catch (Exception e) {
                        pm.dismiss();
                        new ErrorUtil(context).showError(e);
                    }
                }).start();
            };
            Runnable checkAndRun = () -> {
                boolean inKeyDir = false;
                if (!isInZip && file != null) {
                    String path = file.getAbsolutePath();
                    if (RootManager.isPathInKeyDirectory(path)) {
                        inKeyDir = true;
                    }
                }
                if (inKeyDir) {
                    new MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.warning_dangerous_directory)
                            .setMessage(R.string.warn_delete_s)
                            .setPositiveButton(R.string.delete, (d, w) -> {
                                if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                                    wrapper[0] = sw;
                                    doDelete.run();
                                }); else doDelete.run();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else {
                    if (sign[0]) SignWrapper.requireAuth(context, sw -> {
                        wrapper[0] = sw;
                        doDelete.run();
                    }); else doDelete.run();
                }
            };
            checkAndRun.run();
        }).setNegativeButton(android.R.string.cancel, (dialog1, which1) -> pm.dismiss());
        dialogUtil.styleAlertDialog(deleteDialog.create());
    }

    private void showCompressDialog(File file, String fileName, boolean multi) {
        if (isInZip) {
            return;
        }
        File parentFile2 = file.getParentFile();
        String parentFileName = parentFile2.getName();
        MaterialAlertDialogBuilder compressDialog = dialogUtil.getDialogBuilder();
        compressDialog.setTitle(context.rss.getString(R.string.compress));
        View compressView = LayoutInflater.from(context).inflate(R.layout.compress_dialog, null);

        TextInputEditText filenameEditText = compressView.findViewById(R.id.filename_compress_edittext);
        filenameEditText.setText((multi ? parentFileName : FilenameUtils.removeExtension(fileName)) + ".zip");
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        String[] archiveFormats = ArchiveUtil.getSupportedCreateExts();
        AutoCompleteTextView archiveFormatInput = compressView.findViewById(R.id.compress_format);
        archiveFormatInput.setText(archiveFormats[0]);
        archiveFormatInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, archiveFormats));

        AutoCompleteTextView compressLevelInput = compressView.findViewById(R.id.compress_level);
        compressLevelInput.setText(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
        List<String> compressionLevels = new ArrayList<>();
        for (CompressionLevel cl : CompressionLevel.values()) compressionLevels.add(cl.name());
        compressLevelInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, compressionLevels));
        compressLevelInput.setOnItemClickListener((parent2, view1, position2, id1) -> settings.edit().putString("compressLevel", compressionLevels.get(position2)).apply());
        compressDialog.setView(compressView);
        compressDialog.setNegativeButton(context.rss.getString(android.R.string.cancel), null);
        ProgressManager pm = new ProgressManager(context, true);
        compressDialog.setPositiveButton(context.rss.getString(R.string.compress), (dialog4, which) -> {
            String name = ((TextInputEditText) compressView.findViewById(R.id.filename_compress_edittext)).getText().toString().trim();
            if (name.isEmpty()) name = multi ? parentFileName : FilenameUtils.removeExtension(fileName);
            String format = ((AutoCompleteTextView) compressView.findViewById(R.id.compress_format)).getText().toString().trim();
            if (!format.startsWith(".")) format = "." + format;
            if (!name.toLowerCase(Locale.ENGLISH).endsWith(format)) name += format;
            File outputZip = new File(parentFile2, name);
            if (outputZip.exists()) {
                File existing = outputZip;
                String existingFormat = format;
                dialogUtil.getDialogBuilder()
                        .setTitle(context.rss.getString(R.string.output_exists_title))
                        .setMessage(context.rss.getString(R.string.output_exists_msg, existing.getName()))
                        .setPositiveButton(context.rss.getString(R.string.create_new_file), (d, w) -> runCompress(FileUtils.getUnusedFile(existing), existingFormat, file, fileName, multi, compressView, pm))
                        .setNeutralButton(context.rss.getString(R.string.add_to_existing), (d, w) -> runCompress(existing, existingFormat, file, fileName, multi, compressView, pm))
                        .setNegativeButton(context.rss.getString(android.R.string.cancel), null)
                        .show();
                return;
            }
            runCompress(outputZip, format, file, fileName, multi, compressView, pm);
        });
        pm.setText(context.rss.getString(R.string.compressing));
        context.handler.post(compressDialog::show);
    }

    private void runCompress(File outputZip, String format, File file, String fileName, boolean multi, View compressView, ProgressManager pm) {
        pm.show();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        new Thread(() -> {

                List<File> sources = new ArrayList<>();
                if (multi) {
                    for (int i : selectedPositions) sources.add((File) values[i]);
                } else sources.add(file);

                boolean compressElevated = AccessManager.fileOpsOn(context);
                File compressStageTmp = null;
                List<File> readableSources = sources;
                File effectiveOutput = outputZip;
                boolean outputToRoot = false;
                if (compressElevated) {
                    boolean anyNeedStage = false;
                    for (File s : sources) {
                        if (RootStaging.needsStaging(context, s)) { anyNeedStage = true; break; }
                    }
                    if (anyNeedStage) {
                        try {
                            compressStageTmp = new File(context.getCacheDir(), "compress_stage_" + System.currentTimeMillis());
                            //noinspection ResultOfMethodCallIgnored
                            compressStageTmp.mkdirs();
                            readableSources = new ArrayList<>();
                            for (File s : sources) {
                                if (RootStaging.needsStaging(context, s)) {
                                    File stagedChild = new File(compressStageTmp, s.getName());
                                    if (s.isDirectory()) AccessManager.stageTree(context, s.getAbsolutePath(), stagedChild);
                                    else AccessManager.stageFile(context, s.getAbsolutePath(), stagedChild);
                                    readableSources.add(stagedChild);
                                } else readableSources.add(s);
                            }
                        } catch (Exception e) {
                            pm.dismiss();
                            new ErrorUtil(context).showError(e);
                            return;
                        }
                    }
                    File outParent = outputZip.getParentFile();
                    if (outParent != null && !outParent.canWrite()
                            && AccessManager.exists(context, outParent.getAbsolutePath())) {
                        outputToRoot = true;
                        if (compressStageTmp == null) {
                            compressStageTmp = new File(context.getCacheDir(),
                                    "compress_stage_" + System.currentTimeMillis());
                            //noinspection ResultOfMethodCallIgnored
                            compressStageTmp.mkdirs();
                        }
                        effectiveOutput = new File(compressStageTmp, outputZip.getName());
                    }
                }
                final List<File> finalSources = readableSources;
                final File finalOutput = effectiveOutput;
                final boolean finalToRoot = outputToRoot;
                final File finalStageTmp = compressStageTmp;

                if (format.equals(".zip")) {
                    ZipParameters zipParameters = new ZipParameters();
                    CompressionLevel compressionLevel = CompressionLevel.valueOf(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
                    zipParameters.setCompressionLevel(compressionLevel);
                    if (compressionLevel == CompressionLevel.NO_COMPRESSION)
                        zipParameters.setCompressionMethod(CompressionMethod.STORE);
                    CharSequence pw = ((TextView) compressView.findViewById(R.id.pw_edittext)).getText();

                    try (ZipFile zf = new ZipFile(finalOutput)) {
                        if (!TextUtils.isEmpty(pw)) {
                            zipParameters.setEncryptFiles(true);
                            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                            zf.setPassword(pw.toString().toCharArray());
                        }
                        for (File source : finalSources) {
                            if (source.isDirectory())
                                zf.addFolder(source, zipParameters);
                            else zf.addFile(source, zipParameters);
                        }
                        if (finalToRoot) AccessManager.uploadFile(context, finalOutput, outputZip.getAbsolutePath());
                        pm.dismiss();
                    } catch (Exception e) {
                        pm.dismiss();
                        new ErrorUtil(context).showError(e);
                    } finally {
                        context.handler.post(context::reloadCurrentFolder);
                        if (finalStageTmp != null && !finalToRoot) Util.deleteDir(finalStageTmp);
                        else if (finalStageTmp != null && finalToRoot && !finalOutput.equals(outputZip)) {
                            // Keep only the delivered archive; drop staged sources.
                            for (File s : finalSources) {
                                if (s.getParentFile() != null && s.getParentFile().equals(finalStageTmp)
                                        && !s.equals(finalOutput)) Util.deleteDir(s);
                            }
                            //noinspection ResultOfMethodCallIgnored
                            finalOutput.delete();
                        }
                    }
                } else {
                    try {
                        ArchiveUtil.create(finalOutput, finalSources);
                        if (finalToRoot) AccessManager.uploadFile(context, finalOutput, outputZip.getAbsolutePath());
                        pm.dismiss();
                        context.handler.post(context::reloadCurrentFolder);
                    } catch (Exception e) {
                        pm.dismiss();
                        new ErrorUtil(context).showError(e);
                    } finally {
                        if (finalStageTmp != null) Util.deleteDir(finalStageTmp);
                    }
                }
        }).start();
    }

    private void updateFolderCountOnMainScreen(int position) {
    }

    public void handleSwipe(int position) {
        context.setCurrentPane(pane1 ? 1 : 2);
        if (isMultiSelectMode) {
            if (rangeStartPosition != null) {
                int start = Math.min(rangeStartPosition, position);
                int end = Math.max(rangeStartPosition, position);
                for (int i = start; i <= end; i++) {
                    selectedPositions.add(i);
                }
                updateFolderCountOnMainScreen(position);
                rangeStartPosition = null;
            } else {
                selectedPositions.add(position);
                rangeStartPosition = position;
                updateFolderCountOnMainScreen(position);
            }
        } else {
            isMultiSelectMode = true;
            rangeStartPosition = position;
            selectedPositions.add(position);
            updateFolderCountOnMainScreen(position);
            context.setMultiSelectModeUI(true);
        }
        notifyDataSetChanged();
    }

    public void handleMultiSelect(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
            if (selectedPositions.isEmpty()) {
                isMultiSelectMode = false;
                rangeStartPosition = null;
                context.setMultiSelectModeUI(false);
                if (isInZip) {
                    List<Object> zipEntryInfos = Arrays.asList(values);
                    context.setCurrentFolder(currentZipPath, zipEntryInfos);
                } else
                    context.setCurrentFolder(pane1 ? context.pane1Folder : context.pane2Folder, (File[]) values);
            } else
                updateFolderCountOnMainScreen(position);
        } else {
            selectedPositions.add(position);
            updateFolderCountOnMainScreen(position);
        }
        notifyDataSetChanged();
    }

    public List<Object> getSelectedFiles() {
        List<Object> selectedFiles = new ArrayList<>();
        for (Integer position : selectedPositions) {
            selectedFiles.add(values[position]);
        }
        return selectedFiles;
    }

    public void clearSelection() {
        selectedPositions.clear();
        isMultiSelectMode = false;
        rangeStartPosition = null;
        context.setMultiSelectModeUI(false);
        notifyDataSetChanged();
    }

    public void exitMultiSelectMode() {
        clearSelection();
        if (isInZip) {
            context.setCurrentFolder(currentZipPath, Arrays.asList(values));
        } else {
            context.setCurrentFolder(pane1 ? context.pane1Folder : context.pane2Folder, (File[]) values);
        }
    }

    public void invertSelection() {
        isMultiSelectMode = true;
        for (int i = (isInZip ? 0 : 1); i < values.length; i++) {
            if (selectedPositions.contains(i)) selectedPositions.remove(i);
            else selectedPositions.add(i);
        }
        notifyDataSetChanged();
    }

    public void selectSameType() {
        if (selectedPositions.isEmpty() || values.length == 0) return;
        Object ref = values[selectedPositions.iterator().next()];
        boolean refIsFolder = isInZip ? ((ZipEntryInfo) ref).isDirectory() : ((File) ref).isDirectory();
        String refName = ref instanceof File ? ((File) ref).getName() : ((ZipEntryInfo) ref).getName();
        String refExt = FilenameUtils.getExtension(refName).toLowerCase(Locale.ROOT);

        isMultiSelectMode = true;
        selectedPositions.clear();
        for (int i = (isInZip ? 0 : 1); i < values.length; i++) {
            Object o = values[i];
            boolean isFolder = isInZip ? ((ZipEntryInfo) o).isDirectory() : ((File) o).isDirectory();
            if (refIsFolder) {
                if (isFolder) selectedPositions.add(i);
            } else if (!isFolder) {
                String n = o instanceof File ? ((File) o).getName() : ((ZipEntryInfo) o).getName();
                if (FilenameUtils.getExtension(n).toLowerCase(Locale.ROOT).equals(refExt))
                    selectedPositions.add(i);
            }
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        isMultiSelectMode = true;
        for (int i = (isInZip ? 0 : 1); i < values.length; i++) selectedPositions.add(i);
        notifyDataSetChanged();
    }
}
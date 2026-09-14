package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import androidx.preference.PreferenceManager;
import android.text.ClipboardManager;
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
import android.widget.TextView;
import io.github.codehasan.colorpicker.extensions.Extensions;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.aXMLDecoder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.reandroid.apkeditor.Util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.DialogAdapter;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.listeners.SwipeTouchListener;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.activities.CompareTextActivity;
import io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareArscDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareZipDialog;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RenameUtil;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;

public class MainFilesArrayAdapter extends RecyclerView.Adapter<MainFilesArrayAdapter.ViewHolder> {

    private final MainActivity context;
    public final Object[] values;
    public boolean isInZip;
    public String currentZipPath;
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
        Object item = values[position];
        File file;
        ZipEntryInfo entry;
        String fileName;

        holder.fileNameView.setText("");
        holder.fileDateView.setText("");
        holder.fileIconView.setImageDrawable(null);

        int scale = UiPrefs.getScale(context);
        holder.fileNameView.setTextSize(UiPrefs.nameSize(scale));
        holder.fileNameView.setMaxLines(UiPrefs.getMaxLines(context));
        holder.fileNameView.setEllipsize(android.text.TextUtils.TruncateAt.END);
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

            int finalPosition1 = finalPosition;
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
                    items[mi] = menuItems.get(mi).label;
                    itemIds[mi] = menuItems.get(mi).id;
                }

                final Object finalCompareFile1 = compareFile1;
                final Object finalCompareFile2 = compareFile2;

                GridView gridView = new GridView(context);
                gridView.setNumColumns(2);
                gridView.setBackgroundColor(Color.TRANSPARENT);
                gridView.setPadding(16, 16, 16, 16);
                gridView.setAdapter(new DialogAdapter(context, menuItems, isInZip));
                gridView.setVerticalSpacing(40);
                AlertDialog dialog = dialogUtil.getDialogBuilder()
                        .setTitle(fileName)
                        .setView(gridView)
                        .create();

                gridView.setOnItemClickListener((parent1, view, position1, id) -> {
                    dialog.dismiss();
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
                                        Extensions.showMessage(context, "Checksums for multiple zip entries not supported");
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
                            case FileMenuOrder.CMD:
                                if (isInZip) {
                                    Extensions.showMessage(context, "Command Helper not supported for zip entries");
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
                                        showRenameDialog(finalPosition1, file, entry, fileName, multi);
                                        break;
                                    case FileMenuOrder.DELETE:
                                        showDeleteDialog(finalPosition1, file, entry, multi);
                                        break;
                                    case FileMenuOrder.COMPRESS:
                                        showCompressDialog(file, fileName, multi);
                                        break;
                                    case FileMenuOrder.PROPERTIES:
                                        propertiesDialog.show(multi, values, selectedPositions, isInZip, file, entry, fileName, getFilesToDisplay(multi, finalPosition1).toString());
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
                });
                dialogUtil.styleAlertDialog(dialog);
                return true;
            };
            context.handler.post(() -> convertView.setOnTouchListener(new SwipeTouchListener(
                    context,
                    originalClickListener,
                    originalLongClickListener,
                    finalPosition,
                    MainFilesArrayAdapter.this,
                    pane1 ? 1 : 2)));
        }).start();

    }

    private void showOpenWithDialog(File file, String fileName) {
        String[] actions = {
                context.getString(R.string.text_editor),
                context.getString(R.string.archive_viewer),
                context.getString(R.string.image_viewer),
                context.getString(R.string.hex_editor),
                context.getString(R.string.media_player),
                context.getString(R.string.apk_info)
        };
        int[] icons = {
                R.drawable.baseline_text_snippet_24,
                R.drawable.baseline_folder_zip_24,
                R.drawable.image_24px,
                R.drawable.ic_hash_mt,
                R.drawable.video_24px,
                R.drawable.apk_document_24px
        };

        GridView gridView = new GridView(context);
        gridView.setNumColumns(3);
        gridView.setBackgroundColor(Color.TRANSPARENT);
        gridView.setPadding(16, 16, 16, 16);
        gridView.setVerticalSpacing(24);
        gridView.setAdapter(new ArrayAdapter<>(context, 0, actions) {
            @NonNull
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);

                ImageView iconView = new ImageView(context);
                iconView.setImageResource(icons[position]);
                int iconSize = (int) (40 * context.getResources().getDisplayMetrics().density + 0.5f);
                iconView.setLayoutParams(new ViewGroup.LayoutParams(iconSize, iconSize));
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                ColorUtil.changeImageColor(iconView.getDrawable(), typedValue.data);

                TextView labelView = new TextView(context);
                labelView.setText(actions[position]);
                labelView.setTextSize(12);
                labelView.setGravity(Gravity.CENTER);

                item.addView(iconView);
                item.addView(labelView);
                return item;
            }
        });

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.open_with) + ": " + fileName)
                .setView(wrapOpenWithContent(gridView))
                .setNeutralButton(context.rss.getString(R.string.more), (d, w) -> {
                    withReadableCopy(file, readable -> {
                        Uri u = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
                        String mime = MimeUtil.getMimeTypeForAction(context, readable);
                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW)
                                .setDataAndType(u, mime != null ? mime : "application/octet-stream")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open " + fileName));
                    });
                })
                .create();

        gridView.setOnItemClickListener((parent1, view1, position1, id1) -> {
            dialog.dismiss();
            try {
                switch (position1) {
                    case 0 -> { // Text editor
                        if (!file.isFile()) {
                            Extensions.showMessage(context, R.string.cannot_open_item);
                            return;
                        }
                        openTextEditorRootAware(file);
                    }
                    case 1 -> { // Archive viewer
                        String lowerName = fileName.toLowerCase(Locale.ROOT);
                        boolean zipBased = lowerName.endsWith(".zip") || lowerName.endsWith(".apk")
                                || lowerName.endsWith(".jar") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk");
                        if (!file.isFile() || !zipBased) {
                            Extensions.showMessage(context, R.string.not_supported_archive);
                            return;
                        }
                        withReadableCopy(file, readable -> context.loadZipFolderInPane(readable, "", pane1, true));
                    }
                    case 2 -> // Image viewer
                        withReadableCopy(file, readable -> context.openImageViewer(readable.getAbsolutePath()));
                    case 3 -> { // Hex editor
                        if (!file.isFile()) {
                            Extensions.showMessage(context, R.string.cannot_open_item);
                            return;
                        }
                        openHexEditorRootAware(file);
                    }
                    case 4 -> // Media player
                        withReadableCopy(file, readable -> context.playMediaFile(readable.getAbsolutePath()));
                    case 5 -> { // APK info
                        String lowerExt = fileName.toLowerCase(Locale.ROOT);
                        if (!lowerExt.endsWith(".apk") && !lowerExt.endsWith(".apks") && !lowerExt.endsWith(".xapk")) {
                            Extensions.showMessage(context, R.string.not_an_apk);
                            return;
                        }
                        withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, fileName));
                    }
                }
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private View wrapOpenWithContent(GridView gridView) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        CheckBox fixMimeCb = new CheckBox(context);
        fixMimeCb.setText(R.string.fix_mime_type);
        fixMimeCb.setChecked(settings.getBoolean("fix_mime_type", false));
        fixMimeCb.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("fix_mime_type", isChecked).apply());

        content.addView(fixMimeCb);
        content.addView(gridView);
        return content;
    }

    private void handleFileClick(File file, String fileName) {
        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        if (fileName.endsWith(".txt") || fileName.endsWith(".json")
            || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
            || fileName.endsWith(".gradle") || fileName.endsWith(".properties")) {
            openTextEditorRootAware(file);
        } else if(HashUtil.isChecksumFile(fileName)) {
            withReadableCopy(file, readable -> checksumDialogs.showHashVerifyDialog(readable));
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
                            if (which == 0) withReadableCopy(file, readable -> fileOps.extractArchive(readable));
                            else withReadableCopy(file, readable -> {
                                Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
                                context.startActivity(new Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, context.getContentResolver().getType(uri))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                            });
                        }).create());
            } else if (fileName.endsWith(".apks") || fileName.endsWith(".xapk") || fileName.endsWith(".aspk") || fileName.endsWith(".apkm")) {
                withReadableCopy(file, readable -> showSplitApkMenu(readable, fileName));
            } else {
                withReadableCopy(file, readable -> {
                    Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider",
                            readable);
                    context.startActivity(new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, context.getContentResolver().getType(uri))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                });
            }
        }
    }

    /** Callback for {@link #withReadableCopy(File, ReadableCallback)}. */
    private interface ReadableCallback {
        void onReady(File readable) throws Exception;
    }

    /**
     * Run {@code cb} with a readable file: the original when the app uid can
     * read it, otherwise a root-staged cache copy (binary-safe). Staging runs
     * off the UI thread with a clear error when root is off/unavailable.
     */
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
            Extensions.showMessage(context, "Cannot open: permission denied. Enable elevated file ops for system paths.");
            return;
        }
        Extensions.showMessage(context, "Reading with elevated access…");
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

    /** Text editor intent that remembers the root original for save-back. */
    private Intent rootAwareEditorIntent(File readable, File original) {
        Intent i = new Intent(context, TextEditorActivity.class);
        if (readable != null && original != null
                && !readable.getAbsolutePath().equals(original.getAbsolutePath())) {
            i.putExtra("rootOriginalPath", original.getAbsolutePath());
            Extensions.showMessage(context, "Opened via root — Save writes back as root");
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
                Extensions.showMessage(context, "Opened via root — Save writes back as root");
            }
            context.startActivity(i);
        });
    }

    /** .bak restore that works on root-only paths via su rename. */
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
        if (origExists) {
            //noinspection ResultOfMethodCallIgnored
            origFile.renameTo(new File(origPath + "_tmp_" + ".bak"));
        }
        //noinspection ResultOfMethodCallIgnored
        bakFile.renameTo(new File(origPath));
        if (origExists) {
            //noinspection ResultOfMethodCallIgnored
            origFile.renameTo(new File(bakPath));
        }
    }

    /** Split-APK menu, always operating on a readable (possibly staged) copy. */
    private void showSplitApkMenu(File readable, String displayName) {
        String[] items = new String[] { "Install", "View", "Sign", "AntiSplit/merge to APK" };
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
                                    Extensions.showMessage(context, "You could try merging the APK then installing it");
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
        String[] options = {"ARSC Editor Plus", "ARSC Editor", "Translation mode", "Resource querier"};
        String[] modes = {
                io.github.abdurazaaqmohammed.arsc.ArscEditorActivity.MODE_PLUS,
                io.github.abdurazaaqmohammed.arsc.ArscEditorActivity.MODE_EDITOR,
                io.github.abdurazaaqmohammed.arsc.ArscEditorActivity.MODE_TRANSLATE,
                io.github.abdurazaaqmohammed.arsc.ArscEditorActivity.MODE_QUERIER};
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle("Open with")
                .setSingleChoiceItems(options, -1, (dialog, which) -> {
                    dialog.dismiss();
                    Class<?> target = io.github.abdurazaaqmohammed.arsc.ArscEditorActivity.MODE_EDITOR.equals(modes[which])
                            ? io.github.abdurazaaqmohammed.arsc.ArscSimpleEditorActivity.class
                            : io.github.abdurazaaqmohammed.arsc.ArscEditorActivity.class;
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
            renameInput.setSelection(0, fileName.indexOf(FilenameUtils.getExtension(fileName)) - 1);
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
                                else Extensions.showMessage(context, "Failed to rename " + fileName);
                            }
                        } else {
                            if (file.renameTo(new File(ogFolder, s))) context.loadFolderInPane(ogFolder, pane1);
                            else Extensions.showMessage(context, "Failed to rename " + fileName);
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
                                } else context.handler.post(() -> clearSelection());
                            } else {
                                List<ZipEntryInfo> selected = new ArrayList<>();
                                for (int i : selectedPositions) selected.add((ZipEntryInfo) values[i]);
                                fileOps.deleteZipEntry(selected.toArray(new ZipEntryInfo[0]));
                                if (sign[0]) wrapper[0].signApk(zipFile);
                                context.handler.post(() -> clearSelection());
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
                            context.handler.post(() -> clearSelection());
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
                            .setPositiveButton("Delete Anyway", (d, w) -> {
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
            pm.show();
            new Thread(() -> {
                String name = ((TextInputEditText) compressView.findViewById(R.id.filename_compress_edittext)).getText().toString().trim();
                if (name.isEmpty()) name = multi ? parentFileName : FilenameUtils.removeExtension(fileName);
                String format = ((AutoCompleteTextView) compressView.findViewById(R.id.compress_format)).getText().toString().trim();
                if (!format.startsWith(".")) format = "." + format;
                if (!name.toLowerCase(Locale.ENGLISH).endsWith(format)) name += format;
                File outputZip = new File(parentFile2, name);

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
                        context.reloadCurrentFolder();
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
                        context.reloadCurrentFolder();
                    } catch (Exception e) {
                        pm.dismiss();
                        new ErrorUtil(context).showError(e);
                    } finally {
                        if (finalStageTmp != null) Util.deleteDir(finalStageTmp);
                    }
                }
            }).start();
        });
        pm.setText(context.rss.getString(R.string.compressing));
        context.handler.post(compressDialog::show);
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
        String refExt = org.apache.commons.io.FilenameUtils.getExtension(refName).toLowerCase(Locale.ROOT);

        isMultiSelectMode = true;
        selectedPositions.clear();
        for (int i = (isInZip ? 0 : 1); i < values.length; i++) {
            Object o = values[i];
            boolean isFolder = isInZip ? ((ZipEntryInfo) o).isDirectory() : ((File) o).isDirectory();
            if (refIsFolder) {
                if (isFolder) selectedPositions.add(i);
            } else if (!isFolder) {
                String n = o instanceof File ? ((File) o).getName() : ((ZipEntryInfo) o).getName();
                if (org.apache.commons.io.FilenameUtils.getExtension(n).toLowerCase(Locale.ROOT).equals(refExt))
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
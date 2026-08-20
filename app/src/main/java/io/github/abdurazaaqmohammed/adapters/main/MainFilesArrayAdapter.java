package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.TextUtils;
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
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareArscDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareZipDialog;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RenameUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
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
                    context.lastPaneSelected = pane1 ? 1 : 2;
                    handleMultiSelect(finalPosition);
                } : !isInZip && file.isFile() ?
                    v -> {
                        context.lastPaneSelected = pane1 ? 1 : 2;
                        context.setCurrentFolder(file.getParentFile(), getOldValues());
                        handleFileClick(file, fileName);
                    } : (View.OnClickListener) v -> {
                    context.lastPaneSelected = pane1 ? 1 : 2;
                    if (isInZip)
                        fileOps.handleZipEntryClick(entry);
                    else
                        context.loadFolderInPane(file, pane1);
                };
            }

            int finalPosition1 = finalPosition;
            View.OnLongClickListener originalLongClickListener = v -> {
                context.lastPaneSelected = pane1 ? 1 : 2;
                if (isInZip) {
                    context.setCurrentFolder(currentZipPath, Arrays.asList(values));
                } else
                    context.setCurrentFolder(file.getParentFile(), getOldValues());

                boolean multi = !selectedPositions.isEmpty();
                String direction = pane1 ? "->" : "<-";
                String[] baseItems = new String[] { "Copy " + direction, "Move " + direction, "Rename", "Delete", "Compress", "Properties", "Share", "Open with", "Bookmark", "Command Helper", context.getString(R.string.checksums) };
                List<String> itemsList = new ArrayList<>(Arrays.asList(baseItems));

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
                        itemsList.add(context.getString(R.string.batch_sign));
                        itemsList.add(context.getString(R.string.batch_optimize));
                        itemsList.add(context.getString(R.string.batch_install));
                    }
                }

                if (!multi && !isInZip && !file.isDirectory() && ArchiveUtil.isSupportedArchive(fileName)) {
                    itemsList.add(context.getString(R.string.extract));
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

                        if (isZip1 && isZip2) itemsList.add("Compare ZIP");
                        if (isArsc1 && isArsc2) itemsList.add("Compare ARSC");
                        if (!isZip1 && !isZip2 && !ext1.equals("arsc") && !ext2.equals("arsc")) {
                            itemsList.add("Compare Text");
                            if (compareFile1 instanceof File && compareFile2 instanceof File
                                    && !((File) compareFile1).isDirectory() && !((File) compareFile2).isDirectory())
                                itemsList.add(context.getString(R.string.compare_hashes));
                        }
                        if (ext1.equals("apk") && ext2.equals("apk")
                                && compareFile1 instanceof File && compareFile2 instanceof File)
                            itemsList.add(context.getString(R.string.compare_apks));
                    }
                }

                String[] items = itemsList.toArray(new String[0]);

                final Object finalCompareFile1 = compareFile1;
                final Object finalCompareFile2 = compareFile2;

                GridView gridView = new GridView(context);
                gridView.setNumColumns(2);
                gridView.setBackgroundColor(Color.TRANSPARENT);
                gridView.setPadding(16, 16, 16, 16);
                gridView.setAdapter(new DialogAdapter(context, items));
                gridView.setVerticalSpacing(40);
                AlertDialog dialog = dialogUtil.getDialogBuilder()
                        .setTitle(fileName)
                        .setView(gridView)
                        .create();

                gridView.setOnItemClickListener((parent1, view, position1, id) -> {
                    dialog.dismiss();
                    ProgressManager pm;
                    try {
                        String selectedAction = items[position1];
                        switch (selectedAction) {
                            case "Compare Text":
                                context.startActivity(new Intent(context, CompareTextActivity.class)
                                        .putExtra("file1", finalCompareFile1 instanceof File ? ((File) finalCompareFile1).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile1).getFullPath())
                                        .putExtra("file2", finalCompareFile2 instanceof File ? ((File) finalCompareFile2).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile2).getFullPath())
                                        .putExtra("isZip1", finalCompareFile1 instanceof ZipEntryInfo)
                                        .putExtra("isZip2", finalCompareFile2 instanceof ZipEntryInfo)
                                        .putExtra("zip1", finalCompareFile1 instanceof ZipEntryInfo ? ((ZipEntryInfo) finalCompareFile1).getZipFile().getAbsolutePath() : null)
                                        .putExtra("zip2", finalCompareFile2 instanceof ZipEntryInfo ? ((ZipEntryInfo) finalCompareFile2).getZipFile().getAbsolutePath() : null)
                                );
                                return;
                            case "Compare ZIP":
                                new CompareZipDialog(context,
                                        finalCompareFile1 instanceof File ? (File) finalCompareFile1 : ((ZipEntryInfo) finalCompareFile1).getZipFile(),
                                        finalCompareFile2 instanceof File ? (File) finalCompareFile2 : ((ZipEntryInfo) finalCompareFile2).getZipFile()
                                ).show();
                                return;
                            case "Compare ARSC":
                                new CompareArscDialog(context,
                                        finalCompareFile1 instanceof File ? ((File) finalCompareFile1).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile1).getZipFile().getAbsolutePath(),
                                        finalCompareFile2 instanceof File ? ((File) finalCompareFile2).getAbsolutePath() : ((ZipEntryInfo) finalCompareFile2).getZipFile().getAbsolutePath()
                                ).show();
                                return;
                            case "Compare hashes":
                                checksumDialogs.showCompareHashesDialog((File) finalCompareFile1, (File) finalCompareFile2);
                                return;
                            case "Checksums":
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
                            case "Compare APKs":
                                apkTools.showCompareApksDialog((File) finalCompareFile1, (File) finalCompareFile2);
                                return;
                            case "Batch sign APKs": {
                                List<File> apks = new ArrayList<>();
                                for (int bp : selectedPositions) apks.add((File) values[bp]);
                                apkTools.batchSignApks(apks);
                                return;
                            }
                            case "Batch optimize APKs": {
                                List<File> apks = new ArrayList<>();
                                for (int bp : selectedPositions) apks.add((File) values[bp]);
                                apkTools.batchOptimizeApks(apks);
                                return;
                            }
                            case "Install APKs": {
                                for (int bp : selectedPositions) InstallUtil.installApkWithDialog(context, (File) values[bp]);
                                return;
                            }
                            case "Command Helper":
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
                            case "Extract":
                                if (isInZip || multi) return;
                                fileOps.extractArchive(file);
                                return;
                            default:
                                switch (position1) {
                                    case 0:
                                        pm = new ProgressManager(context, true).show();
                                        new Thread(() -> {
                                            try {
                                                if (multi) {
                                                    List<Object> itemsToCopy = new ArrayList<>();
                                                    for (int f : selectedPositions) {
                                                        Object file1 = values[f];
                                                        pm.setText(context.rss.getString(R.string.copying, file1));
                                                        itemsToCopy.add(file1);
                                                    }
                                                    fileOps.copyMultiple(itemsToCopy);
                                                } else {
                                                    pm.setText(context.rss.getString(R.string.copying, item));
                                                    fileOps.copy(item);
                                                }
                                                pm.dismiss();
                                            } catch (Exception e) {
                                                pm.dismiss();
                                                new ErrorUtil(context).showError(e);
                                            }
                                        }).start();
                                        break;
                                    case 1:
                                        if (context.pane1Folder == context.pane2Folder) {
                                            break;
                                        }
                                        pm = new ProgressManager(context, true).show();
                                        new Thread(() -> {
                                            try {
                                                pm.setText(context.rss.getString(R.string.copying, item));
                                                fileOps.move(item);
                                                pm.dismiss();
                                            } catch (Exception e) {
                                                pm.dismiss();
                                                new ErrorUtil(context).showError(e);
                                            }
                                        }).start();
                                        break;
                                    case 2:
                                        showRenameDialog(finalPosition1, file, entry, fileName, multi);
                                        break;
                                    case 3:
                                        showDeleteDialog(finalPosition1, file, entry, multi);
                                        break;
                                    case 4:
                                        showCompressDialog(file, fileName, multi);
                                        break;
                                    case 5:
                                        propertiesDialog.show(multi, values, selectedPositions, isInZip, file, entry, fileName, getFilesToDisplay(multi, finalPosition1).toString());
                                        break;
                                    case 6:
                                        Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(context.getContentResolver().getType(uri)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share " + fileName));
                                        break;
                                    case 7:
                                        Uri u = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW).setDataAndType(u, context.getContentResolver().getType(u)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open " + fileName));
                                        break;
                                    case 8:
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

    private void handleFileClick(File file, String fileName) {
        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        if (fileName.endsWith(".txt") || fileName.endsWith(".json")
            || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
            || fileName.endsWith(".gradle") || fileName.endsWith(".properties")) {
            context.startActivity(new Intent(context, TextEditorActivity.class).putExtra("path", file.getPath()));
        } else if(HashUtil.isChecksumFile(fileName)) {
            checksumDialogs.showHashVerifyDialog(file);
        } else if(fileName.endsWith(".xml")) {
            try (InputStream is = FileUtils.getInputStream(file)) {
                if (FileUtils.isAxml(is)) try (InputStream is2 = FileUtils.getInputStream(file)) {
                    context.startActivity(new Intent(context, TextEditorActivity.class)
                            .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2).decodeAsString().trim())
                            .putExtra("axml", true)
                            .putExtra("path", file.getPath()));
                }
                else context.startActivity(new Intent(context, TextEditorActivity.class).putExtra("path", file.getPath()));
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            context.openImageViewer(file.getPath());
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            context.playMediaFile(file.getPath());
        } else if ((ext.equals(".apk"))) {
            apkTools.showApkInfoDialog(file, fileName);
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
                    File orig = new File(origPath);
                    boolean origExists = orig.exists();
                    if(origExists) {
                        orig.renameTo(new File(origPath + "_tmp_" + bak));
                    }
                    file.renameTo(new File(origPath));
                    if(origExists) orig.renameTo(new File(bakPath));
                })
                .setNegativeButton(android.R.string.cancel, null).show();
            } else if (fileName.endsWith(".zip")) {
                fileOps.openZipFile(file, null);
            } else if (ArchiveUtil.isSupportedArchive(fileName)) {
                dialogUtil.styleAlertDialog(
                        dialogUtil.getDialogBuilder().setSingleChoiceItems(new CharSequence[] { context.rss.getString(R.string.extract), context.rss.getString(R.string.open_with) }, -1, (dialog, which) -> {
                            dialog.dismiss();
                            if (which == 0) fileOps.extractArchive(file);
                            else {
                                Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
                                context.startActivity(new Intent(Intent.ACTION_VIEW)
                                        .setDataAndType(uri, context.getContentResolver().getType(uri))
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
                            }
                        }).create());
            } else if (fileName.endsWith(".apks") || fileName.endsWith(".xapk") || fileName.endsWith(".aspk") || fileName.endsWith(".apkm")) {
                String[] items = new String[] { "Install", "View", "Sign", "AntiSplit/merge to APK" };
                dialogUtil.styleAlertDialog(
                        dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog, which) -> {
                            dialog.dismiss();
                            try {
                                switch (which) {
                                    case 0:
                                        if (LegacyUtils.aboveSdk20) {
                                            new Thread(() -> {
                                                try (ZipFile zf = new ZipFile(file)) {
                                                    List<File> apkFiles = new ArrayList<>();
                                                    File tmpDir = new File(context.getCacheDir(), "split_install_" + System.currentTimeMillis());
                                                    tmpDir.mkdirs();
                                                    for (FileHeader fh : zf.getFileHeaders()) {
                                                        if (fh.getFileName().endsWith(".apk")) {
                                                            File tmpApk = new File(tmpDir, fh.getFileName());
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
                                                        InstallUtil.installSplitApksWithDialog(context, apkFiles, file.getName());
                                                    } else {
                                                        Extensions.showMessage(context, R.string.no_apk_files_found);
                                                    }
                                                } catch (Exception e) {
                                                    context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
                                                }
                                            }).start();
                                        } else {
                                            Extensions.showMessage(context, "Installing split APKs is not supported on this version of Android :(");

                                            // We should check if apk minsdk <20 here
                                            Extensions.showMessage(context, "You could try merging the APK then installing it");
                                        }
                                        break;
                                    case 1:
                                        fileOps.openZipFile(file, null);
                                        break;
                                    case 2:
                                        SignatureKeyDialog.show(context, file, true);
                                        break;
                                    case 3:
                                        MergeUtil.mergeSplitApk(file, context);
                                        break;
                                }
                            } catch (Exception e) {
                                new ErrorUtil(context).showError(e);
                            }
                        }).create());
            } else {
                Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider",
                        file);
                context.startActivity(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, context.getContentResolver().getType(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            }
        }
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
                        io.github.abdurazaaqmohammed.utils.RootManager rm = io.github.abdurazaaqmohammed.utils.RootManager.getInstance(context);
                        if (rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
                            try {
                                rm.rename(file.getAbsolutePath(), new File(ogFolder, s).getAbsolutePath());
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
        SharedPreferences settings = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
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
                        RootManager rm = RootManager.getInstance(context);
                        boolean useRootForDelete = rm.isRootFileOpsEnabled() && rm.isRootAvailable();
                        if (multi) {
                            if (!isInZip) {
                                File selectedFile = null;
                                for (int i : selectedPositions) {
                                    selectedFile = (File) values[i];
                                    File finalSelectedFile1 = selectedFile;
                                    if (finalSelectedFile1 != null)
                                        pm.setText(context.rss.getString(R.string.deleting, finalSelectedFile1.getName()));

                                    if (useRootForDelete) {
                                        try {
                                            rm.delete(selectedFile.getAbsolutePath());
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
                                    context.handler.post(() -> context.loadFolderInPane(finalSelectedFile.getParentFile(), pane1));
                                }
                            } else {
                                List<ZipEntryInfo> selected = new ArrayList<>();
                                for (int i : selectedPositions) selected.add((ZipEntryInfo) values[i]);
                                fileOps.deleteZipEntry(selected.toArray(new ZipEntryInfo[0]));
                                if (sign[0]) wrapper[0].signApk(zipFile);
                            }
                        } else if (!isInZip) {
                            int total = (int) Util.countInsideFolder(file).total();
                            pm.setProgress(0, total);
                            pm.setText(context.rss.getString(R.string.deleting, file.getName()));

                            if (useRootForDelete) {
                                try {
                                    rm.delete(file.getAbsolutePath());
                                } catch (Exception e) {
                                    if (file.isDirectory()) Util.deleteDir(file, pm, total);
                                    else file.delete();
                                }
                            } else {
                                if (file.isDirectory()) Util.deleteDir(file, pm, total);
                                else file.delete();
                            }
                            context.handler.post(() -> context.loadFolderInPane(file.getParentFile(), pane1));
                        } else {
                            fileOps.deleteZipEntry(entry);
                            if (sign[0]) wrapper[0].signApk(zipFile);
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
        filenameEditText.setText(multi ? parentFileName + ".zip" : fileName.replace(FilenameUtils.getExtension(fileName), "zip"));
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
                if (name.isEmpty()) name = multi ? parentFileName : fileName;
                String format = ((AutoCompleteTextView) compressView.findViewById(R.id.compress_format)).getText().toString().trim();
                if (!format.startsWith(".")) format = "." + format;
                if (!name.toLowerCase(Locale.ENGLISH).endsWith(format)) name += format;
                File outputZip = new File(parentFile2, name);

                List<File> sources = new ArrayList<>();
                if (multi) {
                    for (int i : selectedPositions) sources.add((File) values[i]);
                } else sources.add(file);

                if (format.equals(".zip")) {
                    ZipParameters zipParameters = new ZipParameters();
                    CompressionLevel compressionLevel = CompressionLevel.valueOf(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
                    zipParameters.setCompressionLevel(compressionLevel);
                    if (compressionLevel == CompressionLevel.NO_COMPRESSION)
                        zipParameters.setCompressionMethod(CompressionMethod.STORE);
                    CharSequence pw = ((TextView) compressView.findViewById(R.id.pw_edittext)).getText();

                    try (ZipFile zf = new ZipFile(outputZip)) {
                        if (!TextUtils.isEmpty(pw)) {
                            zipParameters.setEncryptFiles(true);
                            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                            zf.setPassword(pw.toString().toCharArray());
                        }
                        for (File source : sources) {
                            if (source.isDirectory())
                                zf.addFolder(source, zipParameters);
                            else zf.addFile(source, zipParameters);
                        }
                        pm.dismiss();
                    } catch (Exception e) {
                        pm.dismiss();
                        new ErrorUtil(context).showError(e);
                    }
                } else {
                    try {
                        ArchiveUtil.create(outputZip, sources);
                        pm.dismiss();
                    } catch (Exception e) {
                        pm.dismiss();
                        new ErrorUtil(context).showError(e);
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
        }
        notifyDataSetChanged();
    }

    public void handleMultiSelect(int position) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position);
            if (selectedPositions.isEmpty()) {
                isMultiSelectMode = false;
                rangeStartPosition = null;
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
        notifyDataSetChanged();
    }

    public void selectAll() {
        isMultiSelectMode = true;
        for (int i = (isInZip ? 0 : 1); i < values.length; i++) selectedPositions.add(i);
        notifyDataSetChanged();
    }
}

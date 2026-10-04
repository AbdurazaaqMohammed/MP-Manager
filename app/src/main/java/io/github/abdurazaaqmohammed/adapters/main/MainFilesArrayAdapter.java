package io.github.abdurazaaqmohammed.adapters.main;

import android.content.ClipData;
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
import io.github.abdurazaaqmohammed.plugins.ext.ExtensionRegistry;
import io.github.abdurazaaqmohammed.plugins.ext.FileMenuAction;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginHost;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginTrust;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
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
import io.github.abdurazaaqmohammed.utils.FileSplitMerge;
import io.github.abdurazaaqmohammed.features.files.EntryDialogs;
import io.github.abdurazaaqmohammed.features.files.FileOpener;
import io.github.abdurazaaqmohammed.features.media.BatchImageTools;
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
    private final BatchImageTools batchImages;
    private final FileOpener fileOpener;
    private final EntryDialogs entryDialogs;

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
        batchImages = new BatchImageTools(context, dialogUtil,
                new BatchImageTools.Selection() {
                    @Override
                    public List<File> selectedImages() {
                        List<File> out = new ArrayList<>();
                        for (int p : selectedPositions) {
                            Object o = MainFilesArrayAdapter.this.values[p];
                            if (o instanceof File f) {
                                if (f.isFile() && FileUtils.isImageFile(f.getName())) out.add(f);
                            }
                        }
                        return out;
                    }

                    @Override
                    public List<File> selectedJpegs() {
                        List<File> out = new ArrayList<>();
                        for (File f : selectedImages()) {
                            if (BatchImageTools.isJpegPath(f.getName())) out.add(f);
                        }
                        return out;
                    }
                },
                doneText -> {
                    clearSelection();
                    context.loadFolderInPane(pane1 ? context.pane1Folder : context.pane2Folder, pane1);
                    Extensions.showMessage(context, doneText);
                });
        fileOpener = new FileOpener(context, dialogUtil, pane1, apkTools, checksumDialogs, fileOps);
        entryDialogs = new EntryDialogs(context, dialogUtil, pane1, fileOps,
                new EntryDialogs.State() {
                    @Override
                    public Object[] values() {
                        // Must be the field, not the constructor parameter of the
                        // same name: the field carries the up-dir entry at index 0
                        // and the parameter does not, so reading the parameter
                        // shifted every dialog one row down the listing.
                        return MainFilesArrayAdapter.this.values;
                    }

                    @Override
                    public Set<Integer> selectedPositions() {
                        return selectedPositions;
                    }

                    @Override
                    public boolean isInZip() {
                        return isInZip;
                    }

                    @Override
                    public String currentZipPath() {
                        return currentZipPath;
                    }

                    @Override
                    public void clearSelection() {
                        MainFilesArrayAdapter.this.clearSelection();
                    }
                });
    }

    public void openWithForFile(File file, String fileName) {
        fileOpener.openWithForFile(file, fileName);
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

    /**
     * Runs an external (out-of-process) file action after consent: stages the
     * selection as content URIs with grants and forwards the plugin result
     * message to the user.
     */
    private void runExternalFileAction(ExternalActions.Entry entry,
                                       List<File> files, List<Uri> uris) {
        try {
            if (entry == null || files == null || files.isEmpty()
                    || uris == null || uris.size() != files.size()) return;
            Intent intent = PluginHost.explicitIntent(entry.plugin,
                    PluginContracts.ACTION_FILE_MENU);
            intent.putExtra(PluginContracts.EXTRA_PLUGIN_ID, entry.plugin.pluginId);
            ArrayList<String> names = new ArrayList<>();
            for (File f : files) names.add(f.getName());
            intent.putStringArrayListExtra(PluginContracts.EXTRA_FILE_NAMES, names);
            intent.setDataAndType(uris.get(0),
                    context.getContentResolver().getType(uris.get(0)));
            if (uris.size() > 1) {
                ClipData clip = ClipData.newRawUri("files", uris.get(0));
                for (int i = 1; i < uris.size(); i++) {
                    clip.addItem(new ClipData.Item(uris.get(i)));
                }
                intent.setClipData(clip);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            for (Uri uri : uris) {
                try {
                    context.grantUriPermission(entry.plugin.packageName, uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
            }
            PluginTrust.ensureTrusted(context, entry.plugin, () ->
                    context.launchExternalFile(intent, result -> {
                        try {
                            Intent data = result.getData();
                            String msg = data == null ? null : data.getStringExtra(
                                    PluginContracts.EXTRA_MESSAGE);
                            if (msg == null || msg.isEmpty()) {
                                msg = result.getResultCode() == android.app.Activity.RESULT_OK
                                        ? "Done" : "Cancelled";
                            }
                            io.github.codehasan.colorpicker.extensions.Extensions.showMessage(context, msg);
                        } catch (Exception ignored) {
                        }
                    }));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final View convertView = holder.itemView;
        // Drop any listener left over from a previous bind *before* anything
        // else. The real listener is attached from a posted runnable, and if this
        // row were rebound first, the stale one survived and the row answered
        // taps and long-presses for its old index -- which showed up as the
        // highlight and the delete target sitting one row off.
        convertView.setOnTouchListener(null);
        convertView.setTranslationX(0f);
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
                        fileOpener.handleFileClick(file, fileName);
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

                // Diagnostic: one line per long-press, so a row that acts on its
                // neighbour reveals whether the bind index, the live index or the
                // data at those indices is the one that moved.
                // The menu acts on the selection, so a stale leftover selection
                // would make a long-press on this row name -- and delete -- some
                // other file. Drop selections the last listing invalidated, and
                // when the pressed row is not part of the selection, make that row
                // the selection.
                selectedPositions.removeIf(p -> p < 0 || p >= values.length);
                if (!selectedPositions.contains(finalPosition)) {
                    selectedPositions.clear();
                    selectedPositions.add(finalPosition);
                    notifyDataSetChanged();
                }
                boolean multi = selectedPositions.size() > 1;
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

                // Tool entry: opens the per-file tool list (GPG encrypt/decrypt
                // and friends). Real files only; a zip entry has no file to work
                // on.
                if (!multi && !isInZip && file != null) {
                    visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.TOOLS, FileMenuOrder.labelFor(context, FileMenuOrder.TOOLS, direction)));
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

                // Third-party file actions: real files only, visibility decided per selection.
                final List<File> pluginFiles = new ArrayList<>();
                if (!isInZip) {
                    if (multi) {
                        for (int fp : selectedPositions) {
                            Object o = values[fp];
                            if (o instanceof File) pluginFiles.add((File) o);
                        }
                    } else if (file != null) {
                        pluginFiles.add(file);
                    }
                    if (!pluginFiles.isEmpty()) {
                        for (FileMenuAction action : ExtensionRegistry.fileMenuActions()) {
                            if (action == null || action.id() == null) continue;
                            boolean show = false;
                            try {
                                show = action.visibleFor(pluginFiles);
                            } catch (Exception ignored) {
                            }
                            if (show) {
                                String label = action.label() == null || action.label().isEmpty()
                                        ? action.id() : action.label();
                                visibleMenu.add(new FileMenuOrder.MenuItem(action.id(), label));
                            }
                        }
                    }
                }

                // External (out-of-process) file actions, filtered by manifest
                // mime/pattern. Listed only when every selected file can be
                // staged as a content URI (falls outside provider roots otherwise).
                final List<ExternalActions.Entry> externalFileEntries = new ArrayList<>();
                final List<Uri> externalFileUris;
                if (!isInZip && !pluginFiles.isEmpty()) {
                    List<Uri> staged = ExternalActions.stageUris(context, pluginFiles);
                    if (staged != null && staged.size() == pluginFiles.size()) {
                        for (ExternalActions.Entry e : ExternalActions.fileEntries(context, pluginFiles)) {
                            if (e == null || e.id == null) continue;
                            externalFileEntries.add(e);
                            String label = e.title == null || e.title.isEmpty() ? e.id : e.title;
                            visibleMenu.add(new FileMenuOrder.MenuItem(e.id, label));
                        }
                    }
                    externalFileUris = staged;
                } else {
                    externalFileUris = null;
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
                        // The menu is shown asynchronously, so by the time an item
                        // is tapped the listing may have been replaced. Re-resolve
                        // the row we acted on and drop selections that no longer
                        // point at a live entry: using the bind-time index would
                        // either read past the end or, worse, hit a neighbouring
                        // file and delete it.
                        // If the pane was re-listed while this menu was open, the
                        // menu is holding a detached adapter whose indices no longer
                        // mean what they did: the same index named gpg.txt.gpg when
                        // the row was pressed and a different file by the time the
                        // item was tapped. Refuse rather than act on a listing the
                        // user is no longer looking at.
                        RecyclerView paneView = (RecyclerView) context.findViewById(
                                pane1 ? R.id.listViewPane1 : R.id.listViewPane2);
                        if (paneView.getAdapter() != MainFilesArrayAdapter.this) {
                            Extensions.showMessage(context, R.string.list_changed_try_again);
                            return;
                        }
                        final int livePosition = indexOf(boundItem);
                        selectedPositions.removeIf(p -> p < 0 || p >= values.length);
                        if (livePosition < 0) {
                            Extensions.showMessage(context, R.string.list_changed_try_again);
                            return;
                        }
                        String actionId = itemIds[position1];
                        FileMenuAction pluginAction = ExtensionRegistry.findFileMenu(actionId);
                        if (pluginAction != null) {
                            pluginAction.run(context, pluginFiles);
                            return;
                        }
                        ExternalActions.Entry externalFile =
                                ExternalActions.findById(externalFileEntries, actionId);
                        if (externalFile != null) {
                            runExternalFileAction(externalFile, pluginFiles, externalFileUris);
                            return;
                        }
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
                                batchImages.batchCrop();
                                return;
                            }
                            case FileMenuOrder.BATCH_EXIF: {
                                batchImages.batchExif();
                                return;
                            }
                            case FileMenuOrder.BATCH_STRIP_META: {
                                batchImages.batchStrip();
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
                            case FileMenuOrder.TOOLS:
                                showFileTools(livePosition, file);
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
                                        entryDialogs.showRenameDialog(livePosition, file, entry, fileName, multi);
                                        break;
                                    case FileMenuOrder.DELETE:
                                        entryDialogs.showDeleteDialog(livePosition, file, entry, multi);
                                        break;
                                    case FileMenuOrder.COMPRESS:
                                        entryDialogs.showCompressDialog(file, fileName, multi);
                                        break;
                                    case FileMenuOrder.PROPERTIES:
                                        propertiesDialog.show(multi, values, selectedPositions, isInZip, file, entry, fileName, entryDialogs.getFilesToDisplay(multi, livePosition).toString());
                                        break;
                                    case FileMenuOrder.SHARE:
                                        if (isInZip) {
                                            fileOpener.shareZipEntry(item, fileName);
                                        } else fileOpener.withReadableCopy(file, readable -> {
                                            Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
                                            String shareMime = MimeUtil.getMimeTypeForAction(context, readable);
                                            context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(shareMime != null ? shareMime : "application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share " + fileName));
                                        });
                                        break;
                                    case FileMenuOrder.OPEN_WITH:
                                        if (isInZip) {
                                            fileOpener.openWithZipEntry(item, fileName);
                                        } else fileOpener.showOpenWithDialog(file, fileName);
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
                        pane1 ? 1 : 2,
                        bindHolder::getBindingAdapterPosition));
            });
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
            // Skip indices the last listing change invalidated.
            if (position == null || position < 0 || position >= values.length) continue;
            selectedFiles.add(values[position]);
        }
        return selectedFiles;
    }

    /**
     * Current index of {@code item} in the shown list (which carries the up-dir
     * entry at 0), or -1 when it is no longer listed.
     */
    private int indexOf(Object item) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == item) return i;
        }
        return -1;
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

    /**
     * Asks how to encrypt, then runs the chosen scheme. The result lands in
     * {@code src.getName() + ".gpg"} next to the source, in OpenPGP format.
     */
    private void gpgEncrypt(File src) {
        String[] modes = {
                context.getString(R.string.gpg_encrypt_password),
                context.getString(R.string.gpg_encrypt_pubkey)};
        new MaterialAlertDialogBuilder(context)
                .setTitle(src.getName())
                .setItems(modes, (d, which) -> {
                    if (which == 0) {
                        promptGpgPassword(src.getName(),
                                password -> runGpgEncrypt(src, password.toCharArray()));
                    } else {
                        promptGpgKeyFile(src.getName(),
                                keyPath -> runGpgEncryptWithKey(src, keyPath));
                    }
                })
                .show();
    }

    private void runGpgEncrypt(File src, char[] password) {
        File target = new File(src.getParentFile(), src.getName() + ".gpg");
        new Thread(() -> {
            File tmp = new File(src.getParentFile(), "." + src.getName() + ".gpg.tmp");
            try {
                try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(src));
                     java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp))) {
                    io.github.abdurazaaqmohammed.utils.GpgCrypto.encrypt(in, out, password);
                }
                finishGpgWrite(tmp, target);
            } catch (Exception e) {
                tmp.delete();
                context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    private void runGpgEncryptWithKey(File src, String keyPath) {
        File target = new File(src.getParentFile(), src.getName() + ".gpg");
        new Thread(() -> {
            File tmp = new File(src.getParentFile(), "." + src.getName() + ".gpg.tmp");
            try {
                org.bouncycastle.openpgp.PGPPublicKey key;
                try (java.io.InputStream keyIn = new java.io.BufferedInputStream(new java.io.FileInputStream(keyPath))) {
                    key = io.github.abdurazaaqmohammed.utils.GpgCrypto.loadEncryptionKey(keyIn);
                }
                try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(src));
                     java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp))) {
                    io.github.abdurazaaqmohammed.utils.GpgCrypto.encryptForKey(in, out, key);
                }
                finishGpgWrite(tmp, target);
            } catch (Exception e) {
                tmp.delete();
                context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    /**
     * Asks for whatever the file actually needs: a password for symmetric
     * files, or a secret key plus its passphrase for public-key ones.
     */
    private void gpgDecrypt(File src) {
        boolean publicKey;
        try (java.io.InputStream probe = new java.io.BufferedInputStream(new java.io.FileInputStream(src))) {
            publicKey = io.github.abdurazaaqmohammed.utils.GpgCrypto.isPublicKeyEncrypted(probe);
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        if (publicKey) {
            promptGpgKeyAndPassphrase(src.getName(),
                    (keyPath, passphrase) -> runGpgDecryptWithKey(src, keyPath, passphrase.toCharArray()));
        } else {
            promptGpgPassword(src.getName(),
                    password -> runGpgDecrypt(src, password.toCharArray()));
        }
    }

    private void runGpgDecrypt(File src, char[] password) {
        File target = decryptedTarget(src);
        new Thread(() -> {
            File tmp = new File(src.getParentFile(), "." + src.getName() + ".dec.tmp");
            try {
                try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(src));
                     java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp))) {
                    io.github.abdurazaaqmohammed.utils.GpgCrypto.decrypt(in, out, password);
                }
                finishGpgWrite(tmp, target);
            } catch (Exception e) {
                tmp.delete();
                context.runOnUiThread(() -> Extensions.showMessage(context,
                        context.getString(R.string.wrong_password_or_corrupt)));
            }
        }).start();
    }

    private void runGpgDecryptWithKey(File src, String keyPath, char[] passphrase) {
        File target = decryptedTarget(src);
        new Thread(() -> {
            File tmp = new File(src.getParentFile(), "." + src.getName() + ".dec.tmp");
            try {
                try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(src));
                     java.io.InputStream keyIn = new java.io.BufferedInputStream(new java.io.FileInputStream(keyPath));
                     java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(tmp))) {
                    io.github.abdurazaaqmohammed.utils.GpgCrypto.decryptWithKey(in, out, keyIn, passphrase);
                }
                finishGpgWrite(tmp, target);
            } catch (Exception e) {
                tmp.delete();
                context.runOnUiThread(() -> Extensions.showMessage(context,
                        context.getString(R.string.gpg_wrong_key)));
            }
        }).start();
    }

    /**
     * Decrypted name: strip the .gpg/.asc/.pgp suffix; fall back to ".out" so
     * an odd name never maps onto the source itself.
     */
    private File decryptedTarget(File src) {
        String base = src.getName();
        String lower = base.toLowerCase(Locale.ENGLISH);
        for (String suffix : new String[]{".gpg", ".asc", ".pgp"}) {
            if (lower.endsWith(suffix) && base.length() > suffix.length()) {
                return new File(src.getParentFile(), base.substring(0, base.length() - suffix.length()));
            }
        }
        return new File(src.getParentFile(), base + ".out");
    }

    /**
     * Moves a finished crypto result into place. The write went to a temporary
     * file first, so a wrong password or a bad key never truncates an existing
     * plaintext file; and if the target already exists the result is dropped
     * instead of silently overwriting it.
     */
    private void finishGpgWrite(File tmp, File target) {
        if (target.exists()) {
            tmp.delete();
            context.runOnUiThread(() -> Extensions.showMessage(context,
                    context.getString(R.string.gpg_target_exists) + ": " + target.getName()));
            return;
        }
        boolean ok = tmp.renameTo(target);
        context.runOnUiThread(() -> {
            if (ok) {
                context.loadFolderInPane(pane1 ? context.pane1Folder : context.pane2Folder, pane1);
                Extensions.showMessage(context, target.getName());
            } else {
                tmp.delete();
                Extensions.showMessage(context, R.string.wrong_password_or_corrupt);
            }
        });
    }

    /**
     * The per-file tool list reached from the long-press menu.
     *
     * <p>Tools live behind one entry rather than each getting a line of their
     * own: the menu is already long, and these act on the file that was pressed
     * rather than on the folder the user is browsing.
     */
    private void showFileTools(int position, File file) {
        String[] tools = {
                context.getString(R.string.encrypt_gpg),
                context.getString(R.string.decrypt_gpg),
                context.getString(R.string.file_split),
                context.getString(R.string.file_merge)};
        new MaterialAlertDialogBuilder(context)
                .setTitle(file.getName())
                .setItems(tools, (d, which) -> {
                    switch (which) {
                        case 0: gpgEncrypt(file); break;
                        case 1: gpgDecrypt(file); break;
                        case 2: promptSplit(file); break;
                        default: mergeParts(file); break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Asks for a chunk size, then splits the file beside itself. */
    private void promptSplit(File src) {
        long[] presets = FileSplitMerge.chunkSizePresets();
        String[] labels = new String[presets.length];
        for (int i = 0; i < presets.length; i++) {
            labels[i] = FileSplitMerge.describeSize(presets[i]);
        }
        new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.file_split_title, src.getName()))
                .setItems(labels, (d, which) -> runSplit(src, presets[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runSplit(File src, long chunkSize) {
        final ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            String error = null;
            int count = 0;
            try {
                List<File> parts = FileSplitMerge.split(src, src.getParentFile(), chunkSize,
                        (done, total) -> {
                            pm.setProgress((int) (total == 0 ? 0 : done * 100 / total));
                            return true;
                        });
                count = parts.size();
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final int n = count;
            final String err = error;
            pm.dismiss();
            context.runOnUiThread(() -> {
                if (err != null) {
                    new ErrorUtil(context).showError(new IOException(err));
                } else {
                    context.loadFolderInPane(pane1 ? context.pane1Folder : context.pane2Folder, pane1);
                    Extensions.showMessage(context, context.getString(R.string.file_split_done, n));
                }
            });
        }).start();
    }

    /** Concatenates every sibling part belonging to the pressed part. */
    private void mergeParts(File anyPart) {
        List<File> parts = FileSplitMerge.findParts(anyPart);
        if (parts.isEmpty()) {
            Extensions.showMessage(context, R.string.file_merge_none);
            return;
        }
        File out = new File(anyPart.getParentFile(),
                FileSplitMerge.mergedName(anyPart.getName()));
        final ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            String error = null;
            try {
                FileSplitMerge.merge(parts, out, (done, total) -> {
                    pm.setProgress((int) (total == 0 ? 0 : done * 100 / total));
                    return true;
                });
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String err = error;
            pm.dismiss();
            context.runOnUiThread(() -> {
                if (err != null) {
                    new ErrorUtil(context).showError(new IOException(err));
                } else {
                    context.loadFolderInPane(pane1 ? context.pane1Folder : context.pane2Folder, pane1);
                    Extensions.showMessage(context, out.getName());
                }
            });
        }).start();
    }

    /** Shows a password box and hands the entered text to {@code onPassword}. */
    private void promptGpgPassword(String title, java.util.function.Consumer<String> onPassword) {
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(R.string.enter_password_gpg);
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String pw = input.getText() == null ? "" : input.getText().toString();
                    if (!pw.isEmpty()) onPassword.accept(pw);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Asks for the recipient's public key file path. */
    private void promptGpgKeyFile(String title, java.util.function.Consumer<String> onKeyPath) {
        final EditText input = new EditText(context);
        input.setHint(R.string.gpg_key_path_hint);
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String path = input.getText() == null ? "" : input.getText().toString().trim();
                    if (!path.isEmpty()) onKeyPath.accept(path);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Asks for the secret key file path and its passphrase (may be empty). */
    private void promptGpgKeyAndPassphrase(String title,
                                           java.util.function.BiConsumer<String, String> onBoth) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        box.setPadding(pad, 0, pad, 0);
        final EditText keyPath = new EditText(context);
        keyPath.setHint(R.string.gpg_key_path_hint);
        final EditText pass = new EditText(context);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setHint(R.string.gpg_passphrase_hint);
        box.addView(keyPath);
        box.addView(pass);
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String path = keyPath.getText() == null ? "" : keyPath.getText().toString().trim();
                    String pw = pass.getText() == null ? "" : pass.getText().toString();
                    if (!path.isEmpty()) onBoth.accept(path, pw);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}

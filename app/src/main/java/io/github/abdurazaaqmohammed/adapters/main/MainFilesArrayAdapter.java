package io.github.abdurazaaqmohammed.adapters.main;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
import io.github.abdurazaaqmohammed.listeners.SwipeTouchListener;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.activities.CompareTextActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareArscDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareDexOptionsDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareZipDialog;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;
import io.github.abdurazaaqmohammed.features.files.EntryDialogs;
import io.github.abdurazaaqmohammed.features.files.FileOpener;
import io.github.abdurazaaqmohammed.features.media.BatchImageTools;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
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

    /** Row metrics snapshotted once: bind previously re-read prefs per row. */
    private final float nameTextSize;
    private final int nameMaxLines;
    private final int rowIconPx;
    /** Memoized isDir per listing (one adapter instance == one listing). */
    private final Map<String, Boolean> dirMemo = new HashMap<>();

    public void setMultiSelectMode(boolean multiSelectMode) {
        context.setMultiSelectModeUI(isMultiSelectMode = multiSelectMode);
    }

    private boolean isMultiSelectMode = false;

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    public final Set<Integer> selectedPositions = new HashSet<>();
    private Integer rangeStartPosition = null;

    public void restoreSelection(Set<Integer> positions) {
        if (positions != null && !positions.isEmpty()) {
            selectedPositions.clear();
            selectedPositions.addAll(positions);
            isMultiSelectMode = true;
            notifyDataSetChanged();
        }
    }

    /** Target of the synthetic ".." row, resolved once per listing. */
    private final File upFile;
    private final File upZipFile;
    private final String upZipPath;

    /** Parent folder / parent archive location the ".." row navigates to. */
    public void goUp() {
        context.clearGreen(pane1);
        context.setSelectedPane(pane1 ? 1 : 2);
        if (isInZip) {
            if (upZipFile == null) return;
            if (upZipPath == null) {
                context.leaveArchive(upZipFile, pane1);
            } else {
                context.loadZipFolderInPane(upZipFile, upZipPath, pane1, true);
            }
            return;
        }
        File target = upFile;
        if (target == null) return;
        if (target.equals(context.pane1Folder) && pane1) return;
        if (target.equals(context.pane2Folder) && !pane1) return;
        context.loadFolderInPaneAsync(target, pane1);
    }

    /** True when the row at {@code position} is the synthetic parent entry. */
    public boolean isUpRow(int position) {
        return position == 0;
    }

    private static final int GREEN_NAME = 0xFF7ED957;

    public int findPositionByName(String name) {
        if (name == null) return -1;
        for (int i = 0; i < values.length; i++) {
            Object o = values[i];
            String n = null;
            if (o instanceof File) n = ((File) o).getName();
            else if (o instanceof ZipEntryInfo) n = ((ZipEntryInfo) o).getName();
            if (name.equals(n)) return i;
        }
        return -1;
    }

    public void flashItem(String name) {
        if (name == null) return;
        context.markGreen(pane1, name);
        int pos = findPositionByName(name);
        if (pos < 0) return;
        try {
            notifyItemChanged(pos);
        } catch (Exception ignored) {}
    }

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

    /**
     * Directory test that also works for root-only paths. A plain
     * {@link File} wrapping e.g. {@code /data/data/...} reports neither
     * directory nor file (the app uid cannot stat it), which previously made
     * the ".." entry fall into the open-as-file branch and fail in
     * {@code RootManager.streamFromRoot}. Ask the elevated backend instead.
     */
    private boolean isDir(File f) {
        try {
            return f != null && f.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Symlink detection for the row badge. Shizuku listings carry the flag
     * from ls; plain listings compare the canonical path (java File follows
     * links for isDirectory/isFile, so this is display-only there).
     */
    static boolean isSymlink(File f) {
        if (f instanceof io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile sf) return sf.isLink();
        try {
            return !f.getCanonicalPath().equals(f.getAbsolutePath());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Last resort for a path the app cannot stat (RootFile/ShizukuFile carry the
     * type from their listing, so this is rarely needed). Only the active
     * backend is consulted, and only when the row is actually opened - doing it
     * during bind spawned a shell per row on listings the app cannot stat.
     */
    private boolean resolveDirViaBackend(File f) {
        if (f == null) return false;
        try {
            String path = f.getAbsolutePath();
            AccessManager.Backend backend = AccessManager.active(context);
            if (backend == AccessManager.Backend.ROOT) {
                return RootManager.getInstance(context).isDirectory(path);
            }
            if (backend == AccessManager.Backend.SHIZUKU) {
                return ShizukuManager.isDirectory(context, path);
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Opens a row: navigate when it is a folder, open it otherwise. Restricted
     * paths are classified off the UI thread so a tap never blocks on a shell.
     */
    private void handleRowClick(final File f, final String fileName) {
        if (f == null) return;
        if (isDirCached(f)) {
            context.clearGreen(pane1);
            context.setSelectedPane(pane1 ? 1 : 2);
            context.loadFolderInPaneAsync(f, pane1);
            return;
        }
        if (f.isFile()) {
            context.clearGreen(pane1);
            context.setSelectedPane(pane1 ? 1 : 2);
            context.setCurrentFolder(f.getParentFile(), getOldValues());
            fileOpener.handleFileClick(f, fileName);
            return;
        }
        new Thread(() -> {
            final boolean dir = resolveDirViaBackend(f);
            context.handler.post(() -> {
                try {
                    context.clearGreen(pane1);
                    context.setSelectedPane(pane1 ? 1 : 2);
                    if (dir) {
                        context.loadFolderInPaneAsync(f, pane1);
                    } else {
                        context.setCurrentFolder(f.getParentFile(), getOldValues());
                        fileOpener.handleFileClick(f, fileName);
                    }
                } catch (Exception ignored) {
                }
            });
        }).start();
    }

    public MainFilesArrayAdapter(MainActivity context, Object[] values, Object parent, boolean pane1, boolean isInZip,
            String currentZipPath) {
        this.values = isInZip ? values : getNewValues(values, (File) parent);
        this.context = context;
        this.pane1 = pane1;
        this.isInZip = isInZip;
        this.currentZipPath = currentZipPath;
        // Row 0 is the synthetic ".." entry. Remember where it goes instead of
        // re-deriving it from the row value on every click, so navigating up can
        // never depend on the row's File/ZipEntryInfo type.
        if (isInZip) {
            ZipEntryInfo up = (parent instanceof ZipEntryInfo ze) ? ze : null;
            this.upZipPath = up != null ? up.getFullPath() : null;
            this.upZipFile = up != null ? up.getZipFile() : null;
            this.upFile = null;
        } else {
            this.upFile = (File) parent;
            this.upZipFile = null;
            this.upZipPath = null;
        }
        int scale = UiPrefs.getScale(context);
        this.nameTextSize = UiPrefs.nameSize(scale);
        this.nameMaxLines = UiPrefs.getMaxLines(context);
        this.rowIconPx = UiPrefs.iconDp(context, scale);
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
                            Object o = values[p];
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
                        // Use the adapter field, not the constructor parameter
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

    /** Local directory test, memoized per listing. Never shells out. */
    private boolean isDirCached(File f) {
        if (f == null) return false;
        String key;
        try {
            key = f.getAbsolutePath();
        } catch (Exception ignored) {
            return isDir(f);
        }
        Boolean cached = dirMemo.get(key);
        if (cached != null) return cached;
        boolean result = isDir(f);
        try {
            dirMemo.put(key, result);
        } catch (Exception ignored) {
        }
        return result;
    }

    public void openWithForFile(File file, String fileName) {
        fileOpener.openWithForFile(file, fileName);
    }

    /** Zip-aware variant so editors launched from an archive know the entry's source. */
    public void openWithForFile(File file, String fileName, File zipFile, String zipEntryPath) {
        fileOpener.openWithForFile(file, fileName, zipFile, zipEntryPath);
    }

    @Override
    public int getItemCount() { return values.length; }

    public Object getItem(int position) { return values[position]; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView fileNameView, fileDateView;
        final ImageView fileIconView, appBadgeView;
        final int defaultNameColor;
        // Last values actually pushed to the row. Re-applying the same text
        // size or max lines invalidates the text layout on every bind, which is
        // costly while scrolling a large folder.
        int appliedTextSizePx = -1;
        int appliedMaxLines = -1;
        int appliedBackgroundColor = -2;
        View.OnClickListener boundClickListener;
        View.OnLongClickListener boundLongClickListener;
        SwipeTouchListener touchListener;
        /**
         * Position of the last completed bind. {@link ViewHolder#getBindingAdapterPosition()}
         * answers NO_POSITION while a row is momentarily detached, which silently
         * dropped swipes on the first render of a freshly opened folder.
         */
        int lastBoundPosition = RecyclerView.NO_POSITION;

        ViewHolder(View v) {
            super(v);
            fileNameView = v.findViewById(R.id.fileName);
            fileIconView = v.findViewById(R.id.fileIcon);
            appBadgeView = v.findViewById(R.id.appBadge);
            fileDateView = v.findViewById(R.id.fileDate);
            int color;
            try {
                color = fileNameView.getCurrentTextColor();
            } catch (Exception e) {
                color = 0xFFFFFFFF;
            }
            defaultNameColor = color;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(
                LayoutInflater.from(context).inflate(R.layout.list_file, parent, false));
        // One gesture detector per row view instead of per bind: building one
        // allocated a GestureDetector and two interpolators on every bind.
        holder.touchListener = new SwipeTouchListener(
                context,
                v -> {
                    View.OnClickListener l = holder.boundClickListener;
                    if (l != null) l.onClick(v);
                },
                v -> {
                    View.OnLongClickListener l = holder.boundLongClickListener;
                    return l != null && l.onLongClick(v);
                },
                () -> {
                    int live = holder.getBindingAdapterPosition();
                    return live != RecyclerView.NO_POSITION ? live : holder.lastBoundPosition;
                },
                MainFilesArrayAdapter.this,
                pane1 ? 1 : 2);
        holder.itemView.setOnTouchListener(holder.touchListener);
        return holder;
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
                                msg = result.getResultCode() == Activity.RESULT_OK
                                        ? "Done" : "Cancelled";
                            }
                            Extensions.showMessage(context, msg);
                        } catch (Exception ignored) {
                        }
                    }));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final View convertView = holder.itemView;
        // Point the row's gesture detector at this adapter before anything can
        // dispatch, so a recycled row never drives the adapter it was created for.
        if (holder.touchListener != null) holder.touchListener.rebind(this, pane1 ? 1 : 2);
        position = holder.getBindingAdapterPosition();
        if (position < 0 || position >= values.length) return;
        holder.lastBoundPosition = position;
        Object item = values[position];
        File file;
        ZipEntryInfo entry;
        String fileName;

        holder.fileNameView.setText("");
        holder.fileDateView.setText("");
        holder.fileIconView.setImageDrawable(null);
        if (holder.appBadgeView != null) {
            holder.appBadgeView.setImageDrawable(null);
            holder.appBadgeView.setVisibility(View.GONE);
            holder.appBadgeView.setTag(null);
        }

        if (holder.appliedTextSizePx != (int) (nameTextSize * holder.fileNameView.getResources().getDisplayMetrics().scaledDensity)) {
            holder.fileNameView.setTextSize(nameTextSize);
            holder.appliedTextSizePx = (int) (nameTextSize * holder.fileNameView.getResources().getDisplayMetrics().scaledDensity);
        }
        if (holder.appliedMaxLines != nameMaxLines) {
            holder.fileNameView.setMaxLines(nameMaxLines);
            holder.fileNameView.setEllipsize(TextUtils.TruncateAt.END);
            holder.appliedMaxLines = nameMaxLines;
        }
        ViewGroup.LayoutParams iconParams = holder.fileIconView.getLayoutParams();
        if (iconParams != null && (iconParams.width != rowIconPx || iconParams.height != rowIconPx)) {
            // Unconditional setLayoutParams per bind forces a remeasure of
            // every row on every bind; only touch it when the size changed.
            iconParams.width = rowIconPx;
            iconParams.height = rowIconPx;
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
            if (position == 0) {
                holder.fileDateView.setVisibility(View.GONE);
                holder.fileDateView.setTag(null);
            } else if (holder.appBadgeView != null && file.getParent() != null) {
                if (isSymlink(file)) {
                    holder.appBadgeView.setImageResource(R.drawable.ic_link_24);
                    holder.appBadgeView.setVisibility(View.VISIBLE);
                } else {
                    iconLoader.requestRowBadge(file, holder.appBadgeView, file.getParent());
                }
            }
        }

        boolean selected = selectedPositions.contains(position);
        int background = selected ? Color.DKGRAY : Color.TRANSPARENT;
        if (holder.appliedBackgroundColor != background) {
            convertView.setBackgroundColor(background);
            holder.appliedBackgroundColor = background;
        }
        try {
            holder.fileNameView.setTextColor(context.isGreen(pane1, fileName) ? GREEN_NAME : holder.defaultNameColor);
        } catch (Exception ignored) {}
        final int finalPosition = position;
        final File finalFile = file;
        final ZipEntryInfo finalEntry = entry;
        final String finalFileName = fileName;
        final Object finalItem = item;

final View.OnClickListener originalClickListener;
        if (finalPosition == 0) {
            // Parent row: never routed through the value at index 0, whose type
            // differs between plain folders (File) and archives (ZipEntryInfo).
            // Multi-select taps are intercepted by the touch listener before this
            // listener runs, so no mode check is needed here.
            originalClickListener = v -> goUp();
        } else {
            originalClickListener = isMultiSelectMode ? v -> {
                context.clearGreen(pane1);
                context.setSelectedPane(pane1 ? 1 : 2);
                handleMultiSelect(finalPosition);
            } : !isInZip && finalFile != null ?
                v -> handleRowClick(finalFile, finalFileName) : (View.OnClickListener) v -> {
                    context.clearGreen(pane1);
                    context.setSelectedPane(pane1 ? 1 : 2);
                    if (isInZip)
                        fileOps.handleZipEntryClick(finalEntry);
                    else
                        context.loadFolderInPaneAsync(finalFile, pane1);
                };
        }

        holder.boundClickListener = originalClickListener;
        if (finalPosition == 0) {
            holder.boundLongClickListener = v -> true;
        } else {
            holder.boundLongClickListener = v -> {
                showRowMenu(holder.getBindingAdapterPosition(), finalFile, finalEntry, finalFileName, finalItem);
                return true;
            };
        }
    }

    /**
     * Builds and shows a row's long-press menu on demand. Previously this ran
     * on a fresh thread for every bound row (menu model, URI staging, dialog
     * inflation) whether or not the user ever long-pressed it. Press-time
     * state (selection etc.) is also fresher than bind-time snapshots.
     */
    private void showRowMenu(int finalPosition, File file, ZipEntryInfo entry, String fileName, Object item) {
        if (finalPosition <= 0 || finalPosition >= values.length) return;
        context.clearGreen(pane1);
        context.setSelectedPane(pane1 ? 1 : 2);
        if (isInZip) {
            context.setCurrentFolder(currentZipPath, Arrays.asList(values));
        } else if (file != null)
            context.setCurrentFolder(file.getParentFile(), getOldValues());

        new Thread(() -> {
            try {
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

                if (!multi && !isInZip && !file.isDirectory() && (ArchiveUtil.isSupportedArchive(fileName) || ArchiveUtil.isMiuiOrAndroidBackup(file))) {
                    visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.EXTRACT, FileMenuOrder.labelFor(context, FileMenuOrder.EXTRACT, direction)));
                }

                RecyclerView.Adapter a = ((RecyclerView) context.findViewById(pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
                Object compareFile1 = null;
                Object compareFile2 = null;
                List<File> dexCompareFiles1 = null;
                List<File> dexCompareFiles2 = null;
                if(a instanceof MainFilesArrayAdapter otherPaneAdapter) {
                    dexCompareFiles1 = collectDexCompareFiles();
                    dexCompareFiles2 = otherPaneAdapter.collectDexCompareFiles();
                    if (dexCompareFiles1 != null && dexCompareFiles2 != null) {
                        visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_DEX, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_DEX, direction)));
                    }
                    Object[] pair = null;
                    if (selectedPositions.size() == 1 && otherPaneAdapter.selectedPositions.size() == 1) {
                        int i1 = selectedPositions.iterator().next();
                        int i2 = otherPaneAdapter.selectedPositions.iterator().next();
                        if (i1 >= 0 && i1 < values.length && i2 >= 0 && i2 < otherPaneAdapter.values.length) {
                            Object o1 = values[i1];
                            Object o2 = otherPaneAdapter.values[i2];
                            if (isComparableEntry(o1) && isComparableEntry(o2)) {
                                compareFile1 = o1;
                                compareFile2 = o2;
                                pair = new Object[]{o1, o2};
                            }
                        }
                    } else if (selectedPositions.size() == 2 && otherPaneAdapter.selectedPositions.isEmpty()) {
                        Iterator<Integer> it = selectedPositions.iterator();
                        int i1 = it.next();
                        int i2 = it.next();
                        if (i1 >= 0 && i1 < values.length && i2 >= 0 && i2 < values.length) {
                            Object o1 = values[i1];
                            Object o2 = values[i2];
                            if (isComparableEntry(o1) && isComparableEntry(o2)) {
                                compareFile1 = o1;
                                compareFile2 = o2;
                                pair = new Object[]{o1, o2};
                                List<File> sameDex1 = singleDexList(o1);
                                List<File> sameDex2 = singleDexList(o2);
                                if (sameDex1 != null && sameDex2 != null && dexCompareFiles1 == null) {
                                    dexCompareFiles1 = sameDex1;
                                    dexCompareFiles2 = sameDex2;
                                    visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_DEX, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_DEX, direction)));
                                }
                            }
                        }
                    }
                    if (pair != null) {
                        Object o1 = pair[0];
                        Object o2 = pair[1];
                        String name1 = o1 instanceof File ? ((File) o1).getName() : ((ZipEntryInfo) o1).getName();
                        String name2 = o2 instanceof File ? ((File) o2).getName() : ((ZipEntryInfo) o2).getName();
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
                            if (o1 instanceof File && o2 instanceof File
                                    && !((File) o1).isDirectory() && !((File) o2).isDirectory())
                                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_HASH, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_HASH, direction)));
                        }
                        if (ext1.equals("apk") && ext2.equals("apk")
                                && o1 instanceof File && o2 instanceof File)
                            visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_APK, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_APK, direction)));
                    }
                } else if (selectedPositions.size() == 2) {
                    Iterator<Integer> it = selectedPositions.iterator();
                    int i1 = it.next();
                    int i2 = it.next();
                    if (i1 >= 0 && i1 < values.length && i2 >= 0 && i2 < values.length) {
                        Object o1 = values[i1];
                        Object o2 = values[i2];
                        if (isComparableEntry(o1) && isComparableEntry(o2)) {
                            compareFile1 = o1;
                            compareFile2 = o2;
                            String name1 = o1 instanceof File ? ((File) o1).getName() : ((ZipEntryInfo) o1).getName();
                            String name2 = o2 instanceof File ? ((File) o2).getName() : ((ZipEntryInfo) o2).getName();
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
                                if (o1 instanceof File && o2 instanceof File
                                        && !((File) o1).isDirectory() && !((File) o2).isDirectory())
                                    visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_HASH, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_HASH, direction)));
                            }
                            if (ext1.equals("apk") && ext2.equals("apk")
                                    && o1 instanceof File && o2 instanceof File)
                                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_APK, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_APK, direction)));
                            List<File> sameDex1 = singleDexList(o1);
                            List<File> sameDex2 = singleDexList(o2);
                            if (sameDex1 != null && sameDex2 != null) {
                                dexCompareFiles1 = sameDex1;
                                dexCompareFiles2 = sameDex2;
                                visibleMenu.add(new FileMenuOrder.MenuItem(FileMenuOrder.CMP_DEX, FileMenuOrder.labelFor(context, FileMenuOrder.CMP_DEX, direction)));
                            }
                        }
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
                final List<File> finalDexCompare1 = dexCompareFiles1;
                final List<File> finalDexCompare2 = dexCompareFiles2;

                final boolean twoColumnMenu = FileMenuOrder.isTwoColumn(context);
                View menuView = LayoutInflater.from(context).inflate(R.layout.dialog_file_menu, null);
                ((TextView) menuView.findViewById(R.id.fileMenuTitle)).setText(fileName);
                RecyclerView menuList = menuView.findViewById(R.id.fileMenuList);
                final BottomSheetDialog menuSheet;
                final MaterialAlertDialogBuilder menuDialogBuilder;
                final AlertDialog[] menuDialog = {null};
                if (twoColumnMenu) {
                    View handle = menuView.findViewById(R.id.fileMenuHandle);
                    if (handle != null) handle.setVisibility(View.GONE);
                    menuList.setLayoutManager(new GridLayoutManager(context, 2));
                    float density = context.getResources().getDisplayMetrics().density;
                    int edge = (int) (12 * density + 0.5f);
                    menuList.setPadding(edge, menuList.getPaddingTop(), edge, menuList.getPaddingBottom());
                    menuSheet = null;
                    menuDialogBuilder = new MaterialAlertDialogBuilder(context).setView(menuView);
                } else {
                    menuList.setLayoutManager(new LinearLayoutManager(context));
                    menuSheet = new BottomSheetDialog(context);
                    menuDialogBuilder = null;
                }
                menuList.setAdapter(new DialogAdapter(context, menuItems, isInZip, twoColumnMenu, position1 -> {
                    if (menuSheet != null) menuSheet.dismiss();
                    if(menuDialog[0] != null) menuDialog[0].dismiss();
                    try {
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
                            case FileMenuOrder.CMP_DEX:
                                if (finalDexCompare1 != null && finalDexCompare2 != null) {
                                    new CompareDexOptionsDialog(context, finalDexCompare1, finalDexCompare2).show();
                                }
                                return;
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
                                        entryDialogs.showRenameDialog(finalPosition, file, entry, fileName, multi);
                                        break;
                                    case FileMenuOrder.DELETE:
                                        entryDialogs.showDeleteDialog(finalPosition, file, entry, multi);
                                        break;
                                    case FileMenuOrder.COMPRESS:
                                        entryDialogs.showCompressDialog(file, fileName, multi);
                                        break;
                                    case FileMenuOrder.PROPERTIES:
                                        propertiesDialog.show(multi, values, selectedPositions, isInZip, file, entry, fileName, entryDialogs.getFilesToDisplay(multi, finalPosition).toString());
                                        break;
                                    case FileMenuOrder.SHARE:
                                        if (isInZip && multi) {
                                            List<ZipEntryInfo> shareEntries = new ArrayList<>();
                                            for (int sp : selectedPositions) {
                                                if (sp >= 0 && sp < values.length && values[sp] instanceof ZipEntryInfo ze) {
                                                    if (!ze.isDirectory()) shareEntries.add(ze);
                                                }
                                            }
                                            if (shareEntries.size() == 1) fileOpener.shareZipEntry(shareEntries.get(0), shareEntries.get(0).getName());
                                            else if (shareEntries.size() > 1) fileOpener.shareZipEntries(shareEntries);
                                        } else if (isInZip) {
                                            fileOpener.shareZipEntry(item, fileName);
                                        } else if (multi) {
                                            // Only real files can be staged as content URIs; directories
                                            // can't be shared and must not count towards the completion
                                            // check or the chooser never fires.
                                            List<File> shareFiles = new ArrayList<>();
                                            for (int sp : selectedPositions) {
                                                if (sp >= 0 && sp < values.length && values[sp] instanceof File f) {
                                                    if (!f.isDirectory()) shareFiles.add(f);
                                                }
                                            }
                                            if (shareFiles.size() == 1) {
                                                File only = shareFiles.get(0);
                                                fileOpener.withReadableCopy(only, readable -> {
                                                    Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
                                                    String shareMime = MimeUtil.getMimeTypeForAction(context, readable);
                                                    context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                                            .setType(shareMime != null ? shareMime : "application/octet-stream")
                                                            .putExtra(Intent.EXTRA_STREAM, uri)
                                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share " + only.getName()));
                                                });
                                            } else if (shareFiles.size() > 1) {
                                                List<Uri> uris = java.util.Collections.synchronizedList(new ArrayList<>());
                                                java.util.concurrent.atomic.AtomicInteger pending =
                                                        new java.util.concurrent.atomic.AtomicInteger(shareFiles.size());
                                                for (File sf : shareFiles) {
                                                    fileOpener.withReadableCopy(sf, readable -> {
                                                        try {
                                                            Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
                                                            uris.add(uri);
                                                        } catch (Exception ignored) {
                                                        }
                                                        if (pending.decrementAndGet() == 0) {
                                                            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                                                            intent.setType("*/*");
                                                            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
                                                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                                            context.startActivity(Intent.createChooser(intent, "Share " + uris.size() + " files"));
                                                        }
                                                    });
                                                }
                                            }
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
                    context.runOnUiThread(() -> menuDialog[0] = menuDialogBuilder.show());
                }
            } catch (Exception e) {
                try {
                    new ErrorUtil(context).showError(e);
                } catch (Exception ignored) {
                }
            }
        }).start();
    }








    private void updateFolderCountOnMainScreen(int position) {
    }

    /**
     * Returns the selected files when the selection is exactly one APK or one or
     * more DEX files. Returns null for anything else (kept files, folders, mixed
     * types, archive entries) so the Compare DEX item only shows for valid input.
     */
    private List<File> collectDexCompareFiles() {
        if (isInZip || selectedPositions.isEmpty()) return null;
        List<File> files = new ArrayList<>();
        boolean hasApk = false;
        for (int position : selectedPositions) {
            if (position < 0 || position >= values.length) return null;
            Object selected = values[position];
            if (!(selected instanceof File selectedFile) || selectedFile.isDirectory()) return null;
            String name = selectedFile.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".apk")) {
                hasApk = true;
            } else if (!name.endsWith(".dex")) {
                return null;
            }
            files.add(selectedFile);
        }
        if (hasApk && files.size() != 1) return null;
        return files;
    }

    private boolean isComparableEntry(Object o) {
        if (o == null) return false;
        if (o instanceof File f) {
            try {
                String n = f.getName();
                if (n == null || n.equals("..")) return false;
                if (f.getAbsolutePath() == null) return false;
            } catch (Exception ignored) { return false; }
            return true;
        }
        if (o instanceof ZipEntryInfo z) {
            try {
                String n = z.getName();
                if (n == null || n.equals("..")) return false;
            } catch (Exception ignored) { return false; }
            return true;
        }
        return false;
    }

    private List<File> singleDexList(Object o) {
        if (isInZip) return null;
        if (!(o instanceof File f)) return null;
        try {
            if (f.isDirectory()) return null;
            String n = f.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".apk") || n.endsWith(".dex")) {
                List<File> l = new ArrayList<>();
                l.add(f);
                return l;
            }
        } catch (Exception ignored) {}
        return null;
    }

    public void handleSwipe(int position) {
        if (position <= 0 || position >= values.length) return;
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
        if (position <= 0 || position >= values.length) return;
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

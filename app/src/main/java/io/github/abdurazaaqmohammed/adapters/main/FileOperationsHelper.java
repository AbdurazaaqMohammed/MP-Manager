package io.github.abdurazaaqmohammed.adapters.main;

import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.reandroid.apkeditor.Util;

import java.util.Locale;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.io.FilenameUtils;

import io.github.abdurazaaqmohammed.utils.RecycleBin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.features.dex.DexTools;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.DestSizePoller;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.ShellProgress;
import io.github.abdurazaaqmohammed.utils.ZipCompat;
import io.github.codehasan.colorpicker.extensions.Extensions;

public class FileOperationsHelper {

    public static final int UPDATE_MODE_REPLACE_ALL = 0;
    public static final int UPDATE_MODE_UPDATE_AND_REPLACE = 1;
    public static final int UPDATE_MODE_SKIP_ALL = 2;

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final MainFilesArrayAdapter adapter;
    private final DexTools dex;
    /** Folder path -> entry names, so planning checks cost one listing per folder. */
    private final Map<String, String[]> dirListingCache = new HashMap<>();

    private volatile ProgressManager activeProgress;

    private interface IoOperation {
        void run() throws Exception;
    }

    public FileOperationsHelper(MainActivity context, DialogUtil dialogUtil, MainFilesArrayAdapter adapter) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.adapter = adapter;
        this.dex = new DexTools(context, dialogUtil, adapter.pane1, new DexTools.OpenWith() {
            @Override
            public void open(File file, String fileName) {
                adapter.openWithForFile(file, fileName);
            }

            @Override
            public void open(File file, String fileName, File zipFile, String zipEntryPath) {
                adapter.openWithForFile(file, fileName, zipFile, zipEntryPath);
            }
        });
    }

    public void showDexOptionsDialog(File dexFile, File zipFile, String entryPath, String displayName) {
        dex.showDexOptionsDialog(dexFile, zipFile, entryPath, displayName);
    }

    public void handleZipEntryClick(ZipEntryInfo zipEntry) {
        dex.handleZipEntryClick(zipEntry);
    }

    private void showActiveProgress(String text) {
        showActiveProgress(text, 0);
    }

    /**
     * Opens the dialog immediately as a spinner. The totals are filled in a
     * moment later by {@link #applyMeasure} on the worker thread, because
     * measuring a root-only tree needs a shell round trip and must never run on
     * the UI thread.
     */
    private void showActiveProgress(String text, int operationRes) {
        dismissActiveProgress();
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(text);
        if (operationRes != 0) pm.setOperation(operationRes);
        activeProgress = pm;
        pm.show();
    }

    /** Pushes measured totals into the live dialog. Safe from any thread. */
    private void applyMeasure(FileUtils.Measure measure) {
        ProgressManager pm = activeProgress;
        if (pm == null || measure == null || measure.files <= 0) return;
        pm.setProgress(0, measure.files);
        pm.setBytes(0L, measure.bytes);
    }

    private void dismissActiveProgress() {
        ProgressManager pm = activeProgress;
        if (pm != null) pm.dismiss();
    }

    private void advanceActiveProgress(int done, int total) {
        ProgressManager pm = activeProgress;
        if (pm != null && total > 0) pm.setProgress(Math.min(done, total), total);
    }

    /** Snaps the dialog to 100% right before it closes, so it never stops short. */
    private void completeActiveProgress(int totalFiles, long totalBytes) {
        ProgressManager pm = activeProgress;
        if (pm == null) return;
        if (totalFiles > 0) pm.setProgress(totalFiles, totalFiles);
        if (totalBytes > 0) pm.setBytes(totalBytes, totalBytes);
    }

    private void runWithProgress(String text, IoOperation op) {
        showActiveProgress(text, 0);
        new Thread(() -> {
            try {
                op.run();
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            } finally {
                //activeProgress = null;
                dismissActiveProgress();
            }
        }).start();
    }

    private void runWithProgress(String text, List<?> items, IoOperation op) {
        runWithProgress(text, items, 0, op);
    }

    /**
     * @param operationRes caption shown above the title ({@code COPY}, {@code MOVE}, …);
     *                     0 leaves it off
     */
    private void runWithProgress(String text, List<?> items, int operationRes, IoOperation op) {
        showActiveProgress(text, operationRes);
        new Thread(() -> {
            try {
                // Measuring happens here, not on the UI thread: an elevated tree
                // needs a shell round trip to count.
                applyMeasure(measureInfo(items).total);
                op.run();
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            } finally {
                //activeProgress = null;
                dismissActiveProgress();
            }
        }).start();
    }

    private static final int PROGRESS_COUNT_CAP = 20000;

    /** Totals for the whole selection plus each item's own share of them. */
    private static final class MeasureInfo {
        final FileUtils.Measure total;
        final java.util.Map<String, FileUtils.Measure> byPath;

        MeasureInfo(FileUtils.Measure total, java.util.Map<String, FileUtils.Measure> byPath) {
            this.total = total;
            this.byPath = byPath;
        }

        FileUtils.Measure of(File f) {
            FileUtils.Measure m = f == null ? null : byPath.get(f.getAbsolutePath());
            return m != null ? m : new FileUtils.Measure(1, 0L);
        }
    }

    private volatile List<?> measuredItems;
    private volatile MeasureInfo measuredInfo;

    private FileUtils.Measure measureItems(List<?> items) {
        return measureInfo(items).total;
    }

    /** Cached totals only — never measures, so it is safe on the UI thread. */
    private FileUtils.Measure cachedMeasure(List<?> items) {
        MeasureInfo info = measuredInfo;
        if (info == null) return null;
        if (items != null && items != measuredItems) return null;
        return info.total;
    }

    /**
     * File and byte totals for {@code items}, computed at most once per list
     * instance.
     *
     * <p>A plain walk returns nothing for root-only paths — {@code listFiles()}
     * simply fails — which is why elevated transfers used to show a bar that
     * never moved. When the walk comes back blind and an elevated backend is
     * active, the same numbers are asked for over the shell instead.
     */
    private MeasureInfo measureInfo(List<?> items) {
        if (items != null && items == measuredItems && measuredInfo != null) return measuredInfo;
        MeasureInfo info = computeMeasure(items);
        measuredItems = items;
        measuredInfo = info;
        return info;
    }

    private MeasureInfo computeMeasure(List<?> items) {
        List<File> roots = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        if (items != null) {
            for (Object item : items) {
                if (item instanceof File f) {
                    roots.add(f);
                    paths.add(f.getAbsolutePath());
                }
            }
        }

        java.util.Map<String, FileUtils.Measure> byPath = new java.util.HashMap<>();
        int files = 0;
        long bytes = 0L;
        boolean blind = false;
        for (File root : roots) {
            FileUtils.Measure m = FileUtils.measure(root, PROGRESS_COUNT_CAP);
            byPath.put(root.getAbsolutePath(), m);
            files += m.files;
            bytes += m.bytes;
            if (!root.canRead()) blind = true;
        }
        FileUtils.Measure local = new FileUtils.Measure(Math.max(1, files), bytes);

        if ((blind || local.bytes == 0) && AccessManager.fileOpsOn(context)) {
            FileUtils.MeasureSet elevated = AccessManager.measureSetElevated(context, paths);
            if (elevated != null && elevated.total.files > 0
                    && (elevated.total.files > local.files || elevated.total.bytes > local.bytes)) {
                java.util.Map<String, FileUtils.Measure> mapped = new java.util.HashMap<>();
                for (int i = 0; i < paths.size(); i++) {
                    FileUtils.Measure m = i < elevated.each.size() ? elevated.each.get(i) : null;
                    mapped.put(paths.get(i), m != null ? m : new FileUtils.Measure(1, 0L));
                }
                return new MeasureInfo(elevated.total, mapped);
            }
        }
        return new MeasureInfo(local, byPath);
    }

    /**
     * Bridges {@link ShellProgress} counters from a single elevated shell process
     * into the dialog, walking the op list so the file name follows along.
     *
     * @param ops    ordered exactly as the shell will run them
     * @param poller optional sampler that fills in the bytes of the item in
     *               flight, between markers
     */
    private ShellProgress.Listener shellProgress(List<RootManager.CopyOp> ops,
                                                 int totalFiles, long totalBytes,
                                                 DestSizePoller poller) {
        ProgressManager pm = activeProgress;
        if (pm == null) return null;
        return new ShellProgress.Listener() {
            private int next = 0;

            @Override
            public void onProgress(int filesDone, long bytesDone) {
                if (totalFiles > 0) pm.setProgress(Math.min(filesDone, totalFiles), totalFiles);
                if (totalBytes > 0) pm.setBytes(Math.min(bytesDone, totalBytes), totalBytes);
                // A marker means op[next] just finished, so show the one after it.
                if (ops == null || ops.isEmpty()) return;
                int show = Math.min(next, ops.size() - 1);
                RootManager.CopyOp op = ops.get(show);
                pm.setPaths(op.src, op.dest);
                pm.setFile(new File(op.dest));
                if (poller != null) {
                    long before = 0L;
                    for (int i = 0; i < show; i++) before += Math.max(0L, ops.get(i).bytes);
                    poller.watch(new File(op.dest), before);
                }
                next++;
            }
        };
    }

    /**
     * Starts a sampler for the item currently being written, so one large file
     * or folder still advances the bar instead of jumping at the end.
     */
    private DestSizePoller newPoller(long totalBytes) {
        if (totalBytes <= 0) return null;
        DestSizePoller poller = new DestSizePoller(absolute -> {
            ProgressManager pm = activeProgress;
            if (pm != null) pm.setBytes(Math.min(absolute, totalBytes), totalBytes);
        });
        poller.start();
        return poller;
    }

    public void copyItemsAsync(List<Object> items) {
        runWithProgress(context.rss.getString(R.string.copying, summarizeItems(items)), items, R.string.op_copying, () -> copyMultiple(items));
    }

    public void copyAsync(Object item) {
        if (adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()) copyItemsAsync(adapter.getSelectedFiles());
        else copyItemsAsync(Collections.singletonList(item));
    }

    public void moveAsync(Object item) {
        List<Object> items = (adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty())
                ? adapter.getSelectedFiles() : Collections.singletonList(item);
        runWithProgress(context.rss.getString(R.string.moving_f, summarizeItems(items)), items, R.string.op_moving, () -> move(item));
    }

    public void copy(Object item) throws IOException {
        if (adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()) {
            copyMultiple(adapter.getSelectedFiles());
        } else {
            copyMultiple(Collections.singletonList(item));
        }
    }

    public void copyMultiple(List<Object> items) throws IOException {
        if (adapter.isInZip) {
            copyFromZip(items);
        } else {
            copyToDestination(items);
        }
    }

    public void move(Object item) throws IOException {
        if (adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()) {
            List<Object> itemsToMove = adapter.getSelectedFiles();
            if (adapter.isInZip) {
                if (!copyFromZip(itemsToMove)) return;
                for (Object o : itemsToMove) deleteZipEntry((ZipEntryInfo) o);
                context.handler.post(adapter::clearSelection);
            } else {
                moveToDestination(itemsToMove);
            }
        } else if (adapter.isInZip) {
            if (!copyToDestination(Collections.singletonList(item))) return;
            // Degrades to a copy when the source archive cannot be rewritten
            // (7z/rar/tar): the data is already out, nothing more to do.
            if (ArchiveBrowseUtil.isZipLike(((ZipEntryInfo) item).getZipFile()))
                deleteZipEntry((ZipEntryInfo) item);
            context.handler.post(adapter::clearSelection);
        } else {
            moveToDestination(Collections.singletonList(item));
        }
    }

    private boolean moveToDestination(List<Object> items) throws IOException {
        File destinationFolder = adapter.pane1 ? context.pane2Folder : context.pane1Folder;
        RecyclerView.Adapter rvAdapter = ((RecyclerView) context.findViewById(adapter.pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        if (rvAdapter instanceof FtpFilesArrayAdapter) {
            ((FtpFilesArrayAdapter) rvAdapter).uploadFiles(items);
            return true;
        }
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) rvAdapter;
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip && !ArchiveBrowseUtil.isZipLike(destinationFolder)) {
            // Browsing a 7z/rar/tar: entries cannot be written back into it.
            Extensions.showMessage(context, R.string.archive_not_editable);
            return true;
        }
        if (destIsZip) {
            if (!copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath)) return false;
            for (Object item : items) {
                if (item instanceof File) ((File) item).delete();
                else if (item instanceof ZipEntryInfo) deleteZipEntry((ZipEntryInfo) item);
            }
            return true;
        }

        boolean useElevated = AccessManager.fileOpsOn(context);
        int done = 0;
        final MeasureInfo info = measureInfo(items);
        final int totalFiles = info.total.files;
        final long totalBytes = info.total.bytes;
        final FileUtils.TransferListener listener = transferListener();

        // A move needs the same replace / skip / keep-both choices a copy gets,
        // so plan the destinations up front exactly like copyToRegularFolder does.
        Map<Object, File> plan = planMoves(items, destinationFolder, useElevated);
        if (plan == null) return true;

        Set<Object> batchDone = new HashSet<>();
        if (useElevated) {
            List<RootManager.CopyOp> ops = new ArrayList<>();
            List<String> sources = new ArrayList<>();
            List<Object> opItems = new ArrayList<>();
            long opFiles = 0;
            for (Map.Entry<Object, File> planned : plan.entrySet()) {
                Object item = planned.getKey();
                if (!(item instanceof File f)) continue;
                File dest = planned.getValue();
                if (dest == null || dest.equals(f)) {
                    batchDone.add(item);
                    continue;
                }
                boolean destExists;
                try {
                    destExists = dirHasEntry(destinationFolder, dest.getName());
                } catch (Exception e) {
                    destExists = false;
                }
                FileUtils.Measure im = info.of(f);
                // The batch already removes replaced destinations, so the per-item
                // clear below must not run for the items it handled.
                ops.add(new RootManager.CopyOp(f.getAbsolutePath(), dest.getAbsolutePath(),
                        f.isDirectory(), destExists, im.files, im.bytes));
                sources.add(f.getAbsolutePath());
                opItems.add(item);
                opFiles += im.files;
            }
            if (!ops.isEmpty()) {
                DestSizePoller poller = newPoller(totalBytes);
                try {
                    if (listener != null) {
                        listener.onFile(new File(ops.get(0).src), new File(ops.get(0).dest));
                    }
                    if (poller != null) poller.watch(new File(ops.get(0).dest), 0L);
                    // The batch reports its own counters through the marker
                    // stream, so no lump sum is added afterwards.
                    AccessManager.copyAllPreserving(context, ops,
                            shellProgress(ops, totalFiles, totalBytes, poller));
                    AccessManager.deleteAll(context, sources, true);
                    batchDone.addAll(opItems);
                    done += opFiles;
                    advanceActiveProgress(done, totalFiles);
                } catch (Exception e) {
                } finally {
                    if (poller != null) poller.stop();
                }
            }
        }

        for (Map.Entry<Object, File> planned : plan.entrySet()) {
            Object item = planned.getKey();
            if (batchDone.contains(item)) continue;
            File dest = planned.getValue();
            if (item instanceof File f) {
                if (dest == null || dest.equals(f)) continue;
                FileUtils.Measure im = info.of(f);
                int itemTotal = im.files;
                long itemBytes = im.bytes;
                clearReplacedDestination(dest, useElevated);
                if (useElevated) {
                    try {
                        // copy -p keeps the timestamps, so no separate preserveTime
                        // round trip is needed. Rename falls back to copy+delete
                        // across filesystems, which mv already does internally.
                        if (listener != null) listener.onFile(f, dest);
                        AccessManager.copyPreserving(context, f.getAbsolutePath(), dest.getAbsolutePath(),
                                f.isDirectory(), true);
                        AccessManager.delete(context, f.getAbsolutePath(), true);
                        done += itemTotal;
                        advanceActiveProgress(done, totalFiles);
                        if (listener != null) listener.onBytes(itemBytes);
                        continue;
                    } catch (Exception e) {
                    }
                }
                if (ShizukuFileOps.involvesShizukuPath(f, destinationFolder) && ShizukuFileOps.shellMove(f, destinationFolder, dest.getName())) {
                    if (listener != null) {
                        listener.onFile(f, dest);
                        listener.onBytes(itemBytes);
                    }
                    done += itemTotal;
                    advanceActiveProgress(done, totalFiles);
                    continue;
                }
                if (dest.exists()) {
                    RecycleBin.moveToBin(context, dest, "overwrite");
                }
                if (f.renameTo(dest)) {
                    if (listener != null) {
                        listener.onFile(f, dest);
                        listener.onBytes(itemBytes);
                    }
                    done += itemTotal;
                    advanceActiveProgress(done, totalFiles);
                    continue;
                }
                if (f.isDirectory()) {
                    if (useElevated) {
                        try {
                            AccessManager.mkdir(context, dest.getAbsolutePath(), true);
                        } catch (Exception ignored) {
                            //noinspection ResultOfMethodCallIgnored
                            dest.mkdir();
                        }
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        dest.mkdir();
                    }
                    FileUtils.copyFolder(f, dest, listener);
                    syncDirTimes(f, dest);
                } else {
                    FileUtils.copyFile(f, dest, listener);
                    AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                }
                if (copySize(f) != copySize(dest)) {
                    throw new IOException("Move failed, copy mismatch: " + f.getName());
                }
                deleteRecursive(f);
                done += itemTotal;
                advanceActiveProgress(done, totalFiles);
            } else if (item instanceof ZipEntryInfo z) {
                if (dest != null) {
                    extractZipEntry(z, destinationFolder, dest.getName());
                }
                done++;
                advanceActiveProgress(done, totalFiles);
            }
        }
        completeActiveProgress(totalFiles, totalBytes);
        List<String> affectedNames = new ArrayList<>();
        for (File destFile : plan.values()) {
            if (destFile != null) affectedNames.add(destFile.getName());
        }
        context.handler.post(() -> {
            adapter.clearSelection();
            clearDirListingCache();
            if (!affectedNames.isEmpty()) {
                context.highlightAndFocusItems(!adapter.pane1, affectedNames);
            } else {
                context.loadFolderInPane(destinationFolder, !adapter.pane1);
            }
            // Source pane also changed after a move (files deleted) — reload it too.
            try {
                File sourceFolder = adapter.pane1 ? context.pane1Folder : context.pane2Folder;
                if (sourceFolder != null && !sourceFolder.equals(destinationFolder)) {
                    context.loadFolderInPane(sourceFolder, adapter.pane1);
                } else {
                    context.refreshAllPanes();
                }
            } catch (Exception ignored) { }
        });
        return true;
    }

    /**
     * Removes whatever already sits at the planned destination, so "replace"
     * really replaces instead of failing the rename or appending into the old
     * folder.
     */
    private void clearReplacedDestination(File dest, boolean useElevated) {
        if (dest == null) return;
        try {
            String binMode = RecycleBin.mode(context);
            if (!RecycleBin.MODE_NEVER.equals(binMode) && !RecycleBin.isInsideBin(context, dest)) {
                RecycleBin.moveToBin(context, dest, "overwrite");
            }
            if (useElevated) {
                if (!AccessManager.exists(context, dest.getAbsolutePath())) return;
                AccessManager.delete(context, dest.getAbsolutePath(), true);
                return;
            }
            if (!dest.exists()) return;
            if (dest.isDirectory()) deleteRecursive(dest);
            else //noinspection ResultOfMethodCallIgnored
                dest.delete();
        } catch (Exception ignored) {
        }
    }

    private static long copySize(File f) {
        if (f.isFile()) return f.length();
        long total = 0;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) total += copySize(k);
        return total;
    }

    private static void deleteRecursive(File f) throws IOException {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        if (f.exists() && !f.delete()) throw new IOException("Cannot delete " + f.getName());
    }

    private File getUnusedDest(File destDir, String name, boolean useElevated) {
        File first = FileUtils.getUnusedFile(destDir, name);
        if (!useElevated) return first;
        try {
            if (!dirHasEntry(destDir, name)) return first;
            String base = FilenameUtils.getBaseName(name);
            String ext = FilenameUtils.getExtension(name);
            for (int i = 1; i < 1000; i++) {
                String candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
                if (!dirHasEntry(destDir, candidate)) return new File(destDir, candidate);
            }
        } catch (Exception ignored) {
        }
        return first;
    }

    /**
     * Existence test inside one folder, backed by a single directory listing.
     * Asking the elevated backend per name costs one shell round trip each, which
     * dominated the runtime of every multi-selection copy.
     */
    private boolean dirHasEntry(File dir, String name) {
        if (dir == null || name == null) return false;
        try {
            if (new File(dir, name).exists()) return true;
        } catch (Exception ignored) {
        }
        final boolean elevated = AccessManager.fileOpsOn(context);
        if (!elevated) return false;
        String key = dir.getAbsolutePath();
        String[] names = dirListingCache.get(key);
        if (names == null) {
            try {
                File[] listed = AccessManager.listWithStat(context, key);
                if (listed != null) {
                    names = new String[listed.length];
                    for (int i = 0; i < listed.length; i++) names[i] = listed[i].getName();
                    dirListingCache.put(key, names);
                    for (String n : names) if (n.equals(name)) return true;
                    return false;
                }
            } catch (Exception ignored) {
            }
            return AccessManager.exists(context, new File(dir, name).getAbsolutePath());
        }
        for (String n : names) if (n.equals(name)) return true;
        return false;
    }

    private void clearDirListingCache() {
        dirListingCache.clear();
    }

    private void syncDirTimes(File srcDir, File dstDir) {
        try {
            AccessManager.preserveTime(context, srcDir.getAbsolutePath(), dstDir.getAbsolutePath());
            File[] kids = srcDir.listFiles();
            if (kids == null) return;
            for (File k : kids) {
                File d = new File(dstDir, k.getName());
                if (!d.exists()) continue;
                if (k.isDirectory()) syncDirTimes(k, d);
                else AccessManager.preserveTime(context, k.getAbsolutePath(), d.getAbsolutePath());
            }
        } catch (Exception ignored) {
        }
    }

    private boolean copyToDestination(List<Object> items) throws IOException {
        File destinationFolder = adapter.pane1 ? context.pane2Folder : context.pane1Folder;
        RecyclerView.Adapter rvAdapter = ((RecyclerView) context.findViewById(adapter.pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        if (rvAdapter instanceof FtpFilesArrayAdapter) {
            ((FtpFilesArrayAdapter) rvAdapter).uploadFiles(items);
            return true;
        }
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) rvAdapter;
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip && !ArchiveBrowseUtil.isZipLike(destinationFolder)) {
            // Browsing a 7z/rar/tar: entries cannot be written back into it.
            Extensions.showMessage(context, R.string.archive_not_editable);
            return true;
        }
        if (destIsZip) {
            return copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            return copyToRegularFolder(items, destinationFolder);
        }
    }

    private boolean copyToRegularFolder(List<Object> items, File destinationFolder) throws IOException {
        boolean useElevated = AccessManager.fileOpsOn(context);
        Map<Object, File> plan = planCopies(items, destinationFolder, useElevated);
        if (plan == null) return true;

        int done = 0;
        final MeasureInfo info = measureInfo(items);
        final int totalFiles = info.total.files;
        final long totalBytes = info.total.bytes;
        final FileUtils.TransferListener listener = transferListener();

        Set<Object> batchDone = new HashSet<>();
        if (useElevated) {
            List<RootManager.CopyOp> ops = new ArrayList<>();
            List<Object> opItems = new ArrayList<>();
            long opFiles = 0;
            for (Map.Entry<Object, File> entry : plan.entrySet()) {
                Object item = entry.getKey();
                if (!(item instanceof File f)) continue;
                File dest = entry.getValue();
                if (dest == null || dest.equals(f)) {
                    batchDone.add(item);
                    continue;
                }
                FileUtils.Measure im = info.of(f);
                ops.add(new RootManager.CopyOp(f.getAbsolutePath(), dest.getAbsolutePath(),
                        f.isDirectory(), dirHasEntry(destinationFolder, dest.getName()),
                        im.files, im.bytes));
                opItems.add(item);
                opFiles += im.files;
            }
            if (!ops.isEmpty()) {
                DestSizePoller poller = newPoller(totalBytes);
                try {
                    if (listener != null) {
                        listener.onFile(new File(ops.get(0).src), new File(ops.get(0).dest));
                    }
                    if (poller != null) poller.watch(new File(ops.get(0).dest), 0L);
                    // One shell process still does the whole batch, but it now
                    // echoes a counter after every item.
                    AccessManager.copyAllPreserving(context, ops,
                            shellProgress(ops, totalFiles, totalBytes, poller));
                    batchDone.addAll(opItems);
                    done += opFiles;
                    advanceActiveProgress(done, totalFiles);
                } catch (Exception e) {
                } finally {
                    if (poller != null) poller.stop();
                }
            }
        }

        for (Map.Entry<Object, File> entry : plan.entrySet()) {
            Object item = entry.getKey();
            if (batchDone.contains(item)) continue;
            if (!(item instanceof File f)) continue;
            File dest = entry.getValue();
            if (dest == null || dest.equals(f)) continue;
            FileUtils.Measure im = info.of(f);
            int itemTotal = im.files;
            long itemBytes = im.bytes;
            if (useElevated) {
                try {
                    if (listener != null) listener.onFile(f, dest);
                    AccessManager.copyPreserving(context, f.getAbsolutePath(), dest.getAbsolutePath(),
                            f.isDirectory(), true);
                    done += itemTotal;
                    advanceActiveProgress(done, totalFiles);
                    if (listener != null) listener.onBytes(itemBytes);
                    continue;
                } catch (Exception e) {
                }
            }
            if (ShizukuFileOps.involvesShizukuPath(f, destinationFolder)
                    && ShizukuFileOps.shellCopy(f, destinationFolder, dest.getName()) != null) {
                if (listener != null) {
                    listener.onFile(f, dest);
                    listener.onBytes(itemBytes);
                }
                done += itemTotal;
                advanceActiveProgress(done, totalFiles);
                continue;
            }
            if (dest.exists()) {
                RecycleBin.moveToBin(context, dest, "overwrite");
            }
            if (f.isDirectory()) {
                if (useElevated) {
                    try {
                        AccessManager.mkdir(context, dest.getAbsolutePath(), true);
                    } catch (Exception ignored) {
                        //noinspection ResultOfMethodCallIgnored
                        dest.mkdir();
                    }
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    dest.mkdir();
                }
                FileUtils.copyFolder(f, dest, listener);
                syncDirTimes(f, dest);
            } else {
                FileUtils.copyFile(f, dest, listener);
                AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
            }
            done += itemTotal;
            advanceActiveProgress(done, totalFiles);
        }
        for (Object item : items) {
            if (item instanceof ZipEntryInfo z) {
                File dest = plan.get(z);
                if (dest == null) continue; // skipped by user conflict choice
                extractZipEntry(z, destinationFolder, dest.getName());
                advanceActiveProgress(++done, totalFiles);
            }
        }
        completeActiveProgress(totalFiles, totalBytes);
        List<String> affectedNames = new ArrayList<>();
        for (File destFile : plan.values()) {
            if (destFile != null) affectedNames.add(destFile.getName());
        }
        context.handler.post(() -> {
            clearDirListingCache();
            if (!affectedNames.isEmpty()) {
                context.highlightAndFocusItems(!adapter.pane1, affectedNames);
            } else {
                context.loadFolderInPane(destinationFolder, !adapter.pane1);
            }
            // Copy destination changed too — ensure the source pane reflects renames/copies.
            try { context.refreshAllPanes(); } catch (Exception ignored) { }
        });
        return true;
    }

    /** Byte-reporting adapter for the dialog that owns the current transfer. */
    private FileUtils.TransferListener transferListener() {
        ProgressManager pm = activeProgress;
        return pm == null ? null : pm.listener();
    }

    private static long sizeOf(File f) {
        if (f == null) return 0L;
        try {
            return f.isFile() ? f.length() : copySize(f);
        } catch (Exception e) {
            return 0L;
        }
    }

    private static long bytesOf(List<?> items) {
        long total = 0L;
        for (Object item : items) {
            if (item instanceof File f) total += sizeOf(f);
        }
        return total;
    }

    /**
     * Works out where every item should land before anything is written, so all
     * name conflicts in a multi-item copy can be shown in one dialog with a
     * choice that applies to all of them.
     *
     * @return source -> destination (null value means "skip"), or null when the
     *         user aborted the whole copy.
     */
    private Map<Object, File> planCopies(List<Object> items, File destinationFolder, boolean useElevated)
            throws IOException {
        return planTransfers(items, destinationFolder, useElevated, false);
    }

    /**
     * Same conflict handling as {@link #planCopies} for moves, so a move offers
     * the identical replace / skip / keep-both choices a copy does.
     */
    private Map<Object, File> planMoves(List<Object> items, File destinationFolder, boolean useElevated)
            throws IOException {
        return planTransfers(items, destinationFolder, useElevated, true);
    }

    private Map<Object, File> planTransfers(List<Object> items, File destinationFolder,
                                           boolean useElevated, boolean isMove) throws IOException {
        int singleTitle = isMove ? R.string.move_conflict_title : R.string.copy_conflict_title;
        int allTitle = isMove ? R.string.move_conflict_title_all : R.string.copy_conflict_title_all;
        Map<Object, File> plan = new LinkedHashMap<>();
        List<CopyConflictDialog.Conflict> conflicts = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof File f) {
                plan.put(f, null);
                File existing = new File(destinationFolder, f.getName());
                if (existsAt(destinationFolder, f, existing, useElevated)) {
                    conflicts.add(describeConflict(f, existing, destinationFolder, useElevated));
                } else {
                    plan.put(f, getUnusedDest(destinationFolder, f.getName(), useElevated));
                }
            } else if (item instanceof ZipEntryInfo z) {
                plan.put(z, null);
                File existing = new File(destinationFolder, z.getName());
                if (candidateExists(destinationFolder, z.getName(), existing, useElevated)) {
                    conflicts.add(describeZipConflict(z, existing, destinationFolder, useElevated));
                } else {
                    plan.put(z, getUnusedDest(destinationFolder, z.getName(), useElevated));
                }
            }
        }
        if (conflicts.isEmpty()) return plan;

        int bulkAction = -1;
        for (int i = 0; i < conflicts.size(); i++) {
            CopyConflictDialog.Conflict c = conflicts.get(i);
            int action;
            if (bulkAction >= 0) {
                action = bulkAction;
            } else {
                dismissActiveProgress();
                List<CopyConflictDialog.Conflict> remaining = conflicts.subList(i, conflicts.size());
                CopyConflictDialog.Decision decision = CopyConflictDialog.askConflict(
                        context, dialogUtil, singleTitle, allTitle, remaining,
                        () -> {
                            showActiveProgress(transferLabel(isMove, summarizeItems(items)),
                                    isMove ? R.string.op_moving : R.string.op_copying);
                            // Cached by the worker before planning; measuring a
                            // root-only tree here would block the UI thread.
                            applyMeasure(cachedMeasure(items));
                        });
                if (decision == null || decision.action == CopyConflictDialog.ACTION_CANCEL) return null;
                action = decision.action;
                if (decision.applyToAll) {
                    bulkAction = action;
                }
            }
            plan.put(c.source, resolveDestination(c, destinationFolder, action, useElevated));
        }
        return plan;
    }

    private String transferLabel(boolean isMove, String items) {
        return isMove ? context.rss.getString(R.string.moving_f, items)
                : context.rss.getString(R.string.copying, items);
    }

    private File resolveDestination(CopyConflictDialog.Conflict c, File destinationFolder,
                                    int action, boolean useElevated) {
        switch (action) {
            case CopyConflictDialog.ACTION_SKIP:
                return null;
            case CopyConflictDialog.ACTION_KEEP_BOTH:
                // Recompute so two conflicts in the same batch cannot both claim
                // the same numbered name.
                return new File(destinationFolder,
                        getDuplicateName(c.sourceName, destinationFolder, useElevated));
            case CopyConflictDialog.ACTION_REPLACE:
            default:
                return new File(destinationFolder, c.sourceName);
        }
    }

    private CopyConflictDialog.Conflict describeConflict(File source, File existing,
                                                         File destinationFolder, boolean useElevated) {
        long sourceSize = -1, sourceTime = 0;
        try {
            sourceSize = source.length();
        } catch (Exception ignored) {
        }
        try {
            sourceTime = source.lastModified();
        } catch (Exception ignored) {
        }
        long existingSize = -1, existingTime = 0;
        try {
            existingSize = existing.length();
        } catch (Exception ignored) {
        }
        try {
            existingTime = existing.lastModified();
        } catch (Exception ignored) {
        }
        return new CopyConflictDialog.Conflict(source, source.getName(), sourceSize, sourceTime,
                existing.getName(), existingSize, existingTime,
                getDuplicateName(source.getName(), destinationFolder, useElevated));
    }

    private CopyConflictDialog.Conflict describeZipConflict(ZipEntryInfo source, File existing,
                                                             File destinationFolder, boolean useElevated) {
        long sourceSize = -1, sourceTime = 0;
        try {
            sourceSize = source.getSize();
        } catch (Exception ignored) {
        }
        try {
            sourceTime = source.getLastModified();
        } catch (Exception ignored) {
        }
        long existingSize = -1, existingTime = 0;
        try {
            existingSize = existing.length();
        } catch (Exception ignored) {
        }
        try {
            existingTime = existing.lastModified();
        } catch (Exception ignored) {
        }
        return new CopyConflictDialog.Conflict(source, source.getName(), sourceSize, sourceTime,
                existing.getName(), existingSize, existingTime,
                getDuplicateName(source.getName(), destinationFolder, useElevated));
    }

    private boolean candidateExists(File destinationFolder, String name, File candidate, boolean useElevated) {
        try {
            if (candidate.exists()) return true;
        } catch (Exception ignored) {
        }
        if (useElevated) {
            try {
                return dirHasEntry(destinationFolder, name);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /** True when the natural destination name is already taken (or is the source itself). */
    private boolean existsAt(File destinationFolder, File source, File candidate, boolean useElevated) {
        if (isSameDirectory(source, destinationFolder)) return true;
        return candidateExists(destinationFolder, source.getName(), candidate, useElevated);
    }

    private boolean isSameDirectory(File file, File destinationFolder) {
        File parent = file.getParentFile();
        if (parent == null || destinationFolder == null) return false;
        try {
            return parent.getCanonicalPath().equals(destinationFolder.getCanonicalPath());
        } catch (IOException e) {
            return parent.getAbsolutePath().equals(destinationFolder.getAbsolutePath());
        }
    }

    private String getDuplicateName(String fileName, File destinationFolder) {
        return getDuplicateName(fileName, destinationFolder, AccessManager.fileOpsOn(context));
    }

    private String getDuplicateName(String fileName, File destinationFolder, boolean useElevated) {
        String base = FilenameUtils.getBaseName(fileName);
        String ext = FilenameUtils.getExtension(fileName);
        int i = 1;
        String candidate;
        do {
            candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
            i++;
        } while (useElevated ? dirHasEntry(destinationFolder, candidate)
                : new File(destinationFolder, candidate).exists());
        return candidate;
    }

    public boolean copyToZip(List items, File zipFile, String currentPath) throws IOException {
        final boolean isApk = zipFile.getName().endsWith(".apk");
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] proceed = {false};
        final CompressionLevel[] compressionLevel = new CompressionLevel[1];
        final int[] updateMode = {UPDATE_MODE_REPLACE_ALL};
        final boolean[] autosign = new boolean[1];

        dismissActiveProgress();
        context.handler.post(() -> {
            LinearLayout ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.dialog_add_to_zip, null);
            ll.<TextView>findViewById(R.id.addToZipText).setText(context.rss.getString(R.string.confirm_add_to_zip_f, summarizeItems(items), zipFile.getName()));

            AutoCompleteTextView compressLevelInput = ll.findViewById(R.id.compress_level);
            compressLevelInput.setText(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
            List<String> compressionLevels = new ArrayList<>();
            for (CompressionLevel cl : CompressionLevel.values()) compressionLevels.add(cl.name());
            compressLevelInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, compressionLevels));
            compressLevelInput.setOnItemClickListener((parent2, view1, position2, id1) -> settings.edit().putString("compressLevel", compressionLevels.get(position2)).apply());

            AutoCompleteTextView updateModeInput = ll.findViewById(R.id.update_mode);
            List<String> updateModes = new ArrayList<>(Arrays.asList(
                    context.rss.getString(R.string.replace_all),
                    context.rss.getString(R.string.update_and_replace),
                    context.rss.getString(R.string.skip_all)));
            updateModeInput.setText(updateModes.get(0));
            updateModeInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, updateModes));
            updateModeInput.setOnItemClickListener((parent2, view1, position2, id1) -> updateMode[0] = position2);

            View signRow = ll.findViewById(R.id.sign_row);
            if (isApk) {
                CheckBox autosignCb = ll.findViewById(R.id.autosign);
                autosignCb.setChecked(autosign[0] = settings.getBoolean("autosign", true));
                autosignCb.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", autosign[0] = isChecked).apply());
                ll.findViewById(R.id.sign_settings).setOnClickListener(context.uiHelper.showSignSettingsDialog());
            } else signRow.setVisibility(View.GONE);

            String add = context.rss.getString(R.string.add);
            AlertDialog dialog = dialogUtil.getDialogBuilder()
                    .setTitle(add)
                    .setView(ll)
                    .setPositiveButton(add, (d, w) -> {
                        String level = compressLevelInput.getText().toString();
                        if (level.isEmpty()) level = settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name());
                        compressionLevel[0] = CompressionLevel.valueOf(level);
                        proceed[0] = true;
                        latch.countDown();
                    })
                    .setNegativeButton(android.R.string.cancel, (d, w) -> latch.countDown())
                    .create();
            dialogUtil.styleAlertDialog(dialog);
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (!proceed[0]) return false;

        showActiveProgress(context.rss.getString(R.string.adding_to, summarizeItems(items), zipFile.getName()));

        performAddToZip(items, zipFile, currentPath, compressionLevel[0], updateMode[0]);

        if (isApk && autosign[0]) context.handler.post(() ->
                SignWrapper.requireAuth(context, sw -> {
                    activeProgress.setText(context.rss.getString(R.string.signing, zipFile.getName()));
                    new Thread(() -> {
                        try {
                            sw.signApk(zipFile);
                            dismissActiveProgress();
                        } catch (Exception e) {
                            dismissActiveProgress();
                            new ErrorUtil(context).showError(e);
                            return;
                        }
                        openZipFile(zipFile, currentPath);
                        context.offerOuterUpdate(zipFile, adapter.pane1);
                    }).start();
                }));
        else {
            openZipFile(zipFile, currentPath);
            context.offerOuterUpdate(zipFile, adapter.pane1);
        }
        return true;
    }

    private void performAddToZip(List items, File zipFile, String currentPath, CompressionLevel compressionLevel, int updateMode) throws IOException {
        ZipParameters zipParameters = new ZipParameters();
        zipParameters.setCompressionLevel(compressionLevel);
        if (compressionLevel == CompressionLevel.NO_COMPRESSION)
            zipParameters.setCompressionMethod(CompressionMethod.STORE);

        String targetDir = TextUtils.isEmpty(currentPath) ? ""
                : currentPath.replace('\\', '/').replaceAll("/+$", "") + "/";

        File bak = new File(zipFile.getParent(), zipFile.getName() + ".bak");
        if (UiPrefs.genBackup(context) && !bak.exists()) FileUtils.copyFile(zipFile, bak);
        File tempFileDir = null;
        try (ZipFile sourceZip = new ZipFile(zipFile)) {
            Map<String, Long> existingEntries = new LinkedHashMap<>();
            for (FileHeader fh : sourceZip.getFileHeaders()) existingEntries.put(fh.getFileName(), fh.getLastModifiedTime());
            Set<String> toRemove = new LinkedHashSet<>();
            // {file, entryName}; a null entryName marks a directory added under targetDir keeping its own name
            List<Object[]> namedAdds = new ArrayList<>();
            List<File> plainFilesToAdd = new ArrayList<>();
            List<String> emptyFolders = new ArrayList<>();

            if (items.get(0) instanceof File) {
                for (Object itemObj : items) {
                    File f = (File) itemObj;
                    String entryName = targetDir.isEmpty() ? f.getName() : targetDir + f.getName();
                    if (!shouldAdd(entryName, f.lastModified(), existingEntries, updateMode, toRemove)) continue;
                    if (f.isDirectory()) namedAdds.add(new Object[] {f, null});
                    else if (targetDir.isEmpty()) plainFilesToAdd.add(f);
                    else namedAdds.add(new Object[] {f, entryName});
                }
            } else {
                tempFileDir = new File(context.getCacheDir(), UUID.randomUUID().toString());
                tempFileDir.mkdirs();
                int counter = 0;
                ZipFile entrySource = null;
                try {
                List<FileHeader> sourceHeaders = new ArrayList<>();
                for (Object itemObj : items) {
                    ZipEntryInfo zipEntry = (ZipEntryInfo) itemObj;
                    if (zipEntry.getZipFile() == null || zipEntry.getFullPath() == null) continue;
                    if ("..".equals(zipEntry.getName())) continue;
                    // One handle for the whole batch: reopening the source
                    // archive per selected entry was slow and pointless.
                    if (entrySource == null || !sameArchive(entrySource, zipEntry.getZipFile())) {
                        if (entrySource != null) entrySource.close();
                        entrySource = new ZipFile(zipEntry.getZipFile());
                        sourceHeaders = new ArrayList<>(entrySource.getFileHeaders());
                    }
                    String fullPath = zipEntry.getFullPath();
                    boolean isDir = zipEntry.isDirectory()
                            || fullPath.endsWith("/")
                            // A folder row in the listing is synthesised from the
                            // entries below it, so the archive itself often has no
                            // header for it. Anything with children is a folder.
                            || hasDescendant(sourceHeaders, normalizeEntryPath(fullPath));
                    if (isDir) {
                        // A folder inside an archive must be walked entry by entry.
                        // Adding only the directory stub produced an empty folder,
                        // which read as "copy did nothing".
                        String dirPath = normalizeEntryPath(fullPath);
                        if (dirPath.isEmpty()) continue;
                        String dirEntryName = targetDir + zipEntry.getName();
                        boolean any = false;
                        for (FileHeader child : sourceHeaders) {
                            if (child.isDirectory()) continue;
                            String childName = normalizeEntryPath(child.getFileName());
                            if (!childName.startsWith(dirPath + "/")) continue;
                            String entryName = dirEntryName + "/" + childName.substring(dirPath.length() + 1);
                            if (!shouldAdd(entryName, child.getLastModifiedTime(), existingEntries, updateMode, toRemove))
                                continue;
                            namedAdds.add(new Object[]{stageZipEntry(entrySource, child, tempFileDir, counter++), entryName});
                            any = true;
                        }
                        if (!any) emptyFolders.add(dirEntryName);
                    } else {
                        FileHeader fh = entrySource.getFileHeader(fullPath);
                        if (fh == null) continue;
                        String entryName = targetDir + zipEntry.getName();
                        if (!shouldAdd(entryName, fh.getLastModifiedTime(), existingEntries, updateMode, toRemove))
                            continue;
                        namedAdds.add(new Object[]{stageZipEntry(entrySource, fh, tempFileDir, counter++), entryName});
                    }
                }
                } finally {
                    if (entrySource != null) entrySource.close();
                }
            }
            if (!toRemove.isEmpty()) sourceZip.removeFiles(new ArrayList<>(toRemove));

            if (!plainFilesToAdd.isEmpty()) sourceZip.addFiles(plainFilesToAdd, zipParameters);
            for (Object[] add : namedAdds) {
                File f = (File) add[0];
                ZipParameters params = new ZipParameters(zipParameters);
                if (add[1] == null) {
                    if (!targetDir.isEmpty())
                        params.setRootFolderNameInZip(targetDir.substring(0, targetDir.length() - 1));
                    sourceZip.addFolder(f, params);
                } else {
                    params.setFileNameInZip((String) add[1]);
                    sourceZip.addFile(f, params);
                }
            }
            addEmptyFolders(tempFileDir != null ? tempFileDir : context.getCacheDir(), sourceZip, emptyFolders);
        } finally {
            if (tempFileDir != null) Util.deleteDir(tempFileDir);
        }
    }

    private static boolean sameArchive(ZipFile open, File file) {
        try {
            return open.getFile() != null && open.getFile().equals(file);
        } catch (Exception e) {
            return false;
        }
    }

    /** True when at least one stored entry lives under {@code dirPath + "/"}. */
    private static boolean hasDescendant(List<FileHeader> headers, String dirPath) {
        if (dirPath.isEmpty()) return false;
        for (FileHeader fh : headers) {
            if (fh.isDirectory()) continue;
            if (normalizeEntryPath(fh.getFileName()).startsWith(dirPath + "/")) return true;
        }
        return false;
    }

    private static String normalizeEntryPath(String entryPath) {
        if (entryPath == null) return "";
        String name = entryPath.replace('\\', '/');
        while (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        return name;
    }

    private static File stageZipEntry(ZipFile sourceZip, FileHeader fh, File tempFileDir, int index)
            throws IOException {
        File tempFile = new File(tempFileDir, "entry_" + index);
        try (InputStream is = sourceZip.getInputStream(fh)) {
            FileUtils.copyFile(is, tempFile);
        }
        return tempFile;
    }

    /** Keeps genuinely empty folders copied out of an archive visible in the target. */
    private static void addEmptyFolders(File tempDir, ZipFile zip, List<String> folderPaths) {
        if (folderPaths.isEmpty() || tempDir == null) return;
        int n = 0;
        for (String path : folderPaths) {
            String clean = normalizeEntryPath(path);
            if (clean.isEmpty()) continue;
            int cut = clean.lastIndexOf('/');
            String leaf = cut >= 0 ? clean.substring(cut + 1) : clean;
            if (leaf.isEmpty()) continue;
            try {
                // zip4j can only add a directory it can see on disk, so a scratch
                // one is created with the wanted leaf name under its wanted parent.
                File container = new File(tempDir, "empty_" + (n++));
                if (!container.mkdirs()) continue;
                File scratch = new File(container, leaf);
                if (!scratch.mkdirs()) continue;
                ZipParameters params = new ZipParameters();
                if (cut > 0) params.setRootFolderNameInZip(clean.substring(0, cut));
                zip.addFolder(scratch, params);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean shouldAdd(String targetName, long sourceModified, Map<String, Long> existingEntries, int updateMode, Set<String> toRemove) {
        Long existingTime = null;
        for (Map.Entry<String, Long> e : existingEntries.entrySet()) {
            if (e.getKey().equals(targetName) || e.getKey().startsWith(targetName + "/")) {
                existingTime = e.getValue();
                break;
            }
        }
        switch (updateMode) {
            case UPDATE_MODE_SKIP_ALL:
                return existingTime == null;
            case UPDATE_MODE_UPDATE_AND_REPLACE:
                if (existingTime != null && sourceModified <= existingTime) return false;
                markExistingForRemoval(existingEntries.keySet(), targetName, toRemove);
                return true;
            default:
                markExistingForRemoval(existingEntries.keySet(), targetName, toRemove);
                return true;
        }
    }

    private void markExistingForRemoval(Set<String> existingNames, String targetName, Set<String> toRemove) {
        for (String name : existingNames)
            if (name.equals(targetName) || name.startsWith(targetName + "/")) toRemove.add(name);
    }

    private String summarizeItems(List<?> items) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(items.size(), 3);
        for (int i = 0; i < limit; i++) {
            Object o = items.get(i);
            sb.append(o instanceof File ? ((File) o).getName() : ((ZipEntryInfo) o).getName());
            if (i < limit - 1) sb.append(", ");
        }
        if (items.size() > limit) sb.append(" ").append(context.rss.getString(R.string.plus_n_more, items.size() - limit));
        return sb.toString();
    }

    private boolean copyFromZip(List<Object> items) throws IOException {
        File destinationFolder = adapter.pane1 ? context.pane2Folder : context.pane1Folder;
        MainFilesArrayAdapter otherPaneAdapter = (MainFilesArrayAdapter) ((RecyclerView) context
                .findViewById(adapter.pane1 ? R.id.listViewPane2 : R.id.listViewPane1)).getAdapter();
        boolean destIsZip = otherPaneAdapter != null && otherPaneAdapter.isInZip;
        if (destIsZip && !ArchiveBrowseUtil.isZipLike(destinationFolder)) {
            // Browsing a 7z/rar/tar: entries cannot be written back into it.
            Extensions.showMessage(context, R.string.archive_not_editable);
            return true;
        }
        if (destIsZip) {
            return copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            return copyToRegularFolder(items, destinationFolder);
        }
    }

    public void extractZipEntry(ZipEntryInfo zipEntry, File destinationFolder) throws IOException {
        extractZipEntry(zipEntry, destinationFolder, null);
    }

    public void extractZipEntry(ZipEntryInfo zipEntry, File destinationFolder, String targetName) throws IOException {
        String zipEntryPath = zipEntry.getFullPath();
        if (zipEntryPath == null) return;
        File zipFile = zipEntry.getZipFile();
        String entryName = (targetName != null && !targetName.isEmpty()) ? targetName : zipEntry.getName();
        // Entries are written under the display name only, so a file inside
        // "docs/" does not land in destination/docs/.
        if (zipEntry.isDirectory()) {
            String prefix = zipEntryPath.endsWith("/") ? zipEntryPath : zipEntryPath + "/";
            for (ArchiveBrowseUtil.RawEntry re : ArchiveBrowseUtil.listEntries(zipFile)) {
                String name = re.path();
                if (!name.startsWith(prefix) || re.isDirectory()) continue;
                File target = new File(destinationFolder, entryName + "/" + name.substring(prefix.length()));
                if (target.exists()) {
                    RecycleBin.moveToBin(context, target, "overwrite");
                }
                ArchiveBrowseUtil.extractEntry(zipFile, name, target);
            }
        } else {
            File target = new File(destinationFolder, entryName);
            if (target.exists()) {
                RecycleBin.moveToBin(context, target, "overwrite");
            }
            ArchiveBrowseUtil.extractEntry(zipFile, zipEntryPath, target);
        }
    }

    private void openZipFile(File zipFile, String path) {
        context.loadZipFolderInPane(zipFile, path != null ? path : "", !adapter.pane1, false);
    }

    public void deleteZipEntry(ZipEntryInfo... entryToDelete) throws IOException {
        File f = entryToDelete[0].getZipFile();
        if (!ArchiveBrowseUtil.isZipLike(f))
            throw new IOException("Entries can only be deleted inside zip archives");
        List<String> toDelete = new ArrayList<>();
        for (FileHeader fh : ZipCompat.listHeaders(f)) {
            String name = fh.getFileName().replace('\\', '/');
            for (ZipEntryInfo info : entryToDelete) {
                String target = info.getFullPath();
                if (target == null) continue;
                if (info.isDirectory() && !target.endsWith("/")) target += "/";
                if (name.equals(target) || (info.isDirectory() && name.startsWith(target))) {
                    toDelete.add(name);
                    break;
                }
            }
        }
        try (ZipFile zf = new ZipFile(f)) {
            zf.removeFiles(toDelete);
        }
        context.loadZipFolderInPane(f, adapter.currentZipPath, adapter.pane1, false);
    }

    public void extractArchive(File archive) {
        File parent = archive.getParentFile();
        String baseName = archive.getName();
        String folderName = baseName;
        if (baseName.endsWith(".tar.gz")) folderName = baseName.substring(0, baseName.length() - ".tar.gz".length());
        else if (baseName.endsWith(".tar.bz2")) folderName = baseName.substring(0, baseName.length() - ".tar.bz2".length());
        else if (baseName.endsWith(".tar.xz")) folderName = baseName.substring(0, baseName.length() - ".tar.xz".length());
        else folderName = baseName.substring(0, baseName.lastIndexOf('.'));
        File destDir = FileUtils.getUnusedFile(new File(parent, folderName));
        new Thread(() -> {
            char[] password = null;
            try {
                // Ask for a password BEFORE showing any progress UI, so
                // cancelling the prompt means nothing was ever started.
                boolean encrypted = RootStaging.needsStaging(context, archive)
                        ? ArchiveUtil.needsPassword(RootStaging.stageForRead(context, archive.getAbsolutePath()))
                        : ArchiveUtil.needsPassword(archive);
                if (encrypted) {
                    password = promptForPassword(false);
                    if (password == null) return;
                }
                destDir.mkdirs();
                // Root-only archives are unreadable to zip4j/tar readers:
                // stage a copy into cache first (binary-safe). Staging happens
                // before measuring so the totals describe the file we will
                // actually read.
                File readable = archive;
                File staged = null;
                if (RootStaging.needsStaging(context, archive)) {
                    staged = RootStaging.stageForRead(
                            context, archive.getAbsolutePath());
                    readable = staged;
                }
                ProgressManager pm = new ProgressManager(context, false);
                pm.showCount(true);
                int totalEntries = archiveEntryCount(readable);
                long totalBytes = archiveUncompressedSize(readable);
                pm.setOperation(R.string.op_extracting);
                pm.setFile(archive);
                pm.setPaths(archive, destDir);
                pm.setProgress(0, totalEntries);
                pm.setBytes(0L, totalBytes);
                pm.setText(context.rss.getString(R.string.extracting_to_folder, destDir.getName()));
                pm.show();
                FileUtils.TransferListener listener = pm.listener();
                try {
                    boolean keepTime = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("preserve_mtime", true);
                    while (true) {
                        try {
                            ArchiveUtil.extract(readable, destDir, keepTime, password, listener);
                            break;
                        } catch (Exception e) {
                            // Missing or wrong password: prompt and retry until it
                            // succeeds or the user cancels. On a retry the previous
                            // password was wrong â€” say so.
                            if (!ArchiveUtil.isPasswordError(e)) throw e;
                            password = promptForPassword(password != null);
                            if (password == null) {
                                pm.dismiss();
                                return;
                            }
                            // A retry restarts the byte count from zero.
                            pm.setBytes(0L, totalBytes);
                        }
                    }
                    pm.setProgress(totalEntries, totalEntries);
                    pm.setBytes(totalBytes, totalBytes);
                } finally {
                    if (staged != null) {
                        //noinspection ResultOfMethodCallIgnored
                        staged.delete();
                    }
                }
                pm.dismiss();
                context.handler.post(() -> context.highlightAndFocusItem(adapter.pane1, destDir.getName()));
            } catch (Exception e) {
                destDir.delete();
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    /** Entry count for the extraction progress bar; 1 when the archive cannot be read cheaply. */
    private static int archiveEntryCount(File archive) {
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".zip")) return Math.max(1, ZipCompat.listHeadersOrEmpty(archive).size());
            File[] kids = archive.listFiles();
            if (kids != null && kids.length > 0) return kids.length;
        } catch (Exception ignored) {
        }
        return 1;
    }

    /**
     * Total uncompressed size the extraction will write, so the dialog can show
     * a byte bar and a live rate. Falls back to the archive size when the entry
     * table is not readable cheaply (7z/rar/tar), which still gives a usable
     * denominator and a sensible "left" figure.
     */
    private static long archiveUncompressedSize(File archive) {
        String lower = archive.getName().toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".zip")) {
                long total = 0L;
                for (FileHeader fh : ZipCompat.listHeadersOrEmpty(archive)) {
                    if (fh.isDirectory()) continue;
                    long size = fh.getUncompressedSize();
                    if (size > 0) total += size;
                }
                if (total > 0) return total;
            }
        } catch (Exception ignored) {
        }
        try {
            return Math.max(0L, archive.length());
        } catch (Exception e) {
            return 0L;
        }
    }

    /** Shown on the background thread; blocks until dismissed. Null = cancel. */
    private char[] promptForPassword(boolean wrongRetry) {
        final char[][] result = new char[1][];
        final CountDownLatch latch = new CountDownLatch(1);
        context.handler.post(() -> {
            TextInputEditText input = new TextInputEditText(context);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (wrongRetry) input.setHint(R.string.wrong_unzip_password);
            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.password)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (d, w) -> result[0] = passwordOf(input))
                    .setNegativeButton(android.R.string.cancel, (d, w) -> result[0] = null)
                    .setOnDismissListener(d -> latch.countDown())
                    .show();
        });
        try {
            latch.await();
        } catch (InterruptedException ignored) {
        }
        return result[0];
    }

    private static char[] passwordOf(TextInputEditText input) {
        Editable text = input.getText();
        return text == null ? new char[0] : text.toString().toCharArray();
    }

}

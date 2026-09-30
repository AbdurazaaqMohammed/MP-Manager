package io.github.abdurazaaqmohammed.adapters.main;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.reandroid.apkeditor.Util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.VersionMap;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem;
import com.android.tools.smali.dexlib2.iface.ClassDef;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import io.github.abdurazaaqmohammed.arsc.ArscEditorPlusActivity;
import io.github.abdurazaaqmohammed.arsc.ArscEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DexMergeUtil;
import io.github.abdurazaaqmohammed.utils.DexStringUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;
import modder.hub.dexeditor.activity.DexEditorActivity;

public class FileOperationsHelper {

    public static final int UPDATE_MODE_REPLACE_ALL = 0;
    public static final int UPDATE_MODE_UPDATE_AND_REPLACE = 1;
    public static final int UPDATE_MODE_SKIP_ALL = 2;

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final MainFilesArrayAdapter adapter;
    private final DexTools dex;

    private volatile ProgressManager activeProgress;

    private interface IoOperation {
        void run() throws Exception;
    }

    public FileOperationsHelper(MainActivity context, DialogUtil dialogUtil, MainFilesArrayAdapter adapter) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.adapter = adapter;
        this.dex = new DexTools(context, dialogUtil, adapter.pane1, adapter::openWithForFile);
    }

    public void showDexOptionsDialog(File dexFile, File zipFile, String entryPath, String displayName) {
        dex.showDexOptionsDialog(dexFile, zipFile, entryPath, displayName);
    }

    public void handleZipEntryClick(ZipEntryInfo zipEntry) {
        dex.handleZipEntryClick(zipEntry);
    }

    private void showActiveProgress(String text) {
        dismissActiveProgress();
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(text);
        activeProgress = pm;
        pm.show();
    }

    private void dismissActiveProgress() {
        ProgressManager pm = activeProgress;
        if (pm != null) pm.dismiss();
    }

    private void runWithProgress(String text, IoOperation op) {
        showActiveProgress(text);
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

    public void copyItemsAsync(List<Object> items) {
        runWithProgress(context.rss.getString(R.string.copying, summarizeItems(items)), () -> copyMultiple(items));
    }

    public void copyAsync(Object item) {
        if (adapter.isMultiSelectMode() && !adapter.getSelectedFiles().isEmpty()) copyItemsAsync(adapter.getSelectedFiles());
        else copyItemsAsync(Collections.singletonList(item));
    }

    public void moveAsync(Object item) {
        runWithProgress(context.rss.getString(R.string.copying, item), () -> move(item));
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
        if (destIsZip) {
            if (!copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath)) return false;
            for (Object item : items) {
                if (item instanceof File) ((File) item).delete();
                else if (item instanceof ZipEntryInfo) deleteZipEntry((ZipEntryInfo) item);
            }
            return true;
        }

        boolean useElevated = AccessManager.fileOpsOn(context);

        for (Object item : items) {
            if (item instanceof File f) {
                File dest = getUnusedDest(destinationFolder, f.getName(), useElevated);
                if (useElevated) {
                    try {
                        if (f.isDirectory()) AccessManager.copyDir(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        else AccessManager.copyFile(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                        AccessManager.delete(context, f.getAbsolutePath(), true);
                        continue;
                    } catch (Exception e) {
                    }
                }
                if (ShizukuFileOps.involvesShizukuPath(f, destinationFolder) && ShizukuFileOps.shellMove(f, destinationFolder, dest.getName()))
                    continue;
                if (f.renameTo(dest)) continue;
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
                    FileUtils.copyFolder(f, dest);
                    syncDirTimes(f, dest);
                } else {
                    FileUtils.copyFile(f, dest);
                    AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                }
                if (copySize(f) != copySize(dest)) {
                    throw new IOException("Move failed, copy mismatch: " + f.getName());
                }
                deleteRecursive(f);
            } else if (item instanceof ZipEntryInfo) {
                extractZipEntry((ZipEntryInfo) item, destinationFolder);
            }
        }
        context.handler.post(() -> {
            adapter.clearSelection();
            context.loadFolderInPane(destinationFolder, !adapter.pane1);
        });
        return true;
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
            if (!AccessManager.exists(context, first.getAbsolutePath())) return first;
            String base = FilenameUtils.getBaseName(name);
            String ext = FilenameUtils.getExtension(name);
            for (int i = 1; i < 1000; i++) {
                String candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
                File c = new File(destDir, candidate);
                if (!AccessManager.exists(context, c.getAbsolutePath())) return c;
            }
        } catch (Exception ignored) {
        }
        return first;
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
        if (destIsZip) {
            return copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            return copyToRegularFolder(items, destinationFolder);
        }
    }

    private boolean copyToRegularFolder(List<Object> items, File destinationFolder) throws IOException {
        boolean useElevated = AccessManager.fileOpsOn(context);

        for (Object item : items) {
            if (item instanceof File f) {
                File dest = isSameDirectory(f, destinationFolder) ? promptForDuplicateName(f, destinationFolder) : getUnusedDest(destinationFolder, f.getName(), useElevated);
                if (dest == null || dest.equals(f)) continue;
                if (useElevated) {
                    try {
                        if (f.isDirectory()) AccessManager.copyDir(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        else AccessManager.copyFile(context, f.getAbsolutePath(), dest.getAbsolutePath(), true);
                        AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                        continue;
                    } catch (Exception e) {
                    }
                }
                if (ShizukuFileOps.involvesShizukuPath(f, destinationFolder) && ShizukuFileOps.shellCopy(f, destinationFolder, dest.getName()) != null)
                    continue;
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
                    FileUtils.copyFolder(f, dest);
                    syncDirTimes(f, dest);
                } else {
                    FileUtils.copyFile(f, dest);
                    AccessManager.preserveTime(context, f.getAbsolutePath(), dest.getAbsolutePath());
                }
            } else if (item instanceof ZipEntryInfo) {
                extractZipEntry((ZipEntryInfo) item, destinationFolder);
            }
        }
        context.handler.post(() -> context.loadFolderInPane(destinationFolder, !adapter.pane1));
        return true;
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
        String base = FilenameUtils.getBaseName(fileName);
        String ext = FilenameUtils.getExtension(fileName);
        boolean useElevated = false;
        try {
            useElevated = AccessManager.fileOpsOn(context);
        } catch (Exception ignored) {
        }
        int i = 1;
        String candidate;
        do {
            candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
            i++;
        } while (new File(destinationFolder, candidate).exists()
                || (useElevated && AccessManager.exists(context, new File(destinationFolder, candidate).getAbsolutePath())));
        return candidate;
    }

    private File promptForDuplicateName(File sourceFile, File destinationFolder) throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        final File[] result = new File[1];
        final String defaultName = getDuplicateName(sourceFile.getName(), destinationFolder);
        dismissActiveProgress();
        context.handler.post(() -> {
            View view = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
            EditText input = view.findViewById(R.id.m_et_edittext);
            input.setText(defaultName);
            input.setSelection(0, defaultName.length());
            input.requestFocus();
            AlertDialog.Builder builder = dialogUtil.getDialogBuilder()
                    .setTitle(context.rss.getString(R.string.enter_name_for_copy))
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String name = input.getText().toString().trim();
                        result[0] = new File(destinationFolder, name.isEmpty() ? defaultName : name);
                        latch.countDown();
                    })
                    .setNegativeButton(android.R.string.cancel, (d, w) -> latch.countDown());
            AlertDialog dialog = builder.create();
            dialogUtil.styleAlertDialog(dialog);
            dialog.setOnShowListener(d -> {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            });
            dialog.show();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        if (result[0] != null) showActiveProgress(context.rss.getString(R.string.copying, sourceFile));
        return result[0];
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
                        }
                    }).start();
                }));
        openZipFile(zipFile, currentPath);
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
        FileUtils.copyFile(zipFile, bak);
        File tempFileDir = null;
        try (ZipFile sourceZip = new ZipFile(zipFile)) {
            Map<String, Long> existingEntries = new LinkedHashMap<>();
            for (FileHeader fh : sourceZip.getFileHeaders()) existingEntries.put(fh.getFileName(), fh.getLastModifiedTime());

            Set<String> toRemove = new LinkedHashSet<>();
            // {file, entryName}; a null entryName marks a directory added under targetDir keeping its own name
            List<Object[]> namedAdds = new ArrayList<>();
            List<File> plainFilesToAdd = new ArrayList<>();

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
                for (Object itemObj : items) {
                    ZipEntryInfo zipEntry = (ZipEntryInfo) itemObj;
                    try (ZipFile sourceZipFile = new ZipFile(zipEntry.getZipFile())) {
                        FileHeader fh = sourceZipFile.getFileHeader(zipEntry.getFullPath());
                        if (fh != null && !fh.isDirectory()) {
                            String entryName = targetDir + zipEntry.getName();
                            if (!shouldAdd(entryName, fh.getLastModifiedTime(), existingEntries, updateMode, toRemove)) continue;
                            File tempFile = new File(tempFileDir, "entry_" + counter++);
                            try (InputStream is = sourceZipFile.getInputStream(fh)) {
                                FileUtils.copyFile(is, tempFile);
                            }
                            namedAdds.add(new Object[] {tempFile, entryName});
                        }
                    }
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
        } finally {
            if (tempFileDir != null) Util.deleteDir(tempFileDir);
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
        if (destIsZip) {
            return copyToZip(items, destinationFolder, otherPaneAdapter.currentZipPath);
        } else {
            return copyToRegularFolder(items, destinationFolder);
        }
    }

    public void extractZipEntry(ZipEntryInfo zipEntry, File destinationFolder) throws IOException {
        String destinationPath = destinationFolder.getPath();
        String zipEntryPath = zipEntry.getFullPath();
        if (zipEntryPath == null) return;
        try (ZipFile zf = new ZipFile(zipEntry.getZipFile())) {
            // zip4j preserves the entry's internal path when extracting, so a file inside
            // "docs/" would land in destination/docs/. Pass an explicit name to avoid that.
            if(zipEntry.isDirectory()) {
                String prefix = zipEntryPath.endsWith("/") ? zipEntryPath : zipEntryPath + "/";
                for(FileHeader fh : zf.getFileHeaders()) {
                    String name = fh.getFileName().replace('\\', '/');
                    if(!name.startsWith(prefix) || fh.isDirectory()) continue;
                    zf.extractFile(fh, destinationPath, zipEntry.getName() + "/" + name.substring(prefix.length()));
                }
            } else zf.extractFile(zf.getFileHeader(zipEntryPath), destinationPath, zipEntry.getName());
        }
    }

    private void openZipFile(File zipFile, String path) {
        context.loadZipFolderInPane(zipFile, path != null ? path : "", !adapter.pane1, false);
    }

    public void deleteZipEntry(ZipEntryInfo... entryToDelete) throws IOException {
        File f = entryToDelete[0].getZipFile();
        List<String> toDelete = new ArrayList<>();
        try(ZipFile zf = new ZipFile(f)) {
            for(FileHeader fh : zf.getFileHeaders()) {
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
        destDir.mkdirs();
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(context.rss.getString(R.string.extracting_to_folder, destDir.getName()));
        pm.show();
        new Thread(() -> {
            try {
                // Root-only archives are unreadable to zip4j/tar readers:
                // stage a copy into cache first (binary-safe).
                File readable = archive;
                File staged = null;
                if (RootStaging.needsStaging(context, archive)) {
                    staged = RootStaging.stageForRead(
                            context, archive.getAbsolutePath());
                    readable = staged;
                }
                try {
                    boolean keepTime = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("preserve_mtime", true);
                    ArchiveUtil.extract(readable, destDir, keepTime);
                } finally {
                    if (staged != null) {
                        //noinspection ResultOfMethodCallIgnored
                        staged.delete();
                    }
                }
                pm.dismiss();
                context.handler.post(() -> context.loadFolderInPane(parent, adapter.pane1));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

}

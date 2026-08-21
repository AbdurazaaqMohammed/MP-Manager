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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.adapters.ZipEntryInfo;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import modder.hub.dexeditor.activity.DexEditorActivity;

public class FileOperationsHelper {

    public static final int UPDATE_MODE_REPLACE_ALL = 0;
    public static final int UPDATE_MODE_UPDATE_AND_REPLACE = 1;
    public static final int UPDATE_MODE_SKIP_ALL = 2;

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final MainFilesArrayAdapter adapter;

    private volatile ProgressManager activeProgress;

    private interface IoOperation {
        void run() throws Exception;
    }

    public FileOperationsHelper(MainActivity context, DialogUtil dialogUtil, MainFilesArrayAdapter adapter) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.adapter = adapter;
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
            } else {
                moveToDestination(itemsToMove);
            }
        } else if (adapter.isInZip) {
            if (!copyToDestination(Collections.singletonList(item))) return;
            deleteZipEntry((ZipEntryInfo) item);
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

        RootManager rm = RootManager.getInstance(context);
        boolean useRoot = rm.isRootFileOpsEnabled() && rm.isRootAvailable();

        for (Object item : items) {
            if (item instanceof File f) {
                File dest = FileUtils.getUnusedFile(destinationFolder, f.getName());
                if (useRoot) {
                    try {
                        if (f.isDirectory()) rm.copyDir(f.getAbsolutePath(), dest.getAbsolutePath());
                        else rm.copyFile(f.getAbsolutePath(), dest.getAbsolutePath());
                        rm.delete(f.getAbsolutePath());
                        continue;
                    } catch (Exception e) {
                    }
                }
                if (f.renameTo(dest)) continue;
                if (f.isDirectory()) {
                    dest.mkdir();
                    FileUtils.copyFolder(f, dest);
                } else
                    FileUtils.copyFile(f, dest);
                f.delete();
            } else if (item instanceof ZipEntryInfo) {
                extractZipEntry((ZipEntryInfo) item, destinationFolder);
            }
        }
        context.handler.post(() -> context.loadFolderInPane(destinationFolder, !adapter.pane1));
        return true;
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
        RootManager rm = RootManager.getInstance(context);
        boolean useRoot = rm.isRootFileOpsEnabled() && rm.isRootAvailable();

        for (Object item : items) {
            if (item instanceof File f) {
                File dest = isSameDirectory(f, destinationFolder) ? promptForDuplicateName(f, destinationFolder) : FileUtils.getUnusedFile(destinationFolder, f.getName());
                if (dest == null || dest.equals(f)) continue;
                if (useRoot) {
                    try {
                        if (f.isDirectory()) rm.copyDir(f.getAbsolutePath(), dest.getAbsolutePath());
                        else rm.copyFile(f.getAbsolutePath(), dest.getAbsolutePath());
                        continue;
                    } catch (Exception e) {
                    }
                }
                if (f.isDirectory()) {
                    dest.mkdir();
                    FileUtils.copyFolder(f, dest);
                } else
                    FileUtils.copyFile(f, dest);
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
        String base = org.apache.commons.io.FilenameUtils.getBaseName(fileName);
        String ext = org.apache.commons.io.FilenameUtils.getExtension(fileName);
        int i = 1;
        String candidate;
        do {
            candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
            i++;
        } while (new File(destinationFolder, candidate).exists());
        return candidate;
    }

    private File promptForDuplicateName(File sourceFile, File destinationFolder) throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        final File[] result = new File[1];
        final String defaultName = getDuplicateName(sourceFile.getName(), destinationFolder);
        dismissActiveProgress();
        context.handler.post(() -> {
            android.view.View view = android.view.LayoutInflater.from(context).inflate(R.layout.enter_name, null);
            android.widget.EditText input = view.findViewById(R.id.m_et_edittext);
            input.setText(defaultName);
            input.setSelection(0, defaultName.length());
            input.requestFocus();
            MaterialAlertDialogBuilder builder = dialogUtil.getDialogBuilder()
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
        try (ZipFile sourceZip = new ZipFile(zipFile)) {
            Map<String, Long> existingEntries = new LinkedHashMap<>();
            for (FileHeader fh : sourceZip.getFileHeaders()) existingEntries.put(fh.getFileName(), fh.getLastModifiedTime());

            Set<String> toRemove = new LinkedHashSet<>();
            List<File> filesToAdd = new ArrayList<>();

            if (items.get(0) instanceof File) {
                for (Object itemObj : items) {
                    File f = (File) itemObj;
                    if (shouldAdd(f.getName(), f.lastModified(), existingEntries, updateMode, toRemove))
                        filesToAdd.add(f);
                }
            } else {
                File tempFileDir = new File(context.getCacheDir(), UUID.randomUUID().toString());
                tempFileDir.mkdir();
                for (Object itemObj : items) {
                    ZipEntryInfo zipEntry = (ZipEntryInfo) itemObj;
                    try (ZipFile sourceZipFile = new ZipFile(zipEntry.getZipFile())) {
                        FileHeader fh = sourceZipFile.getFileHeader(zipEntry.getFullPath());
                        if (fh != null && !fh.isDirectory()) {
                            String fileName = fh.getFileName();
                            String newPath = TextUtils.isEmpty(currentPath) ? fileName : currentPath + File.separator + fileName;
                            String child = newPath.replace(File.separator, "U+1F602");
                            if (shouldAdd(child, fh.getLastModifiedTime(), existingEntries, updateMode, toRemove)) {
                                try (InputStream is = sourceZipFile.getInputStream(fh)) {
                                    FileUtils.copyFile(is, new File(tempFileDir, child));
                                    filesToAdd.add(new File(child));
                                }
                            }
                        }
                    }
                }
            }
            if (!toRemove.isEmpty()) sourceZip.removeFiles(new ArrayList<>(toRemove));
            if (!filesToAdd.isEmpty()) sourceZip.addFiles(filesToAdd, zipParameters);
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
        try (ZipFile zf = new ZipFile(zipEntry.getZipFile())) {
            if(zipEntry.isDirectory()) {
              for(FileHeader fh : zf.getFileHeaders()) if(fh.getFileName().startsWith(zipEntryPath)) zf.extractFile(fh, destinationPath);
            } else zf.extractFile(zf.getFileHeader(zipEntryPath), destinationPath);
        }
    }

    public void openZipFile(File zipFile, String path) {
        context.loadZipFolderInPane(zipFile, path != null ? path : "", !adapter.pane1, false);
    }

    public void deleteZipEntry(ZipEntryInfo... entryToDelete) throws IOException {
        File f = entryToDelete[0].getZipFile();
        List<String> toDelete = new ArrayList<>();
        try(ZipFile zf = new ZipFile(f)) {
            //zf.removeFiles(toDelete); // This is not deleting folders properly
            for(FileHeader fh : zf.getFileHeaders()) {
                String name = fh.getFileName();
                for (ZipEntryInfo info : entryToDelete) if(name.startsWith(info.getName())) toDelete.add(name);
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
                ArchiveUtil.extract(archive, destDir);
                pm.dismiss();
                context.handler.post(() -> context.loadFolderInPane(parent, adapter.pane1));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void handleZipEntryClick(ZipEntryInfo zipEntry) {
        File zipFile = zipEntry.getZipFile();
        String fullPath = zipEntry.getFullPath();
        if(zipEntry.isDirectory()) context.loadZipFolderInPane(zipFile, fullPath, adapter.pane1, false);
        else new Thread(() -> {
            try (ZipFile zf = new ZipFile(zipFile);
             InputStream is = zf.getInputStream(zf.getFileHeader(fullPath))) {
            final String name = zipEntry.getName();
            String outputDir = context.getCacheDir() + File.separator + UUID.randomUUID();
            File tempFolder = new File(outputDir);
            tempFolder.mkdir();
            File tempFile = new File(tempFolder, name);
            tempFile.createNewFile();
            if(name.endsWith(".dex")) {
                List<String> dexFiles = new ArrayList<>();
                    try {
                        FileHeader fh = zf.getFileHeader("classes.dex");
                        int i = 2;
                        while (fh != null) {
                            dexFiles.add(fh.getFileName());
                            //zf.extractFile(fh, outputDir);
                            fh = zf.getFileHeader("classes" + i + ".dex");
                            i++;
                        }
                        ProgressManager pm = new ProgressManager(context, false);
                        int finalI = i;
                        int size = dexFiles.size();
                        Thread t = new Thread(() -> {
                            try {
                                for (int j = 0; j < size; j++) {
                                    String df = dexFiles.get(j);
                                    if (pm.dialog != null && pm.dialog.isShowing()) {
                                        pm.setProgress(j, finalI);
                                        pm.setText(context.rss.getString(R.string.extracting, df));
                                    }
                                    zf.extractFile(zf.getFileHeader(df), outputDir);
                                }
                            } catch (ZipException e) {
                            throw new RuntimeException(e);
                        }});
                        t.start();
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
                        builder.setTitle("MultiDex");
                        CharSequence[] fileNames = new String[size];
                        for (int j = 0; j < size; j++) fileNames[j] = dexFiles.get(j);

                        boolean[] selectedItems = new boolean[size];
                        String classesNo = name.replace("classes", "").replace(".dex", "");
                        try {
                            int initialIndex = TextUtils.isEmpty(classesNo) ? 0 : (Integer.parseInt(classesNo) - 1);
                            if (initialIndex != -1) selectedItems[initialIndex] = true;
                        } catch (NumberFormatException ignored) { }
                        builder.setMultiChoiceItems(fileNames, selectedItems, (dialog, which, isChecked) -> selectedItems[which] = isChecked);

                        builder.setNeutralButton(context.rss.getString(android.R.string.selectAll), null).setPositiveButton(android.R.string.ok, (dialog, which) -> {
                           ArrayList<String> selectedPaths = new ArrayList<>();
                            for (int k = 0; k < selectedItems.length; k++) {
                                if (selectedItems[k]) selectedPaths.add(new File(tempFolder, dexFiles.get(k)).getPath());
                            }
                            if(t.isAlive()) {
                                pm.show();
                                try {
                                    t.join();
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            context.startActivityForResult(new Intent(context, DexEditorActivity.class)
                                        .putExtra("theme", context.theme)
                                        .putStringArrayListExtra("SelectedDexFiles", selectedPaths), 757);
                        });
                        builder.setNegativeButton(android.R.string.cancel, null);

                        context.handler.post(() -> {
                            AlertDialog dialog = builder.create();

                            dialog.setOnShowListener(dialogInterface -> {
                                Button invertButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                                invertButton.setOnClickListener(v -> {
                                    String buttonText = invertButton.getText().toString();

                                    if (buttonText.equals(context.rss.getString(android.R.string.selectAll))) {
                                        // First click: select all
                                        for (int i1 = 0; i1 < selectedItems.length; i1++) {
                                            selectedItems[i1] = true;
                                            dialog.getListView().setItemChecked(i1, true);
                                        }
                                        invertButton.setText(R.string.invert_selection);
                                    } else {
                                        // Subsequent clicks: invert selection
                                        for (int i1 = 0; i1 < selectedItems.length; i1++) {
                                            selectedItems[i1] = !selectedItems[i1];
                                            dialog.getListView().setItemChecked(i1, selectedItems[i1]);
                                        }
                                    }
                                });
                            });
                            dialog.show();
                        });
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
            }
            else if(name.endsWith(".xml")) {
                boolean isAxml = FileUtils.isAxml(is);
                if(isAxml) try(InputStream rssStream = zf.getInputStream(zf.getFileHeader("resources.arsc")); InputStream is2 = zf.getInputStream(zf.getFileHeader(fullPath))) {
                    //ResourceTableParser rtp = new ResourceTableParser(rssStream);
                    //List<ResEntry> resEntries = rtp.parse();
                    File tmpRss = new File(context.getCacheDir(), System.currentTimeMillis() + name);
                    FileUtils.copyFile(rssStream, tmpRss);
                    FileUtils.copyFile(is2, tempFile);

                    context.startActivityForResult(new Intent(context, TextEditorActivity.class)
                        .putExtra("rssPath", tmpRss.getPath())
                        //.putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2, resEntries).decodeAsString())
                        //.putExtra("resEntries", (Serializable) resEntries)
                        .putExtra("zf", zipFile.getPath())
                        .putExtra("zipEntryPath", fullPath)
                        .putExtra("axml", true)
                        .putExtra("path", tempFile.getPath()), 757);
                } else context.startActivity(new Intent(context, TextEditorActivity.class).putExtra("path", tempFile.getPath()));
            } else {
                FileUtils.copyFile(is, tempFile);
                Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", tempFile);
                Intent intent = new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, context.getContentResolver().getType(uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
        }
        }).start();
    }
}

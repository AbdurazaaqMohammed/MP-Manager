package io.github.abdurazaaqmohammed.features.files;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputEditText;
import com.reandroid.apkeditor.Util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.io.FilenameUtils;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileOperationsHelper;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RenameUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

/**
 * Rename/delete/compress entry dialogs extracted from MainFilesArrayAdapter.
 */
public class EntryDialogs {

    public interface State {
        Object[] values();

        Set<Integer> selectedPositions();

        boolean isInZip();

        String currentZipPath();

        void clearSelection();
    }

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final boolean pane1;
    private final FileOperationsHelper fileOps;
    private final State state;

    public EntryDialogs(MainActivity context, DialogUtil dialogUtil, boolean pane1,
            FileOperationsHelper fileOps, State state) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.pane1 = pane1;
        this.fileOps = fileOps;
        this.state = state;
    }

    public CharSequence getFilesToDisplay(boolean multi, int position) {
        Object[] values = state.values();
        if (multi) {
            StringBuilder sb = new StringBuilder();
            for (int i : state.selectedPositions())
                sb.append(',').append(state.isInZip() ? ((ZipEntryInfo) values[i]).getName() : ((File) values[i]).getName());
            return sb.deleteCharAt(0);
        }
        return state.isInZip() ? ((ZipEntryInfo) values[position]).getName() : ((File) values[position]).getName();
    }

    public void showRenameDialog(int position, File file, ZipEntryInfo entry, String fileName, boolean multi) {
        Object[] values = state.values();
        boolean isInZip = state.isInZip();
        if (multi) {
            RenameUtil.showMultiRenameDialog(context, state.selectedPositions(), isInZip, values, pane1, state.currentZipPath());
            return;
        }
        AlertDialog.Builder renameDialog = dialogUtil.getDialogBuilder();
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
                    if (isInZip) {
                        File zipFile = entry.getZipFile();
                        try (ZipFile zf = new ZipFile(zipFile)) {
                            String entryName = entry.getName();
                            if (entry.isDirectory()) {
                                Map<String, String> map = new HashMap<>();

                                for (FileHeader fh : zf.getFileHeaders()) {
                                    String fhFileName = fh.getFileName();
                                    if (fhFileName.startsWith(entryName)) map.put(fhFileName, fhFileName.replace(entryName, s));
                                }
                                if (!map.isEmpty()) zf.renameFiles(map);
                            } else zf.renameFile((entryName), s);
                            context.loadZipFolderInPane(zipFile, state.currentZipPath(), pane1, false);
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

    public void showDeleteDialog(int position, File file, ZipEntryInfo entry, boolean multi) {
        Object[] values = state.values();
        boolean isInZip = state.isInZip();
        ProgressManager pm = new ProgressManager(context, true);
        AlertDialog.Builder deleteDialog = dialogUtil.getDialogBuilder();
        CharSequence filesToDisplay = getFilesToDisplay(multi, position);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean[] sign = new boolean[1];
        File zipFile = isInZip ? entry.getZipFile() : null;
        if (isInZip && zipFile.getName().endsWith(".apk")) {
            LinearLayout ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.item_modified_dialog, null);
            ll.<TextView>findViewById(R.id.modifiedText).setText(context.rss.getString(R.string.confirm_delete_f, filesToDisplay));
            CheckBox autosign = ll.findViewById(R.id.autosign);
            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
            ll.findViewById(R.id.sign_settings).setOnClickListener(context.uiHelper.showSignSettingsDialog());
            deleteDialog.setView(ll);
        } else deleteDialog.setMessage(context.rss.getString(R.string.confirm_delete_f, filesToDisplay));
        deleteDialog.setTitle(context.rss.getString(R.string.warning)).setPositiveButton(context.rss.getString(R.string.yes), (dialog3, which) -> {
            SignWrapper[] wrapper = new SignWrapper[1];
            Runnable doDelete = () -> {
                pm.show();
                new Thread(() -> {
                    try {
                        if (isInZip) FileUtils.copyFile(zipFile, new File(zipFile.getParent(), zipFile.getName() + ".bak"));
                        boolean useElevatedForDelete = AccessManager.fileOpsOn(context);
                        if (multi) {
                            if (!isInZip) {
                                File selectedFile = null;
                                for (int i : state.selectedPositions()) {
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
                                        state.clearSelection();
                                        context.loadFolderInPane(finalSelectedFile.getParentFile(), pane1);
                                    });
                                } else context.handler.post(state::clearSelection);
                            } else {
                                List<ZipEntryInfo> selected = new ArrayList<>();
                                for (int i : state.selectedPositions()) selected.add((ZipEntryInfo) values[i]);
                                fileOps.deleteZipEntry(selected.toArray(new ZipEntryInfo[0]));
                                if (sign[0]) wrapper[0].signApk(zipFile);
                                context.handler.post(state::clearSelection);
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
                                state.clearSelection();
                                context.loadFolderInPane(file.getParentFile(), pane1);
                            });
                        } else {
                            fileOps.deleteZipEntry(entry);
                            if (sign[0]) wrapper[0].signApk(zipFile);
                            context.handler.post(state::clearSelection);
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
                    UIKit.dialog(context)
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

    public void showCompressDialog(File file, String fileName, boolean multi) {
        boolean isInZip = state.isInZip();
        if (isInZip) {
            return;
        }
        File parentFile2 = file.getParentFile();
        String parentFileName = parentFile2.getName();
        AlertDialog.Builder compressDialog = dialogUtil.getDialogBuilder();
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
        Object[] values = state.values();
        pm.show();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        new Thread(() -> {

                List<File> sources = new ArrayList<>();
                if (multi) {
                    for (int i : state.selectedPositions()) sources.add((File) values[i]);
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
}

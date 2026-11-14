package io.github.abdurazaaqmohammed.feature.files;

import io.github.abdurazaaqmohammed.privilege.impl.NativeRootManager;
import io.github.abdurazaaqmohammed.feature.apk.ApkIconHelper;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.abdurazaaqmohammed.feature.apk.SignWrapper;
import io.github.abdurazaaqmohammed.data.prefs.UiPrefs;

import io.github.abdurazaaqmohammed.util.io.TransferControl;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import java.io.IOException;
import java.util.Collections;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.reandroid.apkeditor.Util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.io.FilenameUtils;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.CopyConflictDialog;
import io.github.abdurazaaqmohammed.ui.main.FileOperationsHelper;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.util.archive.ArchiveUtil;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.io.FileTreeStats;
import io.github.abdurazaaqmohammed.util.io.DirectoryStats;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.io.RecycleBin;
import io.github.abdurazaaqmohammed.util.io.RenameHelper;
import io.github.abdurazaaqmohammed.util.io.RenameUtil;
import io.github.abdurazaaqmohammed.privilege.impl.RootManager;
import io.github.abdurazaaqmohammed.privilege.impl.RootStaging;
import io.github.abdurazaaqmohammed.util.io.SafFile;
import io.github.abdurazaaqmohammed.util.io.SafFs;
import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.abdurazaaqmohammed.util.io.TransferCancelledException;
import io.github.abdurazaaqmohammed.util.archive.ZipProgressPump;
import io.github.abdurazaaqmohammed.util.io.ZipTransferControl;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
            int displayed = 0;
            for (int i : state.selectedPositions()) {
                if (i < 0 || i >= values.length) continue;
                if (displayed++ >= 5) break;
                sb.append(',').append(state.isInZip() ? ((ZipEntryInfo) values[i]).getName() : ((File) values[i]).getName());
            }
            if (sb.length() == 0) return "";
            sb.deleteCharAt(0);
            int remaining = state.selectedPositions().size() - Math.min(displayed, 5);
            if (remaining > 0) sb.append(' ').append(context.getString(R.string.plus_n_more, remaining));
            return sb;
        }
        if (position >= 0 && position < values.length) {
            return state.isInZip() ? ((ZipEntryInfo) values[position]).getName() : ((File) values[position]).getName();
        }
        return "";
    }

    public void showRenameDialog(int position, File file, ZipEntryInfo entry, String fileName, boolean multi) {
        Object[] values = state.values();
        boolean isInZip = state.isInZip();
        if (multi) {
            RenameUtil.showMultiRenameDialog(context, state.selectedPositions(), isInZip, values, pane1, state.currentZipPath());
            return;
        }
        MaterialAlertDialogBuilder renameDialog = dialogUtil.getDialogBuilder();
        View rnm = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
        EditText renameInput = rnm.findViewById(R.id.m_et_edittext);
        renameInput.setText(fileName);
        boolean renameIsFile = isInZip ? (entry != null && !entry.isDirectory()) : (file != null && file.isFile());
        renameInput.setOnLongClickListener(v -> {
            RenameHelper.selectBaseName(renameInput, renameIsFile);
            return true;
        });
        // Renaming an entry rewrites the archive, which breaks the APK's
        // signature. Offer to re-sign when the container being browsed is an
        // APK, the same way the add-to-zip and modified-file dialogs do.
        final File zipFile = isInZip && entry != null ? entry.getZipFile() : null;
        final boolean zipIsApk = zipFile != null && ApkIconHelper.isApkArchiveName(zipFile.getName());
        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean[] sign = {zipIsApk && settings.getBoolean("autosign", true)};
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.addView(rnm, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (zipIsApk) {
            View signRow = LayoutInflater.from(context).inflate(R.layout.dialog_sign_option, container, false);
            MaterialCheckBox autosign = signRow.findViewById(R.id.autosign);
            autosign.setChecked(sign[0]);
            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sign[0] = isChecked;
                settings.edit().putBoolean("autosign", isChecked).apply();
            });
            signRow.findViewById(R.id.sign_settings).setOnClickListener(context.uiHelper.showSignSettingsDialog());
            container.addView(signRow);
        }
        renameDialog
                .setTitle(context.rss.getString(R.string.rename_1, fileName))
                .setView(container)
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
                    if (s.isEmpty()) {
                        Extensions.showMessage(context, context.rss.getString(R.string.failed_to_renamex, fileName));
                        return;
                    }
                    if (isInZip) {
                        String target = s;
                        final boolean signIt = zipIsApk && sign[0];
                        Runnable doRename = () -> RenameUtil.renameZipEntry(context, entry, target,
                                state.currentZipPath(), pane1,
                                () -> {
                                    context.flashPaneItem(pane1, target);
                                    context.offerOuterUpdate(entry.getZipFile(), pane1);
                                });
                        if (signIt) {
                            // The rename rewrites the archive; sign it afterwards
                            // so the APK stays installable.
                            SignWrapper.requireAuth(context, sw -> {
                                RenameUtil.renameZipEntry(context, entry, target,
                                        state.currentZipPath(), pane1,
                                        () -> {
                                            context.flashPaneItem(pane1, target);
                                            RenameUtil.signArchiveAfterEdit(context, zipFile, sw,
                                                    state.currentZipPath(), pane1,
                                                    () -> context.offerOuterUpdate(zipFile, pane1));
                                        });
                            });
                        } else doRename.run();
                    } else {
                        File ogFolder = file.getParentFile();
                        if (ogFolder == null) {
                            Extensions.showMessage(context, context.rss.getString(R.string.failed_to_renamex, fileName));
                            return;
                        }
                        File dest = new File(ogFolder, s);
                        if (dest.exists() && !RenameHelper.sameFile(file, dest)) {
                            RenameHelper.askSwapName(context, dialogUtil, file, dest, s, pane1, null);
                            return;
                        }
                        if (RenameHelper.renameFile(context, file, dest)) {
                            context.refreshPaneAfterChange(pane1, s);
                            context.flashPaneItem(pane1, s);
                        } else {
                            Extensions.showMessage(context,
                                    context.rss.getString(R.string.failed_to_renamex, fileName),
                                    FileUtils.describeWriteProblem(context, dest));
                        }
                    }
                });
        AlertDialog ad = renameDialog.create();
        ad.setOnShowListener(d -> {
            renameInput.requestFocus();
            RenameHelper.selectBaseName(renameInput, renameIsFile);
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(renameInput, InputMethodManager.SHOW_IMPLICIT);
        });
        try {
            if (ad.getWindow() != null) ad.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        } catch (Exception ignored) {}
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

    /**
     * Reports every entry as it is removed, so the dialog advances per file
     * instead of jumping from 0% when the whole tree is gone.
     *
     * @param counter shared across several roots so one dialog keeps one total
     */
    private FileUtils.TransferListener countingDeleter(ProgressManager pm, int[] counter, int total) {
        return new FileUtils.TransferListener() {
            @Override
            public void onFile(File source, File destination) {
                if (source == null) return;
                pm.setFile(source);
                pm.setPaths(source.getAbsolutePath(), null);
                counter[0]++;
                if (total > 0) pm.setProgress(Math.min(counter[0], total), total);
            }

            @Override
            public void onBytes(long count) {
                // Deleting frees bytes rather than moving them; the counter is
                // the meaningful unit here.
            }

            @Override
            public void checkpoint() {
                // Parks the delete while the user paused it, and unwinds it
                // when they cancelled.
                pm.control().checkpoint();
            }
        };
    }

    public void showDeleteDialog(int position, File file, ZipEntryInfo entry, boolean multi) {
        Object[] values = state.values();
        boolean isInZip = state.isInZip();
        ProgressManager pm = new ProgressManager(context, false);
        pm.showCount(true);
        pm.setControllable(true);
        pm.setOperation(R.string.op_deleting);
        MaterialAlertDialogBuilder deleteDialog = dialogUtil.getDialogBuilder();
        CharSequence filesToDisplay = getFilesToDisplay(multi, position);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean[] sign = new boolean[1];
        File zipFile = isInZip ? entry.getZipFile() : null;
        TextView customMsgView = null;
        String binMode = RecycleBin.mode(context);
        final CheckBox recycleCb;
        boolean targetInsideBinCalc = false;
        if (!isInZip) {
            File curDir = pane1 ? context.pane1Folder : context.pane2Folder;
            if (curDir != null && RecycleBin.isInsideBin(context, curDir)) {
                targetInsideBinCalc = true;
            } else if (multi) {
                for (int i : state.selectedPositions()) {
                    if (values[i] instanceof File f && RecycleBin.isInsideBin(context, f)) {
                        targetInsideBinCalc = true;
                        break;
                    }
                }
            } else if (file != null && RecycleBin.isInsideBin(context, file)) {
                targetInsideBinCalc = true;
            }
        }
        final boolean isTargetInsideBin = targetInsideBinCalc;
        if (isInZip && zipFile.getName().endsWith(".apk")) {
            recycleCb = null;
            LinearLayout ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.item_modified_dialog, null);
            customMsgView = ll.findViewById(R.id.modifiedText);
            customMsgView.setText(context.rss.getString(R.string.confirm_delete_f, filesToDisplay));
            CheckBox autosign = ll.findViewById(R.id.autosign);
            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
            ll.findViewById(R.id.sign_settings).setOnClickListener(context.uiHelper.showSignSettingsDialog());
            deleteDialog.setView(ll);
        } else if (!isInZip && !isTargetInsideBin && RecycleBin.MODE_ASK.equals(binMode)) {
            recycleCb = new CheckBox(context);
            recycleCb.setText(R.string.recycle_bin);
            recycleCb.setChecked(RecycleBin.lastChoiceEnabled(context));
            LinearLayout ll = new LinearLayout(context);
            ll.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (20 * context.getResources().getDisplayMetrics().density + 0.5f);
            ll.setPadding(pad, pad / 2, pad, 0);
            TextView msg = customMsgView = new TextView(context);
            msg.setText(context.rss.getString(R.string.confirm_delete_f, filesToDisplay));
            ll.addView(msg);
            ll.addView(recycleCb);
            deleteDialog.setView(ll);
        } else {
            recycleCb = null;
            deleteDialog.setMessage(context.rss.getString(R.string.confirm_delete_f, filesToDisplay));
        }
        deleteDialog.setTitle(context.rss.getString(R.string.warning)).setPositiveButton(context.rss.getString(R.string.yes), (dialog3, which) -> {
            SignWrapper[] wrapper = new SignWrapper[1];
            final boolean targetInsideBin = isTargetInsideBin;
            Runnable doDelete = () -> {
                pm.show();
                new Thread(() -> {
                    try {
                        if (isInZip) {
                            if (UiPrefs.genBackup(context)) FileUtils.backupOnce(zipFile);
                        }
                        boolean useElevatedForDelete = PrivilegeManager.Files.fileOpsOn(context);
                        // Recycle bin covers plain-file deletes (root/Shizuku
                        // paths included - the bin stages them through the
                        // elevated backend). In-archive deletes are permanent.
                        boolean toBin;
                        if (isInZip || targetInsideBin) toBin = false;
                        else if (RecycleBin.MODE_ALWAYS.equals(binMode)) toBin = true;
                        else if (RecycleBin.MODE_NEVER.equals(binMode)) toBin = false;
                        else {
                            toBin = recycleCb != null && recycleCb.isChecked();
                            RecycleBin.setLastChoice(context, toBin);
                        }
                        if (!isInZip) {
                            List<File> selectedFiles = new ArrayList<>();
                            if (multi) {
                                for (int i : state.selectedPositions()) {
                                    if (i > 0 && i < values.length && values[i] instanceof File f) selectedFiles.add(f);
                                }
                            } else if (file != null) {
                                selectedFiles.add(file);
                            }
                            deletePlainFiles(pm, selectedFiles, toBin, useElevatedForDelete, filesToDisplay);
                        } else if (multi) {
                            List<ZipEntryInfo> selected = new ArrayList<>();
                            for (int i : state.selectedPositions()) selected.add((ZipEntryInfo) values[i]);
                            pm.setProgress(0, Math.max(1, selected.size()));
                            if (!selected.isEmpty()) {
                                pm.setFile(selected.get(0).getName());
                                pm.setPaths(zipFile != null ? zipFile.getAbsolutePath() : null,
                                        selected.get(0).getFullPath());
                            }
                            fileOps.deleteZipEntry(selected.toArray(new ZipEntryInfo[0]));
                            pm.setProgress(Math.max(1, selected.size()), Math.max(1, selected.size()));
                            if (sign[0]) wrapper[0].signApk(zipFile);
                            File changedZip = zipFile != null ? zipFile
                                    : (selected.isEmpty() ? null : selected.get(0).getZipFile());
                            context.offerOuterUpdate(changedZip, pane1);
                            context.handler.post(state::clearSelection);
                        } else {
                            pm.setProgress(0, 1);
                            pm.setFile(entry.getName());
                            pm.setPaths(entry.getZipFile() != null ? entry.getZipFile().getAbsolutePath() : null,
                                    entry.getFullPath());
                            fileOps.deleteZipEntry(entry);
                            pm.setProgress(1, 1);
                            if (sign[0]) wrapper[0].signApk(zipFile);
                            context.offerOuterUpdate(zipFile, pane1);
                            context.handler.post(state::clearSelection);
                        }
                        pm.dismiss();
                        if (toBin) {
                            context.handler.post(() ->
                                    Extensions.showMessage(context, R.string.moved_to_recycle_bin));
                        }
                    } catch (Exception e) {
                        pm.dismiss();
                        // A cancelled delete is the user's own choice, not a
                        // failure worth reporting. What was already removed
                        // still has to leave the list, so the pane is refreshed.
                        context.handler.post(() -> {
                            state.clearSelection();
                            context.refreshPaneAfterChange(pane1, null);
                        });
                        if (!TransferCancelledException.isCancellation(e)) new ErrorUtil(context).showError(e);
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
        AlertDialog shownDelete = deleteDialog.create();
        dialogUtil.styleAlertDialog(shownDelete);
        appendDeleteSize(shownDelete, customMsgView, isInZip, multi, file, values);
    }

    private void deletePlainFiles(ProgressManager pm, List<File> files, boolean toBin,
                                  boolean elevated, CharSequence names) throws IOException {
        if (files.isEmpty()) return;
        for (File file : files) {
            if (!(file instanceof SafFile) && RootManager.isPathBlocked(file.getAbsolutePath()))
                throw new IOException("Blocked: refusing to delete critical path: " + file.getAbsolutePath());
        }
        pm.setText(context.getString(R.string.calculating));
        pm.setFile(files.get(0));
        pm.setPaths(files.get(0).getAbsolutePath(), null);
        int[] shares = new int[files.size()];
        long entries = 0;
        boolean exact = true;
        PrivilegeManager.Backend backend = PrivilegeManager.Files.active(context);
        List<String> paths = new ArrayList<>();
        for (File file : files) paths.add(file.getAbsolutePath());
        boolean allLocal = files.stream().noneMatch(file -> file instanceof SafFile);
        List<DirectoryStats> batch = allLocal && backend == PrivilegeManager.Backend.ROOT
                ? NativeRootManager.stats(context, paths, pm.control(), 120_000) : null;
        for (int i = 0; i < files.size(); i++) {
            pm.control().checkpoint();
            File file = files.get(i);
            DirectoryStats stats = batch != null ? batch.get(i) : FileTreeStats.read(context, file, pm.control(), 120_000);
            shares[i] = (int) Math.min(Integer.MAX_VALUE, stats.entries());
            entries += stats.entries();
            exact &= stats.complete();
        }
        final int total = exact && entries > 0 && entries <= Integer.MAX_VALUE ? (int) entries : 0;
        pm.setProgress(0, total);
        pm.setText(context.getString(R.string.deleting, names));
        int[] removed = {0};
        // Shizuku's service is one opaque unlink operation per root. Do not
        // pretend that its root counter is the native per-entry counter.
        boolean entryProgress = backend != PrivilegeManager.Backend.SHIZUKU;
        if (toBin) {
            for (int i = 0; i < files.size(); i++) {
                pm.control().checkpoint();
                File file = files.get(i);
                pm.setFile(file);
                pm.setPaths(file.getAbsolutePath(), null);
                if (!RecycleBin.moveToBin(context, file)) {
                    // Bin failure is not consent to permanently delete a file.
                    throw new IOException(context.getString(R.string.recycle_bin_move_failed, file.getName()));
                }
                removed[0] += shares[i];
                if (total > 0) pm.setProgress(Math.min(removed[0], total), total);
            }
        } else if (elevated && allLocal) {
            PrivilegeManager.Files.deleteTree(context, paths, true, (done, bytes) -> {
                if (entryProgress && total > 0) pm.setProgress(Math.min(done, total), total);
            }, pm.control());
        } else {
            for (File file : files) {
                pm.control().checkpoint();
                if (file instanceof SafFile saf) {
                    SafFs.delete(saf);
                } else if (!FileUtils.deleteTree(file, countingDeleter(pm, removed, total))) {
                    throw new IOException(context.getString(R.string.delete_failed, file.getName()));
                }
            }
        }
        // The delete APIs already verify unlink/rmdir/exit status. Checking
        // 5000 paths again via su was the long unexplained wait at 100%.
        if (total > 0) pm.setProgress(total, total);
        context.handler.post(() -> {
            state.clearSelection();
            context.refreshPaneAfterChange(pane1, null);
        });
    }

    public void showMoveDialog(int position, File file, ZipEntryInfo entry, boolean multi) {
        Object[] values = state.values();
        boolean isInZip = state.isInZip();
        File zipFile = isInZip && entry != null ? entry.getZipFile() : null;
        boolean isApk = isInZip && zipFile != null && ApkIconHelper.isApkArchiveName(zipFile.getName());

        if (!isInZip) {
            // Regular folder move: proceed directly via fileOps without unnecessary dialog
            Object item = multi ? null : (values != null && position >= 0 && position < values.length ? values[position] : file);
            fileOps.moveAsync(item);
            return;
        }

        // Inside archive (especially APK): show single confirmation dialog covering all selected files
        CharSequence filesToDisplay = getFilesToDisplay(multi, position);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean[] sign = new boolean[1];

        MaterialAlertDialogBuilder moveDialog = dialogUtil.getDialogBuilder();
        if (isApk) {
            LinearLayout ll = (LinearLayout) LayoutInflater.from(context).inflate(R.layout.item_modified_dialog, null);
            TextView customMsgView = ll.findViewById(R.id.modifiedText);
            customMsgView.setText(context.rss.getString(R.string.confirm_move_f, filesToDisplay));
            CheckBox autosign = ll.findViewById(R.id.autosign);
            autosign.setChecked(sign[0] = settings.getBoolean("autosign", true));
            autosign.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("autosign", sign[0] = isChecked).apply());
            ll.findViewById(R.id.sign_settings).setOnClickListener(context.uiHelper.showSignSettingsDialog());
            moveDialog.setView(ll);
        } else {
            moveDialog.setMessage(context.rss.getString(R.string.confirm_move_f, filesToDisplay));
        }

        moveDialog.setTitle(context.rss.getString(R.string.warning))
                .setPositiveButton(context.rss.getString(R.string.yes), (dialog, which) -> {
                    SignWrapper[] wrapper = new SignWrapper[1];
                    Runnable doMove = () -> {
                        Object item = multi ? null : (values != null && position >= 0 && position < values.length ? values[position] : entry);
                        fileOps.moveZipEntryWithSigning(item, zipFile, isApk && sign[0], wrapper[0]);
                    };
                    if (isApk && sign[0]) {
                        SignWrapper.requireAuth(context, sw -> {
                            wrapper[0] = sw;
                            doMove.run();
                        });
                    } else {
                        doMove.run();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);

        AlertDialog shownMove = moveDialog.create();
        dialogUtil.styleAlertDialog(shownMove);
        shownMove.show();
    }

    public void showRestoreDialog(int position, File file, boolean multi) {
        Object[] values = state.values();
        List<File> targets = new ArrayList<>();
        if (multi) {
            for (int i : state.selectedPositions()) {
                if (values != null && i >= 0 && i < values.length && values[i] instanceof File) {
                    File f = (File) values[i];
                    if (f.getName().toLowerCase().endsWith(".bak")) targets.add(f);
                }
            }
        } else if (file != null && file.getName().toLowerCase().endsWith(".bak")) {
            targets.add(file);
        }
        if (targets.isEmpty()) return;

        new Thread(() -> {
            boolean applyToAll = false;
            int rememberedAction = -1;
            boolean anyRenamed = false;
            boolean useElevated = PrivilegeManager.Files.fileOpsOn(context);

            for (File src : targets) {
                File parent = src.getParentFile();
                if (parent == null) continue;
                String srcName = src.getName();
                String destName = srcName.substring(0, srcName.length() - 4);
                File dest = new File(parent, destName);

                if (dest.exists() && !RenameHelper.sameFile(src, dest)) {
                    int action;
                    if (applyToAll && rememberedAction != -1) {
                        action = rememberedAction;
                    } else {
                        String suggestedName = getDuplicateRestoreName(destName, parent, useElevated);
                        CopyConflictDialog.Conflict conflict = new CopyConflictDialog.Conflict(
                                src, srcName, src.length(), src.lastModified(),
                                dest.getName(), dest.length(), dest.lastModified(),
                                suggestedName
                        );
                        CopyConflictDialog.Decision decision = CopyConflictDialog.askConflict(
                                context, dialogUtil,
                                R.string.copy_conflict_title, R.string.copy_conflict_title_all,
                                Collections.singletonList(conflict), null
                        );
                        action = decision.action;
                        if (decision.applyToAll) {
                            applyToAll = true;
                            rememberedAction = action;
                        }
                    }

                    if (action == CopyConflictDialog.ACTION_CANCEL) {
                        break;
                    } else if (action == CopyConflictDialog.ACTION_SKIP) {
                        continue;
                    } else if (action == CopyConflictDialog.ACTION_KEEP_BOTH) {
                        String newName = getDuplicateRestoreName(destName, parent, useElevated);
                        dest = new File(parent, newName);
                    } else if (action == CopyConflictDialog.ACTION_REPLACE) {
                        if (!dest.delete()) {
                            if (useElevated) {
                                try {
                                    PrivilegeManager.Files.delete(context, dest.getAbsolutePath(), true);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }

                if (RenameHelper.renameFile(context, src, dest)) {
                    anyRenamed = true;
                }
            }

            if (anyRenamed) {
                context.handler.post(() -> {
                    state.clearSelection();
                    context.refreshPaneAfterChange(pane1, null);
                });
            }
        }).start();
    }

    private String getDuplicateRestoreName(String fileName, File destinationFolder, boolean useElevated) {
        String base = FilenameUtils.getBaseName(fileName);
        String ext = FilenameUtils.getExtension(fileName);
        int i = 1;
        String candidate;
        do {
            candidate = ext.isEmpty() ? base + " (" + i + ")" : base + " (" + i + ")." + ext;
            i++;
        } while (dirHasEntry(destinationFolder, candidate, useElevated));
        return candidate;
    }

    private boolean dirHasEntry(File dir, String name, boolean useElevated) {
        File f = new File(dir, name);
        if (f.exists()) return true;
        if (useElevated) {
            try {
                return RootManager.getInstance(context).exists(f.getAbsolutePath());
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void appendDeleteSize(AlertDialog shown, TextView customMsg, boolean isInZip,
                                  boolean multi, File file, Object[] values) {
        if (isInZip) return;
        TextView target = customMsg != null ? customMsg
                : (shown == null ? null : shown.findViewById(android.R.id.message));
        if (target == null) return;
        List<File> targets = new ArrayList<>();
        if (multi) {
            for (int i : state.selectedPositions()) {
                if (i < 0 || i >= values.length) continue;
                if (values[i] instanceof File) targets.add((File) values[i]);
            }
        } else if (file != null) targets.add(file);
        if (targets.isEmpty()) return;
        CharSequence base = target.getText();
        target.setText(base + "\n" + context.rss.getString(R.string.delete_size_calc));
        TransferControl scanControl = new TransferControl();
        shown.setOnDismissListener(d -> scanControl.cancel());
        new Thread(() -> {
            try {
                long bytes = 0;
                long items = 0;
                long hidden = 0;
                List<String> paths = new ArrayList<>();
                for (File t : targets) paths.add(t.getAbsolutePath());
                boolean local = targets.stream().noneMatch(t -> t instanceof SafFile);
                List<DirectoryStats> batch = local && PrivilegeManager.Files.active(context) == PrivilegeManager.Backend.ROOT
                        ? NativeRootManager.stats(context, paths, scanControl, 10_000) : null;
                boolean complete = true;
                for (int i = 0; i < targets.size(); i++) {
                    scanControl.checkpoint();
                    File t = targets.get(i);
                    DirectoryStats stats = batch != null ? batch.get(i) : FileTreeStats.read(context, t, scanControl, 10_000);
                    bytes += stats.bytes();
                    items += Math.max(0, stats.entries() - (t.isDirectory() ? 1 : 0));
                    hidden += stats.hidden();
                    complete &= stats.complete();
                }
                String info = context.rss.getString(R.string.delete_size_info,
                        Formatter.formatFileSize(context, bytes), items, hidden)
                        + (complete ? "" : context.getString(R.string.property_partial));
                context.handler.post(() -> {
                    if (scanControl.isCancelled()) return;
                    try { target.setText(base + "\n" + info); } catch (Exception ignored) {}
                });
            } catch (TransferCancelledException ignored) {}
        }, "delete-confirmation-stats").start();
    }

    public void showCompressDialog(File file, String fileName, boolean multi) {
        boolean isInZip = state.isInZip();
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
        // Keep the filename extension in sync with the picked format, so
        // "name.zip" + switching to .7z does not produce "name.zip.7z".
        archiveFormatInput.setOnItemClickListener((parent2, view2, position2, id2) -> {
            String picked = archiveFormats[position2];
            String current = filenameEditText.getText().toString().trim();
            String stripped = current;
            for (String ext : archiveFormats) {
                if (stripped.toLowerCase(Locale.ENGLISH).endsWith(ext)) {
                    stripped = stripped.substring(0, stripped.length() - ext.length());
                    break;
                }
            }
            if (!stripped.isEmpty()) filenameEditText.setText(stripped + picked);
        });

        AutoCompleteTextView compressLevelInput = compressView.findViewById(R.id.compress_level);
        compressLevelInput.setText(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
        List<String> compressionLevels = new ArrayList<>();
        for (CompressionLevel cl : CompressionLevel.values()) compressionLevels.add(cl.name());
        compressLevelInput.setAdapter(new ArrayAdapter<>(context, R.layout.dropdownitem, compressionLevels));
        compressLevelInput.setOnItemClickListener((parent2, view1, position2, id1) -> settings.edit().putString("compressLevel", compressionLevels.get(position2)).apply());
        compressDialog.setView(compressView);
        compressDialog.setNegativeButton(context.rss.getString(android.R.string.cancel), null);
        ProgressManager pm = new ProgressManager(context, false);
        pm.showCount(true);
        pm.setOperation(R.string.op_compressing);
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

    /** Formats that compress exactly one stream (no container), so they only
     *  make sense for a single selected file. */
    private static boolean isSingleFileFormat(String format) {
        String f = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return f.equals(".gz") || f.equals(".bz2") || f.equals(".xz") || f.equals(".zst");
    }

    private void runCompress(File outputZip, String format, File file, String fileName, boolean multi, View compressView, ProgressManager pm) {
        Object[] values = state.values();
        // Single-file methods cannot represent a multi-selection; stop here
        // with a clear message instead of dying deep inside the archiver.
        if (isSingleFileFormat(format) && multi && state.selectedPositions().size() > 1) {
            Extensions.showMessage(context, context.rss.getString(R.string.compress_single_file_only, format));
            return;
        }
        // Compression honours the pause and cancel buttons.
        pm.setControllable(true);
        pm.show();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        new Thread(() -> {

                List<File> sources = new ArrayList<>();
                if (multi) {
                    for (int i : state.selectedPositions()) sources.add((File) values[i]);
                } else sources.add(file);
                // Sources may live under /data and be invisible to the app, so
                // the totals have to come from the shell in that case.
                FileUtils.Measure measuredTotal = null;
                if (PrivilegeManager.Files.fileOpsOn(context)) {
                    List<String> sourcePaths = new ArrayList<>(sources.size());
                    for (File s : sources) sourcePaths.add(s.getAbsolutePath());
                    FileUtils.MeasureSet set = PrivilegeManager.Files.measureSetElevated(context, sourcePaths);
                    if (set != null && set.total.files > 1) measuredTotal = set.total;
                }
                final FileUtils.Measure measure = measuredTotal != null
                        ? measuredTotal : FileUtils.measure(sources, 20000);
                final int totalSources = measure.files;
                final long totalBytes = measure.bytes;
                pm.setProgress(0, totalSources);
                pm.setBytes(0L, totalBytes);
                if (sources.size() == 1) {
                    pm.setFile(sources.get(0));
                    pm.setPaths(sources.get(0), outputZip);
                } else {
                    pm.setDestination(outputZip);
                }
                final FileUtils.TransferListener listener = pm.listener();

                boolean compressElevated = PrivilegeManager.Files.fileOpsOn(context);
                File compressStageTmp = null;
                List<File> readableSources = sources;
                File effectiveOutput = outputZip;
                boolean outputToRoot = false;
                boolean outputToSaf = false;
                // Provider documents and root-only files are staged into a
                // real folder either way: the archivers read plain Files.
                boolean anyNeedStage = false;
                for (File s : sources) {
                    if (RootStaging.needsStaging(context, s)) { anyNeedStage = true; break; }
                }
                if (compressElevated || anyNeedStage) {
                    if (anyNeedStage) {
                        try {
                            compressStageTmp = new File(context.getCacheDir(), "compress_stage_" + System.currentTimeMillis());
                            //noinspection ResultOfMethodCallIgnored
                            compressStageTmp.mkdirs();
                            readableSources = new ArrayList<>();
                            for (File s : sources) {
                                if (RootStaging.needsStaging(context, s)) {
                                    File stagedChild = new File(compressStageTmp, s.getName());
                                    if (s.isDirectory()) PrivilegeManager.Files.stageTree(context, s.getAbsolutePath(), stagedChild);
                                    else PrivilegeManager.Files.stageFile(context, s.getAbsolutePath(), stagedChild);
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
                            && PrivilegeManager.Files.exists(context, outParent.getAbsolutePath())) {
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
                if (!outputToRoot && SafFs.isSaf(outputZip.getParentFile())) {
                    // The archive is created in cache and streamed into the
                    // provider folder when done.
                    outputToSaf = true;
                    if (compressStageTmp == null) {
                        compressStageTmp = new File(context.getCacheDir(),
                                "compress_stage_" + System.currentTimeMillis());
                        //noinspection ResultOfMethodCallIgnored
                        compressStageTmp.mkdirs();
                    }
                    effectiveOutput = new File(compressStageTmp, outputZip.getName());
                }
                final List<File> finalSources = readableSources;
                final File finalOutput = effectiveOutput;
                final boolean finalToRoot = outputToRoot;
                final boolean finalToSaf = outputToSaf;
                final File finalStageTmp = compressStageTmp;

                if (format.equals(".zip")) {
                    ZipParameters zipParameters = new ZipParameters();
                    CompressionLevel compressionLevel = CompressionLevel.valueOf(settings.getString("compressLevel", CompressionLevel.NO_COMPRESSION.name()));
                    zipParameters.setCompressionLevel(compressionLevel);
                    if (compressionLevel == CompressionLevel.NO_COMPRESSION)
                        zipParameters.setCompressionMethod(CompressionMethod.STORE);
                    CharSequence pw = ((TextView) compressView.findViewById(R.id.pw_edittext)).getText();

                    ZipProgressPump pump = null;
                    ZipTransferControl zipControl = null;
                    try (ZipFile zf = new ZipFile(finalOutput)) {
                        if (!TextUtils.isEmpty(pw)) {
                            zipParameters.setEncryptFiles(true);
                            zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                            zf.setPassword(pw.toString().toCharArray());
                        }
                        // Attached before the pump starts: the bridge may replace
                        // zip4j's monitor, and the pump has to poll the one zip4j
                        // actually writes through or the dialog would freeze.
                        // It also lets the dialog pause and stop the compression
                        // mid-archive instead of only between sources.
                        zipControl = ZipTransferControl.attach(zf, pm.control());
                        // zip4j reports chunk-level progress through its monitor;
                        // the pump turns that into the live byte rate.
                        pump = new ZipProgressPump(zf.getProgressMonitor(), listener,
                                path -> listener.onFile(new File(path), null));
                        pump.start();
                        int compressed = 0;
                        for (File source : finalSources) {
                            pm.setFile(source);
                            pm.setPaths(source, outputZip);
                            if (source.isDirectory())
                                zf.addFolder(source, zipParameters);
                            else zf.addFile(source, zipParameters);
                            compressed += Math.max(1, FileUtils.countFilesForProgress(source, 20000));
                            pm.setProgress(Math.min(compressed, totalSources), totalSources);
                        }
                        if (pm.control().isCancelled()) throw new TransferCancelledException();
                        if (finalToRoot) PrivilegeManager.Files.uploadFile(context, finalOutput, outputZip.getAbsolutePath());
                        else if (finalToSaf) SafFs.copyFrom(finalOutput, (SafFile) outputZip.getParentFile(), "application/zip");
                        pm.setProgress(totalSources, totalSources);
                        pm.setBytes(totalBytes, totalBytes);
                        pm.dismiss();
                    } catch (Exception e) {
                        pm.dismiss();
                        // A cancelled compression leaves an unusable archive
                        // behind, so it is removed rather than kept.
                        if (TransferCancelledException.isCancellation(e) || pm.control().isCancelled()) {
                            if (finalOutput.exists()) finalOutput.delete();
                        } else {
                            new ErrorUtil(context).showError(e);
                        }
                    } finally {
                        if (pump != null) pump.stop();
                        if (zipControl != null) zipControl.detach();
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
                        ArchiveUtil.create(finalOutput, finalSources, listener);
                        if (pm.control().isCancelled()) throw new TransferCancelledException();
                        if (finalToRoot) PrivilegeManager.Files.uploadFile(context, finalOutput, outputZip.getAbsolutePath());
                        else if (finalToSaf) SafFs.copyFrom(finalOutput, (SafFile) outputZip.getParentFile(), null);
                        pm.setProgress(totalSources, totalSources);
                        pm.setBytes(totalBytes, totalBytes);
                        pm.dismiss();
                        context.handler.post(context::reloadCurrentFolder);
                    } catch (Exception e) {
                        pm.dismiss();
                        // A cancelled compression leaves an unusable archive
                        // behind, so it is removed rather than kept.
                        if (TransferCancelledException.isCancellation(e) || pm.control().isCancelled()) {
                            if (finalOutput.exists()) finalOutput.delete();
                        } else {
                            new ErrorUtil(context).showError(e);
                        }
                    } finally {
                        if (finalStageTmp != null) Util.deleteDir(finalStageTmp);
                    }
                }
        }).start();
    }
}

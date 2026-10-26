package io.github.abdurazaaqmohammed.features.files;

import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.checkbox.MaterialCheckBox;
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

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.main.FileOperationsHelper;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ApkIconHelper;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RecycleBin;
import io.github.abdurazaaqmohammed.utils.RenameHelper;
import io.github.abdurazaaqmohammed.utils.RenameUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
            for (int i : state.selectedPositions()) {
                if (i < 0 || i >= values.length) continue;
                sb.append(',').append(state.isInZip() ? ((ZipEntryInfo) values[i]).getName() : ((File) values[i]).getName());
            }
            return sb.length() == 0 ? "" : sb.deleteCharAt(0);
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
            if (ad.getWindow() != null) ad.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
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

    public void showDeleteDialog(int position, File file, ZipEntryInfo entry, boolean multi) {
        Object[] values = state.values();
        boolean isInZip = state.isInZip();
        ProgressManager pm = new ProgressManager(context, false);
        pm.showCount(true);
        MaterialAlertDialogBuilder deleteDialog = dialogUtil.getDialogBuilder();
        CharSequence filesToDisplay = getFilesToDisplay(multi, position);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean[] sign = new boolean[1];
        File zipFile = isInZip ? entry.getZipFile() : null;
        TextView customMsgView = null;
        String binMode = RecycleBin.mode(context);
        final CheckBox recycleCb;
        boolean isTargetInsideBin = false;
        if (!isInZip) {
            if (multi) {
                for (int i : state.selectedPositions()) {
                    if (values[i] instanceof File f && RecycleBin.isInsideBin(context, f)) {
                        isTargetInsideBin = true;
                        break;
                    }
                }
            } else if (file != null && RecycleBin.isInsideBin(context, file)) {
                isTargetInsideBin = true;
            }
        }
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
            TextView msg = new TextView(context);
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
                        boolean useElevatedForDelete = AccessManager.fileOpsOn(context);
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
                        if (multi) {
                            if (!isInZip) {
                                List<File> selectedFiles = new ArrayList<>();
                                for (int i : state.selectedPositions()) {
                                    File f = (File) values[i];
                                    if (f != null) selectedFiles.add(f);
                                }
                                int totalSelected = FileUtils.countFilesForProgress(selectedFiles, 20000);
                                pm.setProgress(0, totalSelected);
                                if (toBin) {
                                    int removed = 0;
                                    for (File f : selectedFiles) {
                                        if (!RecycleBin.moveToBin(context, f)) {
                                            if (f.isDirectory()) Util.deleteDir(f);
                                            else f.delete();
                                        }
                                        removed += FileUtils.countFilesForProgress(f, 20000);
                                        pm.setProgress(Math.min(removed, totalSelected), totalSelected);
                                    }
                                } else if (useElevatedForDelete) {
                                    // One shell round trip for the whole selection:
                                    // deleting item by item spent a su spawn per
                                    // file, all serialised behind the same lock.
                                    List<String> paths = new ArrayList<>(selectedFiles.size());
                                    for (File f : selectedFiles) paths.add(f.getAbsolutePath());
                                    try {
                                        AccessManager.deleteAll(context, paths, true);
                                    } catch (Exception ignored) {
                                        int removed = 0;
                                        for (File f : selectedFiles) {
                                            try {
                                                AccessManager.delete(context, f.getAbsolutePath(), true);
                                                removed += FileUtils.countFilesForProgress(f, 20000);
                                            } catch (Exception ignoredInner) {
                                                if (f.isDirectory()) Util.deleteDir(f);
                                                else f.delete();
                                                removed++;
                                            }
                                            pm.setProgress(Math.min(removed, totalSelected), totalSelected);
                                        }
                                    }
                                } else {
                                    int removed = 0;
                                    for (File f : selectedFiles) {
                                        if (f.isDirectory()) Util.deleteDir(f);
                                        else f.delete();
                                        removed += FileUtils.countFilesForProgress(f, 20000);
                                        pm.setProgress(Math.min(removed, totalSelected), totalSelected);
                                    }
                                }
                                pm.setProgress(totalSelected, totalSelected);
                                int failures = 0;
                                File firstFailure = null;
                                for (File f : selectedFiles) {
                                    boolean gone;
                                    try {
                                        gone = !f.exists() && !AccessManager.exists(context, f.getAbsolutePath());
                                    } catch (Exception e) {
                                        gone = true;
                                    }
                                    if (!gone) {
                                        failures++;
                                        if (firstFailure == null) firstFailure = f;
                                    }
                                }
                                if (failures > 0 && firstFailure != null) {
                                    final int failedCount = failures;
                                    final File failedFile = firstFailure;
                                    String details = FileUtils.describeWriteProblem(context, failedFile);
                                    context.handler.post(() -> Extensions.showMessage(context,
                                            context.rss.getString(R.string.delete_failed_x, failedCount),
                                            details));
                                }
                                context.handler.post(() -> {
                                    state.clearSelection();
                                    context.refreshPaneAfterChange(pane1, null);
                                });
                            } else {
                                List<ZipEntryInfo> selected = new ArrayList<>();
                                for (int i : state.selectedPositions()) selected.add((ZipEntryInfo) values[i]);
                                pm.setProgress(0, Math.max(1, selected.size()));
                                fileOps.deleteZipEntry(selected.toArray(new ZipEntryInfo[0]));
                                pm.setProgress(Math.max(1, selected.size()), Math.max(1, selected.size()));
                                if (sign[0]) wrapper[0].signApk(zipFile);
                                File changedZip = zipFile != null ? zipFile
                                        : (selected.isEmpty() ? null : selected.get(0).getZipFile());
                                context.offerOuterUpdate(changedZip, pane1);
                                context.handler.post(state::clearSelection);
                            }
                        } else if (!isInZip) {
                            int total = Math.max(1, FileUtils.countFilesForProgress(file, 20000));
                            pm.setProgress(0, total);
                            pm.setText(context.rss.getString(R.string.deleting, file.getName()));

                            if (toBin && RecycleBin.moveToBin(context, file)) {
                                // item is in the bin now
                            } else if (useElevatedForDelete) {
                                try {
                                    AccessManager.delete(context, file.getAbsolutePath(), true);
                                } catch (Exception e) {
                                    if (file.isDirectory()) Util.deleteDir(file);
                                    else file.delete();
                                }
                            } else {
                                if (file.isDirectory()) Util.deleteDir(file);
                                else file.delete();
                            }
                            boolean stillThere;
                            try {
                                stillThere = file.exists() || AccessManager.exists(context, file.getAbsolutePath());
                            } catch (Exception e) {
                                stillThere = false;
                            }
                            if (stillThere) {
                                String details = FileUtils.describeWriteProblem(context, file);
                                context.handler.post(() -> Extensions.showMessage(context,
                                        context.rss.getString(R.string.delete_failed, file.getName()),
                                        details));
                            }
                            pm.setProgress(total, total);
                            context.handler.post(() -> {
                                state.clearSelection();
                                context.refreshPaneAfterChange(pane1, null);
                            });
                        } else {
                            pm.setProgress(0, 1);
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
        AlertDialog shownDelete = deleteDialog.create();
        dialogUtil.styleAlertDialog(shownDelete);
        appendDeleteSize(shownDelete, customMsgView, isInZip, multi, file, values);
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
        new Thread(() -> {
            long bytes = 0;
            long items = 0;
            long hidden = 0;
            for (File t : targets) {
                long[] st = dirStats(t);
                bytes += st[0];
                items += st[1];
                hidden += st[2];
            }
            String info = context.rss.getString(R.string.delete_size_info,
                    android.text.format.Formatter.formatFileSize(context, bytes), items, hidden);
            context.handler.post(() -> {
                try {
                    target.setText(base + "\n" + info);
                } catch (Exception ignored) {}
            });
        }).start();
    }

    private long[] dirStats(File root) {
        long bytes = 0;
        long items = 0;
        long hidden = 0;
        try {
            if (root.isFile()) {
                return new long[]{root.length(), 1, root.getName().startsWith(".") ? 1 : 0};
            }
            java.util.ArrayDeque<File> stack = new java.util.ArrayDeque<>();
            java.util.HashSet<String> seen = new java.util.HashSet<>();
            stack.add(root);
            while (!stack.isEmpty()) {
                File d = stack.poll();
                if (d == null) continue;
                String canon;
                try {
                    canon = d.getCanonicalPath();
                } catch (Exception e) {
                    continue;
                }
                if (!seen.add(canon)) continue;
                File[] kids;
                try {
                    kids = d.listFiles();
                } catch (Exception e) {
                    kids = null;
                }
                if (kids == null) {
                    long[] du = duSize(d.getAbsolutePath());
                    if (du != null) bytes += du[0];
                    items++;
                    continue;
                }
                if (d != root) {
                    items++;
                    if (d.getName().startsWith(".")) hidden++;
                }
                for (File k : kids) {
                    if (k == null) continue;
                    try {
                        if (k.isFile()) {
                            bytes += k.length();
                            items++;
                            if (k.getName().startsWith(".")) hidden++;
                        } else {
                            stack.add(k);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return new long[]{bytes, items, hidden};
    }

    private long[] duSize(String path) {
        try {
            if (!AccessManager.fileOpsOn(context)) return null;
            String cmd = "du -sb " + RootManager.escapeShellArg(path);
            String out = null;
            if (AccessManager.active(context) == AccessManager.Backend.ROOT) {
                RootManager.ShellResult r = RootManager.getInstance(context).execute(cmd, 15);
                if (r != null && r.isSuccess()) out = r.output();
            } else if (io.github.abdurazaaqmohammed.utils.ShizukuManager.ready()) {
                io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.Result r =
                        io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell.exec(cmd, 15);
                if (r != null && r.success) out = r.stdout;
            }
            if (out == null) return null;
            return new long[]{Long.parseLong(out.trim().split("\\s+")[0])};
        } catch (Exception ignored) {
            return null;
        }
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
                final int totalSources = Math.max(1, FileUtils.countFilesForProgress(sources, 20000));
                pm.setProgress(0, totalSources);

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
                        int compressed = 0;
                        for (File source : finalSources) {
                            if (source.isDirectory())
                                zf.addFolder(source, zipParameters);
                            else zf.addFile(source, zipParameters);
                            compressed += Math.max(1, FileUtils.countFilesForProgress(source, 20000));
                            pm.setProgress(Math.min(compressed, totalSources), totalSources);
                        }
                        if (finalToRoot) AccessManager.uploadFile(context, finalOutput, outputZip.getAbsolutePath());
                        pm.setProgress(totalSources, totalSources);
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
                        pm.setProgress(totalSources, totalSources);
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

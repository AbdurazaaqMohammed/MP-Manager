package io.github.abdurazaaqmohammed.features.dex;

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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFileOps;
import io.github.abdurazaaqmohammed.adapters.FtpFilesArrayAdapter;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.arsc.ArscEditorPlusActivity;
import io.github.abdurazaaqmohammed.arsc.ArscEditorActivity;
import io.github.abdurazaaqmohammed.features.apk.translate.ArscTranslationModeActivity;
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
import io.github.abdurazaaqmohammed.features.apk.translate.DexTranslationModeActivity;
import modder.hub.dexeditor.activity.DexEditorActivity;

/**
 * DEX/ARSC toolkit extracted from FileOperationsHelper.
 */
public class DexTools {

    /** Opens a staged file with the Open-With dialog. */
    public interface OpenWith {
        void open(java.io.File file, String fileName);
    }

    private final io.github.abdurazaaqmohammed.MPManager.MainActivity context;
    private final io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil;
    private final boolean pane1;
    private final OpenWith openWith;

    public DexTools(io.github.abdurazaaqmohammed.MPManager.MainActivity context,
                      io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil, boolean pane1, OpenWith openWith) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.pane1 = pane1;
        this.openWith = openWith;
    }
    /**
     * Open-with for {@code resources.arsc}.
     *
     * <p>The option order mirrors MT: plain editor, editor++, translation mode, resource querier.
     * Translation mode is MT's locale-config workflow - copy a config, then translate it - and
     * lives in its own activity; the older per-resource translate dialog stays available as
     * "quick translate" so nothing that worked before stops working.
     */
    private void showArscOpenWith(File arscFile, File zipFile, String entryPath) {
        String[] options = {
                context.getString(R.string.arsc_plus),
                context.getString(R.string.arsc_editor),
                context.getString(R.string.translation_mode),
                context.getString(R.string.querier_title)};
        // A null mode means "not an ArscEditorPlusActivity mode"; the index tells which.
        String[] modes = {
                ArscEditorPlusActivity.MODE_PLUS,
                ArscEditorPlusActivity.MODE_EDITOR,
                null,
                ArscEditorPlusActivity.MODE_QUERIER};
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.open_with))
                .setSingleChoiceItems(options, -1, (dialog, which) -> {
                    dialog.dismiss();
                    Intent intent;
                    boolean needsResult = zipFile != null;
                    if (modes[which] == null) {
                        intent = new Intent(context, ArscTranslationModeActivity.class)
                                .putExtra("path", arscFile.getAbsolutePath())
                                .putExtra("apkPath", zipFile == null ? null : zipFile.getAbsolutePath())
                                .putExtra("zipEntryPath", entryPath);
                    } else {
                        // Simple MT-style "ARSC Editor" lives in its own activity;
                        // Plus and Querier stay in ArscEditorPlusActivity.
                        Class<?> target = ArscEditorPlusActivity.MODE_EDITOR.equals(modes[which])
                                ? ArscEditorActivity.class
                                : ArscEditorPlusActivity.class;
                        intent = new Intent(context, target)
                                .putExtra("path", arscFile.getAbsolutePath())
                                .putExtra("apkPath", zipFile == null ? null : zipFile.getAbsolutePath())
                                .putExtra("zipEntryPath", entryPath)
                                .putExtra("arscMode", modes[which]);
                    }
                    // Inside an archive the editor only edits the extracted copy and
                    // returns it via setResult(757); MainActivity then shows the
                    // "APK/ZIP updated" prompt and injects the file itself.
                    if (needsResult) context.startActivityForResult(intent, 757);
                    else context.startActivity(intent);
                }).create());
    }

    public void showDexOptionsDialog(File dexFile, File zipFile, String entryPath, String displayName) {
        String[] options = {
                context.rss.getString(R.string.dex_editor_plus),
                context.rss.getString(R.string.repair_dex),
                context.rss.getString(R.string.dex_properties),
                context.rss.getString(R.string.dex_to_smali),
                context.rss.getString(R.string.translation_mode),
                context.rss.getString(R.string.dex_replace_strings),
                context.rss.getString(R.string.dex_merge)};
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(displayName)
                .setSingleChoiceItems(options, -1, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == 1) {
                        repairDex(dexFile, zipFile);
                    } else if (which == 2) {
                        showDexProperties(dexFile);
                    } else if (which == 3) {
                        dexToSmali(dexFile, zipFile);
                    } else if (which == 5) {
                        showDexStringReplaceDialog(dexFile, zipFile);
                    } else if (which == 6) {
                        mergeDexOption(dexFile, zipFile);
                    } else if (which == 4) {
                        // Translation mode. An APK is usually split over many dex files and only
                        // some of them hold copy, so ask which ones to look at instead of taking
                        // the one that was tapped or loading all of them silently.
                        showMultiDexPicker(dexFile, zipFile);
                    } else if (which == 0 && zipFile != null) {
                        openDexPlusInZip(zipFile, dexFile.getName());
                    } else {
                        openDexPlusFiles(singleDexList(dexFile), null);
                    }
                }).create());
    }

    /**
     * Lists the APK's dex files with checkboxes, then opens the translation screen on the chosen
     * ones. Mirrors MT Manager: everything preselected, Select all / Cancel / Confirm.
     *
     * <p>For a loose .dex there is nothing to choose and the picker is skipped entirely.
     */
    private void showMultiDexPicker(File dexFile, File zipFile) {
        if (zipFile == null) {
            openDexTranslation(null, singleDexList(dexFile));
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zf.entries();
            while (e.hasMoreElements()) {
                String n = e.nextElement().getName();
                if (n.matches("classes(\\d*)\\.dex")) names.add(n);
            }
        } catch (Exception ex) {
            new ErrorUtil(context).showError(ex);
            return;
        }
        if (names.isEmpty()) {
            openDexTranslation(zipFile, singleDexList(dexFile));
            return;
        }
        // classes.dex first, then numeric order: the enumeration order is whatever the zip has.
        names.sort((a, b) -> Integer.compare(dexOrder(a), dexOrder(b)));

        boolean[] checked = new boolean[names.size()];
        Arrays.fill(checked, true);
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dex_multi_select)
                .setMultiChoiceItems(names.toArray(new String[0]), checked,
                        (d, which, isChecked) -> checked[which] = isChecked)
                .setNeutralButton(R.string.menu_select_all, (d, w) -> {
                    Arrays.fill(checked, true);
                    d.dismiss();
                    openDexTranslation(zipFile, dexPathList(zipFile, names));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    ArrayList<String> picked = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) picked.add(names.get(i));
                    if (picked.isEmpty()) {
                        Extensions.showMessage(context, R.string.dex_multi_none);
                        return;
                    }
                    openDexTranslation(zipFile, dexPathList(zipFile, picked));
                })
                .show();
    }

    /** classes.dex sorts first, then classes2, classes3 and so on. */
    private static int dexOrder(String name) {
        if (name.equals("classes.dex")) return 0;
        try {
            return Integer.parseInt(name.replaceAll("\\D+", ""));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** Copies the named entries out of the APK so the translation screen can read them as files. */
    private ArrayList<String> dexPathList(File zipFile, ArrayList<String> names) {
        File dir = new File(context.getFilesDir(), "dextrans_" + UUID.randomUUID());
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        ArrayList<String> out = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zipFile)) {
            io.github.abdurazaaqmohammed.utils.ZipPassword.apply(zf, zipFile);
            for (String name : names) {
                FileHeader fh = zf.getFileHeader(name);
                if (fh == null) continue;
                zf.extractFile(fh, dir.getAbsolutePath());
                File f = new File(dir, name);
                if (f.isFile()) out.add(f.getAbsolutePath());
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
        }
        return out;
    }

    private void openDexTranslation(File zipFile, ArrayList<String> dexPaths) {
        if (dexPaths == null || dexPaths.isEmpty()) return;
        Intent intent = new Intent(context, DexTranslationModeActivity.class)
                .putExtra("theme", context.theme)
                .putStringArrayListExtra("dex_paths", dexPaths);
        // The screen needs the APK to hand back through setResult(757); without it the edited dex
        // files would stay in the app's cache with no way back into the archive.
        if (zipFile != null) intent.putExtra("apkPath", zipFile.getAbsolutePath());
        context.startActivityForResult(intent, 757);
    }

    private static ArrayList<String> singleDexList(File dexFile) {
        ArrayList<String> single = new ArrayList<>();
        single.add(dexFile.getPath());
        return single;
    }

    private void openDexPlusFiles(ArrayList<String> paths, Integer openTab) {
        Intent intent = new Intent(context, DexEditorActivity.class)
                .putExtra("theme", context.theme)
                .putStringArrayListExtra("SelectedDexFiles", paths);
        if (openTab != null) intent.putExtra("openTab", openTab);
        context.startActivityForResult(intent, 757);
    }

    private static final Map<String, DexPreExtract> dexPreExtracts = new LinkedHashMap<>();

    private static class DexPreExtract {
        final File zipFile;
        final File outputDir;
        final List<String> dexNames = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        volatile String error;
        volatile int extracted;
        volatile int total;

        DexPreExtract(File zipFile, File outputDir) {
            this.zipFile = zipFile;
            this.outputDir = outputDir;
        }
    }

    private static synchronized DexPreExtract preExtractAllDex(Context ctx, File zipFile, boolean force) {
        String key = zipFile.getAbsolutePath();
        DexPreExtract existing = dexPreExtracts.get(key);
        if (!force && existing != null && existing.error == null) return existing;
        if (existing != null) {
            dexPreExtracts.remove(key);
            deleteQuietly(existing.outputDir);
        }
        DexPreExtract session = new DexPreExtract(zipFile, new File(ctx.getFilesDir(), "dexwork_" + UUID.randomUUID()));
        //noinspection ResultOfMethodCallIgnored
        session.outputDir.mkdirs();
        dexPreExtracts.put(key, session);
        new Thread(() -> {
            try (ZipFile zf = new ZipFile(zipFile)) {
                io.github.abdurazaaqmohammed.utils.ZipPassword.apply(zf, zipFile);
                FileHeader fh = zf.getFileHeader("classes.dex");
                int i = 2;
                while (fh != null) {
                    session.dexNames.add(fh.getFileName());
                    fh = zf.getFileHeader("classes" + i + ".dex");
                    i++;
                }
                session.total = session.dexNames.size();
                for (int j = 0; j < session.dexNames.size(); j++) {
                    String name = session.dexNames.get(j);
                    FileHeader header = zf.getFileHeader(name);
                    if (header == null) throw new IOException("Entry vanished: " + name);
                    zf.extractFile(header, session.outputDir.getAbsolutePath());
                    File out = new File(session.outputDir, name);
                    long expected = -1;
                    try {
                        expected = header.getUncompressedSize();
                    } catch (Exception ignored) {
                    }
                    if (!out.isFile() || (expected > 0 && out.length() != expected)) {
                        throw new IOException("Extract failed: " + out.getAbsolutePath());
                    }
                    session.extracted = j + 1;
                }
            } catch (Exception e) {
                session.error = String.valueOf(e.getMessage());
            } finally {
                session.done.countDown();
            }
        }).start();
        return session;
    }

    private static void deleteQuietly(File f) {
        try {
            if (f == null || !f.exists()) return;
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) for (File k : kids) deleteQuietly(k);
            }
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        } catch (Exception ignored) {
        }
    }

    private static List<String> listAllDexNames(File zipFile) throws IOException {
        List<String> dexNames = new ArrayList<>();
        try (ZipFile zf = new ZipFile(zipFile)) {
            FileHeader fh = zf.getFileHeader("classes.dex");
            int i = 2;
            while (fh != null) {
                dexNames.add(fh.getFileName());
                fh = zf.getFileHeader("classes" + i + ".dex");
                i++;
            }
        }
        return dexNames;
    }

    private void openDexPlusInZip(File zipFile, String preselected) {
        DexPreExtract session = preExtractAllDex(context, zipFile, false);
        List<String> dexFiles;
        try {
            dexFiles = listAllDexNames(zipFile);
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        if (dexFiles.isEmpty()) {
            Extensions.showMessage(context, R.string.no_files_found);
            return;
        }
        File tempFolder = session.outputDir;
        List<String> dexNames = new ArrayList<>(dexFiles);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(context.rss.getString(R.string.fo_multidex));
        CharSequence[] fileNames = new CharSequence[dexNames.size()];
        for (int j = 0; j < dexNames.size(); j++) fileNames[j] = dexNames.get(j);
        boolean[] selectedItems = new boolean[dexNames.size()];
        String classesNo = preselected == null ? "" : preselected.replace("classes", "").replace(".dex", "");
        try {
            int initialIndex = TextUtils.isEmpty(classesNo) ? 0 : (Integer.parseInt(classesNo) - 1);
            if (initialIndex >= 0 && initialIndex < selectedItems.length) selectedItems[initialIndex] = true;
        } catch (NumberFormatException ignored) { }
        builder.setMultiChoiceItems(fileNames, selectedItems, (dialog, which, isChecked) -> selectedItems[which] = isChecked);
        builder.setNeutralButton(context.rss.getString(android.R.string.selectAll), null).setPositiveButton(android.R.string.ok, (dialog, which) -> {
            List<String> selectedNames = new ArrayList<>();
            for (int k = 0; k < selectedItems.length; k++) {
                if (selectedItems[k]) selectedNames.add(dexNames.get(k));
            }
            if (selectedNames.isEmpty()) return;
            DexPreExtract useSession = session;
            boolean allThere = useSession.error == null;
            if (allThere) {
                for (String n : selectedNames) {
                    if (!new File(useSession.outputDir, n).isFile()) {
                        allThere = false;
                        break;
                    }
                }
            }
            if (!allThere) useSession = preExtractAllDex(context, zipFile, true);
            final DexPreExtract waitSession = useSession;
            if (waitSession.done.getCount() == 0 && waitSession.error == null) {
                openExtractedDex(waitSession, selectedNames);
                return;
            }
            ProgressManager pm = new ProgressManager(context, false);
            pm.show();
            int total = Math.max(1, selectedNames.size());
            pm.setProgress(0, total);
            pm.setText(context.rss.getString(R.string.extracting, selectedNames.get(0)));
            new Thread(() -> {
                while (waitSession.done.getCount() > 0) {
                    int done = Math.min(waitSession.extracted, total);
                    context.handler.post(() -> {
                        pm.setProgress(done, total);
                        pm.setText(context.rss.getString(R.string.extracting, done + "/" + total));
                    });
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
                context.handler.post(() -> {
                    pm.dismiss();
                    if (waitSession.error != null) {
                        new ErrorUtil(context).showError(new Exception(waitSession.error));
                        return;
                    }
                    openExtractedDex(waitSession, selectedNames);
                });
            }).start();
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            Button invertButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            invertButton.setOnClickListener(v -> {
                String buttonText = invertButton.getText().toString();
                if (buttonText.equals(context.rss.getString(android.R.string.selectAll))) {
                    for (int i1 = 0; i1 < selectedItems.length; i1++) {
                        selectedItems[i1] = true;
                        dialog.getListView().setItemChecked(i1, true);
                    }
                    invertButton.setText(R.string.invert_selection);
                } else {
                    for (int i1 = 0; i1 < selectedItems.length; i1++) {
                        selectedItems[i1] = !selectedItems[i1];
                        dialog.getListView().setItemChecked(i1, selectedItems[i1]);
                    }
                }
            });
        });
        dialog.show();
    }

    private void openExtractedDex(DexPreExtract session, List<String> selectedNames) {
        ArrayList<String> selectedPaths = new ArrayList<>();
        String missing = null;
        for (String n : selectedNames) {
            File f = new File(session.outputDir, n);
            if (f.isFile() && f.length() > 0) selectedPaths.add(f.getPath());
            else if (missing == null) missing = f.getAbsolutePath();
        }
        if (selectedPaths.isEmpty()) {
            Extensions.showMessage(context, missing != null ? missing : context.rss.getString(R.string.file_no_longer_available));
            return;
        }
        openDexPlusFiles(selectedPaths, null);
    }

    private void repairDex(File dexFile, File zipFile) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                if (zipFile == null) {
                    File bak = new File(dexFile.getParent(), dexFile.getName() + ".bak");
                    FileUtils.copyFile(dexFile, bak);
                }
                DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, null);
                DexFileFactory.writeDexFile(dexFile.getAbsolutePath(), dex);
                pm.dismiss();
                if (zipFile != null) {
                    context.handler.post(() -> context.handleModifiedFileResult(Uri.fromFile(dexFile)));
                } else {
                    boolean staged = underCacheDir(dexFile.getParentFile());
                    context.handler.post(() -> {
                        Extensions.showMessage(context, context.rss.getString(R.string.repaired_to,
                                staged ? dexFile.getAbsolutePath() : dexFile.getName()));
                        if (!staged) context.loadFolderInPane(dexFile.getParentFile(), pane1);
                    });
                }
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showDexProperties(File dexFile) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                byte[] head = new byte[64];
                try (FileInputStream fis = new FileInputStream(dexFile)) {
                    int n = fis.read(head);
                    if (n < 32) throw new IOException(context.rss.getString(R.string.dex_not_dex));
                }
                String version = new String(head, 4, 3, StandardCharsets.US_ASCII);
                int api;
                try {
                    api = VersionMap.mapDexVersionToApi(Integer.parseInt(version));
                } catch (Exception e) {
                    api = -1;
                }
                long checksum = ((head[8] & 0xFFL) | ((head[9] & 0xFFL) << 8) | ((head[10] & 0xFFL) << 16) | ((head[11] & 0xFFL) << 24));
                StringBuilder sig = new StringBuilder();
                for (int i = 12; i < 32; i++) sig.append(String.format(Locale.US, "%02x", head[i]));
                DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, null);
                int strings = dex.getStringReferences().size();
                int types = dex.getTypeReferences().size();
                int classes = dex.getClasses().size();
                int methods = 0;
                int fields = 0;
                for (ClassDef c : dex.getClasses()) {
                    if (c instanceof DexBackedClassDef bc) {
                        for (Object ignored : bc.getMethods()) methods++;
                        for (Object ignored : bc.getFields()) fields++;
                    }
                }
                String info = "Version: dex " + version + (api > 0 ? " (API " + api + ")" : "")
                        + "\nSize: " + dexFile.length() + " bytes"
                        + "\nChecksum: " + String.format(Locale.US, "%08x", checksum)
                        + "\nSignature: " + sig
                        + "\nStrings: " + strings
                        + "\nTypes: " + types
                        + "\nClasses: " + classes
                        + "\nMethods: " + methods
                        + "\nFields: " + fields;
                pm.dismiss();
                String title = dexFile.getName();
                context.handler.post(() -> dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(title)
                        .setMessage(info)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void dexToSmali(File dexFile, File zipFile) {
        File base = zipFile != null ? zipFile.getParentFile() : dexFile.getParentFile();
        String baseName = (zipFile != null ? zipFile.getName() : dexFile.getName()).replaceFirst("\\.[^.]+$", "");
        File outDir = FileUtils.getUnusedFile(new File(base, baseName + "_smali"));
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                byte[] bytes;
                try (FileInputStream fis = new FileInputStream(dexFile);
                     ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
                    bytes = bos.toByteArray();
                }
                int version = HeaderItem.getVersion(bytes, 0);
                int api = VersionMap.mapDexVersionToApi(version);
                BaksmaliOptions options = new BaksmaliOptions();
                options.apiLevel = api;
                DexBackedDexFile dex = new DexBackedDexFile(Opcodes.forApi(api), bytes);
                Baksmali.disassembleDexFile(dex, outDir, Math.max(1, Runtime.getRuntime().availableProcessors()), options);
                pm.dismiss();
                boolean staged = underCacheDir(outDir.getParentFile());
                context.handler.post(() -> {
                    Extensions.showMessage(context, context.rss.getString(R.string.smali_saved_to,
                            staged ? outDir.getAbsolutePath() : outDir.getName()));
                    if (!staged) context.loadFolderInPane(outDir.getParentFile(), pane1);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    public void handleZipEntryClick(ZipEntryInfo zipEntry) {
        File zipFile = zipEntry.getZipFile();
        String fullPath = zipEntry.getFullPath();
        if (zipEntry.isDirectory()) {
            context.loadZipFolderInPane(zipFile, fullPath, pane1, false);
        } else if (io.github.abdurazaaqmohammed.utils.ArchiveLister.isNonZipBrowsableName(zipFile.getName())) {
            new Thread(() -> {
                try {
                    handleNonZipEntryClick(zipFile, zipEntry);
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            }).start();
        } else new Thread(() -> {
            try (ZipFile zf = new ZipFile(zipFile)) {
            FileHeader entryHeader = zf.getFileHeader(fullPath);
            if (entryHeader == null) throw new IOException("Entry not found: " + fullPath);
            if (entryHeader.isEncrypted() && io.github.abdurazaaqmohammed.utils.ZipPassword.resolve(context, zf, zipFile, entryHeader) == null) return;
            try (InputStream is = zf.getInputStream(entryHeader)) {
            final String name = zipEntry.getName();
            String outputDir = context.getCacheDir() + File.separator + UUID.randomUUID();
            File tempFolder = new File(outputDir);
            tempFolder.mkdir();
            File tempFile = new File(tempFolder, name);
            tempFile.createNewFile();
            if(name.endsWith(".dex")) {
                FileUtils.copyFile(is, tempFile);
                preExtractAllDex(context, zipFile, false);
                context.handler.post(() -> showDexOptionsDialog(tempFile, zipFile, fullPath, name));
            } else if (name.endsWith(".xml")) {
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
                } else context.startActivityForResult(new Intent(context, TextEditorActivity.class)
                        .putExtra("zf", zipFile.getPath())
                        .putExtra("zipEntryPath", fullPath)
                        .putExtra("path", tempFile.getPath()), 757);
            } else if (name.equals("resources.arsc")) {
                FileUtils.copyFile(is, tempFile);
                context.handler.post(() -> showArscOpenWith(tempFile, zipFile, fullPath));
            } else {
                FileUtils.copyFile(is, tempFile);
                context.handler.post(() -> openWith.open(tempFile, name));
            }
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
        }
        }).start();
    }

    /**
     * Tap on a 7z/rar/tar entry: stage the file into the cache and reuse the
     * loose-file flows. Write-back happens through the normal result paths,
     * which refuse non-zip archives with archive_op_unsupported instead of
     * truncating them.
     */
    private void handleNonZipEntryClick(File zipFile, ZipEntryInfo zipEntry) throws Exception {
        final String name = zipEntry.getName();
        File tempFolder = new File(context.getCacheDir(), UUID.randomUUID().toString());
        //noinspection ResultOfMethodCallIgnored
        tempFolder.mkdirs();
        File tempFile = new File(tempFolder, name);
        if (!io.github.abdurazaaqmohammed.utils.ArchiveEntryIO.stage(context, zipEntry, tempFile)) return;
        if (name.endsWith(".dex")) {
            context.handler.post(() -> showDexOptionsDialog(tempFile, null, null, name));
        } else if (name.endsWith(".xml")) {
            boolean isAxml;
            try (InputStream is = new FileInputStream(tempFile)) {
                isAxml = FileUtils.isAxml(is);
            }
            String rssPath = null;
            if (isAxml) {
                File stagedRss = stageResourcesArsc(zipFile);
                if (stagedRss != null) rssPath = stagedRss.getPath();
            }
            // TextEditor switches on hasExtra("axml"), so the flag must only appear
            // for real binary XML; a false value would force the axml decoder.
            Intent intent = new Intent(context, TextEditorActivity.class)
                    .putExtra("path", tempFile.getPath())
                    .putExtra("zf", zipFile.getPath())
                    .putExtra("zipEntryPath", zipEntry.getFullPath());
            if (isAxml) intent.putExtra("axml", true);
            if (rssPath != null) intent.putExtra("rssPath", rssPath);
            context.startActivityForResult(intent, 757);
        } else if (name.equals("resources.arsc")) {
            context.handler.post(() -> showArscOpenWith(tempFile, zipFile, zipEntry.getFullPath()));
        } else {
            context.handler.post(() -> openWith.open(tempFile, name));
        }
    }

    /** Best effort: the entry list is usually cached from the pane listing, so this rarely prompts. */
    private File stageResourcesArsc(File zipFile) {
        try {
            List<ZipEntryInfo> flat = io.github.abdurazaaqmohammed.utils.ArchiveLister.listResolved(context, zipFile);
            if (flat == null) return null;
            for (ZipEntryInfo e : flat) {
                if (e.isDirectory() || !"resources.arsc".equals(e.getFullPath().replace('\\', '/'))) continue;
                File out = new File(context.getCacheDir(), "rss_" + System.currentTimeMillis() + ".arsc");
                return io.github.abdurazaaqmohammed.utils.ArchiveEntryIO.stage(context, e, out) ? out : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Staged-from-archive copies live under the cache; never navigate the pane into it. */
    private boolean underCacheDir(File dir) {
        if (dir == null) return false;
        try {
            return dir.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath() + File.separator);
        } catch (Exception e) {
            return dir.getAbsolutePath().startsWith(context.getCacheDir().getPath() + File.separator);
        }
    }

    private void showDexStringReplaceDialog(File dexFile, File zipFileOrNull) {
        EditText findInput = new EditText(context);
        findInput.setHint(context.rss.getString(R.string.find));
        findInput.setSingleLine(true);
        EditText replaceInput = new EditText(context);
        replaceInput.setHint(context.rss.getString(R.string.replace_with));
        replaceInput.setSingleLine(true);
        CheckBox matchCase = new CheckBox(context);
        matchCase.setText(context.rss.getString(R.string.match_case));
        matchCase.setChecked(true);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(findInput);
        layout.addView(replaceInput);
        layout.addView(matchCase);
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(dexFile.getName())
                .setView(layout)
                .setPositiveButton(context.rss.getString(R.string.replace), (d, w) -> {
                    String find = findInput.getText().toString();
                    String replacement = replaceInput.getText().toString();
                    boolean cs = matchCase.isChecked();
                    if (find.isEmpty()) {
                        Extensions.showMessage(context, context.rss.getString(R.string.fo_enter_find));
                        return;
                    }
                    runDexStringReplace(dexFile, zipFileOrNull, find, replacement, cs);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create());
    }

    private void runDexStringReplace(File dexFile, File zipFileOrNull, String find, String replacement, boolean matchCase) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                File tmpOut = File.createTempFile("dexstr", ".dex", context.getCacheDir());
                int count = DexStringUtil.replaceStrings(dexFile, tmpOut, find, replacement, matchCase);
                pm.dismiss();
                context.handler.post(() -> dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setMessage(context.rss.getString(R.string.fo_replacements_apply, count))
                        .setPositiveButton(context.rss.getString(R.string.apply), (d2, w2) -> applyDexStringReplace(dexFile, zipFileOrNull, tmpOut))
                        .setNegativeButton(android.R.string.cancel, (d2, w2) -> tmpOut.delete())
                        .create()));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void applyDexStringReplace(File dexFile, File zipFileOrNull, File tmpOut) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                if (zipFileOrNull != null) {
                    FileUtils.copyFile(tmpOut, dexFile);
                    tmpOut.delete();
                    pm.dismiss();
                    context.handler.post(() -> context.handleModifiedFileResult(Uri.fromFile(dexFile)));
                } else {
                    File bak = new File(dexFile.getParent(), dexFile.getName() + ".bak");
                    FileUtils.copyFile(dexFile, bak);
                    FileUtils.copyFile(tmpOut, dexFile);
                    tmpOut.delete();
                    pm.dismiss();
                    boolean staged = underCacheDir(dexFile.getParentFile());
                    context.handler.post(() -> {
                        Extensions.showMessage(context, context.rss.getString(R.string.fo_replaced,
                                staged ? dexFile.getAbsolutePath() : dexFile.getName()));
                        if (!staged) context.loadFolderInPane(dexFile.getParentFile(), pane1);
                    });
                }
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void mergeDexOption(File dexFile, File zipFile) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                if (zipFile != null) {
                    File tmpDir = new File(context.getCacheDir(), "dexmerge" + UUID.randomUUID());
                    tmpDir.mkdirs();
                    List<File> inputs = new ArrayList<>();
                    try (ZipFile zf = new ZipFile(zipFile)) {
                        List<String> names = new ArrayList<>();
                        for (FileHeader fh : zf.getFileHeaders()) {
                            String n = fh.getFileName();
                            if (n != null && n.matches("classes(\\d*)\\.dex")) {
                                names.add(n);
                            }
                        }
                        Collections.sort(names, (a, b) -> Integer.compare(dexNameNumber(a), dexNameNumber(b)));
                        if (names.size() > 20) {
                            names = names.subList(0, 20);
                        }
                        if (names.size() < 2) {
                            pm.dismiss();
                            context.handler.post(() -> Extensions.showMessage(context, context.rss.getString(R.string.fo_need_dex)));
                            return;
                        }
                        for (String n : names) {
                            File out0 = new File(tmpDir, n);
                            try (InputStream is = zf.getInputStream(zf.getFileHeader(n))) {
                                FileUtils.copyFile(is, out0);
                            }
                            inputs.add(out0);
                        }
                    }
                    int api = detectDexApi(inputs.get(0));
                    File outDir = new File(context.getCacheDir(), "dexmergeout" + UUID.randomUUID());
                    outDir.mkdirs();
                    File merged = new File(outDir, "classes_merged.dex");
                    DexMergeUtil.mergeDexFiles(inputs, merged, api);
                    pm.dismiss();
                    context.handler.post(() -> context.handleModifiedFileResult(Uri.fromFile(merged)));
                } else {
                    File dir = dexFile.getParentFile();
                    File[] found = dir.listFiles((d, name) -> name.matches("classes(\\d*)\\.dex"));
                    List<File> inputs = new ArrayList<>();
                    if (found != null) {
                        Arrays.sort(found, (a, b) -> Integer.compare(dexNameNumber(a.getName()), dexNameNumber(b.getName())));
                        for (int i = 0; i < found.length && inputs.size() < 20; i++) {
                            inputs.add(found[i]);
                        }
                    }
                    if (inputs.size() < 2) {
                        pm.dismiss();
                        context.handler.post(() -> Extensions.showMessage(context, context.getString(R.string.need_two_dex)));
                        return;
                    }
                    int api = detectDexApi(inputs.get(0));
                    File merged = FileUtils.getUnusedFile(new File(dir, "classes_merged.dex"));
                    DexMergeUtil.mergeDexFiles(inputs, merged, api);
                    pm.dismiss();
                    context.handler.post(() -> {
                        Extensions.showMessage(context, context.rss.getString(R.string.fo_merged_n, inputs.size()));
                        context.loadFolderInPane(dir, pane1);
                    });
                }
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private int detectDexApi(File dexFile) {
        try (FileInputStream fis = new FileInputStream(dexFile)) {
            byte[] magic = new byte[8];
            int read = 0;
            while (read < 8) {
                int n = fis.read(magic, read, 8 - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            if (read >= 7) {
                int version = HeaderItem.getVersion(magic, 0);
                int api = VersionMap.mapDexVersionToApi(version);
                if (api > 0) {
                    return api;
                }
            }
        } catch (Exception ignored) {
        }
        return 28;
    }

    private int dexNameNumber(String name) {
        if ("classes.dex".equals(name)) {
            return 1;
        }
        try {
            return Integer.parseInt(name.substring(7, name.length() - 4));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }
}
package io.github.abdurazaaqmohammed.feature.files;

import io.github.abdurazaaqmohammed.feature.apk.flutter.FlutterToolsDialog;
import io.github.abdurazaaqmohammed.feature.arm64.Arm64DisassemblerActivity;
import io.github.abdurazaaqmohammed.feature.dex.DexTools;
import io.github.abdurazaaqmohammed.feature.sqlite.SqliteViewerActivity;
import io.github.abdurazaaqmohammed.feature.system.SystemImageEngine;
import io.github.abdurazaaqmohammed.feature.system.SystemImageMountManager;
import io.github.abdurazaaqmohammed.feature.system.shell.CommandShellActivity;
import io.github.abdurazaaqmohammed.privilege.api.ShellSession;


import io.github.abdurazaaqmohammed.feature.apk.ApkIconHelper;
import io.github.abdurazaaqmohammed.util.misc.ColorUtil;
import io.github.abdurazaaqmohammed.ui.common.util.DialogUtil;
import io.github.abdurazaaqmohammed.util.misc.ErrorUtil;
import io.github.abdurazaaqmohammed.feature.apk.InstallUtil;
import io.github.abdurazaaqmohammed.feature.apk.MergeUtil;
import io.github.abdurazaaqmohammed.util.misc.StickerUtil;
import io.github.abdurazaaqmohammed.data.prefs.UiPrefs;


import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.method.LinkMovementMethod;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import java.nio.charset.StandardCharsets;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;


import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import io.github.abdurazaaqmohammed.feature.boot.BootImageEngine;
import io.github.abdurazaaqmohammed.feature.boot.BootInspectManager;
import io.github.abdurazaaqmohammed.ui.viewer.ApkDrawableRenderer;
import io.github.abdurazaaqmohammed.ui.editor.DrawablePreviewDialog;
import io.github.abdurazaaqmohammed.ui.editor.StickerPreviewDialog;
import io.github.abdurazaaqmohammed.util.text.FileSize;
import io.github.abdurazaaqmohammed.util.archive.SplitArchiveUtil;
import io.github.abdurazaaqmohammed.feature.payload.PayloadDialog;
import io.github.abdurazaaqmohammed.feature.payload.PayloadParser;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.main.ApkToolsHandler;
import io.github.abdurazaaqmohammed.ui.main.ChecksumDialogs;
import io.github.abdurazaaqmohammed.ui.main.FileOperationsHelper;
import io.github.abdurazaaqmohammed.ui.main.MainFilesArrayAdapter;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.feature.apk.ArscEditorPlusActivity;
import io.github.abdurazaaqmohammed.feature.apk.ArscEditorActivity;
import io.github.abdurazaaqmohammed.ui.editor.CompareDexActivity;
import io.github.abdurazaaqmohammed.ui.editor.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.editor.HtmlViewerActivity;
import io.github.abdurazaaqmohammed.ui.editor.TextEditorActivity;
import io.github.abdurazaaqmohammed.util.archive.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.util.archive.ArchiveUtil;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.util.hash.HashUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.util.text.MimeUtil;
import io.github.abdurazaaqmohammed.util.io.ProgressManager;
import io.github.abdurazaaqmohammed.util.io.SafFs;
import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;
import io.github.abdurazaaqmohammed.util.archive.ZipCompat;
import io.github.abdurazaaqmohammed.feature.apk.nativelib.ElfParser;
import io.github.abdurazaaqmohammed.plugins.ipc.ExternalActions;
import io.github.abdurazaaqmohammed.plugins.ipc.HookRegistry;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginContracts;
import io.github.abdurazaaqmohammed.plugins.ipc.PluginEvents;
import io.github.abdurazaaqmohammed.util.text.ShellQuote;
import io.github.codehasan.colorpicker.extensions.Extensions;

/**
 * File-open workflows extracted from MainFilesArrayAdapter:
 * click dispatch, open-with, font/XML/signature viewers, root-aware
 * staging, split-APK and ARSC menus.
 */
public class FileOpener {

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final boolean pane1;
    private final ApkToolsHandler apkTools;
    private final ChecksumDialogs checksumDialogs;
    private final FileOperationsHelper fileOps;

    public FileOpener(MainActivity context,
                      DialogUtil dialogUtil, boolean pane1,
                      ApkToolsHandler apkTools,
                      ChecksumDialogs checksumDialogs,
                      FileOperationsHelper fileOps) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.pane1 = pane1;
        this.apkTools = apkTools;
        this.checksumDialogs = checksumDialogs;
        this.fileOps = fileOps;
    }

    public interface ReadableCallback {
        void onReady(File readable) throws Exception;
    }

    public void openWithForFile(File file, String fileName) {
        if (fileName != null && FileUtils.matchExt("." + FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT), FileUtils.IMAGE_EXTS)) {
            withReadableCopy(file, readable -> {
                if (StickerUtil.isLottieSticker(readable)) {
                    StickerPreviewDialog.show(context, readable);
                } else {
                    context.openImageViewer(readable.getPath());
                }
            });
            return;
        }
        if (fileName != null && ApkIconHelper.isApkArchiveName(fileName)) {
            if (apkTools != null) {
                apkTools.showApkInfoDialog(file, fileName);
                return;
            }
        }
        if (FileUtils.isElf(fileName) || FileUtils.isElfFile(file)) {
            openElfFileDefault(file, fileName, file);
            return;
        }
        showOpenWithDialog(file, fileName);
    }

    public void openWithForFile(File file, String fileName, File zipFile, String zipEntryPath) {
        if (fileName != null && FileUtils.matchExt("." + FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT), FileUtils.IMAGE_EXTS)) {
            withReadableCopy(file, readable -> {
                if (StickerUtil.isLottieSticker(readable)) {
                    StickerPreviewDialog.show(context, readable);
                } else {
                    context.openImageViewer(readable.getPath());
                }
            });
            return;
        }
        if (fileName != null && ApkIconHelper.isApkArchiveName(fileName)) {
            if (apkTools != null) {
                apkTools.showApkInfoDialog(file, fileName);
                return;
            }
        }
        if (FileUtils.isElf(fileName) || FileUtils.isElfFile(file)) {
            showElfOptionsDialog(file, fileName, file);
            return;
        }
        if (("payload.bin".equalsIgnoreCase(fileName) || (fileName != null && (fileName.endsWith(".bin") || fileName.endsWith(".payload")))) && io.github.abdurazaaqmohammed.feature.payload.PayloadParser.isPayload(file)) {
            io.github.abdurazaaqmohammed.feature.payload.PayloadDialog.showForPayload(context, file, pane1);
            return;
        }
        showOpenWithDialog(file, fileName, zipFile, zipEntryPath);
    }

    public void showOpenWithDialog(File file, String fileName) {
        try {
            context.clearGreen(pane1);
        } catch (Exception ignored) {}
        showOpenWithDialog(file, fileName, null, null);
    }

    /**
     * @param zipFile       the archive a {@code file} was staged from, or null for normal files
     * @param zipEntryPath  the entry path inside {@code zipFile}, or null
     */
    public void showOpenWithDialog(File file, String fileName, File zipFile, String zipEntryPath) {
        showOpenWithDialog(file, fileName, zipFile, zipEntryPath, null);
    }

    public void showOpenWithDialog(File file, String fileName, File zipFile, String zipEntryPath, File originalFile) {
        if (file == null) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        List<String> actionIds = new ArrayList<>(Arrays.asList(
                BUILTIN_TEXT, BUILTIN_ARCHIVE, BUILTIN_IMAGE,
                BUILTIN_HEX, BUILTIN_MEDIA, BUILTIN_APK));
        List<String> actionNames = new ArrayList<>(Arrays.asList(
                context.getString(R.string.text_editor),
                context.getString(R.string.archive_viewer),
                context.getString(R.string.image_viewer),
                context.getString(R.string.hex_editor),
                context.getString(R.string.media_player),
                context.getString(R.string.apk_info)));
        List<Integer> actionIcons = new ArrayList<>(Arrays.asList(
                R.drawable.baseline_text_snippet_24,
                R.drawable.baseline_folder_zip_24,
                R.drawable.image_24px,
                R.drawable.ic_hash_mt,
                R.drawable.video_24px,
                R.drawable.apk_document_24px));
        List<Runnable> actionHandlers = new ArrayList<>(Arrays.asList(
                () -> runBuiltinTool(BUILTIN_TEXT, file, fileName, zipFile, zipEntryPath),
                () -> runBuiltinTool(BUILTIN_ARCHIVE, file, fileName, zipFile, zipEntryPath),
                () -> runBuiltinTool(BUILTIN_IMAGE, file, fileName, zipFile, zipEntryPath),
                () -> runBuiltinTool(BUILTIN_HEX, file, fileName, zipFile, zipEntryPath),
                () -> runBuiltinTool(BUILTIN_MEDIA, file, fileName, zipFile, zipEntryPath),
                () -> runBuiltinTool(BUILTIN_APK, file, fileName, zipFile, zipEntryPath)));
        if (zipFile == null && originalFile != null && file != null
                && !originalFile.getAbsolutePath().equals(file.getAbsolutePath())) {
            actionHandlers.set(0, () -> openTextEditorWithOriginal(file, originalFile));
            actionHandlers.set(3, () -> openHexEditorWithOriginal(file, originalFile));
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".woff") || lower.endsWith(".woff2")) {
            actionIds.add(BUILTIN_FONT);
            actionNames.add(context.getString(R.string.font_preview));
            actionIcons.add(R.drawable.uppercase_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_FONT, file, fileName, zipFile, zipEntryPath));
        }
        if (lower.endsWith(".pdf")) {
            actionIds.add(BUILTIN_PDF);
            actionNames.add(context.getString(R.string.pdf_viewer));
            actionIcons.add(R.drawable.pdf_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_PDF, file, fileName, zipFile, zipEntryPath));
        }
        if (FileUtils.matchExt("." + FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT), FileUtils.HTML_EXTS)) {
            actionIds.add(BUILTIN_HTML);
            actionNames.add(context.getString(R.string.html_viewer));
            actionIcons.add(R.drawable.language_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_HTML, file, fileName, zipFile, zipEntryPath));
        }
        if (lower.endsWith(".arsc")) {
            actionIds.add(BUILTIN_ARSC);
            actionNames.add(context.getString(R.string.arsc_functions));
            actionIcons.add(R.drawable.apk_document_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_ARSC, file, fileName, zipFile, zipEntryPath));
        }
        if (lower.endsWith(".xml")) {
            actionIds.add(BUILTIN_XML);
            actionNames.add(context.getString(R.string.xml_functions));
            actionIcons.add(R.drawable.code_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_XML, file, fileName, zipFile, zipEntryPath));
        }
        // A drawable XML is a picture, not a document. Offer the rendered
        // preview wherever an XML file can be opened (archive entries keep
        // their full res/ path, loose files are sniffed by root tag).
        if (lower.endsWith(".xml") && isDrawableXml(file, zipFile, zipEntryPath)) {
            actionIds.add(BUILTIN_DRAWABLE);
            actionNames.add(context.getString(R.string.drawable_preview));
            actionIcons.add(R.drawable.image_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_DRAWABLE, file, fileName,
                    zipFile, zipEntryPath));
        }
        String keyExt = FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
        if (keyExt.equals("jks") || keyExt.equals("keystore") || keyExt.equals("p12")
                || keyExt.equals("pfx") || keyExt.equals("pk8") || keyExt.equals("pem")) {
            actionIds.add(BUILTIN_SIG);
            actionNames.add(context.getString(R.string.import_signature));
            actionIcons.add(R.drawable.lock_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_SIG, file, fileName, zipFile, zipEntryPath));
        }
        if (keyExt.equals("db") || keyExt.equals("sqlite") || keyExt.equals("sqlite3") || keyExt.equals("db3")) {
            actionIds.add(BUILTIN_SQLITE);
            actionNames.add(context.getString(R.string.sqlite_viewer));
            actionIcons.add(R.drawable.ic_disk_drive_24px);
            actionHandlers.add(() -> runBuiltinTool(BUILTIN_SQLITE, file, fileName, zipFile, zipEntryPath));
        }

        GridView gridView = new GridView(context);
        gridView.setNumColumns(3);
        gridView.setBackgroundColor(Color.TRANSPARENT);
        gridView.setPadding(16, 16, 16, 16);
        gridView.setVerticalSpacing(24);
        gridView.setAdapter(new ArrayAdapter<>(context, 0, actionNames) {
            @NonNull
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);

                ImageView iconView = new ImageView(context);
                iconView.setImageResource(actionIcons.get(position));
                int iconSize = (int) (40 * context.getResources().getDisplayMetrics().density + 0.5f);
                iconView.setLayoutParams(new ViewGroup.LayoutParams(iconSize, iconSize));
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                ColorUtil.changeImageColor(iconView.getDrawable(), typedValue.data);

                TextView labelView = new TextView(context);
                String defId = PreferenceManager.getDefaultSharedPreferences(context)
                        .getString(builtinDefaultKey(fileName), null);
                String label = actionNames.get(position);
                if (defId != null && defId.equals(actionIds.get(position))) label += " ✓";
                labelView.setText(label);
                labelView.setTextSize(12);
                labelView.setGravity(Gravity.CENTER);

                item.addView(iconView);
                item.addView(labelView);
                return item;
            }
        });

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView reportedView = new TextView(context);
        reportedView.setTextSize(12);
        TextView actualView = new TextView(context);
        actualView.setTextSize(12);
        content.addView(reportedView);
        content.addView(actualView);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        SwitchMaterial useActualSwitch =
                new SwitchMaterial(context);
        useActualSwitch.setText(R.string.use_actual_mime);
        useActualSwitch.setChecked(settings.getBoolean("fix_mime_type", false));
        useActualSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                settings.edit().putBoolean("fix_mime_type", isChecked).apply());
        content.addView(useActualSwitch);
        content.addView(gridView);
        try {
            reportedView.setText(context.getString(R.string.reported_mime) + ": "
                    + MimeUtil.getReportedMimeType(context, file));
        } catch (Exception ignored) {
        }
        new Thread(() -> {
            String real = MimeUtil.getRealMimeType(file);
            context.handler.post(() -> {
                try {
                    actualView.setText(context.getString(R.string.real_mime) + ": " + (real != null ? real : "—"));
                } catch (Exception ignored) {
                }
            });
        }).start();

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.open_with) + ": " + fileName)
                .setView(content)
                .setNeutralButton(context.rss.getString(R.string.more), (d, w) -> withReadableCopy(file,
                        readable -> showAppsForMime(readable, fileName, useActualSwitch.isChecked())))
                .create();

        gridView.setOnItemClickListener((parent1, view1, position1, id1) -> {
            dialog.dismiss();
            try {
                actionHandlers.get(position1).run();
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
        gridView.setOnItemLongClickListener((parent1, view1, position1, id1) -> {
            PopupMenu popup = new PopupMenu(context, view1);
            String key = builtinDefaultKey(fileName);
            SharedPreferences longPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            String current = longPrefs.getString(key, null);
            String id = actionIds.get(position1);
            boolean isDef = id.equals(current);
            popup.getMenu().add(context.getString(isDef ? R.string.clear_default : R.string.set_as_default));
            popup.setOnMenuItemClickListener(item -> {
                if (isDef) longPrefs.edit().remove(key).apply();
                else longPrefs.edit().putString(key, id).apply();
                ((ArrayAdapter<?>) gridView.getAdapter()).notifyDataSetChanged();
                return true;
            });
            popup.show();
            return true;
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private static String defaultAppKey(String mime) {
        return MimeUtil.defaultAppKey(mime);
    }

    private static final String BUILTIN_TEXT = "text";
    private static final String BUILTIN_ARCHIVE = "archive";
    private static final String BUILTIN_IMAGE = "image";
    private static final String BUILTIN_HEX = "hex";
    private static final String BUILTIN_MEDIA = "media";
    private static final String BUILTIN_APK = "apk";
    private static final String BUILTIN_FONT = "font";
    private static final String BUILTIN_PDF = "pdf";
    private static final String BUILTIN_HTML = "html";
    private static final String BUILTIN_ARSC = "arsc";
    private static final String BUILTIN_XML = "xml";
    private static final String BUILTIN_SIG = "sig";
    private static final String BUILTIN_DRAWABLE = "drawable_preview";
    private static final String BUILTIN_SQLITE = "sqlite";

    private static boolean isDrawableXml(File file, File zipFile, String zipEntryPath) {
        if (zipEntryPath != null) {
            return zipFile != null
                    ? ApkDrawableRenderer.isDrawableXmlEntry(zipFile, zipEntryPath)
                    : ApkDrawableRenderer.isDrawableXmlPath(zipEntryPath);
        }
        if (file == null) return false;
        if (ApkDrawableRenderer.isDrawableXmlPath(file.getAbsolutePath())) return true;
        return ApkDrawableRenderer.isDrawableXmlFile(null, file.getAbsolutePath());
    }

    private static String builtinDefaultKey(String fileName) {
        String ext = FilenameUtils.getExtension(fileName);
        if (ext == null) ext = "";
        ext = ext.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (ext.isEmpty()) ext = "noext";
        return "openwith_builtin_" + ext;
    }

    private void runBuiltinTool(String id, File file, String fileName, File zipFile, String zipEntryPath) {
        switch (id) {
            case BUILTIN_TEXT:
                if (file == null || !file.isFile()) {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
                openTextEditor(file, zipFile, zipEntryPath);
                return;
            case BUILTIN_ARCHIVE:
                if (file == null || !file.isFile()) return;
                withReadableCopy(file, readable -> {
                    if (zipFile != null && zipEntryPath != null)
                        context.noteArchiveParent(readable, zipFile, zipEntryPath);
                    context.loadZipFolderInPane(readable, "", pane1, true);
                    notifyFileOpened(readable);
                });
                return;
            case BUILTIN_IMAGE:
                if (file == null) return;
                withReadableCopy(file, readable -> {
                    context.openImageViewer(readable.getAbsolutePath());
                    notifyFileOpened(readable);
                });
                return;
            case BUILTIN_HEX:
                if (file == null || !file.isFile()) {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
                openHexEditorRootAware(file, zipFile, zipEntryPath);
                return;
            case BUILTIN_MEDIA:
                if (file == null) return;
                withReadableCopy(file, readable -> {
                    context.playMediaFile(readable.getAbsolutePath());
                    notifyFileOpened(readable);
                });
                return;
            case BUILTIN_APK: {
                if (fileName == null) return;
                String lowerExt = fileName.toLowerCase(Locale.ROOT);
                if (!lowerExt.endsWith(".apk") && !lowerExt.endsWith(".apks") && !lowerExt.endsWith(".xapk")) {
                    Extensions.showMessage(context, R.string.not_an_apk);
                    return;
                }
                withReadableCopy(file, readable -> {
                    if (apkTools != null) apkTools.showApkInfoDialog(readable, fileName);
                    else openWithDefaultOrDialog(readable, fileName);
                    notifyFileOpened(readable);
                });
                return;
            }
            case BUILTIN_FONT:
                if (file == null) return;
                showFontPreview(file, fileName);
                notifyFileOpened(file);
                return;
            case BUILTIN_PDF:
                if (file == null) return;
                showPdfViewer(file, fileName);
                notifyFileOpened(file);
                return;
            case BUILTIN_HTML:
                if (file == null) return;
                showHtmlViewer(file, fileName);
                notifyFileOpened(file);
                return;
            case BUILTIN_ARSC:
                if (file == null) return;
                withReadableCopy(file, readable -> {
                    showArscOpenWith(readable, null, "resources.arsc");
                    notifyFileOpened(readable);
                });
                return;
            case BUILTIN_XML:
                if (file == null) return;
                showXmlFunctions(file, fileName);
                notifyFileOpened(file);
                return;
            case BUILTIN_SIG:
                if (file == null) return;
                importSignature(file, fileName);
                notifyFileOpened(file);
                return;
            case BUILTIN_DRAWABLE:
                if (file == null) return;
                if (zipFile != null && zipEntryPath != null) {
                    DrawablePreviewDialog.show(context, zipFile, zipEntryPath);
                } else {
                    DrawablePreviewDialog.showFile(context, file);
                }
                notifyFileOpened(file);
                return;
            case BUILTIN_SQLITE:
                if (file == null) return;
                openSqliteViewer(file);
                notifyFileOpened(file);
                return;
            default:
                return;
        }
    }

    private void openSqliteViewer(File file) {
        withReadableCopy(file, readable -> {
            if (readable == null || readable.length() < 16) {
                Extensions.showMessage(context, R.string.cannot_open_item);
                return;
            }
            try (FileInputStream fis = new FileInputStream(readable)) {
                byte[] header = new byte[16];
                if (fis.read(header) != 16 || !new String(header, StandardCharsets.US_ASCII).startsWith("SQLite format 3")) {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
            } catch (Exception e) {
                Extensions.showMessage(context, R.string.cannot_open_item);
                return;
            }
            Intent intent = new Intent(context, SqliteViewerActivity.class)
                    .putExtra(SqliteViewerActivity.EXTRA_DB_PATH, readable.getAbsolutePath())
                    .putExtra(SqliteViewerActivity.EXTRA_ORIGINAL_PATH, file.getAbsolutePath());
            context.startActivity(intent);
        });
    }

    private boolean tryRunBuiltinDefault(File file, String fileName) {
        return tryRunBuiltinDefault(file, fileName, null, null);
    }

    /** Built-in extension overrides keep the archive context for editor write-back. */
    private boolean tryRunBuiltinDefault(File file, String fileName, File zipFile, String zipEntryPath) {
        if (file == null || fileName == null) return false;
        String key = builtinDefaultKey(fileName);
        String def = PreferenceManager.getDefaultSharedPreferences(context).getString(key, null);
        if (def == null) return false;
        switch (def) {
            case BUILTIN_TEXT:
            case BUILTIN_ARCHIVE:
            case BUILTIN_IMAGE:
            case BUILTIN_HEX:
            case BUILTIN_MEDIA:
            case BUILTIN_APK:
            case BUILTIN_FONT:
            case BUILTIN_PDF:
            case BUILTIN_HTML:
            case BUILTIN_ARSC:
            case BUILTIN_XML:
            case BUILTIN_SIG:
            case BUILTIN_DRAWABLE:
            case BUILTIN_SQLITE:
                runBuiltinTool(def, file, fileName, zipFile, zipEntryPath);
                return true;
            default:
                PreferenceManager.getDefaultSharedPreferences(context).edit().remove(key).apply();
                return false;
        }
    }

    private void launchAppForMime(ResolveInfo info, Uri uri, String mime) {
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newRawUri(uri.toString(), uri));
        intent.setClassName(info.activityInfo.packageName, info.activityInfo.name);
        context.startActivity(intent);
    }

    /**
     * Launches the user's saved external default (Open with → long-press →
     * Set as default) for {@code mime} straight on its saved component, with
     * the read grant and ClipData the target needs for the content URI. Only
     * a genuinely unavailable target (uninstalled app or a malformed entry)
     * clears the stale default; any unrelated launch failure keeps it and
     * lets the caller fall back safely.
     */
    private boolean tryLaunchDefaultApp(Uri uri, String mime) {
        if (mime == null || uri == null) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String def = prefs.getString(defaultAppKey(mime), null);
        if (def == null) return false;
        ComponentName cn = ComponentName.unflattenFromString(def);
        if (cn == null) {
            prefs.edit().remove(defaultAppKey(mime)).apply();
            return false;
        }
        try {
            Intent launch = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setComponent(cn);
            launch.setClipData(ClipData.newRawUri(uri.toString(), uri));
            context.startActivity(launch);
            return true;
        } catch (ActivityNotFoundException e) {
            prefs.edit().remove(defaultAppKey(mime)).apply();
            return false;
        } catch (Exception e) {
            // SecurityException and friends: the default may still be valid in
            // another context, so keep it and fall back.
            return false;
        }
    }

    /**
     * Probes the MIME types the external default app could be registered under
     * (the app-reported one, then the sniffed and content-detected ones) and
     * launches the user's external default (Open with → More → long-press →
     * Set as default), if any. {@code logicalName} is the name the user
     * clicked, which may differ from the staged copy's name (a
     * {@code foo.pdf.bak} dispatch probes {@code foo.pdf}). Returns true when
     * one matched and was launched.
     */
    private boolean tryLaunchExternalDefault(Uri uri, File readable, String logicalName) {
        String actionMime = MimeUtil.getMimeTypeForAction(context, readable, logicalName);
        if (actionMime == null) actionMime = "application/octet-stream";
        if (tryLaunchDefaultApp(uri, actionMime)) {
            notifyFileOpened(readable);
            return true;
        }
        try {
            String real = MimeUtil.getRealMimeType(readable);
            if (real != null && !real.equals(actionMime) && tryLaunchDefaultApp(uri, real)) {
                notifyFileOpened(readable);
                return true;
            }
            String reported = MimeUtil.getReportedMimeType(context, readable, logicalName);
            if (reported != null && !reported.equals(actionMime) && (real == null || !reported.equals(real)) && tryLaunchDefaultApp(uri, reported)) {
                notifyFileOpened(readable);
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Content URI through the app's FileProvider, or null when unusable. */
    private Uri fileProviderUri(File file) {
        try {
            return FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Shared automatic-open path used by the normal file click and by archive
     * entry taps (via {@link #openZipEntryWithDefault}): the built-in
     * extension override wins first (run with the archive context when one is
     * given), then a saved external MIME default is launched from a readable
     * staged copy, and only when neither applies does {@code fallback} run the
     * ordinary dispatch. When no saved external default exists for any of the
     * file's MIME candidates, no staged copy is made at all, so plain local
     * files dispatch exactly as they did before. The fallback keeps the
     * original {@code file} reference so writeback context is preserved.
     */
    public void openWithSavedDefaults(File file, String fileName,
                                      File zipFile, String zipEntryPath, Runnable fallback) {
        if (file == null || fileName == null) {
            if (fallback != null) fallback.run();
            return;
        }
        if (tryRunBuiltinDefault(file, fileName, zipFile, zipEntryPath)) return;
        if (!hasSavedExternalDefault(file, fileName)) {
            if (fallback != null) fallback.run();
            return;
        }
        withReadableCopy(file, readable -> {
            Uri uri = fileProviderUri(readable);
            if (uri == null || !tryLaunchExternalDefault(uri, readable, MimeUtil.logicalDisplayName(fileName))) {
                if (fallback != null) fallback.run();
            }
        });
    }

    public void openWithSavedDefaults(File file, String fileName, Runnable fallback) {
        openWithSavedDefaults(file, fileName, null, null, fallback);
    }

    /**
     * Automatic saved-default handling for an archive entry tap, callable from
     * DexTools: resolves the built-in default with the archive context, then
     * the saved external MIME default for {@code name}, and only then runs
     * {@code fallback} (the caller's own open-with dispatch).
     */
    public void openZipEntryWithDefault(File staged, String name, File archive, String entry, Runnable fallback) {
        openWithSavedDefaults(staged, name, archive, entry, fallback);
    }

    /**
     * Cheap gate before the expensive staged copy: only stage and read the
     * file when a saved external default could actually apply to one of the
     * MIME types this file resolves under (its extension-derived type, its
     * sniffed type, or the unknown-type catch-all).
     */
    private boolean hasSavedExternalDefault(File file, String fileName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean anyDefault = false;
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith("openwith_default_")) {
                anyDefault = true;
                break;
            }
        }
        if (!anyDefault) return false;
        String byName = MimeUtil.getMimeTypeFromExtension(MimeUtil.logicalDisplayName(fileName));
        if (byName != null && prefs.contains(defaultAppKey(byName))) return true;
        if (prefs.contains(defaultAppKey("application/octet-stream"))) return true;
        if (SafFs.isSaf(file) || !file.canRead()) return true;
        String reported = MimeUtil.getReportedMimeType(context, file, MimeUtil.logicalDisplayName(fileName));
        if (reported != null && prefs.contains(defaultAppKey(reported))) return true;
        try {
            String real = MimeUtil.getRealMimeType(file);
            if (real != null && prefs.contains(defaultAppKey(real))) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Routes a file whose bytes look like an archive (epub, .zip and friends)
     * either to the user's chosen default app or — when none was set — into
     * MP's archive viewer, exactly as before. Built-in defaults win before
     * this method is reached; SAF/root-staged copies keep opening externally
     * because the in-app archive browser cannot write them back.
     */
    private void openArchiveOrDefault(File file, String fileName) {
        withReadableCopy(file, readable -> {
            boolean staged;
            try {
                staged = !readable.getAbsolutePath().equals(file.getAbsolutePath());
            } catch (Exception e) {
                staged = true;
            }
            Uri uri = fileProviderUri(readable);
            if (uri != null && tryLaunchExternalDefault(uri, readable, fileName)) return;
            if (staged || !ArchiveBrowseUtil.isBrowsable(readable)) {
                openWithDefaultOrDialog(readable, fileName);
                return;
            }
            openArchiveSafe(readable);
        });
    }

    public void openWithDefaultOrDialog(File file, String fileName) {
        openWithSavedDefaults(file, fileName, null, null, () -> {
            withReadableCopy(file, readable -> {
                if (fileProviderUri(readable) == null) {
                    showOpenWithDialog(readable, fileName);
                    return;
                }
                showOpenWithDialog(readable, fileName, null, null, file);
            });
        });
    }

    /**
     * Opens an archive on the usual read-dispatch. A provider-backed archive
     * only has a staged copy here, and the archive browser rewrites that copy
     * in place - edits would never reach the provider - so for SAF sources the
     * archive opens with an external app instead; the SAF pane's own Extract
     * and Compress actions cover working with it inside the app.
     */
    private void openArchiveSafe(File file) {
        if (SafFs.isSaf(file)) {
            openWithDefaultOrDialog(file, file.getName());
            return;
        }
        File target = file;
        if (SplitArchiveUtil.isRarSplit(file) || SplitArchiveUtil.isSevenZSplit(file) || SplitArchiveUtil.isZipSplit(file)) {
            File firstPart = SplitArchiveUtil.findFirstPart(file);
            if (firstPart != null && firstPart.exists()) target = firstPart;
        } else if (ArchiveBrowseUtil.detectArchiveType(file) == ArchiveBrowseUtil.ArchiveType.NONE) {
            File firstPart = SplitArchiveUtil.findFirstPart(file);
            if (firstPart != null && firstPart.exists() && ArchiveBrowseUtil.detectArchiveType(firstPart) != ArchiveBrowseUtil.ArchiveType.NONE) {
                target = firstPart;
            }
        }
        withReadableCopy(target, readable -> {
            context.loadZipFolderInPane(readable, "", pane1, true);
            notifyFileOpened(readable);
        });
    }

    private void showAppsForMime(File file, String fileName, boolean useActual) {
        String mime = useActual ? MimeUtil.getRealMimeType(file) : null;
        if (mime == null) mime = MimeUtil.getReportedMimeType(context, file);
        if (mime == null) mime = "application/octet-stream";
        showAppsForMime(file, fileName, mime);
    }

    private void showAppsForMime(File file, String fileName, String chosenMime) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file);
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> apps = pm.queryIntentActivities(
                new Intent(Intent.ACTION_VIEW).setDataAndType(uri, chosenMime),
                PackageManager.MATCH_DEFAULT_ONLY);
        if (apps.isEmpty()) {
            Extensions.showMessage(context, R.string.no_apps_found);
            return;
        }
        apps.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                String.valueOf(a.loadLabel(pm)), String.valueOf(b.loadLabel(pm))));
        RecyclerView list = new RecyclerView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle(context.rss.getString(R.string.open_with) + ": " + fileName + " (" + chosenMime + ")")
                .setView(list)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        list.setAdapter(new RecyclerView.Adapter<>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                LinearLayout row = new LinearLayout(context);
                row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int pad = (int) (12 * context.getResources().getDisplayMetrics().density + 0.5f);
                row.setPadding(pad, pad, pad, pad);
                ImageView icon = new ImageView(context);
                int s = (int) (40 * context.getResources().getDisplayMetrics().density + 0.5f);
                icon.setLayoutParams(new LinearLayout.LayoutParams(s, s));
                LinearLayout texts = new LinearLayout(context);
                texts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                tp.leftMargin = pad;
                TextView name = new TextView(context);
                name.setTextSize(15);
                TextView sub = new TextView(context);
                sub.setTextSize(12);
                texts.addView(name);
                texts.addView(sub);
                row.addView(icon);
                row.addView(texts, tp);
                return new RecyclerView.ViewHolder(row) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ResolveInfo info = apps.get(position);
                LinearLayout row = (LinearLayout) holder.itemView;
                LinearLayout texts = (LinearLayout) row.getChildAt(1);
                ImageView icon = (ImageView) row.getChildAt(0);
                TextView name = (TextView) texts.getChildAt(0);
                TextView sub = (TextView) texts.getChildAt(1);
                try {
                    icon.setImageDrawable(info.loadIcon(pm));
                } catch (Exception ignored) {
                }
                String label = String.valueOf(info.loadLabel(pm));
                name.setText(label);
                String currentDef = prefs.getString(defaultAppKey(chosenMime), null);
                boolean isDef = currentDef != null && currentDef.equals(
                        new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString());
                sub.setText(isDef ? context.getString(R.string.default_app, label) : info.activityInfo.packageName);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    try {
                        launchAppForMime(info, uri, chosenMime);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
                row.setOnLongClickListener(v -> {
                    PopupMenu popup = new PopupMenu(context, row);
                    String flat = new ComponentName(
                            info.activityInfo.packageName, info.activityInfo.name).flattenToString();
                    if (isDef) {
                        popup.getMenu().add(context.getString(R.string.clear_default));
                    } else {
                        popup.getMenu().add(context.getString(R.string.set_as_default));
                    }
                    popup.setOnMenuItemClickListener(item -> {
                        if (isDef) prefs.edit().remove(defaultAppKey(chosenMime)).apply();
                        // An external default only takes effect when it is not
                        // shadowed by the built-in extension override (which
                        // is checked first on every click), so clear it.
                        else prefs.edit().putString(defaultAppKey(chosenMime), flat)
                                .remove(builtinDefaultKey(fileName)).apply();
                        notifyDataSetChanged();
                        return true;
                    });
                    popup.show();
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return apps.size();
            }
        });
        dialogUtil.styleAlertDialog(dialog);
    }

    private void showFontPreview(File file, String fileName) {
        withReadableCopy(file, readable -> {
            try {
                Typeface tf;
                try {
                    tf = Typeface.createFromFile(readable);
                } catch (RuntimeException e) {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
                if (tf == null) {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
                LinearLayout root = new LinearLayout(context);
                root.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
                root.setPadding(pad, pad, pad, pad);

                EditText customInput = new EditText(context);
                customInput.setHint("Type custom text here...");
                customInput.setTextSize(16);
                root.addView(customInput);

                TextView sample = new TextView(context);
                sample.setText("The quick brown fox jumps over the lazy dog.\nABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !?@#$%^&*()");
                sample.setTypeface(tf);
                sample.setTextSize(20);
                sample.setPadding(0, pad / 2, 0, pad / 2);
                root.addView(sample);

                customInput.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                        sample.setText(s.length() > 0 ? s : "The quick brown fox jumps over the lazy dog.\nABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !?@#$%^&*()");
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });

                TextView meta = new TextView(context);
                meta.setText(fileName + " · " + Formatter.formatFileSize(context, readable.length()));
                meta.setTextSize(13);
                root.addView(meta);

                ScrollView scroll = new ScrollView(context);
                scroll.addView(root);

                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(context.getString(R.string.font_preview) + ": " + fileName)
                        .setView(scroll)
                        .setPositiveButton(android.R.string.ok, null)
                        .create());
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
    }

    private void showPdfViewer(File file, String fileName) {
        withReadableCopy(file, readable -> {
            if (readable == null || readable.length() < 4) {
                Extensions.showMessage(context, R.string.cannot_open_item);
                return;
            }
            try (FileInputStream fis = new FileInputStream(readable)) {
                byte[] header = new byte[4];
                if (fis.read(header) != 4 || header[0] != '%' || header[1] != 'P' || header[2] != 'D' || header[3] != 'F') {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
            } catch (Exception e) {
                Extensions.showMessage(context, R.string.cannot_open_item);
                return;
            }
            Intent intent = new Intent(context, io.github.abdurazaaqmohammed.ui.viewer.PdfViewerActivity.class)
                    .putExtra(io.github.abdurazaaqmohammed.ui.viewer.PdfViewerActivity.EXTRA_FILE_PATH, readable.getAbsolutePath())
                    .putExtra(io.github.abdurazaaqmohammed.ui.viewer.PdfViewerActivity.EXTRA_FILE_NAME, fileName);
            context.startActivity(intent);
        });
    }

    private void showHtmlViewer(File readable, String fileName) {
        context.startActivity(new Intent(context, HtmlViewerActivity.class)
                .putExtra(HtmlViewerActivity.EXTRA_PATH, readable.getAbsolutePath())
                .putExtra(HtmlViewerActivity.EXTRA_NAME, fileName));
    }


    private boolean isValidElfFile(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             ElfParser ignored = new ElfParser(raf)) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openElfFileDefault(File file, String fileName, File originalFile) {
        if (isValidElfFile(file)) {
            openArm64Disassembler(file, originalFile);
        } else {
            openHexEditorWithOriginal(file, originalFile);
        }
    }

    /** Upper bound of dialog.elf.extra options appended to the dialog. */
    private static final int MAX_ELF_HOOK_OPTIONS = 16;

    private void showElfOptionsDialog(File file, String fileName, File originalFile) {
        final boolean hasDisassembler = isValidElfFile(file);
        List<String> optionsList = new ArrayList<>();
        if (hasDisassembler) {
            optionsList.add("ARM64 Disassembler");
        }
        optionsList.add(context.getString(R.string.hex_editor));
        optionsList.add(context.getString(R.string.text_editor));
        optionsList.add(context.getString(R.string.open_with));
        final int builtinCount = optionsList.size();

        // dialog.elf.extra hooks, appended after the built-in options. The
        // parent registry applies order/hiding and filters disabled packages;
        // only dedup and the dialog-friendliness cap are added here.
        List<ExternalActions.Entry> elfHooks = new ArrayList<>();
        try {
            Set<String> seenHookIds = new HashSet<>();
            for (ExternalActions.Entry e : HookRegistry.visibleEntries(context,
                    PluginContracts.HOOK_ELF_DIALOG_EXTRA)) {
                if (e == null || e.id == null || e.plugin == null) continue;
                if (!seenHookIds.add(e.id)) continue;
                if (elfHooks.size() >= MAX_ELF_HOOK_OPTIONS) break;
                elfHooks.add(e);
                optionsList.add(e.title == null || e.title.isEmpty() ? e.id : e.title);
            }
        } catch (Exception ignored) {
        }

        String[] options = optionsList.toArray(new String[0]);

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle("ELF: " + fileName)
                .setItems(options, (d, which) -> {
                    if (which >= builtinCount) {
                        int hookIndex = which - builtinCount;
                        if (hookIndex >= 0 && hookIndex < elfHooks.size()) {
                            runElfHookAction(elfHooks.get(hookIndex), file);
                        }
                        return;
                    }
                    int action = which;
                    if (hasDisassembler) {
                        if (action == 0) {
                            openArm64Disassembler(file, originalFile);
                            return;
                        }
                        action--;
                    }
                    switch (action) {
                        case 0 -> openHexEditorWithOriginal(file, originalFile);
                        case 1 -> openTextEditorWithOriginal(file, originalFile);
                        case 2 -> showOpenWithDialog(file, fileName, null, null, originalFile);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialogUtil.styleAlertDialog(dialog);
    }

    /**
     * Runs one dialog.elf.extra hook entry against the ELF. The file is
     * resolved to a host-readable source, then shared strictly as a read-only
     * content URI through the shared file-action runner (same code path as
     * the file menu). When the source sits outside the FileProvider roots, a
     * sanitized private cache copy is staged instead and deleted on the
     * result, cancel included. Grants are intent-flag-only (one-shot,
     * Android-managed); no raw path and no persistent grant crosses over.
     */
    private void runElfHookAction(ExternalActions.Entry entry, File file) {
        if (entry == null || entry.plugin == null || file == null) return;
        withReadableCopy(file, readable -> {
            File fallbackCopy = null;
            boolean handedOff = false;
            try {
                if (readable == null || !readable.isFile()) {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
                List<File> files = new ArrayList<>();
                files.add(readable);
                List<Uri> uris = ExternalActions.stageUris(context, files);
                if (uris == null || uris.size() != 1) {
                    fallbackCopy = stagePrivateReadableCopy(readable);
                    if (fallbackCopy == null || fileProviderUri(fallbackCopy) == null) {
                        if (fallbackCopy != null) {
                            try { fallbackCopy.delete(); } catch (Exception ignored) {}
                        }
                        Extensions.showMessage(context, R.string.cannot_open_item);
                        return;
                    }
                    List<Uri> single = new ArrayList<>();
                    single.add(fileProviderUri(fallbackCopy));
                    uris = single;
                }
                final File cleanupCopy = fallbackCopy;
                MainFilesArrayAdapter.runExternalFileAction(context, entry, files, uris,
                        cleanupCopy == null ? null : () -> {
                            try { cleanupCopy.delete(); } catch (Exception ignored) {}
                        });
                handedOff = true;
            } catch (Exception ignored) {
            } finally {
                if (!handedOff && fallbackCopy != null) {
                    try { fallbackCopy.delete(); } catch (Exception ignored) {}
                }
            }
        });
    }

    /**
     * Private cache staging fallback for sources the FileProvider cannot
     * describe: collision-safe sanitized name, plain stream copy. The copy
     * stays host-private until the shared runner shares it as a read-only
     * content URI scoped to the clicked plugin, and the caller deletes it on
     * result.
     */
    private File stagePrivateReadableCopy(File source) {
        try {
            if (source == null || !source.isFile()) return null;
            String name = source.getName() == null ? "file" : source.getName();
            name = name.replace('/', '_').replace('\\', '_');
            String base = name;
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                base = name.substring(0, dot);
                ext = name.substring(dot);
            }
            if (base.length() > 48) base = base.substring(0, 48);
            if (base.isEmpty()) base = "file";
            File stageDir = new File(context.getCacheDir(), "elf_hook_stage");
            if (!stageDir.exists() && !stageDir.mkdirs()) return null;
            File out = new File(stageDir, "elf_stage_"
                    + UUID.randomUUID().toString().substring(0, 8) + "_" + base + ext);
            try (InputStream in = new FileInputStream(source);
                 FileOutputStream fout = new FileOutputStream(out)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) fout.write(buf, 0, n);
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fires the consented file.opened event after a user-initiated open has
     * actually dispatched (viewer/editor/activity started). Metadata only:
     * display name plus extension-derived mime; never a path, URI or file
     * content. Placed at the leaf launch points so dialogs, menu builds and
     * failed opens never emit.
     */
    private void notifyFileOpened(File opened) {
        try {
            String name = opened == null ? null : opened.getName();
            if (name == null || name.isEmpty()) return;
            PluginEvents.fileOpened(context, name,
                    MimeUtil.getMimeTypeFromExtension(name));
        } catch (Exception ignored) {
        }
    }

    private void openArm64Disassembler(File file, File originalFile) {
        File orig = originalFile != null ? originalFile : file;
        File stagedArchive = DexTools.archiveForStagedFile(orig.getPath());
        Intent intent = new Intent(context, Arm64DisassemblerActivity.class)
                .putExtra(Arm64DisassemblerActivity.EXTRA_SO_PATH, file.getAbsolutePath())
                .putExtra(Arm64DisassemblerActivity.EXTRA_ORIGINAL_PATH, orig.getAbsolutePath());
        if (stagedArchive != null) {
            intent.putExtra("zipFilePath", stagedArchive.getAbsolutePath());
        }
        if (context instanceof MainActivity) {
            ((MainActivity) context).startActivityForResult(intent, 757);
        } else {
            context.startActivity(intent);
        }
        notifyFileOpened(file);
    }

    public static boolean isKernelFile(File file, String fileName) {
        if (file == null || fileName == null) return false;
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.equals("kernel") || name.equals("zimage") || name.equals("image")
                || name.startsWith("kernel_") || name.startsWith("vmlinuz")
                || (name.startsWith("image.") && !name.endsWith(".png") && !name.endsWith(".jpg"))) {
            return true;
        }
        if (file.isFile() && file.length() >= 64) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                if (raf.length() > 64) {
                    raf.seek(0x38);
                    byte[] m = new byte[4];
                    raf.readFully(m);
                    if (m[0] == 'A' && m[1] == 'R' && m[2] == 'M' && m[3] == 0x64) return true;
                }
                if (raf.length() > 40) {
                    raf.seek(0x24);
                    int zMagic = Integer.reverseBytes(raf.readInt());
                    if (zMagic == 0x016f2818) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void showKernelOptionsDialog(File file, String fileName, File originalFile) {
        String[] options = {
                context.getString(R.string.hex_editor),
                "Kernel Info & Version",
                context.getString(R.string.text_editor),
                context.getString(R.string.open_with)
        };

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle("Kernel: " + fileName)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0 -> openHexEditorWithOriginal(file, originalFile);
                        case 1 -> showKernelInfoDialog(file, fileName);
                        case 2 -> openTextEditorWithOriginal(file, originalFile);
                        case 3 -> showOpenWithDialog(file, fileName, null, null, originalFile);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialogUtil.styleAlertDialog(dialog);
    }

    private void showKernelInfoDialog(File file, String fileName) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.loading));

        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            sb.append("File: ").append(fileName).append("\n");
            sb.append("Size: ").append(FileSize.getHumanReadableFileSize(file.length()))
                    .append(" (").append(file.length()).append(" bytes)\n\n");

            String arch = "Unknown / Compressed";
            String banner = null;

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                long len = raf.length();
                if (len > 64) {
                    raf.seek(0x38);
                    byte[] m = new byte[4];
                    raf.readFully(m);
                    if (m[0] == 'A' && m[1] == 'R' && m[2] == 'M' && m[3] == 0x64) {
                        arch = "ARM64 (AArch64 Linux Kernel Image)";
                    }
                }
                if (len > 40 && arch.startsWith("Unknown")) {
                    raf.seek(0x24);
                    int zMagic = Integer.reverseBytes(raf.readInt());
                    if (zMagic == 0x016f2818) {
                        arch = "ARM 32-bit (zImage Linux Kernel)";
                    }
                }

                // Scan for "Linux version "
                raf.seek(0);
                byte[] buffer = new byte[65536];
                byte[] pattern = "Linux version ".getBytes(StandardCharsets.US_ASCII);
                long bytesScanned = 0;
                long maxScan = Math.min(len, 32L * 1024 * 1024);

                outer:
                while (bytesScanned < maxScan) {
                    int read = raf.read(buffer);
                    if (read <= 0) break;
                    for (int i = 0; i < read - pattern.length; i++) {
                        boolean match = true;
                        for (int j = 0; j < pattern.length; j++) {
                            if (buffer[i + j] != pattern[j]) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            int p = i;
                            while (p < read && buffer[p] != 0 && buffer[p] != '\n' && baos.size() < 1024) {
                                baos.write(buffer[p]);
                                p++;
                            }
                            banner = new String(baos.toByteArray(), StandardCharsets.UTF_8).trim();
                            break outer;
                        }
                    }
                    bytesScanned += read;
                    if (read == buffer.length) {
                        raf.seek(Math.max(0, bytesScanned - pattern.length));
                    }
                }
            } catch (Exception e) {
                sb.append("Error inspecting: ").append(e.getMessage()).append("\n");
            }

            sb.append("Architecture: ").append(arch).append("\n\n");
            if (banner != null && !banner.isEmpty()) {
                sb.append("Kernel Version Banner:\n").append(banner).append("\n");
            } else {
                sb.append("Kernel Version Banner: (Embedded banner not found; kernel may be compressed or stripped)\n");
            }

            final String infoText = sb.toString();
            context.runOnUiThread(() -> {
                pm.dismiss();
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Kernel Info: " + fileName)
                        .setMessage(infoText)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton("📋 Copy", (d, w) -> {
                            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Kernel Info", infoText));
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            });
        }).start();
    }


    private void showXmlFunctions(File file, String fileName) {
        withReadableCopy(file, readable -> {
            boolean binary = false;
            try (InputStream is = FileUtils.getInputStream(readable)) {
                binary = FileUtils.isAxml(is);
            } catch (Exception ignored) {
            }
            String[] items = binary
                    ? new String[]{context.getString(R.string.open_as_text), context.getString(R.string.decode_open)}
                    : new String[]{context.getString(R.string.open_as_text), context.getString(R.string.format_xml)};
            final boolean isBinary = binary;
            dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                    .setTitle(context.getString(R.string.xml_functions) + ": " + fileName)
                    .setSingleChoiceItems(items, -1, (dialog, which) -> {
                        dialog.dismiss();
                        if (which == 0) {
                            context.startActivity(rootAwareEditorIntent(readable, file)
                                    .putExtra("path", readable.getPath()));
                        } else if (isBinary) {
                            // Decoded by the editor from the staged copy (see the
                            // note in handleFileClick): pre-decoded text can blow
                            // the binder transaction limit on large binary XML.
                            context.startActivity(rootAwareEditorIntent(readable, file)
                                    .putExtra("axml", true)
                                    .putExtra("path", readable.getPath()));
                        } else {
                            formatXmlFile(readable, file, fileName);
                        }
                    }).create());
        });
    }

    private void formatXmlFile(File readable, File original, String fileName) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        new Thread(() -> {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(true);
                Document doc;
                try (InputStream is = FileUtils.getInputStream(readable)) {
                    doc = dbf.newDocumentBuilder().parse(is);
                }
                Transformer transformer =
                        TransformerFactory.newInstance().newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                File out = new File(context.getCacheDir(), System.currentTimeMillis() + "_formatted.xml");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    transformer.transform(new DOMSource(doc),
                            new StreamResult(fos));
                }
                if (!readable.getAbsolutePath().equals(original.getAbsolutePath())) {
                    PrivilegeManager.Files.copyFile(context, out.getAbsolutePath(), original.getAbsolutePath(), true);
                } else {
                    FileUtils.copyFile(out, readable);
                }
                out.delete();
                pm.dismiss();
                context.handler.post(() -> {
                    Extensions.showMessage(context, R.string.xml_formatted);
                    context.loadFolderInPane(original.getParentFile(), pane1);
                });
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showShellScriptDialog(File readable, File original, String fileName) {
        boolean rootMode = false;
        try {
            rootMode = PrivilegeManager.Root.isRootMode(context);
        } catch (Exception ignored) {
        }
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad, pad, pad);

        TextView warning = new TextView(context);
        warning.setText(context.getString(R.string.sh_warning));
        root.addView(warning);

        CheckBox rootBox = null;
        if (rootMode) {
            rootBox = new CheckBox(context);
            rootBox.setText(R.string.sh_run_as_root);
            root.addView(rootBox);
        }
        final CheckBox runAsRootBox = rootBox;

        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.sh_select_action) + ": " + fileName)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.command_shell, (dialog, which) -> {
                    Intent intent = new Intent(context, CommandShellActivity.class);
                    intent.putExtra("script_path", readable.getAbsolutePath());
                    context.startActivity(intent);
                })
                .setPositiveButton(R.string.sh_execute, (dialog, which) -> executeShellScript(
                        readable, runAsRootBox != null && runAsRootBox.isChecked()))
                .create());
    }

    private void executeShellScript(File script, boolean asRoot) {
        boolean rootMode = false;
        try {
            rootMode = PrivilegeManager.Root.isRootMode(context);
        } catch (Exception ignored) {
        }
        if (asRoot && !rootMode) {
            Extensions.showMessage(context, R.string.sh_root_unavailable);
            return;
        }
        ProgressManager pm = new ProgressManager(context, true);
        pm.show();
        try {
            pm.setText(context.getString(R.string.sh_executing));
        } catch (Exception ignored) {
        }
        new Thread(() -> {
            int exitCode = -1;
            String out = "";
            String err = "";
            try {
                if (asRoot) {
                    ShellSession.ShellResult r = PrivilegeManager.Root.execute(context,
                            "sh " + ShellQuote.escapeShellArg(script.getAbsolutePath()), 120);
                    exitCode = r.exitCode();
                    out = r.output();
                    err = r.error();
                } else {
                    Process process = new ProcessBuilder("sh", script.getAbsolutePath()).start();
                    StringBuilder outSb = new StringBuilder();
                    StringBuilder errSb = new StringBuilder();
                    Thread drainOut = new Thread(() -> {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                if (outSb.length() > 0) outSb.append('\n');
                                outSb.append(line);
                            }
                        } catch (Exception ignored) {
                        }
                    });
                    Thread drainErr = new Thread(() -> {
                        try (BufferedReader br = new BufferedReader(
                                new InputStreamReader(process.getErrorStream()))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                if (errSb.length() > 0) errSb.append('\n');
                                errSb.append(line);
                            }
                        } catch (Exception ignored) {
                        }
                    });
                    drainOut.start();
                    drainErr.start();
                    boolean finished = process.waitFor(120, TimeUnit.SECONDS);
                    if (!finished) {
                        process.destroyForcibly();
                        errSb.append("Command timed out");
                    } else {
                        exitCode = process.exitValue();
                    }
                    try {
                        drainOut.join(2000);
                        drainErr.join(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    out = outSb.toString();
                    err = errSb.toString();
                }
            } catch (Exception e) {
                err = String.valueOf(e.getMessage());
            }
            pm.dismiss();
            final int code = exitCode;
            final String stdout = out;
            final String stderr = err;
            context.handler.post(() -> showShellOutputDialog(code, stdout, stderr));
        }).start();
    }

    private void showShellOutputDialog(int exitCode, String stdout, String stderr) {
        StringBuilder sb = new StringBuilder();
        sb.append(context.getString(R.string.sh_exit_code, exitCode)).append("\n\n");
        if (stderr != null && !stderr.isEmpty()) {
            sb.append("STDERR:\n").append(stderr).append("\n\n");
        }
        sb.append("STDOUT:\n");
        sb.append((stdout == null || stdout.isEmpty())
                ? context.getString(R.string.sh_no_output) : stdout);
        TextView tv = new TextView(context);
        tv.setText(sb.toString());
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        tv.setPadding(pad, pad / 2, pad, pad / 2);
        ScrollView sv = new ScrollView(context);
        sv.addView(tv);
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.sh_output)
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .create());
    }

    private void importSignature(File file, String fileName) {
        File keysDir = UiPrefs.keysDir(context);
        new Thread(() -> {
            try {
                if (!keysDir.isDirectory() && !keysDir.mkdirs() && !keysDir.isDirectory()) {
                    throw new IOException("Cannot create keys dir");
                }
                File dest = FileUtils.getUnusedFile(new File(keysDir, fileName));
                try (InputStream is = FileUtils.getInputStream(file);
                     FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                }
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                Set<String> paths = new HashSet<>(prefs.getStringSet("signature_key_paths", new HashSet<>()));
                paths.add(dest.getAbsolutePath());
                prefs.edit().putStringSet("signature_key_paths", paths).putString("keyPath", dest.getAbsolutePath()).apply();
                context.handler.post(() -> Extensions.showMessage(context, R.string.signature_file_set));
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    /** Backup safety first, then explicit defaults and ordinary dispatch on the original file. */
    public void handleFileClick(File file, String fileName) {
        try {
            context.clearGreen(pane1);
        } catch (Exception ignored) {}
        if (BackupOpenPolicy.isBackupName(fileName)) {
            handleBackupClick(file, fileName);
            return;
        }
        openWithSavedDefaults(file, fileName, null, null,
                () -> dispatchFileClick(file, fileName));
    }

    private void handleBackupClick(File file, String fileName) {
        // Read only a header to recognize a native container; never restore a staged copy.
        new Thread(() -> {
            boolean nativeBackup = BackupOpenPolicy.isNativeBackup(file, fileName);
            boolean needsReadable = SafFs.isSaf(file) || !file.canRead();
            context.handler.post(() -> {
                if (needsReadable) {
                    withReadableCopy(file, readable -> completeBackupClick(file, readable, fileName));
                } else {
                    completeBackupClick(file, nativeBackup, fileName);
                }
            });
        }, "backup-open").start();
    }

    private void completeBackupClick(File file, File readable, String name) {
        if (BackupOpenPolicy.isNativeBackup(readable, name)) {
            openArchiveOrDefault(file, name);
        } else {
            completeBackupClick(file, false, name);
        }
    }

    private void completeBackupClick(File file, boolean nativeBackup, String fileName) {
        if (nativeBackup) {
            openArchiveOrDefault(file, fileName);
        } else if (UiPrefs.showRestoreDialog(context)) {
            EntryDialogs.showBackupRestoreDialog(context, dialogUtil, pane1, file);
        } else {
            String originalName = BackupOpenPolicy.originalName(fileName);
            if (originalName == null || originalName.isEmpty()) showOpenWithDialog(file, fileName);
            else handleFileClick(file, originalName);
        }
    }

    /** Ordinary dispatch after explicit defaults and backup routing have been resolved. */
    private void dispatchFileClick(File file, String fileName) {
        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        if (FileUtils.matchExt(ext, FileUtils.TEXT_EXTS)
            || fileName.endsWith(".txt") || fileName.endsWith(".json")
            || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
            || fileName.endsWith(".gradle") || fileName.endsWith(".properties") || fileName.endsWith(".prop")
            || fileName.endsWith(".conf") || fileName.endsWith(".cfg") || fileName.endsWith(".ini")
            || fileName.endsWith(".log") || fileName.endsWith(".md") || fileName.endsWith(".csv")
            || fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".toml")
            || fileName.endsWith(".rc")) {
            openTextEditorRootAware(file);
        } else if (FileUtils.isElf(fileName) || FileUtils.isElfFile(file)) {
            withReadableCopy(file, readable -> openElfFileDefault(readable, fileName, file));
        } else if (isKernelFile(file, fileName)) {
            withReadableCopy(file, readable -> showKernelOptionsDialog(readable, fileName, file));
        } else if (fileName.endsWith(".img") || fileName.endsWith(".iso") || file.getAbsolutePath().startsWith("/dev/block/")) {
            if (BootImageEngine.isBootImage(file)) {
                BootInspectManager.openBootImage(context, file, pane1);
            } else if (SystemImageEngine.isSparseImage(file)) {
                promptUnsparseImage(file, fileName);
            } else if (SystemImageMountManager.isMountableImage(file)) {
                SystemImageMountManager.showImageOptionsDialog(context, this, file, fileName, pane1);
            } else {
                openWithDefaultOrDialog(file, fileName);
            }
        } else if (("payload.bin".equalsIgnoreCase(fileName) || fileName.endsWith(".bin") || fileName.endsWith(".payload")) && PayloadParser.isPayload(file)) {
            PayloadDialog.showForPayload(context, file, pane1);
        } else if(HashUtil.isChecksumFile(fileName)) {
            // Entry names resolve next to the manifest: when the manifest was
            // staged from an elevated folder, keep the original parent as the
            // base dir instead of the staging cache.
            withReadableCopy(file, readable -> {
                if (checksumDialogs != null) checksumDialogs.showHashVerifyDialog(readable, file.getParentFile());
                else openWithDefaultOrDialog(readable, fileName);
            });
        } else if (fileName.endsWith(".xml") || fileName.endsWith(".axml")
            || FileUtils.isAxml(file) || FileUtils.isAbx(file)) {
            withReadableCopy(file, readable -> {
                try {
                    // Binary XML (ABX/AXML) is decoded by TextEditorActivity from
                    // the staged copy; decoding here and shipping the text through
                    // the intent can exceed the binder transaction limit on big
                    // system XMLs (/data/system/packages.xml) and crash with
                    // "Failure from system" (TransactionTooLargeException).
                    context.startActivity(rootAwareEditorIntent(readable, file)
                            .putExtra("path", readable.getPath()));
                    notifyFileOpened(readable);
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            });
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            withReadableCopy(file, readable -> {
                if (StickerUtil.isLottieSticker(readable)) {
                    StickerPreviewDialog.show(context, readable);
                } else {
                    context.openImageViewer(readable.getPath());
                }
                notifyFileOpened(readable);
            });
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            withReadableCopy(file, readable -> {
                context.playMediaFile(readable.getPath());
                notifyFileOpened(readable);
            });
        } else if ((ext.equals(".apk"))) {
            withReadableCopy(file, readable -> {
                if (apkTools != null) apkTools.showApkInfoDialog(readable, fileName);
                else openWithDefaultOrDialog(readable, fileName);
                notifyFileOpened(readable);
            });
        } else {
            if (fileName.endsWith(".arsc")) {
                withReadableCopy(file, readable -> showArscOpenWith(readable, null, "resources.arsc"));
            } else if (ArchiveUtil.isSupportedArchive(fileName)) {
                // Extension says archive: honor the user's default app first,
                // then the bytes-check, then the archive viewer.
                openArchiveOrDefault(file, fileName);
            } else if (fileName.endsWith(".apks") || fileName.endsWith(".xapk") || fileName.endsWith(".aspk") || fileName.endsWith(".apkm")) {
                withReadableCopy(file, readable -> showSplitApkMenu(readable, fileName));
            } else if (fileName.endsWith(".dex")) {
                if (fileOps != null) fileOps.showDexOptionsDialog(file, null, null, fileName);
                else openWithDefaultOrDialog(file, fileName);
            } else if (fileName.endsWith(".sh")) {
                withReadableCopy(file, readable -> showShellScriptDialog(readable, file, fileName));
            } else if (FileUtils.matchExt(ext, FileUtils.FONT_EXTS)) {
                withReadableCopy(file, readable -> showFontPreview(readable, fileName));
            } else if (ext.equals(".pdf")) {
                withReadableCopy(file, readable -> showPdfViewer(readable, fileName));
            } else if (FileUtils.matchExt(ext, FileUtils.HTML_EXTS)) {
                withReadableCopy(file, readable -> showHtmlViewer(readable, fileName));
            } else if (fileName.endsWith(".mtcr") || fileName.endsWith(".dexdiff")) {
                withReadableCopy(file, readable -> {
                    Intent intent = new Intent(context, CompareDexActivity.class)
                            .putExtra(CompareDexActivity.EXTRA_MTCR_FILE, readable.getAbsolutePath());
                    context.startActivity(intent);
                });
            } else if (fileName.equals("libapp.so") || fileName.equals("libflutter.so")) {
                withReadableCopy(file, readable -> FlutterToolsDialog.showForLibrary(context, readable, fileName));
            } else if (ext.equals(".db") || ext.equals(".sqlite") || ext.equals(".sqlite3") || ext.equals(".db3")) {
                openSqliteViewer(file);
            } else if (ArchiveBrowseUtil.isBrowsable(file)) {
                // Unhandled extension but the bytes are an archive (zip/7z/rar/
                // tar/gz/...): an .epub lands here. The user's default app wins
                // over the archive viewer; without one we open the viewer as before.
                openArchiveOrDefault(file, fileName);
            } else {
                openWithDefaultOrDialog(file, fileName);
            }
        }
    }

    private void promptUnsparseImage(File file, String fileName) {
        SystemImageEngine.SparseInfo sInfo =
                SystemImageEngine.readSparseInfo(file);
        long uncompressedSize = (sInfo != null) ? sInfo.uncompressedSizeBytes : 0;
        String sizeStr = uncompressedSize > 0 ? context.getString(R.string.unsparse_size_prefix,
                FileSize.getHumanReadableFileSize(uncompressedSize)) : "";

        String msg = context.getString(R.string.unsparse_image_msg) + sizeStr;

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.unsparse_image_title)
                .setMessage(msg)
                .setPositiveButton(R.string.unsparse_btn, (d, w) -> {
                    String baseName = fileName;
                    int dotIdx = baseName.lastIndexOf('.');
                    if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);
                    File outRaw = new File(file.getParentFile(), baseName + "_raw.img");

                    ProgressManager pm = new ProgressManager(context, false).show();
                    pm.setText(context.getString(R.string.unsparsing_progress, fileName));

                    new Thread(() -> {
                        try {
                            SystemImageEngine.unsparseToFile(file, outRaw,
                                    new SystemImageEngine.ProgressCallback() {
                                @Override
                                public void onProgress(String message, int percent) {
                                    context.runOnUiThread(() -> {
                                        pm.setText(message);
                                        pm.setProgress(percent, 100);
                                    });
                                }

                                @Override
                                public void onError(String error) {
                                    context.runOnUiThread(() -> {
                                        pm.dismiss();
                                        Extensions.showMessage(context, "Error: " + error);
                                    });
                                }

                                @Override
                                public void onSuccess(File result) {
                                    context.runOnUiThread(() -> {
                                        pm.dismiss();
                                        Toast.makeText(context,
                                                context.getString(R.string.unsparsed_success, result.getName()),
                                                Toast.LENGTH_LONG).show();
                                        context.reloadCurrentFolder();
                                    });
                                }
                            });
                        } catch (Exception e) {
                            context.runOnUiThread(() -> {
                                pm.dismiss();
                                Extensions.showMessage(context, "Unsparse failed: " + e.getMessage());
                            });
                        }
                    }).start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.open_with, (d, w) -> openWithDefaultOrDialog(file, fileName))
                .show();
    }

    private File stageZipEntry(ZipEntryInfo zipEntry) throws IOException {
        if (zipEntry == null || zipEntry.isDirectory() || zipEntry.getFullPath() == null) throw new IOException(context.getString(R.string.cannot_open_item));
        File out = new File(context.getCacheDir(), "zip_entry_" + System.currentTimeMillis() + "_" + zipEntry.getName().replaceAll("[^a-zA-Z0-9._-]", "_"));
        ArchiveBrowseUtil.extractEntry(zipEntry.getZipFile(), zipEntry.getFullPath(), out);
        return out;
    }

    public void shareZipEntry(Object item, String fileName) {
        if (!(item instanceof ZipEntryInfo)) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        new Thread(() -> {
            try {
                File staged = stageZipEntry((ZipEntryInfo) item);
                context.handler.post(() -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", staged);
                        String shareMime = MimeUtil.getMimeTypeForAction(context, staged);
                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType(shareMime != null ? shareMime : "application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share " + fileName));
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    public void shareZipEntries(List<ZipEntryInfo> entries) {
        if (entries == null || entries.isEmpty()) return;
        new Thread(() -> {
            ArrayList<Uri> uris = new ArrayList<>();
            IOException failure = null;
            for (ZipEntryInfo entry : entries) {
                if (entry == null || entry.isDirectory()) continue;
                try {
                    File staged = stageZipEntry(entry);
                    uris.add(FileProvider.getUriForFile(context, context.getPackageName() + ".provider", staged));
                } catch (IOException e) {
                    if (failure == null) failure = e;
                }
            }
            final IOException finalFailure = failure;
            context.handler.post(() -> {
                try {
                    if (uris.isEmpty()) {
                        if (finalFailure != null) new ErrorUtil(context).showError(finalFailure);
                        else Extensions.showMessage(context, R.string.cannot_open_item);
                        return;
                    }
                    if (uris.size() == 1) {
                        context.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                                .setType("application/octet-stream")
                                .putExtra(Intent.EXTRA_STREAM, uris.get(0))
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share 1 file"));
                    } else {
                        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                                .setType("*/*")
                                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(Intent.createChooser(intent, "Share " + uris.size() + " files"));
                    }
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            });
        }).start();
    }

    public void openWithZipEntry(Object item, String fileName) {
        if (!(item instanceof ZipEntryInfo)) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        final ZipEntryInfo entry = (ZipEntryInfo) item;
        new Thread(() -> {
            try {
                File staged = stageZipEntry(entry);
                context.handler.post(() -> {
                    try {
                        showOpenWithDialog(staged, fileName, entry.getZipFile(), entry.getFullPath());
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    private long calculateTotalSize(List<File> files) {
        if (files == null || files.isEmpty()) return 0;
        long total = 0;
        ArrayDeque<File> stack = new ArrayDeque<>();
        HashSet<String> seen = new HashSet<>();
        for (File f : files) {
            if (f == null) continue;
            if (f.isFile()) {
                total += f.length();
            } else if (f.isDirectory()) {
                stack.add(f);
            }
        }
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
            File[] kids = d.listFiles();
            if (kids == null) {
                long rootSize = getRootFolderSize(d.getAbsolutePath());
                if (rootSize > 0) total += rootSize;
                continue;
            }
            for (File k : kids) {
                if (k == null) continue;
                if (k.isFile()) {
                    total += k.length();
                } else if (k.isDirectory()) {
                    stack.add(k);
                }
            }
        }
        return total;
    }

    private long getRootFolderSize(String path) {
        try {
            if (!PrivilegeManager.Files.fileOpsOn(context)) return 0;
            String cmd = "du -sb " + ShellQuote.escapeShellArg(path);
            String out = null;
            if (PrivilegeManager.Files.active(context) == PrivilegeManager.Backend.ROOT) {
                ShellSession.ShellResult r = PrivilegeManager.Root.execute(context, cmd, 5);
                if (r != null && r.isSuccess()) out = r.output();
            } else if (PrivilegeManager.Shizuku.ready()) {
                ShellSession.Result r = PrivilegeManager.shizuku().exec(cmd, 5);
                if (r != null && r.success) out = r.stdout;
            }
            if (out != null) {
                String first = out.trim().split("\\s+")[0];
                return Long.parseLong(first);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private long calculateZipEntryFolderSize(ZipEntryInfo entry) {
        if (entry == null || entry.getZipFile() == null) return 0;
        // Charset applied so the prefix matches the browsed entry names.
        try (ZipFile srcZip = ZipCompat.openZip(entry.getZipFile())) {
            String prefix = entry.getFullPath();
            if (!prefix.endsWith("/")) prefix += "/";
            long total = 0;
            for (FileHeader fh : srcZip.getFileHeaders()) {
                if (fh.getFileName().startsWith(prefix) && !fh.isDirectory()) {
                    total += fh.getUncompressedSize();
                }
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void showShareZipConfirmDialog(String baseName, List<File> filesToMeasure, Runnable onConfirm) {
        AtomicBoolean shown = new AtomicBoolean(false);
        AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();

        Runnable showInitial = () -> {
            if (shown.compareAndSet(false, true)) {
                AlertDialog d = new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.share_zip)
                        .setMessage(context.getString(R.string.confirm_share_zip, baseName))
                        .setPositiveButton(R.string.share, (dia, w) -> onConfirm.run())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                dialogRef.set(d);
            }
        };
        context.handler.postDelayed(showInitial, 80);

        new Thread(() -> {
            long size = calculateTotalSize(filesToMeasure);
            String nameWithSize = size > 0
                    ? baseName + " (" + FileSize.getHumanReadableFileSize(size) + ")"
                    : baseName;
            context.handler.post(() -> {
                context.handler.removeCallbacks(showInitial);
                if (shown.compareAndSet(false, true)) {
                    new MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.share_zip)
                            .setMessage(context.getString(R.string.confirm_share_zip, nameWithSize))
                            .setPositiveButton(R.string.share, (dia, w) -> onConfirm.run())
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else {
                    AlertDialog d = dialogRef.get();
                    if (d != null && d.isShowing()) {
                        d.setMessage(context.getString(R.string.confirm_share_zip, nameWithSize));
                    }
                }
            });
        }).start();
    }

    public void shareFolderAsZip(File folder) {
        if (folder == null) return;
        showShareZipConfirmDialog(folder.getName(), Collections.singletonList(folder), () -> doShareFolderAsZip(folder));
    }

    private void doShareFolderAsZip(File folder) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(context.getString(R.string.compressing));
        pm.show();
        new Thread(() -> {
            File zipFile = null;
            File stagedDir = null;
            try {
                File cacheDir = new File(context.getCacheDir(), "shared_zips");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File[] existing = cacheDir.listFiles();
                if (existing != null) {
                    long cutoff = System.currentTimeMillis() - (60 * 60 * 1000L);
                    for (File f : existing) {
                        if (f.lastModified() < cutoff) FileUtils.deleteTree(f, null);
                    }
                }
                zipFile = new File(cacheDir, folder.getName() + ".zip");
                if (zipFile.exists()) zipFile.delete();

                File sourceToCompress = folder;
                if (PrivilegeManager.Staging.needsStaging(context, folder)) {
                    stagedDir = new File(context.getCacheDir(), "stage_share_" + System.currentTimeMillis());
                    stagedDir.mkdirs();
                    sourceToCompress = new File(stagedDir, folder.getName());
                    PrivilegeManager.Files.stageTree(context, folder.getAbsolutePath(), sourceToCompress);
                }

                ZipParameters params = new ZipParameters();
                params.setCompressionMethod(CompressionMethod.DEFLATE);
                params.setCompressionLevel(CompressionLevel.NORMAL);
                try (ZipFile zf = new ZipFile(zipFile)) {
                    zf.addFolder(sourceToCompress, params);
                }

                final File finalZip = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    try {
                        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", finalZip);
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("application/zip");
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share) + " " + finalZip.getName()));
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                final File fz = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    if (fz != null && fz.exists()) fz.delete();
                    new ErrorUtil(context).showError(e);
                });
            } finally {
                if (stagedDir != null) {
                    FileUtils.deleteTree(stagedDir, null);
                }
            }
        }).start();
    }

    public void shareFoldersAsZip(List<File> files) {
        if (files == null || files.isEmpty()) return;
        if (files.size() == 1 && files.get(0).isDirectory()) {
            shareFolderAsZip(files.get(0));
            return;
        }
        String name = files.get(0).getParentFile() != null ? files.get(0).getParentFile().getName() : files.get(0).getName();
        showShareZipConfirmDialog(name, files, () -> doShareFilesAsZip(files, name));
    }

    private void doShareFilesAsZip(List<File> files, String baseName) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(context.getString(R.string.compressing));
        pm.show();
        new Thread(() -> {
            File zipFile = null;
            File stagedDir = null;
            try {
                File cacheDir = new File(context.getCacheDir(), "shared_zips");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File[] existing = cacheDir.listFiles();
                if (existing != null) {
                    long cutoff = System.currentTimeMillis() - (60 * 60 * 1000L);
                    for (File f : existing) {
                        if (f.lastModified() < cutoff) FileUtils.deleteTree(f, null);
                    }
                }
                zipFile = new File(cacheDir, (baseName != null && !baseName.isEmpty() ? baseName : "archive") + ".zip");
                if (zipFile.exists()) zipFile.delete();

                ZipParameters params = new ZipParameters();
                params.setCompressionMethod(CompressionMethod.DEFLATE);
                params.setCompressionLevel(CompressionLevel.NORMAL);
                try (ZipFile zf = new ZipFile(zipFile)) {
                    for (File f : files) {
                        File source = f;
                        if (PrivilegeManager.Staging.needsStaging(context, f)) {
                            if (stagedDir == null) {
                                stagedDir = new File(context.getCacheDir(), "stage_share_" + System.currentTimeMillis());
                                stagedDir.mkdirs();
                            }
                            source = new File(stagedDir, f.getName());
                            if (f.isDirectory()) PrivilegeManager.Files.stageTree(context, f.getAbsolutePath(), source);
                            else PrivilegeManager.Files.stageFile(context, f.getAbsolutePath(), source);
                        }
                        if (source.isDirectory()) zf.addFolder(source, params);
                        else zf.addFile(source, params);
                    }
                }

                final File finalZip = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    try {
                        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", finalZip);
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("application/zip");
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share) + " " + finalZip.getName()));
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                final File fz = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    if (fz != null && fz.exists()) fz.delete();
                    new ErrorUtil(context).showError(e);
                });
            } finally {
                if (stagedDir != null) {
                    FileUtils.deleteTree(stagedDir, null);
                }
            }
        }).start();
    }

    public void shareMixedFilesAndFolders(List<File> folders, List<File> plainFiles) {
        if (folders == null || folders.isEmpty()) return;
        String name = folders.size() == 1
                ? folders.get(0).getName()
                : folders.get(0).getName() + "_and_more";
        List<File> all = new ArrayList<>(folders.size() + (plainFiles != null ? plainFiles.size() : 0));
        all.addAll(folders);
        if (plainFiles != null) all.addAll(plainFiles);
        showShareZipConfirmDialog(name, all, () -> doShareMixedFilesAndFolders(folders, plainFiles, name));
    }

    private void doShareMixedFilesAndFolders(List<File> folders, List<File> plainFiles, String baseName) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(context.getString(R.string.compressing));
        pm.show();
        new Thread(() -> {
            File zipFile = null;
            File stagedDir = null;
            try {
                File cacheDir = new File(context.getCacheDir(), "shared_zips");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                File[] existing = cacheDir.listFiles();
                if (existing != null) {
                    long cutoff = System.currentTimeMillis() - (60 * 60 * 1000L);
                    for (File f : existing) {
                        if (f.lastModified() < cutoff) FileUtils.deleteTree(f, null);
                    }
                }
                zipFile = new File(cacheDir, (baseName != null && !baseName.isEmpty() ? baseName : "archive") + ".zip");
                if (zipFile.exists()) zipFile.delete();

                ZipParameters params = new ZipParameters();
                params.setCompressionMethod(CompressionMethod.DEFLATE);
                params.setCompressionLevel(CompressionLevel.NORMAL);
                try (ZipFile zf = new ZipFile(zipFile)) {
                    for (File f : folders) {
                        File source = f;
                        if (PrivilegeManager.Staging.needsStaging(context, f)) {
                            if (stagedDir == null) {
                                stagedDir = new File(context.getCacheDir(), "stage_share_" + System.currentTimeMillis());
                                stagedDir.mkdirs();
                            }
                            source = new File(stagedDir, f.getName());
                            PrivilegeManager.Files.stageTree(context, f.getAbsolutePath(), source);
                        }
                        if (source.isDirectory()) zf.addFolder(source, params);
                        else zf.addFile(source, params);
                    }
                }

                List<Uri> uris = new ArrayList<>();
                uris.add(FileProvider.getUriForFile(context, context.getPackageName() + ".provider", zipFile));

                if (plainFiles != null) {
                    for (File sf : plainFiles) {
                        File readableFile = sf;
                        if (SafFs.isSaf(sf) || !(sf.exists() && sf.canRead() && appReadable(sf))) {
                            if (PrivilegeManager.Files.fileOpsOn(context)) {
                                readableFile = PrivilegeManager.Staging.stageForRead(context, sf.getAbsolutePath());
                            }
                        }
                        if (readableFile != null && readableFile.exists()) {
                            try {
                                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", readableFile);
                                uris.add(uri);
                            } catch (Exception ignored) {}
                        }
                    }
                }

                final File finalZip = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    try {
                        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        intent.setType("*/*");
                        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share) + " " + uris.size() + " files"));
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                final File fz = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    if (fz != null && fz.exists()) fz.delete();
                    new ErrorUtil(context).showError(e);
                });
            } finally {
                if (stagedDir != null) {
                    FileUtils.deleteTree(stagedDir, null);
                }
            }
        }).start();
    }

    public void shareZipFolderAsZip(ZipEntryInfo entry) {
        if (entry == null) return;
        AtomicBoolean shown = new AtomicBoolean(false);
        AtomicReference<AlertDialog> dialogRef = new AtomicReference<>();

        Runnable showInitial = () -> {
            if (shown.compareAndSet(false, true)) {
                AlertDialog d = new MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.share_zip)
                        .setMessage(context.getString(R.string.confirm_share_zip, entry.getName()))
                        .setPositiveButton(R.string.share, (dia, w) -> doShareZipFolderAsZip(entry))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                dialogRef.set(d);
            }
        };
        context.handler.postDelayed(showInitial, 80);

        new Thread(() -> {
            long size = calculateZipEntryFolderSize(entry);
            String nameWithSize = size > 0
                    ? entry.getName() + " (" + FileSize.getHumanReadableFileSize(size) + ")"
                    : entry.getName();
            context.handler.post(() -> {
                context.handler.removeCallbacks(showInitial);
                if (shown.compareAndSet(false, true)) {
                    new MaterialAlertDialogBuilder(context)
                            .setTitle(R.string.share_zip)
                            .setMessage(context.getString(R.string.confirm_share_zip, nameWithSize))
                            .setPositiveButton(R.string.share, (dia, w) -> doShareZipFolderAsZip(entry))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else {
                    AlertDialog d = dialogRef.get();
                    if (d != null && d.isShowing()) {
                        d.setMessage(context.getString(R.string.confirm_share_zip, nameWithSize));
                    }
                }
            });
        }).start();
    }

    private void doShareZipFolderAsZip(ZipEntryInfo entry) {
        ProgressManager pm = new ProgressManager(context, true);
        pm.setText(context.getString(R.string.compressing));
        pm.show();
        new Thread(() -> {
            File zipFile = null;
            File stageExtractDir = null;
            try {
                File cacheDir = new File(context.getCacheDir(), "shared_zips");
                if (!cacheDir.exists()) cacheDir.mkdirs();
                zipFile = new File(cacheDir, entry.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + ".zip");
                if (zipFile.exists()) zipFile.delete();

                stageExtractDir = new File(context.getCacheDir(), "stage_zip_share_" + System.currentTimeMillis());
                File extractTarget = new File(stageExtractDir, entry.getName());
                extractTarget.mkdirs();

                try (ZipFile srcZip = ZipCompat.openZip(entry.getZipFile())) {
                    String prefix = entry.getFullPath();
                    if (!prefix.endsWith("/")) prefix += "/";
                    for (FileHeader fh : srcZip.getFileHeaders()) {
                        if (fh.getFileName().startsWith(prefix)) {
                            String rel = fh.getFileName().substring(prefix.length());
                            if (rel.isEmpty()) continue;
                            if (fh.isDirectory()) {
                                new File(extractTarget, rel).mkdirs();
                            } else {
                                File dest = new File(extractTarget, rel);
                                if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
                                srcZip.extractFile(fh, extractTarget.getAbsolutePath(), rel);
                            }
                        }
                    }
                }

                ZipParameters params = new ZipParameters();
                params.setCompressionMethod(CompressionMethod.DEFLATE);
                params.setCompressionLevel(CompressionLevel.NORMAL);
                try (ZipFile outZf = new ZipFile(zipFile)) {
                    outZf.addFolder(extractTarget, params);
                }

                final File finalZip = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    try {
                        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", finalZip);
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("application/zip");
                        intent.putExtra(Intent.EXTRA_STREAM, uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share) + " " + finalZip.getName()));
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                final File fz = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    if (fz != null && fz.exists()) fz.delete();
                    new ErrorUtil(context).showError(e);
                });
            } finally {
                if (stageExtractDir != null) {
                    FileUtils.deleteTree(stageExtractDir, null);
                }
            }
        }).start();
    }

    private static boolean appReadable(File file) {
        try {
            if (PrivilegeManager.Shizuku.isAndroidDataPath(file))
                return new File(file.getAbsolutePath()).canRead();
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    public void withReadableCopy(File file, ReadableCallback cb) {
        if (file == null) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        if (SafFs.isSaf(file)) {
            // A provider document is answered by staging a real copy first;
            // every handler below then works on it unchanged.
            Extensions.showMessage(context, R.string.reading_from_provider);
            new Thread(() -> {
                try {
                    File staged = PrivilegeManager.Staging.stageForRead(context, file.getAbsolutePath());
                    context.handler.post(() -> {
                        try {
                            cb.onReady(staged);
                        } catch (Exception e) {
                            new ErrorUtil(context).showError(e);
                        }
                    });
                } catch (Exception e) {
                    context.handler.post(() -> new ErrorUtil(context).showError(e));
                }
            }).start();
            return;
        }
        try {
            if (file != null) {
                // If path is under /mnt/media_rw/[UUID], check if equivalent /storage/[UUID] exists and is readable directly
                String p = file.getAbsolutePath();
                if (p.startsWith("/mnt/media_rw/")) {
                    String sub = p.substring("/mnt/media_rw/".length());
                    File alt = new File("/storage/" + sub);
                    if (alt.exists() && alt.canRead() && appReadable(alt)) {
                        cb.onReady(alt);
                        return;
                    }
                }
                if (file.exists() && file.canRead() && appReadable(file)) {
                    cb.onReady(file);
                    return;
                }
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        if (!PrivilegeManager.Files.fileOpsOn(context)) {
            Extensions.showMessage(context, R.string.cannot_open_permission_denied);
            return;
        }
        Extensions.showMessage(context, R.string.reading_with_elevated_access);
        new Thread(() -> {
            try {
                File staged = PrivilegeManager.Staging.stageForRead(context, file.getAbsolutePath());
                context.handler.post(() -> {
                    try {
                        cb.onReady(staged);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    private Intent rootAwareEditorIntent(File readable, File original) {
        Intent i = new Intent(context, TextEditorActivity.class);
        if (readable != null && original != null
                && !readable.getAbsolutePath().equals(original.getAbsolutePath())) {
            i.putExtra("rootOriginalPath", original.getAbsolutePath());
            // The editor saves through PrivilegeManager.Staging.writeBack, which streams a
            // provider path back into its document; say so instead of "root".
            Extensions.showMessage(context, SafFs.isSaf(original)
                    ? R.string.opened_from_provider : R.string.opened_with_root);
        }
        return i;
    }

    private void openTextEditorRootAware(File file) {
        withReadableCopy(file, readable -> {
            context.startActivity(rootAwareEditorIntent(readable, file)
                    .putExtra("path", readable.getAbsolutePath()));
            notifyFileOpened(readable);
        });
    }

    private void openTextEditorWithOriginal(File readable, File original) {
        if (readable == null || !readable.isFile()) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        File orig = original != null ? original : readable;
        context.startActivity(rootAwareEditorIntent(readable, orig)
                .putExtra("path", readable.getAbsolutePath()));
        notifyFileOpened(readable);
    }

    private void openHexEditorWithOriginal(File readable, File original) {
        openHexEditorWithOriginal(readable, original, -1);
    }

    private void openHexEditorWithOriginal(File readable, File original, long offset) {
        if (readable == null || !readable.isFile()) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        File orig = original != null ? original : readable;
        Intent i = new Intent(context, HexEditorActivity.class)
                .putExtra("path", readable.getAbsolutePath());
        if (offset >= 0) {
            i.putExtra("offset", offset);
        }
        if (!readable.getAbsolutePath().equals(orig.getAbsolutePath())) {
            i.putExtra("rootOriginalPath", orig.getAbsolutePath());
            Extensions.showMessage(context, R.string.opened_with_root);
        }
        context.startActivity(i);
        notifyFileOpened(readable);
    }

    /**
     * Opens {@code file} in the built-in text editor. When the file was staged out of an
     * archive, the archive and entry path are forwarded (with request code 757) so the
     * editor returns through {@code handleModifiedFileResult} and MainActivity can offer to
     * add the edit back to the archive.
     */
    private void openTextEditor(File file, File zipFile, String zipEntryPath) {
        if (zipFile != null && zipEntryPath != null && !zipEntryPath.isEmpty()) {
            context.startActivityForResult(new Intent(context, TextEditorActivity.class)
                    .putExtra("zf", zipFile.getPath())
                    .putExtra("zipEntryPath", zipEntryPath)
                    .putExtra("path", file.getAbsolutePath()), 757);
            return;
        }
        openTextEditorRootAware(file);
    }

    private void openHexEditorRootAware(File file) {
        openHexEditorRootAware(file, null, null);
    }

    private void openHexEditorRootAware(File file, File zipFile, String zipEntryPath) {
        withReadableCopy(file, readable -> {
            Intent i = new Intent(context, HexEditorActivity.class)
                    .putExtra("path", readable.getAbsolutePath());
            if (zipFile != null) {
                i.putExtra("zf", zipFile.getAbsolutePath());
                i.putExtra("zipFilePath", zipFile.getAbsolutePath());
            }
            if (zipEntryPath != null) {
                i.putExtra("zipEntryPath", zipEntryPath);
            }
            if (!readable.getAbsolutePath().equals(file.getAbsolutePath())) {
                i.putExtra("rootOriginalPath", file.getAbsolutePath());
                Extensions.showMessage(context, R.string.opened_with_root);
            }
            context.startActivity(i);
            notifyFileOpened(readable);
        });
    }

    private void restoreBakRootAware(File bakFile, File origFile, String fileName) {
        String bakPath = bakFile.getPath();
        String origPath = origFile.getPath();
        boolean useElevated = PrivilegeManager.Files.fileOpsOn(context)
                && (PrivilegeManager.Staging.needsStaging(context, bakPath) || PrivilegeManager.Staging.needsStaging(context, origPath));
        if (useElevated) {
            new Thread(() -> {
                try {
                    boolean origExists = PrivilegeManager.Files.exists(context, origPath) || origFile.exists();
                    if (origExists) PrivilegeManager.Files.rename(context, origPath, origPath + "_tmp_.bak", true);
                    PrivilegeManager.Files.rename(context, bakPath, origPath, true);
                    if (origExists) PrivilegeManager.Files.rename(context, origPath + "_tmp_.bak", bakPath, true);
                    context.handler.post(() -> context.loadFolderInPane(
                            bakFile.getParentFile() != null ? bakFile.getParentFile() : new File("/"), pane1));
                } catch (Exception e) {
                    context.handler.post(() -> new ErrorUtil(context).showError(e));
                }
            }).start();
            return;
        }
        boolean origExists = origFile.exists();
        File tmpFile = new File(origPath + "_tmp_.bak");
        if (origExists) {
            //noinspection ResultOfMethodCallIgnored
            origFile.renameTo(tmpFile);
        }
        //noinspection ResultOfMethodCallIgnored
        bakFile.renameTo(new File(origPath));
        if (origExists) {
            //noinspection ResultOfMethodCallIgnored
            tmpFile.renameTo(new File(bakPath));
        }
        context.handler.post(() -> context.loadFolderInPane(
                bakFile.getParentFile() != null ? bakFile.getParentFile() : new File("/"), pane1));
    }

    private void showSplitApkMenu(File readable, String displayName) {
        String[] items = new String[] {
                context.rss.getString(R.string.install),
                context.rss.getString(R.string.view),
                context.rss.getString(R.string.apk_info),
                context.rss.getString(R.string.antisplit_merge_to_apk)
        };
        dialogUtil.styleAlertDialog(
                dialogUtil.getDialogBuilder().setSingleChoiceItems(items, -1, (dialog, which) -> {
                    dialog.dismiss();
                    try {
                        switch (which) {
                            case 0:
                                if (LegacyUtils.aboveSdk20) {
                                    new Thread(() -> {
                                        try (ZipFile zf = new ZipFile(readable)) {
                                            List<File> apkFiles = new ArrayList<>();
                                            File tmpDir = new File(context.getCacheDir(), "split_install_" + System.currentTimeMillis());
                                            //noinspection ResultOfMethodCallIgnored
                                            tmpDir.mkdirs();
                                            for (FileHeader fh : zf.getFileHeaders()) {
                                                if (fh.getFileName().endsWith(".apk")) {
                                                    File tmpApk = new File(tmpDir, new File(fh.getFileName()).getName());
                                                    try (InputStream is = zf.getInputStream(fh);
                                                         FileOutputStream fos = new FileOutputStream(tmpApk)) {
                                                        byte[] buf = new byte[65536];
                                                        int n;
                                                        while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                                                    } catch (EOFException | ZipException cappedRead) {
                                                        ZipCompat.extractEntryTolerant(readable, fh.getFileName(), tmpApk);
                                                    }
                                                    apkFiles.add(tmpApk);
                                                }
                                            }
                                            if (!apkFiles.isEmpty()) {
                                                InstallUtil.installSplitApksWithDialog(context, apkFiles, displayName);
                                            } else {
                                                context.runOnUiThread(() -> Extensions.showMessage(context, R.string.no_apk_files_found));
                                            }
                                        } catch (OutOfMemoryError oom) {
                                            // Errors are not caught below; a huge
                                            // .apks should fail with a message,
                                            // not a crash.
                                            context.runOnUiThread(() -> Extensions.showMessage(context, R.string.out_of_memory_operation));
                                        } catch (Exception e) {
                                            context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
                                        }
                                    }).start();
                                } else {
                                    Extensions.showMessage(context, "Installing split APKs is not supported on this version of Android :(");
                                    context.handler.postDelayed(() -> Extensions.showMessage(context, "You could try merging the APK then installing it"), 1500);
                                }
                                break;
                            case 1:
                                context.loadZipFolderInPane(readable, "", pane1, true);
                                break;
                            case 2:
                                // App/APK Info of the base APK inside the APKS
                                new Thread(() -> {
                                    try (ZipFile zf = new ZipFile(readable)) {
                                        FileHeader baseHeader = null;
                                        for (FileHeader fh : zf.getFileHeaders()) {
                                            String fn = fh.getFileName().toLowerCase(Locale.ROOT);
                                            if (fn.endsWith("base.apk")) {
                                                baseHeader = fh;
                                                break;
                                            } else if (baseHeader == null && fn.endsWith(".apk")) {
                                                baseHeader = fh;
                                            }
                                        }
                                        if (baseHeader != null) {
                                            File tmpApk = new File(context.getCacheDir(), "split_base_" + System.currentTimeMillis() + ".apk");
                                            try (InputStream is = zf.getInputStream(baseHeader);
                                                 FileOutputStream fos = new FileOutputStream(tmpApk)) {
                                                byte[] buf = new byte[65536];
                                                int n;
                                                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
                                            } catch (Exception e) {
                                                ZipCompat.extractEntryTolerant(readable, baseHeader.getFileName(), tmpApk);
                                            }
                                            File finalTmpApk = tmpApk;
                                            String baseName = new File(baseHeader.getFileName()).getName();
                                            context.runOnUiThread(() -> {
                                                if (apkTools != null) apkTools.showApkInfoDialog(finalTmpApk, baseName, true);
                                            });
                                        } else {
                                            context.runOnUiThread(() -> Extensions.showMessage(context, R.string.no_apk_files_found));
                                        }
                                    } catch (Exception e) {
                                        context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
                                    }
                                }).start();
                                break;
                            case 3:
                                MergeUtil.showAntisplitDialog(readable, context);
                                break;
                        }
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                }).create());
    }

    private void showArscOpenWith(File arscFile, File apkFile, String entryPath) {
        String[] options = {context.rss.getString(R.string.arsc_editor_plus), context.rss.getString(R.string.arsc_editor), context.rss.getString(R.string.translation_mode), context.rss.getString(R.string.resource_querier)};
        String[] modes = {
                ArscEditorPlusActivity.MODE_PLUS,
                ArscEditorPlusActivity.MODE_EDITOR,
                ArscEditorPlusActivity.MODE_TRANSLATE,
                ArscEditorPlusActivity.MODE_QUERIER};
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.open_with)
                .setSingleChoiceItems(options, -1, (dialog, which) -> {
                    dialog.dismiss();
                    Class<?> target = ArscEditorPlusActivity.MODE_EDITOR.equals(modes[which])
                            ? ArscEditorActivity.class
                            : ArscEditorPlusActivity.class;
                    Intent arscIntent = new Intent(context, target)
                            .putExtra("path", arscFile.getAbsolutePath())
                            .putExtra("apkPath", apkFile == null ? null : apkFile.getAbsolutePath())
                            .putExtra("zipEntryPath", entryPath)
                            .putExtra("arscMode", modes[which]);
                    // Inside an archive the editor only edits the extracted copy and
                    // returns it via setResult(757); MainActivity then shows the
                    // "APK/ZIP updated" prompt and injects the file itself.
                    if (apkFile != null) context.startActivityForResult(arscIntent, 757);
                    else context.startActivity(arscIntent);
                }).create());
    }
}
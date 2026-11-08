package io.github.abdurazaaqmohammed.features.files;

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

import io.github.abdurazaaqmohammed.boot.BootImageEngine;
import io.github.abdurazaaqmohammed.boot.BootInspectManager;
import io.github.abdurazaaqmohammed.drawable.ApkDrawableRenderer;
import io.github.abdurazaaqmohammed.ui.dialogs.DrawablePreviewDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.StickerPreviewDialog;
import io.github.abdurazaaqmohammed.utils.ApkIconHelper;
import io.github.abdurazaaqmohammed.utils.FileSize;
import io.github.abdurazaaqmohammed.utils.SplitArchiveUtil;
import io.github.abdurazaaqmohammed.features.payload.PayloadDialog;
import io.github.abdurazaaqmohammed.features.payload.PayloadParser;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuFile;
import io.github.abdurazaaqmohammed.adapters.main.ApkToolsHandler;
import io.github.abdurazaaqmohammed.adapters.main.ChecksumDialogs;
import io.github.abdurazaaqmohammed.adapters.main.FileOperationsHelper;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.arsc.ArscEditorPlusActivity;
import io.github.abdurazaaqmohammed.arsc.ArscEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.CompareDexActivity;
import io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.HtmlViewerActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SafFs;
import io.github.abdurazaaqmohammed.utils.ShizukuManager;
import io.github.abdurazaaqmohammed.MPManager.shizuku.ShizukuShell;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.StickerUtil;
import io.github.abdurazaaqmohammed.utils.ZipCompat;
import io.github.abdurazaaqmohammed.utils.nativelib.ElfParser;
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
            showElfOptionsDialog(file, fileName, file);
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
                .setNeutralButton(context.rss.getString(R.string.more), (d, w) -> withReadableCopy(file, readable -> showAppsForMime(readable, fileName, useActualSwitch.isChecked())))
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
        return "openwith_default_" + mime.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
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
                });
                return;
            case BUILTIN_IMAGE:
                if (file == null) return;
                withReadableCopy(file, readable -> context.openImageViewer(readable.getAbsolutePath()));
                return;
            case BUILTIN_HEX:
                if (file == null || !file.isFile()) {
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }
                openHexEditorRootAware(file);
                return;
            case BUILTIN_MEDIA:
                if (file == null) return;
                withReadableCopy(file, readable -> context.playMediaFile(readable.getAbsolutePath()));
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
                });
                return;
            }
            case BUILTIN_FONT:
                if (file == null) return;
                showFontPreview(file, fileName);
                return;
            case BUILTIN_PDF:
                if (file == null) return;
                showPdfViewer(file, fileName);
                return;
            case BUILTIN_HTML:
                if (file == null) return;
                showHtmlViewer(file, fileName);
                return;
            case BUILTIN_ARSC:
                if (file == null) return;
                withReadableCopy(file, readable -> showArscOpenWith(readable, null, "resources.arsc"));
                return;
            case BUILTIN_XML:
                if (file == null) return;
                showXmlFunctions(file, fileName);
                return;
            case BUILTIN_SIG:
                if (file == null) return;
                importSignature(file, fileName);
                return;
            case BUILTIN_DRAWABLE:
                if (file == null) return;
                if (zipFile != null && zipEntryPath != null) {
                    DrawablePreviewDialog.show(context, zipFile, zipEntryPath);
                } else {
                    DrawablePreviewDialog.showFile(context, file);
                }
                return;
            default:
                return;
        }
    }

    private boolean tryRunBuiltinDefault(File file, String fileName) {
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
                runBuiltinTool(def, file, fileName, null, null);
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
        intent.setClassName(info.activityInfo.packageName, info.activityInfo.name);
        context.startActivity(intent);
    }

    private boolean tryLaunchDefaultApp(Uri uri, String mime) {
        if (mime == null) return false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String def = prefs.getString(defaultAppKey(mime), null);
        if (def == null) return false;
        ComponentName cn = ComponentName.unflattenFromString(def);
        if (cn == null) return false;
        PackageManager pm = context.getPackageManager();
        try {
            pm.getActivityInfo(cn, 0);
            Intent probe = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime);
            probe.setComponent(cn);
            List<ResolveInfo> stillThere = pm.queryIntentActivities(probe, 0);
            if (!stillThere.isEmpty()) {
                probe.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(probe);
                return true;
            }
        } catch (Exception ignored) {
        }
        prefs.edit().remove(defaultAppKey(mime)).apply();
        return false;
    }

    /**
     * Probes the MIME types the external default app could be registered under
     * (the app-reported one, then the sniffed and content-detected ones) and
     * launches the user's external default (Open with → More → long-press →
     * Set as default), if any. Returns true when one matched and was launched.
     */
    private boolean tryLaunchExternalDefault(Uri uri, File readable) {
        String actionMime = MimeUtil.getMimeTypeForAction(context, readable);
        if (actionMime == null) actionMime = "application/octet-stream";
        if (tryLaunchDefaultApp(uri, actionMime)) return true;
        try {
            String real = MimeUtil.getRealMimeType(readable);
            if (real != null && !real.equals(actionMime) && tryLaunchDefaultApp(uri, real)) return true;
            String reported = MimeUtil.getReportedMimeType(context, readable);
            if (reported != null && !reported.equals(actionMime) && (real == null || !reported.equals(real)) && tryLaunchDefaultApp(uri, reported)) return true;
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
            Uri uri = null;
            try {
                uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
            } catch (Exception ignored) {
            }
            if (uri != null && tryLaunchExternalDefault(uri, readable)) return;
            if (staged || !ArchiveBrowseUtil.isBrowsable(readable)) {
                openWithDefaultOrDialog(readable, fileName);
                return;
            }
            openArchiveSafe(readable);
        });
    }

    private void openWithDefaultOrDialog(File file, String fileName) {
        if (tryRunBuiltinDefault(file, fileName)) return;
        withReadableCopy(file, readable -> {
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
            } catch (Exception e) {
                showOpenWithDialog(readable, fileName);
                return;
            }
            if (tryLaunchExternalDefault(uri, readable)) return;
            showOpenWithDialog(readable, fileName, null, null, file);
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
        withReadableCopy(target, readable -> context.loadZipFolderInPane(readable, "", pane1, true));
    }

    private void showAppsForMime(File file, String fileName, boolean useActual) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", file);
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        String mime = useActual ? MimeUtil.getRealMimeType(file) : null;
        if (mime == null) mime = MimeUtil.getReportedMimeType(context, file);
        if (mime == null) mime = "application/octet-stream";
        final String chosenMime = mime;
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
                        else prefs.edit().putString(defaultAppKey(chosenMime), flat).apply();
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
                Typeface tf = Typeface.createFromFile(readable);
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
            try {
                ParcelFileDescriptor pfd = ParcelFileDescriptor.open(readable, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(pfd);
                int pageCount = renderer.getPageCount();
                if (pageCount <= 0) {
                    renderer.close();
                    pfd.close();
                    Extensions.showMessage(context, R.string.cannot_open_item);
                    return;
                }

                int[] currentPage = new int[]{0};

                LinearLayout root = new LinearLayout(context);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setGravity(Gravity.CENTER_HORIZONTAL);
                int pad = (int) (12 * context.getResources().getDisplayMetrics().density + 0.5f);
                root.setPadding(pad, pad, pad, pad);

                TextView pageInfo = new TextView(context);
                pageInfo.setGravity(Gravity.CENTER);
                pageInfo.setTextSize(15);
                pageInfo.setPadding(0, 0, 0, pad / 2);
                root.addView(pageInfo);

                ImageView pageImageView = new ImageView(context);
                pageImageView.setAdjustViewBounds(true);
                pageImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                root.addView(pageImageView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout navLayout = new LinearLayout(context);
                navLayout.setOrientation(LinearLayout.HORIZONTAL);
                navLayout.setGravity(Gravity.CENTER);
                navLayout.setPadding(0, pad, 0, 0);

                Button prevBtn = new Button(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                prevBtn.setText("◀ Prev");
                Button nextBtn = new Button(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                nextBtn.setText("Next ▶");

                navLayout.addView(prevBtn);
                navLayout.addView(nextBtn);
                root.addView(navLayout);

                final Bitmap[] currentBitmap = new Bitmap[1];

                Runnable renderPage = () -> {
                    try {
                        PdfRenderer.Page page = renderer.openPage(currentPage[0]);
                        int screenWidth = context.getResources().getDisplayMetrics().widthPixels - (pad * 4);
                        if (screenWidth <= 0) screenWidth = 800;
                        int targetWidth = screenWidth;
                        int targetHeight = (int) ((float) page.getHeight() / page.getWidth() * targetWidth);

                        Bitmap bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        page.close();

                        if (currentBitmap[0] != null && !currentBitmap[0].isRecycled()) {
                            currentBitmap[0].recycle();
                        }
                        currentBitmap[0] = bitmap;

                        pageImageView.setImageBitmap(bitmap);
                        pageInfo.setText((currentPage[0] + 1) + " / " + pageCount);
                        prevBtn.setEnabled(currentPage[0] > 0);
                        nextBtn.setEnabled(currentPage[0] < pageCount - 1);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                };

                prevBtn.setOnClickListener(v -> {
                    if (currentPage[0] > 0) {
                        currentPage[0]--;
                        renderPage.run();
                    }
                });

                nextBtn.setOnClickListener(v -> {
                    if (currentPage[0] < pageCount - 1) {
                        currentPage[0]++;
                        renderPage.run();
                    }
                });

                renderPage.run();

                ScrollView scroll = new ScrollView(context);
                scroll.addView(root);

                AlertDialog dialog = dialogUtil.getDialogBuilder()
                        .setTitle(fileName)
                        .setView(scroll)
                        .setPositiveButton(android.R.string.ok, null)
                        .create();

                dialog.setOnDismissListener(d -> {
                    try {
                        if (currentBitmap[0] != null && !currentBitmap[0].isRecycled()) {
                            currentBitmap[0].recycle();
                        }
                        renderer.close();
                        pfd.close();
                    } catch (Exception ignored) {}
                });

                dialogUtil.styleAlertDialog(dialog);
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
    }

    private void showHtmlViewer(File readable, String fileName) {
        context.startActivity(new Intent(context, HtmlViewerActivity.class)
                .putExtra(HtmlViewerActivity.EXTRA_PATH, readable.getAbsolutePath())
                .putExtra(HtmlViewerActivity.EXTRA_NAME, fileName));
    }


    private void showElfOptionsDialog(File file, String fileName, File originalFile) {
        String[] options = {
                context.getString(R.string.hex_editor),
                "ELF Inspector",
                context.getString(R.string.text_editor),
                context.getString(R.string.open_with)
        };

        AlertDialog dialog = dialogUtil.getDialogBuilder()
                .setTitle("ELF: " + fileName)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0 -> openHexEditorWithOriginal(file, originalFile);
                        case 1 -> showElfInspectorDialog(file, fileName, originalFile);
                        case 2 -> openTextEditorWithOriginal(file, originalFile);
                        case 3 -> showOpenWithDialog(file, fileName, null, null, originalFile);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            android.widget.ListView lv = dialog.getListView();
            if (lv != null) {
                lv.setOnItemLongClickListener((parent, view, position, id) -> {
                    if (position == 1) { // Long press on "ELF Inspector"
                        dialog.dismiss();
                        exportElfReadableCodeToDownload(file, fileName);
                        return true;
                    }
                    return false;
                });
            }
        });
        dialogUtil.styleAlertDialog(dialog);
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
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                        .setTitle("Kernel Info: " + fileName)
                        .setMessage(infoText)
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton("📋 Copy", (d, w) -> {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (cm != null) {
                                cm.setPrimaryClip(ClipData.newPlainText("Kernel Info", infoText));
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            });
        }).start();
    }

    private void exportElfReadableCodeToDownload(File file, String fileName) {
        ProgressManager pm = new ProgressManager(context, true).show();
        pm.setText(context.getString(R.string.exporting));

        new Thread(() -> {
            try {
                String content = generateElfReadableCode(file, fileName);
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) downloadDir.mkdirs();

                File exportFile = new File(downloadDir, fileName + "_decompiled.txt");
                int counter = 1;
                while (exportFile.exists()) {
                    exportFile = new File(downloadDir, fileName + "_decompiled_" + counter + ".txt");
                    counter++;
                }

                try (FileOutputStream fos = new FileOutputStream(exportFile)) {
                    fos.write(content.getBytes(StandardCharsets.UTF_8));
                }

                final File finalExportFile = exportFile;
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    context.refreshPane(true);
                    android.widget.Toast.makeText(context, "Exported: " + finalExportFile.getName() + " to Download", android.widget.Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> {
                    pm.dismiss();
                    new ErrorUtil(context).showError(e);
                });
            }
        }).start();
    }

    private String generateElfReadableCode(File file, String fileName) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r");
             ElfParser elf = new ElfParser(raf)) {
            StringBuilder sb = new StringBuilder();
            sb.append("// =====================================================================\n");
            sb.append("// DECOMPILED ELF SUMMARY & READABLE CODE\n");
            sb.append("// File: ").append(fileName).append(" (").append(file.length()).append(" bytes)\n");
            sb.append("// Format: ELF ").append(elf.is64 ? "64-bit" : "32-bit").append(" (Little-Endian)\n");
            sb.append("// Architecture: ").append(ElfParser.machineName(elf.machine))
                    .append(" (0x").append(Integer.toHexString(elf.machine)).append(")\n");
            sb.append("// Entry Point: 0x").append(Long.toHexString(elf.entry)).append("\n");
            sb.append("// =====================================================================\n\n");

            // 1. Dependencies & Dynamic Info
            Map<String, String> dyn = elf.dynamicInfo();
            if (!dyn.isEmpty()) {
                sb.append("// =================== [1] SHARED LIBRARIES & METADATA ===================\n");
                if (dyn.containsKey("soname")) {
                    sb.append("// Internal Library Name (SONAME): ").append(dyn.get("soname")).append("\n");
                }
                if (dyn.containsKey("needed")) {
                    sb.append("// Imported Dependencies (DT_NEEDED):\n");
                    for (String lib : dyn.get("needed").split(",\\s*")) {
                        sb.append("#include <").append(lib.trim()).append(">\n");
                    }
                }
                sb.append("\n");
            }

            // 2. Symbols & Functions
            List<ElfParser.Symbol> allSymbols = new ArrayList<>();
            try {
                allSymbols.addAll(elf.symbols(".dynsym"));
            } catch (Exception ignored) {}
            try {
                List<ElfParser.Symbol> symtab = elf.symbols(".symtab");
                if (symtab != null && !symtab.isEmpty()) allSymbols.addAll(symtab);
            } catch (Exception ignored) {}

            List<ElfParser.Symbol> exports = new ArrayList<>();
            List<ElfParser.Symbol> jniFuncs = new ArrayList<>();
            List<ElfParser.Symbol> imports = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (ElfParser.Symbol s : allSymbols) {
                if (s.name == null || s.name.isEmpty() || !seen.add(s.name)) continue;
                if (s.name.startsWith("Java_") || s.name.contains("JNI_OnLoad") || s.name.contains("JNI_OnUnload")) {
                    jniFuncs.add(s);
                } else if (s.shndx == 0) {
                    imports.add(s);
                } else if (s.type == 2 /* FUNC */ || s.type == 10 /* IFUNC */) {
                    exports.add(s);
                }
            }

            if (!jniFuncs.isEmpty()) {
                sb.append("// =================== [2] JNI EXPORTED METHODS (JAVA BINDINGS) ===================\n");
                sb.append("extern \"C\" {\n");
                for (ElfParser.Symbol jni : jniFuncs) {
                    sb.append("    // Addr: 0x").append(Long.toHexString(jni.value))
                            .append(" | Size: ").append(jni.size).append("\n");
                    sb.append("    JNIEXPORT void JNICALL ").append(jni.name).append("(JNIEnv* env, jobject thiz);\n\n");
                }
                sb.append("}\n\n");
            }

            if (!exports.isEmpty()) {
                sb.append("// =================== [3] DEFINED FUNCTIONS (CODE LOGIC) ===================\n");
                for (ElfParser.Symbol exp : exports) {
                    sb.append("// [FUNC] 0x").append(Long.toHexString(exp.value))
                            .append(" (size: ").append(exp.size).append(" bytes) [")
                            .append(exp.bindName()).append("]\n");
                    sb.append("int ").append(exp.name).append("();\n");
                }
                sb.append("\n");
            }

            if (!imports.isEmpty()) {
                sb.append("// =================== [4] IMPORTED SYSTEM CALLS & API FUNCTIONS ===================\n");
                sb.append("// External functions invoked by this binary (reveals capabilities):\n");
                for (ElfParser.Symbol imp : imports) {
                    sb.append("extern int ").append(imp.name).append("();\n");
                }
                sb.append("\n");
            }

            // 3. Sections Overview
            sb.append("// =================== [5] ELF SECTIONS ===================\n");
            for (ElfParser.Section sec : elf.sections()) {
                sb.append(String.format(Locale.ROOT, "// %-20s  offset: 0x%08x  size: %-8d  addr: 0x%08x\n",
                        sec.name, sec.offset, sec.size, sec.addr));
            }
            sb.append("\n");

            // 4. Embedded Strings, URLs, Paths, and Commands
            sb.append("// =================== [6] EMBEDDED STRINGS, COMMANDS & URLS ===================\n");
            List<String[]> rodataStrings = new ArrayList<>();
            try {
                rodataStrings.addAll(elf.strings(".rodata", 4, 1500));
            } catch (Exception ignored) {}
            try {
                rodataStrings.addAll(elf.strings(".data", 4, 500));
            } catch (Exception ignored) {}

            if (rodataStrings.size() < 10) {
                extractGenericStrings(file, 4, 2000, rodataStrings);
            }

            Set<String> seenStrings = new HashSet<>();
            List<String> pathsAndCommands = new ArrayList<>();
            List<String> urlsAndWeb = new ArrayList<>();
            List<String> otherStrings = new ArrayList<>();

            for (String[] entry : rodataStrings) {
                String str = entry[1].trim();
                if (str.length() < 3 || !seenStrings.add(str)) continue;
                if (str.startsWith("http://") || str.startsWith("https://") || str.startsWith("ftp://")) {
                    urlsAndWeb.add(entry[0] + ": " + str);
                } else if (str.startsWith("/") || str.contains("/bin/") || str.contains("/system/") || str.startsWith("su ") || str.startsWith("sh ")) {
                    pathsAndCommands.add(entry[0] + ": " + str);
                } else {
                    otherStrings.add(entry[0] + ": " + str);
                }
            }

            if (!urlsAndWeb.isEmpty()) {
                sb.append("// --- Network Endpoints & URLs ---\n");
                for (String s : urlsAndWeb) sb.append(s).append("\n");
                sb.append("\n");
            }

            if (!pathsAndCommands.isEmpty()) {
                sb.append("// --- System Paths & Shell Commands ---\n");
                for (String s : pathsAndCommands) sb.append(s).append("\n");
                sb.append("\n");
            }

            if (!otherStrings.isEmpty()) {
                sb.append("// --- Error Messages, Format Strings & Identifiers ---\n");
                for (String s : otherStrings) sb.append(s).append("\n");
                sb.append("\n");
            }

            return sb.toString();
        }
    }

    private static void extractGenericStrings(File file, int minLen, int maxResults, List<String[]> out) {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buf = new byte[64 * 1024];
            ByteArrayOutputStream cur = new ByteArrayOutputStream();
            long offset = 0;
            long strStart = 0;
            int n;
            while ((n = in.read(buf)) != -1 && out.size() < maxResults) {
                for (int i = 0; i < n && out.size() < maxResults; i++) {
                    byte b = buf[i];
                    if (b >= 0x20 && b <= 0x7E) {
                        if (cur.size() == 0) strStart = offset + i;
                        cur.write(b);
                    } else {
                        if (cur.size() >= minLen) {
                            out.add(new String[]{"0x" + Long.toHexString(strStart), cur.toString("UTF-8")});
                        }
                        cur.reset();
                    }
                }
                offset += n;
            }
            if (cur.size() >= minLen && out.size() < maxResults) {
                out.add(new String[]{"0x" + Long.toHexString(strStart), cur.toString("UTF-8")});
            }
        } catch (Exception ignored) {}
    }

    private void showElfInspectorDialog(File file, String fileName, File originalFile) {
        final File targetOriginal = originalFile != null ? originalFile : file;
        new Thread(() -> {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 ElfParser elf = new ElfParser(raf)) {
                final AlertDialog[] dialogRef = new AlertDialog[1];
                SpannableStringBuilder ssb = new SpannableStringBuilder();
                ssb.append("File: ").append(fileName).append("\n");
                ssb.append("Format: ELF ").append(elf.is64 ? "64-bit" : "32-bit").append("\n");
                ssb.append("Architecture: ").append(ElfParser.machineName(elf.machine))
                        .append(" (0x").append(Integer.toHexString(elf.machine)).append(")\n");

                ssb.append("Entry point: ");
                String entryAddrStr = "0x" + Long.toHexString(elf.entry);
                int entryStart = ssb.length();
                ssb.append(entryAddrStr);
                int entryEnd = ssb.length();
                final long entryOffset = elf.entry;
                ssb.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(@NonNull View widget) {
                        openHexEditorWithOriginal(file, targetOriginal, entryOffset);
                    }
                    @Override
                    public void updateDrawState(@NonNull TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(0xFF29B6F6);
                        ds.setUnderlineText(true);
                    }
                }, entryStart, entryEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.append("\n");

                ssb.append("Size: ").append(Formatter.formatFileSize(context, file.length())).append("\n\n");

                ElfParser.Section[] sections = elf.sections();
                if (sections != null && sections.length > 0) {
                    ssb.append("--- Sections (").append(String.valueOf(sections.length)).append(") ---\n");
                    for (ElfParser.Section s : sections) {
                        if (s != null && s.name != null && !s.name.isEmpty()) {
                            ssb.append(String.format(Locale.ROOT, "%-16s ", s.name));
                            String addrStr = String.format(Locale.ROOT, "addr:0x%08x", s.addr);
                            int addrStart = ssb.length();
                            ssb.append(addrStr);
                            int addrEnd = ssb.length();
                            final long targetOffset = s.offset > 0 ? s.offset : s.addr;
                            ssb.setSpan(new ClickableSpan() {
                                @Override
                                public void onClick(@NonNull View widget) {
                                    openHexEditorWithOriginal(file, targetOriginal, targetOffset);
                                }
                                @Override
                                public void updateDrawState(@NonNull TextPaint ds) {
                                    super.updateDrawState(ds);
                                    ds.setColor(0xFF29B6F6);
                                    ds.setUnderlineText(true);
                                }
                            }, addrStart, addrEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            ssb.append(String.format(Locale.ROOT, " size:0x%x\n", s.size));
                        }
                    }
                    ssb.append("\n");
                }

                Map<String, String> dyn = elf.dynamicInfo();
                if (dyn != null && !dyn.isEmpty()) {
                    ssb.append("--- Dynamic / Dependencies ---\n");
                    for (Map.Entry<String, String> entry : dyn.entrySet()) {
                        ssb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                    }
                }

                context.runOnUiThread(() -> {
                    ScrollView sv = new ScrollView(context);
                    HorizontalScrollView hsv = new HorizontalScrollView(context);
                    TextView tv = new TextView(context);
                    tv.setText(ssb);
                    tv.setTypeface(Typeface.MONOSPACE);
                    tv.setTextSize(13);
                    int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
                    tv.setPadding(pad, pad, pad, pad);
                    tv.setMovementMethod(LinkMovementMethod.getInstance());
                    tv.setHighlightColor(Color.TRANSPARENT);
                    hsv.addView(tv);
                    sv.addView(hsv);

                    AlertDialog dialog = dialogUtil.getDialogBuilder()
                            .setTitle("ELF: " + fileName)
                            .setView(sv)
                            .setPositiveButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.hex_editor, (d, w) -> openHexEditorWithOriginal(file, targetOriginal))
                            .create();
                    dialogRef[0] = dialog;
                    dialogUtil.styleAlertDialog(dialog);
                });
            } catch (Exception e) {
                context.runOnUiThread(() -> new ErrorUtil(context).showError(e));
            }
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
                    AccessManager.copyFile(context, out.getAbsolutePath(), original.getAbsolutePath(), true);
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
            rootMode = RootManager.getInstance(context).isRootMode();
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
                .setNeutralButton(R.string.sh_edit, (dialog, which) -> context.startActivity(
                        rootAwareEditorIntent(readable, original).putExtra("path", readable.getPath())))
                .setPositiveButton(R.string.sh_execute, (dialog, which) -> executeShellScript(
                        readable, runAsRootBox != null && runAsRootBox.isChecked()))
                .create());
    }

    private void executeShellScript(File script, boolean asRoot) {
        boolean rootMode = false;
        try {
            rootMode = RootManager.getInstance(context).isRootMode();
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
                    RootManager.ShellResult r = RootManager.getInstance(context).execute(
                            "sh " + RootManager.escapeShellArg(script.getAbsolutePath()), 120);
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
        File keysDir = new File(Environment.getExternalStorageDirectory()
                + File.separator + "MT2" + File.separator + "keys");
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

    public void handleFileClick(File file, String fileName) {
        try {
            context.clearGreen(pane1);
        } catch (Exception ignored) {}
        if (tryRunBuiltinDefault(file, fileName)) return;
        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        if (fileName.endsWith(".txt") || fileName.endsWith(".json")
            || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
            || fileName.endsWith(".gradle") || fileName.endsWith(".properties") || fileName.endsWith(".prop")
            || fileName.endsWith(".conf") || fileName.endsWith(".cfg") || fileName.endsWith(".ini")
            || fileName.endsWith(".log") || fileName.endsWith(".md") || fileName.endsWith(".csv")
            || fileName.endsWith(".yaml") || fileName.endsWith(".yml") || fileName.endsWith(".toml")
            || fileName.endsWith(".rc")) {
            openTextEditorRootAware(file);
        } else if (FileUtils.isElfFile(file)) {
            withReadableCopy(file, readable -> showElfOptionsDialog(readable, fileName, file));
        } else if (isKernelFile(file, fileName)) {
            withReadableCopy(file, readable -> showKernelOptionsDialog(readable, fileName, file));
        } else if (fileName.endsWith(".img") || fileName.endsWith(".iso") || file.getAbsolutePath().startsWith("/dev/block/")) {
            if (io.github.abdurazaaqmohammed.boot.BootImageEngine.isBootImage(file)) {
                io.github.abdurazaaqmohammed.boot.BootInspectManager.openBootImage(context, file, pane1);
            } else if (io.github.abdurazaaqmohammed.features.system.SystemImageEngine.isSparseImage(file)) {
                promptUnsparseImage(file, fileName);
            } else if (io.github.abdurazaaqmohammed.features.system.SystemImageMountManager.isMountableImage(file)) {
                io.github.abdurazaaqmohammed.features.system.SystemImageMountManager.showImageOptionsDialog(context, file, fileName, pane1);
            } else {
                openWithDefaultOrDialog(file, fileName);
            }
        } else if (("payload.bin".equalsIgnoreCase(fileName) || fileName.endsWith(".bin") || fileName.endsWith(".payload")) && PayloadParser.isPayload(file)) {
            PayloadDialog.showForPayload(context, file, pane1);
        } else if(HashUtil.isChecksumFile(fileName)) {
            withReadableCopy(file, readable -> {
                if (checksumDialogs != null) checksumDialogs.showHashVerifyDialog(readable);
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
            });
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            withReadableCopy(file, readable -> context.playMediaFile(readable.getPath()));
        } else if ((ext.equals(".apk"))) {
            withReadableCopy(file, readable -> {
                if (apkTools != null) apkTools.showApkInfoDialog(readable, fileName);
                else openWithDefaultOrDialog(readable, fileName);
            });
        } else {
            String bak = ".bak";
            if (ext.equals(bak)) {
                String nameWithoutBak = fileName.substring(0, fileName.length() - bak.length());
                String innerExt = FilenameUtils.getExtension(nameWithoutBak);
                if (!innerExt.isEmpty()) {
                    handleFileClick(file, nameWithoutBak);
                } else {
                    openArchiveSafe(file);
                }
            } else if (fileName.endsWith(".arsc")) {
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
                withReadableCopy(file, readable -> io.github.abdurazaaqmohammed.features.apk.flutter.FlutterToolsDialog.showForLibrary(context, readable, fileName));
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
        boolean isAr = java.util.Locale.getDefault().getLanguage().startsWith("ar");
        io.github.abdurazaaqmohammed.features.system.SystemImageEngine.SparseInfo sInfo =
                io.github.abdurazaaqmohammed.features.system.SystemImageEngine.readSparseInfo(file);
        long uncompressedSize = (sInfo != null) ? sInfo.uncompressedSizeBytes : 0;
        String sizeStr = uncompressedSize > 0 ? ("\n\n" + (isAr ? "الحجم بعد فك الضغط: " : "Uncompressed Size: ") +
                io.github.abdurazaaqmohammed.utils.FileSize.getHumanReadableFileSize(uncompressedSize)) : "";

        String msg = (isAr
                ? "هذا الملف عبارة عن صورة أندرويد مضغوطة (Android Sparse Image).\nهل تريد فك ضغطها إلى صورة خام Raw Image (.img)؟"
                : "This file is a compressed Android Sparse Image.\nWould you like to unsparse it into a raw image (.img)?") + sizeStr;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(isAr ? "فك ضغط Sparse Image" : "Unsparse Image")
                .setMessage(msg)
                .setPositiveButton(isAr ? "📂 فك الضغط" : "📂 Unsparse", (d, w) -> {
                    String baseName = fileName;
                    int dotIdx = baseName.lastIndexOf('.');
                    if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);
                    File outRaw = new File(file.getParentFile(), baseName + "_raw.img");

                    ProgressManager pm = new ProgressManager(context, false).show();
                    pm.setText(isAr ? ("جاري فك ضغط " + fileName + "...") : ("Unsparsing " + fileName + "..."));

                    new Thread(() -> {
                        try {
                            io.github.abdurazaaqmohammed.features.system.SystemImageEngine.unsparseToFile(file, outRaw,
                                    new io.github.abdurazaaqmohammed.features.system.SystemImageEngine.ProgressCallback() {
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
                                        android.widget.Toast.makeText(context,
                                                (isAr ? "تم فك الضغط بنجاح: " : "Unsparsed successfully: ") + result.getName(),
                                                android.widget.Toast.LENGTH_LONG).show();
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
                        Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", staged);
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
                    uris.add(FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", staged));
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
            if (!AccessManager.fileOpsOn(context)) return 0;
            String cmd = "du -sb " + RootManager.escapeShellArg(path);
            String out = null;
            if (AccessManager.active(context) == AccessManager.Backend.ROOT) {
                RootManager.ShellResult r = RootManager.getInstance(context).execute(cmd, 5);
                if (r != null && r.isSuccess()) out = r.output();
            } else if (ShizukuManager.ready()) {
                ShizukuShell.Result r = ShizukuShell.exec(cmd, 5);
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
        try (ZipFile srcZip = new ZipFile(entry.getZipFile())) {
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
                if (RootStaging.needsStaging(context, folder)) {
                    stagedDir = new File(context.getCacheDir(), "stage_share_" + System.currentTimeMillis());
                    stagedDir.mkdirs();
                    sourceToCompress = new File(stagedDir, folder.getName());
                    AccessManager.stageTree(context, folder.getAbsolutePath(), sourceToCompress);
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
                        Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", finalZip);
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
                        if (RootStaging.needsStaging(context, f)) {
                            if (stagedDir == null) {
                                stagedDir = new File(context.getCacheDir(), "stage_share_" + System.currentTimeMillis());
                                stagedDir.mkdirs();
                            }
                            source = new File(stagedDir, f.getName());
                            if (f.isDirectory()) AccessManager.stageTree(context, f.getAbsolutePath(), source);
                            else AccessManager.stageFile(context, f.getAbsolutePath(), source);
                        }
                        if (source.isDirectory()) zf.addFolder(source, params);
                        else zf.addFile(source, params);
                    }
                }

                final File finalZip = zipFile;
                context.handler.post(() -> {
                    pm.dismiss();
                    try {
                        Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", finalZip);
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
                        if (RootStaging.needsStaging(context, f)) {
                            if (stagedDir == null) {
                                stagedDir = new File(context.getCacheDir(), "stage_share_" + System.currentTimeMillis());
                                stagedDir.mkdirs();
                            }
                            source = new File(stagedDir, f.getName());
                            AccessManager.stageTree(context, f.getAbsolutePath(), source);
                        }
                        if (source.isDirectory()) zf.addFolder(source, params);
                        else zf.addFile(source, params);
                    }
                }

                List<Uri> uris = new ArrayList<>();
                uris.add(FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", zipFile));

                if (plainFiles != null) {
                    for (File sf : plainFiles) {
                        File readableFile = sf;
                        if (SafFs.isSaf(sf) || !(sf.exists() && sf.canRead() && appReadable(sf))) {
                            if (AccessManager.fileOpsOn(context)) {
                                readableFile = RootStaging.stageForRead(context, sf.getAbsolutePath());
                            }
                        }
                        if (readableFile != null && readableFile.exists()) {
                            try {
                                Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readableFile);
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

                try (ZipFile srcZip = new ZipFile(entry.getZipFile())) {
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
                        Uri uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", finalZip);
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
            if (file instanceof ShizukuFile)
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
                    File staged = RootStaging.stageForRead(context, file.getAbsolutePath());
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
            if (file != null && file.exists() && file.canRead() && appReadable(file)) {
                cb.onReady(file);
                return;
            }
        } catch (Exception e) {
            new ErrorUtil(context).showError(e);
            return;
        }
        if (!AccessManager.fileOpsOn(context)) {
            Extensions.showMessage(context, R.string.cannot_open_permission_denied);
            return;
        }
        Extensions.showMessage(context, R.string.reading_with_elevated_access);
        new Thread(() -> {
            try {
                File staged = RootStaging.stageForRead(context, file.getAbsolutePath());
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
            // The editor saves through RootStaging.writeBack, which streams a
            // provider path back into its document; say so instead of "root".
            Extensions.showMessage(context, SafFs.isSaf(original)
                    ? R.string.opened_from_provider : R.string.opened_with_root);
        }
        return i;
    }

    private void openTextEditorRootAware(File file) {
        withReadableCopy(file, readable ->
                context.startActivity(rootAwareEditorIntent(readable, file)
                        .putExtra("path", readable.getAbsolutePath())));
    }

    private void openTextEditorWithOriginal(File readable, File original) {
        if (readable == null || !readable.isFile()) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        File orig = original != null ? original : readable;
        context.startActivity(rootAwareEditorIntent(readable, orig)
                .putExtra("path", readable.getAbsolutePath()));
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
        withReadableCopy(file, readable -> {
            Intent i = new Intent(context, HexEditorActivity.class)
                    .putExtra("path", readable.getAbsolutePath());
            if (!readable.getAbsolutePath().equals(file.getAbsolutePath())) {
                i.putExtra("rootOriginalPath", file.getAbsolutePath());
                Extensions.showMessage(context, R.string.opened_with_root);
            }
            context.startActivity(i);
        });
    }

    private void restoreBakRootAware(File bakFile, File origFile, String fileName) {
        String bakPath = bakFile.getPath();
        String origPath = origFile.getPath();
        boolean useElevated = AccessManager.fileOpsOn(context)
                && (RootStaging.needsStaging(context, bakPath) || RootStaging.needsStaging(context, origPath));
        if (useElevated) {
            new Thread(() -> {
                try {
                    boolean origExists = AccessManager.exists(context, origPath) || origFile.exists();
                    if (origExists) AccessManager.rename(context, origPath, origPath + "_tmp_.bak", true);
                    AccessManager.rename(context, bakPath, origPath, true);
                    if (origExists) AccessManager.rename(context, origPath + "_tmp_.bak", bakPath, true);
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
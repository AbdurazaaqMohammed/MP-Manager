package io.github.abdurazaaqmohammed.features.files;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Environment;
import android.text.ClipboardManager;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.apk.axml.aXMLDecoder;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.reandroid.apkeditor.Util;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.adapters.DialogAdapter;
import io.github.abdurazaaqmohammed.domain.files.ZipEntryInfo;
import io.github.abdurazaaqmohammed.arsc.ArscEditorPlusActivity;
import io.github.abdurazaaqmohammed.arsc.ArscEditorActivity;
import io.github.abdurazaaqmohammed.listeners.SwipeTouchListener;
import io.github.abdurazaaqmohammed.ui.UIHelper;
import io.github.abdurazaaqmohammed.ui.activities.CompareTextActivity;
import io.github.abdurazaaqmohammed.ui.activities.HexEditorActivity;
import io.github.abdurazaaqmohammed.ui.activities.TextEditorActivity;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareArscDialog;
import io.github.abdurazaaqmohammed.ui.dialogs.CompareZipDialog;
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.ArchiveUtil;
import io.github.abdurazaaqmohammed.utils.ColorUtil;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.features.media.BatchImageTools;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.HashUtil;
import io.github.abdurazaaqmohammed.utils.InstallUtil;
import io.github.abdurazaaqmohammed.utils.JpegMetaStrip;
import io.github.abdurazaaqmohammed.utils.JpegtranJni;
import io.github.abdurazaaqmohammed.utils.LegacyUtils;
import io.github.abdurazaaqmohammed.utils.MergeUtil;
import io.github.abdurazaaqmohammed.utils.MimeUtil;
import io.github.abdurazaaqmohammed.utils.NativeToolManager;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RenameUtil;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.utils.SignWrapper;
import io.github.abdurazaaqmohammed.utils.SignatureKeyDialog;
import io.github.abdurazaaqmohammed.utils.UiPrefs;
import io.github.codehasan.colorpicker.extensions.Extensions;
import io.github.abdurazaaqmohammed.core.ui.UIKit;

/**
 * File-open workflows extracted from MainFilesArrayAdapter:
 * click dispatch, open-with, font/XML/signature viewers, root-aware
 * staging, split-APK and ARSC menus.
 */
public class FileOpener {

    private final io.github.abdurazaaqmohammed.MPManager.MainActivity context;
    private final io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil;
    private final boolean pane1;
    private final io.github.abdurazaaqmohammed.adapters.main.ApkToolsHandler apkTools;
    private final io.github.abdurazaaqmohammed.adapters.main.ChecksumDialogs checksumDialogs;
    private final io.github.abdurazaaqmohammed.adapters.main.FileOperationsHelper fileOps;

    public FileOpener(io.github.abdurazaaqmohammed.MPManager.MainActivity context,
                        io.github.abdurazaaqmohammed.utils.DialogUtil dialogUtil, boolean pane1,
                        io.github.abdurazaaqmohammed.adapters.main.ApkToolsHandler apkTools,
                        io.github.abdurazaaqmohammed.adapters.main.ChecksumDialogs checksumDialogs,
                        io.github.abdurazaaqmohammed.adapters.main.FileOperationsHelper fileOps) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.pane1 = pane1;
        this.apkTools = apkTools;
        this.checksumDialogs = checksumDialogs;
        this.fileOps = fileOps;
    }

    public interface ReadableCallback {
        void onReady(java.io.File readable) throws Exception;
    }

    public void openWithForFile(File file, String fileName) {
        showOpenWithDialog(file, fileName);
    }

    public void showOpenWithDialog(File file, String fileName) {
        if (file == null) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
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
                () -> {
                    if (!file.isFile()) {
                        Extensions.showMessage(context, R.string.cannot_open_item);
                        return;
                    }
                    openTextEditorRootAware(file);
                },
                () -> {
                    String lowerName = fileName.toLowerCase(Locale.ROOT);
                    boolean zipBased = lowerName.endsWith(".zip") || lowerName.endsWith(".apk")
                            || lowerName.endsWith(".jar") || lowerName.endsWith(".apks") || lowerName.endsWith(".xapk");
                    if (!file.isFile() || !zipBased) {
                        Extensions.showMessage(context, R.string.not_supported_archive);
                        return;
                    }
                    withReadableCopy(file, readable -> context.loadZipFolderInPane(readable, "", pane1, true));
                },
                () -> withReadableCopy(file, readable -> context.openImageViewer(readable.getAbsolutePath())),
                () -> {
                    if (!file.isFile()) {
                        Extensions.showMessage(context, R.string.cannot_open_item);
                        return;
                    }
                    openHexEditorRootAware(file);
                },
                () -> withReadableCopy(file, readable -> context.playMediaFile(readable.getAbsolutePath())),
                () -> {
                    String lowerExt = fileName.toLowerCase(Locale.ROOT);
                    if (!lowerExt.endsWith(".apk") && !lowerExt.endsWith(".apks") && !lowerExt.endsWith(".xapk")) {
                        Extensions.showMessage(context, R.string.not_an_apk);
                        return;
                    }
                    withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, fileName));
                }));
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".woff") || lower.endsWith(".woff2")) {
            actionNames.add(context.getString(R.string.font_preview));
            actionIcons.add(R.drawable.uppercase_24px);
            actionHandlers.add(() -> showFontPreview(file, fileName));
        }
        if (lower.endsWith(".arsc")) {
            actionNames.add(context.getString(R.string.arsc_functions));
            actionIcons.add(R.drawable.apk_document_24px);
            actionHandlers.add(() -> withReadableCopy(file, readable -> showArscOpenWith(readable, null, "resources.arsc")));
        }
        if (lower.endsWith(".xml")) {
            actionNames.add(context.getString(R.string.xml_functions));
            actionIcons.add(R.drawable.code_24px);
            actionHandlers.add(() -> showXmlFunctions(file, fileName));
        }
        String keyExt = FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
        if (keyExt.equals("jks") || keyExt.equals("keystore") || keyExt.equals("p12")
                || keyExt.equals("pfx") || keyExt.equals("pk8") || keyExt.equals("pem")) {
            actionNames.add(context.getString(R.string.import_signature));
            actionIcons.add(R.drawable.lock_24px);
            actionHandlers.add(() -> importSignature(file, fileName));
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
                labelView.setText(actionNames.get(position));
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
        dialogUtil.styleAlertDialog(dialog);
    }

    private static String defaultAppKey(String mime) {
        return "openwith_default_" + mime.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "_");
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

    private void openWithDefaultOrDialog(File file, String fileName) {
        withReadableCopy(file, readable -> {
            Uri uri;
            try {
                uri = FileProvider.getUriForFile(context, "io.github.abdurazaaqmohammed.MPManager.provider", readable);
            } catch (Exception e) {
                showOpenWithDialog(readable, fileName);
                return;
            }
            String actionMime = MimeUtil.getMimeTypeForAction(context, readable);
            if (actionMime == null) actionMime = "application/octet-stream";
            if (tryLaunchDefaultApp(uri, actionMime)) return;
            String real = MimeUtil.getRealMimeType(readable);
            if (real != null && !real.equals(actionMime) && tryLaunchDefaultApp(uri, real)) return;
            String reported = MimeUtil.getReportedMimeType(context, readable);
            if (reported != null && !reported.equals(actionMime) && (real == null || !reported.equals(real)) && tryLaunchDefaultApp(uri, reported)) return;
            showOpenWithDialog(readable, fileName);
        });
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
                TextView sample = new TextView(context);
                sample.setText("ABCDEFGHIJKLMNOPQRSTUVWXYZ\nabcdefghijklmnopqrstuvwxyz\n0123456789 !?@#");
                sample.setTypeface(tf);
                sample.setTextSize(22);
                root.addView(sample);
                TextView meta = new TextView(context);
                meta.setText(fileName + " · " + readable.length() + " bytes");
                meta.setTextSize(13);
                root.addView(meta);
                dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                        .setTitle(context.getString(R.string.font_preview) + ": " + fileName)
                        .setView(root)
                        .setPositiveButton(android.R.string.ok, null)
                        .create());
            } catch (Exception e) {
                new ErrorUtil(context).showError(e);
            }
        });
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
                            try (InputStream is2 = FileUtils.getInputStream(readable)) {
                                context.startActivity(rootAwareEditorIntent(readable, file)
                                        .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2).decodeAsString().trim())
                                        .putExtra("axml", true)
                                        .putExtra("path", readable.getPath()));
                            } catch (Exception e) {
                                new ErrorUtil(context).showError(e);
                            }
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
        String ext = '.' + FilenameUtils.getExtension(fileName).toLowerCase();
        if (fileName.endsWith(".txt") || fileName.endsWith(".json")
            || fileName.endsWith(".java") || fileName.endsWith(".smali") || fileName.endsWith(".pro")
            || fileName.endsWith(".gradle") || fileName.endsWith(".properties")) {
            openTextEditorRootAware(file);
        } else if(HashUtil.isChecksumFile(fileName)) {
            withReadableCopy(file, checksumDialogs::showHashVerifyDialog);
        } else if(fileName.endsWith(".xml")) {
            withReadableCopy(file, readable -> {
                try (InputStream is = FileUtils.getInputStream(readable)) {
                    if (FileUtils.isAxml(is)) try (InputStream is2 = FileUtils.getInputStream(readable)) {
                        context.startActivity(rootAwareEditorIntent(readable, file)
                                .putExtra(Intent.EXTRA_TEXT, new aXMLDecoder(is2).decodeAsString().trim())
                                .putExtra("axml", true)
                                .putExtra("path", readable.getPath()));
                    }
                    else context.startActivity(rootAwareEditorIntent(readable, file)
                            .putExtra("path", readable.getPath()));
                } catch (Exception e) {
                    new ErrorUtil(context).showError(e);
                }
            });
        } else if (FileUtils.matchExt(ext, FileUtils.IMAGE_EXTS)) {
            withReadableCopy(file, readable -> context.openImageViewer(readable.getPath()));
        } else if (FileUtils.matchExt(ext, FileUtils.AUDIO_EXTS) || FileUtils.matchExt(ext, FileUtils.VIDEO_EXTS)) {
            withReadableCopy(file, readable -> context.playMediaFile(readable.getPath()));
        } else if ((ext.equals(".apk"))) {
            withReadableCopy(file, readable -> apkTools.showApkInfoDialog(readable, fileName));
        } else {
            String bak = ".bak";
            if(ext.equals(bak)) {
                View et = LayoutInflater.from(context).inflate(R.layout.enter_name, null);
                context.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
                EditText tv = et.findViewById(R.id.m_et_edittext);
                String newName = fileName.replace(bak, "");
                tv.setText(newName);
                tv.requestFocus();
                tv.post(() -> {
                    tv.setSelection(0, newName.indexOf(FilenameUtils.getExtension(newName)) - 1);
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(tv, InputMethodManager.SHOW_IMPLICIT);
                });
                UIKit.dialog(context)
                .setTitle(R.string.restore_backup)
                .setView(et)
                .setPositiveButton(R.string.restore, (dialog, which) -> {
                    String bakPath = file.getPath();
                    String origPath = bakPath.replace(bak, "");
                    restoreBakRootAware(file, new File(origPath), fileName);
                })
                .setNegativeButton(android.R.string.cancel, null).show();
            } else if (fileName.endsWith(".zip")) {
                withReadableCopy(file, readable -> context.loadZipFolderInPane(readable, "", pane1, true));
            } else if (fileName.endsWith(".arsc")) {
                withReadableCopy(file, readable -> showArscOpenWith(readable, null, "resources.arsc"));
            } else if (ArchiveUtil.isSupportedArchive(fileName)) {
                dialogUtil.styleAlertDialog(
                        dialogUtil.getDialogBuilder().setSingleChoiceItems(new CharSequence[] { context.rss.getString(R.string.extract), context.rss.getString(R.string.open_with) }, -1, (dialog, which) -> {
                            dialog.dismiss();
                            if (which == 0) withReadableCopy(file, fileOps::extractArchive);
                            else showOpenWithDialog(file, fileName);
                        }).create());
            } else if (fileName.endsWith(".apks") || fileName.endsWith(".xapk") || fileName.endsWith(".aspk") || fileName.endsWith(".apkm")) {
                withReadableCopy(file, readable -> showSplitApkMenu(readable, fileName));
            } else if (fileName.endsWith(".dex")) {
                fileOps.showDexOptionsDialog(file, null, null, fileName);
            } else {
                openWithDefaultOrDialog(file, fileName);
            }
        }
    }

    private File stageZipEntry(ZipEntryInfo zipEntry) throws IOException {
        if (zipEntry == null || zipEntry.isDirectory() || zipEntry.getFullPath() == null) throw new IOException(context.getString(R.string.cannot_open_item));
        File out = new File(context.getCacheDir(), "zip_entry_" + System.currentTimeMillis() + "_" + zipEntry.getName().replaceAll("[^a-zA-Z0-9._-]", "_"));
        try (ZipFile zf = new ZipFile(zipEntry.getZipFile())) {
            FileHeader fh = zf.getFileHeader(zipEntry.getFullPath());
            if (fh == null || fh.isDirectory()) throw new IOException(context.getString(R.string.cannot_open_item));
            try (InputStream is = zf.getInputStream(fh);
                 FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
            }
        }
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

    public void openWithZipEntry(Object item, String fileName) {
        if (!(item instanceof ZipEntryInfo)) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        new Thread(() -> {
            try {
                File staged = stageZipEntry((ZipEntryInfo) item);
                context.handler.post(() -> {
                    try {
                        showOpenWithDialog(staged, fileName);
                    } catch (Exception e) {
                        new ErrorUtil(context).showError(e);
                    }
                });
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
            }
        }).start();
    }

    public void withReadableCopy(File file, ReadableCallback cb) {
        if (file == null) {
            Extensions.showMessage(context, R.string.cannot_open_item);
            return;
        }
        try {
            if (file != null && file.exists() && file.canRead()) {
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
            Extensions.showMessage(context, R.string.opened_with_root);
        }
        return i;
    }

    private void openTextEditorRootAware(File file) {
        withReadableCopy(file, readable ->
                context.startActivity(rootAwareEditorIntent(readable, file)
                        .putExtra("path", readable.getAbsolutePath())));
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
        String[] items = new String[] { context.rss.getString(R.string.install), context.rss.getString(R.string.view), context.rss.getString(R.string.sign), context.rss.getString(R.string.antisplit_merge_to_apk) };
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
                                                    }
                                                    apkFiles.add(tmpApk);
                                                }
                                            }
                                            if (!apkFiles.isEmpty()) {
                                                InstallUtil.installSplitApksWithDialog(context, apkFiles, displayName);
                                            } else {
                                                context.runOnUiThread(() -> Extensions.showMessage(context, R.string.no_apk_files_found));
                                            }
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
                                SignatureKeyDialog.show(context, readable, true);
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
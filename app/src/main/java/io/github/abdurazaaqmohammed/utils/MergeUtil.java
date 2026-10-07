package io.github.abdurazaaqmohammed.utils;

import io.github.codehasan.colorpicker.extensions.Extensions;

import com.google.android.material.textfield.TextInputLayout;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkBundle;
import com.reandroid.apk.ApkModule;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.common.AndroidManifestHelper;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ValueType;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.Deflater;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
public class MergeUtil {
    public static CompressionLevel getPreferredCompressionLevel(Context context) {
        try {
            String s = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("compressLevel", CompressionLevel.NORMAL.name());
            return CompressionLevel.valueOf(s);
        } catch (Exception e) {
            return CompressionLevel.NORMAL;
        }
    }

    public static int toDeflaterLevel(CompressionLevel level) {
        if (level == null) return Deflater.DEFAULT_COMPRESSION;
        return switch (level) {
            case NO_COMPRESSION -> Deflater.NO_COMPRESSION;
            case FASTEST -> Deflater.BEST_SPEED;
            case FASTER -> 2;
            case FAST -> 3;
            case MEDIUM_FAST -> 4;
            case NORMAL -> Deflater.DEFAULT_COMPRESSION;
            case HIGHER -> 7;
            case MAXIMUM -> 8;
            case PRE_ULTRA, ULTRA -> Deflater.BEST_COMPRESSION;
            default -> Deflater.DEFAULT_COMPRESSION;
        };
    }

    public static ZipParameters newPreferredZipParameters(Context context) {
        CompressionLevel level = getPreferredCompressionLevel(context);
        ZipParameters zp = new ZipParameters();
        zp.setCompressionLevel(level);
        zp.setCompressionMethod(level == CompressionLevel.NO_COMPRESSION
                ? CompressionMethod.STORE : CompressionMethod.DEFLATE);
        return zp;
    }

    public static void applyPreferredCompression(ApkBundle bundle, Context context) {
        try {
            bundle.setCompressionLevel(toDeflaterLevel(getPreferredCompressionLevel(context)));
        } catch (Exception ignored) { }
    }


    public static AutoCompleteTextView createCompressionDropdown(Context context) {
        AutoCompleteTextView tv = new AutoCompleteTextView(context);
        tv.setInputType(InputType.TYPE_NULL);
        tv.setSingleLine(false);
        List<String> levels = new ArrayList<>();
        for (CompressionLevel cl : CompressionLevel.values()) levels.add(cl.name());
        String current;
        try {
            current = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString("compressLevel", CompressionLevel.NORMAL.name());
        } catch (Exception e) {
            current = CompressionLevel.NORMAL.name();
        }
        tv.setText(current, false);
        tv.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_dropdown_item_1line, levels));
        tv.setOnItemClickListener((p, v, pos, id) -> {
            try {
                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString("compressLevel", levels.get(pos)).apply();
            } catch (Exception ignored) { }
        });
        return tv;
    }

    /** Same dropdown wrapped in a labeled Material container for dialog rows. */
    public static TextInputLayout createCompressionDropdownLayout(Context context) {
        TextInputLayout layout =
                new TextInputLayout(context);
        try {
            layout.setHint(context.getString(R.string.compression_level));
        } catch (Exception ignored) {
            layout.setHint("Compression level");
        }
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.addView(createCompressionDropdown(context),
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    public static class Options {
        public boolean autosign = true;
        public boolean deviceOnly = true;
        public boolean extractNativeLibs = true;
        public List<String> splitNames = null;
    }

    public static void mergeSplitApk(File file, MainActivity context) {
        Options opts = new Options();
        try {
            opts.autosign = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("autosign", true);
            opts.deviceOnly = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_device_only", true);
            opts.extractNativeLibs = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_extract_native_libs", true);
        } catch (Exception ignored) {
        }
        mergeSplitApk(file, context, opts);
    }

    public static void showAntisplitDialog(File file, MainActivity context) {
        new Thread(() -> {
            List<FileHeader> splits = new ArrayList<>();
            try (ZipFile zf = new ZipFile(file)) {
                for (FileHeader fh : zf.getFileHeaders()) {
                    if (!fh.isDirectory() && fh.getFileName().toLowerCase(Locale.US).endsWith(".apk")) {
                        splits.add(fh);
                    }
                }
            } catch (Exception e) {
                context.handler.post(() -> new ErrorUtil(context).showError(e));
                return;
            }
            if (splits.isEmpty()) {
                context.handler.post(() -> Extensions.showMessage(context, context.getString(R.string.antisplit_no_splits)));
                return;
            }
            String baseName = findBaseSplit(splits);
            context.handler.post(() -> showAntisplitDialogInner(file, context, splits, baseName));
        }).start();
    }

    private static String findBaseSplit(List<FileHeader> splits) {
        for (FileHeader fh : splits) {
            String name = new File(fh.getFileName()).getName();
            if (name.equalsIgnoreCase("base.apk")) return fh.getFileName();
        }
        String fallback = null;
        long biggest = -1;
        for (FileHeader fh : splits) {
            String name = new File(fh.getFileName()).getName().toLowerCase(Locale.US);
            if (!name.contains("split") && !name.startsWith("config.")) return fh.getFileName();
            try {
                long size = fh.getUncompressedSize();
                if (size > biggest) {
                    biggest = size;
                    fallback = fh.getFileName();
                }
            } catch (Exception ignored) {
            }
        }
        return fallback != null ? fallback : splits.get(0).getFileName();
    }

    private static boolean matchesDevice(String entryName, MainActivity context) {
        String lower = entryName.toLowerCase(Locale.US);
        String shortName = new File(lower).getName();
        if (shortName.equals("base.apk")) return true;
        boolean looksLikeConfig = shortName.contains("config.")
                || shortName.contains("split_config")
                || shortName.contains("density")
                || shortName.contains("language")
                || shortName.contains("dpi")
                || shortName.contains("arm")
                || shortName.contains("x86")
                || shortName.contains("mips");
        if (!looksLikeConfig) return true;
        int dpi = 320;
        try {
            dpi = context.getResources().getDisplayMetrics().densityDpi;
        } catch (Exception ignored) {
        }
        String bucket = dpi <= 120 ? "ldpi" : dpi <= 160 ? "mdpi" : dpi <= 213 ? "tvdpi"
                : dpi <= 320 ? "xhdpi" : dpi <= 480 ? "xxhdpi" : "xxxhdpi";
        String normalized = lower.replace('_', '-');
        String bucketDash = bucket.replace('_', '-');
        if (normalized.contains(bucketDash) || lower.contains("nodpi")) return true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (String abi : Build.SUPPORTED_ABIS) {
                    if (abi == null) continue;
                    String a = abi.toLowerCase(Locale.US);
                    String alt = a.replace('-', '_');
                    String alt2 = a.replace('_', '-');
                    if (lower.contains(a) || lower.contains(alt) || normalized.contains(alt2)) return true;
                }
            } else {
                String abi = Build.CPU_ABI;
                if (abi != null) {
                    String a = abi.toLowerCase(Locale.US);
                    if (lower.contains(a) || lower.contains(a.replace('-', '_'))) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            String lang = Locale.getDefault().getLanguage();
            if (!TextUtils.isEmpty(lang)) {
                String l = lang.toLowerCase(Locale.US);
                if (lower.contains("config." + l) || lower.contains("config_" + l)
                        || lower.contains("-" + l + ".") || lower.contains("_" + l + ".")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void showAntisplitDialogInner(File file, MainActivity context, List<FileHeader> splits, String baseName) {
        float density = context.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, 0);

        TextView splitsTitle = new TextView(context);
        splitsTitle.setText(context.getString(R.string.antisplit_splits, splits.size()));
        splitsTitle.setTypeface(null, Typeface.BOLD);
        root.addView(splitsTitle);

        LinearLayout checkBoxHolder = new LinearLayout(context);
        checkBoxHolder.setOrientation(LinearLayout.VERTICAL);
        List<CheckBox> boxes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (FileHeader fh : splits) {
            String fullName = fh.getFileName();
            String shortName = new File(fullName).getName();
            names.add(fullName);
            CheckBox cb = new MaterialCheckBox(context);
            boolean isBase = fullName.equals(baseName);
            cb.setText(isBase ? context.getString(R.string.antisplit_base_suffix, shortName) : shortName);
            cb.setChecked(true);
            cb.setEnabled(!isBase);
            cb.setTag(fullName);
            checkBoxHolder.addView(cb);
            boxes.add(cb);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(checkBoxHolder);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int) (200 * density)));

        boolean deviceOnlySaved = true;
        boolean extractSaved = true;
        boolean autosignSaved = true;
        try {
            deviceOnlySaved = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_device_only", true);
            extractSaved = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("antisplit_extract_native_libs", true);
            autosignSaved = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("autosign", true);
        } catch (Exception ignored) {
        }

        Runnable applyDeviceSelection = () -> {
            for (CheckBox cb : boxes) {
                String fullName = (String) cb.getTag();
                if (fullName.equals(baseName)) {
                    cb.setChecked(true);
                } else {
                    cb.setChecked(matchesDevice(fullName, context));
                }
            }
        };

        MaterialSwitch deviceOnlySwitch = new MaterialSwitch(context);
        deviceOnlySwitch.setText(context.getString(R.string.antisplit_device_only));
        deviceOnlySwitch.setChecked(deviceOnlySaved);
        root.addView(deviceOnlySwitch);

        MaterialSwitch extractSwitch = new MaterialSwitch(context);
        extractSwitch.setText(context.getString(R.string.set_extract_native_libs));
        extractSwitch.setChecked(extractSaved);
        root.addView(extractSwitch);

        root.addView(createCompressionDropdownLayout(context));

        LinearLayout signRow = new LinearLayout(context);
        signRow.setOrientation(LinearLayout.HORIZONTAL);
        signRow.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox autosignBox = new MaterialCheckBox(context);
        autosignBox.setText(R.string.auto_sign);
        autosignBox.setChecked(autosignSaved);
        LinearLayout.LayoutParams signParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        signRow.addView(autosignBox, signParams);
        MaterialButton signSettings = new MaterialButton(context);
        signSettings.setText(R.string.sign_set);
        signSettings.setOnClickListener(context.uiHelper.showSignSettingsDialog());
        signRow.addView(signSettings);
        root.addView(signRow);

        if (deviceOnlySaved) applyDeviceSelection.run();
        deviceOnlySwitch.setOnCheckedChangeListener((b, checked) -> {
            if (checked) applyDeviceSelection.run();
            else for (CheckBox cb : boxes) cb.setChecked(true);
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle("AntiSplit")
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(context.getString(R.string.antisplit_merge), (d, w) -> {
                    boolean deviceOnly = deviceOnlySwitch.isChecked();
                    boolean extractLibs = extractSwitch.isChecked();
                    boolean autosign = autosignBox.isChecked();
                    try {
                        PreferenceManager.getDefaultSharedPreferences(context).edit()
                                .putBoolean("antisplit_device_only", deviceOnly)
                                .putBoolean("antisplit_extract_native_libs", extractLibs)
                                .putBoolean("autosign", autosign)
                                .apply();
                    } catch (Exception ignored) {
                    }
                    Options opts = new Options();
                    opts.autosign = autosign;
                    opts.deviceOnly = deviceOnly;
                    opts.extractNativeLibs = extractLibs;
                    List<String> selected = new ArrayList<>();
                    for (CheckBox cb : boxes) {
                        String fullName = (String) cb.getTag();
                        if (fullName.equals(baseName)) {
                            selected.add(fullName);
                        } else if (deviceOnly) {
                            if (matchesDevice(fullName, context)) selected.add(fullName);
                        } else if (cb.isChecked()) {
                            selected.add(fullName);
                        }
                    }
                    if (!selected.contains(baseName)) selected.add(0, baseName);
                    opts.splitNames = selected;
                    mergeSplitApk(file, context, opts);
                }).show();
    }

    public static void mergeSplitApk(File file, MainActivity context, Options opts) {
        if (opts == null) opts = new Options();
        final Options options = opts;
        ProgressManager pm = new ProgressManager(context, true).show();
        new RunUtil(null, context, null).runInBackground(() -> {
            APKLogger logger = pm.getLogger();
            File dir = new File(context.getCacheDir(), UUID.randomUUID().toString());
            try(ApkBundle bundle = new ApkBundle()) {
                bundle.setAPKLogger(logger);
                applyPreferredCompression(bundle, context);
                if (options.splitNames != null && !options.splitNames.isEmpty()) {
                    if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
                        logger.logMessage(context.getString(R.string.logger_cannot_create_tmp));
                        return false;
                    }
                    try (ZipFile zf = new ZipFile(file)) {
                        for (String name : options.splitNames) {
                            try {
                                FileHeader fh = zf.getFileHeader(name);
                                if (fh != null) zf.extractFile(fh, dir.getAbsolutePath());
                            } catch (Exception e) {
                                logger.logMessage(context.getString(R.string.logger_skip_entry, name, String.valueOf(e.getMessage())));
                            }
                        }
                    }
                    bundle.loadApkDirectory(dir, false);
                } else {
                    try(ArchiveFile zf = new ArchiveFile(file)) {
                        zf.extractAll(dir);
                        bundle.loadApkDirectory(dir, false);
                    }
                }
                for (ApkModule apkModule : bundle.getApkModuleList()) {
                    String protect = Util.isProtected(apkModule);
                    if (protect != null) {
                        logger.logMessage(file.getAbsolutePath());
                               logger.logMessage(protect);
                        return false;
                    }
                }
                try(ApkModule mergedModule = bundle.mergeModules(false)) {
                    sanitizeManifest(mergedModule);
                    try {
                        mergedModule.setExtractNativeLibs(options.extractNativeLibs);
                    } catch (Exception e) {
                        logger.logMessage(context.getString(R.string.logger_extract_native_libs, String.valueOf(e.getMessage())));
                    }
                    mergedModule.refreshTable();
                    mergedModule.refreshManifest();
                    logger.logMessage(context.getString(R.string.logger_writing_apk));
                    String outName = file.getName().replaceFirst("(?i)\\.(xapk|aspk|apks|apkm)$", "_antisplit.apk");
                    if (outName.equals(file.getName())) outName = file.getName() + "_antisplit.apk";
                    File outputFile = FileUtils.getUnusedFile(new File(file.getParentFile(), outName));
                    mergedModule.writeApk(outputFile);
                    pm.dismiss();
                    if (options.autosign) {
                        context.handler.post(() -> SignWrapper.requireAuth(context, sw -> {
                            ProgressManager signPm = new ProgressManager(context, true);
                            signPm.setText(R.string.signing, outputFile.getName());
                            signPm.show();
                            new Thread(() -> {
                                try {
                                    sw.signApk(outputFile);
                                    signPm.dismiss();
                                    context.handler.post(() -> {
                                        Extensions.showMessage(context, context.getString(R.string.logger_saved_to, outputFile.getName()));
                                        context.reloadCurrentFolder();
                                    });
                                } catch (Exception e) {
                                    signPm.dismiss();
                                    new ErrorUtil(context).showError(e);
                                }
                            }).start();
                        }));
                    } else {
                        context.handler.post(() -> {
                            Extensions.showMessage(context, context.getString(R.string.logger_saved_to, outputFile.getName()));
                            context.reloadCurrentFolder();
                        });
                    }
                }
            }
            Util.deleteDir(dir);
            dir.deleteOnExit();
            return true;
        });
    }

    public static File mergeBundle(ApkBundle bundle) throws IOException {
        return mergeBundle(bundle, null);
    }

    public static File mergeBundle(ApkBundle bundle, Context context) throws IOException {
        for (ApkModule apkModule : bundle.getApkModuleList()) {
            String protect = Util.isProtected(apkModule);
            if (protect != null) {

            }
        }
        try(ApkModule mergedModule = bundle.mergeModules(false)) {
            sanitizeManifest(mergedModule);
            if (context != null) {
                try { mergedModule.setCompressionLevel(toDeflaterLevel(getPreferredCompressionLevel(context))); }
                catch (Exception ignored) { }
            }
            mergedModule.refreshTable();
            mergedModule.refreshManifest();
            File outputFile = FileUtils.getUnusedFile(APKExtractorActivity.getAppFolder(), mergedModule.getPackageName() + ".apk");
            mergedModule.writeApk(outputFile);
            return outputFile;
        }
    }

    private static void sanitizeManifest(ApkModule apkModule) {
        if(!apkModule.hasAndroidManifest()){
            return;
        }
        AndroidManifestBlock manifest = apkModule.getAndroidManifest();
        //logMessage("Sanitizing manifest ...");

        AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                AndroidManifest.ID_requiredSplitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestById(manifest,
                AndroidManifest.ID_splitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                AndroidManifest.NAME_splitTypes, null);

        AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                AndroidManifest.NAME_requiredSplitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestByName(manifest,
                AndroidManifest.NAME_splitTypes, null);
        AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                AndroidManifest.ID_extractNativeLibs,
                null, AndroidManifest.NAME_extractNativeLibs);
        AndroidManifestHelper.removeAttributeFromManifestAndApplication(manifest,
                AndroidManifest.ID_isSplitRequired,
                null, AndroidManifest.NAME_isSplitRequired);
        ResXmlElement application = manifest.getApplicationElement();
        List<ResXmlElement> splitMetaDataElements =
                AndroidManifestHelper.listSplitRequired(application);
        boolean splits_removed = false;
        for(ResXmlElement meta : splitMetaDataElements){
            if(!splits_removed){
                splits_removed = removeSplitsTableEntry(meta, apkModule);
            }
            // logMessage("Removed-element : <" + meta.getName() + "> name=\""                    + AndroidManifestBlock.getAndroidNameValue(meta) + "\"");
            application.remove(meta);
        }
        manifest.refresh();
    }
    private static boolean removeSplitsTableEntry(ResXmlElement metaElement, ApkModule apkModule) {
        ResXmlAttribute nameAttribute = metaElement.searchAttributeByResourceId(AndroidManifest.ID_name);
        if(nameAttribute == null){
            return false;
        }
        if(!"com.android.vending.splits".equals(nameAttribute.getValueAsString())){
            return false;
        }
        ResXmlAttribute valueAttribute=metaElement.searchAttributeByResourceId(
                AndroidManifest.ID_value);
        if(valueAttribute==null){
            valueAttribute=metaElement.searchAttributeByResourceId(
                    AndroidManifest.ID_resource);
        }
        if(valueAttribute == null
                || valueAttribute.getValueType() != ValueType.REFERENCE){
            return false;
        }
        if(!apkModule.hasTableBlock()){
            return false;
        }
        TableBlock tableBlock = apkModule.getTableBlock();
        ResourceEntry resourceEntry = tableBlock.getResource(valueAttribute.getData());
        if(resourceEntry == null){
            return false;
        }
        ZipEntryMap zipEntryMap = apkModule.getZipEntryMap();
        for(Entry entry : resourceEntry){
            if(entry == null){
                continue;
            }
            ResValue resValue = entry.getResValue();
            if(resValue == null){
                continue;
            }
            String path = resValue.getValueAsString();
            zipEntryMap.remove(path);
            entry.setNull(true);
            SpecTypePair specTypePair = entry.getTypeBlock()
                    .getParentSpecTypePair();
            specTypePair.removeNullEntries(entry.getId());
        }
        return true;
    }
}
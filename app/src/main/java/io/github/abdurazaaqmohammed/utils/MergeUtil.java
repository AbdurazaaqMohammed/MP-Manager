package io.github.abdurazaaqmohammed.utils;

import io.github.codehasan.colorpicker.extensions.Extensions;

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
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.container.SpecTypePair;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValue;
import com.reandroid.arsc.value.ValueType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.github.abdurazaaqmohammed.ApkExtractor.APKExtractorActivity;
import io.github.abdurazaaqmohammed.MPManager.MainActivity;
public class MergeUtil {
    public static void mergeSplitApk(File file, MainActivity context) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new RunUtil(null, context, null).runInBackground(() -> {
            APKLogger logger = pm.getLogger();
            File dir = new File(context.getCacheDir(), UUID.randomUUID().toString());
            try(ApkBundle bundle = new ApkBundle(); ArchiveFile zf = new ArchiveFile(file)) {
                bundle.setAPKLogger(logger);
                zf.extractAll(dir);
                bundle.loadApkDirectory(dir, false);
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
                    mergedModule.refreshTable();
                    mergedModule.refreshManifest();
                    logger.logMessage("Writing apk ...");
                    File outputFile = io.github.abdurazaaqmohammed.utils.FileUtils.getUnusedFile(new File(file.getParentFile(), file.getName().replaceFirst("\\.(?:xapk|aspk|apk[sm])", "_antisplit.apk")));
                    mergedModule.writeApk(outputFile);
                    pm.dismiss();
                    context.handler.post(() -> {
                        Extensions.showMessage(context, "Saved to: " + outputFile.getName());
                        context.reloadCurrentFolder();
                    });
                }
            }
            Util.deleteDir(dir);
            dir.deleteOnExit();
            return true;
        });
    }

    public static File mergeBundle(ApkBundle bundle) throws IOException {
        for (ApkModule apkModule : bundle.getApkModuleList()) {
            String protect = Util.isProtected(apkModule);
            if (protect != null) {

            }
        }
        try(ApkModule mergedModule = bundle.mergeModules(false)) {
            sanitizeManifest(mergedModule);
            mergedModule.refreshTable();
            mergedModule.refreshManifest();
            File outputFile = io.github.abdurazaaqmohammed.utils.FileUtils.getUnusedFile(APKExtractorActivity.getAppFolder(), mergedModule.getPackageName() + ".apk");
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
        com.reandroid.arsc.chunk.xml.ResXmlAttribute nameAttribute = metaElement.searchAttributeByResourceId(AndroidManifest.ID_name);
        if(nameAttribute == null){
            return false;
        }
        if(!"com.android.vending.splits".equals(nameAttribute.getValueAsString())){
            return false;
        }
        com.reandroid.arsc.chunk.xml.ResXmlAttribute valueAttribute=metaElement.searchAttributeByResourceId(
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
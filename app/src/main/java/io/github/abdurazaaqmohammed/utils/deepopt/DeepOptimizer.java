package io.github.abdurazaaqmohammed.utils.deepopt;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.APKLogger;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.InputSource;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import io.github.abdurazaaqmohammed.utils.FileUtils;

import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrator for the deep APK optimization pipeline:
 * <ol>
 *   <li>manifest scan (components, resource roots, xml class/method roots)</li>
 *   <li>dex indexing (per-dex opcodes, subclass closures, reflection detection)</li>
 *   <li>res-XML scan (custom-view tags, {@code ?attr/} refs, {@code @type/name} refs)</li>
 *   <li>reachability fixpoint + safe empty-method cascade + dangling-ref verification</li>
 *   <li>resource roots from kept code (R$ fields, inlined const IDs, getIdentifier)</li>
 *   <li>Phase-A resource sweep (setNull + delete unused res files, IDs stay stable)</li>
 *   <li>dex rebuild via DexPool + swap into the module</li>
 *   <li>post-build validation, then write with max compression</li>
 * </ol>
 */
public class DeepOptimizer {

    private static final Set<String> COMPONENT_TAGS = new HashSet<>(Arrays.asList(
            "application", "activity", "activity-alias", "service", "receiver",
            "provider", "instrumentation"));

    private final APKLogger logger;
    private final OptimizerReport report;

    private boolean preserveDebug = true;
    private int maxPasses = 25;
    private boolean verifyOutput = true;
    private boolean removeClasses = true;
    private boolean removeMethods = true;
    private boolean removeFields = true;
    private String manifestPackage;

    private final Set<String> keptClasses = new HashSet<>();
    private final Set<String> keptMethodKeys = new HashSet<>();
    private final Set<String> keptFieldKeys = new HashSet<>();
    private final Set<String> modifiedClasses = new HashSet<>();

    public static File optimize(Context context, File inputApk, APKLogger logger) throws Exception {
        return optimize(context, inputApk, null, null, logger);
    }

    public static File optimize(Context context, File inputApk, Set<String> filesToDelete,
                                SharedPreferences settings, APKLogger logger) throws Exception {
        File outFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                FilenameUtils.getBaseName(inputApk.getName()) + "_deep.apk"));
        new DeepOptimizer(logger, settings).run(inputApk, outFile, filesToDelete);
        return outFile;
    }

    private DeepOptimizer(APKLogger logger, SharedPreferences settings) {
        this.logger = logger;
        this.report = new OptimizerReport(logger);
        if (settings != null) {
            preserveDebug = settings.getBoolean("deep_opt_preserve_debug", true);
            maxPasses = Math.max(0, settings.getInt("deep_opt_max_passes", 25));
            verifyOutput = settings.getBoolean("deep_opt_verify_output", true);
            removeClasses = settings.getBoolean("deep_opt_remove_classes", true);
            removeMethods = settings.getBoolean("deep_opt_remove_methods", true);
            removeFields = settings.getBoolean("deep_opt_remove_fields", true);
        }
    }

    private void log(String message) {
        report.log(message);
    }

    private void run(File inputApk, File outFile, Set<String> filesToDelete) throws Exception {
        ApkModule module = ApkModule.loadApkFile(inputApk);
        try {
            manifestPackage = module.getPackageName();
        } catch (Exception ignored) {
        }
        deleteFiles(module, filesToDelete);

        Set<Integer> resourceRoots = new HashSet<>();
        Set<String> nameRefKeys = new LinkedHashSet<>();
        Set<String> xmlMethodRoots = new HashSet<>();
        Set<String> xmlClassRoots = new HashSet<>();
        Set<String> componentClasses = new HashSet<>();

        scanManifest(module, componentClasses, resourceRoots, nameRefKeys, xmlMethodRoots, xmlClassRoots);
        log("Manifest components: " + componentClasses.size());

        DexIndex index = new DexIndex();
        Map<String, DexBackedDexFile> dexFiles = new LinkedHashMap<>();
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (!name.matches("classes\\d*\\.dex")) continue;
            byte[] bytes = readAll(source.openStream());
            dexFiles.put(name, new DexBackedDexFile(DexIndex.opcodesForDex(bytes), bytes));
        }
        index.build(dexFiles);
        log("Classes loaded: " + index.classesTotal);
        if (index.reflectionMode) log("Reflection usage detected - applying conservative keeps");

        scanResXml(module, componentClasses, resourceRoots, nameRefKeys, xmlMethodRoots, xmlClassRoots);

        ReachabilityAnalyzer reachability = new ReachabilityAnalyzer(index, keptClasses,
                keptMethodKeys, keptFieldKeys, report);
        reachability.setRoots(componentClasses, xmlClassRoots, xmlMethodRoots);
        reachability.run();
        if (!removeClasses) expandKeptClasses(index);
        log("Reachable: " + keptMethodKeys.size() + " methods, " + keptFieldKeys.size()
                + " fields, " + keptClasses.size() + "/" + index.classesTotal + " classes");

        if (removeMethods) {
            EmptyMethodCascade cascade = new EmptyMethodCascade(index, reachability, keptClasses,
                    keptMethodKeys, keptFieldKeys, modifiedClasses, report, preserveDebug, maxPasses);
            cascade.run();
            reachability.run();
            if (!removeClasses) expandKeptClasses(index);
            log("After cascade: " + keptMethodKeys.size() + " methods, " + keptFieldKeys.size()
                    + " fields, " + keptClasses.size() + " classes");
        } else {
            log("Method removal disabled - keeping all methods");
        }

        boolean allKeep = module.hasTableBlock()
                ? ResourceRootScanner.scan(index, keptClasses, keptMethodKeys,
                resourceRoots, nameRefKeys, module.getTableBlock()) : false;
        if (allKeep) log("getIdentifier/getString in kept code - keeping all resources");

        ResourceSweeper.sweep(module, resourceRoots, nameRefKeys, allKeep, report);

        DexRebuilder.rebuild(module, index, keptClasses, keptMethodKeys, keptFieldKeys,
                modifiedClasses, removeClasses, removeMethods, removeFields, report);
        report.classesTotal = index.classesTotal;
        report.logSummary();

        compressAll(module);
        module.writeApk(outFile, (path, method, length) -> {
        });
        log("Deep optimization complete: " + outFile.getName());

        if (verifyOutput) {
            try {
                ApkValidator.validate(outFile, index, keptClasses, keptMethodKeys, keptFieldKeys);
                log("Post-build validation passed");
            } catch (Exception e) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
                throw e;
            }
        }
    }

    private void deleteFiles(ApkModule module, Set<String> filesToDelete) {
        if (filesToDelete == null || filesToDelete.isEmpty()) return;
        List<String> toRemove = new ArrayList<>();
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            for (String fd : filesToDelete) {
                if (name.endsWith(fd) || name.matches(fd)) {
                    toRemove.add(name);
                    break;
                }
            }
        }
        for (String name : toRemove) module.getZipEntryMap().remove(name);
    }

    private void expandKeptClasses(DexIndex index) {
        keptClasses.addAll(index.classByType.keySet());
    }

    /**
     * Last step of the pipeline: force every zip entry to DEFLATE at the maximum level so the
     * max-compression setting applies to absolutely everything (including entries the plain
     * optimizer previously left STORED).
     */
    private void compressAll(ApkModule module) {
        try {
            for (InputSource source : module.getInputSources()) {
                source.setMethod(java.util.zip.ZipEntry.DEFLATED);
            }
        } catch (Exception ignored) {
        }
        module.setCompressionLevel(9);
    }

    private void scanManifest(ApkModule module, Set<String> componentClasses, Set<Integer> resourceRoots,
                              Set<String> nameRefKeys, Set<String> xmlMethodRoots, Set<String> xmlClassRoots) {
        if (!module.hasAndroidManifest()) return;
        AndroidManifestBlock manifest = module.getAndroidManifest();
        if (manifest.getManifestElement() != null) {
            walkXml(manifest.getManifestElement(), componentClasses, resourceRoots,
                    nameRefKeys, xmlMethodRoots, xmlClassRoots, true);
        }
        int icon = manifest.getIconResourceId();
        if (icon != 0) resourceRoots.add(icon);
        int roundIcon = manifest.getRoundIconResourceId();
        if (roundIcon != 0) resourceRoots.add(roundIcon);
        Integer label = manifest.getApplicationLabelReference();
        if (label != null) resourceRoots.add(label);
        ResXmlElement application = manifest.getApplicationElement();
        if (application != null) {
            ResXmlAttribute theme = application.searchAttributeByResourceId(AndroidManifest.ID_theme);
            if (theme != null && theme.getValueType().isReference()) resourceRoots.add(theme.getData());
        }
    }

    private void scanResXml(ApkModule module, Set<String> componentClasses, Set<Integer> resourceRoots,
                            Set<String> nameRefKeys, Set<String> xmlMethodRoots, Set<String> xmlClassRoots) {
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (!name.startsWith("res/") || !name.endsWith(".xml")) continue;
            try (InputStream is = source.openStream()) {
                ResXmlDocument doc = AndroidManifestBlock.load(is);
                for (Iterator<ResXmlElement> it = doc.getElements(); it.hasNext(); ) {
                    ResXmlElement element = it.next();
                    walkXml(element, componentClasses, resourceRoots, nameRefKeys,
                            xmlMethodRoots, xmlClassRoots, false);
                }
            } catch (Exception e) {
                log("XML scan skipped for " + name + ": " + e);
            }
        }
    }

    private void walkXml(ResXmlElement element, Set<String> componentClasses, Set<Integer> resourceRoots,
                         Set<String> nameRefKeys, Set<String> methodRoots, Set<String> classRoots,
                         boolean isManifest) {
        String elementName = element.getName();
        if (elementName != null && elementName.contains(".")) classRoots.add(elementName);
        for (int i = 0; i < element.getAttributeCount(); i++) {
            ResXmlAttribute attr = element.getAttributeAt(i);
            String attrName = attr.getName();
            if (attrName == null) continue;
            String lower = attrName.toLowerCase(Locale.US);
            String value = attr.getValueString();

            if (isManifest) {
                if (COMPONENT_TAGS.contains(elementName) && lower.equals("name")
                        && value != null && value.contains(".") && !value.startsWith("@")) {
                    componentClasses.add(toType(value));
                } else if ((lower.equals("targetactivity") && "activity-alias".equals(elementName))
                        || (lower.equals("appcomponentfactory") && "application".equals(elementName))
                        || (lower.equals("backupagent") && "application".equals(elementName))
                        || (lower.equals("classloader") && "application".equals(elementName))) {
                    if (value != null && value.contains(".") && !value.startsWith("@"))
                        componentClasses.add(toType(value));
                }
            } else {
                if (lower.endsWith("onclick") && value != null && !value.isEmpty())
                    methodRoots.add(value);
                if (value != null && value.contains(".")
                        && (lower.equals("class")
                        || (lower.endsWith("name")
                        && ("fragment".equals(elementName) || "view".equals(elementName)))))
                    classRoots.add(value);
            }

            if (attr.getValueType().isReference()) {
                resourceRoots.add(attr.getData());
            } else if (value != null && value.startsWith("@")) {
                String key = ResourceSweeper.resourceRefKey(value);
                if (key != null) nameRefKeys.add(key);
            }
        }
        for (Iterator<ResXmlElement> it = element.getElements(); it.hasNext(); ) {
            ResXmlElement child = it.next();
            walkXml(child, componentClasses, resourceRoots, nameRefKeys,
                    methodRoots, classRoots, isManifest);
        }
    }

    private String toType(String dotted) {
        if (dotted.startsWith("L") && dotted.endsWith(";")) return dotted;
        if (dotted.startsWith(".") && manifestPackage != null)
            dotted = manifestPackage + dotted;
        return "L" + dotted.replace('.', '/') + ";";
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int len;
        while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
        is.close();
        return bos.toByteArray();
    }
}
package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.APKLogger;
import com.reandroid.apkeditor.refactor.AutoRefactor;
import com.reandroid.app.AndroidManifest;
import com.reandroid.archive.InputSource;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.chunk.xml.ResXmlElement;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;

import java.io.ByteArrayInputStream;
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
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Orchestrator for the deep APK optimization pipeline:
 * <ol>
 *   <li>signature strip + junk-file deletion (guarded against res/assets/lib/services)</li>
 *   <li>manifest scan (components, resource roots, xml class/method roots, meta-data class roots)</li>
 *   <li>dex indexing (single-pass member maps, string pool, reflection + dynamic-resource detection)</li>
 *   <li>extended keep roots (META-INF/services, JNI strings in .so, packer/plugin detection)</li>
 *   <li>res-XML scan (custom-view tags, refs; unparseable compiled XML forces conservative keeps)</li>
 *   <li>reachability fixpoint + safe empty-method cascade + dangling-ref verification</li>
 *   <li>resource roots from kept code; name-match keep when resources are looked up by name</li>
 *   <li>Phase-A resource sweep (setNull + delete unused res files, IDs stay stable)</li>
 *   <li>optional Phase-B resource name shortening (only when provably safe)</li>
 *   <li>dex rebuild via DexPool + swap into the module</li>
 *   <li>post-build validation, falling back to a repack-only output instead of losing the run</li>
 * </ol>
 */
public class DeepOptimizer {

    private static final Set<String> COMPONENT_TAGS = new HashSet<>(Arrays.asList(
            "application", "activity", "activity-alias", "service", "receiver",
            "provider", "instrumentation"));
    private static final Pattern FQCN = Pattern.compile(
            "^[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\.[a-zA-Z_$][a-zA-Z0-9_$]*)+$");
    /** Binary XML chunk header: bytes 03 00 08 00 = RES_XML_TYPE (0x0003), header size 8. */
    private static final int RES_XML_MAGIC = 0x00080003;

    private final APKLogger logger;
    private final OptimizerReport report;
    private final DeepOptOptions options;

    private boolean hardConservative;
    private String manifestPackage;

    private final Set<String> keptClasses = new HashSet<>();
    private final Set<String> keptMethodKeys = new HashSet<>();
    private final Set<String> keptFieldKeys = new HashSet<>();
    private final Set<String> modifiedClasses = new HashSet<>();

    public static File optimize(File inputApk, File outFile, Set<String> filesToDelete,
                                DeepOptOptions options, APKLogger logger) throws Exception {
        DeepOptimizer optimizer = new DeepOptimizer(logger, options == null ? DeepOptOptions.defaults() : options);
        optimizer.run(inputApk, outFile, filesToDelete);
        return outFile;
    }

    private DeepOptimizer(APKLogger logger, DeepOptOptions options) {
        this.logger = logger;
        this.options = options;
        this.report = new OptimizerReport(logger);
    }

    private void log(String message) {
        report.log(message);
    }

    private void run(File inputApk, File outFile, Set<String> filesToDelete) throws Exception {
        ApkModule module = ApkModule.loadApkFile(inputApk);
        // The optimize pipeline never resolves attribute names against an android framework
        // table; skipping it removes the /sdcard framework dependency (and its crash path).
        module.setLoadDefaultFramework(false);
        try {
            manifestPackage = module.getPackageName();
        } catch (Exception ignored) {
        }
        stripSignatures(module);
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
        if (!index.dynamicResourceClasses.isEmpty())
            log("Dynamic resource lookups in " + index.dynamicResourceClasses.size() + " classes");

        KeepRootScanner.Result keepRoots = KeepRootScanner.scan(module, index, report);
        xmlClassRoots.addAll(dottedOf(keepRoots.classRoots));

        scanResXml(module, componentClasses, resourceRoots, nameRefKeys, xmlMethodRoots, xmlClassRoots);

        ReachabilityAnalyzer reachability = new ReachabilityAnalyzer(index, keptClasses,
                keptMethodKeys, keptFieldKeys, report);
        reachability.setForceReflectionMode(keepRoots.reflectionConservative);
        reachability.setNativeKeepClasses(keepRoots.nativeClasses);
        reachability.setNativeStrings(keepRoots.nativeStrings);
        reachability.setRoots(componentClasses, xmlClassRoots, xmlMethodRoots);
        String debugType = System.getProperty("deepopt.debugType");
        if (debugType != null) log("debug: xmlClassRoots=" + xmlClassRoots.size()
                + " contains-debug-type=" + xmlClassRoots.contains(debugType.replace('/', '.')));
        reachability.run();
        if (debugType != null) {
            String dbg = "L" + debugType.replace('.', '/') + ";";
            log("debug: after reachability kept=" + keptClasses.contains(dbg));
        }
        // JNI bridge safety net: native libraries (Rust bridges especially - libsignal
        // encodes its JNI strings) can reference ANY class of the native class's package
        // root by name at runtime. Keep the package root tree of every kept class that
        // declares native methods, bounded so huge roots are skipped.
        Set<String> nativeRoots = packageRootsOfNativeClasses(index, keptClasses);
        if (!nativeRoots.isEmpty()) {
            int expanded = 0;
            for (String type : index.classByType.keySet()) {
                String dotted = type.substring(1, type.length() - 1).replace('/', '.');
                if (nativeRoots.contains(packageRoot(dotted))) {
                    xmlClassRoots.add(dotted);
                    expanded++;
                }
            }
            log("JNI package-root keeps: " + expanded + " classes under " + nativeRoots);
            // Native code can reach ANY member (private included) of these classes via
            // encoded JNI strings - keep all members like scanner-found native classes.
            Set<String> nativeMemberKeep = new HashSet<>(keepRoots.nativeClasses);
            for (String type : index.classByType.keySet()) {
                String dotted = type.substring(1, type.length() - 1).replace('/', '.');
                if (nativeRoots.contains(packageRoot(dotted))) nativeMemberKeep.add(type);
            }
            reachability.setNativeKeepClasses(nativeMemberKeep);
            reachability.run();
        }
        if (!options.removeClasses) expandKeptClasses(index);
        log("Reachable: " + keptMethodKeys.size() + " methods, " + keptFieldKeys.size()
                + " fields, " + keptClasses.size() + "/" + index.classesTotal + " classes");

        if (options.removeMethods && options.maxPasses > 0) {
            EmptyMethodCascade cascade = new EmptyMethodCascade(index, reachability, keptClasses,
                    keptMethodKeys, keptFieldKeys, modifiedClasses, report,
                    options.preserveDebug, options.maxPasses);
            cascade.run();
            reachability.run();
            if (!options.removeClasses) expandKeptClasses(index);
            log("After cascade: " + keptMethodKeys.size() + " methods, " + keptFieldKeys.size()
                    + " fields, " + keptClasses.size() + " classes");
        } else {
            log("Method removal disabled - keeping all methods");
        }
        if (debugType != null) {
            String dbg = "L" + debugType.replace('.', '/') + ";";
            log("debug: after cascade kept=" + keptClasses.contains(dbg));
        }

        ResourceTableIndex tableIndex = module.hasTableBlock()
                ? ResourceTableIndex.build(module.getTableBlock(), manifestPackage) : null;
        boolean dynamicLookup = tableIndex != null && ResourceRootScanner.scan(index, keptClasses,
                keptMethodKeys, resourceRoots, nameRefKeys, tableIndex);
        Set<String> keepNames = null;
        if (dynamicLookup) {
            if (options.safeMode) {
                keepNames = index.allStrings;
                report.dynamicNameKeep = true;
                log("Resources resolved by name - keeping every entry whose name matches a dex string ("
                        + keepNames.size() + " strings)");
            } else {
                log("Aggressive mode - ignoring dynamic resource lookups");
            }
        }
        hardConservative |= keepRoots.keepAllResources;

        if (tableIndex != null)
            ResourceSweeper.sweep(module, tableIndex, resourceRoots, nameRefKeys, keepNames,
                    hardConservative, report);

        phaseB(module, dynamicLookup);

        DexRebuilder.rebuild(module, index, keptClasses, keptMethodKeys, keptFieldKeys,
                modifiedClasses, options.removeClasses, options.removeMethods,
                options.removeFields, report);
        report.classesTotal = index.classesTotal;
        report.logSummary();

        compressAll(module);
        writeApk(module, outFile);
        ApkZipAlignUtil.ensureInstallable(outFile);
        log("Deep optimization complete: " + outFile.getName());

        if (options.verifyOutput) {
            try {
                ApkValidator.validate(outFile, index, keptClasses, keptMethodKeys, keptFieldKeys);
                log("Post-build validation passed");
            } catch (Exception e) {
                report.log("Validation failed: " + e);
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
                writeFallbackRepack(inputApk, outFile, filesToDelete);
                throw new Exception("Deep optimization failed validation; repack-only output written: " + e, e);
            }
        }
    }

    private void phaseB(ApkModule module, boolean dynamicLookup) {
        if (!options.shortenNames || hardConservative || dynamicLookup || !module.hasTableBlock()) return;
        boolean hasNative = false;
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (name != null && name.endsWith(".so")) { hasNative = true; break; }
        }
        if (hasNative) {
            log("Native libraries present - skipping resource name shortening");
            return;
        }
        try {
            log("Phase B: shortening resource names");
            AutoRefactor refactor = new AutoRefactor(module);
            refactor.setApkLogger(logger);
            refactor.refactor();
        } catch (Exception e) {
            report.log("Resource name shortening failed (kept original names): " + e);
        }
    }

    /** Safety net: a valid, installable repack of the ORIGINAL apk instead of nothing. */
    private void writeFallbackRepack(File inputApk, File outFile, Set<String> filesToDelete) throws Exception {
        report.log("Writing repack-only fallback output");
        ApkModule fresh = ApkModule.loadApkFile(inputApk);
        fresh.setLoadDefaultFramework(false);
        stripSignatures(fresh);
        deleteFiles(fresh, filesToDelete);
        compressAll(fresh);
        fresh.writeApk(outFile, (path, method, length) -> {
        });
        ApkZipAlignUtil.ensureInstallable(outFile);
    }

    private void writeApk(ApkModule module, File outFile) throws Exception {
        int[] counter = {0};
        module.writeApk(outFile, (path, method, length) -> {
            if (++counter[0] % 2048 == 0) log("Writing: " + counter[0] + " entries");
        });
    }

    private void stripSignatures(ApkModule module) {
        List<String> toRemove = new ArrayList<>();
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (name == null || !name.startsWith("META-INF/")) continue;
            String entry = name.substring("META-INF/".length());
            if (entry.endsWith(".SF") || entry.endsWith(".RSA") || entry.endsWith(".DSA")
                    || entry.endsWith(".EC") || entry.equalsIgnoreCase("MANIFEST.MF"))
                toRemove.add(name);
        }
        for (String name : toRemove) module.getZipEntryMap().remove(name);
    }

    private void deleteFiles(ApkModule module, Set<String> filesToDelete) {
        if (filesToDelete == null || filesToDelete.isEmpty()) return;
        List<String> toRemove = new ArrayList<>();
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (name == null || isProtectedPath(name)) continue;
            for (String fd : filesToDelete) {
                if (name.endsWith(fd) || name.matches(fd)) {
                    toRemove.add(name);
                    break;
                }
            }
        }
        for (String name : toRemove) module.getZipEntryMap().remove(name);
    }

    /** Paths the junk-file list must never touch - they are load-bearing at runtime. */
    private static boolean isProtectedPath(String name) {
        return name.startsWith("res/") || name.startsWith("assets/") || name.startsWith("lib/")
                || name.startsWith("META-INF/services/") || name.endsWith(".dex")
                || name.startsWith("r/") || name.equals("AndroidManifest.xml")
                || name.equals("resources.arsc");
    }

    private void expandKeptClasses(DexIndex index) {
        keptClasses.addAll(index.classByType.keySet());
    }

    /** Package roots (first three dot segments) of kept classes that declare native methods. */
    private static Set<String> packageRootsOfNativeClasses(DexIndex index, Set<String> keptClasses) {
        Set<String> roots = new HashSet<>();
        for (String type : keptClasses) {
            if (!index.nativeDeclaringClasses.contains(type)) continue;
            String dotted = type.substring(1, type.length() - 1).replace('/', '.');
            roots.add(packageRoot(dotted));
        }
        roots.remove(null);
        roots.remove("");
        // Guard: a root owning a huge slice of the app would defeat pruning entirely.
        Set<String> bounded = new HashSet<>();
        for (String root : roots) {
            int count = 0;
            for (String type : index.classByType.keySet()) {
                String dotted = type.substring(1, type.length() - 1).replace('/', '.');
                if (packageRoot(dotted).equals(root) && ++count > 2000) break;
            }
            if (count <= 2000) bounded.add(root);
        }
        return bounded;
    }

    /** First three segments of the class's package ("org.signal.libsignal.internal.Native" -> "org.signal.libsignal"). */
    private static String packageRoot(String dottedClass) {
        String pkg = dottedClass.contains(".") ? dottedClass.substring(0, dottedClass.lastIndexOf('.')) : dottedClass;
        String[] seg = pkg.split("\\.");
        int take = Math.min(3, seg.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < take; i++) {
            if (i > 0) sb.append('.');
            sb.append(seg[i]);
        }
        return sb.toString();
    }

    /**
     * Last step of the pipeline: force every compressible zip entry to DEFLATE at the maximum
     * level so the max-compression setting applies to as much as possible. Entries that the
     * installer requires to be STORED (manifest, resource table, raw resources) and native
     * libraries, which must stay STORED and page aligned when not extracted at install time,
     * keep their original method.
     */
    private void compressAll(ApkModule module) {
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (name != null && (ApkZipAlignUtil.mustStore(name)
                    || (name.startsWith("lib/") && name.endsWith(".so")))) continue;
            try {
                source.setMethod(ZipEntry.DEFLATED);
            } catch (Exception e) {
                log("Compression skipped for " + name + ": " + e);
            }
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
            try {
                byte[] bytes = readAll(source.openStream());
                if (!isBinaryXml(bytes)) continue; // plain-text XML (res/raw) holds no compiled refs
                ResXmlDocument doc = AndroidManifestBlock.load(new ByteArrayInputStream(bytes));
                for (Iterator<ResXmlElement> it = doc.getElements(); it.hasNext(); ) {
                    ResXmlElement element = it.next();
                    walkXml(element, componentClasses, resourceRoots, nameRefKeys,
                            xmlMethodRoots, xmlClassRoots, false);
                }
            } catch (Exception e) {
                log("Unparseable compiled XML " + name + " - keeping all resources");
                hardConservative = true;
            }
        }
    }

    private static boolean isBinaryXml(byte[] bytes) {
        if (bytes.length < 4) return false;
        int magic = (bytes[0] & 0xFF) | ((bytes[1] & 0xFF) << 8) | ((bytes[2] & 0xFF) << 16) | ((bytes[3] & 0xFF) << 24);
        return magic == RES_XML_MAGIC;
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
                // Reflection frameworks read class names from manifest metadata
                // (WorkManager startup, Firebase init, appComponentFactory-like setups).
                if (value != null && FQCN.matcher(value).matches() && !value.startsWith("@"))
                    classRoots.add(toType(value));
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

    private Set<String> dottedOf(Set<String> types) {
        Set<String> out = new HashSet<>();
        for (String type : types) {
            if (type.startsWith("L") && type.endsWith(";"))
                out.add(type.substring(1, type.length() - 1).replace('/', '.'));
        }
        return out;
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

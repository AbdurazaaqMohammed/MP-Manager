package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.DexFileInputSource;
import com.reandroid.app.AndroidManifest;
import com.reandroid.arsc.array.ResValueMapArray;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValueMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;


/**
 * Post-build verification. Re-parses the OUTPUT apk and checks:
 * <ol>
 *   <li>the manifest is present and parseable, and its resource references resolve</li>
 *   <li>the resource table re-parses and no kept (non-null) resource references an
 *       all-removed entry</li>
 *   <li>every dex re-parses</li>
 *   <li>no kept class/method/field reference dangles</li>
 * </ol>
 * Any failure aborts the deep run; the caller falls back to a repack-only output.
 */
public final class ApkValidator {

    private ApkValidator() {
    }

    public static void validate(File outFile, DexIndex index, Set<String> keptClasses,
                                Set<String> keptMethodKeys, Set<String> keptFieldKeys) throws Exception {
        try (ApkModule module = ApkModule.loadApkFile(outFile)) {
            module.setLoadDefaultFramework(false);
            if (!module.hasAndroidManifest())
                throw new IOException("Output APK has no parseable AndroidManifest.xml");
            verifyManifestReferences(module);
            if (module.hasTableBlock()) {
                ResourceTableIndex tableIndex = ResourceTableIndex.build(module.getTableBlock(),
                        module.getPackageName());
                verifyResources(tableIndex);
            }

            for (DexFileInputSource source : module.listDexFiles()) {
                byte[] bytes;
                try (InputStream is = source.getInputSource().openStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
                    bytes = bos.toByteArray();
                }
                new DexBackedDexFile(DexIndex.opcodesForDex(bytes), bytes);
            }
        }

        verifyNoDanglingRefs(index, keptClasses, keptMethodKeys, keptFieldKeys);
    }

    /** Icon/label/theme references in the manifest must resolve to live entries. */
    private static void verifyManifestReferences(ApkModule module) throws IOException {
        AndroidManifestBlock manifest = module.getAndroidManifest();
        if (!module.hasTableBlock()) return;
        ResourceTableIndex tableIndex;
        try {
            tableIndex = ResourceTableIndex.build(module.getTableBlock(), module.getPackageName());
        } catch (Exception e) {
            throw new IOException("resources.arsc failed to re-parse: " + e, e);
        }
        checkLive(tableIndex, manifest.getIconResourceId(), "manifest icon");
        checkLive(tableIndex, manifest.getRoundIconResourceId(), "manifest round icon");
        Integer label = manifest.getApplicationLabelReference();
        if (label != null) checkLive(tableIndex, label, "manifest label");
    }

    private static void checkLive(ResourceTableIndex tableIndex, int resId, String what) throws IOException {
        if (resId == 0) return;
        com.reandroid.arsc.model.ResourceEntry entry = tableIndex.byId(resId);
        if (entry == null) return; // framework or unrelated package
        for (Entry config : entry) {
            if (config != null && !config.isNull()) return; // live
        }
        throw new IOException(what + " resource 0x" + Integer.toHexString(resId)
                + " was removed but is still referenced");
    }

    private static void verifyResources(ResourceTableIndex tableIndex) throws IOException {
        for (Integer id : tableIndex.byId.keySet()) {
            com.reandroid.arsc.model.ResourceEntry resEntry = tableIndex.byId.get(id);
            for (Entry config : resEntry) {
                if (config == null || config.isNull()) continue;
                try {
                    if (config.isComplex()) {
                        ResValueMapArray maps = config.getResValueMapArray();
                        if (maps == null) continue;
                        for (int i = 0; i < maps.size(); i++) {
                            ResValueMap item = maps.get(i);
                            if (item.getValueType().isReference())
                                checkLive(tableIndex, item.getData(), "Kept resource 0x"
                                        + Integer.toHexString(id) + " reference");
                        }
                    } else if (config.getValueType().isReference()) {
                        checkLive(tableIndex, config.getResValue().getData(), "Kept resource 0x"
                                + Integer.toHexString(id) + " reference");
                    }
                } catch (IOException ex) {
                    throw ex;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static void verifyNoDanglingRefs(DexIndex index, Set<String> keptClasses,
                                             Set<String> keptMethodKeys, Set<String> keptFieldKeys)
            throws IOException {
        Set<String> violations = new HashSet<>();
        for (String type : keptClasses) {
            ClassDef classDef = index.classByType.get(type);
            if (classDef == null) continue;
            checkBatch(ReferenceExtractor.extractClass(classDef), index, keptClasses,
                    keptMethodKeys, keptFieldKeys, violations);
            for (Method method : classDef.getMethods()) {
                if (keptMethodKeys.contains(DexIndex.methodKey(method)))
                    checkBatch(ReferenceExtractor.extractMethod(method), index, keptClasses,
                            keptMethodKeys, keptFieldKeys, violations);
            }
            for (Field field : classDef.getFields()) {
                if (keptFieldKeys.contains(DexIndex.fieldKey(field))
                        && field.getInitialValue() != null) {
                    ReferenceExtractor.ReferenceBatch batch = new ReferenceExtractor.ReferenceBatch();
                    ReferenceExtractor.extractEncodedValue(field.getInitialValue(), batch);
                    checkBatch(batch, index, keptClasses, keptMethodKeys, keptFieldKeys, violations);
                }
            }
        }
        if (!violations.isEmpty())
            throw new IOException("Dangling references in output: " + violations);
    }

    private static void checkBatch(ReferenceExtractor.ReferenceBatch batch, DexIndex index,
                                   Set<String> keptClasses, Set<String> keptMethodKeys,
                                   Set<String> keptFieldKeys, Set<String> violations) {
        for (ReferenceExtractor.MethodCall call : batch.calls) {
            String resolved = index.resolveMethodKey(call.reference);
            if (resolved != null && !keptMethodKeys.contains(resolved))
                violations.add("method " + resolved);
        }
        for (FieldReference ref : batch.fields) {
            String resolved = index.resolveFieldKey(ref);
            if (resolved != null && !keptFieldKeys.contains(resolved))
                violations.add("field " + resolved);
        }
        for (String refType : batch.types) {
            if (!keptClasses.contains(refType) && index.classByType.containsKey(refType))
                violations.add("type " + refType);
        }
    }
}

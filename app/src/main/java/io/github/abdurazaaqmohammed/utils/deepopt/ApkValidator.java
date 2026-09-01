package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.reandroid.apk.ApkModule;
import com.reandroid.apk.DexFileInputSource;
import com.reandroid.arsc.array.ResValueMapArray;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.model.ResourceEntry;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResValueMap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


public final class ApkValidator {

    private ApkValidator() {
    }

    public static void validate(File outFile, DexIndex index, Set<String> keptClasses,
                                Set<String> keptMethodKeys, Set<String> keptFieldKeys) throws Exception {
        try (ApkModule module = ApkModule.loadApkFile(outFile)) {
            if (module.getAndroidManifest() == null) throw new IOException("Output APK has no parseable AndroidManifest.xml");
            if (module.hasTableBlock()) verifyResources(module.getTableBlock());

            for (DexFileInputSource source : module.listDexFiles()) {
                byte[] bytes;
                try (InputStream is = source.getInputSource().openStream(); java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
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

    private static void verifyResources(TableBlock table) throws IOException {
        Map<Integer, ResourceEntry> byId = new HashMap<>();
        for (PackageBlock pkg : table.listPackages()) {
            for (java.util.Iterator<ResourceEntry> it = pkg.getResources(); it.hasNext(); ) {
                ResourceEntry entry = it.next();
                byId.put(entry.getResourceId(), entry);
            }
        }
        for (Map.Entry<Integer, ResourceEntry> e : byId.entrySet()) {
            ResourceEntry entry = e.getValue();
            for (Entry config : entry) {
                if (config == null || config.isNull()) continue;
                try {
                    if (config.isComplex()) {
                        ResValueMapArray maps = config.getResValueMapArray();
                        if (maps == null) continue;
                        for (int i = 0; i < maps.size(); i++) {
                            ResValueMap item = maps.get(i);
                            if (item.getValueType().isReference()) {
                                ResourceEntry target = byId.get(item.getData());
                                if (target != null && isAllNull(target))
                                    throw new IOException("Kept resource " + e.getKey()
                                            + " references removed entry " + item.getData());
                            }
                        }
                    } else if (config.getValueType().isReference()) {
                        ResourceEntry referenced = config.getValueAsReference();
                        if (referenced != null) {
                            ResourceEntry target = byId.get(referenced.getResourceId());
                            if (target != null && isAllNull(target))
                                throw new IOException("Kept resource " + e.getKey()
                                        + " references removed entry " + referenced.getResourceId());
                        }
                    }
                } catch (IOException ex) {
                    throw ex;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean isAllNull(ResourceEntry entry) {
        for (Entry config : entry) {
            if (config != null && !config.isNull()) return false;
        }
        return true;
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
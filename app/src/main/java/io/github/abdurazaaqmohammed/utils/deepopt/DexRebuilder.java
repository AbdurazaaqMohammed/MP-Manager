package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.ByteInputSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rebuilds the dexes from the kept sets. Per dex, in original order, only dexes that
 * actually changed are rewritten (a {@link DexPool} interned through a
 * {@link MemoryDataStore}); untouched dexes keep their original bytes. Dexes with no kept
 * classes are dropped — except {@code classes.dex}, which is always kept present (empty
 * but valid) while any other dex exists, for legacy ART safety.
 */
public final class DexRebuilder {

    private DexRebuilder() {
    }

    public static void rebuild(ApkModule module, DexIndex index, Set<String> keptClasses,
                               Set<String> keptMethodKeys, Set<String> keptFieldKeys,
                               Set<String> modifiedClasses, boolean removeClasses,
                               boolean removeMethods, boolean removeFields,
                               OptimizerReport report) throws java.io.IOException {
        int removedClasses = 0;
        int removedMethods = 0;
        int removedFields = 0;
        boolean multiDex = index.typesByDex.size() > 1;
        for (Map.Entry<String, List<String>> dexEntry : index.typesByDex.entrySet()) {
            String dexName = dexEntry.getKey();
            List<String> types = dexEntry.getValue();
            List<ImmutableClassDef> kept = new ArrayList<>();
            boolean dexModified = false;
            for (String type : types) {
                if (removeClasses && !keptClasses.contains(type)) {
                    removedClasses++;
                    dexModified = true;
                    continue;
                }
                ClassDef classDef = index.classByType.get(type);
                if (classDef == null) {
                    removedClasses++;
                    dexModified = true;
                    continue;
                }
                List<Method> direct = new ArrayList<>();
                List<Method> virtual = new ArrayList<>();
                boolean memberRemoved = false;
                for (Method m : classDef.getDirectMethods()) {
                    if (!removeMethods
                            || keptMethodKeys.contains(DexIndex.methodKey(m))
                            || (m.getAccessFlags() & AccessFlags.PRIVATE.getValue()) == 0) direct.add(m);
                    else { removedMethods++; memberRemoved = true; }
                }
                for (Method m : classDef.getVirtualMethods()) {
                    if (!removeMethods
                            || keptMethodKeys.contains(DexIndex.methodKey(m))
                            || (m.getAccessFlags() & AccessFlags.PRIVATE.getValue()) == 0) virtual.add(m);
                    else { removedMethods++; memberRemoved = true; }
                }
                List<Field> staticFields = new ArrayList<>();
                List<Field> instanceFields = new ArrayList<>();
                for (Field f : classDef.getStaticFields()) {
                    if (!removeFields
                            || keptFieldKeys.contains(DexIndex.fieldKey(f))
                            || (f.getAccessFlags() & AccessFlags.PRIVATE.getValue()) == 0) staticFields.add(f);
                    else { removedFields++; memberRemoved = true; }
                }
                for (Field f : classDef.getInstanceFields()) {
                    if (!removeFields
                            || keptFieldKeys.contains(DexIndex.fieldKey(f))
                            || (f.getAccessFlags() & AccessFlags.PRIVATE.getValue()) == 0) instanceFields.add(f);
                    else { removedFields++; memberRemoved = true; }
                }
                if (memberRemoved || modifiedClasses.contains(type)) {
                    dexModified = true;
                    kept.add(new ImmutableClassDef(type, classDef.getAccessFlags(), classDef.getSuperclass(),
                            classDef.getInterfaces(), classDef.getSourceFile(), classDef.getAnnotations(),
                            staticFields, instanceFields, direct, virtual));
                } else {
                    kept.add(ImmutableClassDef.of(classDef));
                }
            }
            if (kept.isEmpty()) {
                if ("classes.dex".equals(dexName) && multiDex) {
                    writeEmptyDex(module, dexName, index);
                    report.log("Rewrote empty " + dexName + " (legacy ART safety)");
                } else {
                    module.getZipEntryMap().remove(dexName);
                    report.log("Removed empty dex: " + dexName);
                }
                continue;
            }
            if (!dexModified) continue;
            DexPool pool = new DexPool(index.dexOpcodes.getOrDefault(dexName, Opcodes.forDexVersion(35)));
            for (ImmutableClassDef classDef : kept) pool.internClass(classDef);
            MemoryDataStore store = new MemoryDataStore();
            pool.writeTo(store);
            module.add(new ByteInputSource(Arrays.copyOf(store.getData(), store.getSize()), dexName));
            report.log("Rewrote " + dexName + " (" + kept.size() + " classes)");
        }
        report.classesRemoved = removedClasses;
        report.methodsRemoved += removedMethods;
        report.fieldsRemoved += removedFields;
    }

    private static void writeEmptyDex(ApkModule module, String dexName, DexIndex index) throws java.io.IOException {
        DexPool pool = new DexPool(index.dexOpcodes.getOrDefault(dexName, Opcodes.forDexVersion(35)));
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        module.add(new ByteInputSource(Arrays.copyOf(store.getData(), store.getSize()), dexName));
    }
}
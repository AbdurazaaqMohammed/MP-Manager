package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global index over every dex in the module: type -> ClassDef, per-dex ordering,
 * subclass/ancestor closures, method/field lookup maps, string pool harvest,
 * {@code @Keep} precomputation and reflection/dynamic-resource detection.
 * {@link #classByType} is a mutable working map: the empty-method cascade rebuilds
 * classes (fewer members) and puts them back so later passes see the current shape.
 */
public class DexIndex {

    public static final Set<String> REFLECTION_METHOD_NAMES = new HashSet<>(Arrays.asList(
            "forName", "getMethod", "getDeclaredMethod", "getMethods", "getDeclaredMethods",
            "getField", "getDeclaredField", "getFields", "getDeclaredFields",
            "newInstance", "getConstructor", "getDeclaredConstructor", "getConstructors",
            "getDeclaredConstructors"));
    /**
     * Resources methods that resolve resources by NAME at runtime. Only these force the
     * dynamic-resource mode; typed accessors like getString(int) use inlined IDs that the
     * const-scan already tracks.
     */
    public static final Set<String> DYNAMIC_RESOURCE_METHODS = new HashSet<>(Arrays.asList(
            "getIdentifier", "getResourceName", "getResourceEntryName", "getResourceTypeName"));
    private static final Set<String> KEEP_ANNOTATIONS = new HashSet<>(Arrays.asList(
            "Landroidx/annotation/Keep;", "Landroid/support/annotation/Keep;"));

    public final Map<String, ClassDef> classByType = new LinkedHashMap<>();
    public final Map<String, ClassDef> originalClasses = new HashMap<>();
    public final Map<String, String> classToDex = new HashMap<>();
    public final Map<String, List<String>> typesByDex = new LinkedHashMap<>();
    public final Map<String, Opcodes> dexOpcodes = new HashMap<>();
    public final Map<String, Set<String>> subclasses = new HashMap<>();
    public final Map<String, Set<String>> allDescendants = new HashMap<>();
    public final Map<String, Set<String>> allAncestors = new HashMap<>();
    public final Map<String, Method> methodByKey = new HashMap<>();
    public final Map<String, Field> fieldByKey = new HashMap<>();
    public final Map<String, Map<String, Set<String>>> methodsBySig = new HashMap<>();
    public final Map<String, Map<String, Set<String>>> fieldsBySig = new HashMap<>();
    private final Map<String, String> resolvedMethodCache = new HashMap<>();
    private final Map<String, String> resolvedFieldCache = new HashMap<>();
    /** Every string in every dex string pool - the source for dynamic-name keeps. */
    public final Set<String> allStrings = new HashSet<>();
    /** Classes containing a call to a {@link #DYNAMIC_RESOURCE_METHODS} method. */
    public final Set<String> dynamicResourceClasses = new HashSet<>();
    /** Classes carrying {@code @Keep} - always kept with all their members. */
    public final Set<String> keepAnnotatedClasses = new HashSet<>();
    public final Set<String> keepAnnotatedMethodKeys = new HashSet<>();
    public final Set<String> keepAnnotatedFieldKeys = new HashSet<>();

    public boolean reflectionMode;
    public int classesTotal;

    private final Map<String, ReferenceExtractor.ReferenceBatch> methodBatchCache = new HashMap<>();
    private final Map<String, ReferenceExtractor.ReferenceBatch> classBatchCache = new HashMap<>();

    public static Opcodes opcodesForDex(byte[] dexBytes) {
        try {
            if (dexBytes != null && dexBytes.length >= 8) {
                int version = (dexBytes[4] - '0') * 100 + (dexBytes[5] - '0') * 10 + (dexBytes[6] - '0');
                if (version >= 35 && version <= 999) return Opcodes.forDexVersion(version);
            }
        } catch (Exception ignored) {
        }
        return Opcodes.forDexVersion(35);
    }

    public void build(Map<String, DexBackedDexFile> dexFiles) {
        for (Map.Entry<String, DexBackedDexFile> entry : dexFiles.entrySet()) {
            String dexName = entry.getKey();
            DexBackedDexFile dex = entry.getValue();
            dexOpcodes.put(dexName, dex.getOpcodes());
            harvestStrings(dex);
            List<String> types = new ArrayList<>();
            for (ClassDef classDef : dex.getClasses()) {
                classByType.put(classDef.getType(), classDef);
                originalClasses.put(classDef.getType(), classDef);
                classToDex.put(classDef.getType(), dexName);
                types.add(classDef.getType());
            }
            typesByDex.put(dexName, types);
        }
        classesTotal = classByType.size();
        buildClosures();
        buildMemberMaps();
    }

    /** Whole dex string pool - far cheaper than walking every instruction. */
    private void harvestStrings(DexBackedDexFile dex) {
        try {
            for (String s : dex.getStringSection()) {
                if (s != null) allStrings.add(s);
            }
        } catch (Throwable ignored) {
        }
    }

    private void buildClosures() {
        for (Map.Entry<String, ClassDef> e : classByType.entrySet()) {
            String sup = e.getValue().getSuperclass();
            if (sup != null) subclasses.computeIfAbsent(sup, k -> new HashSet<>()).add(e.getKey());
            for (String iface : e.getValue().getInterfaces())
                subclasses.computeIfAbsent(iface, k -> new HashSet<>()).add(e.getKey());
        }
        for (String type : classByType.keySet()) {
            allDescendants.put(type, collectDescendants(type));
            allAncestors.put(type, collectAncestors(type));
        }
    }

    /** Single pass: member maps, {@code @Keep} flags and reflection/dynamic-resource detection. */
    private void buildMemberMaps() {
        for (ClassDef classDef : classByType.values()) {
            String type = classDef.getType();
            Map<String, Set<String>> sigIndex = new HashMap<>();
            for (Method m : classDef.getMethods()) {
                methodByKey.put(methodKey(m), m);
                sigIndex.computeIfAbsent(methodSigKey(m), k -> new HashSet<>()).add(methodKey(m));
                if (hasKeep(m.getAnnotations())) keepAnnotatedMethodKeys.add(methodKey(m));
            }
            methodsBySig.put(type, sigIndex);
            Map<String, Set<String>> fieldSigIndex = new HashMap<>();
            for (Field f : classDef.getFields()) {
                fieldByKey.put(fieldKey(f), f);
                fieldSigIndex.computeIfAbsent(f.getName() + ":" + f.getType(), k -> new HashSet<>())
                        .add(fieldKey(f));
                if (hasKeep(f.getAnnotations())) keepAnnotatedFieldKeys.add(fieldKey(f));
            }
            fieldsBySig.put(type, fieldSigIndex);
            if (hasKeep(classDef.getAnnotations())) keepAnnotatedClasses.add(type);
            scanClassCode(classDef);
        }
    }

    private void scanClassCode(ClassDef classDef) {
        boolean dynamicResource = false;
        boolean reflect = false;
        for (Method method : classDef.getMethods()) {
            MethodImplementation impl = method.getImplementation();
            if (impl == null) continue;
            for (Instruction instruction : impl.getInstructions()) {
                if (!(instruction instanceof ReferenceInstruction)) continue;
                Reference reference = ((ReferenceInstruction) instruction).getReference();
                if (reference instanceof MethodReference ref) {
                    String defining = ref.getDefiningClass();
                    String name = ref.getName();
                    if ("Landroid/content/res/Resources;".equals(defining)
                            && DYNAMIC_RESOURCE_METHODS.contains(name)) {
                        dynamicResource = true;
                    } else if (("Ljava/lang/Class;".equals(defining) && REFLECTION_METHOD_NAMES.contains(name))
                            || defining.startsWith("Ljava/lang/reflect/")) {
                        reflect = true;
                    }
                } else if (reference instanceof TypeReference) {
                    if (((TypeReference) reference).getType().startsWith("Ljava/lang/reflect/"))
                        reflect = true;
                }
            }
        }
        if (dynamicResource) dynamicResourceClasses.add(classDef.getType());
        if (reflect) reflectionMode = true;
    }

    private static boolean hasKeep(Set<? extends Annotation> annotations) {
        if (annotations == null) return false;
        for (Annotation annotation : annotations) {
            if (KEEP_ANNOTATIONS.contains(annotation.getType())) return true;
        }
        return false;
    }

    public boolean isKeepAnnotated(ClassDef classDef) {
        return keepAnnotatedClasses.contains(classDef.getType());
    }

    public boolean isKeepAnnotated(Method method) {
        return keepAnnotatedMethodKeys.contains(methodKey(method));
    }

    public boolean isKeepAnnotated(Field field) {
        return keepAnnotatedFieldKeys.contains(fieldKey(field));
    }

    /** Cached reference extraction; caches must be cleared after any class rebuild. */
    public ReferenceExtractor.ReferenceBatch extractMethodBatch(Method method) {
        String key = methodKey(method);
        ReferenceExtractor.ReferenceBatch cached = methodBatchCache.get(key);
        if (cached != null) return cached;
        ReferenceExtractor.ReferenceBatch batch = ReferenceExtractor.extractMethod(method);
        methodBatchCache.put(key, batch);
        return batch;
    }

    public ReferenceExtractor.ReferenceBatch extractClassBatch(ClassDef classDef) {
        String key = classDef.getType();
        ReferenceExtractor.ReferenceBatch cached = classBatchCache.get(key);
        if (cached != null) return cached;
        ReferenceExtractor.ReferenceBatch batch = ReferenceExtractor.extractClass(classDef);
        classBatchCache.put(key, batch);
        return batch;
    }

    /** Call after rebuilding any class in {@link #classByType} so stale batches are dropped. */
    public void clearBatchCaches() {
        methodBatchCache.clear();
        classBatchCache.clear();
    }

    private Set<String> collectDescendants(String type) {
        Set<String> out = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.add(type);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            Set<String> children = subclasses.get(current);
            if (children == null) continue;
            for (String child : children) if (out.add(child)) stack.add(child);
        }
        out.remove(type);
        return out;
    }

    private Set<String> collectAncestors(String type) {
        Set<String> out = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.add(type);
        while (!stack.isEmpty()) {
            String current = stack.pop();
            ClassDef classDef = classByType.get(current);
            if (classDef == null) continue;
            if (classDef.getSuperclass() != null && out.add(classDef.getSuperclass()))
                stack.add(classDef.getSuperclass());
            for (String iface : classDef.getInterfaces()) if (out.add(iface)) stack.add(iface);
        }
        out.remove(type);
        return out;
    }

    public static String methodKey(Method method) {
        return method.getDefiningClass() + "->" + method.getName()
                + "(" + String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
    }

    public static String methodRefKey(MethodReference reference) {
        return reference.getDefiningClass() + "->" + reference.getName()
                + "(" + String.join("", reference.getParameterTypes()) + ")" + reference.getReturnType();
    }

    public static String methodSigKey(MethodReference reference) {
        return reference.getName() + "(" + String.join("", reference.getParameterTypes()) + ")"
                + reference.getReturnType();
    }

    public static String methodSigKey(Method method) {
        return method.getName() + "(" + String.join("", method.getParameterTypes()) + ")"
                + method.getReturnType();
    }

    public static String fieldKey(Field field) {
        return field.getDefiningClass() + "->" + field.getName() + ":" + field.getType();
    }

    public static String fieldRefKey(FieldReference reference) {
        return reference.getDefiningClass() + "->" + reference.getName() + ":" + reference.getType();
    }

    public static String toType(String dotted) {
        if (dotted == null) return null;
        if (dotted.startsWith("L") && dotted.endsWith(";")) return dotted;
        if (!dotted.contains(".")) return null;
        return "L" + dotted.replace('.', '/') + ";";
    }

    /**
     * Resolves a method reference over the ORIGINAL class hierarchy (Kotlin multifile
     * facades reference an empty class whose methods live in its superclass). Returns the
     * key of the declaring method, or null when resolution reaches a framework class.
     */
    public String resolveMethodKey(MethodReference reference) {
        String refKey = methodRefKey(reference);
        String cached = resolvedMethodCache.get(refKey);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String resolved = resolveMethodKeyUncached(reference);
        resolvedMethodCache.put(refKey, resolved == null ? "" : resolved);
        return resolved;
    }

    private String resolveMethodKeyUncached(MethodReference reference) {
        String current = reference.getDefiningClass();
        int guard = 0;
        while (current != null && guard++ < 128) {
            ClassDef classDef = originalClasses.get(current);
            if (classDef == null) return null;
            String sig = methodSigKey(reference);
            Set<String> keys = methodsBySig.getOrDefault(current, Collections.emptyMap()).get(sig);
            if (keys != null && !keys.isEmpty()) return keys.iterator().next();
            current = classDef.getSuperclass();
        }
        return null;
    }

    public String resolveFieldKey(FieldReference reference) {
        String refKey = fieldRefKey(reference);
        String cached = resolvedFieldCache.get(refKey);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String resolved = resolveFieldKeyUncached(reference);
        resolvedFieldCache.put(refKey, resolved == null ? "" : resolved);
        return resolved;
    }

    private String resolveFieldKeyUncached(FieldReference reference) {
        String current = reference.getDefiningClass();
        String sig = reference.getName() + ":" + reference.getType();
        int guard = 0;
        while (current != null && guard++ < 128) {
            ClassDef classDef = originalClasses.get(current);
            if (classDef == null) return null;
            Set<String> keys = fieldsBySig.getOrDefault(current, Collections.emptyMap()).get(sig);
            if (keys != null && !keys.isEmpty()) return keys.iterator().next();
            current = classDef.getSuperclass();
        }
        return null;
    }
}

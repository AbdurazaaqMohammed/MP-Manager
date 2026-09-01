package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Global index over every dex in the module: type -> ClassDef, per-dex ordering,
 * subclass/ancestor closures, method/field lookup maps and reflection detection.
 * {@link #classByType} is a mutable working map: the empty-method cascade rebuilds
 * classes (fewer members) and puts them back so later passes see the current shape.
 */
public class DexIndex {

    public static final Set<String> REFLECTION_METHOD_NAMES = new HashSet<>(Arrays.asList(
            "forName", "getMethod", "getDeclaredMethod", "getMethods", "getDeclaredMethods",
            "getField", "getDeclaredField", "getFields", "getDeclaredFields",
            "newInstance", "getConstructor", "getDeclaredConstructor", "getConstructors",
            "getDeclaredConstructors"));
    public static final Set<String> RESOURCES_METHOD_NAMES = new HashSet<>(Arrays.asList(
            "getIdentifier", "getString", "getResourceName", "getResourceEntryName",
            "getResourceTypeName", "getStringArray", "getText", "getQuantityString"));

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
    public final Set<String> allStrings = new HashSet<>();

    public boolean reflectionMode;
    public int classesTotal;

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
        detectReflection();
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

    private void buildMemberMaps() {
        for (ClassDef classDef : classByType.values()) {
            Map<String, Set<String>> sigIndex = new HashMap<>();
            for (Method m : classDef.getMethods()) {
                methodByKey.put(methodKey(m), m);
                sigIndex.computeIfAbsent(methodSigKey(m), k -> new HashSet<>()).add(methodKey(m));
            }
            methodsBySig.put(classDef.getType(), sigIndex);
            Map<String, Set<String>> fieldSigIndex = new HashMap<>();
            for (Field f : classDef.getFields()) {
                fieldByKey.put(fieldKey(f), f);
                fieldSigIndex.computeIfAbsent(f.getName() + ":" + f.getType(), k -> new HashSet<>())
                        .add(fieldKey(f));
            }
            fieldsBySig.put(classDef.getType(), fieldSigIndex);
        }
    }

    /** Defining-class-aware reflection detection; keys off real call sites, not bare names. */
    private void detectReflection() {
        for (ClassDef classDef : classByType.values()) {
            for (Method method : classDef.getMethods()) {
                MethodImplementation impl = method.getImplementation();
                if (impl == null) continue;
                for (Instruction instruction : impl.getInstructions()) {
                    if (!(instruction instanceof ReferenceInstruction)) continue;
                    Reference reference = ((ReferenceInstruction) instruction).getReference();
                    if (reference instanceof StringReference) {
                        allStrings.add(((StringReference) reference).getString());
                    } else if (reference instanceof MethodReference) {
                        MethodReference ref = (MethodReference) reference;
                        String defining = ref.getDefiningClass();
                        String name = ref.getName();
                        if ("Landroid/content/res/Resources;".equals(defining)
                                && RESOURCES_METHOD_NAMES.contains(name)) {
                            reflectionMode = true;
                        } else if (("Ljava/lang/Class;".equals(defining) && REFLECTION_METHOD_NAMES.contains(name))
                                || defining.startsWith("Ljava/lang/reflect/")) {
                            reflectionMode = true;
                        }
                    } else if (reference instanceof TypeReference) {
                        String type = ((TypeReference) reference).getType();
                        if (type.startsWith("Ljava/lang/reflect/")) reflectionMode = true;
                    }
                }
            }
        }
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
package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.reandroid.apk.APKLogger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Seeds + worklist reachability fixpoint over the working classes ({@link DexIndex#classByType}).
 * Kept sets are recomputed from the roots on every {@link #run()}; this is what lets the
 * empty-method cascade drop now-unreachable members between passes.
 */
public class ReachabilityAnalyzer {

    private final DexIndex index;
    private final Set<String> keptClasses;
    private final Set<String> keptMethodKeys;
    private final Set<String> keptFieldKeys;
    private final OptimizerReport report;
    private final Deque<ReferenceExtractor.ReferenceBatch> pendingClassBatches = new ArrayDeque<>();

    private Set<String> componentClasses = Collections.emptySet();
    private Set<String> classRoots = Collections.emptySet();
    private Set<String> methodRoots = Collections.emptySet();

    public ReachabilityAnalyzer(DexIndex index, Set<String> keptClasses, Set<String> keptMethodKeys,
                                Set<String> keptFieldKeys, OptimizerReport report) {
        this.index = index;
        this.keptClasses = keptClasses;
        this.keptMethodKeys = keptMethodKeys;
        this.keptFieldKeys = keptFieldKeys;
        this.report = report;
    }

    public void setRoots(Set<String> componentClasses, Set<String> classRoots, Set<String> methodRoots) {
        this.componentClasses = componentClasses;
        this.classRoots = classRoots;
        this.methodRoots = methodRoots;
    }

    public void run() {
        keptClasses.clear();
        keptMethodKeys.clear();
        keptFieldKeys.clear();
        pendingClassBatches.clear();
        Deque<Method> worklist = new ArrayDeque<>();

        for (String dotted : classRoots) {
            String type = DexIndex.toType(dotted);
            if (type != null) addKeptClass(type, worklist);
        }
        for (String type : componentClasses) addKeptClass(type, worklist);
        for (ClassDef classDef : index.classByType.values()) {
            for (Method method : classDef.getMethods()) {
                if (methodRoots.contains(method.getName())) keepMethod(method, worklist);
            }
        }
        fixpoint(worklist);

        if (index.reflectionMode) {
            report.reflectionMode = true;
            reflectionSeeds(worklist);
            fixpoint(worklist);
        }
    }

    private void fixpoint(Deque<Method> worklist) {
        Set<String> processed = new HashSet<>();
        while (!worklist.isEmpty() || !pendingClassBatches.isEmpty()) {
            if (!pendingClassBatches.isEmpty()) {
                handleBatch(pendingClassBatches.poll(), worklist);
                continue;
            }
            Method method = worklist.poll();
            if (!processed.add(DexIndex.methodKey(method))) continue;
            ReferenceExtractor.ReferenceBatch batch = ReferenceExtractor.extractMethod(method);
            handleBatch(batch, worklist);
        }
    }

    private void handleBatch(ReferenceExtractor.ReferenceBatch batch, Deque<Method> worklist) {
        for (ReferenceExtractor.MethodCall call : batch.calls) dispatchMethod(call, worklist);
        for (FieldReference ref : batch.fields) keepField(ref, worklist);
        for (String type : batch.types) addKeptClass(type, worklist);
    }

    private void dispatchMethod(ReferenceExtractor.MethodCall call, Deque<Method> worklist) {
        MethodReference ref = call.reference;
        String name = call.opcode == null ? null : call.opcode.name;
        if (call.opcode == null || name.startsWith("invoke-custom") || name.startsWith("invoke-polymorphic")) {
            keepSignatureIn(ref.getDefiningClass(), ref, worklist);
            for (String sub : index.allDescendants.getOrDefault(ref.getDefiningClass(), Collections.emptySet()))
                keepSignatureInType(sub, ref, worklist);
        } else if (name.startsWith("invoke-direct") || name.startsWith("invoke-static")) {
            keepSignatureIn(ref.getDefiningClass(), ref, worklist);
        } else if (name.startsWith("invoke-super")) {
            keepSignatureIn(ref.getDefiningClass(), ref, worklist);
        } else {
            keepSignatureIn(ref.getDefiningClass(), ref, worklist);
            for (String sub : index.allDescendants.getOrDefault(ref.getDefiningClass(), Collections.emptySet()))
                keepSignatureInType(sub, ref, worklist);
        }
    }

    /**
     * Keeps the matching method in the defining class and every app ancestor, because
     * ART resolves method references up the superclass chain (e.g. Kotlin multifile
     * facades emit invoke-static against an empty facade class).
     */
    private void keepSignatureIn(String type, MethodReference ref, Deque<Method> worklist) {
        if (type == null) return;
        keepSignatureInType(type, ref, worklist);
        for (String ancestor : index.allAncestors.getOrDefault(type, Collections.emptySet()))
            keepSignatureInType(ancestor, ref, worklist);
    }

    private void keepSignatureInType(String type, MethodReference ref, Deque<Method> worklist) {
        addKeptClass(type, worklist);
        Set<String> keys = index.methodsBySig.getOrDefault(type, Collections.emptyMap())
                .get(DexIndex.methodSigKey(ref));
        if (keys == null) return;
        for (String key : keys) {
            if (keptMethodKeys.add(key)) {
                Method method = index.methodByKey.get(key);
                if (method != null) worklist.add(method);
            }
        }
    }

    private void keepField(FieldReference ref, Deque<Method> worklist) {
        addKeptClass(ref.getDefiningClass(), worklist);
        keptFieldKeys.add(DexIndex.fieldRefKey(ref));
        String resolved = index.resolveFieldKey(ref);
        if (resolved != null) keptFieldKeys.add(resolved);
    }

    private void keepMethod(Method method, Deque<Method> worklist) {
        String key = DexIndex.methodKey(method);
        if (keptMethodKeys.add(key)) worklist.add(method);
    }

    private void addKeptClass(String type, Deque<Method> worklist) {
        if (type == null) return;
        if (!keptClasses.add(type)) return;
        ClassDef classDef = index.classByType.get(type);
        if (classDef == null) return;
        keepEntryMembers(classDef, worklist);
        for (Method method : classDef.getMethods()) {
            if ((method.getAccessFlags() & AccessFlags.PRIVATE.getValue()) != 0) continue;
            worklist.add(method);
        }
        pendingClassBatches.add(ReferenceExtractor.extractClass(classDef));
        if (classDef.getSuperclass() != null) addKeptClass(classDef.getSuperclass(), worklist);
        for (String iface : classDef.getInterfaces()) addKeptClass(iface, worklist);
    }

    private void keepEntryMembers(ClassDef classDef, Deque<Method> worklist) {
        String type = classDef.getType();
        boolean component = componentClasses.contains(type);
        boolean serializable = classDef.getInterfaces().contains("Ljava/io/Serializable;");
        boolean externalizable = classDef.getInterfaces().contains("Ljava/io/Externalizable;");
        boolean parcelable = classDef.getInterfaces().contains("Landroid/os/Parcelable;");
        boolean enumType = "Ljava/lang/Enum;".equals(classDef.getSuperclass());
        for (Method method : classDef.getMethods()) {
            String name = method.getName();
            boolean entry = name.equals("<init>") || name.equals("<clinit>")
                    || (method.getAccessFlags() & AccessFlags.NATIVE.getValue()) != 0
                    || name.startsWith("on")
                    || name.equals("attachBaseContext") || name.equals("finalize")
                    || component || enumType
                    || isMain(method)
                    || (serializable && (name.equals("writeObject") || name.equals("readObject")
                    || name.equals("readObjectNoData") || name.equals("readResolve")))
                    || (externalizable && (name.equals("readExternal") || name.equals("writeExternal")))
                    || (parcelable && (name.equals("writeToParcel") || name.equals("describeContents")));
            if (entry) keepMethod(method, worklist);
        }
        for (Field field : classDef.getFields()) {
            String name = field.getName();
            boolean entryField = component || enumType || serializable || externalizable || parcelable
                    || name.equals("serialVersionUID")
                    || (parcelable && name.equals("CREATOR"));
            if (entryField) keptFieldKeys.add(DexIndex.fieldKey(field));
        }
    }

    private boolean isMain(Method method) {
        if (!"main".equals(method.getName())) return false;
        List<? extends CharSequence> params = method.getParameterTypes();
        return params.size() == 1 && "[Ljava/lang/String;".equals(params.get(0).toString())
                && "V".equals(method.getReturnType());
    }

    private void reflectionSeeds(Deque<Method> worklist) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> strings = collectKeptStrings();
            for (String s : strings) {
                String type = DexIndex.toType(s);
                if (type != null && index.classByType.containsKey(type) && !keptClasses.contains(type)) {
                    addKeptClass(type, worklist);
                    changed = true;
                }
            }
            for (String type : new HashSet<>(keptClasses)) {
                ClassDef classDef = index.classByType.get(type);
                if (classDef == null) continue;
                for (Method method : classDef.getMethods()) {
                    if (strings.contains(method.getName())
                            && keptMethodKeys.add(DexIndex.methodKey(method))) {
                        worklist.add(method);
                        changed = true;
                    }
                }
                for (Field field : classDef.getFields()) {
                    if (strings.contains(field.getName())
                            && keptFieldKeys.add(DexIndex.fieldKey(field)))
                        changed = true;
                }
            }
            if (changed) fixpoint(worklist);
        }
    }

    /** String literals referenced from the currently kept methods only. */
    private Set<String> collectKeptStrings() {
        Set<String> strings = new HashSet<>();
        for (String type : keptClasses) {
            ClassDef classDef = index.classByType.get(type);
            if (classDef == null) continue;
            for (Method method : classDef.getMethods()) {
                if (!keptMethodKeys.contains(DexIndex.methodKey(method))) continue;
                com.android.tools.smali.dexlib2.iface.MethodImplementation impl = method.getImplementation();
                if (impl == null) continue;
                for (com.android.tools.smali.dexlib2.iface.instruction.Instruction instruction : impl.getInstructions()) {
                    if (!(instruction instanceof com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)) continue;
                    com.android.tools.smali.dexlib2.iface.reference.Reference ref =
                            ((com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction) instruction).getReference();
                    if (ref instanceof com.android.tools.smali.dexlib2.iface.reference.StringReference)
                        strings.add(((com.android.tools.smali.dexlib2.iface.reference.StringReference) ref).getString());
                }
            }
        }
        return strings;
    }
}
package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.reandroid.apk.APKLogger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Empty-method cascade. Repeatedly removes reachable-but-empty methods under a safe
 * override rule (private/static empties always; virtual empties only when the whole
 * app super-class chain is provably empty, no interface declares the signature, and the
 * direct superclass is app code), then nopps every remaining call site (in every class,
 * kept or not) and re-runs the reachability fixpoint to drop orphans. A dangling-reference
 * verification guards every pass; methods referenced from annotations or field initial
 * values are never removed.
 */
public class EmptyMethodCascade {

    private final DexIndex index;
    private final ReachabilityAnalyzer reachability;
    private final Set<String> keptClasses;
    private final Set<String> keptMethodKeys;
    private final Set<String> keptFieldKeys;
    private final Set<String> modifiedClasses;
    private final OptimizerReport report;
    private final boolean preserveDebug;
    private final int maxPasses;

    private Set<String> staticMethodRefs = new HashSet<>();

    public EmptyMethodCascade(DexIndex index, ReachabilityAnalyzer reachability, Set<String> keptClasses,
                              Set<String> keptMethodKeys, Set<String> keptFieldKeys,
                              Set<String> modifiedClasses, OptimizerReport report,
                              boolean preserveDebug, int maxPasses) {
        this.index = index;
        this.reachability = reachability;
        this.keptClasses = keptClasses;
        this.keptMethodKeys = keptMethodKeys;
        this.keptFieldKeys = keptFieldKeys;
        this.modifiedClasses = modifiedClasses;
        this.report = report;
        this.preserveDebug = preserveDebug;
        this.maxPasses = maxPasses;
    }

    public void run() {
        collectStaticMethodRefs();
        Set<String> removedKeys = new HashSet<>();
        boolean changed = true;
        int pass = 0;
        while (changed && pass < maxPasses && !report.conservativeLock) {
            changed = false;
            pass++;
            for (String type : new HashSet<>(index.classByType.keySet())) {
                ClassDef classDef = index.classByType.get(type);
                if (classDef == null || !keptClasses.contains(type)) continue;
                List<Method> removable = new ArrayList<>();
                for (Method method : classDef.getMethods()) {
                    if (isSafelyRemovable(method, type)) removable.add(method);
                }
                if (removable.isEmpty()) continue;
                changed = true;
                for (Method method : removable) {
                    removedKeys.add(DexIndex.methodKey(method));
                    report.methodsRemoved++;
                }
                index.classByType.put(type, removeMethods(classDef, removable));
                modifiedClasses.add(type);
            }
            if (!changed) break;
            for (String type : new ArrayList<>(index.classByType.keySet())) {
                ClassDef classDef = index.classByType.get(type);
                if (classDef != null) rewriteCallSites(classDef, removedKeys);
            }
            reachability.run();
            if (verifyDanglingRefs()) {
                report.conservativeLock = true;
                report.log("Dangling references detected after empty-method removal; conservative lock engaged");
            }
        }
        report.passesUsed = pass;
        report.fixedPoint = pass <= 1 || !changed;
        if (pass > 0) report.log("Empty-method cascade: " + pass + " pass(es), " + report.methodsRemoved + " methods removed");
    }

    /** Method keys referenced from annotations / field initial values can never be removed. */
    private void collectStaticMethodRefs() {
        Set<String> refs = new HashSet<>();
        for (ClassDef classDef : index.classByType.values()) {
            ReferenceExtractor.ReferenceBatch batch = ReferenceExtractor.extractClass(classDef);
            for (ReferenceExtractor.MethodCall call : batch.calls)
                refs.add(DexIndex.methodRefKey(call.reference));
            for (Method method : classDef.getMethods()) {
                if (method.getAnnotations() == null) continue;
                ReferenceExtractor.ReferenceBatch mb = new ReferenceExtractor.ReferenceBatch();
                for (Annotation annotation : method.getAnnotations())
                    ReferenceExtractor.extractAnnotation(annotation, mb);
                for (ReferenceExtractor.MethodCall call : mb.calls)
                    refs.add(DexIndex.methodRefKey(call.reference));
            }
        }
        staticMethodRefs = refs;
    }

    private boolean isSafelyRemovable(Method method, String type) {
        String name = method.getName();
        if (name.equals("<init>") || name.equals("<clinit>") || name.startsWith("on")) return false;
        int flags = method.getAccessFlags();
        if ((flags & AccessFlags.ABSTRACT.getValue()) != 0
                || (flags & AccessFlags.NATIVE.getValue()) != 0) return false;
        MethodImplementation impl = method.getImplementation();
        if (impl == null || !isReturnVoidOnly(method)) return false;
        if (staticMethodRefs.contains(DexIndex.methodKey(method))) return false;
        if ((flags & AccessFlags.PRIVATE.getValue()) != 0 || (flags & AccessFlags.STATIC.getValue()) != 0)
            return true;
        ClassDef classDef = index.classByType.get(type);
        if (classDef == null || classDef.getSuperclass() == null) return false;
        if (index.classByType.get(classDef.getSuperclass()) == null) return false;
        if (hasInterfaceSignature(classDef, method)) return false;
        String current = classDef.getSuperclass();
        while (current != null) {
            ClassDef sup = index.classByType.get(current);
            if (sup == null) break;
            for (Method m : sup.getMethods()) {
                if (!sameSignature(m, method)) continue;
                int sf = m.getAccessFlags();
                if ((sf & AccessFlags.ABSTRACT.getValue()) != 0) return false;
                if (m.getImplementation() != null && !isReturnVoidOnly(m)) return false;
            }
            if (hasInterfaceSignature(sup, method)) return false;
            current = sup.getSuperclass();
        }
        return true;
    }

    private boolean hasInterfaceSignature(ClassDef classDef, Method method) {
        for (String iface : classDef.getInterfaces()) {
            ClassDef ifaceDef = index.classByType.get(iface);
            if (ifaceDef == null) return true;
            for (Method m : ifaceDef.getMethods()) {
                if (sameSignature(m, method)) return true;
            }
            if (hasInterfaceSignature(ifaceDef, method)) return true;
        }
        return false;
    }

    private boolean sameSignature(Method m, Method other) {
        return DexIndex.methodSigKey(m).equals(DexIndex.methodSigKey(other));
    }

    private boolean isReturnVoidOnly(Method method) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return false;
        boolean hasReturn = false;
        for (Instruction instruction : impl.getInstructions()) {
            String op = instruction.getOpcode().name;
            if (op.equals("return-void")) {
                hasReturn = true;
                continue;
            }
            if (!op.equals("nop")) return false;
        }
        return hasReturn;
    }

    private ClassDef removeMethods(ClassDef classDef, List<Method> removable) {
        Set<String> keys = new HashSet<>();
        for (Method m : removable) keys.add(DexIndex.methodKey(m));
        List<Method> direct = new ArrayList<>();
        List<Method> virtual = new ArrayList<>();
        for (Method m : classDef.getDirectMethods())
            if (!keys.contains(DexIndex.methodKey(m))) direct.add(m);
        for (Method m : classDef.getVirtualMethods())
            if (!keys.contains(DexIndex.methodKey(m))) virtual.add(m);
        return new ImmutableClassDef(classDef.getType(), classDef.getAccessFlags(),
                classDef.getSuperclass(), classDef.getInterfaces(), classDef.getSourceFile(),
                classDef.getAnnotations(), classDef.getStaticFields(), classDef.getInstanceFields(),
                direct, virtual);
    }

    private void rewriteCallSites(ClassDef classDef, Set<String> removedKeys) {
        List<Method> direct = new ArrayList<>();
        List<Method> virtual = new ArrayList<>();
        boolean modified = false;
        for (Method m : classDef.getDirectMethods()) {
            Method nm = CodeRewriter.nopCallsToRemoved(index, m, removedKeys, preserveDebug);
            if (nm != m) nm = LivenessAnalyzer.eliminateDead(nm, preserveDebug);
            if (nm != m) modified = true;
            direct.add(nm);
        }
        for (Method m : classDef.getVirtualMethods()) {
            Method nm = CodeRewriter.nopCallsToRemoved(index, m, removedKeys, preserveDebug);
            if (nm != m) nm = LivenessAnalyzer.eliminateDead(nm, preserveDebug);
            if (nm != m) modified = true;
            virtual.add(nm);
        }
        if (!modified) return;
        index.classByType.put(classDef.getType(), new ImmutableClassDef(classDef.getType(),
                classDef.getAccessFlags(), classDef.getSuperclass(), classDef.getInterfaces(),
                classDef.getSourceFile(), classDef.getAnnotations(),
                classDef.getStaticFields(), classDef.getInstanceFields(), direct, virtual));
        modifiedClasses.add(classDef.getType());
    }

    private boolean verifyDanglingRefs() {
        List<String> violations = new ArrayList<>();
        for (String type : keptClasses) {
            ClassDef classDef = index.classByType.get(type);
            if (classDef == null) continue;
            checkBatch(ReferenceExtractor.extractClass(classDef), violations);
            for (Method method : classDef.getMethods()) {
                if (keptMethodKeys.contains(DexIndex.methodKey(method)))
                    checkBatch(ReferenceExtractor.extractMethod(method), violations);
            }
            for (Field field : classDef.getFields()) {
                if (!keptFieldKeys.contains(DexIndex.fieldKey(field))) continue;
                if (field.getAnnotations() == null) continue;
                ReferenceExtractor.ReferenceBatch fb = new ReferenceExtractor.ReferenceBatch();
                for (Annotation annotation : field.getAnnotations())
                    ReferenceExtractor.extractAnnotation(annotation, fb);
                checkBatch(fb, violations);
            }
        }
        if (violations.isEmpty()) return false;
        for (String violation : violations) report.log("Dangling ref: " + violation);
        return true;
    }

    private void checkBatch(ReferenceExtractor.ReferenceBatch batch, List<String> violations) {
        for (ReferenceExtractor.MethodCall call : batch.calls) {
            MethodReference ref = call.reference;
            String resolved = index.resolveMethodKey(ref);
            if (resolved != null && !keptMethodKeys.contains(resolved))
                violations.add("method " + resolved);
        }
        for (FieldReference ref : batch.fields) {
            String resolved = index.resolveFieldKey(ref);
            if (resolved != null && !keptFieldKeys.contains(resolved))
                violations.add("field " + resolved);
        }
        for (String type : batch.types) {
            if (!keptClasses.contains(type) && index.classByType.containsKey(type))
                violations.add("type " + type);
        }
    }
}
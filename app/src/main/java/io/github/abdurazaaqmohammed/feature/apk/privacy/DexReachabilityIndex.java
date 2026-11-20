package io.github.abdurazaaqmohammed.feature.apk.privacy;

import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.DualReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodProtoReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.util.ReferenceUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Streaming Multi-DEX Reachability Discovery Engine.
 * <p>
 * Pass 1 discovery engine for the Dual-Module Sanitizer Suite. Scans an {@link Iterable}
 * of {@link DexBackedDexFile} objects sequentially to avoid out-of-memory (OOM) errors on large
 * enterprise APKs with 25+ DEX files.
 * </p>
 * <p>
 * Indexes all invoked methods, referenced fields, and referenced classes into compact
 * in-memory Sets to verify active call graphs and prevent placebo patching (reporting success
 * on dead code that is never invoked at runtime).
 * </p>
 */
public final class DexReachabilityIndex {

    private final Set<String> invokedMethodDescriptors = new HashSet<>();
    private final Set<String> invokedMethodsWithReturn = new HashSet<>();
    private final Set<String> invokedMethodsByName = new HashSet<>();

    private final Set<String> referencedFieldDescriptors = new HashSet<>();
    private final Set<String> referencedFieldsByName = new HashSet<>();

    private final Set<String> referencedClasses = new HashSet<>();

    private int totalInstructionsScanned = 0;

    public DexReachabilityIndex() {}

    /**
     * Builds a reachability index by streaming through DEX files sequentially.
     *
     * @param dexFiles an iterable sequence of DexBackedDexFile objects.
     * @return a populated DexReachabilityIndex.
     */
    public static DexReachabilityIndex buildFromDexFiles(Iterable<? extends DexBackedDexFile> dexFiles) {
        DexReachabilityIndex index = new DexReachabilityIndex();
        if (dexFiles != null) {
            for (DexBackedDexFile dex : dexFiles) {
                if (dex != null) {
                    index.indexDexFile(dex);
                }
            }
        }
        return index;
    }

    /**
     * Builds a reachability index from varargs DEX files.
     *
     * @param dexFiles one or more DexBackedDexFile objects.
     * @return a populated DexReachabilityIndex.
     */
    public static DexReachabilityIndex buildFromDexFiles(DexBackedDexFile... dexFiles) {
        DexReachabilityIndex index = new DexReachabilityIndex();
        if (dexFiles != null) {
            for (DexBackedDexFile dex : dexFiles) {
                if (dex != null) {
                    index.indexDexFile(dex);
                }
            }
        }
        return index;
    }

    /**
     * Incrementally indexes all references from a single DEX file.
     *
     * @param dexFile the DEX file to scan.
     */
    public void indexDexFile(DexBackedDexFile dexFile) {
        if (dexFile == null) return;

        for (ClassDef classDef : dexFile.getClasses()) {
            if (classDef == null) continue;

            // Superclass reference
            String superclass = classDef.getSuperclass();
            if (superclass != null) {
                recordClassReference(superclass);
            }

            // Interface references
            List<String> interfaces = classDef.getInterfaces();
            if (interfaces != null) {
                for (String iface : interfaces) {
                    recordClassReference(iface);
                }
            }

            // Scan method instructions
            for (Method method : classDef.getMethods()) {
                if (method == null) continue;
                MethodImplementation impl = method.getImplementation();
                if (impl == null) continue;

                // Exception handler class references
                for (TryBlock<? extends ExceptionHandler> tryBlock : impl.getTryBlocks()) {
                    for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                        String exType = handler.getExceptionType();
                        if (exType != null) {
                            recordClassReference(exType);
                        }
                    }
                }

                // Reference instructions
                for (Instruction inst : impl.getInstructions()) {
                    totalInstructionsScanned++;
                    if (inst instanceof ReferenceInstruction) {
                        recordReference(((ReferenceInstruction) inst).getReference());
                        if (inst instanceof DualReferenceInstruction) {
                            recordReference(((DualReferenceInstruction) inst).getReference2());
                        }
                    }
                }
            }
        }
    }

    private void recordReference(Reference ref) {
        if (ref == null) return;

        if (ref instanceof MethodReference) {
            MethodReference methodRef = (MethodReference) ref;
            String desc = ReferenceUtil.getMethodDescriptor(methodRef);
            invokedMethodDescriptors.add(desc);

            String declaringClass = methodRef.getDefiningClass();
            String name = methodRef.getName();
            String returnType = methodRef.getReturnType();

            invokedMethodsWithReturn.add(declaringClass + "->" + name + ":" + returnType);
            invokedMethodsByName.add(declaringClass + "->" + name);

            recordClassReference(declaringClass);
            recordClassReference(returnType);
            for (CharSequence param : methodRef.getParameterTypes()) {
                if (param != null) {
                    recordClassReference(param.toString());
                }
            }
        } else if (ref instanceof FieldReference) {
            FieldReference fieldRef = (FieldReference) ref;
            String desc = ReferenceUtil.getFieldDescriptor(fieldRef);
            referencedFieldDescriptors.add(desc);

            String declaringClass = fieldRef.getDefiningClass();
            String name = fieldRef.getName();
            String type = fieldRef.getType();

            referencedFieldsByName.add(declaringClass + "->" + name);

            recordClassReference(declaringClass);
            recordClassReference(type);
        } else if (ref instanceof TypeReference) {
            TypeReference typeRef = (TypeReference) ref;
            recordClassReference(typeRef.getType());
        } else if (ref instanceof MethodProtoReference) {
            MethodProtoReference protoRef = (MethodProtoReference) ref;
            recordClassReference(protoRef.getReturnType());
            for (CharSequence param : protoRef.getParameterTypes()) {
                if (param != null) {
                    recordClassReference(param.toString());
                }
            }
        }
    }

    private void recordClassReference(String type) {
        if (type == null || type.isEmpty()) return;
        // Strip array dimensions: [[Ljava/lang/String; -> Ljava/lang/String;
        while (type.startsWith("[")) {
            type = type.substring(1);
        }
        if (type.startsWith("L") && type.endsWith(";")) {
            referencedClasses.add(type);
        }
    }

    /**
     * Normalizes a class descriptor from Java FQCN, slash path, or standard Dalvik format.
     */
    public static String normalizeClassType(String type) {
        if (type == null) return "";
        type = type.trim();
        if (type.isEmpty()) return "";
        if (type.length() == 1) return type; // primitive descriptor: Z, B, C, S, I, J, F, D, V
        if (type.startsWith("[")) {
            int bracketCount = 0;
            while (bracketCount < type.length() && type.charAt(bracketCount) == '[') {
                bracketCount++;
            }
            String component = type.substring(bracketCount);
            return type.substring(0, bracketCount) + normalizeClassType(component);
        }
        if (type.startsWith("L") && type.endsWith(";")) {
            type = type.substring(1, type.length() - 1);
        }
        return "L" + type.replace('.', '/') + ";";
    }

    /**
     * Normalizes return types (mapping keywords like void, boolean, int to descriptors).
     */
    public static String normalizeReturnType(String returnType) {
        if (returnType == null) return null;
        returnType = returnType.trim();
        if (returnType.isEmpty()) return "";
        switch (returnType) {
            case "void": return "V";
            case "boolean": return "Z";
            case "byte": return "B";
            case "char": return "C";
            case "short": return "S";
            case "int": return "I";
            case "long": return "J";
            case "float": return "F";
            case "double": return "D";
            default: return normalizeClassType(returnType);
        }
    }

    /**
     * Checks whether a method is invoked by any instruction in the indexed DEX files.
     *
     * @param declaringClass the class defining the method.
     * @param methodName the method name.
     * @param returnType the return type descriptor or Java type keyword.
     * @return true if an invocation instruction exists.
     */
    public boolean isMethodInvoked(String declaringClass, String methodName, String returnType) {
        if (declaringClass == null || methodName == null) return false;
        String normClass = normalizeClassType(declaringClass);
        if (returnType == null) {
            return isMethodInvoked(normClass, methodName);
        }
        String normReturn = normalizeReturnType(returnType);
        return invokedMethodsWithReturn.contains(normClass + "->" + methodName + ":" + normReturn);
    }

    /**
     * Checks whether a method name on the given class is invoked, regardless of signature.
     *
     * @param declaringClass the class defining the method.
     * @param methodName the method name.
     * @return true if an invocation instruction exists.
     */
    public boolean isMethodInvoked(String declaringClass, String methodName) {
        if (declaringClass == null || methodName == null) return false;
        String normClass = normalizeClassType(declaringClass);
        return invokedMethodsByName.contains(normClass + "->" + methodName);
    }

    /**
     * Checks whether a specific MethodReference is invoked.
     *
     * @param methodRef the MethodReference to check.
     * @return true if invoked.
     */
    public boolean isMethodInvoked(MethodReference methodRef) {
        if (methodRef == null) return false;
        String desc = ReferenceUtil.getMethodDescriptor(methodRef);
        return invokedMethodDescriptors.contains(desc);
    }

    /**
     * Checks whether a method descriptor or key is invoked.
     *
     * @param methodDescriptor method descriptor or signature key.
     * @return true if invoked.
     */
    public boolean isMethodInvoked(String methodDescriptor) {
        if (methodDescriptor == null || methodDescriptor.isEmpty()) return false;
        if (invokedMethodDescriptors.contains(methodDescriptor)) return true;
        if (invokedMethodsWithReturn.contains(methodDescriptor)) return true;
        return invokedMethodsByName.contains(methodDescriptor);
    }

    /**
     * Checks whether a field on the given class is referenced (read or written).
     *
     * @param declaringClass the class declaring the field.
     * @param fieldName the field name.
     * @return true if referenced.
     */
    public boolean isFieldReferenced(String declaringClass, String fieldName) {
        if (declaringClass == null || fieldName == null) return false;
        String normClass = normalizeClassType(declaringClass);
        return referencedFieldsByName.contains(normClass + "->" + fieldName);
    }

    /**
     * Checks whether a typed field on the given class is referenced.
     *
     * @param declaringClass the class declaring the field.
     * @param fieldName the field name.
     * @param fieldType the field type descriptor or Java type keyword.
     * @return true if referenced.
     */
    public boolean isFieldReferenced(String declaringClass, String fieldName, String fieldType) {
        if (declaringClass == null || fieldName == null) return false;
        String normClass = normalizeClassType(declaringClass);
        if (fieldType == null) {
            return isFieldReferenced(normClass, fieldName);
        }
        String normType = normalizeReturnType(fieldType);
        return referencedFieldDescriptors.contains(normClass + "->" + fieldName + ":" + normType);
    }

    /**
     * Checks whether a FieldReference is referenced.
     *
     * @param fieldRef the FieldReference to check.
     * @return true if referenced.
     */
    public boolean isFieldReferenced(FieldReference fieldRef) {
        if (fieldRef == null) return false;
        String desc = ReferenceUtil.getFieldDescriptor(fieldRef);
        if (referencedFieldDescriptors.contains(desc)) return true;
        return isFieldReferenced(fieldRef.getDefiningClass(), fieldRef.getName());
    }

    /**
     * Checks whether a class is referenced anywhere in bytecode, inheritance, or type references.
     *
     * @param declaringClass the class name or descriptor.
     * @return true if referenced.
     */
    public boolean isClassReferenced(String declaringClass) {
        if (declaringClass == null || declaringClass.isEmpty()) return false;
        String normClass = normalizeClassType(declaringClass);
        return referencedClasses.contains(normClass);
    }

    /**
     * Returns total unique indexed references (methods, fields, and classes).
     */
    public int getTotalIndexedReferences() {
        return invokedMethodDescriptors.size() + referencedFieldDescriptors.size() + referencedClasses.size();
    }

    /**
     * Returns the number of distinct method invocations indexed.
     */
    public int getTotalInvokedMethods() {
        return invokedMethodDescriptors.size();
    }

    /**
     * Returns the number of distinct field references indexed.
     */
    public int getTotalReferencedFields() {
        return referencedFieldDescriptors.size();
    }

    /**
     * Returns the number of distinct class references indexed.
     */
    public int getTotalReferencedClasses() {
        return referencedClasses.size();
    }

    /**
     * Returns the total count of instructions scanned across all DEX files.
     */
    public int getTotalInstructionsScanned() {
        return totalInstructionsScanned;
    }
}

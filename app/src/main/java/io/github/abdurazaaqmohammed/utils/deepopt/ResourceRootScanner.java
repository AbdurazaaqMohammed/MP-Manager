package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;

import java.util.Set;

/**
 * Extracts resource roots from the final kept code: {@code R$<type>} field references
 * (sget/iget/sput/iput) and inlined {@code const} resource IDs (0x7f…) that resolve to a
 * real table entry. Returns whether any kept code resolves resources by NAME at runtime
 * (getIdentifier family), which - in safe mode - enables the name-match keep instead of
 * the old keep-everything behavior.
 */
public final class ResourceRootScanner {

    private ResourceRootScanner() {
    }

    public static boolean scan(DexIndex index, Set<String> keptClasses, Set<String> keptMethodKeys,
                               Set<Integer> resourceRoots, Set<String> nameRefKeys,
                               ResourceTableIndex tableIndex) {
        boolean dynamicLookup = false;
        for (String type : keptClasses) {
            ClassDef classDef = index.classByType.get(type);
            if (classDef == null) continue;
            for (Method method : classDef.getMethods()) {
                if (!keptMethodKeys.contains(DexIndex.methodKey(method))) continue;
                MethodImplementation impl = method.getImplementation();
                if (impl == null) continue;
                for (Instruction instruction : impl.getInstructions()) {
                    if (instruction instanceof ReferenceInstruction) {
                        Reference ref = ((ReferenceInstruction) instruction).getReference();
                        if (ref instanceof FieldReference) {
                            String resourceType = resourceType(((FieldReference) ref).getDefiningClass());
                            if (resourceType != null)
                                nameRefKeys.add(resourceType + ":" + ((FieldReference) ref).getName());
                        } else if (ref instanceof MethodReference methodRef) {
                            if ("Landroid/content/res/Resources;".equals(methodRef.getDefiningClass())
                                    && DexIndex.DYNAMIC_RESOURCE_METHODS.contains(methodRef.getName()))
                                dynamicLookup = true;
                        }
                    } else if (instruction instanceof NarrowLiteralInstruction) {
                        int literal = ((NarrowLiteralInstruction) instruction).getNarrowLiteral();
                        if ((literal & 0xFF000000) == 0x7F000000 && tableIndex.byId(literal) != null)
                            resourceRoots.add(literal);
                    }
                }
            }
        }
        return dynamicLookup;
    }

    /** "Lcom/foo/R$string;" -> "string"; null for anything else. */
    private static String resourceType(String definingClass) {
        if (definingClass == null) return null;
        int idx = definingClass.lastIndexOf("R$");
        if (idx < 0) return null;
        String rest = definingClass.substring(idx + 2);
        if (!rest.endsWith(";")) return null;
        String typeName = rest.substring(0, rest.length() - 1);
        if (typeName.isEmpty() || typeName.length() > 12) return null;
        for (int i = 0; i < typeName.length(); i++) {
            char c = typeName.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return null;
        }
        return typeName;
    }
}

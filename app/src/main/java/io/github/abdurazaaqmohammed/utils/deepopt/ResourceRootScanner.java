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
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.model.ResourceEntry;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Extracts resource roots from the final kept code: {@code R$<type>} field references
 * (sget/iget/sput/iput), inlined {@code const} resource IDs (0x7f…) that resolve to a
 * real table entry, and {@code Resources->getIdentifier/getString} call sites which force
 * the all-keep mode.
 */
public final class ResourceRootScanner {

    private ResourceRootScanner() {
    }

    public static boolean scan(DexIndex index, Set<String> keptClasses, Set<String> keptMethodKeys,
                               Set<Integer> resourceRoots, Set<String> nameRefKeys, TableBlock table) {
        if (table == null) return false;
        Map<Integer, ResourceEntry> byId = new HashMap<>();
        for (PackageBlock pkg : table.listPackages()) {
            for (java.util.Iterator<ResourceEntry> it = pkg.getResources(); it.hasNext(); ) {
                ResourceEntry entry = it.next();
                byId.put(entry.getResourceId(), entry);
            }
        }
        boolean allKeep = false;
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
                        } else if (ref instanceof MethodReference) {
                            MethodReference methodRef = (MethodReference) ref;
                            if ("Landroid/content/res/Resources;".equals(methodRef.getDefiningClass())
                                    && DexIndex.RESOURCES_METHOD_NAMES.contains(methodRef.getName()))
                                allKeep = true;
                        }
                    } else if (instruction instanceof NarrowLiteralInstruction) {
                        int literal = ((NarrowLiteralInstruction) instruction).getNarrowLiteral();
                        if ((literal & 0xFF000000) == 0x7F000000 && byId.containsKey(literal))
                            resourceRoots.add(literal);
                    }
                }
            }
        }
        return allKeep;
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
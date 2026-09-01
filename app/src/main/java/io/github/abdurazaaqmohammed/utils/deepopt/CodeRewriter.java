package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * In-place, size-preserving nopping of calls to removed empty methods. Every removed
 * instruction is replaced by exactly {@code getCodeUnits()} NOPs so total code-unit
 * counts, branch offsets, try ranges and debug addresses all stay valid. A following
 * {@code move-result*} is nopped together with the call.
 */
public final class CodeRewriter {

    private CodeRewriter() {
    }

    public static Method nopCallsToRemoved(DexIndex index, Method method, Set<String> removedKeys, boolean preserveDebug) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return method;
        List<Instruction> list = new ArrayList<>();
        for (Instruction ins : impl.getInstructions()) list.add(ins);
        boolean[] removed = new boolean[list.size()];
        boolean modified = false;
        for (int i = 0; i < list.size(); i++) {
            Instruction ins = list.get(i);
            if (!ins.getOpcode().name.startsWith("invoke-")) continue;
            if (!(ins instanceof ReferenceInstruction)) continue;
            if (!(((ReferenceInstruction) ins).getReference() instanceof MethodReference)) continue;
            MethodReference ref = (MethodReference) ((ReferenceInstruction) ins).getReference();
            String resolvedKey = index.resolveMethodKey(ref);
            if (resolvedKey == null || !removedKeys.contains(resolvedKey)) continue;
            removed[i] = true;
            modified = true;
            int next = i + 1;
            if (next < list.size() && list.get(next).getOpcode().name.startsWith("move-result"))
                removed[next] = true;
        }
        if (!modified) return method;
        List<ImmutableInstruction> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (removed[i]) {
                for (int n = 0; n < list.get(i).getCodeUnits(); n++)
                    out.add(new ImmutableInstruction10x(Opcode.NOP));
            } else {
                out.add(ImmutableInstruction.of(list.get(i)));
            }
        }
        ImmutableMethod base = ImmutableMethod.of(method);
        return new ImmutableMethod(base.getDefiningClass(), base.getName(), base.getParameters(),
                method.getReturnType(), method.getAccessFlags(), method.getAnnotations(),
                method.getHiddenApiRestrictions(),
                new ImmutableMethodImplementation(impl.getRegisterCount(), out,
                        impl.getTryBlocks(), preserveDebug ? impl.getDebugItems() : null));
    }
}
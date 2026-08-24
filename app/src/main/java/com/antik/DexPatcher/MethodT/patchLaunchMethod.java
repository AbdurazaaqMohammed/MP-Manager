package com.antik.DexPatcher.MethodT;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;

import java.util.ArrayList;
import java.util.List;

public class patchLaunchMethod {
    public static Method patchLaunchMethod(Method m) {
        MethodImplementation im = m.getImplementation();
        if (im == null) return m;
        List<Instruction> o_ins = new ArrayList<Instruction>();
        for (Instruction i : im.getInstructions()) {
        o_ins.add(i);
        }
        List<Instruction> n_ins = new ArrayList<Instruction>();
        int p_off = -1;
        int c_off = 0;

        for (int i = 0; i < o_ins.size(); i++) {
            Instruction inst = o_ins.get(i);
            n_ins.add(inst);
            int u = inst.getCodeUnits();
            if (inst instanceof ReferenceInstruction) {
                ReferenceInstruction r_i = (ReferenceInstruction) inst;
                if (r_i.getReference() instanceof MethodReference) {
                    MethodReference m_r = (MethodReference) r_i.getReference();
                    if ("Lcom/pairip/VMRunner;".equals(m_r.getDefiningClass()) && "invoke".equals(m_r.getName())) {

                        boolean a_p = false;
                        if (i + 1 < o_ins.size()) {
                            Instruction nx = o_ins.get(i + 1);
                            if (nx instanceof ReferenceInstruction) {
                                ReferenceInstruction n_r = (ReferenceInstruction) nx;
                                if (n_r.getReference() instanceof MethodReference) {
                                    MethodReference n_m = (MethodReference) n_r.getReference();
                                    if ("pairip".equals(n_m.getName())) {
                                        a_p = true;
                                    }
                                }
                            }
                        }
                        if (!a_p) {
                            p_off = c_off + u;
                            n_ins.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, new ImmutableMethodReference("Lcom/pairip/StartupLauncher;", "pairip", null, "V")));
                        }
                    }
                }
            }
            c_off += u;
        }

        if (p_off == -1) return m;

        List<? extends com.android.tools.smali.dexlib2.iface.TryBlock<? extends com.android.tools.smali.dexlib2.iface.ExceptionHandler>> o_tbs = im.getTryBlocks();
        List<com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock> n_tbs = new ArrayList<com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock>();

        for (com.android.tools.smali.dexlib2.iface.TryBlock<? extends com.android.tools.smali.dexlib2.iface.ExceptionHandler> t : o_tbs) {
            int s = t.getStartCodeAddress();
            int c = t.getCodeUnitCount();
            int e = s + c;
            List<com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler> h = new ArrayList<com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler>();
            for (com.android.tools.smali.dexlib2.iface.ExceptionHandler ha : t.getExceptionHandlers()) {
                int h_a = ha.getHandlerCodeAddress();
                if (h_a >= p_off) {
                    h.add(new com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler(ha.getExceptionType(), h_a + 3));
                } else {
                    h.add(com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler.of(ha));
                }
            }
            if (s < p_off && e >= p_off) {
                n_tbs.add(new com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock(s, c + 3, h));
            } else if (s >= p_off) {
                n_tbs.add(new com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock(s + 3, c, h));
            } else {
                n_tbs.add(new com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock(s, c, h));
            }
        }

        return new ImmutableMethod(m.getDefiningClass(), m.getName(), m.getParameters(), m.getReturnType(), m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), new ImmutableMethodImplementation(im.getRegisterCount(), n_ins, n_tbs, im.getDebugItems()));
    }
}

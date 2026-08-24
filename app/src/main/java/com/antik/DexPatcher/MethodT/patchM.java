package com.antik.DexPatcher.MethodT;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class patchM {
    public static Method patchMethodIfTarget(Method m) {
        String n = m.getName();
        if ("verifySignatureMatches".equals(n)) {
            System.out.println("[INFO] Bypassing verifySignatureMatches");
            List<Instruction> ins = Arrays.asList(new ImmutableInstruction11n(Opcode.CONST_4, 0, 1), new ImmutableInstruction11x(Opcode.RETURN, 0));
            return new ImmutableMethod(m.getDefiningClass(), n, m.getParameters(), m.getReturnType(), m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), new ImmutableMethodImplementation(1, ins, null, null));
        } else if ("verifyIntegrity".equals(n)) {
            System.out.println("[INFO] Bypassing " + n);
            String rt = m.getReturnType();
            List<Instruction> ins;
            if ("V".equals(rt)) {
                ins = Collections.singletonList(new ImmutableInstruction10x(Opcode.RETURN_VOID));
            } else if (rt.startsWith("L") || rt.startsWith("[")) {
                ins = Arrays.asList(new ImmutableInstruction11n(Opcode.CONST_4, 0, 0), new ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0));
            } else {
                ins = Arrays.asList(new ImmutableInstruction11n(Opcode.CONST_4, 0, 1), new ImmutableInstruction11x(Opcode.RETURN, 0));
            }
            return new ImmutableMethod(m.getDefiningClass(), n, m.getParameters(), rt, m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), new ImmutableMethodImplementation(1, ins, null, null));
        } else if (!"<init>".equals(n) && m.getImplementation() != null && "V".equals(m.getReturnType())) {
            System.out.println("[INFO] Bypassing void method " + n);
            List<Instruction> ins = Collections.singletonList(new ImmutableInstruction10x(Opcode.RETURN_VOID));
            return new ImmutableMethod(m.getDefiningClass(), n, m.getParameters(), m.getReturnType(), m.getAccessFlags(), m.getAnnotations(), m.getHiddenApiRestrictions(), new ImmutableMethodImplementation(m.getParameterTypes().size()+1, ins, null, null));
        }
        return m;
    }
}
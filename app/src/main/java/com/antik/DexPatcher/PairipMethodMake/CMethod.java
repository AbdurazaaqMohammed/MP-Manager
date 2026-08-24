package com.antik.DexPatcher.PairipMethodMake;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CMethod {
    public static Method createPairipMethod(String dc, List<String> j_ts) {
        List<Instruction> ins = new ArrayList<Instruction>();
        for (String t : j_ts) {
            ins.add(new ImmutableInstruction21c(Opcode.CONST_CLASS, 0, new ImmutableTypeReference(t)));
            ins.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, new ImmutableMethodReference("Lcom/pairip/PairipLog;", "put", Collections.singletonList("Ljava/lang/Class;"), "V")));
        }
        ins.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));
        return new ImmutableMethod(dc, "pairip", null, "V", 0x08, null, null, new ImmutableMethodImplementation(1, ins, null, null));
    }
}

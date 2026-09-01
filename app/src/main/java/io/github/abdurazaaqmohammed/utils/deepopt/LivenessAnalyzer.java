package io.github.abdurazaaqmohammed.utils.deepopt;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.PayloadInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchElement;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Safe dead-code elimination built on a basic-block CFG and a real backward liveness
 * dataflow. Only removes pure, non-throwing register arithmetic ({@code const},
 * {@code move}, {@code add-*}, conversions, ...) whose result register is dead at every
 * successor. Never removes invokes, field stores, volatile accessors, {@code move-exception},
 * {@code move-result}, monitors, returns, throws, branches or payloads. Methods containing
 * try/catch are skipped entirely (conservative), and any CFG build failure degrades to a
 * no-op returning the original method.
 */
public final class LivenessAnalyzer {

    private LivenessAnalyzer() {
    }

    public static Method eliminateDead(Method method, boolean preserveDebug) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) return method;
        List<Instruction> list = new ArrayList<>();
        for (Instruction ins : impl.getInstructions()) list.add(ins);
        if (list.isEmpty()) return method;
        if (!impl.getTryBlocks().isEmpty()) return method;

        int[] removed;
        try {
            removed = computeDead(list);
        } catch (Exception e) {
            return method;
        }
        if (removed == null) return method;
        boolean any = false;
        for (int r : removed) any |= r != 0;
        if (!any) return method;

        List<ImmutableInstruction> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (removed[i] != 0) {
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

    private static int[] computeDead(List<Instruction> list) {
        int n = list.size();
        int[] offsets = new int[n];
        int cu = 0;
        for (int i = 0; i < n; i++) {
            offsets[i] = cu;
            cu += list.get(i).getCodeUnits();
        }
        Map<Integer, Integer> offsetToIndex = new HashMap<>();
        for (int i = 0; i < n; i++) offsetToIndex.put(offsets[i], i);

        boolean[] isBlockStart = new boolean[n];
        isBlockStart[0] = true;
        for (int i = 0; i < n; i++) {
            Instruction ins = list.get(i);
            Opcode op = ins.getOpcode();
            String name = op.name;
            if (ins instanceof PayloadInstruction) {
                isBlockStart[i] = true;
                continue;
            }
            if (name.startsWith("goto") || name.startsWith("if-")) {
                Integer ti = offsetToIndex.get(offsets[i] + ((OffsetInstruction) ins).getCodeOffset());
                if (ti == null) return null;
                isBlockStart[ti] = true;
                if (i + 1 < n) isBlockStart[i + 1] = true;
            } else if (name.equals("packed-switch") || name.equals("sparse-switch")) {
                Integer ti = offsetToIndex.get(offsets[i] + ((OffsetInstruction) ins).getCodeOffset());
                if (ti == null) return null;
                isBlockStart[ti] = true;
                int j = i + 1;
                while (j < n && list.get(j) instanceof PayloadInstruction) j++;
                if (j < n) isBlockStart[j] = true;
            } else if (name.startsWith("return") || name.equals("throw")) {
                if (i + 1 < n) isBlockStart[i + 1] = true;
            } else if (ins instanceof OffsetInstruction) {
                Integer ti = offsetToIndex.get(offsets[i] + ((OffsetInstruction) ins).getCodeOffset());
                if (ti != null) isBlockStart[ti] = true;
            }
        }

        List<List<Integer>> blocks = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (isBlockStart[i] && !current.isEmpty()) {
                blocks.add(current);
                current = new ArrayList<>();
            }
            current.add(i);
        }
        if (!current.isEmpty()) blocks.add(current);
        int[] blockOf = new int[n];
        for (int b = 0; b < blocks.size(); b++)
            for (int i : blocks.get(b)) blockOf[i] = b;

        List<List<Integer>> succ = buildSuccessors(list, offsets, offsetToIndex, blockOf, blocks);
        if (succ == null) return null;

        boolean[] reachable = new boolean[blocks.size()];
        Deque<Integer> stack = new ArrayDeque<>();
        reachable[0] = true;
        stack.push(0);
        while (!stack.isEmpty()) {
            int b = stack.pop();
            for (int s : succ.get(b)) if (!reachable[s]) {
                reachable[s] = true;
                stack.push(s);
            }
        }

        Map<Integer, Set<Integer>> liveIn = new HashMap<>();
        Map<Integer, Set<Integer>> liveOut = new HashMap<>();
        for (int b = 0; b < blocks.size(); b++) {
            liveIn.put(b, new HashSet<>());
            liveOut.put(b, new HashSet<>());
        }
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 1000) {
            changed = false;
            for (int b = blocks.size() - 1; b >= 0; b--) {
                Set<Integer> out = new HashSet<>();
                for (int s : succ.get(b)) out.addAll(liveIn.get(s));
                if (!out.equals(liveOut.get(b))) {
                    liveOut.put(b, out);
                    changed = true;
                }
                Set<Integer> in = new HashSet<>(out);
                List<Integer> bList = blocks.get(b);
                for (int k = bList.size() - 1; k >= 0; k--)
                    transfer(list.get(bList.get(k)), in);
                if (!in.equals(liveIn.get(b))) {
                    liveIn.put(b, in);
                    changed = true;
                }
            }
        }

        int[] removed = new int[n];
        for (int b = 0; b < blocks.size(); b++) {
            if (!reachable[b]) continue;
            Set<Integer> live = new HashSet<>(liveOut.get(b));
            List<Integer> bList = blocks.get(b);
            for (int k = bList.size() - 1; k >= 0; k--) {
                int idx = bList.get(k);
                Instruction ins = list.get(idx);
                if (isRemovable(ins)) {
                    int dest = destReg(ins);
                    boolean wide = ins.getOpcode().setsWideRegister();
                    if (dest >= 0 && !live.contains(dest) && (!wide || !live.contains(dest + 1))) {
                        removed[idx] = 1;
                        continue;
                    }
                }
                transfer(ins, live);
            }
        }
        return removed;
    }

    private static List<List<Integer>> buildSuccessors(List<Instruction> list, int[] offsets,
                                                       Map<Integer, Integer> offsetToIndex,
                                                       int[] blockOf, List<List<Integer>> blocks) {
        int n = list.size();
        List<List<Integer>> succ = new ArrayList<>();
        for (int b = 0; b < blocks.size(); b++) succ.add(new ArrayList<>());
        for (int b = 0; b < blocks.size(); b++) {
            List<Integer> bList = blocks.get(b);
            List<Integer> s = succ.get(b);
            int last = bList.get(bList.size() - 1);
            Instruction ins = list.get(last);
            String name = ins.getOpcode().name;
            if (name.startsWith("goto")) {
                Integer ti = offsetToIndex.get(offsets[last] + ((OffsetInstruction) ins).getCodeOffset());
                if (ti == null) return null;
                addSucc(s, blockOf[ti]);
            } else if (name.startsWith("if-")) {
                Integer ti = offsetToIndex.get(offsets[last] + ((OffsetInstruction) ins).getCodeOffset());
                if (ti == null) return null;
                addSucc(s, blockOf[ti]);
                if (last + 1 < n) addSucc(s, blockOf[last + 1]);
            } else if (name.equals("packed-switch") || name.equals("sparse-switch")) {
                Integer ti = offsetToIndex.get(offsets[last] + ((OffsetInstruction) ins).getCodeOffset());
                if (ti == null) return null;
                Instruction payload = list.get(ti);
                if (!(payload instanceof SwitchPayload)) return null;
                for (SwitchElement element : ((SwitchPayload) payload).getSwitchElements()) {
                    Integer tti = offsetToIndex.get(offsets[last] + element.getOffset());
                    if (tti == null) return null;
                    addSucc(s, blockOf[tti]);
                }
                int j = last + 1;
                while (j < n && list.get(j) instanceof PayloadInstruction) j++;
                if (j < n) addSucc(s, blockOf[j]);
            } else if (name.startsWith("return") || name.equals("throw")) {
                // no successors
            } else if (last + 1 < n) {
                addSucc(s, blockOf[last + 1]);
            }
        }
        return succ;
    }

    private static void addSucc(List<Integer> s, int b) {
        if (!s.contains(b)) s.add(b);
    }

    private static void transfer(Instruction ins, Set<Integer> live) {
        int dest = destReg(ins);
        boolean wide = ins.getOpcode().setsWideRegister();
        if (dest >= 0) {
            live.remove(dest);
            if (wide) live.remove(dest + 1);
        }
        live.addAll(readRegs(ins));
    }

    private static boolean isRemovable(Instruction ins) {
        Opcode op = ins.getOpcode();
        String name = op.name;
        if (op.canThrow()) return false;
        if (!op.setsRegister()) return false;
        if (op.isVolatileFieldAccessor() || op.isStaticFieldAccessor()) return false;
        if (name.equals("move-result") || name.equals("move-result-wide") || name.equals("move-result-object")
                || name.equals("move-exception")) return false;
        return name.startsWith("const")
                || name.startsWith("move")
                || name.startsWith("add-") || name.startsWith("sub-") || name.startsWith("mul-")
                || name.startsWith("and-") || name.startsWith("or-") || name.startsWith("xor-")
                || name.startsWith("shl-") || name.startsWith("shr-") || name.startsWith("ushr-")
                || name.startsWith("neg-") || name.startsWith("not-") || name.startsWith("rsub-")
                || name.startsWith("int-to-") || name.startsWith("long-to-") || name.startsWith("float-to-")
                || name.startsWith("double-to-") || name.startsWith("cmpl-") || name.startsWith("cmpg-")
                || name.equals("cmp-long");
    }

    private static int destReg(Instruction ins) {
        if (!ins.getOpcode().setsRegister()) return -1;
        if (ins instanceof OneRegisterInstruction) return ((OneRegisterInstruction) ins).getRegisterA();
        if (ins instanceof TwoRegisterInstruction) return ((TwoRegisterInstruction) ins).getRegisterA();
        if (ins instanceof ThreeRegisterInstruction) return ((ThreeRegisterInstruction) ins).getRegisterA();
        return -1;
    }

    private static Set<Integer> readRegs(Instruction ins) {
        Set<Integer> out = new HashSet<>();
        String op = ins.getOpcode().name;
        if (ins instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction f = (FiveRegisterInstruction) ins;
            out.add(f.getRegisterC());
            out.add(f.getRegisterD());
            out.add(f.getRegisterE());
            out.add(f.getRegisterF());
            out.add(f.getRegisterG());
        } else if (ins instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction r = (RegisterRangeInstruction) ins;
            for (int i = 0; i < r.getRegisterCount(); i++) out.add(r.getStartRegister() + i);
        } else if (ins instanceof ThreeRegisterInstruction) {
            ThreeRegisterInstruction t = (ThreeRegisterInstruction) ins;
            if (op.startsWith("aput")) out.add(t.getRegisterA());
            out.add(t.getRegisterB());
            out.add(t.getRegisterC());
        } else if (ins instanceof TwoRegisterInstruction) {
            TwoRegisterInstruction t = (TwoRegisterInstruction) ins;
            if (op.startsWith("if-")) out.add(t.getRegisterA());
            out.add(t.getRegisterB());
        } else if (ins instanceof OneRegisterInstruction) {
            OneRegisterInstruction o = (OneRegisterInstruction) ins;
            if (op.startsWith("if-") || op.equals("return") || op.equals("return-wide") || op.equals("return-object")
                    || op.equals("throw") || op.equals("monitor-enter") || op.equals("monitor-exit")
                    || op.equals("packed-switch") || op.equals("sparse-switch") || op.equals("fill-array-data")
                    || op.startsWith("sput") || op.equals("check-cast")) {
                out.add(o.getRegisterA());
            }
        }
        if (ins.getOpcode().setsWideRegister()) {
            Set<Integer> copy = new HashSet<>(out);
            for (int r : copy) out.add(r + 1);
        }
        return out;
    }
}
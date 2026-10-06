package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Restores strings that were encrypted by common string-obfuscation tools.
 *
 * <p>Candidates are {@code invoke-static} calls that return {@code String}, are followed by
 * {@code move-result-object} and take plain const arguments on a straight-line code path. The
 * decryption method is executed by a small smali interpreter (nothing from the target APK is
 * ever loaded into this process) and the call site is rewritten into a single {@code const-string}
 * holding the plaintext.</p>
 */
public final class DexStringDecryptor {

    public interface Listener {
        void onMessage(String message);
    }

    public static final class Options {
        public final String customSignature;
        public final boolean advanced;
        public final Listener listener;

        public Options(String customSignature, boolean advanced, Listener listener) {
            this.customSignature = customSignature == null ? "" : customSignature.trim();
            this.advanced = advanced;
            this.listener = listener;
        }
    }

    public static final class Outcome {
        /** Rewritten dex bytes, or null when nothing was decrypted. */
        public final byte[] dexBytes;
        public final int decrypted;
        public final int skipped;

        Outcome(byte[] dexBytes, int decrypted, int skipped) {
            this.dexBytes = dexBytes;
            this.decrypted = decrypted;
            this.skipped = skipped;
        }
    }

    private DexStringDecryptor() {
    }

    /**
     * @param dex           the dex entry to rewrite
     * @param globalClasses all classes of the whole APK indexed by descriptor, so decryption
     *                      methods living in another dex entry can still be resolved
     * @param api           target API level, decides the output dex version
     */
    public static Outcome decryptDex(DexBackedDexFile dex, Map<String, ClassDef> globalClasses,
                                     int api, Options opts) throws IOException {
        return new Engine(dex, globalClasses, api, opts).run();
    }

    // ------------------------------------------------------------------------ call planning

    private static final class Plan {
        int segStartIdx;
        int defIdx;
        int invokeIdx;
        int mrIdx;
        int resultReg;
        MethodReference ref;
        Object[] argVals;
        String cipher;
        String plain;
    }

    private static final Object NOT_CONST = new Object();

    static boolean plausible(String s, String cipher) {
        if (s == null || s.isEmpty() || s.equals(cipher)) return false;
        if (s.length() > 500_000) return false;
        int bad = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t' && c != '\r') bad++;
        }
        return bad * 100 <= s.length() * 15;
    }

    // ------------------------------------------------------------------------------ engine

    private static final class Engine {
        private final DexBackedDexFile dex;
        private final Map<String, ClassDef> global;
        private final int api;
        private final Options opts;

        private final Session session = new Session();
        private int decrypted;
        private int skipped;
        private int logged;

        Engine(DexBackedDexFile dex, Map<String, ClassDef> global, int api, Options opts) {
            this.dex = dex;
            this.global = global;
            this.api = api;
            this.opts = opts;
        }

        Outcome run() throws IOException {
            Map<ClassDef, Map<String, MutableMethodImplementation>> mutatedByClass =
                    new LinkedHashMap<>();
            for (ClassDef cls : dex.getClasses()) {
                Map<String, MutableMethodImplementation> mutated = processClass(cls);
                if (!mutated.isEmpty()) mutatedByClass.put(cls, mutated);
            }
            if (mutatedByClass.isEmpty()) {
                return new Outcome(null, decrypted, skipped);
            }
            List<ClassDef> outClasses = new ArrayList<>();
            for (ClassDef cls : dex.getClasses()) {
                Map<String, MutableMethodImplementation> mutated = mutatedByClass.get(cls);
                outClasses.add(mutated == null ? cls : rebuildClass(cls, mutated));
            }
            DexPool pool = new DexPool(Opcodes.forApi(api));
            for (ClassDef cls : outClasses) pool.internClass(cls);
            MemoryDataStore store = new MemoryDataStore();
            pool.writeTo(store);
            byte[] bytes = Arrays.copyOf(store.getData(), store.getSize());
            return new Outcome(bytes, decrypted, skipped);
        }

        private static ClassDef rebuildClass(ClassDef cls,
                                             Map<String, MutableMethodImplementation> mutated) {
            List<Method> methods = new ArrayList<>();
            for (Method m : cls.getMethods()) {
                MutableMethodImplementation mut = mutated.get(methodKey(m));
                if (mut != null) {
                    methods.add(new ImmutableMethod(m.getDefiningClass(), m.getName(),
                            m.getParameters(), m.getReturnType(), m.getAccessFlags(),
                            m.getAnnotations(), m.getHiddenApiRestrictions(), mut));
                } else {
                    methods.add(m);
                }
            }
            List<Field> fields = new ArrayList<>();
            for (Field f : cls.getStaticFields()) fields.add(f);
            for (Field f : cls.getInstanceFields()) fields.add(f);
            List<String> interfaces = new ArrayList<>();
            for (CharSequence cs : cls.getInterfaces()) interfaces.add(cs.toString());
            return new ImmutableClassDef(cls.getType(), cls.getAccessFlags(), cls.getSuperclass(),
                    interfaces, cls.getSourceFile() == null ? null : cls.getSourceFile().toString(),
                    cls.getAnnotations(), fields, methods);
        }

        private static String methodKey(Method m) {
            StringBuilder sb = new StringBuilder(m.getName()).append('(');
            for (MethodParameter p : m.getParameters()) sb.append(p.getType());
            return sb.append(')').append(m.getReturnType()).toString();
        }

        private Map<String, MutableMethodImplementation> processClass(ClassDef cls) {
            Map<String, MutableMethodImplementation> mutated = new HashMap<>();
            for (Method m : cls.getMethods()) {
                MethodImplementation impl = m.getImplementation();
                if (impl == null) continue;
                List<Instruction> insns = new ArrayList<>();
                for (Instruction in : impl.getInstructions()) insns.add(in);
                if (insns.isEmpty()) continue;
                List<Plan> plans = scanMethod(insns, impl, m);
                if (plans.isEmpty()) continue;
                if (applyPlans(m, impl, plans)) {
                    MutableMethodImplementation mut = new MutableMethodImplementation(impl);
                    for (Plan p : plans) {
                        if (p.plain == null) continue;
                        mut.removeInstruction(p.mrIdx);
                        mut.removeInstruction(p.invokeIdx);
                        mut.replaceInstruction(p.defIdx, new BuilderInstruction21c(
                                Opcode.CONST_STRING, p.resultReg,
                                new ImmutableStringReference(p.plain)));
                        p.plain = null;
                        decrypted++;
                    }
                    mutated.put(methodKey(m), mut);
                }
            }
            return mutated;
        }

        private List<Plan> scanMethod(List<Instruction> insns, MethodImplementation impl,
                                      Method owner) {
            int n = insns.size();
            int[] addr = new int[n];
            int a = 0;
            for (int i = 0; i < n; i++) {
                addr[i] = a;
                a += insns.get(i).getCodeUnits();
            }
            Map<Integer, Integer> idxByAddr = new HashMap<>();
            for (int i = 0; i < n; i++) idxByAddr.put(addr[i], i);
            Set<Integer> branchTargets = new HashSet<>();
            for (int i = 0; i < n; i++) {
                Instruction in = insns.get(i);
                if (isJumpLike(in.getOpcode())) {
                    int off = ((OffsetInstruction) in).getCodeOffset();
                    branchTargets.add(addr[i] + off);
                }
            }
            List<int[]> tryRanges = new ArrayList<>();
            for (TryBlock<?> tb : impl.getTryBlocks()) {
                tryRanges.add(new int[]{tb.getStartCodeAddress(),
                        tb.getStartCodeAddress() + tb.getCodeUnitCount()});
            }

            List<Plan> plans = new ArrayList<>();
            for (int i = 0; i + 1 < n; i++) {
                Instruction in = insns.get(i);
                Opcode op = in.getOpcode();
                if (op != Opcode.INVOKE_STATIC && op != Opcode.INVOKE_STATIC_RANGE) continue;
                if (insns.get(i + 1).getOpcode() != Opcode.MOVE_RESULT_OBJECT) continue;
                if (!(in instanceof ReferenceInstruction ri)) continue;
                if (!(ri.getReference() instanceof MethodReference ref)) continue;
                if (!"Ljava/lang/String;".equals(ref.getReturnType())) continue;
                if (!hasStringParam(ref)) continue;
                if (!matchesSignature(ref)) continue;
                Plan plan = planCall(insns, addr, branchTargets, tryRanges, i, ref);
                if (plan == null) {
                    skipped++;
                    log("skip " + ref.getDefiningClass() + "->" + ref.getName() + " in "
                            + owner.getDefiningClass() + "->" + owner.getName()
                            + ": call shape not restorable");
                    continue;
                }
                if (!runDecrypt(plan)) continue;
                plans.add(plan);
            }
            return plans;
        }

        private boolean runDecrypt(Plan plan) {
            Method target = resolve(plan.ref);
            if (target == null) {
                skipped++;
                log("skip " + plan.ref.getDefiningClass() + "->" + plan.ref.getName()
                        + ": method body not found");
                return false;
            }
            Object plain;
            try {
                plain = session.execute(target, plan.argVals, global, 0);
            } catch (Throwable t) {
                skipped++;
                log("skip " + plan.ref.getDefiningClass() + "->" + plan.ref.getName()
                        + ": execution failed (" + t + ")");
                return false;
            }
            if (!(plain instanceof String s) || !plausible(s, plan.cipher)) {
                skipped++;
                log("skip " + plan.ref.getDefiningClass() + "->" + plan.ref.getName()
                        + ": no usable plaintext");
                return false;
            }
            plan.plain = s;
            return true;
        }

        /** Marks every successfully decrypted plan as applied (plain != null); overlap losers are dropped. */
        private boolean applyPlans(Method owner, MethodImplementation impl, List<Plan> plans) {
            List<int[]> claimed = new ArrayList<>();
            int applied = 0;
            List<Plan> ordered = new ArrayList<>(plans);
            ordered.sort((p, q) -> Integer.compare(q.invokeIdx, p.invokeIdx));
            for (Plan p : ordered) {
                boolean overlap = false;
                for (int[] c : claimed) {
                    if (p.segStartIdx >= c[0] && p.segStartIdx <= c[1]) {
                        overlap = true;
                        break;
                    }
                }
                if (overlap) {
                    skipped++;
                    p.plain = null;
                    log("skip rewrite at " + owner.getDefiningClass() + "->" + owner.getName()
                            + ": overlapping call sites");
                    continue;
                }
                claimed.add(new int[]{p.segStartIdx, p.mrIdx});
                applied++;
            }
            return applied > 0;
        }

        private Plan planCall(List<Instruction> insns, int[] addr, Set<Integer> branchTargets,
                              List<int[]> tryRanges, int invokeIdx, MethodReference ref) {
            int n = insns.size();
            int mrIdx = invokeIdx + 1;
            int[] regs = invokeRegisters(insns.get(invokeIdx));
            if (regs == null || regs.length == 0) return null;
            if (!(insns.get(mrIdx) instanceof OneRegisterInstruction mri)) return null;
            int resultReg = mri.getRegisterA();
            boolean resultIsArg = false;
            for (int r : regs) {
                if (r == resultReg) {
                    resultIsArg = true;
                    break;
                }
            }
            if (!resultIsArg) return null;

            // the overwritten definition must itself be the cipher const-string
            int resultDefIdx = findNearestDef(insns, resultReg, invokeIdx);
            if (resultDefIdx < 0) return null;
            Instruction rdef = insns.get(resultDefIdx);
            if (!(rdef instanceof ReferenceInstruction rri)
                    || !(rri.getReference() instanceof StringReference cipherRef)) {
                return null;
            }

            List<? extends CharSequence> paramTypes = ref.getParameterTypes();
            if (paramTypes.size() != regs.length) return null;
            Object[] args = new Object[regs.length];
            int minDefIdx = Integer.MAX_VALUE;
            for (int pi = 0; pi < regs.length; pi++) {
                int defIdx = findNearestDef(insns, regs[pi], invokeIdx);
                if (defIdx < 0) return null;
                Object val = constValueOf(insns.get(defIdx), paramTypes.get(pi).toString());
                if (val == NOT_CONST) return null;
                args[pi] = val;
                if (defIdx < minDefIdx) minDefIdx = defIdx;
            }

            // straight-line segment: no jump in between, no branch into it, no try coverage
            for (int i = minDefIdx; i < invokeIdx; i++) {
                if (isJumpLike(insns.get(i).getOpcode())) return null;
            }
            int segStart = addr[minDefIdx];
            int segEnd = addr[mrIdx] + insns.get(mrIdx).getCodeUnits();
            for (int t : branchTargets) {
                if (t >= segStart && t < segEnd) return null;
            }
            for (int[] tr : tryRanges) {
                if (tr[0] < segEnd && tr[1] > segStart) return null;
            }

            Plan plan = new Plan();
            plan.segStartIdx = minDefIdx;
            plan.defIdx = resultDefIdx;
            plan.invokeIdx = invokeIdx;
            plan.mrIdx = mrIdx;
            plan.resultReg = resultReg;
            plan.ref = ref;
            plan.argVals = args;
            plan.cipher = cipherRef.getString();
            return plan;
        }

        /** Index of the instruction that last wrote {@code reg} before {@code beforeIdx},
         * -2 when that write is not a const definition, -1 when unknown. */
        private static int findNearestDef(List<Instruction> insns, int reg, int beforeIdx) {
            for (int i = beforeIdx - 1; i >= 0; i--) {
                Instruction in = insns.get(i);
                if (isReadOnly(in.getOpcode())) continue;
                int a = registerAOf(in);
                if (a == reg) return isConstLike(in.getOpcode()) ? i : -2;
                if (a < 0) return -1;
            }
            return -1;
        }

        private static Object constValueOf(Instruction in, String descriptor) {
            if (!isConstLike(in.getOpcode())) return NOT_CONST;
            if (in instanceof ReferenceInstruction ri
                    && ri.getReference() instanceof StringReference sr) {
                return "Ljava/lang/String;".equals(descriptor) ? sr.getString() : NOT_CONST;
            }
            long lit;
            if (in instanceof NarrowLiteralInstruction nl) {
                lit = nl.getNarrowLiteral();
            } else if (in instanceof WideLiteralInstruction wl) {
                lit = wl.getWideLiteral();
            } else {
                return NOT_CONST;
            }
            if (lit < Integer.MIN_VALUE || lit > Integer.MAX_VALUE) {
                return "J".equals(descriptor) ? lit : NOT_CONST;
            }
            return switch (descriptor) {
                case "I", "Z", "B", "S", "C" -> (int) lit;
                case "J" -> lit;
                default -> NOT_CONST;
            };
        }

        private static boolean isConstLike(Opcode op) {
            return switch (op) {
                case CONST_STRING, CONST_STRING_JUMBO, CONST_4, CONST_16, CONST,
                     CONST_WIDE_16, CONST_WIDE_32, CONST_WIDE -> true;
                default -> false;
            };
        }

        /** Instructions that never change a register value. Anything else may have written the
         * register - the conservative direction is to fail the scan unless it is a const. */
        private static boolean isReadOnly(Opcode op) {
            return switch (op) {
                case INVOKE_VIRTUAL, INVOKE_SUPER, INVOKE_DIRECT, INVOKE_INTERFACE,
                     INVOKE_STATIC, INVOKE_STATIC_RANGE, INVOKE_VIRTUAL_RANGE,
                     INVOKE_SUPER_RANGE, INVOKE_DIRECT_RANGE, INVOKE_INTERFACE_RANGE,
                     GOTO, GOTO_16, GOTO_32,
                     IF_EQ, IF_NE, IF_LT, IF_GE, IF_GT, IF_LE,
                     IF_EQZ, IF_NEZ, IF_LTZ, IF_GEZ, IF_GTZ, IF_LEZ,
                     RETURN, RETURN_WIDE, RETURN_OBJECT, RETURN_VOID,
                     IPUT, IPUT_WIDE, IPUT_OBJECT, IPUT_BOOLEAN, IPUT_BYTE, IPUT_CHAR,
                     IPUT_SHORT, SPUT, SPUT_WIDE, SPUT_OBJECT, SPUT_BOOLEAN, SPUT_BYTE,
                     SPUT_CHAR, SPUT_SHORT,
                     MONITOR_ENTER, MONITOR_EXIT, NOP, CHECK_CAST, THROW,
                     PACKED_SWITCH, SPARSE_SWITCH, FILL_ARRAY_DATA, ARRAY_PAYLOAD,
                     PACKED_SWITCH_PAYLOAD, SPARSE_SWITCH_PAYLOAD -> true;
                default -> false;
            };
        }

        private static boolean isJumpLike(Opcode op) {
            return switch (op) {
                case GOTO, GOTO_16, GOTO_32,
                     IF_EQ, IF_NE, IF_LT, IF_GE, IF_GT, IF_LE,
                     IF_EQZ, IF_NEZ, IF_LTZ, IF_GEZ, IF_GTZ, IF_LEZ,
                     PACKED_SWITCH, SPARSE_SWITCH -> true;
                default -> false;
            };
        }

        private static int registerAOf(Instruction in) {
            if (in instanceof OneRegisterInstruction one) return one.getRegisterA();
            if (in instanceof TwoRegisterInstruction two) return two.getRegisterA();
            return -1;
        }

        private static int[] invokeRegisters(Instruction in) {
            if (in instanceof RegisterRangeInstruction range) {
                int start = range.getStartRegister();
                int count = range.getRegisterCount();
                int[] out = new int[count];
                for (int i = 0; i < count; i++) out[i] = start + i;
                return out;
            }
            if (in instanceof FiveRegisterInstruction f) {
                int count = f.getRegisterCount();
                int[] out = new int[count];
                if (count > 0) out[0] = f.getRegisterC();
                if (count > 1) out[1] = f.getRegisterD();
                if (count > 2) out[2] = f.getRegisterE();
                if (count > 3) out[3] = f.getRegisterF();
                if (count > 4) out[4] = f.getRegisterG();
                return out;
            }
            return null;
        }

        private static boolean hasStringParam(MethodReference ref) {
            for (CharSequence t : ref.getParameterTypes()) {
                if ("Ljava/lang/String;".contentEquals(t)) return true;
            }
            return false;
        }

        private boolean matchesSignature(MethodReference ref) {
            String custom = opts.customSignature;
            if (!custom.isEmpty()) return globMatch(signatureOf(ref), custom);
            if (opts.advanced) return true;
            String name = ref.getName();
            String cls = ref.getDefiningClass();
            return name.matches("(?i).*(decrypt|decode|xor|deobfuscat|unhash|decodestr).*")
                    || cls.matches("(?i).*(fog|crypt|obfusc|stringenc|strcrypt|xor|protect).*");
        }

        private static String signatureOf(MethodReference ref) {
            StringBuilder sb = new StringBuilder(ref.getDefiningClass())
                    .append("->").append(ref.getName()).append('(');
            for (CharSequence t : ref.getParameterTypes()) sb.append(t);
            return sb.append(')').append(ref.getReturnType()).toString();
        }

        /** Glob with '*' wildcards; without a descriptor the pattern matches as a prefix. */
        private static boolean globMatch(String value, String glob) {
            StringBuilder rx = new StringBuilder();
            StringBuilder atom = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*') {
                    if (atom.length() > 0) {
                        rx.append(Pattern.quote(atom.toString()));
                        atom.setLength(0);
                    }
                    rx.append(".*");
                } else {
                    atom.append(c);
                }
            }
            if (atom.length() > 0) rx.append(Pattern.quote(atom.toString()));
            boolean hasDesc = glob.indexOf('(') >= 0;
            // without a descriptor the glob must end at a boundary: "(", "->" or end of value
            String full = hasDesc ? "^" + rx + "$" : "^" + rx + "(?=$|\\(|->).*";
            return Pattern.matches(full, value);
        }

        private Method resolve(MethodReference ref) {
            ClassDef cls = global.get(ref.getDefiningClass());
            if (cls == null) return null;
            List<? extends CharSequence> want = ref.getParameterTypes();
            outer:
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(ref.getName())) continue;
                if (!m.getReturnType().equals(ref.getReturnType())) continue;
                if (m.getParameters().size() != want.size()) continue;
                for (int i = 0; i < want.size(); i++) {
                    if (!m.getParameters().get(i).getType().equals(want.get(i).toString())) {
                        continue outer;
                    }
                }
                return m;
            }
            return null;
        }

        private void log(String msg) {
            if (opts.listener != null && logged < 80) {
                logged++;
                opts.listener.onMessage(msg);
            }
        }
    }

    // -------------------------------------------------------------------------- interpreter

    private static final Object VOID = new Object();
    private static final Object WIDE_TAIL = new Object();
    private static final Object NO_PENDING = new Object();

    static final class Uninit {
        final String type;

        Uninit(String type) {
            this.type = type;
        }
    }

    private static final class Session {
        private final Map<String, Object> statics = new HashMap<>();
        private final Set<String> clinitDone = new HashSet<>();
        private Map<String, ClassDef> global;

        Object execute(Method m, Object[] args, Map<String, ClassDef> global, int depth) {
            this.global = global;
            return exec(m, Arrays.asList(args), depth);
        }

        private Object exec(Method m, List<Object> args, int depth) {
            if (depth > 16) throw new NotSupported("call depth");
            MethodImplementation impl = m.getImplementation();
            if (impl == null) throw new NotSupported("no body: " + m.getDefiningClass());
            ensureClinit(m.getDefiningClass(), depth);

            List<Instruction> insns = new ArrayList<>();
            for (Instruction in : impl.getInstructions()) insns.add(in);
            int n = insns.size();
            int[] addr = new int[n];
            int a = 0;
            for (int i = 0; i < n; i++) {
                addr[i] = a;
                a += insns.get(i).getCodeUnits();
            }
            Map<Integer, Integer> idxByAddr = new HashMap<>();
            for (int i = 0; i < n; i++) idxByAddr.put(addr[i], i);

            Object[] regs = new Object[impl.getRegisterCount()];
            int cursor = regs.length;
            for (int pi = 0; pi < args.size(); pi++) {
                String type = m.getParameters().get(pi).getType();
                cursor -= isWide(type) ? 2 : 1;
                if (cursor < 0) throw new NotSupported("register layout");
                regs[cursor] = args.get(pi);
                if (isWide(type) && cursor + 1 < regs.length) regs[cursor + 1] = WIDE_TAIL;
            }

            int pc = 0;
            long steps = 0;
            Object pending = NO_PENDING;
            while (true) {
                if (pc < 0 || pc >= n) throw new NotSupported("pc out of range");
                if (++steps > 500_000) throw new NotSupported("step limit");
                Instruction in = insns.get(pc);
                switch (in.getOpcode()) {
                    case NOP -> pc++;
                    case CONST_4, CONST_16, CONST -> {
                        regs[regA(in)] = narrowLit(in);
                        pc++;
                    }
                    case CONST_HIGH16 -> {
                        regs[regA(in)] = narrowLit(in) << 16;
                        pc++;
                    }
                    case CONST_WIDE_16, CONST_WIDE_32, CONST_WIDE -> {
                        regs[regA(in)] = wideLit(in);
                        pc++;
                    }
                    case CONST_WIDE_HIGH16 -> {
                        regs[regA(in)] = ((long) narrowLit(in)) << 48;
                        pc++;
                    }
                    case CONST_STRING, CONST_STRING_JUMBO -> {
                        regs[regA(in)] = stringRef(in);
                        pc++;
                    }
                    case MOVE, MOVE_OBJECT, MOVE_WIDE,
                         MOVE_16, MOVE_OBJECT_16, MOVE_WIDE_16,
                         MOVE_FROM16, MOVE_OBJECT_FROM16, MOVE_WIDE_FROM16 -> {
                        regs[regA(in)] = regs[regB(in)];
                        pc++;
                    }
                    case MOVE_RESULT -> {
                        regs[regA(in)] = takePending(pending, Integer.class);
                        pending = NO_PENDING;
                        pc++;
                    }
                    case MOVE_RESULT_WIDE -> {
                        regs[regA(in)] = takePending(pending, Long.class);
                        if (regA(in) + 1 < regs.length) regs[regA(in) + 1] = WIDE_TAIL;
                        pending = NO_PENDING;
                        pc++;
                    }
                    case MOVE_RESULT_OBJECT -> {
                        if (pending == NO_PENDING) throw new NotSupported("no pending result");
                        regs[regA(in)] = pending;
                        pending = NO_PENDING;
                        pc++;
                    }
                    case MOVE_EXCEPTION -> throw new NotSupported("move-exception");
                    case RETURN_VOID -> {
                        return VOID;
                    }
                    case RETURN, RETURN_WIDE, RETURN_OBJECT -> {
                        return regs[regA(in)];
                    }
                    case GOTO, GOTO_16, GOTO_32 -> pc = jumpTarget(in, pc, addr, idxByAddr);
                    case IF_EQZ, IF_NEZ, IF_LTZ, IF_GEZ, IF_GTZ, IF_LEZ -> {
                        long v = num(regs[regA(in)]);
                        boolean cond = switch (in.getOpcode()) {
                            case IF_EQZ -> v == 0;
                            case IF_NEZ -> v != 0;
                            case IF_LTZ -> v < 0;
                            case IF_GEZ -> v >= 0;
                            case IF_GTZ -> v > 0;
                            default -> v <= 0;
                        };
                        pc = branch(cond, in, pc, addr, idxByAddr);
                    }
                    case IF_EQ, IF_NE, IF_LT, IF_GE, IF_GT, IF_LE -> {
                        long l = num(regs[regA(in)]);
                        long r = num(regs[regB(in)]);
                        boolean cond = switch (in.getOpcode()) {
                            case IF_EQ -> l == r;
                            case IF_NE -> l != r;
                            case IF_LT -> l < r;
                            case IF_GE -> l >= r;
                            case IF_GT -> l > r;
                            default -> l <= r;
                        };
                        pc = branch(cond, in, pc, addr, idxByAddr);
                    }
                    case CHECK_CAST -> pc++;
                    case NEW_INSTANCE -> {
                        String type = ((TypeReference) ((ReferenceInstruction) in).getReference())
                                .getType();
                        if (!type.equals("Ljava/lang/String;")
                                && !type.equals("Ljava/lang/StringBuilder;")) {
                            throw new NotSupported("new-instance " + type);
                        }
                        regs[regA(in)] = new Uninit(type);
                        pc++;
                    }
                    case NEW_ARRAY -> {
                        String type = ((TypeReference) ((ReferenceInstruction) in).getReference())
                                .getType();
                        int len = (int) num(regs[regB(in)]);
                        regs[regA(in)] = newArray(type, len);
                        pc++;
                    }
                    case FILL_ARRAY_DATA -> {
                        Object arr = regs[regA(in)];
                        int pIdx = jumpTarget(in, pc, addr, idxByAddr);
                        if (!(insns.get(pIdx) instanceof ArrayPayload payload)) {
                            throw new NotSupported("fill payload");
                        }
                        fillArray(arr, payload);
                        pc++;
                    }
                    case ARRAY_PAYLOAD, PACKED_SWITCH_PAYLOAD, SPARSE_SWITCH_PAYLOAD ->
                            throw new NotSupported("payload fallthrough");
                    case ARRAY_LENGTH -> {
                        Object arr = regs[regB(in)];
                        if (arr == null || !arr.getClass().isArray()) {
                            throw new NotSupported("array-length");
                        }
                        regs[regA(in)] = java.lang.reflect.Array.getLength(arr);
                        pc++;
                    }
                    case AGET, AGET_OBJECT, AGET_BOOLEAN, AGET_BYTE, AGET_CHAR, AGET_SHORT,
                         AGET_WIDE -> {
                        int idx = (int) num(regs[regC(in)]);
                        regs[regA(in)] = java.lang.reflect.Array.get(regs[regB(in)], idx);
                        pc++;
                    }
                    case APUT, APUT_OBJECT, APUT_BOOLEAN, APUT_BYTE, APUT_CHAR, APUT_SHORT,
                         APUT_WIDE -> {
                        Object arr = regs[regB(in)];
                        int idx = (int) num(regs[regC(in)]);
                        java.lang.reflect.Array.set(arr, idx, coerceFor(arr, regs[regA(in)]));
                        pc++;
                    }
                    case NEG_INT -> {
                        regs[regA(in)] = (int) -num(regs[regB(in)]);
                        pc++;
                    }
                    case NOT_INT -> {
                        regs[regA(in)] = ~(int) num(regs[regB(in)]);
                        pc++;
                    }
                    case NEG_LONG -> {
                        regs[regA(in)] = -num(regs[regB(in)]);
                        pc++;
                    }
                    case NOT_LONG -> {
                        regs[regA(in)] = ~num(regs[regB(in)]);
                        pc++;
                    }
                    case INT_TO_LONG -> {
                        regs[regA(in)] = num(regs[regB(in)]);
                        regs[regA(in) + 1] = WIDE_TAIL;
                        pc++;
                    }
                    case INT_TO_BYTE -> {
                        regs[regA(in)] = (byte) num(regs[regB(in)]);
                        pc++;
                    }
                    case INT_TO_SHORT -> {
                        regs[regA(in)] = (short) num(regs[regB(in)]);
                        pc++;
                    }
                    case INT_TO_CHAR -> {
                        regs[regA(in)] = (char) num(regs[regB(in)]);
                        pc++;
                    }
                    case LONG_TO_INT -> {
                        regs[regA(in)] = (int) num(regs[regB(in)]);
                        pc++;
                    }
                    case ADD_INT, SUB_INT, MUL_INT, DIV_INT, REM_INT, AND_INT, OR_INT, XOR_INT,
                         SHL_INT, SHR_INT, USHR_INT -> {
                        regs[regA(in)] = intBin(in.getOpcode(),
                                (int) num(regs[regB(in)]), (int) num(regs[regC(in)]));
                        pc++;
                    }
                    case ADD_INT_2ADDR, SUB_INT_2ADDR, MUL_INT_2ADDR, DIV_INT_2ADDR,
                         REM_INT_2ADDR, AND_INT_2ADDR, OR_INT_2ADDR, XOR_INT_2ADDR,
                         SHL_INT_2ADDR, SHR_INT_2ADDR, USHR_INT_2ADDR -> {
                        regs[regA(in)] = intBin(in.getOpcode(),
                                (int) num(regs[regA(in)]), (int) num(regs[regB(in)]));
                        pc++;
                    }
                    case ADD_INT_LIT8, RSUB_INT_LIT8, MUL_INT_LIT8, DIV_INT_LIT8, REM_INT_LIT8,
                         AND_INT_LIT8, OR_INT_LIT8, XOR_INT_LIT8, SHL_INT_LIT8, SHR_INT_LIT8,
                         USHR_INT_LIT8 -> {
                        regs[regA(in)] = intLit(in.getOpcode(),
                                (int) num(regs[regB(in)]), narrowLit(in));
                        pc++;
                    }
                    case ADD_INT_LIT16, RSUB_INT, MUL_INT_LIT16, DIV_INT_LIT16,
                         REM_INT_LIT16, AND_INT_LIT16, OR_INT_LIT16, XOR_INT_LIT16 -> {
                        regs[regA(in)] = intLit(in.getOpcode(),
                                (int) num(regs[regA(in)]), narrowLit(in));
                        pc++;
                    }
                    case ADD_LONG, SUB_LONG, MUL_LONG, DIV_LONG, REM_LONG, AND_LONG, OR_LONG,
                         XOR_LONG, SHL_LONG, SHR_LONG, USHR_LONG -> {
                        regs[regA(in)] = longBin(in.getOpcode(),
                                num(regs[regB(in)]), num(regs[regC(in)]));
                        pc++;
                    }
                    case ADD_LONG_2ADDR, SUB_LONG_2ADDR, MUL_LONG_2ADDR, DIV_LONG_2ADDR,
                         REM_LONG_2ADDR, AND_LONG_2ADDR, OR_LONG_2ADDR, XOR_LONG_2ADDR,
                         SHL_LONG_2ADDR, SHR_LONG_2ADDR, USHR_LONG_2ADDR -> {
                        regs[regA(in)] = longBin(in.getOpcode(),
                                num(regs[regA(in)]), num(regs[regB(in)]));
                        pc++;
                    }
                    case CMP_LONG, CMPL_FLOAT, CMPG_FLOAT, CMPL_DOUBLE, CMPG_DOUBLE -> {
                        long l = num(regs[regB(in)]);
                        long r = num(regs[regC(in)]);
                        regs[regA(in)] = Long.compare(l, r);
                        pc++;
                    }
                    case SGET, SGET_WIDE, SGET_OBJECT, SGET_BOOLEAN, SGET_BYTE, SGET_CHAR,
                         SGET_SHORT -> {
                        FieldReference fr = (FieldReference) ((ReferenceInstruction) in)
                                .getReference();
                        checkOwnStatic(m, fr);
                        String key = fr.getDefiningClass() + "->" + fr.getName();
                        if (!statics.containsKey(key)) {
                            throw new NotSupported("uninitialized static " + key);
                        }
                        regs[regA(in)] = statics.get(key);
                        pc++;
                    }
                    case SPUT, SPUT_WIDE, SPUT_OBJECT, SPUT_BOOLEAN, SPUT_BYTE, SPUT_CHAR,
                         SPUT_SHORT -> {
                        FieldReference fr = (FieldReference) ((ReferenceInstruction) in)
                                .getReference();
                        checkOwnStatic(m, fr);
                        statics.put(fr.getDefiningClass() + "->" + fr.getName(),
                                regs[regA(in)]);
                        pc++;
                    }
                    case INVOKE_STATIC, INVOKE_STATIC_RANGE -> {
                        pending = invoke(m, in, regs, true, depth);
                        pc++;
                    }
                    case INVOKE_VIRTUAL, INVOKE_SUPER, INVOKE_DIRECT, INVOKE_INTERFACE,
                         INVOKE_VIRTUAL_RANGE, INVOKE_SUPER_RANGE, INVOKE_DIRECT_RANGE,
                         INVOKE_INTERFACE_RANGE -> {
                        pending = invoke(m, in, regs, false, depth);
                        pc++;
                    }
                    case PACKED_SWITCH, SPARSE_SWITCH -> throw new NotSupported("switch");
                    default -> throw new NotSupported("opcode " + in.getOpcode());
                }
            }
        }

        // ---------------------------------------------------------------- session helpers

        private Object invoke(Method owner, Instruction in, Object[] regs, boolean isStatic,
                              int depth) {
            MethodReference ref = (MethodReference) ((ReferenceInstruction) in).getReference();
            int[] argRegs = Engine.invokeRegisters(in);
            if (argRegs == null) throw new NotSupported("invoke regs");
            Object[] raw = new Object[argRegs.length];
            for (int i = 0; i < argRegs.length; i++) raw[i] = regs[argRegs[i]];

            if (!isStatic && ref.getName().equals("<init>")) {
                Object receiver = raw[0];
                if (!(receiver instanceof Uninit u)) throw new NotSupported("double init");
                regs[argRegs[0]] = construct(u.type, ref, raw);
                return null;
            }

            List<Object> args = new ArrayList<>(Arrays.asList(raw));
            Object nativeResult = callNative(ref, args, isStatic);
            if (nativeResult != NOT_CONST) return nativeResult;

            Method target = resolveMethod(ref);
            if (target == null) throw new NotSupported("method not found " + ref.getDefiningClass());
            return exec(target, new ArrayList<>(args), depth + 1);
        }

        /** Returns NOT_CONST when the call is not one of the whitelisted JDK/Android methods. */
        private Object callNative(MethodReference ref, List<Object> args, boolean isStatic) {
            String c = ref.getDefiningClass();
            String n = ref.getName();
            switch (c) {
                case "Ljava/lang/String;" -> {
                    String s = isStatic ? null : argStr(args, 0);
                    String p0 = ref.getParameterTypes().isEmpty() ? ""
                            : ref.getParameterTypes().get(0).toString();
                    return switch (n) {
                        case "length" -> s.length();
                        case "isEmpty" -> s.isEmpty() ? 1 : 0;
                        case "charAt" -> (int) s.charAt((int) num(args.get(1)));
                        case "indexOf" -> args.size() == 2
                                ? s.indexOf(argStr(args, 1)) : s.indexOf(argStr(args, 1),
                                (int) num(args.get(2)));
                        case "equals" -> s.equals(args.get(1)) ? 1 : 0;
                        case "equalsIgnoreCase" -> s.equalsIgnoreCase(argStr(args, 1)) ? 1 : 0;
                        case "compareTo" -> s.compareTo(argStr(args, 1));
                        case "toCharArray" -> s.toCharArray();
                        case "getBytes" -> args.size() <= 1
                                ? s.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                : s.getBytes(java.nio.charset.Charset.forName(argStr(args, 1)));
                        case "trim" -> s.trim();
                        case "concat" -> s.concat(argStr(args, 1));
                        case "substring" -> args.size() == 2
                                ? s.substring((int) num(args.get(1)))
                                : s.substring((int) num(args.get(1)), (int) num(args.get(2)));
                        case "startsWith" -> (args.size() == 2
                                ? s.startsWith(argStr(args, 1))
                                : s.startsWith(argStr(args, 1), (int) num(args.get(2)))) ? 1 : 0;
                        case "endsWith" -> s.endsWith(argStr(args, 1)) ? 1 : 0;
                        case "hashCode" -> s.hashCode();
                        case "toString" -> s;
                        case "replace" -> "C".equals(p0)
                                ? s.replace((char) num(args.get(1)), (char) num(args.get(2)))
                                : s.replace(argStr(args, 1), argStr(args, 2));
                        case "toLowerCase" -> s.toLowerCase();
                        case "toUpperCase" -> s.toUpperCase();
                        case "contains" -> s.contains(argStr(args, 1)) ? 1 : 0;
                        case "valueOf" -> switch (p0) {
                            case "C" -> String.valueOf((char) num(args.get(0)));
                            case "J" -> String.valueOf(num(args.get(0)));
                            case "I", "Z" -> String.valueOf((int) num(args.get(0)));
                            default -> stringOf(args.get(0));
                        };
                        case "copyValueOf" -> args.get(0) instanceof char[] ca
                                ? new String(ca) : stringOf(args.get(0));
                        default -> throw new NotSupported("String." + n);
                    };
                }
                case "Ljava/lang/StringBuilder;" -> {
                    if (n.equals("toString")) return ((StringBuilder) args.get(0)).toString();
                    if (n.equals("length")) return ((StringBuilder) args.get(0)).length();
                    if (n.equals("append")) {
                        StringBuilder sb = (StringBuilder) args.get(0);
                        Object v = args.get(1);
                        String dt = ref.getParameterTypes().get(0).toString();
                        switch (dt) {
                            case "C" -> sb.append((char) num(v));
                            case "I", "B", "S" -> sb.append((int) num(v));
                            case "Z" -> sb.append(num(v) != 0);
                            case "J" -> sb.append(num(v));
                            case "F", "D" -> throw new NotSupported("append float");
                            case "[C" -> sb.append((char[]) v);
                            case "Ljava/lang/String;" -> sb.append((String) v);
                            default -> sb.append(stringOf(v));
                        }
                        return sb;
                    }
                    throw new NotSupported("StringBuilder." + n);
                }
                case "Ljava/util/Base64;", "Ljava/util/Base64$Decoder;",
                     "Ljava/util/Base64$Encoder;" -> {
                    return base64Call(c, n, args);
                }
                case "Landroid/util/Base64;" -> {
                    return androidBase64(n, args);
                }
                case "Ljava/util/Arrays;" -> {
                    if (n.equals("copyOf")) {
                        Object src = args.get(0);
                        int len = (int) num(args.get(1));
                        if (src instanceof byte[] a) return java.util.Arrays.copyOf(a, len);
                        if (src instanceof char[] a) return java.util.Arrays.copyOf(a, len);
                        if (src instanceof int[] a) return java.util.Arrays.copyOf(a, len);
                        if (src instanceof long[] a) return java.util.Arrays.copyOf(a, len);
                        if (src instanceof short[] a) return java.util.Arrays.copyOf(a, len);
                        if (src instanceof Object[] a) return java.util.Arrays.copyOf(a, len);
                        throw new NotSupported("copyOf type");
                    }
                    if (n.equals("toString")) {
                        Object src = args.get(0);
                        if (src instanceof int[] a) return java.util.Arrays.toString(a);
                        if (src instanceof Object[] a) return java.util.Arrays.toString(a);
                        if (src instanceof long[] a) return java.util.Arrays.toString(a);
                        if (src instanceof boolean[] a) return java.util.Arrays.toString(a);
                        if (src instanceof char[] a) return java.util.Arrays.toString(a);
                        if (src instanceof byte[] a) return java.util.Arrays.toString(a);
                        throw new NotSupported("Arrays.toString type");
                    }
                    throw new NotSupported("Arrays." + n);
                }
                case "Ljava/lang/System;" -> {
                    if (n.equals("arraycopy")) {
                        System.arraycopy(args.get(0), (int) num(args.get(1)), args.get(2),
                                (int) num(args.get(3)), (int) num(args.get(4)));
                        return null;
                    }
                    throw new NotSupported("System." + n);
                }
                case "Ljava/lang/Integer;" -> {
                    return switch (n) {
                        case "parseInt" -> args.size() == 1
                                ? Integer.parseInt(argStr(args, 0))
                                : Integer.parseInt(argStr(args, 0), (int) num(args.get(1)));
                        case "toString" -> args.size() == 1
                                ? Integer.toString((int) num(args.get(0)))
                                : Integer.toString((int) num(args.get(0)), (int) num(args.get(1)));
                        case "valueOf" -> args.get(0) instanceof String s
                                ? Integer.valueOf(s) : (int) num(args.get(0));
                        case "toHexString" -> Integer.toHexString((int) num(args.get(0)));
                        default -> throw new NotSupported("Integer." + n);
                    };
                }
                case "Ljava/lang/Long;" -> {
                    return switch (n) {
                        case "parseLong" -> Long.parseLong(argStr(args, 0));
                        case "toString" -> Long.toString(num(args.get(0)));
                        case "valueOf" -> args.get(0) instanceof String s
                                ? Long.valueOf(s) : num(args.get(0));
                        case "toHexString" -> Long.toHexString(num(args.get(0)));
                        default -> throw new NotSupported("Long." + n);
                    };
                }
                case "Ljava/lang/Character;" -> {
                    return switch (n) {
                        case "toUpperCase" -> (int) Character.toUpperCase((char) num(args.get(0)));
                        case "toLowerCase" -> (int) Character.toLowerCase((char) num(args.get(0)));
                        case "digit" -> Character.digit((char) num(args.get(0)),
                                args.size() > 1 ? (int) num(args.get(1)) : 10);
                        case "forDigit" -> (int) Character.forDigit((int) num(args.get(0)),
                                args.size() > 1 ? (int) num(args.get(1)) : 10);
                        default -> throw new NotSupported("Character." + n);
                    };
                }
                case "Ljava/lang/Byte;", "Ljava/lang/Short;", "Ljava/lang/Boolean;",
                     "Ljava/lang/Float;", "Ljava/lang/Double;", "Ljava/lang/Math;" -> {
                    throw new NotSupported("jdk " + c + "." + n);
                }
                case "Ljava/lang/Object;" -> {
                    if (n.equals("toString")) return stringOf(args.get(0));
                    if (n.equals("hashCode")) return args.get(0) == null ? 0 : args.get(0).hashCode();
                    if (n.equals("equals")) return (args.get(0) == null
                            ? args.get(1) == null : args.get(0).equals(args.get(1))) ? 1 : 0;
                    throw new NotSupported("Object." + n);
                }
                default -> {
                    return NOT_CONST;
                }
            }
        }

        private Object construct(String type, MethodReference ref, Object[] raw) {
            List<? extends CharSequence> params = ref.getParameterTypes();
            switch (type) {
                case "Ljava/lang/String;" -> {
                    if (params.isEmpty()) return "";
                    if (params.size() == 1) {
                        Object p = raw[1];
                        if (p instanceof char[] ca) return new String(ca);
                        if (p instanceof byte[] ba) return new String(ba,
                                java.nio.charset.StandardCharsets.UTF_8);
                        if (p instanceof String s) return new String(s);
                    }
                    if (params.size() == 2 && "Ljava/lang/String;".contentEquals(params.get(0))
                            && "Ljava/lang/String;".contentEquals(params.get(1))) {
                        return new String((byte[]) raw[1],
                                java.nio.charset.Charset.forName((String) raw[2]));
                    }
                    throw new NotSupported("String init " + params);
                }
                case "Ljava/lang/StringBuilder;" -> {
                    if (params.isEmpty()) return new StringBuilder();
                    if (params.size() == 1 && "Ljava/lang/String;".contentEquals(params.get(0))) {
                        return new StringBuilder((String) raw[1]);
                    }
                    throw new NotSupported("StringBuilder init " + params);
                }
                default -> throw new NotSupported("init " + type);
            }
        }

        private void ensureClinit(String type, int depth) {
            if (!clinitDone.add(type)) return;
            ClassDef cls = global.get(type);
            if (cls == null) return;
            for (Method m : cls.getMethods()) {
                if (m.getName().equals("<clinit>") && m.getParameters().isEmpty()) {
                    exec(m, new ArrayList<>(), depth + 1);
                    return;
                }
            }
        }

        private void checkOwnStatic(Method m, FieldReference fr) {
            if (!fr.getDefiningClass().equals(m.getDefiningClass())) {
                throw new NotSupported("cross-class static " + fr.getDefiningClass());
            }
        }

        private Method resolveMethod(MethodReference ref) {
            ClassDef cls = global.get(ref.getDefiningClass());
            if (cls == null) return null;
            List<? extends CharSequence> want = ref.getParameterTypes();
            outer:
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(ref.getName())) continue;
                if (!m.getReturnType().equals(ref.getReturnType())) continue;
                if (m.getParameters().size() != want.size()) continue;
                for (int i = 0; i < want.size(); i++) {
                    if (!m.getParameters().get(i).getType().equals(want.get(i).toString())) {
                        continue outer;
                    }
                }
                return m;
            }
            return null;
        }
    }

    // ------------------------------------------------------------------------- static utils

    private static int regA(Instruction in) {
        return ((OneRegisterInstruction) in).getRegisterA();
    }

    private static int regB(Instruction in) {
        return ((TwoRegisterInstruction) in).getRegisterB();
    }

    private static int regC(Instruction in) {
        return ((ThreeRegisterInstruction) in).getRegisterC();
    }

    private static int narrowLit(Instruction in) {
        if (in instanceof NarrowLiteralInstruction nl) return nl.getNarrowLiteral();
        if (in instanceof WideLiteralInstruction wl) return (int) wl.getWideLiteral();
        throw new NotSupported("narrow literal");
    }

    private static long wideLit(Instruction in) {
        if (in instanceof WideLiteralInstruction wl) return wl.getWideLiteral();
        if (in instanceof NarrowLiteralInstruction nl) return nl.getNarrowLiteral();
        throw new NotSupported("wide literal");
    }

    private static String stringRef(Instruction in) {
        return ((StringReference) ((ReferenceInstruction) in).getReference()).getString();
    }

    private static long num(Object o) {
        if (o instanceof Integer i) return i;
        if (o instanceof Long l) return l;
        if (o instanceof Byte b) return b;
        if (o instanceof Short s) return s;
        if (o instanceof Character c) return c;
        if (o instanceof Boolean b) return b ? 1 : 0;
        throw new NotSupported("not numeric: " + o);
    }

    private static <T> T takePending(Object pending, Class<T> type) {
        if (pending == NO_PENDING || pending == VOID) {
            throw new NotSupported("no pending result");
        }
        if (!type.isInstance(pending)) {
            throw new NotSupported("pending type " + pending.getClass());
        }
        return type.cast(pending);
    }

    private static String argStr(List<Object> args, int i) {
        Object v = args.get(i);
        if (v instanceof String s) return s;
        throw new NotSupported("expected String, got " + v);
    }

    private static String stringOf(Object v) {
        return String.valueOf(v);
    }

    private static boolean isWide(String type) {
        return "J".equals(type) || "D".equals(type);
    }

    private static int jumpTarget(Instruction in, int pc, int[] addr,
                                  Map<Integer, Integer> idx) {
        // dex branch offsets are relative to the *start* of the current instruction
        int target = addr[pc] + ((OffsetInstruction) in).getCodeOffset();
        Integer i = idx.get(target);
        if (i == null) throw new NotSupported("branch target " + target + " for "
                + in.getOpcode() + " pc=" + pc + " addr=" + addr[pc]
                + " units=" + in.getCodeUnits() + " keys=" + new TreeSet<>(idx.keySet()));
        return i;
    }

    private static int branch(boolean cond, Instruction in, int pc, int[] addr,
                              Map<Integer, Integer> idx) {
        return cond ? jumpTarget(in, pc, addr, idx) : pc + 1;
    }

    private static int intBin(Opcode op, int l, int r) {
        return switch (op) {
            case ADD_INT, ADD_INT_2ADDR -> l + r;
            case SUB_INT, SUB_INT_2ADDR -> l - r;
            case MUL_INT, MUL_INT_2ADDR -> l * r;
            case DIV_INT, DIV_INT_2ADDR -> l / r;
            case REM_INT, REM_INT_2ADDR -> l % r;
            case AND_INT, AND_INT_2ADDR -> l & r;
            case OR_INT, OR_INT_2ADDR -> l | r;
            case XOR_INT, XOR_INT_2ADDR -> l ^ r;
            case SHL_INT, SHL_INT_2ADDR -> l << r;
            case SHR_INT, SHR_INT_2ADDR -> l >> r;
            case USHR_INT, USHR_INT_2ADDR -> l >>> r;
            default -> throw new NotSupported("int op " + op);
        };
    }

    private static int intLit(Opcode op, int l, int lit) {
        return switch (op) {
            case ADD_INT_LIT8, ADD_INT_LIT16 -> l + lit;
            case RSUB_INT_LIT8, RSUB_INT -> lit - l;
            case MUL_INT_LIT8, MUL_INT_LIT16 -> l * lit;
            case DIV_INT_LIT8, DIV_INT_LIT16 -> l / lit;
            case REM_INT_LIT8, REM_INT_LIT16 -> l % lit;
            case AND_INT_LIT8, AND_INT_LIT16 -> l & lit;
            case OR_INT_LIT8, OR_INT_LIT16 -> l | lit;
            case XOR_INT_LIT8, XOR_INT_LIT16 -> l ^ lit;
            case SHL_INT_LIT8 -> l << lit;
            case SHR_INT_LIT8 -> l >> lit;
            case USHR_INT_LIT8 -> l >>> lit;
            default -> throw new NotSupported("int lit op " + op);
        };
    }

    private static long longBin(Opcode op, long l, long r) {
        return switch (op) {
            case ADD_LONG, ADD_LONG_2ADDR -> l + r;
            case SUB_LONG, SUB_LONG_2ADDR -> l - r;
            case MUL_LONG, MUL_LONG_2ADDR -> l * r;
            case DIV_LONG, DIV_LONG_2ADDR -> l / r;
            case REM_LONG, REM_LONG_2ADDR -> l % r;
            case AND_LONG, AND_LONG_2ADDR -> l & r;
            case OR_LONG, OR_LONG_2ADDR -> l | r;
            case XOR_LONG, XOR_LONG_2ADDR -> l ^ r;
            case SHL_LONG, SHL_LONG_2ADDR -> l << r;
            case SHR_LONG, SHR_LONG_2ADDR -> l >> r;
            case USHR_LONG, USHR_LONG_2ADDR -> l >>> r;
            default -> throw new NotSupported("long op " + op);
        };
    }

    private static Object newArray(String desc, int len) {
        if (len < 0) throw new NotSupported("negative array size");
        String comp = desc.length() > 1 ? desc.substring(1) : desc;
        return switch (comp) {
            case "B" -> new byte[len];
            case "C" -> new char[len];
            case "S" -> new short[len];
            case "I" -> new int[len];
            case "J" -> new long[len];
            case "F" -> new float[len];
            case "D" -> new double[len];
            case "Z" -> new boolean[len];
            default -> new Object[len];
        };
    }

    private static void fillArray(Object arr, ArrayPayload payload) {
        List<Number> elements = payload.getArrayElements();
        for (int i = 0; i < elements.size(); i++) {
            java.lang.reflect.Array.set(arr, i, coerceFor(arr, elements.get(i)));
        }
    }

    private static Object coerceFor(Object arr, Object v) {
        Class<?> comp = arr.getClass().getComponentType();
        if (comp == byte.class) return (byte) num(v);
        if (comp == short.class) return (short) num(v);
        if (comp == char.class) return (char) num(v);
        if (comp == int.class) return (int) num(v);
        if (comp == long.class) return num(v);
        if (comp == boolean.class) return num(v) != 0;
        if (comp == float.class || comp == double.class) {
            throw new NotSupported("float array");
        }
        return v;
    }

    // ------------------------------------------------------------------------------- base64

    static final class B64Key {
        final boolean url;

        B64Key(boolean url) {
            this.url = url;
        }
    }

    static final class B64EncKey {
        final boolean url;

        B64EncKey(boolean url) {
            this.url = url;
        }
    }

    private static Object base64Call(String cls, String n, List<Object> args) {
        switch (cls) {
            case "Ljava/util/Base64;" -> {
                if (n.startsWith("get")) {
                    boolean url = n.contains("Url");
                    return n.endsWith("Encoder") ? new B64EncKey(url) : new B64Key(url);
                }
                throw new NotSupported("Base64." + n);
            }
            case "Ljava/util/Base64$Decoder;" -> {
                boolean url = args.get(0) instanceof B64Key k && k.url;
                return b64decode(argStr(args, 1), url);
            }
            case "Ljava/util/Base64$Encoder;" -> {
                boolean url = args.get(0) instanceof B64EncKey k && k.url;
                return b64encode((byte[]) args.get(1), url, true, false);
            }
            default -> throw new NotSupported("base64 " + cls);
        }
    }

    private static Object androidBase64(String n, List<Object> args) {
        if (n.equals("decode")) {
            String s = argStr(args, 0);
            int flags = args.size() > 1 ? (int) num(args.get(1)) : 0;
            return b64decode(s, (flags & 8) != 0);
        }
        if (n.equals("encodeToString")) {
            byte[] data = (byte[]) args.get(0);
            int flags = (int) num(args.get(1));
            return b64encode(data, (flags & 8) != 0, (flags & 1) == 0, (flags & 2) == 0);
        }
        throw new NotSupported("android Base64." + n);
    }

    private static byte[] b64decode(String s, boolean url) {
        String tbl = url
                ? "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int[] rev = new int[128];
        Arrays.fill(rev, -1);
        for (int i = 0; i < 64; i++) rev[tbl.charAt(i)] = i;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int acc = 0;
        int bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '=') break;
            if (Character.isWhitespace(ch)) continue;
            int v = ch < 128 ? rev[ch] : -1;
            if (v < 0) continue;
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((acc >> bits) & 0xFF);
                acc &= (1 << bits) - 1;
            }
        }
        return out.toByteArray();
    }

    private static String b64encode(byte[] data, boolean url, boolean pad, boolean wrap) {
        String tbl = url
                ? "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
        int i = 0;
        while (i + 3 <= data.length) {
            int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8)
                    | (data[i + 2] & 0xFF);
            sb.append(tbl.charAt((v >> 18) & 63)).append(tbl.charAt((v >> 12) & 63))
                    .append(tbl.charAt((v >> 6) & 63)).append(tbl.charAt(v & 63));
            i += 3;
        }
        int rem = data.length - i;
        if (rem == 1) {
            int v = (data[i] & 0xFF) << 16;
            sb.append(tbl.charAt((v >> 18) & 63)).append(tbl.charAt((v >> 12) & 63));
            if (pad) sb.append("==");
        } else if (rem == 2) {
            int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8);
            sb.append(tbl.charAt((v >> 18) & 63)).append(tbl.charAt((v >> 12) & 63))
                    .append(tbl.charAt((v >> 6) & 63));
            if (pad) sb.append('=');
        }
        if (wrap) {
            StringBuilder w = new StringBuilder();
            for (int p = 0; p < sb.length(); p += 76) {
                w.append(sb, p, Math.min(p + 76, sb.length())).append('\n');
            }
            return w.toString();
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------------------ markers

    static final class NotSupported extends RuntimeException {
        NotSupported(String why) {
            super(why);
        }
    }
}

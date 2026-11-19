package io.github.abdurazaaqmohammed.feature.apk.privacy;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableFieldReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristics and bytecode transformation rules for Module B: Generative AI & Assistant Nullification.
 * <p>
 * Handles:
 * 1. Universal Boolean eligibility stubs (isEligible, isEnabled, supportsAssistant, getSupportsAssistant,
 *    isAiEnabled, isRufusEnabled) on AI controller/manager classes.
 * 2. Object eligibility stubs for Amazon SavX (evaluateEligibility() -> NOT_ELIGIBLE).
 * 3. Cloud experiment gates (IMobileWeblab / LaunchDarkly treatment callsites forced to "C" or false).
 * 4. UI tab and activity ingress neutralization (BottomTabsBarSavXController, Rufus ingress points).
 * 5. Reachability-aware dead code verification.
 */
public final class AiBytecodeRules {

    private AiBytecodeRules() {}

    public static final String SAVX_ELIGIBILITY_STATE_CLASS =
            "Lcom/amazon/mShop/savX/manager/eligibility/SavXEligibilityState;";

    public static final Pattern AI_CONTROLLER_CLASS_PATTERN = Pattern.compile(
            ".*(savx|rufus|assistant|sparky|copilot|genai|ai_?chat|userfeaturemanager).*",
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern EXPERIMENT_NAME_PATTERN = Pattern.compile(
            ".*(rufus|assistant|sparky|copilot|genai|ai_?chat).*",
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern TAB_INGRESS_CLASS_PATTERN = Pattern.compile(
            ".*(BottomTabsBarSavXController|SavXTabBar|RufusTab|RufusIngress).*",
            Pattern.CASE_INSENSITIVE
    );

    public static final Pattern TAB_INGRESS_METHOD_PATTERN = Pattern.compile(
            "(?i).*(selectRufusTab.*|showRufusIngress.*).*"
    );

    private static final Set<String> UNIVERSAL_BOOLEAN_ELIGIBILITY_METHODS = new HashSet<>(Arrays.asList(
            "isEligible", "isEnabled", "supportsAssistant", "getSupportsAssistant", "isAiEnabled", "isRufusEnabled"
    ));

    private static final Set<String> ASSISTANT_SPECIFIC_BOOLEAN_METHODS = new HashSet<>(Arrays.asList(
            "supportsAssistant", "getSupportsAssistant", "isAiEnabled", "isRufusEnabled"
    ));

    /**
     * Checks if a class descriptor or name represents an AI controller or manager class.
     */
    public static boolean isAiControllerClass(String classType) {
        if (classType == null) return false;
        if (classType.contains("SavXEligibilityState") || classType.endsWith("State;") || classType.endsWith("Status;")) {
            return false;
        }
        return AI_CONTROLLER_CLASS_PATTERN.matcher(classType).matches();
    }

    /**
     * Checks if a ClassDef represents an AI controller, examining type or defined AI methods.
     */
    public static boolean isAiControllerClass(ClassDef classDef) {
        if (classDef == null) return false;
        if ((classDef.getAccessFlags() & AccessFlags.ENUM.getValue()) != 0) return false;
        if ("Ljava/lang/Enum;".equals(classDef.getSuperclass())) return false;
        if (isAiControllerClass(classDef.getType())) return true;
        for (Method m : classDef.getMethods()) {
            String name = m.getName();
            if (ASSISTANT_SPECIFIC_BOOLEAN_METHODS.contains(name)
                    || isSavXEligibilityReturn(m.getReturnType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a return type descriptor represents SavXEligibilityState.
     */
    public static boolean isSavXEligibilityReturn(String returnType) {
        if (returnType == null || returnType.startsWith("[")) return false;
        String norm = DexReachabilityIndex.normalizeClassType(returnType);
        return SAVX_ELIGIBILITY_STATE_CLASS.equals(norm) || (returnType.startsWith("L") && returnType.endsWith("SavXEligibilityState;"));
    }

    /**
     * Determines whether a method is an AI eligibility method on an AI controller class.
     */
    public static boolean isEligibilityMethod(Method method, boolean isAiClass) {
        if (method == null) return false;
        String name = method.getName();
        if ("<init>".equals(name) || "<clinit>".equals(name) || "values".equals(name) || "valueOf".equals(name) || name.startsWith("$")) return false;

        String returnType = method.getReturnType();
        if (returnType == null || returnType.startsWith("[")) return false;

        // 1. Amazon SavX evaluateEligibility() -> SavXEligibilityState
        if (isSavXEligibilityReturn(returnType)) {
            if (isAiClass || "evaluateEligibility".equals(name) || name.toLowerCase().contains("eligib")) {
                return true;
            }
        }

        // 2. Universal Boolean eligibility methods returning 'Z'
        if ("Z".equals(returnType)) {
            if (ASSISTANT_SPECIFIC_BOOLEAN_METHODS.contains(name)) {
                return true;
            }
            if (UNIVERSAL_BOOLEAN_ELIGIBILITY_METHODS.contains(name) && isAiClass) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines whether a method is an AI eligibility method given the class descriptor.
     */
    public static boolean isEligibilityMethod(Method method, String classType) {
        return isEligibilityMethod(method, isAiControllerClass(classType));
    }

    /**
     * Stubs an AI eligibility method to return false (for boolean) or NOT_ELIGIBLE (for SavX).
     * Respects reachabilityIndex if provided.
     */
    public static Method nullifyEligibilityMethod(Method method, DexReachabilityIndex reachabilityIndex, PrivacyStats stats, boolean isAiClass) {
        if (method == null) return null;
        if (!isEligibilityMethod(method, isAiClass)) {
            return method;
        }

        // Reachability dead code check
        if (reachabilityIndex != null && !isMethodReferenced(reachabilityIndex, method)) {
            if (stats != null) {
                stats.aiDeadCodeSkipped++;
                stats.deadCodeSkipped++;
            }
            return method;
        }

        String returnType = method.getReturnType();
        boolean isWide = "J".equals(returnType) || "D".equals(returnType);
        int requiredLocals = isWide ? 2 : 1;
        int paramRegs = computeParameterRegisters(method);
        int origRegs = method.getImplementation() != null ? method.getImplementation().getRegisterCount() : 0;
        int regCount = Math.max(paramRegs + requiredLocals, origRegs);

        MutableMethodImplementation newImpl = new MutableMethodImplementation(regCount);

        if (isSavXEligibilityReturn(returnType)) {
            String enumClass = (returnType.startsWith("L") && returnType.endsWith(";") && !returnType.startsWith("["))
                    ? returnType
                    : SAVX_ELIGIBILITY_STATE_CLASS;
            FieldReference fieldRef = new ImmutableFieldReference(
                    enumClass,
                    "NOT_ELIGIBLE",
                    enumClass
            );
            newImpl.addInstruction(new BuilderInstruction21c(Opcode.SGET_OBJECT, 0, fieldRef));
            newImpl.addInstruction(new BuilderInstruction11x(Opcode.RETURN_OBJECT, 0));
        } else {
            // Boolean stub: const/4 v0, 0 -> return v0
            newImpl.addInstruction(PrivacyBytecodeEngine.createConstInt(0, 0));
            newImpl.addInstruction(new BuilderInstruction11x(Opcode.RETURN, 0));
        }

        if (stats != null) {
            stats.aiEligibilityManagersStubbed++;
        }

        int flags = method.getAccessFlags() & ~(AccessFlags.ABSTRACT.getValue() | AccessFlags.NATIVE.getValue());
        return new ImmutableMethod(
                method.getDefiningClass(),
                method.getName(),
                method.getParameters(),
                method.getReturnType(),
                flags,
                method.getAnnotations(),
                method.getHiddenApiRestrictions(),
                newImpl
        );
    }

    /**
     * Overload for backward compatibility using class descriptor.
     */
    public static Method nullifyEligibilityMethod(Method method, DexReachabilityIndex reachabilityIndex, PrivacyStats stats) {
        return nullifyEligibilityMethod(method, reachabilityIndex, stats, isAiControllerClass(method != null ? method.getDefiningClass() : null));
    }

    /**
     * Checks if a class is an AI UI tab or ingress controller.
     */
    public static boolean isTabIngressClass(String classType) {
        if (classType == null) return false;
        return TAB_INGRESS_CLASS_PATTERN.matcher(classType).matches();
    }

    /**
     * Checks if a method is a UI tab or ingress trigger.
     */
    public static boolean isTabIngressMethod(Method method, String classType) {
        if (method == null) return false;
        String name = method.getName();
        if ("<init>".equals(name) || "<clinit>".equals(name)) return false;

        // Restrict tab ingress neutralization to void and primitive returns to avoid NPE on objects
        String returnType = method.getReturnType();
        if (returnType == null || returnType.startsWith("L") || returnType.startsWith("[")) {
            return false;
        }

        if (TAB_INGRESS_METHOD_PATTERN.matcher(name).matches()) {
            return true;
        }

        if (isTabIngressClass(classType)) {
            return name.startsWith("selectRufus")
                    || name.startsWith("showRufus")
                    || name.startsWith("populateRufus")
                    || name.contains("RufusTab");
        }

        return false;
    }

    /**
     * Neutralizes a tab ingress or activity ingress method to a safe no-op return.
     * Respects reachabilityIndex if provided.
     */
    public static Method nullifyTabIngressMethod(Method method, DexReachabilityIndex reachabilityIndex, PrivacyStats stats) {
        if (method == null) return null;
        if (!isTabIngressMethod(method, method.getDefiningClass())) {
            return method;
        }

        // Reachability dead code check
        if (reachabilityIndex != null && !isMethodReferenced(reachabilityIndex, method)) {
            if (stats != null) {
                stats.aiDeadCodeSkipped++;
                stats.deadCodeSkipped++;
            }
            return method;
        }

        String returnType = method.getReturnType();
        boolean isWide = "J".equals(returnType) || "D".equals(returnType);
        int requiredLocals = isWide ? 2 : 1;
        int paramRegs = computeParameterRegisters(method);
        int origRegs = method.getImplementation() != null ? method.getImplementation().getRegisterCount() : 0;
        int regCount = Math.max(paramRegs + requiredLocals, origRegs);

        MutableMethodImplementation newImpl = new MutableMethodImplementation(regCount);

        if ("V".equals(returnType)) {
            newImpl.addInstruction(new BuilderInstruction10x(Opcode.RETURN_VOID));
        } else if ("Z".equals(returnType) || "B".equals(returnType) || "C".equals(returnType)
                || "S".equals(returnType) || "I".equals(returnType) || "F".equals(returnType)) {
            newImpl.addInstruction(PrivacyBytecodeEngine.createConstInt(0, 0));
            newImpl.addInstruction(new BuilderInstruction11x(Opcode.RETURN, 0));
        } else if ("J".equals(returnType) || "D".equals(returnType)) {
            newImpl.addInstruction(PrivacyBytecodeEngine.createConstWide(0, 0L));
            newImpl.addInstruction(new BuilderInstruction11x(Opcode.RETURN_WIDE, 0));
        } else {
            // Complex object or unsupported type - do not mutate to avoid NPE / VerifyError
            return method;
        }

        if (stats != null) {
            stats.aiTabIngressNeutralized++;
        }

        int flags = method.getAccessFlags() & ~(AccessFlags.ABSTRACT.getValue() | AccessFlags.NATIVE.getValue());
        return new ImmutableMethod(
                method.getDefiningClass(),
                method.getName(),
                method.getParameters(),
                method.getReturnType(),
                flags,
                method.getAnnotations(),
                method.getHiddenApiRestrictions(),
                newImpl
        );
    }

    /**
     * Checks if an invoke instruction is an experiment gate invocation matching AI experiment patterns.
     */
    public static boolean isExperimentGateInvocation(Instruction inst, MethodReference mr, List<BuilderInstruction> instructions, int invokeIdx, Map<Integer, String> stringRegisters) {
        if (inst == null || mr == null) return false;

        // Check if method name itself matches AI patterns
        if (EXPERIMENT_NAME_PATTERN.matcher(mr.getName()).matches()) {
            return true;
        }

        // Check argument registers for experiment string matching
        List<Integer> argRegs = getArgumentRegisters(inst);
        for (int reg : argRegs) {
            String str = resolveKeyString(instructions, invokeIdx, reg, stringRegisters);
            if (str != null && EXPERIMENT_NAME_PATTERN.matcher(str).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Intercepts a WebLab / LaunchDarkly treatment callsite.
     * If instructionIndex points to an invoke instruction, inspects and replaces the following
     * move-result / move-result-object instruction with "C" (for String) or 0 (for boolean).
     * If instructionIndex points directly to a move-result / move-result-object instruction,
     * replaces it directly.
     */
    public static boolean interceptWebLabTreatment(MutableMethodImplementation mutable, int instructionIndex, PrivacyStats stats) {
        if (mutable == null || instructionIndex < 0 || instructionIndex >= mutable.getInstructions().size()) {
            return false;
        }

        Instruction inst = mutable.getInstructions().get(instructionIndex);
        Opcode op = inst.getOpcode();

        // Case 1: instructionIndex is the invoke instruction
        if (PrivacyBytecodeEngine.isInvoke(op)) {
            if (inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof MethodReference) {
                    MethodReference mr = (MethodReference) ref;
                    String returnType = mr.getReturnType();
                    if (instructionIndex + 1 < mutable.getInstructions().size()) {
                        Instruction next = mutable.getInstructions().get(instructionIndex + 1);
                        if ("Ljava/lang/String;".equals(returnType) || returnType.endsWith("String;")) {
                            if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                int reg = ((OneRegisterInstruction) next).getRegisterA();
                                mutable.replaceInstruction(instructionIndex + 1, new BuilderInstruction21c(
                                        Opcode.CONST_STRING,
                                        reg,
                                        new ImmutableStringReference("C")
                                ));
                                if (stats != null) stats.aiFeatureGatesNeutralized++;
                                return true;
                            }
                        } else if ("Z".equals(returnType)) {
                            if (next.getOpcode() == Opcode.MOVE_RESULT && next instanceof OneRegisterInstruction) {
                                int reg = ((OneRegisterInstruction) next).getRegisterA();
                                mutable.replaceInstruction(instructionIndex + 1, PrivacyBytecodeEngine.createConstInt(reg, 0));
                                if (stats != null) stats.aiFeatureGatesNeutralized++;
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        // Case 2: instructionIndex is already the move-result / move-result-object instruction
        if (op == Opcode.MOVE_RESULT_OBJECT && inst instanceof OneRegisterInstruction) {
            if (instructionIndex > 0) {
                Instruction prev = mutable.getInstructions().get(instructionIndex - 1);
                if (PrivacyBytecodeEngine.isInvoke(prev.getOpcode()) && prev instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) prev).getReference();
                    if (ref instanceof MethodReference) {
                        String ret = ((MethodReference) ref).getReturnType();
                        if (!"Ljava/lang/String;".equals(ret) && !ret.endsWith("String;")) {
                            return false;
                        }
                    }
                }
            }
            int reg = ((OneRegisterInstruction) inst).getRegisterA();
            mutable.replaceInstruction(instructionIndex, new BuilderInstruction21c(
                    Opcode.CONST_STRING,
                    reg,
                    new ImmutableStringReference("C")
            ));
            if (stats != null) stats.aiFeatureGatesNeutralized++;
            return true;
        } else if (op == Opcode.MOVE_RESULT && inst instanceof OneRegisterInstruction) {
            if (instructionIndex > 0) {
                Instruction prev = mutable.getInstructions().get(instructionIndex - 1);
                if (PrivacyBytecodeEngine.isInvoke(prev.getOpcode()) && prev instanceof ReferenceInstruction) {
                    Object ref = ((ReferenceInstruction) prev).getReference();
                    if (ref instanceof MethodReference) {
                        String ret = ((MethodReference) ref).getReturnType();
                        if (!"Z".equals(ret)) {
                            return false;
                        }
                    }
                }
            }
            int reg = ((OneRegisterInstruction) inst).getRegisterA();
            mutable.replaceInstruction(instructionIndex, PrivacyBytecodeEngine.createConstInt(reg, 0));
            if (stats != null) stats.aiFeatureGatesNeutralized++;
            return true;
        }

        return false;
    }

    // --- Helper Utilities ---

    public static boolean isMethodReferenced(DexReachabilityIndex reachabilityIndex, Method method) {
        if (reachabilityIndex == null || method == null) return false;
        return reachabilityIndex.isMethodInvoked(method)
                || reachabilityIndex.isMethodInvoked(method.getDefiningClass(), method.getName(), method.getReturnType());
    }

    public static int computeParameterRegisters(Method method) {
        if (method == null) return 0;
        int count = 0;
        if ((method.getAccessFlags() & AccessFlags.STATIC.getValue()) == 0) {
            count += 1; // 'this' register
        }
        List<? extends CharSequence> params = method.getParameters();
        if (params != null) {
            for (CharSequence param : params) {
                String type = param.toString();
                if ("J".equals(type) || "D".equals(type)) {
                    count += 2;
                } else {
                    count += 1;
                }
            }
        }
        return count;
    }

    public static List<Integer> getArgumentRegisters(Instruction inst) {
        List<Integer> regs = new ArrayList<>();
        if (inst instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction fri = (FiveRegisterInstruction) inst;
            int count = fri.getRegisterCount();
            if (count > 0) regs.add(fri.getRegisterC());
            if (count > 1) regs.add(fri.getRegisterD());
            if (count > 2) regs.add(fri.getRegisterE());
            if (count > 3) regs.add(fri.getRegisterF());
            if (count > 4) regs.add(fri.getRegisterG());
        } else if (inst instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction rri = (RegisterRangeInstruction) inst;
            int start = rri.getStartRegister();
            int count = rri.getRegisterCount();
            for (int i = 0; i < count; i++) {
                regs.add(start + i);
            }
        }
        return regs;
    }

    public static String resolveKeyString(List<BuilderInstruction> instructions, int invokeIdx, int keyReg, Map<Integer, String> stringRegisters) {
        if (stringRegisters != null && stringRegisters.containsKey(keyReg)) {
            return stringRegisters.get(keyReg);
        }
        if (instructions == null) return null;
        int start = Math.max(0, invokeIdx - 10);
        for (int j = invokeIdx - 1; j >= start; j--) {
            Instruction prev = instructions.get(j);
            Opcode op = prev.getOpcode();
            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                if (prev instanceof OneRegisterInstruction && prev instanceof ReferenceInstruction) {
                    if (((OneRegisterInstruction) prev).getRegisterA() == keyReg) {
                        Object ref = ((ReferenceInstruction) prev).getReference();
                        if (ref instanceof StringReference) {
                            return ((StringReference) ref).getString();
                        }
                    }
                }
            }
        }
        return null;
    }
}

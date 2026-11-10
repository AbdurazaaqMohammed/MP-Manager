package io.github.abdurazaaqmohammed.utils;

import android.util.Base64;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.VersionMap;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DexStringDecryptor {

    private DexStringDecryptor() {}

    public static int decryptDexStrings(File dexIn, File dexOut, String customMethodSignature) throws Exception {
        DexRepairEngine.repairHeader(dexIn);
        byte[] dexBytes = Files.readAllBytes(dexIn.toPath());
        int api = VersionMap.mapDexVersionToApi(HeaderItem.getVersion(dexBytes, 0));
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexIn, null);

        int totalDecrypted = 0;
        DexPool pool = new DexPool(dex.getOpcodes());

        for (ClassDef classDef : dex.getClasses()) {
            List<Method> directMethods = new ArrayList<>();
            List<Method> virtualMethods = new ArrayList<>();
            boolean classModified = false;

            for (Method m : classDef.getDirectMethods()) {
                MethodTransformationResult result = transformMethod(m, customMethodSignature);
                if (result.modified) {
                    classModified = true;
                    totalDecrypted += result.count;
                    directMethods.add(result.method);
                } else {
                    directMethods.add(m);
                }
            }

            for (Method m : classDef.getVirtualMethods()) {
                MethodTransformationResult result = transformMethod(m, customMethodSignature);
                if (result.modified) {
                    classModified = true;
                    totalDecrypted += result.count;
                    virtualMethods.add(result.method);
                } else {
                    virtualMethods.add(m);
                }
            }

            if (classModified) {
                ClassDef updated = new ImmutableClassDef(
                        classDef.getType(),
                        classDef.getAccessFlags(),
                        classDef.getSuperclass(),
                        classDef.getInterfaces(),
                        classDef.getSourceFile(),
                        classDef.getAnnotations(),
                        classDef.getStaticFields(),
                        classDef.getInstanceFields(),
                        directMethods,
                        virtualMethods
                );
                pool.internClass(DexDebugStripper.strip(updated));
            } else {
                pool.internClass(DexDebugStripper.strip(classDef));
            }
        }

        if (totalDecrypted > 0) {
            MemoryDataStore store = new MemoryDataStore();
            pool.writeTo(store);
            byte[] data = Arrays.copyOf(store.getData(), store.getSize());
            File parent = dexOut.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStream os = new FileOutputStream(dexOut)) {
                os.write(data);
            }
        }

        return totalDecrypted;
    }

    private static class MethodTransformationResult {
        final boolean modified;
        final int count;
        final Method method;

        MethodTransformationResult(boolean modified, int count, Method method) {
            this.modified = modified;
            this.count = count;
            this.method = method;
        }
    }

    private static MethodTransformationResult transformMethod(Method method, String customMethodSignature) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return new MethodTransformationResult(false, 0, method);
        }

        MutableMethodImplementation mutable = new MutableMethodImplementation(impl);
        List<BuilderInstruction> instructions = mutable.getInstructions();
        int count = 0;

        // Track constants held in registers (register index -> string value or integer key)
        Map<Integer, String> stringRegisters = new HashMap<>();
        Map<Integer, Integer> intRegisters = new HashMap<>();

        for (int i = 0; i < instructions.size(); i++) {
            Instruction inst = instructions.get(i);
            Opcode op = inst.getOpcode();

            // Track const-string
            if (op == Opcode.CONST_STRING || op == Opcode.CONST_STRING_JUMBO) {
                if (inst instanceof OneRegisterInstruction && inst instanceof ReferenceInstruction) {
                    int reg = ((OneRegisterInstruction) inst).getRegisterA();
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof StringReference) {
                        stringRegisters.put(reg, ((StringReference) ref).getString());
                    }
                }
            } else if (op == Opcode.CONST_4 || op == Opcode.CONST_16 || op == Opcode.CONST) {
                if (inst instanceof NarrowLiteralInstruction && inst instanceof OneRegisterInstruction) {
                    int reg = ((OneRegisterInstruction) inst).getRegisterA();
                    int val = ((NarrowLiteralInstruction) inst).getNarrowLiteral();
                    intRegisters.put(reg, val);
                }
            } else if (op == Opcode.MOVE_OBJECT || op == Opcode.MOVE_OBJECT_16 || op == Opcode.MOVE_OBJECT_FROM16) {
                if (inst instanceof TwoRegisterInstruction) {
                    int dest = ((TwoRegisterInstruction) inst).getRegisterA();
                    int src = ((TwoRegisterInstruction) inst).getRegisterB();
                    if (stringRegisters.containsKey(src)) {
                        stringRegisters.put(dest, stringRegisters.get(src));
                    }
                }
            } else if (op == Opcode.INVOKE_STATIC) {
                if (inst instanceof Instruction35c && inst instanceof ReferenceInstruction) {
                    Instruction35c inv = (Instruction35c) inst;
                    Object ref = ((ReferenceInstruction) inst).getReference();
                    if (ref instanceof MethodReference) {
                        MethodReference mRef = (MethodReference) ref;
                        String returnType = mRef.getReturnType();
                        List<? extends CharSequence> paramTypes = mRef.getParameterTypes();

                        boolean matchesCustom = customMethodSignature != null
                                && !customMethodSignature.isEmpty()
                                && (mRef.getName().equals(customMethodSignature)
                                || (mRef.getDefiningClass() + "->" + mRef.getName()).contains(customMethodSignature));

                        // Scenario 1: decrypt(String ciphertext) -> String
                        if ("Ljava/lang/String;".equals(returnType) && paramTypes.size() == 1
                                && "Ljava/lang/String;".equals(paramTypes.get(0).toString())) {
                            int argReg = inv.getRegisterC();
                            String ciphertext = stringRegisters.get(argReg);
                            if (ciphertext != null) {
                                String decrypted = attemptDecrypt(ciphertext, matchesCustom);
                                if (decrypted != null && i + 1 < instructions.size()) {
                                    Instruction next = instructions.get(i + 1);
                                    if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                        int resultReg = ((OneRegisterInstruction) next).getRegisterA();
                                        // Replace move-result-object with const-string resultReg, decrypted
                                        mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                                Opcode.CONST_STRING,
                                                resultReg,
                                                new ImmutableStringReference(decrypted)
                                        ));
                                        count++;
                                        stringRegisters.put(resultReg, decrypted);
                                    }
                                }
                            }
                        } else if ("Ljava/lang/String;".equals(returnType) && paramTypes.size() == 2
                                && "Ljava/lang/String;".equals(paramTypes.get(0).toString())
                                && "I".equals(paramTypes.get(1).toString())) {
                            // Scenario 2: decrypt(String ciphertext, int key) -> String
                            int strReg = inv.getRegisterC();
                            int keyReg = inv.getRegisterD();
                            String ciphertext = stringRegisters.get(strReg);
                            Integer key = intRegisters.get(keyReg);
                            if (ciphertext != null && key != null) {
                                String decrypted = xorDecrypt(ciphertext, key);
                                if (decrypted != null && i + 1 < instructions.size()) {
                                    Instruction next = instructions.get(i + 1);
                                    if (next.getOpcode() == Opcode.MOVE_RESULT_OBJECT && next instanceof OneRegisterInstruction) {
                                        int resultReg = ((OneRegisterInstruction) next).getRegisterA();
                                        mutable.replaceInstruction(i + 1, new BuilderInstruction21c(
                                                Opcode.CONST_STRING,
                                                resultReg,
                                                new ImmutableStringReference(decrypted)
                                        ));
                                        count++;
                                        stringRegisters.put(resultReg, decrypted);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (count > 0) {
            Method transformed = new ImmutableMethod(
                    method.getDefiningClass(),
                    method.getName(),
                    method.getParameters(),
                    method.getReturnType(),
                    method.getAccessFlags(),
                    method.getAnnotations(),
                    method.getHiddenApiRestrictions(),
                    mutable
            );
            return new MethodTransformationResult(true, count, transformed);
        }

        return new MethodTransformationResult(false, 0, method);
    }

    private static String attemptDecrypt(String ciphertext, boolean force) {
        if (ciphertext == null || ciphertext.isEmpty()) return null;

        // Try Base64 decoding
        try {
            byte[] b64 = Base64.decode(ciphertext, Base64.NO_WRAP);
            if (b64 != null && b64.length > 0) {
                String s = new String(b64, StandardCharsets.UTF_8);
                if (isPrintableAsciiOrUtf8(s)) return s;
            }
        } catch (Exception ignored) {}

        // Try XOR with common constants or string reverse if custom matched
        if (force) {
            // Rot13 / simple shift test
            char[] chars = ciphertext.toCharArray();
            for (int k = 1; k < 256; k++) {
                char[] candidate = new char[chars.length];
                for (int c = 0; c < chars.length; c++) candidate[c] = (char) (chars[c] ^ k);
                String s = new String(candidate);
                if (isPrintableAscii(s)) return s;
            }
        }
        return null;
    }

    private static String xorDecrypt(String ciphertext, int key) {
        if (ciphertext == null) return null;
        char[] chars = ciphertext.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (chars[i] ^ key);
        }
        return new String(chars);
    }

    private static boolean isPrintableAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 32 || c > 126) return false;
        }
        return true;
    }

    private static boolean isPrintableAsciiOrUtf8(String s) {
        int valid = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 || c == '\n' || c == '\r' || c == '\t') valid++;
        }
        return valid > (s.length() * 0.85);
    }
}

package io.github.abdurazaaqmohammed.utils;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.VersionMap;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import com.reandroid.apk.APKLogger;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fast dex patching in the style of DexPatcher: work on the binary dex model,
 * disassemble only the classes being changed, assemble only the changed/new
 * smali files, and merge everything back with DexPool.
 */
public final class FastDexPatch {

    private FastDexPatch() {
    }

    public static BaksmaliOptions defaultBaksmaliOptions() {
        BaksmaliOptions options = new BaksmaliOptions();
        options.parameterRegisters = true;
        options.localsDirective = true;
        return options;
    }

    public static byte[] readDexBytes(File apk, String entry) throws IOException {
        try (ZipFile zin = new ZipFile(apk)) {
            FileHeader header = zin.getFileHeader(entry);
            if (header == null) throw new IOException("Missing " + entry + " in " + apk.getName());
            try (InputStream is = zin.getInputStream(header);
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        }
    }

    public static Map<String, File> disassembleClasses(DexBackedDexFile dex, Set<String> descriptors,
                                                       File outDir, BaksmaliOptions options,
                                                       APKLogger logger) throws IOException {
        Map<String, File> result = new LinkedHashMap<>();
        for (ClassDef classDef : dex.getClasses()) {
            if (!descriptors.contains(classDef.getType())) continue;
            ClassDefinition def = new ClassDefinition(options, classDef);
            String relPath = classDef.getType().substring(1, classDef.getType().length() - 1) + ".smali";
            File out = new File(outDir, relPath);
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new java.io.FileOutputStream(out), StandardCharsets.UTF_8)) {
                BaksmaliWriter bw = new BaksmaliWriter(writer);
                def.writeTo(bw);
                bw.close();
            }
            result.put(classDef.getType(), out);
            if (logger != null) logger.logMessage("Disassembled " + classDef.getType());
        }
        return result;
    }

    public static File assembleMiniDex(File smaliDir, int api, APKLogger logger) throws IOException {
        File outDex = new File(smaliDir.getParentFile(), smaliDir.getName() + ".mini.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = api;
        if (logger != null) logger.logMessage("Assembling patched classes ...");
        String errOut = assembleWithErrCapture(options, smaliDir.getPath());
        if (errOut == null) {
            throw new IOException("Failed to assemble patched classes"
                    + (logger == null ? "" : " (see injector log)"));
        }
        return outDex;
    }

    public static byte[] mergeDex(DexBackedDexFile orig, File miniDexFile, int api) throws IOException {
        DexBackedDexFile mini = DexFileFactory.loadDexFile(miniDexFile, Opcodes.getDefault());
        Map<String, ClassDef> replacements = new HashMap<>();
        for (ClassDef c : mini.getClasses()) replacements.put(c.getType(), c);
        DexPool pool = new DexPool(Opcodes.forApi(api));
        for (ClassDef c : orig.getClasses()) {
            ClassDef r = replacements.remove(c.getType());
            pool.internClass(r != null ? r : c);
        }
        for (ClassDef c : replacements.values()) pool.internClass(c);
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        return Arrays.copyOf(store.getData(), store.getSize());
    }

    public static Set<String> findClassesWithMethodCalls(DexBackedDexFile dex, String definingClass,
                                                         Set<String> methodNames) {
        Set<String> result = new LinkedHashSet<>();
        for (ClassDef classDef : dex.getClasses()) {
            if (referencesMethods(classDef, definingClass, methodNames)) result.add(classDef.getType());
        }
        return result;
    }

    private static boolean referencesMethods(ClassDef classDef, String definingClass, Set<String> methodNames) {
        List<Iterable<? extends Method>> groups = new ArrayList<>(2);
        groups.add(classDef.getDirectMethods());
        groups.add(classDef.getVirtualMethods());
        for (Iterable<? extends Method> group : groups) {
            for (Method method : group) {
                MethodImplementation impl = method.getImplementation();
                if (impl == null) continue;
                for (Instruction instruction : impl.getInstructions()) {
                    if (!(instruction instanceof ReferenceInstruction)) continue;
                    Opcode opcode = instruction.getOpcode();
                    if (!isInvoke(opcode)) continue;
                    Reference ref = ((ReferenceInstruction) instruction).getReference();
                    if (!(ref instanceof MethodReference mr)) continue;
                    if (definingClass.equals(mr.getDefiningClass()) && methodNames.contains(mr.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isInvoke(Opcode opcode) {
        return switch (opcode) {
            case INVOKE_VIRTUAL, INVOKE_SUPER, INVOKE_DIRECT, INVOKE_STATIC, INVOKE_INTERFACE,
                 INVOKE_VIRTUAL_RANGE, INVOKE_SUPER_RANGE, INVOKE_DIRECT_RANGE, INVOKE_STATIC_RANGE,
                 INVOKE_INTERFACE_RANGE -> true;
            default -> false;
        };
    }

    private static String assembleWithErrCapture(SmaliOptions options, String input) throws IOException {
        java.io.PrintStream oldErr = System.err;
        java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream();
        java.io.PrintStream capture;
        try {
            capture = new java.io.PrintStream(errBuf, true, "UTF-8");
        } catch (Exception e) {
            capture = new java.io.PrintStream(errBuf);
        }
        System.setErr(capture);
        boolean ok;
        try {
            ok = Smali.assemble(options, input);
        } finally {
            try {
                capture.flush();
            } catch (Exception ignored) {
            }
            System.setErr(oldErr);
        }
        String captured = "";
        try {
            captured = errBuf.toString("UTF-8");
        } catch (Exception ignored) {
        }
        return ok ? captured : null;
    }

    public static String descriptorToClassName(String descriptor) {
        String s = descriptor;
        if (s.startsWith("L")) s = s.substring(1);
        if (s.endsWith(";")) s = s.substring(0, s.length() - 1);
        return s.replace('/', '.');
    }

    public static int detectDexApi(File inputApk, String entryName) {
        try (ZipFile zin = new ZipFile(inputApk)) {
            FileHeader header = zin.getFileHeader(entryName);
            if (header == null) return 28;
            try (InputStream is = zin.getInputStream(header)) {
                byte[] magic = new byte[8];
                int read = 0;
                while (read < 8) {
                    int n = is.read(magic, read, 8 - read);
                    if (n < 0) break;
                    read += n;
                }
                String m = new String(magic, 0, read, StandardCharsets.US_ASCII);
                if (m.startsWith("dex\n") && m.length() >= 7) {
                    int ver = Integer.parseInt(m.substring(4, 7));
                    int api = VersionMap.mapDexVersionToApi(ver);
                    if (api > 0) return api;
                }
            }
        } catch (Exception ignored) {
        }
        return 28;
    }
}

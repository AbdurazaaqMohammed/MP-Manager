package io.github.abdurazaaqmohammed.utils;

import android.content.Context;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.MPManager.R;

/**
 * MT's "inject logging": drop print statements into chosen methods so a running APK writes its
 * arguments and return values to a file for reverse engineering.
 *
 * <p>Everything runs on baksmali text instead of raw dexlib2 instructions - the smali assembler
 * validates the result while it builds, so a bad injection fails loudly before the APK is touched
 * instead of producing a class the verifier rejects at runtime.
 *
 * <p>Two registers are appended to every patched method's {@code .locals} count and used as
 * scratch. They are always fresh, so they can never collide with the registers the method already
 * used (parameters shift automatically because smali addresses them as {@code p0}, {@code p1}, ...).
 */
public final class MethodLoggerInjector {

    /** Class injected into the target APK that performs the actual file/log write. */
    public static final String HELPER_TYPE = "Lmpspy/MpSpy;";
    private static final String HELPER_PATH = "mpspy/MpSpy.smali";

    /** Wildcard blow-up guard: patching every method of a large app is never what you want. */
    private static final int MAX_METHODS = 3000;

    private static final Pattern METHOD_RE = Pattern.compile(
            "^\\s*\\.method\\s+(.+?)\\s+([\\w$<>\\-]+)\\(([^)]*)\\)(\\S+)\\s*$");
    private static final Pattern LOCALS_RE = Pattern.compile(
            "^(\\s*)\\.(locals|registers)\\s+(\\d+)(.*)$");
    private static final Pattern RETURN_RE = Pattern.compile(
            "^return(?:-void|-wide|-object)?\\s*(\\S+)?\\s*$");

    private MethodLoggerInjector() {
    }

    /**
     * Rewrites {@code inputApk} with logging calls injected into every method matching the two
     * wildcard patterns and returns the new APK.
     *
     * @param classPattern  simple name or {@code *} glob, empty means every class
     * @param methodPattern method name, optionally {@code name(descriptor)}, empty means every method
     */
    public static File inject(Context context, File inputApk, String classPattern,
                              String methodPattern, APKLogger logger) throws Exception {
        String packageName = packageNameOf(inputApk);
        String logPath = "/data/data/" + packageName + "/files/mp_spy.log";
        if (logger != null) {
            logger.logMessage(context.getString(R.string.logger_spy_path, logPath));
        }

        String namePattern = methodPattern == null ? "" : methodPattern.trim();
        String descriptorPattern = "";
        int paren = namePattern.indexOf('(');
        if (paren >= 0) {
            descriptorPattern = namePattern.substring(paren);
            namePattern = namePattern.substring(0, paren);
        }

        File workDir = createTempDir(context, "spy");
        try {
            MultiDexContainer<? extends DexBackedDexFile> container =
                    DexFileFactory.loadDexContainer(inputApk, Opcodes.getDefault());

            // Count up front on the dex model: a wildcard left blank would otherwise only be
            // rejected after every matching class had already been disassembled.
            int matching = 0;
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
                if (entry == null) continue;
                matching += countMatchingMethods(entry.getDexFile(), classPattern,
                        namePattern, descriptorPattern);
            }
            if (matching == 0) {
                throw new IOException(context.getString(R.string.logger_no_methods));
            }
            if (matching > MAX_METHODS) {
                throw new IOException(context.getString(R.string.logger_too_many, matching, MAX_METHODS));
            }

            Map<String, File> replacements = new LinkedHashMap<>();
            int totalPatched = 0;
            for (String entryName : container.getDexEntryNames()) {
                MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
                if (entry == null) continue;
                DexBackedDexFile dex = entry.getDexFile();
                Set<String> targets = findTargetClasses(dex, classPattern, namePattern);
                if (targets.isEmpty()) continue;

                int api = FastDexPatch.detectDexApi(inputApk, entryName);
                File patchDir = new File(workDir,
                        "spy_" + entryName.replaceAll("[^A-Za-z0-9]", "_"));
                if (!patchDir.mkdirs() && !patchDir.isDirectory()) {
                    throw new IOException("Could not create " + patchDir);
                }
                if (logger != null) {
                    logger.logMessage(context.getString(R.string.logger_patching, entryName));
                }
                Map<String, File> smaliFiles = FastDexPatch.disassembleClasses(
                        context, dex, targets, patchDir, FastDexPatch.defaultBaksmaliOptions(), logger);
                writeHelperSmali(patchDir, logPath);

                int before = totalPatched;
                for (File smaliFile : smaliFiles.values()) {
                    String content = readFile(smaliFile);
                    AtomicInteger patched = new AtomicInteger();
                    String edited = patchClass(content, namePattern, descriptorPattern, patched);
                    if (patched.get() > 0) {
                        writeFile(smaliFile, edited);
                        totalPatched += patched.get();
                    }
                }
                if (totalPatched == before) continue;

                File miniDex = FastDexPatch.assembleMiniDex(context, patchDir, api, logger);
                byte[] merged = FastDexPatch.mergeDex(dex, miniDex, api);
                File mergedFile = new File(workDir, entryName + ".merged");
                try (FileOutputStream fos = new FileOutputStream(mergedFile)) {
                    fos.write(merged);
                }
                replacements.put(entryName, mergedFile);
            }

            if (replacements.isEmpty() || totalPatched == 0) {
                throw new IOException(context.getString(R.string.logger_no_methods));
            }
            if (totalPatched > MAX_METHODS) {
                throw new IOException(context.getString(R.string.logger_too_many, totalPatched, MAX_METHODS));
            }
            if (logger != null) {
                logger.logMessage(context.getString(R.string.logger_injected_methods, totalPatched));
            }
            File outputFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                    FilenameUtils.getBaseName(inputApk.getName()) + "_spy.apk"));
            ApkZipAlignUtil.rebuildApk(inputApk, outputFile, replacements, null, null, null);
            if (logger != null) {
                logger.logMessage(context.getString(R.string.logger_saved_to, outputFile.getName()));
            }
            return outputFile;
        } finally {
            deleteDirectory(workDir);
        }
    }

    // ------------------------------------------------------------------ target lookup

    private static Set<String> findTargetClasses(DexBackedDexFile dex, String classPattern,
                                                 String methodPattern) {
        Set<String> result = new LinkedHashSet<>();
        for (ClassDef classDef : dex.getClasses()) {
            String className = FastDexPatch.descriptorToClassName(classDef.getType());
            if (!glob(className, classPattern)) continue;
            for (Method method : classDef.getMethods()) {
                if (glob(method.getName(), methodPattern)) {
                    result.add(classDef.getType());
                    break;
                }
            }
        }
        return result;
    }

    /** Counts what {@link #patchClass} would actually rewrite, abstract/native bodies excluded. */
    private static int countMatchingMethods(DexBackedDexFile dex, String classPattern,
                                            String namePattern, String descriptorPattern) {
        int count = 0;
        for (ClassDef classDef : dex.getClasses()) {
            if (!glob(FastDexPatch.descriptorToClassName(classDef.getType()), classPattern)) continue;
            for (Method method : classDef.getMethods()) {
                if (method.getImplementation() == null) continue;
                if (!glob(method.getName(), namePattern)) continue;
                if (!descriptorPattern.isEmpty()) {
                    StringBuilder descriptor = new StringBuilder("(");
                    for (MethodParameter parameter : method.getParameters()) {
                        descriptor.append(parameter.getType());
                    }
                    descriptor.append(')').append(method.getReturnType());
                    if (!descriptorPattern.contentEquals(descriptor)) continue;
                }
                count++;
            }
        }
        return count;
    }

    /** Simple glob: {@code *} matches anything, every other character is literal. */
    private static boolean glob(String value, String pattern) {
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) return true;
        if (pattern.indexOf('*') < 0) return value.equals(pattern);
        StringBuilder regex = new StringBuilder();
        String[] parts = pattern.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) regex.append(".*");
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.matches(regex.toString(), value);
    }

    // ------------------------------------------------------------------ smali rewriting

    private static String patchClass(String content, String namePattern, String descriptorPattern,
                                     AtomicInteger counter) {
        String[] lines = content.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length + 64);
        int i = 0;
        while (i < lines.length) {
            Matcher matcher = METHOD_RE.matcher(lines[i]);
            if (!matcher.find()) {
                out.add(lines[i]);
                i++;
                continue;
            }
            String access = matcher.group(1).trim();
            String name = matcher.group(2);
            String params = matcher.group(3);
            String returnType = matcher.group(4);
            int start = i;
            int end = i;
            while (end < lines.length && !lines[end].trim().equals(".end method")) end++;
            if (end >= lines.length) {
                out.addAll(Arrays.asList(lines).subList(start, lines.length));
                break;
            }
            boolean hasBody = access.indexOf("abstract") < 0 && access.indexOf("native") < 0;
            boolean wanted = hasBody
                    && glob(name, namePattern)
                    && (descriptorPattern.isEmpty()
                    || ("(" + params + ")" + returnType).equals(descriptorPattern));
            if (wanted) {
                out.addAll(patchMethod(Arrays.copyOfRange(lines, start, end + 1),
                        access, name, params, returnType));
                counter.incrementAndGet();
            } else {
                out.addAll(Arrays.asList(lines).subList(start, end + 1));
            }
            i = end + 1;
        }
        return String.join("\n", out);
    }

    private static List<String> patchMethod(String[] block, String access, String name,
                                            String params, String returnType) {
        boolean isStatic = access.indexOf("static") >= 0;
        int localsIndex = -1;
        int localsValue = 0;
        String localsDirective = ".locals";
        for (int i = 0; i < block.length; i++) {
            Matcher matcher = LOCALS_RE.matcher(block[i]);
            if (matcher.find()) {
                localsIndex = i;
                localsValue = Integer.parseInt(matcher.group(3));
                localsDirective = "." + matcher.group(2);
                break;
            }
        }
        if (localsIndex < 0) return Arrays.asList(block);

        List<String> parameterTypes = splitParameters(params);
        int parameterRegisters = isStatic ? 0 : 1;
        for (String type : parameterTypes) {
            parameterRegisters += ("J".equals(type) || "D".equals(type)) ? 2 : 1;
        }
        // `.registers` counts the parameters as well, so the two fresh slots have to sit below
        // them; with `.locals` they simply follow the declared locals. Either way the slots are
        // ones the method never wrote itself.
        int localsCount = ".registers".equals(localsDirective)
                ? localsValue - parameterRegisters : localsValue;
        if (localsCount < 0) return Arrays.asList(block);

        String scratch1 = "v" + localsCount;
        String scratch2 = "v" + (localsCount + 1);

        int insertAt = -1;
        for (int i = localsIndex + 1; i < block.length - 1; i++) {
            String trimmed = block[i].trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")
                    || trimmed.startsWith(".") || trimmed.startsWith(":")) continue;
            insertAt = i;
            break;
        }
        if (insertAt < 0) insertAt = block.length - 1;

        boolean printThis = !isStatic;
        if ("<init>".equals(name)) {
            // Until super()/this() returns, p0 is still uninitializedThis and the verifier rejects
            // it as an argument, so the head has to move below that call.
            boolean afterSuper = false;
            for (int i = localsIndex + 1; i < block.length - 1; i++) {
                String trimmed = block[i].trim();
                if (trimmed.startsWith("invoke-direct") && trimmed.contains("-><init>(")) {
                    insertAt = i + 1;
                    afterSuper = true;
                    break;
                }
            }
            if (!afterSuper) printThis = false;
        }

        List<String> head = buildHead(name, isStatic, printThis, params, scratch1, scratch2);
        List<String> out = new ArrayList<>(block.length + head.size() + 16);
        boolean headAdded = false;
        for (int i = 0; i < block.length; i++) {
            if (i == localsIndex) {
                Matcher matcher = LOCALS_RE.matcher(block[i]);
                matcher.find();
                out.add(matcher.group(1) + localsDirective + " "
                        + (localsValue + 2) + matcher.group(4));
                continue;
            }
            if (i == insertAt && !headAdded) {
                out.addAll(head);
                headAdded = true;
            }
            if (i < block.length - 1 && RETURN_RE.matcher(block[i].trim()).find()) {
                out.addAll(buildReturn(block[i], returnType, scratch1, scratch2));
            }
            out.add(block[i]);
        }
        return out;
    }

    private static List<String> buildHead(String name, boolean isStatic, boolean printThis,
                                          String params, String scratch1, String scratch2) {
        List<String> head = new ArrayList<>();
        head.add("    const-string " + scratch1 + ", \"" + name + "\"");
        head.add("    invoke-static {" + scratch1 + "}, " + HELPER_TYPE
                + "->log(Ljava/lang/String;)V");

        int register = 0;
        if (!isStatic) {
            if (printThis) {
                head.add("    const-string " + scratch1 + ", \"  this=\"");
                head.add("    invoke-static {" + scratch1 + ", p0}, " + HELPER_TYPE
                        + "->p(Ljava/lang/String;Ljava/lang/Object;)V");
            }
            register = 1;
        }
        List<String> parameterTypes = splitParameters(params);
        for (int i = 0; i < parameterTypes.size(); i++) {
            String type = parameterTypes.get(i);
            String source = "p" + register;
            head.add("    const-string " + scratch1 + ", \"  arg" + i + "=\"");
            if (isPrimitive(type)) {
                head.add("    invoke-static {" + source + "}, " + boxOf(type));
                head.add("    move-result-object " + scratch2);
                head.add("    invoke-static {" + scratch1 + ", " + scratch2 + "}, " + HELPER_TYPE
                        + "->p(Ljava/lang/String;Ljava/lang/Object;)V");
            } else {
                head.add("    invoke-static {" + scratch1 + ", " + source + "}, " + HELPER_TYPE
                        + "->p(Ljava/lang/String;Ljava/lang/Object;)V");
            }
            register += ("J".equals(type) || "D".equals(type)) ? 2 : 1;
        }
        return head;
    }

    private static List<String> buildReturn(String line, String returnType,
                                            String scratch1, String scratch2) {
        String trimmed = line.trim();
        if (trimmed.startsWith("return-void")) {
            return Arrays.asList(
                    "    const-string " + scratch1 + ", \"  <- void\"",
                    "    invoke-static {" + scratch1 + "}, " + HELPER_TYPE + "->log(Ljava/lang/String;)V");
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2 || parts[1].startsWith("{")) return Collections.emptyList();
        String source = parts[1];
        List<String> result = new ArrayList<>(4);
        result.add("    const-string " + scratch1 + ", \"  <- \"");
        if (isPrimitive(returnType)) {
            result.add("    invoke-static {" + source + "}, " + boxOf(returnType));
            result.add("    move-result-object " + scratch2);
            result.add("    invoke-static {" + scratch1 + ", " + scratch2 + "}, " + HELPER_TYPE
                    + "->p(Ljava/lang/String;Ljava/lang/Object;)V");
        } else {
            result.add("    invoke-static {" + scratch1 + ", " + source + "}, " + HELPER_TYPE
                    + "->p(Ljava/lang/String;Ljava/lang/Object;)V");
        }
        return result;
    }

    /** Splits {@code ILjava/lang/String;J} into {@code [I, Ljava/lang/String;, J]}. */
    private static List<String> splitParameters(String params) {
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < params.length()) {
            int start = i;
            while (i < params.length() && params.charAt(i) == '[') i++;
            if (i >= params.length()) break;
            if (params.charAt(i) == 'L') {
                i = params.indexOf(';', i);
                if (i < 0) break;
                i++;
            } else {
                i++;
            }
            result.add(params.substring(start, i));
        }
        return result;
    }

    private static boolean isPrimitive(String type) {
        switch (type) {
            case "Z": case "B": case "S": case "C":
            case "I": case "J": case "F": case "D":
                return true;
            default:
                return false;
        }
    }

    private static String boxOf(String type) {
        switch (type) {
            case "Z": return "Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;";
            case "B": return "Ljava/lang/Byte;->valueOf(B)Ljava/lang/Byte;";
            case "S": return "Ljava/lang/Short;->valueOf(S)Ljava/lang/Short;";
            case "C": return "Ljava/lang/Character;->valueOf(C)Ljava/lang/Character;";
            case "I": return "Ljava/lang/Integer;->valueOf(I)Ljava/lang/Integer;";
            case "J": return "Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;";
            case "F": return "Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;";
            case "D": return "Ljava/lang/Double;->valueOf(D)Ljava/lang/Double;";
            default: return "Ljava/lang/String;->valueOf(Ljava/lang/Object;)Ljava/lang/String;";
        }
    }

    // ------------------------------------------------------------------ helper class

    private static void writeHelperSmali(File patchDir, String logPath) throws IOException {
        File target = new File(patchDir, HELPER_PATH);
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        writeFile(target, helperSmali(logPath));
    }

    /**
     * The class that does the writing is itself injected, so the patched APK needs no runtime
     * dependency. Every write is wrapped in a catch: a full disk or a denied path must never make
     * the host app crash just because it is being traced.
     */
    private static String helperSmali(String logPath) {
        return ""
                + ".class public final Lmpspy/MpSpy;\n"
                + ".super Ljava/lang/Object;\n"
                + "\n"
                + ".method public constructor <init>()V\n"
                + "    .locals 0\n"
                + "    invoke-direct {p0}, Ljava/lang/Object;-><init>()V\n"
                + "    return-void\n"
                + ".end method\n"
                + "\n"
                + ".method public static log(Ljava/lang/String;)V\n"
                + "    .locals 3\n"
                + "    :try_start_0\n"
                + "    const-string v0, \"MpSpy\"\n"
                + "    invoke-static {v0, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I\n"
                + "    new-instance v0, Ljava/io/FileWriter;\n"
                + "    const-string v1, \"" + logPath + "\"\n"
                + "    const/4 v2, 0x1\n"
                + "    invoke-direct {v0, v1, v2}, Ljava/io/FileWriter;-><init>(Ljava/lang/String;Z)V\n"
                + "    invoke-virtual {v0, p0}, Ljava/io/FileWriter;->write(Ljava/lang/String;)V\n"
                + "    const-string v1, \"\\n\"\n"
                + "    invoke-virtual {v0, v1}, Ljava/io/FileWriter;->write(Ljava/lang/String;)V\n"
                + "    invoke-virtual {v0}, Ljava/io/FileWriter;->flush()V\n"
                + "    invoke-virtual {v0}, Ljava/io/FileWriter;->close()V\n"
                + "    :try_end_0\n"
                + "    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0\n"
                + "    return-void\n"
                + "\n"
                + "    :catch_0\n"
                + "    move-exception v0\n"
                + "    return-void\n"
                + ".end method\n"
                + "\n"
                + ".method public static p(Ljava/lang/String;Ljava/lang/Object;)V\n"
                + "    .locals 3\n"
                + "    :try_start_1\n"
                + "    new-instance v0, Ljava/lang/StringBuilder;\n"
                + "    invoke-direct {v0}, Ljava/lang/StringBuilder;-><init>()V\n"
                + "    invoke-virtual {v0, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;\n"
                + "    move-result-object v0\n"
                + "    invoke-virtual {v0, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;\n"
                + "    move-result-object v0\n"
                + "    invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;\n"
                + "    move-result-object v0\n"
                + "    invoke-static {v0}, Lmpspy/MpSpy;->log(Ljava/lang/String;)V\n"
                + "    :try_end_1\n"
                + "    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1\n"
                + "    return-void\n"
                + "\n"
                + "    :catch_1\n"
                + "    move-exception v0\n"
                + "    return-void\n"
                + ".end method\n";
    }

    // ------------------------------------------------------------------ plumbing

    private static String packageNameOf(File apk) throws IOException {
        try {
            ApkModule module = ApkModule.loadApkFile(apk);
            String packageName = module.getPackageName();
            if (packageName != null && !packageName.isEmpty()) return packageName;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Could not read package name", e);
        }
        throw new IOException("Could not read package name");
    }

    private static File createTempDir(Context context, String prefix) {
        File dir = new File(context.getCacheDir(), prefix + "_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    private static String readFile(File file) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int length;
            while ((length = reader.read(buffer)) != -1) builder.append(buffer, 0, length);
        }
        return builder.toString();
    }

    private static void writeFile(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
        }
    }

    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) deleteDirectory(file);
                else //noinspection ResultOfMethodCallIgnored
                    file.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }
}

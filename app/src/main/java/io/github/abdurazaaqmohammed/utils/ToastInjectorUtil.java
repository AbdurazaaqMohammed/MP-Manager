package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Xml;

import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import com.reandroid.apk.APKLogger;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.apache.commons.io.FilenameUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ToastInjectorUtil {

    private static final String TOAST_METHOD_NAME = "showMPManagerToast";
    private static final String ON_CREATE_SIG = "(Landroid/os/Bundle;)V";
    private static final ImmutableMethodReference TOAST_MAKE_TEXT = new ImmutableMethodReference(
            "Landroid/widget/Toast;", "makeText",
            java.util.Arrays.asList("Landroid/content/Context;", "Ljava/lang/CharSequence;", "I"),
            "Landroid/widget/Toast;");
    private static final ImmutableMethodReference TOAST_SHOW = new ImmutableMethodReference(
            "Landroid/widget/Toast;", "show", java.util.Collections.emptyList(), "V");

    public static File addToastToActivities(Context context, File inputApk,
                                            List<String> activityClassNames, String toastMessage,
                                            APKLogger logger) throws Exception {
        Set<String> targetEntries = findDexEntriesForClasses(inputApk, activityClassNames, logger);
        if (targetEntries.isEmpty()) {
            throw new IOException("Could not locate the selected activities in any dex file");
        }
        Map<String, byte[]> patchedDexBytes = new LinkedHashMap<>();
        for (String entryName : targetEntries) {
            if (logger != null) logger.logMessage("Patching " + entryName + " ...");
            byte[] patched = patchDexEntryForToast(inputApk, entryName, activityClassNames, toastMessage, logger);
            if (patched != null) patchedDexBytes.put(entryName, patched);
        }
        if (patchedDexBytes.isEmpty()) {
            throw new IOException("Could not patch any dex file");
        }
        File workDir = createTempDir(context, "add_toast");
        try {
            Map<String, File> assembledDexFiles = new LinkedHashMap<>();
            for (Map.Entry<String, byte[]> entry : patchedDexBytes.entrySet()) {
                File outputDex = new File(workDir, entry.getKey());
                try (FileOutputStream fos = new FileOutputStream(outputDex)) {
                    fos.write(entry.getValue());
                }
                assembledDexFiles.put(entry.getKey(), outputDex);
            }
            return buildOutputApk(inputApk, assembledDexFiles, "_toast", logger);
        } finally {
            deleteDirectory(workDir);
        }
    }

    private static byte[] patchDexEntryForToast(File inputApk, String entryName,
                                                List<String> classNames, String toastMessage,
                                                APKLogger logger) throws Exception {
        byte[] dexBytes = readZipEntry(inputApk, entryName);
        Opcodes opcodes = Opcodes.getDefault();
        DexBackedDexFile dexFile = new DexBackedDexFile(opcodes, dexBytes);

        Set<String> classDescriptors = new LinkedHashSet<>();
        for (String className : classNames) classDescriptors.add("L" + className.replace('.', '/') + ";");

        List<ClassDef> newClasses = new ArrayList<>();
        boolean modified = false;
        for (ClassDef classDef : dexFile.getClasses()) {
            if (classDescriptors.contains(classDef.getType())) {
                classDef = patchActivityClass(classDef, toastMessage);
                modified = true;
                if (logger != null) logger.logMessage("Injected toast into " + classDef.getType());
            }
            newClasses.add(classDef);
        }
        if (!modified) return null;

        MemoryDataStore store = new MemoryDataStore();
        DexPool dexPool = new DexPool(opcodes);
        for (ClassDef c : newClasses) dexPool.internClass(c);
        dexPool.writeTo(store);
        return Arrays.copyOf(store.getData(), store.getSize());
    }

    private static ClassDef patchActivityClass(ClassDef classDef, String toastMessage) {
        String classDescriptor = classDef.getType();
        List<Method> directMethods = new ArrayList<>();
        List<Method> virtualMethods = new ArrayList<>();
        boolean found = false;

        for (Method m : classDef.getDirectMethods()) {
            if (isOnCreate(m)) {
                // Rare: private onCreate. Keep wrapper + renamed both direct.
                directMethods.add(buildRenamedOnCreate(m));
                directMethods.add(buildWrapperOnCreate(m, classDescriptor));
                found = true;
            } else {
                directMethods.add(m);
            }
        }
        for (Method m : classDef.getVirtualMethods()) {
            if (isOnCreate(m)) {
                // Normal case: renamed goes to direct (private), wrapper stays virtual.
                directMethods.add(buildRenamedOnCreate(m));
                virtualMethods.add(buildWrapperOnCreate(m, classDescriptor));
                found = true;
            } else {
                virtualMethods.add(m);
            }
        }

        if (!found) return classDef;

        directMethods.add(buildToastMethod(classDescriptor, toastMessage));

        return new ImmutableClassDef(
                classDescriptor,
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
    }

    private static boolean isOnCreate(Method m) {
        return "onCreate".equals(m.getName()) && ON_CREATE_SIG.equals(getMethodDescriptor(m));
    }

    private static String getMethodDescriptor(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (MethodParameter param : m.getParameters()) sb.append(param.getType());
        sb.append(')');
        sb.append(m.getReturnType());
        return sb.toString();
    }

    private static Method buildWrapperOnCreate(Method original, String classDescriptor) {
        int regCount = 2;
        int p0 = 0;
        int p1 = 1;

        ImmutableMethodReference helperRef = new ImmutableMethodReference(
                classDescriptor, TOAST_METHOD_NAME, Collections.emptyList(), "V");
        ImmutableMethodReference renamedRef = new ImmutableMethodReference(
                classDescriptor, "onCreate$mpmanager",
                Collections.singletonList("Landroid/os/Bundle;"), "V");

        List<Instruction> instructions = new ArrayList<>();
        instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 1, p0, 0, 0, 0, 0, helperRef));
        instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_DIRECT, 2, p0, p1, 0, 0, 0, renamedRef));
        instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));

        ImmutableMethodImplementation impl = new ImmutableMethodImplementation(regCount, instructions, null, null);
        return new ImmutableMethod(
                classDescriptor,
                "onCreate",
                original.getParameters(),
                original.getReturnType(),
                original.getAccessFlags(),
                original.getAnnotations(),
                original.getHiddenApiRestrictions(),
                impl
        );
    }

    private static Method buildRenamedOnCreate(Method original) {
        return new ImmutableMethod(
                original.getDefiningClass(),
                "onCreate$mpmanager",
                original.getParameters(),
                original.getReturnType(),
                AccessFlags.PRIVATE.getValue(),
                original.getAnnotations(),
                original.getHiddenApiRestrictions(),
                ImmutableMethodImplementation.of(original.getImplementation())
        );
    }

    private static Method buildToastMethod(String classDescriptor, String toastMessage) {
        // instance method with no params: p0 = this at register 2, locals v0=0, v1=1
        int regCount = 3;
        int p0 = 2, v0 = 0, v1 = 1;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(new ImmutableInstruction21c(Opcode.CONST_STRING, v0, new ImmutableStringReference(toastMessage)));
        instructions.add(new ImmutableInstruction11n(Opcode.CONST_4, v1, 1));
        instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 3, p0, v0, v1, 0, 0, TOAST_MAKE_TEXT));
        instructions.add(new ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, v0));
        instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_VIRTUAL, 1, v0, 0, 0, 0, 0, TOAST_SHOW));
        instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));

        ImmutableMethodImplementation impl = new ImmutableMethodImplementation(regCount, instructions, null, null);
        return new ImmutableMethod(
                classDescriptor,
                TOAST_METHOD_NAME,
                Collections.emptyList(),
                "V",
                AccessFlags.PRIVATE.getValue(),
                Collections.emptySet(),
                Collections.emptySet(),
                impl
        );
    }

    private static byte[] readZipEntry(File zipFile, String entryName) throws IOException {
        try (ZipFile zin = new ZipFile(zipFile)) {
            FileHeader header = zin.getFileHeader(entryName);
            try (InputStream is = zin.getInputStream(header)) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                return baos.toByteArray();
            }
        }
    }

    public static File removeAllToasts(Context context, File inputApk, APKLogger logger) throws Exception {
        File workDir = createTempDir(context, "remove_toast");
        try {
            Map<String, File> dexEntryToSmaliDir = disassembleApk(inputApk, workDir, null, logger);
            Set<String> modifiedEntries = removeAllToastCalls(workDir, logger);
            Map<String, File> assembledDexFiles = assembleDexFiles(workDir, dexEntryToSmaliDir, modifiedEntries, logger);
            return buildOutputApk(inputApk, assembledDexFiles, "_no_toast", logger);
        } finally {
            deleteDirectory(workDir);
        }
    }

    public static List<ActivityInfo> getActivities(Context context, String apkPath) {
        PackageInfo packageInfo = context.getPackageManager().getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
        if (packageInfo != null && packageInfo.activities != null) {
            return new ArrayList<>(Arrays.asList(packageInfo.activities));
        }
        return new ArrayList<>();
    }

    public static String findMainLauncherActivity(Context context, String apkPath, String packageName) {
        String manifestXml = readManifestXml(context, apkPath);
        if (TextUtils.isEmpty(manifestXml)) return null;
        return parseMainLauncherActivity(manifestXml, packageName);
    }

    private static File createTempDir(Context context, String prefix) {
        File dir = new File(context.getCacheDir(), prefix + "_" + System.currentTimeMillis());
        dir.mkdirs();
        return dir;
    }

    private static Set<String> findDexEntriesForClasses(File inputApk,
                                                        List<String> classNames, APKLogger logger) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        if (classNames == null || classNames.isEmpty()) return result;
        Opcodes opcodes =  Opcodes.getDefault();
        MultiDexContainer<? extends DexBackedDexFile> container = DexFileFactory.loadDexContainer(inputApk, opcodes);
        Set<String> classDescriptors = new LinkedHashSet<>();
        for (String className : classNames) {
            classDescriptors.add("L" + className.replace('.', '/') + ";");
        }
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entryName);
            if (dexEntry == null) continue;
            for (ClassDef classDef : dexEntry.getDexFile().getClasses()) {
                if (classDescriptors.contains(classDef.getType())) {
                    result.add(entryName);
                    break;
                }
            }
        }
        if (logger != null) logger.logMessage("Target classes are in " + result.size() + " dex file(s)");
        return result;
    }

    private static Map<String, File> disassembleApk(File inputApk, File workDir, Set<String> entriesToDisassemble, APKLogger logger) throws Exception {
        Map<String, File> result = new LinkedHashMap<>();
        Opcodes opcodes =Opcodes.getDefault();
        MultiDexContainer<? extends DexBackedDexFile> container = DexFileFactory.loadDexContainer(inputApk, opcodes);
        List<String> entryNames = container.getDexEntryNames();
        int jobs = Runtime.getRuntime().availableProcessors();
        int disassembleCount = 0;
        for (String entryName : entryNames) {
            if (entriesToDisassemble != null && !entriesToDisassemble.contains(entryName)) continue;
            File smaliDir = smaliDirForEntry(workDir, entryName);
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> dexEntry = container.getEntry(entryName);
            if (dexEntry == null) continue;
            disassembleCount++;
            if (logger != null) logger.logMessage("Disassembling " + entryName + " ...");
            BaksmaliOptions options = new BaksmaliOptions();
            options.parameterRegisters = true;
            options.localsDirective = true;
            if (!Baksmali.disassembleDexFile(dexEntry.getDexFile(), smaliDir, jobs, options)) {
                throw new IOException("Failed to disassemble " + entryName);
            }
            result.put(entryName, smaliDir);
        }
        if (logger != null) logger.logMessage("Disassembled " + disassembleCount + " of " + entryNames.size() + " dex file(s)");
        return result;
    }

    private static Map<String, File> assembleDexFiles(File workDir, Map<String, File> dexEntryToSmaliDir,
                                                        Set<String> entriesToAssemble,
                                                      APKLogger logger) throws Exception {
        Map<String, File> result = new LinkedHashMap<>();
        int jobs = Runtime.getRuntime().availableProcessors();
        for (Map.Entry<String, File> entry : dexEntryToSmaliDir.entrySet()) {
            String entryName = entry.getKey();
            if (entriesToAssemble != null && !entriesToAssemble.contains(entryName)) continue;
            File smaliDir = entry.getValue();
            File outputDex = new File(workDir, entryName);
            if (logger != null) logger.logMessage("Assembling " + entryName + " ...");
            SmaliOptions options = new SmaliOptions();
            options.outputDexFile = outputDex.getPath();
            options.jobs = jobs;
            if (!Smali.assemble(options, smaliDir.getPath())) {
                throw new IOException("Failed to assemble " + entryName);
            }
            result.put(entryName, outputDex);
        }
        return result;
    }

    private static File buildOutputApk(File inputApk, Map<String, File> assembledDexFiles,
                                         String suffix, APKLogger logger) throws IOException {
        File outputFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                FilenameUtils.getBaseName(inputApk.getName()) + suffix + ".apk"));
        Set<String> replacedEntries = assembledDexFiles.keySet();

        Map<String, CompressionMethod> originalDexMethods = new HashMap<>();
        try (ZipFile zin = new ZipFile(inputApk)) {
            for (FileHeader header : zin.getFileHeaders()) {
                if (replacedEntries.contains(header.getFileName())) {
                    originalDexMethods.put(header.getFileName(), methodFor(header));
                }
            }
        }

        try (ZipFile zin = new ZipFile(inputApk); ZipFile zout = new ZipFile(outputFile)) {
            for (FileHeader header : zin.getFileHeaders()) {
                String name = header.getFileName();
                if (header.isDirectory()) continue;
                if (replacedEntries.contains(name)) continue;
                try (InputStream is = zin.getInputStream(header)) {
                    addStream(zout, is, name, methodFor(header));
                }
            }
            for (Map.Entry<String, File> entry : assembledDexFiles.entrySet()) {
                CompressionMethod method = originalDexMethods.getOrDefault(entry.getKey(), CompressionMethod.DEFLATE);
                addFile(zout, entry.getValue(), entry.getKey(), method);
            }
        }
        if (logger != null) logger.logMessage("Saved to: " + outputFile.getName());
        return outputFile;
    }

    private static File smaliDirForEntry(File workDir, String entryName) {
        String base = entryName;
        if (base.endsWith(".dex")) base = base.substring(0, base.length() - ".dex".length());
        if ("classes".equals(base)) return new File(workDir, "smali");
        return new File(workDir, "smali_" + base);
    }

    private static String dexEntryForSmaliDir(File smaliDir) {
        String name = smaliDir.getName();
        if ("smali".equals(name)) return "classes.dex";
        if (name.startsWith("smali_")) return name.substring("smali_".length()) + ".dex";
        return smaliDir.getName() + ".dex";
    }

    private static Set<String> removeAllToastCalls(File workDir, APKLogger logger) throws IOException {
        List<File> smaliFiles = findAllSmaliFiles(workDir);
        Set<String> modifiedEntries = new LinkedHashSet<>();

        for (File file : smaliFiles) {
            String content = readFile(file);
            String original = content;
            content = removeToastBlocks(content);
            if (!content.equals(original)) {
                writeFile(file, content);
                String entryName = getDexEntryForSmaliFile(workDir, file);
                if (entryName != null) modifiedEntries.add(entryName);
                if (logger != null) logger.logMessage("Removed Toast calls from " + file.getName());
            }
        }
        if (logger != null) logger.logMessage("Modified " + modifiedEntries.size() + " dex file(s)");
        return modifiedEntries;
    }

    /**
     * Removes complete Toast.makeText -> move-result-object -> Toast.show() blocks,
     * tolerating .line / .param / blank / comment lines between them, so no orphaned
     * move-result-object is left behind.
     */
    private static String removeToastBlocks(String content) {
        String[] lines = content.split("\\n", -1);
        boolean[] remove = new boolean[lines.length];
        int i = 0;
        while (i < lines.length) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("invoke-") && trimmed.contains("Landroid/widget/Toast;->makeText")) {
                int j = i + 1;
                while (j < lines.length && isNonInstruction(lines[j])) j++;
                int moveResultLine = -1;
                if (j < lines.length && lines[j].trim().startsWith("move-result-object")) {
                    moveResultLine = j;
                    j++;
                    while (j < lines.length && isNonInstruction(lines[j])) j++;
                }
                if (j < lines.length && lines[j].trim().startsWith("invoke-")
                        && lines[j].trim().contains("Landroid/widget/Toast;->show")) {
                    remove[i] = true;
                    if (moveResultLine != -1) remove[moveResultLine] = true;
                    remove[j] = true;
                    i = j + 1;
                    continue;
                }
            } else if (trimmed.startsWith("invoke-") && trimmed.contains("Landroid/widget/Toast;->show")) {
                remove[i] = true;
            }
            i++;
        }
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < lines.length; k++) {
            if (!remove[k]) sb.append(lines[k]).append('\n');
        }
        return sb.toString();
    }

    private static boolean isNonInstruction(String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith(".") || t.startsWith("#");
    }

    private static String getDexEntryForSmaliFile(File workDir, File smaliFile) {
        File dir = smaliFile.getParentFile();
        while (dir != null && !workDir.equals(dir.getParentFile())) {
            dir = dir.getParentFile();
        }
        if (dir != null) {
            return dexEntryForSmaliDir(dir);
        }
        return null;
    }

    private static List<File> findAllSmaliFiles(File dir) {
        List<File> result = new ArrayList<>();
        File[] dirs = dir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File subDir : dirs) {
                if (subDir.getName().startsWith("smali")) {
                    collectSmaliFiles(subDir, result);
                }
            }
        }
        return result;
    }

    private static void collectSmaliFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collectSmaliFiles(f, result);
            else if (f.getName().endsWith(".smali")) result.add(f);
        }
    }

    private static String readManifestXml(Context context, String apkPath) {
        com.apk.axml.APKParser parser = new com.apk.axml.APKParser();
        try {
            parser.parse(apkPath, context);
            return parser.getManifestAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String parseMainLauncherActivity(String manifestXml, String packageName) {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(manifestXml));
            int eventType = parser.getEventType();
            boolean inActivity = false;
            boolean inIntentFilter = false;
            boolean hasMainAction = false;
            boolean hasLauncherCategory = false;
            String currentActivity = null;
            String mainActivity = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                if (eventType == XmlPullParser.START_TAG) {
                    if ("activity".equals(tagName)) {
                        inActivity = true;
                        currentActivity = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                        if (currentActivity != null && currentActivity.startsWith(".")) {
                            currentActivity = packageName + currentActivity;
                        }
                    } else if (inActivity && "intent-filter".equals(tagName)) {
                        inIntentFilter = true;
                        hasMainAction = false;
                        hasLauncherCategory = false;
                    } else if (inIntentFilter && "action".equals(tagName)) {
                        String name = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                        if ("android.intent.action.MAIN".equals(name)) hasMainAction = true;
                    } else if (inIntentFilter && "category".equals(tagName)) {
                        String name = parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name");
                        if ("android.intent.category.LAUNCHER".equals(name)) hasLauncherCategory = true;
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ("intent-filter".equals(tagName) && hasMainAction && hasLauncherCategory && currentActivity != null) {
                        mainActivity = currentActivity;
                    } else if ("activity".equals(tagName)) {
                        inActivity = false;
                        currentActivity = null;
                        inIntentFilter = false;
                    }
                }
                eventType = parser.next();
            }
            return mainActivity;
        } catch (XmlPullParserException | IOException ignored) {
            return null;
        }
    }

    private static String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buf = new char[8192];
            int len;
            while ((len = reader.read(buf)) != -1) sb.append(buf, 0, len);
        }
        return sb.toString();
    }

    private static void writeFile(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    private static CompressionMethod methodFor(FileHeader header) {
        CompressionMethod method = header.getCompressionMethod();
        if (method != CompressionMethod.STORE && method != CompressionMethod.DEFLATE) method = CompressionMethod.DEFLATE;
        return method;
    }

    private static void addStream(ZipFile zout, InputStream is, String name, CompressionMethod method) throws IOException {
        ZipParameters params = new ZipParameters();
        params.setCompressionMethod(method);
        params.setEncryptFiles(false);
        params.setFileNameInZip(name);
        zout.addStream(is, params);
    }

    private static void addFile(ZipFile zout, File file, String name, CompressionMethod method) throws IOException {
        ZipParameters params = new ZipParameters();
        params.setCompressionMethod(method);
        params.setEncryptFiles(false);
        params.setFileNameInZip(name);
        zout.addFile(file, params);
    }

    private static void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else //noinspection ResultOfMethodCallIgnored
                    f.delete();
            }
        }
        //noinspection ResultOfMethodCallIgnored
        dir.delete();
    }
}

package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Xml;

import com.android.tools.smali.baksmali.Baksmali;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class ToastInjectorUtil {

    private static final String TOAST_METHOD_NAME = "showMPManagerToast";

    public static File addToastToActivities(Context context, File inputApk,
                                            List<String> activityClassNames, String toastMessage,
                                            APKLogger logger) throws Exception {
        File workDir = createTempDir(context, "add_toast");
        try {
            Set<String> targetEntries = findDexEntriesForClasses(context, inputApk, activityClassNames, logger);
            if (targetEntries.isEmpty()) {
                throw new IOException("Could not locate the selected activities in any dex file");
            }
            Map<String, File> dexEntryToSmaliDir = disassembleApk(context, inputApk, workDir, targetEntries, logger);
            for (String className : activityClassNames) {
                File smaliFile = findSmaliFile(workDir, className);
                if (smaliFile == null) {
                    if (logger != null) logger.logMessage("Smali not found: " + className);
                    continue;
                }
                injectToastIntoActivity(smaliFile, className, toastMessage);
                if (logger != null) logger.logMessage("Injected toast into " + className);
            }
            Map<String, File> assembledDexFiles = assembleDexFiles(workDir, dexEntryToSmaliDir, targetEntries, context, inputApk, logger);
            return buildOutputApk(inputApk, assembledDexFiles, "_toast", logger);
        } finally {
            deleteDirectory(workDir);
        }
    }

    public static File removeAllToasts(Context context, File inputApk, APKLogger logger) throws Exception {
        File workDir = createTempDir(context, "remove_toast");
        try {
            Map<String, File> dexEntryToSmaliDir = disassembleApk(context, inputApk, workDir, null, logger);
            Set<String> modifiedEntries = removeAllToastCalls(workDir, logger);
            Map<String, File> assembledDexFiles = assembleDexFiles(workDir, dexEntryToSmaliDir, modifiedEntries, context, inputApk, logger);
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

    private static Set<String> findDexEntriesForClasses(Context context, File inputApk,
                                                        List<String> classNames, APKLogger logger) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        if (classNames == null || classNames.isEmpty()) return result;
        Opcodes opcodes = Opcodes.forApi(getApiLevel(context, inputApk.getPath()));
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

    private static Map<String, File> disassembleApk(Context context, File inputApk, File workDir,
                                                      Set<String> entriesToDisassemble, APKLogger logger) throws Exception {
        Map<String, File> result = new LinkedHashMap<>();
        Opcodes opcodes = Opcodes.forApi(getApiLevel(context, inputApk.getPath()));
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
            //options.apiLevel = opcodes.api;
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
                                                        Context context, File inputApk, APKLogger logger) throws Exception {
        Map<String, File> result = new LinkedHashMap<>();
        int apiLevel = getApiLevel(context, inputApk.getPath());
        int jobs = Runtime.getRuntime().availableProcessors();
        for (Map.Entry<String, File> entry : dexEntryToSmaliDir.entrySet()) {
            String entryName = entry.getKey();
            if (entriesToAssemble != null && !entriesToAssemble.contains(entryName)) continue;
            File smaliDir = entry.getValue();
            File outputDex = new File(workDir, entryName);
            if (logger != null) logger.logMessage("Assembling " + entryName + " ...");
            SmaliOptions options = new SmaliOptions();
            //options.apiLevel = apiLevel;
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

    private static String dexEntryForSmaliDir(File workDir, File smaliDir) {
        String name = smaliDir.getName();
        if ("smali".equals(name)) return "classes.dex";
        if (name.startsWith("smali_")) return name.substring("smali_".length()) + ".dex";
        return smaliDir.getName() + ".dex";
    }

    private static File findSmaliFile(File workDir, String className) {
        String relative = className.replace('.', '/') + ".smali";
        File[] dirs = workDir.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                if (dir.getName().startsWith("smali")) {
                    File candidate = new File(dir, relative);
                    if (candidate.isFile()) return candidate;
                }
            }
        }
        return null;
    }

    private static void injectToastIntoActivity(File smaliFile, String className, String toastMessage) throws IOException {
        String classDescriptor = "L" + className.replace('.', '/') + ";";
        String escapedMessage = escapeSmaliString(toastMessage);
        String[] newMethodLines = new String[] {
                ".method private " + TOAST_METHOD_NAME + "()V",
                "    .locals 2",
                "",
                "    const-string v0, \"" + escapedMessage + "\"",
                "",
                "    const/4 v1, 0x1",
                "",
                "    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;",
                "",
                "    move-result-object v0",
                "",
                "    invoke-virtual {v0}, Landroid/widget/Toast;->show()V",
                "",
                "    return-void",
                ".end method"
        };

        List<String> lines = readLines(smaliFile);
        int methodStart = -1;
        int methodEnd = -1;
        int superCallLine = -1;

        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (methodStart == -1 && trimmed.startsWith(".method ") && trimmed.contains("onCreate(Landroid/os/Bundle;)V")) {
                methodStart = i;
            } else if (methodStart != -1 && methodEnd == -1) {
                if (superCallLine == -1 && trimmed.matches("invoke-super \\{p0, p1\\}, .*->onCreate\\(Landroid/os/Bundle;\\)V")) {
                    superCallLine = i;
                }
                if (trimmed.equals(".end method")) {
                    methodEnd = i;
                    break;
                }
            }
        }

        int insertAfterLine = -1;
        if (superCallLine != -1) {
            insertAfterLine = superCallLine;
        } else if (methodStart != -1 && methodEnd != -1) {
            for (int i = methodStart + 1; i < methodEnd; i++) {
                String trimmed = lines.get(i).trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith(".") && !trimmed.startsWith("#")) {
                    insertAfterLine = i - 1;
                    break;
                }
            }
            if (insertAfterLine == -1) insertAfterLine = methodEnd - 1;
        }

        if (insertAfterLine != -1) {
            lines.add(insertAfterLine + 1, "    invoke-direct {p0}, " + classDescriptor + "->" + TOAST_METHOD_NAME + "()V");
        }

        int endClassLine = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).trim().equals(".end class")) {
                endClassLine = i;
                break;
            }
        }
        if (endClassLine != -1) {
            for (int i = 0; i < newMethodLines.length; i++) {
                lines.add(endClassLine + i, newMethodLines[i]);
            }
        } else {
            lines.addAll(Arrays.asList(newMethodLines));
        }

        writeLines(smaliFile, lines);
    }

    private static Set<String> removeAllToastCalls(File workDir, APKLogger logger) throws IOException {
        List<File> smaliFiles = findAllSmaliFiles(workDir);
        Set<String> modifiedEntries = new LinkedHashSet<>();

        Pattern fullBlock = Pattern.compile(
                "(?m)^\\s*const-string\\s+v\\d+,\\s*\"[^\"]*\"\\R" +
                        "^\\s*const/4\\s+v\\d+,\\s*(?:0x0|0x1)\\R" +
                        "^\\s*invoke-static\\s+\\{[^}]*\\},\\s*Landroid/widget/Toast;->makeText\\(Landroid/content/Context;(?:Ljava/lang/CharSequence;|I)I\\)Landroid/widget/Toast;\\R" +
                        "(?:^\\s*move-result-object\\s+(?:v|p)\\d+\\R)?" +
                        "^\\s*invoke-virtual\\s+\\{(?:v|p)\\d+\\},\\s*Landroid/widget/Toast;->show\\(\\)V\\R?"
        );

        Pattern makeTextWithResult = Pattern.compile(
                "(?m)^\\s*invoke-static\\s+\\{[^}]*\\},\\s*Landroid/widget/Toast;->makeText\\([^\\n]*\\)Landroid/widget/Toast;\\s*\\R" +
                        "(?:^\\s*move-result-object\\s+(?:v|p)\\d+\\s*\\R)?"
        );

        Pattern showCall = Pattern.compile("(?m)^\\s*invoke-virtual\\s+\\{(?:v|p)\\d+\\},\\s*Landroid/widget/Toast;->show\\(\\)V\\s*\\R?");

        for (File file : smaliFiles) {
            String content = readFile(file);
            String original = content;
            content = fullBlock.matcher(content).replaceAll("");
            content = makeTextWithResult.matcher(content).replaceAll("");
            content = showCall.matcher(content).replaceAll("");
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

    private static String getDexEntryForSmaliFile(File workDir, File smaliFile) {
        File dir = smaliFile.getParentFile();
        while (dir != null && !workDir.equals(dir.getParentFile())) {
            dir = dir.getParentFile();
        }
        if (dir != null) {
            return dexEntryForSmaliDir(workDir, dir);
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

    private static int getApiLevel(Context context, String apkPath) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apkPath, 0);
            if (pi != null && pi.applicationInfo != null) {
                return Math.min(pi.applicationInfo.targetSdkVersion, 35);
            }
        } catch (Exception ignored) {}
        return 28;
    }

    private static List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    private static void writeLines(File file, List<String> lines) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            for (String line : lines) writer.println(line);
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

    private static String escapeSmaliString(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c); break;
            }
        }
        return sb.toString();
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

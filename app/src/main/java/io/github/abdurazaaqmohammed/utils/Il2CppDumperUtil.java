package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Xml;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MultiDexContainer;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableExceptionHandler;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.ImmutableTryBlock;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference;
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.apk.axml.APKParser;
import com.reandroid.apk.APKLogger;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.io.FilenameUtils;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Injects the Il2Cpp Self-Dumper native library (il2cppdumper-main/) into an
 * APK: the prebuilt libil2cppdumper.so from our assets is added to the APK's
 * lib/<abi>/ folders and a {@code System.loadLibrary("il2cppdumper")} call is
 * wired into the static initializer of the app's Application class (falling
 * back to the launcher activity). When the patched app starts, the dumper
 * waits for libil2cpp.so to be mapped and writes dump.cs + script.json to the
 * app's external files dir (see il2cppdumper-main/README.md).
 *
 * The dex patch follows the proven rename-and-wrap pattern used for toast
 * injection: the existing {@code <clinit>} (if any) is renamed to
 * {@code clinit$mpmanager} and replaced by a new initializer that loads the
 * library and then calls the original one — so no register counting or smali
 * text editing is needed and try/catch blocks stay intact.
 *
 * <p>Two knobs the caller chooses (see {@link #listTargets} and
 * {@link DocumentsProviderInjector}): which class receives the loadLibrary
 * call — the Application class by default, any activity when a game only
 * initialises IL2CPP later — and whether the patched app also gets a
 * DocumentsProvider so the dump can be read without root.</p>
 */
public final class Il2CppDumperUtil {

    private Il2CppDumperUtil() {
    }

    public static final String LIB_NAME = "il2cppdumper";
    private static final String SO_NAME = "lib" + LIB_NAME + ".so";
    private static final String ASSET_DIR = "il2cppdumper";
    private static final String RENAMED_CLINIT = "clinit$mpmanager";
    /** ABIs we ship prebuilt dumper libraries for. */
    public static final String[] SUPPORTED_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"};

    /** True when the APK contains a libil2cpp.so in any lib folder. */
    public static boolean hasIl2Cpp(File apk) {
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader h : zf.getFileHeaders()) {
                String name = h.getFileName();
                if (!h.isDirectory() && name.startsWith("lib/") && name.endsWith("/libil2cpp.so")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** ABI folders (lib/<abi>/) present in the APK that we can add a dumper to. */
    public static List<String> dumpableAbis(File apk) throws IOException {
        Set<String> dirs = new LinkedHashSet<>();
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader h : zf.getFileHeaders()) {
                String name = h.getFileName();
                if (h.isDirectory() || !name.startsWith("lib/") || !name.endsWith(".so")) continue;
                int secondSlash = name.indexOf('/', 4);
                if (secondSlash > 4) dirs.add(name.substring(4, secondSlash));
            }
        }
        List<String> result = new ArrayList<>();
        for (String abi : SUPPORTED_ABIS) {
            if (dirs.contains(abi)) result.add(abi);
        }
        return result;
    }

    /**
     * The default injection point: the manifest's application android:name,
     * falling back to the best-ranked activity. The dialog lets the user
     * override this from {@link #listTargets}.
     */
    public static String findInjectionTarget(Context context, File apk) {
        List<Target> targets = listTargets(context, apk);
        if (targets.isEmpty()) return null;
        return targets.get(0).className;
    }

    /** A class the dumper's loadLibrary call can be wired into. */
    public static final class Target {
        /** Application class — its static initializer runs before anything else. */
        public static final int APPLICATION = 0;
        /** Activity with a MAIN/LAUNCHER intent filter. */
        public static final int LAUNCHER = 1;
        /** An activity that looks like the Unity/IL2CPP entry point. */
        public static final int UNITY = 2;
        /** Any other activity. */
        public static final int ACTIVITY = 3;

        public final String className;
        public final int kind;

        Target(String className, int kind) {
            this.className = className;
            this.kind = kind;
        }

        @Override
        public String toString() {
            return className;
        }
    }

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER";

    /**
     * Every class worth offering as an injection point, best first: the
     * Application class, the launcher activities, activities that look like a
     * Unity entry point, then the rest of the declared activities.
     *
     * <p>Why this is a choice at all: the loadLibrary call only runs once the
     * class it sits in is initialised. For most games the Application class
     * covers that, but a game whose launcher activity is a splash/login screen
     * only loads IL2CPP in a later activity — picking that activity (or
     * launching it manually) is what makes the dump appear.</p>
     */
    public static List<Target> listTargets(Context context, File apk) {
        List<Target> targets = new ArrayList<>();
        String pkg = packageName(context, apk);
        String manifestXml = readManifestXml(context, apk.getPath());
        if (manifestXml == null) return targets;

        String applicationClass = null;
        Map<String, Integer> activities = new LinkedHashMap<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(manifestXml));
            String current = null;
            boolean inFilter = false;
            boolean hasMain = false;
            boolean hasLauncher = false;
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                String tag = parser.getName();
                if (event == XmlPullParser.START_TAG) {
                    if ("application".equals(tag)) {
                        applicationClass = qualify(parser.getAttributeValue(ANDROID_NS, "name"), pkg);
                    } else if ("activity".equals(tag) || "activity-alias".equals(tag)) {
                        // An activity-alias is not a real class: its android:name
                        // is just a label the launcher can point at, so the code
                        // that actually runs is android:targetActivity.
                        String targetActivity = "activity-alias".equals(tag)
                                ? parser.getAttributeValue(ANDROID_NS, "targetActivity") : null;
                        current = qualify(targetActivity != null ? targetActivity
                                : parser.getAttributeValue(ANDROID_NS, "name"), pkg);
                        inFilter = false;
                        hasMain = false;
                        hasLauncher = false;
                    } else if (current != null && "intent-filter".equals(tag)) {
                        inFilter = true;
                        hasMain = false;
                        hasLauncher = false;
                    } else if (inFilter && "action".equals(tag)) {
                        if (ACTION_MAIN.equals(parser.getAttributeValue(ANDROID_NS, "name"))) {
                            hasMain = true;
                        }
                    } else if (inFilter && "category".equals(tag)) {
                        if (CATEGORY_LAUNCHER.equals(parser.getAttributeValue(ANDROID_NS, "name"))) {
                            hasLauncher = true;
                        }
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    if ("intent-filter".equals(tag)) {
                        if (current != null && hasMain && hasLauncher) {
                            activities.put(current, Target.LAUNCHER);
                        }
                        inFilter = false;
                    } else if ("activity".equals(tag) || "activity-alias".equals(tag)) {
                        if (current != null && !activities.containsKey(current)) {
                            activities.put(current, guessKind(current));
                        }
                        current = null;
                        inFilter = false;
                    }
                }
                event = parser.next();
            }
        } catch (Exception ignored) {
        }

        if (!TextUtils.isEmpty(applicationClass)) {
            targets.add(new Target(applicationClass, Target.APPLICATION));
        }
        List<Target> rest = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : activities.entrySet()) {
            if (entry.getKey().equals(applicationClass)) continue;
            rest.add(new Target(entry.getKey(), entry.getValue()));
        }
        // Stable within a kind: the manifest order is kept for the long tail,
        // which is how the launcher activity usually ends up on top.
        Collections.sort(rest, (a, b) -> {
            if (a.kind != b.kind) return Integer.compare(a.kind, b.kind);
            return 0;
        });
        targets.addAll(rest);
        return targets;
    }

    private static int guessKind(String className) {
        String lower = className.toLowerCase(Locale.ROOT);
        if (lower.contains("unityplayer") || lower.endsWith(".unity")) return Target.UNITY;
        return Target.ACTIVITY;
    }

    private static String qualify(String name, String pkg) {
        if (TextUtils.isEmpty(name)) return null;
        if (name.startsWith(".")) return pkg + name;
        if (name.indexOf('.') < 0 && !TextUtils.isEmpty(pkg)) return pkg + "." + name;
        return name;
    }

    private static String packageName(Context context, File apk) {
        PackageInfo pi = context.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
        if (pi != null && !TextUtils.isEmpty(pi.packageName)) return pi.packageName;
        return "";
    }

    private static String readManifestXml(Context context, String apkPath) {
        try {
            APKParser parser = new APKParser();
            parser.parse(apkPath, context);
            return parser.getManifestAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Builds the patched APK: patched dex entry + added dumper libraries.
     *
     * @param targetClass    fully qualified class receiving the loadLibrary call
     * @param injectProvider also declare the DocumentsProvider so the dump can
     *                       be read without root (see
     *                       {@link DocumentsProviderInjector})
     */
    public static File patchApk(Context context, File inputApk, List<String> abis,
                                String targetClass, boolean injectProvider,
                                APKLogger logger) throws Exception {
        if (abis == null || abis.isEmpty()) {
            throw new IOException("No dumper ABI selected");
        }
        if (TextUtils.isEmpty(targetClass)) {
            throw new IOException("Could not find an Application or launcher activity class in the manifest");
        }
        String descriptor = "L" + targetClass.replace('.', '/') + ";";
        Opcodes opcodes = Opcodes.getDefault();
        MultiDexContainer<? extends DexBackedDexFile> container =
                DexFileFactory.loadDexContainer(inputApk, opcodes);

        String targetEntry = null;
        DexBackedDexFile targetDex = null;
        for (String entryName : container.getDexEntryNames()) {
            MultiDexContainer.DexEntry<? extends DexBackedDexFile> entry = container.getEntry(entryName);
            if (entry == null) continue;
            for (ClassDef classDef : entry.getDexFile().getClasses()) {
                if (descriptor.equals(classDef.getType())) {
                    targetEntry = entryName;
                    targetDex = entry.getDexFile();
                    break;
                }
            }
            if (targetEntry != null) break;
        }
        if (targetEntry == null) {
            throw new IOException(targetClass + " is declared in the manifest but its code is "
                    + "not in this APK (split or stubbed). Pick another injection point, "
                    + "the Application class if there is one.");
        }
        if (logger != null) logger.logMessage("Patching " + targetEntry + ": " + descriptor);

        byte[] patchedDex = rewriteDexWithLoadLibrary(targetDex, descriptor, logger);
        File workDir = new File(context.getCacheDir(),
                "il2cppdump_" + System.currentTimeMillis());
        workDir.mkdirs();
        try {
            File patchedDexFile = new File(workDir, targetEntry + ".patched");
            try (FileOutputStream fos = new FileOutputStream(patchedDexFile)) {
                fos.write(patchedDex);
            }
            Map<String, File> replacements = new LinkedHashMap<>();
            replacements.put(targetEntry, patchedDexFile);

            Map<String, File> additions = new LinkedHashMap<>();
            Map<String, Integer> methods = new LinkedHashMap<>();
            for (String abi : abis) {
                File so = extractDumperSo(context, abi, workDir);
                if (so == null) {
                    throw new IOException("No bundled dumper library for ABI " + abi);
                }
                String entryName = "lib/" + abi + "/" + SO_NAME;
                additions.put(entryName, so);
                methods.put(entryName, 0);
                if (logger != null) logger.logMessage("Adding " + entryName);
            }

            if (injectProvider) {
                addDocumentsProvider(context, inputApk, workDir, replacements, additions, methods, logger);
            }

            File outputFile = FileUtils.getUnusedFile(new File(inputApk.getParentFile(),
                    FilenameUtils.getBaseName(inputApk.getName()) + "_il2cppdump.apk"));
            ApkZipAlignUtil.rebuildApk(inputApk, outputFile, replacements, methods, additions, null);
            if (logger != null) logger.logMessage("Saved " + outputFile.getName());
            return outputFile;
        } finally {
            deleteDirectory(workDir);
        }
    }

    /**
     * Adds the provider dex and its manifest declaration to the patch. The
     * provider runs inside the patched app, so it can hand out the dump from
     * the app's own (otherwise unreachable) directories.
     */
    private static void addDocumentsProvider(Context context, File inputApk, File workDir,
                                             Map<String, File> replacements,
                                             Map<String, File> additions,
                                             Map<String, Integer> methods,
                                             APKLogger logger) throws IOException {
        String pkg = packageName(context, inputApk);
        String authority = DocumentsProviderInjector.authorityFor(pkg);
        File manifest = DocumentsProviderInjector.patchManifest(inputApk, authority,
                new File(workDir, "AndroidManifest.xml.patched"));
        if (manifest == null) {
            if (logger != null) {
                logger.logMessage("Documents provider already declared: " + authority);
            }
            return;
        }
        File providerDex = DocumentsProviderInjector.buildProviderDex(context, workDir);
        String dexName = DocumentsProviderInjector.nextDexName(inputApk);
        additions.put(dexName, providerDex);
        methods.put(dexName, 8);
        replacements.put("AndroidManifest.xml", manifest);
        if (logger != null) {
            logger.logMessage("Adding documents provider " + authority + " (" + dexName + ")");
        }
    }

    static byte[] rewriteDexWithLoadLibrary(DexBackedDexFile dex, String descriptor,
                                            APKLogger logger) throws IOException {
        DexPool pool = new DexPool(dex.getOpcodes());
        boolean found = false;
        for (ClassDef classDef : dex.getClasses()) {
            if (descriptor.equals(classDef.getType())) {
                classDef = injectLoadLibrary(classDef);
                found = true;
            }
            pool.internClass(classDef);
        }
        if (!found) throw new IOException(descriptor + " disappeared from the dex file");
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        return Arrays.copyOf(store.getData(), store.getSize());
    }

    /** Renames any existing &lt;clinit&gt; and installs a loading initializer. */
    static ClassDef injectLoadLibrary(ClassDef classDef) {
        List<Method> directMethods = new ArrayList<>();
        Method existing = null;
        String renamedName = RENAMED_CLINIT;
        Set<String> takenNames = new LinkedHashSet<>();
        for (Method m : classDef.getDirectMethods()) {
            takenNames.add(m.getName());
        }
        while (takenNames.contains(renamedName)) renamedName += "$";
        for (Method m : classDef.getDirectMethods()) {
            if ("<clinit>".equals(m.getName())) {
                existing = m;
                // Drop ACC_CONSTRUCTOR: the renamed copy is an ordinary static method.
                int flags = m.getAccessFlags() & ~AccessFlags.CONSTRUCTOR.getValue();
                directMethods.add(new ImmutableMethod(classDef.getType(), renamedName,
                        m.getParameters(), m.getReturnType(), flags, m.getAnnotations(),
                        m.getHiddenApiRestrictions(),
                        ImmutableMethodImplementation.of(m.getImplementation())));
            } else {
                directMethods.add(m);
            }
        }
        directMethods.add(buildClinit(classDef.getType(), existing != null ? renamedName : null));
        return new ImmutableClassDef(classDef.getType(), classDef.getAccessFlags(),
                classDef.getSuperclass(), classDef.getInterfaces(), classDef.getSourceFile(),
                classDef.getAnnotations(), classDef.getStaticFields(),
                classDef.getInstanceFields(), directMethods, classDef.getVirtualMethods());
    }

    /**
     * Builds the class initializer that loads the dumper library. Load failures
     * are swallowed (wrong-ABI device, stripped lib) so the target app keeps
     * working without a dump; the original initializer always still runs.
     * Code unit addresses (with / without an original clinit):
     * <pre>
     * 0: const-string v0               (2)  ┐ try [0,5)
     * 2: invoke-static {v0} load       (3)  ┘
     * 5: invoke-static {} original     (3)  | only when an original exists
     * 8: return-void                   (2)  | 5: return-void without original
     * 10/7: move-exception v0          (1)  ┐ catch-all handler
     * 11/-: invoke-static {} original  (3)  |
     * 14/8: return-void                (2)  ┘
     * </pre>
     */
    private static ImmutableMethod buildClinit(String classType, String originalName) {
        ImmutableMethodReference loadLibrary = new ImmutableMethodReference(
                "Ljava/lang/System;", "loadLibrary",
                Collections.singletonList("Ljava/lang/String;"), "V");
        ImmutableMethodReference originalRef = originalName == null ? null :
                new ImmutableMethodReference(classType, originalName,
                        Collections.emptyList(), "V");
        List<com.android.tools.smali.dexlib2.iface.instruction.Instruction> instructions = new ArrayList<>();
        instructions.add(new ImmutableInstruction21c(Opcode.CONST_STRING, 0,
                new ImmutableStringReference(LIB_NAME)));
        instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 1, 0, 0, 0, 0, 0, loadLibrary));
        if (originalRef != null) {
            instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, originalRef));
            instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));      // addr 8
            instructions.add(new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, 0)); // addr 10
            instructions.add(new ImmutableInstruction35c(Opcode.INVOKE_STATIC, 0, 0, 0, 0, 0, 0, originalRef));
            instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));      // addr 14
        } else {
            instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));      // addr 5
            instructions.add(new ImmutableInstruction11x(Opcode.MOVE_EXCEPTION, 0)); // addr 7
            instructions.add(new ImmutableInstruction10x(Opcode.RETURN_VOID));      // addr 8
        }
        int handlerAddr = originalRef != null ? 10 : 7;
        List<ImmutableTryBlock> tries = Collections.singletonList(
                // null exception type = catch-all handler
                new ImmutableTryBlock(0, 5, Collections.singletonList(
                        new ImmutableExceptionHandler(null, handlerAddr))));
        ImmutableMethodImplementation impl = new ImmutableMethodImplementation(
                1, instructions, tries, null);
        return new ImmutableMethod(classType, "<clinit>", Collections.emptyList(), "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Collections.emptySet(), Collections.emptySet(), impl);
    }

    private static File extractDumperSo(Context context, String abi, File workDir) throws IOException {
        String assetPath = ASSET_DIR + "/" + abi + "/" + SO_NAME;
        File out = new File(workDir, abi + "_" + SO_NAME);
        try (InputStream in = context.getAssets().open(assetPath);
             OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
            return out;
        } catch (IOException e) {
            return null;
        }
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

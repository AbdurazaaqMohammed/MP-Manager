package io.github.abdurazaaqmohammed.features.apk;

import android.content.Context;
import android.text.TextUtils;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.VersionMap;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.reandroid.apk.APKLogger;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;

/**
 * Offline unpacker for 360 "Jiagu" protected APKs (com.stub.StubApp).
 *
 * Works from DEX files previously dumped out of the running process by the root
 * Jiagu Dumper tool: the real classes.dex content lives only in memory while the
 * stub decrypts it, so the dump is the only source of the original bytecode.
 *
 * Pipeline (own implementation, no code taken from other tools):
 *  1. validate + de-duplicate the dumped dexes, drop stub/jiagu helper dexes;
 *  2. locate the real Application class by walking superclass chains across dexes;
 *  3. patch every real dex in the binary model: injected
 *     Lcom/stub/StubApp;->... hook invocations become same-size NOPs (keeping code
 *     unit counts identical preserves try/catch and debug addresses), the move-result
 *     consuming getOrigApplicationContext() is NOPed as well;
 *  4. rebuild the APK through ApkModule: drop old dexes + jiagu payloads, patch the
 *     manifest application name to the real class, write the rewritten dexes;
 *  5. align for install; the caller signs.
 */
public final class JiaguUnpackEngine {

    public static final String STUB_APP_DESCRIPTOR = "Lcom/stub/StubApp;";
    public static final String STUB_APP_JAVA = "com.stub.StubApp";
    private static final Pattern JIAGU_ASSET = Pattern.compile("(?i)^assets/libjiagu.*");
    private static final Pattern OLD_DEX = Pattern.compile("(?i)^classes[0-9]*\\.dex$");
    private static final Pattern SIGNATURE = Pattern.compile("(?i)^META-INF/(MANIFEST\\.MF|.*\\.(SF|RSA|DSA|EC))$");

    private JiaguUnpackEngine() {}

    public static class Result {
        public File apk;
        public String applicationClass;
        public int dexCount;
        public int nopPatched;
        public final List<String> log = new ArrayList<>();
        void log(String s) { log.add(s); }
    }

    /** Manifest entries + resource table decoded from an APK (the app's own axml stack). */
    public static class ManifestData {
        public List<com.apk.axml.serializableItems.XMLEntry> entries;
        public List<com.apk.axml.serializableItems.ResEntry> res;
        public String poolEncoding;
    }

    /** Decode AndroidManifest.xml with the app's own axml parser (works on jiagu APKs). */
    public static ManifestData decodeManifest(File apk) throws Exception {
        ManifestData out = new ManifestData();
        try (ZipFile zf = new ZipFile(apk)) {
            FileHeader manifestEntry = zf.getFileHeader("AndroidManifest.xml");
            if (manifestEntry == null) throw new IOException("No AndroidManifest.xml in " + apk.getName());
            FileHeader resHeader = zf.getFileHeader("resources.arsc");
            if (resHeader != null) {
                try (InputStream ris = zf.getInputStream(resHeader)) {
                    out.res = new com.apk.axml.ResourceTableParser(ris).parse();
                } catch (Exception ignored) {
                }
            }
            try (InputStream is = zf.getInputStream(manifestEntry)) {
                out.entries = new com.apk.axml.aXMLDecoder(is, out.res).decode();
            }
            out.poolEncoding = manifestPoolEncoding(zf, manifestEntry);
            return out;
        }
    }

    private static String manifestPoolEncoding(ZipFile zf, FileHeader header) {
        try (InputStream is = zf.getInputStream(header)) {
            byte[] head = new byte[28];
            int read = 0;
            while (read < head.length) {
                int n = is.read(head, read, head.length - read);
                if (n < 0) break;
                read += n;
            }
            if (read < head.length) return null;
            int flags = (head[24] & 0xFF) | ((head[25] & 0xFF) << 8)
                    | ((head[26] & 0xFF) << 16) | ((head[27] & 0xFF) << 24);
            return (flags & 0x100) != 0 ? "UTF8" : "UNICODE";
        } catch (Exception e) {
            return null;
        }
    }

    /** @return value of the given attribute (middle tag) inside the manifest entries, or null. */
    public static String manifestAttr(List<com.apk.axml.serializableItems.XMLEntry> entries, String attr) {
        if (entries == null) return null;
        for (com.apk.axml.serializableItems.XMLEntry e : entries) {
            String tag = e.getTag();
            if (tag != null && tag.contains(attr)) {
                String v = e.getValue();
                if (v != null && !v.trim().isEmpty()) return v.trim();
            }
        }
        return null;
    }

    /** Quick check from the APK entry names + manifest application name. */
    public static boolean isJiagu(File apk) {
        try {
            ManifestData md = decodeManifest(apk);
            if (md.entries != null) {
                for (com.apk.axml.serializableItems.XMLEntry e : md.entries) {
                    String v = e.getValue();
                    if (v != null && v.trim().equals(STUB_APP_JAVA)) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader h : zf.getFileHeaders()) {
                String n = h.getFileName();
                if (n != null && n.startsWith("assets/libjiagu")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static byte[] readFile(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        }
    }

    /** Loaded dump dex with metadata for classification. */
    static class LoadedDex {
        String name;
        byte[] bytes;
        String sha1;
        DexBackedDexFile dex;
        List<DexBackedClassDef> classes;
        boolean stub; // class content is the protection stub itself
    }

    /** Validate dump dexes, drop duplicates and stub/jiagu helper dexes. */
    static List<LoadedDex> loadAndClassify(List<File> files, Result out) throws IOException {
        Set<String> seen = new HashSet<>();
        Map<String, LoadedDex> bySha = new LinkedHashMap<>();
        int index = 0;
        for (File f : files) {
            if (f == null || !f.isFile() || f.length() < 112 || f.length() > 200 * 1024 * 1024) continue;
            byte[] bytes;
            try {
                bytes = readFile(f);
            } catch (Exception e) {
                out.log("Skip unreadable " + f.getName() + ": " + e.getMessage());
                continue;
            }
            if (!isDexHeader(bytes)) continue;
            String sha;
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                sha = hex(md.digest(bytes));
            } catch (Exception e) {
                sha = f.getName() + "-" + bytes.length;
            }
            if (!seen.add(sha)) {
                out.log("Duplicate dump skipped: " + f.getName());
                continue;
            }
            DexBackedDexFile dex;
            List<DexBackedClassDef> classes;
            try {
                int api = VersionMap.mapDexVersionToApi(
                        com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem.getVersion(bytes, 0));
                dex = new DexBackedDexFile(Opcodes.forApi(api), bytes);
                classes = new ArrayList<>(dex.getClasses());
                // dexlib2 resolves strings lazily - force a full parse now so a
                // corrupted dump dex is rejected here instead of mid-unpack.
                for (ClassDef cd : classes) {
                    cd.getType();
                    cd.getSuperclass();
                }
            } catch (Exception e) {
                out.log("Invalid dex skipped (" + f.getName() + "): " + e.getMessage());
                continue;
            }
            LoadedDex ld = new LoadedDex();
            ld.name = "dump#" + (++index) + " (" + f.getName() + ")";
            ld.bytes = bytes;
            ld.sha1 = sha;
            ld.dex = dex;
            ld.classes = classes;
            classify(ld);
            bySha.put(sha, ld);
        }
        List<LoadedDex> kept = new ArrayList<>();
        for (LoadedDex ld : bySha.values()) {
            if (ld.classes.size() < 3 && ld.classes.stream().noneMatch(c -> isAppChainCandidate(c))) {
                out.log("Dropped tiny dex " + ld.name + " (" + ld.classes.size() + " classes)");
                continue;
            }
            if (ld.stub) {
                out.log("Dropped stub dex " + ld.name + " (" + ld.classes.size() + " classes, all protection classes)");
                continue;
            }
            kept.add(ld);
            out.log("Kept " + ld.name + ": " + ld.classes.size() + " classes, " + FileSize(ld.bytes.length));
        }
        // Largest first: biggest dex becomes classes.dex (stable multidex order).
        kept.sort((a, b) -> Integer.compare(b.bytes.length, a.bytes.length));
        return kept;
    }

    private static String FileSize(int len) {
        if (len >= 1024 * 1024) return String.format(Locale.US, "%.1f MB", len / 1048576f);
        if (len >= 1024) return String.format(Locale.US, "%.1f KB", len / 1024f);
        return len + " B";
    }

    /** Class prefixes that only ever belong to the protection stub / injected payloads. */
    private static final String[] PACKER_PREFIXES = {
            "Lcom/stub/", "Lcom/qihoo/", "Lcom/tianyu/", "Lcom/jg/",
            "Ldalvik/annotation/Signature;", "Ldalvik/annotation/EnclosingClass;",
            "Lcom/google/android/ads/z__", // repackager-injected ad-fraud SDK
    };

    private static boolean isPackerClass(String type) {
        for (String prefix : PACKER_PREFIXES) {
            if (type.startsWith(prefix)) return true;
        }
        return false;
    }

    private static void classify(LoadedDex ld) {
        // Kept intentionally permissive: the protected app resolves part of its
        // runtime data through the packer's helper dexes, so only the stub
        // proper (com.stub / com.qihoo.jiagu loader classes) is marked here for
        // informational logging. Everything valid is rebuilt into the APK.
        boolean allStub = !ld.classes.isEmpty();
        for (DexBackedClassDef c : ld.classes) {
            String t = c.getType();
            if (!(t.startsWith("Lcom/stub/") || t.startsWith("Lcom/qihoo/jiagu/"))) {
                allStub = false;
                break;
            }
        }
        ld.stub = allStub;
    }

    private static boolean isAppChainCandidate(DexBackedClassDef c) {
        return "Landroid/app/Application;".equals(c.getSuperclass());
    }

    static boolean isDexHeader(byte[] b) {
        if (b.length < 112) return false;
        if (b[0] != 'd' || b[1] != 'e' || b[2] != 'x' || b[3] != '\n' || b[7] != 0) return false;
        if (b[4] != '0' || b[5] != '3') return false;
        if (b[6] < '5' || b[6] > '9') return false;
        return b[36] == 112 && b[37] == 0 && b[38] == 0 && b[39] == 0; // header_size == 112
    }

    private static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Descriptor "Le/a/a;" -> "e.a.a". */
    public static String descriptorToJava(String descriptor) {
        if (descriptor == null) return null;
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        }
        return descriptor;
    }

    /** Walk superclass chains across dexes; return concrete Application subclasses. */
    static List<String> findRealApplications(List<LoadedDex> dexes, Result out) {
        Map<String, String> superMap = new LinkedHashMap<>();
        Map<String, Integer> accessMap = new HashMap<>();
        for (LoadedDex ld : dexes) {
            for (DexBackedClassDef c : ld.classes) {
                try {
                    if (c.getSuperclass() != null && !superMap.containsKey(c.getType())) {
                        superMap.put(c.getType(), c.getSuperclass());
                        accessMap.put(c.getType(), c.getAccessFlags());
                    }
                } catch (Exception badClass) {
                    out.log("Skipping unreadable class in " + ld.name + ": " + badClass.getMessage());
                }
            }
        }
        List<String> candidates = new ArrayList<>();
        for (String type : superMap.keySet()) {
            String t = type;
            int hops = 0;
            while (t != null && hops++ < 32) {
                if ("Landroid/app/Application;".equals(t)) {
                    candidates.add(type);
                    break;
                }
                t = superMap.get(t);
            }
        }
        List<String> concrete = new ArrayList<>();
        for (String c : candidates) {
            String lower = c.toLowerCase(Locale.US);
            if (lower.contains("stub") || lower.contains("qihoo") || lower.contains("jiagu")) continue;
            int flags = accessMap.getOrDefault(c, 0);
            boolean isAbstract = (flags & 0x0400) != 0 || (flags & 0x0200) != 0; // ABSTRACT / INTERFACE
            if (!isAbstract) concrete.add(c);
        }
        // Prefer the topmost concrete class: one no other concrete candidate extends.
        for (String c : concrete) {
            boolean extended = false;
            for (String other : concrete) {
                if (other.equals(c)) continue;
                String t = other;
                int hops = 0;
                while (t != null && hops++ < 32) {
                    t = superMap.get(t);
                    if (c.equals(t)) { extended = true; break; }
                }
                if (extended) break;
            }
            if (!extended) {
                out.log("Real Application class: " + descriptorToJava(c) + " (" + c + ")");
                return new ArrayList<>(List.of(c));
            }
        }
        if (concrete.size() > 1) {
            out.log("Multiple concrete Application subclasses, using first: "
                    + descriptorToJava(concrete.get(0)));
        }
        return concrete;
    }

    /**
     * NOP-patch injected StubApp hook calls. Every removed invoke is replaced with
     * the same number of 1-unit NOP instructions so all following code addresses
     * (try blocks, debug items) stay valid; the move-result consuming
     * getOrigApplicationContext() is NOPed too, leaving the original context in place.
     */
    static byte[] rewriteDex(LoadedDex ld, Result out, int[] patched) throws IOException {
        DexPool pool = new DexPool(Opcodes.forApi(
                VersionMap.mapDexVersionToApi(com.android.tools.smali.dexlib2.dexbacked.raw.HeaderItem.getVersion(ld.bytes, 0))));
        boolean dexModified = false;
        for (DexBackedClassDef classDef : ld.classes) {
            List<Method> direct = new ArrayList<>();
            boolean changed = false;
            for (Method m : classDef.getDirectMethods()) {
                Method r = rewriteMethod(m, patched);
                if (r != m) { changed = true; dexModified = true; }
                direct.add(r);
            }
            List<Method> virtual = new ArrayList<>();
            for (Method m : classDef.getVirtualMethods()) {
                Method r = rewriteMethod(m, patched);
                if (r != m) { changed = true; dexModified = true; }
                virtual.add(r);
            }
            if (changed) {
                pool.internClass(new ImmutableClassDef(classDef.getType(), classDef.getAccessFlags(),
                        classDef.getSuperclass(), classDef.getInterfaces(), classDef.getSourceFile(),
                        classDef.getAnnotations(),
                        classDef.getStaticFields(), classDef.getInstanceFields(), direct, virtual));
            } else {
                pool.internClass(classDef);
            }
        }
        if (dexModified) {
            out.log("Patched hook calls in " + ld.name);
        }
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        return Arrays.copyOf(store.getData(), store.getSize());
    }

    private static Method rewriteMethod(Method m, int[] patched) {
        MethodImplementation impl = m.getImplementation();
        if (impl == null) return m;
        List<Instruction> outIns = new ArrayList<>();
        boolean modified = false;
        boolean skipMoveResult = false;
        for (Instruction ins : impl.getInstructions()) {
            if (skipMoveResult) {
                if (isMoveResult(ins)) {
                    addNops(outIns, ins.getCodeUnits());
                    skipMoveResult = false;
                    modified = true;
                    patched[0]++;
                    continue;
                }
            }
            if (ins instanceof ReferenceInstruction) {
                com.android.tools.smali.dexlib2.iface.reference.Reference ref =
                        ((ReferenceInstruction) ins).getReference();
                if (ref instanceof MethodReference mr && STUB_APP_DESCRIPTOR.equals(mr.getDefiningClass())) {
                    addNops(outIns, ins.getCodeUnits());
                    modified = true;
                    patched[0]++;
                    if (!"V".equals(mr.getReturnType())) skipMoveResult = true;
                    continue;
                }
                if (ref instanceof FieldReference fr && STUB_APP_DESCRIPTOR.equals(fr.getDefiningClass())) {
                    addNops(outIns, ins.getCodeUnits());
                    modified = true;
                    patched[0]++;
                    continue;
                }
            }
            outIns.add(ins);
        }
        if (!modified) return m;
        ImmutableMethodImplementation newImpl = new ImmutableMethodImplementation(
                impl.getRegisterCount(), outIns, impl.getTryBlocks(), impl.getDebugItems());
        return new ImmutableMethod(m.getDefiningClass(), m.getName(), m.getParameters(),
                m.getReturnType(), m.getAccessFlags(), m.getAnnotations(),
                m.getHiddenApiRestrictions(), newImpl);
    }

    private static boolean isMoveResult(Instruction ins) {
        return ins.getOpcode().name.startsWith("move-result");
    }

    private static void addNops(List<Instruction> outIns, int codeUnits) {
        int n = Math.max(1, codeUnits);
        for (int i = 0; i < n; i++) outIns.add(new ImmutableInstruction10x(Opcode.NOP));
    }

    /**
     * Full unpack: rebuild the APK from the original + dumped dexes.
     *
     * @param dumpDexes dumped .dex files from the root Jiagu Dumper tool
     */
    public static Result unpack(Context context, File apk, List<File> dumpDexes, APKLogger logger) throws Exception {
        return unpack(context, apk, dumpDexes, logger, null);
    }

    /** @param outputDir where to write the rebuilt APK (null: next to the original). */
    public static Result unpack(Context context, File apk, List<File> dumpDexes, APKLogger logger,
                                File outputDir) throws Exception {
        Result out = new Result();

        // Manifest via the app's own axml stack (reandroid cannot parse some jiagu APKs).
        ManifestData md = decodeManifest(apk);
        if (md.entries == null) throw new IllegalArgumentException("Cannot decode AndroidManifest.xml");
        String stubName = null;
        com.apk.axml.serializableItems.XMLEntry stubEntry = null;
        for (com.apk.axml.serializableItems.XMLEntry e : md.entries) {
            String tag = e.getTag();
            if (tag != null && tag.contains("android:name") && !TextUtils.isEmpty(e.getValue())
                    && e.getValue().trim().toLowerCase(Locale.US).contains("stub")) {
                stubName = e.getValue().trim();
                stubEntry = e;
                break;
            }
        }
        if (stubName == null) {
            throw new IllegalArgumentException("Not a Jiagu (com.stub.StubApp) protected APK: no stub application name in the manifest");
        }
        out.log("Original application: " + stubName);

        List<LoadedDex> dexes = loadAndClassify(dumpDexes, out);
        if (dexes.isEmpty()) {
            throw new IllegalArgumentException("No valid dexes in the dump folder");
        }
        List<String> appClasses = findRealApplications(dexes, out);
        if (appClasses.isEmpty()) {
            throw new IllegalArgumentException("No concrete Application subclass found in the dumped dexes");
        }
        String appClass = appClasses.get(0);
        out.applicationClass = descriptorToJava(appClass);

        int[] patched = {0};
        List<byte[]> rewritten = new ArrayList<>();
        for (LoadedDex ld : dexes) {
            try {
                rewritten.add(rewriteDex(ld, out, patched));
            } catch (Exception rewriteFailure) {
                // dexlib2 parses strings lazily, so bad strings surface only here
                out.log("Rewrite failed for " + ld.name + ": " + rewriteFailure.getMessage() + " - skipped");
            }
        }
        if (rewritten.isEmpty()) {
            throw new IllegalArgumentException("All dumped dexes failed to rewrite: "
                    + (dexes.isEmpty() ? "none loaded" : dexes.get(dexes.size() - 1).name));
        }
        out.dexCount = rewritten.size();
        out.nopPatched = patched[0];
        out.log("NOP-patched instructions: " + patched[0]);

        // Manifest: point application at the real class.
        stubEntry.setValue(out.applicationClass);
        out.log("Manifest application -> " + out.applicationClass);

        // Drop the repackager-injected loader providers (all-o/O/0 class names):
        // they auto-run before the Application and pull the ad-fraud payload back in.
        int removedProviders = removeInjectedProviders(md.entries, out);
        byte[] manifestBytes;
        try {
            com.apk.axml.aXMLUtils.StringPoolChunk.Encoding enc = "UTF8".equals(md.poolEncoding)
                    ? com.apk.axml.aXMLUtils.StringPoolChunk.Encoding.UTF8 : com.apk.axml.aXMLUtils.StringPoolChunk.Encoding.UNICODE;
            manifestBytes = new com.apk.axml.aXMLEncoder().encodeString(md.entries, context, md.res, enc);
        } catch (Exception e) {
            throw new IOException("Manifest re-encode failed: " + e.getMessage(), e);
        }

        // Rebuild the zip: keep everything except old dexes, jiagu payloads and signatures.
        File parent = (outputDir != null && (outputDir.isDirectory() || outputDir.mkdirs()))
                ? outputDir : apk.getParentFile();
        File outFile = FileUtils.getUnusedFile(new File(parent,
                apk.getName().replaceFirst("(?i)\\.apk$", "") + "_unjiagu.apk"));
        try (ZipFile zin = new ZipFile(apk); ZipFile zout = new ZipFile(outFile)) {
            List<FileHeader> headers = zin.getFileHeaders();
            int dexIdx = 1;
            int copied = 0;
            for (FileHeader h : headers) {
                String name = h.getFileName();
                if (name == null) continue;
                if (OLD_DEX.matcher(name).matches()
                        || JIAGU_ASSET.matcher(name).matches()
                        || SIGNATURE.matcher(name).matches()
                        || name.equals("stamp-cert-sha256")
                        || name.equals("AndroidManifest.xml")) {
                    out.log("Removed " + name);
                    continue;
                }
                try (InputStream is = zin.getInputStream(h)) {
                    zout.addStream(is, params(name));
                    copied++;
                }
            }
            out.log("Copied " + copied + " entries");
            try (InputStream is = new ByteArrayInputStream(manifestBytes)) {
                zout.addStream(is, params("AndroidManifest.xml"));
            }
            for (byte[] bytes : rewritten) {
                String dexName = dexIdx == 1 ? "classes.dex" : "classes" + dexIdx + ".dex";
                try (InputStream is = new ByteArrayInputStream(bytes)) {
                    zout.addStream(is, params(dexName));
                }
                out.log("Added " + dexName + " (" + FileSize(bytes.length) + ")");
                dexIdx++;
            }
        }
        try {
            ApkZipAlignUtil.ensureInstallable(outFile);
        } catch (Exception e) {
            out.log("Align skipped: " + e.getMessage());
        }
        out.apk = outFile;
        out.log("Written " + outFile.getName());
        if (logger != null) {
            for (String line : out.log) logger.logMessage(line);
        }
        return out;
    }

    private static net.lingala.zip4j.model.ZipParameters params(String name) {
        net.lingala.zip4j.model.ZipParameters p = new net.lingala.zip4j.model.ZipParameters();
        p.setFileNameInZip(name);
        p.setCompressionLevel(net.lingala.zip4j.model.enums.CompressionLevel.FASTEST);
        return p;
    }

    /**
     * Removes &lt;provider&gt; elements whose class name is pure obfuscation noise
     * (only o/O/0/underscore/dot characters) - the repackager uses them to auto-load
     * the injected ad payload before the real Application runs.
     *
     * @return number of removed providers
     */
    static int removeInjectedProviders(List<com.apk.axml.serializableItems.XMLEntry> entries, Result out) {
        if (entries == null) return 0;
        java.util.regex.Pattern junkName = java.util.regex.Pattern.compile("^[oO0_.]{8,}$");
        java.util.regex.Pattern componentName = java.util.regex.Pattern.compile(
                "android:name\s*=\s*\"([^\"]+)\"");
        int removed = 0;
        int i = 0;
        while (i < entries.size()) {
            com.apk.axml.serializableItems.XMLEntry e = entries.get(i);
            String tag = e.getTag() == null ? "" : e.getTag();
            String trimmed = tag.trim();
            boolean component = trimmed.startsWith("<activity") || trimmed.startsWith("<service")
                    || trimmed.startsWith("<receiver") || trimmed.startsWith("<provider");
            if (!component) {
                i++;
                continue;
            }
            String element = trimmed.substring(1, trimmed.indexOf(' ') > 0 ? trimmed.indexOf(' ') : trimmed.length());
            // merged short elements carry the whole tag text in one entry
            boolean merged = trimmed.contains("/>");
            String name = null;
            java.util.regex.Matcher nm = componentName.matcher(tag);
            if (nm.find()) name = nm.group(1);
            int end = i;
            if (!merged) {
                String close = "</" + element;
                int j = i + 1;
                while (j < entries.size()) {
                    com.apk.axml.serializableItems.XMLEntry a = entries.get(j);
                    String atag = a.getTag() == null ? "" : a.getTag();
                    String amid = a.getMiddleTag() == null ? "" : a.getMiddleTag();
                    if (name == null && amid.contains("=\"") && atag.contains("android:name")) {
                        name = a.getValue();
                    }
                    if (atag.trim().startsWith(close)) {
                        end = j;
                        break;
                    }
                    j++;
                }
            }
            if (name != null && junkName.matcher(name).matches()) {
                for (int k = end; k >= i; k--) {
                    out.log("Removed injected component: " + name);
                    entries.remove(k);
                }
                removed++;
                continue; // stay at i, the next entry shifted here
            }
            i = Math.max(end, i) + 1;
        }
        if (removed > 0) out.log("Removed " + removed + " injected component(s)");
        return removed;
    }
}

package io.github.abdurazaaqmohammed.features.dex;

import com.android.tools.smali.baksmali.Adaptors.ClassDefinition;
import com.android.tools.smali.baksmali.BaksmaliOptions;
import com.android.tools.smali.baksmali.formatter.BaksmaliWriter;
import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.HiddenApiRestriction;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedClassDef;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.AnnotationElement;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.debug.EndLocal;
import com.android.tools.smali.dexlib2.iface.debug.EpilogueBegin;
import com.android.tools.smali.dexlib2.iface.debug.LineNumber;
import com.android.tools.smali.dexlib2.iface.debug.PrologueEnd;
import com.android.tools.smali.dexlib2.iface.debug.RestartLocal;
import com.android.tools.smali.dexlib2.iface.debug.SetSourceFile;
import com.android.tools.smali.dexlib2.iface.debug.StartLocal;
import com.android.tools.smali.dexlib2.iface.instruction.FieldOffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.InlineIndexInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchElement;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.VerificationErrorInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.VtableIndexInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction3rc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction45cc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction4rcc;
import com.android.tools.smali.dexlib2.iface.instruction.formats.PackedSwitchPayload;
import com.android.tools.smali.dexlib2.iface.instruction.formats.SparseSwitchPayload;
import com.android.tools.smali.dexlib2.iface.reference.CallSiteReference;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodHandleReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodProtoReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.android.tools.smali.dexlib2.iface.value.AnnotationEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ArrayEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.BooleanEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ByteEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.CharEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.DoubleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EncodedValue;
import com.android.tools.smali.dexlib2.iface.value.EnumEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FieldEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.FloatEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.IntEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.LongEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodHandleEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.MethodTypeEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.NullEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.ShortEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.StringEncodedValue;
import com.android.tools.smali.dexlib2.iface.value.TypeEncodedValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import modder.hub.dexeditor.smali.SharedSmaliUtils;

/**
 * Loads the smali of every class declared in a set of dex files (directly or
 * inside APKs) and reports the classes that were added, removed or changed
 * between two such sets.
 *
 * <p>Class equality is decided structurally on the raw dexlib2 class defs,
 * mirroring exactly what {@code baksmali} renders for the shared options, so
 * no smali text has to be generated for the class list. The smali text is
 * produced on demand for a single class when its diff is opened.</p>
 */
public final class DexCompareEngine {

    public enum Status {
        ADDED, REMOVED, CHANGED
    }

    public static final class ComparedClass {
        public final String type;
        public final Status status;

        ComparedClass(String type, Status status) {
            this.type = type;
            this.status = status;
        }

        /** Human readable class name without the smali L...; decoration. */
        public String displayName() {
            return DexCompareEngine.displayName(type);
        }
    }

    public interface Progress {
        void onProgress(int done, int total);
    }

    /**
     * Compare-time tolerances chosen when the comparison is started. They are
     * applied both when deciding whether a class changed at all and when the
     * per-class diff is built, so ignored noise never shows up as a difference.
     */
    public static final class Options {
        public boolean ignoreDebugInfo;
        public boolean ignoreOptimizations;
        public boolean ignoreRegisterCount;
        public boolean ignoreNopInstructions;

        public Options() {
        }

        public Options(boolean ignoreDebugInfo, boolean ignoreOptimizations,
                       boolean ignoreRegisterCount, boolean ignoreNopInstructions) {
            this.ignoreDebugInfo = ignoreDebugInfo;
            this.ignoreOptimizations = ignoreOptimizations;
            this.ignoreRegisterCount = ignoreRegisterCount;
            this.ignoreNopInstructions = ignoreNopInstructions;
        }

        public boolean any() {
            return ignoreDebugInfo || ignoreOptimizations || ignoreRegisterCount || ignoreNopInstructions;
        }

        /** True when the line is noise that the enabled options ask to ignore. */
        public boolean ignorable(String rawLine) {
            if (rawLine == null || !any()) return false;
            String line = rawLine.trim();
            if (line.isEmpty()) return false;
            if (ignoreNopInstructions && isNop(line)) return true;
            if (ignoreDebugInfo && isDebugDirective(line)) return true;
            return ignoreRegisterCount && isRegisterDirective(line);
        }

        private static boolean isNop(String line) {
            if (!line.startsWith("nop")) return false;
            return line.length() == 3 || line.charAt(3) == ' ' || line.charAt(3) == '\t' || line.charAt(3) == '/';
        }

        private static boolean isRegisterDirective(String line) {
            return hasWordPrefix(line, ".registers") || hasWordPrefix(line, ".locals");
        }

        private static boolean isDebugDirective(String line) {
            return hasWordPrefix(line, ".line")
                    || hasWordPrefix(line, ".local")
                    || line.startsWith(".end local")
                    || line.startsWith(".restart local")
                    || hasWordPrefix(line, ".prologue")
                    || hasWordPrefix(line, ".param")
                    || hasWordPrefix(line, ".source");
        }

        private static boolean hasWordPrefix(String line, String prefix) {
            if (!line.startsWith(prefix)) return false;
            return line.length() == prefix.length()
                    || !Character.isLetterOrDigit(line.charAt(prefix.length()));
        }

        /**
         * Rewrites a single line into a form that is stable across compiler
         * optimizations: equivalent opcode encodings ({@code const/4} vs
         * {@code const}, {@code invoke-virtual/range} vs {@code invoke-virtual},
         * {@code goto/16} vs {@code goto}, …) and compiler-assigned label
         * numbers ({@code :goto_0} vs {@code :goto_1}) are canonicalized.
         */
        public String canonicalize(String rawLine) {
            if (rawLine == null) return "";
            if (!ignoreOptimizations) return rawLine;
            String line = rawLine.trim();
            int end = 0;
            while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
            String opcode = line.substring(0, end);
            int slash = opcode.indexOf('/');
            if (slash > 0 && OPCODE_BASES.contains(opcode.substring(0, slash))) {
                line = opcode.substring(0, slash) + line.substring(end);
            }
            return canonicalizeLabels(line);
        }

        private static final Set<String> OPCODE_BASES = new java.util.HashSet<>(Arrays.asList(
                "move", "move-wide", "move-object", "const", "const-wide", "const-string", "goto",
                "invoke-virtual", "invoke-super", "invoke-direct", "invoke-static", "invoke-interface",
                "invoke-custom", "invoke-polymorphic"));

        private static final Pattern TRAILING_LABEL_NUMBERS = Pattern.compile("(_\\d+)+$");

        /** Strips trailing _N/_N_N number groups from label tokens outside string literals. */
        private static String canonicalizeLabels(String line) {
            StringBuilder out = new StringBuilder(line.length());
            boolean inString = false;
            int i = 0;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (inString) {
                    out.append(c);
                    if (c == '\\' && i + 1 < line.length()) {
                        out.append(line.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (c == '"') inString = false;
                    i++;
                    continue;
                }
                if (c == '"') {
                    inString = true;
                    out.append(c);
                    i++;
                    continue;
                }
                if (c == ':' && i + 1 < line.length()
                        && (Character.isLetter(line.charAt(i + 1)) || line.charAt(i + 1) == '_')) {
                    int start = i;
                    i++;
                    while (i < line.length()
                            && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                    String token = line.substring(start, i);
                    String stripped = TRAILING_LABEL_NUMBERS.matcher(token).replaceAll("");
                    out.append(stripped.isEmpty() ? token : stripped);
                    continue;
                }
                out.append(c);
                i++;
            }
            return out.toString();
        }

        /**
         * Compares two whole smali texts under these options: ignored lines are
         * dropped from both sides, the rest is compared line by line.
         */
        public boolean smaliEqual(String a, String b) {
            if (a == null) a = "";
            if (b == null) b = "";
            if (a.equals(b)) return true;
            if (!any()) return false;
            List<String> leftSide = significantLines(a);
            List<String> rightSide = significantLines(b);
            if (leftSide.size() != rightSide.size()) return false;
            for (int i = 0; i < leftSide.size(); i++) {
                if (!canonicalize(leftSide.get(i)).equals(canonicalize(rightSide.get(i)))) return false;
            }
            return true;
        }

        /** Splits smali into lines and drops the ones the options ignore. */
        public List<String> significantLines(String smali) {
            List<String> out = new ArrayList<>();
            if (smali == null) return out;
            if (smali.endsWith("\n")) smali = smali.substring(0, smali.length() - 1);
            for (String line : smali.split("\n", -1)) {
                if (!ignorable(line)) out.add(line);
            }
            return out;
        }
    }

    private final Map<String, ClassDef> left = new HashMap<>();
    private final Map<String, ClassDef> right = new HashMap<>();
    private Options options = new Options();

    public void setOptions(Options options) {
        this.options = options == null ? new Options() : options;
    }

    public Options getOptions() {
        return options;
    }

    public static String displayName(String type) {
        if (type == null) return "";
        if (type.length() > 2 && type.charAt(0) == 'L' && type.endsWith(";")) {
            return type.substring(1, type.length() - 1);
        }
        return type;
    }

    public void loadLeft(List<File> files, File cacheDir, Progress progress) throws Exception {
        load(files, left, new File(cacheDir, "left"), progress);
    }

    public void loadRight(List<File> files, File cacheDir, Progress progress) throws Exception {
        load(files, right, new File(cacheDir, "right"), progress);
    }

    private void load(List<File> files, Map<String, ClassDef> into, File cacheDir, Progress progress) throws Exception {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            // best effort, a failed mkdir is reported by the loads below
        }
        pruneExtractionCache(cacheDir);
        List<File> dexFiles = new ArrayList<>();
        for (File file : files) {
            if (file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                dexFiles.addAll(extractDexFiles(file, cacheDir));
            } else {
                dexFiles.add(file);
            }
        }
        int done = 0;
        for (File dex : dexFiles) {
            DexBackedDexFile dexFile = DexFileFactory.loadDexFile(dex, null);
            for (ClassDef classDef : dexFile.getClasses()) {
                into.put(classDef.getType(), classDef);
            }
            done++;
            if (progress != null) progress.onProgress(done, Math.max(1, dexFiles.size()));
        }
    }

    /** Extracts classes*.dex entries from an APK into the cache dir and returns them. */
    private List<File> extractDexFiles(File apk, File cacheDir) throws Exception {
        List<File> out = new ArrayList<>();
        try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(apk)) {
            List<net.lingala.zip4j.model.FileHeader> dexHeaders = new ArrayList<>();
            for (net.lingala.zip4j.model.FileHeader header : zip.getFileHeaders()) {
                String name = header.getFileName();
                if (name != null && name.matches("classes(\\d*)\\.dex")) dexHeaders.add(header);
            }
            dexHeaders.sort((a, b) -> Integer.compare(dexNumber(a.getFileName()), dexNumber(b.getFileName())));
            for (net.lingala.zip4j.model.FileHeader header : dexHeaders) {
                String name = header.getFileName();
                // content-addressed cache name: the entry's CRC and size, so a
                // previously extracted identical dex is reused without I/O
                File target = new File(cacheDir, cacheName(apk.getName(), name, header.getCrc()));
                if (!isCached(target, header.getUncompressedSize())) {
                    try (InputStream in = zip.getInputStream(header);
                         OutputStream os = new java.io.FileOutputStream(target)) {
                        byte[] buffer = new byte[65536];
                        int read;
                        while ((read = in.read(buffer)) != -1) os.write(buffer, 0, read);
                    }
                }
                out.add(target);
            }
        }
        return out;
    }

    private static String cacheName(String apkName, String dexName, long crc) {
        String base = apkName.replaceAll("[^A-Za-z0-9._-]", "_");
        return base + "_" + Long.toHexString(crc) + "_" + dexName;
    }

    private static boolean isCached(File target, long expectedSize) {
        if (!target.isFile()) return false;
        if (expectedSize >= 0 && target.length() != expectedSize) return false;
        try (InputStream in = new FileInputStream(target)) {
            byte[] magic = new byte[4];
            return in.read(magic) == 4 && magic[0] == 'd' && magic[1] == 'e' && magic[2] == 'x' && magic[3] == '\n';
        } catch (Exception e) {
            return false;
        }
    }

    /** Best-effort cap on the extraction cache so old entries don't pile up. */
    private static void pruneExtractionCache(File cacheDir) {
        File[] files = cacheDir.listFiles();
        if (files == null || files.length < 2) return;
        final long maxBytes = 512L * 1024 * 1024;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= maxBytes) return;
        Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File file : files) {
            if (total <= maxBytes) break;
            total -= file.length();
            file.delete();
        }
    }

    private static int dexNumber(String name) {
        if ("classes.dex".equals(name)) return 1;
        try {
            return Integer.parseInt(name.substring(7, name.length() - 4));
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Compares both loaded sides and returns only the differing classes, sorted by name.
     *
     * <p>Classes are compared on worker threads in parallel; each class is only
     * ever touched by one thread, which is what the dexlib2 reads require.</p>
     */
    public List<ComparedClass> computeDifferences(Progress progress) throws Exception {
        TreeSet<String> allTypes = new TreeSet<>(left.keySet());
        allTypes.addAll(right.keySet());
        String[] types = allTypes.toArray(new String[0]);
        int total = types.length;
        ComparedClass[] results = new ComparedClass[total];
        if (total > 0) {
            AtomicInteger done = new AtomicInteger();
            int cores = Runtime.getRuntime().availableProcessors();
            if (total < 128 || cores <= 1) {
                compareRange(types, 0, total, results, progress, done);
            } else {
                compareParallel(types, total, results, progress, done, cores);
            }
        }
        List<ComparedClass> result = new ArrayList<>();
        for (ComparedClass item : results) {
            if (item != null) result.add(item);
        }
        return result;
    }

    private void compareParallel(String[] types, int total, ComparedClass[] results,
                                        Progress progress, AtomicInteger done, int cores) throws Exception {
        int threads = Math.min(cores, 8);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicReference<Exception> failure = new AtomicReference<>();
        try {
            List<Future<?>> futures = new ArrayList<>();
            int chunks = threads * 4;
            int chunkSize = (total + chunks - 1) / chunks;
            for (int start = 0; start < total; start += chunkSize) {
                final int from = start;
                final int to = Math.min(total, start + chunkSize);
                futures.add(pool.submit(() -> {
                    try {
                        compareRange(types, from, to, results, progress, done);
                    } catch (Exception e) {
                        failure.compareAndSet(null, e);
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
        } finally {
            pool.shutdownNow();
        }
        if (failure.get() != null) throw failure.get();
    }

    private void compareRange(String[] types, int from, int to, ComparedClass[] results,
                              Progress progress, AtomicInteger done) throws Exception {
        for (int i = from; i < to; i++) {
            String type = types[i];
            ClassDef l = left.get(type);
            ClassDef r = right.get(type);
            if (l == null) {
                results[i] = new ComparedClass(type, Status.ADDED);
            } else if (r == null) {
                results[i] = new ComparedClass(type, Status.REMOVED);
            } else if (!classesEqual(l, r)) {
                results[i] = new ComparedClass(type, Status.CHANGED);
            }
            int count = done.incrementAndGet();
            if (progress != null && count % 50 == 0) progress.onProgress(count, types.length);
        }
    }

    /**
     * Decides whether the two class defs render the same smali. The structural
     * comparison answers directly for the common case; anything it can't judge
     * falls back to rendering both sides and comparing the text.
     */
    private boolean classesEqual(ClassDef l, ClassDef r) throws Exception {
        if (structuralCompareSupported()) {
            try {
                if (classStructurallyEquals(l, r)) {
                    // identical structure always renders identical smali
                    return true;
                }
                if (!options.any()) {
                    // without ignore options the structural comparison is also
                    // complete: a mismatch means the smali differs
                    return false;
                }
            } catch (Exception e) {
                // couldn't judge structurally, fall through to the text comparison
            }
        }
        return options.smaliEqual(toSmali(l), toSmali(r));
    }

    private static boolean structuralCompareSupported() {
        BaksmaliOptions o = SharedSmaliUtils.OPTIONS;
        // rendering modes that depend on cross-class analysis can't be mirrored
        // by a purely structural comparison
        return o.registerInfo == 0 && !o.normalizeVirtualMethods && !o.deodex
                && o.syntheticAccessorResolver == null;
    }

    public String smaliLeft(String type) throws Exception {
        return toSmali(left.get(type));
    }

    public String smaliRight(String type) throws Exception {
        return toSmali(right.get(type));
    }

    public boolean hasType(String type, boolean leftSide) {
        return (leftSide ? left : right).containsKey(type);
    }

    private static String toSmali(ClassDef classDef) throws Exception {
        if (classDef == null) return "";
        StringWriter stringWriter = new StringWriter(16 * 1024);
        BaksmaliWriter writer = new BaksmaliWriter(stringWriter);
        new ClassDefinition(SharedSmaliUtils.OPTIONS, classDef).writeTo(writer);
        writer.close();
        return stringWriter.toString();
    }

    // ==========================================
    // Structural class equality
    //
    // Mirrors what baksmali writes for the shared options: two classes render
    // identically exactly when these parts match. Every check is conservative —
    // anything unknown or unexpected reports "not equal" and falls back to the
    // text comparison, never the other way round.
    // ==========================================

    /**
     * True when the two class defs would render the same smali text. May return
     * false for classes that would actually render identically (odex quirks,
     * unsupported constructs); never true for classes that wouldn't.
     */
    private static boolean classStructurallyEquals(ClassDef a, ClassDef b) throws Exception {
        if (a.getAccessFlags() != b.getAccessFlags()) return false;
        if (!Objects.equals(a.getSuperclass(), b.getSuperclass())) return false;
        if (!Objects.equals(a.getSourceFile(), b.getSourceFile())) return false;
        if (!listEquals(a.getInterfaces(), b.getInterfaces())) return false;
        if (!annotationsEqual(a.getAnnotations(), b.getAnnotations())) return false;
        if (!fieldsEqual(staticFields(a), staticFields(b))) return false;
        if (!fieldsEqual(instanceFields(a), instanceFields(b))) return false;
        if (!methodsEqual(directMethods(a), directMethods(b))) return false;
        if (!methodsEqual(virtualMethods(a), virtualMethods(b))) return false;
        return true;
    }

    // The renderer walks the dex-file-ordered member lists including duplicates
    // (skipDuplicates=false); mirror that instead of the interface defaults.
    private static Iterable<? extends Field> staticFields(ClassDef def) {
        return def instanceof DexBackedClassDef d ? d.getStaticFields(false) : def.getStaticFields();
    }

    private static Iterable<? extends Field> instanceFields(ClassDef def) {
        return def instanceof DexBackedClassDef d ? d.getInstanceFields(false) : def.getInstanceFields();
    }

    private static Iterable<? extends Method> directMethods(ClassDef def) {
        return def instanceof DexBackedClassDef d ? d.getDirectMethods(false) : def.getDirectMethods();
    }

    private static Iterable<? extends Method> virtualMethods(ClassDef def) {
        return def instanceof DexBackedClassDef d ? d.getVirtualMethods(false) : def.getVirtualMethods();
    }

    private static boolean fieldsEqual(Iterable<? extends Field> a, Iterable<? extends Field> b) throws Exception {
        Iterator<? extends Field> ia = a.iterator();
        Iterator<? extends Field> ib = b.iterator();
        while (ia.hasNext()) {
            if (!ib.hasNext()) return false;
            Field fa = ia.next();
            Field fb = ib.next();
            if (fa.getAccessFlags() != fb.getAccessFlags()) return false;
            if (!Objects.equals(fa.getName(), fb.getName())) return false;
            if (!Objects.equals(fa.getType(), fb.getType())) return false;
            if (!restrictionsEqual(fa.getHiddenApiRestrictions(), fb.getHiddenApiRestrictions())) return false;
            if (!encodedValueEquals(fa.getInitialValue(), fb.getInitialValue())) return false;
            if (!annotationsEqual(fa.getAnnotations(), fb.getAnnotations())) return false;
        }
        return !ib.hasNext();
    }

    private static boolean methodsEqual(Iterable<? extends Method> a, Iterable<? extends Method> b) throws Exception {
        Iterator<? extends Method> ia = a.iterator();
        Iterator<? extends Method> ib = b.iterator();
        while (ia.hasNext()) {
            if (!ib.hasNext()) return false;
            if (!methodEquals(ia.next(), ib.next())) return false;
        }
        return !ib.hasNext();
    }

    private static boolean methodEquals(Method a, Method b) throws Exception {
        if (a.getAccessFlags() != b.getAccessFlags()) return false;
        if (!Objects.equals(a.getName(), b.getName())) return false;
        if (!Objects.equals(a.getReturnType(), b.getReturnType())) return false;
        if (!restrictionsEqual(a.getHiddenApiRestrictions(), b.getHiddenApiRestrictions())) return false;
        if (!annotationsEqual(a.getAnnotations(), b.getAnnotations())) return false;

        List<? extends MethodParameter> pa = a.getParameters();
        List<? extends MethodParameter> pb = b.getParameters();
        if (pa.size() != pb.size()) return false;
        for (int i = 0; i < pa.size(); i++) {
            MethodParameter xa = pa.get(i);
            MethodParameter xb = pb.get(i);
            if (!Objects.equals(xa.getType(), xb.getType())) return false;
            // parameter names come from debug info; baksmali only renders them
            // when the debug info option is on
            if (SharedSmaliUtils.OPTIONS.debugInfo && !Objects.equals(xa.getName(), xb.getName())) return false;
            if (!annotationsEqual(xa.getAnnotations(), xb.getAnnotations())) return false;
        }

        MethodImplementation implA = a.getImplementation();
        MethodImplementation implB = b.getImplementation();
        if (implA == null || implB == null) return implA == implB;
        if (implA.getRegisterCount() != implB.getRegisterCount()) return false;
        if (!instructionsEqual(implA.getInstructions(), implB.getInstructions())) return false;
        if (!tryBlocksEqual(implA.getTryBlocks(), implB.getTryBlocks())) return false;
        if (SharedSmaliUtils.OPTIONS.debugInfo && !debugItemsEqual(implA.getDebugItems(), implB.getDebugItems())) {
            return false;
        }
        return true;
    }

    private static boolean instructionsEqual(Iterable<? extends Instruction> a, Iterable<? extends Instruction> b) throws Exception {
        Iterator<? extends Instruction> ia = a.iterator();
        Iterator<? extends Instruction> ib = b.iterator();
        while (ia.hasNext()) {
            if (!ib.hasNext()) return false;
            if (!instructionEquals(ia.next(), ib.next())) return false;
        }
        return !ib.hasNext();
    }

    private static boolean instructionEquals(Instruction a, Instruction b) throws Exception {
        Opcode opcode = a.getOpcode();
        if (opcode != b.getOpcode()) return false;
        if (opcode.odexOnly()) {
            // odex quirks are rendered through the analysis path; let the text
            // comparison handle them
            throw new UnsupportedOperationException("odex instruction: " + opcode);
        }
        // Every reference-bearing instruction compares its reference
        // structurally. (Format20bc is odex-only, so it bails out above.)
        // Register operands are handled per-format below.
        if (a instanceof ReferenceInstruction xa) {
            if (!(b instanceof ReferenceInstruction xb)) return false;
            if (!referenceEquals(xa.getReference(), xb.getReference())) return false;
        } else if (b instanceof ReferenceInstruction) {
            return false;
        }
        switch (opcode.format) {
            case Format21c: {
                Instruction21c xa = (Instruction21c) a;
                Instruction21c xb = (Instruction21c) b;
                return xa.getRegisterA() == xb.getRegisterA();
            }
            case Format22c: {
                Instruction22c xa = (Instruction22c) a;
                Instruction22c xb = (Instruction22c) b;
                return xa.getRegisterA() == xb.getRegisterA()
                        && xa.getRegisterB() == xb.getRegisterB();
            }
            case Format31c: {
                Instruction31c xa = (Instruction31c) a;
                Instruction31c xb = (Instruction31c) b;
                return xa.getRegisterA() == xb.getRegisterA();
            }
            case Format35c: {
                Instruction35c xa = (Instruction35c) a;
                Instruction35c xb = (Instruction35c) b;
                return invokeRegistersEqual(xa, xb);
            }
            case Format3rc: {
                Instruction3rc xa = (Instruction3rc) a;
                Instruction3rc xb = (Instruction3rc) b;
                return xa.getRegisterCount() == xb.getRegisterCount()
                        && xa.getStartRegister() == xb.getStartRegister();
            }
            case Format45cc: {
                Instruction45cc xa = (Instruction45cc) a;
                Instruction45cc xb = (Instruction45cc) b;
                return invokeRegistersEqual(xa, xb)
                        && referenceEquals(xa.getReference2(), xb.getReference2());
            }
            case Format4rcc: {
                Instruction4rcc xa = (Instruction4rcc) a;
                Instruction4rcc xb = (Instruction4rcc) b;
                return xa.getRegisterCount() == xb.getRegisterCount()
                        && xa.getStartRegister() == xb.getStartRegister()
                        && referenceEquals(xa.getReference2(), xb.getReference2());
            }
            default: {
                // Non-reference formats: compare every rendered operand
                // structurally. getCodeUnits() is only the instruction size,
                // not the operand bytes, so it can't be used here. Opcode
                // equality was already checked above.
                if (a instanceof ArrayPayload xa) {
                    if (!(b instanceof ArrayPayload xb)) return false;
                    if (xa.getElementWidth() != xb.getElementWidth()) return false;
                    return Objects.equals(xa.getArrayElements(), xb.getArrayElements());
                }
                if (a instanceof PackedSwitchPayload xa) {
                    if (!(b instanceof PackedSwitchPayload xb)) return false;
                    return switchElementsEqual(xa.getSwitchElements(), xb.getSwitchElements());
                }
                if (a instanceof SparseSwitchPayload xa) {
                    if (!(b instanceof SparseSwitchPayload xb)) return false;
                    return switchElementsEqual(xa.getSwitchElements(), xb.getSwitchElements());
                }
                if (a instanceof VerificationErrorInstruction xa) {
                    if (!(b instanceof VerificationErrorInstruction xb)) return false;
                    if (xa.getVerificationError() != xb.getVerificationError()) return false;
                } else if (b instanceof VerificationErrorInstruction) {
                    return false;
                }
                if (a instanceof FieldOffsetInstruction xa) {
                    if (!(b instanceof FieldOffsetInstruction xb)) return false;
                    if (xa.getFieldOffset() != xb.getFieldOffset()) return false;
                } else if (b instanceof FieldOffsetInstruction) {
                    return false;
                }
                if (a instanceof InlineIndexInstruction xa) {
                    if (!(b instanceof InlineIndexInstruction xb)) return false;
                    if (xa.getInlineIndex() != xb.getInlineIndex()) return false;
                } else if (b instanceof InlineIndexInstruction) {
                    return false;
                }
                if (a instanceof VtableIndexInstruction xa) {
                    if (!(b instanceof VtableIndexInstruction xb)) return false;
                    if (xa.getVtableIndex() != xb.getVtableIndex()) return false;
                } else if (b instanceof VtableIndexInstruction) {
                    return false;
                }
                if (a instanceof WideLiteralInstruction xa) {
                    if (!(b instanceof WideLiteralInstruction xb)) return false;
                    if (xa.getWideLiteral() != xb.getWideLiteral()) return false;
                } else if (b instanceof WideLiteralInstruction) {
                    return false;
                }
                if (a instanceof OffsetInstruction xa) {
                    if (!(b instanceof OffsetInstruction xb)) return false;
                    if (xa.getCodeOffset() != xb.getCodeOffset()) return false;
                } else if (b instanceof OffsetInstruction) {
                    return false;
                }
                if (a instanceof ThreeRegisterInstruction xa) {
                    if (!(b instanceof ThreeRegisterInstruction xb)) return false;
                    if (xa.getRegisterA() != xb.getRegisterA()
                            || xa.getRegisterB() != xb.getRegisterB()
                            || xa.getRegisterC() != xb.getRegisterC()) return false;
                } else if (a instanceof TwoRegisterInstruction xa) {
                    if (!(b instanceof TwoRegisterInstruction xb)) return false;
                    if (xa.getRegisterA() != xb.getRegisterA()
                            || xa.getRegisterB() != xb.getRegisterB()) return false;
                } else if (a instanceof OneRegisterInstruction xa) {
                    if (!(b instanceof OneRegisterInstruction xb)) return false;
                    if (xa.getRegisterA() != xb.getRegisterA()) return false;
                } else if (b instanceof OneRegisterInstruction) {
                    return false;
                }
                return true;
            }
        }
    }

    /** Only the registers the renderer actually prints (count-driven) are compared. */
    private static boolean invokeRegistersEqual(FiveRegisterInstruction a, FiveRegisterInstruction b) {
        int count = a.getRegisterCount();
        if (count != b.getRegisterCount()) return false;
        if (a.getRegisterC() != b.getRegisterC()) return false;
        if (count > 1 && a.getRegisterD() != b.getRegisterD()) return false;
        if (count > 2 && a.getRegisterE() != b.getRegisterE()) return false;
        if (count > 3 && a.getRegisterF() != b.getRegisterF()) return false;
        if (count > 4 && a.getRegisterG() != b.getRegisterG()) return false;
        return true;
    }

    private static boolean referenceEquals(Reference a, Reference b) throws Exception {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof StringReference sa) {
            return b instanceof StringReference sb && Objects.equals(sa.getString(), sb.getString());
        }
        if (a instanceof TypeReference ta) {
            return b instanceof TypeReference tb && Objects.equals(ta.getType(), tb.getType());
        }
        if (a instanceof FieldReference fa) {
            return b instanceof FieldReference fb
                    && Objects.equals(fa.getDefiningClass(), fb.getDefiningClass())
                    && Objects.equals(fa.getName(), fb.getName())
                    && Objects.equals(fa.getType(), fb.getType());
        }
        if (a instanceof MethodReference ma) {
            return b instanceof MethodReference mb
                    && Objects.equals(ma.getDefiningClass(), mb.getDefiningClass())
                    && Objects.equals(ma.getName(), mb.getName())
                    && Objects.equals(ma.getReturnType(), mb.getReturnType())
                    && charSequencesEqual(ma.getParameterTypes(), mb.getParameterTypes());
        }
        if (a instanceof MethodProtoReference pa) {
            return b instanceof MethodProtoReference pb
                    && Objects.equals(pa.getReturnType(), pb.getReturnType())
                    && charSequencesEqual(pa.getParameterTypes(), pb.getParameterTypes());
        }
        if (a instanceof CallSiteReference ca) {
            return b instanceof CallSiteReference cb
                    && Objects.equals(ca.getMethodName(), cb.getMethodName())
                    && referenceEquals(ca.getMethodHandle(), cb.getMethodHandle())
                    && referenceEquals(ca.getMethodProto(), cb.getMethodProto())
                    && encodedValuesEqual(ca.getExtraArguments(), cb.getExtraArguments());
        }
        if (a instanceof MethodHandleReference ha) {
            return b instanceof MethodHandleReference hb
                    && ha.getMethodHandleType() == hb.getMethodHandleType()
                    && referenceEquals(ha.getMemberReference(), hb.getMemberReference());
        }
        return false;
    }

    private static boolean tryBlocksEqual(List<? extends TryBlock<? extends ExceptionHandler>> a,
                                          List<? extends TryBlock<? extends ExceptionHandler>> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            TryBlock<? extends ExceptionHandler> ta = a.get(i);
            TryBlock<? extends ExceptionHandler> tb = b.get(i);
            if (ta.getStartCodeAddress() != tb.getStartCodeAddress()) return false;
            if (ta.getCodeUnitCount() != tb.getCodeUnitCount()) return false;
            List<? extends ExceptionHandler> ha = ta.getExceptionHandlers();
            List<? extends ExceptionHandler> hb = tb.getExceptionHandlers();
            if (ha.size() != hb.size()) return false;
            for (int j = 0; j < ha.size(); j++) {
                ExceptionHandler xa = ha.get(j);
                ExceptionHandler xb = hb.get(j);
                if (xa.getHandlerCodeAddress() != xb.getHandlerCodeAddress()) return false;
                if (!Objects.equals(xa.getExceptionType(), xb.getExceptionType())) return false;
            }
        }
        return true;
    }

    private static boolean switchElementsEqual(List<? extends SwitchElement> a,
                                               List<? extends SwitchElement> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).getKey() != b.get(i).getKey()) return false;
            if (a.get(i).getOffset() != b.get(i).getOffset()) return false;
        }
        return true;
    }

    private static boolean debugItemsEqual(Iterable<? extends DebugItem> a, Iterable<? extends DebugItem> b) throws Exception {
        Iterator<? extends DebugItem> ia = a.iterator();
        Iterator<? extends DebugItem> ib = b.iterator();
        while (ia.hasNext()) {
            if (!ib.hasNext()) return false;
            if (!debugItemEquals(ia.next(), ib.next())) return false;
        }
        return !ib.hasNext();
    }

    private static boolean debugItemEquals(DebugItem a, DebugItem b) {
        if (a.getCodeAddress() != b.getCodeAddress()) return false;
        if (a instanceof LineNumber la) {
            return b instanceof LineNumber lb && la.getLineNumber() == lb.getLineNumber();
        }
        if (a instanceof StartLocal sa) {
            return b instanceof StartLocal sb
                    && sa.getRegister() == sb.getRegister()
                    && Objects.equals(sa.getName(), sb.getName())
                    && Objects.equals(sa.getType(), sb.getType())
                    && Objects.equals(sa.getSignature(), sb.getSignature());
        }
        if (a instanceof EndLocal ea) {
            return b instanceof EndLocal eb && ea.getRegister() == eb.getRegister();
        }
        if (a instanceof RestartLocal ra) {
            return b instanceof RestartLocal rb && ra.getRegister() == rb.getRegister();
        }
        if (a instanceof PrologueEnd) return b instanceof PrologueEnd;
        if (a instanceof EpilogueBegin) return b instanceof EpilogueBegin;
        if (a instanceof SetSourceFile sa) {
            return b instanceof SetSourceFile sb && Objects.equals(sa.getSourceFile(), sb.getSourceFile());
        }
        return false;
    }

    private static boolean annotationsEqual(Collection<? extends Annotation> a,
                                            Collection<? extends Annotation> b) throws Exception {
        if (a.size() != b.size()) return false;
        Iterator<? extends Annotation> ia = a.iterator();
        Iterator<? extends Annotation> ib = b.iterator();
        while (ia.hasNext()) {
            Annotation xa = ia.next();
            Annotation xb = ib.next();
            if (xa.getVisibility() != xb.getVisibility()) return false;
            if (!Objects.equals(xa.getType(), xb.getType())) return false;
            if (!annotationElementsEqual(xa.getElements(), xb.getElements())) return false;
        }
        return true;
    }

    private static boolean annotationElementsEqual(Set<? extends AnnotationElement> a,
                                                   Set<? extends AnnotationElement> b) throws Exception {
        if (a.size() != b.size()) return false;
        Iterator<? extends AnnotationElement> ia = a.iterator();
        Iterator<? extends AnnotationElement> ib = b.iterator();
        while (ia.hasNext()) {
            AnnotationElement xa = ia.next();
            AnnotationElement xb = ib.next();
            if (!Objects.equals(xa.getName(), xb.getName())) return false;
            if (!encodedValueEquals(xa.getValue(), xb.getValue())) return false;
        }
        return true;
    }

    private static boolean encodedValueEquals(EncodedValue a, EncodedValue b) throws Exception {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a instanceof BooleanEncodedValue xa) {
            return b instanceof BooleanEncodedValue xb && xa.getValue() == xb.getValue();
        }
        if (a instanceof ByteEncodedValue xa) {
            return b instanceof ByteEncodedValue xb && xa.getValue() == xb.getValue();
        }
        if (a instanceof ShortEncodedValue xa) {
            return b instanceof ShortEncodedValue xb && xa.getValue() == xb.getValue();
        }
        if (a instanceof CharEncodedValue xa) {
            return b instanceof CharEncodedValue xb && xa.getValue() == xb.getValue();
        }
        if (a instanceof IntEncodedValue xa) {
            return b instanceof IntEncodedValue xb && xa.getValue() == xb.getValue();
        }
        if (a instanceof LongEncodedValue xa) {
            return b instanceof LongEncodedValue xb && xa.getValue() == xb.getValue();
        }
        if (a instanceof FloatEncodedValue xa) {
            return b instanceof FloatEncodedValue xb
                    && Float.floatToRawIntBits(xa.getValue()) == Float.floatToRawIntBits(xb.getValue());
        }
        if (a instanceof DoubleEncodedValue xa) {
            return b instanceof DoubleEncodedValue xb
                    && Double.doubleToRawLongBits(xa.getValue()) == Double.doubleToRawLongBits(xb.getValue());
        }
        if (a instanceof StringEncodedValue xa) {
            return b instanceof StringEncodedValue xb && Objects.equals(xa.getValue(), xb.getValue());
        }
        if (a instanceof TypeEncodedValue xa) {
            return b instanceof TypeEncodedValue xb && Objects.equals(xa.getValue(), xb.getValue());
        }
        if (a instanceof EnumEncodedValue xa) {
            return b instanceof EnumEncodedValue xb && referenceEquals(xa.getValue(), xb.getValue());
        }
        if (a instanceof FieldEncodedValue xa) {
            return b instanceof FieldEncodedValue xb && referenceEquals(xa.getValue(), xb.getValue());
        }
        if (a instanceof MethodEncodedValue xa) {
            return b instanceof MethodEncodedValue xb && referenceEquals(xa.getValue(), xb.getValue());
        }
        if (a instanceof MethodHandleEncodedValue xa) {
            return b instanceof MethodHandleEncodedValue xb && referenceEquals(xa.getValue(), xb.getValue());
        }
        if (a instanceof MethodTypeEncodedValue xa) {
            return b instanceof MethodTypeEncodedValue xb && referenceEquals(xa.getValue(), xb.getValue());
        }
        if (a instanceof NullEncodedValue) return b instanceof NullEncodedValue;
        if (a instanceof AnnotationEncodedValue xa) {
            return b instanceof AnnotationEncodedValue xb
                    && Objects.equals(xa.getType(), xb.getType())
                    && annotationElementsEqual(xa.getElements(), xb.getElements());
        }
        if (a instanceof ArrayEncodedValue xa) {
            return b instanceof ArrayEncodedValue xb && encodedValuesEqual(xa.getValue(), xb.getValue());
        }
        return false;
    }

    private static boolean encodedValuesEqual(List<? extends EncodedValue> a, List<? extends EncodedValue> b) throws Exception {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!encodedValueEquals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static boolean restrictionsEqual(Set<HiddenApiRestriction> a, Set<HiddenApiRestriction> b) {
        if (a.size() != b.size()) return false;
        // EnumSet iterates in ordinal order on both sides
        Iterator<HiddenApiRestriction> ia = a.iterator();
        Iterator<HiddenApiRestriction> ib = b.iterator();
        while (ia.hasNext()) {
            if (ia.next() != ib.next()) return false;
        }
        return true;
    }

    private static boolean listEquals(List<String> a, List<String> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static boolean charSequencesEqual(List<? extends CharSequence> a, List<? extends CharSequence> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).toString(), b.get(i).toString())) return false;
        }
        return true;
    }
}

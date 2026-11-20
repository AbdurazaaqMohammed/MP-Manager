package io.github.abdurazaaqmohammed.feature.apk;

import androidx.annotation.NonNull;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.HiddenApiRestriction;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Field;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.dexlib2.Opcode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Dex Anti-Confuse: removes the dead array-payload junk that dex "confuse"/protect
 * tools inject into method bodies, and reports exactly what it did.
 *
 * <p><b>The transformation being inverted.</b> REAndroid APKEditor's protect command
 * (vendored in this repo as {@code apkkit}, see
 * {@code com.reandroid.apkeditor.protect.DexArrayPayloadConfuser}) appends one bogus
 * {@code fill-array-data-payload} pseudo-instruction (ARRAY_PAYLOAD, opcode 0x0300) to
 * the end of every non-empty method body. The payload's element width is always odd,
 * drawn from {3,5,7,...,19}, with 1..6 elements of random bytes. Nothing ever branches
 * to it, so it is unreachable dead data; per the Dalvik bytecode spec payload
 * pseudo-instructions "must never be encountered during the normal flow of execution",
 * which is why the dex still runs — but decompilers choke on it.</p>
 *
 * <p><b>Why that is a safe discriminator.</b> Real compilers (dx/d8) emit array payloads
 * with element widths 1/2/4/8 and always reference them from a {@code fill-array-data}
 * instruction. The junk payload is (a) the last instruction of the method, (b) odd-width
 * 3..19 — a width no compiler emits — and (c) not used by anything: no branch or data
 * target reaches the payload interval, no try range or handler address touches it, no
 * debug item sits at or after it, and switch-bearing methods are skipped outright. A
 * trailing payload that fails any check is left untouched, so legit referenced payloads
 * survive.</p>
 *
 * <p><b>Honesty contract.</b> Counts are exact. When nothing matches, the input dex is
 * not rewritten at all (no output written, no silent churn) and {@link Result#changed()}
 * is false, so callers can report a true no-op. This tool does <i>not</i> undo identifier
 * renaming or string encryption — renaming is impossible to invert without the original
 * mapping file, and string decryption is a separate tool ({@link DexStringDecryptor}).</p>
 *
 * <p><b>Byte-faithfulness.</b> No dexlib2 builder or immutable re-encoding is used:
 * surviving instructions keep their original {@code DexBacked} forms, and removal is a
 * delegating view that hides the last instruction. Because the removed instruction is
 * the last one, every surviving instruction's code address is unchanged, so all relative
 * offsets, try ranges and debug addresses stay valid without rewriting. If the dex
 * contains a non-standard surviving payload width (not 1/2/4/8) the whole file is left
 * untouched with an explanatory error, because writing such a payload back would go
 * through {@code getArrayElements()}, which dexlib2 restricts to standard widths.</p>
 */
public final class DexAntiConfuse {

    private DexAntiConfuse() {}

    /** The confuser's junk element width range: odd values from 3 through 19. */
    static final int JUNK_MIN_WIDTH = 3;
    static final int JUNK_MAX_WIDTH = 19;

    /** Exact, per-run outcome. Every field is what actually happened, nothing padded. */
    public static final class Result {
        public final int filesScanned;
        public final int filesChanged;
        public final int methodsScanned;
        public final int payloadsRemoved;

        Result(int filesScanned, int filesChanged, int methodsScanned, int payloadsRemoved) {
            this.filesScanned = filesScanned;
            this.filesChanged = filesChanged;
            this.methodsScanned = methodsScanned;
            this.payloadsRemoved = payloadsRemoved;
        }

        /** True only if the dex actually changed on disk. */
        public boolean changed() {
            return payloadsRemoved > 0;
        }

        /** Human-readable breakdown for dialogs and logs; states zero counts as zero. */
        public String summary() {
            return "Dex Anti-Confuse: removed " + payloadsRemoved
                    + " injected array-payload" + (payloadsRemoved == 1 ? "" : "s")
                    + " from " + filesChanged + " of " + filesScanned + " dex file"
                    + (filesScanned == 1 ? "" : "s")
                    + " (" + methodsScanned + " methods scanned)";
        }
    }

    /**
     * Scans one dex file, removes confuser array-payload junk, writes the cleaned dex
     * to {@code dexOut} only when at least one payload was removed. When nothing
     * matches, {@code dexOut} is not created and the input is left untouched — the
     * result then reports an honest no-op instead of a cosmetic rewrite.
     *
     * @throws IOException when the dex carries a non-standard surviving payload width;
     *     the file is left untouched in that case.
     */
    public static Result cleanDex(File dexIn, File dexOut) throws IOException {
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexIn, null);
        WorkingDex working = cleanDexModel(dex);
        if (!working.changed) {
            return new Result(1, 0, working.methodsScanned, working.payloadsRemoved);
        }
        writeDex(working.pool, dexOut);
        return new Result(1, 1, working.methodsScanned, working.payloadsRemoved);
    }

    /**
     * Scans several dex files (e.g. classes.dex + classes2.dex), cleaning each into the
     * output directory under its own name. Files with no junk are not rewritten, so a
     * no-junk dex stays byte-identical on disk.
     */
    public static Result cleanDexFiles(List<File> dexFiles, File outputDir) throws IOException {
        int methodsScanned = 0;
        int payloadsRemoved = 0;
        int filesChanged = 0;
        for (File f : dexFiles) {
            DexBackedDexFile dex = DexFileFactory.loadDexFile(f, null);
            WorkingDex working = cleanDexModel(dex);
            methodsScanned += working.methodsScanned;
            payloadsRemoved += working.payloadsRemoved;
            if (working.changed) {
                File out = new File(outputDir, f.getName());
                writeDex(working.pool, out);
                filesChanged++;
            }
        }
        return new Result(dexFiles.size(), filesChanged, methodsScanned, payloadsRemoved);
    }

    private static final class WorkingDex {
        final DexPool pool;
        boolean changed;
        boolean nonStandardPayload;
        int methodsScanned;
        int payloadsRemoved;

        WorkingDex(DexPool pool) {
            this.pool = pool;
        }
    }

    private static WorkingDex cleanDexModel(DexBackedDexFile dex) throws IOException {
        DexPool pool = new DexPool(dex.getOpcodes());
        WorkingDex out = new WorkingDex(pool);
        for (ClassDef classDef : dex.getClasses()) {
            boolean classChanged = false;
            List<Method> direct = new ArrayList<>();
            List<Method> virtual = new ArrayList<>();

            for (Method m : classDef.getDirectMethods()) {
                Method cleaned = cleanMethod(m, out);
                if (cleaned != m) {
                    classChanged = true;
                }
                direct.add(cleaned);
            }
            for (Method m : classDef.getVirtualMethods()) {
                Method cleaned = cleanMethod(m, out);
                if (cleaned != m) {
                    classChanged = true;
                }
                virtual.add(cleaned);
            }

            if (classChanged) {
                // Delegating view only: original annotations, fields, source file and
                // debug items pass through untouched — this tool removes dead
                // instructions and must not touch anything else.
                pool.internClass(new FilteredClassDef(classDef, direct, virtual));
                out.changed = true;
            } else {
                pool.internClass(classDef);
            }
        }
        if (out.nonStandardPayload) {
            throw new IOException("DexAntiConfuse: non-standard array payload width found "
                    + "(only widths 1/2/4/8 can be re-serialized); file left untouched");
        }
        return out;
    }

    /**
     * Removes one trailing junk payload from the method if (and only if) it carries the
     * confuser's signature. Returns the original method instance when untouched, so the
     * caller can detect real changes by reference comparison.
     */
    private static Method cleanMethod(Method method, WorkingDex out) {
        MethodImplementation impl = method.getImplementation();
        if (impl == null) {
            return method;
        }
        out.methodsScanned++;
        // dexlib2 exposes instructions as an Iterable; index it once for the signature
        // checks. Deliberately NOT routed through MutableMethodImplementation or the
        // immutable forms: those materialize payloads via getArrayElements(), which
        // throws ExceptionWithContext for any element width other than 1/2/4/8 — i.e.
        // they would crash on exactly the junk widths this tool is meant to remove.
        List<Instruction> ins = new ArrayList<>();
        for (Instruction i : impl.getInstructions()) {
            ins.add(i);
        }
        int n = ins.size();
        if (n == 0) {
            return method;
        }
        Instruction last = ins.get(n - 1);
        boolean junk = isJunkPayloadSignature(last, impl, ins);

        // Survivors (everything except the removed trailing junk) must be re-serializable:
        // a surviving non-standard payload width would crash the dex writer, so such
        // files are reported and left untouched instead of being partially rewritten.
        int survivorEnd = junk ? n - 1 : n;
        for (int i = 0; i < survivorEnd; i++) {
            Instruction inst = ins.get(i);
            if (inst instanceof ArrayPayload) {
                int w = ((ArrayPayload) inst).getElementWidth();
                if (w != 1 && w != 2 && w != 4 && w != 8) {
                    out.nonStandardPayload = true;
                }
            }
        }

        if (!junk) {
            return method;
        }
        out.payloadsRemoved++;
        return new FilteredMethod(method, new TailFilteredMethodImplementation(impl, n - 1));
    }

    /**
     * The confuser signature: trailing ARRAY_PAYLOAD with odd element width 3..19 that
     * is not in use anywhere in the method. Each condition is load-bearing: width alone
     * is not trusted, and neither is position alone.
     */
    static boolean isJunkPayloadSignature(Instruction last, MethodImplementation impl,
                                          List<? extends Instruction> ins) {
        if (!(last instanceof ArrayPayload)) {
            return false;
        }
        int width = ((ArrayPayload) last).getElementWidth();
        if (width < JUNK_MIN_WIDTH || width > JUNK_MAX_WIDTH || (width % 2) == 0) {
            return false;
        }
        return !isPayloadInUse(impl, ins);
    }

    /**
     * True when the trailing payload interval is used by anything in the method. The
     * payload spans {@code [payloadStart, payloadEnd)} code units and every check is
     * deliberately over-inclusive — a wrongly removed payload breaks the method:
     * <ul>
     *   <li>any branch or data-offset instruction ({@code goto}, {@code if-*},
     *       {@code packed-switch}/{@code sparse-switch}, {@code fill-array-data}) whose
     *       target address reaches the payload start or beyond;</li>
     *   <li>any switch payload present in the method — switch element offsets are
     *       relative to the switch instruction, and a target landing inside the tail
     *       interval cannot be ruled out cheaply, so switch-bearing methods are skipped;</li>
     *   <li>any try-block range intersecting the payload interval, or any exception
     *       handler address at or after the payload start;</li>
     *   <li>any debug item at or after the payload start.</li>
     * </ul>
     * Offsets are accumulated with {@link Instruction#getCodeUnits()} (16-bit code
     * units); Dalvik branch offsets are relative to the referencing instruction's own
     * address.
     */
    static boolean isPayloadInUse(MethodImplementation impl, List<? extends Instruction> ins) {
        int n = ins.size();
        if (n == 0) {
            return false;
        }
        int payloadStart = 0;
        int offset = 0;
        for (int i = 0; i < n; i++) {
            if (i == n - 1) {
                payloadStart = offset;
                break;
            }
            offset += ins.get(i).getCodeUnits();
        }
        int payloadEnd = payloadStart + ins.get(n - 1).getCodeUnits();

        offset = 0;
        for (int i = 0; i < n - 1; i++) {
            Instruction inst = ins.get(i);
            if (inst instanceof OffsetInstruction) {
                int target = offset + ((OffsetInstruction) inst).getCodeOffset();
                if (target >= payloadStart) {
                    return true;
                }
            }
            if (inst instanceof SwitchPayload) {
                // Skip switch-bearing methods entirely: switch element targets are
                // relative to the switch instruction, and conservatively ruling out a
                // tail target is cheaper than computing every element's base.
                return true;
            }
            offset += inst.getCodeUnits();
        }
        for (TryBlock<? extends ExceptionHandler> tryBlock : impl.getTryBlocks()) {
            int start = tryBlock.getStartCodeAddress();
            int end = start + tryBlock.getCodeUnitCount();
            if (payloadStart < end && start < payloadEnd) {
                return true;
            }
            for (ExceptionHandler handler : tryBlock.getExceptionHandlers()) {
                if (handler.getHandlerCodeAddress() >= payloadStart) {
                    return true;
                }
            }
        }
        for (DebugItem item : impl.getDebugItems()) {
            if (item.getCodeAddress() >= payloadStart) {
                return true;
            }
        }
        return false;
    }

    /**
     * A MethodImplementation that hides one instruction (the trailing junk payload).
     * Everything else — earlier instructions byte-for-byte through their original
     * DexBacked forms, try blocks, and debug items — is delegated untouched. Because
     * the removed instruction is the last one, no earlier instruction's code address
     * changes, so all relative branch offsets and try/debug ranges remain valid
     * without any offset rewriting.
     */
    private static final class TailFilteredMethodImplementation implements MethodImplementation {
        private final MethodImplementation delegate;
        private final int removedIndex;

        TailFilteredMethodImplementation(MethodImplementation delegate, int removedIndex) {
            this.delegate = delegate;
            this.removedIndex = removedIndex;
        }

        @Override
        public int getRegisterCount() {
            return delegate.getRegisterCount();
        }

        @NonNull
        @Override
        public Iterable<? extends Instruction> getInstructions() {
            int index = 0;
            List<Instruction> out = new ArrayList<>();
            for (Instruction inst : delegate.getInstructions()) {
                if (index++ != removedIndex) {
                    out.add(inst);
                }
            }
            return out;
        }

        @NonNull
        @Override
        public List<? extends TryBlock<? extends ExceptionHandler>> getTryBlocks() {
            return delegate.getTryBlocks();
        }

        @NonNull
        @Override
        public Iterable<? extends DebugItem> getDebugItems() {
            return delegate.getDebugItems();
        }
    }

    /** Delegating Method that swaps only the implementation for the filtered view. */
    private static final class FilteredMethod implements Method {
        private final Method delegate;
        private final MethodImplementation filtered;

        FilteredMethod(Method delegate, MethodImplementation filtered) {
            this.delegate = delegate;
            this.filtered = filtered;
        }

        @Override @NonNull public String getDefiningClass() { return delegate.getDefiningClass(); }
        @Override @NonNull public String getName() { return delegate.getName(); }
        @Override @NonNull public List<? extends MethodParameter> getParameters() { return delegate.getParameters(); }
        @Override @NonNull public List<? extends CharSequence> getParameterTypes() { return delegate.getParameterTypes(); }
        @Override @NonNull public String getReturnType() { return delegate.getReturnType(); }
        @Override public int getAccessFlags() { return delegate.getAccessFlags(); }
        @Override @NonNull public Set<? extends Annotation> getAnnotations() { return delegate.getAnnotations(); }
        @Override @NonNull public Set<HiddenApiRestriction> getHiddenApiRestrictions() { return delegate.getHiddenApiRestrictions(); }
        @Override public MethodImplementation getImplementation() { return filtered; }
        @Override public int compareTo(@NonNull MethodReference o) { return delegate.compareTo(o); }
        @Override public void validateReference() throws Reference.InvalidReferenceException { delegate.validateReference(); }
    }

    /**
     * Delegating ClassDef that swaps only the method lists for ones containing filtered
     * methods. Annotations, fields, source file and everything else are the original
     * DexBacked objects — nothing is re-encoded.
     */
    private static final class FilteredClassDef implements ClassDef {
        private final ClassDef delegate;
        private final List<Method> direct;
        private final List<Method> virtual;

        FilteredClassDef(ClassDef delegate, List<Method> direct, List<Method> virtual) {
            this.delegate = delegate;
            this.direct = direct;
            this.virtual = virtual;
        }

        @Override @NonNull public String getType() { return delegate.getType(); }
        @Override public int getAccessFlags() { return delegate.getAccessFlags(); }
        @Override public String getSuperclass() { return delegate.getSuperclass(); }
        @Override @NonNull public List<String> getInterfaces() { return delegate.getInterfaces(); }
        @Override public String getSourceFile() { return delegate.getSourceFile(); }
        @Override @NonNull public Set<? extends Annotation> getAnnotations() { return delegate.getAnnotations(); }
        @Override @NonNull public Iterable<? extends Field> getStaticFields() { return delegate.getStaticFields(); }
        @Override @NonNull public Iterable<? extends Field> getInstanceFields() { return delegate.getInstanceFields(); }
        @Override @NonNull public Iterable<? extends Field> getFields() { return delegate.getFields(); }
        @Override @NonNull public Iterable<? extends Method> getDirectMethods() { return direct; }
        @Override @NonNull public Iterable<? extends Method> getVirtualMethods() { return virtual; }
        @Override @NonNull public Iterable<? extends Method> getMethods() {
            List<Method> all = new ArrayList<>(direct);
            all.addAll(virtual);
            return all;
        }
        @Override public int compareTo(@NonNull CharSequence o) { return delegate.compareTo(o); }
        @Override public void validateReference() throws Reference.InvalidReferenceException { delegate.validateReference(); }
        @Override public int length() { return delegate.length(); }
        @Override public char charAt(int index) { return delegate.charAt(index); }
        @Override @NonNull public CharSequence subSequence(int start, int end) { return delegate.subSequence(start, end); }
        @Override @NonNull public String toString() { return delegate.toString(); }
    }

    private static void writeDex(DexPool pool, File dexOut) throws IOException {
        MemoryDataStore store = new MemoryDataStore();
        pool.writeTo(store);
        byte[] data = Arrays.copyOf(store.getData(), store.getSize());
        File parent = dexOut.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (OutputStream os = new FileOutputStream(dexOut)) {
            os.write(data);
        }
    }
}

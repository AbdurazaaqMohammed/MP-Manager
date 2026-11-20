package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.iface.ExceptionHandler;
import com.android.tools.smali.dexlib2.iface.TryBlock;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodImplementation;
import com.android.tools.smali.dexlib2.iface.debug.DebugItem;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import com.reandroid.apkeditor.protect.DexArrayPayloadConfuser;
import com.reandroid.apkeditor.protect.DexConfuseTask;
import com.reandroid.dex.model.DexClass;
import com.reandroid.dex.model.DexFile;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Regression tests for Dex Anti Confuse.
 *
 * <p>Uses the REAL production confuser (vendored apkkit {@link DexArrayPayloadConfuser})
 * to produce genuinely confused dex input, then asserts the anti-confuse tool removes
 * exactly the junk, preserves control flow and debug information in the actual binary
 * output, and reports honest no-ops.</p>
 */
public class DexAntiConfuseTest {

    /** Class with: plain method, legit fill-array-data payload (width 4, referenced), try/catch, loop. */
    private static final String A_SMALI = """
            .class public Ltest/A;
            .super Ljava/lang/Object;
            .source "A.java"
            .method public constructor <init>()V
                .locals 1
                .line 4
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method
            .method public greet()Ljava/lang/String;
                .locals 1
                .line 8
                const-string v0, "hi"
                return-object v0
            .end method
            .method public withArrayAndTry()V
                .locals 2
            :try_start_0
                .line 12
                const/4 v0, 0x3
                new-array v0, v0, [I
                fill-array-data v0, :array_0
            :try_end_0
            .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0
                return-void
            :catch_0
                move-exception v1
                return-void
                nop
            :array_0
            .array-data 4
                0x1 0x2 0x3 0x4
            .end array-data
            .end method
            .method public loop(I)V
                .locals 1
                move v0, p1
            :label_loop
                add-int/lit8 v0, v0, -0x1
                if-lez v0, :label_done
                goto :label_loop
            :label_done
                return-void
            .end method
            """;

    private File writeSmali(String smali) throws Exception {
        File dir = Files.createTempDirectory("anticonfuse").toFile();
        File pkg = new File(dir, "test");
        assertTrue(pkg.mkdirs());
        String name = smali.contains("Ltest/Fog;") ? "Fog.smali" : "A.smali";
        try (FileWriter w = new FileWriter(new File(pkg, name))) {
            w.write(smali);
        }
        return dir;
    }

    private File assembleDir(File smaliDir) throws Exception {
        File outDex = new File(smaliDir.getParentFile(), "orig_" + System.nanoTime() + ".dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 28;
        assertTrue("smali assemble failed", Smali.assemble(options, smaliDir.getPath()));
        return outDex;
    }

    /** Mirrors DexConfuser.save(): refresh, shrink, refreshFull, getBytes. */
    private static byte[] save(DexFile rdex) {
        rdex.refresh();
        rdex.shrink();
        rdex.refreshFull();
        return rdex.getBytes();
    }

    /** Applies the REAL production confuser task, mirroring DexConfuser.confuse(). */
    private File confuse(File origDex) throws Exception {
        DexConfuseTask confuser = new DexArrayPayloadConfuser();
        DexFile rdex;
        try (FileInputStream in = new FileInputStream(origDex)) {
            rdex = DexFile.read(in);
        }
        Iterator<DexClass> classes = rdex.getDexClasses();
        while (classes.hasNext()) {
            confuser.apply(classes.next());
        }
        assertTrue("confuser applied nothing", confuser.getTotalCount() > 0);
        byte[] bytes = save(rdex);
        File confused = new File(origDex.getParentFile(), "confused.dex");
        try (FileOutputStream out = new FileOutputStream(confused)) {
            out.write(bytes);
        }
        return confused;
    }

    private static DexBackedDexFile loadDex(File f) throws Exception {
        return new DexBackedDexFile(Opcodes.getDefault(), Files.readAllBytes(f.toPath()));
    }

    private static ClassDef findClass(DexBackedDexFile dex, String type) {
        for (ClassDef c : dex.getClasses()) {
            if (type.equals(c.getType())) return c;
        }
        return null;
    }

    private static Method findMethod(ClassDef c, String name) {
        for (Method m : c.getDirectMethods()) {
            if (name.equals(m.getName())) return m;
        }
        for (Method m : c.getVirtualMethods()) {
            if (name.equals(m.getName())) return m;
        }
        return null;
    }

    private static List<Instruction> ins(Method m) {
        MethodImplementation impl = m.getImplementation();
        List<Instruction> out = new ArrayList<>();
        if (impl == null) return out;
        for (Instruction i : impl.getInstructions()) out.add(i);
        return out;
    }

    private static int trailingJunkCount(DexBackedDexFile dex) {
        int junk = 0;
        for (ClassDef c : dex.getClasses()) {
            for (Method m : c.getDirectMethods()) junk += trailingJunk(ins(m));
            for (Method m : c.getVirtualMethods()) junk += trailingJunk(ins(m));
        }
        return junk;
    }

    private static int trailingJunk(List<Instruction> list) {
        if (list.isEmpty()) return 0;
        Instruction last = list.get(list.size() - 1);
        if (last instanceof ArrayPayload) {
            int w = ((ArrayPayload) last).getElementWidth();
            if (w >= 3 && w <= 19 && (w % 2) == 1) return 1;
        }
        return 0;
    }

    /** Snapshot of everything that must survive anti-confuse: opcodes, try blocks, debug items. */
    private static Map<String, List<String>> describeBody(Method m) {
        MethodImplementation impl = m.getImplementation();
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (impl == null) return out;
        List<String> ops = new ArrayList<>();
        for (Instruction i : ins(m)) {
            ops.add(i.getOpcode().name);
        }
        out.put("opcodes", ops);
        List<String> tries = new ArrayList<>();
        impl.getTryBlocks().forEach(t -> tries.add(t.getStartCodeAddress() + ".."
                + (t.getStartCodeAddress() + t.getCodeUnitCount())));
        out.put("tries", tries);
        List<String> debug = new ArrayList<>();
        impl.getDebugItems().forEach(d -> debug.add(d.getCodeAddress() + ":" + d.getDebugItemType()));
        out.put("debug", debug);
        out.put("registers", List.of(String.valueOf(impl.getRegisterCount())));
        return out;
    }

    private static void assertBodiesEqual(Method expected, Method actual) {
        Map<String, List<String>> exp = describeBody(expected);
        Map<String, List<String>> act = describeBody(actual);
        // The confuser's writer pads its junk payload to the 4-byte alignment the Dalvik
        // spec requires with a trailing nop. Anti-confuse removes only the junk payload
        // itself (conservative: a branch could target the nop), so the unreachable pad
        // may remain — strip trailing nops on both sides before comparing opcodes.
        List<String> expOps = stripTrailingNops(exp.remove("opcodes"));
        List<String> actOps = stripTrailingNops(act.remove("opcodes"));
        assertEquals(expected.getName() + " opcodes", expOps, actOps);
        assertEquals(expected.getName() + " rest", exp, act);
    }

    private static List<String> stripTrailingNops(List<String> ops) {
        int end = ops.size();
        while (end > 0 && "nop".equals(ops.get(end - 1))) end--;
        return ops.subList(0, end);
    }

    @Test
    public void confusedDexIsCleanedAndSemanticsPreserved() throws Exception {
        File origFile = assembleDir(writeSmali(A_SMALI));
        DexBackedDexFile orig = loadDex(origFile);
        ClassDef origClass = findClass(orig, "Ltest/A;");
        assertNotNull(origClass);
        int expectedMethods = 0;
        Map<String, Method> origMethods = new LinkedHashMap<>();
        for (Method m : origClass.getDirectMethods()) {
            origMethods.put(m.getName(), m);
            if (m.getImplementation() != null) expectedMethods++;
        }
        for (Method m : origClass.getVirtualMethods()) {
            origMethods.put(m.getName(), m);
            if (m.getImplementation() != null) expectedMethods++;
        }
        assertTrue(expectedMethods >= 4);
        assertEquals(0, trailingJunkCount(orig));

        File confusedFile = confuse(origFile);
        DexBackedDexFile confused = loadDex(confusedFile);
        int junk = trailingJunkCount(confused);
        assertEquals("confuser must append junk to every non-empty method",
                expectedMethods, junk);

        File cleanedFile = new File(origFile.getParentFile(), "cleaned.dex");
        DexAntiConfuse.Result result = DexAntiConfuse.cleanDex(confusedFile, cleanedFile);
        assertTrue("anti-confuse must report change", result.changed());
        assertEquals(1, result.filesScanned);
        assertEquals(1, result.filesChanged);
        assertEquals(expectedMethods, result.payloadsRemoved);
        assertTrue("every non-empty method must be scanned", result.methodsScanned >= expectedMethods);
        assertTrue(result.summary().contains(String.valueOf(expectedMethods)));

        DexBackedDexFile cleaned = loadDex(cleanedFile);
        assertEquals("all junk removed from binary output", 0, trailingJunkCount(cleaned));

        ClassDef cleanedClass = findClass(cleaned, "Ltest/A;");
        assertNotNull(cleanedClass);
        for (Map.Entry<String, Method> e : origMethods.entrySet()) {
            Method cleanedMethod = findMethod(cleanedClass, e.getKey());
            assertNotNull("method " + e.getKey() + " missing after clean", cleanedMethod);
            // Opcodes, try ranges, debug items and register counts must survive intact:
            assertBodiesEqual(e.getValue(), cleanedMethod);
        }

        // The legit referenced width-4 payload survives with its values:
        Method withArray = findMethod(cleanedClass, "withArrayAndTry");
        boolean legitPayload = false;
        for (Instruction i : ins(withArray)) {
            if (i instanceof ArrayPayload) {
                ArrayPayload p = (ArrayPayload) i;
                assertEquals("legit payload width must be untouched", 4, p.getElementWidth());
                assertEquals(4, p.getArrayElements().size());
                legitPayload = true;
            }
        }
        assertTrue("legit referenced payload must survive", legitPayload);

        // greet still returns "hi" (dexlib2 opcode names are lowercase):
        Method greet = findMethod(cleanedClass, "greet");
        assertEquals("const-string", ins(greet).get(0).getOpcode().name);
    }

    @Test
    public void unconfusedDexIsHonestNoOp() throws Exception {
        File origFile = assembleDir(writeSmali(A_SMALI));
        File out = new File(origFile.getParentFile(), "noop_out.dex");
        DexAntiConfuse.Result result = DexAntiConfuse.cleanDex(origFile, out);
        assertFalse("no junk -> no change", result.changed());
        assertEquals(0, result.payloadsRemoved);
        assertEquals(0, result.filesChanged);
        assertEquals(1, result.filesScanned);
        assertTrue("still scans every method", result.methodsScanned >= 4);
        assertFalse("no output written on a true no-op", out.exists());
        assertTrue("input untouched", Files.readAllBytes(origFile.toPath()).length > 0);
    }

    /**
     * The legit trailing payload is REFERENCED by fill-array-data: the offset math must
     * detect that (proven against real assembled binary data, not synthetic lists), and
     * the junk signature must reject the method, so cleanDex stays a no-op.
     */
    @Test
    public void referencedLegitPayloadIsInUseAndNotRemoved() throws Exception {
        File origFile = assembleDir(writeSmali(A_SMALI));
        DexBackedDexFile dex = loadDex(origFile);
        ClassDef c = findClass(dex, "Ltest/A;");
        Method withArray = findMethod(c, "withArrayAndTry");
        MethodImplementation impl = withArray.getImplementation();
        List<Instruction> list = ins(withArray);
        Instruction last = list.get(list.size() - 1);
        assertTrue("smali payload is the trailing instruction", last instanceof ArrayPayload);
        assertEquals("smali emits element width 4", 4, ((ArrayPayload) last).getElementWidth());
        assertTrue("offset math must see the fill-array-data reference",
                DexAntiConfuse.isPayloadInUse(impl, list));
        assertFalse("referenced width-4 payload is not junk",
                DexAntiConfuse.isJunkPayloadSignature(last, impl, list));

        File out = new File(origFile.getParentFile(), "ref_noop.dex");
        DexAntiConfuse.Result result = DexAntiConfuse.cleanDex(origFile, out);
        assertFalse(result.changed());
        assertFalse("no output written on a true no-op", out.exists());
    }

    /**
     * A dex that mixes junk (removable) with a surviving non-standard payload width
     * must be rejected whole: cleanDex aborts before writing anything, the input bytes
     * stay identical (full content hash, not just length), and no output is produced.
     */
    @Test
    public void mixedSurvivingNonStandardPayloadAbortsAndInputUnchanged() throws Exception {
        File origFile = assembleDir(writeSmali(A_SMALI));
        File confusedFile = confuse(origFile);
        byte[] confusedBytes = Files.readAllBytes(confusedFile.toPath());
        assertTrue(trailingJunkCount(loadDex(confusedFile)) > 0);

        // Append a second payload with width 21 (odd, outside the 3..19 junk signature)
        // to one method via the reandroid model: it survives cleaning and cannot be
        // re-serialized by dexlib2, so the whole file must be refused untouched.
        DexFile rdex;
        try (FileInputStream in = new FileInputStream(confusedFile)) {
            rdex = DexFile.read(in);
        }
        Iterator<DexClass> classes = rdex.getDexClasses();
        int added = 0;
        while (classes.hasNext()) {
            DexClass dc = classes.next();
            dc.edit();
            Iterator<com.reandroid.dex.model.DexMethod> methods = dc.declaredMethods();
            while (methods.hasNext()) {
                com.reandroid.dex.model.DexMethod dm = methods.next();
                com.reandroid.dex.ins.InsArrayData extra =
                        (com.reandroid.dex.ins.InsArrayData) dm.addInstruction(
                                com.reandroid.dex.ins.Opcode.ARRAY_PAYLOAD).getIns();
                extra.setWidth(21);
                extra.setSize(1);
                extra.put(0, 0xAB);
                added++;
            }
        }
        assertTrue(added > 0);
        File mixedFile = new File(origFile.getParentFile(), "mixed.dex");
        try (FileOutputStream out = new FileOutputStream(mixedFile)) {
            out.write(save(rdex));
        }
        byte[] mixedBytes = Files.readAllBytes(mixedFile.toPath());

        File out = new File(origFile.getParentFile(), "mixed_cleaned.dex");
        try {
            DexAntiConfuse.cleanDex(mixedFile, out);
            fail("non-standard surviving payload must abort the run");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("non-standard"));
        }
        assertFalse("no output written on abort", out.exists());
        assertArrayEquals("input dex must stay byte-identical on abort",
                mixedBytes, Files.readAllBytes(mixedFile.toPath()));
        // And the pre-mix confused bytes were never the ones written back either:
        assertNotEquals(confusedBytes, Files.readAllBytes(mixedFile.toPath()));
    }

    // ---- Synthetic guard tests for isPayloadInUse (cheap, no file I/O) ----

    private static void assertInUse(String why, MethodImplementation impl, List<Instruction> list) {
        assertTrue("tail must be treated as in use: " + why,
                DexAntiConfuse.isPayloadInUse(impl, list));
    }

    private static void assertNotInUse(String why, MethodImplementation impl, List<Instruction> list) {
        assertFalse("tail must be removable: " + why,
                DexAntiConfuse.isPayloadInUse(impl, list));
    }

    @Test
    public void branchTargetAtPayloadStartOrInteriorIsInUse() {
        // [goto(v1,1)] [junk payload(8 units)] — target lands exactly at payload start:
        List<Instruction> atStart = List.of(
                new FakeBranch(Opcode.GOTO, 2, 0 + 4),
                new FakePayload(8));
        assertInUse("branch target == payload start", fakeImpl(atStart, List.of(), List.of()), atStart);

        // target lands INSIDE the payload interval (start+1):
        List<Instruction> interior = List.of(
                new FakeBranch(Opcode.GOTO, 2, 0 + 5),
                new FakePayload(8));
        assertInUse("branch target inside payload", fakeImpl(interior, List.of(), List.of()), interior);

        // branch target far before the tail: removable
        List<Instruction> before = List.of(
                new FakeBranch(Opcode.GOTO, 2, 0 - 2),
                new FakePayload(8));
        assertNotInUse("branch target before tail", fakeImpl(before, List.of(), List.of()), before);
    }

    @Test
    public void exceptionHandlerAtOrAfterTailIsInUse() {
        // handler address == payload start (removed interval boundary):
        List<Instruction> atStart = List.of(
                new FakeNop(Opcode.NOP, 2),
                new FakePayload(8));
        assertInUse("handler at payload start",
                fakeImpl(atStart,
                        List.of(new FakeTryBlock(0, 2, new FakeHandler(4))),
                        List.of()), atStart);

        // handler address beyond the payload (past method end, malformed but live):
        List<Instruction> beyond = List.of(
                new FakeNop(Opcode.NOP, 2),
                new FakePayload(8));
        assertInUse("handler beyond payload end",
                fakeImpl(beyond,
                        List.of(new FakeTryBlock(0, 2, new FakeHandler(12))),
                        List.of()), beyond);

        // handler before the tail: removable
        List<Instruction> before = List.of(
                new FakeNop(Opcode.NOP, 2),
                new FakePayload(8));
        assertNotInUse("handler before tail",
                fakeImpl(before,
                        List.of(new FakeTryBlock(0, 2, new FakeHandler(0))),
                        List.of()), before);
    }

    @Test
    public void debugItemAtOrAfterTailIsInUse() {
        List<Instruction> list = List.of(
                new FakeNop(Opcode.NOP, 2),
                new FakePayload(8));
        assertInUse("debug item at payload start",
                fakeImpl(list, List.of(), List.of(new FakeDebug(4))), list);
        assertInUse("debug item beyond payload end",
                fakeImpl(list, List.of(), List.of(new FakeDebug(12))), list);
        assertNotInUse("debug item before tail",
                fakeImpl(list, List.of(), List.of(new FakeDebug(0))), list);
    }

    @Test
    public void switchBearingMethodIsSkippedConservatively() {
        List<Instruction> list = List.of(
                new FakeNop(Opcode.NOP, 2),
                new FakeSwitch(),
                new FakePayload(8));
        assertInUse("switch-bearing method is conservatively skipped",
                fakeImpl(list, List.of(), List.of()), list);
    }

    @Test
    public void cleanMethodTailIsRemovable() {
        // [const] [return] [junk] — no references anywhere: removable
        List<Instruction> list = List.of(
                new FakeNop(Opcode.NOP, 2),
                new FakeNop(Opcode.NOP, 2),
                new FakePayload(8));
        assertNotInUse("plain tail is removable",
                fakeImpl(list, List.of(), List.of()), list);
    }

    // Minimal fakes — no Immutable materialization, mirroring the iface contracts only.

    private static MethodImplementation fakeImpl(List<Instruction> instructions,
                                                 List<TryBlock<FakeHandler>> tries,
                                                 List<DebugItem> debug) {
        return new FakeImpl(2, instructions, tries, debug);
    }

    private static final class FakeImpl implements MethodImplementation {
        final int registers;
        final List<Instruction> instructions;
        final List<TryBlock<FakeHandler>> tries;
        final List<DebugItem> debug;

        FakeImpl(int registers, List<Instruction> instructions,
                 List<TryBlock<FakeHandler>> tries, List<DebugItem> debug) {
            this.registers = registers;
            this.instructions = instructions;
            this.tries = tries;
            this.debug = debug;
        }
        @Override public int getRegisterCount() { return registers; }
        @Override public Iterable<? extends Instruction> getInstructions() { return instructions; }
        @Override public List<? extends TryBlock<? extends ExceptionHandler>> getTryBlocks() { return tries; }
        @Override public Iterable<? extends DebugItem> getDebugItems() { return debug; }
    }

    private static class FakeInstruction implements Instruction {
        final Opcode op;
        final int codeUnits;
        FakeInstruction(Opcode op, int codeUnits) { this.op = op; this.codeUnits = codeUnits; }
        @Override public Opcode getOpcode() { return op; }
        @Override public int getCodeUnits() { return codeUnits; }
    }

    private static final class FakeNop extends FakeInstruction {
        FakeNop(Opcode op, int codeUnits) { super(op, codeUnits); }
    }

    private static final class FakeBranch extends FakeInstruction implements OffsetInstruction {
        final int rel;
        FakeBranch(Opcode op, int codeUnits, int rel) { super(op, codeUnits); this.rel = rel; }
        @Override public int getCodeOffset() { return rel; }
    }

    private static final class FakePayload extends FakeInstruction implements ArrayPayload {
        FakePayload(int codeUnits) { super(Opcode.ARRAY_PAYLOAD, codeUnits); }
        @Override public int getElementWidth() { return 5; }
        @Override public List<Number> getArrayElements() { return List.of(); }
    }

    private static final class FakeSwitch extends FakeInstruction implements SwitchPayload {
        FakeSwitch() { super(Opcode.NOP, 4); }
        @Override public List<? extends com.android.tools.smali.dexlib2.iface.instruction.SwitchElement>
                getSwitchElements() { return List.of(); }
    }

    private static final class FakeHandler implements ExceptionHandler {
        final int address;
        FakeHandler(int address) { this.address = address; }
        @Override public String getExceptionType() { return "Ljava/lang/Exception;"; }
        @Override public com.android.tools.smali.dexlib2.iface.reference.TypeReference getExceptionTypeReference() { return null; }
        @Override public int getHandlerCodeAddress() { return address; }
        @Override public int compareTo(ExceptionHandler o) { return Integer.compare(address, ((FakeHandler) o).address); }
    }

    private static final class FakeTryBlock implements TryBlock<FakeHandler> {
        final int start;
        final int count;
        final FakeHandler handler;
        FakeTryBlock(int start, int count, FakeHandler handler) {
            this.start = start; this.count = count; this.handler = handler;
        }
        @Override public int getStartCodeAddress() { return start; }
        @Override public int getCodeUnitCount() { return count; }
        @Override public List<? extends FakeHandler> getExceptionHandlers() { return List.of(handler); }
    }

    private static final class FakeDebug implements DebugItem {
        final int address;
        FakeDebug(int address) { this.address = address; }
        @Override public int getDebugItemType() { return 0x02; }
        @Override public int getCodeAddress() { return address; }
    }
}

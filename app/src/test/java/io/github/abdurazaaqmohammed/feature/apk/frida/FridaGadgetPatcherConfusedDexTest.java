package io.github.abdurazaaqmohammed.feature.apk.frida;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import com.reandroid.apkeditor.protect.DexArrayPayloadConfuser;
import com.reandroid.dex.model.DexClass;
import com.reandroid.dex.model.DexFile;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression for the Frida Gadget injection failure
 * "ExceptionWithContext while writing code_item for Lv05;->000000([[Landroid/graphics/Rect;)V".
 *
 * <p>Root cause: the dex "confuse"/protect feature (vendored
 * {@code com.reandroid.apkeditor.protect.DexArrayPayloadConfuser}) appends an unreachable
 * {@code fill-array-data-payload} with an <b>odd element width</b> (3..19) to every non-empty
 * method. dexlib2 reads those payloads fine, but the writer could not represent them:
 * {@code InstructionWriter.write(ArrayPayload)} asks the payload for its elements and the
 * reader threw {@code ExceptionWithContext("Invalid element width: ...")} for any width
 * outside {1,2,4,8} — which surfaced as
 * {@code Exception occurred while writing code_item for method ...} in the
 * {@code writeCodeItems}/runChunked phase of {@code DexWriter}.
 *
 * <p>The patcher ({@link FridaGadgetPatcher#rewriteDexWithGadgetLoad}) round-trips the whole
 * dex through {@code DexPool}, so any confused class in the target dex broke the injection,
 * and the Immutable copy of the target's own confused {@code onCreate} broke it even earlier.
 * Both paths are exercised here, plus a legit referenced even-width payload that must survive
 * byte-exactly.
 */
public class FridaGadgetPatcherConfusedDexTest {

    private static final String GAME_APP_SMALI =
            """
                    .class public Lcom/example/GameApp;
                    .super Landroid/app/Application;
                    .source "GameApp.java"
                    .method public constructor <init>()V
                        .locals 0
                        invoke-direct {p0}, Landroid/app/Application;-><init>()V
                        return-void
                    .end method
                    .method public onCreate(Landroid/os/Bundle;)V
                        .locals 2
                        const/4 v0, 1
                        const/4 v1, 2
                        return-void
                    .end method
                    """;

    private static final String V05_SMALI =
            """
                    .class public Lv05;
                    .super Ljava/lang/Object;
                    .source "v05.java"
                    .method public constructor <init>()V
                        .locals 0
                        invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                        return-void
                    .end method
                    .method public 000000([[Landroid/graphics/Rect;)V
                        .locals 1
                        const/4 v0, 3
                        return-void
                    .end method
                    .method public filler([I)I
                        .locals 3
                        const/4 v0, 4
                        new-array v0, v0, [I
                        fill-array-data v0, :arr
                        const/4 v1, 2
                        aget v2, v0, v1
                        return v2
                        nop
                        :arr
                        .array-data 4
                            10 -20 30 -40
                        .end array-data
                    .end method
                    """;

    /** Assembles the smali files into one dex and returns its bytes. */
    private static byte[] assembleDex() throws Exception {
        File dir = Files.createTempDirectory("fridaconfuse").toFile();
        writeSmali(dir, "app.smali", GAME_APP_SMALI);
        writeSmali(dir, "v05.smali", V05_SMALI);
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = new File(dir, "out.dex").getPath();
        options.apiLevel = 28;
        Assert.assertTrue(Smali.assemble(options, dir.getPath()));
        File dex = new File(dir, "out.dex");
        byte[] bytes = new byte[(int) dex.length()];
        try (java.io.InputStream in = new java.io.FileInputStream(dex)) {
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                assertTrue("short read", n > 0);
                read += n;
            }
        }
        return bytes;
    }

    private static void writeSmali(File dir, String name, String smali) throws Exception {
        try (FileWriter w = new FileWriter(new File(dir, name))) {
            w.write(smali);
        }
    }

    /**
     * Runs the exact production confuse pass (DexConfuser applies DexArrayPayloadConfuser to
     * every class of the dex) and returns the confused dex bytes.
     *
     * @param onlyClassType when non-null, only that class type (e.g. "Lv05;") is confused;
     *                      used to isolate the writer failure from the Immutable-copy failure.
     */
    private static byte[] confuseDex(byte[] dexBytes, String onlyClassType) throws Exception {
        DexFile dexFile = DexFile.read(dexBytes);
        DexArrayPayloadConfuser confuser = new DexArrayPayloadConfuser();
        List<DexClass> classes = new ArrayList<>();
        java.util.Iterator<DexClass> it = dexFile.getDexClasses();
        while (it.hasNext()) classes.add(it.next());
        for (DexClass cls : classes) {
            String type = cls.getKey().toString();
            if (onlyClassType != null && !type.equals(onlyClassType)) continue;
            confuser.apply(cls);
        }
        assertTrue("confuser must have added payloads", confuser.getTotalCount() > 0);
        dexFile.refresh();
        dexFile.shrink();
        dexFile.refreshFull();
        return dexFile.getBytes();
    }

    private static DexBackedDexFile loadDex(byte[] bytes) {
        return new DexBackedDexFile(Opcodes.getDefault(), bytes);
    }

    private static ClassDef findClass(DexBackedDexFile dex, String type) {
        for (ClassDef def : dex.getClasses()) {
            if (type.equals(def.getType())) return def;
        }
        return null;
    }

    private static Method findMethod(ClassDef def, String name) {
        for (Method m : def.getVirtualMethods()) {
            if (m.getName().equals(name)) return m;
        }
        for (Method m : def.getDirectMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    /** Materializes the (lazy) instruction iterable of a method implementation. */
    private static List<Instruction> instructionsOf(Method method) {
        List<Instruction> out = new ArrayList<>();
        for (Instruction i : method.getImplementation().getInstructions()) out.add(i);
        return out;
    }

    /** The trailing array payload of a confused method, or null when absent. */
    private static ArrayPayload trailingPayload(List<Instruction> ins) {
        if (ins.isEmpty()) return null;
        Instruction last = ins.get(ins.size() - 1);
        return last instanceof ArrayPayload ? (ArrayPayload) last : null;
    }

    /** One-line description of a payload for equality checks: width, declared count, data. */
    private static String payloadSignature(ArrayPayload payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("w=").append(payload.getElementWidth());
        if (payload instanceof com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedArrayPayload) {
            com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedArrayPayload raw =
                    (com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedArrayPayload) payload;
            sb.append(" count=").append(raw.elementCount);
        }
        sb.append(" data=");
        for (Number n : payload.getArrayElements()) {
            sb.append(n.intValue()).append(',');
        }
        return sb.toString();
    }

    private static int codeUnitsOf(List<Instruction> ins) {
        int sum = 0;
        for (Instruction i : ins) sum += i.getCodeUnits();
        return sum;
    }

    // ------------------------------------------------------------------
    // Writer path: confused unrelated class, clean injection target.
    // This is the reported failure shape.
    // ------------------------------------------------------------------

    @Test
    public void rewritesDexWithConfusedUnrelatedClass() throws Exception {
        byte[] confusedBytes = confuseDex(assembleDex(), "Lv05;");
        DexBackedDexFile confused = loadDex(confusedBytes);

        // Sanity: the confuser did what it is supposed to do.
        Method junkMethod = findMethod(findClass(confused, "Lv05;"), "000000");
        assertNotNull(junkMethod);
        ArrayPayload junk = trailingPayload(instructionsOf(junkMethod));
        assertNotNull("confused method must end with an array payload", junk);
        int width = junk.getElementWidth();
        assertTrue("confuser emits odd widths, got " + width, width % 2 == 1 && width >= 3 && width <= 19);

        // The production patcher path must now round-trip the whole dex.
        byte[] patched = FridaGadgetPatcher.rewriteDexWithGadgetLoad(
                confused, "Lcom/example/GameApp;", FridaGadgetPatcher.KIND_APPLICATION, null);

        DexBackedDexFile out = loadDex(patched);

        // The junk payload survives bit-exactly (opaque, untouched).
        Method outJunk = findMethod(findClass(out, "Lv05;"), "000000");
        assertNotNull(outJunk);
        List<Instruction> outIns = instructionsOf(outJunk);
        ArrayPayload outPayload = trailingPayload(outIns);
        assertNotNull("payload must survive the rewrite", outPayload);
        assertEquals(width, outPayload.getElementWidth());
        assertEquals(payloadSignature(junk), payloadSignature(outPayload));
        assertEquals("method code units must be preserved",
                codeUnitsOf(instructionsOf(junkMethod)), codeUnitsOf(outIns));

        // The legit referenced even-width payload survives too. Note the confuser appends a
        // junk payload to this method as well (it is in the confused class), so match the
        // payload by its known shape instead of by position.
        Method fillerIn = findMethod(findClass(confused, "Lv05;"), "filler");
        Method fillerOut = findMethod(findClass(out, "Lv05;"), "filler");
        assertNotNull(fillerOut);
        ArrayPayload legitIn = null;
        for (Instruction i : instructionsOf(fillerIn)) {
            if (i instanceof ArrayPayload && ((ArrayPayload) i).getElementWidth() == 4) {
                legitIn = (ArrayPayload) i;
            }
        }
        ArrayPayload legitOut = null;
        for (Instruction i : instructionsOf(fillerOut)) {
            if (i instanceof ArrayPayload && ((ArrayPayload) i).getElementWidth() == 4) {
                legitOut = (ArrayPayload) i;
            }
        }
        assertNotNull("referenced width-4 payload must survive", legitIn);
        assertNotNull("referenced width-4 payload must survive the rewrite", legitOut);
        assertEquals(payloadSignature(legitIn), payloadSignature(legitOut));
    }

    // ------------------------------------------------------------------
    // Immutable-copy path: the target Application class itself is confused,
    // so injectLoadLibrary's ImmutableMethodImplementation.of(onCreate) must
    // preserve the opaque payload instead of throwing.
    // ------------------------------------------------------------------

    @Test
    public void wrapsConfusedOnCreateOfTargetClass() throws Exception {
        byte[] confusedBytes = confuseDex(assembleDex(), null);
        DexBackedDexFile confused = loadDex(confusedBytes);

        byte[] patched = FridaGadgetPatcher.rewriteDexWithGadgetLoad(
                confused, "Lcom/example/GameApp;", FridaGadgetPatcher.KIND_APPLICATION, null);
        DexBackedDexFile out = loadDex(patched);

        ClassDef app = findClass(out, "Lcom/example/GameApp;");
        assertNotNull(app);

        // The wrapper loads the gadget.
        Method onCreate = findMethod(app, "onCreate");
        assertNotNull("wrapper onCreate must exist", onCreate);
        List<Instruction> wrapperIns = instructionsOf(onCreate);
        assertTrue(wrapperIns.get(0).getOpcode().name().startsWith("CONST_STRING"));
        assertEquals("frida-gadget",
                ((com.android.tools.smali.dexlib2.iface.reference.StringReference)
                        ((com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)
                                wrapperIns.get(0)).getReference()).getString());

        // The renamed delegate keeps the original body — including the junk payload.
        Method delegate = findMethod(app, "onCreate$frida");
        assertNotNull("renamed original onCreate must be preserved", delegate);
        List<Instruction> delegateIns = instructionsOf(delegate);
        ArrayPayload delegatePayload = trailingPayload(delegateIns);
        assertNotNull("confused target class payload must survive the wrapper conversion", delegatePayload);
        int width = delegatePayload.getElementWidth();
        assertTrue("odd width, got " + width, width % 2 == 1 && width >= 3 && width <= 19);

        // And the unrelated confused class round-trips as well.
        Method outJunk = findMethod(findClass(out, "Lv05;"), "000000");
        assertNotNull(outJunk);
        assertNotNull(trailingPayload(instructionsOf(outJunk)));
    }

    // ------------------------------------------------------------------
    // Builder path: MutableMethodImplementation conversion (used by the
    // writer's jumbo-string fix and by anti-confuse tooling) must preserve
    // the payload size math, not just avoid throwing.
    // ------------------------------------------------------------------

    @Test
    public void mutableConversionPreservesOddWidthPayload() throws Exception {
        byte[] confusedBytes = confuseDex(assembleDex(), "Lv05;");
        DexBackedDexFile confused = loadDex(confusedBytes);
        Method junkMethod = findMethod(findClass(confused, "Lv05;"), "000000");
        List<Instruction> before = instructionsOf(junkMethod);
        ArrayPayload beforePayload = trailingPayload(before);
        assertNotNull(beforePayload);

        MutableMethodImplementation mutable =
                new MutableMethodImplementation(junkMethod.getImplementation());
        List<Instruction> after = new ArrayList<>(mutable.getInstructions());

        assertEquals("instruction count must be preserved", before.size(), after.size());
        assertEquals("method code units must be preserved",
                codeUnitsOf(before), codeUnitsOf(after));
        Instruction last = after.get(after.size() - 1);
        assertTrue("payload must survive conversion", last instanceof ArrayPayload);
        ArrayPayload afterPayload = (ArrayPayload) last;
        assertEquals(beforePayload.getElementWidth(), afterPayload.getElementWidth());
        // The converted payload is a builder payload without the reader's declared-count
        // field, so check the count via the element stream itself: for opaque widths the
        // reader guarantees one entry per element, so size equals the declared count.
        assertEquals("element count must be preserved",
                beforePayload.getArrayElements().size(), afterPayload.getArrayElements().size());
        assertEquals("element data must be preserved",
                beforePayload.getArrayElements(), afterPayload.getArrayElements());
        if (beforePayload instanceof com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedArrayPayload) {
            assertEquals("declared count must equal the element stream",
                    ((com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedArrayPayload) beforePayload).elementCount,
                    afterPayload.getArrayElements().size());
        }
    }

    // ------------------------------------------------------------------
    // Guard rail: newly constructed immutable payloads must still reject
    // invalid widths — the fix preserves what exists, it does not turn
    // odd widths into an ordinary authored value.
    // ------------------------------------------------------------------

    @Test
    public void immutablePayloadStillRejectsInvalidWidth() {
        try {
            new com.android.tools.smali.dexlib2.immutable.instruction.ImmutableArrayPayload(
                    3, java.util.Collections.singletonList(1));
            fail("constructing an odd-width immutable payload must stay rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("element width"));
        }
    }
}

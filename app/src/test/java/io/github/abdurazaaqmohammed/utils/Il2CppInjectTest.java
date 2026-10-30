package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod;
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation;
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x;
import com.android.tools.smali.smali.Smali;

import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Verifies the rename-and-wrap {@code <clinit>} injection used to load
 * libil2cppdumper.so from the target app's Application class.
 */
public class Il2CppInjectTest {

    private static final String TYPE = "Lcom/example/GameApp;";

    private static Method clinitWithReturn() {
        ImmutableMethodImplementation impl = new ImmutableMethodImplementation(1,
                Collections.singletonList(new ImmutableInstruction10x(Opcode.RETURN_VOID)),
                null, null);
        return new ImmutableMethod(TYPE, "<clinit>", Collections.emptyList(), "V",
                AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                Collections.emptySet(), Collections.emptySet(), impl);
    }

    private static ClassDef classDef(Method... directMethods) {
        return new ImmutableClassDef(TYPE, AccessFlags.PUBLIC.getValue(),
                "Landroid/app/Application;", Collections.emptySet(), null,
                Collections.emptySet(), Collections.emptyList(), Collections.emptyList(),
                directMethods.length == 0 ? Collections.emptyList() : List.of(directMethods),
                Collections.emptyList());
    }

    private static Method find(ClassDef def, String name) {
        for (Method m : def.getDirectMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    @Test
    public void createsClinitWhenMissing() {
        ClassDef patched = Il2CppDumperUtil.injectLoadLibrary(classDef());
        Method clinit = find(patched, "<clinit>");
        assertNotNull(clinit);
        assertEquals(AccessFlags.STATIC.getValue() | AccessFlags.CONSTRUCTOR.getValue(),
                clinit.getAccessFlags());
        assertEquals(1, clinit.getImplementation().getRegisterCount());
        assertLoadsLibrary(clinit, null);
    }

    @Test
    public void wrapsExistingClinit() {
        ClassDef patched = Il2CppDumperUtil.injectLoadLibrary(classDef(clinitWithReturn()));
        Method clinit = find(patched, "<clinit>");
        assertNotNull(clinit);
        assertLoadsLibrary(clinit, "clinit$mpmanager");

        Method renamed = find(patched, "clinit$mpmanager");
        assertNotNull("original initializer must be preserved under a new name", renamed);
        assertTrue("renamed copy must no longer be a constructor",
                (renamed.getAccessFlags() & AccessFlags.CONSTRUCTOR.getValue()) == 0);
        assertEquals(AccessFlags.STATIC.getValue(), renamed.getAccessFlags());
        assertEquals(1, renamed.getImplementation().getRegisterCount());
    }

    private static void assertLoadsLibrary(Method clinit, String wrappedName) {
        List<Instruction> ins = new java.util.ArrayList<>();
        for (Instruction i : clinit.getImplementation().getInstructions()) ins.add(i);
        assertEquals(Opcode.CONST_STRING, ins.get(0).getOpcode());
        assertEquals("il2cppdumper", ((StringReference) ((Instruction21c) ins.get(0)).getReference()).getString());
        assertEquals(Opcode.INVOKE_STATIC, ins.get(1).getOpcode());
        MethodReference ref = (MethodReference) ((Instruction35c) ins.get(1)).getReference();
        assertEquals("Ljava/lang/System;", ref.getDefiningClass());
        assertEquals("loadLibrary", ref.getName());
        // try/catch guard: a failed load must not break the target app
        assertEquals(1, clinit.getImplementation().getTryBlocks().size());
        if (wrappedName == null) {
            assertEquals(5, ins.size());
        } else {
            assertEquals(7, ins.size());
            MethodReference call = (MethodReference) ((Instruction35c) ins.get(2)).getReference();
            assertEquals(TYPE, call.getDefiningClass());
            assertEquals(wrappedName, call.getName());
            // handler path re-invokes the original initializer (index 5)
            MethodReference handler = (MethodReference) ((Instruction35c) ins.get(5)).getReference();
            assertEquals(wrappedName, handler.getName());
        }
        assertEquals(Opcode.RETURN_VOID, ins.get(ins.size() - 1).getOpcode());
    }

    private static final String APP_SMALI =
            """
                    .class public Lcom/example/GameApp;
                    .super Landroid/app/Application;
                    .source "GameApp.java"
                    .method public constructor <init>()V
                        .locals 0
                        invoke-direct {p0}, Landroid/app/Application;-><init>()V
                        return-void
                    .end method
                    .method static constructor <clinit>()V
                        .locals 1
                        const-string v0, "init"
                        return-void
                    .end method
                    """;

    private static final String OTHER_SMALI =
            """
                    .class public Lcom/example/Other;
                    .super Ljava/lang/Object;
                    .source "Other.java"
                    .method public ping()I
                        .locals 1
                        const/4 v0, 7
                        return v0
                    .end method
                    """;

    private static com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile assemble(String... smali)
            throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("il2cppinject").toFile();
        for (int i = 0; i < smali.length; i++) {
            try (java.io.FileWriter w = new java.io.FileWriter(new File(dir, "C" + i + ".smali"))) {
                w.write(smali[i]);
            }
        }
        com.android.tools.smali.smali.SmaliOptions options = new com.android.tools.smali.smali.SmaliOptions();
        options.outputDexFile = new File(dir, "out.dex").getPath();
        options.apiLevel = 28;
        org.junit.Assert.assertTrue(Smali.assemble(options, dir.getPath()));
        return com.android.tools.smali.dexlib2.DexFileFactory.loadDexFile(
                new File(dir, "out.dex"), com.android.tools.smali.dexlib2.Opcodes.getDefault());
    }

    @Test
    public void fullDexRewriteKeepsOtherClassesAndPatchesTarget() throws Exception {
        com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile dex = assemble(APP_SMALI, OTHER_SMALI);
        byte[] rewritten = Il2CppDumperUtil.rewriteDexWithLoadLibrary(dex, "Lcom/example/GameApp;", null);

        com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile patched =
                new com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile(
                        com.android.tools.smali.dexlib2.Opcodes.getDefault(), rewritten);
        ClassDef app = null;
        ClassDef other = null;
        for (ClassDef c : patched.getClasses()) {
            if ("Lcom/example/GameApp;".equals(c.getType())) app = c;
            if ("Lcom/example/Other;".equals(c.getType())) other = c;
        }
        assertNotNull(app);
        assertNotNull(other);
        assertLoadsLibrary(find(app, "<clinit>"), "clinit$mpmanager");
        assertNotNull(find(app, "clinit$mpmanager"));
        // untouched class survives the rewrite with its method intact
        Method ping = null;
        for (Method m : other.getVirtualMethods()) if (m.getName().equals("ping")) ping = m;
        assertNotNull(ping);
    }
}

package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore;
import com.android.tools.smali.dexlib2.writer.pool.DexPool;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests verifying Stage 1: PrivacyBytecodeEngine Dalvik opcode mutations.
 */
public class PrivacyBytecodeEngineTest {

    private static final String PRIVACY_TARGET_SMALI =
            """
            .class public Ltest/PrivacyTarget;
            .super Ljava/lang/Object;
            .source "PrivacyTarget.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public testLogging()I
                .locals 3
                const-string v0, "Tag"
                const-string v1, "Message with sensitive data"
                invoke-static {v0, v1}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                move-result v2
                return v2
            .end method

            .method public testTelephony(Landroid/telephony/TelephonyManager;)Ljava/lang/String;
                .locals 2
                invoke-virtual {p1}, Landroid/telephony/TelephonyManager;->getDeviceId()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method

            .method public testAdvertising(Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;)Ljava/lang/String;
                .locals 2
                invoke-virtual {p1}, Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;->getId()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method

            .method public testAdTrackingLimit(Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;)Z
                .locals 2
                invoke-virtual {p1}, Lcom/google/android/gms/ads/identifier/AdvertisingIdClient$Info;->isLimitAdTrackingEnabled()Z
                move-result v0
                return v0
            .end method

            .method public testAndroidId(Landroid/content/ContentResolver;)Ljava/lang/String;
                .locals 2
                const-string v0, "android_id"
                invoke-static {p1, v0}, Landroid/provider/Settings$Secure;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
                move-result-object v1
                return-object v1
            .end method

            .method public testAdbCheck(Landroid/content/ContentResolver;)I
                .locals 2
                const-string v0, "adb_enabled"
                invoke-static {p1, v0}, Landroid/provider/Settings$Global;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I
                move-result v1
                return v1
            .end method

            .method public testDebuggableProperty()Ljava/lang/String;
                .locals 2
                const-string v0, "ro.debuggable"
                invoke-static {v0}, Landroid/os/SystemProperties;->get(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v1
                return-object v1
            .end method
            """;

    private File assembleSmali(String smaliCode) throws Exception {
        File dir = Files.createTempDirectory("privacy_test").toFile();
        File pkg = new File(dir, "test");
        assertTrue(pkg.mkdirs());
        try (FileWriter w = new FileWriter(new File(pkg, "PrivacyTarget.smali"))) {
            w.write(smaliCode);
        }
        File outDex = new File(dir, "classes.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 26;
        assertTrue(Smali.assemble(options, dir.getPath()));
        return outDex;
    }

    @Test
    public void testSanitizeMethodTransformations() throws Exception {
        File dexFile = assembleSmali(PRIVACY_TARGET_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());

        ClassDef classDef = null;
        for (ClassDef cd : dex.getClasses()) {
            if ("Ltest/PrivacyTarget;".equals(cd.getType())) {
                classDef = cd;
                break;
            }
        }
        assertNotNull(classDef);

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertTrue(stats.classesModified > 0);
        assertTrue("Log calls stripped: " + stats.logCallsStripped, stats.logCallsStripped >= 1);
        assertTrue("Telephony getters blanked: " + stats.telephonyGettersBlanked, stats.telephonyGettersBlanked >= 1);
        assertTrue("Ad IDs neutralized: " + stats.adIdsNeutralized, stats.adIdsNeutralized >= 3);
        assertTrue("Dev mode checks masked: " + stats.devModeChecksMasked, stats.devModeChecksMasked >= 2);

        // Verify transformed instructions in methods
        for (Method method : sanitized.getVirtualMethods()) {
            List<Instruction> instructions = new ArrayList<>();
            for (Instruction i : method.getImplementation().getInstructions()) {
                instructions.add(i);
            }

            if ("testLogging".equals(method.getName())) {
                // Must have NOP where Log.d was, and const 0 where move-result was
                boolean foundNop = false;
                boolean foundConstZero = false;
                for (Instruction inst : instructions) {
                    if (inst.getOpcode() == Opcode.NOP) foundNop = true;
                    if ((inst.getOpcode() == Opcode.CONST_4 || inst.getOpcode() == Opcode.CONST_16)
                            && inst instanceof NarrowLiteralInstruction) {
                        if (((NarrowLiteralInstruction) inst).getNarrowLiteral() == 0) {
                            foundConstZero = true;
                        }
                    }
                }
                assertTrue("NOP not found in testLogging", foundNop);
                assertTrue("const 0 not found in testLogging", foundConstZero);
            } else if ("testTelephony".equals(method.getName())) {
                // move-result-object must be rewritten to const-string ""
                boolean foundEmptyString = false;
                for (Instruction inst : instructions) {
                    if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                        Object ref = ((ReferenceInstruction) inst).getReference();
                        if (ref instanceof StringReference && "".equals(((StringReference) ref).getString())) {
                            foundEmptyString = true;
                        }
                    }
                }
                assertTrue("Empty string const not found in testTelephony", foundEmptyString);
            } else if ("testAdTrackingLimit".equals(method.getName())) {
                // move-result must be rewritten to const 1 (true)
                boolean foundConstOne = false;
                for (Instruction inst : instructions) {
                    if ((inst.getOpcode() == Opcode.CONST_4 || inst.getOpcode() == Opcode.CONST_16)
                            && inst instanceof NarrowLiteralInstruction) {
                        if (((NarrowLiteralInstruction) inst).getNarrowLiteral() == 1) {
                            foundConstOne = true;
                        }
                    }
                }
                assertTrue("const 1 not found in testAdTrackingLimit", foundConstOne);
            } else if ("testAndroidId".equals(method.getName())) {
                // android_id query result rewritten to const-string ""
                int emptyStringCount = 0;
                for (Instruction inst : instructions) {
                    if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                        Object ref = ((ReferenceInstruction) inst).getReference();
                        if (ref instanceof StringReference && "".equals(((StringReference) ref).getString())) {
                            emptyStringCount++;
                        }
                    }
                }
                assertTrue("Empty string not placed in testAndroidId", emptyStringCount >= 1);
            } else if ("testDebuggableProperty".equals(method.getName())) {
                // ro.debuggable query rewritten to const-string "0"
                boolean foundZeroString = false;
                for (Instruction inst : instructions) {
                    if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                        Object ref = ((ReferenceInstruction) inst).getReference();
                        if (ref instanceof StringReference && "0".equals(((StringReference) ref).getString())) {
                            foundZeroString = true;
                        }
                    }
                }
                assertTrue("String '0' not found in testDebuggableProperty", foundZeroString);
            }
        }
    }

    @Test
    public void testRegisterWidthConstants() {
        // v0 with 0 -> CONST_4
        BuilderInstruction inst0 = PrivacyBytecodeEngine.createConstInt(0, 0);
        assertEquals(Opcode.CONST_4, inst0.getOpcode());

        // v15 with 1 -> CONST_4
        BuilderInstruction inst15 = PrivacyBytecodeEngine.createConstInt(15, 1);
        assertEquals(Opcode.CONST_4, inst15.getOpcode());

        // v16 with 0 -> CONST_16 (because v16 exceeds 4 bits)
        BuilderInstruction inst16 = PrivacyBytecodeEngine.createConstInt(16, 0);
        assertEquals(Opcode.CONST_16, inst16.getOpcode());

        // v255 with 1 -> CONST_16
        BuilderInstruction inst255 = PrivacyBytecodeEngine.createConstInt(255, 1);
        assertEquals(Opcode.CONST_16, inst255.getOpcode());

        // Wide const (0L)
        BuilderInstruction wide0 = PrivacyBytecodeEngine.createConstWide(0, 0L);
        assertEquals(Opcode.CONST_WIDE_16, wide0.getOpcode());
    }

    @Test
    public void testDexVersionPreservedDuringSanitization() throws Exception {
        byte[] dex039Header = new byte[]{'d', 'e', 'x', '\n', '0', '3', '9', 0, 0, 0, 0, 0};
        byte[] dex038Header = new byte[]{'d', 'e', 'x', '\n', '0', '3', '8', 0, 0, 0, 0, 0};
        byte[] dex035Header = new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0, 0, 0, 0, 0};

        Opcodes opcodes039 = PrivacyBytecodeEngine.opcodesForDex(dex039Header);
        assertEquals(28, opcodes039.api);

        Opcodes opcodes038 = PrivacyBytecodeEngine.opcodesForDex(dex038Header);
        assertEquals(27, opcodes038.api);

        Opcodes opcodes035 = PrivacyBytecodeEngine.opcodesForDex(dex035Header);
        assertEquals(23, opcodes035.api);

        // Fallbacks
        assertEquals(Opcodes.getDefault().api, PrivacyBytecodeEngine.opcodesForDex(null).api);
        assertEquals(Opcodes.getDefault().api, PrivacyBytecodeEngine.opcodesForDex(new byte[]{0, 1, 2}).api);

        // Verify DexPool output magic matches source DEX version
        DexPool pool039 = new DexPool(opcodes039);
        MemoryDataStore store039 = new MemoryDataStore();
        pool039.writeTo(store039);
        byte[] out039 = store039.getData();
        assertEquals('d', (char) out039[0]);
        assertEquals('e', (char) out039[1]);
        assertEquals('x', (char) out039[2]);
        assertEquals('\n', (char) out039[3]);
        assertEquals('0', (char) out039[4]);
        assertEquals('3', (char) out039[5]);
        assertEquals('9', (char) out039[6]);
        assertEquals(0, out039[7]);

        DexPool pool038 = new DexPool(opcodes038);
        MemoryDataStore store038 = new MemoryDataStore();
        pool038.writeTo(store038);
        byte[] out038 = store038.getData();
        assertEquals('0', (char) out038[4]);
        assertEquals('3', (char) out038[5]);
        assertEquals('8', (char) out038[6]);
    }
}

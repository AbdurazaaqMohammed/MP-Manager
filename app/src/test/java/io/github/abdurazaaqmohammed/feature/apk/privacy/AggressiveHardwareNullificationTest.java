package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for Task 9: Aggressive Hardware Fingerprinting & Callsite Rewriter.
 * Verifies Dalvik opcode mutations for:
 * - Build.getSerial() (invoke replaced with nop, move-result-object replaced with const-string "")
 * - sget-object Build.SERIAL replaced with const-string ""
 * - WifiInfo.getMacAddress() move-result-object replaced with const-string "02:00:00:00:00:00"
 * - WifiInfo.getSSID() move-result-object replaced with const-string "<unknown ssid>"
 * - WifiInfo.getBSSID() move-result-object replaced with const-string "02:00:00:00:00:00"
 * - NetworkInterface.getHardwareAddress() move-result-object replaced with const/4 vA, 0 (null)
 * - Standard mode stability guarantee (no mutations on these callsites)
 * - PrivacyStats.hardwareIdentifiersBlanked metric tracking and merge
 */
public class AggressiveHardwareNullificationTest {

    private static final String HARDWARE_TARGET_SMALI =
            """
            .class public Ltest/HardwareTarget;
            .super Ljava/lang/Object;
            .source "HardwareTarget.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public testBuildGetSerial()Ljava/lang/String;
                .locals 2
                invoke-static {}, Landroid/os/Build;->getSerial()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method

            .method public testBuildSerialField()Ljava/lang/String;
                .locals 1
                sget-object v0, Landroid/os/Build;->SERIAL:Ljava/lang/String;
                return-object v0
            .end method

            .method public testWifiMacAddress(Landroid/net/wifi/WifiInfo;)Ljava/lang/String;
                .locals 2
                invoke-virtual {p1}, Landroid/net/wifi/WifiInfo;->getMacAddress()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method

            .method public testWifiSSID(Landroid/net/wifi/WifiInfo;)Ljava/lang/String;
                .locals 2
                invoke-virtual {p1}, Landroid/net/wifi/WifiInfo;->getSSID()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method

            .method public testWifiBSSID(Landroid/net/wifi/WifiInfo;)Ljava/lang/String;
                .locals 2
                invoke-virtual {p1}, Landroid/net/wifi/WifiInfo;->getBSSID()Ljava/lang/String;
                move-result-object v0
                return-object v0
            .end method

            .method public testNetworkInterfaceHardwareAddress(Ljava/net/NetworkInterface;)[B
                .locals 2
                invoke-virtual {p1}, Ljava/net/NetworkInterface;->getHardwareAddress()[B
                move-result-object v0
                return-object v0
            .end method
            """;

    private File assembleSmali(String smaliCode) throws Exception {
        File dir = Files.createTempDirectory("aggressive_hardware_test").toFile();
        File pkg = new File(dir, "test");
        assertTrue(pkg.mkdirs());
        try (FileWriter w = new FileWriter(new File(pkg, "HardwareTarget.smali"))) {
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

    private ClassDef loadTargetClass(File dexFile) throws Exception {
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        for (ClassDef cd : dex.getClasses()) {
            if ("Ltest/HardwareTarget;".equals(cd.getType())) {
                return cd;
            }
        }
        return null;
    }

    private Method findMethod(ClassDef classDef, String name) {
        for (Method m : classDef.getVirtualMethods()) {
            if (name.equals(m.getName())) return m;
        }
        for (Method m : classDef.getDirectMethods()) {
            if (name.equals(m.getName())) return m;
        }
        return null;
    }

    private List<Instruction> getInstructions(Method method) {
        List<Instruction> list = new ArrayList<>();
        assertNotNull("Method implementation must not be null", method.getImplementation());
        for (Instruction i : method.getImplementation().getInstructions()) {
            list.add(i);
        }
        return list;
    }

    @Test
    public void testAggressiveHardwareNullificationAll() throws Exception {
        File dexFile = assembleSmali(HARDWARE_TARGET_SMALI);
        ClassDef classDef = loadTargetClass(dexFile);
        assertNotNull(classDef);

        PrivacyOptions options = PrivacyOptions.aggressive();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertTrue("Class should be modified", stats.classesModified > 0);
        assertEquals("All 6 hardware identifiers must be blanked", 6, stats.hardwareIdentifiersBlanked);

        // 1. Build.getSerial() -> invoke replaced with NOP, move-result-object replaced with const-string ""
        Method mBuildGetSerial = findMethod(sanitized, "testBuildGetSerial");
        assertNotNull(mBuildGetSerial);
        List<Instruction> instSerial = getInstructions(mBuildGetSerial);
        boolean foundNop = false;
        boolean foundEmptySerial = false;
        for (Instruction inst : instSerial) {
            if (inst.getOpcode() == Opcode.NOP) {
                foundNop = true;
            } else if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference && "".equals(((StringReference) ref).getString())) {
                    foundEmptySerial = true;
                }
            } else if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                org.junit.Assert.fail("move-result-object should have been eliminated for Build.getSerial()");
            }
        }
        assertTrue("Build.getSerial invoke must be replaced with NOP", foundNop);
        assertTrue("Build.getSerial move-result-object must be replaced with const-string \"\"", foundEmptySerial);

        // 2. sget-object Build.SERIAL -> const-string ""
        Method mBuildSerialField = findMethod(sanitized, "testBuildSerialField");
        assertNotNull(mBuildSerialField);
        List<Instruction> instField = getInstructions(mBuildSerialField);
        boolean foundFieldEmptyString = false;
        for (Instruction inst : instField) {
            if (inst.getOpcode() == Opcode.SGET_OBJECT) {
                org.junit.Assert.fail("sget-object Build.SERIAL should have been replaced");
            } else if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference && "".equals(((StringReference) ref).getString())) {
                    foundFieldEmptyString = true;
                }
            }
        }
        assertTrue("sget-object Build.SERIAL must be replaced with const-string \"\"", foundFieldEmptyString);

        // 3. WifiInfo.getMacAddress() -> const-string "02:00:00:00:00:00"
        Method mWifiMac = findMethod(sanitized, "testWifiMacAddress");
        assertNotNull(mWifiMac);
        List<Instruction> instMac = getInstructions(mWifiMac);
        boolean foundDummyMac = false;
        for (Instruction inst : instMac) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                org.junit.Assert.fail("move-result-object should have been replaced for WifiInfo.getMacAddress()");
            } else if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference && "02:00:00:00:00:00".equals(((StringReference) ref).getString())) {
                    foundDummyMac = true;
                }
            }
        }
        assertTrue("WifiInfo.getMacAddress move-result-object must be replaced with const-string \"02:00:00:00:00:00\"", foundDummyMac);

        // 4. WifiInfo.getSSID() -> const-string "<unknown ssid>"
        Method mWifiSSID = findMethod(sanitized, "testWifiSSID");
        assertNotNull(mWifiSSID);
        List<Instruction> instSSID = getInstructions(mWifiSSID);
        boolean foundDummySSID = false;
        for (Instruction inst : instSSID) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                org.junit.Assert.fail("move-result-object should have been replaced for WifiInfo.getSSID()");
            } else if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference && "<unknown ssid>".equals(((StringReference) ref).getString())) {
                    foundDummySSID = true;
                }
            }
        }
        assertTrue("WifiInfo.getSSID move-result-object must be replaced with const-string \"<unknown ssid>\"", foundDummySSID);

        // 5. WifiInfo.getBSSID() -> const-string "02:00:00:00:00:00"
        Method mWifiBSSID = findMethod(sanitized, "testWifiBSSID");
        assertNotNull(mWifiBSSID);
        List<Instruction> instBSSID = getInstructions(mWifiBSSID);
        boolean foundDummyBSSID = false;
        for (Instruction inst : instBSSID) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                org.junit.Assert.fail("move-result-object should have been replaced for WifiInfo.getBSSID()");
            } else if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference && "02:00:00:00:00:00".equals(((StringReference) ref).getString())) {
                    foundDummyBSSID = true;
                }
            }
        }
        assertTrue("WifiInfo.getBSSID move-result-object must be replaced with const-string \"02:00:00:00:00:00\"", foundDummyBSSID);

        // 6. NetworkInterface.getHardwareAddress() -> const/4 vA, 0 (null)
        Method mNetHw = findMethod(sanitized, "testNetworkInterfaceHardwareAddress");
        assertNotNull(mNetHw);
        List<Instruction> instNetHw = getInstructions(mNetHw);
        boolean foundNullConst = false;
        for (Instruction inst : instNetHw) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                org.junit.Assert.fail("move-result-object should have been replaced for NetworkInterface.getHardwareAddress()");
            } else if ((inst.getOpcode() == Opcode.CONST_4 || inst.getOpcode() == Opcode.CONST_16)
                    && inst instanceof NarrowLiteralInstruction) {
                if (((NarrowLiteralInstruction) inst).getNarrowLiteral() == 0) {
                    foundNullConst = true;
                }
            }
        }
        assertTrue("NetworkInterface.getHardwareAddress move-result-object must be replaced with const/4 0 (null)", foundNullConst);
    }

    @Test
    public void testStandardModePreservesHardwareAccess() throws Exception {
        File dexFile = assembleSmali(HARDWARE_TARGET_SMALI);
        ClassDef classDef = loadTargetClass(dexFile);
        assertNotNull(classDef);

        PrivacyOptions options = PrivacyOptions.standard();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertEquals("No hardware identifiers should be blanked in standard mode", 0, stats.hardwareIdentifiersBlanked);

        // Check Build.getSerial() is untouched
        Method mBuildGetSerial = findMethod(sanitized, "testBuildGetSerial");
        assertNotNull(mBuildGetSerial);
        boolean foundInvokeStatic = false;
        boolean foundMoveResult = false;
        for (Instruction inst : getInstructions(mBuildGetSerial)) {
            if (inst.getOpcode() == Opcode.INVOKE_STATIC) foundInvokeStatic = true;
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) foundMoveResult = true;
        }
        assertTrue("Standard mode must preserve invoke-static for Build.getSerial()", foundInvokeStatic);
        assertTrue("Standard mode must preserve move-result-object for Build.getSerial()", foundMoveResult);

        // Check sget-object Build.SERIAL is untouched
        Method mBuildSerialField = findMethod(sanitized, "testBuildSerialField");
        assertNotNull(mBuildSerialField);
        boolean foundSget = false;
        for (Instruction inst : getInstructions(mBuildSerialField)) {
            if (inst.getOpcode() == Opcode.SGET_OBJECT) foundSget = true;
        }
        assertTrue("Standard mode must preserve sget-object for Build.SERIAL", foundSget);

        // Check WifiInfo methods preserve move-result-object
        for (String wifiMethod : List.of("testWifiMacAddress", "testWifiSSID", "testWifiBSSID")) {
            Method m = findMethod(sanitized, wifiMethod);
            assertNotNull(m);
            boolean moveResultFound = false;
            for (Instruction inst : getInstructions(m)) {
                if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) moveResultFound = true;
            }
            assertTrue("Standard mode must preserve move-result-object for " + wifiMethod, moveResultFound);
        }

        // Check NetworkInterface.getHardwareAddress preserves move-result-object
        Method mNetHw = findMethod(sanitized, "testNetworkInterfaceHardwareAddress");
        assertNotNull(mNetHw);
        boolean moveResultHw = false;
        for (Instruction inst : getInstructions(mNetHw)) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) moveResultHw = true;
        }
        assertTrue("Standard mode must preserve move-result-object for NetworkInterface.getHardwareAddress()", moveResultHw);
    }

    @Test
    public void testGranularBlankHardwareSerialFlag() throws Exception {
        File dexFile = assembleSmali(HARDWARE_TARGET_SMALI);
        ClassDef classDef = loadTargetClass(dexFile);
        assertNotNull(classDef);

        PrivacyOptions options = new PrivacyOptions.Builder()
                .setMode(SanitizationMode.STANDARD)
                .setBlankHardwareSerial(true)
                .setBlankWifiMac(false)
                .build();

        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertEquals("Only Build.getSerial and Build.SERIAL should be blanked", 2, stats.hardwareIdentifiersBlanked);

        // Build.getSerial blanked
        Method mSerial = findMethod(sanitized, "testBuildGetSerial");
        boolean hasNop = false;
        for (Instruction inst : getInstructions(mSerial)) {
            if (inst.getOpcode() == Opcode.NOP) hasNop = true;
        }
        assertTrue("Build.getSerial should be NOP'd when blankHardwareSerial is true", hasNop);

        // Wifi mac preserved
        Method mWifiMac = findMethod(sanitized, "testWifiMacAddress");
        boolean hasMoveResult = false;
        for (Instruction inst : getInstructions(mWifiMac)) {
            if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) hasMoveResult = true;
        }
        assertTrue("Wifi mac should be preserved when blankWifiMac is false", hasMoveResult);
    }

    @Test
    public void testGranularBlankWifiMacFlag() throws Exception {
        File dexFile = assembleSmali(HARDWARE_TARGET_SMALI);
        ClassDef classDef = loadTargetClass(dexFile);
        assertNotNull(classDef);

        PrivacyOptions options = new PrivacyOptions.Builder()
                .setMode(SanitizationMode.STANDARD)
                .setBlankHardwareSerial(false)
                .setBlankWifiMac(true)
                .build();

        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertEquals("4 wifi/network hardware identifiers should be blanked", 4, stats.hardwareIdentifiersBlanked);

        // Build.getSerial preserved
        Method mSerial = findMethod(sanitized, "testBuildGetSerial");
        boolean hasInvoke = false;
        for (Instruction inst : getInstructions(mSerial)) {
            if (inst.getOpcode() == Opcode.INVOKE_STATIC) hasInvoke = true;
        }
        assertTrue("Build.getSerial should be preserved when blankHardwareSerial is false", hasInvoke);

        // Wifi mac blanked
        Method mWifiMac = findMethod(sanitized, "testWifiMacAddress");
        boolean hasDummyMac = false;
        for (Instruction inst : getInstructions(mWifiMac)) {
            if (inst.getOpcode() == Opcode.CONST_STRING && inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof StringReference && "02:00:00:00:00:00".equals(((StringReference) ref).getString())) {
                    hasDummyMac = true;
                }
            }
        }
        assertTrue("Wifi mac should be blanked when blankWifiMac is true", hasDummyMac);
    }

    @Test
    public void testPrivacyStatsHardwareIdentifiersBlankedIntegration() {
        PrivacyStats stats = new PrivacyStats();
        assertEquals(0, stats.hardwareIdentifiersBlanked);

        stats.hardwareIdentifiersBlanked = 5;
        assertEquals(5, stats.telemetryTotalMutations());
        assertEquals(5, stats.totalMutations());

        PrivacyStats other = new PrivacyStats();
        other.hardwareIdentifiersBlanked = 3;
        stats.merge(other);

        assertEquals(8, stats.hardwareIdentifiersBlanked);
        assertEquals(8, stats.telemetryTotalMutations());
        assertEquals(8, stats.totalMutations());
    }
}

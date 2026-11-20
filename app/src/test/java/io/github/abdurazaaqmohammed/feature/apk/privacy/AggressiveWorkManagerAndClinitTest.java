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
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for Task 10: Aggressive WorkManager Periodic Enqueue Suppression
 * and Tracker <clinit> Static Initializer Neutralization.
 */
public class AggressiveWorkManagerAndClinitTest {

    private static final String WORK_MANAGER_SMALI =
            """
            .class public Ltest/WorkManagerCaller;
            .super Ljava/lang/Object;
            .source "WorkManagerCaller.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public testEnqueueUniquePeriodicWork(Landroidx/work/WorkManager;Landroidx/work/PeriodicWorkRequest;)Landroidx/work/Operation;
                .locals 3
                const-string v0, "telemetry_unique_work"
                sget-object v1, Landroidx/work/ExistingPeriodicWorkPolicy;->KEEP:Landroidx/work/ExistingPeriodicWorkPolicy;
                invoke-virtual {p1, v0, v1, p2}, Landroidx/work/WorkManager;->enqueueUniquePeriodicWork(Ljava/lang/String;Landroidx/work/ExistingPeriodicWorkPolicy;Landroidx/work/PeriodicWorkRequest;)Landroidx/work/Operation;
                move-result-object v2
                return-object v2
            .end method

            .method public testEnqueuePeriodicWork(Landroidx/work/WorkManager;Landroidx/work/PeriodicWorkRequest;)Landroidx/work/Operation;
                .locals 2
                invoke-virtual {p1, p2}, Landroidx/work/WorkManager;->enqueuePeriodicWork(Landroidx/work/PeriodicWorkRequest;)Landroidx/work/Operation;
                move-result-object v0
                return-object v0
            .end method

            .method public testEnqueuePeriodicWorkNoCapture(Landroidx/work/WorkManager;Landroidx/work/PeriodicWorkRequest;)V
                .locals 1
                invoke-virtual {p1, p2}, Landroidx/work/WorkManager;->enqueuePeriodicWork(Landroidx/work/PeriodicWorkRequest;)Landroidx/work/Operation;
                return-void
            .end method
            """;

    private File assembleSmali(String smaliCode, String relativePath) throws Exception {
        File dir = Files.createTempDirectory("aggressive_wm_test").toFile();
        File smaliFile = new File(dir, relativePath);
        if (smaliFile.getParentFile() != null) {
            smaliFile.getParentFile().mkdirs();
        }
        try (FileWriter w = new FileWriter(smaliFile)) {
            w.write(smaliCode);
        }
        File outDex = new File(dir, "classes.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 26;
        assertTrue("Smali assemble should succeed", Smali.assemble(options, dir.getPath()));
        return outDex;
    }

    private File assembleMultipleSmali(Map<String, String> classes) throws Exception {
        File dir = Files.createTempDirectory("aggressive_multi_test").toFile();
        for (Map.Entry<String, String> entry : classes.entrySet()) {
            File smaliFile = new File(dir, entry.getKey() + ".smali");
            if (smaliFile.getParentFile() != null) {
                smaliFile.getParentFile().mkdirs();
            }
            try (FileWriter w = new FileWriter(smaliFile)) {
                w.write(entry.getValue());
            }
        }
        File outDex = new File(dir, "classes.dex");
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 26;
        assertTrue("Smali assemble should succeed", Smali.assemble(options, dir.getPath()));
        return outDex;
    }

    private ClassDef loadClass(File dexFile, String classType) throws Exception {
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());
        for (ClassDef cd : dex.getClasses()) {
            if (classType.equals(cd.getType())) {
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

    // =========================================================================
    // WorkManager Periodic Enqueue Suppression Tests
    // =========================================================================

    @Test
    public void testWorkManagerEnqueueSuppressionInAggressiveMode() throws Exception {
        File dexFile = assembleSmali(WORK_MANAGER_SMALI, "test/WorkManagerCaller.smali");
        ClassDef classDef = loadClass(dexFile, "Ltest/WorkManagerCaller;");
        assertNotNull(classDef);

        PrivacyOptions options = PrivacyOptions.aggressive();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertTrue("Class should be marked modified", stats.classesModified > 0);
        assertEquals("Three WorkManager periodic enqueue invocations must be suppressed",
                3, stats.periodicWorkEnqueueSuppressed);

        // 1. Verify enqueueUniquePeriodicWork
        Method mUnique = findMethod(sanitized, "testEnqueueUniquePeriodicWork");
        assertNotNull(mUnique);
        List<Instruction> instUnique = getInstructions(mUnique);
        boolean foundNopUnique = false;
        boolean foundConstNullUnique = false;
        for (Instruction inst : instUnique) {
            if (inst.getOpcode() == Opcode.NOP) {
                foundNopUnique = true;
            } else if (inst.getOpcode() == Opcode.CONST_4 && inst instanceof OneRegisterInstruction && inst instanceof NarrowLiteralInstruction) {
                OneRegisterInstruction ori = (OneRegisterInstruction) inst;
                NarrowLiteralInstruction nli = (NarrowLiteralInstruction) inst;
                if (ori.getRegisterA() == 2 && nli.getNarrowLiteral() == 0) {
                    foundConstNullUnique = true;
                }
            } else if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                org.junit.Assert.fail("move-result-object should have been eliminated for enqueueUniquePeriodicWork");
            } else if (inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof MethodReference) {
                    MethodReference mr = (MethodReference) ref;
                    assertFalse("enqueueUniquePeriodicWork should be removed",
                            "enqueueUniquePeriodicWork".equals(mr.getName()));
                }
            }
        }
        assertTrue("enqueueUniquePeriodicWork invoke must be replaced with NOP", foundNopUnique);
        assertTrue("move-result-object v2 must be replaced with const/4 v2, 0", foundConstNullUnique);

        // 2. Verify enqueuePeriodicWork with move-result-object
        Method mPeriodic = findMethod(sanitized, "testEnqueuePeriodicWork");
        assertNotNull(mPeriodic);
        List<Instruction> instPeriodic = getInstructions(mPeriodic);
        boolean foundNopPeriodic = false;
        boolean foundConstNullPeriodic = false;
        for (Instruction inst : instPeriodic) {
            if (inst.getOpcode() == Opcode.NOP) {
                foundNopPeriodic = true;
            } else if (inst.getOpcode() == Opcode.CONST_4 && inst instanceof OneRegisterInstruction && inst instanceof NarrowLiteralInstruction) {
                OneRegisterInstruction ori = (OneRegisterInstruction) inst;
                NarrowLiteralInstruction nli = (NarrowLiteralInstruction) inst;
                if (ori.getRegisterA() == 0 && nli.getNarrowLiteral() == 0) {
                    foundConstNullPeriodic = true;
                }
            } else if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                org.junit.Assert.fail("move-result-object should have been eliminated for enqueuePeriodicWork");
            } else if (inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof MethodReference) {
                    MethodReference mr = (MethodReference) ref;
                    assertFalse("enqueuePeriodicWork should be removed",
                            "enqueuePeriodicWork".equals(mr.getName()));
                }
            }
        }
        assertTrue("enqueuePeriodicWork invoke must be replaced with NOP", foundNopPeriodic);
        assertTrue("move-result-object v0 must be replaced with const/4 v0, 0", foundConstNullPeriodic);

        // 3. Verify enqueuePeriodicWork without move-result-object (void return)
        Method mNoCapture = findMethod(sanitized, "testEnqueuePeriodicWorkNoCapture");
        assertNotNull(mNoCapture);
        List<Instruction> instNoCapture = getInstructions(mNoCapture);
        boolean foundNopNoCapture = false;
        for (Instruction inst : instNoCapture) {
            if (inst.getOpcode() == Opcode.NOP) {
                foundNopNoCapture = true;
            } else if (inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof MethodReference) {
                    MethodReference mr = (MethodReference) ref;
                    assertFalse("enqueuePeriodicWork should be removed",
                            "enqueuePeriodicWork".equals(mr.getName()));
                }
            }
        }
        assertTrue("enqueuePeriodicWork invoke without move-result must be replaced with NOP", foundNopNoCapture);
    }

    @Test
    public void testWorkManagerSuppressionUnderExplicitOption() throws Exception {
        File dexFile = assembleSmali(WORK_MANAGER_SMALI, "test/WorkManagerCaller.smali");
        ClassDef classDef = loadClass(dexFile, "Ltest/WorkManagerCaller;");
        assertNotNull(classDef);

        // Standard mode with explicit suppressPeriodicWorkEnqueue enabled
        PrivacyOptions options = PrivacyOptions.builder()
                .setMode(SanitizationMode.STANDARD)
                .setSuppressPeriodicWorkEnqueue(true)
                .build();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertTrue("Class should be modified under explicit suppressPeriodicWorkEnqueue", stats.classesModified > 0);
        assertEquals("Three periodic work calls suppressed", 3, stats.periodicWorkEnqueueSuppressed);
    }

    @Test
    public void testWorkManagerPreservedUnderStandardMode() throws Exception {
        File dexFile = assembleSmali(WORK_MANAGER_SMALI, "test/WorkManagerCaller.smali");
        ClassDef classDef = loadClass(dexFile, "Ltest/WorkManagerCaller;");
        assertNotNull(classDef);

        PrivacyOptions options = PrivacyOptions.standard();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(classDef, options, stats);

        assertEquals("No periodic work enqueues should be suppressed under standard mode",
                0, stats.periodicWorkEnqueueSuppressed);

        Method mUnique = findMethod(sanitized, "testEnqueueUniquePeriodicWork");
        assertNotNull(mUnique);
        boolean foundInvokeUnique = false;
        boolean foundMoveResultUnique = false;
        for (Instruction inst : getInstructions(mUnique)) {
            if (inst instanceof ReferenceInstruction) {
                Object ref = ((ReferenceInstruction) inst).getReference();
                if (ref instanceof MethodReference && "enqueueUniquePeriodicWork".equals(((MethodReference) ref).getName())) {
                    foundInvokeUnique = true;
                }
            } else if (inst.getOpcode() == Opcode.MOVE_RESULT_OBJECT) {
                foundMoveResultUnique = true;
            }
        }
        assertTrue("enqueueUniquePeriodicWork must be preserved in standard mode", foundInvokeUnique);
        assertTrue("move-result-object must be preserved in standard mode", foundMoveResultUnique);
    }

    // =========================================================================
    // Tracker <clinit> Static Initializer Neutralization Tests
    // =========================================================================

    private static String createClinitSmali(String className, String stringLiteral) {
        return """
               .class public %s
               .super Ljava/lang/Object;
               .source "Class.java"

               .field public static KEY:Ljava/lang/String;

               .method public constructor <init>()V
                   .locals 1
                   invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                   return-void
               .end method

               .method static constructor <clinit>()V
                   .locals 1
                   const-string v0, "%s"
                   sput-object v0, %s->KEY:Ljava/lang/String;
                   return-void
               .end method
               """.formatted(className, stringLiteral, className);
    }

    @Test
    public void testTrackerClinitNeutralizationInAggressiveMode() throws Exception {
        List<String> pureTrackerClasses = Arrays.asList(
                "Lcom/cujo/otm/telemetry/TelemetryService;",
                "Lcom/appsflyer/AppsFlyerLib;",
                "Lcom/adjust/sdk/AdjustInstance;",
                "Lcom/google/firebase/analytics/FirebaseAnalytics;",
                "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
                "Lcom/branch/io/Branch;",
                "Lio/branch/BranchUtil;",
                "Lcom/kochava/base/Tracker;",
                "Lcom/singular/sdk/Singular;",
                "Lcom/threatmetrix/TrustDefender;"
        );

        String normalAppClass = "Lcom/xfinity/digitalhome/MainActivity;";

        Map<String, String> classesMap = new HashMap<>();
        for (String trackerClass : pureTrackerClasses) {
            String path = trackerClass.substring(1, trackerClass.length() - 1);
            classesMap.put(path, createClinitSmali(trackerClass, "TrackerSecret_" + path));
        }
        String normalPath = normalAppClass.substring(1, normalAppClass.length() - 1);
        classesMap.put(normalPath, createClinitSmali(normalAppClass, "NormalAppConfig"));

        File dexFile = assembleMultipleSmali(classesMap);

        PrivacyOptions options = PrivacyOptions.aggressive();
        PrivacyStats stats = new PrivacyStats();

        // 1. Verify every tracker class has <clinit> neutralized to single return-void with regCount 0
        for (String trackerClass : pureTrackerClasses) {
            ClassDef cd = loadClass(dexFile, trackerClass);
            assertNotNull("Class must exist: " + trackerClass, cd);
            PrivacyStats singleStats = new PrivacyStats();
            ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(cd, options, singleStats);

            assertEquals("Tracker class <clinit> must be sanitized: " + trackerClass,
                    1, singleStats.trackerClinitSanitized);
            assertTrue("Tracker class must be marked modified", singleStats.classesModified > 0);

            Method clinit = findMethod(sanitized, "<clinit>");
            assertNotNull("<clinit> must still exist on " + trackerClass, clinit);
            assertNotNull("<clinit> implementation must not be null", clinit.getImplementation());
            assertEquals("Neutralized <clinit> must have registerCount 0",
                    0, clinit.getImplementation().getRegisterCount());

            List<Instruction> instructions = getInstructions(clinit);
            assertEquals("Neutralized <clinit> must contain exactly 1 instruction", 1, instructions.size());
            assertEquals("Neutralized <clinit> single instruction must be return-void",
                    Opcode.RETURN_VOID, instructions.get(0).getOpcode());

            stats.merge(singleStats);
        }

        assertEquals("All 10 tracker classes must have <clinit> sanitized",
                10, stats.trackerClinitSanitized);

        // 2. Normal app class must preserve <clinit> intact
        ClassDef cdNormal = loadClass(dexFile, normalAppClass);
        assertNotNull(cdNormal);
        PrivacyStats normalStats = new PrivacyStats();
        ClassDef sanitizedNormal = PrivacyBytecodeEngine.sanitizeClass(cdNormal, options, normalStats);

        assertEquals("Normal app class must NOT have <clinit> sanitized",
                0, normalStats.trackerClinitSanitized);
        Method clinitNormal = findMethod(sanitizedNormal, "<clinit>");
        assertNotNull(clinitNormal);
        assertNotNull(clinitNormal.getImplementation());
        assertTrue("Normal <clinit> must have registerCount >= 1",
                clinitNormal.getImplementation().getRegisterCount() >= 1);
        assertTrue("Normal <clinit> must have multiple instructions",
                getInstructions(clinitNormal).size() > 1);
    }

    @Test
    public void testTrackerClinitPreservedInStandardMode() throws Exception {
        String trackerClass = "Lcom/appsflyer/AppsFlyerLib;";
        File dexFile = assembleSmali(createClinitSmali(trackerClass, "AppsFlyerInit"), "com/appsflyer/AppsFlyerLib.smali");
        ClassDef cd = loadClass(dexFile, trackerClass);
        assertNotNull(cd);

        PrivacyOptions options = PrivacyOptions.standard();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(cd, options, stats);

        assertEquals("Tracker <clinit> must NOT be sanitized under standard mode",
                0, stats.trackerClinitSanitized);

        Method clinit = findMethod(sanitized, "<clinit>");
        assertNotNull(clinit);
        List<Instruction> instructions = getInstructions(clinit);
        boolean foundSput = false;
        for (Instruction inst : instructions) {
            if (inst.getOpcode() == Opcode.SPUT_OBJECT) {
                foundSput = true;
            }
        }
        assertTrue("sput-object in tracker <clinit> must be preserved under standard mode", foundSput);
    }

    @Test
    public void testTrackerClinitNeutralizationWithExplicitNullifyOption() throws Exception {
        String trackerClass = "Lcom/adjust/sdk/AdjustInstance;";
        File dexFile = assembleSmali(createClinitSmali(trackerClass, "AdjustInit"), "com/adjust/sdk/AdjustInstance.smali");
        ClassDef cd = loadClass(dexFile, trackerClass);
        assertNotNull(cd);

        PrivacyOptions options = PrivacyOptions.builder()
                .setMode(SanitizationMode.AGGRESSIVE)
                .setDeepStructuralAnalysis(false)
                .setNullifyTrackerClinit(true)
                .build();
        PrivacyStats stats = new PrivacyStats();
        ClassDef sanitized = PrivacyBytecodeEngine.sanitizeClass(cd, options, stats);

        assertEquals(1, stats.trackerClinitSanitized);
        Method clinit = findMethod(sanitized, "<clinit>");
        assertNotNull(clinit);
        assertEquals(1, getInstructions(clinit).size());
        assertEquals(Opcode.RETURN_VOID, getInstructions(clinit).get(0).getOpcode());
    }

    // =========================================================================
    // PrivacyStats Metrics & Integration Tests
    // =========================================================================

    @Test
    public void testPrivacyStatsPeriodicWorkAndClinitMetrics() {
        PrivacyStats stats = new PrivacyStats();
        assertEquals(0, stats.periodicWorkEnqueueSuppressed);
        assertEquals(0, stats.trackerClinitSanitized);

        stats.periodicWorkEnqueueSuppressed = 4;
        stats.trackerClinitSanitized = 6;

        assertEquals(10, stats.telemetryTotalMutations());
        assertEquals(10, stats.totalMutations());

        PrivacyStats other = new PrivacyStats();
        other.periodicWorkEnqueueSuppressed = 2;
        other.trackerClinitSanitized = 3;
        other.hardwareIdentifiersBlanked = 5;

        stats.merge(other);
        assertEquals(6, stats.periodicWorkEnqueueSuppressed);
        assertEquals(9, stats.trackerClinitSanitized);
        assertEquals(5, stats.hardwareIdentifiersBlanked);
        assertEquals(20, stats.telemetryTotalMutations());
        assertEquals(20, stats.totalMutations());
    }
}

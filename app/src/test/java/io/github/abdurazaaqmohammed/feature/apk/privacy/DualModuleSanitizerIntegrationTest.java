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
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.InputSource;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * End-to-end integration tests verifying Task 6: Multi-DEX APK Integration & Granular Audit Reporting.
 * Validates cross-DEX reachability discovery, selective bytecode mutation for active targets,
 * dead code skipping, and audit status reporting.
 */
public class DualModuleSanitizerIntegrationTest {

    // --- Smali Definitions for Multi-DEX Integration ---

    // DEX 1: Telemetry Worker (Module A)
    private static final String TELEMETRY_WORKER_SMALI =
            """
            .class public Lcom/example/telemetry/AnalyticsTelemetryWorker;
            .super Landroidx/work/Worker;
            .source "AnalyticsTelemetryWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/Worker;-><init>()V
                return-void
            .end method

            .method public doWork()Landroidx/work/ListenableWorker$Result;
                .locals 1
                invoke-static {}, Landroidx/work/ListenableWorker$Result;->retry()Landroidx/work/ListenableWorker$Result;
                move-result-object v0
                return-object v0
            .end method
            """;

    // DEX 1: Active AI Assistant Manager (Module B)
    private static final String ACTIVE_AI_MANAGER_SMALI =
            """
            .class public Lcom/example/ai/RufusAssistantManager;
            .super Ljava/lang/Object;
            .source "RufusAssistantManager.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public isEligible()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method
            """;

    // DEX 1: Main Caller in DEX 1 calling active targets
    private static final String MAIN_CALLER_SMALI =
            """
            .class public Lcom/example/app/MainCaller;
            .super Ljava/lang/Object;
            .source "MainCaller.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public runOperations(Lcom/example/telemetry/AnalyticsTelemetryWorker;)V
                .locals 2
                invoke-virtual {p1}, Lcom/example/telemetry/AnalyticsTelemetryWorker;->doWork()Landroidx/work/ListenableWorker$Result;
                invoke-static {}, Lcom/example/ai/RufusAssistantManager;->isEligible()Z
                return-void
            .end method
            """;

    // DEX 2: Unreferenced Dead Code AI Manager (Module B target, 0 callers)
    private static final String DEAD_AI_MANAGER_SMALI =
            """
            .class public Lcom/example/ai/DeadAiManager;
            .super Ljava/lang/Object;
            .source "DeadAiManager.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public supportsAssistant()Z
                .locals 1
                const/4 v0, 0x1
                return v0
            .end method
            """;

    // DEX 2: Active Telemetry Logger (Module A target)
    private static final String TELEMETRY_LOGGER_SMALI =
            """
            .class public Lcom/example/telemetry/TelemetryLogger;
            .super Ljava/lang/Object;
            .source "TelemetryLogger.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static logEvent(Ljava/lang/String;)I
                .locals 2
                const-string v0, "TelemetryTag"
                invoke-static {v0, p0}, Landroid/util/Log;->d(Ljava/lang/String;Ljava/lang/String;)I
                move-result v1
                return v1
            .end method
            """;

    // DEX 2: Secondary Caller invoking TelemetryLogger
    private static final String SECONDARY_CALLER_SMALI =
            """
            .class public Lcom/example/app/SecondaryCaller;
            .super Ljava/lang/Object;
            .source "SecondaryCaller.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static performSecondaryTask()V
                .locals 1
                const-string v0, "event_fired"
                invoke-static {v0}, Lcom/example/telemetry/TelemetryLogger;->logEvent(Ljava/lang/String;)I
                return-void
            .end method
            """;

    // Unreferenced Dead Telemetry Worker (Module A target, 0 callers)
    private static final String DEAD_TELEMETRY_WORKER_SMALI =
            """
            .class public Lcom/example/telemetry/UnreferencedTelemetryWorker;
            .super Landroidx/work/Worker;
            .source "UnreferencedTelemetryWorker.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Landroidx/work/Worker;-><init>()V
                return-void
            .end method

            .method public doWork()Landroidx/work/ListenableWorker$Result;
                .locals 1
                invoke-static {}, Landroidx/work/ListenableWorker$Result;->failure()Landroidx/work/ListenableWorker$Result;
                move-result-object v0
                return-object v0
            .end method
            """;

    // Pure benign utility class for testing zero mutations
    private static final String BENIGN_UTIL_SMALI =
            """
            .class public Lcom/example/util/MathUtil;
            .super Ljava/lang/Object;
            .source "MathUtil.java"

            .method public constructor <init>()V
                .locals 0
                invoke-direct {p0}, Ljava/lang/Object;-><init>()V
                return-void
            .end method

            .method public static add(II)I
                .locals 1
                add-int v0, p0, p1
                return v0
            .end method
            """;

    // --- Helper Assembly Methods ---

    private File assembleDex(File dir, String dexFileName, String... smaliContents) throws Exception {
        File smaliDir = new File(dir, dexFileName + "_src");
        assertTrue(smaliDir.mkdirs());
        int i = 0;
        for (String smali : smaliContents) {
            File sf = new File(smaliDir, "Class" + (i++) + ".smali");
            try (FileWriter w = new FileWriter(sf)) {
                w.write(smali);
            }
        }
        File outDex = new File(dir, dexFileName);
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = outDex.getPath();
        options.jobs = 1;
        options.apiLevel = 26;
        assertTrue("Smali assembly failed for " + dexFileName, Smali.assemble(options, smaliDir.getPath()));
        return outDex;
    }

    private File buildTestApk(File dir, String apkName, File... dexFiles) throws Exception {
        File apkFile = new File(dir, apkName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apkFile))) {
            // Dummy AndroidManifest.xml
            zos.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zos.write(new byte[]{0x03, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00});
            zos.closeEntry();

            for (File dex : dexFiles) {
                zos.putNextEntry(new ZipEntry(dex.getName()));
                zos.write(Files.readAllBytes(dex.toPath()));
                zos.closeEntry();
            }
        }
        return apkFile;
    }

    private ClassDef findClass(DexBackedDexFile dex, String descriptor) {
        for (ClassDef cd : dex.getClasses()) {
            if (descriptor.equals(cd.getType())) {
                return cd;
            }
        }
        return null;
    }

    // --- Tests ---

    @Test
    public void testMultiDexSanitizationWithGranularAudit() throws Exception {
        File tempDir = Files.createTempDirectory("multi_dex_test").toFile();

        // Assemble DEX 1: Worker (Module A), Rufus (Module B), MainCaller
        File dex1 = assembleDex(tempDir, "classes.dex",
                TELEMETRY_WORKER_SMALI, ACTIVE_AI_MANAGER_SMALI, MAIN_CALLER_SMALI);

        // Assemble DEX 2: Dead AI Manager (Dead code), Telemetry Logger (Module A), SecondaryCaller
        File dex2 = assembleDex(tempDir, "classes2.dex",
                DEAD_AI_MANAGER_SMALI, TELEMETRY_LOGGER_SMALI, SECONDARY_CALLER_SMALI);

        File apkIn = buildTestApk(tempDir, "input_multidex.apk", dex1, dex2);
        File apkOut = new File(tempDir, "output_multidex.apk");

        PrivacyOptions options = PrivacyOptions.all();

        // Run full multi-DEX pipeline with granular audit
        PrivacyAuditReport report = PrivacyBytecodeEngine.sanitizeApkWithAudit(apkIn, apkOut, options, null);

        assertNotNull("Report must not be null", report);
        assertTrue("Sanitization must be reported successful", report.isSanitizationSuccessful());
        assertEquals("Overall status must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.getOverallSanitizationStatus());

        // Verify Module A (Telemetry & Hardware Fingerprinting)
        assertNotNull(report.telemetryModuleResult);
        assertEquals("Module A status must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.telemetryModuleResult.status);
        assertTrue("Module A mutations must be > 0", report.telemetryModuleResult.mutationsApplied > 0);

        // Verify Module B (AI & Assistant Nullification)
        assertNotNull(report.aiModuleResult);
        assertEquals("Module B status must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.aiModuleResult.status);
        assertTrue("Module B mutations must be > 0", report.aiModuleResult.mutationsApplied > 0);

        // Verify Dead Code skipping
        assertNotNull(report.sanitizationStats);
        assertTrue("Dead code methods must be skipped across multi-DEX files", report.sanitizationStats.deadCodeSkipped > 0);

        // Verify Output APK exists and is valid multi-DEX
        assertTrue("Output APK must be created", apkOut.exists() && apkOut.length() > 0);

        // Verify DEX bytecode modifications in output APK
        ApkModule outModule = ApkModule.loadApkFile(apkOut);
        outModule.setLoadDefaultFramework(false);

        DexBackedDexFile outDex1 = null;
        DexBackedDexFile outDex2 = null;

        for (InputSource src : outModule.getInputSources()) {
            if ("classes.dex".equals(src.getName())) {
                byte[] bytes = src.openStream().readAllBytes();
                outDex1 = new DexBackedDexFile(Opcodes.getDefault(), bytes);
            } else if ("classes2.dex".equals(src.getName())) {
                byte[] bytes = src.openStream().readAllBytes();
                outDex2 = new DexBackedDexFile(Opcodes.getDefault(), bytes);
            }
        }

        assertNotNull("classes.dex must exist in output APK", outDex1);
        assertNotNull("classes2.dex must exist in output APK", outDex2);

        // 1. Verify Active Telemetry Worker was neutralized in outDex1
        ClassDef workerDef = findClass(outDex1, "Lcom/example/telemetry/AnalyticsTelemetryWorker;");
        assertNotNull(workerDef);
        Method doWorkMethod = null;
        for (Method m : workerDef.getVirtualMethods()) {
            if ("doWork".equals(m.getName())) doWorkMethod = m;
        }
        assertNotNull(doWorkMethod);
        boolean foundSuccessInvoke = false;
        for (Instruction inst : doWorkMethod.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.INVOKE_STATIC && inst instanceof ReferenceInstruction) {
                MethodReference mr = (MethodReference) ((ReferenceInstruction) inst).getReference();
                if ("success".equals(mr.getName())) foundSuccessInvoke = true;
            }
        }
        assertTrue("doWork() must invoke Result.success()", foundSuccessInvoke);

        // 2. Verify Active AI Manager was stubbed in outDex1 (returns 0/false)
        ClassDef aiDef = findClass(outDex1, "Lcom/example/ai/RufusAssistantManager;");
        assertNotNull(aiDef);
        Method isEligibleMethod = null;
        for (Method m : aiDef.getVirtualMethods()) {
            if ("isEligible".equals(m.getName())) isEligibleMethod = m;
        }
        assertNotNull(isEligibleMethod);
        boolean foundConstZero = false;
        for (Instruction inst : isEligibleMethod.getImplementation().getInstructions()) {
            if ((inst.getOpcode() == Opcode.CONST_4 || inst.getOpcode() == Opcode.CONST_16)
                    && inst instanceof NarrowLiteralInstruction) {
                if (((NarrowLiteralInstruction) inst).getNarrowLiteral() == 0) {
                    foundConstZero = true;
                }
            }
        }
        assertTrue("RufusAssistantManager.isEligible must return const 0", foundConstZero);

        // 3. Verify Dead AI Manager in outDex2 was NOT modified (dead code skipped, still returns 1)
        ClassDef deadAiDef = findClass(outDex2, "Lcom/example/ai/DeadAiManager;");
        assertNotNull(deadAiDef);
        Method deadMethod = null;
        for (Method m : deadAiDef.getVirtualMethods()) {
            if ("supportsAssistant".equals(m.getName())) deadMethod = m;
        }
        assertNotNull(deadMethod);
        boolean deadReturnedOne = false;
        for (Instruction inst : deadMethod.getImplementation().getInstructions()) {
            if ((inst.getOpcode() == Opcode.CONST_4 || inst.getOpcode() == Opcode.CONST_16)
                    && inst instanceof NarrowLiteralInstruction) {
                if (((NarrowLiteralInstruction) inst).getNarrowLiteral() == 1) {
                    deadReturnedOne = true;
                }
            }
        }
        assertTrue("DeadAiManager.supportsAssistant must remain unchanged (return 1)", deadReturnedOne);

        // 4. Verify Log call stripped in outDex2
        ClassDef loggerDef = findClass(outDex2, "Lcom/example/telemetry/TelemetryLogger;");
        assertNotNull(loggerDef);
        Method logMethod = null;
        for (Method m : loggerDef.getDirectMethods()) {
            if ("logEvent".equals(m.getName())) logMethod = m;
        }
        assertNotNull(logMethod);
        boolean foundNop = false;
        for (Instruction inst : logMethod.getImplementation().getInstructions()) {
            if (inst.getOpcode() == Opcode.NOP) foundNop = true;
        }
        assertTrue("Log.d invocation must be replaced by NOP", foundNop);
    }

    @Test
    public void testZeroMatchingTargetsReportWarning() throws Exception {
        File tempDir = Files.createTempDirectory("zero_target_test").toFile();
        File dex1 = assembleDex(tempDir, "classes.dex", BENIGN_UTIL_SMALI);
        File apkIn = buildTestApk(tempDir, "benign.apk", dex1);
        File apkOut = new File(tempDir, "benign_out.apk");

        PrivacyAuditReport report = PrivacyBytecodeEngine.sanitizeApkWithAudit(apkIn, apkOut, PrivacyOptions.all(), null);

        assertNotNull(report);
        assertFalse("Sanitization should not be marked successful when 0 targets matched", report.isSanitizationSuccessful());
        assertEquals(ModuleAuditResult.Status.WARNING_ZERO_MUTATIONS, report.getOverallSanitizationStatus());
        assertEquals(ModuleAuditResult.Status.NOT_DETECTED, report.telemetryModuleResult.status);
        assertEquals(ModuleAuditResult.Status.NOT_DETECTED, report.aiModuleResult.status);
        assertEquals(0, report.getSanitizationStats().totalMutations());
    }

    @Test
    public void testPureDeadCodeSkippedStatus() throws Exception {
        File tempDir = Files.createTempDirectory("dead_code_test").toFile();
        File dex1 = assembleDex(tempDir, "classes.dex", DEAD_AI_MANAGER_SMALI);
        File apkIn = buildTestApk(tempDir, "dead_apk.apk", dex1);
        File apkOut = new File(tempDir, "dead_out.apk");

        PrivacyAuditReport report = PrivacyBytecodeEngine.sanitizeApkWithAudit(apkIn, apkOut, PrivacyOptions.all(), null);

        assertNotNull(report);
        assertFalse(report.isSanitizationSuccessful());
        assertEquals(ModuleAuditResult.Status.WARNING_ZERO_MUTATIONS, report.getOverallSanitizationStatus());
        assertEquals(ModuleAuditResult.Status.NOT_DETECTED, report.telemetryModuleResult.status);
        assertEquals(0, report.telemetryModuleResult.deadCodeSkipped);
        assertEquals(ModuleAuditResult.Status.DEAD_CODE_SKIPPED, report.aiModuleResult.status);
        assertTrue(report.aiModuleResult.deadCodeSkipped > 0);
        assertTrue(report.getSanitizationStats().deadCodeSkipped > 0);
        assertTrue(report.getSanitizationStats().aiDeadCodeSkipped > 0);
        assertEquals(0, report.getSanitizationStats().telemetryDeadCodeSkipped);
        assertEquals(0, report.getSanitizationStats().totalMutations());
    }

    @Test
    public void testPureDeadTelemetryWorkerStatus() throws Exception {
        File tempDir = Files.createTempDirectory("dead_telemetry_test").toFile();
        File dex1 = assembleDex(tempDir, "classes.dex", DEAD_TELEMETRY_WORKER_SMALI);
        File apkIn = buildTestApk(tempDir, "dead_telemetry.apk", dex1);
        File apkOut = new File(tempDir, "dead_telemetry_out.apk");

        PrivacyAuditReport report = PrivacyBytecodeEngine.sanitizeApkWithAudit(apkIn, apkOut, PrivacyOptions.all(), null);

        assertNotNull(report);
        assertFalse(report.isSanitizationSuccessful());
        assertEquals(ModuleAuditResult.Status.WARNING_ZERO_MUTATIONS, report.getOverallSanitizationStatus());
        assertEquals(ModuleAuditResult.Status.DEAD_CODE_SKIPPED, report.telemetryModuleResult.status);
        assertTrue(report.telemetryModuleResult.deadCodeSkipped > 0);
        assertEquals(ModuleAuditResult.Status.NOT_DETECTED, report.aiModuleResult.status);
        assertEquals(0, report.aiModuleResult.deadCodeSkipped);
        assertTrue(report.getSanitizationStats().deadCodeSkipped > 0);
        assertTrue(report.getSanitizationStats().telemetryDeadCodeSkipped > 0);
        assertEquals(0, report.getSanitizationStats().aiDeadCodeSkipped);
        assertEquals(0, report.getSanitizationStats().totalMutations());
    }

    @Test
    public void testLegacySanitizeApkOverloadBuildsReachabilityIndex() throws Exception {
        File tempDir = Files.createTempDirectory("legacy_test").toFile();
        File dex1 = assembleDex(tempDir, "classes.dex", ACTIVE_AI_MANAGER_SMALI, MAIN_CALLER_SMALI);
        File dex2 = assembleDex(tempDir, "classes2.dex", DEAD_AI_MANAGER_SMALI);
        File apkIn = buildTestApk(tempDir, "legacy_in.apk", dex1, dex2);
        File apkOut = new File(tempDir, "legacy_out.apk");

        // Call legacy sanitizeApk overload without providing a reachability index
        PrivacyStats stats = PrivacyBytecodeEngine.sanitizeApk(apkIn, apkOut, PrivacyOptions.all(), null);

        assertNotNull(stats);
        assertTrue("Active AI mutation applied", stats.aiTotalMutations() > 0);
        assertTrue("Dead code skipped automatically by built index", stats.deadCodeSkipped > 0);
        assertTrue(apkOut.exists());
    }
}

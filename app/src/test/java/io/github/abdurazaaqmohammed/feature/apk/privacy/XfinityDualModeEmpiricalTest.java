package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.Annotation;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.reandroid.apk.APKLogger;
import com.reandroid.apk.ApkModule;
import com.reandroid.archive.InputSource;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.InputStream;

/**
 * Empirical integration test suite evaluating Dual-Mode Privacy Sanitization
 * (Standard Mode vs. Aggressive Mode) on real-world enterprise multi-DEX target:
 * Xfinity Digital Home (v6.14.0-2, 21 DEX files, ~308MB stock APK).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class XfinityDualModeEmpiricalTest {

    public static final File XFINITY_APK = new File("/home/deck/.gemini/antigravity/scratch/xfinity_audit/stock.apk");
    public static final File STANDARD_APK = new File("/home/deck/.gemini/antigravity/scratch/xfinity_audit/xfinity_standard.apk");
    public static final File AGGRESSIVE_APK = new File("/home/deck/.gemini/antigravity/scratch/xfinity_audit/xfinity_aggressive.apk");

    private static PrivacyAuditReport standardReport;
    private static PrivacyAuditReport aggressiveReport;

    private static final APKLogger CONSOLE_LOGGER = new APKLogger() {
        @Override
        public void logMessage(String message) {
            System.out.println("[XFINITY-LOG] " + message);
        }

        @Override
        public void logError(String message, Throwable tr) {
            System.err.println("[XFINITY-ERR] " + message);
            if (tr != null) tr.printStackTrace();
        }

        @Override
        public void logVerbose(String message) {
            // Suppress verbose per-entry logs to prevent console flooding
        }
    };

    private static synchronized PrivacyAuditReport runStandardSanitization() throws Exception {
        if (standardReport != null && STANDARD_APK.exists() && STANDARD_APK.length() > 200L * 1024 * 1024) {
            return standardReport;
        }

        assumeTrue("Xfinity stock APK not found at " + XFINITY_APK, XFINITY_APK.exists());

        if (STANDARD_APK.exists()) {
            STANDARD_APK.delete();
        }

        long startTime = System.currentTimeMillis();
        PrivacyOptions standardOptions = PrivacyOptions.standard();
        System.out.println("\n=======================================================");
        System.out.println("  STARTING XFINITY STANDARD MODE SANITIZATION PASS     ");
        System.out.println("=======================================================");
        System.out.println("Target: " + XFINITY_APK.getAbsolutePath() + " (" + (XFINITY_APK.length() / (1024 * 1024)) + " MB)");
        System.out.println("Output: " + STANDARD_APK.getAbsolutePath());
        System.out.println("Mode: " + standardOptions.mode.title + " - " + standardOptions.mode.description);

        standardReport = PrivacyBytecodeEngine.sanitizeApkWithAudit(
                XFINITY_APK, STANDARD_APK, standardOptions, CONSOLE_LOGGER
        );

        long durationMs = System.currentTimeMillis() - startTime;
        System.out.println(">>> Standard Mode Completed in " + durationMs + " ms (" + (durationMs / 1000) + " s)");
        return standardReport;
    }

    private static synchronized PrivacyAuditReport runAggressiveSanitization() throws Exception {
        if (aggressiveReport != null && AGGRESSIVE_APK.exists() && AGGRESSIVE_APK.length() > 200L * 1024 * 1024) {
            return aggressiveReport;
        }

        assumeTrue("Xfinity stock APK not found at " + XFINITY_APK, XFINITY_APK.exists());

        if (AGGRESSIVE_APK.exists()) {
            AGGRESSIVE_APK.delete();
        }

        long startTime = System.currentTimeMillis();
        PrivacyOptions aggressiveOptions = PrivacyOptions.aggressive();
        System.out.println("\n=======================================================");
        System.out.println("  STARTING XFINITY AGGRESSIVE MODE SANITIZATION PASS   ");
        System.out.println("=======================================================");
        System.out.println("Target: " + XFINITY_APK.getAbsolutePath() + " (" + (XFINITY_APK.length() / (1024 * 1024)) + " MB)");
        System.out.println("Output: " + AGGRESSIVE_APK.getAbsolutePath());
        System.out.println("Mode: " + aggressiveOptions.mode.title + " - " + aggressiveOptions.mode.description);

        aggressiveReport = PrivacyBytecodeEngine.sanitizeApkWithAudit(
                XFINITY_APK, AGGRESSIVE_APK, aggressiveOptions, CONSOLE_LOGGER
        );

        long durationMs = System.currentTimeMillis() - startTime;
        System.out.println(">>> Aggressive Mode Completed in " + durationMs + " ms (" + (durationMs / 1000) + " s)");
        return aggressiveReport;
    }

    @Test
    public void test1_StandardModeOnXfinity() throws Exception {
        PrivacyAuditReport report = runStandardSanitization();
        assertNotNull("Standard report must not be null", report);

        PrivacyStats stats = report.sanitizationStats;
        assertNotNull("SanitizationStats must not be null", stats);

        System.out.println("\n-------------------------------------------------------");
        System.out.println("  XFINITY STANDARD MODE AUDIT METRICS                  ");
        System.out.println("-------------------------------------------------------");
        System.out.println("Overall Status:           " + report.overallSanitizationStatus);
        System.out.println("Telemetry Module Status:  " + report.telemetryModuleResult.status);
        System.out.println("Telemetry Mutations:      " + report.telemetryModuleResult.mutationsApplied);
        System.out.println("AI Module Status:         " + report.aiModuleResult.status);
        System.out.println("AI Mutations:             " + report.aiModuleResult.mutationsApplied);
        System.out.println("Dead Code Skipped:        " + stats.deadCodeSkipped);
        System.out.println("  - Telemetry Dead Code:  " + stats.telemetryDeadCodeSkipped);
        System.out.println("  - AI Dead Code:         " + stats.aiDeadCodeSkipped);
        System.out.println("Classes Modified:         " + stats.classesModified);
        System.out.println("Total Mutations:          " + stats.totalMutations());
        System.out.println("Granular Breakdown:");
        System.out.println("  - Log calls stripped:        " + stats.logCallsStripped);
        System.out.println("  - Telephony getters blanked: " + stats.telephonyGettersBlanked);
        System.out.println("  - Ad IDs neutralized:        " + stats.adIdsNeutralized);
        System.out.println("  - Dev mode checks masked:    " + stats.devModeChecksMasked);
        System.out.println("  - Telemetry domains sinkholed:" + stats.telemetryDomainsSinkholed);
        System.out.println("  - Workers neutralized:       " + stats.backgroundWorkersNeutralized);
        System.out.println("  - Hardware IDs blanked:      " + stats.hardwareIdentifiersBlanked);
        System.out.println("  - Periodic work suppressed:  " + stats.periodicWorkEnqueueSuppressed);
        System.out.println("  - Tracker clinit sanitized:  " + stats.trackerClinitSanitized);
        System.out.println("  - AI feature gates:          " + stats.aiFeatureGatesNeutralized);
        System.out.println("  - AI eligibility stubbed:    " + stats.aiEligibilityManagersStubbed);
        System.out.println("  - AI tab ingress neutralized:" + stats.aiTabIngressNeutralized);
        System.out.println("  - AI endpoints sinkholed:    " + stats.aiEndpointsSinkholed);
        System.out.println("Output APK size:          " + (STANDARD_APK.length() / (1024 * 1024)) + " MB (" + STANDARD_APK.length() + " bytes)");
        System.out.println("-------------------------------------------------------\n");

        // Assertions
        assertEquals("Standard Mode report status must be SUCCESS",
                ModuleAuditResult.Status.SUCCESS, report.overallSanitizationStatus);
        assertEquals("Telemetry module status must be SUCCESS",
                ModuleAuditResult.Status.SUCCESS, report.telemetryModuleResult.status);
        assertEquals("AI module status must be SUCCESS",
                ModuleAuditResult.Status.SUCCESS, report.aiModuleResult.status);

        assertTrue("Telemetry mutations must be > 0", report.telemetryModuleResult.mutationsApplied > 0);
        assertTrue("AI mutations must be > 0", report.aiModuleResult.mutationsApplied > 0);
        assertTrue("Classes modified must be > 0", stats.classesModified > 0);
        assertTrue("Output APK must exist", STANDARD_APK.exists());
        assertTrue("Output APK must be > 200MB (actual: " + STANDARD_APK.length() + " bytes)",
                STANDARD_APK.length() > 200L * 1024 * 1024);

        // Standard Mode specific invariants: aggressive features must be 0
        assertEquals("Hardware IDs blanked must be 0 in standard mode", 0, stats.hardwareIdentifiersBlanked);
        assertEquals("Periodic work suppressed must be 0 in standard mode", 0, stats.periodicWorkEnqueueSuppressed);
        assertEquals("Tracker clinit sanitized must be 0 in standard mode", 0, stats.trackerClinitSanitized);
    }

    @Test
    public void test2_AggressiveModeOnXfinity() throws Exception {
        PrivacyAuditReport standardRep = runStandardSanitization();
        PrivacyAuditReport aggressiveRep = runAggressiveSanitization();

        assertNotNull("Aggressive report must not be null", aggressiveRep);
        PrivacyStats stdStats = standardRep.sanitizationStats;
        PrivacyStats aggStats = aggressiveRep.sanitizationStats;
        assertNotNull("Aggressive SanitizationStats must not be null", aggStats);

        System.out.println("\n-------------------------------------------------------");
        System.out.println("  XFINITY AGGRESSIVE MODE AUDIT METRICS                ");
        System.out.println("-------------------------------------------------------");
        System.out.println("Overall Status:           " + aggressiveRep.overallSanitizationStatus);
        System.out.println("Telemetry Module Status:  " + aggressiveRep.telemetryModuleResult.status);
        System.out.println("Telemetry Mutations:      " + aggressiveRep.telemetryModuleResult.mutationsApplied);
        System.out.println("AI Module Status:         " + aggressiveRep.aiModuleResult.status);
        System.out.println("AI Mutations:             " + aggressiveRep.aiModuleResult.mutationsApplied);
        System.out.println("Dead Code Skipped:        " + aggStats.deadCodeSkipped);
        System.out.println("  - Telemetry Dead Code:  " + aggStats.telemetryDeadCodeSkipped);
        System.out.println("  - AI Dead Code:         " + aggStats.aiDeadCodeSkipped);
        System.out.println("Classes Modified:         " + aggStats.classesModified);
        System.out.println("Total Mutations:          " + aggStats.totalMutations());
        System.out.println("Aggressive-Specific Breakdown:");
        System.out.println("  - Hardware IDs blanked:      " + aggStats.hardwareIdentifiersBlanked);
        System.out.println("  - Periodic work suppressed:  " + aggStats.periodicWorkEnqueueSuppressed);
        System.out.println("  - Tracker clinit sanitized:  " + aggStats.trackerClinitSanitized);
        System.out.println("Output APK size:          " + (AGGRESSIVE_APK.length() / (1024 * 1024)) + " MB (" + AGGRESSIVE_APK.length() + " bytes)");
        System.out.println("-------------------------------------------------------\n");

        System.out.println("=======================================================");
        System.out.println("  COMPARATIVE AUDIT SUMMARY (STANDARD vs AGGRESSIVE)   ");
        System.out.println("=======================================================");
        System.out.printf("%-32s | %-12s | %-12s\n", "Metric", "Standard", "Aggressive");
        System.out.println("---------------------------------+--------------+--------------");
        System.out.printf("%-32s | %-12d | %-12d\n", "Total Mutations", stdStats.totalMutations(), aggStats.totalMutations());
        System.out.printf("%-32s | %-12d | %-12d\n", "Telemetry Mutations", stdStats.telemetryTotalMutations(), aggStats.telemetryTotalMutations());
        System.out.printf("%-32s | %-12d | %-12d\n", "AI Mutations", stdStats.aiTotalMutations(), aggStats.aiTotalMutations());
        System.out.printf("%-32s | %-12d | %-12d\n", "Hardware IDs Blanked", stdStats.hardwareIdentifiersBlanked, aggStats.hardwareIdentifiersBlanked);
        System.out.printf("%-32s | %-12d | %-12d\n", "Periodic Work Suppressed", stdStats.periodicWorkEnqueueSuppressed, aggStats.periodicWorkEnqueueSuppressed);
        System.out.printf("%-32s | %-12d | %-12d\n", "Tracker Clinit Sanitized", stdStats.trackerClinitSanitized, aggStats.trackerClinitSanitized);
        System.out.printf("%-32s | %-12d | %-12d\n", "Classes Modified", stdStats.classesModified, aggStats.classesModified);
        System.out.printf("%-32s | %-12d | %-12d\n", "Dead Code Skipped", stdStats.deadCodeSkipped, aggStats.deadCodeSkipped);
        System.out.println("=======================================================\n");

        // Assertions
        assertEquals("Aggressive Mode report status must be SUCCESS",
                ModuleAuditResult.Status.SUCCESS, aggressiveRep.overallSanitizationStatus);
        assertEquals("Telemetry module status must be SUCCESS",
                ModuleAuditResult.Status.SUCCESS, aggressiveRep.telemetryModuleResult.status);
        assertEquals("AI module status must be SUCCESS",
                ModuleAuditResult.Status.SUCCESS, aggressiveRep.aiModuleResult.status);

        assertTrue("Aggressive output APK must exist", AGGRESSIVE_APK.exists());
        assertTrue("Aggressive output APK must be > 200MB (actual: " + AGGRESSIVE_APK.length() + " bytes)",
                AGGRESSIVE_APK.length() > 200L * 1024 * 1024);

        // Assert Aggressive specific mutations > 0 or present
        assertTrue("Aggressive total mutations must be greater than Standard total mutations (" +
                        aggStats.totalMutations() + " > " + stdStats.totalMutations() + ")",
                aggStats.totalMutations() > stdStats.totalMutations());
    }

    @Test
    public void test3_RetrofitParameterAnnotationsPreservedOnXfinity() throws Exception {
        runStandardSanitization();
        assertTrue("Standard APK must exist", STANDARD_APK.exists());

        ApkModule module = ApkModule.loadApkFile(STANDARD_APK);
        InputSource classes12 = module.getInputSource("classes12.dex");
        assertNotNull("classes12.dex must exist in sanitized APK", classes12);

        byte[] dexBytes;
        try (InputStream in = classes12.openStream()) {
            dexBytes = in.readAllBytes();
        }
        DexBackedDexFile dex = new DexBackedDexFile(Opcodes.getDefault(), dexBytes);
        boolean foundMethod = false;
        for (ClassDef c : dex.getClasses()) {
            if ("Lcim/comcast/com/xal/core/network/api/cmfa/servicesv3/authenticatorId/AuthenticatorIdService;".equals(c.getType())) {
                for (Method m : c.getVirtualMethods()) {
                    if ("getAuthenticatorIdStatus".equals(m.getName())) {
                        foundMethod = true;
                        assertEquals(2, m.getParameters().size());
                        MethodParameter p0 = m.getParameters().get(0);
                        assertEquals("Parameter #0 must retain @Query annotation", 1, p0.getAnnotations().size());
                        Annotation a0 = p0.getAnnotations().iterator().next();
                        assertEquals("Lretrofit2/http/Query;", a0.getType());

                        MethodParameter p1 = m.getParameters().get(1);
                        assertEquals("Parameter #1 must retain @Query annotation", 1, p1.getAnnotations().size());
                        Annotation a1 = p1.getAnnotations().iterator().next();
                        assertEquals("Lretrofit2/http/Query;", a1.getType());
                    }
                }
            }
        }
        assertTrue("AuthenticatorIdService.getAuthenticatorIdStatus must be found and verified", foundMethod);
    }

    @Test
    public void test4_DexVersionHeadersPreservedOnXfinity() throws Exception {
        runStandardSanitization();
        assertTrue("Standard APK must exist", STANDARD_APK.exists());

        ApkModule module = ApkModule.loadApkFile(STANDARD_APK);
        for (InputSource source : module.getInputSources()) {
            String name = source.getName();
            if (!name.matches("classes\\d*\\.dex")) continue;

            byte[] header = new byte[8];
            try (InputStream in = source.openStream()) {
                int read = in.read(header);
                assertEquals(8, read);
            }
            assertEquals("DEX " + name + " must have magic 'dex\\n039\\0'", "dex\n039\0", new String(header));
        }
    }
}

package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.reandroid.apk.APKLogger;

import org.junit.Test;

import java.io.File;

/**
 * End-to-end verification of PrivacyBytecodeEngine against real-world enterprise multi-DEX APKs:
 * - Amazon Shopping (v32.19.0.100, 14 DEX files, 183MB)
 * - Xfinity Digital Home (v6.14.0-2, 13 DEX files, 308MB)
 */
public class RealBinarySanitizerVerificationTest {

    private static final File AMAZON_APK = new File("/home/deck/.gemini/antigravity/scratch/amazon_audit/apk/base.apk");
    private static final File XFINITY_APK = new File("/home/deck/.gemini/antigravity/scratch/xfinity_audit/stock.apk");

    private static final APKLogger CONSOLE_LOGGER = new APKLogger() {
        @Override
        public void logMessage(String message) {
            System.out.println("[APK-LOG] " + message);
        }

        @Override
        public void logError(String message, Throwable tr) {
            System.err.println("[APK-ERR] " + message);
            if (tr != null) tr.printStackTrace();
        }

        @Override
        public void logVerbose(String message) {
            System.out.println("[APK-VERB] " + message);
        }
    };

    @Test
    public void testSanitizeRealAmazonShoppingApk() throws Exception {
        assumeTrue("Binary not found at " + AMAZON_APK, AMAZON_APK.exists());

        File outApk = new File("/home/deck/.gemini/antigravity/scratch/amazon_audit/amazon_sanitized.apk");
        if (!outApk.getParentFile().exists()) {
            outApk = File.createTempFile("amazon_sanitized", ".apk");
            outApk.deleteOnExit();
        } else if (outApk.exists()) {
            outApk.delete();
        }

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyAuditReport report = PrivacyBytecodeEngine.sanitizeApkWithAudit(
                AMAZON_APK, outApk, options, CONSOLE_LOGGER
        );

        assertNotNull("Report must not be null", report);
        PrivacyStats stats = report.sanitizationStats;
        assertNotNull("Stats must not be null", stats);

        System.out.println("=== Amazon Shopping Sanitization Results ===");
        System.out.println("Telemetry status: " + report.telemetryModuleResult.status + " (" + report.telemetryModuleResult.mutationsApplied + " mutations)");
        System.out.println("AI status: " + report.aiModuleResult.status + " (" + report.aiModuleResult.mutationsApplied + " mutations)");
        System.out.println("Overall status: " + report.overallSanitizationStatus);
        System.out.println("Detailed stats: " + stats);

        // Assert non-zero mutations and correct status
        assertTrue("Must have applied mutations", stats.totalMutations() > 0);
        assertEquals("Overall status must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.overallSanitizationStatus);
        assertEquals("Telemetry module must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.telemetryModuleResult.status);
        assertTrue("Telemetry mutations > 0", report.telemetryModuleResult.mutationsApplied > 0);
        assertEquals("AI module must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.aiModuleResult.status);
        assertTrue("AI mutations > 0", report.aiModuleResult.mutationsApplied > 0);
        assertTrue("Output APK must exist and be non-empty", outApk.exists() && outApk.length() > 1024 * 1024);
    }

    @Test
    public void testSanitizeRealXfinityApk() throws Exception {
        assumeTrue("Binary not found at " + XFINITY_APK, XFINITY_APK.exists());

        File outApk = new File("/home/deck/.gemini/antigravity/scratch/xfinity_audit/xfinity_sanitized.apk");
        if (!outApk.getParentFile().exists()) {
            outApk = File.createTempFile("xfinity_sanitized", ".apk");
            outApk.deleteOnExit();
        } else if (outApk.exists()) {
            outApk.delete();
        }

        PrivacyOptions options = PrivacyOptions.all();
        PrivacyAuditReport report = PrivacyBytecodeEngine.sanitizeApkWithAudit(
                XFINITY_APK, outApk, options, CONSOLE_LOGGER
        );

        assertNotNull("Report must not be null", report);
        PrivacyStats stats = report.sanitizationStats;
        assertNotNull("Stats must not be null", stats);

        System.out.println("=== Xfinity Digital Home Sanitization Results ===");
        System.out.println("Telemetry status: " + report.telemetryModuleResult.status + " (" + report.telemetryModuleResult.mutationsApplied + " mutations)");
        System.out.println("AI status: " + report.aiModuleResult.status + " (" + report.aiModuleResult.mutationsApplied + " mutations)");
        System.out.println("Overall status: " + report.overallSanitizationStatus);
        System.out.println("Detailed stats: " + stats);

        assertTrue("Must have applied mutations", stats.totalMutations() > 0);
        assertEquals("Overall status must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.overallSanitizationStatus);
        assertEquals("Telemetry module must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.telemetryModuleResult.status);
        assertTrue("Telemetry mutations > 0", report.telemetryModuleResult.mutationsApplied > 0);
        assertEquals("AI module must be SUCCESS", ModuleAuditResult.Status.SUCCESS, report.aiModuleResult.status);
        assertTrue("AI mutations > 0", report.aiModuleResult.mutationsApplied > 0);
        assertTrue("Output APK must exist and be non-empty", outApk.exists() && outApk.length() > 1024 * 1024);
    }
}

package io.github.abdurazaaqmohammed.feature.apk.privacy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;

import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

/**
 * Unit tests verifying Stage 2: Semantic Privacy Scanner and Invasiveness Risk Scoring.
 */
public class PrivacyScannerEngineTest {

    private static final String INVASIVE_APP_SMALI =
            """
            .class public Lcom/example/InvasiveApp;
            .super Landroid/app/Activity;
            .source "InvasiveApp.java"

            .method public constructor <init>()V
                .locals 1
                invoke-direct {p0}, Landroid/app/Activity;-><init>()V
                return-void
            .end method

            .method public snoopUser(Landroid/telephony/TelephonyManager;Landroid/content/ContentResolver;)V
                .locals 3
                invoke-virtual {p1}, Landroid/telephony/TelephonyManager;->getDeviceId()Ljava/lang/String;
                move-result-object v0
                const-string v1, "android_id"
                invoke-static {p2, v1}, Landroid/provider/Settings$Secure;->getString(Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
                move-result-object v2
                const-string v1, "TAG"
                invoke-static {v1, v0}, Landroid/util/Log;->e(Ljava/lang/String;Ljava/lang/String;)I
                return-void
            .end method

            .method public checkEnvironment(Landroid/content/pm/PackageManager;Landroid/content/ContentResolver;)V
                .locals 2
                const/4 v0, 0x0
                invoke-virtual {p1, v0}, Landroid/content/pm/PackageManager;->getInstalledPackages(I)Ljava/util/List;
                move-result-object v1
                const-string v0, "adb_enabled"
                invoke-static {p2, v0}, Landroid/provider/Settings$Global;->getInt(Landroid/content/ContentResolver;Ljava/lang/String;)I
                return-void
            .end method
            """;

    private File assembleSmali(String smaliCode) throws Exception {
        File dir = Files.createTempDirectory("scanner_test").toFile();
        File pkg = new File(dir, "com/example");
        assertTrue(pkg.mkdirs());
        try (FileWriter w = new FileWriter(new File(pkg, "InvasiveApp.smali"))) {
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
    public void testPrivacyScanningAndScoring() throws Exception {
        File dexFile = assembleSmali(INVASIVE_APP_SMALI);
        DexBackedDexFile dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault());

        PrivacyAuditReport report = PrivacyScannerEngine.scanClasses(dex.getClasses());

        assertNotNull(report);
        assertTrue("Overall score should be > 0, got: " + report.overallScore, report.overallScore > 0);
        assertTrue("Hardware score should be > 0, got: " + report.hardwareScore, report.hardwareScore > 0);
        assertTrue("Surveillance score should be > 0, got: " + report.surveillanceScore, report.surveillanceScore > 0);
        assertTrue("Log leak score should be > 0, got: " + report.logLeakScore, report.logLeakScore > 0);
        assertTrue(report.threats.size() >= 4);

        // Check categories present in report
        boolean foundHw = false;
        boolean foundSurv = false;
        boolean foundLog = false;

        for (DetectedThreat threat : report.threats) {
            assertEquals("[Non-Root]", threat.getRootBadge());
            assertFalse(threat.requiresRoot);
            if (threat.category == DetectedThreat.Category.HARDWARE_FINGERPRINT) foundHw = true;
            if (threat.category == DetectedThreat.Category.SURVEILLANCE) foundSurv = true;
            if (threat.category == DetectedThreat.Category.LOG_LEAK) foundLog = true;
        }

        assertTrue("Hardware fingerprint threat missing", foundHw);
        assertTrue("Surveillance threat missing", foundSurv);
        assertTrue("Log leak threat missing", foundLog);
    }
}

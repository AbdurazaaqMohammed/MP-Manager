package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DialogEditorEngineTest {

    @Test
    public void packageRoundTrip() throws Exception {
        DialogEditorEngine.DialogPackage pack =
                new DialogEditorEngine.DialogPackage();
        pack.name = "Test";
        pack.injectionMethod = "SMALI";
        pack.dialog = new OverlayInjectorUtil.DialogOptions();
        pack.dialog.title = "Title";

        DialogEditorEngine.DialogPackage imported =
                DialogEditorEngine.fromJson(DialogEditorEngine.toJson(pack));

        assertEquals("Test", imported.name);
        assertEquals("SMALI", imported.injectionMethod);
        assertEquals("Title", imported.dialog.title);
    }

    @Test
    public void repairNormalizesInvalidValues() {
        DialogEditorEngine.DialogPackage pack =
                new DialogEditorEngine.DialogPackage();
        pack.dialog = new OverlayInjectorUtil.DialogOptions();
        pack.dialog.cornerRadiusDp = -20f;
        pack.dialog.animSpeedMs = 20;

        DialogEditorEngine.repair(pack);

        assertEquals(0f, pack.dialog.cornerRadiusDp, 0f);
        assertEquals(100, pack.dialog.animSpeedMs);
    }

    @Test
    public void analyzeCountsApkEntries() throws Exception {
        File apk = File.createTempFile("dialog-engine", ".apk");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(apk))) {
            zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("classes2.dex"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("res/layout/dialog.xml"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("res/drawable/icon.png"));
            zip.closeEntry();
        }

        DialogEditorEngine.AnalysisResult result =
                DialogEditorEngine.analyzeApk(apk);

        assertEquals(2, result.dexCount);
        assertEquals(1, result.xmlCount);
        assertEquals(1, result.imageCount);
        assertTrue(result.manifestFound);
        assertFalse(result.issues.isEmpty());
        assertEquals(DialogEditorEngine.InjectionMethod.SMALI, result.recommended);
        apk.delete();
    }
}

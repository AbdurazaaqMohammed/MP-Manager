package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** JVM checks for the single-pass archive scan used by the APK info loader. */
public class ApkInfoLoaderScanTest {

    private static File zip(String name, Map<String, Boolean> entries) throws IOException {
        File f = File.createTempFile("apk-info-scan", ".apk");
        f.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(f))) {
            // FlutterDetector requires at least 1 KB; a ZIP comment pads the
            // file without adding archive entries.
            zos.setComment(new String(new char[1200]).replace('\0', 'c'));
            for (Map.Entry<String, Boolean> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                if (!e.getValue()) zos.write(new byte[]{1, 2, 3, 4});
                zos.closeEntry();
            }
        }
        return f;
    }

    private static Map<String, Boolean> entries(String... names) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String name : names) result.put(name, false);
        return result;
    }

    @Test
    public void derivesAllRowsFromOneHeaderPass() throws Exception {
        File apk = zip("mixed", entries(
                "AndroidManifest.xml", "classes.dex", "lib/arm64-v8a/libil2cpp.so",
                "lib/arm64-v8a/libfoo.so", "assets/flutter_assets/isolate_snapshot.bin"));
        ApkInfoLoader.ArchiveFacts facts = ApkInfoLoader.scanArchive(apk);
        assertEquals(5, facts.entryCount);
        assertTrue(facts.il2cpp);
        assertTrue(facts.nativeLibs);
        assertTrue(facts.flutter);
    }

    @Test
    public void preservesDetectorPredicates() throws Exception {
        File directoryOnly = zip("dirs", entries(
                "lib/", "lib/arm64-v8a/", "lib/arm64-v8a/libil2cpp.so/"));
        ApkInfoLoader.ArchiveFacts facts = ApkInfoLoader.scanArchive(directoryOnly);
        assertEquals(3, facts.entryCount);
        assertFalse("a directory named like a library is not a native lib", facts.nativeLibs);
        assertFalse("a directory named like il2cpp is not il2cpp", facts.il2cpp);
        assertFalse(facts.flutter);

        File unrelated = zip("unrelated", entries(
                "assets/libapp.so.txt", "res/raw/libflutter.so.bak", "lib/arm64-v8a/libother.so"));
        facts = ApkInfoLoader.scanArchive(unrelated);
        // FlutterDetector uses contains(), so these are considered Flutter names.
        assertTrue(facts.flutter);
        assertTrue(facts.nativeLibs);
        assertFalse(facts.il2cpp);
    }

    @Test
    public void emptyArchiveHasZeroCount() throws Exception {
        File apk = zip("empty", entries());
        ApkInfoLoader.ArchiveFacts facts = ApkInfoLoader.scanArchive(apk);
        assertEquals(0, facts.entryCount);
        assertFalse(facts.il2cpp);
        assertFalse(facts.nativeLibs);
        assertFalse(facts.flutter);
    }
}

package io.github.abdurazaaqmohammed.feature.apk;

import static org.junit.Assert.*;

import android.content.Context;
import android.sun.security.provider.JavaKeyStoreProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.apksig.ApkVerifier;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Security;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import io.github.abdurazaaqmohammed.privilege.internal.NativeToolManager;
import io.github.abdurazaaqmohammed.util.archive.Aapt2Jni;
import io.github.abdurazaaqmohammed.util.io.FileUtils;
import io.github.abdurazaaqmohammed.utils.ApkZipAlignUtil;

/** Exercises the downloaded, patched AAPT2 through the JNI runner under the app UID. */
public class Aapt2OptimizerIntegrationTest {
    /** main() offset in the termux x86_64 build shipped as the test asset. */
    private static final long X86_64_MAIN_OFFSET = 0xF8305L;

    private File binary;

    @Before public void prepareBinary() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue("The test asset is only available for x86_64",
                "x86_64".equals(NativeToolManager.deviceAbi()));
        Assume.assumeTrue("The JNI runner must be packaged in the test APK", Aapt2Jni.isAvailable());
        File dir = new File(app.getCacheDir(), "aapt2-jni-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        binary = new File(dir, "aapt2");
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("aapt2-x86_64.bin");
             FileOutputStream out = new FileOutputStream(binary)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        Aapt2BinaryManager.clearPieFlag(binary);
        assertTrue(binary.setExecutable(true));
    }

    private File fixture(Context app) throws Exception {
        File dir = new File(app.getCacheDir(), "aapt2-integration-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        File input = new File(dir, "fixture.apk");
        try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("aapt2-fixture.apk");
             FileOutputStream out = new FileOutputStream(input)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        return input;
    }

    private File optimize(Context app, File input, Aapt2Optimizer.Options options) throws Exception {
        return Aapt2Optimizer.optimize(binary, app.getCacheDir(), input, options, X86_64_MAIN_OFFSET);
    }

    private String dump(Context app, File apk) throws Exception {
        File log = File.createTempFile("aapt2-dump-", ".txt", app.getCacheDir());
        try {
            int exit = Aapt2Jni.run(binary,
                    new String[]{"aapt2", "dump", "resources", apk.getAbsolutePath()},
                    log, app.getCacheDir(), X86_64_MAIN_OFFSET);
            assertEquals(0, exit);
            return new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        } finally {
            log.delete();
        }
    }

    @Test public void scansDensitiesLanguagesAndAbisFromTheFixture() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = fixture(app);
        Aapt2ApkSpecs.Specs specs = Aapt2ApkSpecs.scan(input);
        assertTrue(specs.densities.toString(), specs.containsIgnoreCase(specs.densities, "hdpi"));
        assertTrue(specs.densities.toString(), specs.containsIgnoreCase(specs.densities, "xhdpi"));
        assertFalse(specs.languages.toString(), specs.languages.isEmpty());
        assertTrue(specs.languages.toString(), specs.containsIgnoreCase(specs.languages, "fr"));
        assertTrue(specs.abis.toString(), specs.containsIgnoreCase(specs.abis, "arm64-v8a"));
        assertTrue(specs.abis.toString(), specs.containsIgnoreCase(specs.abis, "x86_64"));
        assertTrue(specs.missing(java.util.Arrays.asList("xhdpi", "xxhdpi"),
                specs.densities).contains("xxhdpi"));
    }

    @Test public void optionsFilterResourcesAndAbisAndUpdateSdkWithoutTouchingInput() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = fixture(app);
        byte[] original = Files.readAllBytes(input.toPath());
        File output = optimize(app, input, new Aapt2Optimizer.Options("xhdpi", "fr", "arm64-v8a", "24", "30"));
        assertNotEquals(input, output);
        assertArrayEquals(original, Files.readAllBytes(input.toPath()));
        assertNull(ApkZipAlignUtil.installIssue(output));
        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("lib/arm64-v8a/libfixture.so"));
            assertNull(zip.getEntry("lib/x86_64/libfixture.so"));
            assertNull(zip.getEntry("META-INF/OLD.SF"));
            assertNotNull(zip.getEntry("classes.dex"));
            assertNull("unneeded density removed", zip.getEntry("res/drawable-hdpi-v4/icon.xml"));
            assertNotNull("requested density retained", zip.getEntry("res/drawable-xhdpi-v4/icon.xml"));
            ZipEntry manifestEntry = zip.getEntry("AndroidManifest.xml");
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                AndroidManifestBlock manifest = AndroidManifestBlock.load(in);
                assertEquals(Integer.valueOf(24), manifest.getMinSdkVersion());
                assertEquals(Integer.valueOf(30), manifest.getTargetSdkVersion());
            }
        }
    }

    @Test public void blankFieldsPreserveBothArchitecturesAndSdk() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = fixture(app);
        File output = optimize(app, input, new Aapt2Optimizer.Options("", "", "", "", ""));
        try (ZipFile zip = new ZipFile(output)) {
            assertNotNull(zip.getEntry("lib/arm64-v8a/libfixture.so"));
            assertNotNull(zip.getEntry("lib/x86_64/libfixture.so"));
            assertNull(zip.getEntry("META-INF/OLD.SF"));
            try (InputStream in = zip.getInputStream(zip.getEntry("AndroidManifest.xml"))) {
                AndroidManifestBlock manifest = AndroidManifestBlock.load(in);
                assertEquals(Integer.valueOf(23), manifest.getMinSdkVersion());
                assertEquals(Integer.valueOf(28), manifest.getTargetSdkVersion());
            }
        }
    }

    @Test public void unknownAbiFailsInsteadOfRemovingEveryLibrary() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = fixture(app);
        try {
            optimize(app, input, new Aapt2Optimizer.Options("", "", "x86", "", ""));
            fail("Expected absent ABI to be rejected");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("architectures"));
        }
        assertEquals(1, input.getParentFile().listFiles().length);
    }

    @Test public void configurationFilterRemovesUnselectedLanguage() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = fixture(app);
        File output = optimize(app, input, new Aapt2Optimizer.Options("", "en", "", "", ""));
        String resourceTable = dump(app, output);
        assertTrue(resourceTable, resourceTable.contains("Fixture"));
        assertFalse(resourceTable, resourceTable.contains("Exemple"));
    }

    @Test public void sdkOverrideMustNotConflictWithPreservedValue() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = fixture(app);
        try {
            optimize(app, input, new Aapt2Optimizer.Options("", "", "", "29", ""));
            fail("Expected conflict with existing target SDK 28");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Minimum SDK"));
        }
        assertEquals(1, input.getParentFile().listFiles().length);
    }

    @Test public void existingOutputIsNeverOverwritten() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File input = fixture(app);
        File existing = new File(input.getParentFile(), "fixture_aapt2.apk");
        byte[] sentinel = new byte[]{1, 2, 3};
        Files.write(existing.toPath(), sentinel);
        File result = optimize(app, input, new Aapt2Optimizer.Options("", "", "", "", ""));
        assertNotEquals(existing, result);
        assertArrayEquals(sentinel, Files.readAllBytes(existing.toPath()));
    }

    @Test public void optionalSigningProducesVerifiableApk() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // MainActivity registers this provider before the real dialog is used.
        Security.addProvider(new JavaKeyStoreProvider());
        File input = fixture(app);
        File output = optimize(app, input, new Aapt2Optimizer.Options("", "", "", "", ""));
        SignWrapper signer = new SignWrapper(FileUtils.getDebugKeystore(app), "android", true, true, false, false);
        Aapt2Optimizer.signOutput(app, output, signer);
        ApkVerifier.Result verification = new ApkVerifier.Builder(output).build().verify();
        assertTrue(verification.getErrors().toString(), verification.isVerified());
        assertNull(ApkZipAlignUtil.installIssue(output));
    }

    @Test public void signingDoesNotOverwriteExistingV4Sidecar() throws Exception {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Security.addProvider(new JavaKeyStoreProvider());
        File input = fixture(app);
        File output = optimize(app, input, new Aapt2Optimizer.Options("", "", "", "", ""));
        File existing = new File(input.getParentFile(), "fixture_aapt2.idsig");
        byte[] sentinel = new byte[]{4, 5, 6};
        Files.write(existing.toPath(), sentinel);
        SignWrapper signer = new SignWrapper(FileUtils.getDebugKeystore(app), "android", true, true, false, true);
        try {
            Aapt2Optimizer.signOutput(app, output, signer);
            fail("Expected existing sidecar to be protected");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("sidecar"));
        }
        assertArrayEquals(sentinel, Files.readAllBytes(existing.toPath()));
    }
}

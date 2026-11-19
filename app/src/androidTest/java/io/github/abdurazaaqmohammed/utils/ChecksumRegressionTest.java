package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.github.abdurazaaqmohammed.ui.main.ChecksumDialogs;
import io.github.abdurazaaqmohammed.util.hash.HashUtil;

/**
 * Exercises the checksum verification path on a real device through the
 * production entry opener ({@link ChecksumDialogs#entryOpener}): plain reads
 * for app-owned files, fast-fail "missing" for absent targets and no opens at
 * all for unsafe names.
 */
public class ChecksumRegressionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Context context;
    private File dir;

    @Before public void prepare() throws Exception {
        context = instrumentation.getTargetContext();
        dir = new File(context.getCacheDir(), "checksum-regression");
        assertTrue(dir.isDirectory() || dir.mkdirs());
    }

    @After public void cleanup() {
        deleteRecursively(dir);
    }

    private static void deleteRecursively(File file) {
        File[] kids = file.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private File write(String name, String content) throws Exception {
        File f = new File(dir, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return f;
    }

    private static String hashOf(String content, String algorithm) throws Exception {
        try (InputStream in = new java.io.ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            return HashUtil.hashStream(in, algorithm);
        }
    }

    @Test public void stagingOpenerClassifiesValidChangedMissingAndRejected() throws Exception {
        write("a.txt", "hello");
        write("b.txt", "world");
        File manifest = write("payload.md5",
                hashOf("hello", "MD5") + "  a.txt\n"
                        + hashOf("world", "MD5") + "  b.txt\n"
                        + hashOf("ghost", "MD5") + "  gone.txt\n"
                        + hashOf("ghost", "MD5") + "  ../escape.txt\n");
        write("b.txt", "tampered");

        List<HashUtil.CheckResult> results = HashUtil.verifyChecksumFile(manifest,
                ChecksumDialogs.entryOpener(context, dir));

        assertEquals(4, results.size());
        assertTrue(results.get(0).valid);

        HashUtil.CheckResult changed = results.get(1);
        assertFalse(changed.valid);
        assertFalse("changed is not missing", changed.missing);
        assertFalse(changed.rejected);
        assertEquals(hashOf("tampered", "MD5"), changed.actual);

        HashUtil.CheckResult missing = results.get(2);
        assertFalse(missing.valid);
        assertTrue("absent target must be classified missing", missing.missing);

        HashUtil.CheckResult rejected = results.get(3);
        assertFalse(rejected.valid);
        assertTrue("traversal must be rejected without opening", rejected.rejected);
    }

    @Test public void generatedSfvVerifiesAgainstRealFilesThroughStagingOpener() throws Exception {
        File a = write("one.bin", "123456789");
        File b = write("two.bin", "hello");
        File manifest = write("payload.sfv",
                HashUtil.generateSfv(java.util.Arrays.asList(a, b)));

        List<HashUtil.CheckResult> results = HashUtil.verifyChecksumFile(manifest,
                ChecksumDialogs.entryOpener(context, dir));

        assertEquals(2, results.size());
        for (HashUtil.CheckResult r : results) {
            assertTrue(r.fileName + " valid", r.valid);
            assertFalse(r.missing);
            assertFalse(r.rejected);
        }
    }

    @Test public void generatedMd5VerifiesAgainstRealFilesThroughStagingOpener() throws Exception {
        File a = write("doc.txt", "hello");
        File manifest = write("payload.md5",
                HashUtil.generateMd5(java.util.Collections.singletonList(a)));

        List<HashUtil.CheckResult> results = HashUtil.verifyChecksumFile(manifest,
                ChecksumDialogs.entryOpener(context, dir));

        assertEquals(1, results.size());
        assertTrue(results.get(0).valid);
        assertEquals("doc.txt", results.get(0).fileName);
    }
}

package io.github.abdurazaaqmohammed.MPManager.shizuku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

/**
 * FileSorting compares every entry by {@code length()} and {@code lastModified()}. If those hit the
 * filesystem for a {@link ShizukuFile} the app forks a stat per comparison and ANRs on large
 * directories such as Android/data, so both must serve the values cached at listing time.
 *
 * <p>Each case wraps a real on-disk file whose actual size/mtime differ from the cached ones, so a
 * regression that reintroduces the stat cannot pass by coincidence.
 */
public class ShizukuFileCacheTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void servesCachedTimestampWithoutStat() throws Exception {
        File real = folder.newFile("entry");
        ShizukuFile cached = new ShizukuFile(real.getAbsolutePath(), false, 7L, 1234567890000L);

        assertEquals(1234567890000L, cached.lastModified());
        assertTrue("cached timestamp must win over the real mtime",
                real.lastModified() != 1234567890000L);
    }

    @Test
    public void servesCachedSizeWithoutStat() throws Exception {
        File real = folder.newFile("entry");
        java.nio.file.Files.write(real.toPath(), "not empty".getBytes("UTF-8"));
        // Zero size is the case that used to fall through to super.length() and stat per comparison.
        ShizukuFile cached = new ShizukuFile(real.getAbsolutePath(), false, 0L, 0L);

        assertEquals(0L, cached.length());
        assertTrue("existing file must not be empty, otherwise the stat fallback is invisible",
                real.length() > 0L);
    }

    @Test
    public void keepsProbingWhenSizeIsUnknown() {
        // Constructed via getParentFile(): no listing happened, so length() may still stat.
        ShizukuFile unknown = new ShizukuFile("/definitely/not/here/at/all");

        assertEquals(0L, unknown.length());
        assertEquals(0L, unknown.lastModified());
    }
}
package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Roundtrip coverage for the zstd (.zst) codec: detection, browsing and
 * extraction of bare .zst payloads and of .tar.zst bundles.
 */
public class ZstdArchiveTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static void assumeNativeZstd() {
        org.junit.Assume.assumeTrue("native zstd not available on this host",
                ZstdBridge.isLoaded());
    }

    @Test
    public void singleFileZstdRoundtrip() throws Exception {
        assumeNativeZstd();
        byte[] payload = compressiblePayload();
        File src = folder.newFile("payload.bin");
        Files.write(src.toPath(), payload);

        File out = new File(folder.getRoot(), "payload.bin.zst");
        ArchiveUtil.create(out, Collections.singletonList(src));

        assertEquals(ArchiveBrowseUtil.ArchiveType.ZSTD, ArchiveBrowseUtil.detectArchiveType(out));
        assertTrue(ArchiveUtil.isSupportedArchive(out.getName()));

        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(out);
        assertEquals(1, entries.size());
        assertEquals("payload.bin", entries.get(0).path());

        File dest = folder.newFolder("dest");
        ArchiveUtil.extract(out, dest);
        assertTrue(Arrays.equals(payload,
                Files.readAllBytes(new File(dest, "payload.bin").toPath())));
    }

    @Test
    public void tarZstdRoundtrip() throws Exception {
        assumeNativeZstd();
        File dir = folder.newFolder("src");
        File a = new File(dir, "a.txt");
        File sub = new File(dir, "sub");
        assertTrue(sub.mkdirs());
        File b = new File(sub, "b.txt");
        Files.write(a.toPath(), "hello a".getBytes(StandardCharsets.UTF_8));
        Files.write(b.toPath(), "hello b".getBytes(StandardCharsets.UTF_8));

        File out = new File(folder.getRoot(), "bundle.tar.zst");
        ArchiveUtil.create(out, Arrays.asList(a, sub));

        assertEquals(ArchiveBrowseUtil.ArchiveType.ZSTD, ArchiveBrowseUtil.detectArchiveType(out));
        assertTrue(ArchiveUtil.isSupportedArchive(out.getName()));

        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(out);
        List<String> names = new java.util.ArrayList<>();
        for (ArchiveBrowseUtil.RawEntry e : entries) names.add(e.path());
        assertTrue(names.contains("a.txt"));
        assertTrue(names.contains("sub/"));
        assertTrue(names.contains("sub/b.txt"));

        File dest = folder.newFolder("out");
        ArchiveUtil.extract(out, dest);
        assertEquals("hello a", new String(
                Files.readAllBytes(new File(dest, "a.txt").toPath()), StandardCharsets.UTF_8));
        assertEquals("hello b", new String(
                Files.readAllBytes(new File(dest, "sub/b.txt").toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void entryCrcIsReportedForZstdPayload() throws Exception {
        assumeNativeZstd();
        byte[] payload = compressiblePayload();
        File src = folder.newFile("crc.bin");
        Files.write(src.toPath(), payload);

        File out = new File(folder.getRoot(), "crc.bin.zst");
        ArchiveUtil.create(out, Collections.singletonList(src));

        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(payload);
        assertEquals(crc.getValue(), ArchiveBrowseUtil.entryCrc32(out, "crc.bin"));
    }

    private static byte[] compressiblePayload() {
        byte[] data = new byte[60000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) ('a' + (i % 7));
        return data;
    }
}

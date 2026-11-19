package io.github.abdurazaaqmohammed.domain.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.abdurazaaqmohammed.util.archive.ZipCompat;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Regression coverage for searching inside legacy Chinese zip archives: the
 * search loops must go through ZipCompat's per-entry charset decode, because
 * java.util.zip opens archives by decoding every name up front and fails (or
 * garbles) on legacy GBK entries — the search must never touch it. The same
 * archives exercise name search, content search, XML search and the
 * result-to-open consistency (a hit's fullPath must open through ZipCompat).
 */
public class FileSearchTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static void assumeGbk() {
        boolean gbk;
        try {
            Charset.forName("GBK");
            gbk = true;
        } catch (Exception e) {
            gbk = false;
        }
        Assume.assumeTrue("GBK charset not available on this host", gbk);
    }

    private static Charset gbk() {
        return Charset.forName("GBK");
    }

    private static byte[] payload(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) ('a' + (i % 26));
        return data;
    }

    private static byte[] deflate(byte[] data) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static final class Spec {
        final byte[] nameBytes;
        final boolean utf8Flag;
        final byte[] compressed;
        final byte[] data;

        Spec(byte[] nameBytes, boolean utf8Flag, byte[] compressed, byte[] data) {
            this.nameBytes = nameBytes;
            this.utf8Flag = utf8Flag;
            this.compressed = compressed; // null for directory entries
            this.data = data;
        }
    }

    private static Spec dir(String name, Charset cs) throws IOException {
        return new Spec(name.getBytes(cs), false, null, null);
    }

    private static Spec file(String name, Charset cs, byte[] content) throws IOException {
        return new Spec(name.getBytes(cs), false, deflate(content), content);
    }

    /**
     * Writes an archive by hand so the stored name encoding and the UTF-8
     * flag can be controlled exactly (standard writers refuse legacy encodings).
     */
    private static void writeZip(File zip, List<Spec> specs) throws IOException {
        long[] offsets = new long[specs.size()];
        CRC32 crc = new CRC32();
        try (RandomAccessFile raf = new RandomAccessFile(zip, "rw")) {
            raf.setLength(0);
            for (int i = 0; i < specs.size(); i++) {
                Spec s = specs.get(i);
                offsets[i] = raf.getFilePointer();
                int flags = s.utf8Flag ? 0x800 : 0;
                int method = s.compressed == null ? 0 : 8;
                byte[] compressed = s.compressed == null ? new byte[0] : s.compressed;
                byte[] data = s.data == null ? new byte[0] : s.data;
                crc.reset();
                crc.update(data);
                raf.write(new byte[]{
                        0x50, 0x4B, 3, 4, 20, 0,
                        (byte) flags, (byte) (flags >> 8),
                        (byte) method, (byte) (method >> 8),
                        0, 0, 0x21, 0,
                        (byte) crc.getValue(), (byte) (crc.getValue() >>> 8),
                        (byte) (crc.getValue() >>> 16), (byte) (crc.getValue() >>> 24),
                        (byte) compressed.length, (byte) (compressed.length >>> 8),
                        (byte) (compressed.length >>> 16), (byte) (compressed.length >>> 24),
                        (byte) data.length, (byte) (data.length >>> 8),
                        (byte) (data.length >>> 16), (byte) (data.length >>> 24),
                        (byte) s.nameBytes.length, (byte) (s.nameBytes.length >>> 8),
                        0, 0});
                raf.write(s.nameBytes);
                if (method != 0) raf.write(compressed);
                else if (s.data != null && s.data.length > 0) raf.write(s.data);
            }
            long centralOffset = raf.getFilePointer();
            for (int i = 0; i < specs.size(); i++) {
                Spec s = specs.get(i);
                int flags = s.utf8Flag ? 0x800 : 0;
                int method = s.compressed == null ? 0 : 8;
                byte[] compressed = s.compressed == null ? new byte[0] : s.compressed;
                byte[] data = s.data == null ? new byte[0] : s.data;
                crc.reset();
                crc.update(data);
                raf.write(new byte[]{
                        0x50, 0x4B, 1, 2, 20, 0, 20, 0,
                        (byte) flags, (byte) (flags >> 8),
                        (byte) method, (byte) (method >> 8),
                        0, 0, 0x21, 0,
                        (byte) crc.getValue(), (byte) (crc.getValue() >>> 8),
                        (byte) (crc.getValue() >>> 16), (byte) (crc.getValue() >>> 24),
                        (byte) compressed.length, (byte) (compressed.length >>> 8),
                        (byte) (compressed.length >>> 16), (byte) (compressed.length >>> 24),
                        (byte) data.length, (byte) (data.length >>> 8),
                        (byte) (data.length >>> 16), (byte) (data.length >>> 24),
                        (byte) s.nameBytes.length, (byte) (s.nameBytes.length >>> 8),
                        0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, (byte) 0x81,
                        (byte) offsets[i], (byte) (offsets[i] >>> 8),
                        (byte) (offsets[i] >>> 16), (byte) (offsets[i] >>> 24)});
                raf.write(s.nameBytes);
            }
            long centralSize = raf.getFilePointer() - centralOffset;
            raf.write(new byte[]{
                    0x50, 0x4B, 5, 6, 0, 0, 0, 0,
                    (byte) specs.size(), (byte) (specs.size() >>> 8),
                    (byte) specs.size(), (byte) (specs.size() >>> 8),
                    (byte) centralSize, (byte) (centralSize >>> 8),
                    (byte) (centralSize >>> 16), (byte) (centralSize >>> 24),
                    (byte) centralOffset, (byte) (centralOffset >>> 8),
                    (byte) (centralOffset >>> 16), (byte) (centralOffset >>> 24),
                    0, 0});
        }
        if (zip.length() == 0) throw new IOException("empty zip written");
    }

    private static String names(List<ZipEntryInfo> results) {
        StringBuilder sb = new StringBuilder();
        for (ZipEntryInfo r : results) sb.append('[').append(r.getFullPath()).append("] ");
        return sb.toString();
    }

    /** Name search inside a legacy GBK archive: hits carry openable full paths. */
    @Test
    public void searchZipEntriesFindsLegacyGbkNames() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-names.zip");
        writeZip(zip, Arrays.asList(
                dir("中文文件夹/", gbk()),
                file("中文文件夹/测试文件.txt", gbk(), payload(64)),
                file("另一个文件.log", gbk(), payload(64))));

        List<ZipEntryInfo> results = FileSearch.searchZipEntries(zip, "", "测试", true, false, false,
                null, -1, -1, null);
        assertEquals("expected one name hit, got: " + names(results), 1, results.size());
        ZipEntryInfo hit = results.get(0);
        assertEquals("中文文件夹/测试文件.txt", hit.getFullPath());
        assertEquals("测试文件.txt", hit.getName());
        assertFalse(hit.isDirectory());
        // Timestamp must be a plausible epoch (the writer stores DOS
        // 1980-01-01), not a raw DOS packing or a double-converted value.
        assertTrue("implausible timestamp: " + hit.getLastModified(),
                hit.getLastModified() >= 0 && hit.getLastModified() < 4102444800000L);

        // The invariant that makes search results usable: the reported fullPath
        // must open through ZipCompat — java.util.zip would fail to even list.
        try (java.io.InputStream in = ZipCompat.openEntry(zip, hit.getFullPath())) {
            assertTrue(in.read() >= 0);
        }
    }

    /** Content search (textInside) matches bytes inside a GBK-named entry. */
    @Test
    public void searchZipEntriesTextInsideReadsGbkNamedEntry() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-text.zip");
        byte[] content = "hello 中文 world\nsecond line\n".getBytes(StandardCharsets.UTF_8);
        writeZip(zip, Arrays.asList(
                file("笔记.txt", gbk(), content),
                file("无命中.txt", gbk(), "nothing here\n".getBytes(StandardCharsets.UTF_8))));

        List<ZipEntryInfo> results = FileSearch.searchZipEntries(zip, "", "", false, false, false,
                "hello 中文", -1, -1, null);
        assertEquals("expected only the matching entry, got: " + names(results), 1, results.size());
        assertEquals("笔记.txt", results.get(0).getFullPath());
    }

    /** Size filtering works with the per-entry decode in place. */
    @Test
    public void searchZipEntriesFiltersBySize() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-size.zip");
        writeZip(zip, Arrays.asList(
                file("大文件.bin", gbk(), payload(5000)),
                file("小文件.bin", gbk(), payload(10))));

        List<ZipEntryInfo> results = FileSearch.searchZipEntries(zip, "", "", false, false, false,
                null, 1000, -1, null);
        assertEquals(1, results.size());
        assertEquals("大文件.bin", results.get(0).getFullPath());
        assertEquals(5000, results.get(0).getSize());
    }

    /** Content search via findInArchiveFiles: hit records the decoded name. */
    @Test
    public void findInArchiveFilesMatchesInsideGbkNamedEntry() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-content.zip");
        writeZip(zip, Arrays.asList(
                file("说明文档.txt", gbk(), "第一行\nneedle here\n".getBytes(StandardCharsets.UTF_8)),
                file("图片占位.png", gbk(), new byte[]{1, 2, 3, 4})));

        List<ContentHit> hits = new java.util.ArrayList<>();
        FileSearch.findInArchiveFiles(zip, "needle", false, false, false, null,
                hits, new int[1], null);
        assertEquals(1, hits.size());
        assertEquals("说明文档.txt", hits.get(0).displayPath());
        assertTrue(hits.get(0).snippet().contains("needle"));
    }

    /** XML content search via searchInArchiveXml: plain XML and hit naming. */
    @Test
    public void searchInArchiveXmlMatchesInsideGbkNamedXml() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-xml.zip");
        byte[] xml = "<?xml version=\"1.0\"?><resources><name>中文标签</name></resources>\n"
                .getBytes(StandardCharsets.UTF_8);
        writeZip(zip, Arrays.asList(
                file("布局配置.xml", gbk(), xml),
                file("普通文本.txt", gbk(), "中文标签\n".getBytes(StandardCharsets.UTF_8))));

        List<ContentHit> hits = new java.util.ArrayList<>();
        FileSearch.searchInArchiveXml(zip, "中文标签", false, false, false, null,
                hits, new int[1], null);
        assertEquals(1, hits.size());
        assertEquals("布局配置.xml", hits.get(0).displayPath());
        assertTrue(hits.get(0).snippet().contains("中文标签"));
    }

    /** Sub-path scoping: only entries under the prefix are searched. */
    @Test
    public void searchZipEntriesRespectsSubPathPrefix() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-prefix.zip");
        writeZip(zip, Arrays.asList(
                dir("中文文件夹/", gbk()),
                file("中文文件夹/测试文件.txt", gbk(), payload(64)),
                file("测试文件.txt", gbk(), payload(64))));

        List<ZipEntryInfo> results = FileSearch.searchZipEntries(zip, "中文文件夹", "测试", false, false, false,
                null, -1, -1, null);
        assertEquals("prefix scoping should exclude the root-level twin, got: " + names(results),
                1, results.size());
        assertEquals("中文文件夹/测试文件.txt", results.get(0).getFullPath());
    }

    /** A non-archive file yields no results and no exception. */
    @Test
    public void searchZipEntriesToleratesNonArchive() throws Exception {
        File notZip = folder.newFile("plain.bin");
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(notZip)) {
            out.write(payload(64));
        }
        List<ZipEntryInfo> results = FileSearch.searchZipEntries(notZip, "", "x", false, false, false,
                null, -1, -1, null);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    /** Pattern-based name search (wildcards) still routes through the decode. */
    @Test
    public void searchZipEntriesWildcardPatternMatchesGbkName() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-wildcard.zip");
        writeZip(zip, Arrays.asList(
                file("测试文件.txt", gbk(), payload(64)),
                file("别的.log", gbk(), payload(64))));

        List<ZipEntryInfo> results = FileSearch.searchZipEntries(zip, "", "*.txt", false, false, false,
                null, -1, -1, null);
        assertEquals("expected the .txt entry only, got: " + names(results), 1, results.size());
        assertEquals("测试文件.txt", results.get(0).getFullPath());

        Pattern pattern = FileSearch.compilePattern("*.txt", false, false);
        assertNotNull(pattern);
    }

    /** Guard: content search inside a GBK archive must not touch java.util.zip. */
    @Test
    public void gbkArchiveContentSearchWorksEndToEnd() throws Exception {
        assumeGbk();
        File zip = folder.newFile("gbk-open.zip");
        writeZip(zip, Arrays.asList(file("中文文件夹/测试文件.txt", gbk(), "abc\n".getBytes(StandardCharsets.UTF_8))));
        // If anything in the search path still touched java.util.zip for
        // listing or reading, this would throw or garble before matching.
        List<ZipEntryInfo> results = FileSearch.searchZipEntries(zip, "", "测试", true, false, false,
                "abc", -1, -1, null);
        assertEquals(1, results.size());
        try (java.io.InputStream in = ZipCompat.openEntry(zip, results.get(0).getFullPath())) {
            assertEquals('a', in.read());
        }
    }
}

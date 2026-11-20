package io.github.abdurazaaqmohammed.util.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import net.lingala.zip4j.model.FileHeader;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Regression coverage for legacy Chinese zip filenames: archives written by
 * Chinese Windows tools store GBK names without the UTF-8 flag, and both zip
 * backends used to read them as mojibake — zip4j with its UTF-8 default, the
 * direct central-directory fallback with CP437. Every test here builds the
 * raw archive bytes so the stored name encoding and flag can be controlled
 * exactly, then checks browse, open, extract-on-disk and mutation-lookup
 * consistency.
 */
public class ZipCharsetTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** Resolved lazily so JVMs without GBK skip the tests instead of erroring in static init. */
    private static Charset gbk() {
        return Charset.forName("GBK");
    }

    private static void assumeGbk() {
        Assume.assumeTrue("GBK charset not available on this host",
                ZipCharset.isGbkSupported());
    }

    /** Legacy Chinese zip: GBK names, no UTF-8 flag, deflate. */
    @Test
    public void legacyGbkZipListsOpensAndExtractsCorrectNames() throws Exception {
        assumeGbk();
        byte[] payload = payload(20000);
        File zip = folder.newFile("gbk.zip");
        writeZip(zip, Arrays.asList(
                spec("中文文件夹/".getBytes(gbk()), false, null, null),
                spec("中文文件夹/测试文件.txt".getBytes(gbk()), false, deflate(payload), payload)));

        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(zip);
        assertEquals(2, entries.size());
        assertEquals("中文文件夹/", entries.get(0).path());
        assertEquals("中文文件夹/测试文件.txt", entries.get(1).path());

        assertEquals(ZipCharset.Plan.GBK, ZipCharset.planFor(zip));

        // Open by the decoded name — the invariant extract-per-entry and the
        // mutation paths rely on.
        ByteArrayOutputStream opened = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try (java.io.InputStream in = ZipCompat.openEntry(zip, "中文文件夹/测试文件.txt")) {
            while ((n = in.read(buf)) != -1) opened.write(buf, 0, n);
        }
        assertTrue(Arrays.equals(payload, opened.toByteArray()));

        // Full extraction must land on disk with readable names, not mojibake.
        File dest = folder.newFolder("gbk-dest");
        ArchiveUtil.extract(zip, dest);
        File extracted = new File(dest, "中文文件夹/测试文件.txt");
        assertTrue("extracted Chinese name missing on disk: " + Arrays.toString(dest.list()),
                extracted.isFile());
        assertTrue(Arrays.equals(payload, Files.readAllBytes(extracted.toPath())));
    }

    /** The same archive through the plain ZipCompat extractAll fallback. */
    @Test
    public void gbkExtractAllFallbackWritesCorrectNames() throws Exception {
        assumeGbk();
        byte[] payload = payload(20000);
        File zip = folder.newFile("gbk-fallback.zip");
        writeZip(zip, Arrays.asList(
                spec("文件夹/说明.txt".getBytes(gbk()), false, deflate(payload), payload)));

        File dest = folder.newFolder("gbk-fallback-dest");
        ZipCompat.extractAll(zip, dest, false);
        File out = new File(dest, "文件夹/说明.txt");
        assertTrue(out.isFile());
        assertTrue(Arrays.equals(payload, Files.readAllBytes(out.toPath())));
    }

    /** UTF-8 flagged names must keep decoding exactly, unicode preserved. */
    @Test
    public void utf8FlaggedNamesArePreserved() throws Exception {
        assumeGbk();
        byte[] payload = payload(5000);
        String unicodeName = "中文数据/界面标签.properties";
        File zip = folder.newFile("utf8flag.zip");
        writeZip(zip, Arrays.asList(
                spec(unicodeName.getBytes(StandardCharsets.UTF_8), true, deflate(payload), payload)));

        assertEquals(ZipCharset.Plan.UTF8, ZipCharset.planFor(zip));
        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(zip);
        assertEquals(1, entries.size());
        assertEquals(unicodeName, entries.get(0).path());

        File dest = folder.newFolder("utf8flag-dest");
        ArchiveUtil.extract(zip, dest);
        assertTrue(Arrays.equals(payload,
                Files.readAllBytes(new File(dest, unicodeName).toPath())));
    }

    /**
     * Mixed archive: legacy GBK name beside a UTF-8-flagged unicode name.
     * zip4j's single archive-wide charset cannot decode both, so listing goes
     * through the per-entry parser and extraction (without a password) uses
     * the ZipCompat fallback; every entry must come out with its own name.
     */
    @Test
    public void mixedArchiveDecodesEachEntryWithItsOwnCharset() throws Exception {
        assumeGbk();
        byte[] payload = payload(8000);
        File zip = folder.newFile("mixed.zip");
        writeZip(zip, Arrays.asList(
                spec("旧版文件.txt".getBytes(gbk()), false, deflate(payload), payload),
                spec("Unicode名称.json".getBytes(StandardCharsets.UTF_8), true, deflate(payload), payload)));

        assertEquals(ZipCharset.Plan.MIXED, ZipCharset.planFor(zip));
        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(zip);
        List<String> names = new ArrayList<>();
        for (ArchiveBrowseUtil.RawEntry e : entries) names.add(e.path());
        assertTrue(names.contains("旧版文件.txt"));
        assertTrue(names.contains("Unicode名称.json"));

        File dest = folder.newFolder("mixed-dest");
        ArchiveUtil.extract(zip, dest);
        assertTrue(new File(dest, "旧版文件.txt").isFile());
        assertTrue(new File(dest, "Unicode名称.json").isFile());
        assertTrue(Arrays.equals(payload, Files.readAllBytes(new File(dest, "旧版文件.txt").toPath())));
        assertTrue(Arrays.equals(payload, Files.readAllBytes(new File(dest, "Unicode名称.json").toPath())));
    }

    /** Unflagged UTF-8 accents and emoji must not be forced through GBK. */
    @Test
    public void unflaggedUtf8AccentsAndEmojiDecodeCorrectly() throws Exception {
        byte[] payload = payload(2000);
        String name = "café ☕/naïve résumé.txt";
        File zip = folder.newFile("utf8-unflagged.zip");
        writeZip(zip, Arrays.asList(
                spec(name.getBytes(StandardCharsets.UTF_8), false, deflate(payload), payload)));

        assertEquals(StandardCharsets.UTF_8,
                ZipCharset.charsetFor(name.getBytes(StandardCharsets.UTF_8), false));
        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(zip);
        assertEquals(1, entries.size());
        assertEquals(name, entries.get(0).path());

        File dest = folder.newFolder("utf8-unflagged-dest");
        ZipCompat.extractAll(zip, dest, false);
        assertTrue(Arrays.equals(payload,
                Files.readAllBytes(new File(dest, name).toPath())));
    }

    /** Western CP437 legacy names keep their single-byte reading. */
    @Test
    public void westernLegacyNamesAreNotForcedToGbk() throws Exception {
        byte[] payload = payload(2000);
        // CP437: 0x82 = é. In "café.txt" the accent byte is followed by '.',
        // and in "résumé.txt" by 's' — the latter DOES form a coincidentally
        // valid GBK pair (0x82 0x73), but its trail never reaches the Hanzi
        // area (>= 0xA1), so neither name is forced through GBK.
        byte[] cafe = {(byte) 'c', (byte) 'a', (byte) 'f', (byte) 0x82, (byte) '.', (byte) 't', (byte) 'x', (byte) 't'};
        byte[] resume = {(byte) 'r', (byte) 0x82, (byte) 's', (byte) 'u', (byte) 'm', (byte) 0x82,
                (byte) '.', (byte) 't', (byte) 'x', (byte) 't'};
        File zip = folder.newFile("cp437.zip");
        writeZip(zip, Arrays.asList(
                spec(cafe, false, deflate(payload), payload),
                spec(resume, false, deflate(payload), payload)));

        assertEquals(ZipCharset.Plan.LATIN1, ZipCharset.planFor(zip));
        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(zip);
        assertEquals("café.txt", entries.get(0).path());
        assertEquals("résumé.txt", entries.get(1).path());

        File dest = folder.newFolder("cp437-dest");
        ZipCompat.extractAll(zip, dest, false);
        assertTrue(Arrays.equals(payload, Files.readAllBytes(new File(dest, "café.txt").toPath())));
        assertTrue(Arrays.equals(payload, Files.readAllBytes(new File(dest, "résumé.txt").toPath())));
    }

    /**
     * The mutation invariant: the charset-applied zip4j handle used by
     * add/delete operations decodes exactly the names the browser showed.
     */
    @Test
    public void charsetAppliedZip4jHandleMatchesListedNames() throws Exception {
        assumeGbk();
        byte[] payload = payload(3000);
        File zip = folder.newFile("gbk-mutation.zip");
        writeZip(zip, Arrays.asList(
                spec("中文文件夹/".getBytes(gbk()), false, null, null),
                spec("中文文件夹/删除我.txt".getBytes(gbk()), false, deflate(payload), payload),
                spec("另一个文件.txt".getBytes(gbk()), false, deflate(payload), payload)));

        List<String> listed = new ArrayList<>();
        for (FileHeader fh : ZipCompat.listHeaders(zip)) listed.add(fh.getFileName());
        List<String> zip4jSeen = new ArrayList<>();
        try (net.lingala.zip4j.ZipFile zf = ZipCompat.openZip(zip)) {
            for (FileHeader fh : zf.getFileHeaders()) zip4jSeen.add(fh.getFileName());
        }
        assertEquals(listed, zip4jSeen);
        assertTrue(zip4jSeen.contains("中文文件夹/删除我.txt"));

        // The name lookup delete/add rely on must resolve through zip4j too.
        try (net.lingala.zip4j.ZipFile zf = ZipCompat.openZip(zip)) {
            assertEquals("中文文件夹/删除我.txt", zf.getFileHeader("中文文件夹/删除我.txt").getFileName());
        }
    }

    /**
     * Info-ZIP Unicode Path extra field (0x7075): when its CRC-32 validates
     * against the stored legacy name, the UTF-8 name it carries wins; when it
     * does not, the stored name stays authoritative.
     */
    @Test
    public void unicodePathExtraFieldIsHonouredOnlyWhenCrcMatches() throws Exception {
        byte[] payload = payload(2000);
        byte[] legacyName = "cafe.txt".getBytes(StandardCharsets.US_ASCII);
        byte[] unicodeName = "真實名稱.txt".getBytes(StandardCharsets.UTF_8);
        byte[] goodExtra = unicodePathExtra(legacyName, unicodeName);

        File zip = folder.newFile("upath.zip");
        writeZip(zip, Arrays.asList(spec(legacyName, false, deflate(payload), payload, goodExtra)));
        List<ArchiveBrowseUtil.RawEntry> entries = ArchiveBrowseUtil.listEntries(zip);
        assertEquals("真實名稱.txt", entries.get(0).path());
        assertEquals(ZipCharset.Plan.MIXED, ZipCharset.planFor(zip));

        // Corrupt field: the CRC no longer matches the stored legacy name
        // ("café.txt" stored CP437-style), so the field is ignored and the
        // stored name stands — read through the historical single-byte
        // charset, which is exactly what the heuristic falls back to here.
        byte[] storedLegacy = {(byte) 'c', (byte) 'a', (byte) 'f', (byte) 0x82, (byte) '.', (byte) 't', (byte) 'x', (byte) 't'};
        byte[] corruptExtra = unicodePathExtra("cafe.txt".getBytes(StandardCharsets.US_ASCII),
                "另外名称.txt".getBytes(StandardCharsets.UTF_8));
        File zip2 = folder.newFile("upath-bad.zip");
        writeZip(zip2, Arrays.asList(spec(storedLegacy, false, deflate(payload), payload, corruptExtra)));
        assertEquals("café.txt", ArchiveBrowseUtil.listEntries(zip2).get(0).path());
    }

    /** Pure ASCII archives stay on the untouched zip4j path. */
    @Test
    public void asciiArchiveKeepsDefaultPlan() throws Exception {
        byte[] payload = payload(1000);
        File zip = folder.newFile("ascii.zip");
        writeZip(zip, Arrays.asList(
                spec("folder/".getBytes(StandardCharsets.US_ASCII), false, null, null),
                spec("folder/file.txt".getBytes(StandardCharsets.US_ASCII), false, deflate(payload), payload)));
        assertEquals(ZipCharset.Plan.DEFAULT, ZipCharset.planFor(zip));
        assertEquals("folder/file.txt", ArchiveBrowseUtil.listEntries(zip).get(1).path());
    }

    /**
     * Unflagged-but-valid-UTF-8 archives must take the UTF-8 plan: zip4j's
     * unflagged fallback is CP437, which would turn "café" into "caf├⌐".
     */
    @Test
    public void unflaggedUtf8OnlyArchiveUsesUtf8Plan() throws Exception {
        byte[] payload = payload(1000);
        String name = "émoji ✅ café.txt";
        File zip = folder.newFile("utf8-only.zip");
        writeZip(zip, Arrays.asList(
                spec(name.getBytes(StandardCharsets.UTF_8), false, deflate(payload), payload)));
        assertEquals(ZipCharset.Plan.UTF8, ZipCharset.planFor(zip));
        assertEquals(name, ArchiveBrowseUtil.listEntries(zip).get(0).path());
    }

    /**
     * The mechanism behind encrypted mixed-charset extraction: zip4j's header
     * names, corrected from the per-entry decode via local-header offsets,
     * match exactly what browsing shows — so extractAll lands the right
     * filenames on disk even though zip4j itself cannot decode every entry.
     */
    @Test
    public void correctedZip4jHeadersMatchPerEntryDecode() throws Exception {
        assumeGbk();
        byte[] payload = payload(3000);
        File zip = folder.newFile("mixed-corrected.zip");
        writeZip(zip, Arrays.asList(
                spec("旧版文件.txt".getBytes(gbk()), false, deflate(payload), payload),
                spec("Unicode名称.json".getBytes(StandardCharsets.UTF_8), true, deflate(payload), payload)));

        try (net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(zip)) {
            // Before correction, zip4j's single archive-wide decode cannot
            // spell the legacy GBK name correctly.
            List<String> before = new ArrayList<>();
            for (FileHeader fh : zf.getFileHeaders()) before.add(fh.getFileName());
            assertTrue("zip4j default decode should garble the legacy name here",
                    before.contains("旧版文件.txt") == false || before.contains("Unicode名称.json") == false);

            ZipCompat.correctZip4jHeaderNames(zf, zip);
            List<String> after = new ArrayList<>();
            for (FileHeader fh : zf.getFileHeaders()) after.add(fh.getFileName());
            assertTrue(after.contains("旧版文件.txt"));
            assertTrue(after.contains("Unicode名称.json"));
        }
    }

    /**
     * Full mutation round trip on a mixed archive, the path
     * deleteZipEntry/add-to-zip take: the charset-corrected zip4j handle
     * resolves the browsed legacy name, removes it, and the surviving entries
     * still decode correctly afterwards — unicode name preserved, no mojibake
     * written into the archive.
     */
    @Test
    public void mixedArchiveMutationRemovesLegacyEntryAndKeepsUnicode() throws Exception {
        assumeGbk();
        byte[] payload = payload(3000);
        File zip = folder.newFile("mixed-mutation.zip");
        writeZip(zip, Arrays.asList(
                spec("旧版文件夹/".getBytes(gbk()), false, null, null),
                spec("旧版文件夹/旧版文件.txt".getBytes(gbk()), false, deflate(payload), payload),
                spec("Unicode名称.json".getBytes(StandardCharsets.UTF_8), true, deflate(payload), payload)));

        // Exactly what deleteZipEntry does: open with charset + correction,
        // look the entry up by its browsed name, remove it.
        try (net.lingala.zip4j.ZipFile zf = ZipCompat.openZip(zip)) {
            assertNotNull(zf.getFileHeader("旧版文件夹/旧版文件.txt"));
            zf.removeFiles(Collections.singletonList("旧版文件夹/旧版文件.txt"));
        }

        List<String> names = new ArrayList<>();
        for (ArchiveBrowseUtil.RawEntry e : ArchiveBrowseUtil.listEntries(zip)) names.add(e.path());
        assertTrue("directory stub should survive: " + names, names.contains("旧版文件夹/"));
        assertFalse("removed entry must be gone: " + names, names.contains("旧版文件夹/旧版文件.txt"));
        assertTrue("unicode entry must survive with its name intact: " + names,
                names.contains("Unicode名称.json"));

        // The archive must still open cleanly by name after the rewrite.
        try (java.io.InputStream in = ZipCompat.openEntry(zip, "Unicode名称.json")) {
            int first = in.read();
            assertTrue(first >= 0);
        }
    }

    /** GBK names that are not valid UTF-8 are classified as GBK, not unicode. */
    @Test
    public void gbkChineseNameIsNotMistakenForUtf8() throws Exception {
        assumeGbk();
        // GBK "中文.txt" contains bytes no valid UTF-8 sequence accepts.
        byte[] nameBytes = "中文.txt".getBytes(gbk());
        ZipCompat.Entry probe = entryWith(nameBytes);
        assertEquals(ZipCharset.Plan.GBK, ZipCharset.planFor(Collections.singletonList(probe)));
        assertFalseLikeMojibake(nameBytes);
    }

    private void assertFalseLikeMojibake(byte[] nameBytes) {
        String decoded = ZipCharset.decodeName(nameBytes, false);
        for (int i = 0; i < decoded.length(); i++) {
            if (decoded.charAt(i) == '\uFFFD') {
                fail("GBK name decoded with replacement characters: " + decoded);
            }
        }
        assertEquals("中文.txt", decoded);
    }

    private ZipCompat.Entry entryWith(byte[] nameBytes) throws IOException {
        // Built through a real archive so the parser assigns the metadata.
        File zip = folder.newFile("nameprobe.zip");
        writeZip(zip, Arrays.asList(spec(nameBytes, false, deflate(payload(64)), payload(64))));
        return ZipCompat.readCentralDirectory(zip).get(0);
    }

    private static byte[] payload(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) ('a' + (i % 26));
        return data;
    }

    /** Raw deflate (no zlib wrapper), the way zip stores it. */
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

    /** Builds the 0x7075 extra field: CRC-32 of the legacy name, then the UTF-8 name. */
    private static byte[] unicodePathExtra(byte[] legacyNameBytes, byte[] unicodeNameBytes) {
        CRC32 crc = new CRC32();
        crc.update(legacyNameBytes);
        byte[] extra = new byte[4 + 4 + unicodeNameBytes.length];
        extra[0] = (byte) 0x75;
        extra[1] = (byte) 0x70;
        extra[2] = (byte) ((4 + unicodeNameBytes.length) & 0xFF);
        extra[3] = (byte) (((4 + unicodeNameBytes.length) >> 8) & 0xFF);
        long value = crc.getValue();
        extra[4] = (byte) value;
        extra[5] = (byte) (value >>> 8);
        extra[6] = (byte) (value >>> 16);
        extra[7] = (byte) (value >>> 24);
        System.arraycopy(unicodeNameBytes, 0, extra, 8, unicodeNameBytes.length);
        return extra;
    }

    private static Spec spec(byte[] nameBytes, boolean utf8Flag, byte[] compressed, byte[] data) {
        return new Spec(nameBytes, utf8Flag, compressed, data, null);
    }

    private static Spec spec(byte[] nameBytes, boolean utf8Flag, byte[] compressed, byte[] data, byte[] extra) {
        return new Spec(nameBytes, utf8Flag, compressed, data, extra);
    }

    private static final class Spec {
        final byte[] nameBytes;
        final boolean utf8Flag;
        final byte[] compressed;
        final byte[] data;
        final byte[] extra;

        Spec(byte[] nameBytes, boolean utf8Flag, byte[] compressed, byte[] data, byte[] extra) {
            this.nameBytes = nameBytes;
            this.utf8Flag = utf8Flag;
            this.compressed = compressed; // null for directory entries
            this.data = data;
            this.extra = extra;
        }
    }

    /**
     * Writes an archive by hand so tests control the exact name bytes, the
     * UTF-8 flag and the extra fields — none of which the standard writers
     * allow. Entries are always stored in the order given.
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
                        0x50, 0x4B, 3, 4,          // local header signature
                        20, 0,                      // version needed
                        (byte) flags, (byte) (flags >> 8),
                        (byte) method, (byte) (method >> 8),
                        0, 0, 0x21, 0,              // dos time/date
                        (byte) crc.getValue(), (byte) (crc.getValue() >>> 8),
                        (byte) (crc.getValue() >>> 16), (byte) (crc.getValue() >>> 24),
                        (byte) compressed.length, (byte) (compressed.length >>> 8),
                        (byte) (compressed.length >>> 16), (byte) (compressed.length >>> 24),
                        (byte) data.length, (byte) (data.length >>> 8),
                        (byte) (data.length >>> 16), (byte) (data.length >>> 24),
                        (byte) s.nameBytes.length, (byte) (s.nameBytes.length >>> 8),
                        0, 0});                 // local extra length (none)
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
                int extraLen = s.extra == null ? 0 : s.extra.length;

                boolean isDir = s.nameBytes.length > 0 && s.nameBytes[s.nameBytes.length - 1] == '/';
                byte b0 = isDir ? (byte) 0x10 : 0;
                byte b2 = isDir ? (byte) 0xed : (byte) 0xa4;
                byte b3 = isDir ? (byte) 0x41 : (byte) 0x81;
                raf.write(new byte[]{
                        0x50, 0x4B, 1, 2,           // central signature
                        20, 0, 20, 0,               // versions
                        (byte) flags, (byte) (flags >> 8),
                        (byte) method, (byte) (method >> 8),
                        0, 0, 0x21, 0,              // dos time/date
                        (byte) crc.getValue(), (byte) (crc.getValue() >>> 8),
                        (byte) (crc.getValue() >>> 16), (byte) (crc.getValue() >>> 24),
                        (byte) compressed.length, (byte) (compressed.length >>> 8),
                        (byte) (compressed.length >>> 16), (byte) (compressed.length >>> 24),
                        (byte) data.length, (byte) (data.length >>> 8),
                        (byte) (data.length >>> 16), (byte) (data.length >>> 24),
                        (byte) s.nameBytes.length, (byte) (s.nameBytes.length >>> 8),
                        (byte) extraLen, (byte) (extraLen >>> 8),
                        0, 0,                       // comment length
                        0, 0,                       // disk number
                        0, 0,                       // internal attrs
                        b0, 0, b2, b3,              // external attrs
                        (byte) offsets[i], (byte) (offsets[i] >>> 8),
                        (byte) (offsets[i] >>> 16), (byte) (offsets[i] >>> 24)});
                raf.write(s.nameBytes);
                if (extraLen > 0) raf.write(s.extra);
            }
            long centralSize = raf.getFilePointer() - centralOffset;

            raf.write(new byte[]{
                    0x50, 0x4B, 5, 6,               // EOCD signature
                    0, 0, 0, 0,                     // disk numbers
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
}

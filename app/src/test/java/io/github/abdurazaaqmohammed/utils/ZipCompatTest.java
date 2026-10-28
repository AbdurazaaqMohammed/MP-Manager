package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import net.lingala.zip4j.model.FileHeader;

import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Regression coverage for archives whose compression method zip4j cannot
 * decode. zip4j aborted the whole central directory with
 * "Unknown compression method" for these, so browsing a release zip such as an
 * LSPosed module crashed the app.
 */
public class ZipCompatTest {

    private static final int METHOD_XZ = 95;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void listsAndExtractsXzEntries() throws Exception {
        byte[] payload = payload(40000);
        File zip = folder.newFile("xz.zip");
        writeSingleEntryZip(zip, "module/framework.dex", payload, METHOD_XZ, xzCompress(payload));

        List<FileHeader> headers = ZipCompat.listHeaders(zip);
        assertEquals(1, headers.size());
        assertEquals("module/framework.dex", headers.get(0).getFileName());
        assertEquals(payload.length, headers.get(0).getUncompressedSize());

        File out = folder.newFile("extracted.bin");
        ZipCompat.extractEntry(zip, "module/framework.dex", out);
        assertTrue(Arrays.equals(payload, Files.readAllBytes(out.toPath())));
    }

    @Test
    public void computesCrcOfXzEntry() throws Exception {
        byte[] payload = payload(9000);
        File zip = folder.newFile("xz-crc.zip");
        writeSingleEntryZip(zip, "data.bin", payload, METHOD_XZ, xzCompress(payload));

        CRC32 crc = new CRC32();
        crc.update(payload);
        assertEquals(crc.getValue(), ZipCompat.crc32(zip, "data.bin"));
    }

    @Test
    public void extractAllRejectsEntriesEscapingDestination() throws Exception {
        byte[] payload = payload(64);
        File zip = folder.newFile("evil.zip");
        writeSingleEntryZip(zip, "../escaped.txt", payload, METHOD_XZ, xzCompress(payload));

        File dest = folder.newFolder("dest");
        try {
            ZipCompat.extractAll(zip, dest, false);
            fail("Expected traversal to be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("outside destination"));
        }
    }

    @Test
    public void ordinaryDeflateZipStillWorks() throws Exception {
        byte[] payload = payload(30000);
        File zip = folder.newFile("plain.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("notes/readme.txt"));
            out.write(payload);
            out.closeEntry();
        }

        List<FileHeader> headers = ZipCompat.listHeaders(zip);
        assertEquals(1, headers.size());
        assertEquals("notes/readme.txt", headers.get(0).getFileName());

        File out = folder.newFile("plain-out.txt");
        ZipCompat.extractEntry(zip, "notes/readme.txt", out);
        assertTrue(Arrays.equals(payload, Files.readAllBytes(out.toPath())));
    }

    @Test
    public void missingEntryReportsClearError() throws Exception {
        byte[] payload = payload(128);
        File zip = folder.newFile("missing.zip");
        writeSingleEntryZip(zip, "present.bin", payload, METHOD_XZ, xzCompress(payload));
        try {
            ZipCompat.openEntry(zip, "absent.bin").close();
            fail("Expected an IOException");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    private static byte[] payload(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) data[i] = (byte) ('a' + (i % 26));
        return data;
    }

    private static byte[] xzCompress(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (XZCompressorOutputStream xz = new XZCompressorOutputStream(bos)) {
            xz.write(data);
        }
        return bos.toByteArray();
    }

    /**
     * Writes a one-entry archive by hand so the central directory can carry an
     * arbitrary compression method code, which the standard writers refuse.
     */
    private static void writeSingleEntryZip(File zip, String name, byte[] content, int method, byte[] compressed)
            throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        int flags = 0x800;
        byte[] local = new byte[30];
        u4(local, 0, 0x04034b50L);
        u2(local, 4, 20);
        u2(local, 6, flags);
        u2(local, 8, method);
        u2(local, 10, 0);
        u2(local, 12, 0x2100);
        u4(local, 14, crcOf(content));
        u4(local, 18, compressed.length);
        u4(local, 22, content.length);
        u2(local, 26, nameBytes.length);
        u2(local, 28, 0);

        byte[] central = new byte[46];
        u4(central, 0, 0x02014b50L);
        u2(central, 4, 20);
        u2(central, 6, 20);
        u2(central, 8, flags);
        u2(central, 10, method);
        u2(central, 12, 0);
        u2(central, 14, 0x2100);
        u4(central, 16, crcOf(content));
        u4(central, 20, compressed.length);
        u4(central, 24, content.length);
        u2(central, 28, nameBytes.length);
        u2(central, 30, 0);
        u2(central, 32, 0);
        u2(central, 34, 0);
        u2(central, 36, 0);
        u4(central, 38, 0x81a40000L);
        u4(central, 42, 0);

        try (RandomAccessFile raf = new RandomAccessFile(zip, "rw")) {
            raf.setLength(0);
            raf.write(local);
            raf.write(nameBytes);
            raf.write(compressed);
            long centralOffset = raf.getFilePointer();
            raf.write(central);
            raf.write(nameBytes);
            byte[] eocd = new byte[22];
            u4(eocd, 0, 0x06054b50L);
            u2(eocd, 4, 0);
            u2(eocd, 6, 0);
            u2(eocd, 8, 1);
            u2(eocd, 10, 1);
            u4(eocd, 12, central.length + nameBytes.length);
            u4(eocd, 16, (int) centralOffset);
            u2(eocd, 20, 0);
            raf.write(eocd);
        }
    }

    private static long crcOf(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    private static void u2(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
    }

    private static void u4(byte[] b, int off, long v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
    }
}
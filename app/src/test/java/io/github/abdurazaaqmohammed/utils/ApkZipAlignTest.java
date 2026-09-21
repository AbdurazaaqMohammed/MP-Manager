package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ApkZipAlignTest {

    @Test
    public void mustStoreRules() {
        assertTrue(ApkZipAlignUtil.mustStore("AndroidManifest.xml"));
        assertTrue(ApkZipAlignUtil.mustStore("resources.arsc"));
        assertTrue(ApkZipAlignUtil.mustStore("res/drawable/icon.png"));
        assertTrue(ApkZipAlignUtil.mustStore("res/raw/sound.mp3"));
        assertFalse(ApkZipAlignUtil.mustStore("res/layout/main.xml"));
        assertFalse(ApkZipAlignUtil.mustStore("res/values/strings.xml"));
        assertFalse(ApkZipAlignUtil.mustStore("classes.dex"));
        assertFalse(ApkZipAlignUtil.mustStore("assets/font.ttf"));
        assertFalse(ApkZipAlignUtil.mustStore("META-INF/MANIFEST.MF"));
    }

    @Test
    public void misalignedArscGetsFixed() throws Exception {
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("AndroidManifest.xml", bytes(100, 1));
        payloads.put("resources.arsc", bytes(1000, 2));
        payloads.put("classes.dex", bytes(5000, 3));
        payloads.put("res/drawable/icon.png", bytes(700, 4));
        payloads.put("res/layout/main.xml", bytes(300, 5));
        File apk = writeTestApk(payloads, false);

        String before = ApkZipAlignUtil.installIssue(apk);
        assertNotNull("test setup must be non-compliant, got: " + before, before);
        assertTrue("expected alignment complaint, got: " + before, before.contains("aligned"));

        assertTrue(ApkZipAlignUtil.ensureInstallable(apk));
        assertNull(ApkZipAlignUtil.installIssue(apk));

        // payloads untouched, methods per aapt rules
        assertPayloads(apk, payloads);
        assertMethod(apk, "AndroidManifest.xml", ZipEntry.STORED);
        assertMethod(apk, "resources.arsc", ZipEntry.STORED);
        assertMethod(apk, "classes.dex", ZipEntry.DEFLATED);
        assertMethod(apk, "res/drawable/icon.png", ZipEntry.STORED);
        assertMethod(apk, "res/layout/main.xml", ZipEntry.DEFLATED);

        // idempotent: second run is a no-op
        assertFalse(ApkZipAlignUtil.ensureInstallable(apk));
        assertNull(ApkZipAlignUtil.installIssue(apk));
        assertPayloads(apk, payloads);
        assertLocalNamesMatchCentralDirectory(apk);
    }

    @Test
    public void deflatedArscGetsConverted() throws Exception {
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("AndroidManifest.xml", bytes(100, 1));
        payloads.put("resources.arsc", bytes(1000, 2));
        payloads.put("classes.dex", bytes(5000, 3));
        File apk = writeTestApk(payloads, true);

        String before = ApkZipAlignUtil.installIssue(apk);
        assertNotNull(before);
        assertTrue("expected stored complaint, got: " + before, before.contains("STORED"));

        assertTrue(ApkZipAlignUtil.ensureInstallable(apk));
        assertNull(ApkZipAlignUtil.installIssue(apk));
        assertPayloads(apk, payloads);
        assertMethod(apk, "resources.arsc", ZipEntry.STORED);
        assertLocalNamesMatchCentralDirectory(apk);
    }

    // ---------- helpers ----------

    private static byte[] bytes(int len, int seed) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = (byte) ((seed * 31 + i * 7) & 0xFF);
        return b;
    }

    /**
     * Writes an apk-like zip with java.util.zip (no alignment padding, like a
     * zip4j rebuild): manifest STORED, arsc STORED (or DEFLATED when
     * deflateArsc), dex/xml DEFLATED, png STORED.
     */
    private static File writeTestApk(Map<String, byte[]> payloads, boolean deflateArsc) throws Exception {
        File apk = File.createTempFile("aligntest", ".apk");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apk))) {
            for (Map.Entry<String, byte[]> e : payloads.entrySet()) {
                String name = e.getKey();
                byte[] data = e.getValue();
                ZipEntry ze = new ZipEntry(name);
                boolean store = name.equals("AndroidManifest.xml")
                        || name.equals("res/drawable/icon.png")
                        || (name.equals("resources.arsc") && !deflateArsc);
                if (store) {
                    ze.setMethod(ZipEntry.STORED);
                    ze.setSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    ze.setCrc(crc.getValue());
                }
                zos.putNextEntry(ze);
                zos.write(data);
                zos.closeEntry();
            }
        }
        return apk;
    }

    private static void assertPayloads(File apk, Map<String, byte[]> payloads) throws Exception {
        try (ZipFile zf = new ZipFile(apk)) {
            assertEquals(payloads.size(), zf.size());
            for (Map.Entry<String, byte[]> e : payloads.entrySet()) {
                ZipEntry ze = zf.getEntry(e.getKey());
                assertNotNull(e.getKey(), ze);
                assertBytesEquals(e.getValue(), readAll(zf.getInputStream(ze)));
            }
        }
    }

    private static void assertMethod(File apk, String name, int method) throws Exception {
        try (ZipFile zf = new ZipFile(apk)) {
            assertEquals(name, method, zf.getEntry(name).getMethod());
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }

    private static void assertBytesEquals(byte[] expected, byte[] actual) {
        assertEquals("length", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                throw new AssertionError("bytes differ at " + i);
            }
        }
    }

    /**
     * Mirrors apksig's strict check (LocalFileRecord.getRecord): every local
     * header name must equal the central directory name, otherwise signing
     * fails with "Malformed ZIP entry".
     */
    private static void assertLocalNamesMatchCentralDirectory(File apk) throws Exception {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(apk, "r");
             ZipFile zf = new ZipFile(apk)) {
            java.util.Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                long off = localOffsetOf(raf, apk, ze.getName());
                raf.seek(off);
                byte[] h = new byte[30];
                raf.readFully(h);
                int nameLen = u16(h, 26);
                byte[] nameBytes = new byte[nameLen];
                raf.readFully(nameBytes);
                String localName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
                assertEquals("local name of " + ze.getName(), ze.getName(), localName);
            }
        }
    }

    private static long localOffsetOf(java.io.RandomAccessFile raf, File apk, String name) throws Exception {
        long fileLen = raf.length();
        long searchStart = Math.max(0, fileLen - 70000);
        int len = (int) (fileLen - searchStart);
        byte[] tail = new byte[len];
        raf.seek(searchStart);
        raf.readFully(tail);
        int eocd = -1;
        for (int i = len - 22; i >= 0; i--) {
            if (u32(tail, i) == 0x06054b50L) {
                eocd = i;
                break;
            }
        }
        if (eocd < 0) throw new AssertionError("no EOCD");
        int count = u16(tail, eocd + 10);
        long pos = u32(tail, eocd + 16);
        for (int i = 0; i < count; i++) {
            raf.seek(pos);
            byte[] h = new byte[46];
            raf.readFully(h);
            int nl = u16(h, 28), el = u16(h, 30), cl = u16(h, 32);
            byte[] nm = new byte[nl];
            raf.readFully(nm);
            String entryName = new String(nm, java.nio.charset.StandardCharsets.UTF_8);
            if (entryName.equals(name)) return u32(h, 42);
            pos += 46 + nl + el + cl;
        }
        throw new AssertionError("entry not found: " + name);
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static long u32(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o + 1] & 0xFFL) << 8)
                | ((b[o + 2] & 0xFFL) << 16) | ((b[o + 3] & 0xFFL) << 24);
    }
}

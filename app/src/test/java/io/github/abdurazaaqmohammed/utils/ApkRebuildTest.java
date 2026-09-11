package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ApkRebuildTest {

    private static byte[] bytes(int len, int seed) {
        byte[] b = new byte[len];
        for (int i = 0; i < len; i++) b[i] = (byte) ((seed * 31 + i * 7) & 0xFF);
        return b;
    }

    private static File writeTestApk(Map<String, byte[]> payloads, Map<String, Integer> methods) throws Exception {
        File apk = File.createTempFile("rebuildtest", ".apk");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(apk))) {
            for (Map.Entry<String, byte[]> e : payloads.entrySet()) {
                byte[] data = e.getValue();
                ZipEntry ze = new ZipEntry(e.getKey());
                if (methods.get(e.getKey()) == ZipEntry.STORED) {
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

    private static File writeTempBytes(byte[] data) throws Exception {
        File f = File.createTempFile("rebuildentry", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(data);
        }
        return f;
    }

    private static byte[] readEntry(File apk, String name) throws Exception {
        try (ZipFile zf = new ZipFile(apk)) {
            ZipEntry ze = zf.getEntry(name);
            assertNotNull(name, ze);
            return readAll(zf.getInputStream(ze));
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

    private static long dataOffsetOf(File apk, String name) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
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
                String entryName = new String(nm, "UTF-8");
                long localOff = u32(h, 42);
                if (entryName.equals(name)) {
                    raf.seek(localOff);
                    byte[] lh = new byte[30];
                    raf.readFully(lh);
                    return localOff + 30 + u16(lh, 26) + u16(lh, 28);
                }
                pos += 46 + nl + el + cl;
            }
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

    @Test
    public void rebuildAppliesAllRules() throws Exception {
        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("AndroidManifest.xml", bytes(100, 1));
        payloads.put("resources.arsc", bytes(1000, 2));
        payloads.put("classes.dex", bytes(20000, 3));
        payloads.put("lib/arm64-v8a/libx.so", bytes(9000, 4));
        payloads.put("res/drawable/icon.png", bytes(700, 5));
        payloads.put("res/layout/main.xml", bytes(300, 6));
        payloads.put("META-INF/DUMMY.SF", bytes(50, 7));
        Map<String, Integer> methods = new HashMap<>();
        methods.put("AndroidManifest.xml", ZipEntry.STORED);
        methods.put("resources.arsc", ZipEntry.STORED);
        methods.put("classes.dex", ZipEntry.DEFLATED);
        methods.put("lib/arm64-v8a/libx.so", ZipEntry.STORED);
        methods.put("res/drawable/icon.png", ZipEntry.STORED);
        methods.put("res/layout/main.xml", ZipEntry.DEFLATED);
        methods.put("META-INF/DUMMY.SF", ZipEntry.DEFLATED);
        File input = writeTestApk(payloads, methods);

        byte[] newDex = bytes(21000, 8);
        byte[] note = "hello".getBytes("UTF-8");
        Map<String, File> replacements = new LinkedHashMap<>();
        replacements.put("classes.dex", writeTempBytes(newDex));
        Map<String, File> additions = new LinkedHashMap<>();
        additions.put("assets/note.txt", writeTempBytes(note));
        Set<String> skip = new HashSet<>();
        skip.add("META-INF/DUMMY.SF");

        File output = File.createTempFile("rebuildout", ".apk");
        output.delete();
        ApkZipAlignUtil.rebuildApk(input, output, replacements, null, additions, skip);

        assertNull(ApkZipAlignUtil.installIssue(output));
        assertBytesEquals(newDex, readEntry(output, "classes.dex"));
        assertBytesEquals(note, readEntry(output, "assets/note.txt"));
        assertBytesEquals(payloads.get("AndroidManifest.xml"), readEntry(output, "AndroidManifest.xml"));
        assertBytesEquals(payloads.get("resources.arsc"), readEntry(output, "resources.arsc"));
        assertBytesEquals(payloads.get("lib/arm64-v8a/libx.so"), readEntry(output, "lib/arm64-v8a/libx.so"));
        assertBytesEquals(payloads.get("res/drawable/icon.png"), readEntry(output, "res/drawable/icon.png"));
        assertBytesEquals(payloads.get("res/layout/main.xml"), readEntry(output, "res/layout/main.xml"));
        try (ZipFile zf = new ZipFile(output)) {
            assertNull(zf.getEntry("META-INF/DUMMY.SF"));
            assertEquals(ZipEntry.STORED, zf.getEntry("resources.arsc").getMethod());
            assertEquals(ZipEntry.STORED, zf.getEntry("lib/arm64-v8a/libx.so").getMethod());
            assertEquals(ZipEntry.DEFLATED, zf.getEntry("classes.dex").getMethod());
            assertEquals(ZipEntry.DEFLATED, zf.getEntry("assets/note.txt").getMethod());
        }
        assertEquals(0, dataOffsetOf(output, "resources.arsc") % 4);
        assertEquals(0, dataOffsetOf(output, "lib/arm64-v8a/libx.so") % 4096);
    }

    private static void assertBytesEquals(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) throw new AssertionError("bytes differ at " + i);
        }
    }
}

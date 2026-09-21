package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class JpegMetaStripTest {

    private static byte[] segment(int marker, byte[] payload) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(marker);
        int len = payload.length + 2;
        out.write((len >> 8) & 0xFF);
        out.write(len & 0xFF);
        out.write(payload);
        return out.toByteArray();
    }

    private static byte[] zeros(int n) {
        return new byte[n];
    }

    private static byte[] sampleJpeg() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        byte[] app0 = new byte[]{0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00};
        out.write(segment(0xE0, app0));
        byte[] exif = new byte[]{0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x4D, 0x4D, 0x00, 0x2A};
        out.write(segment(0xE1, exif));
        out.write(segment(0xFE, "hello".getBytes(StandardCharsets.US_ASCII)));
        out.write(segment(0xDB, zeros(65)));
        out.write(segment(0xC0, new byte[]{0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00}));
        out.write(segment(0xC4, zeros(29)));
        out.write(segment(0xDA, new byte[]{0x01, 0x01, 0x00, 0x00, 0x3F, 0x00}));
        out.write(new byte[]{0x11, 0x22, (byte) 0xFF, 0x00, 0x33, (byte) 0xFF, (byte) 0xD3, 0x44});
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD9});
        return out.toByteArray();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    @Test
    public void stripRemovesMetadataKeepsStructure() throws Exception {
        byte[] jpeg = sampleJpeg();
        assertTrue(JpegMetaStrip.isJpeg(jpeg));
        byte[] stripped = JpegMetaStrip.strip(jpeg);
        assertTrue(JpegMetaStrip.isJpeg(stripped));
        assertTrue(contains(stripped, "JFIF".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(contains(stripped, "Exif".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(contains(stripped, "hello".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(stripped.length < jpeg.length);
        int n = stripped.length;
        assertTrue(stripped[n - 2] == (byte) 0xFF && stripped[n - 1] == (byte) 0xD9);
        byte[] scan = new byte[]{0x11, 0x22, (byte) 0xFF, 0x00, 0x33, (byte) 0xFF, (byte) 0xD3, 0x44};
        assertTrue(contains(stripped, scan));
    }

    @Test
    public void stripKeepsApp0AndDropsApp13() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        out.write(segment(0xE0, zeros(14)));
        out.write(segment(0xED, "Photoshop".getBytes(StandardCharsets.US_ASCII)));
        out.write(segment(0xDB, zeros(65)));
        out.write(segment(0xC0, new byte[]{0x08, 0x00, 0x01, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00}));
        out.write(segment(0xC4, zeros(29)));
        out.write(segment(0xDA, new byte[]{0x01, 0x01, 0x00, 0x00, 0x3F, 0x00}));
        out.write(new byte[]{0x00, (byte) 0xFF, (byte) 0xD9});
        byte[] stripped = JpegMetaStrip.strip(out.toByteArray());
        assertFalse(contains(stripped, "Photoshop".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(contains(stripped, new byte[]{(byte) 0xFF, (byte) 0xE0}));
        assertTrue(contains(stripped, new byte[]{0x00, (byte) 0xFF, (byte) 0xD9}));
    }

    @Test
    public void stripRejectsNonJpeg() {
        try {
            JpegMetaStrip.strip(new byte[]{0x42, 0x4D, 0x00});
            fail("expected IOException");
        } catch (Exception e) {
            assertTrue(e instanceof java.io.IOException);
        }
    }

    @Test
    public void stripRejectsTruncated() {
        try {
            JpegMetaStrip.strip(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            fail("expected IOException");
        } catch (Exception e) {
            assertTrue(e instanceof java.io.IOException);
        }
    }

    @Test
    public void stripIsIdempotent() throws Exception {
        byte[] once = JpegMetaStrip.strip(sampleJpeg());
        byte[] twice = JpegMetaStrip.strip(once);
        assertArrayEquals(once, twice);
        assertTrue(Arrays.equals(once, twice));
    }
}

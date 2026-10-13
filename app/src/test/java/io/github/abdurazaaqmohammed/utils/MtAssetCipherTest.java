package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.zip.Adler32;

/** Round trips for the asset container, using the integrity fields the reader checks. */
public class MtAssetCipherTest {

    @Test
    public void containerMagicIsRecognised() {
        byte[] wrapped = MtAssetCipher.encrypt(payloadDex(256));
        assertTrue(MtAssetCipher.looksLikeContainer(wrapped));
        assertFalse(MtAssetCipher.looksLikeContainer(payloadDex(256)));
        assertFalse(MtAssetCipher.looksLikeContainer(new byte[0]));
    }

    @Test
    public void dexRoundTripRecoversKeyFromMagic() {
        byte[] inner = payloadDex(4096);
        byte key = 0x14;
        byte[] wrapped = MtAssetCipher.encrypt(inner, key);

        MtAssetCipher.Result result = MtAssetCipher.decrypt(wrapped);

        assertTrue(result.detail, result.ok);
        assertEquals("dex", result.format);
        assertEquals(key, result.key);
        assertArrayEquals(inner, result.data);
    }

    @Test
    public void arscRoundTripRecoversKeyFromMagic() {
        byte[] inner = payloadArsc(2048);
        byte key = 0x70;
        byte[] wrapped = MtAssetCipher.encrypt(inner, key);

        MtAssetCipher.Result result = MtAssetCipher.decrypt(wrapped);

        assertTrue(result.detail, result.ok);
        assertEquals("arsc", result.format);
        assertEquals(key, result.key);
        assertArrayEquals(inner, result.data);
    }

    @Test
    public void elfRoundTripRecoversKeyFromMagic() {
        byte[] inner = payloadElf(512);
        byte key = (byte) 0xd8;
        byte[] wrapped = MtAssetCipher.encrypt(inner, key);

        MtAssetCipher.Result result = MtAssetCipher.decrypt(wrapped);

        assertTrue(result.detail, result.ok);
        assertEquals("elf", result.format);
        assertEquals(key, result.key);
        assertArrayEquals(inner, result.data);
    }

    @Test
    public void defaultKeyStillRoundTrips() {
        byte[] inner = payloadDex(300);
        byte[] wrapped = MtAssetCipher.encrypt(inner);
        MtAssetCipher.Result result = MtAssetCipher.decrypt(wrapped);
        assertTrue(result.detail, result.ok);
        assertEquals(MtAssetCipher.DEFAULT_KEY, result.key);
    }

    @Test
    public void aForeignKeyIsRecoveredNotStored() {
        byte[] inner = payloadDex(512);
        byte[] wrapped = MtAssetCipher.encrypt(inner, (byte) 0x21);

        // The key is never stored, so wrapping with any byte is fine: the reader
        // takes it back out of the inner magic and proves it with the checksums.
        MtAssetCipher.Result result = MtAssetCipher.decrypt(wrapped);
        assertTrue(result.detail, result.ok);
        assertEquals((byte) 0x21, result.key);
        assertArrayEquals(inner, result.data);

        // Forcing a key that is not the one used still fails the dex checks.
        MtAssetCipher.Result forced = MtAssetCipher.decrypt(wrapped, (byte) 0x22);
        assertEquals("a wrong key must not look like a dex", "raw", forced.format);
    }

    @Test
    public void plainPayloadWithUnknownHeaderNeedsAKey() {
        byte[] inner = new byte[64];
        new Random(7).nextBytes(inner);
        byte[] wrapped = MtAssetCipher.encrypt(inner, (byte) 0x33);

        MtAssetCipher.Result guessed = MtAssetCipher.decrypt(wrapped);
        assertFalse(guessed.ok);

        MtAssetCipher.Result keyed = MtAssetCipher.decrypt(wrapped, (byte) 0x33);
        assertTrue(keyed.ok);
        assertArrayEquals(inner, keyed.data);
    }

    @Test
    public void truncatedContainerIsRejected() {
        byte[] wrapped = MtAssetCipher.encrypt(payloadDex(256));
        assertFalse(MtAssetCipher.looksLikeContainer(Arrays.copyOf(wrapped, 4)));
        assertFalse(MtAssetCipher.decrypt(new byte[]{(byte) 0xfb, 0x61, 0x62}).ok);
    }

    @Test
    public void extensionFollowsTheVerifiedFormat() {
        assertEquals(".dex", MtAssetCipher.extensionFor("dex"));
        assertEquals(".arsc", MtAssetCipher.extensionFor("arsc"));
        assertEquals(".so", MtAssetCipher.extensionFor("elf"));
        assertEquals(".bin", MtAssetCipher.extensionFor(null));
    }

    @Test
    public void unusedSiblingNeverOverwrites() {
        java.io.File dir = new java.io.File(System.getProperty("java.io.tmpdir"), "mtasset-test");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        java.io.File first = new java.io.File(dir, "payload");
        assertNotNull(first);
        java.io.File a = MtAssetCipher.unusedSibling(first, ".dex");
        try {
            MtAssetCipher.writeAll(a, new byte[]{1});
            java.io.File b = MtAssetCipher.unusedSibling(first, ".dex");
            assertFalse(a.getPath().equals(b.getPath()));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** A dex whose size, adler32 and SHA-1 fields all agree, so the reader accepts it. */
    private static byte[] payloadDex(int size) {
        byte[] p = new byte[size];
        for (int i = 8; i < size; i++) {
            p[i] = (byte) (i * 31 + 7);
        }
        p[0] = 'd';
        p[1] = 'e';
        p[2] = 'x';
        p[3] = '\n';
        p[4] = '0';
        p[5] = '3';
        p[6] = '5';
        p[7] = 0;
        writeLe32(p, 32, size);
        writeLe32(p, 36, 0x70);
        writeLe32(p, 40, 0x12345678);
        // The signature goes in first: the adler32 covers everything from byte 12
        // on, signature included, so it has to be computed last.
        byte[] sha = sha1Of(Arrays.copyOfRange(p, 32, size));
        System.arraycopy(sha, 0, p, 12, 20);
        Adler32 adler = new Adler32();
        adler.update(p, 12, size - 12);
        writeLe32(p, 8, (int) adler.getValue());
        return p;
    }

    /** A resource table header whose own total size matches the buffer. */
    private static byte[] payloadArsc(int size) {
        byte[] p = new byte[size];
        for (int i = 12; i < size; i++) {
            p[i] = (byte) (i * 13 + 3);
        }
        writeLe16(p, 0, 0x0002);
        writeLe16(p, 2, 12);
        writeLe32(p, 4, size);
        return p;
    }

    /** An elf header with a plausible type and machine. */
    private static byte[] payloadElf(int size) {
        byte[] p = new byte[size];
        for (int i = 16; i < size; i++) {
            p[i] = (byte) (i * 17 + 5);
        }
        p[0] = 0x7f;
        p[1] = 'E';
        p[2] = 'L';
        p[3] = 'F';
        p[4] = 2;
        p[5] = 1;
        writeLe16(p, 16, 3);
        writeLe16(p, 18, 0xb7);
        return p;
    }

    private static byte[] sha1Of(byte[] data) {
        try {
            return java.security.MessageDigest.getInstance("SHA-1").digest(data);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static void writeLe32(byte[] p, int off, int value) {
        p[off] = (byte) value;
        p[off + 1] = (byte) (value >> 8);
        p[off + 2] = (byte) (value >> 16);
        p[off + 3] = (byte) (value >> 24);
    }

    private static void writeLe16(byte[] p, int off, int value) {
        p[off] = (byte) value;
        p[off + 1] = (byte) (value >> 8);
    }
}

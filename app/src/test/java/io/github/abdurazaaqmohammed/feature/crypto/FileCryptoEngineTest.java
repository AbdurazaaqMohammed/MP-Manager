package io.github.abdurazaaqmohammed.feature.crypto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileCryptoEngineTest {

    @Test
    public void detectsAndDecodesBase64() {
        byte[] data = "SGVsbG8sIFdvcmxkIQ==".getBytes(StandardCharsets.US_ASCII);
        FileCryptoEngine.Finding f = FileCryptoEngine.analyzeBytes(data, "data.bin", "data.bin", "");
        assertEquals(FileCryptoEngine.Kind.ENCODING, f.kind);
        assertEquals("Base64", f.encoding);
        FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, "", "", "");
        assertTrue(r.success);
        assertTrue(r.text.contains("Hello, World!"));
    }

    @Test
    public void detectsAndDecodesHex() {
        byte[] data = "48656c6c6f2c20486578".getBytes(StandardCharsets.US_ASCII);
        FileCryptoEngine.Finding f = FileCryptoEngine.analyzeBytes(data, "data.bin", "data.bin", "");
        assertEquals(FileCryptoEngine.Kind.ENCODING, f.kind);
        assertEquals("Hex", f.encoding);
        FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, "", "", "");
        assertTrue(r.success);
        assertTrue(r.text.contains("Hello, Hex"));
    }

    @Test
    public void decompressesGzipAsCompressionNotEncryption() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write("compressed text".getBytes(StandardCharsets.UTF_8));
        }
        byte[] data = out.toByteArray();
        FileCryptoEngine.Finding f = FileCryptoEngine.analyzeBytes(data, "data.gz", "data.gz", "");
        assertEquals("GZIP", f.type);
        assertEquals(FileCryptoEngine.Kind.COMPRESSION, f.kind);
        assertFalse(f.encrypted);
        FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, "", "", "");
        assertTrue(r.success);
        assertTrue(r.text.contains("compressed text"));
    }

    @Test
    public void decryptsXorWithProvidedKey() {
        byte[] plain = "secret xor payload".getBytes(StandardCharsets.UTF_8);
        byte[] key = new byte[]{0x5A};
        byte[] data = FileCryptoEngine.xor(plain, key);
        FileCryptoEngine.Finding f = new FileCryptoEngine.Finding();
        f.kind = FileCryptoEngine.Kind.ENCRYPTION;
        f.encrypted = true;
        f.needsKey = true;
        FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, "", "5A", "");
        assertTrue(r.success);
        assertTrue(r.text.contains("secret xor payload"));
    }

    @Test
    public void decryptsAesCbcWithKeyAndIv() throws Exception {
        byte[] plain = "aes protected text".getBytes(StandardCharsets.UTF_8);
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] iv = "abcdef0123456789".getBytes(StandardCharsets.UTF_8);
        byte[] data = FileCryptoEngine.aes(plain, key, iv, true, true);
        FileCryptoEngine.Finding f = new FileCryptoEngine.Finding();
        f.kind = FileCryptoEngine.Kind.ENCRYPTION;
        f.encrypted = true;
        f.needsKey = true;
        FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, "",
                "0123456789abcdef", "abcdef0123456789");
        assertTrue(r.success);
        assertTrue(r.text.contains("aes protected text"));
    }

    @Test
    public void doesNotClaimSuccessWithoutEncryptionKey() {
        byte[] data = new byte[64];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 37 + 11);
        FileCryptoEngine.Finding f = FileCryptoEngine.analyzeBytes(data, "unknown.bin", "unknown.bin", "");
        FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, "", "", "");
        assertFalse(r.success);
        assertTrue(r.needsKey || r.message.length() > 0);
    }

    @Test
    public void plainTextIsNotReportedAsEncrypted() {
        byte[] data = "This is ordinary readable text, not encrypted.".getBytes(StandardCharsets.UTF_8);
        FileCryptoEngine.Finding f = FileCryptoEngine.analyzeBytes(data, "readme.txt", "readme.txt", "");
        assertEquals(FileCryptoEngine.Kind.TEXT, f.kind);
        FileCryptoEngine.Result r = FileCryptoEngine.decrypt(f, data, "", "", "");
        assertFalse(r.success);
    }

    @Test
    public void passwordKeyRemainsUtf8NotBase64() {
        byte[] key = FileCryptoEngine.parseKey("password");
        assertEquals(8, key.length);
        assertEquals("password", new String(key, StandardCharsets.UTF_8));
    }

    @Test
    public void detectsKnownMagicTypes() {
        assertTrue(FileCryptoEngine.detectType(new byte[]{(byte) 0x89, 'P', 'N', 'G'}, "x").contains("PNG"));
        assertTrue(FileCryptoEngine.detectType(new byte[]{0x1f, (byte) 0x8b}, "x").contains("GZIP"));
        assertTrue(FileCryptoEngine.detectType("PK\u0003\u0004".getBytes(StandardCharsets.ISO_8859_1), "x.apk")
                .contains("APK"));
    }

    @Test
    public void scansArchiveEntriesAndFindsEncodedContent() throws Exception {
        File zip = File.createTempFile("crypto-scan", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("assets/secret.txt"));
            out.write("U2VjcmV0IGFzc2V0".getBytes(StandardCharsets.US_ASCII));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("assets/readme.txt"));
            out.write("ordinary text".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        List<FileCryptoEngine.Finding> found = FileCryptoEngine.scan(zip, false, null);
        assertFalse(found.isEmpty());
        FileCryptoEngine.Finding target = null;
        for (FileCryptoEngine.Finding f : found) {
            if (f.name.equals("secret.txt")) target = f;
        }
        assertNotNull(target);
        assertEquals("Base64", target.encoding);
        byte[] data = FileCryptoEngine.readFile(zip);
        assertNotNull(data);
        zip.delete();
    }

    @Test
    public void entropyDistinguishesTextFromRandom() {
        byte[] text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8);
        byte[] random = new byte[256];
        for (int i = 0; i < random.length; i++) random[i] = (byte) i;
        assertTrue(FileCryptoEngine.entropy(random) > FileCryptoEngine.entropy(text));
    }
}

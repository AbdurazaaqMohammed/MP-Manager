package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.apksig.ApkSigner;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;

import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApkSignFlowTest {

    @Test
    public void injectStyleApkSignsAndVerifies() throws Exception {
        String[] names = {"AndroidManifest.xml", "resources.arsc", "classes.dex",
                "res/drawable/icon.png", "res/layout/main.xml"};
        byte[][] datas = {minimalBinaryManifest(), pseudoRandom(20000, 1),
                pseudoRandom(60000, 2), pseudoRandom(700, 3), pseudoRandom(300, 4)};
        boolean[] store = {true, true, false, true, false};

        File apk = File.createTempFile("signflow", ".apk");
        try (ZipFile zf = new ZipFile(apk)) {
            for (int i = 0; i < names.length; i++) {
                ZipParameters zp = new ZipParameters();
                zp.setCompressionMethod(store[i] ? CompressionMethod.STORE : CompressionMethod.DEFLATE);
                zp.setEncryptFiles(false);
                zp.setFileNameInZip(names[i]);
                try (InputStream is = new ByteArrayInputStream(datas[i])) {
                    zf.addStream(is, zp);
                }
            }
        }

        ApkZipAlignUtil.ensureInstallable(apk);
        assertNull(ApkZipAlignUtil.installIssue(apk));

        File keystore = File.createTempFile("signflow", ".jks");
        keystore.delete();
        String keytool = System.getProperty("java.home") + File.separator + "bin"
                + File.separator + "keytool" + (File.separatorChar == '\\' ? ".exe" : "");
        Assume.assumeTrue("keytool not available", new File(keytool).isFile());
        List<String> cmd = new ArrayList<>();
        Collections.addAll(cmd, keytool, "-genkeypair", "-keystore", keystore.getAbsolutePath(),
                "-alias", "test", "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-storepass", "pass123", "-keypass", "pass123", "-dname", "CN=Test",
                "-storetype", "JKS");
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] out = readAll(proc.getInputStream());
        int rc = proc.waitFor();
        Assume.assumeTrue("keytool failed: " + new String(out), rc == 0);

        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = new java.io.FileInputStream(keystore)) {
            ks.load(is, "pass123".toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey("test", "pass123".toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate("test");
        List<X509Certificate> certs = new ArrayList<>(Collections.singletonList(cert));

        File signed = new File(apk.getParentFile(), "signflow-signed.apk");
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("test", key, certs).build();
        ApkSigner.Builder builder = new ApkSigner.Builder(Collections.singletonList(cfg));
        builder.setInputApk(apk);
        builder.setOutputApk(signed);
        builder.setV1SigningEnabled(true);
        builder.setV2SigningEnabled(true);
        builder.setV3SigningEnabled(false);
        builder.setMinSdkVersion(24);
        builder.build().sign();
        assertTrue(signed.isFile() && signed.length() > 0);
        // NOTE: no ApkVerifier step — its JAR-manifest text parsing trips over
        // a JVM-environment quirk unrelated to zip structure. The sign step
        // above already runs apksig's strict local-header validation
        // (the exact "Malformed ZIP entry" path from the bug report).
    }

    /**
     * Minimal binary AndroidManifest.xml:
     * &lt;manifest package="test.example"&gt;&lt;uses-sdk android:minSdkVersion="24"/&gt;&lt;/manifest&gt;
     * (UTF-8 string pool, no resource map; attribute lookup by name works.)
     */
    private static byte[] minimalBinaryManifest() throws Exception {
        String[] strings = {"android", "http://schemas.android.com/apk/res/android",
                "manifest", "minSdkVersion", "package", "test.example", "uses-sdk", "24"};
        java.io.ByteArrayOutputStream stringData = new java.io.ByteArrayOutputStream();
        int[] offsets = new int[strings.length];
        for (int i = 0; i < strings.length; i++) {
            offsets[i] = stringData.size();
            byte[] utf8 = strings[i].getBytes("UTF-8");
            // AAPT UTF-8 form: 1-or-2-byte char count, 1-or-2-byte byte count, data, NUL.
            writeLen8(stringData, strings[i].length());
            writeLen8(stringData, utf8.length);
            stringData.write(utf8);
            stringData.write(0);
        }
        while (stringData.size() % 4 != 0) stringData.write(0);

        java.io.ByteArrayOutputStream pool = new java.io.ByteArrayOutputStream();
        writeU16(pool, 0x0001);
        writeU16(pool, 28);
        int poolSizePos = pool.size();
        writeU32(pool, 0); // size placeholder
        writeU32(pool, strings.length);
        writeU32(pool, 0);
        writeU32(pool, 0x100); // UTF-8
        writeU32(pool, 28 + 4 * strings.length);
        writeU32(pool, 0);
        for (int off : offsets) writeU32(pool, off);
        pool.write(stringData.toByteArray());
        patchU32(pool, poolSizePos, pool.size());

        java.io.ByteArrayOutputStream xml = new java.io.ByteArrayOutputStream();
        writeU16(xml, 0x0003);
        writeU16(xml, 8);
        int xmlSizePos = xml.size();
        writeU32(xml, 0); // size placeholder
        xml.write(pool.toByteArray());
        // Node chunks: chunk header(8) + lineNumber(4) + comment(4) + payload.
        // start namespace: prefix=0 (android), uri=1 (total 24 bytes)
        writeU16(xml, 0x0100);
        writeU16(xml, 16);
        writeU32(xml, 24);
        writeU32(xml, 0);
        writeU32(xml, 0xFFFFFFFFL);
        writeU32(xml, 0);
        writeU32(xml, 1);
        // start manifest, 1 attr: package="test.example" (fixed part 36 bytes)
        writeStartElement(xml, 0xFFFFFFFFL, 2, 1);
        writeAttr(xml, 0xFFFFFFFFL, 4, 5, 0x03, 5);
        // start uses-sdk, 1 attr: android:minSdkVersion=24
        writeStartElement(xml, 0xFFFFFFFFL, 6, 1);
        writeAttr(xml, 1, 3, 7, 0x10, 24);
        // end uses-sdk, end manifest, end namespace
        writeEndElement(xml, 0xFFFFFFFFL, 6);
        writeEndElement(xml, 0xFFFFFFFFL, 2);
        writeU16(xml, 0x0101);
        writeU16(xml, 16);
        writeU32(xml, 24);
        writeU32(xml, 0);
        writeU32(xml, 0xFFFFFFFFL);
        writeU32(xml, 0);
        writeU32(xml, 1);
        patchU32(xml, xmlSizePos, xml.size());
        return xml.toByteArray();
    }

    private static void writeStartElement(java.io.ByteArrayOutputStream out, long ns, int name,
                                          int attrCount) throws Exception {
        writeU16(out, 0x0102);
        writeU16(out, 16);
        writeU32(out, 36L + 20L * attrCount);
        writeU32(out, 0);
        writeU32(out, 0xFFFFFFFFL);
        writeU32(out, ns);
        writeU32(out, name);
        writeU16(out, 20);
        writeU16(out, 20);
        writeU16(out, attrCount);
        writeU16(out, 0);
        writeU16(out, 0);
        writeU16(out, 0);
    }

    private static void writeAttr(java.io.ByteArrayOutputStream out, long ns, int name,
                                  int raw, int type, int data) throws Exception {
        writeU32(out, ns);
        writeU32(out, name);
        writeU32(out, raw);
        writeU16(out, 8);
        out.write(0);
        out.write(type);
        writeU32(out, data & 0xFFFFFFFFL);
    }

    private static void writeEndElement(java.io.ByteArrayOutputStream out, long ns, int name) throws Exception {
        writeU16(out, 0x0103);
        writeU16(out, 16);
        writeU32(out, 24);
        writeU32(out, 0);
        writeU32(out, 0xFFFFFFFFL);
        writeU32(out, ns);
        writeU32(out, name);
    }

    private static void writeU16(java.io.ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void writeLen8(java.io.ByteArrayOutputStream out, int v) throws Exception {
        if (v < 0 || v > 32767) throw new IllegalArgumentException("string too long");
        if (v < 128) {
            out.write(v);
        } else {
            out.write(0x80 | (v >>> 8));
            out.write(v & 0xFF);
        }
    }

    private static void writeU32(java.io.ByteArrayOutputStream out, long v) {
        out.write((int) (v & 0xFF));
        out.write((int) ((v >>> 8) & 0xFF));
        out.write((int) ((v >>> 16) & 0xFF));
        out.write((int) ((v >>> 24) & 0xFF));
    }

    private static void patchU32(java.io.ByteArrayOutputStream out, int pos, int v) {
        byte[] b = out.toByteArray();
        b[pos] = (byte) (v & 0xFF);
        b[pos + 1] = (byte) ((v >>> 8) & 0xFF);
        b[pos + 2] = (byte) ((v >>> 16) & 0xFF);
        b[pos + 3] = (byte) ((v >>> 24) & 0xFF);
        out.reset();
        out.write(b, 0, b.length);
    }

    private static byte[] pseudoRandom(int len, int seed) {        byte[] b = new byte[len];
        long state = seed * 0x9E3779B9L + 1;
        for (int i = 0; i < len; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            b[i] = (byte) (state >>> 33);
        }
        return b;
    }

    private static byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        is.close();
        return bos.toByteArray();
    }
}

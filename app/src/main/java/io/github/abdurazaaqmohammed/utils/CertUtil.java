package io.github.abdurazaaqmohammed.utils;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CertUtil {

    private static final byte[] APK_SIG_BLOCK_MAGIC = "APK Sig Block 42".getBytes(StandardCharsets.US_ASCII);
    private static final int V2_BLOCK_ID = 0x7109871a;
    private static final int V3_BLOCK_ID = 0xf05368c0;
    private static final int V31_BLOCK_ID = 0x1b93ad61;


    public static List<X509Certificate> getCertificatesUnverified(File apk) throws IOException {
        try {
            byte[] der = extractCertFromSigningBlock(apk);
            if (der != null) {
                List<X509Certificate> out = new ArrayList<>();
                out.add((X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(der)));
                return out;
            }
        } catch (Exception ignored) {
        }
        try (ZipFile zf = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().toUpperCase(Locale.US);
                if (!name.startsWith("META-INF/")) continue;
                if (!name.endsWith(".RSA") && !name.endsWith(".DSA") && !name.endsWith(".EC")) continue;
                try {
                    Collection<? extends java.security.cert.Certificate> certs =
                            CertificateFactory.getInstance("X.509").generateCertificates(zf.getInputStream(entry));
                    if (!certs.isEmpty()) {
                        List<X509Certificate> list = new ArrayList<>();
                        for (java.security.cert.Certificate c : certs) list.add((X509Certificate) c);
                        return list;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static byte[] extractCertFromSigningBlock(File apk) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(apk, "r")) {
            long fileLen = raf.length();
            if (fileLen < 32 + 24) return null;

            // Find End Of Central Directory record (scan the last 64KB + 22 bytes)
            int tailSize = (int) Math.min(fileLen, 0xFFFF + 22);
            byte[] tail = new byte[tailSize];
            raf.seek(fileLen - tailSize);
            raf.readFully(tail);
            int eocdPos = -1;
            for (int i = tail.length - 22; i >= 0; i--) {
                if (tail[i] == 0x50 && tail[i + 1] == 0x4B && tail[i + 2] == 0x05 && tail[i + 3] == 0x06) {
                    eocdPos = i;
                    break;
                }
            }
            if (eocdPos < 0) return null;
            long cdOffset = getUInt32(tail, eocdPos + 16);
            if (cdOffset < 32 + 8) return null;

            // The signing block ends with: size(uint64) + magic(16) right before the Central Directory
            byte[] footer = new byte[24];
            raf.seek(cdOffset - 24);
            raf.readFully(footer);
            for (int i = 0; i < 16; i++)
                if (footer[8 + i] != APK_SIG_BLOCK_MAGIC[i]) return null;
            long blockSize = getUInt64(footer, 0);
            long blockStart = cdOffset - blockSize - 8;
            if (blockStart < 0) return null;

            byte[] pairs = new byte[(int) (blockSize - 24)];
            raf.seek(blockStart + 8);
            raf.readFully(pairs);

            byte[] v2 = null, v3 = null, v31 = null;
            int pos = 0;
            while (pos + 12 <= pairs.length) {
                long pairLen = getUInt64(pairs, pos);
                if (pos + 8 + pairLen > pairs.length) break;
                int id = (int) getUInt32(pairs, pos + 8);
                if (id == V2_BLOCK_ID && v2 == null)
                    v2 = copyRange(pairs, pos + 12, (int) (pairLen - 4));
                else if (id == V3_BLOCK_ID && v3 == null)
                    v3 = copyRange(pairs, pos + 12, (int) (pairLen - 4));
                else if (id == V31_BLOCK_ID && v31 == null)
                    v31 = copyRange(pairs, pos + 12, (int) (pairLen - 4));
                pos += 8 + (int) pairLen;
            }
            byte[] blockValue = v3 != null ? v3 : v31 != null ? v31 : v2;
            if (blockValue == null) return null;
            return firstCertificateFromSignerSequence(blockValue);
        }
    }

    /**
     * v2/v3 signer value = length-prefixed sequence of length-prefixed signers; each signer starts
     * with length-prefixed signed-data containing digests then certificates sequences.
     */
    private static byte[] firstCertificateFromSignerSequence(byte[] value) {
        if (value.length < 8) return null;
        int signersSeqLen = getInt32(value, 0);
        int pos = 4;
        if (signersSeqLen <= 0 || pos + 4 > value.length) return null;
        int signerLen = getInt32(value, pos);
        pos += 4;
        if (signerLen <= 0 || pos + signerLen > value.length) return null;

        int signedDataLen = getInt32(value, pos);
        int signedDataStart = pos + 4;
        if (signedDataLen <= 0 || signedDataStart + signedDataLen > value.length) return null;

        int p = signedDataStart;
        int digestsLen = getInt32(value, p);
        p += 4 + digestsLen;
        if (p + 4 > value.length) return null;
        int certsLen = getInt32(value, p);
        p += 4;
        if (certsLen <= 0 || p + 4 > value.length) return null;
        int firstCertLen = getInt32(value, p);
        p += 4;
        if (firstCertLen <= 0 || p + firstCertLen > value.length) return null;
        return copyRange(value, p, firstCertLen);
    }

    private static byte[] copyRange(byte[] src, int off, int len) {
        byte[] out = new byte[len];
        System.arraycopy(src, off, out, 0, len);
        return out;
    }

    private static int getInt32(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }

    private static long getUInt32(byte[] b, int off) {
        return getInt32(b, off) & 0xFFFFFFFFL;
    }

    private static long getUInt64(byte[] b, int off) {
        return (getUInt32(b, off) & 0xFFFFFFFFL) | (getUInt32(b, off + 4) << 32);
    }

    public static List<X509Certificate> getCertificates(File apk) throws IOException, ApkFormatException, NoSuchAlgorithmException {
        return new ApkVerifier.Builder(apk).build().verify().getSignerCertificates();
    }

    public static String getFingerprint(X509Certificate cert, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return formatHex(md.digest(cert.getEncoded()));
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            return "";
        }
    }

    public static String getSha1(X509Certificate cert) {
        return getFingerprint(cert, "SHA-1");
    }

    public static String getSha256(X509Certificate cert) {
        return getFingerprint(cert, "SHA-256");
    }

    public static CharSequence describe(X509Certificate cert) {
        if (cert == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: ").append(cert.getSubjectX500Principal().getName()).append('\n');
        sb.append("Issuer: ").append(cert.getIssuerX500Principal().getName()).append('\n');
        sb.append("Serial: ").append(cert.getSerialNumber().toString(16)).append('\n');
        sb.append("Valid from: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(cert.getNotBefore())).append('\n');
        sb.append("Valid to: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(cert.getNotAfter())).append('\n');
        sb.append("Signature algo: ").append(cert.getSigAlgName()).append('\n');
        sb.append("SHA-1: ").append(getSha1(cert)).append('\n');
        sb.append("SHA-256: ").append(getSha256(cert));
        return sb;
    }

    public static String formatHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            sb.append(Character.forDigit(b >> 4, 16)).append(Character.forDigit(b & 0xF, 16));
            if (i < bytes.length - 1) sb.append(':');
        }
        return sb.toString().toUpperCase();
    }
}

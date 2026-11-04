package io.github.abdurazaaqmohammed.utils;

import com.android.apksig.ApkVerifier;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;

/**
 * Everything the UI needs to describe an APK's signature: which schemes are
 * present, whether they actually verify, and the signer's certificate details.
 *
 * <p>The important distinction this adds over "look for META-INF/MANIFEST.MF" is
 * between <em>unsigned</em> and <em>signed but broken</em>. An APK can carry a v2
 * or v3 signing block and no JAR manifest at all, so the manifest test alone
 * reports those as "Not signed" even though a (possibly forged or corrupted)
 * signature is present. A detected signing block or META-INF certificate means
 * the APK is signed, so a failed verification must be reported as such.
 */
public final class ApkSignatureInfo {

    public static final int STATUS_VERIFIED = 0;
    public static final int STATUS_INVALID = 1;
    public static final int STATUS_NOT_SIGNED = 2;
    public static final int STATUS_UNKNOWN = 3;

    private final File apk;
    private final int status;
    private final String schemes;
    private final List<String> errors;
    private final List<X509Certificate> certificates;
    private final boolean hasSigningBlock;

    private ApkSignatureInfo(File apk, int status, String schemes, List<String> errors,
                             List<X509Certificate> certificates, boolean hasSigningBlock) {
        this.apk = apk;
        this.status = status;
        this.schemes = schemes;
        this.errors = errors == null ? Collections.emptyList() : errors;
        this.certificates = certificates == null ? Collections.emptyList() : certificates;
        this.hasSigningBlock = hasSigningBlock;
    }

    public File getApk() {
        return apk;
    }

    public int getStatus() {
        return status;
    }

    public boolean isVerified() {
        return status == STATUS_VERIFIED;
    }

    /** Comma-separated scheme list such as "V1, V2, V3", never empty. */
    public String getSchemes() {
        return schemes;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<X509Certificate> getCertificates() {
        return certificates;
    }

    public X509Certificate getPrimaryCertificate() {
        return certificates.isEmpty() ? null : certificates.get(0);
    }

    /** True when the archive carries an APK signing block or a META-INF certificate. */
    public boolean hasSigningMaterial() {
        return hasSigningBlock;
    }

    /** Short label for the APK info dialog's signature row. */
    public String getStatusLabel() {
        switch (status) {
            case STATUS_VERIFIED:
                return schemes;
            case STATUS_INVALID:
                return schemes.isEmpty() ? "Verification failed" : schemes + " (verification failed)";
            case STATUS_NOT_SIGNED:
                return "Not signed";
            default:
                return "Unknown";
        }
    }

    private static final java.util.Map<String, ApkSignatureInfo> sigInfoCache = new java.util.concurrent.ConcurrentHashMap<>();

    public static ApkSignatureInfo getCached(File apk) {
        if (apk == null) return null;
        try {
            String key = apk.getAbsolutePath() + ":" + apk.length() + ":" + apk.lastModified();
            return sigInfoCache.get(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ApkSignatureInfo analyze(File apk) {
        if (apk == null) return null;
        String cacheKey = null;
        try {
            cacheKey = apk.getAbsolutePath() + ":" + apk.length() + ":" + apk.lastModified();
            ApkSignatureInfo cached = sigInfoCache.get(cacheKey);
            if (cached != null) return cached;
        } catch (Exception ignored) {
        }
        List<X509Certificate> certs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        boolean v1 = false, v2 = false, v3 = false, v4 = false;
        boolean verified = false;
        boolean anyScheme = false;
        boolean structural = hasSignatureMaterial(apk);
        int status = STATUS_UNKNOWN;
        try {
            ApkVerifier.Result result = new ApkVerifier.Builder(apk).build().verify();
            try {
                List<X509Certificate> signers = result.getSignerCertificates();
                if (signers != null) certs.addAll(signers);
            } catch (Exception ignored) {
            }
            v1 = safe(result::isVerifiedUsingV1Scheme);
            v2 = safe(result::isVerifiedUsingV2Scheme);
            v3 = safe(result::isVerifiedUsingV3Scheme) || safe(result::isVerifiedUsingV31Scheme);
            v4 = safe(result::isVerifiedUsingV4Scheme);
            anyScheme = v1 || v2 || v3 || v4;
            verified = result.isVerified();
            try {
                if (result.containsErrors()) {
                    for (Object issue : result.getErrors()) errors.add(String.valueOf(issue));
                }
            } catch (Exception ignored) {
            }
            if (certs.isEmpty()) {
                try {
                    List<X509Certificate> unverified = CertUtil.getCertificatesUnverified(apk);
                    if (unverified != null) certs.addAll(unverified);
                } catch (Exception ignored) {
                }
            }
            if (verified && anyScheme) {
                status = STATUS_VERIFIED;
            } else if (structural || anyScheme || !certs.isEmpty()) {
                // A signature is present but did not verify (or one of the
                // schemes did not). Saying "Not signed" here was the bug.
                status = STATUS_INVALID;
            } else {
                status = STATUS_NOT_SIGNED;
            }
        } catch (Throwable t) {
            if (structural) status = STATUS_INVALID;
            else if (!certs.isEmpty()) status = STATUS_INVALID;
            else status = STATUS_UNKNOWN;
            errors.add(t.getMessage() == null ? t.toString() : t.getMessage());
        }
        if (!verified || !anyScheme) {
            // Detect schemes structurally (like MT Manager) when verification fails or returns no verified schemes
            if (!v1) v1 = hasV1Signature(apk);
            boolean[] blockSchemes = detectSigningBlockSchemes(apk);
            if (!v2 && blockSchemes[0]) v2 = true;
            if (!v3 && blockSchemes[1]) v3 = true;
            if (!v4 && blockSchemes[2]) v4 = true;
            if (v1 || v2 || v3 || v4) anyScheme = true;
        }
        StringBuilder schemes = new StringBuilder();
        if (v1) schemes.append("V1");
        if (v2) schemes.append(schemes.length() == 0 ? "V2" : ", V2");
        if (v3) schemes.append(schemes.length() == 0 ? "V3" : ", V3");
        if (v4) schemes.append(schemes.length() == 0 ? "V4" : ", V4");
        ApkSignatureInfo info = new ApkSignatureInfo(apk, status, schemes.toString(), errors, certs, structural);
        if (cacheKey != null) {
            sigInfoCache.put(cacheKey, info);
        }
        return info;
    }

    private static boolean safe(BooleanSupplier supplier) {
        try {
            return Boolean.TRUE.equals(supplier.getAsBoolean());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasV1Signature(File apk) {
        if (apk == null || !apk.isFile()) return false;
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader header : zf.getFileHeaders()) {
                String name = header.getFileName();
                if (name == null) continue;
                String upper = name.toUpperCase(Locale.US);
                if (upper.startsWith("META-INF/") && (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC"))) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** Returns boolean[]{hasV2, hasV3, hasV4} detected from APK signing block */
    public static boolean[] detectSigningBlockSchemes(File apk) {
        boolean[] res = new boolean[3]; // v2, v3, v4
        if (apk == null || !apk.isFile()) return res;
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(apk, "r")) {
            long fileLen = raf.length();
            if (fileLen < 32 + 24) return res;

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
            if (eocdPos < 0) return res;
            long cdOffset = (tail[eocdPos + 16] & 0xFFL)
                    | ((tail[eocdPos + 17] & 0xFFL) << 8)
                    | ((tail[eocdPos + 18] & 0xFFL) << 16)
                    | ((tail[eocdPos + 19] & 0xFFL) << 24);
            if (cdOffset < 32 + 8) return res;

            byte[] footer = new byte[24];
            raf.seek(cdOffset - 24);
            raf.readFully(footer);
            byte[] magic = "APK Sig Block 42".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            for (int i = 0; i < 16; i++) {
                if (footer[8 + i] != magic[i]) return res;
            }
            long blockSize = 0;
            for (int i = 0; i < 8; i++) blockSize |= ((long) (footer[i] & 0xFF)) << (8 * i);
            long blockStart = cdOffset - blockSize - 8;
            if (blockStart < 0) return res;

            byte[] pairs = new byte[(int) (blockSize - 24)];
            raf.seek(blockStart + 8);
            raf.readFully(pairs);

            int pos = 0;
            while (pos + 12 <= pairs.length) {
                long pairLen = 0;
                for (int i = 0; i < 8; i++) pairLen |= ((long) (pairs[pos + i] & 0xFF)) << (8 * i);
                if (pos + 8 + pairLen > pairs.length) break;
                long idLong = (pairs[pos + 8] & 0xFFL)
                        | ((pairs[pos + 9] & 0xFFL) << 8)
                        | ((pairs[pos + 10] & 0xFFL) << 16)
                        | ((pairs[pos + 11] & 0xFFL) << 24);
                int id = (int) idLong;
                if (id == 0x7109871a) res[0] = true; // V2
                else if (id == 0xf05368c0 || id == 0x1b93ad61) res[1] = true; // V3 or V3.1
                else if (id == 0x2b09189e) res[2] = true; // V4
                pos += 8 + (int) pairLen;
            }
        } catch (Exception ignored) {}
        return res;
    }

    /**
     * Structural presence check that does not depend on verification: an APK
     * signing block, or any META-INF certificate/signature file.
     */
    public static boolean hasSignatureMaterial(File apk) {
        if (apk == null || !apk.isFile()) return false;
        try (ZipFile zf = new ZipFile(apk)) {
            for (FileHeader header : zf.getFileHeaders()) {
                String name = header.getFileName();
                if (name == null) continue;
                String upper = name.toUpperCase(Locale.US);
                if (!upper.startsWith("META-INF/")) continue;
                if (upper.endsWith(".RSA") || upper.endsWith(".DSA") || upper.endsWith(".EC")
                        || upper.endsWith(".SF") || upper.equals("META-INF/MANIFEST.MF")) return true;
            }
        } catch (Exception ignored) {
        }
        // A v2/v3-only APK has no META-INF certificate at all, so the signing
        // block itself has to be probed too.
        try {
            byte[] der = CertUtil.extractCertFromSigningBlock(apk);
            return der != null && der.length > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /** MD5 of the certificate's encoded form, uppercase hex without separators. */
    public static String md5Of(X509Certificate cert) {
        return digest(cert, "MD5");
    }

    public static String sha1Of(X509Certificate cert) {
        return digest(cert, "SHA-1");
    }

    public static String sha256Of(X509Certificate cert) {
        return digest(cert, "SHA-256");
    }

    private static String digest(X509Certificate cert, String algorithm) {
        if (cert == null) return "";
        try {
            return CertUtil.formatHex(MessageDigest.getInstance(algorithm).digest(cert.getEncoded()))
                    .replace(":", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** CRC32 (signed decimal plus unsigned hex) of a certificate, for display. */
    public static String crc32Of(X509Certificate cert) {
        if (cert == null) return "";
        CRC32 crc = new CRC32();
        try {
            crc.update(cert.getEncoded());
        } catch (Exception e) {
            return "";
        }
        long value = crc.getValue();
        return String.format(Locale.US, "0x%08X (%d)", value, (int) value);
    }
}
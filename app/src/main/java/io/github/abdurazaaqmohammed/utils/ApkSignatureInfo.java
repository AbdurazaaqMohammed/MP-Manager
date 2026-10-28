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

    public static ApkSignatureInfo analyze(File apk) {
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
        StringBuilder schemes = new StringBuilder();
        if (v1) schemes.append("V1");
        if (v2) schemes.append(schemes.length() == 0 ? "V2" : ", V2");
        if (v3) schemes.append(schemes.length() == 0 ? "V3" : ", V3");
        if (v4) schemes.append(schemes.length() == 0 ? "V4" : ", V4");
        return new ApkSignatureInfo(apk, status, schemes.toString(), errors, certs, structural);
    }

    private static boolean safe(BooleanSupplier supplier) {
        try {
            return Boolean.TRUE.equals(supplier.getAsBoolean());
        } catch (Throwable ignored) {
            return false;
        }
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
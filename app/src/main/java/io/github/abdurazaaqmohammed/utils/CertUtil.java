package io.github.abdurazaaqmohammed.utils;

import com.android.apksig.ApkVerifier;
import com.android.apksig.apk.ApkFormatException;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.List;

public class CertUtil {

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

    public static String describe(X509Certificate cert) {
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
        return sb.toString();
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

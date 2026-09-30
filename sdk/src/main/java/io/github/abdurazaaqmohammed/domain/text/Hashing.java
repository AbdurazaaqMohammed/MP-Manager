package io.github.abdurazaaqmohammed.domain.text;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Hash helpers extracted from ToolRunnerActivity.hashString().
 */
public final class Hashing {

    private Hashing() {
    }

    public static String hex(String s, String algo) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algo);
        byte[] bytes = digest.digest(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String h = Integer.toHexString(0xFF & b);
            if (h.length() == 1) out.append('0');
            out.append(h);
        }
        return out.toString();
    }

    public static String md5(String s) throws Exception {
        return hex(s, "MD5");
    }

    public static String sha1(String s) throws Exception {
        return hex(s, "SHA-1");
    }

    public static String sha256(String s) throws Exception {
        return hex(s, "SHA-256");
    }

    public static String sha512(String s) throws Exception {
        return hex(s, "SHA-512");
    }
}

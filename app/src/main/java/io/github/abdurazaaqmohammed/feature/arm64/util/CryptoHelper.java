package io.github.abdurazaaqmohammed.feature.arm64.util;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Lightweight in-app cryptographic & decoding helper for reverse engineering:
 * Supports XOR, RC4, Base64, Hex, and ROT13 transformations, plus MD5, SHA-1, SHA-256 digests.
 */
public final class CryptoHelper {

    private CryptoHelper() {}

    public static byte[] xor(byte[] data, byte[] key) {
        if (data == null || key == null || key.length == 0) return data;
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return out;
    }

    public static byte[] rc4(byte[] data, byte[] key) {
        if (data == null || key == null || key.length == 0) return data;
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) {
            s[i] = i;
        }
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            int tmp = s[i];
            s[i] = s[j];
            s[j] = tmp;
        }
        int i = 0;
        j = 0;
        byte[] out = new byte[data.length];
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) & 0xFF;
            j = (j + s[i]) & 0xFF;
            int tmp = s[i];
            s[i] = s[j];
            s[j] = tmp;
            int t = (s[i] + s[j]) & 0xFF;
            out[k] = (byte) (data[k] ^ s[t]);
        }
        return out;
    }

    public static String rot13(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            if (c >= 'a' && c <= 'm') c += 13;
            else if (c >= 'A' && c <= 'M') c += 13;
            else if (c >= 'n' && c <= 'z') c -= 13;
            else if (c >= 'N' && c <= 'Z') c -= 13;
            sb.append(c);
        }
        return sb.toString();
    }

    public static byte[] parseInput(String input, String format) {
        if (input == null) return new byte[0];
        if ("Base64".equalsIgnoreCase(format)) {
            try {
                return Base64.decode(input.trim(), Base64.DEFAULT);
            } catch (Exception ignored) {
                return input.getBytes(StandardCharsets.UTF_8);
            }
        } else if ("Hex".equalsIgnoreCase(format)) {
            return hexToBytes(input.replaceAll("\\s+", ""));
        } else {
            return input.getBytes(StandardCharsets.UTF_8);
        }
    }

    public static String formatOutput(byte[] data, String format) {
        if (data == null) return "";
        if ("Base64".equalsIgnoreCase(format)) {
            return Base64.encodeToString(data, Base64.NO_WRAP);
        } else if ("Hex".equalsIgnoreCase(format)) {
            return bytesToHex(data);
        } else {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        if (len % 2 != 0) {
            hex = "0" + hex;
            len++;
        }
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            try {
                out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            } catch (NumberFormatException e) {
                out[i / 2] = 0;
            }
        }
        return out;
    }

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String hash(byte[] data, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(data);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return "N/A";
        }
    }

    public static String process(String algorithm, boolean isDecrypt, String inputData,
                                  String inputFormat, String outputFormat, String key, String keyFormat) {
        if (inputData == null || inputData.isEmpty()) return "";
        if ("ROT13".equalsIgnoreCase(algorithm)) {
            return rot13(inputData);
        }
        byte[] dataBytes = parseInput(inputData, inputFormat);
        byte[] keyBytes = (key != null && !key.isEmpty()) ? parseInput(key, keyFormat) : new byte[0];

        byte[] resultBytes;
        if ("XOR".equalsIgnoreCase(algorithm)) {
            resultBytes = xor(dataBytes, keyBytes.length > 0 ? keyBytes : new byte[]{0x5A});
        } else if ("RC4".equalsIgnoreCase(algorithm)) {
            resultBytes = rc4(dataBytes, keyBytes.length > 0 ? keyBytes : new byte[]{0x01, 0x02, 0x03});
        } else {
            resultBytes = dataBytes;
        }

        return formatOutput(resultBytes, outputFormat);
    }
}

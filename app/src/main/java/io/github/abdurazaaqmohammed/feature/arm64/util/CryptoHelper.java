package io.github.abdurazaaqmohammed.feature.arm64.util;

import android.util.Base64;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Lightweight in-app cryptographic & decoding helper for reverse engineering:
 * Supports AES (ECB/CBC), DES (ECB/CBC), 3DES, XOR, RC4, Base64, Hex, and ROT13 transformations,
 * multiple charsets, and MD5, SHA-1, SHA-256 digests.
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

    public static byte[] aes(byte[] data, byte[] key, byte[] iv, boolean isDecrypt, boolean isCbc) throws Exception {
        byte[] validKey = normalizeKey(key, 16, 24, 32);
        SecretKeySpec keySpec = new SecretKeySpec(validKey, "AES");
        Cipher cipher;
        if (isCbc) {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] validIv = normalizeIv(iv, 16);
            cipher.init(isDecrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(validIv));
        } else {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(isDecrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, keySpec);
        }
        return cipher.doFinal(data);
    }

    public static byte[] des(byte[] data, byte[] key, byte[] iv, boolean isDecrypt, boolean isCbc) throws Exception {
        byte[] validKey = normalizeKey(key, 8, 8, 8);
        SecretKeySpec keySpec = new SecretKeySpec(validKey, "DES");
        Cipher cipher;
        if (isCbc) {
            cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
            byte[] validIv = normalizeIv(iv, 8);
            cipher.init(isDecrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(validIv));
        } else {
            cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(isDecrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, keySpec);
        }
        return cipher.doFinal(data);
    }

    public static byte[] desede(byte[] data, byte[] key, byte[] iv, boolean isDecrypt, boolean isCbc) throws Exception {
        byte[] validKey = normalizeKey(key, 24, 24, 24);
        SecretKeySpec keySpec = new SecretKeySpec(validKey, "DESede");
        Cipher cipher;
        if (isCbc) {
            cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
            byte[] validIv = normalizeIv(iv, 8);
            cipher.init(isDecrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(validIv));
        } else {
            cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(isDecrypt ? Cipher.DECRYPT_MODE : Cipher.ENCRYPT_MODE, keySpec);
        }
        return cipher.doFinal(data);
    }

    private static byte[] normalizeKey(byte[] key, int len1, int len2, int len3) {
        if (key == null || key.length == 0) {
            byte[] def = new byte[len1];
            Arrays.fill(def, (byte) 0);
            return def;
        }
        if (key.length == len1 || key.length == len2 || key.length == len3) {
            return key;
        }
        int targetLen = len1;
        if (key.length > len2) targetLen = len3;
        else if (key.length > len1) targetLen = len2;
        byte[] out = new byte[targetLen];
        System.arraycopy(key, 0, out, 0, Math.min(key.length, targetLen));
        return out;
    }

    private static byte[] normalizeIv(byte[] iv, int len) {
        if (iv == null || iv.length == 0) {
            return new byte[len];
        }
        if (iv.length == len) return iv;
        byte[] out = new byte[len];
        System.arraycopy(iv, 0, out, 0, Math.min(iv.length, len));
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

    public static Charset resolveCharset(String charsetName) {
        if (charsetName == null || charsetName.isEmpty()) return StandardCharsets.UTF_8;
        try {
            return Charset.forName(charsetName.trim());
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    public static byte[] parseInput(String input, String format, Charset charset) {
        if (input == null || input.isEmpty()) return new byte[0];
        if (charset == null) charset = StandardCharsets.UTF_8;
        if ("Base64".equalsIgnoreCase(format)) {
            try {
                return Base64.decode(input.trim(), Base64.DEFAULT);
            } catch (Exception ignored) {
                return input.getBytes(charset);
            }
        } else if ("Hex".equalsIgnoreCase(format)) {
            return hexToBytes(input.replaceAll("\\s+", ""));
        } else {
            return input.getBytes(charset);
        }
    }

    public static String formatOutput(byte[] data, String format, Charset charset) {
        if (data == null || data.length == 0) return "";
        if (charset == null) charset = StandardCharsets.UTF_8;
        if ("Base64".equalsIgnoreCase(format)) {
            return Base64.encodeToString(data, Base64.NO_WRAP);
        } else if ("Hex".equalsIgnoreCase(format)) {
            return bytesToHex(data);
        } else {
            return new String(data, charset);
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
                                  String inputFormat, String outputFormat,
                                  String key, String keyFormat,
                                  String iv, String ivFormat,
                                  String charsetName) {
        if (inputData == null || inputData.isEmpty()) return "";
        Charset cs = resolveCharset(charsetName);

        if ("ROT13".equalsIgnoreCase(algorithm)) {
            return rot13(inputData);
        }

        byte[] dataBytes = parseInput(inputData, inputFormat, cs);
        if (dataBytes.length == 0 && !inputData.isEmpty()) {
            return "Error: Failed to parse input as " + inputFormat;
        }

        byte[] keyBytes = (key != null && !key.isEmpty()) ? parseInput(key, keyFormat, cs) : new byte[0];
        byte[] ivBytes = (iv != null && !iv.isEmpty()) ? parseInput(iv, ivFormat, cs) : new byte[0];

        try {
            byte[] resultBytes;
            String algo = algorithm != null ? algorithm.toUpperCase(Locale.US).trim() : "XOR";

            if (algo.startsWith("XOR")) {
                byte[] effectiveKey = keyBytes.length > 0 ? keyBytes : new byte[]{0x5A};
                resultBytes = xor(dataBytes, effectiveKey);
            } else if (algo.startsWith("RC4")) {
                byte[] effectiveKey = keyBytes.length > 0 ? keyBytes : new byte[]{0x01, 0x02, 0x03};
                resultBytes = rc4(dataBytes, effectiveKey);
            } else if (algo.contains("AES")) {
                boolean isCbc = algo.contains("CBC");
                resultBytes = aes(dataBytes, keyBytes, ivBytes, isDecrypt, isCbc);
            } else if (algo.contains("3DES") || algo.contains("TRIPLE")) {
                boolean isCbc = algo.contains("CBC");
                resultBytes = desede(dataBytes, keyBytes, ivBytes, isDecrypt, isCbc);
            } else if (algo.contains("DES")) {
                boolean isCbc = algo.contains("CBC");
                resultBytes = des(dataBytes, keyBytes, ivBytes, isDecrypt, isCbc);
            } else if (algo.equals("BASE64")) {
                if (isDecrypt) {
                    resultBytes = Base64.decode(inputData.trim(), Base64.DEFAULT);
                } else {
                    resultBytes = dataBytes;
                    outputFormat = "Base64";
                }
            } else if (algo.equals("HEX")) {
                if (isDecrypt) {
                    resultBytes = hexToBytes(inputData.replaceAll("\\s+", ""));
                } else {
                    resultBytes = dataBytes;
                    outputFormat = "Hex";
                }
            } else {
                resultBytes = dataBytes;
            }

            return formatOutput(resultBytes, outputFormat, cs);
        } catch (Exception e) {
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}

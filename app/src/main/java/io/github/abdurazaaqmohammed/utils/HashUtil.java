package io.github.abdurazaaqmohammed.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

public class HashUtil {

    public static final String[] ALGORITHMS = { "MD5", "SHA-1", "SHA-256", "SHA-512", "CRC32" };

    public static final String[] CHECKSUM_EXTS = { "md5", "sha1", "sha256", "sha512", "sfv", "crc32" };

    private static final Pattern HEX_PATTERN = Pattern.compile("[0-9a-fA-F]+");

    public static boolean isChecksumFile(String fileName) {
        return matchExt(getExtension(fileName));
    }

    public static String getExtension(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i >= 0 ? fileName.substring(i + 1).toLowerCase(Locale.US) : "";
    }

    public static boolean matchExt(String ext) {
        for (String e : CHECKSUM_EXTS) if (e.equals(ext)) return true;
        return false;
    }

    public static java.util.Map<String, String> hashAll(File file) throws Exception {
        java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
        java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
        java.security.MessageDigest sha512 = java.security.MessageDigest.getInstance("SHA-512");
        CRC32 crc = new CRC32();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 8192)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md5.update(buffer, 0, read);
                sha1.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
                sha512.update(buffer, 0, read);
                crc.update(buffer, 0, read);
            }
        }
        java.util.Map<String, String> result = new java.util.LinkedHashMap<>();
        result.put("MD5", toHex(md5.digest()));
        result.put("SHA-1", toHex(sha1.digest()));
        result.put("SHA-256", toHex(sha256.digest()));
        result.put("SHA-512", toHex(sha512.digest()));
        result.put("CRC32", crc32Hex(crc.getValue()));
        return result;
    }

    public static String hashFile(File file, String algorithm) throws Exception {
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 8192)) {
            return hashStream(in, algorithm);
        }
    }

    public static String hashStream(InputStream in, String algorithm) throws Exception {
        if ("CRC32".equalsIgnoreCase(algorithm)) {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) crc.update(buffer, 0, read);
            return String.format(Locale.US, "%08x", crc.getValue());
        }
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) digest.update(buffer, 0, read);
        return toHex(digest.digest());
    }

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.US, "%02x", b & 0xff));
        return sb.toString();
    }

    public static String crc32Hex(long value) {
        return String.format(Locale.US, "%08x", value);
    }

    public static String algorithmForChecksumFile(String fileName) {
        String ext = getExtension(fileName);
        switch (ext) {
            case "md5": return "MD5";
            case "sha1": return "SHA-1";
            case "sha256": return "SHA-256";
            case "sha512": return "SHA-512";
            case "sfv":
            case "crc32": return "CRC32";
            default: return null;
        }
    }

    private static int hashLength(String algorithm) {
        if (algorithm == null) return 0;
        switch (algorithm) {
            case "MD5": return 32;
            case "SHA-1": return 40;
            case "SHA-256": return 64;
            case "SHA-512": return 128;
            case "CRC32": return 8;
            default: return 0;
        }
    }

    public static List<CheckResult> verifyChecksumFile(File checksumFile) throws IOException {
        String algorithm = algorithmForChecksumFile(checksumFile.getName());
        List<CheckResult> results = new ArrayList<>();
        if (algorithm == null) return results;
        File baseDir = checksumFile.getParentFile();
        int expectedLen = hashLength(algorithm);

        try (BufferedReader br = new BufferedReader(new FileReader(checksumFile))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;

                String expected;
                String fileName;
                if ("CRC32".equals(algorithm)) {
                    expected = parts[parts.length - 1].toLowerCase(Locale.US);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (sb.length() > 0) sb.append(' ');
                        sb.append(parts[i]);
                    }
                    fileName = sb.toString();
                } else {
                    String maybe = parts[0];
                    if (isHex(maybe) && maybe.length() == expectedLen) {
                        expected = maybe.toLowerCase(Locale.US);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i < parts.length; i++) {
                            if (sb.length() > 0) sb.append(' ');
                            sb.append(parts[i]);
                        }
                        fileName = sb.toString();
                    } else continue;
                }

                while (fileName.startsWith("*") || fileName.startsWith(" ")) fileName = fileName.substring(1).trim();
                if (fileName.isEmpty()) continue;

                File target = new File(baseDir, fileName);
                CheckResult result = new CheckResult();
                result.fileName = fileName;
                result.expected = expected;
                result.algorithm = algorithm;
                result.line = lineNo;
                try {
                    result.actual = hashFile(target, algorithm);
                    result.valid = expected.equalsIgnoreCase(result.actual);
                    if (!result.valid) result.error = "Hash mismatch";
                } catch (Exception e) {
                    result.valid = false;
                    result.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                }
                results.add(result);
            }
        }
        return results;
    }

    private static boolean isHex(String s) {
        return HEX_PATTERN.matcher(s).matches();
    }

    public static class CheckResult {
        public String fileName;
        public String expected;
        public String actual;
        public String algorithm;
        public boolean valid;
        public String error;
        public int line;
    }
}

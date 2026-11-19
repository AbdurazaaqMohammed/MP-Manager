package io.github.abdurazaaqmohammed.util.hash;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
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

    public static Map<String, String> hashAll(File file) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        MessageDigest sha512 = MessageDigest.getInstance("SHA-512");
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
        Map<String, String> result = new LinkedHashMap<>();
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

    public static String hashApkOrArchiveBase(File file, String algorithm) {
        if (file == null || !file.exists()) return null;
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".apkm") || name.endsWith(".aspk") || name.endsWith(".zip")) {
            try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(file)) {
                java.util.zip.ZipEntry baseEntry = null;
                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    java.util.zip.ZipEntry ze = entries.nextElement();
                    String en = ze.getName().toLowerCase(Locale.ROOT);
                    if (en.equals("base.apk") || en.endsWith("/base.apk") || en.equals("base-master.apk") || en.endsWith("/base-master.apk") || en.equals("master.apk") || en.equals("main.apk")) {
                        baseEntry = ze;
                        break;
                    }
                }
                if (baseEntry == null) {
                    entries = zf.entries();
                    while (entries.hasMoreElements()) {
                        java.util.zip.ZipEntry ze = entries.nextElement();
                        if (ze.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                            baseEntry = ze;
                            break;
                        }
                    }
                }
                if (baseEntry != null) {
                    try (InputStream is = zf.getInputStream(baseEntry)) {
                        return hashStream(is, algorithm);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        try {
            return hashFile(file, algorithm);
        } catch (Exception e) {
            return null;
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
        return switch (ext) {
            case "md5" -> "MD5";
            case "sha1" -> "SHA-1";
            case "sha256" -> "SHA-256";
            case "sha512" -> "SHA-512";
            case "sfv", "crc32" -> "CRC32";
            default -> null;
        };
    }

    private static int hashLength(String algorithm) {
        if (algorithm == null) return 0;
        return switch (algorithm) {
            case "MD5" -> 32;
            case "SHA-1" -> 40;
            case "SHA-256" -> 64;
            case "SHA-512" -> 128;
            case "CRC32" -> 8;
            default -> 0;
        };
    }

    /** Opens the bytes of a manifest target given its sanitized relative name.
     *  Implementations map the name onto a plain file, a staged elevated copy
     *  or an archive sibling entry; the stream is closed by the verifier. */
    public interface EntryOpener {
        InputStream open(String relativeName) throws IOException;
    }

    /** One parsed manifest line. Rejected lines (unsafe or malformed names)
     *  are kept with {@link #rejected} set so the UI can show them. */
    public static class ManifestEntry {
        /** Sanitized relative name usable with a base directory; null when rejected. */
        public String name;
        /** Original name as written in the manifest, for display. */
        public String rawName;
        public String expected;
        public int line;
        public boolean rejected;
    }

    /**
     * Turns a manifest line into an entry, or null when the line does not
     * carry a plausible hash/name pair. Conventional formats only:
     * .sfv "name CRC" (CRC is the last whitespace token) and md5sum-style
     * "HASH  name" / BSD-style "ALG (name) = HASH" for the digest family.
     * Names are sanitized so verification can never escape the base
     * directory (no absolute paths, "..", drive/stream markers or
     * backslash separators); rejected names come back with
     * {@link ManifestEntry#rejected} set.
     */
    public static ManifestEntry parseManifestLine(String algorithm, String rawLine, int lineNo) {
        String line = rawLine;
        if (lineNo == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
        line = line.trim();
        if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) return null;
        int expectedLen = hashLength(algorithm);

        String expected;
        String fileName;
        if ("CRC32".equals(algorithm)) {
            // SFV puts the CRC in the last whitespace token; accept tabs too
            // (the old whitespace-split parser did).
            int sp = Math.max(line.lastIndexOf(' '), line.lastIndexOf('\t'));
            if (sp <= 0 || sp == line.length() - 1) return null;
            expected = line.substring(sp + 1).toLowerCase(Locale.US);
            if (!isHex(expected) || expected.length() != expectedLen) return null;
            fileName = line.substring(0, sp).trim();
        } else {
            // BSD style: "ALG (name) = HASH"
            Matcher m = BSD_LINE.matcher(line);
            if (m.matches()) {
                fileName = m.group(1).trim();
                expected = m.group(2).toLowerCase(Locale.US);
                if (!isHex(expected) || expected.length() != expectedLen) return null;
            } else {
                // md5sum style: "HASH[ *]name" — split at the first whitespace
                // so names containing spaces (and runs of spaces) survive.
                int sp = -1;
                for (int i = 0; i < line.length(); i++) {
                    if (Character.isWhitespace(line.charAt(i))) { sp = i; break; }
                }
                if (sp <= 0) return null;
                String maybe = line.substring(0, sp);
                if (!isHex(maybe) || maybe.length() != expectedLen) return null;
                expected = maybe.toLowerCase(Locale.US);
                fileName = line.substring(sp).trim();
            }
            if (fileName.startsWith("*")) fileName = fileName.substring(1).trim();
        }
        if (fileName.isEmpty()) return null;

        ManifestEntry entry = new ManifestEntry();
        entry.rawName = fileName;
        entry.expected = expected;
        entry.line = lineNo;
        entry.name = sanitizeEntryName(fileName);
        entry.rejected = entry.name == null;
        return entry;
    }

    /**
     * Normalizes a manifest name to a safe relative path: backslashes become
     * separators, leading "./" is stripped, and absolute paths, ".."/"."
     * segments, empty segments and Windows drive/stream markers are rejected
     * by returning null. With a null result the entry must never be opened.
     */
    public static String sanitizeEntryName(String raw) {
        if (raw == null) return null;
        String name = raw.replace('\\', '/').trim();
        if (name.isEmpty() || name.indexOf('\0') >= 0 || name.indexOf(':') >= 0) return null;
        while (name.startsWith("./")) name = name.substring(2);
        if (name.isEmpty() || name.charAt(0) == '/') return null;
        StringBuilder sb = new StringBuilder();
        for (String seg : name.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return null;
            if (sb.length() > 0) sb.append('/');
            sb.append(seg);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Parses all lines of a manifest stream (UTF-8; comments are ";" and "#"). */
    public static List<ManifestEntry> parseManifest(String algorithm, BufferedReader br) throws IOException {
        List<ManifestEntry> entries = new ArrayList<>();
        String line;
        int lineNo = 0;
        while ((line = br.readLine()) != null) {
            lineNo++;
            ManifestEntry e = parseManifestLine(algorithm, line, lineNo);
            if (e != null) entries.add(e);
        }
        return entries;
    }

    /**
     * Verifies parsed entries through the given opener. A rejected entry
     * yields an invalid result with {@link CheckResult#rejected} set and is
     * never opened; a missing/unreadable target yields {@link CheckResult#missing}.
     */
    public static List<CheckResult> verifyEntries(String algorithm, List<ManifestEntry> entries,
                                                  EntryOpener opener) {
        List<CheckResult> results = new ArrayList<>();
        for (ManifestEntry e : entries) {
            CheckResult r = new CheckResult();
            r.fileName = e.name != null ? e.name : e.rawName;
            r.expected = e.expected;
            r.algorithm = algorithm;
            r.line = e.line;
            r.rejected = e.rejected;
            if (e.rejected) {
                r.valid = false;
                results.add(r);
                continue;
            }
            try (InputStream in = opener.open(e.name)) {
                r.actual = hashStream(in, algorithm);
                r.valid = e.expected.equalsIgnoreCase(r.actual);
            } catch (FileNotFoundException fnf) {
                r.valid = false;
                r.missing = true;
                r.error = fnf.getMessage();
            } catch (Exception ex) {
                r.valid = false;
                r.error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            }
            results.add(r);
        }
        return results;
    }

    /**
     * Verifies a manifest file against targets opened through {@code opener},
     * which receives the sanitized relative name and decides where the bytes
     * come from (plain file, staged elevated copy, archive sibling, ...).
     */
    public static List<CheckResult> verifyChecksumFile(File checksumFile,
                                                       EntryOpener opener) throws IOException {
        String algorithm = algorithmForChecksumFile(checksumFile.getName());
        List<CheckResult> results = new ArrayList<>();
        if (algorithm == null) return results;
        try (BufferedReader br = openManifestReader(new FileInputStream(checksumFile))) {
            return verifyEntries(algorithm, parseManifest(algorithm, br), opener);
        }
    }

    /**
     * Wraps manifest bytes in a reader, decoded as UTF-8. Manifests written by
     * Windows tools often use a legacy codepage, so bytes that are not valid
     * UTF-8 fall back to ISO-8859-1 (1:1 byte map) instead of silently turning
     * every name into U+FFFD garbage.
     */
    public static BufferedReader openManifestReader(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(8192);
        byte[] buf = new byte[8192];
        int n;
        try (InputStream source = in) {
            while ((n = source.read(buf)) != -1) bos.write(buf, 0, n);
        }
        byte[] bytes = bos.toByteArray();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            text = new String(bytes, StandardCharsets.ISO_8859_1);
        }
        return new BufferedReader(new StringReader(text));
    }

    /** Convenience: verify targets that sit next to the manifest, plain reads. */
    public static List<CheckResult> verifyChecksumFile(File checksumFile) throws IOException {
        File baseDir = checksumFile.getParentFile();
        return verifyChecksumFile(checksumFile, rel -> new FileInputStream(new File(baseDir, rel)));
    }

    /** Filenames that round-trip through the conventional, unescaped manifest formats. */
    public static boolean canGenerateEntry(String name, boolean sfv) {
        if (name == null || name.isEmpty() || !name.equals(name.trim())
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0
                || name.indexOf('\\') >= 0 || name.indexOf('/') >= 0
                || !name.equals(sanitizeEntryName(name))) return false;
        return sfv ? !name.startsWith(";") && !name.startsWith("#") : !name.startsWith("*");
    }

    /** Generates conventional .sfv text ("name CRC", uppercase, CRLF, ";" header). */
    public static String generateSfv(List<File> files) throws Exception {
        StringBuilder sb = new StringBuilder("; Generated by MP-Manager\r\n");
        for (File f : files) {
            sb.append(f.getName()).append(' ')
                    .append(hashFile(f, "CRC32").toUpperCase(Locale.US)).append("\r\n");
        }
        return sb.toString();
    }

    /** Generates md5sum-style text ("HASH  name", lowercase, LF). */
    public static String generateMd5(List<File> files) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (File f : files) {
            sb.append(hashFile(f, "MD5")).append("  ").append(f.getName()).append('\n');
        }
        return sb.toString();
    }

    private static final Pattern BSD_LINE = Pattern.compile("^\\w+\\s*\\((.+)\\)\\s*=\\s*([0-9a-fA-F]+)\\s*$");

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
        /** Target could not be read at all (absent or unreadable). */
        public boolean missing;
        /** Entry name was unsafe (absolute/traversal) and was never opened. */
        public boolean rejected;
    }
}

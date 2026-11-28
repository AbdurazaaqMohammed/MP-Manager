package io.github.abdurazaaqmohammed.feature.crypto;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import net.lingala.zip4j.model.FileHeader;

/** Real content analysis for encoded, compressed and encrypted files. */
public final class FileCryptoEngine {

    public static final int MAX_SCAN_BYTES = 16 * 1024 * 1024;
    public static final int MAX_DECRYPT_BYTES = 64 * 1024 * 1024;
    private static final int MAX_ENTRIES = 5000;
    private static final int MAX_FILES = 3000;
    private static final int SAMPLE = 65536;

    private FileCryptoEngine() {}

    public enum Kind { TEXT, ENCODING, COMPRESSION, ENCRYPTION, ARCHIVE, BINARY, UNKNOWN }

    public interface Progress {
        void onProgress(String message, int done, int total);
    }

    public static final class Finding {
        public String path = "";
        public String name = "";
        public String container = "";
        public String type = "Unknown";
        public String encoding = "";
        public String cipher = "";
        public String status = "Unknown";
        public String detail = "";
        public String archivePath = "";
        public String entryName = "";
        public String textPreview = "";
        public Kind kind = Kind.UNKNOWN;
        public long size;
        public double entropy;
        public int confidence;
        public boolean encoded;
        public boolean compressed;
        public boolean encrypted;
        public boolean needsKey;
        public boolean zipEncrypted;
        public boolean tooLarge;

        public boolean interesting() {
            return encoded || compressed || encrypted || needsKey || zipEncrypted
                    || kind == Kind.ENCRYPTION || kind == Kind.ENCODING
                    || kind == Kind.COMPRESSION || kind == Kind.ARCHIVE;
        }

        public String shortLabel() {
            return name + "  ·  " + type + "  ·  " + status;
        }
    }

    public static final class Result {
        public boolean success;
        public boolean uncertain;
        public boolean needsKey;
        public String method = "";
        public String message = "";
        public String outputType = "";
        public byte[] data;
        public String text = "";

        public static Result fail(String message, boolean needsKey) {
            Result r = new Result();
            r.message = message;
            r.needsKey = needsKey;
            return r;
        }
    }

    // ---------------------------------------------------------------- scanning

    public static List<Finding> scan(File source, boolean includeAll, Progress progress) {
        List<Finding> out = new ArrayList<>();
        if (source == null || !source.exists()) return out;
        if (source.isDirectory()) {
            walk(source, 0, includeAll, out, progress);
        } else if (isZip(source)) {
            scanArchive(source, includeAll, out, progress);
        } else {
            Finding f = analyzeFile(source, "");
            if (f != null) out.add(f);
        }
        return out;
    }

    private static void walk(File dir, int depth, boolean includeAll,
                             List<Finding> out, Progress progress) {
        if (depth > 12 || out.size() >= MAX_FILES) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (out.size() >= MAX_FILES) return;
            if (child.isDirectory()) {
                walk(child, depth + 1, includeAll, out, progress);
            } else if (child.isFile()) {
                if (progress != null) progress.onProgress("Scanning " + child.getName(), out.size(), -1);
                if (isZip(child)) {
                    scanArchive(child, includeAll, out, progress);
                } else {
                    Finding f = analyzeFile(child, "");
                    if (f != null && (includeAll || f.interesting())) out.add(f);
                }
            }
        }
    }

    private static void scanArchive(File archive, boolean includeAll,
                                    List<Finding> out, Progress progress) {
        Map<String, Boolean> encrypted = new HashMap<>();
        try {
            net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(archive);
            for (FileHeader h : z.getFileHeaders()) {
                if (!h.isDirectory()) encrypted.put(h.getFileName(), h.isEncrypted());
            }
        } catch (Exception ignored) {
        }
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int count = 0;
            while (entries.hasMoreElements() && count++ < MAX_ENTRIES) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                boolean zipEnc = Boolean.TRUE.equals(encrypted.get(entryName));
                Finding f = new Finding();
                f.path = archive.getAbsolutePath() + "!" + entryName;
                f.name = baseName(entryName);
                f.entryName = entryName;
                f.archivePath = archive.getAbsolutePath();
                f.container = archive.getName();
                f.size = entry.getSize();
                f.zipEncrypted = zipEnc;
                if (zipEnc) {
                    f.kind = Kind.ENCRYPTION;
                    f.type = "Encrypted ZIP";
                    f.encrypted = true;
                    f.needsKey = true;
                    f.status = "Encrypted ZIP - Password required";
                    f.detail = "Encrypted compressed archive entry. Requires valid password.";
                    f.confidence = 100;
                    out.add(f);
                    continue;
                }
                if (entry.getSize() > MAX_SCAN_BYTES) {
                    f.tooLarge = true;
                    f.type = "Large entry";
                    f.status = "Exceeds scan limit";
                    f.detail = "Size: " + formatSize(entry.getSize()) + "; header inspected.";
                    if (includeAll) out.add(f);
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    byte[] data = readLimited(in, MAX_SCAN_BYTES);
                    Finding analyzed = analyzeBytes(data, f.name, f.path, f.container);
                    analyzed.archivePath = f.archivePath;
                    analyzed.entryName = entryName;
                    analyzed.size = entry.getSize();
                    if (includeAll || analyzed.interesting()) out.add(analyzed);
                } catch (Exception e) {
                    f.kind = Kind.ENCRYPTION;
                    f.type = "Unreadable entry";
                    f.status = "Failed to read entry";
                    f.detail = e.getMessage() == null ? e.toString() : e.getMessage();
                    f.encrypted = true;
                    f.needsKey = true;
                    out.add(f);
                }
                if (progress != null && count % 20 == 0) {
                    progress.onProgress("Scanning " + entryName, count, -1);
                }
            }
        } catch (Exception e) {
            Finding f = new Finding();
            f.path = archive.getAbsolutePath();
            f.name = archive.getName();
            f.type = "Archive";
            f.status = "Failed to open archive";
            f.detail = e.getMessage() == null ? e.toString() : e.getMessage();
            out.add(f);
        }
    }

    public static Finding analyzeFile(File file, String container) {
        if (file == null || !file.isFile()) return null;
        try {
            long size = file.length();
            byte[] data;
            if (size > MAX_SCAN_BYTES) {
                try (InputStream in = new FileInputStream(file)) {
                    data = readLimited(in, MAX_SCAN_BYTES);
                }
            } else {
                try (InputStream in = new FileInputStream(file)) {
                    data = readLimited(in, MAX_SCAN_BYTES);
                }
            }
            Finding f = analyzeBytes(data, file.getName(), file.getAbsolutePath(), container);
            f.size = size;
            f.tooLarge = size > MAX_SCAN_BYTES;
            if (f.tooLarge) f.detail += "\nInspected first " + formatSize(MAX_SCAN_BYTES)
                    + " of " + formatSize(size) + ".";
            return f;
        } catch (Exception e) {
            Finding f = new Finding();
            f.path = file.getAbsolutePath();
            f.name = file.getName();
            f.status = "Scan failed";
            f.detail = e.getMessage() == null ? e.toString() : e.getMessage();
            return f;
        }
    }

    // ---------------------------------------------------------------- analysis

    public static Finding analyzeBytes(byte[] data, String name, String path, String container) {
        Finding f = new Finding();
        f.name = name == null ? "Unnamed" : name;
        f.path = path == null ? f.name : path;
        f.container = container == null ? "" : container;
        f.size = data == null ? 0 : data.length;
        if (data == null || data.length == 0) {
            f.status = "Empty file";
            return f;
        }
        f.entropy = entropy(data);
        f.type = detectType(data, f.name);
        f.textPreview = previewText(data, 4096);

        EncodingCandidate encoded = detectEncoding(data);
        if (encoded != null && (meaningful(encoded.data) || encoded.confidence >= 80)) {
            f.kind = Kind.ENCODING;
            f.encoding = encoded.name;
            f.encoded = true;
            f.confidence = encoded.confidence;
            f.status = "Encoded (" + encoded.name + ") - Decodable";
            f.detail = "Detected encoding from content. "
                    + "Expected output: " + detectType(encoded.data, "") + ".";
            f.type = "Encoded text (" + encoded.name + ")";
            if (encoded.uncertain) f.detail += "\nHeuristic result; requires confirmation.";
            return f;
        }

        if (isZipType(f.type)) {
            f.kind = Kind.ARCHIVE;
            f.status = "Inspectable archive";
            f.confidence = 90;
            f.detail = "ZIP/APK archive. Entries can be inspected and extracted.";
            return f;
        }
        if (isCompressionType(f.type)) {
            f.kind = Kind.COMPRESSION;
            f.compressed = true;
            f.status = "Compressed (" + f.type + ") - Decodable";
            f.confidence = 90;
            f.detail = "Known compression signature. Does not require key.";
            return f;
        }
        if (f.type.startsWith("AES/OpenSSL") || f.type.startsWith("Encrypted PEM")) {
            f.kind = Kind.ENCRYPTION;
            f.encrypted = true;
            f.needsKey = true;
            f.cipher = f.type;
            f.confidence = 85;
            f.status = "Encrypted - Requires key";
            f.detail = "Detected cryptographic wrapper. Requires correct password/key and IV.";
            return f;
        }
        if (isKnownBinaryType(f.type)) {
            f.kind = Kind.BINARY;
            f.status = "Known unencrypted file";
            f.confidence = 80;
            f.detail = "Identified type from magic header signature.";
            return f;
        }
        if (isPlainText(data)) {
            f.kind = Kind.TEXT;
            f.status = "Plain text";
            f.confidence = 75;
            f.detail = "Readable plain text content without detected encryption.";
            String transformed = likelyTransform(data);
            if (transformed != null) {
                f.kind = Kind.ENCODING;
                f.encoding = transformed;
                f.encoded = true;
                f.status = "Potential text transform (" + transformed + ")";
                f.confidence = 55;
                f.detail = "Possible lightweight transform. Use decoding to confirm.";
            }
            return f;
        }
        XorGuess xor = guessSingleByteXor(data);
        if (xor != null) {
            f.kind = Kind.ENCODING;
            f.encoding = "XOR";
            f.cipher = "XOR (Key " + xor.key + ")";
            f.encoded = true;
            f.confidence = xor.score;
            f.status = "Potential XOR - Heuristic guess";
            f.detail = "Best candidate key: " + xor.key + ". Requires verification.";
            return f;
        }
        if (f.entropy >= 7.4) {
            f.kind = Kind.ENCRYPTION;
            f.encrypted = true;
            f.needsKey = true;
            f.cipher = "Unknown (high entropy)";
            f.confidence = 60;
            f.status = "Potential encryption - Requires key/info";
            f.detail = "Entropy: " + String.format(Locale.US, "%.2f", f.entropy)
                    + "/8.0. May be encrypted or compressed.";
            return f;
        }
        if (f.entropy >= 6.8) {
            f.kind = Kind.UNKNOWN;
            f.status = "Dense unknown data";
            f.confidence = 40;
            f.detail = "May be compressed, encrypted, or proprietary format.";
            return f;
        }
        f.kind = Kind.BINARY;
        f.status = "Unencrypted binary data";
        f.confidence = 35;
        f.detail = "No known signature or cryptographic indicator.";
        return f;
    }

    // ---------------------------------------------------------------- decryption

    public static Result decrypt(Finding f, byte[] data, String password, String keyText, String ivText) {
        if (f == null) return Result.fail("No file selected.", false);
        if (f.zipEncrypted) return decryptZipEntry(f, password);
        if (data == null || data.length == 0) return Result.fail("No processable data.", false);
        if (data.length > MAX_DECRYPT_BYTES) {
            return Result.fail("File exceeds safe decrypt limit (" + formatSize(MAX_DECRYPT_BYTES) + ").", false);
        }

        if (f.kind == Kind.TEXT && f.encoding.isEmpty()) {
            return Result.fail("File is plain text without detected encryption.", false);
        }
        List<Result> candidates = new ArrayList<>();
        if (!f.encoding.isEmpty() && !"XOR".equalsIgnoreCase(f.encoding)) {
            Result r = decodeByName(data, f.encoding);
            if (r != null) candidates.add(r);
        }
        for (String enc : new String[]{"Base64", "Hex", "Base32", "URL", "HTML", "Unicode"}) {
            Result r = decodeByName(data, enc);
            if (r != null) candidates.add(r);
        }
        for (String type : new String[]{"GZIP", "BZIP2", "XZ"}) {
            Result r = decompress(data, type);
            if (r != null) candidates.add(r);
        }
        byte[] key = parseKey(keyText);
        byte[] iv = parseKey(ivText);
        if (key.length > 0) {
            List<byte[]> inputs = new ArrayList<>();
            inputs.add(data);
            byte[] b64Input = decodeBase64(data);
            if (b64Input != null && b64Input.length > 0 && !Arrays.equals(b64Input, data)) {
                inputs.add(b64Input);
            }
            byte[] hexInput = decodeHex(data);
            if (hexInput != null && hexInput.length > 0 && !Arrays.equals(hexInput, data)) {
                inputs.add(hexInput);
            }
            for (int i = 0; i < inputs.size(); i++) {
                byte[] in = inputs.get(i);
                String prefix = i == 0 ? "" : (i == 1 ? "Base64 → " : "Hex → ");
                addKeyed(candidates, in, key, iv, prefix + "XOR", () -> xor(in, key));
                addKeyed(candidates, in, key, iv, prefix + "RC4", () -> rc4(in, key));
                addKeyed(candidates, in, key, iv, prefix + "AES/ECB", () -> aes(in, key, null, false, false));
                addKeyed(candidates, in, key, iv, prefix + "AES/CBC", () -> aes(in, key, iv, false, true));
                addKeyed(candidates, in, key, iv, prefix + "AES/GCM", () -> aesGcm(in, key, iv));
                addKeyed(candidates, in, key, iv, prefix + "DES/ECB", () -> des(in, key, null, false, false));
                addKeyed(candidates, in, key, iv, prefix + "DES/CBC", () -> des(in, key, iv, false, true));
                addKeyed(candidates, in, key, iv, prefix + "3DES/ECB", () -> desede(in, key, null, false, false));
                addKeyed(candidates, in, key, iv, prefix + "3DES/CBC", () -> desede(in, key, iv, false, true));
            }
        }
        if (key.length == 0) {
            XorGuess xor = guessSingleByteXor(data);
            if (xor != null && xor.data != null) {
                Result r = resultFrom("XOR (Key " + xor.key + ")", xor.data, true);
                if (r != null) {
                    r.uncertain = true;
                    r.message = "Heuristic result: Best XOR key is " + xor.key
                            + ". Review output.";
                    candidates.add(r);
                }
            }
        }

        Result best = choose(candidates);
        if (best != null) return best;

        if (f.kind == Kind.ENCRYPTION || f.encrypted || f.needsKey) {
            String msg = f.detail == null || f.detail.isEmpty()
                    ? "Cannot decrypt without key, password, or cipher info."
                    : f.detail;
            return Result.fail(msg, true);
        }
        return Result.fail("No valid decoding method found. Data may be raw binary or unsupported.", false);
    }

    private interface BytesSupplier { byte[] get() throws Exception; }

    private static void addKeyed(List<Result> out, byte[] data, byte[] key, byte[] iv,
                                 String method, BytesSupplier supplier) {
        try {
            byte[] result = supplier.get();
            if (result != null && result.length > 0) {
                Result r = resultFrom(method, result, false);
                if (r != null) out.add(r);
            }
        } catch (Exception ignored) {
        }
    }

    private static Result choose(List<Result> candidates) {
        Result best = null;
        int bestScore = -1;
        for (Result r : candidates) {
            if (r == null || !r.success || r.data == null) continue;
            int score = r.uncertain ? 55 : 90;
            if (isPlainText(r.data)) score += 5;
            if (isKnownBinary(r.data)) score += 10;
            if (r.data.length > 0) score += 1;
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }
        return best;
    }

    private static Result resultFrom(String method, byte[] data, boolean uncertain) {
        if (data == null || data.length == 0) return null;
        Result r = new Result();
        r.method = method;
        r.data = data;
        r.outputType = detectType(data, "");
        r.text = previewText(data, 12000);
        if (isPlainText(data)) {
            r.success = true;
            r.message = "Produced readable plain text.";
        } else if (isKnownBinary(data)) {
            r.success = true;
            r.message = "Produced recognized data: " + r.outputType + ".";
        } else if (isZipType(r.outputType) || isCompressionType(r.outputType)) {
            r.success = true;
            r.message = "Produced container: " + r.outputType + ".";
        } else if (printableRatio(data) > 0.55) {
            r.success = true;
            r.message = "Binary result with printable text ratio.";
        } else {
            return null;
        }
        r.uncertain = uncertain;
        return r;
    }

    private static Result decodeByName(byte[] data, String name) {
        try {
            byte[] out = null;
            if ("Base64".equals(name)) out = decodeBase64(data);
            else if ("Hex".equals(name)) out = decodeHex(data);
            else if ("Base32".equals(name)) out = decodeBase32(data);
            else if ("URL".equals(name)) out = decodeUrl(data);
            else if ("HTML".equals(name)) out = decodeHtml(data);
            else if ("Unicode".equals(name)) out = decodeUnicode(data);
            else if ("ROT13".equals(name)) out = rot13(data).getBytes(StandardCharsets.UTF_8);
            if (out == null || out.length == 0 || Arrays.equals(out, data)) return null;
            Result r = resultFrom(name, out, false);
            if (r != null && isCompressionType(r.outputType)) {
                String type = r.outputType;
                Result expanded = decompress(out, type);
                if (expanded != null) {
                    expanded.method = name + " → " + expanded.method;
                    return expanded;
                }
            }
            return r;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static Result decryptZipEntry(Finding f, String password) {
        if (f.archivePath == null || f.archivePath.isEmpty()) {
            return Result.fail("Archive path not available.", false);
        }
        if (password == null || password.isEmpty()) {
            return Result.fail("ZIP entry is encrypted. Enter password first.", true);
        }
        try {
            net.lingala.zip4j.ZipFile z = new net.lingala.zip4j.ZipFile(new File(f.archivePath));
            z.setPassword(password.toCharArray());
            FileHeader header = null;
            for (FileHeader h : z.getFileHeaders()) {
                if (f.entryName.equals(h.getFileName())) {
                    header = h;
                    break;
                }
            }
            if (header == null) return Result.fail("Entry not found inside archive.", false);
            byte[] data;
            try (InputStream in = z.getInputStream(header)) {
                data = readLimited(in, MAX_DECRYPT_BYTES);
            }
            Result r = resultFrom("ZIP/Extract", data, false);
            if (r == null) {
                r = new Result();
                r.success = true;
                r.method = "ZIP/Extract";
                r.data = data;
                r.outputType = detectType(data, "");
                r.text = previewText(data, 12000);
                r.message = "Entry extracted successfully (binary data).";
            }
            return r;
        } catch (Exception e) {
            return Result.fail("ZIP extraction failed: " + (e.getMessage() == null ? e.toString() : e.getMessage()), true);
        }
    }

    // ---------------------------------------------------------------- primitives

    public static byte[] xor(byte[] data, byte[] key) {
        if (data == null || key == null || key.length == 0) return data;
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ key[i % key.length]);
        return out;
    }

    public static byte[] rc4(byte[] data, byte[] key) {
        if (data == null || key == null || key.length == 0) return data;
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            int t = s[i]; s[i] = s[j]; s[j] = t;
        }
        int i = 0; j = 0;
        byte[] out = new byte[data.length];
        for (int k = 0; k < data.length; k++) {
            i = (i + 1) & 0xFF;
            j = (j + s[i]) & 0xFF;
            int t = s[i]; s[i] = s[j]; s[j] = t;
            out[k] = (byte) (data[k] ^ s[(s[i] + s[j]) & 0xFF]);
        }
        return out;
    }

    public static byte[] aes(byte[] data, byte[] key, byte[] iv, boolean encrypt, boolean cbc) throws Exception {
        byte[] k = normalize(key, 16, 24, 32);
        Cipher cipher;
        if (cbc) {
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(k, "AES"), new IvParameterSpec(normalize(iv, 16)));
        } else {
            cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(k, "AES"));
        }
        return cipher.doFinal(data);
    }

    public static byte[] aesGcm(byte[] data, byte[] key, byte[] iv) throws Exception {
        byte[] k = normalize(key, 16, 24, 32);
        byte[] nonce = normalize(iv, 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(k, "AES"),
                new GCMParameterSpec(128, nonce));
        return cipher.doFinal(data);
    }

    public static byte[] des(byte[] data, byte[] key, byte[] iv, boolean encrypt, boolean cbc) throws Exception {
        byte[] k = normalize(key, 8, 8, 8);
        Cipher cipher;
        if (cbc) {
            cipher = Cipher.getInstance("DES/CBC/PKCS5Padding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(k, "DES"), new IvParameterSpec(normalize(iv, 8)));
        } else {
            cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(k, "DES"));
        }
        return cipher.doFinal(data);
    }

    public static byte[] desede(byte[] data, byte[] key, byte[] iv, boolean encrypt, boolean cbc) throws Exception {
        byte[] k = normalize(key, 24, 24, 24);
        Cipher cipher;
        if (cbc) {
            cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(k, "DESede"), new IvParameterSpec(normalize(iv, 8)));
        } else {
            cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(k, "DESede"));
        }
        return cipher.doFinal(data);
    }

    private static byte[] normalize(byte[] key, int... lengths) {
        int len = lengths[0];
        if (key != null) {
            for (int l : lengths) if (key.length == l) return key;
            for (int l : lengths) if (key.length <= l) { len = l; break; }
            if (key.length > lengths[lengths.length - 1]) len = lengths[lengths.length - 1];
        }
        byte[] out = new byte[len];
        if (key != null) System.arraycopy(key, 0, out, 0, Math.min(key.length, len));
        return out;
    }

    public static byte[] parseKey(String text) {
        if (text == null) return new byte[0];
        String s = text.trim();
        if (s.isEmpty()) return new byte[0];
        try {
            String lower = s.toLowerCase(Locale.US);
            if (lower.startsWith("hex:")) return hexToBytes(s.substring(4).replaceAll("[\\s:-]", ""));
            if (lower.startsWith("b64:")) return Base64.getDecoder().decode(s.substring(4));
            if (s.matches("(?i)^(?:[0-9a-f]{2}[\\s:-]?)+$")) {
                String hex = s.replaceAll("[\\s:-]", "");
                // 16/24/32-character hex-looking values are usually a text
                // passphrase; use hex: prefix for a raw hex key of that length.
                if (hex.length() % 2 == 0 && hex.length() != 16
                        && hex.length() != 24 && hex.length() != 32) {
                    return hexToBytes(hex);
                }
            }
            // Plain words such as "password" are UTF-8 keys; Base64 needs an
            // explicit b64: prefix or an unmistakable Base64 marker.
            if (s.length() >= 8 && (s.contains("+") || s.contains("/") || s.endsWith("="))) {
                byte[] b = Base64.getDecoder().decode(s.replace('-', '+').replace('_', '/'));
                if (b.length >= 4) return b;
            }
        } catch (Exception ignored) {
        }
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] decodeBase64(byte[] data) {
        String s = new String(data, StandardCharsets.US_ASCII).replaceAll("\\s+", "");
        if (s.isEmpty() || !s.matches("^[A-Za-z0-9+/=_-]+$")) return null;
        try {
            return Base64.getDecoder().decode(s.replace('-', '+').replace('_', '/'));
        } catch (Exception e) {
            try {
                return Base64.getMimeDecoder().decode(s);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static byte[] decodeHex(byte[] data) {
        String s = new String(data, StandardCharsets.US_ASCII).replaceAll("[\\s:-]", "");
        if (s.isEmpty() || s.length() % 2 != 0 || !s.matches("^[0-9a-fA-F]+$")) return null;
        return hexToBytes(s);
    }

    public static byte[] decodeBase32(byte[] data) {
        String s = new String(data, StandardCharsets.US_ASCII).replaceAll("\\s+", "").replace("=", "");
        if (s.isEmpty() || !s.matches("^[A-Z2-7]+$")) return null;
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : s.toCharArray()) {
            int v = alphabet.indexOf(c);
            if (v < 0) return null;
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }

    public static byte[] decodeUrl(byte[] data) {
        try {
            String s = new String(data, StandardCharsets.UTF_8);
            if (!s.matches("(?s).*%[0-9a-fA-F]{2}.*")) return null;
            return java.net.URLDecoder.decode(s, "UTF-8").getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] decodeHtml(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        if (!s.matches("(?s).*&(?:#x?[0-9a-fA-F]+|[a-zA-Z]+);.*")) return null;
        s = s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&").replace("&nbsp;", " ");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("&#x([0-9a-fA-F]+);|&#([0-9]+);").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                int code = m.group(1) != null ? Integer.parseInt(m.group(1), 16)
                        : Integer.parseInt(m.group(2));
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                        new String(Character.toChars(code))));
            } catch (Exception ignored) {
            }
        }
        m.appendTail(sb);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] decodeUnicode(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        if (!s.matches("(?s).*\\\\u[0-9a-fA-F]{4}.*")) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(
                    new String(Character.toChars(Integer.parseInt(m.group(1), 16)))));
        }
        m.appendTail(sb);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static String rot13(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 'a' && c <= 'm') c += 13;
            else if (c >= 'A' && c <= 'M') c += 13;
            else if (c >= 'n' && c <= 'z') c -= 13;
            else if (c >= 'N' && c <= 'Z') c -= 13;
            sb.append(c);
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest(data)) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- detection

    private static final class EncodingCandidate {
        final String name;
        final byte[] data;
        final int confidence;
        final boolean uncertain;
        EncodingCandidate(String name, byte[] data, int confidence, boolean uncertain) {
            this.name = name;
            this.data = data;
            this.confidence = confidence;
            this.uncertain = uncertain;
        }
    }

    private static EncodingCandidate detectEncoding(byte[] data) {
        if (data == null || data.length < 6) return null;
        String text = new String(data, StandardCharsets.US_ASCII).trim();
        if (text.length() < 6) return null;
        String compact = text.replaceAll("\\s+", "");

        if (compact.length() >= 16 && compact.length() % 2 == 0
                && compact.matches("^[0-9a-fA-F]+$")) {
            byte[] out = decodeHex(data);
            if (out != null && out.length >= 4) {
                return new EncodingCandidate("Hex", out, isKnownBinary(out) ? 90 : 78, false);
            }
        }
        if (compact.length() >= 12 && compact.matches("^[A-Za-z0-9+/=_-]+$")
                && (compact.contains("=") || compact.length() % 4 == 0)
                && !compact.matches("^[A-Za-z]+$")) {
            byte[] out = decodeBase64(data);
            if (out != null && out.length >= 4) {
                int c = 88;
                if (isKnownBinary(out) || isZipType(detectType(out, ""))) c = 95;
                if (isPlainText(out)) c = Math.max(c, 92);
                return new EncodingCandidate("Base64", out, c, false);
            }
        }
        if (compact.length() >= 16 && compact.matches("^[A-Z2-7]+=*$")) {
            byte[] out = decodeBase32(data);
            if (out != null && out.length >= 4) {
                return new EncodingCandidate("Base32", out, 82, false);
            }
        }
        if (text.matches("(?s).*%[0-9a-fA-F]{2}.*")) {
            byte[] out = decodeUrl(data);
            if (out != null) return new EncodingCandidate("URL", out, 80, false);
        }
        if (text.matches("(?s).*&(?:#x?[0-9a-fA-F]+|[a-zA-Z]+);.*")) {
            byte[] out = decodeHtml(data);
            if (out != null) return new EncodingCandidate("HTML", out, 80, false);
        }
        if (text.matches("(?s).*\\\\u[0-9a-fA-F]{4}.*")) {
            byte[] out = decodeUnicode(data);
            if (out != null) return new EncodingCandidate("Unicode", out, 80, false);
        }
        return null;
    }

    private static String likelyTransform(byte[] data) {
        String s = new String(data, StandardCharsets.UTF_8);
        if (s.length() < 8) return null;
        String rot = rot13(data);
        if (scoreEnglish(rot) > scoreEnglish(s) + 3) return "ROT13";
        for (int shift = 1; shift < 26; shift++) {
            String c = caesar(s, shift);
            if (scoreEnglish(c) > scoreEnglish(s) + 4) return "Caesar " + shift;
        }
        return null;
    }

    private static String caesar(String s, int shift) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= 'a' && c <= 'z') c = (char) ('a' + (c - 'a' + shift) % 26);
            else if (c >= 'A' && c <= 'Z') c = (char) ('A' + (c - 'A' + shift) % 26);
            sb.append(c);
        }
        return sb.toString();
    }

    private static final class XorGuess {
        int key;
        int score;
        byte[] data;
    }

    private static XorGuess guessSingleByteXor(byte[] data) {
        if (data.length < 6) return null;
        XorGuess best = null;
        for (int key = 1; key < 256; key++) {
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ key);
            int score = (int) (printableRatio(out) * 80)
                    + scoreEnglish(new String(out, StandardCharsets.UTF_8));
            if (best == null || score > best.score) {
                best = new XorGuess();
                best.key = key;
                best.score = score;
                best.data = out;
            }
        }
        if (best != null && best.score >= 75 && printableRatio(best.data) > 0.9) return best;
        return null;
    }

    private static int scoreEnglish(String s) {
        if (s == null) return 0;
        String lower = s.toLowerCase(Locale.US);
        int score = 0;
        for (String w : new String[]{" the ", " and ", " to ", " of ", " is ",
                " in ", " this ", " that ", " for ", " you "}) {
            int idx = 0;
            while ((idx = lower.indexOf(w, idx)) >= 0) {
                score += 3;
                idx += w.length();
            }
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') score += 1;
        }
        return score;
    }

    public static String detectType(byte[] data, String name) {
        if (data == null || data.length < 2) return "Empty/Short";
        if (startsWith(data, 0x50, 0x4B, 0x03, 0x04) || startsWith(data, 0x50, 0x4B, 0x05, 0x06)
                || startsWith(data, 0x50, 0x4B, 0x07, 0x08)) {
            String lower = name == null ? "" : name.toLowerCase(Locale.US);
            return lower.endsWith(".apk") ? "APK (ZIP)" : "ZIP";
        }
        if (startsWith(data, 0x1F, 0x8B)) return "GZIP";
        if (startsWith(data, 'B', 'Z', 'h')) return "BZIP2";
        if (startsWith(data, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00)) return "XZ";
        if (startsWith(data, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C)) return "7Z";
        if (startsWith(data, 'R', 'a', 'r', '!')) return "RAR";
        if (startsWith(data, 0x04, 0x22, 0x4D, 0x18)) return "LZ4";
        if (startsWith(data, 0x28, 0xB5, 0x2F, 0xFD)) return "ZSTD";
        if (data.length > 262 && new String(data, 257, 5, StandardCharsets.US_ASCII).equals("ustar")) return "TAR";
        if (startsWith(data, '%', 'P', 'D', 'F')) return "PDF";
        if (startsWith(data, 0x89, 'P', 'N', 'G')) return "PNG";
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) return "JPEG";
        if (startsWith(data, 'G', 'I', 'F', '8')) return "GIF";
        if (startsWith(data, 'R', 'I', 'F', 'F') && data.length > 12
                && new String(data, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) return "WEBP";
        if (startsWith(data, 'B', 'M')) return "BMP";
        if (startsWith(data, 'd', 'e', 'x', '\n')) return "DEX";
        if (startsWith(data, 0x7F, 'E', 'L', 'F')) return "ELF";
        if (startsWith(data, 'S', 'Q', 'L', 'i', 't', 'e', ' ', 'f', 'o', 'r', 'm', 'a', 't')) return "SQLite";
        if (startsWith(data, 0xAC, 0xED)) return "Java Serialized";
        if (startsWith(data, 'S', 'a', 'l', 't', 'e', 'd', '_', '_')) return "AES/OpenSSL (Salted__)";
        String head = new String(data, 0, Math.min(data.length, 64), StandardCharsets.US_ASCII);
        if (head.contains("-----BEGIN ENCRYPTED")
                || (head.contains("-----BEGIN RSA PRIVATE KEY")
                && head.contains("Proc-Type: 4,ENCRYPTED"))) return "Encrypted PEM";
        if (head.contains("-----BEGIN ")) return "PEM";
        return isPlainText(data) ? "Text" : "Binary";
    }

    private static boolean isZipType(String type) {
        return "ZIP".equals(type) || "APK (ZIP)".equals(type);
    }

    private static boolean isCompressionType(String type) {
        return "GZIP".equals(type) || "BZIP2".equals(type) || "XZ".equals(type)
                || "7Z".equals(type) || "RAR".equals(type) || "LZ4".equals(type)
                || "ZSTD".equals(type) || "TAR".equals(type);
    }

    private static boolean isKnownBinaryType(String type) {
        return "PDF".equals(type) || "PNG".equals(type) || "JPEG".equals(type)
                || "GIF".equals(type) || "WEBP".equals(type) || "BMP".equals(type)
                || "DEX".equals(type) || "ELF".equals(type) || "SQLite".equals(type)
                || "Java Serialized".equals(type) || "PEM".equals(type);
    }

    private static boolean isKnownBinary(byte[] data) {
        return isKnownBinaryType(detectType(data, ""));
    }

    public static boolean isZip(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] head = new byte[4];
            int n = in.read(head);
            return n == 4 && head[0] == 'P' && head[1] == 'K'
                    && (head[2] == 3 || head[2] == 5 || head[2] == 7);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean startsWith(byte[] data, int... values) {
        if (data == null || data.length < values.length) return false;
        for (int i = 0; i < values.length; i++) {
            if ((data[i] & 0xFF) != (values[i] & 0xFF)) return false;
        }
        return true;
    }

    private static Result decompress(byte[] data, String type) {
        try {
            InputStream in;
            if ("GZIP".equals(type)) in = new GzipCompressorInputStream(new ByteArrayInputStream(data));
            else if ("BZIP2".equals(type)) in = new BZip2CompressorInputStream(new ByteArrayInputStream(data));
            else if ("XZ".equals(type)) in = new XZCompressorInputStream(new ByteArrayInputStream(data));
            else return null;
            byte[] out = readLimited(in, MAX_DECRYPT_BYTES);
            in.close();
            if (out.length == 0) return null;
            Result r = resultFrom(type + " (Decompressed)", out, false);
            if (r == null) {
                r = new Result();
                r.success = true;
                r.method = type + " (Decompressed)";
                r.data = out;
                r.outputType = detectType(out, "");
                r.text = previewText(out, 12000);
                r.message = "Decompressed successfully (binary data).";
            } else {
                r.message = "Decompressed successfully (compressed, not encrypted).";
            }
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- text utils

    public static boolean isPlainText(byte[] data) {
        if (data == null || data.length == 0) return false;
        int limit = Math.min(data.length, SAMPLE);
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            dec.decode(ByteBuffer.wrap(data, 0, limit));
        } catch (CharacterCodingException e) {
            return false;
        }
        int printable = 0;
        for (int i = 0; i < limit; i++) {
            int b = data[i] & 0xFF;
            if (b == 9 || b == 10 || b == 13 || b >= 32) printable++;
        }
        return printable >= limit * 0.92;
    }

    public static double printableRatio(byte[] data) {
        if (data == null || data.length == 0) return 0;
        int limit = Math.min(data.length, SAMPLE);
        int printable = 0;
        for (int i = 0; i < limit; i++) {
            int b = data[i] & 0xFF;
            if (b == 9 || b == 10 || b == 13 || (b >= 32 && b < 127) || b >= 0xC0) printable++;
        }
        return (double) printable / limit;
    }

    private static boolean meaningful(byte[] data) {
        if (data == null || data.length == 0) return false;
        return isPlainText(data) || isKnownBinary(data) || isZipType(detectType(data, ""));
    }

    public static String previewText(byte[] data, int max) {
        if (data == null || data.length == 0) return "";
        if (isPlainText(data)) {
            String s = new String(data, StandardCharsets.UTF_8);
            return s.length() > max ? s.substring(0, max) + "\n…" : s;
        }
        return hexDump(data, Math.min(max / 3, 2048));
    }

    public static String hexDump(byte[] data, int maxBytes) {
        if (data == null) return "";
        int n = Math.min(data.length, maxBytes);
        StringBuilder sb = new StringBuilder(n * 4);
        for (int i = 0; i < n; i += 16) {
            sb.append(String.format(Locale.US, "%08x  ", i));
            int end = Math.min(i + 16, n);
            for (int j = i; j < end; j++) sb.append(String.format(Locale.US, "%02x ", data[j] & 0xFF));
            for (int j = end; j < i + 16; j++) sb.append("   ");
            sb.append(" |");
            for (int j = i; j < end; j++) {
                int b = data[j] & 0xFF;
                sb.append(b >= 32 && b < 127 ? (char) b : '.');
            }
            sb.append("|\n");
        }
        if (n < data.length) sb.append("… (").append(data.length - n).append(" additional bytes)");
        return sb.toString();
    }

    public static double entropy(byte[] data) {
        if (data == null || data.length == 0) return 0;
        int n = Math.min(data.length, SAMPLE);
        int[] freq = new int[256];
        for (int i = 0; i < n; i++) freq[data[i] & 0xFF]++;
        double e = 0;
        for (int c : freq) {
            if (c == 0) continue;
            double p = (double) c / n;
            e -= p * (Math.log(p) / Math.log(2));
        }
        return e;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static String baseName(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        int total = 0;
        while ((n = in.read(buf)) > 0 && total < max) {
            int take = Math.min(n, max - total);
            out.write(buf, 0, take);
            total += take;
        }
        return out.toByteArray();
    }

    public static byte[] readFile(File file) throws IOException {
        if (file.length() > MAX_DECRYPT_BYTES) {
            throw new IOException("File is larger than " + formatSize(MAX_DECRYPT_BYTES));
        }
        try (InputStream in = new FileInputStream(file)) {
            return readLimited(in, MAX_DECRYPT_BYTES);
        }
    }
}

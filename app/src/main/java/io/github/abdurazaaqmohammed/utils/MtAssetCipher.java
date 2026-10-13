package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.Adler32;

/**
 * Reader and writer for the small asset container that some protected APKs use
 * to hide secondary payloads (extra dex, arsc, native binaries) in assets/.
 *
 * <p>The layout is four bytes of magic followed by the whole inner file, every
 * byte of which is XORed with a single per-file key:
 *
 * <pre>
 *   fb 61 62 b8 | (inner[0] ^ key) (inner[1] ^ key) ...
 * </pre>
 *
 * <p>The key is not stored in the file. It is recovered by assuming an inner
 * format, taking the first byte of the payload, and XORing it with the first
 * byte that format must start with ("dex", 0x7f for ELF, 0x02 for arsc). The
 * guess is then checked against the format's own integrity fields, so a wrong
 * guess cannot be mistaken for a successful decryption: a dex has to agree with
 * its adler32 and SHA-1, an arsc table with its own total size.
 *
 * <p>This is an independent implementation written from the observed byte
 * layout; it is not derived from any protected application's code.
 */
public final class MtAssetCipher {

    /** Magic stored as the little-endian int {@code 0xb86261fb}. */
    public static final int MAGIC = 0xb86261fb;

    private static final byte[] MAGIC_BYTES = {(byte) 0xfb, 0x61, 0x62, (byte) 0xb8};
    private static final int HEADER_SIZE = 4;

    /** Key used for encryption when the caller does not pick one. */
    public static final byte DEFAULT_KEY = 0x5a;

    private MtAssetCipher() {
    }

    /** Outcome of a decrypt attempt: format name, key, and the inner bytes. */
    public static final class Result {
        public final boolean ok;
        public final String format;
        public final byte key;
        public final String detail;
        public final byte[] data;

        private Result(boolean ok, String format, byte key, String detail, byte[] data) {
            this.ok = ok;
            this.format = format;
            this.key = key;
            this.detail = detail;
            this.data = data;
        }

        static Result good(String format, byte key, String detail, byte[] data) {
            return new Result(true, format, key, detail, data);
        }

        static Result bad(String detail) {
            return new Result(false, null, (byte) 0, detail, null);
        }
    }

    /** True when the file starts with the container magic. */
    public static boolean looksLikeContainer(byte[] file) {
        if (file == null || file.length <= HEADER_SIZE) {
            return false;
        }
        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (file[i] != MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Decrypt a container, working out the inner format from its own magic and
     * checking the result against that format's integrity fields.
     */
    public static Result decrypt(byte[] container) {
        if (!looksLikeContainer(container)) {
            return Result.bad("missing " + hex(MAGIC_BYTES) + " header");
        }
        byte[] payload = Arrays.copyOfRange(container, HEADER_SIZE, container.length);
        if (payload.length == 0) {
            return Result.bad("empty payload");
        }

        String[] candidates = {"dex", "elf", "arsc", "zip"};
        for (String candidate : candidates) {
            byte key = (byte) (payload[0] ^ firstByteOf(candidate));
            byte[] plain = xor(payload, key);
            String why = verify(candidate, plain);
            if (why == null) {
                return Result.good(candidate, key, why, plain);
            }
        }
        return Result.bad("inner format not recognised, or key not recoverable from a known magic");
    }

    /**
     * Decrypt with an explicit key, for inner formats that carry no verifiable
     * header. Only the container magic is checked, so the caller gets whatever
     * the key produces.
     */
    public static Result decrypt(byte[] container, byte key) {
        if (!looksLikeContainer(container)) {
            return Result.bad("missing " + hex(MAGIC_BYTES) + " header");
        }
        byte[] payload = Arrays.copyOfRange(container, HEADER_SIZE, container.length);
        byte[] plain = xor(payload, key);
        String format = sniff(plain);
        return Result.good(format == null ? "raw" : format, key, null, plain);
    }

    /** Wrap an inner file in the container. Any key works; the default is used. */
    public static byte[] encrypt(byte[] plain) {
        return encrypt(plain, DEFAULT_KEY);
    }

    /** Wrap an inner file in the container with a chosen key. */
    public static byte[] encrypt(byte[] plain, byte key) {
        if (plain == null || plain.length == 0) {
            throw new IllegalArgumentException("nothing to encrypt");
        }
        byte[] out = new byte[HEADER_SIZE + plain.length];
        System.arraycopy(MAGIC_BYTES, 0, out, 0, HEADER_SIZE);
        for (int i = 0; i < plain.length; i++) {
            out[HEADER_SIZE + i] = (byte) (plain[i] ^ key);
        }
        return out;
    }

    /** Name for the format a decrypted buffer starts with, or null. */
    public static String sniff(byte[] plain) {
        for (String candidate : new String[]{"dex", "elf", "arsc", "zip"}) {
            if (verify(candidate, plain) == null) {
                return candidate;
            }
        }
        return null;
    }

    /** Suggested extension for a decrypted payload of the given format. */
    public static String extensionFor(String format) {
        if ("dex".equals(format)) {
            return ".dex";
        }
        if ("elf".equals(format)) {
            return ".so";
        }
        if ("arsc".equals(format)) {
            return ".arsc";
        }
        if ("zip".equals(format)) {
            return ".zip";
        }
        return ".bin";
    }

    private static byte firstByteOf(String format) {
        switch (format) {
            case "dex":
                return 'd';
            case "elf":
                return 0x7f;
            case "arsc":
                return 0x02;
            default:
                return 'P';
        }
    }

    /**
     * Check a decrypted buffer against the integrity fields of the format it
     * claims to be. Returns null when it checks out, otherwise a short reason.
     */
    private static String verify(String format, byte[] p) {
        switch (format) {
            case "dex":
                return verifyDex(p);
            case "elf":
                return verifyElf(p);
            case "arsc":
                return verifyArsc(p);
            case "zip":
                return startsWith(p, 'P', 'K', 3, 4) ? null : "not a zip";
            default:
                return "unknown format";
        }
    }

    private static String verifyDex(byte[] p) {
        if (!startsWith(p, 'd', 'e', 'x', '\n')) {
            return "not a dex";
        }
        if (p.length < 112) {
            return "dex too short for a header";
        }
        int fileSize = le32(p, 32);
        if (fileSize != p.length) {
            return "dex size field says " + fileSize + ", file is " + p.length;
        }
        int storedAdler = le32(p, 8);
        int realAdler = (int) adler32(p, 12, p.length - 12);
        if (storedAdler != realAdler) {
            return "dex adler32 mismatch";
        }
        byte[] storedSha = Arrays.copyOfRange(p, 12, 32);
        byte[] realSha = sha1(p, 32, p.length - 32);
        if (!Arrays.equals(storedSha, realSha)) {
            return "dex SHA-1 mismatch";
        }
        return null;
    }

    private static String verifyArsc(byte[] p) {
        if (p.length < 8) {
            return "arsc too short";
        }
        int type = le16(p, 0);
        int headerSize = le16(p, 2);
        int total = le32(p, 4);
        if (type != 0x0002) {
            return "not a resource table";
        }
        if (headerSize != 8 && headerSize != 12) {
            return "bad resource table header size " + headerSize;
        }
        if (total != p.length) {
            return "arsc size field says " + total + ", file is " + p.length;
        }
        return null;
    }

    private static String verifyElf(byte[] p) {
        if (!startsWith(p, 0x7f, 'E', 'L', 'F')) {
            return "not an elf";
        }
        if (p.length < 16) {
            return "elf too short for a header";
        }
        int type = le16(p, 16);
        if (type < 1 || type > 4) {
            return "bad elf type " + type;
        }
        int machine = le16(p, 18);
        if (machine == 0) {
            return "elf with no machine";
        }
        return null;
    }

    private static byte[] xor(byte[] in, byte key) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = (byte) (in[i] ^ key);
        }
        return out;
    }

    private static boolean startsWith(byte[] p, int b0, int b1, int b2, int b3) {
        return p.length >= 4
                && (p[0] & 0xff) == b0
                && (p[1] & 0xff) == b1
                && (p[2] & 0xff) == b2
                && (p[3] & 0xff) == b3;
    }

    private static int le16(byte[] p, int off) {
        return (p[off] & 0xff) | ((p[off + 1] & 0xff) << 8);
    }

    private static int le32(byte[] p, int off) {
        return (p[off] & 0xff)
                | ((p[off + 1] & 0xff) << 8)
                | ((p[off + 2] & 0xff) << 16)
                | ((p[off + 3] & 0xff) << 24);
    }

    private static long adler32(byte[] p, int off, int len) {
        Adler32 adler = new Adler32();
        adler.update(p, off, len);
        return adler.getValue();
    }

    private static byte[] sha1(byte[] p, int off, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(p, off, len);
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 missing", e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte value : b) {
            sb.append(String.format("%02x", value));
        }
        return sb.toString();
    }

    /** Default output file for a decrypt/encrypt result, never overwriting. */
    public static File unusedSibling(File source, String suffix) {
        File parent = source.getParentFile();
        String name = source.getName();
        File candidate = new File(parent, name + suffix);
        for (int i = 1; candidate.exists() && i < 1000; i++) {
            candidate = new File(parent, name + suffix + "." + i);
        }
        return candidate;
    }

    /** Read a whole file into memory. */
    public static byte[] readAll(File file) throws IOException {
        return java.nio.file.Files.readAllBytes(file.toPath());
    }

    /** Write a whole file, replacing it if it exists. */
    public static void writeAll(File file, byte[] data) throws IOException {
        java.nio.file.Files.write(file.toPath(), data);
    }
}

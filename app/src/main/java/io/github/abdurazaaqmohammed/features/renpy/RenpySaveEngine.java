package io.github.abdurazaaqmohammed.features.renpy;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 100% Pure Java Engine for Ren'Py save (.save) and persistent files.
 * Zero external python dependencies, zero process spawning, zero permission issues on Android.
 * Reads pickle protocol 2, patches variables in place, and unlocks galleries natively.
 */
public final class RenpySaveEngine {

    private RenpySaveEngine() {}

    public static class RenpyVariable implements Serializable {
        public final String rawKey;
        public final String displayName;
        public final String type;
        public Object value;

        public RenpyVariable(String rawKey, String displayName, String type, Object value) {
            this.rawKey = rawKey;
            this.displayName = displayName;
            this.type = type;
            this.value = value;
        }

        public String getDisplayValue() {
            if (value == null) return "None";
            return String.valueOf(value);
        }
    }

    public static class ParseResult {
        public final boolean isSave;
        public final List<RenpyVariable> variables;

        public ParseResult(boolean isSave, List<RenpyVariable> variables) {
            this.isSave = isSave;
            this.variables = variables;
        }
    }

    /**
     * Checks if a file is a valid zip archive without throwing exceptions.
     */
    public static boolean isZipFile(File file) {
        if (file == null || !file.exists() || file.length() < 22) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            return read == 4 && header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Decompresses raw zlib-compressed data (RFC 1950) into uncompressed bytes.
     */
    public static byte[] decompressZlib(byte[] input) throws Exception {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(input);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length * 3);
            byte[] buf = new byte[8192];
            while (!inflater.finished()) {
                int count = inflater.inflate(buf);
                if (count > 0) {
                    baos.write(buf, 0, count);
                } else {
                    if (inflater.needsInput()) {
                        break;
                    }
                }
            }
            return baos.toByteArray();
        } finally {
            inflater.end();
        }
    }

    /**
     * Compresses bytes using zlib compression (best compression, level 9).
     */
    public static byte[] compressZlib(byte[] input) throws Exception {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length);
            byte[] buf = new byte[8192];
            while (!deflater.finished()) {
                int count = deflater.deflate(buf);
                baos.write(buf, 0, count);
            }
            return baos.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * Reads a file completely into a byte array.
     */
    private static byte[] readFileBytes(File file) throws Exception {
        long len = file.length();
        if (len > 150 * 1024 * 1024) throw new IllegalStateException("File too large (" + len + " bytes)");
        byte[] bytes = new byte[(int) len];
        try (FileInputStream fis = new FileInputStream(file)) {
            int offset = 0;
            int numRead;
            while (offset < bytes.length && (numRead = fis.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
        }
        return bytes;
    }

    /**
     * Reads the raw pickle bytes from either a .save file (from the 'log' entry) or a persistent file (zlib uncompressed).
     */
    public static byte[] extractPickleBytes(File targetFile, boolean isSave) throws Exception {
        if (isSave) {
            try (ZipFile zf = new ZipFile(targetFile)) {
                ZipEntry logEntry = zf.getEntry("log");
                if (logEntry == null) {
                    throw new IllegalStateException("Not a valid Ren'Py save: 'log' entry missing");
                }
                try (InputStream is = zf.getInputStream(logEntry);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream((int) logEntry.getSize())) {
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = is.read(buf)) != -1) {
                        baos.write(buf, 0, r);
                    }
                    return baos.toByteArray();
                }
            }
        } else {
            byte[] raw = readFileBytes(targetFile);
            return decompressZlib(raw);
        }
    }

    /**
     * Parses variables from the pickle byte stream using pure Java scanning.
     */
    public static ParseResult parse(Context context, File targetFile) throws Exception {
        boolean isSave = isZipFile(targetFile);
        byte[] data = extractPickleBytes(targetFile, isSave);

        List<RenpyVariable> list = new ArrayList<>();
        int n = data.length;
        int i = 0;

        while (i < n - 3) {
            int tag = data[i] & 0xFF;
            String key = null;

            if (tag == 'U') { // SHORT_BINSTRING
                int slen = data[i + 1] & 0xFF;
                if (i + 2 + slen <= n) {
                    key = new String(data, i + 2, slen, StandardCharsets.ISO_8859_1);
                }
                i += 2 + slen;
            } else if (tag == 'X') { // BINUNICODE
                if (i + 5 <= n) {
                    int slen = ByteBuffer.wrap(data, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (slen >= 0 && i + 5 + slen <= n) {
                        key = new String(data, i + 5, slen, StandardCharsets.UTF_8);
                    }
                    i += 5 + slen;
                } else {
                    i++;
                    continue;
                }
            } else {
                i++;
                continue;
            }

            if (key == null || key.length() < 2) continue;

            // Save files: only show game variables under 'store.' (ignoring internal store._*)
            if (isSave) {
                if (!key.startsWith("store.") || key.startsWith("store._")) {
                    continue;
                }
            } else {
                // Persistent files: skip internal Ren'Py engine keys except known gallery/audio
                if (key.startsWith("_") && !key.equals("_seen_images") && !key.equals("_seen_ever") && !key.equals("_seen_audio")) {
                    continue;
                }
            }

            // Skip BINPUT (q + 1) or LONG_BINPUT (r + 4)
            if (i < n && (data[i] & 0xFF) == 'q') {
                i += 2;
            } else if (i + 4 < n && (data[i] & 0xFF) == 'r') {
                i += 5;
            }

            if (i >= n) break;

            int vTag = data[i] & 0xFF;
            String vtype = null;
            Object val = null;

            if (vTag == 0x88) { // NEWTRUE
                vtype = "bool";
                val = true;
            } else if (vTag == 0x89) { // NEWFALSE
                vtype = "bool";
                val = false;
            } else if (vTag == 'N') { // NONE
                vtype = "None";
                val = null;
            } else if (vTag == 'K') { // BININT1
                if (i + 1 < n) {
                    vtype = "int";
                    val = (long) (data[i + 1] & 0xFF);
                }
            } else if (vTag == 'M') { // BININT2
                if (i + 2 < n) {
                    vtype = "int";
                    val = (long) (ByteBuffer.wrap(data, i + 1, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF);
                }
            } else if (vTag == 'J') { // BININT
                if (i + 4 < n) {
                    vtype = "int";
                    val = (long) ByteBuffer.wrap(data, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                }
            } else if (vTag == 'G') { // BINFLOAT
                if (i + 8 < n) {
                    vtype = "float";
                    val = ByteBuffer.wrap(data, i + 1, 8).order(ByteOrder.BIG_ENDIAN).getDouble();
                }
            } else if (vTag == 'U') { // SHORT_BINSTRING
                int vlen = data[i + 1] & 0xFF;
                if (i + 2 + vlen <= n) {
                    vtype = "str";
                    val = new String(data, i + 2, vlen, StandardCharsets.ISO_8859_1);
                }
            } else if (vTag == 'X') { // BINUNICODE
                if (i + 5 <= n) {
                    int vlen = ByteBuffer.wrap(data, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (vlen >= 0 && i + 5 + vlen <= n) {
                        vtype = "str";
                        val = new String(data, i + 5, vlen, StandardCharsets.UTF_8);
                    }
                }
            }

            if (vtype != null) {
                String displayName = key.startsWith("store.") ? key.substring(6) : key;
                list.add(new RenpyVariable(key, displayName, vtype, val));
            }
        }

        // Sort alphabetically by display name
        Collections.sort(list, (a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        return new ParseResult(isSave, list);
    }

    /**
     * Serializes a primitive/scalar value into Python Pickle Protocol 2 byte sequence.
     */
    public static byte[] serializeValueProto2(Object val) {
        if (val == null) {
            return new byte[]{'N'};
        } else if (val instanceof Boolean) {
            return new byte[]{(byte) (((Boolean) val) ? 0x88 : 0x89)};
        } else if (val instanceof Number) {
            long l = ((Number) val).longValue();
            if (val instanceof Double || val instanceof Float) {
                byte[] b = new byte[9];
                b[0] = 'G';
                ByteBuffer.wrap(b, 1, 8).order(ByteOrder.BIG_ENDIAN).putDouble(((Number) val).doubleValue());
                return b;
            } else if (l >= 0 && l <= 255) {
                return new byte[]{'K', (byte) l};
            } else if (l >= 0 && l <= 65535) {
                byte[] b = new byte[3];
                b[0] = 'M';
                ByteBuffer.wrap(b, 1, 2).order(ByteOrder.LITTLE_ENDIAN).putShort((short) l);
                return b;
            } else if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                byte[] b = new byte[5];
                b[0] = 'J';
                ByteBuffer.wrap(b, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) l);
                return b;
            } else {
                byte[] sBytes = (l + "L").getBytes(StandardCharsets.US_ASCII);
                byte[] b = new byte[2 + sBytes.length];
                b[0] = 'p';
                b[1] = (byte) sBytes.length;
                System.arraycopy(sBytes, 0, b, 2, sBytes.length);
                return b;
            }
        } else {
            String s = String.valueOf(val);
            byte[] sBytes = s.getBytes(StandardCharsets.UTF_8);
            byte[] b = new byte[5 + sBytes.length];
            b[0] = 'X';
            ByteBuffer.wrap(b, 1, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(sBytes.length);
            System.arraycopy(sBytes, 0, b, 5, sBytes.length);
            return b;
        }
    }

    private static class Patch {
        final int start;
        final int end;
        final byte[] newBytes;
        Patch(int start, int end, byte[] newBytes) {
            this.start = start;
            this.end = end;
            this.newBytes = newBytes;
        }
    }

    /**
     * Patches the pickle byte stream with user modifications in-place.
     */
    public static byte[] patchPickleBytes(byte[] rawBytes, Map<String, Object> modifications) {
        if (modifications == null || modifications.isEmpty()) return rawBytes;

        List<Patch> patches = new ArrayList<>();
        int n = rawBytes.length;
        int i = 0;

        while (i < n - 3) {
            int tag = rawBytes[i] & 0xFF;
            String key = null;

            if (tag == 'U') {
                int slen = rawBytes[i + 1] & 0xFF;
                if (i + 2 + slen <= n) {
                    key = new String(rawBytes, i + 2, slen, StandardCharsets.ISO_8859_1);
                }
                i += 2 + slen;
            } else if (tag == 'X') {
                if (i + 5 <= n) {
                    int slen = ByteBuffer.wrap(rawBytes, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (slen >= 0 && i + 5 + slen <= n) {
                        key = new String(rawBytes, i + 5, slen, StandardCharsets.UTF_8);
                    }
                    i += 5 + slen;
                } else {
                    i++;
                    continue;
                }
            } else {
                i++;
                continue;
            }

            if (key == null || !modifications.containsKey(key)) continue;

            // Skip BINPUT or LONG_BINPUT
            if (i < n && (rawBytes[i] & 0xFF) == 'q') {
                i += 2;
            } else if (i + 4 < n && (rawBytes[i] & 0xFF) == 'r') {
                i += 5;
            }

            if (i >= n) break;

            int valStart = i;
            int valEnd = i;
            int vTag = rawBytes[i] & 0xFF;

            if (vTag == 0x88 || vTag == 0x89 || vTag == 'N') {
                valEnd = i + 1;
            } else if (vTag == 'K') {
                valEnd = i + 2;
            } else if (vTag == 'M') {
                valEnd = i + 3;
            } else if (vTag == 'J') {
                valEnd = i + 5;
            } else if (vTag == 'G') {
                valEnd = i + 9;
            } else if (vTag == 'U') {
                int vlen = rawBytes[i + 1] & 0xFF;
                valEnd = i + 2 + vlen;
            } else if (vTag == 'X') {
                int vlen = ByteBuffer.wrap(rawBytes, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                valEnd = i + 5 + vlen;
            }

            if (valEnd > valStart && valEnd <= n) {
                byte[] encoded = serializeValueProto2(modifications.get(key));
                patches.add(new Patch(valStart, valEnd, encoded));
            }
        }

        // Apply patches in reverse order to preserve offsets
        Collections.sort(patches, (p1, p2) -> Integer.compare(p2.start, p1.start));

        ByteArrayOutputStream baos = new ByteArrayOutputStream(n + 1024);
        int currentPos = 0;
        // Sort ascending for progressive copy
        List<Patch> asc = new ArrayList<>(patches);
        Collections.sort(asc, (p1, p2) -> Integer.compare(p1.start, p2.start));

        for (Patch p : asc) {
            if (p.start > currentPos) {
                baos.write(rawBytes, currentPos, p.start - currentPos);
            }
            baos.write(p.newBytes, 0, p.newBytes.length);
            currentPos = p.end;
        }
        if (currentPos < n) {
            baos.write(rawBytes, currentPos, n - currentPos);
        }

        return baos.toByteArray();
    }

    /**
     * Intelligent Gallery and CG unlocking algorithm.
     * Searches and activates all gallery flags, unlocks CG lists, and flips completion bools.
     */
    public static byte[] unlockGalleryBytes(byte[] rawBytes, boolean isSave) {
        byte[] copy = rawBytes.clone();
        int n = copy.length;
        int i = 0;

        while (i < n - 3) {
            int tag = copy[i] & 0xFF;
            String key = null;

            if (tag == 'U') {
                int slen = copy[i + 1] & 0xFF;
                if (i + 2 + slen <= n) {
                    key = new String(copy, i + 2, slen, StandardCharsets.ISO_8859_1);
                }
                i += 2 + slen;
            } else if (tag == 'X') {
                if (i + 5 <= n) {
                    int slen = ByteBuffer.wrap(copy, i + 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                    if (slen >= 0 && i + 5 + slen <= n) {
                        key = new String(copy, i + 5, slen, StandardCharsets.UTF_8);
                    }
                    i += 5 + slen;
                } else {
                    i++;
                    continue;
                }
            } else {
                i++;
                continue;
            }

            if (key == null || key.length() < 2) continue;

            // Skip BINPUT / LONG_BINPUT
            if (i < n && (copy[i] & 0xFF) == 'q') {
                i += 2;
            } else if (i + 4 < n && (copy[i] & 0xFF) == 'r') {
                i += 5;
            }

            if (i >= n) break;

            String lower = key.toLowerCase(Locale.ROOT);
            boolean isGalleryKey = lower.contains("gallery") || lower.contains("clear") || lower.contains("unlock")
                    || lower.contains("ending") || lower.contains("scene") || lower.contains("cg") || lower.contains("seen_")
                    || lower.contains("achievement");

            if (isGalleryKey) {
                int vTag = copy[i] & 0xFF;
                if (vTag == 0x89 || vTag == 'N') { // NEWFALSE or NONE -> NEWTRUE (0x88)
                    copy[i] = (byte) 0x88;
                } else if (vTag == 'K' && i + 1 < n && copy[i + 1] == 0) { // 0 -> 1
                    copy[i + 1] = 1;
                }
            }
        }

        // Unlock boolean lists (e.g. persistent.clear = [False, False, ...])
        for (int j = 1; j < n - 1; j++) {
            if ((copy[j] & 0xFF) == 0x89) { // NEWFALSE
                int prev = copy[j - 1] & 0xFF;
                int next = copy[j + 1] & 0xFF;
                // Check if inside a list or tuple of bools
                if ((prev == 0x88 || prev == 0x89 || prev == '(') || (next == 0x88 || next == 0x89 || next == ']' || next == 'e')) {
                    copy[j] = (byte) 0x88; // Turn to NEWTRUE
                }
            }
        }

        return copy;
    }

    /**
     * Unlocks the gallery of the target file, creates a backup, and saves the file.
     */
    public static File unlockGallery(Context context, File targetFile) throws Exception {
        boolean isSave = isZipFile(targetFile);
        byte[] pickleBytes = extractPickleBytes(targetFile, isSave);
        byte[] unlocked = unlockGalleryBytes(pickleBytes, isSave);

        return writeModifiedFile(targetFile, isSave, unlocked);
    }

    /**
     * Saves user modifications to the target file with an automatic .bak backup.
     */
    public static File saveWithBackup(Context context, File targetFile, Map<String, Object> modifications) throws Exception {
        boolean isSave = isZipFile(targetFile);
        byte[] pickleBytes = extractPickleBytes(targetFile, isSave);
        byte[] patched = patchPickleBytes(pickleBytes, modifications);

        return writeModifiedFile(targetFile, isSave, patched);
    }

    private static File writeModifiedFile(File targetFile, boolean isSave, byte[] newPickleData) throws Exception {
        File bak = new File(targetFile.getParentFile(), targetFile.getName() + ".bak");
        if (bak.exists()) bak.delete();

        // Copy original to backup
        try (InputStream in = new FileInputStream(targetFile);
             OutputStream out = new FileOutputStream(bak)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
        }

        if (isSave) {
            // Rewrite zip with patched 'log' entry
            File tmpZip = new File(targetFile.getParentFile(), targetFile.getName() + ".tmp");
            try (ZipFile zin = new ZipFile(bak);
                 ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(tmpZip))) {
                Enumeration<? extends ZipEntry> entries = zin.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    ZipEntry newEntry = new ZipEntry(entry.getName());
                    zout.putNextEntry(newEntry);
                    if ("log".equals(entry.getName())) {
                        zout.write(newPickleData);
                    } else {
                        try (InputStream is = zin.getInputStream(entry)) {
                            byte[] b = new byte[8192];
                            int len;
                            while ((len = is.read(b)) != -1) {
                                zout.write(b, 0, len);
                            }
                        }
                    }
                    zout.closeEntry();
                }
            }
            if (!tmpZip.renameTo(targetFile)) {
                targetFile.delete();
                tmpZip.renameTo(targetFile);
            }
        } else {
            // Re-compress persistent file with zlib
            byte[] compressed = compressZlib(newPickleData);
            try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                fos.write(compressed);
            }
        }

        return bak;
    }
}

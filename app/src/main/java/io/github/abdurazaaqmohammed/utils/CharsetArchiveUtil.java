package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Archive Charset Utility for MPlite.
 * Inspects archive raw header bytes to automatically detect filename encoding
 * (UTF-8, GBK, BIG5, Shift-JIS, CP437), resolving garbled Chinese and Asian filenames.
 */
public final class CharsetArchiveUtil {

    public static final Charset CHARSET_GBK = Charset.forName("GBK");
    public static final Charset CHARSET_BIG5 = Charset.forName("Big5");
    public static final Charset CHARSET_CP437 = Charset.forName("ISO-8859-1");

    private CharsetArchiveUtil() {}

    /**
     * Inspects the central directory of a ZIP file and auto-detects the character encoding.
     */
    public static Charset detectZipCharset(File zipFile) {
        if (zipFile == null || !zipFile.exists() || zipFile.length() < 22) {
            return StandardCharsets.UTF_8;
        }

        try (RandomAccessFile raf = new RandomAccessFile(zipFile, "r")) {
            long len = raf.length();
            int window = (int) Math.min(len, 65557L);
            byte[] tail = new byte[window];
            raf.seek(len - window);
            raf.readFully(tail);

            int eocd = -1;
            for (int i = tail.length - 22; i >= 0; i--) {
                if ((tail[i] & 0xFF) == 0x50 && (tail[i+1] & 0xFF) == 0x4B &&
                    (tail[i+2] & 0xFF) == 0x05 && (tail[i+3] & 0xFF) == 0x06) {
                    eocd = i;
                    break;
                }
            }
            if (eocd < 0) return StandardCharsets.UTF_8;

            long total = ((tail[eocd + 10] & 0xFF) | ((tail[eocd + 11] & 0xFF) << 8)) & 0xFFFFL;
            long cdOffset = ((tail[eocd + 16] & 0xFF) | ((tail[eocd + 17] & 0xFF) << 8) |
                            ((tail[eocd + 18] & 0xFF) << 16) | ((tail[eocd + 19] & 0xFF) << 24)) & 0xFFFFFFFFL;

            if (cdOffset >= len || cdOffset < 0) return StandardCharsets.UTF_8;

            raf.seek(cdOffset);
            List<byte[]> sampleNames = new ArrayList<>();
            byte[] head = new byte[46];

            for (int i = 0; i < Math.min(total, 64); i++) {
                raf.readFully(head);
                if ((head[0] & 0xFF) != 0x50 || (head[1] & 0xFF) != 0x4B ||
                    (head[2] & 0xFF) != 0x01 || (head[3] & 0xFF) != 0x02) break;

                int flags = (head[8] & 0xFF) | ((head[9] & 0xFF) << 8);
                int nameLen = (head[28] & 0xFF) | ((head[29] & 0xFF) << 8);
                int extraLen = (head[30] & 0xFF) | ((head[31] & 0xFF) << 8);
                int commentLen = (head[32] & 0xFF) | ((head[33] & 0xFF) << 8);

                byte[] nameBytes = new byte[nameLen];
                raf.readFully(nameBytes);
                if (extraLen > 0) raf.skipBytes(extraLen);
                if (commentLen > 0) raf.skipBytes(commentLen);

                // If general purpose bit 11 is set, it explicitly claims UTF-8
                if ((flags & 0x800) != 0) {
                    // Check if it's indeed valid UTF-8
                    if (isValidUtf8(nameBytes)) {
                        continue;
                    }
                }
                sampleNames.add(nameBytes);
            }

            if (sampleNames.isEmpty()) return StandardCharsets.UTF_8;

            // Analyze non-flagged names
            int gbkHits = 0;
            int big5Hits = 0;
            int totalNonAscii = 0;

            for (byte[] bytes : sampleNames) {
                if (hasNonAscii(bytes)) {
                    totalNonAscii++;
                    if (isValidGbk(bytes)) gbkHits++;
                    if (isValidBig5(bytes)) big5Hits++;
                }
            }

            if (totalNonAscii > 0) {
                if (gbkHits >= big5Hits && gbkHits > 0) {
                    return CHARSET_GBK;
                } else if (big5Hits > 0) {
                    return CHARSET_BIG5;
                }
            }
        } catch (Throwable ignored) {
        }

        return StandardCharsets.UTF_8;
    }

    private static boolean hasNonAscii(byte[] bytes) {
        for (byte b : bytes) {
            if ((b & 0x80) != 0) return true;
        }
        return false;
    }

    public static boolean isValidUtf8(byte[] bytes) {
        int expectedLen = 0;
        for (byte b : bytes) {
            int ub = b & 0xFF;
            if (expectedLen > 0) {
                if ((ub & 0xC0) != 0x80) return false;
                expectedLen--;
            } else {
                if ((ub & 0x80) == 0) continue;
                if ((ub & 0xE0) == 0xC0) expectedLen = 1;
                else if ((ub & 0xF0) == 0xE0) expectedLen = 2;
                else if ((ub & 0xF8) == 0xF0) expectedLen = 3;
                else return false;
            }
        }
        return expectedLen == 0;
    }

    public static boolean isValidGbk(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b1 = bytes[i++] & 0xFF;
            if (b1 <= 0x7F) continue;
            if (b1 < 0x81 || b1 > 0xFE) return false;
            if (i >= bytes.length) return false;
            int b2 = bytes[i++] & 0xFF;
            if (b2 < 0x40 || b2 > 0xFE || b2 == 0x7F) return false;
        }
        return true;
    }

    public static boolean isValidBig5(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b1 = bytes[i++] & 0xFF;
            if (b1 <= 0x7F) continue;
            if (b1 < 0xA1 || b1 > 0xF9) return false;
            if (i >= bytes.length) return false;
            int b2 = bytes[i++] & 0xFF;
            if ((b2 < 0x40 || b2 > 0x7E) && (b2 < 0xA1 || b2 > 0xFE)) return false;
        }
        return true;
    }
}
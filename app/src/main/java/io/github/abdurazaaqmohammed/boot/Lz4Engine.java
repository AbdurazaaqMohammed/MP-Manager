package io.github.abdurazaaqmohammed.boot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Pure Java LZ4 implementation supporting both:
 * 1. LZ4 Legacy format (Magic: 0x184C2102) used by Linux Kernel / Android Boot Ramdisk
 * 2. LZ4 Standard Frame format (Magic: 0x184D2204)
 * 3. Fast & Best LZ4 block compression
 * Zero external dependencies, 100% offline pure Java.
 */
public class Lz4Engine {

    public static final int LEGACY_MAGIC = 0x184C2102;
    public static final int FRAME_MAGIC = 0x184D2204;
    private static final int HASH_LOG = 14;
    private static final int HASH_TABLE_SIZE = 1 << HASH_LOG;

    public static File sNativeLz4Bin = null;

    static {
        File f = new File("/data/user/0/com.agy/files/usr/bin/lz4");
        if (f.exists() && f.canExecute()) {
            sNativeLz4Bin = f;
        }
    }

    /**
     * Decompresses an LZ4 file directly to an output file.
     * Uses native lz4 binary if available, or streaming pure Java fallback.
     */
    public static boolean decompressFile(File inFile, File outFile) {
        if (sNativeLz4Bin != null && sNativeLz4Bin.exists() && sNativeLz4Bin.canExecute()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(sNativeLz4Bin.getAbsolutePath(), "-d", "-f", inFile.getAbsolutePath(), outFile.getAbsolutePath());
                Process p = pb.start();
                if (p.waitFor() == 0 && outFile.exists() && outFile.length() > 0) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // Pure Java stream fallback
        try (FileInputStream fis = new FileInputStream(inFile);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            byte[] magic = new byte[4];
            if (fis.read(magic) < 4) return false;
            int m = (magic[0] & 0xFF) | ((magic[1] & 0xFF) << 8) | ((magic[2] & 0xFF) << 16) | ((magic[3] & 0xFF) << 24);

            if (m == LEGACY_MAGIC) {
                byte[] lenBuf = new byte[4];
                while (fis.read(lenBuf) == 4) {
                    int blockSize = (lenBuf[0] & 0xFF) | ((lenBuf[1] & 0xFF) << 8) | ((lenBuf[2] & 0xFF) << 16) | ((lenBuf[3] & 0xFF) << 24);
                    if (blockSize <= 0 || blockSize > 16 * 1024 * 1024) break;
                    byte[] block = new byte[blockSize];
                    int r = 0;
                    while (r < blockSize) {
                        int read = fis.read(block, r, blockSize - r);
                        if (read == -1) break;
                        r += read;
                    }
                    byte[] decompressed = decompressBlock(block, 0, blockSize, 8 * 1024 * 1024);
                    fos.write(decompressed);
                }
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Compresses a file to LZ4 Legacy format directly on disk.
     */
    public static boolean compressLegacyFile(File inFile, File outFile) {
        if (sNativeLz4Bin != null && sNativeLz4Bin.exists() && sNativeLz4Bin.canExecute()) {
            try {
                ProcessBuilder pb = new ProcessBuilder(sNativeLz4Bin.getAbsolutePath(), "-l", "-12", "-f", inFile.getAbsolutePath(), outFile.getAbsolutePath());
                Process p = pb.start();
                if (p.waitFor() == 0 && outFile.exists() && outFile.length() > 4) {
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // Streaming pure Java LZ4 Legacy compression (8MB chunks)
        try (FileInputStream fis = new FileInputStream(inFile);
             FileOutputStream fos = new FileOutputStream(outFile)) {
            // Write magic 0x184C2102
            fos.write(0x02);
            fos.write(0x21);
            fos.write(0x4C);
            fos.write(0x18);

            int blockSize = 8 * 1024 * 1024;
            byte[] inBuf = new byte[blockSize];
            int r;
            while ((r = readChunk(fis, inBuf)) > 0) {
                byte[] compressed = compressBlock(inBuf, 0, r);
                int sz = compressed.length;
                fos.write(sz & 0xFF);
                fos.write((sz >>> 8) & 0xFF);
                fos.write((sz >>> 16) & 0xFF);
                fos.write((sz >>> 24) & 0xFF);
                fos.write(compressed, 0, sz);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int readChunk(FileInputStream fis, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            int r = fis.read(buf, total, buf.length - total);
            if (r == -1) break;
            total += r;
        }
        return total;
    }

    /**
     * Decompresses an LZ4 stream (auto-detects Legacy block format vs Frame format).
     */
    public static byte[] decompress(byte[] input) throws IOException {
        if (input.length < 4) {
            throw new IOException("Input data too short for LZ4");
        }
        ByteBuffer bb = ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getInt(0);

        if (magic == LEGACY_MAGIC) {
            return decompressLegacy(input);
        } else if (magic == FRAME_MAGIC) {
            return decompressFrame(input);
        } else {
            // Attempt raw block decompression
            return decompressBlock(input, 0, input.length, 64 * 1024 * 1024);
        }
    }

    private static byte[] decompressLegacy(byte[] input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length * 3);
        int offset = 4; // skip magic

        while (offset + 4 <= input.length) {
            int blockSize = (input[offset] & 0xFF) |
                    ((input[offset + 1] & 0xFF) << 8) |
                    ((input[offset + 2] & 0xFF) << 16) |
                    ((input[offset + 3] & 0xFF) << 24);
            offset += 4;

            if (blockSize <= 0 || offset + blockSize > input.length) {
                break;
            }

            byte[] decompressedBlock = decompressBlock(input, offset, blockSize, 8 * 1024 * 1024);
            out.write(decompressedBlock);
            offset += blockSize;
        }

        return out.toByteArray();
    }

    private static byte[] decompressFrame(byte[] input) throws IOException {
        int offset = 4;
        int flg = input[offset++] & 0xFF;
        int bd = input[offset++] & 0xFF;
        if ((flg & 0x08) != 0) {
            offset += 8;
        }
        if ((flg & 0x01) != 0) {
            offset += 4;
        }
        offset++;

        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length * 3);
        while (offset + 4 <= input.length) {
            int blockSize = (input[offset] & 0xFF) |
                    ((input[offset + 1] & 0xFF) << 8) |
                    ((input[offset + 2] & 0xFF) << 16) |
                    ((input[offset + 3] & 0xFF) << 24);
            offset += 4;

            if (blockSize == 0) break;

            boolean uncompressed = (blockSize & 0x80000000) != 0;
            blockSize &= 0x7FFFFFFF;

            if (uncompressed) {
                out.write(input, offset, blockSize);
            } else {
                byte[] decompressed = decompressBlock(input, offset, blockSize, 4 * 1024 * 1024);
                out.write(decompressed);
            }
            offset += blockSize;
        }
        return out.toByteArray();
    }

    public static byte[] decompressBlock(byte[] src, int srcOffset, int srcLen, int maxDestLen) throws IOException {
        byte[] dest = new byte[Math.max(srcLen * 4, 65536)];
        int srcIdx = srcOffset;
        int srcEnd = srcOffset + srcLen;
        int destIdx = 0;

        while (srcIdx < srcEnd) {
            int token = src[srcIdx++] & 0xFF;
            int literalLen = token >>> 4;

            if (literalLen == 15) {
                int s = 0;
                while (srcIdx < srcEnd && (s = src[srcIdx++] & 0xFF) == 255) {
                    literalLen += 255;
                }
                if (srcIdx <= srcEnd) {
                    literalLen += s;
                }
            }

            if (destIdx + literalLen > dest.length) {
                dest = Arrays.copyOf(dest, Math.max(dest.length * 2, destIdx + literalLen + 65536));
            }

            System.arraycopy(src, srcIdx, dest, destIdx, literalLen);
            srcIdx += literalLen;
            destIdx += literalLen;

            if (srcIdx >= srcEnd) {
                break;
            }

            int offset = (src[srcIdx++] & 0xFF) | ((src[srcIdx++] & 0xFF) << 8);
            if (offset == 0) {
                throw new IOException("Malformed LZ4 stream: match offset 0");
            }

            int matchLen = (token & 0x0F) + 4;
            if (matchLen == 19) {
                int s = 0;
                while (srcIdx < srcEnd && (s = src[srcIdx++] & 0xFF) == 255) {
                    matchLen += 255;
                }
                if (srcIdx <= srcEnd) {
                    matchLen += s;
                }
            }

            if (destIdx + matchLen > dest.length) {
                dest = Arrays.copyOf(dest, Math.max(dest.length * 2, destIdx + matchLen + 65536));
            }

            int matchPos = destIdx - offset;
            if (matchPos < 0) {
                throw new IOException("Malformed LZ4 stream: match offset out of range");
            }

            for (int i = 0; i < matchLen; i++) {
                dest[destIdx++] = dest[matchPos++];
            }
        }

        return Arrays.copyOf(dest, destIdx);
    }

    /**
     * Compresses data into LZ4 Legacy format (0x184C2102) matching Linux/Android boot ramdisk.
     */
    public static byte[] compressLegacy(byte[] src) {
        if (sNativeLz4Bin != null && sNativeLz4Bin.exists() && sNativeLz4Bin.canExecute()) {
            try {
                File tempIn = File.createTempFile("cpio_", ".raw");
                File tempOut = File.createTempFile("lz4_", ".raw");
                try (FileOutputStream fos = new FileOutputStream(tempIn)) {
                    fos.write(src);
                }

                ProcessBuilder pb = new ProcessBuilder(sNativeLz4Bin.getAbsolutePath(), "-l", "-12", "-f", tempIn.getAbsolutePath(), tempOut.getAbsolutePath());
                Process p = pb.start();
                int exit = p.waitFor();

                if (exit == 0 && tempOut.exists() && tempOut.length() > 4) {
                    byte[] res = new byte[(int) tempOut.length()];
                    try (FileInputStream fis = new FileInputStream(tempOut)) {
                        fis.read(res);
                    }
                    tempIn.delete();
                    tempOut.delete();
                    return res;
                }
                tempIn.delete();
                tempOut.delete();
            } catch (Exception ignored) {}
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(src.length);
        out.write(0x02);
        out.write(0x21);
        out.write(0x4C);
        out.write(0x18);

        int blockSize = 8 * 1024 * 1024;
        int offset = 0;

        while (offset < src.length) {
            int chunkLen = Math.min(blockSize, src.length - offset);
            byte[] compressed = compressBlock(src, offset, chunkLen);

            int sz = compressed.length;
            out.write(sz & 0xFF);
            out.write((sz >>> 8) & 0xFF);
            out.write((sz >>> 16) & 0xFF);
            out.write((sz >>> 24) & 0xFF);

            out.write(compressed, 0, compressed.length);
            offset += chunkLen;
        }

        return out.toByteArray();
    }

    public static byte[] compressBlock(byte[] src, int srcOffset, int srcLen) {
        if (srcLen == 0) return new byte[0];

        int[] hashTable = new int[HASH_TABLE_SIZE];
        Arrays.fill(hashTable, -1);

        byte[] dest = new byte[srcLen + (srcLen / 255) + 64];
        int srcIdx = srcOffset;
        int srcEnd = srcOffset + srcLen;
        int anchor = srcIdx;
        int destIdx = 0;
        int mflimit = srcEnd - 12;
        int matchlimit = srcEnd - 5;

        while (srcIdx < mflimit) {
            int hash = hash4(src, srcIdx);
            int matchPos = hashTable[hash];
            hashTable[hash] = srcIdx;

            if (matchPos >= srcOffset && srcIdx - matchPos < 65536 && srcIdx - matchPos > 0 &&
                    src[matchPos] == src[srcIdx] &&
                    src[matchPos + 1] == src[srcIdx + 1] &&
                    src[matchPos + 2] == src[srcIdx + 2] &&
                    src[matchPos + 3] == src[srcIdx + 3]) {

                int literalLen = srcIdx - anchor;
                int matchLen = 4;
                while (srcIdx + matchLen < matchlimit && src[matchPos + matchLen] == src[srcIdx + matchLen]) {
                    matchLen++;
                }

                int token = Math.min(literalLen, 15) << 4;
                int tokenMatch = Math.min(matchLen - 4, 15);
                token |= tokenMatch;

                dest[destIdx++] = (byte) token;

                if (literalLen >= 15) {
                    int rem = literalLen - 15;
                    while (rem >= 255) {
                        dest[destIdx++] = (byte) 255;
                        rem -= 255;
                    }
                    dest[destIdx++] = (byte) rem;
                }

                System.arraycopy(src, anchor, dest, destIdx, literalLen);
                destIdx += literalLen;

                int offsetVal = srcIdx - matchPos;
                dest[destIdx++] = (byte) (offsetVal & 0xFF);
                dest[destIdx++] = (byte) ((offsetVal >>> 8) & 0xFF);

                if (matchLen - 4 >= 15) {
                    int rem = matchLen - 4 - 15;
                    while (rem >= 255) {
                        dest[destIdx++] = (byte) 255;
                        rem -= 255;
                    }
                    dest[destIdx++] = (byte) rem;
                }

                srcIdx += matchLen;
                anchor = srcIdx;
            } else {
                srcIdx++;
            }
        }

        int literalLen = srcEnd - anchor;
        int token = Math.min(literalLen, 15) << 4;
        dest[destIdx++] = (byte) token;
        if (literalLen >= 15) {
            int rem = literalLen - 15;
            while (rem >= 255) {
                dest[destIdx++] = (byte) 255;
                rem -= 255;
            }
            dest[destIdx++] = (byte) rem;
        }
        System.arraycopy(src, anchor, dest, destIdx, literalLen);
        destIdx += literalLen;

        return Arrays.copyOf(dest, destIdx);
    }

    private static int hash4(byte[] b, int i) {
        int v = (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8) | ((b[i + 2] & 0xFF) << 16) | ((b[i + 3] & 0xFF) << 24);
        return (v * -1640531535) >>> (32 - HASH_LOG);
    }
}

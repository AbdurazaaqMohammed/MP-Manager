package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Preserves and restores the APK Signing Block (Scheme v2/v3/v4) across archive modifications.
 *
 * When ZIP libraries (like zip4j) append or update entries in an APK, they write a new
 * Central Directory immediately following the new data, leaving any existing APK Signing
 * Block detached or buried in the middle of the file. This utility allows extracting the
 * APK Signing Block before modification and re-injecting it directly before the newly written
 * Central Directory, matching MT Manager's behavior and keeping original developer signature
 * blocks intact and readable for signature-bypass and LSPatch workflows.
 */
public final class ApkSignaturePreserver {

    private static final byte[] APK_SIG_BLOCK_MAGIC = new byte[]{
            'A', 'P', 'K', ' ', 'S', 'i', 'g', ' ', 'B', 'l', 'o', 'c', 'k', ' ', '4', '2'
    };

    private ApkSignaturePreserver() {}

    /**
     * Extracts the complete APK Signing Block from the given APK file, if present.
     * Returns null if no valid APK Signing Block is found.
     */
    public static byte[] extractSigningBlock(File apkFile) {
        if (apkFile == null || !apkFile.isFile()) return null;
        try (RandomAccessFile raf = new RandomAccessFile(apkFile, "r")) {
            long length = raf.length();
            if (length < 32) return null;

            long eocdOffset = findEOCD(raf, length);
            if (eocdOffset == -1) return null;

            raf.seek(eocdOffset + 16);
            byte[] cdOffsetBytes = new byte[4];
            raf.readFully(cdOffsetBytes);
            long cdOffset = ByteBuffer.wrap(cdOffsetBytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

            if (cdOffset < 32 || cdOffset > length) return null;

            // Check the 16-byte magic directly before the Central Directory
            raf.seek(cdOffset - 16);
            byte[] magic = new byte[16];
            raf.readFully(magic);
            if (!Arrays.equals(magic, APK_SIG_BLOCK_MAGIC)) return null;

            // Read the 8-byte block size preceding the magic
            raf.seek(cdOffset - 24);
            byte[] sizeBytes = new byte[8];
            raf.readFully(sizeBytes);
            long blockSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();

            if (blockSize < 24 || blockSize > (length - 32)) return null;

            long totalBlockLen = blockSize + 8; // includes the leading 8-byte size field
            long blockStart = cdOffset - totalBlockLen;
            if (blockStart < 0) return null;

            // Verify the leading 8-byte size matches
            raf.seek(blockStart);
            byte[] leadSizeBytes = new byte[8];
            raf.readFully(leadSizeBytes);
            long leadSize = ByteBuffer.wrap(leadSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
            if (leadSize != blockSize) return null;

            // Read the entire block
            raf.seek(blockStart);
            byte[] blockData = new byte[(int) totalBlockLen];
            raf.readFully(blockData);
            return blockData;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Injects an APK Signing Block immediately before the Central Directory of the APK,
     * updating the Central Directory offset in the End of Central Directory record.
     */
    public static boolean injectSigningBlock(File apkFile, byte[] sigBlock) {
        if (apkFile == null || !apkFile.isFile() || sigBlock == null || sigBlock.length == 0) {
            return false;
        }
        try (RandomAccessFile raf = new RandomAccessFile(apkFile, "rw")) {
            long length = raf.length();
            if (length < 22) return false;

            long eocdOffset = findEOCD(raf, length);
            if (eocdOffset == -1) return false;

            // Read Central Directory size and offset from EOCD
            raf.seek(eocdOffset + 12);
            byte[] cdSizeBytes = new byte[4];
            raf.readFully(cdSizeBytes);
            long cdSize = ByteBuffer.wrap(cdSizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

            raf.seek(eocdOffset + 16);
            byte[] cdOffsetBytes = new byte[4];
            raf.readFully(cdOffsetBytes);
            long cdOffset = ByteBuffer.wrap(cdOffsetBytes).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xFFFFFFFFL;

            if (cdOffset + cdSize > length) return false;

            // If the block is already present at this exact position, do nothing
            if (cdOffset >= sigBlock.length) {
                raf.seek(cdOffset - 16);
                byte[] magic = new byte[16];
                raf.readFully(magic);
                if (Arrays.equals(magic, APK_SIG_BLOCK_MAGIC)) {
                    return true;
                }
            }

            // Read Central Directory data
            raf.seek(cdOffset);
            byte[] cdData = new byte[(int) cdSize];
            raf.readFully(cdData);

            // Read EOCD record and optional trailing comment
            int eocdAndCommentLen = (int) (length - eocdOffset);
            raf.seek(eocdOffset);
            byte[] eocdData = new byte[eocdAndCommentLen];
            raf.readFully(eocdData);

            // Write sigBlock at cdOffset
            raf.seek(cdOffset);
            raf.write(sigBlock);

            // Write Central Directory data directly after sigBlock
            raf.write(cdData);

            // Update Central Directory offset in EOCD
            long newCdOffset = cdOffset + sigBlock.length;
            ByteBuffer bb = ByteBuffer.wrap(eocdData).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(16, (int) newCdOffset);

            // Write updated EOCD record
            raf.write(eocdData);

            // Truncate in case original file was longer
            raf.setLength(raf.getFilePointer());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static long findEOCD(RandomAccessFile raf, long length) throws Exception {
        int maxSearch = (int) Math.min(length, 65536L + 22L);
        long searchStart = length - maxSearch;
        raf.seek(searchStart);
        byte[] buffer = new byte[maxSearch];
        raf.readFully(buffer);

        for (int i = maxSearch - 22; i >= 0; i--) {
            if (buffer[i] == 0x50 && buffer[i + 1] == 0x4B && buffer[i + 2] == 0x05 && buffer[i + 3] == 0x06) {
                return searchStart + i;
            }
        }
        return -1;
    }
}

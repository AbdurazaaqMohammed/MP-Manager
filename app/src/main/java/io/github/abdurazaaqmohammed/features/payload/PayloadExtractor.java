package io.github.abdurazaaqmohammed.features.payload;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;

import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.ProgressManager;

/**
 * Extracts partition images (.img) from Android OTA payload.bin.
 * Handles RAW (REPLACE), XZ, BZIP2, and ZERO block operations.
 */
public final class PayloadExtractor {

    private PayloadExtractor() {
    }

    public static File extractPartition(File payloadFile, PayloadParser.PayloadInfo info,
                                        PayloadEntry entry, File outputDir,
                                        ProgressManager pm) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        File outFile = new File(outputDir, entry.getFileName());

        try (RandomAccessFile src = new RandomAccessFile(payloadFile, "r");
             RandomAccessFile dst = new RandomAccessFile(outFile, "rw")) {

            int blockSize = info.blockSize > 0 ? info.blockSize : 4096;
            long totalOps = entry.operations.size();
            long processedOps = 0;
            byte[] zeroBlock = new byte[blockSize];

            for (PayloadEntry.InstallOp op : entry.operations) {
                if (pm != null && pm.isCancelling()) {
                    throw new IOException("Extraction cancelled by user");
                }

                if (op.type == PayloadEntry.InstallOp.TYPE_ZERO) {
                    for (PayloadEntry.Extent ext : op.dstExtents) {
                        dst.seek(ext.startBlock * blockSize);
                        for (long b = 0; b < ext.numBlocks; b++) {
                            dst.write(zeroBlock);
                        }
                    }
                } else if (op.dataLength > 0) {
                    long fileOffset = info.dataOffsetBase + op.dataOffset;
                    src.seek(fileOffset);
                    byte[] data = new byte[(int) op.dataLength];
                    src.readFully(data);

                    byte[] decompressed = decompress(op.type, data);

                    int offset = 0;
                    for (PayloadEntry.Extent ext : op.dstExtents) {
                        dst.seek(ext.startBlock * blockSize);
                        int bytesToWrite = (int) Math.min(ext.numBlocks * blockSize, decompressed.length - offset);
                        if (bytesToWrite > 0) {
                            dst.write(decompressed, offset, bytesToWrite);
                            offset += bytesToWrite;
                        }
                    }
                }

                processedOps++;
                if (pm != null && totalOps > 0) {
                    pm.setProgress((int) processedOps, (int) totalOps);
                }
            }

            if (entry.size > 0) {
                dst.setLength(entry.size);
            }
        }
        return outFile;
    }

    private static byte[] decompress(int type, byte[] data) throws IOException {
        switch (type) {
            case PayloadEntry.InstallOp.TYPE_REPLACE:
                return data;
            case PayloadEntry.InstallOp.TYPE_REPLACE_BZ:
                try (InputStream is = new BZip2CompressorInputStream(new ByteArrayInputStream(data))) {
                    return readAllBytes(is);
                }
            case PayloadEntry.InstallOp.TYPE_REPLACE_XZ:
                try (InputStream is = new XZInputStream(new ByteArrayInputStream(data))) {
                    return readAllBytes(is);
                }
            case PayloadEntry.InstallOp.TYPE_REPLACE_ZSTD:
                throw new IOException("Zstandard OTA payload compression is not supported on this platform");
            default:
                return data;
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buf = new byte[64 * 1024];
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int r;
        while ((r = is.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toByteArray();
    }
}

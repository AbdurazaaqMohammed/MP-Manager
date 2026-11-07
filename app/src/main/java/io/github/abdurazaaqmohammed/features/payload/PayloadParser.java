package io.github.abdurazaaqmohammed.features.payload;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * High-performance, zero-dependency parser for Android OTA payload.bin files.
 * Directly decodes protobuf DeltaArchiveManifest to discover all partitions and operations.
 */
public final class PayloadParser {

    public static final byte[] MAGIC = {'C', 'r', 'A', 'U'};

    public static class PayloadInfo {
        public final File file;
        public final long version;
        public final int blockSize;
        public final long manifestSize;
        public final long metadataSignatureSize;
        public final long dataOffsetBase;
        public final List<PayloadEntry> partitions;

        public PayloadInfo(File file, long version, int blockSize, long manifestSize,
                           long metadataSignatureSize, long dataOffsetBase, List<PayloadEntry> partitions) {
            this.file = file;
            this.version = version;
            this.blockSize = blockSize;
            this.manifestSize = manifestSize;
            this.metadataSignatureSize = metadataSignatureSize;
            this.dataOffsetBase = dataOffsetBase;
            this.partitions = partitions;
        }

        public long getTotalUncompressedSize() {
            long total = 0;
            for (PayloadEntry entry : partitions) {
                total += entry.size;
            }
            return total;
        }
    }

    private PayloadParser() {
    }

    public static boolean isPayload(File file) {
        if (file == null || !file.isFile() || file.length() < 24) return false;
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] magic = new byte[4];
            raf.readFully(magic);
            return magic[0] == MAGIC[0] && magic[1] == MAGIC[1] && magic[2] == MAGIC[2] && magic[3] == MAGIC[3];
        } catch (Exception ignored) {
            return false;
        }
    }

    public static PayloadInfo parse(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] magic = new byte[4];
            raf.readFully(magic);
            if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1] || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) {
                throw new IOException("Not a valid Android OTA payload: invalid CrAU magic");
            }

            long version = raf.readLong();
            long manifestSize = raf.readLong();
            long metadataSigSize = 0;
            long headerLen = 4 + 8 + 8;
            if (version >= 2) {
                metadataSigSize = raf.readInt() & 0xFFFFFFFFL;
                headerLen += 4;
            }

            if (manifestSize <= 0 || manifestSize > 64 * 1024 * 1024) {
                throw new IOException("Invalid manifest size: " + manifestSize);
            }

            byte[] manifestBytes = new byte[(int) manifestSize];
            raf.readFully(manifestBytes);

            long dataOffsetBase = headerLen + manifestSize + metadataSigSize;

            ByteBuffer buf = ByteBuffer.wrap(manifestBytes);
            int blockSize = 4096;
            List<PayloadEntry> partitions = new ArrayList<>();

            while (buf.hasRemaining()) {
                long tag = readVarint(buf);
                int fieldNum = (int) (tag >>> 3);
                int wireType = (int) (tag & 0x07);

                if (fieldNum == 3 && wireType == 0) { // block_size
                    blockSize = (int) readVarint(buf);
                } else if (fieldNum == 13 && wireType == 2) { // repeated PartitionUpdate
                    int len = (int) readVarint(buf);
                    int limit = buf.position() + len;
                    PayloadEntry entry = parsePartitionUpdate(buf, limit);
                    if (entry != null) {
                        partitions.add(entry);
                    }
                    buf.position(limit);
                } else {
                    skipField(buf, wireType);
                }
            }

            return new PayloadInfo(file, version, blockSize, manifestSize, metadataSigSize, dataOffsetBase, partitions);
        }
    }

    private static PayloadEntry parsePartitionUpdate(ByteBuffer buf, int limit) {
        String name = "";
        long size = 0;
        byte[] hash = null;
        List<PayloadEntry.InstallOp> ops = new ArrayList<>();

        while (buf.position() < limit) {
            long tag = readVarint(buf);
            int fieldNum = (int) (tag >>> 3);
            int wireType = (int) (tag & 0x07);

            if (fieldNum == 1 && wireType == 2) { // partition_name
                int len = (int) readVarint(buf);
                byte[] nb = new byte[len];
                buf.get(nb);
                name = new String(nb, StandardCharsets.UTF_8);
            } else if (fieldNum == 7 && wireType == 2) { // new_partition_info
                int len = (int) readVarint(buf);
                int infoLimit = buf.position() + len;
                while (buf.position() < infoLimit) {
                    long itag = readVarint(buf);
                    int ifn = (int) (itag >>> 3);
                    int iwt = (int) (itag & 0x07);
                    if (ifn == 1 && iwt == 0) {
                        size = readVarint(buf);
                    } else if (ifn == 2 && iwt == 2) {
                        int hlen = (int) readVarint(buf);
                        hash = new byte[hlen];
                        buf.get(hash);
                    } else {
                        skipField(buf, iwt);
                    }
                }
                buf.position(infoLimit);
            } else if (fieldNum == 8 && wireType == 2) { // repeated InstallOperation
                int len = (int) readVarint(buf);
                int opLimit = buf.position() + len;
                PayloadEntry.InstallOp op = parseInstallOp(buf, opLimit);
                if (op != null) {
                    ops.add(op);
                }
                buf.position(opLimit);
            } else {
                skipField(buf, wireType);
            }
        }

        if (name.isEmpty()) return null;
        PayloadEntry entry = new PayloadEntry(name, size, hash);
        for (PayloadEntry.InstallOp op : ops) {
            entry.operations.add(op);
            entry.compressionTypes.add(op.getTypeName());
        }
        return entry;
    }

    private static PayloadEntry.InstallOp parseInstallOp(ByteBuffer buf, int limit) {
        int type = 0;
        long dataOffset = 0;
        long dataLength = 0;
        List<PayloadEntry.Extent> extents = new ArrayList<>();

        while (buf.position() < limit) {
            long tag = readVarint(buf);
            int fieldNum = (int) (tag >>> 3);
            int wireType = (int) (tag & 0x07);

            if (fieldNum == 1 && wireType == 0) {
                type = (int) readVarint(buf);
            } else if (fieldNum == 2 && wireType == 0) {
                dataOffset = readVarint(buf);
            } else if (fieldNum == 3 && wireType == 0) {
                dataLength = readVarint(buf);
            } else if (fieldNum == 6 && wireType == 2) { // repeated Extent dst_extents
                int len = (int) readVarint(buf);
                int extLimit = buf.position() + len;
                PayloadEntry.Extent ext = parseExtent(buf, extLimit);
                if (ext != null) {
                    extents.add(ext);
                }
                buf.position(extLimit);
            } else {
                skipField(buf, wireType);
            }
        }

        PayloadEntry.InstallOp op = new PayloadEntry.InstallOp(type, dataOffset, dataLength);
        op.dstExtents.addAll(extents);
        return op;
    }

    private static PayloadEntry.Extent parseExtent(ByteBuffer buf, int limit) {
        long startBlock = 0;
        long numBlocks = 0;

        while (buf.position() < limit) {
            long tag = readVarint(buf);
            int fieldNum = (int) (tag >>> 3);
            int wireType = (int) (tag & 0x07);

            if (fieldNum == 1 && wireType == 0) {
                startBlock = readVarint(buf);
            } else if (fieldNum == 2 && wireType == 0) {
                numBlocks = readVarint(buf);
            } else {
                skipField(buf, wireType);
            }
        }
        return new PayloadEntry.Extent(startBlock, numBlocks);
    }

    private static long readVarint(ByteBuffer buf) {
        long result = 0;
        int shift = 0;
        while (buf.hasRemaining()) {
            byte b = buf.get();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift > 64) {
                break;
            }
        }
        return result;
    }

    private static void skipField(ByteBuffer buf, int wireType) {
        switch (wireType) {
            case 0: // Varint
                readVarint(buf);
                break;
            case 1: // 64-bit
                buf.position(buf.position() + 8);
                break;
            case 2: // Length-delimited
                int len = (int) readVarint(buf);
                buf.position(buf.position() + len);
                break;
            case 5: // 32-bit
                buf.position(buf.position() + 4);
                break;
            default:
                break;
        }
    }
}

package io.github.abdurazaaqmohammed.features.payload;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.abdurazaaqmohammed.utils.FileSize;

/**
 * Represents a single partition inside an Android OTA payload.bin.
 */
public class PayloadEntry {

    public static class Extent {
        public final long startBlock;
        public final long numBlocks;

        public Extent(long startBlock, long numBlocks) {
            this.startBlock = startBlock;
            this.numBlocks = numBlocks;
        }
    }

    public static class InstallOp {
        public static final int TYPE_REPLACE = 0;
        public static final int TYPE_REPLACE_BZ = 1;
        public static final int TYPE_MOVE = 2;
        public static final int TYPE_BSDIFF = 3;
        public static final int TYPE_SOURCE_COPY = 4;
        public static final int TYPE_SOURCE_BSDIFF = 5;
        public static final int TYPE_ZERO = 6;
        public static final int TYPE_DISCARD = 7;
        public static final int TYPE_REPLACE_XZ = 8;
        public static final int TYPE_REPLACE_ZSTD = 9;

        public final int type;
        public final long dataOffset;
        public final long dataLength;
        public final List<Extent> dstExtents = new ArrayList<>();

        public InstallOp(int type, long dataOffset, long dataLength) {
            this.type = type;
            this.dataOffset = dataOffset;
            this.dataLength = dataLength;
        }

        public String getTypeName() {
            switch (type) {
                case TYPE_REPLACE: return "RAW";
                case TYPE_REPLACE_BZ: return "BZIP2";
                case TYPE_REPLACE_XZ: return "XZ";
                case TYPE_REPLACE_ZSTD: return "ZSTD";
                case TYPE_ZERO: return "ZERO";
                case TYPE_DISCARD: return "DISCARD";
                default: return "OP_" + type;
            }
        }
    }

    public final String name;
    public final long size;
    public final byte[] hash;
    public final List<InstallOp> operations = new ArrayList<>();
    public final Set<String> compressionTypes = new HashSet<>();

    public PayloadEntry(String name, long size, byte[] hash) {
        this.name = name;
        this.size = size;
        this.hash = hash;
    }

    public String getFileName() {
        return name.endsWith(".img") ? name : (name + ".img");
    }

    public String getDisplaySize() {
        return FileSize.getHumanReadableFileSize(size);
    }

    public String getSha256Hex() {
        if (hash == null || hash.length == 0) return "";
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    public boolean isBootTypeImage() {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("boot") || lower.contains("recovery");
    }
}

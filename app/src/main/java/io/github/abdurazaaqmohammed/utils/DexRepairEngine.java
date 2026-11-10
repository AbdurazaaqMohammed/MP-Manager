package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.zip.Adler32;

public final class DexRepairEngine {

    private static final byte[] DEX_MAGIC_035 = new byte[]{'d', 'e', 'x', '\n', '0', '3', '5', 0};
    private static final int ENDIAN_CONSTANT = 0x12345678;
    private static final int HEADER_SIZE = 0x70;

    private DexRepairEngine() {}

    /**
     * Inspects the given dex file and repairs corrupt/zeroed headers, invalid file sizes,
     * missing signatures, or bad Adler-32 checksums.
     *
     * @param dexFile The DEX file to inspect and repair in-place.
     * @return true if header repair was applied, or false if already valid.
     * @throws Exception If repairing fails or structure cannot be recovered.
     */
    public static boolean repairHeader(File dexFile) throws Exception {
        if (dexFile == null || !dexFile.exists() || dexFile.length() < HEADER_SIZE) {
            throw new IllegalArgumentException("Invalid DEX file: file is too small or missing");
        }

        long fileLen = dexFile.length();
        byte[] header = new byte[HEADER_SIZE];

        try (RandomAccessFile raf = new RandomAccessFile(dexFile, "rw")) {
            raf.seek(0);
            raf.readFully(header);

            boolean needsMagicRepair = !isValidDexMagic(header);
            boolean isAllZeros = isZeroed(header, 0, 40);

            if (needsMagicRepair || isAllZeros) {
                reconstructHeader(raf, fileLen, header);
                return true;
            }

            // If magic is present, ensure fileSize, checksum, and signature are consistent
            ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int recordedSize = buf.getInt(32);
            if (recordedSize <= 0 || recordedSize > fileLen) {
                buf.putInt(32, (int) fileLen);
                updateChecksums(raf, fileLen, buf.array());
                return true;
            }
        }
        return false;
    }

    private static boolean isValidDexMagic(byte[] header) {
        if (header.length < 8) return false;
        return header[0] == 'd' && header[1] == 'e' && header[2] == 'x' && header[3] == '\n'
                && (header[4] == '0' || header[4] == '1')
                && header[7] == 0;
    }

    private static boolean isZeroed(byte[] data, int start, int length) {
        int end = Math.min(data.length, start + length);
        for (int i = start; i < end; i++) {
            if (data[i] != 0) return false;
        }
        return true;
    }

    private static void reconstructHeader(RandomAccessFile raf, long fileLen, byte[] header) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Locate map_list from file
        MapListInfo mapInfo = scanForMapList(raf, fileLen);

        // 2. Write Magic
        System.arraycopy(DEX_MAGIC_035, 0, header, 0, 8);

        // 3. Write Sizes and Standard Constants
        buf.putInt(32, (int) fileLen);
        buf.putInt(36, HEADER_SIZE);
        buf.putInt(40, ENDIAN_CONSTANT);
        buf.putInt(44, 0); // linkSize
        buf.putInt(48, 0); // linkOff

        if (mapInfo != null) {
            buf.putInt(52, mapInfo.mapOff);
            buf.putInt(56, mapInfo.stringIdsSize);
            buf.putInt(60, mapInfo.stringIdsOff);
            buf.putInt(64, mapInfo.typeIdsSize);
            buf.putInt(68, mapInfo.typeIdsOff);
            buf.putInt(72, mapInfo.protoIdsSize);
            buf.putInt(76, mapInfo.protoIdsOff);
            buf.putInt(80, mapInfo.fieldIdsSize);
            buf.putInt(84, mapInfo.fieldIdsOff);
            buf.putInt(88, mapInfo.methodIdsSize);
            buf.putInt(92, mapInfo.methodIdsOff);
            buf.putInt(96, mapInfo.classDefsSize);
            buf.putInt(100, mapInfo.classDefsOff);
            buf.putInt(104, (int) (fileLen - mapInfo.dataOff));
            buf.putInt(108, mapInfo.dataOff);
        } else {
            // Fallback: estimate offsets
            buf.putInt(52, 0);
            buf.putInt(60, HEADER_SIZE);
        }

        updateChecksums(raf, fileLen, header);
    }

    private static void updateChecksums(RandomAccessFile raf, long fileLen, byte[] header) throws Exception {
        // Calculate SHA-1 (from byte 32 to end of file)
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(header, 32, HEADER_SIZE - 32);

        byte[] chunk = new byte[8192];
        raf.seek(HEADER_SIZE);
        long remaining = fileLen - HEADER_SIZE;
        while (remaining > 0) {
            int toRead = (int) Math.min(chunk.length, remaining);
            int n = raf.read(chunk, 0, toRead);
            if (n <= 0) break;
            sha1.update(chunk, 0, n);
            remaining -= n;
        }
        byte[] sig = sha1.digest();
        System.arraycopy(sig, 0, header, 12, 20);

        // Calculate Adler-32 (from byte 12 to end of file)
        Adler32 adler = new Adler32();
        adler.update(header, 12, HEADER_SIZE - 12);
        raf.seek(HEADER_SIZE);
        remaining = fileLen - HEADER_SIZE;
        while (remaining > 0) {
            int toRead = (int) Math.min(chunk.length, remaining);
            int n = raf.read(chunk, 0, toRead);
            if (n <= 0) break;
            adler.update(chunk, 0, n);
            remaining -= n;
        }

        long checksum = adler.getValue();
        ByteBuffer buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(8, (int) checksum);

        // Write header back to file
        raf.seek(0);
        raf.write(header);
    }

    private static class MapListInfo {
        int mapOff;
        int stringIdsSize, stringIdsOff = HEADER_SIZE;
        int typeIdsSize, typeIdsOff;
        int protoIdsSize, protoIdsOff;
        int fieldIdsSize, fieldIdsOff;
        int methodIdsSize, methodIdsOff;
        int classDefsSize, classDefsOff;
        int dataOff = HEADER_SIZE;
    }

    private static MapListInfo scanForMapList(RandomAccessFile raf, long fileLen) {
        try {
            // Read candidate areas starting from end of file backwards
            long searchStart = Math.max(HEADER_SIZE, fileLen - 0x100000); // scan last 1MB
            int searchLen = (int) (fileLen - searchStart);
            byte[] buf = new byte[searchLen];
            raf.seek(searchStart);
            raf.readFully(buf);

            ByteBuffer byteBuf = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);

            for (int relOff = searchLen - 16; relOff >= 0; relOff -= 4) {
                int size = byteBuf.getInt(relOff);
                if (size >= 8 && size <= 25 && relOff + 4 + size * 12 <= searchLen) {
                    MapListInfo info = parseMapItems(byteBuf, relOff, size, (int) (searchStart + relOff), fileLen);
                    if (info != null) {
                        return info;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static MapListInfo parseMapItems(ByteBuffer buf, int relOff, int size, int absMapOff, long fileLen) {
        MapListInfo info = new MapListInfo();
        info.mapOff = absMapOff;
        boolean hasMapList = false;
        boolean hasStringIds = false;

        for (int i = 0; i < size; i++) {
            int itemOff = relOff + 4 + i * 12;
            int type = buf.getShort(itemOff) & 0xffff;
            int unused = buf.getShort(itemOff + 2) & 0xffff;
            int itemSize = buf.getInt(itemOff + 4);
            int offset = buf.getInt(itemOff + 8);

            if (unused != 0 || offset < 0 || offset > fileLen) {
                return null;
            }

            switch (type) {
                case 0x0001: // TYPE_STRING_ID_ITEM
                    info.stringIdsSize = itemSize;
                    info.stringIdsOff = offset;
                    hasStringIds = true;
                    break;
                case 0x0002: // TYPE_TYPE_ID_ITEM
                    info.typeIdsSize = itemSize;
                    info.typeIdsOff = offset;
                    break;
                case 0x0003: // TYPE_PROTO_ID_ITEM
                    info.protoIdsSize = itemSize;
                    info.protoIdsOff = offset;
                    break;
                case 0x0004: // TYPE_FIELD_ID_ITEM
                    info.fieldIdsSize = itemSize;
                    info.fieldIdsOff = offset;
                    break;
                case 0x0005: // TYPE_METHOD_ID_ITEM
                    info.methodIdsSize = itemSize;
                    info.methodIdsOff = offset;
                    break;
                case 0x0006: // TYPE_CLASS_DEF_ITEM
                    info.classDefsSize = itemSize;
                    info.classDefsOff = offset;
                    break;
                case 0x1000: // TYPE_MAP_LIST
                    hasMapList = true;
                    break;
                case 0x2001: // TYPE_STRING_DATA_ITEM
                    if (info.dataOff == HEADER_SIZE) info.dataOff = offset;
                    break;
            }
        }

        return (hasMapList && hasStringIds) ? info : null;
    }
}

package io.github.abdurazaaqmohammed.features.files.network;

import java.nio.charset.StandardCharsets;

/** Node-status queries resolve Windows computer names without logging in or enabling SMB1. */
final class NetBiosNames {
    private NetBiosNames() {}

    static byte[] query(int transaction) {
        byte[] packet = new byte[50];
        packet[0] = (byte) (transaction >>> 8); packet[1] = (byte) transaction;
        packet[5] = 1; // One question.
        packet[12] = 32;
        for (int i = 0; i < 16; i++) {
            int b = i == 0 ? '*' : 0;
            packet[13 + i * 2] = (byte) ('A' + (b >>> 4));
            packet[14 + i * 2] = (byte) ('A' + (b & 15));
        }
        packet[46] = 0; packet[47] = 0x21; // NBSTAT
        packet[48] = 0; packet[49] = 1; // IN
        return packet;
    }

    static String parse(byte[] bytes, int length, int transaction) {
        if (length < 12 || length > bytes.length || u16(bytes, 0) != (transaction & 0xffff)
                || (u16(bytes, 2) & 0x800f) != 0x8000) return null;
        int offset = 12;
        int questions = u16(bytes, 4), answers = u16(bytes, 6);
        if (questions > 1 || answers > 16) return null;
        for (int q = 0; q < questions; q++) {
            offset = skipName(bytes, offset, length);
            if (offset < 0 || offset + 4 > length) return null;
            offset += 4;
        }
        String workstation = null;
        for (int a = 0; a < answers; a++) {
            offset = skipName(bytes, offset, length);
            if (offset < 0 || offset + 10 > length) return null;
            int type = u16(bytes, offset), klass = u16(bytes, offset + 2), size = u16(bytes, offset + 8);
            offset += 10;
            if (size > length - offset) return null;
            if (type == 0x21 && klass == 1 && size > 0) {
                int count = bytes[offset] & 255;
                if (1 + count * 18 > size) return null;
                for (int n = 0; n < count; n++) {
                    int start = offset + 1 + n * 18;
                    if ((u16(bytes, start + 16) & 0x8000) != 0) continue; // Group/workgroup, not a host.
                    String name = new String(bytes, start, 15, StandardCharsets.US_ASCII).trim();
                    if (name.isEmpty() || !name.matches("[A-Za-z0-9_ .-]+")) continue;
                    int suffix = bytes[start + 15] & 255;
                    if (suffix == 0x20) return name; // File server.
                    if (suffix == 0) workstation = name;
                }
            }
            offset += size;
        }
        return workstation;
    }

    private static int u16(byte[] data, int offset) { return (data[offset] & 255) * 256 + (data[offset + 1] & 255); }
    private static int skipName(byte[] data, int offset, int length) {
        for (int labels = 0; labels < 128 && offset < length; labels++) {
            int size = data[offset++] & 255;
            if (size == 0) return offset;
            if ((size & 0xc0) == 0xc0) return offset < length ? offset + 1 : -1;
            if (size > 63 || size > length - offset) return -1;
            offset += size;
        }
        return -1;
    }
}

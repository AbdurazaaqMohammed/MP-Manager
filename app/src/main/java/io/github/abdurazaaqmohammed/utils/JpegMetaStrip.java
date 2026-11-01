package io.github.abdurazaaqmohammed.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class JpegMetaStrip {

    private JpegMetaStrip() {
    }

    public static byte[] readFully(File file) throws IOException {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    public static void writeFully(File file, byte[] data) throws IOException {
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(data);
        }
    }

    public static boolean isJpeg(byte[] data) {
        return data.length >= 2 && data[0] == (byte) 0xFF && data[1] == (byte) 0xD8;
    }

    public static byte[] strip(byte[] data) throws IOException {
        if (!isJpeg(data)) throw new IOException("Not a JPEG");
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(0xFF);
        out.write(0xD8);
        int pos = 2;
        while (pos < data.length) {
            if (data[pos] != (byte) 0xFF) throw new IOException("Bad JPEG structure at " + pos);
            int markerPos = pos;
            do pos++;
            while (pos < data.length && data[pos] == (byte) 0xFF);
            if (pos >= data.length) throw new IOException("Truncated marker");
            int marker = data[pos] & 0xFF;
            pos++;
            if (marker == 0xD9) {
                out.write(0xFF);
                out.write(0xD9);
                break;
            }
            if (marker == 0xDA) {
                if (pos + 2 > data.length) throw new IOException("Truncated SOS header");
                int segLen = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
                if (segLen < 2 || pos + segLen > data.length) throw new IOException("Bad SOS length");
                out.write(data, markerPos, pos - markerPos + segLen);
                pos += segLen;
                pos = copyScanData(data, pos, out);
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(data, markerPos, pos - markerPos);
                continue;
            }
            if (pos + 2 > data.length) throw new IOException("Truncated segment length");
            int segLen = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            if (segLen < 2 || pos + segLen > data.length) throw new IOException("Bad segment length");
            if (keepMarker(marker)) {
                out.write(data, markerPos, pos - markerPos + segLen);
            }
            pos += segLen;
        }
        byte[] result = out.toByteArray();
        if (result.length < 4) throw new IOException("Empty result");
        return result;
    }

    private static boolean keepMarker(int marker) {
        if (marker == 0xE0) return true;
        if (marker >= 0xC0 && marker <= 0xCF) return true;
        if (marker == 0xDC) return true;
        if (marker == 0xDD) return true;
        return marker != 0xFE && (marker < 0xE1 || marker > 0xEF);
    }

    private static int copyScanData(byte[] data, int pos, ByteArrayOutputStream out) throws IOException {
        while (pos < data.length) {
            byte b = data[pos++];
            out.write(b);
            if (b != (byte) 0xFF) continue;
            if (pos >= data.length) throw new IOException("Truncated scan data");
            byte next = data[pos++];
            out.write(next);
            int m = next & 0xFF;
            if (m == 0x00) continue;
            if (m >= 0xD0 && m <= 0xD7) continue;
            if (m == 0xD9) break;
            if (m == 0x01) {
            }
        }
        return pos;
    }

    public static void stripFile(File file) throws IOException {
        byte[] stripped = strip(readFully(file));
        File tmp = new File(file.getParentFile(), file.getName() + ".stripped" + System.currentTimeMillis());
        try {
            writeFully(tmp, stripped);
            writeFully(file, stripped);
        } finally {
            tmp.delete();
        }
    }

    public static void fixJpegMarkerOrder(File file) {
        try {
            byte[] data = readFully(file);
            if (!isJpeg(data)) return;
            // Check if first marker is APP1 (0xFFE1) and an APP0 (0xFFE0) exists after it
            int pos = 2;
            int app1Start = -1, app1Len = -1;
            int app0Start = -1, app0Len = -1;

            while (pos < data.length - 4) {
                if (data[pos] != (byte) 0xFF) break;
                int marker = data[pos + 1] & 0xFF;
                if (marker == 0xDA || marker == 0xD9) break; // SOS or EOI
                int segLen = ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
                int totalSeg = 2 + segLen;
                if (pos + totalSeg > data.length) break;

                if (marker == 0xE1 && app1Start == -1 && app0Start == -1) {
                    app1Start = pos;
                    app1Len = totalSeg;
                } else if (marker == 0xE0 && app1Start == 2 && app0Start == -1) {
                    app0Start = pos;
                    app0Len = totalSeg;
                    break;
                }
                pos += totalSeg;
            }

            // If APP1 is right after SOI (pos == 2) and APP0 exists immediately after APP1, swap them so APP0 comes first
            if (app1Start == 2 && app0Start == 2 + app1Len) {
                byte[] fixed = new byte[data.length];
                fixed[0] = (byte) 0xFF;
                fixed[1] = (byte) 0xD8;
                System.arraycopy(data, app0Start, fixed, 2, app0Len);
                System.arraycopy(data, app1Start, fixed, 2 + app0Len, app1Len);
                int restStart = app0Start + app0Len;
                int restLen = data.length - restStart;
                System.arraycopy(data, restStart, fixed, 2 + app0Len + app1Len, restLen);
                writeFully(file, fixed);
            }
        } catch (Exception ignored) {}
    }
}

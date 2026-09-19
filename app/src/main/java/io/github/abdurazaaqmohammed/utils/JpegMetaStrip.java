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
            pos++;
            while (pos < data.length && data[pos] == (byte) 0xFF) pos++;
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
            if (m == 0x01) continue;
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
}

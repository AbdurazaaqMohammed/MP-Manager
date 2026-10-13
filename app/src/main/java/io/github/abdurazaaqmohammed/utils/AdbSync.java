package io.github.abdurazaaqmohammed.utils;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The ADB {@code sync:} protocol — how {@code adb push} / {@code pull} / {@code ls}
 * actually move bytes.
 *
 * <p>Running a shell command is fine for printing text, but moving a real file
 * needs a binary channel, and this is it. Every frame is a 4-byte ASCII id, a
 * 4-byte big-endian length, then that many payload bytes. The ids are:
 *
 * <ul>
 *   <li>{@code STAT} / {@code LIST} — ask for one path's metadata, or a whole
 *       directory listing;</li>
 *   <li>{@code RECV} — the server streams a file back as {@code DATA} chunks
 *       ended by {@code DONE};</li>
 *   <li>{@code SEND} — the client streams a file up as {@code DATA} chunks ended
 *       by {@code DONE}, and the server answers {@code OKAY} or {@code FAIL}.</li>
 * </ul>
 *
 * <p>One thing that trips people up: unlike the ADB message header (which is
 * little-endian), every integer inside the sync protocol is big-endian.
 */
public final class AdbSync {

    private static final int MAX_CHUNK = 64 * 1024;
    private static final int MAX_PATH = 4096;

    /** Metadata for one path, as the server reports it. */
    public static final class Entry {
        public final int mode;
        public final int size;
        public final int mtime;
        public final String name;

        Entry(int mode, int size, int mtime, String name) {
            this.mode = mode;
            this.size = size;
            this.mtime = mtime;
            this.name = name;
        }

        /** True when the mode bits say "directory". */
        public boolean isDirectory() {
            return (mode & 040000) != 0;
        }

        /** True when the mode bits say "regular file". */
        public boolean isFile() {
            return (mode & 0100000) != 0;
        }

        @Override
        public String toString() {
            return name + " (" + (isDirectory() ? "dir" : "file") + ", " + size + " bytes)";
        }
    }

    private final InputStream in;
    private final OutputStream out;

    /** Wrap the raw stream pair handed back by {@link AdbClient#openService}. */
    public AdbSync(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
    }

    /**
     * List one directory. Sends {@code LIST}, then reads {@code DENT} entries
     * until the server signals {@code DONE}.
     */
    public List<Entry> list(String path) throws IOException {
        send("LIST", path.getBytes(StandardCharsets.UTF_8));
        List<Entry> entries = new ArrayList<>();
        for (;;) {
            Frame frame = readFrame();
            if ("DONE".equals(frame.id)) {
                return entries;
            }
            if ("FAIL".equals(frame.id)) {
                throw new IOException("adb list " + path + ": " + message(frame));
            }
            if (!"DENT".equals(frame.id)) {
                throw new IOException("unexpected sync id '" + frame.id + "' while listing");
            }
            entries.add(parseDent(frame.payload));
        }
    }

    /** Ask for one path's metadata. Returns {@code null} when it does not exist. */
    public Entry stat(String path) throws IOException {
        send("STAT", path.getBytes(StandardCharsets.UTF_8));
        Frame frame = readFrame();
        if ("STAT".equals(frame.id)) {
            return parseStat(frame.payload, "");
        }
        if ("FAIL".equals(frame.id)) {
            return null;
        }
        throw new IOException("unexpected sync id '" + frame.id + "' while stating");
    }

    /** Pull a file: {@code RECV}, then collect {@code DATA} chunks until {@code DONE}. */
    public byte[] pull(String path) throws IOException {
        send("RECV", path.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (;;) {
            Frame frame = readFrame();
            if ("DONE".equals(frame.id)) {
                return data.toByteArray();
            }
            if ("FAIL".equals(frame.id)) {
                throw new IOException("adb pull " + path + ": " + message(frame));
            }
            if (!"DATA".equals(frame.id)) {
                throw new IOException("unexpected sync id '" + frame.id + "' while pulling");
            }
            data.write(frame.payload);
        }
    }

    /**
     * Push a file: {@code SEND} with the mode and path, then stream the bytes as
     * {@code DATA} chunks, close with {@code DONE}, and wait for {@code OKAY}.
     *
     * @param mode unix mode bits to create the file with, e.g. {@code 0644}
     */
    public void push(String path, byte[] data, int mode) throws IOException {
        String header = Integer.toOctalString(mode & 07777) + "," + path;
        send("SEND", header.getBytes(StandardCharsets.UTF_8));
        int offset = 0;
        while (offset < data.length) {
            int len = Math.min(MAX_CHUNK, data.length - offset);
            byte[] chunk = new byte[len];
            System.arraycopy(data, offset, chunk, 0, len);
            send("DATA", chunk);
            offset += len;
        }
        // Zero-length DONE is the classic end-of-transfer marker every adbd
        // accepts; some newer servers also read an mtime here, but zero is safe.
        send("DONE", new byte[0]);
        Frame frame = readFrame();
        if ("OKAY".equals(frame.id)) {
            return;
        }
        throw new IOException("adb push " + path + " not accepted: " + message(frame));
    }

    private void send(String id, byte[] payload) throws IOException {
        if (payload.length > MAX_PATH && !"DATA".equals(id)) {
            throw new IOException("sync payload too large for " + id);
        }
        out.write(id.getBytes(StandardCharsets.US_ASCII));
        writeInt(payload.length);
        out.write(payload);
        out.flush();
    }

    private Frame readFrame() throws IOException {
        String id = readAscii(4);
        int len = readInt();
        if (len < 0 || len > MAX_CHUNK + MAX_PATH) {
            throw new IOException("implausible sync length " + len + " for " + id);
        }
        return new Frame(id, readFully(len));
    }

    private static Entry parseDent(byte[] payload) throws IOException {
        if (payload.length < 16) {
            throw new IOException("short DENT entry");
        }
        int mode = readIntAt(payload, 0);
        int size = readIntAt(payload, 4);
        int mtime = readIntAt(payload, 8);
        int nameLen = readIntAt(payload, 12);
        if (16 + nameLen > payload.length) {
            throw new IOException("DENT name length overruns entry");
        }
        String name = new String(payload, 16, nameLen, StandardCharsets.UTF_8);
        return new Entry(mode, size, mtime, name);
    }

    private static Entry parseStat(byte[] payload, String name) throws IOException {
        if (payload.length < 12) {
            throw new IOException("short STAT reply");
        }
        return new Entry(readIntAt(payload, 0), readIntAt(payload, 4),
                readIntAt(payload, 8), name);
    }

    private String message(Frame frame) {
        return new String(frame.payload, StandardCharsets.UTF_8).trim();
    }

    private void writeInt(int value) throws IOException {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private int readInt() throws IOException {
        return readIntAt(readFully(4), 0);
    }

    private static int readIntAt(byte[] buf, int off) {
        return ((buf[off] & 0xff) << 24) | ((buf[off + 1] & 0xff) << 16)
                | ((buf[off + 2] & 0xff) << 8) | (buf[off + 3] & 0xff);
    }

    private String readAscii(int count) throws IOException {
        return new String(readFully(count), StandardCharsets.US_ASCII);
    }

    private byte[] readFully(int count) throws IOException {
        byte[] buf = new byte[count];
        int off = 0;
        while (off < count) {
            int read = in.read(buf, off, count - off);
            if (read == -1) {
                throw new EOFException("sync stream ended early");
            }
            off += read;
        }
        return buf;
    }

    private static final class Frame {
        final String id;
        final byte[] payload;

        Frame(String id, byte[] payload) {
            this.id = id;
            this.payload = payload;
        }
    }
}

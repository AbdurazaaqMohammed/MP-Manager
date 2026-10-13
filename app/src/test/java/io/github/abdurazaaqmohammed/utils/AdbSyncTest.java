package io.github.abdurazaaqmohammed.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Byte-for-byte checks of the ADB {@code sync:} framing.
 *
 * <p>No server is needed: the server's replies are scripted into a byte array,
 * handed to {@link AdbSync}, and both directions are asserted — what {@code
 * AdbSync} parses, and what it writes back. This is the same protocol adbd
 * speaks, so getting the frames exactly right is the whole test.
 */
public class AdbSyncTest {

    // ---- STAT ---------------------------------------------------------------

    @Test
    public void statRepliesWithModeSizeAndMtime() throws Exception {
        ByteArrayOutputStream resp = frame("STAT", concat(be(0100644), be(1234), be(1700000000)));
        ByteArrayOutputStream req = new ByteArrayOutputStream();
        AdbSync sync = sync(req, resp);

        AdbSync.Entry entry = sync.stat("/data/local/tmp/x");

        assertTrue("request starts with STAT", startsWith(req, "STAT"));
        assertTrue("request carries the path", contains(req, "/data/local/tmp/x"));
        assertEquals(1234, entry.size);
        assertTrue(entry.isFile());
        assertFalse(entry.isDirectory());
        assertEquals(1700000000, entry.mtime);
    }

    @Test
    public void statOnMissingPathReturnsNull() throws Exception {
        ByteArrayOutputStream resp = frame("FAIL", "No such file".getBytes(StandardCharsets.UTF_8));
        AdbSync sync = sync(new ByteArrayOutputStream(), resp);

        assertNull(sync.stat("/does/not/exist"));
    }

    // ---- LIST ---------------------------------------------------------------

    @Test
    public void listParsesDentEntriesUntilDone() throws Exception {
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        resp.write(frame("DENT", dent(040755, 0, 1700000000, "com.example")).toByteArray());
        resp.write(frame("DENT", dent(0100644, 42, 1700000001, "file.txt")).toByteArray());
        resp.write(frame("DONE", be(0)).toByteArray());
        ByteArrayOutputStream req = new ByteArrayOutputStream();
        AdbSync sync = sync(req, resp);

        List<AdbSync.Entry> entries = sync.list("/data/data");

        assertTrue("request starts with LIST", startsWith(req, "LIST"));
        assertEquals(2, entries.size());
        assertEquals("com.example", entries.get(0).name);
        assertTrue("first entry is a directory", entries.get(0).isDirectory());
        assertEquals("file.txt", entries.get(1).name);
        assertEquals(42, entries.get(1).size);
        assertTrue("second entry is a file", entries.get(1).isFile());
    }

    @Test
    public void listPropagatesFailureAsIOException() throws Exception {
        ByteArrayOutputStream resp = frame("FAIL", "Permission denied".getBytes(StandardCharsets.UTF_8));
        AdbSync sync = sync(new ByteArrayOutputStream(), resp);

        try {
            sync.list("/root");
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Permission denied"));
        }
    }

    // ---- RECV (pull) --------------------------------------------------------

    @Test
    public void pullConcatenatesDataChunksUntilDone() throws Exception {
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        resp.write(frame("DATA", "hello ".getBytes(StandardCharsets.UTF_8)).toByteArray());
        resp.write(frame("DATA", "world".getBytes(StandardCharsets.UTF_8)).toByteArray());
        resp.write(frame("DONE", be(0)).toByteArray());
        ByteArrayOutputStream req = new ByteArrayOutputStream();
        AdbSync sync = sync(req, resp);

        byte[] data = sync.pull("/sdcard/a.txt");

        assertTrue("request starts with RECV", startsWith(req, "RECV"));
        assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), data);
    }

    @Test
    public void pullOfEmptyFileReturnsEmptyArray() throws Exception {
        ByteArrayOutputStream resp = frame("DONE", be(0));
        AdbSync sync = sync(new ByteArrayOutputStream(), resp);

        assertEquals(0, sync.pull("/sdcard/empty").length);
    }

    // ---- SEND (push) --------------------------------------------------------

    @Test
    public void pushWritesHeaderThenDataThenDone() throws Exception {
        ByteArrayOutputStream resp = frame("OKAY", new byte[0]);
        ByteArrayOutputStream req = new ByteArrayOutputStream();
        AdbSync sync = sync(req, resp);

        sync.push("/sdcard/out.bin", "file-bytes".getBytes(StandardCharsets.UTF_8), 0644);

        byte[] written = req.toByteArray();
        assertTrue("opens with SEND", startsWith(req, "SEND"));
        assertTrue("mode,path header", contains(req, "644,/sdcard/out.bin"));
        assertTrue("contains a DATA frame", indexOf(written, "DATA") >= 0);
        assertTrue("closes with a DONE frame", indexOf(written, "DONE") >= 0);
    }

    @Test
    public void pushRejectionSurfacesServerMessage() throws Exception {
        ByteArrayOutputStream resp = frame("FAIL", "Read-only file system".getBytes(StandardCharsets.UTF_8));
        AdbSync sync = sync(new ByteArrayOutputStream(), resp);

        try {
            sync.push("/system/x", new byte[]{1}, 0644);
            fail("expected IOException");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Read-only file system"));
        }
    }

    // ---- helpers ------------------------------------------------------------

    private static AdbSync sync(ByteArrayOutputStream req, ByteArrayOutputStream resp) {
        return new AdbSync(new ByteArrayInputStream(resp.toByteArray()), req);
    }

    private static byte[] be(int value) {
        return new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value};
    }

    /** Build one server frame: ASCII id, big-endian length, payload. */
    private static ByteArrayOutputStream frame(String id, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(id.getBytes(StandardCharsets.US_ASCII));
            out.write(be(payload.length));
            out.write(payload);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return out;
    }

    /** DENT payload: mode, size, mtime, name length (all big-endian), then name. */
    private static byte[] dent(int mode, int size, int mtime, String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(be(mode));
            out.write(be(size));
            out.write(be(mtime));
            out.write(be(nameBytes.length));
            out.write(nameBytes);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            for (byte[] part : parts) {
                out.write(part);
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        return out.toByteArray();
    }

    private static boolean startsWith(ByteArrayOutputStream stream, String prefix) {
        byte[] data = stream.toByteArray();
        byte[] want = prefix.getBytes(StandardCharsets.US_ASCII);
        if (data.length < want.length) {
            return false;
        }
        for (int i = 0; i < want.length; i++) {
            if (data[i] != want[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(ByteArrayOutputStream stream, String needle) {
        return indexOf(stream.toByteArray(), needle) >= 0;
    }

    private static int indexOf(byte[] data, String needle) {
        byte[] want = needle.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i + want.length <= data.length; i++) {
            for (int j = 0; j < want.length; j++) {
                if (data[i + j] != want[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}

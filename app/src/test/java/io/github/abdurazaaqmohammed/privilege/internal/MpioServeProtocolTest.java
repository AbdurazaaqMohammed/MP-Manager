package io.github.abdurazaaqmohammed.privilege.internal;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MpioServeProtocolTest {

    @Test
    public void parseServeListStreamHandlesEntriesWeirdNamesSymlinksAndEmptyDir() throws Exception {
        String weirdName = "bad\nline\037name with spaces";
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        writeNul(raw, "E 1 0 0 0 1700000000000 755");
        writeNul(raw, "system");
        writeNul(raw, "E 0 1 0 4096 1700000001000 644");
        writeNul(raw, weirdName);
        writeNul(raw, "E 1 0 1 0 1700000002000 755");
        writeNul(raw, "bin");
        writeNul(raw, "E 0 1 1 0 1700000003000 777");
        writeNul(raw, "broken");
        writeNul(raw, "OK");

        File[] files = MpioServeClient.parseServeListStream(
                new ByteArrayInputStream(raw.toByteArray()), "/", new ByteArrayOutputStream());
        assertNotNull(files);
        assertEquals(4, files.length);

        RootFile sys = (RootFile) files[0];
        assertEquals("/system", sys.getAbsolutePath());
        assertTrue(sys.isDirectory());
        assertFalse(sys.isFile());
        assertFalse(sys.isLink());
        assertEquals("755", sys.getRootMode());
        assertEquals(1700000000000L, sys.lastModified());

        RootFile weird = (RootFile) files[1];
        assertEquals(weirdName, weird.getName());
        assertFalse(weird.isDirectory());
        assertTrue(weird.isFile());
        assertEquals(4096L, weird.length());

        RootFile bin = (RootFile) files[2];
        assertEquals("bin", bin.getName());
        assertTrue(bin.isDirectory());
        assertTrue(bin.isLink());

        RootFile broken = (RootFile) files[3];
        assertEquals("broken", broken.getName());
        assertFalse(broken.isDirectory());
        assertTrue(broken.isFile());
        assertTrue(broken.isLink());

        // Valid empty directory returns zero-length array, not null.
        ByteArrayOutputStream emptyRaw = new ByteArrayOutputStream();
        writeNul(emptyRaw, "OK");
        File[] empty = MpioServeClient.parseServeListStream(
                new ByteArrayInputStream(emptyRaw.toByteArray()), "/empty", new ByteArrayOutputStream());
        assertNotNull(empty);
        assertEquals(0, empty.length);
    }

    @Test
    public void parseServeListStreamReturnsNullOnErrAndKeepsStreamAligned() throws Exception {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        writeNul(raw, "ERR");
        writeNul(raw, "13");
        writeNul(raw, "OK");

        ByteArrayInputStream in = new ByteArrayInputStream(raw.toByteArray());
        ByteArrayOutputStream scratch = new ByteArrayOutputStream();
        assertNull(MpioServeClient.parseServeListStream(in, "/forbidden", scratch));
        File[] next = MpioServeClient.parseServeListStream(in, "/ok_empty", scratch);
        assertNotNull(next);
        assertEquals(0, next.length);
    }

    @Test
    public void parseServeStatDistinguishesEnoentFromPermissionError() throws Exception {
        ByteArrayOutputStream okRaw = new ByteArrayOutputStream();
        writeNul(okRaw, "S 1 0 1 1 512 1700000005000 644");
        RootManager.RootEntry entry = MpioServeClient.parseServeStatResponse(
                new ByteArrayInputStream(okRaw.toByteArray()), "/system/foo", new ByteArrayOutputStream());
        assertNotNull(entry);
        assertTrue(entry.exists());
        assertFalse(entry.isDirectory());
        assertTrue(entry.isFile());
        assertEquals(512L, entry.length());
        assertEquals(1700000005000L, entry.lastModified());
        assertEquals("644", entry.mode());

        ByteArrayOutputStream enoentRaw = new ByteArrayOutputStream();
        writeNul(enoentRaw, "ERR");
        writeNul(enoentRaw, "2");
        RootManager.RootEntry missing = MpioServeClient.parseServeStatResponse(
                new ByteArrayInputStream(enoentRaw.toByteArray()), "/missing", new ByteArrayOutputStream());
        assertNotNull(missing);
        assertFalse(missing.exists());

        ByteArrayOutputStream eaccesRaw = new ByteArrayOutputStream();
        writeNul(eaccesRaw, "ERR");
        writeNul(eaccesRaw, "13");
        assertNull(MpioServeClient.parseServeStatResponse(
                new ByteArrayInputStream(eaccesRaw.toByteArray()), "/restricted", new ByteArrayOutputStream()));
    }

    @Test
    public void serveClientCachesLegacyUnsupportedBinaryHandshakeFailure() {
        AtomicInteger launches = new AtomicInteger();
        MpioServeClient client = new MpioServeClient(globalNs -> {
            launches.incrementAndGet();
            return new Process() {
                @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
                @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
                @Override public InputStream getErrorStream() {
                    return new ByteArrayInputStream("Unknown command: serve\n".getBytes(StandardCharsets.UTF_8));
                }
                @Override public int waitFor() { return 1; }
                @Override public int exitValue() { return 1; }
                @Override public void destroy() {}
            };
        });

        assertNull(client.listDir("/system", false));
        assertTrue(client.isUnsupported());
        assertNull(client.listDir("/vendor", false));
        assertEquals(1, launches.get());
    }

    @Test
    public void serveClientReusesDaemonAcrossNamespaceAndRecyclesOnNamespaceChange() throws Exception {
        AtomicInteger launches = new AtomicInteger();
        MpioServeClient client = new MpioServeClient(globalNs -> new FakeServeProcess(launches.incrementAndGet()));

        File[] first = client.listDir("/empty", false);
        File[] second = client.listDir("/empty", false);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(1, launches.get());

        // Switching mount namespace mode must cleanly recycle the daemon once.
        File[] third = client.listDir("/empty", true);
        assertNotNull(third);
        assertEquals(2, launches.get());
    }

    private static void writeNul(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.write(0);
    }

    private static final class FakeServeProcess extends Process {
        private final PipedInputStream clientIn = new PipedInputStream();
        private final PipedOutputStream serverOut;
        private final PipedOutputStream clientOut;
        private final PipedInputStream serverIn = new PipedInputStream();
        private volatile boolean alive = true;

        FakeServeProcess(int id) throws IOException {
            this.serverOut = new PipedOutputStream(clientIn);
            this.clientOut = new PipedOutputStream(serverIn);
            Thread worker = new Thread(() -> {
                try {
                    serverOut.write(("MPIO_SERVE 1\n").getBytes(StandardCharsets.UTF_8));
                    serverOut.flush();
                    while (alive) {
                        String op = readToken(serverIn);
                        if (op == null) break;
                        if ("Q".equals(op)) {
                            serverOut.write("BYE\0".getBytes(StandardCharsets.UTF_8));
                            serverOut.flush();
                            break;
                        }
                        if ("L".equals(op)) {
                            readToken(serverIn);
                            serverOut.write("OK\0".getBytes(StandardCharsets.UTF_8));
                            serverOut.flush();
                        }
                    }
                } catch (IOException ignored) {
                } finally {
                    alive = false;
                }
            }, "fake-mpio-" + id);
            worker.setDaemon(true);
            worker.start();
        }

        private static String readToken(InputStream in) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (;;) {
                int b = in.read();
                if (b == -1) return null;
                if (b == 0) return bos.toString(StandardCharsets.UTF_8.name());
                bos.write(b);
            }
        }

        @Override public OutputStream getOutputStream() { return clientOut; }
        @Override public InputStream getInputStream() { return clientIn; }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() {
            if (alive) throw new IllegalThreadStateException();
            return 0;
        }
        @Override public void destroy() {
            alive = false;
            try { serverOut.close(); } catch (IOException ignored) {}
            try { serverIn.close(); } catch (IOException ignored) {}
        }
    }
}

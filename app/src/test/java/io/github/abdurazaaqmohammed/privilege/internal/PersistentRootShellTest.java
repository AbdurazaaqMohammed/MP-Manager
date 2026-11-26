package io.github.abdurazaaqmohammed.privilege.internal;

import io.github.abdurazaaqmohammed.privilege.api.ShellSession.ShellResult;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PersistentRootShellTest {

    @Test
    public void reusesSingleShellAcrossQueriesAndRespawnsOnNamespaceSwitch() throws Exception {
        AtomicInteger spawns = new AtomicInteger();
        PersistentRootShell shell = new PersistentRootShell(globalNs -> new MockShellProcess(spawns.incrementAndGet(), false));

        ShellResult r1 = shell.exec("echo first", 5, false);
        ShellResult r2 = shell.exec("echo second", 5, false);
        assertTrue(r1.isSuccess());
        assertEquals("first", r1.output());
        assertTrue(r2.isSuccess());
        assertEquals("second", r2.output());
        assertEquals(1, spawns.get());

        ShellResult r3 = shell.exec("echo global", 5, true);
        assertTrue(r3.isSuccess());
        assertEquals("global", r3.output());
        assertEquals(2, spawns.get());
    }

    @Test(timeout = 10000)
    public void timeoutDestroysWedgedShellAndNextCallStartsFresh() throws Exception {
        AtomicInteger spawns = new AtomicInteger();
        PersistentRootShell shell = new PersistentRootShell(globalNs -> {
            int idx = spawns.incrementAndGet();
            return new MockShellProcess(idx, idx == 1);
        });

        ShellResult timedOut = shell.exec("hang", 1, false);
        assertFalse(timedOut.isSuccess());

        ShellResult recovered = shell.exec("echo ok", 5, false);
        assertTrue(recovered.isSuccess());
        assertEquals("ok", recovered.output());
        assertEquals(2, spawns.get());
    }

    private static final class MockShellProcess extends Process {
        private final PipedInputStream clientOutIn = new PipedInputStream();
        private final PipedInputStream clientErrIn = new PipedInputStream();
        private final PipedOutputStream serverOut;
        private final PipedOutputStream serverErr;
        private final PipedOutputStream clientInOut;
        private final PipedInputStream serverIn = new PipedInputStream();
        private volatile boolean alive = true;

        MockShellProcess(int id, boolean hangOnCommand) throws IOException {
            this.serverOut = new PipedOutputStream(clientOutIn);
            this.serverErr = new PipedOutputStream(clientErrIn);
            this.clientInOut = new PipedOutputStream(serverIn);
            Thread worker = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(serverIn, StandardCharsets.UTF_8))) {
                    String cmdText = "";
                    String line;
                    while (alive && (line = r.readLine()) != null) {
                        if ("exit".equals(line)) break;
                        if (line.startsWith("printf '%s%d\\n' '")) {
                            if (hangOnCommand) {
                                while (alive) {
                                    try { Thread.sleep(50); } catch (InterruptedException ignored) { break; }
                                }
                                break;
                            }
                            int q1 = line.indexOf('\'', line.indexOf("__MP_OUT_"));
                            int start = line.indexOf("__MP_OUT_");
                            String outTok = line.substring(start, q1);
                            String outPayload = cmdText.startsWith("echo ") ? cmdText.substring(5) : "";
                            serverOut.write((outPayload + "\n" + outTok + "0\n").getBytes(StandardCharsets.UTF_8));
                            serverOut.flush();
                        } else if (line.startsWith("printf '%s\\n' '")) {
                            int start = line.indexOf("__MP_ERR_");
                            int q1 = line.indexOf('\'', start);
                            String errTok = line.substring(start, q1);
                            serverErr.write((errTok + "\n").getBytes(StandardCharsets.UTF_8));
                            serverErr.flush();
                        } else if (!line.equals("(") && !line.equals(")") && !line.startsWith("__mp_rc=") && !line.startsWith("export ")) {
                            cmdText = line;
                        }
                    }
                } catch (IOException ignored) {
                } finally {
                    alive = false;
                }
            }, "mock-su-" + id);
            worker.setDaemon(true);
            worker.start();
        }

        @Override public OutputStream getOutputStream() { return clientInOut; }
        @Override public InputStream getInputStream() { return clientOutIn; }
        @Override public InputStream getErrorStream() { return clientErrIn; }
        @Override public int waitFor() { return 0; }
        @Override public int exitValue() {
            if (alive) throw new IllegalThreadStateException();
            return 0;
        }
        @Override public void destroy() {
            alive = false;
            try { serverOut.close(); } catch (IOException ignored) {}
            try { serverErr.close(); } catch (IOException ignored) {}
            try { serverIn.close(); } catch (IOException ignored) {}
        }
    }
}

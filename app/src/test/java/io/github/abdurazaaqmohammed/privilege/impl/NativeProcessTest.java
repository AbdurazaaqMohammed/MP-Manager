package io.github.abdurazaaqmohammed.privilege.impl;

import io.github.abdurazaaqmohammed.privilege.impl.NativeProcess;

import org.junit.Test;
import java.io.InputStream;
import java.io.OutputStream;
import static org.junit.Assert.*;

public class NativeProcessTest {
    @Test public void portableLivenessAndTimedWaitDoNotUseApi26Methods() throws Exception {
        Process process = new Process() {
            boolean running = true;
            @Override public OutputStream getOutputStream() { return null; }
            @Override public InputStream getInputStream() { return null; }
            @Override public InputStream getErrorStream() { return null; }
            @Override public int waitFor() { running = false; return 0; }
            @Override public int exitValue() { if (running) throw new IllegalThreadStateException(); return 0; }
            @Override public void destroy() { running = false; }
            @Override public boolean isAlive() { throw new AssertionError("API 26 method used"); }
            @Override public Process destroyForcibly() { throw new AssertionError("API 26 method used"); }
        };
        assertTrue(NativeProcess.isAlive(process));
        assertFalse(NativeProcess.await(process, 5));
        process.destroy();
        assertFalse(NativeProcess.isAlive(process));
        assertTrue(NativeProcess.await(process, 5));
    }
}

package io.github.abdurazaaqmohammed.utils;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class NativeCopyProcessTest {
    private static class Result extends Process {
        final InputStream input;
        final int code;
        Result(String output, int code) {
            input = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
            this.code = code;
        }
        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return input; }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() throws InterruptedException { return code; }
        @Override public int exitValue() { return code; }
        @Override public void destroy() {}
        @Override public boolean isAlive() { return false; }
    }

    @Test public void finalResultReportsLongCountersAndOutputIsFullyDrained() throws Exception {
        List<Long> bytes = new ArrayList<>();
        List<String> names = new ArrayList<>();
        assertTrue(NativeCopyProcess.run(new Result("MPIO_PID 123\nPROGRESS 0 2147483648 file with spaces.bin\n"
                + "COPY_DONE done_files=1 done_bytes=2684354683 ms=4000\n", 0),
                (files, done, name) -> { bytes.add(done); names.add(name); }, null, null));
        assertEquals(List.of(2147483648L, 2684354683L), bytes);
        assertEquals("file with spaces.bin", names.get(0));
    }

    @Test public void exitZeroWithoutCompletionIsNotSuccess() {
        NativeCopyException error = assertThrows(NativeCopyException.class,
                () -> NativeCopyProcess.run(new Result("PROGRESS 0 900000000 partial.bin\n", 0), null, null, null));
        assertTrue(error.getMessage().contains("missing COPY_DONE"));
    }

    @Test public void nonzeroExitAndLateErrorCannotBeHiddenByDoneMarker() {
        String done = "COPY_DONE done_files=1 done_bytes=100 ms=1\n";
        assertThrows(NativeCopyException.class, () -> NativeCopyProcess.run(new Result(done, 1), null, null, null));
        NativeCopyException error = assertThrows(NativeCopyException.class,
                () -> NativeCopyProcess.run(new Result(done + "No space left on device\nCOPY_ERROR errno=28\n", 0), null, null, null));
        assertTrue(error.getMessage().contains("No space left on device"));
    }

    @Test public void malformedCompletionAndCancellationAreNotSuccess() {
        assertThrows(NativeCopyException.class, () -> NativeCopyProcess.run(
                new Result("COPY_DONE done_files=1 done_bytes=-1\n", 0), null, null, null));
        assertThrows(TransferCancelledException.class, () -> NativeCopyProcess.run(
                new Result("CANCELLED done_files=0 done_bytes=100\n", 1), null, null, null));
    }

    private static final class Running extends Result {
        final PipedInputStream stream = new PipedInputStream();
        final PipedOutputStream writer;
        final CountDownLatch stopped = new CountDownLatch(1);
        Running() throws Exception {
            super("", 1);
            writer = new PipedOutputStream(stream);
            writer.write("MPIO_PID 1234\nPROGRESS 0 100 copy.bin\n".getBytes(StandardCharsets.UTF_8));
            writer.flush();
        }
        @Override public InputStream getInputStream() { return stream; }
        @Override public boolean isAlive() { return stopped.getCount() > 0; }
        @Override public int waitFor() throws InterruptedException {
            assertTrue("native process stopped", stopped.await(5, TimeUnit.SECONDS));
            return 1;
        }
        void stop(String result) throws Exception {
            writer.write(result.getBytes(StandardCharsets.UTF_8));
            writer.close();
            stopped.countDown();
        }
        @Override public void destroy() {
            try { stop("CANCELLED\n"); } catch (Exception ignored) {}
        }
    }

    @Test(timeout = 10000) public void cancellationSignalsRootPidNotJustSuClient() throws Exception {
        Running process = new Running();
        TransferControl control = new TransferControl();
        List<String> signals = new ArrayList<>();
        assertThrows(TransferCancelledException.class, () -> NativeCopyProcess.run(process,
                (files, bytes, name) -> control.cancel(), control, command -> {
                    signals.add(command);
                    process.stop("CANCELLED done_files=0 done_bytes=100\n");
                }));
        assertEquals(1, signals.size());
        assertTrue(signals.get(0).contains("kill -TERM 1234"));
        assertFalse(process.isAlive());
    }

    @Test(timeout = 10000) public void pauseAndResumeSignalTheNativeProcess() throws Exception {
        Running process = new Running();
        TransferControl control = new TransferControl();
        List<String> signals = new ArrayList<>();
        assertThrows(TransferCancelledException.class, () -> NativeCopyProcess.run(process,
                (files, bytes, name) -> control.pause(), control, command -> {
                    signals.add(command);
                    if (command.contains("-STOP")) control.resume();
                    else process.stop("CANCELLED\n");
                }));
        assertEquals(2, signals.size());
        assertTrue(signals.get(0).contains("kill -STOP 1234"));
        assertTrue(signals.get(1).contains("kill -CONT 1234"));
    }
}

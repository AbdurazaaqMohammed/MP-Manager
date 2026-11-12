package io.github.abdurazaaqmohammed.utils;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class FileChannelCopyTest {
    private abstract static class Channel extends FileChannel {
        @Override protected void implCloseChannel() {}
        @Override public int read(ByteBuffer dst) { throw new UnsupportedOperationException(); }
        @Override public long read(ByteBuffer[] dsts, int offset, int length) { throw new UnsupportedOperationException(); }
        @Override public int write(ByteBuffer src) { throw new UnsupportedOperationException(); }
        @Override public long write(ByteBuffer[] srcs, int offset, int length) { throw new UnsupportedOperationException(); }
        @Override public long position() { throw new UnsupportedOperationException(); }
        @Override public FileChannel position(long position) { throw new UnsupportedOperationException(); }
        @Override public FileChannel truncate(long size) { throw new UnsupportedOperationException(); }
        @Override public void force(boolean metadata) throws IOException { throw new UnsupportedOperationException(); }
        @Override public long transferTo(long position, long count, WritableByteChannel target) throws IOException { throw new UnsupportedOperationException(); }
        @Override public long transferFrom(ReadableByteChannel src, long position, long count) { throw new UnsupportedOperationException(); }
        @Override public int read(ByteBuffer dst, long position) { throw new UnsupportedOperationException(); }
        @Override public int write(ByteBuffer src, long position) { throw new UnsupportedOperationException(); }
        @Override public MappedByteBuffer map(MapMode mode, long position, long size) { throw new UnsupportedOperationException(); }
        @Override public FileLock lock(long position, long size, boolean shared) { throw new UnsupportedOperationException(); }
        @Override public FileLock tryLock(long position, long size, boolean shared) { throw new UnsupportedOperationException(); }
    }

    private static class Source extends Channel {
        final long length;
        boolean zeroTransfers;
        long zeroAt = -1L;
        long eofAt = Long.MAX_VALUE;
        long lastRead = -1L;
        long maxTransfer = Long.MAX_VALUE;
        Source(long length) { this.length = length; }
        @Override public long size() { return length; }
        @Override public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
            if (zeroTransfers || position == zeroAt) { zeroAt = -1L; return 0L; }
            int size = (int)Math.min(65536L, Math.min(maxTransfer, count));
            if (length > Integer.MAX_VALUE) {
                long written = Math.min(maxTransfer, count);
                ((Destination)target).length += written;
                return written;
            }
            ByteBuffer buffer = ByteBuffer.allocate(size);
            fill(buffer, position, size);
            buffer.flip();
            return target.write(buffer);
        }
        @Override public int read(ByteBuffer buffer, long position) {
            lastRead = position;
            if (position >= eofAt) return -1;
            int size = (int)Math.min(buffer.remaining(), Math.min(length, eofAt) - position);
            fill(buffer, position, size);
            return size;
        }
        private static void fill(ByteBuffer buffer, long position, int count) {
            for (int i = 0; i < count; i++) buffer.put((byte)((position + i) * 31));
        }
    }

    private static class Destination extends Channel {
        long length;
        int writeLimit = Integer.MAX_VALUE;
        boolean synced;
        boolean failSync;
        final ByteArrayOutputStream contents;
        Destination(boolean capture) { contents = capture ? new ByteArrayOutputStream() : null; }
        @Override public long size() { return length; }
        @Override public int write(ByteBuffer buffer) {
            int count = Math.min(buffer.remaining(), writeLimit);
            for (int i = 0; i < count; i++) {
                byte value = buffer.get();
                if (contents != null) contents.write(value);
            }
            length += count;
            return count;
        }
        @Override public void force(boolean metadata) throws IOException {
            if (failSync) throw new IOException("writeback failed");
            synced = true;
        }
    }

    @Test public void zeroTransferFallsBackToReadsAndHandlesShortWrites() throws Exception {
        Source source = new Source(200003);
        source.zeroTransfers = true;
        Destination destination = new Destination(true);
        destination.writeLimit = 997;
        AtomicLong progress = new AtomicLong();
        FileChannelCopy.copy(source, destination, 1024 * 1024, null, progress::addAndGet);
        assertEquals(source.length, progress.get());
        assertEquals(source.length, destination.length);
        byte[] copied = destination.contents.toByteArray();
        for (int i = 0; i < copied.length; i++) assertEquals((byte)(i * 31), copied[i]);
        assertTrue(destination.synced);
    }

    @Test public void zeroTransferBeyondTwoGiBDoesNotTruncateOrResetProgress() throws Exception {
        Source source = new Source(2684354683L);
        source.maxTransfer = 2147479552L;
        source.zeroAt = source.maxTransfer;
        Destination destination = new Destination(false);
        AtomicLong progress = new AtomicLong();
        FileChannelCopy.copy(source, destination, Long.MAX_VALUE, null, progress::addAndGet);
        assertEquals(2147479552L, source.lastRead);
        assertEquals(source.length, destination.length);
        assertEquals(source.length, progress.get());
        assertTrue(destination.synced);
    }

    @Test public void earlyEofIsAnErrorNotSuccessfulPartialCopy() {
        Source source = new Source(200000);
        source.zeroTransfers = true;
        source.eofAt = 70000;
        Destination destination = new Destination(false);
        assertThrows(EOFException.class, () -> FileChannelCopy.copy(source, destination, 1024, null, null));
        assertFalse(destination.synced);
    }

    @Test public void zeroWriteAndWritebackFailureAreErrors() {
        Source source = new Source(1000);
        source.zeroTransfers = true;
        Destination destination = new Destination(false);
        destination.writeLimit = 0;
        assertThrows(IOException.class, () -> FileChannelCopy.copy(source, destination, 1024, null, null));
        destination.writeLimit = 1024;
        destination.failSync = true;
        assertThrows(IOException.class, () -> FileChannelCopy.copy(source, destination, 1024, null, null));
    }

    @Test public void cancellationIsCheckedDuringFallbackWrites() {
        Source source = new Source(200000);
        source.zeroTransfers = true;
        Destination destination = new Destination(false);
        destination.writeLimit = 997;
        TransferControl control = new TransferControl();
        assertThrows(TransferCancelledException.class, () -> FileChannelCopy.copy(source, destination, 1024,
                control::checkpoint, bytes -> control.cancel()));
        assertEquals(997L, destination.length);
        assertFalse(destination.synced);
    }
}

package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A read-only {@link SeekableByteChannel} that concatenates multiple file slices
 * into a single seamless byte stream without memory copying or temporary files.
 * Used for multi-part archives (.7z.001, .zip.001, .001/.002, etc.).
 */
public final class MultiFileSeekableByteChannel implements SeekableByteChannel {

    private final List<File> files;
    private final long[] lengths;
    private final long[] startOffsets;
    private final long totalLength;

    private boolean open = true;
    private long globalPosition = 0;

    private int currentFileIdx = -1;
    private RandomAccessFile currentRaf;

    public MultiFileSeekableByteChannel(List<File> fileList) throws IOException {
        Objects.requireNonNull(fileList, "fileList cannot be null");
        if (fileList.isEmpty()) {
            throw new IllegalArgumentException("fileList cannot be empty");
        }
        this.files = new ArrayList<>(fileList);
        int count = files.size();
        this.lengths = new long[count];
        this.startOffsets = new long[count];

        long offset = 0;
        for (int i = 0; i < count; i++) {
            File f = files.get(i);
            if (f == null || !f.exists()) {
                throw new IOException("File part missing: " + (f == null ? "null" : f.getAbsolutePath()));
            }
            long len = f.length();
            this.startOffsets[i] = offset;
            this.lengths[i] = len;
            offset += len;
        }
        this.totalLength = offset;
    }

    @Override
    public synchronized boolean isOpen() {
        return open;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!open) return;
        open = false;
        closeCurrentRaf();
    }

    private void closeCurrentRaf() {
        if (currentRaf != null) {
            try {
                currentRaf.close();
            } catch (IOException ignored) {}
            currentRaf = null;
            currentFileIdx = -1;
        }
    }

    @Override
    public synchronized long position() throws IOException {
        ensureOpen();
        return globalPosition;
    }

    @Override
    public synchronized SeekableByteChannel position(long newPosition) throws IOException {
        ensureOpen();
        if (newPosition < 0) {
            throw new IllegalArgumentException("Negative position: " + newPosition);
        }
        this.globalPosition = newPosition;
        return this;
    }

    @Override
    public synchronized long size() throws IOException {
        ensureOpen();
        return totalLength;
    }

    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        ensureOpen();
        if (!dst.hasRemaining()) return 0;
        if (globalPosition >= totalLength) return -1;

        int totalRead = 0;
        byte[] buf = null;

        while (dst.hasRemaining() && globalPosition < totalLength) {
            int fileIdx = findFileIndex(globalPosition);
            if (fileIdx < 0 || fileIdx >= files.size()) break;

            long offsetInFile = globalPosition - startOffsets[fileIdx];
            long bytesAvailableInFile = lengths[fileIdx] - offsetInFile;
            if (bytesAvailableInFile <= 0) {
                globalPosition = startOffsets[fileIdx] + lengths[fileIdx];
                continue;
            }

            int toRead = (int) Math.min((long) dst.remaining(), bytesAvailableInFile);
            if (toRead <= 0) break;

            ensureRaf(fileIdx);
            currentRaf.seek(offsetInFile);

            if (buf == null || buf.length < toRead) {
                buf = new byte[Math.min(toRead, 65536)];
            }
            int readChunk = Math.min(toRead, buf.length);
            int n = currentRaf.read(buf, 0, readChunk);
            if (n <= 0) break;

            dst.put(buf, 0, n);
            globalPosition += n;
            totalRead += n;
        }

        return (totalRead == 0 && globalPosition >= totalLength) ? -1 : totalRead;
    }

    private int findFileIndex(long pos) {
        for (int i = 0; i < files.size(); i++) {
            if (pos >= startOffsets[i] && pos < startOffsets[i] + lengths[i]) {
                return i;
            }
        }
        return files.size() - 1;
    }

    private void ensureRaf(int fileIdx) throws IOException {
        if (currentFileIdx != fileIdx || currentRaf == null) {
            closeCurrentRaf();
            currentRaf = new RandomAccessFile(files.get(fileIdx), "r");
            currentFileIdx = fileIdx;
        }
    }

    private void ensureOpen() throws ClosedChannelException {
        if (!open) throw new ClosedChannelException();
    }

    @Override
    public int write(ByteBuffer src) {
        throw new NonWritableChannelException();
    }

    @Override
    public SeekableByteChannel truncate(long size) {
        throw new NonWritableChannelException();
    }
}

package io.github.abdurazaaqmohammed.utils;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.function.LongConsumer;

/** Complete a channel copy even when Android's sendfile/transferTo makes no progress. */
final class FileChannelCopy {
    private FileChannelCopy() {
    }

    static void copy(FileChannel source, FileChannel destination, long chunkSize,
                     Runnable checkpoint, LongConsumer progress) throws IOException {
        long size = source.size();
        long copied = 0L;
        ByteBuffer buffer = null;
        while (copied < size) {
            if (checkpoint != null) checkpoint.run();
            long count = source.transferTo(copied, Math.min(size - copied, chunkSize), destination);
            if (count > 0) {
                copied += count;
                if (progress != null) progress.accept(count);
                continue;
            }
            // A zero transfer is not EOF. Some Android/FUSE combinations hit
            // this mid-file, including around large-file sendfile limits.
            if (buffer == null) buffer = ByteBuffer.allocate(65536);
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), size - copied));
            int read = source.read(buffer, copied);
            if (read < 0) throw new EOFException("Source ended before all bytes were copied");
            if (read == 0) throw new IOException("Source read made no progress during copy");
            buffer.flip();
            while (buffer.hasRemaining()) {
                if (checkpoint != null) checkpoint.run();
                int written = destination.write(buffer);
                if (written <= 0) throw new IOException("Destination write made no progress during copy");
                copied += written;
                if (progress != null) progress.accept(written);
            }
        }
        if (source.size() != size || destination.size() != size) {
            throw new IOException("Copy size mismatch or source changed during copy");
        }
        destination.force(true);
    }
}

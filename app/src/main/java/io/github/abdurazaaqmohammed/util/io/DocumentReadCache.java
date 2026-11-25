package io.github.abdurazaaqmohammed.util.io;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Stages privileged document reads into a seekable, private descriptor. */
public final class DocumentReadCache {
    private DocumentReadCache() { }

    public interface Writer {
        void writeTo(OutputStream out) throws IOException;
    }

    public static ParcelFileDescriptor open(File cacheDir, CancellationSignal signal, Writer writer)
            throws IOException {
        if (signal != null) signal.throwIfCanceled();
        File directory = new File(cacheDir, "document-reads");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Cannot create document read cache");
        }
        File staged = File.createTempFile("read-", ".tmp", directory);
        try {
            try (OutputStream out = new FilterOutputStream(new FileOutputStream(staged)) {
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (signal != null) signal.throwIfCanceled();
                    out.write(bytes, offset, length);
                }

                @Override public void write(int value) throws IOException {
                    if (signal != null) signal.throwIfCanceled();
                    out.write(value);
                }
            }) {
                writer.writeTo(out);
            }
            if (signal != null) signal.throwIfCanceled();
            ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(staged,
                    ParcelFileDescriptor.MODE_READ_ONLY);
            // The open descriptor keeps the inode alive until the recipient closes
            // it. Unlink now so no cache copy survives a completed/abandoned read.
            if (!staged.delete()) {
                descriptor.close();
                throw new IOException("Cannot unlink staged document");
            }
            return descriptor;
        } finally {
            if (staged.exists()) staged.delete();
        }
    }
}

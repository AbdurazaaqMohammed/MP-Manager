package io.github.abdurazaaqmohammed.features.files.network;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/** Blocking API: callers must use a worker thread. Streams remain caller-owned. */
public interface RemoteClient extends Closeable {
    List<RemoteEntry> list(String directory) throws IOException;
    /** Returns null only for a missing resource; authentication/errors must throw. */
    RemoteEntry stat(String path) throws IOException;
    void download(String path, OutputStream destination) throws IOException;
    /** Creates a new file. Existing destinations are never silently overwritten. */
    void upload(String path, InputStream source, long size) throws IOException;
    void mkdir(String path) throws IOException;
    void rename(String path, String destination) throws IOException;
    void delete(String path, boolean directory) throws IOException;
    /** Optional quota/volume information. Unsupported servers return null. */
    default RemoteSpace space() throws IOException { return null; }
}

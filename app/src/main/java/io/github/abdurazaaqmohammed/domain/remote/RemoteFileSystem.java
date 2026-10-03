package io.github.abdurazaaqmohammed.domain.remote;

import java.io.InputStream;
import java.util.List;

/**
 * A remote filesystem backend.
 *
 * <p>Contract:
 * <ul>
 *   <li>Every method blocks. Call from a worker thread.</li>
 *   <li>{@link #connect} must be called before anything else and
 *       {@link #disconnect} on teardown. Implementations are single-use.</li>
 *   <li>Paths are absolute and use "/" regardless of the backend.</li>
 *   <li>Methods for unsupported operations throw {@link UnsupportedOperationException};
 *       callers should gate on {@link #supports} first.</li>
 *   <li>{@link #read} returns a stream the CALLER closes.</li>
 *   <li>{@link #write} consumes and CLOSES {@code in}.</li>
 * </ul>
 */
public interface RemoteFileSystem extends AutoCloseable {

    RemoteCredentials.Kind kind();

    /** Short label for the UI, e.g. "SMB". */
    String displayName();

    /**
     * Open a session. Throws {@link RemoteException} with
     * {@link RemoteException#isAuthFailure()} when credentials are rejected.
     */
    void connect(RemoteCredentials credentials) throws RemoteException;

    boolean isConnected();

    /** Idempotent; must not throw. */
    void disconnect();

    /**
     * List one directory. Returns an empty list, never null.
     *
     * @param path absolute; "/" for the root
     */
    List<RemoteEntry> list(String path) throws RemoteException;

    /** Metadata for a single entry, or null when it does not exist. */
    RemoteEntry stat(String path) throws RemoteException;

    /** Caller closes the stream. */
    InputStream read(String path) throws RemoteException;

    /**
     * Create or replace a file. {@code in} is always closed by this call, even on
     * failure, so callers do not have to track it across an exception.
     *
     * @param append append instead of truncate, when the backend supports it
     */
    void write(String path, InputStream in, boolean append) throws RemoteException;

    /** Delete a file or (empty) directory. */
    void delete(String path) throws RemoteException;

    void mkdir(String path) throws RemoteException;

    /** Move/rename. Backends without a server-side move copy then delete. */
    void rename(String from, String to) throws RemoteException;

    boolean exists(String path) throws RemoteException;

    /** Root path to open on connect, usually {@code "/"} or a bucket. */
    String root();

    /** Capability probe, so the UI can grey out what this backend cannot do. */
    boolean supports(Capability capability);

    @Override
    void close();
}
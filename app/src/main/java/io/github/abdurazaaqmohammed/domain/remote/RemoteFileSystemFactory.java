package io.github.abdurazaaqmohammed.domain.remote;

/**
 * Creates a backend for a connection kind.
 *
 * <p>A factory rather than a switch in the host so that a backend can live in a
 * plugin: the host asks the registry for a {@link Kind}, and an external plugin
 * may have registered one.
 */
public interface RemoteFileSystemFactory {

    /** Which kind this factory produces. */
    RemoteCredentials.Kind kind();

    /**
     * @return a disconnected instance; the caller still calls
     *         {@link RemoteFileSystem#connect}
     */
    RemoteFileSystem create();
}
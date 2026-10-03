package io.github.abdurazaaqmohammed.domain.remote;

/**
 * One entry in a remote directory listing.
 *
 * <p>A record because it is immutable, cheap and compared structurally — the
 * adapters build them in bulk and the sort/filter code already treats
 * {@code domain.files} entries the same way.
 *
 * <p>{@code path} is backend-native and absolute, never ends in "/" except for
 * directories that are known to be containers. {@code modifiedAt} is 0 when the
 * backend does not report it.
 *
 * @param path       absolute path, e.g. {@code /pub/music/a.mp3}
 * @param name       display name, e.g. {@code a.mp3}
 * @param directory  true when the entry can be entered
 * @param size       size in bytes, or -1 when unknown
 * @param modifiedAt last-modified epoch millis, or 0 when unknown
 * @param isLink     true when the entry is a symlink and {@code target} is set
 * @param target     symlink target, else null
 */
public record RemoteEntry(
        String path,
        String name,
        boolean directory,
        long size,
        long modifiedAt,
        boolean isLink,
        String target) {

    public static RemoteEntry file(String path, String name, long size, long modifiedAt) {
        return new RemoteEntry(path, name, false, size, modifiedAt, false, null);
    }

    public static RemoteEntry directory(String path, String name, long modifiedAt) {
        return new RemoteEntry(path, name, true, -1, modifiedAt, false, null);
    }

    /** Entry whose size is unknown, e.g. a WebDAV collection without getcontentlength. */
    public static RemoteEntry unknownSize(String path, String name, boolean directory, long modifiedAt) {
        return new RemoteEntry(path, name, directory, -1, modifiedAt, false, null);
    }

    public String parentPath() {
        if (path == null || path.isEmpty()) return "/";
        int slash = path.lastIndexOf('/');
        if (slash <= 0) return "/";
        return path.substring(0, slash);
    }

    public String extension() {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot + 1);
    }
}
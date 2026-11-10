package io.github.abdurazaaqmohammed.features.files.network;

public final class RemoteEntry {
    public final String path;
    public final boolean directory;
    public final long size;
    public final long modified;

    public RemoteEntry(String path, boolean directory, long size, long modified) {
        this.path = RemotePath.normalize(path);
        this.directory = directory;
        this.size = Math.max(0, size);
        this.modified = Math.max(0, modified);
    }

    public String name() { return RemotePath.name(path); }
}

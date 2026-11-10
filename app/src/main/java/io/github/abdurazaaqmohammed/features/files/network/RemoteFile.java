package io.github.abdurazaaqmohammed.features.files.network;

import java.io.File;
import java.util.UUID;

/** Metadata-only File for pane/history bookkeeping. Never performs network I/O. */
public final class RemoteFile extends File {
    public static final class Connection {
        public final String id = UUID.randomUUID().toString();
        public final RemoteClient client;
        public final String address;
        public final String storageId;
        public final String storageIdentity;
        public Connection(RemoteClient client, String address) {
            this(client, address, null);
        }
        public Connection(RemoteClient client, String address, String storageId) {
            this(client, address, storageId, address);
        }
        public Connection(RemoteClient client, String address, String storageId, String storageIdentity) {
            this.client = client;
            this.address = address.endsWith("/") ? address : address + "/";
            this.storageId = storageId;
            this.storageIdentity = storageIdentity;
        }
    }

    public final Connection connection;
    public final RemoteEntry entry;

    public RemoteFile(Connection connection, RemoteEntry entry) {
        super("/network/" + connection.id + "/" + entry.path);
        this.connection = connection;
        this.entry = entry;
    }

    public static RemoteFile root(Connection connection) {
        return new RemoteFile(connection, new RemoteEntry("", true, 0, 0));
    }

    public String displayPath() {
        // URI encoding makes spaces, %, # and Unicode unambiguous without exposing credentials.
        try {
            String encoded = new java.net.URI(null, null, "/" + entry.path, null).toASCIIString().substring(1);
            return connection.address + encoded;
        } catch (java.net.URISyntaxException e) { return connection.address + entry.path; }
    }

    @Override public String getName() { return entry.path.isEmpty() ? connection.address : entry.name(); }
    @Override public RemoteFile getParentFile() {
        return entry.path.isEmpty() ? null : new RemoteFile(connection,
                new RemoteEntry(RemotePath.parent(entry.path), true, 0, 0));
    }
    @Override public String getParent() { File p = getParentFile(); return p == null ? null : p.getPath(); }
    @Override public boolean isDirectory() { return entry.directory; }
    @Override public boolean isFile() { return !entry.directory; }
    @Override public boolean exists() { return true; }
    @Override public boolean canRead() { return true; }
    @Override public boolean canWrite() { return false; }
    @Override public boolean isHidden() { return getName().startsWith("."); }
    @Override public long length() { return entry.size; }
    @Override public long lastModified() { return entry.modified; }
    @Override public File[] listFiles() { return null; }
    @Override public boolean delete() { return false; }
    @Override public boolean mkdir() { return false; }
    @Override public boolean mkdirs() { return false; }
    @Override public boolean renameTo(File destination) { return false; }
}

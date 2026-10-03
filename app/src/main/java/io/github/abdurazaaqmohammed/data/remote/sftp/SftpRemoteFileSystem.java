package io.github.abdurazaaqmohammed.data.remote.sftp;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.abdurazaaqmohammed.domain.remote.Capability;
import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystem;

/**
 * SFTP over SSH, via jsch.
 *
 * <p>SFTP is a different protocol from FTPS: this is SSH's file-transfer
 * subsystem on port 22, not FTP with TLS. Keys and passwords both work; a
 * passphrase-protected key is taken from the {@code keyPassphrase} extra.
 *
 * <p>Paths are made absolute against the home directory that the login lands
 * in, because jsch resolves a relative path against its own working directory
 * and that differs between servers.
 */
public class SftpRemoteFileSystem implements RemoteFileSystem {

    private static final int TIMEOUT_MS = 30_000;

    private final AtomicBoolean connected = new AtomicBoolean();
    private Session session;
    private ChannelSftp channel;
    /** Physical directory the profile's path field points at; the tree root. */
    private String base;

    @Override
    public RemoteCredentials.Kind kind() {
        return RemoteCredentials.Kind.SFTP;
    }

    @Override
    public String displayName() {
        return "SFTP";
    }

    @Override
    public void connect(RemoteCredentials creds) throws RemoteException {
        if (creds.kind() != RemoteCredentials.Kind.SFTP) {
            throw new RemoteException("Not an SFTP profile: " + creds.kind());
        }
        String user = creds.username() == null ? "" : creds.username().trim();
        if (user.isEmpty()) {
            throw new RemoteException("SFTP needs a username");
        }
        String keyPath = creds.extra("privateKey", "");

        try {
            JSch jsch = new JSch();
            if (!keyPath.isEmpty()) {
                File keyFile = new File(keyPath);
                if (!keyFile.exists()) {
                    throw new RemoteException("Private key not found: " + keyPath);
                }
                String passphrase = creds.extra("keyPassphrase", "");
                if (passphrase.isEmpty()) {
                    jsch.addIdentity(keyPath);
                } else {
                    jsch.addIdentity(keyPath, passphrase);
                }
            }

            Session s = jsch.getSession(user, creds.host(), creds.effectivePort());
            if (keyPath.isEmpty()) {
                if (creds.password() == null || creds.password().isEmpty()) {
                    throw new RemoteException("SFTP needs a password or a private key");
                }
                s.setPassword(creds.password());
            } else if (creds.password() != null && !creds.password().isEmpty()) {
                // A passphrase may arrive in the password field too.
                s.setPassword(creds.password());
            }

            Properties config = new Properties();
            if (keyPath.isEmpty()) {
                config.put("PreferredAuthentications", "password,keyboard-interactive");
            } else {
                config.put("PreferredAuthentications", "publickey,password,keyboard-interactive");
            }
            // Hosts are user-entered and rarely in known_hosts on a phone; the
            // insecure switch still controls whether that is allowed.
            if (!creds.insecure()) {
                config.put("StrictHostKeyChecking", "yes");
            } else {
                config.put("StrictHostKeyChecking", "no");
            }
            s.setConfig(config);

            s.connect(TIMEOUT_MS);
            ChannelSftp c = (ChannelSftp) s.openChannel("sftp");
            c.connect(TIMEOUT_MS);
            if (!c.isConnected()) {
                throw new RemoteException("SFTP channel did not open");
            }
this.session = s;
            this.channel = c;
            // The profile's path is the root of the browsable tree, the same
            // way a bucket is for S3. An empty path means the directory the
            // login lands in; a relative one is resolved against it.
            String configured = creds.path() == null ? "" : creds.path().trim();
            if (configured.isEmpty() || "/".equals(configured)) {
                this.base = normalize(c.pwd());
            } else if (configured.startsWith("/")) {
                this.base = normalize(configured);
            } else {
                this.base = normalize(c.pwd() + "/" + configured);
            }
            connected.set(true);
        } catch (RemoteException e) {
            closeQuietly();
            throw e;
        } catch (JSchException | SftpException e) {
            closeQuietly();
            throw new RemoteException("SFTP connect failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected.get() && channel != null && channel.isConnected();
    }

    @Override
    public void disconnect() {
        connected.set(false);
    }

    @Override
    public void close() {
        connected.set(false);
        closeQuietly();
    }

    private void closeQuietly() {
        if (channel != null) {
            try {
                channel.disconnect();
            } catch (RuntimeException ignored) {
                // teardown is best effort
            }
            channel = null;
        }
        if (session != null) {
            try {
                session.disconnect();
            } catch (RuntimeException ignored) {
                // teardown is best effort
            }
            session = null;
        }
        base = null;
    }

    @Override
    public String root() {
        return "/";
    }

    @Override
    public boolean supports(Capability capability) {
        // SFTP can genuinely append, unlike object storage.
        return true;
    }

    // -------------------------------------------------------------- browsing

    @Override
    public List<RemoteEntry> list(String path) throws RemoteException {
        requireConnected();
        String target = resolve(path);
        try {
            @SuppressWarnings("unchecked")
            Vector<ChannelSftp.LsEntry> raw = channel.ls(target);
            List<RemoteEntry> out = new ArrayList<>();
            if (raw == null) return out;
            for (ChannelSftp.LsEntry entry : raw) {
                String name = entry.getFilename();
                // ls always includes the directory itself and its parent.
                if (name == null || ".".equals(name) || "..".equals(name)) continue;
                SftpATTRS attrs = entry.getAttrs();
                boolean directory = attrs != null && attrs.isDir();
                long size = attrs == null || directory ? -1 : attrs.getSize();
                long modified = attrs == null ? 0L : attrs.getMTime() * 1000L;
                String logical = toLogical(join(target, name));
                out.add(directory
                        ? RemoteEntry.directory(logical + "/", name, modified)
                        : RemoteEntry.file(logical, name, size, modified));
            }
            return out;
        } catch (SftpException e) {
            throw new RemoteException("SFTP list failed: " + e.getMessage(), e);
        }
    }

    @Override
    public RemoteEntry stat(String path) throws RemoteException {
        requireConnected();
        String target = resolve(path);
        try {
            SftpATTRS attrs = channel.stat(target);
            if (attrs == null) return null;
            boolean directory = attrs.isDir();
            String name = nameOf(target);
            String logical = toLogical(target);
            return directory
                    ? RemoteEntry.directory(logical, name, attrs.getMTime() * 1000L)
                    : RemoteEntry.file(logical, name, attrs.getSize(), attrs.getMTime() * 1000L);
        } catch (SftpException e) {
            // SFTP reports a missing path as a status code, not an exception
            // type, so absence has to be recognised rather than thrown.
            if (isNoSuchFile(e)) return null;
            throw new RemoteException("SFTP stat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String path) throws RemoteException {
        requireConnected();
        try {
            String target = resolve(path);
            channel.stat(target);
            return true;
        } catch (SftpException e) {
            if (isNoSuchFile(e)) return false;
            throw new RemoteException("SFTP exists failed: " + e.getMessage(), e);
        }
    }

    // --------------------------------------------------------------- writing

    @Override
    public void write(String path, InputStream in, boolean append) throws RemoteException {
        requireConnected();
        String target = resolve(path);
        try {
            channel.put(in, target, append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE);
        } catch (SftpException e) {
            throw new RemoteException("SFTP write failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String path) throws RemoteException {
        requireConnected();
        String target = resolve(path);
        try {
            SftpATTRS attrs = channel.lstat(target);
            if (attrs != null && attrs.isDir()) {
                channel.rmdir(target);
            } else {
                channel.rm(target);
            }
        } catch (SftpException e) {
            if (isNoSuchFile(e)) return;
            throw new RemoteException("SFTP delete failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void mkdir(String path) throws RemoteException {
        requireConnected();
        try {
            channel.mkdir(resolve(path));
        } catch (SftpException e) {
            // A second mkdir on an existing directory is not worth failing over.
            if (e.id != ChannelSftp.SSH_FX_FAILURE) {
                throw new RemoteException("SFTP mkdir failed: " + e.getMessage(), e);
            }
            if (!exists(path)) {
                throw new RemoteException("SFTP mkdir failed: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void rename(String from, String to) throws RemoteException {
        requireConnected();
        try {
            channel.rename(resolve(from), resolve(to));
        } catch (SftpException e) {
            throw new RemoteException("SFTP rename failed: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream read(String path) throws RemoteException {
        requireConnected();
        try {
            // jsch has no streaming read, so the payload goes to a cache file
            // that is removed when the stream is closed.
            final File temp = File.createTempFile("sftp-", ".bin");
            temp.deleteOnExit();
            try (OutputStream out = new java.io.FileOutputStream(temp)) {
                channel.get(resolve(path), out);
            }
            InputStream in = new java.io.FileInputStream(temp) {
                @Override
                public void close() throws IOException {
                    super.close();
                    if (!temp.delete()) temp.deleteOnExit();
                }
            };
            return in;
        } catch (SftpException | IOException e) {
            throw new RemoteException("SFTP read failed: " + e.getMessage(), e);
        }
    }

    // --------------------------------------------------------------- helpers

    private void requireConnected() throws RemoteException {
        if (!isConnected()) {
            throw new RemoteException("SFTP is not connected");
        }
    }

    /**
     * Maps a UI path onto the server.
     *
     * <p>UI paths are logical: they are relative to the configured base, not to
     * the server's filesystem root. Passing a logical path through untouched
     * would otherwise double the base on the second hop.
     */
    private String resolve(String path) {
        String p = path == null ? "" : path.trim();
        if (p.isEmpty() || "/".equals(p)) return base;
        while (p.startsWith("/")) p = p.substring(1);
        String combined = normalize(base + "/" + p);
        return combined.isEmpty() ? "/" : combined;
    }

    /** Inverse of {@link #resolve}: server path back to a UI path. */
    private String toLogical(String physical) {
        if (base == null || physical == null) return physical;
        if (physical.equals(base)) return "/";
        if (physical.startsWith(base + "/")) return physical.substring(base.length());
        return physical;
    }

    private static boolean isNoSuchFile(SftpException e) {
        // jsch only surfaces SSH_FX_NO_SUCH_FILE; SSH_FX_NO_SUCH_PATH is not
        // defined on ChannelSftp. Permission problems stay real errors.
        return e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE;
    }

    private static String normalize(String p) {
        String s = p == null ? "" : p.trim();
        while (s.contains("//")) s = s.replace("//", "/");
        while (s.length() > 1 && s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "/";
        return s;
    }

    private static String join(String dir, String name) {
        if (dir == null || dir.isEmpty() || "/".equals(dir)) return "/" + name;
        return dir.endsWith("/") ? dir + name : dir + "/" + name;
    }

    private static String nameOf(String path) {
        String p = path == null ? "" : path;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if ("/".equals(p)) return "/";
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }
}
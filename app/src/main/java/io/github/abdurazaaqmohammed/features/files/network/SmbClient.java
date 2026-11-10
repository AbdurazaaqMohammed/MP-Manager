package io.github.abdurazaaqmohammed.features.files.network;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mserref.NtStatus;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileAllInformation;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2CreateOptions;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskEntry;
import com.hierynomus.smbj.share.DiskShare;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class SmbClient implements RemoteClient {
    private final SMBClient client;
    private Connection connection;
    private Session session;
    private DiskShare share;

    public SmbClient(String host, int port, String shareName, String username,
                     String password, String domain) throws IOException {
        RemotePath.validateName(shareName);
        if (host.isEmpty() || host.contains("/") || host.contains("\\") || port < 1 || port > 65535)
            throw new IllegalArgumentException("Invalid SMB host or port");
        client = new SMBClient(SmbConfig.builder().withTimeout(60, TimeUnit.SECONDS)
                .withSocketFactory(new TimedSocketFactory(javax.net.SocketFactory.getDefault(), 20000))
                .withSoTimeout(20000).build());
        try {
            connection = client.connect(host, port);
            session = connection.authenticate(username.isEmpty() ? AuthenticationContext.anonymous()
                    : new AuthenticationContext(username, password.toCharArray(), domain));
            com.hierynomus.smbj.share.Share connected = session.connectShare(shareName);
            if (!(connected instanceof DiskShare)) {
                connected.close();
                throw new IOException("SMB location is not a disk share");
            }
            share = (DiskShare) connected;
        } catch (Exception e) {
            close();
            throw error(e);
        }
    }

    static String smbPath(String path) {
        String normalized = RemotePath.normalize(path);
        if (normalized.indexOf(':') >= 0) throw new IllegalArgumentException("SMB alternate data streams are not supported");
        return normalized.replace('/', '\\');
    }

    private static IOException error(Exception e) {
        return e instanceof IOException ? (IOException) e : new IOException("SMB: " + e.getMessage(), e);
    }

    static boolean isMissing(long status) {
        return status == NtStatus.STATUS_NO_SUCH_FILE.getValue()
                || status == NtStatus.STATUS_OBJECT_NAME_NOT_FOUND.getValue()
                || status == NtStatus.STATUS_OBJECT_PATH_NOT_FOUND.getValue();
    }

    @Override public synchronized List<RemoteEntry> list(String directory) throws IOException {
        try {
            List<RemoteEntry> result = new ArrayList<>();
            for (FileIdBothDirectoryInformation entry : share.list(smbPath(directory))) {
                String name = entry.getFileName();
                if (name.equals(".") || name.equals("..")) continue;
                // Reparse points may escape the share or introduce recursion cycles.
                if ((entry.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0) continue;
                result.add(new RemoteEntry(RemotePath.child(directory, name),
                        (entry.getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0,
                        entry.getEndOfFile(), entry.getLastWriteTime().toEpochMillis()));
            }
            return result;
        } catch (Exception e) { throw error(e); }
    }

    @Override public synchronized RemoteEntry stat(String path) throws IOException {
        try {
            String p = smbPath(path);
            if (!p.isEmpty() && !share.fileExists(p) && !share.folderExists(p)) return null;
            FileAllInformation info = share.getFileInformation(p);
            if ((info.getBasicInformation().getFileAttributes() & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0)
                throw new IOException("SMB reparse points are not supported");
            return new RemoteEntry(path, info.getStandardInformation().isDirectory(),
                    info.getStandardInformation().getEndOfFile(), info.getBasicInformation().getLastWriteTime().toEpochMillis());
        } catch (SMBApiException e) {
            if (isMissing(e.getStatusCode())) return null;
            throw error(e);
        } catch (Exception e) { throw error(e); }
    }

    @Override public synchronized void download(String path, OutputStream destination) throws IOException {
        try (com.hierynomus.smbj.share.File file = share.openFile(smbPath(path),
                EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN, EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
             InputStream input = file.getInputStream()) {
            WebDavClient.copy(input, destination);
        } catch (Exception e) { throw error(e); }
    }

    @Override public synchronized void upload(String path, InputStream source, long size) throws IOException {
        WebDavClient.requireNonRoot(path);
        try (com.hierynomus.smbj.share.File file = share.openFile(smbPath(path),
                EnumSet.of(AccessMask.GENERIC_WRITE), EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE));
             OutputStream output = file.getOutputStream()) {
            WebDavClient.copy(source, output);
        } catch (Exception e) { throw error(e); }
    }

    @Override public synchronized void mkdir(String path) throws IOException {
        WebDavClient.requireNonRoot(path);
        try { share.mkdir(smbPath(path)); }
        catch (Exception e) { throw error(e); }
    }

    @Override public synchronized void rename(String path, String destination) throws IOException {
        WebDavClient.requireNonRoot(path);
        WebDavClient.requireNonRoot(destination);
        try (DiskEntry entry = share.open(smbPath(path), EnumSet.of(AccessMask.DELETE), null,
                SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)) {
            entry.rename(smbPath(destination), false);
        } catch (Exception e) { throw error(e); }
    }

    @Override public synchronized void delete(String path, boolean directory) throws IOException {
        WebDavClient.requireNonRoot(path);
        try {
            if (directory) {
                // Enumerate ourselves so reparse points are never followed.
                for (RemoteEntry child : list(path)) delete(child.path, child.directory);
                share.rmdir(smbPath(path), false);
            } else share.rm(smbPath(path));
        } catch (Exception e) { throw error(e); }
    }

    @Override public synchronized RemoteSpace space() throws IOException {
        try {
            com.hierynomus.msfscc.fileinformation.ShareInfo info = share.getShareInformation();
            return spaceFromInformation(info);
        } catch (SMBApiException e) {
            if (e.getStatusCode() == NtStatus.STATUS_NOT_SUPPORTED.getValue()
                    || e.getStatusCode() == NtStatus.STATUS_NOT_IMPLEMENTED.getValue()
                    || e.getStatusCode() == NtStatus.STATUS_INVALID_INFO_CLASS.getValue()
                    || e.getStatusCode() == NtStatus.STATUS_ACCESS_DENIED.getValue()) return null;
            throw error(e);
        } catch (com.hierynomus.smbj.common.SMBRuntimeException e) {
            // Some servers report success with an empty/truncated optional FS-info body.
            // Reject that metadata, but keep the authenticated session usable for files.
            if (e.getCause() instanceof com.hierynomus.protocol.commons.buffer.Buffer.BufferException) return null;
            throw error(e);
        } catch (Exception e) { throw error(e); }
    }

    static RemoteSpace spaceFromInformation(com.hierynomus.msfscc.fileinformation.ShareInfo info) {
        try {
            if (info.getSectorsPerAllocationUnit() <= 0 || info.getBytesPerSector() <= 0) return null;
            long unit = Math.multiplyExact(info.getSectorsPerAllocationUnit(), info.getBytesPerSector());
            long total = Math.multiplyExact(info.getTotalAllocationUnits(), unit);
            // Use caller-available space so a server-side user quota is respected.
            long free = Math.multiplyExact(info.getCallerAvailableAllocationUnits(), unit);
            return RemoteSpace.fromTotalFree(total, free);
        } catch (IllegalArgumentException | ArithmeticException invalid) { return null; }
    }

    @Override public void close() {
        try { if (share != null) share.close(); } catch (Exception ignored) {}
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
        client.close();
    }
}

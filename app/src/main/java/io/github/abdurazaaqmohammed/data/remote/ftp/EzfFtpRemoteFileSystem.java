package io.github.abdurazaaqmohammed.data.remote.ftp;

import android.content.Context;

import com.lilincpp.github.libezftp.EZFtpClient;
import com.lilincpp.github.libezftp.EZFtpFile;
import com.lilincpp.github.libezftp.IEZFtpClient;
import com.lilincpp.github.libezftp.callback.OnEZFtpCallBack;
import com.lilincpp.github.libezftp.callback.OnEZFtpDataTransferCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import io.github.abdurazaaqmohammed.domain.remote.Capability;
import io.github.abdurazaaqmohammed.domain.remote.RemoteCredentials;
import io.github.abdurazaaqmohammed.domain.remote.RemoteEntry;
import io.github.abdurazaaqmohammed.domain.remote.RemoteException;
import io.github.abdurazaaqmohammed.domain.remote.RemoteFileSystem;
import io.github.abdurazaaqmohammed.domain.remote.RemoteListing;

/**
 * FTP and FTPS backend, and the reference implementation of
 * {@link RemoteFileSystem}.
 *
 * <p>Two mismatches between EZ-FTP and the abstraction, both absorbed here
 * instead of leaking into the contract:
 *
 * <ol>
 *   <li><b>EZ-FTP is callback-based and stateful.</b> There is no "list this
 *       path" call: {@code getCurDirFileList()} returns whatever the server's
 *       CWD points at. The abstraction is synchronous and path-addressed, so
 *       every operation runs under {@link #lock} and blocks on its callback via
 *       {@link #await}. The lock also fixes a latent race in the old
 *       FtpController: two panes navigating at once mutated the same server CWD.
 *
 *   <li><b>Transfers are file-path based.</b> {@code downloadFile} and
 *       {@code uploadFile} both take a local path, so there is no streaming
 *       API. {@link #read} stages through a cache file; {@link #write} stages
 *       its input first. That costs a disk round-trip instead of buffering a
 *       whole file in memory.
 * </ol>
 *
 * <p>EZ-FTP's file model has no modification time, so listings report
 * {@code modifiedAt == 0}, matching the previous FTPFileWrapper.
 */
public class EzfFtpRemoteFileSystem implements RemoteFileSystem {

    private static final long TIMEOUT_SECONDS = 60;

    /** Guards the server CWD and the client, neither of which is thread-safe. */
    private final ReentrantLock lock = new ReentrantLock();
    private final Context appContext;

    private IEZFtpClient client;
    private RemoteCredentials credentials;

    public EzfFtpRemoteFileSystem(Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    @Override
    public RemoteCredentials.Kind kind() {
        return credentials == null ? RemoteCredentials.Kind.FTP : credentials.kind();
    }

    @Override
    public String displayName() {
        return switch (kind()) {
            case FTPS_EXPLICIT, FTPS_IMPLICIT -> "FTPS";
            default -> "FTP";
        };
    }

    @Override
    public String root() {
        return "/";
    }

    // ------------------------------------------------------------- lifecycle

    @Override
    public void connect(RemoteCredentials creds) throws RemoteException {
        this.credentials = creds;
        int securityType = switch (creds.kind()) {
            case FTPS_EXPLICIT -> IEZFtpClient.SECURITY_FTPS_EXPLICIT;
            case FTPS_IMPLICIT -> IEZFtpClient.SECURITY_FTPS_IMPLICIT;
            default -> IEZFtpClient.SECURITY_NONE;
        };
        IEZFtpClient c = new EZFtpClient();
        try {
            connectClient(c, creds, securityType);
        } catch (RemoteException e) {
            try {
                c.release();
            } catch (Exception ignored) {
                // nothing useful to do with a failed cleanup
            }
            throw e;
        }
        this.client = c;
    }

    /** Not called under {@link #lock}: the lock belongs to a live session. */
    private void connectClient(IEZFtpClient c, RemoteCredentials creds, int securityType)
            throws RemoteException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteException> failure = new AtomicReference<>();
        c.connect(creds.host(), creds.effectivePort(), creds.username(),
                creds.password(), securityType, new OnEZFtpCallBack<>() {
                    @Override
                    public void onSuccess(Void response) {
                        latch.countDown();
                    }

                    @Override
                    public void onFail(int code, String msg) {
                        failure.set(toRemoteException(code, msg));
                        latch.countDown();
                    }
                });
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new RemoteException("FTP connect timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("FTP connect interrupted", e);
        }
        RemoteException f = failure.get();
        if (f != null) throw f;
    }

    @Override
    public boolean isConnected() {
        IEZFtpClient c = client;
        return c != null && c.isConnected();
    }

    @Override
    public void disconnect() {
        IEZFtpClient c = client;
        client = null;
        if (c == null) return;
        lock.lock();
        try {
            try {
                c.disconnect();
            } catch (Exception ignored) {
                // socket is being dropped regardless
            }
        } finally {
            lock.unlock();
        }
        try {
            c.release();
        } catch (Exception ignored) {
            // ditto
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    // ---------------------------------------------------------------- listing

    @Override
    public List<RemoteEntry> list(String path) throws RemoteException {
        String base = normalize(path);
        lock.lock();
        try {
            requireConnected();
            changeDir(base);
            List<EZFtpFile> raw = awaitList((ftp, cb) -> ftp.getCurDirFileList(cb));
            List<RemoteEntry> out = new ArrayList<>();
            if (raw == null) return out;
            for (EZFtpFile f : raw) {
                if (f == null) continue;
                String name = f.getName();
                if (name == null || name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;
                boolean dir = f.getType() == EZFtpFile.TYPE_DIRECTORY;
                out.add(new RemoteEntry(RemoteListing.join(base, name), name, dir,
                        dir ? -1 : f.getSize(), 0, false, null));
            }
            return out;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public RemoteEntry stat(String path) throws RemoteException {
        String normalized = normalize(path);
        int slash = normalized.lastIndexOf('/');
        String parent = slash <= 0 ? "/" : normalized.substring(0, slash);
        String name = normalized.substring(slash + 1);
        if (name.isEmpty()) return RemoteEntry.directory("/", "/", 0);
        for (RemoteEntry e : list(parent)) {
            if (e.name().equals(name)) return e;
        }
        return null;
    }

    @Override
    public boolean exists(String path) throws RemoteException {
        try {
            return stat(path) != null;
        } catch (RemoteException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ files

    @Override
    public InputStream read(String path) throws RemoteException {
        String normalized = normalize(path);
        File staged = new File(cacheDir(), "ftp-read-" + System.nanoTime());
        boolean ok = false;
        try {
            RemoteEntry entry = stat(normalized);
            if (entry != null && entry.directory()) {
                throw new RemoteException("Cannot read a directory: " + normalized);
            }
            // downloadFile needs an EZFtpFile rather than a path, and the
            // adapter has no way to synthesise a remote handle from a path.
            // The parent listing is the only handle we can obtain.
            EZFtpFile handle = findHandle(normalized);
            if (handle == null) throw new RemoteException("No such file: " + normalized);

            lock.lock();
            try {
                requireConnected();
                changeDir(dirOf(normalized));
                awaitTransfer(cb -> client.downloadFile(handle, staged.getAbsolutePath(), cb));
            } finally {
                lock.unlock();
            }
            // A zero-byte stage is legitimate (empty remote file), so only
            // check that the transfer produced the file at all.
            if (!staged.exists()) throw new RemoteException("FTP download produced no file");
            ok = true;
            try {
                return new FileInputStream(staged);
            } catch (java.io.FileNotFoundException e) {
                throw new RemoteException("Cannot open staged download: " + e.getMessage(), e);
            }
        } finally {
            if (!ok) staged.delete();
        }
    }

    @Override
    public void write(String path, InputStream in, boolean append) throws RemoteException {
        if (append) throw new UnsupportedOperationException("FTP append is not supported");
        String normalized = normalize(path);
        File staged = new File(cacheDir(), "ftp-write-" + System.nanoTime());
        try {
            try (OutputStream out = new FileOutputStream(staged)) {
                in.transferTo(out);
            }
            lock.lock();
            try {
                requireConnected();
                changeDir(dirOf(normalized));
                awaitTransfer(cb -> client.uploadFile(staged.getAbsolutePath(), cb));
            } finally {
                lock.unlock();
            }
        } catch (IOException e) {
            throw new RemoteException("FTP upload failed: " + e.getMessage(), e);
        } finally {
            staged.delete();
        }
    }

    @Override
    public void delete(String path) throws RemoteException {
        String normalized = normalize(path);
        RemoteEntry entry = stat(normalized);
        if (entry == null) throw new RemoteException("No such entry: " + normalized);
        boolean dir = entry.directory();
        lock.lock();
        try {
            requireConnected();
            changeDir(dirOf(normalized));
            String name = nameOf(normalized);
            if (dir) {
                awaitVoid((ftp, cb) -> ftp.deleteDirectory(name, cb));
            } else {
                awaitVoid((ftp, cb) -> ftp.deleteFile(name, cb));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void mkdir(String path) throws RemoteException {
        String normalized = normalize(path);
        lock.lock();
        try {
            requireConnected();
            changeDir(dirOf(normalized));
            awaitVoid((ftp, cb) -> ftp.createDirectory(nameOf(normalized), cb));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void rename(String from, String to) throws RemoteException {
        lock.lock();
        try {
            requireConnected();
            awaitVoid((ftp, cb) -> ftp.rename(normalize(from), normalize(to), cb));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean supports(Capability capability) {
        return switch (capability) {
            case DIRECTORIES, MKDIR, RENAME -> true;
            // EZ-FTP exposes no append, no symlink listing, no timestamp setter
            // and no server-side copy.
            case APPEND, SYMLINKS, SET_TIMESTAMPS, SERVER_SIDE_COPY -> false;
        };
    }

    // ---------------------------------------------------------------- helpers

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) return "/";
        String p = path.startsWith("/") ? path : "/" + path;
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }

    private static String dirOf(String normalized) {
        int slash = normalized.lastIndexOf('/');
        return slash <= 0 ? "/" : normalized.substring(0, slash);
    }

    private static String nameOf(String normalized) {
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private File cacheDir() {
        File dir = new File(appContext.getCacheDir(), "ftp-transfer");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** The EZFtpFile handle for a path, obtained from its parent listing. */
    private EZFtpFile findHandle(String normalized) throws RemoteException {
        requireLockHeld("findHandle");
        String parent = dirOf(normalized);
        String name = nameOf(normalized);
        changeDir(parent);
        List<EZFtpFile> raw = awaitList((ftp, cb) -> ftp.getCurDirFileList(cb));
        if (raw == null) return null;
        for (EZFtpFile f : raw) {
            if (f != null && name.equals(f.getName())) return f;
        }
        return null;
    }

    private void changeDir(String path) throws RemoteException {
        requireLockHeld("changeDir");
        String target = normalize(path);
        // Explicit witness: without it T infers to Object and the lambda's
        // callback parameter stops matching changeDirectory's String answer.
        this.<String>await((ftp, cb) -> ftp.changeDirectory(target, cb));
    }

    // ---- callback plumbing -------------------------------------------------
    //
    // EZ-FTP has no blocking API, so each call wraps its callback and waits on a
    // latch. Every one of these runs while the caller holds #lock; callbacks are
    // delivered on EZ-FTP's own thread, so waiting cannot deadlock.

    private interface Call<T> {
        void invoke(IEZFtpClient ftp, OnEZFtpCallBack<T> callback);
    }

    /** Fire a call and block until it answers; throws on server error. */
    private <T> T await(Call<T> call) throws RemoteException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RemoteException> failure = new AtomicReference<>();
        try {
            call.invoke(client, new OnEZFtpCallBack<T>() {
                @Override
                public void onSuccess(T response) {
                    result.set(response);
                    latch.countDown();
                }

                @Override
                public void onFail(int code, String msg) {
                    failure.set(toRemoteException(code, msg));
                    latch.countDown();
                }
            });
        } catch (Exception e) {
            throw new RemoteException("FTP call failed: " + e.getMessage(), e);
        }
        awaitLatch(latch, failure);
        return result.get();
    }

    /** For the EZ-FTP calls that answer with Void. */
    private void awaitVoid(Call<Void> call) throws RemoteException {
        await(call);
    }

    /** File listing, the one call whose result the abstraction needs. */
    private List<EZFtpFile> awaitList(Call<List<EZFtpFile>> call) throws RemoteException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<EZFtpFile>> result = new AtomicReference<>();
        AtomicReference<RemoteException> failure = new AtomicReference<>();
        try {
            call.invoke(client, new OnEZFtpCallBack<>() {
                @Override
                public void onSuccess(List<EZFtpFile> response) {
                    result.set(response);
                    latch.countDown();
                }

                @Override
                public void onFail(int code, String msg) {
                    failure.set(toRemoteException(code, msg));
                    latch.countDown();
                }
            });
        } catch (Exception e) {
            throw new RemoteException("FTP call failed: " + e.getMessage(), e);
        }
        awaitLatch(latch, failure);
        return result.get();
    }

    private void awaitLatch(CountDownLatch latch, AtomicReference<RemoteException> failure)
            throws RemoteException {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new RemoteException("FTP timed out after " + TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("FTP interrupted", e);
        }
        RemoteException f = failure.get();
        if (f != null) throw f;
    }

    /** 530/534/550-on-login are the codes EZ-FTP uses for a rejected login. */
    private static RemoteException toRemoteException(int code, String msg) {
        String m = msg == null ? "" : msg.toLowerCase();
        boolean auth = code == 530 || code == 532
                || m.contains("login") || m.contains("password");
        return auth
                ? RemoteException.auth("FTP login rejected: " + msg)
                : new RemoteException("FTP error " + code + ": " + msg, code, false, null);
    }

    /** Blocks until the transfer reports COMPLETED, and surfaces ERROR/ABORTED. */
    private void awaitTransfer(TransferCall call) throws RemoteException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<RemoteException> failure = new AtomicReference<>();
        AtomicReference<Boolean> completed = new AtomicReference<>(false);
        try {
            call.invoke(new OnEZFtpDataTransferCallback() {
                @Override
                public void onStateChanged(int state) {
                    if (state == COMPLETED) {
                        completed.set(true);
                        latch.countDown();
                    } else if (state == ERROR || state == ABORTED) {
                        latch.countDown();
                    }
                }

                @Override
                public void onTransferred(long fileSize, int transferredSize) {
                    // progress only
                }

                @Override
                public void onErr(int code, String msg) {
                    failure.set(new RemoteException("FTP transfer failed: " + msg, code, false, null));
                    latch.countDown();
                }
            });
        } catch (Exception e) {
            throw new RemoteException("FTP transfer could not start: " + e.getMessage(), e);
        }
        awaitLatch(latch, failure);
        if (!Boolean.TRUE.equals(completed.get())) {
            throw new RemoteException("FTP transfer did not complete");
        }
    }

    /** Receives the watcher that awaitTransfer waits on. */
    private interface TransferCall {
        void invoke(OnEZFtpDataTransferCallback callback);
    }

    private void requireConnected() throws RemoteException {
        if (client == null || !client.isConnected()) {
            throw new RemoteException("FTP is not connected");
        }
    }

    /**
     * Every path that mutates or reads the server CWD must be called with the
     * lock held. Checking it here turns what would be a silent race (two panes
     * navigating at once, one changing CWD under the other's listing) into an
     * immediate, attributable failure.
     */
    private void requireLockHeld(String where) {
        if (!lock.isHeldByCurrentThread()) {
            throw new IllegalStateException(where + " called without holding the FTP lock");
        }
    }
}
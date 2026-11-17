package org.lsposed.lspatch.share.remote;

import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * A module's remote files, laid out the way Vector's daemon lays them out.
 *
 * <p>Ports the module-file half of {@code org.matrix.vector.daemon.data.FileSystem}: a per-{@code
 * (user, module)} directory, and {@code ensureModuleFilePath}'s rule that a name is a single path
 * component — no separator, no {@code .}/{@code ..} traversal. The daemon additionally labels the
 * directory with an SELinux context and {@code chown}s it so a module app of a different uid may
 * reach it; a rootless LSPatch cannot and does not need to — the directory is owned by whichever app
 * hosts the store (the manager, or the embedded host), and every reader reaches it over the binder.</p>
 *
 * <p>The trust split of the two services is enforced here by {@code readOnly}: the injected hook
 * ({@code IModuleService}) opens read-only, the module app ({@code IXposedService}) may create and
 * write — "a module app may write its remote files, a hooked process may only read them."</p>
 */
public class RemoteFileStore {

    private static final String TAG = "LSPatch-RemoteFiles";

    private final Context context;
    private volatile File baseDir;

    public RemoteFileStore(Context context) {
        // The context is used directly (not getApplicationContext(), which is null during the
        // bootstrap where this is built) and the directory is resolved lazily, so construction does
        // not depend on getFilesDir() being ready yet.
        this.context = context;
    }

    // One tree under the host app's files dir, mirroring the daemon's modules/<user>/<pkg>/files.
    private File baseDir() {
        File dir = baseDir;
        if (dir == null) {
            dir = new File(context.getFilesDir(), "lspatch-xfiles");
            baseDir = dir;
        }
        return dir;
    }

    /** Names are single path components — no separators, no {@code .}/{@code ..} traversal. */
    private static void ensureModuleFilePath(String name) {
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("invalid remote file name: " + name);
        }
    }

    private File resolveModuleDir(String packageName, int userId) {
        File dir = new File(baseDir(), userId + "/" + packageName + "/files");
        dir.mkdirs();
        return dir;
    }

    public String[] listRemoteFiles(String packageName, int userId) {
        String[] names = resolveModuleDir(packageName, userId).list();
        return names != null ? names : new String[0];
    }

    /**
     * Opens a remote file. {@code readOnly} distinguishes the hook (read) from the module app
     * (create/read-write). Returns null when a read-only open finds nothing, so the caller can raise
     * the {@code FileNotFoundException} the API documents.
     */
    public ParcelFileDescriptor openRemoteFile(String packageName, int userId, String name, boolean readOnly) {
        ensureModuleFilePath(name);
        File file = new File(resolveModuleDir(packageName, userId), name);
        int mode = readOnly
                ? ParcelFileDescriptor.MODE_READ_ONLY
                : ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE;
        try {
            return ParcelFileDescriptor.open(file, mode);
        } catch (FileNotFoundException e) {
            if (!readOnly) Log.w(TAG, "openRemoteFile " + name, e);
            return null;
        }
    }

    public boolean deleteRemoteFile(String packageName, int userId, String name) {
        ensureModuleFilePath(name);
        return new File(resolveModuleDir(packageName, userId), name).delete();
    }
}

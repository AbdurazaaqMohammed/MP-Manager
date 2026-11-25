package io.github.abdurazaaqmohammed.provider;


import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.util.concurrent.atomic.AtomicBoolean;

import rikka.shizuku.Shizuku;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.privilege.api.PrivilegeManager;

/**
 * MP Manager as a {@link DocumentsProvider}.
 *
 * <p>The provider backs the content URIs returned by MP Manager's picker.
 * Available privileged storage is also advertised in DocumentsUI, whose
 * roots are shared between OPEN_DOCUMENT and GET_CONTENT.</p>
 *
 * <p>Unlike the providers that patchers inject into other apps, this one runs
 * in MP Manager's own process, so it can use the elevated backends the rest of
 * the app uses: root (when root file operations are enabled) and Shizuku. That
 * is what makes {@code /storage/emulated/0/Android/data}, which a normal app
 * cannot even list, browsable from another app through this provider.</p>
 *
 * <p>The selected, enabled backend exposes either the root filesystem or
 * Android data through Shizuku. Internal-storage URIs from the activity remain
 * readable without advertising a duplicate internal-storage sidebar root.</p>
 *
 * <p>Document ids are absolute paths. Every incoming id is resolved through
 * {@code getCanonicalPath()} and checked against the exported bases, so a
 * crafted {@code ../} id or a symlink cannot walk outside them.</p>
 *
 * <p>Directly readable files return a file descriptor. Elevated reads are
 * staged privately and return a seekable descriptor after the copy succeeds.
 * Writes are supported where the app itself can write; elevated paths are
 * exposed read-only.</p>
 */
public class StorageDocumentsProvider extends DocumentsProvider {

    private static final String ROOT_APPDATA = "appdata";
    private static final String ROOT_FS = "root";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_ICON,
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
    };

    /** Directories first, then case-insensitive by name. */
    private static final Comparator<File> ORDER = (a, b) -> {
        boolean ad = safeIsDirectory(a);
        boolean bd = safeIsDirectory(b);
        if (ad != bd) return ad ? -1 : 1;
        return a.getName().compareToIgnoreCase(b.getName());
    };

    private SharedPreferences preferences;
    private final AtomicBoolean checkingRoot =
            new AtomicBoolean();
    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (prefs, key) -> {
                if ("su_command".equals(key)) {
                    PrivilegeManager.Root.refreshRootCache(getContext());
                }
                if ("working_mode".equals(key) || "root_file_ops".equals(key)
                        || "shizuku_file_ops".equals(key) || "su_command".equals(key)) {
                    notifyRootsChanged();
                }
            };
    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::notifyRootsChanged;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::notifyRootsChanged;
    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> notifyRootsChanged();

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) return false;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener);
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionListener);
        return true;
    }

    @Override
    public void shutdown() {
        if (preferences != null) preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        super.shutdown();
    }

    private void notifyRootsChanged() {
        Context context = getContext();
        if (context != null) context.getContentResolver().notifyChange(
                DocumentsContract.buildRootsUri(context.getPackageName() + ".documents"), null);
    }

    private void checkRootInBackground(Context context) {
        if (!PrivilegeManager.Root.isRootFileOpsEnabled(context)
                || PrivilegeManager.Root.isRootAvailableCached(context)
                || !checkingRoot.compareAndSet(false, true)) return;
        // Never block DocumentsUI's shared roots cache on a su process.
        new Thread(() -> {
            try {
                PrivilegeManager.Root.isRootAvailable(context);
            } finally {
                checkingRoot.set(false);
            }
        }, "document-roots").start();
    }

    // ======== roots ========

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        Context context = getContext();
        if (context == null) return result;
        String internal = internalRoot();
        checkRootInBackground(context);
        try {
            if (PrivilegeManager.active(context) == PrivilegeManager.Backend.SHIZUKU
                    && androidDataReachable(context)) {
                addRoot(result, context, ROOT_APPDATA,
                        context.getString(R.string.storage_provider_label)
                                + " (Shizuku)",
                        context.getString(R.string.storage_provider_appdata),
                        internal + "/Android/data");
            }
            if (rootReachable(context)) {
                addRoot(result, context, ROOT_FS,
                        context.getString(R.string.storage_provider_label) + " (root)",
                        context.getString(R.string.storage_provider_root), "/");
            }
        } catch (Throwable ignored) {
            // A provider that throws here makes the whole picker fail; an
            // incomplete root list is far better.
        }
        return result;
    }

    private void addRoot(MatrixCursor result, Context context, String id, String title,
                         String summary, String path) {
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, id);
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY
                        | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(DocumentsContract.Root.COLUMN_TITLE, title);
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, summary);
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, path);
        row.add(DocumentsContract.Root.COLUMN_ICON, context.getApplicationInfo().icon);
    }

    /** Android/{data,obb,media} is only listable as root or as the shell user. */
    private static boolean androidDataReachable(Context context) {
        try {
            if (!PrivilegeManager.Files.fileOpsConfigured(context)) return false;
            if (PrivilegeManager.active(context) == PrivilegeManager.Backend.ROOT) {
                return PrivilegeManager.Root.isRootAvailableCached(context);
            }
            return PrivilegeManager.Shizuku.ready();
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean rootReachable(Context context) {
        try {
            return PrivilegeManager.Root.isRootFileOpsEnabled(context)
                    && PrivilegeManager.Root.isRootAvailableCached(context);
        } catch (Throwable e) {
            return false;
        }
    }

    private static String internalRoot() {
        try {
            File dir = Environment.getExternalStorageDirectory();
            if (dir != null) return dir.getAbsolutePath();
        } catch (Throwable ignored) {
        }
        return "/storage/emulated/0";
    }

    /** Bases a document id may resolve to, given what is currently usable. */
    private List<File> exportedBases() {
        Context context = getContext();
        List<File> bases = new ArrayList<>();
        String internal = internalRoot();
        bases.add(new File(internal));
        if (context != null && androidDataReachable(context)) {
            bases.add(new File(internal + "/Android/data"));
        }
        if (context != null && PrivilegeManager.Root.isRootFileOpsEnabled(context)
                && PrivilegeManager.Files.fileOpsOn(context)) {
            bases.add(new File("/"));
        }
        return bases;
    }

    // ======== documents ========

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        addFileRow(result, fileForDocId(documentId));
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        for (File child : listChildren(fileForDocId(parentDocumentId))) {
            addFileRow(result, child);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
                                             CancellationSignal signal)
            throws FileNotFoundException {
        File file = fileForDocId(documentId);
        if (safeIsDirectory(file)) throw new FileNotFoundException("Not a file: " + documentId);
        boolean wantsWrite = mode != null && (mode.indexOf('w') >= 0
                || mode.indexOf('a') >= 0 || mode.indexOf('t') >= 0);
        if (wantsWrite) {
            // Elevated paths are read-only here on purpose: the write would have
            // to be staged and pushed back on close, and silently accepting it
            // would report success for data that never landed.
            if (!safeCanWrite(file)) {
                throw new FileNotFoundException("Read-only document: " + documentId);
            }
            try {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
            } catch (IOException e) {
                throw new FileNotFoundException("Failed to open " + documentId + ": " + e.getMessage());
            }
        }
        Context context = getContext();
        try {
            return PrivilegeManager.Files.openReadFd(context, file.getAbsolutePath(), signal);
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to open " + documentId + ": " + e.getMessage());
        }
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        Context context = getContext();
        File parent = fileForDocId(parentDocumentId);
        File target = new File(parent, displayName);
        boolean elevated = PrivilegeManager.Files.needsShellAccess(context, parent.getAbsolutePath());
        try {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                PrivilegeManager.Files.mkdir(context, target.getAbsolutePath(), elevated);
            } else {
                PrivilegeManager.Files.touch(context, target.getAbsolutePath(), elevated);
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to create " + displayName + ": " + e.getMessage());
        }
        return target.getAbsolutePath();
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        Context context = getContext();
        File file = fileForDocId(documentId);
        if (isExportedBase(file)) {
            throw new FileNotFoundException("Refusing to delete a provider root: " + documentId);
        }
        boolean elevated = PrivilegeManager.Files.needsShellAccess(context, file.getAbsolutePath());
        try {
            PrivilegeManager.Files.deleteTree(context, Collections.singletonList(documentId),
                    elevated, null);
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to delete " + documentId + ": " + e.getMessage());
        }
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        Context context = getContext();
        File file = fileForDocId(documentId);
        File target = new File(file.getParentFile(), displayName);
        boolean elevated = PrivilegeManager.Files.needsShellAccess(context, file.getAbsolutePath());
        try {
            PrivilegeManager.Files.rename(context, documentId, target.getAbsolutePath(), elevated);
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to rename " + documentId + ": " + e.getMessage());
        }
        // Ids are paths, so a rename mints a new one; returning it makes the
        // framework re-point instead of keeping the stale id.
        return target.getAbsolutePath();
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        return mimeTypeOf(fileForDocId(documentId));
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        try {
            String parent = fileForDocId(parentDocumentId).getCanonicalPath();
            String child = fileForDocId(documentId).getCanonicalPath();
            return child.equals(parent) || child.startsWith(prefixOf(parent));
        } catch (Exception e) {
            return false;
        }
    }

    // ======== helpers ========

    private void addFileRow(MatrixCursor result, File file) {
        boolean dir = safeIsDirectory(file);
        int flags = 0;
        boolean writable = safeCanWrite(file);
        if (dir) {
            if (writable) flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (writable) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
        }
        if (writable) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                    | DocumentsContract.Document.FLAG_SUPPORTS_RENAME;
        }

        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath());
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayNameOf(file));
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeTypeOf(file));
        row.add(DocumentsContract.Document.COLUMN_SIZE, dir ? 0L : safeLength(file));
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, safeLastModified(file));
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
    }

    /** The root of a volume shows its summary name, not the folder name. */
    private String displayNameOf(File file) {
        String path = file.getAbsolutePath();
        if (path.equals(internalRoot())) {
            return getContext() == null ? file.getName()
                    : getContext().getString(R.string.storage_provider_label);
        }
        if (path.equals("/")) return getContext().getString(R.string.storage_provider_root);
        if (path.equals(internalRoot() + "/Android/data")) return "data";
        return file.getName();
    }

    /**
     * Children of {@code parent}, falling back to the elevated backend when the
     * app cannot list the folder itself (Android/data, /data, ...).
     */
    private File[] listChildren(File parent) {
        String path = parent.getAbsolutePath();
        File[] children = null;
        try {
            children = parent.listFiles();
        } catch (Throwable ignored) {
        }
        boolean needsStat = children == null || children.length == 0;
        if (children != null) {
            for (File entry : children) {
                if (!entry.isDirectory() && !entry.isFile()) needsStat = true;
            }
        }
        if (needsStat && PrivilegeManager.Files.fileOpsConfigured(getContext())
                && PrivilegeManager.Files.fileOpsOn(getContext())) {
            try {
                File[] elevated = PrivilegeManager.Files.listWithStat(getContext(), path);
                if (elevated != null && elevated.length > 0) children = elevated;
            } catch (Throwable ignored) {
            }
        }
        if (children == null) return new File[0];
        Arrays.sort(children, ORDER);
        return children;
    }

    private File fileForDocId(String documentId) throws FileNotFoundException {
        if (documentId == null || documentId.isEmpty()) {
            throw new FileNotFoundException("Empty document id");
        }
        File file = new File(documentId);
        if (!file.isAbsolute()) throw new FileNotFoundException("Not an absolute id: " + documentId);
        try {
            String canonical = file.getCanonicalPath();
            for (File base : exportedBases()) {
                String root = base.getCanonicalPath();
                if (canonical.equals(root) || canonical.startsWith(prefixOf(root))) {
                    // File.length/isDirectory on an app-UID File report zero/false
                    // for privileged paths. Keep the backend's stat information.
                    if (!file.canRead() && file.getParent() != null) {
                        File[] siblings = PrivilegeManager.Files.listWithStat(getContext(), file.getParent());
                        if (siblings != null) {
                            for (File sibling : siblings) {
                                if (sibling.getName().equals(file.getName())) return sibling;
                            }
                        }
                    }
                    return file;
                }
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to resolve " + documentId + ": " + e.getMessage());
        }
        throw new FileNotFoundException(documentId + " is outside the exported roots");
    }

    private boolean isExportedBase(File file) {
        String path = file.getAbsolutePath();
        for (File base : exportedBases()) {
            if (base.getAbsolutePath().equals(path)) return true;
        }
        return false;
    }

    private static String prefixOf(String dir) {
        return dir.endsWith("/") ? dir : dir + "/";
    }

    private static String mimeTypeOf(File file) {
        if (safeIsDirectory(file)) return DocumentsContract.Document.MIME_TYPE_DIR;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private static boolean safeIsDirectory(File file) {
        try {
            return file.isDirectory();
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean safeCanWrite(File file) {
        try {
            return file.canWrite();
        } catch (Throwable e) {
            return false;
        }
    }

    private static long safeLength(File file) {
        try {
            return Math.max(0L, file.length());
        } catch (Throwable e) {
            return 0L;
        }
    }

    private static long safeLastModified(File file) {
        try {
            return Math.max(0L, file.lastModified());
        } catch (Throwable e) {
            return 0L;
        }
    }
}

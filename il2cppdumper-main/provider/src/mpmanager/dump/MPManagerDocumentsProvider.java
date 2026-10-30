package mpmanager.dump;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exposes the patched app's private data directories through the Storage
 * Access Framework so any file manager that speaks SAF can browse them with no
 * root. Injected by MP-Manager together with libil2cppdumper.so: the dump
 * lands in one of the app's own data directories, and this provider — running
 * inside the app's process and UID — is the one component that can hand those
 * files out.
 *
 * <p>Declared in the manifest behind android.permission.MANAGE_DOCUMENTS (the
 * platform-signature permission the system Documents UI holds), so nothing but
 * the system file picker can bind it, and access reaches other apps only
 * through the user granting a document or tree.</p>
 *
 * <p>Exposes up to three roots, in the order the dumper uses them:
 * the app's internal data dir (/data/...), its external files dir
 * (Android/data/...), and /sdcard/il2cpp_dump. Document ids are absolute file
 * paths; every path handed back in is re-checked to sit inside one of the
 * exported roots, so a crafted id cannot walk out of the app's own data.</p>
 *
 * <p>Adapted from LSPatch's LSPatchDocumentsProvider (Apache-2.0), extended
 * with the multi-root support. Rebuild the bundled dex after editing:
 * <pre>
 *   javac -source 8 -target 8 -bootclasspath $SDK/platforms/android-*/android.jar \
 *       -d out src/mpmanager/dump/MPManagerDocumentsProvider.java
 *   $SDK/build-tools/*/d8 --min-api 23 --output . out/mpmanager/dump/*.class
 * </pre></p>
 */
public class MPManagerDocumentsProvider extends DocumentsProvider {

    private static final String[] ROOT_IDS = {"data", "ext", "sd"};

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

    /** Directories this provider is allowed to reach, canonicalised in attachInfo. */
    private File[] roots;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        List<File> resolved = new ArrayList<>();
        resolved.add(canonical(new File(context.getApplicationInfo().dataDir)));
        File ext = context.getExternalFilesDir(null);
        if (ext != null) resolved.add(canonical(ext));
        File sd = new File("/sdcard/il2cpp_dump");
        if (sd.isDirectory() || sd.mkdirs()) resolved.add(canonical(sd));
        roots = resolved.toArray(new File[0]);
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        Context context = getContext();
        if (context == null || roots == null) return result;
        ApplicationInfo appInfo = context.getApplicationInfo();
        String label = appInfo.loadLabel(context.getPackageManager()).toString();
        String[] summaries = {"App data (internal)", "App external files", "il2cpp_dump (sdcard)"};
        for (int i = 0; i < roots.length && i < ROOT_IDS.length; i++) {
            File root = roots[i];
            if (root == null) continue;
            MatrixCursor.RowBuilder row = result.newRow();
            row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_IDS[i]);
            row.add(DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_SUPPORTS_CREATE
                            | DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                            | DocumentsContract.Root.FLAG_LOCAL_ONLY);
            row.add(DocumentsContract.Root.COLUMN_TITLE, label != null ? label : context.getPackageName());
            row.add(DocumentsContract.Root.COLUMN_SUMMARY, summaries[i]);
            row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, root.getAbsolutePath());
            row.add(DocumentsContract.Root.COLUMN_ICON, appInfo.icon);
        }
        return result;
    }

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
        File parent = fileForDocId(parentDocumentId);
        File[] children = parent.listFiles();
        if (children != null) {
            for (File child : children) {
                addFileRow(result, child);
            }
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileForDocId(documentId),
                ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parent = fileForDocId(parentDocumentId);
        File target = new File(parent, displayName);
        try {
            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                if (!target.mkdir()) throw new IOException("Failed to mkdir " + target);
            } else {
                if (!target.createNewFile()) throw new IOException("Failed to create " + target);
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to create document: " + e.getMessage());
        }
        return target.getAbsolutePath();
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = fileForDocId(documentId);
        if (!deleteRecursively(file)) {
            throw new FileNotFoundException("Failed to delete " + documentId);
        }
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File file = fileForDocId(documentId);
        File target = new File(file.getParentFile(), displayName);
        if (!file.renameTo(target)) {
            throw new FileNotFoundException("Failed to rename " + documentId);
        }
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
            return child.startsWith(parent.endsWith("/") ? parent : parent + "/");
        } catch (IOException e) {
            return false;
        }
    }

    private void addFileRow(MatrixCursor result, File file) {
        int flags = 0;
        if (file.isDirectory()) {
            if (file.canWrite()) flags |= DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE;
        } else if (file.canWrite()) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_WRITE;
        }
        if (file.canWrite()) {
            flags |= DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                    | DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                    | DocumentsContract.Document.FLAG_SUPPORTS_REMOVE;
        }

        MatrixCursor.RowBuilder row = result.newRow();
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.getAbsolutePath());
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayNameOf(file));
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeTypeOf(file));
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length());
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags);
    }

    private String displayNameOf(File file) {
        Context context = getContext();
        if (context != null && roots != null && roots.length > 0 && file.equals(roots[0])) {
            String label = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
            if (label != null && label.length() > 0) return label;
        }
        return file.getName();
    }

    /**
     * Resolves a document id back to a file, refusing anything that would land
     * outside the exported roots — a crafted {@code ../} id cannot reach
     * another app's data or follow a symlink out.
     */
    private File fileForDocId(String documentId) throws FileNotFoundException {
        File file = new File(documentId);
        try {
            String canonical = file.getCanonicalPath();
            if (roots != null) {
                for (File root : roots) {
                    if (root == null) continue;
                    String base = root.getCanonicalPath();
                    if (canonical.equals(base) || canonical.startsWith(base + "/")) {
                        return file;
                    }
                }
            }
            throw new FileNotFoundException(documentId + " is outside the exported roots");
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to resolve " + documentId + ": " + e.getMessage());
        }
    }

    private static String mimeTypeOf(File file) {
        if (file.isDirectory()) return DocumentsContract.Document.MIME_TYPE_DIR;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    private static boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) return false;
                }
            }
        }
        return file.delete();
    }
}

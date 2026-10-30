package io.github.abdurazaaqmohammed.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything a pane needs to browse a Storage Access Framework provider: the
 * path encoding that turns a document into a {@link File} ({@link SafFile}),
 * the listing query, and the write operations a file manager exposes.
 *
 * <p>Two ways a location is addressed, both handled here:</p>
 * <ul>
 *   <li><b>tree</b> — a URI the user granted through the system picker
 *   ({@code ACTION_OPEN_DOCUMENT_TREE}), persisted and used to build
 *   {@code .../tree/<tree>/document/<id>} URIs;</li>
 *   <li><b>authority</b> — a provider that is exported without a permission
 *   and can be queried directly, no grant needed.</li>
 * </ul>
 *
 * <p>All calls are synchronous and belong on a background thread.</p>
 */
public final class SafFs {

    public static final String PREFIX = "saf:";
    /** Stands in for a missing tree URI in the encoded path. */
    private static final String NO_TREE = "-";

    private static final String[] DOC_PROJECTION = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
    };

    private static volatile ContentResolver resolver;

    private SafFs() {
    }

    /** Called once by the UI that first needs provider access. */
    public static void init(Context context) {
        if (resolver == null && context != null) {
            resolver = context.getApplicationContext().getContentResolver();
        }
    }

    private static ContentResolver resolver() throws IOException {
        ContentResolver r = resolver;
        if (r == null) throw new IOException("SAF not initialised");
        return r;
    }

    // ------------------------------------------------------------------
    // path encoding
    // ------------------------------------------------------------------

    public static boolean isSaf(File file) {
        return file != null && isSafPath(file.getPath());
    }

    public static boolean isSafPath(String path) {
        return path != null && path.startsWith(PREFIX);
    }

    /** Turns a {@code saf:} path back into a {@link SafFile}; other files pass through. */
    public static File wrap(File file) {
        if (file instanceof SafFile) return file;
        if (!isSaf(file)) return file;
        String[] parts = split(file.getPath());
        if (parts == null) return file;
        // Rebuilt from a stored path (history entry, last-folder preference):
        // the id is what addresses the document, the name is cosmetic.
        return new SafFile(parts[0], parts[1], parts[2], nameOfPath(parts[2]),
                DocumentsContract.Document.MIME_TYPE_DIR, 0L, 0L, true, true);
    }

    static String pathFor(String authority, String treeUri, String docId) {
        String tree = treeUri == null ? NO_TREE : Uri.encode(treeUri);
        return PREFIX + authority + "/" + tree + "/" + Uri.encode(docId);
    }

    /** {authority, treeUri-or-null, docId} or null when the path is malformed. */
    private static String[] split(String path) {
        if (!isSafPath(path)) return null;
        String rest = path.substring(PREFIX.length());
        int first = rest.indexOf('/');
        if (first <= 0) return null;
        int second = rest.indexOf('/', first + 1);
        if (second < 0) return null;
        String authority = rest.substring(0, first);
        String tree = rest.substring(first + 1, second);
        String docId = Uri.decode(rest.substring(second + 1));
        return new String[]{authority, NO_TREE.equals(tree) ? null : Uri.decode(tree), docId};
    }

    // ------------------------------------------------------------------
    // addressing
    // ------------------------------------------------------------------

    static Uri childrenUri(SafFile dir) {
        String tree = treeOf(dir);
        if (tree != null) {
            return DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(tree), dir.getDocId());
        }
        return DocumentsContract.buildChildDocumentsUri(dir.getAuthority(), dir.getDocId());
    }

    static Uri documentUri(SafFile file) {
        String tree = treeOf(file);
        if (tree != null) {
            return DocumentsContract.buildDocumentUriUsingTree(Uri.parse(tree), file.getDocId());
        }
        return DocumentsContract.buildDocumentUri(file.getAuthority(), file.getDocId());
    }

    private static String treeOf(SafFile file) {
        String[] parts = split(file.getPath());
        return parts == null ? null : parts[1];
    }

    // ------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------

    /** Children of a directory, folders first then names, or null on failure. */
    public static List<SafFile> children(SafFile dir) {
        if (dir == null) return null;
        List<SafFile> out = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = resolver().query(childrenUri(dir), DOC_PROJECTION, null, null, null);
            if (cursor == null) return null;
            String tree = treeOf(dir);
            while (cursor.moveToNext()) {
                String docId = cursor.getString(0);
                if (docId == null) continue;
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.getLong(3);
                long modified = cursor.getLong(4);
                int flags = cursor.getInt(5);
                boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                boolean writable = isDir
                        ? (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
                        : (flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0;
                out.add(new SafFile(dir.getAuthority(), tree, docId,
                        name == null ? docId : name, mime, size, modified, isDir, writable));
            }
        } catch (Exception e) {
            return out.isEmpty() ? null : out;
        } finally {
            closeQuietly(cursor);
        }
        Collections.sort(out, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
        });
        return out;
    }

    /** Re-reads one document so a wrapped path has real metadata. */
    public static SafFile stat(SafFile file) {
        Cursor cursor = null;
        try {
            cursor = resolver().query(documentUri(file), DOC_PROJECTION, null, null, null);
            if (cursor == null || !cursor.moveToFirst()) return file;
            String mime = cursor.getString(2);
            int flags = cursor.getInt(5);
            boolean isDir = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
            return new SafFile(file.getAuthority(), treeOf(file), file.getDocId(),
                    cursor.getString(1), mime, cursor.getLong(3), cursor.getLong(4), isDir,
                    isDir ? (flags & DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE) != 0
                            : (flags & DocumentsContract.Document.FLAG_SUPPORTS_WRITE) != 0);
        } catch (Exception e) {
            return file;
        } finally {
            closeQuietly(cursor);
        }
    }

    public static InputStream openInput(SafFile file) throws IOException {
        InputStream in = resolver().openInputStream(documentUri(file));
        if (in == null) throw new FileNotFoundException(file.getName());
        return in;
    }

    public static OutputStream openOutput(SafFile file) throws IOException {
        OutputStream out = resolver().openOutputStream(documentUri(file), "wt");
        if (out == null) throw new FileNotFoundException(file.getName());
        return out;
    }

    // ------------------------------------------------------------------
    // writing
    // ------------------------------------------------------------------

    public static boolean delete(SafFile file) {
        try {
            DocumentsContract.deleteDocument(resolver(), documentUri(file));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean rename(SafFile file, String displayName) {
        if (displayName == null || displayName.isEmpty()) return false;
        try {
            DocumentsContract.renameDocument(resolver(), documentUri(file), displayName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Creates a directory called {@code name} inside {@code parent}. */
    public static boolean createDirectory(SafFile parent, String name) {
        if (name == null || name.isEmpty()) return false;
        try {
            return DocumentsContract.createDocument(resolver(), documentUri(parent),
                    DocumentsContract.Document.MIME_TYPE_DIR, name) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Creates a new empty file inside {@code parent}. */
    public static boolean createFile(SafFile parent, String displayName, String mimeType) {
        if (displayName == null || displayName.isEmpty()) return false;
        try {
            return DocumentsContract.createDocument(resolver(), documentUri(parent),
                    mimeType == null ? "application/octet-stream" : mimeType, displayName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Streams a document into a real folder on disk. */
    public static void copyTo(SafFile source, File target) throws IOException {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = openInput(source);
            out = new java.io.FileOutputStream(target);
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    /**
     * Streams a real file into a provider folder. The document is created
     * first and the bytes are written to the URI the provider hands back,
     * because ids are the provider's to assign.
     */
    public static void copyFrom(File source, SafFile parent, String mimeType) throws IOException {
        Uri created;
        try {
            created = DocumentsContract.createDocument(resolver(), documentUri(parent),
                    mimeType == null ? "application/octet-stream" : mimeType, source.getName());
        } catch (Exception e) {
            throw new IOException(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        if (created == null) throw new IOException("The provider refused to create " + source.getName());
        InputStream in = null;
        OutputStream out = null;
        try {
            out = resolver().openOutputStream(created, "wt");
            if (out == null) throw new FileNotFoundException(source.getName());
            in = new java.io.FileInputStream(source);
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    /**
     * Parent of a document inside the same location, or null when it is
     * already the location's root.
     */
    public static File parentOf(SafFile file) {
        String docId = file.getDocId();
        String tree = treeOf(file);
        String rootId = rootDocId(tree);
        if (rootId != null && rootId.equals(docId)) return null;
        int slash = docId.lastIndexOf('/');
        // Path-shaped ids (the injected provider) can be trimmed; opaque ids
        // cannot, so those jump to the root rather than inventing a parent.
        if (slash > 0) {
            String parentId = docId.substring(0, slash);
            return new SafFile(file.getAuthority(), tree, parentId,
                    nameOfPath(parentId), DocumentsContract.Document.MIME_TYPE_DIR,
                    0L, 0L, true, true);
        }
        if (rootId != null) {
            return new SafFile(file.getAuthority(), tree, rootId,
                    nameOfPath(rootId), DocumentsContract.Document.MIME_TYPE_DIR,
                    0L, 0L, true, true);
        }
        return null;
    }

    private static String rootDocId(String treeUri) {
        if (treeUri == null) return null;
        try {
            return DocumentsContract.getTreeDocumentId(Uri.parse(treeUri));
        } catch (Exception e) {
            return null;
        }
    }

    private static String nameOfPath(String docId) {
        int slash = docId.lastIndexOf('/');
        String name = slash >= 0 ? docId.substring(slash + 1) : docId;
        return name.isEmpty() ? docId : name;
    }

    /** Root document of a granted tree, wrapped as a SafFile. */
    public static SafFile treeRoot(Uri treeUri) {
        String docId;
        try {
            docId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            return null;
        }
        SafFile root = new SafFile(treeUri.getAuthority(), treeUri.toString(), docId,
                null, DocumentsContract.Document.MIME_TYPE_DIR, 0L, 0L, true, true);
        return stat(root);
    }

    /**
     * The ".." row of a pane: a document that displays as ".." but points at
     * {@code dir} itself, so clicking it navigates to that folder.
     */
    public static SafFile asUp(SafFile dir) {
        return new SafFile(dir.getAuthority(), treeOf(dir), dir.getDocId(), "..",
                DocumentsContract.Document.MIME_TYPE_DIR, 0L, 0L, true, true);
    }

    private static void closeQuietly(Cursor cursor) {
        if (cursor == null) return;
        try {
            cursor.close();
        } catch (Exception ignored) {
        }
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}

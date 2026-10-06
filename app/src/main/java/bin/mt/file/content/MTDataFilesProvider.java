package bin.mt.file.content;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * DocumentsProvider that MP Manager injects into a target APK so the app's
 * own data directories become reachable over the Storage Access Framework
 * (MT Manager's "local storage", or any other SAF client).
 *
 * <p>Root document: the package name, whose children are the four exported
 * directories - {@code data}, {@code user_de_data}, {@code android_data} and
 * {@code android_obb}. Below those, documents are regular files.</p>
 *
 * <p>Interop columns/methods used by MT Manager: cursor columns
 * {@link #COLUMN_MT_PATH} (absolute path) and {@link #COLUMN_MT_EXTRAS}
 * ({@code mode|uid|gid[|linkTarget]}), plus the {@code mt:}-prefixed
 * {@link #call} methods for timestamp / permission / symlink changes.</p>
 */
public class MTDataFilesProvider extends DocumentsProvider {

    public static final String COLUMN_MT_EXTRAS = "mt_extras";
    public static final String COLUMN_MT_PATH = "mt_path";
    public static final String METHOD_SET_LAST_MODIFIED = "mt:setLastModified";
    public static final String METHOD_SET_PERMISSIONS = "mt:setPermissions";
    public static final String METHOD_CREATE_SYMLINK = "mt:createSymlink";

    private static final String EXTRA_URI = "uri";
    private static final String EXTRA_TIME = "time";
    private static final String EXTRA_PERMISSIONS = "permissions";
    private static final String EXTRA_PATH = "path";
    private static final String RESULT = "result";
    private static final String MESSAGE = "message";

    private static final String TYPE_DATA = "data";
    private static final String TYPE_USER_DE_DATA = "user_de_data";
    private static final String TYPE_ANDROID_DATA = "android_data";
    private static final String TYPE_ANDROID_OBB = "android_obb";

    private static final int S_IFMT = 0170000;
    private static final int S_IFLNK = 0120000;

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[]{
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_DOCUMENT_ID
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            COLUMN_MT_EXTRAS,
    };

    private String packageName;
    private File dataDir;
    private File userDeDataDir;
    private File androidDataDir;
    private File androidObbDir;

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        packageName = context.getPackageName();
        dataDir = context.getFilesDir().getParentFile();
        String dataDirPath = dataDir.getPath();
        if (dataDirPath.startsWith("/data/user/")) {
            userDeDataDir = new File("/data/user_de/" + dataDirPath.substring("/data/user/".length()));
        }
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) {
            androidDataDir = externalFilesDir.getParentFile();
        }
        androidObbDir = context.getObbDir();
        return true;
    }

    /**
     * Resolves a document id to its file. {@code null} means the root document
     * (the package name itself).
     */
    private File resolveDocId(String docId, boolean checkExists) throws FileNotFoundException {
        if (docId == null || !docId.startsWith(packageName)) {
            throw new FileNotFoundException(docId + " not found");
        }
        String rest = docId.substring(packageName.length());
        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        if (rest.isEmpty()) {
            return null;
        }
        String type;
        String subPath;
        int slash = rest.indexOf('/');
        if (slash < 0) {
            type = rest;
            subPath = "";
        } else {
            type = rest.substring(0, slash);
            subPath = rest.substring(slash + 1);
        }
        File base = baseDirForType(type);
        if (base == null) {
            throw new FileNotFoundException(docId + " not found");
        }
        File file = subPath.isEmpty() ? base : new File(base, subPath);
        if (checkExists) {
            try {
                Os.lstat(file.getPath());
            } catch (Exception e) {
                throw new FileNotFoundException(docId + " not found");
            }
        }
        return file;
    }

    private File resolveDocId(String docId) throws FileNotFoundException {
        return resolveDocId(docId, true);
    }

    private File baseDirForType(String type) {
        if (TYPE_DATA.equalsIgnoreCase(type)) {
            return dataDir;
        }
        if (TYPE_USER_DE_DATA.equalsIgnoreCase(type)) {
            return userDeDataDir;
        }
        if (TYPE_ANDROID_DATA.equalsIgnoreCase(type)) {
            return androidDataDir;
        }
        if (TYPE_ANDROID_OBB.equalsIgnoreCase(type)) {
            return androidObbDir;
        }
        return null;
    }

    private String typeForDir(File file) {
        String path = file.getPath();
        if (path.equals(dataDir.getPath())) {
            return TYPE_DATA;
        }
        if (userDeDataDir != null && path.equals(userDeDataDir.getPath())) {
            return TYPE_USER_DE_DATA;
        }
        if (androidDataDir != null && path.equals(androidDataDir.getPath())) {
            return TYPE_ANDROID_DATA;
        }
        if (androidObbDir != null && path.equals(androidObbDir.getPath())) {
            return TYPE_ANDROID_OBB;
        }
        return null;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        Context context = getContext();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        String title = applicationInfo.loadLabel(context.getPackageManager()).toString();
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        MatrixCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, packageName);
        row.add(Root.COLUMN_DOCUMENT_ID, packageName);
        row.add(Root.COLUMN_SUMMARY, packageName);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD);
        row.add(Root.COLUMN_TITLE, title);
        row.add(Root.COLUMN_MIME_TYPES, "*/*");
        row.add(Root.COLUMN_ICON, applicationInfo.icon);
        return result;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        addRow(result, documentId, null);
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        if (parentDocumentId.endsWith("/")) {
            parentDocumentId = parentDocumentId.substring(0, parentDocumentId.length() - 1);
        }
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        File parent = resolveDocId(parentDocumentId);
        if (parent == null) {
            addRow(result, parentDocumentId + "/" + TYPE_DATA, dataDir);
            if (androidDataDir != null && androidDataDir.exists()) {
                addRow(result, parentDocumentId + "/" + TYPE_ANDROID_DATA, androidDataDir);
            }
            if (androidObbDir != null && androidObbDir.exists()) {
                addRow(result, parentDocumentId + "/" + TYPE_ANDROID_OBB, androidObbDir);
            }
            if (userDeDataDir != null && userDeDataDir.exists()) {
                addRow(result, parentDocumentId + "/" + TYPE_USER_DE_DATA, userDeDataDir);
            }
        } else {
            File[] children = parent.listFiles();
            if (children != null) {
                for (File child : children) {
                    addRow(result, parentDocumentId + "/" + child.getName(), child);
                }
            }
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        File file = resolveDocId(documentId, false);
        if (file == null) {
            throw new FileNotFoundException(documentId + " not found");
        }
        return ParcelFileDescriptor.open(file, parseFileMode(mode));
    }

    private static int parseFileMode(String mode) {
        switch (mode) {
            case "r":
                return ParcelFileDescriptor.MODE_READ_ONLY;
            case "w":
            case "wt":
                return ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE;
            case "wa":
                return ParcelFileDescriptor.MODE_WRITE_ONLY
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_APPEND;
            case "rw":
                return ParcelFileDescriptor.MODE_READ_WRITE
                        | ParcelFileDescriptor.MODE_CREATE;
            case "rwt":
                return ParcelFileDescriptor.MODE_READ_WRITE
                        | ParcelFileDescriptor.MODE_CREATE
                        | ParcelFileDescriptor.MODE_TRUNCATE;
            default:
                throw new IllegalArgumentException("Invalid mode: " + mode);
        }
    }

    @Override
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        File parent = resolveDocId(parentDocumentId);
        if (parent == null) {
            throw new FileNotFoundException("Cannot create a document under " + parentDocumentId);
        }
        File created = new File(parent, displayName);
        int suffix = 2;
        while (created.exists()) {
            created = new File(parent, displayName + " (" + suffix++ + ")");
        }
        boolean ok;
        if (Document.MIME_TYPE_DIR.equals(mimeType)) {
            ok = created.mkdir();
        } else {
            try {
                ok = created.createNewFile();
            } catch (IOException e) {
                ok = false;
            }
        }
        if (ok) {
            return parentDocumentId.endsWith("/")
                    ? parentDocumentId + created.getName()
                    : parentDocumentId + "/" + created.getName();
        }
        throw new FileNotFoundException("Failed to create " + displayName + " under " + parentDocumentId);
    }

    @Override
    public void deleteDocument(String documentId) throws FileNotFoundException {
        File file = resolveDocId(documentId);
        if (file == null || !deleteRecursively(file)) {
            throw new FileNotFoundException("Failed to delete " + documentId);
        }
    }

    private static boolean deleteRecursively(File file) {
        if (file.isDirectory() && !isSymbolicLink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private static boolean isSymbolicLink(File file) {
        try {
            StructStat stat = Os.lstat(file.getPath());
            return (stat.st_mode & S_IFMT) == S_IFLNK;
        } catch (ErrnoException e) {
            return false;
        }
    }

    @Override
    public void removeDocument(String documentId, String parentDocumentId) throws FileNotFoundException {
        deleteDocument(documentId);
    }

    @Override
    public String renameDocument(String documentId, String displayName) throws FileNotFoundException {
        File file = resolveDocId(documentId);
        if (file == null) {
            throw new FileNotFoundException("Cannot rename " + documentId);
        }
        File target = new File(file.getParentFile(), displayName);
        if (!file.renameTo(target)) {
            throw new FileNotFoundException("Failed to rename " + documentId + " to " + displayName);
        }
        int cut = documentId.lastIndexOf('/', documentId.length() - 2);
        if (cut < 0) {
            throw new FileNotFoundException("Failed to rename " + documentId + " to " + displayName);
        }
        return documentId.substring(0, cut) + "/" + displayName;
    }

    @Override
    public String moveDocument(String sourceDocumentId, String sourceParentDocumentId,
                               String targetParentDocumentId) throws FileNotFoundException {
        File source = resolveDocId(sourceDocumentId);
        File targetDir = resolveDocId(targetParentDocumentId);
        if (source == null || targetDir == null) {
            throw new FileNotFoundException("Failed to move " + sourceDocumentId + " to " + targetParentDocumentId);
        }
        File target = new File(targetDir, source.getName());
        if (target.exists() || !source.renameTo(target)) {
            throw new FileNotFoundException("Failed to move " + sourceDocumentId + " to " + targetParentDocumentId);
        }
        return targetParentDocumentId.endsWith("/")
                ? targetParentDocumentId + target.getName()
                : targetParentDocumentId + "/" + target.getName();
    }

    @Override
    public String getDocumentType(String documentId) throws FileNotFoundException {
        File file = resolveDocId(documentId);
        return file == null ? Document.MIME_TYPE_DIR : mimeTypeOf(file);
    }

    @Override
    public boolean isChildDocument(String parentDocumentId, String documentId) {
        if (documentId.equals(parentDocumentId)) {
            return true;
        }
        return documentId.startsWith(parentDocumentId)
                && documentId.charAt(parentDocumentId.length()) == '/';
    }

    private static String mimeTypeOf(File file) {
        if (file.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            String mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle fromSuper = super.call(method, arg, extras);
        if (fromSuper != null) {
            return fromSuper;
        }
        if (method == null || !method.startsWith("mt:")) {
            return null;
        }
        Bundle out = new Bundle();
        try {
            if (extras == null) {
                out.putBoolean(RESULT, false);
                out.putString(MESSAGE, "Missing extras");
                return out;
            }
            Uri uri = extras.getParcelable(EXTRA_URI);
            if (uri == null) {
                out.putBoolean(RESULT, false);
                out.putString(MESSAGE, "Missing uri");
                return out;
            }
            java.util.List<String> segments = uri.getPathSegments();
            String documentId = segments.size() >= 4 ? segments.get(3) : segments.get(1);
            switch (method) {
                case METHOD_SET_LAST_MODIFIED: {
                    File file = resolveDocId(documentId);
                    long time = extras.getLong(EXTRA_TIME);
                    out.putBoolean(RESULT, file != null && file.setLastModified(time));
                    break;
                }
                case METHOD_SET_PERMISSIONS: {
                    File file = resolveDocId(documentId);
                    if (file == null) {
                        out.putBoolean(RESULT, false);
                        break;
                    }
                    try {
                        Os.chmod(file.getPath(), extras.getInt(EXTRA_PERMISSIONS));
                        out.putBoolean(RESULT, true);
                    } catch (ErrnoException e) {
                        out.putBoolean(RESULT, false);
                        out.putString(MESSAGE, e.getMessage());
                    }
                    break;
                }
                case METHOD_CREATE_SYMLINK: {
                    File file = resolveDocId(documentId, false);
                    if (file == null) {
                        out.putBoolean(RESULT, false);
                        break;
                    }
                    try {
                        Os.symlink(extras.getString(EXTRA_PATH), file.getPath());
                        out.putBoolean(RESULT, true);
                    } catch (ErrnoException e) {
                        out.putBoolean(RESULT, false);
                        out.putString(MESSAGE, e.getMessage());
                    }
                    break;
                }
                default:
                    out.putBoolean(RESULT, false);
                    out.putString(MESSAGE, "Unsupported method: " + method);
                    break;
            }
        } catch (Exception e) {
            out.putBoolean(RESULT, false);
            out.putString(MESSAGE, e.toString());
        }
        return out;
    }

    /**
     * Adds one row. {@code file == null} means the root document itself.
     */
    private void addRow(MatrixCursor result, String docId, File file) throws FileNotFoundException {
        if (file == null && !docId.equals(packageName)) {
            file = resolveDocId(docId);
        }
        MatrixCursor.RowBuilder row = result.newRow();
        if (file == null) {
            row.add(Document.COLUMN_DOCUMENT_ID, packageName);
            row.add(Document.COLUMN_DISPLAY_NAME, packageName);
            row.add(Document.COLUMN_SIZE, 0L);
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
            row.add(Document.COLUMN_LAST_MODIFIED, 0);
            row.add(Document.COLUMN_FLAGS, 0);
            return;
        }
        int flags = 0;
        if (file.isDirectory()) {
            if (file.canWrite()) {
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
            }
        } else if (file.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_WRITE;
        }
        File parent = file.getParentFile();
        if (parent != null && parent.canWrite()) {
            flags |= Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME;
        }
        String specialType = typeForDir(file);
        String displayName = specialType != null ? specialType : file.getName();
        row.add(Document.COLUMN_DOCUMENT_ID, docId);
        row.add(Document.COLUMN_DISPLAY_NAME, displayName);
        row.add(Document.COLUMN_SIZE, file.length());
        row.add(Document.COLUMN_MIME_TYPE, mimeTypeOf(file));
        row.add(Document.COLUMN_LAST_MODIFIED, file.lastModified());
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(COLUMN_MT_PATH, file.getAbsolutePath());
        if (specialType == null) {
            try {
                StructStat stat = Os.lstat(file.getPath());
                StringBuilder extras = new StringBuilder()
                        .append(stat.st_mode)
                        .append('|').append(stat.st_uid)
                        .append('|').append(stat.st_gid);
                if ((stat.st_mode & S_IFMT) == S_IFLNK) {
                    extras.append('|').append(Os.readlink(file.getPath()));
                }
                row.add(COLUMN_MT_EXTRAS, extras.toString());
            } catch (Exception ignored) {
            }
        }
    }
}

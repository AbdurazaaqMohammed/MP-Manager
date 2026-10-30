package io.github.abdurazaaqmohammed.utils;

import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * A Storage Access Framework document presented as a {@link File}, so a
 * provider's tree can be listed in a pane exactly like a directory on disk.
 *
 * <p>This mirrors what {@code ShizukuFile} and {@code FTPFileWrapper} do: the
 * pane, its breadcrumb and its navigation all speak {@code File}, so a remote
 * or provider-backed location only has to answer the questions a {@code File}
 * answers — name, type, size, mtime, children — and navigation works.</p>
 *
 * <p>The path is synthetic but self-describing
 * ({@code saf:<authority>/<tree>/<docId>}), so it survives history entries,
 * scroll-position keys and the last-folder preference, and
 * {@link SafFs#wrap(File)} can rebuild the object from the path alone.</p>
 *
 * <p>What a {@code File} cannot do is open a stream by path, so content
 * operations are explicit: {@link SafFs#openInput}, {@link SafFs#copyTo} and
 * friends. The pane's SAF adapter uses those; the plain on-disk code paths are
 * never handed a {@code SafFile}.</p>
 */
public class SafFile extends File {

    private final String authority;
    /** Tree URI this document was reached through, or null for a bare provider root. */
    private final String treeUri;
    private final String docId;
    private final String name;
    private final String mimeType;
    private final long size;
    private final long modified;
    private final boolean directory;
    private final boolean writable;

    SafFile(String authority, String treeUri, String docId, String name, String mimeType,
            long size, long modified, boolean directory, boolean writable) {
        super(SafFs.pathFor(authority, treeUri, docId));
        this.authority = authority;
        this.treeUri = treeUri;
        this.docId = docId;
        this.name = name;
        this.mimeType = mimeType;
        this.size = size;
        this.modified = modified;
        this.directory = directory;
        this.writable = writable;
    }

    public String getAuthority() {
        return authority;
    }

    public String getDocId() {
        return docId;
    }

    public String getMimeType() {
        return mimeType;
    }

    /**
     * Human-readable location for the pane header: the document id when the
     * provider uses path-shaped ids (the injected one does), otherwise the
     * authority plus id.
     */
    public String getDisplayPath() {
        if (docId != null && docId.startsWith("/")) return docId;
        return authority + ":" + (docId == null ? "" : docId);
    }

    /** Content URI for this document, usable with {@link android.content.ContentResolver}. */
    public Uri getDocumentUri() {
        return SafFs.documentUri(this);
    }

    @Override
    public String getName() {
        if (name != null && name.length() > 0) return name;
        return super.getName();
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isFile() {
        return !directory;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public long lastModified() {
        return modified;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return writable;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public File[] listFiles() {
        List<SafFile> children = SafFs.children(this);
        return children == null ? null : children.toArray(new SafFile[0]);
    }

    /**
     * Parent within the same location. SAF has no "parent" query, so a
     * path-shaped document id (what our injected provider uses) is trimmed;
     * anything opaque jumps to the location's root instead of guessing wrong.
     */
    @Override
    public File getParentFile() {
        return SafFs.parentOf(this);
    }

    @Override
    public boolean delete() {
        return SafFs.delete(this);
    }

    @Override
    public boolean renameTo(File dest) {
        return SafFs.rename(this, dest == null ? null : dest.getName());
    }

    @Override
    public boolean mkdir() {
        File parent = getParentFile();
        return parent instanceof SafFile && SafFs.createDirectory((SafFile) parent, getName());
    }

    @Override
    public boolean mkdirs() {
        return mkdir();
    }

    public InputStream openInput() throws IOException {
        return SafFs.openInput(this);
    }

    public OutputStream openOutput() throws IOException {
        return SafFs.openOutput(this);
    }

    @Override
    public String toString() {
        return getName();
    }
}

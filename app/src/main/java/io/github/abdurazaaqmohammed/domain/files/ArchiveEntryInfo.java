package io.github.abdurazaaqmohammed.domain.files;

import java.io.File;
import java.io.Serializable;

/**
 * Represents a single entry (file or folder) inside a streaming archive.
 * Unlike ZipEntryInfo, this does NOT hold a live handle to the archive —
 * the entry is read on-demand when the user taps it.
 */
public class ArchiveEntryInfo implements Serializable {

    private final String name;
    private final String fullPath;
    private final long size;
    private final long lastModified;
    private final boolean isDirectory;
    private final File archiveFile;
    private final String archiveType; // "tar.gz", "tar.bz2", "tar.xz", "tar", "rar", "7z"

    public ArchiveEntryInfo(String name, String fullPath, long size, long lastModified,
                            boolean isDirectory, File archiveFile, String archiveType) {
        this.name = name;
        this.fullPath = fullPath;
        this.size = size;
        this.lastModified = lastModified;
        this.isDirectory = isDirectory;
        this.archiveFile = archiveFile;
        this.archiveType = archiveType;
    }

    public String getName() { return name; }

    public String getFullPath() { return fullPath; }

    public long getSize() { return size; }

    public long getLastModified() { return lastModified; }

    public boolean isDirectory() { return isDirectory; }

    public File getArchiveFile() { return archiveFile; }

    public String getArchiveType() { return archiveType; }
}

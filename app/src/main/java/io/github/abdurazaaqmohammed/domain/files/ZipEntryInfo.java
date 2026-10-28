package io.github.abdurazaaqmohammed.domain.files;

import net.lingala.zip4j.model.FileHeader;

import java.io.File;

import io.github.abdurazaaqmohammed.utils.ArchiveBrowseUtil;

/**
 * Value holder for one zip entry. Moved here from the adapters package;
 * {@code io.github.abdurazaaqmohammed.adapters.ZipEntryInfo} remains as a
 * deprecated subclass until its last usages migrate.
 */
public class ZipEntryInfo {
    private final String name;
    private final String fullPath;
    private final boolean isDirectory;
    private long size;
    private long lastModified;
    private final File zipFile;

    public ZipEntryInfo(String name, String fullPath, boolean isDirectory, long size, long lastModified, File zipFile) {
        this.name = name;
        this.fullPath = fullPath;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.zipFile = zipFile;
    }

    public void setAggregated(long size, long lastModified) {
        this.size = size;
        this.lastModified = lastModified;
    }

    public ZipEntryInfo(FileHeader fileHeader, File zipFile, String parentPath) {
        String entryName = fileHeader.getFileName().replace('\\','/');
        boolean isDir = fileHeader.isDirectory() || entryName.endsWith("/");
        this.fullPath = entryName;
        this.isDirectory = isDir;
        String cleaned = isDir ? entryName.replaceAll("/+$","") : entryName;
        this.name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/')+1);
        long size = fileHeader.getUncompressedSize();
        long time = fileHeader.getLastModifiedTime();
        this.size = size < 0 ? 0 : size;
        this.lastModified = time >= 0 ? time : 0L;
        this.zipFile = zipFile;
    }

    public String getName() { return name; }
    public String getFullPath() { return fullPath; }
    public boolean isDirectory() { return isDirectory; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
    public File getZipFile() { return zipFile; }

    public long computeCrc32() {
        if (isDirectory || zipFile == null || fullPath == null) return -1;
        return ArchiveBrowseUtil.entryCrc32(zipFile, fullPath);
    }
}

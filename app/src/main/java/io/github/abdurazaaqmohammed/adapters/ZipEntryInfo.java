package io.github.abdurazaaqmohammed.adapters;

import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.util.zip.ZipEntry;

public class ZipEntryInfo {
    private final String name;
    private final String fullPath;
    private final boolean isDirectory;
    private final long size;
    private final long lastModified;
    private final File zipFile;
    public ZipEntryInfo(String name, String fullPath, boolean isDirectory, long size, long lastModified, File zipFile) {
        this.name = name;
        this.fullPath = fullPath;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.zipFile = zipFile;
    }

    public ZipEntryInfo(FileHeader fileHeader, File zipFile, String parentPath) {
        String entryName = fileHeader.getFileName().replace('\\','/');
        boolean isDir = fileHeader.isDirectory() || entryName.endsWith("/");
        this.fullPath = entryName;
        this.isDirectory = isDir;
        String cleaned = isDir ? entryName.replaceAll("/+$","") : entryName;
        this.name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/')+1);
        this.size = fileHeader.getUncompressedSize() < 0 ? 0 : fileHeader.getUncompressedSize();
        this.lastModified = fileHeader.getLastModifiedTime() >= 0 ? fileHeader.getLastModifiedTime() : 0L;

        this.zipFile = zipFile;
    }

    public String getName() { return name; }
    public String getFullPath() { return fullPath; }
    public boolean isDirectory() { return isDirectory; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
    public File getZipFile() { return zipFile; }
}

package io.github.abdurazaaqmohammed.adapters;

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

    public ZipEntryInfo(ZipEntry entry, File zipFile, String parentPath) {
        String entryName = entry.getName().replace('\\','/');
        boolean isDir = entry.isDirectory() || entryName.endsWith("/");
        this.fullPath = entryName;
        this.isDirectory = isDir;
        String cleaned = isDir ? entryName.replaceAll("/+$","") : entryName;
        this.name = cleaned.isEmpty() ? "/" : cleaned.substring(cleaned.lastIndexOf('/')+1);
        this.size = entry.getSize() < 0 ? 0 : entry.getSize();
        this.lastModified = entry.getTime() >= 0 ? entry.getTime() : 0L;

        this.zipFile = zipFile;
    }

    public String getName() { return name; }
    public String getFullPath() { return fullPath; }
    public boolean isDirectory() { return isDirectory; }
    public long getSize() { return size; }
    public long getLastModified() { return lastModified; }
    public File getZipFile() { return zipFile; }
}

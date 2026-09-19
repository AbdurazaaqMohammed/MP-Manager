package io.github.abdurazaaqmohammed.utils;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;

/**
 * A {@link File} carrying metadata obtained via root (su), so UI code that
 * calls {@code isFile()/isDirectory()/length()/lastModified()/exists()} keeps
 * working for root-only paths like {@code /data/data/...} where the app uid
 * has no direct stat permission.
 *
 * <p>Safety: this class never executes shell commands itself and never
 * changes anything on the device. It only caches values supplied by
 * {@link RootManager} (which validates/escapes all shell args and blocks
 * destructive paths). When no cached value is present it delegates to
 * {@code super}, i.e. plain {@code java.io.File} behaviour.
 */
public class RootFile extends File {

    private final boolean hasStat;
    private final boolean statExists;
    private final boolean statIsDirectory;
    private final boolean statIsFile;
    private final long statLength;
    private final long statLastModified;
    private final String statMode;

    public RootFile(String pathname,
                    boolean exists,
                    boolean isDirectory,
                    boolean isFile,
                    long length,
                    long lastModified,
                    String mode) {
        super(pathname);
        this.hasStat = true;
        this.statExists = exists;
        this.statIsDirectory = isDirectory;
        this.statIsFile = isFile;
        this.statLength = length;
        this.statLastModified = lastModified;
        this.statMode = mode;
    }

    public RootFile(String parent, String child,
                    boolean exists,
                    boolean isDirectory,
                    boolean isFile,
                    long length,
                    long lastModified,
                    String mode) {
        super(parent, child);
        this.hasStat = true;
        this.statExists = exists;
        this.statIsDirectory = isDirectory;
        this.statIsFile = isFile;
        this.statLength = length;
        this.statLastModified = lastModified;
        this.statMode = mode;
    }

    /** Plain wrapper without cached stat (behaves exactly like File). */
    public RootFile(String pathname) {
        super(pathname);
        this.hasStat = false;
        this.statExists = false;
        this.statIsDirectory = false;
        this.statIsFile = false;
        this.statLength = 0L;
        this.statLastModified = 0L;
        this.statMode = null;
    }

    public boolean hasRootStat() {
        return hasStat;
    }

    public String getRootMode() {
        return statMode;
    }

    @Override
    public boolean exists() {
        if (hasStat) return statExists;
        return super.exists();
    }

    @Override
    public boolean isFile() {
        if (hasStat) return statIsFile;
        return super.isFile();
    }

    @Override
    public boolean isDirectory() {
        if (hasStat) return statIsDirectory;
        return super.isDirectory();
    }

    @Override
    public long length() {
        if (hasStat) return statLength;
        return super.length();
    }

    @Override
    public long lastModified() {
        if (hasStat) return statLastModified;
        return super.lastModified();
    }

    @Override
    public boolean canRead() {
        if (hasStat) return statExists && super.canRead();
        return super.canRead();
    }

    @Override
    public File[] listFiles() {
        return super.listFiles();
        // Caller (MainActivity) performs the root fallback with stat, so
        // just return null here to signal "needs root listing".
    }

    @Override
    public File[] listFiles(FileFilter filter) {
        return super.listFiles(filter);
    }

    @Override
    public File[] listFiles(FilenameFilter filter) {
        return super.listFiles(filter);
    }

    @Override
    public String[] list() {
        return super.list();
    }

    @Override
    public String[] list(FilenameFilter filter) {
        return super.list(filter);
    }
}

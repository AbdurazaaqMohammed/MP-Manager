package io.github.abdurazaaqmohammed.util.io;

/** Exact logical byte/count totals; directories include the queried root, links are not followed. */
public record DirectoryStats(long files, long folders, long bytes, long hidden, long errors) {
    public long entries() { return files + folders; }
    public boolean complete() { return errors == 0; }
    public DirectoryStats plus(DirectoryStats other) {
        return new DirectoryStats(files + other.files, folders + other.folders, bytes + other.bytes,
                hidden + other.hidden, errors + other.errors);
    }
}

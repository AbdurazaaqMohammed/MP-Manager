package io.github.abdurazaaqmohammed.features.files.network;

/** Server-reported quota/volume space. Unknown totals are never represented as zero. */
public final class RemoteSpace {
    /** Cache identity includes the endpoint/account so editing a pin cannot show another server's quota. */
    public record Report(String identity, RemoteSpace space) {}
    public final long totalBytes;
    public final long usedBytes;
    public final long freeBytes;

    private RemoteSpace(long total, long used, long free) {
        totalBytes = total;
        usedBytes = used;
        freeBytes = free;
    }

    public static RemoteSpace fromTotalFree(long total, long free) {
        if (total < 0 || free < 0 || free > total) throw new IllegalArgumentException("Invalid volume space");
        return new RemoteSpace(total, total - free, free);
    }

    public static RemoteSpace fromQuota(long used, long free) {
        if (used < 0 || free < 0) throw new IllegalArgumentException("Invalid storage quota");
        return fromTotalFree(Math.addExact(used, free), free);
    }

    public static RemoteSpace available(long free) {
        if (free < 0) throw new IllegalArgumentException("Invalid available space");
        return new RemoteSpace(-1, -1, free);
    }
}

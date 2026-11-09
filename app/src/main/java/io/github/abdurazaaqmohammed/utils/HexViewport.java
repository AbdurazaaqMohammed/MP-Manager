package io.github.abdurazaaqmohammed.utils;

/** Bounded RecyclerView row window over a file of any long-sized length. */
public final class HexViewport {
    public static final int MAX_ROWS = 250000;
    private long firstRow;
    private long totalRows;
    public void resize(long bytes) {
        totalRows = bytes / 8 + (bytes % 8 == 0 ? 0 : 1);
        firstRow = Math.min(firstRow, Math.max(0, totalRows - MAX_ROWS));
    }
    public long firstRow() { return firstRow; }
    public int rowCount() { return (int) Math.min(MAX_ROWS, totalRows - firstRow); }
    public long offset(int position) { return (firstRow + position) * 8; }
    public int position(long offset) { return (int) (offset / 8 - firstRow); }
    public boolean ensureVisible(long offset) {
        long row = offset / 8;
        if (row >= firstRow && row < firstRow + rowCount()) return false;
        return shift(row - firstRow - MAX_ROWS / 2);
    }
    public boolean shift(long rows) {
        long next = Math.max(0, Math.min(firstRow + rows, Math.max(0, totalRows - MAX_ROWS)));
        if (next == firstRow) return false;
        firstRow = next;
        return true;
    }
    public boolean hasNextWindow() { return firstRow + rowCount() < totalRows; }
}

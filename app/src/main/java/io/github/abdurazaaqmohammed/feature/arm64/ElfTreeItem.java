package io.github.abdurazaaqmohammed.feature.arm64;

/**
 * Model representing an item in the ELF structure explorer tree view.
 */
public class ElfTreeItem {

    public enum ItemType {
        HEADER,
        SEGMENT,
        SECTION,
        SYMBOL
    }

    public final ItemType itemType;
    public final String title;
    public final String subtitle;
    public final String permissions;
    public final long address;
    public final long fileOffset;
    public final long size;
    public final boolean isClickable;

    public ElfTreeItem(ItemType itemType, String title, String subtitle, String permissions,
                       long address, long fileOffset, long size, boolean isClickable) {
        this.itemType = itemType;
        this.title = title;
        this.subtitle = subtitle;
        this.permissions = permissions;
        this.address = address;
        this.fileOffset = fileOffset;
        this.size = size;
        this.isClickable = isClickable;
    }

    public static ElfTreeItem header(String title, String countInfo) {
        return new ElfTreeItem(ItemType.HEADER, title, countInfo, null, -1, -1, 0, false);
    }

    public static ElfTreeItem segment(String name, String permissions, long vaddr, long offset, long size) {
        String sub = String.format("0x%X  ·  %s", vaddr, formatSize(size));
        return new ElfTreeItem(ItemType.SEGMENT, name, sub, permissions, vaddr, offset, size, true);
    }

    public static ElfTreeItem section(String name, String permissions, long vaddr, long offset, long size) {
        String sub = String.format("0x%X  ·  %s", vaddr, formatSize(size));
        return new ElfTreeItem(ItemType.SECTION, name, sub, permissions, vaddr, offset, size, true);
    }

    public static ElfTreeItem symbol(String name, String bindAndType, long vaddr, long offset) {
        String sub = String.format("0x%X  ·  %s", vaddr, bindAndType);
        return new ElfTreeItem(ItemType.SYMBOL, name, sub, null, vaddr, offset, 0, true);
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
    }
}

package io.github.abdurazaaqmohammed.features.dumper;

import java.io.Serializable;

/**
 * Metadata model for an Android storage partition (physical or dynamic).
 */
public class PartitionItem implements Serializable {

    public enum Group {
        COMMON,
        DYNAMIC,
        FIRMWARE,
        BOOTLOADER,
        OTHER
    }

    public enum Slot {
        A,
        B,
        SHARED
    }

    public final String rawName;
    public final String friendlyName;
    public final String description;
    public final String blockPath;
    public final String targetDev;
    public final long sizeBytes;
    public final Slot slot;
    public final boolean isActiveSlot;
    public final boolean isDynamic;
    public final String outputFileName;
    public final Group group;

    public PartitionItem(String rawName, String friendlyName, String description,
                         String blockPath, String targetDev, long sizeBytes,
                         Slot slot, boolean isActiveSlot, boolean isDynamic,
                         String outputFileName, Group group) {
        this.rawName = rawName;
        this.friendlyName = friendlyName;
        this.description = description;
        this.blockPath = blockPath;
        this.targetDev = targetDev;
        this.sizeBytes = sizeBytes;
        this.slot = slot;
        this.isActiveSlot = isActiveSlot;
        this.isDynamic = isDynamic;
        this.outputFileName = outputFileName;
        this.group = group;
    }

    public boolean isSystemOrVendor() {
        if (rawName == null) return false;
        String lower = rawName.toLowerCase();
        return lower.startsWith("system") || lower.startsWith("vendor")
                || lower.startsWith("product") || lower.startsWith("system_ext")
                || lower.startsWith("odm");
    }
}

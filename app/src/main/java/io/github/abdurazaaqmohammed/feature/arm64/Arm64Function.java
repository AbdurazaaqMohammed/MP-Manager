package io.github.abdurazaaqmohammed.feature.arm64;

import java.util.Locale;

public class Arm64Function {
    public String name;
    public String demangledName;
    public long vaddr;
    public long fileOffset;
    public long size;
    public String bindName;
    public boolean isExported;

    public int getInstructionCount() {
        return (int) (Math.max(0, size) / 4);
    }

    public String getFormattedAddress() {
        return String.format(Locale.ROOT, "0x%08X", vaddr);
    }

    public String getFormattedOffset() {
        return String.format(Locale.ROOT, "0x%08X", fileOffset);
    }

    public String getDisplayName() {
        return (demangledName != null && !demangledName.isEmpty()) ? demangledName : name;
    }

    public String getSubtitle() {
        String base = String.format(Locale.ROOT, "%s · Off: %s · %d B · %s",
                getFormattedAddress(), getFormattedOffset(), size,
                bindName != null ? bindName : "LOCAL");
        if (demangledName != null && !demangledName.equals(name)) {
            return name + "\n" + base;
        }
        return base;
    }
}

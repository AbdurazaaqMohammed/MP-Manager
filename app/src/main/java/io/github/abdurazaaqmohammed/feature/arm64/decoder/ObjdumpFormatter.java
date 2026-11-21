package io.github.abdurazaaqmohammed.feature.arm64.decoder;

public class ObjdumpFormatter implements Formatter {

    @Override
    public String formatMemAccess(MemAccessType memAccessType, String register, int offset) {
        if (memAccessType == MemAccessType.PreIndex) {
            return "[" + register + ", #" + offset + "]!";
        }
        if (memAccessType == MemAccessType.PostIndex) {
            return "[" + register + "], #" + offset;
        }
        if (memAccessType == MemAccessType.OnlyOffset) {
            if (offset == 0) {
                return "[" + register + "]";
            } else {
                return "[" + register + ", #" + offset + "]";
            }
        }
        throw new RuntimeException("Unknown type " + memAccessType);
    }

    @Override
    public String formatLabel(long addressValue) {
        return "0x" + Long.toHexString(addressValue);
    }

    @Override
    public String formatImmWithLsl(int value, int lslBits) {
        if (lslBits == 0) {
            return "#0x" + Integer.toHexString(value);
        }
        return "#0x" + Integer.toHexString(value) + ", LSL #" + lslBits;
    }

    @Override
    public boolean formatVectorRegisterListAsRange() {
        return true;
    }
}

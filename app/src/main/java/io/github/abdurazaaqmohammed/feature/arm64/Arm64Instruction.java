package io.github.abdurazaaqmohammed.feature.arm64;

import java.util.Arrays;
import java.util.Locale;

public class Arm64Instruction {
    public long address;
    public long fileOffset;
    public int opcode;
    public byte[] rawBytes;
    public byte[] originalBytes;
    public String mnemonic;
    public String fullAsm;
    public boolean isModified;

    public String getHexBytes() {
        if (rawBytes == null || rawBytes.length < 4) return "?? ?? ?? ??";
        return String.format(Locale.ROOT, "%02X %02X %02X %02X",
                rawBytes[0] & 0xFF, rawBytes[1] & 0xFF, rawBytes[2] & 0xFF, rawBytes[3] & 0xFF);
    }

    public String getOriginalHexBytes() {
        if (originalBytes == null || originalBytes.length < 4) return getHexBytes();
        return String.format(Locale.ROOT, "%02X %02X %02X %02X",
                originalBytes[0] & 0xFF, originalBytes[1] & 0xFF, originalBytes[2] & 0xFF, originalBytes[3] & 0xFF);
    }

    public boolean hasPendingChanges() {
        if (originalBytes == null || rawBytes == null) return false;
        return !Arrays.equals(rawBytes, originalBytes);
    }
}

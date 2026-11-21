package io.github.abdurazaaqmohammed.feature.arm64.decoder;

public class AsmStatement {
    public final long address;
    public String key;
    public String opcode;
    public String arg1;
    public String arg2;
    public String arg3;
    public String arg4;
    public String arg5;

    public AsmStatement(long address) {
        this.address = address;
    }

    public void markAsUnknown(int opcode) {
        this.opcode = ".inst";
        arg1 = String.format("0x%08x", opcode);
        arg2 = null;
        arg3 = null;
        arg4 = null;
        arg5 = null;
    }

    public String format() {
        final StringBuilder sb = new StringBuilder(20);
        sb.append(opcode);

        if (arg1 != null) {
            sb.append(' ').append(arg1);
        }
        if (arg2 != null) {
            sb.append(", ").append(arg2);
        }
        if (arg3 != null) {
            sb.append(", ").append(arg3);
        }
        if (arg4 != null) {
            sb.append(", ").append(arg4);
        }
        if (arg5 != null) {
            sb.append(", ").append(arg5);
        }
        return sb.toString();
    }
}

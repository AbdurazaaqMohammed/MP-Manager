package io.github.abdurazaaqmohammed.utils.nativelib;

import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free instruction decoders for the common subsets of AArch64,
 * ARM (A32) and Thumb-2 (T32). These cover the overwhelming majority of
 * instructions found in Android .so code (branches, calls, load/store,
 * ALU/move immediates, adr/adrp); anything not recognized degrades to a raw
 * word/halfword line so the output never lies about offsets or sizes.
 * x86/x86_64 is not decoded (variable-length ISA) and yields raw bytes.
 */
public final class InstructionDecoder {

    public static final class Insn {
        public final long address;
        public final int size;
        public final String text;

        Insn(long address, int size, String text) {
            this.address = address;
            this.size = size;
            this.text = text;
        }
    }

    private InstructionDecoder() {
    }

    public static boolean supports(int machine) {
        return machine == 0xB7 || machine == 0x28; // AArch64, ARM
    }

    /** Decodes up to {@code maxInstructions} starting at file offset for vaddr. */
    public static List<Insn> decode(int machine, byte[] code, long vaddr, long maxBytes, int maxInstructions) {
        if (machine == 0xB7) return decodeArm64(code, vaddr, maxBytes, maxInstructions);
        if (machine == 0x28) return decodeArm(code, vaddr, maxBytes, maxInstructions);
        return decodeRaw(code, vaddr, maxBytes, maxInstructions);
    }

    private static List<Insn> decodeRaw(byte[] code, long vaddr, long maxBytes, int max) {
        List<Insn> out = new ArrayList<>();
        for (int i = 0; i + 3 < code.length && i < maxBytes && out.size() < max; i += 16) {
            int n = Math.min(16, code.length - i);
            StringBuilder sb = new StringBuilder(".byte ");
            for (int b = 0; b < n; b++) {
                if (b > 0) sb.append(' ');
                sb.append(String.format("%02x", code[i + b]));
            }
            out.add(new Insn(vaddr + i, n, sb.toString()));
        }
        return out;
    }

    // ------------------------------------------------------------------ A64

    private static String[] x = {"x0", "x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9", "x10", "x11", "x12", "x13", "x14", "x15",
            "x16", "x17", "x18", "x19", "x20", "x21", "x22", "x23", "x24", "x25", "x26", "x27", "x28", "x29", "x30", "sp"};

    private static String[] w = {"w0", "w1", "w2", "w3", "w4", "w5", "w6", "w7", "w8", "w9", "w10", "w11", "w12", "w13", "w14", "w15",
            "w16", "w17", "w18", "w19", "w20", "w21", "w22", "w23", "w24", "w25", "w26", "w27", "w28", "w29", "w30", "wsp"};

    private static String reg(int r, boolean sf) {
        r &= 0x1F;
        return sf ? x[r] : w[r];
    }

    private static List<Insn> decodeArm64(byte[] code, long vaddr, long maxBytes, int max) {
        List<Insn> out = new ArrayList<>();
        for (int i = 0; i + 3 < code.length && i < maxBytes && out.size() < max; i += 4) {
            long pc = vaddr + i;
            int op = u32(code, i);
            out.add(new Insn(pc, 4, arm64(pc, op)));
        }
        return out;
    }

    private static String arm64(long pc, int op) {
        boolean sf = (op >>> 31) != 0;
        int opc = (op >>> 22) & 0x3;
        // B / BL
        if ((op >>> 26) == 0b000101 || (op >>> 26) == 0b100101) {
            long imm = sext(op & 0x3FFFFFF, 26) << 2;
            String label = "0x" + Long.toHexString(pc + imm);
            return ((op >>> 26) == 0b100101 ? "bl\t" : "b\t") + label;
        }
        // B.cond
        if ((op & 0xFF000010) == 0x54000000) {
            long imm = sext((op >> 5) & 0x7FFFF, 19) << 2;
            int cond = op & 0xF;
            return bcond[cond] + "\t0x" + Long.toHexString(pc + imm);
        }
        // CBZ / CBNZ
        if ((op & 0x7E000000) == 0x34000000) {
            long imm = sext((op >> 5) & 0x7FFFF, 19) << 2;
            boolean nz = (op & 0x01000000) != 0;
            return (nz ? "cbnz\t" : "cbz\t") + reg(op & 0x1F, sf) + ", 0x" + Long.toHexString(pc + imm);
        }
        // TBZ / TBNZ
        if ((op & 0x7E000000) == 0x36000000) {
            long imm = sext((op >> 5) & 0x3FFF, 14) << 2;
            int bit = ((op >>> 31) << 5) | ((op >> 19) & 0x1F);
            return ((op & 0x01000000) != 0 ? "tbnz\t" : "tbz\t") + reg(op & 0x1F, false)
                    + ", #" + bit + ", 0x" + Long.toHexString(pc + imm);
        }
        // ADRP / ADR
        if ((op & 0x9F000000) == 0x90000000) {
            long immlo = (op >> 29) & 0x3;
            long immhi = (op >> 5) & 0x7FFFF;
            long imm = (immhi << 2 | immlo);
            if ((op & 0x01000000) == 0) { // ADR
                imm = sext(imm, 21);
                return "adr\t" + reg(op & 0x1F, true) + ", 0x" + Long.toHexString(pc + imm);
            }
            imm = sext(imm, 21) << 12;
            long page = (pc & ~0xFFFL) + imm;
            return "adrp\t" + reg(op & 0x1F, true) + ", 0x" + Long.toHexString(page);
        }
        // RET / BR / BLR / BRAA-ish (reg)
        if ((op & 0xFFFFFC1F) == 0xD65F0000) return "ret";
        if ((op & 0xFFFFFC1F) == 0xD61F0000) return "br\t" + x[(op >> 5) & 0x1F];
        if ((op & 0xFFFFFC1F) == 0xD63F0000) return "blr\t" + x[(op >> 5) & 0x1F];
        // NOP / SVC
        if (op == 0xD503201F) return "nop";
        if ((op & 0xFFE0001F) == 0xD4000001) return "svc\t#0x" + Integer.toHexString((op >> 5) & 0xFFFF);
        // MOVZ / MOVK / MOVN
        if ((op & 0x1F800000) == 0x52800000) {
            int hw = (op >> 21) & 0x3;
            long imm16 = (op >> 5) & 0xFFFF;
            boolean isN = (op & 0x00800000) != 0;
            String name = isN ? "movn" : (op & 0x00400000) != 0 ? "movk" : "movz";
            return name + "\t" + reg(op & 0x1F, sf) + ", #0x" + Long.toHexString(imm16)
                    + (hw != 0 ? ", lsl #" + (hw * 16) : "");
        }
        // LDR/LDRSW/STR unsigned imm (size 00-11, V=0, opc 00/01/10)
        if ((op & 0x3B000000) == 0x38000000 && (op & 0x04800000) == 0x04000000) {
            int size = op >>> 30;
            int opc2 = (op >> 22) & 0x3;
            int rn = (op >> 5) & 0x1F;
            long imm12 = (op >> 10) & 0xFFF;
            String rt = reg(op & 0x1F, size == 3 || (size == 2 && opc2 == 1));
            int scale = opc2 == 1 ? 2 : size;
            long off = imm12 << scale;
            String opName;
            switch (size << 2 | opc2) {
                case 0x0: opName = "strb"; break;
                case 0x1: opName = "ldr"; rt = reg(op & 0x1F, false); break; // ldr w, [x, imm<<2]
                case 0x2: opName = "ldr"; rt = reg(op & 0x1F, false); break; // ldrb w
                case 0x4: opName = "strh"; break;
                case 0x5: opName = "ldrsw"; rt = reg(op & 0x1F, true); break;
                case 0x6: opName = "ldrh"; rt = reg(op & 0x1F, false); break;
                case 0x8: opName = "str"; break;
                case 0x9: opName = "ldrsw"; rt = reg(op & 0x1F, true); break;
                case 0xA: opName = "ldrsb"; rt = reg(op & 0x1F, false); break;
                case 0xC: opName = "str"; break;
                case 0xD: opName = "ldr"; rt = reg(op & 0x1F, true); break;
                case 0xE: opName = "ldrsh"; rt = reg(op & 0x1F, false); break;
                default: opName = null;
            }
            if (opName != null) {
                return opName + "\t" + rt + ", [" + x[rn] + (off != 0 ? ", #" + (opc2 == 1 ? "#" : "") + off : "") + "]";
            }
        }
        // LDP/STP
        if ((op & 0x3A000000) == 0x28000000 && (op & 0x04800000) != 0) {
            boolean load = (op & 0x00400000) != 0;
            int mode = (op >> 23) & 0x3;
            if (mode == 1 || mode == 3) { // post/pre index or signed offset (mode 2)
                int rt1 = op & 0x1F;
                int rt2 = (op >> 10) & 0x1F;
                int rn = (op >> 5) & 0x1F;
                long imm7 = sext((op >> 15) & 0x7F, 7) << (sf ? 3 : 2);
                String name = (load ? "ldp" : "stp") + (mode == 3 ? "!" : mode == 1 ? "" : "");
                String suffix = mode == 1 ? ", #" + imm7 + "!" : mode == 3 ? ", #" + imm7 : ", #" + imm7;
                return name + "\t" + reg(rt1, sf) + ", " + reg(rt2, sf) + ", [" + x[rn] + suffix + "]";
            }
        }
        // ADD/SUB imm
        if ((op & 0x1B000000) == 0x11000000) {
            boolean sub = (op & 0x40000000) != 0;
            boolean setFlags = (op & 0x00200000) != 0;
            long imm12 = (op >> 10) & 0xFFF;
            int rn = (op >> 5) & 0x1F;
            int rd = op & 0x1F;
            String name = (sub ? "sub" : "add") + (setFlags ? "s" : "");
            if (setFlags && !sub && rd == 31) return "cmp\t" + x[rn] + ", #" + imm12;
            return name + "\t" + reg(rd, sf) + ", " + reg(rn, sf) + ", #" + imm12;
        }
        // ADD/SUB shifted register
        if ((op & 0x1F000000) == 0x0B000000) {
            boolean sub = (op & 0x40000000) != 0;
            boolean setFlags = (op & 0x00200000) != 0;
            int rm = (op >> 16) & 0x1F;
            int rn = (op >> 5) & 0x1F;
            int rd = op & 0x1F;
            if (setFlags && !sub && rd == 31) return "cmp\t" + reg(rn, sf) + ", " + reg(rm, sf);
            // MOV (register) alias: ORR with rn=31
            return ((sub ? "sub" : "add") + (setFlags ? "s" : "")) + "\t" + reg(rd, sf)
                    + ", " + reg(rn, sf) + ", " + reg(rm, sf);
        }
        // ORR/MOV register
        if ((op & 0x1F000000) == 0x2A000000) {
            int rm = (op >> 16) & 0x1F;
            int rn = (op >> 5) & 0x1F;
            int rd = op & 0x1F;
            if (rn == 31) return "mov\t" + reg(rd, sf) + ", " + reg(rm, sf);
            return "orr\t" + reg(rd, sf) + ", " + reg(rn, sf) + ", " + reg(rm, sf);
        }
        // CSEL/CSINC
        if ((op & 0x1FE00C00) == 0x1A800000) {
            boolean csinc = (op & 0x00200000) == 0;
            int rm = (op >> 16) & 0x1F;
            int rn = (op >> 5) & 0x1F;
            int cond = (op >> 12) & 0xF;
            return (csinc ? "csinc\t" : "csel\t") + reg(op & 0x1F, sf) + ", " + reg(rn, sf)
                    + ", " + reg(rm, sf) + ", " + bcond[cond];
        }
        // MRS/MSR
        if ((op & 0xFFF00000) == 0xD5300000) return "mrs\t" + x[op & 0x1F] + ", S0x" + Integer.toHexString((op >> 5) & 0x7FFF);
        if ((op & 0xFFF00000) == 0xD5100000) return "msr\tS0x" + Integer.toHexString((op >> 5) & 0x7FFF) + ", " + x[op & 0x1F];
        return ".word\t0x" + String.format("%08x", op);
    }

    private static final String[] bcond = {"beq", "bne", "bcs", "bcc", "bmi", "bpl", "bvs", "bvc",
            "bhi", "bls", "bge", "blt", "bgt", "ble", "bal", "bnv"};

    /** Public wrappers used by the MCP xref scanner. */
    public static int u32pub(byte[] code, int off) {
        return u32(code, off);
    }

    public static long sextpub(long value, int bits) {
        return sext(value, bits);
    }

    private static long sext(long value, int bits) {
        if ((value & (1L << (bits - 1))) != 0) {
            value -= (1L << bits);
        }
        return value;
    }

    private static int u32(byte[] code, int off) {
        return (code[off] & 0xFF) | ((code[off + 1] & 0xFF) << 8)
                | ((code[off + 2] & 0xFF) << 16) | ((code[off + 3] & 0xFF) << 24);
    }

    private static int u16(byte[] code, int off) {
        return (code[off] & 0xFF) | ((code[off + 1] & 0xFF) << 8);
    }

    // ------------------------------------------------------------- A32 / T32

    private static final String[] r32 = {"r0", "r1", "r2", "r3", "r4", "r5", "r6", "r7", "r8", "r9", "r10", "r11", "r12", "sp", "lr", "pc"};

    private static List<Insn> decodeArm(byte[] code, long vaddr, long maxBytes, int max) {
        List<Insn> out = new ArrayList<>();
        // Heuristic start in Thumb when the LSB of a symbol was set; here we
        // decode both encodings as Thumb-2 from offset 0 (Android .so text is
        // overwhelmingly Thumb for armeabi-v7a).
        boolean thumb = true;
        int i = 0;
        while (i + 1 < code.length && i < maxBytes && out.size() < max) {
            long pc = vaddr + i;
            int hw = u16(code, i);
            if (thumb && (hw & 0xE000) != 0xE000) {
                // 16-bit Thumb
                int size = 2;
                String text = thumb16(pc, hw);
                out.add(new Insn(pc, size, text));
                i += size;
                continue;
            }
            if (i + 3 >= code.length) break;
            int op32 = (hw << 16) | u16(code, i + 2);
            int size = 4;
            String text = thumb ? thumb32(pc, op32) : arm32(pc, op32);
            out.add(new Insn(pc, size, text));
            i += size;
        }
        return out;
    }

    private static String thumb16(long pc, int hw) {
        if ((hw & 0xF800) == 0xD000 && (hw & 0x0F00) != 0x0F00) { // B<cond>
            long imm = sext(hw & 0xFF, 8) << 1;
            return bcond[(hw >> 8) & 0xF] + "\t0x" + Long.toHexString(pc + 4 + imm);
        }
        if ((hw & 0xF800) == 0xE000) { // B (unconditional)
            long imm = sext(hw & 0x7FF, 11) << 1;
            return "b\t0x" + Long.toHexString(pc + 4 + imm);
        }
        if ((hw & 0xFF00) == 0xB500 || (hw & 0xFF00) == 0xB400) { // PUSH
            return thumbRegList("push", hw & 0x1FF, (hw & 0xFF00) == 0xB500 ? "lr" : null);
        }
        if ((hw & 0xFF00) == 0xBD00 || (hw & 0xFF00) == 0xBC00) { // POP
            return thumbRegList("pop", hw & 0x1FF, (hw & 0xFF00) == 0xBD00 ? "pc" : null);
        }
        if ((hw & 0xFFC0) == 0x4700) return "bx\t" + r32[(hw >> 3) & 0xF];
        if ((hw & 0xFF80) == 0x4780) return "blx\t" + r32[(hw >> 3) & 0xF];
        if ((hw & 0xFF00) == 0x4600) { // MOV (high register)
            int rd = ((hw >> 4) & 0x8) | ((hw >> 0) & 0x7);
            int rm = (hw >> 3) & 0xF;
            return "mov\t" + r32[rd] + ", " + r32[rm];
        }
        if ((hw & 0xF800) == 0x4800) { // LDR literal
            int rt = (hw >> 8) & 0x7;
            long imm = (hw & 0xFF) << 2;
            return "ldr\t" + r32[rt] + ", [pc, #" + imm + "]\t; 0x" + Long.toHexString(((pc + 4) & ~3) + imm);
        }
        if ((hw & 0xF800) == 0x6800) { // LDR imm T1
            int imm5 = ((hw >> 6) & 0x1F) << 2;
            return "ldr\t" + r32[hw & 0x7] + ", [" + r32[(hw >> 3) & 0x7] + (imm5 != 0 ? ", #" + imm5 : "") + "]";
        }
        if ((hw & 0xF800) == 0x6000) { // STR imm T1
            int imm5 = ((hw >> 6) & 0x1F) << 2;
            return "str\t" + r32[hw & 0x7] + ", [" + r32[(hw >> 3) & 0x7] + (imm5 != 0 ? ", #" + imm5 : "") + "]";
        }
        if ((hw & 0xFE00) == 0x1C00 || (hw & 0xFE00) == 0x1800) { // ADDS/SUBS reg
            return ((hw & 0x0200) != 0 ? "adds\t" : "subs\t") + r32[hw & 0x7] + ", " + r32[(hw >> 3) & 0x7] + ", " + r32[(hw >> 6) & 0x7];
        }
        if ((hw & 0xF800) == 0x3000) return "adds\t" + r32[(hw >> 8) & 0x7] + ", #" + (hw & 0xFF);
        if ((hw & 0xF800) == 0x2000) return "movs\t" + r32[(hw >> 8) & 0x7] + ", #" + (hw & 0xFF);
        if ((hw & 0xFFC0) == 0x4280) return "cmp\t" + r32[(hw >> 3) & 0xF] + ", " + r32[hw & 0x7];
        if ((hw & 0xFF00) == 0x4300) return "orrs\t" + r32[((hw >> 4) & 0x8) | (hw & 0x7)] + ", " + r32[(hw >> 3) & 0xF];
        if (hw == 0xBF00) return "nop";
        return String.format(".short\t0x%04x", hw);
    }

    private static String thumbRegList(String name, int mask, String extra) {
        StringBuilder sb = new StringBuilder(name).append("\t{");
        boolean first = true;
        for (int r = 0; r < 9; r++) {
            if ((mask & (1 << r)) != 0) {
                if (!first) sb.append(", ");
                sb.append(r32[r]);
                first = false;
            }
        }
        if (extra != null) {
            if (!first) sb.append(", ");
            sb.append(extra);
        }
        return sb.append("}").toString();
    }

    private static String thumb32(long pc, int op) {
        int hw1 = (op >>> 16) & 0xFFFF;
        int hw2 = op & 0xFFFF;
        // BL / BLX
        if ((hw1 & 0xF800) == 0xF000 && (hw2 & 0xD000) == 0xD000) {
            long s = sext((hw1 >> 10) & 1, 1);
            long i1 = ((~(hw2 >> 13)) & 1) ^ (s == -1 ? 0 : 1);
            long i2 = ((~(hw2 >> 11)) & 1) ^ (s == -1 ? 0 : 1);
            long imm = (s << 24) | (i1 << 23) | (i2 << 22) | ((hw1 & 0x3FF) << 12) | ((hw2 & 0x7FF) << 1);
            boolean blx = (hw2 & 0x1000) == 0;
            if (s == -1) imm -= (1L << 25);
            return (blx ? "blx\t0x" : "bl\t0x") + Long.toHexString(pc + 4 + imm);
        }
        // B (unconditional T4)
        if ((hw1 & 0xF800) == 0xF000 && (hw2 & 0xD000) == 0x9000) {
            long s = sext((hw1 >> 10) & 1, 1);
            long imm = (s << 24) | (((hw1 >> 0) & 0x3FF) << 12) | (((hw2 >> 0) & 0x7FF) << 1);
            if (s == -1) imm -= (1L << 25);
            return "b.w\t0x" + Long.toHexString(pc + 4 + imm);
        }
        // LDR/STR imm T3/T4 (12-bit)
        if ((hw1 & 0xFFF0) == 0xF8D0) {
            int rn = hw1 & 0xF;
            int rt = (hw2 >> 12) & 0xF;
            long imm12 = hw2 & 0xFFF;
            return "ldr\t" + r32[rt] + ", [" + r32[rn] + (imm12 != 0 ? ", #" + imm12 : "") + "]";
        }
        if ((hw1 & 0xFFF0) == 0xF8C0) {
            int rn = hw1 & 0xF;
            int rt = (hw2 >> 12) & 0xF;
            long imm12 = hw2 & 0xFFF;
            return "str\t" + r32[rt] + ", [" + r32[rn] + (imm12 != 0 ? ", #" + imm12 : "") + "]";
        }
        if ((hw1 & 0xFFF0) == 0xF8B0) return "ldrh\t" + r32[(hw2 >> 12) & 0xF] + ", [" + r32[hw1 & 0xF] + ", #" + (hw2 & 0xFFF) + "]";
        if ((hw1 & 0xFFF0) == 0xF8A0) return "strh\t" + r32[(hw2 >> 12) & 0xF] + ", [" + r32[hw1 & 0xF] + ", #" + (hw2 & 0xFFF) + "]";
        if ((hw1 & 0xFFF0) == 0xF890) return "ldrb\t" + r32[(hw2 >> 12) & 0xF] + ", [" + r32[hw1 & 0xF] + ", #" + (hw2 & 0xFFF) + "]";
        if ((hw1 & 0xFFF0) == 0xF880) return "strb\t" + r32[(hw2 >> 12) & 0xF] + ", [" + r32[hw1 & 0xF] + ", #" + (hw2 & 0xFFF) + "]";
        // ADDW/SUBW
        if ((hw1 & 0xFBE0) == 0xF200) return "addw\t" + r32[hw2 & 0xF] + ", " + r32[(hw1 >> 0) & 0xF] + ", #" + (((hw1 >> 4) & 0xF) << 8 | (hw2 >> 0) & 0xFF);
        if ((hw1 & 0xFBE0) == 0xF2A0) return "subw\t" + r32[hw2 & 0xF] + ", " + r32[(hw1 >> 0) & 0xF] + ", #" + (((hw1 >> 4) & 0xF) << 8 | (hw2 >> 0) & 0xFF);
        // MOVW/MOVT
        if ((hw1 & 0xFBF0) == 0xF240) return "movw\t" + r32[hw2 & 0xF] + ", #0x" + Integer.toHexString((((hw1 >> 4) & 0xF) << 12) | ((hw2 >> 4) & 0xF00) | (hw2 & 0xFF));
        if ((hw1 & 0xFBF0) == 0xF2C0) return "movt\t" + r32[hw2 & 0xF] + ", #0x" + Integer.toHexString((((hw1 >> 4) & 0xF) << 12) | ((hw2 >> 4) & 0xF00) | (hw2 & 0xFF));
        // TBB/TBH? CBZ/CBNZ (32-bit? no, 16-bit). MRS/MSR
        if ((hw1 & 0xFFFF) == 0xF3EF) return "mrs\t" + r32[hw2 & 0xF] + ", S0x" + Integer.toHexString((hw2 >> 0) & 0xFF);
        if ((hw1 & 0xFFF0) == 0xF380) return "msr\tS0x" + Integer.toHexString((hw2 >> 8) & 0xFF) + ", " + r32[hw2 & 0xF];
        return String.format(".short\t0x%04x, 0x%04x", hw1, hw2);
    }

    private static String arm32(long pc, int op) {
        int cond = (op >>> 28) & 0xF;
        String c = cond == 0xE ? "" : bcond[cond].substring(1);
        // B / BL
        if ((op & 0x0F000000) == 0x0A000000 || (op & 0x0F000000) == 0x0B000000) {
            long imm = sext(op & 0xFFFFFF, 24) << 2;
            return ((op & 0x01000000) != 0 ? "bl" : "b") + c + "\t0x" + Long.toHexString(pc + 8 + imm);
        }
        // LDR/STR imm (A32)
        if ((op & 0x0C000000) == 0x04000000) {
            boolean load = (op & 0x00100000) != 0;
            boolean byte_ = (op & 0x00400000) != 0;
            int rd = (op >> 12) & 0xF;
            int rn = (op >> 16) & 0xF;
            long imm = op & 0xFFF;
            boolean up = (op & 0x00800000) != 0;
            return (load ? "ldr" : "str") + (byte_ ? "b" : "") + c + "\t" + r32[rd] + ", ["
                    + r32[rn] + (imm != 0 ? ", #" + (up ? "" : "-") + imm : "") + "]";
        }
        // Data-processing immediate
        if ((op & 0x02000000) != 0 && (op & 0x0C000000) == 0x00000000) {
            int kind = (op >> 21) & 0xF;
            int rd = (op >> 12) & 0xF;
            int rn = (op >> 16) & 0xF;
            long imm = op & 0xFFF;
            String[] names = {"and", "eor", "sub", "rsb", "add", "adc", "sbc", "rsc", "tst", "teq", "cmp", "cmn", "orr", "mov", "bic", "mvn"};
            String name = names[kind] + c;
            if (kind >= 8 && kind <= 11) return name + "\t" + r32[rn] + ", #" + imm;
            if (kind == 13 || kind == 15) return name + "\t" + r32[rd] + ", #" + imm;
            return name + "\t" + r32[rd] + ", " + r32[rn] + ", #" + imm;
        }
        // BX
        if ((op & 0x0FFFFFF0) == 0x012FFF10) return "bx" + c + "\t" + r32[op & 0xF];
        if (op == 0xE1A00000) return "nop";
        return ".word\t0x" + String.format("%08x", op);
    }
}

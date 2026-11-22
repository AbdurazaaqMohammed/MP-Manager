package io.github.abdurazaaqmohammed.feature.arm64.decoder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
public class ArmDisasmHelper {

    private static final boolean VERBOSE = false;
    private final Formatter formatter;

    private static final String[] X_REGS_ZR = new String[32];
    private static final String[] X_REGS_SP = new String[32];
    private static final String[] W_REGS_ZR = new String[32];
    private static final String[] W_REGS_SP = new String[32];
    private static final String[] H_REGS = new String[32];
    private static final String[] S_REGS = new String[32];
    private static final String[] D_REGS = new String[32];
    private static final String[] Q_REGS = new String[32];
    private static final String[] B_REGS = new String[32];

    static {
        for (int i = 0; i <= 30; i++) {
            X_REGS_ZR[i] = X_REGS_SP[i] = "X" + i;
            W_REGS_ZR[i] = W_REGS_SP[i] = "W" + i;
            H_REGS[i] = "H" + i;
            S_REGS[i] = "S" + i;
            D_REGS[i] = "D" + i;
            Q_REGS[i] = "Q" + i;
            B_REGS[i] = "B" + i;
        }
        X_REGS_ZR[31] = "XZR"; X_REGS_SP[31] = "SP";
        W_REGS_ZR[31] = "WZR"; W_REGS_SP[31] = "WSP";
        H_REGS[31] = "H31"; S_REGS[31] = "S31"; D_REGS[31] = "D31";
        Q_REGS[31] = "Q31"; B_REGS[31] = "B31";
    }

    public ArmDisasmHelper(Formatter formatter) {
        this.formatter = formatter;
    }

    public String formatPreIndexMemAccess(char registerType, int register, int offset) {
        String reg = getRegisterName(registerType, register, true, -1);
        return formatter.formatMemAccess(Formatter.MemAccessType.PreIndex, reg, offset);
    }

    public String formatPostIndexMemAccess(char registerType, int register, int offset) {
        String reg = getRegisterName(registerType, register, true, -1);
        return formatter.formatMemAccess(Formatter.MemAccessType.PostIndex, reg, offset);
    }

    public String formatMemAccessWithOffset(char registerType, int register, int offset) {
        String reg = getRegisterName(registerType, register, true, -1);
        return formatter.formatMemAccess(Formatter.MemAccessType.OnlyOffset, reg, offset);
    }

    public String formatMemAccessWithTwoRegisters(char registerType, int baseRegister, int extraRegister, int option, int scale, boolean showDefaultLsl, int shift, boolean is8Bit) {
        String baseReg = getRegisterName(registerType, baseRegister, true, -1);
        String extraReg = getRegisterName(((option & 1) == 1) ? 'X' : 'W', extraRegister, false, -1);
        if (option == 3 && scale == 0 && shift == 0) {
            return "[" + baseReg + ", " + extraReg + (showDefaultLsl ? ", lsl #0" : "") + "]";
        }
        if (!(registerType == 'W' || registerType == 'X')) {
            throw new RuntimeException();
        }
        String suffix;
        if (option == 2) {
            suffix = "uxtw";
        } else if (option == 3 && !is8Bit) {
            suffix = "lsl";
        } else if (option == 6) {
            suffix = "sxtw";
        } else if (option == 7) {
            suffix = "sxtx";
        } else {
            throw new UndefinedInstructionException("option=" + option + " scale=" + scale + " is8Bit=" + is8Bit + " shift=" + shift);
        }
        if (scale > 0 || shift > 0) {
            suffix += " #" + scale;
        }
        return "[" + baseReg + ", " + extraReg + ", " + suffix + "]";
    }

    public String formatMemAccessWithTwoRegistersLsl(char registerType, int baseRegister, int extraRegister, int scale, int shift) {
        String baseReg = getRegisterName(registerType, baseRegister, true, -1);
        String extraReg = getRegisterName('X', extraRegister, false, -1);
        if (!(registerType == 'W' || registerType == 'X')) {
            throw new RuntimeException();
        }
        if (scale == 0 && shift == 0) {
            return "[" + baseReg + ", " + extraReg + "]";
        }
        return "[" + baseReg + ", " + extraReg + ", LSL #" + scale + "]";
    }

    public String decodeBaseRegister(int imm5, int register) {
        return getRegisterName((imm5 & 0b1111) == 8 ? 'X' : 'W', register, false, -1);
    }

    public String decodeBaseRegister2(int imm5, int register) {
        int trailingZeros = Integer.numberOfTrailingZeros(imm5);
        char regType;
        if (trailingZeros <= 2) {
            regType = 'W';
        } else if (trailingZeros == 3) {
            regType = 'X';
        } else {
            throw new UndefinedInstructionException("imm5=" + imm5);
        }
        return getRegisterName(regType, register, false, -1);
    }

    public String getRegisterName(char registerType, int register, boolean withSp, int optionalReg) {
        if (register >= 0 && register <= 30) {
            if (register == optionalReg) {
                return null;
            }
            switch (registerType) {
                case 'X': return X_REGS_ZR[register];
                case 'W': return W_REGS_ZR[register];
                case 'H': return H_REGS[register];
                case 'S': return S_REGS[register];
                case 'D': return D_REGS[register];
                case 'Q': return Q_REGS[register];
                case 'B': return B_REGS[register];
            }
        }
        if (register == 31) {
            switch (registerType) {
                case 'X': return withSp ? X_REGS_SP[31] : X_REGS_ZR[31];
                case 'W': return withSp ? W_REGS_SP[31] : W_REGS_ZR[31];
                case 'H': return H_REGS[31];
                case 'S': return S_REGS[31];
                case 'D': return D_REGS[31];
                case 'Q': return Q_REGS[31];
                case 'B': return B_REGS[31];
            }
        }
        throw new UndefinedInstructionException("Unknown register " + registerType + " and " + register + " with-sp=" + withSp + " optionalReg=" + optionalReg);
    }

    public String formatFpuRegister(String prefix, int register) {
        if (register >= 0 && register < 32) {
            switch (prefix.charAt(0)) {
                case 'H': return H_REGS[register];
                case 'S': return S_REGS[register];
                case 'D': return D_REGS[register];
                case 'Q': return Q_REGS[register];
                case 'B': return B_REGS[register];
            }
            return prefix + register;
        }
        throw new RuntimeException();
    }

    public String decodeFpuRegister_sz(int register, int sz, String prefixForZero, String prefixForOne) {
        String prefix;
        if (sz == 0 && prefixForZero != null) {
            prefix = prefixForZero;
        } else if (sz == 1 && prefixForOne != null) {
            prefix = prefixForOne;
        } else {
            throw new UndefinedInstructionException("sz=" + sz);
        }
        return formatFpuRegister(prefix, register);
    }

    public String decodeFpuRegister_size(int size, int register, String prefixFor0, String prefixFor1, String prefixFor2, String prefixFor3) {
        String prefix;
        if (size == 0 && prefixFor0 != null) {
            prefix = prefixFor0;
        } else if (size == 1 && prefixFor1 != null) {
            prefix = prefixFor1;
        } else if (size == 2 && prefixFor2 != null) {
            prefix = prefixFor2;
        } else if (size == 3 && prefixFor3 != null) {
            prefix = prefixFor3;
        } else {
            throw new UndefinedInstructionException("size=" + size + " register=" + register);
        }
        return formatFpuRegister(prefix, register);
    }

    public String decodeFpuRegister_leadingZeros(int immh, int register, String prefixFor3LeadingZeros, String prefixFor2LeadingZeros, String prefixFor1LeadingZeros, String prefixFor0LeadingZeros) {
        String prefix;
        if (immh == 1 && prefixFor3LeadingZeros != null) {
            prefix = prefixFor3LeadingZeros;
        } else if (immh >= 2 && immh < 4 && prefixFor2LeadingZeros != null) {
            prefix = prefixFor2LeadingZeros;
        } else if (immh >= 4 && immh < 8 && prefixFor1LeadingZeros != null) {
            prefix = prefixFor1LeadingZeros;
        } else if (immh >= 8 && prefixFor0LeadingZeros != null) {
            prefix = prefixFor0LeadingZeros;
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " register=" + register);
        }
        return formatFpuRegister(prefix, register);
    }

    public String decodeFpuRegister_custom1(int imm5, int register) {
        int trailingZeros = Integer.numberOfTrailingZeros(imm5);
        String prefix;
        if (trailingZeros == 3) {
            prefix = "D";
        } else if (trailingZeros == 2) {
            prefix = "S";
        } else if (trailingZeros == 1) {
            prefix = "H";
        } else if (trailingZeros == 0) {
            prefix = "B";
        } else {
            throw new UndefinedInstructionException("immh=" + imm5 + " register=" + register);
        }
        return formatFpuRegister(prefix, register);
    }

    public String decodeVectorReg_sizeQ(int size, int q, int register, String control) {
        if (control.length() != 8) {
            throw new RuntimeException();
        }
        if (VERBOSE && !control.replace("_", "").replace("r", "").isEmpty()) {
            throw new RuntimeException();
        }
        return decodeVectorReg_sizeQ(size, q, register,
                control.charAt(0) == '_',
                control.charAt(1) == '_',
                control.charAt(2) == '_',
                control.charAt(3) == '_',
                control.charAt(4) == '_',
                control.charAt(5) == '_',
                control.charAt(6) == '_',
                control.charAt(7) == '_');
    }

    private String decodeVectorReg_sizeQ(int size, int q, int register,
                                         boolean enable8B, boolean enable16B,
                                         boolean enable4H, boolean enable8H,
                                         boolean enable2S, boolean enable4S,
                                         boolean enable1D, boolean enable2D) {
        String suffix;
        if (size == 0 && q == 0 && enable8B) {
            suffix = "8B";
        } else if (size == 0 && q == 1 && enable16B) {
            suffix = "16B";
        } else if (size == 1 && q == 0 && enable4H) {
            suffix = "4H";
        } else if (size == 1 && q == 1 && enable8H) {
            suffix = "8H";
        } else if (size == 2 && q == 0 && enable2S) {
            suffix = "2S";
        } else if (size == 2 && q == 1 && enable4S) {
            suffix = "4S";
        } else if (size == 3 && q == 0 && enable1D) {
            suffix = "1D";
        } else if (size == 3 && q == 1 && enable2D) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("size=" + size + " q=" + q);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_sizeQ_custom1(int register, int q, int size) {
        String suffix;
        if (size == 0 && q == 0) {
            suffix = "4H";
        } else if (size == 0 && q == 1) {
            suffix = "8H";
        } else if (size == 1 && q == 0) {
            suffix = "2S";
        } else if (size == 1 && q == 1) {
            suffix = "4S";
        } else if (size == 2 && q == 0) {
            suffix = "1D";
        } else if (size == 2 && q == 1) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("size=" + size + " q=" + q);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg2(int size, int register) {
        String suffix;
        if (size == 3) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("size=" + size);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorRegBWithQ(int q, int register) {
        String suffix;
        if (q == 0) {
            suffix = "8B";
        } else {
            suffix = "16B";
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorRegHWithQ(int q, int register) {
        String suffix;
        if (q == 0) {
            suffix = "4H";
        } else {
            suffix = "8H";
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorRegSWithQ(int q, int register) {
        String suffix;
        if (q == 0) {
            suffix = "2S";
        } else {
            suffix = "4S";
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg5(int q, int imm5, int register) {
        String suffix;
        int trailingZeros = Integer.numberOfTrailingZeros(imm5);
        if (trailingZeros == 0 && q == 0) {
            suffix = "8B";
        } else if (trailingZeros == 0 && q == 1) {
            suffix = "16B";
        } else if (trailingZeros == 1 && q == 0) {
            suffix = "4H";
        } else if (trailingZeros == 1 && q == 1) {
            suffix = "8H";
        } else if (trailingZeros == 2 && q == 0) {
            suffix = "2S";
        } else if (trailingZeros == 2 && q == 1) {
            suffix = "4S";
        } else if (trailingZeros == 3 && q == 1) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("q=" + q + " imm5=" + imm5);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg6(int sz, int q, int register) {
        String suffix;
        if (sz == 0 && q == 0) {
            suffix = "2S";
        } else if (sz == 0 && q == 1) {
            suffix = "4S";
        } else if (sz == 1 && q == 1) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("sz=" + sz + " q=" + q);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg9(int immh, int q, int register) {
        String suffix;
        if (immh < 4 && immh >= 2 && q == 0) {
            suffix = "4H";
        } else if (immh < 4 && immh >= 2 && q == 1) {
            suffix = "8H";
        } else if (immh < 8 && immh >= 4 && q == 0) {
            suffix = "2S";
        } else if (immh < 8 && immh >= 4 && q == 1) {
            suffix = "4S";
        } else if (immh >= 8 && q == 1) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " q=" + q + " register=" + register);
        }

        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg10(int immh, int register) {
        String suffix;
        if (immh == 1) {
            suffix = "8H";
        } else if (immh >= 2 && immh < 4) {
            suffix = "4S";
        } else if (immh >= 4 && immh < 8) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " register=" + register);
        }

        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg11(int immh, int q, int register) {
        String suffix;
        if (immh == 1 && q == 0) {
            suffix = "8B";
        } else if (immh == 1 && q == 1) {
            suffix = "16B";
        } else if (immh >= 2 && immh < 4 && q == 0) {
            suffix = "4H";
        } else if (immh >= 2 && immh < 4 && q == 1) {
            suffix = "8H";
        } else if (immh >= 4 && immh < 8 && q == 0) {
            suffix = "2S";
        } else if (immh >= 4 && immh < 8 && q == 1) {
            suffix = "4S";
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " q=" + q + " register=" + register);
        }

        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg12(int immh, int q, int register) {
        String suffix;
        if (immh == 1 && q == 0) {
            suffix = "8B";
        } else if (immh == 1 && q == 1) {
            suffix = "16B";
        } else if (immh >= 2 && immh < 4 && q == 0) {
            suffix = "4H";
        } else if (immh >= 2 && immh < 4 && q == 1) {
            suffix = "8H";
        } else if (immh >= 4 && immh < 8 && q == 0) {
            suffix = "2S";
        } else if (immh >= 4 && immh < 8 && q == 1) {
            suffix = "4S";
        } else if (immh >= 8 && q == 1) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " q=" + q + " register=" + register);
        }

        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_szQ1(int register, int sz, int q) {
        String suffix;
        if (sz == 0 && q == 0) {
            suffix = "4H";
        } else if (sz == 0 && q == 1) {
            suffix = "8H";
        } else if (sz == 1 && q == 0) {
            suffix = "2S";
        } else if (sz == 1 && q == 1) {
            suffix = "4S";
        } else {
            throw new UndefinedInstructionException("sz=" + sz + " q=" + q);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_szQ2(int sz, int q, int register) {
        String suffix;
        if (sz == 0 && q == 0) {
            suffix = "2S";
        } else if (sz == 0 && q == 1) {
            suffix = "4S";
        } else {
            throw new UndefinedInstructionException("sz=" + sz + " q=" + q);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_szQ3(int register, int sz, int q) {
        String suffix;
        if (sz == 0 && q == 1) {
            suffix = "4S";
        } else {
            throw new UndefinedInstructionException("sz=" + sz + " q=" + q);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_szQ4(int sz, int q, int register) {
        String suffix;
        if (sz == 1 && q == 0) {
            suffix = "2S";
        } else if (sz == 1 && q == 1) {
            suffix = "4S";
        } else {
            throw new UndefinedInstructionException("sz=" + sz + " q=" + q);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_sz(int register, int sz, String zero, String one) {
        String suffix;
        if (sz == 0 && zero != null) {
            suffix = zero;
        } else if (sz == 1 && one != null) {
            suffix = one;
        } else {
            throw new UndefinedInstructionException("sz=" + sz);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorRegWithIndex1(int register, int imm5, boolean enableB, boolean enableH, boolean enableS, boolean enableD) {
        int trailingZeros = Integer.numberOfTrailingZeros(imm5);
        String prefix;
        if (trailingZeros == 0 && enableB) {
            prefix = "B";
        } else if (trailingZeros == 1 && enableH) {
            prefix = "H";
        } else if (trailingZeros == 2 && enableS) {
            prefix = "S";
        } else if (trailingZeros == 3 && enableD) {
            prefix = "D";
        } else {
            throw new UndefinedInstructionException("imm5=" + imm5 + " register=" + register);
        }
        int index = (imm5 >> (trailingZeros + 1)) & intMask(4 - trailingZeros);
        String suffix = prefix + "[" + index + "]";
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorRegWithIndex2(int register, int sz, int h, int l, int m) {
        String type;
        int index;
        if (sz == 1) {
            type = "H";
            index = h * 2 + l;
        } else if (sz == 2) {
            type = "S";
            index = h;
        } else {
            throw new UndefinedInstructionException("sz=" + sz);
        }
        String suffix = type + "[" + index + "]";
        return formatVectorRegister(m * 16 + register, suffix);
    }

    public String decodeVectorRegWithIndex_szHml1(int register, int sz, int h, int l, int m) {
        String type;
        if (sz == 0) {
            type = "S";
        } else if (sz == 1) {
            type = "D";
        } else {
            throw new UndefinedInstructionException("sz=" + sz + " l=" + l + " m=" + m + " h=" + h);
        }
        int index;
        if (sz == 0) {
            index = 2 * h + l;
        } else if (sz == 1 && l == 0) {
            index = h;
        } else {
            throw new UndefinedInstructionException("sz=" + sz + " l=" + l + " m=" + m + " h=" + h);
        }
        String suffix = type + "[" + index + "]";
        return formatVectorRegister(m * 16 + register, suffix);
    }

    public String decodeVectorRegWithIndex6(int register, int size, int h, int l, int m) {
        String type;
        int index;
        int realRegister;

        if (size == 1) {
            type = "H";
            index = 4 * h + 2 * l + m;
            realRegister = register;
        } else if (size == 2) {
            type = "S";
            index = 2 * h + l;
            realRegister = m * 16 + register;
        } else {
            throw new UndefinedInstructionException("sz=" + size);
        }
        String suffix = type + "[" + index + "]";
        return formatVectorRegister(realRegister, suffix);
    }

    public String decodeVectorRegWithIndex7(int register, int size, int h, int l, int m) {
        String type;
        int index;
        int rmHigh;
        if (size == 1) {
            type = "H";
            index = 4 * h + 2 * l + m;
            rmHigh = 0;
        } else if (size == 2) {
            type = "S";
            index = 2 * h + l;
            rmHigh = m;
        } else {
            throw new UndefinedInstructionException("sz=" + size);
        }
        String suffix = type + "[" + index + "]";
        return formatVectorRegister(rmHigh * 16 + register, suffix);
    }

    public String decodeVectorRegWithIndex10(int register, int imm5, int imm4) {
        int trailingZeros = Integer.numberOfTrailingZeros(imm5);
        String prefix;
        int index;
        if (trailingZeros == 3) {
            prefix = "D";
            index = (imm4 >> 3) & 0b1;
        } else if (trailingZeros == 2) {
            prefix = "S";
            index = (imm4 >> 2) & 0b11;
        } else if (trailingZeros == 1) {
            prefix = "H";
            index = (imm4 >> 1) & 0b111;
        } else if (trailingZeros == 0) {
            prefix = "B";
            index = (imm4) & 0b1111;
        } else {
            throw new UndefinedInstructionException("imm5=" + imm5 + " register=" + register);
        }
        String suffix = prefix + "[" + index + "]";
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorRegWithIndex3(int register, int h, int l, int m) {
        String type = "H";
        int index = h * 4 + 2 * l + m;
        String suffix = type + "[" + index + "]";
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorRegWithIndex11(int register, int m, int h, int l) {
        String type = "4B";
        int index = 2 * h + l;
        String suffix = type + "[" + index + "]";
        return formatVectorRegister(16 * m + register, suffix);
    }

    public String decodeVectorRegWithIndex12(int register, int imm2) {
        String type = "S";
        String suffix = type + "[" + imm2 + "]";
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_size1(int size, int register) {
        String suffix;
        if (size == 0) {
            suffix = "8H";
        } else if (size == 1) {
            suffix = "4S";
        } else if (size == 2) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("size=" + size);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_size2(int size, int register) {
        String suffix;
        if (size == 0) {
            suffix = "8H";
        } else if (size == 3) {
            suffix = "1Q";
        } else {
            throw new UndefinedInstructionException("size=" + size);
        }
        return formatVectorRegister(register, suffix);
    }

    public String decodeVectorReg_size3(int size, int register) {
        String suffix;
        if (size == 1) {
            suffix = "4S";
        } else if (size == 2) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("size=" + size);
        }
        return formatVectorRegister(register, suffix);
    }

    public String formatVectorRegister(int register, String suffix) {
        if (register >= 0 && register <= 31) {
            return "v" + register + "." + suffix;
        }
        throw new RuntimeException();
    }

    public String decodeShiftedRegister(char registerType, int register, int shiftType, int amount, boolean rorEnabled) {
        String reg = getRegisterName(registerType, register, false, -1);
        String suffix;
        if (shiftType == 0 && amount == 0) {
            suffix = "";
        } else if (shiftType == 0) {
            suffix = ", lsl #" + amount;
        } else if (shiftType == 1) {
            suffix = ", lsr #" + amount;
        } else if (shiftType == 2) {
            suffix = ", asr #" + amount;
        } else if (shiftType == 3 && rorEnabled) {
            suffix = ", ror #" + amount;
        } else {
            throw new UndefinedInstructionException("type=" + shiftType + " amount=" + amount);
        }
        if (registerType == 'W' && amount >= 32) {
            throw new UndefinedInstructionException("register=" + registerType + " type=" + shiftType + " amount=" + amount);
        }
        return reg + suffix;
    }

    public String decodeRegisterWithExtend(char registerType, int register, int option, int shift, boolean preferLsl, boolean is64Bit) {
        String reg = getRegisterName(registerType, register, false, -1);
        boolean isLsl = (is64Bit && option == 3) || (!is64Bit && option == 2);
        if (preferLsl && isLsl && shift == 0) {
            return reg;
        }
        return reg + ", " + decodeExtendWithShift(registerType, option, shift, false, preferLsl, is64Bit);
    }

    private String decodeExtendWithShift(char registerType, int option, int shift, boolean includeZeroShift, boolean preferLsl, boolean is64Bit) {
        if (!(registerType == 'W' || registerType == 'X')) {
            throw new RuntimeException();
        }
        if (shift > 4) {
            throw new UndefinedInstructionException("option=" + option + " scale=" + shift);
        }
        String suffix;
        switch (option) {
            case 0:
                suffix = "uxtb";
                break;
            case 1:
                suffix = "uxth";
                break;
            case 2:
                suffix = preferLsl && !is64Bit ? "lsl" : "uxtw";
                break;
            case 3:
                suffix = preferLsl && is64Bit ? "lsl" : "uxtx";
                break;
            case 4:
                suffix = "sxtb";
                break;
            case 5:
                suffix = "sxth";
                break;
            case 6:
                suffix = "sxtw";
                break;
            case 7:
                suffix = "sxtx";
                break;
            default:
                throw new UndefinedInstructionException("option=" + option + " scale=" + shift);
        }
        if (shift > 0 || includeZeroShift) {
            suffix += " #" + shift;
        }
        return suffix;
    }


    public String decodeLd1(int q, int size, int startRegister, int numRegs, boolean enable1D) {
        String suffix;
        if (size == 0 && q == 0) {
            suffix = "8B";
        } else if (size == 0 && q == 1) {
            suffix = "16B";
        } else if (size == 1 && q == 0) {
            suffix = "4H";
        } else if (size == 1 && q == 1) {
            suffix = "8H";
        } else if (size == 2 && q == 0) {
            suffix = "2S";
        } else if (size == 2 && q == 1) {
            suffix = "4S";
        } else if (size == 3 && q == 0 && enable1D) {
            suffix = "1D";
        } else if (size == 3 && q == 1) {
            suffix = "2D";
        } else {
            throw new UndefinedInstructionException("size=" + size + " q=" + q);
        }
        return formatRegisterSet(startRegister, numRegs, suffix);
    }

    public String decodeTbx(int startRegister, int numRegs) {
        return formatRegisterSet(startRegister, numRegs, "16B");
    }

    private String formatRegisterSet(int startRegister, int numRegs, String suffix) {
        if (numRegs == 1) {
            return "{v" + startRegister + "." + suffix + "}";
        }
        if (numRegs == 2) {
            return "{v" + startRegister + "." + suffix + ",v" + ((startRegister + 1) % 32) + "." + suffix + "}";
        }
        int last = (startRegister + numRegs - 1) % 32;
        if (startRegister < last && formatter.formatVectorRegisterListAsRange()) {
            if (numRegs == 3) {
                return "{v" + startRegister + "." + suffix + "-v" + ((startRegister + 2) % 32) + "." + suffix + "}";
            }
            if (numRegs == 4) {
                return "{v" + startRegister + "." + suffix + "-v" + ((startRegister + 3) % 32) + "." + suffix + "}";
            }
        } else {
            if (numRegs == 3) {
                return "{v" + startRegister + "." + suffix + ",v" + ((startRegister + 1) % 32) + "." + suffix + ",v" + ((startRegister + 2) % 32) + "." + suffix + "}";
            }
            if (numRegs == 4) {
                return "{v" + startRegister + "." + suffix + ",v" + ((startRegister + 1) % 32) + "." + suffix + ",v" + ((startRegister + 2) % 32) + "." + suffix + ",v" + ((startRegister + 3) % 32) + "." + suffix + "}";
            }
        }
        throw new UndefinedInstructionException("numRegs=" + numRegs);
    }

    public int decodeLd1Offset(int q, int numRegs) {
        int result = numRegs * 8;
        if (q == 1) {
            result *= 2;
        }
        return result;
    }

    public int decodeLdraaOffset(int imm9, int s) {
        if (s == 0) {
            return 8 * imm9;
        } else if (s == 1) {
            return 8 * (imm9 - 512);
        } else {
            throw new UndefinedInstructionException("imm9=" + imm9 + " s=" + s);
        }
    }

    private int decodeLdXIndex(String registerType, int register, int q, int s, int size) {
        int index;
        if ("B".equals(registerType)) {
            index = 8 * q + 4 * s + size;
        } else if ("H".equals(registerType)) {
            index = 4 * q + 2 * s + size / 2;
        } else if ("S".equals(registerType)) {
            index = 2 * q + s;
        } else if ("D".equals(registerType)) {
            index = q;
        } else {
            throw new UndefinedInstructionException("registerType = [" + registerType + "], register = [" + register + "], q = [" + q + "], s = [" + s + "], size = [" + size + "]");
        }
        return index;
    }

    public String decodeLd1Index(String registerType, int register, int q, int s, int size) {
        int index = decodeLdXIndex(registerType, register, q, s, size);
        return formatRegisterSet(register, 1, registerType) + "[" + index + "]";
    }

    public String decodeLd2Index(String registerType, int register, int q, int s, int size) {
        int index = decodeLdXIndex(registerType, register, q, s, size);
        return formatRegisterSet(register, 2, registerType) + "[" + index + "]";
    }

    public String decodeLd3Index(String registerType, int register, int q, int s, int size) {
        int index = decodeLdXIndex(registerType, register, q, s, size);
        return formatRegisterSet(register, 3, registerType) + "[" + index + "]";
    }

    public String decodeLd4Index(String registerType, int register, int q, int s, int size) {
        int index = decodeLdXIndex(registerType, register, q, s, size);
        return formatRegisterSet(register, 4, registerType) + "[" + index + "]";
    }


    public int getPart(int value, int offsetFromRight, int numBits) {
        int mask = (1 << numBits) - 1;
        return (value >> offsetFromRight) & mask;
    }

    public int asSigned(int value, int numBits) {
        if (value < 0) {
            throw new RuntimeException("Value must be >= 0 (is " + value + ")");
        }
        int full = 1 << numBits;
        int half = full / 2;
        int result = value;
        if (result >= half) {
            result -= full;
        }
        return result;
    }

    public String decodeShiftedImm16(char registerType, int value, int shiftAmount, boolean negate) {
        if (registerType == 'W' && shiftAmount > 1) {
            throw new UndefinedInstructionException("registerType=" + registerType + " shiftAmount=" + shiftAmount);
        }
        long valueAsLong = value;
        long v = valueAsLong << (16 * shiftAmount);
        if (negate) {
            v = ~v;
            if (registerType == 'W') {
                v &= 0xffffffffL;
            }
        }
        return "#0x" + Long.toHexString(v);
    }

    public String decodeMovShiftedImm16(int imm16, int hw, int registerSize) {
        if (registerSize == 32 && hw > 1) {
            throw new UndefinedInstructionException("hw=" + hw + " registerSize=" + registerSize);
        }
        return formatter.formatImmWithLsl(imm16, 16 * hw);
    }

    public String decodeMslAmount(int cmode) {
        int value;
        if (cmode == 12) {
            value = 8;
        } else if (cmode == 13) {
            value = 16;
        } else {
            throw new UndefinedInstructionException("cmode=" + cmode);
        }
        return "MSL #" + value;
    }

    public String decodeShiftedImm12(int value, int shift) {
        if (shift > 1) {
            throw new UndefinedInstructionException("value=" + value + " shift=" + shift);
        }
        return formatter.formatImmWithLsl(value, shift * 12);
    }

    public String decodeAdrLabel(long pc, int offset, boolean pageAlign) {
        long v;
        if (pageAlign) {
            v = (pc >> 12) << 12;
            long shifted = offset;
            shifted = shifted << 12;
            v += shifted;
        } else {
            v = pc + offset;
        }
        return formatter.formatLabel(v);
    }

    public String decodePcRelativeLabel(long pc, int offset) {
        long v = pc + offset;
        return formatter.formatLabel(v);
    }

    public String decodeShift(int immr, int imms, int regSize) {
        if (regSize == 32 && (immr >= 32 || imms >= 32)) {
            throw new UndefinedInstructionException("immr=" + immr + " imms=" + imms + " regSize=" + regSize);
        }
        int value;
        if (imms == regSize - 1) {
            value = immr;
        } else if (imms == immr - 1) {
            value = regSize - immr;
        } else {
            throw new UndefinedInstructionException("immr=" + immr + " imms=" + imms + " regSize=" + regSize);
        }
        return "#" + value;
    }

    public String decodeShift_immMinusConst(int immh, int immb, boolean active3LeadingZeros, boolean active2LeadingZeros, boolean active1LeadingZeros, boolean active0LeadingZeros) {
        int value;
        int combined = immh * 8 + immb;

        if (immh == 1 && active3LeadingZeros) {
            value = combined - 8;
        } else if (immh >= 2 && immh < 4 && active2LeadingZeros) {
            value = combined - 16;
        } else if (immh >= 4 && immh < 8 && active1LeadingZeros) {
            value = combined - 32;
        } else if (immh >= 8 && immh < 16 && active0LeadingZeros) {
            value = combined - 64;
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " immb=" + immb);
        }
        return "#" + value;
    }

    public String decodeShift_constMinusImm(int immh, int immb, boolean active3LeadingZeros, boolean active2LeadingZeros, boolean active1LeadingZeros, boolean active0LeadingZeros) {
        int value;
        int combined = immh * 8 + immb;

        if (immh == 1 && active3LeadingZeros) {
            value = 16 - combined;
        } else if (immh >= 2 && immh < 4 && active2LeadingZeros) {
            value = 32 - combined;
        } else if (immh >= 4 && immh < 8 && active1LeadingZeros) {
            value = 64 - combined;
        } else if (immh >= 8 && immh < 16 && active0LeadingZeros) {
            value = 128 - combined;
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " immb=" + immb);
        }
        return "#" + value;
    }

    public String decodeShift2(int size) {
        int value;

        if (size == 0 || size == 1 || size == 2) {
            value = 8 << size;
        } else {
            throw new UndefinedInstructionException("size=" + size);
        }
        return "#" + value;
    }

    public String decodeAluImm(int n, int immr, int imms, int regSize) {
        return "#0x" + Long.toHexString(decodeAluImm0(n, immr, imms, regSize));
    }

    public long decodeAluImm0(int n, int immr, int imms, int regSize) {
        int len = Integer.highestOneBit((n << 6) | (~imms) & 0b111111);
        if (len < 1) {
            throw new UndefinedInstructionException("len=" + len + " n=" + n + " immr=" + immr + " imms=" + imms);
        }
        int r = immr & (len - 1);
        int s = imms & (len - 1);
        if (s == len - 1) {
            throw new UndefinedInstructionException("len=" + len + " n=" + n + " immr=" + immr + " imms=" + imms);
        }
        long welem = mask(s + 1) & mask(len);
        long rotated = ror(welem, r, len);
        long value = replicatePattern(rotated, len);
        return mask(regSize) & value;
    }

    private long mask(int len) {
        if (len == 64) {
            return 0xffffffffffffffffL;
        }
        return (1L << len) - 1;
    }

    private int intMask(int len) {
        if (len <= 0 || len >= 32) {
            throw new RuntimeException("len=" + len);
        }
        return (1 << len) - 1;
    }

    private long replicatePattern(long pattern, int patternLength) {
        long result = pattern & mask(patternLength);
        if (patternLength <= 2) {
            result = result | (result << 2);
        }
        if (patternLength <= 4) {
            result = result | (result << 4);
        }
        if (patternLength <= 8) {
            result = result | (result << 8);
        }
        if (patternLength <= 16) {
            result = result | (result << 16);
        }
        if (patternLength <= 32) {
            result = result | (result << 32);
        }
        return result;
    }

    private long ror(long n, int amount, int size) {
        return (n >> amount) | (n << (size - amount));
    }

    public String decodeLsb(int immr, int imms, int regSize, boolean BFXIL) {
        int value;
        if (BFXIL) {
            value = immr;
        } else if (regSize == 32) {
            value = 32 - immr;
        } else if (regSize == 64) {
            value = 64 - immr;
        } else {
            throw new UndefinedInstructionException("immr=" + immr + " imms=" + imms + " regSize=" + regSize);
        }
        return "#" + value;
    }

    public String decodeLsbRor(int imms, int regSize) {
        //noinspection UnnecessaryLocalVariable
        int value = imms;
        if (value >= regSize) {
            throw new RuntimeException();
        }
        return "#" + value;
    }

    public String decodeWidth(int immr, int imms, int regSize, boolean BFXIL) {
        int value;
        if (regSize != 32 && regSize != 64) {
            throw new UndefinedInstructionException("immr=" + immr + " imms=" + imms + " regSize=" + regSize);
        }
        if (regSize == 32 && (immr >= 32 || imms >= 32)) {
            throw new UndefinedInstructionException("immr=" + immr + " imms=" + imms + " regSize=" + regSize);
        }
        value = imms + 1;
        if (BFXIL) {
            value -= immr;
        }
        return "#" + value;
    }

    public String formatDecimalImm(int imm) {
        return "#" + imm;
    }

    public String formatHexImm(int imm) {
        return "#0x" + Integer.toHexString(imm);
    }

    public String decodeBranchCondition(int cc) {
        switch (cc) {
            case 0:
                return "eq";
            case 1:
                return "ne";
            case 2:
                return "cs";
            case 3:
                return "cc";
            case 4:
                return "mi";
            case 5:
                return "pl";
            case 6:
                return "vs";
            case 7:
                return "vc";

            case 8:
                return "hi";
            case 9:
                return "ls";
            case 10:
                return "ge";
            case 11:
                return "lt";
            case 12:
                return "gt";
            case 13:
                return "le";
            case 14:
                return "al";
            case 15:
                return "nv";
        }
        throw new RuntimeException();
    }

    public String decodeImm8WithLsl(int a, int b, int c, int d, int e, int f, int g, int h, int op, int cmode) {
        int imm8 = a << 7 | b << 6 | c << 5 | d << 4 | e << 3 | f << 2 | g << 1 | h;
        if (cmode == 0 || cmode == 1 || cmode == 8 || cmode == 9) {
            return formatHexImm(imm8);
        } else if (cmode == 2 || cmode == 3 || cmode == 10 || cmode == 11) {
            return formatter.formatImmWithLsl(imm8, 8);
        } else if (cmode == 4 || cmode == 5) {
            return formatter.formatImmWithLsl(imm8, 16);
        } else if (cmode == 6 || cmode == 7) {
            return formatter.formatImmWithLsl(imm8, 24);
        } else {
            throw new UndefinedInstructionException("imm8=" + imm8 + "=0x" + Integer.toHexString(imm8) + " cmode=" + cmode + " ");
        }
    }

    public String decodeMovi8Constant(int a, int b, int c, int d, int e, int f, int g, int h) {
        int imm8 = a << 7 | b << 6 | c << 5 | d << 4 | e << 3 | f << 2 | g << 1 | h;
        return formatHexImm(imm8);
    }

    public String decodeMovi64Constant(int a, int b, int c, int d, int e, int f, int g, int h) {
        long value = 0;
        value = (value << 8) + replicateBit8Times(a);
        value = (value << 8) + replicateBit8Times(b);
        value = (value << 8) + replicateBit8Times(c);
        value = (value << 8) + replicateBit8Times(d);
        value = (value << 8) + replicateBit8Times(e);
        value = (value << 8) + replicateBit8Times(f);
        value = (value << 8) + replicateBit8Times(g);
        value = (value << 8) + replicateBit8Times(h);
        return "#0x" + Long.toHexString(value);
    }

    private long replicateBit8Times(int input) {
        if (input == 0) {
            return 0;
        } else if (input == 1) {
            return 0xff;
        }
        throw new RuntimeException();
    }

    private static int encode(String bits) {
        return Integer.parseInt(bits.replaceAll("[^01]", ""), 2);
    }

    // Packed key: (op1 << 11) | (crn << 7) | (crm << 3) | op2
    private static final Map<Integer, String> SYS_OP_MAP;
    static {
        Map<Integer, String> m = new HashMap<>();
        m.put(encode("000 0111 1000 000"), "AT S1E1R");
        m.put(encode("100 0111 1000 000"), "AT S1E2R");
        m.put(encode("110 0111 1000 000"), "AT S1E3R");
        m.put(encode("000 0111 1000 001"), "AT S1E1W");
        m.put(encode("100 0111 1000 001"), "AT S1E2W");
        m.put(encode("110 0111 1000 001"), "AT S1E3W");
        m.put(encode("000 0111 1000 010"), "AT S1E0R");
        m.put(encode("000 0111 1000 011"), "AT S1E0W");
        m.put(encode("100 0111 1000 100"), "AT S12E1R");
        m.put(encode("100 0111 1000 101"), "AT S12E1W");
        m.put(encode("100 0111 1000 110"), "AT S12E0R");
        m.put(encode("100 0111 1000 111"), "AT S12E0W");
        m.put(encode("000 0111 1001 000"), "AT S1E1RP");
        m.put(encode("000 0111 1001 001"), "AT S1E1WP");
        m.put(encode("011 0111 0100 001"), "DC ZVA");
        m.put(encode("000 0111 0110 001"), "DC IVAC");
        m.put(encode("000 0111 0110 010"), "DC ISW");
        m.put(encode("011 0111 1010 001"), "DC CVAC");
        m.put(encode("000 0111 1010 010"), "DC CSW");
        m.put(encode("011 0111 1011 001"), "DC CVAU");
        m.put(encode("011 0111 1110 001"), "DC CIVAC");
        m.put(encode("000 0111 1110 010"), "DC CISW");
        m.put(encode("011 0111 1100 001"), "DC CVAP");
        m.put(encode("000 0111 0001 000"), "IC IALLUIS");
        m.put(encode("000 0111 0101 000"), "IC IALLU");
        // "IC IVAU,Xn" is register-dependent; handled as a special case in getSysOp.
        SYS_OP_MAP = m;
    }

    public String getSysOp(int op1, int op2, int crm, int crn, int register) {
        if (crn == 8 && getTlbiRegister(op1, crm, op2) != null) {
            return "TLBI dummy";
        }
        // IC IVAU: op1=3, crn=7, crm=5, op2=1 — result includes the register operand.
        if (op1 == 3 && crn == 7 && crm == 5 && op2 == 1) {
            return "IC IVAU," + getRegisterName('X', register, false, -1);
        }
        int key = (op1 << 11) | (crn << 7) | (crm << 3) | op2;
        String result = SYS_OP_MAP.get(key);
        return result != null ? result : "SYS dummy";
    }

    // Packed key: (op0 << 14) | (op1 << 11) | (crn << 7) | (crm << 3) | op2
    private static final Map<Integer, String> SYSTEM_REGISTER_MAP;
    static {
        Map<Integer, String> m = new HashMap<>();
        m.put(encode("11 011 1110 0000 010"), "CNTVCT_EL0");
        m.put(encode("10 000 0000 0110 100"), "DBGBVR6_EL1");
        m.put(encode("10 000 0000 0110 101"), "DBGBCR6_EL1");
        m.put(encode("11 011 0100 0100 000"), "FPCR");
        m.put(encode("11 011 0100 0100 001"), "FPSR");
        m.put(encode("11 000 0000 0111 001"), "ID_AA64MMFR1_EL1");
        m.put(encode("11 000 0000 0000 000"), "MIDR_EL1");
        m.put(encode("11 000 0100 0001 000"), "SP_EL0");
        m.put(encode("11 000 0100 0010 000"), "SPSEL");
        m.put(encode("11 110 0100 0000 000"), "SPSR_EL3");
        m.put(encode("11 100 0100 0011 010"), "SPSR_UND");
        m.put(encode("11 000 0001 0000 000"), "SCTLR_EL1");
        m.put(encode("11 101 0010 0000 010"), "TCR_EL12");
        m.put(encode("11 011 1101 0000 010"), "TPIDR_EL0");
        m.put(encode("11 110 1101 0000 010"), "TPIDR_EL3");
        m.put(encode("11 011 1101 0000 011"), "TPIDRRO_EL0");
        m.put(encode("11 000 0010 0000 000"), "TTBR0_EL1");
        m.put(encode("11 100 0101 0010 011"), "VSESR_EL2");
        m.put(encode("11 000 0100 0010 100"), "UAO");
        m.put(encode("11 101 1100 0000 000"), "VBAR_EL12");
        m.put(encode("11 110 1100 0000 000"), "VBAR_EL3");
        m.put(encode("11 100 0000 0000 101"), "VMPIDR_EL2");
        m.put(encode("11 100 0000 0000 000"), "VPIDR_EL2");
        m.put(encode("11 100 0010 0110 000"), "VSTTBR_EL2");
        m.put(encode("11 100 0010 0001 010"), "VTCR_EL2");
        m.put(encode("11 100 0010 0001 000"), "VTTBR_EL2");
        m.put(encode("11 000 0010 0001 010"), "APIBKEYLO_EL1");
        m.put(encode("10 000 0000 0100 110"), "DBGWVR4_EL1");
        m.put(encode("11 110 0110 0000 000"), "FAR_EL3");
        m.put(encode("11 011 1110 1000 111"), "PMEVCNTR7_EL0");
        m.put(encode("11 100 0110 0000 000"), "FAR_EL2");
        m.put(encode("11 000 0000 0100 001"), "ID_AA64PFR1_EL1");
        m.put(encode("11 110 0001 0010 000"), "ZCR_EL3");
        m.put(encode("11 101 0110 0000 000"), "FAR_EL12");
        m.put(encode("11 000 0000 0101 001"), "ID_AA64DFR1_EL1");
        m.put(encode("11 011 1110 1101 101"), "PMEVTYPER13_EL0");
        m.put(encode("11 011 1110 0011 000"), "CNTV_TVAL_EL0");
        m.put(encode("11 000 0101 0001 000"), "AFSR0_EL1");
        m.put(encode("11 100 0001 0000 000"), "SCTLR_EL2");
        m.put(encode("11 101 0101 0001 001"), "afsr1_el12");
        SYSTEM_REGISTER_MAP = m;
    }

    public String decodeSystemRegister(int op0, int op1, int crn, int crm, int op2) {
        int key = (op0 << 14) | (op1 << 11) | (crn << 7) | (crm << 3) | op2;
        String r = SYSTEM_REGISTER_MAP.get(key);
        if (r == null) {
            return "s" + op0 + "_" + op1 + "_c" + crn + "_c" + crm + "_" + op2;
        }
        return r;
    }

    public String decodeTlbiRegister(int register, int op1, int crm, int op2) {
        String tlbiReg = getTlbiRegister(op1, crm, op2);
        if (tlbiReg == null) {
            throw new UndefinedInstructionException("op1=" + op1 + " crm=" + crm + " op2=" + op2);
        }
        String suffix = register == 31 ? "" : ",X" + register;
        return tlbiReg + suffix;
    }

    // Packed key: (op1 << 7) | (crm << 3) | op2
    private static final Map<Integer, String> TLBI_MAP;
    static {
        Map<Integer, String> m = new HashMap<>();
        m.put(encode("000 0011 000"), "VMALLE1IS");
        m.put(encode("000 0011 001"), "VAE1IS");
        m.put(encode("000 0011 010"), "ASIDE1IS");
        m.put(encode("000 0011 011"), "VAAE1IS");
        m.put(encode("000 0011 101"), "VALE1IS");
        m.put(encode("000 0011 111"), "VAALE1IS");
        m.put(encode("000 0111 000"), "VMALLE1");
        m.put(encode("000 0111 001"), "VAE1");
        m.put(encode("000 0111 010"), "ASIDE1");
        m.put(encode("000 0111 011"), "VAAE1");
        m.put(encode("000 0111 101"), "VALE1");
        m.put(encode("000 0111 111"), "VAALE1");
        m.put(encode("100 0000 001"), "IPAS2E1IS");
        m.put(encode("100 0000 101"), "IPAS2LE1IS");
        m.put(encode("100 0011 000"), "ALLE2IS");
        m.put(encode("100 0011 001"), "VAE2IS");
        m.put(encode("100 0011 100"), "ALLE1IS");
        m.put(encode("100 0011 101"), "VALE2IS");
        m.put(encode("100 0011 110"), "VMALLS12E1IS");
        m.put(encode("100 0100 001"), "IPAS2E1");
        m.put(encode("100 0100 101"), "IPAS2LE1");
        m.put(encode("100 0111 000"), "ALLE2");
        m.put(encode("100 0111 001"), "VAE2");
        m.put(encode("100 0111 100"), "ALLE1");
        m.put(encode("100 0111 101"), "VALE2");
        m.put(encode("100 0111 110"), "VMALLS12E1");
        m.put(encode("110 0011 000"), "ALLE3IS");
        m.put(encode("110 0011 001"), "VAE3IS");
        m.put(encode("110 0011 101"), "VALE3IS");
        m.put(encode("110 0111 000"), "ALLE3");
        m.put(encode("110 0111 001"), "VAE3");
        m.put(encode("110 0111 101"), "VALE3");
        m.put(encode("100 0100 000"), "IPAS2E1OS");
        m.put(encode("100 0100 010"), "RIPAS2E1");
        m.put(encode("100 0100 011"), "RIPAS2E1OS");
        m.put(encode("100 0000 110"), "RIPAS2LE1IS");
        m.put(encode("000 0010 011"), "RVAAE1IS");
        m.put(encode("000 0110 111"), "RVAALE1");
        m.put(encode("110 0101 001"), "RVAE3OS");
        m.put(encode("000 0101 101"), "RVALE1OS");
        m.put(encode("110 0010 101"), "RVALE3IS");
        m.put(encode("000 0001 011"), "VAAE1OS");
        m.put(encode("000 0001 111"), "VAALE1OS");
        m.put(encode("100 0010 101"), "RVALE2IS");
        TLBI_MAP = m;
    }

    private String getTlbiRegister(int op1, int crm, int op2) {
        int key = (op1 << 7) | (crm << 3) | op2;
        return TLBI_MAP.get(key);
    }

    public String decodeDmbOption(int crm) {
        if (crm == 1) {
            return "oshld";
        } else if (crm == 2) {
            return "oshst";
        } else if (crm == 3) {
            return "osh";
        } else if (crm == 5) {
            return "nshld";
        } else if (crm == 6) {
            return "nshst";
        } else if (crm == 7) {
            return "nsh";
        } else if (crm == 9) {
            return "ishld";
        } else if (crm == 10) {
            return "ishst";
        } else if (crm == 11) {
            return "ish";
        } else if (crm == 13) {
            return "ld";
        } else if (crm == 14) {
            return "st";
        } else if (crm == 15) {
            return "sy";
        }
        return "#0x" + (crm < 16 ? "0" : "") + Integer.toHexString(crm);
    }

    public String decodePrefetchOp(int imm5) {
        String type;
        String target;
        String policy;

        int typeInt = (imm5 >> 3) & 0x3;
        if (typeInt == 0) {
            type = "PLD";
        } else if (typeInt == 1) {
            type = "PLI";
        } else if (typeInt == 2) {
            type = "PST";
        } else {
            type = null;
        }

        int targetInt = (imm5 >> 1) & 0x3;
        if (targetInt == 0) {
            target = "L1";
        } else if (targetInt == 1) {
            target = "L2";
        } else if (targetInt == 2) {
            target = "L3";
        } else {
            target = null;
        }
        policy = ((imm5 & 1) == 0) ? "KEEP" : "STRM";
        if (type != null && target != null) {
            return type + target + policy;
        }
        return "#0x" + (imm5 < 16 ? "0" : "") + Integer.toHexString(imm5);
    }

    public String decodeIsbOption(int crm) {
        if (crm == 15) {
            return null;
        }
        return formatHexImm(crm);
    }

    public String decodeIndex(int q, int imm4) {
        if (q == 1) {
            return "#" + imm4;
        }
        if ((imm4 & 0b1000) == 0) {
            return "#" + imm4;
        }
        throw new UndefinedInstructionException("q=" + q + " imm4=" + imm4);
    }

    public String decodeFbitsFromScale(int scale) {
        return "#" + (64 - scale);
    }

    public String decodeFbits2(int immh, int immb) {
        int size;
        if (immh >= 8) {
            size = 128;
        } else if (immh >= 4) {
            size = 64;
        } else if (immh >= 2) {
            size = 32;
        } else {
            throw new UndefinedInstructionException("immh=" + immh + " immb=" + immb);
        }

        int value = size - ((immh << 3) + immb);
        return "#" + value;
    }

    public String decodeFmovConstant(int imm8) {
        return decodeFmovConstant(isBitSet(imm8, 7), isBitSet(imm8, 6), isBitSet(imm8, 5), isBitSet(imm8, 4), isBitSet(imm8, 3), isBitSet(imm8, 2), isBitSet(imm8, 1), isBitSet(imm8, 0));
    }

    public String decodeFmovConstant(int a, int b, int c, int d, int e, int f, int g, int h) {
        int exp = ((1 - b) * 4 + c * 2 + d) - 3;
        double mantissa = (16.0 + 8 * e + 4 * f + 2 * g + h) / 16.0;
        double value = mantissa * Math.pow(2, exp);
        if (a == 1) {
            value = -value;
        }
        return String.format(Locale.ENGLISH, "#%.18e", value);
    }

    @SuppressWarnings("RedundantIfStatement")
    // More relaxed then the method from the ARM docs since we also test the other variants.
    public boolean isBfxPreferred(int sf, int uns, int imms, int immr) {
        if (imms < immr) {
            return false;
        }
        if (sf == 0 && imms == 31) {
            return false;
        }
        if (sf == 1 && imms == 63) {
            return false;
        }
        if (immr == 0) {
            if (sf == 0 && (imms == 7 || imms == 15)) {
                return false;
            }
        }
        return true;
    }

    private int isBitSet(int v, int bitNo) {
        return (v >> bitNo) & 1;
    }

    public int bitCount(int v) {
        return Integer.bitCount(v);
    }

    private String toBinaryStringWithSpacesBetweenBytes(int v) {
        StringBuilder result = new StringBuilder(Integer.toBinaryString(v));
        while (result.length() < 32) {
            result.insert(0, "0");
        }
        result.insert(24, " ");
        result.insert(16, " ");
        result.insert(8, " ");
        return result.toString();
    }

    public void logUnknownOpcode(int opcode) {
        System.out.println("         33222222 22221111 111111");
        System.out.println("         10987654 32109876 54321098 76543210");
        System.out.println(String.format("opcode = %s = 0x%08x", toBinaryStringWithSpacesBetweenBytes(opcode), opcode));
    }

    public String decodePstatefield(int op1, int op2) {
        switch ((op1 << 3) | op2) {
            case 3: return "UAO";
            case 4: return "PAN";
            case 5: return "SPSel";
            case 30: return "DAIFSet";
            case 31: return "DAIFClr";
            default: throw new UndefinedInstructionException("op1=" + op1 + " op2=" + op2);
        }
    }

}

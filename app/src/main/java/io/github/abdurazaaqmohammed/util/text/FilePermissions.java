package io.github.abdurazaaqmohammed.util.text;

/**
 * Unix permission formatting for file rows. Pure helpers over the numeric
 * st_mode (from {@code Os.lstat} on locally stat-able paths), the {@code ls -l}
 * permission column cached on root/Shizuku listings, or the octal output of
 * {@code stat -c %a} (root permission mirror). No android imports so the
 * logic stays unit-testable on the JVM.
 */
public final class FilePermissions {

    /** exec bit per triad: owner, group, other. */
    private static final int[] EXEC_BIT = {0100, 0010, 0001};
    /** setuid / setgid / sticky. */
    private static final int[] SPECIAL_BIT = {04000, 02000, 01000};

    private FilePermissions() {
    }

    /** Display text for a numeric st_mode, e.g. {@code rwxr-x--x (750)}; null when unusable. */
    public static String formatMode(int mode) {
        return formatBits(mode & 07777);
    }

    /** Display text for a {@code stat -c %a} output token ("750", "4755"); null when invalid. */
    public static String formatOctal(String octal) {
        if (octal == null) return null;
        String t = octal.trim();
        if (t.isEmpty() || t.length() > 4) return null;
        int bits = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '7') return null;
            bits = (bits << 3) | (c - '0');
        }
        return formatBits(bits);
    }

    /**
     * Display text for the 9-char {@code ls -l} permission column
     * ("rwxr-x--x", "rwSr--r--", ...); null when it is not a valid column.
     * The type character is already stripped by the listings that cache it.
     */
    public static String formatLsMode(String lsMode) {
        if (lsMode == null) return null;
        String s = lsMode.trim();
        if (s.length() != 9) return null;
        int bits = 0;
        for (int t = 0; t < 3; t++) {
            if (s.charAt(t * 3) == 'r') bits |= EXEC_BIT[t] << 2;
            else if (s.charAt(t * 3) != '-') return null;
            if (s.charAt(t * 3 + 1) == 'w') bits |= EXEC_BIT[t] << 1;
            else if (s.charAt(t * 3 + 1) != '-') return null;
            char x = s.charAt(t * 3 + 2);
            if (x == 'x') {
                bits |= EXEC_BIT[t];
            } else if (x == 's' && t < 2) {
                bits |= EXEC_BIT[t] | SPECIAL_BIT[t];
            } else if (x == 'S' && t < 2) {
                bits |= SPECIAL_BIT[t];
            } else if (x == 't' && t == 2) {
                bits |= EXEC_BIT[t] | SPECIAL_BIT[t];
            } else if (x == 'T' && t == 2) {
                bits |= SPECIAL_BIT[t];
            } else if (x != '-') {
                return null;
            }
        }
        return formatBits(bits);
    }

    /** {@code rwxr-x--x (750)} style text from 12 permission bits. */
    private static String formatBits(int bits) {
        StringBuilder sb = new StringBuilder(16);
        for (int t = 0; t < 3; t++) {
            sb.append((bits & (EXEC_BIT[t] << 2)) != 0 ? 'r' : '-');
            sb.append((bits & (EXEC_BIT[t] << 1)) != 0 ? 'w' : '-');
            boolean exec = (bits & EXEC_BIT[t]) != 0;
            boolean special = (bits & SPECIAL_BIT[t]) != 0;
            if (special) {
                sb.append(exec ? (t == 2 ? 't' : 's') : (t == 2 ? 'T' : 'S'));
            } else {
                sb.append(exec ? 'x' : '-');
            }
        }
        sb.append(" (").append(Integer.toOctalString(bits & 07777)).append(')');
        return sb.toString();
    }
}

package modder.hub.dexeditor.smali;

/**
 * SmaliHelper: Utility methods for parsing and manipulating Smali signatures and text.
 */
public class SmaliHelper {

    /**
     * Converts a Smali type signature (Lcom/pkg/Class;) or a dotted class name to slash format.
     */
    public static String smali2OnlySlash(String smaliName) {
        if (smaliName.startsWith("L") && smaliName.endsWith(";")) {
            return smaliName.substring(1, smaliName.length() - 1);
        }
        return smaliName.replace('.', '/');
    }

    /**
     * Extracts the class name without package (e.g., com/pkg/Main -> Main, or Lcom/pkg/Main; -> Main).
     */
    public static String extractSimpleName(String str) {
        if (str == null || str.isEmpty()) return "";
        int lastSlashIndex = str.lastIndexOf('/');
        int start = lastSlashIndex + 1;
        int end = str.length();
        if (str.endsWith(";")) {
            end--;
        }
        return str.substring(start, end);
    }

    /**
     * Equivalent of {@code s.trim().startsWith("# ")}, without the full-string copy that
     * {@link String#trim()} makes. {@code trim()} strips leading/trailing chars <= U+0020;
     * only the leading ones matter for a prefix check.
     */
    public static boolean hasSmaliHeader(String s) {
        if (s == null) return false;
        int i = 0, n = s.length();
        while (i < n && s.charAt(i) <= ' ') i++;
        return i + 1 < n && s.charAt(i) == '#' && s.charAt(i + 1) == ' ';
    }

    /**
     * Equivalent of {@code s.replaceFirst("(?s)^#.*?\\n\\n", "")}: when the string starts
     * with {@code '#'} it is cut right after the first {@code \n\n}; otherwise it is
     * returned unchanged. No regex compilation and no copy on the no-match path.
     */
    public static String stripSmaliHeaderUnchecked(String s) {
        if (s == null || s.isEmpty() || s.charAt(0) != '#') return s;
        int idx = s.indexOf("\n\n");
        return idx < 0 ? s : s.substring(idx + 2);
    }

    /**
     * Equivalent of the gate+strip pair used throughout the dex editor:
     * {@code if (s.trim().startsWith("# ")) s = s.replaceFirst("(?s)^#.*?\\n\\n", "");}.
     * The regex anchor {@code ^} only matches a {@code '#'} at index 0, so a header preceded
     * by whitespace passes the gate but is not stripped - preserved exactly here.
     */
    public static String stripSmaliHeader(String s) {
        if (!hasSmaliHeader(s) || s.charAt(0) != '#') return s;
        return stripSmaliHeaderUnchecked(s);
    }

    /**
     * Finds the positions of the outermost double quotes in a string.
     * Useful for highlighting string literals in Smali.
     */
    public static int[] getOuterQuotePositions(String input) {
        int startQuote = -1;
        int endQuote = -1;
        boolean insideString = false;
        boolean escapeNext = false;
        for (int i = 0; i < input.length(); i++) {
            char currentChar = input.charAt(i);
            if (currentChar == '\\') {
                escapeNext = true;
            } else if (currentChar == '"' && !escapeNext) {
                if (!insideString) {
                    startQuote = i;
                    insideString = true;
                } else {
                    endQuote = i;
                    break;
                }
            } else {
                escapeNext = false;
            }
        }
        return new int[]{startQuote, endQuote};
    }
}

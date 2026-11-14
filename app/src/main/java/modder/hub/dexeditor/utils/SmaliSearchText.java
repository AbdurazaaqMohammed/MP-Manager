package modder.hub.dexeditor.utils;

import java.util.ArrayList;
import java.util.List;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.HiddenApiRestriction;

/** Source locations come from the displayed smali, not dex iteration order or raw pool text. */
public final class SmaliSearchText {
    private SmaliSearchText() {}

    public record Literal(int line, int column, String source, String value) {}

    public static List<Literal> literals(String smali) {
        List<Literal> out = new ArrayList<>();
        if (smali == null) return out;
        int start = 0, line = 0, annotationDepth = 0;
        while (start < smali.length()) {
            int end = smali.indexOf('\n', start);
            if (end < 0) end = smali.length();
            String source = smali.substring(start, end);
            String trimmed = source.trim();
            if (tokenAt(trimmed, 0, ".annotation") || tokenAt(trimmed, 0, ".subannotation")
                    || (annotationDepth > 0 && subannotationValue(trimmed))) annotationDepth++;
            if (annotationDepth > 0 || tokenAt(trimmed, 0, "const-string")
                    || tokenAt(trimmed, 0, "const-string/jumbo") || tokenAt(trimmed, 0, ".field")) {
                boolean escaped = false;
                int quote = -1;
                for (int i = 0; i < source.length(); i++) {
                    char c = source.charAt(i);
                    if (escaped) { escaped = false; continue; }
                    if (quote < 0 && c == '#') break;
                    if (quote >= 0 && c == '\\') { escaped = true; continue; }
                    if (c == '"') {
                        if (quote < 0) quote = i;
                        else {
                            out.add(new Literal(line, quote + 1, source.trim(), unescape(source.substring(quote + 1, i))));
                            quote = -1;
                        }
                    }
                }
            }
            if (endDirective(trimmed, "annotation") || endDirective(trimmed, "subannotation"))
                annotationDepth = Math.max(0, annotationDepth - 1);
            start = end + 1;
            line++;
        }
        return out;
    }

    /** Match complete tokens, not a literal space or an opcode/directive prefix. */
    private static boolean tokenAt(String text, int start, String token) {
        int end = start + token.length();
        return text.startsWith(token, start) && (end == text.length()
                || Character.isWhitespace(text.charAt(end)) || text.charAt(end) == '#');
    }

    private static int skipWhitespace(String text, int start) {
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        return start;
    }

    private static boolean endDirective(String text, String kind) {
        return tokenAt(text, 0, ".end") && tokenAt(text, skipWhitespace(text, 4), kind);
    }

    private static boolean subannotationValue(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // An assignment directive must precede a literal/comment, never
            // occur inside one. Do not normalize whitespace in string values.
            if (c == '#' || c == '"' || c == '\'') return false;
            if (c == '=') return tokenAt(text, skipWhitespace(text, i + 1), ".subannotation");
        }
        return false;
    }

    public static String unescape(String literal) {
        StringBuilder out = new StringBuilder(literal.length());
        for (int i = 0; i < literal.length(); i++) {
            char c = literal.charAt(i);
            if (c != '\\' || i + 1 >= literal.length()) { out.append(c); continue; }
            char next = literal.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case '\\', '"', '\'' -> out.append(next);
                case 'u' -> {
                    if (i + 4 < literal.length()) {
                        try {
                            out.append((char) Integer.parseInt(literal.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) { out.append(c).append(next); }
                    } else out.append(c).append(next);
                }
                default -> out.append(c).append(next);
            }
        }
        return out.toString();
    }

    public static int declarationLine(String smali, String directive, String signature) {
        if (smali == null || signature == null) return -1;
        int start = 0, line = 0;
        while (start < smali.length()) {
            int end = smali.indexOf('\n', start);
            if (end < 0) end = smali.length();
            String text = smali.substring(start, end).trim();
            if (tokenAt(text, 0, directive) && signature.equals(declaredSignature(text, directive))) return line;
            start = end + 1;
            line++;
        }
        return -1;
    }

    /** Read the declaration's member, never search its initializer or trailing comment. */
    private static String declaredSignature(String text, String directive) {
        boolean field = directive.equals(".field");
        if (!field && !directive.equals(".method")) return null;
        DeclarationReader reader = new DeclarationReader(text, directive.length());
        char separator = field ? ':' : '(';
        String name;
        while (true) {
            name = reader.name();
            if (name == null) return null;
            reader.whitespace();
            if (reader.peek() == separator) break;
            if (AccessFlags.getAccessFlag(name) == null && HiddenApiRestriction.forName(name) == null) return null;
        }
        reader.position++;
        StringBuilder signature = new StringBuilder(name);
        signature.append(separator);
        if (!field) {
            reader.whitespace();
            while (reader.peek() != ')') {
                String parameter = reader.type(false);
                if (parameter == null) return null;
                signature.append(parameter);
                reader.whitespace();
            }
            reader.position++;
            signature.append(')');
        }
        String type = reader.type(!field);
        if (type == null) return null;
        signature.append(type);
        reader.whitespace();
        char trailing = reader.peek();
        return trailing == '\0' || trailing == '#' || (field && trailing == '=') ? signature.toString() : null;
    }

    private static final class DeclarationReader {
        private final String text;
        private int position;

        private DeclarationReader(String text, int position) {
            this.text = text;
            this.position = position;
        }

        private char peek() { return position < text.length() ? text.charAt(position) : '\0'; }
        private void whitespace() { position = skipWhitespace(text, position); }

        private String name() {
            whitespace();
            if (peek() == '`') return quotedName();
            int start = position;
            while (position < text.length()) {
                char c = peek();
                if (Character.isWhitespace(c) || ":()=#\"'`;[]/".indexOf(c) >= 0) break;
                position++;
            }
            return position == start ? null : text.substring(start, position);
        }

        private String quotedName() {
            int start = ++position;
            int end = text.indexOf('`', start);
            if (end < 0 || end == start) return null;
            position = end + 1;
            return text.substring(start, end);
        }

        private String type(boolean allowVoid) {
            whitespace();
            StringBuilder type = new StringBuilder();
            while (peek() == '[') {
                type.append('[');
                position++;
                whitespace();
            }
            char kind = peek();
            if ("ZBSCIJFD".indexOf(kind) >= 0 || (kind == 'V' && allowVoid && type.length() == 0)) {
                position++;
                return type.append(kind).toString();
            }
            if (kind != 'L') return null;
            type.append('L');
            position++;
            boolean hasName = false;
            while (peek() != ';') {
                char c = peek();
                if (c == '\0' || Character.isWhitespace(c) || "()=#\"':[]".indexOf(c) >= 0) return null;
                if (c == '`') {
                    String quoted = quotedName();
                    if (quoted == null) return null;
                    type.append(quoted);
                } else {
                    type.append(c);
                    position++;
                }
                hasName = true;
            }
            if (!hasName) return null;
            position++;
            return type.append(';').toString();
        }
    }
}

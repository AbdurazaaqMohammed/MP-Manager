package io.github.abdurazaaqmohammed.feature.editor;

import java.util.function.UnaryOperator;

import io.github.rosemoe.sora.lang.format.AsyncFormatter;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.TextRange;
import modder.hub.dexeditor.smali.SmaliIndentFormatter;

/**
 * Pure formatters and {@link AsyncFormatter} bridges for structured languages
 * (Smali and JSON) supported by Sora's {@code formatCodeAsync()} action.
 */
public final class StructuredCodeFormatter {

    private static final String INDENT_UNIT = SmaliIndentFormatter.INDENT_UNIT;

    private StructuredCodeFormatter() {
    }

    /** Creates an {@link AsyncFormatter} for the given TextMate scope, or {@code null} if unsupported. */
    public static AsyncFormatter forScope(String scope) {
        if ("source.smali".equals(scope)) {
            return new TransformFormatter(SmaliIndentFormatter::format);
        }
        if ("source.json".equals(scope)) {
            return new TransformFormatter(StructuredCodeFormatter::formatJson);
        }
        return null;
    }

    /**
     * Creates an {@link AsyncFormatter} applying hierarchical Smali indentation.
     */
    public static AsyncFormatter forSmali() {
        return new TransformFormatter(SmaliIndentFormatter::format);
    }

    /**
     * Pretty-prints JSON using 4-space indentation while preserving object key
     * order, numeric representations, and string escapes verbatim. Returns the
     * input unchanged when quotes or brackets are unbalanced.
     */
    public static String formatJson(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        int len = raw.length();
        StringBuilder out = new StringBuilder(len + Math.min(len / 4, 4096));
        boolean inString = false;
        boolean escaped = false;
        boolean sawToken = false;
        int depth = 0;

        for (int i = 0; i < len; i++) {
            char c = raw.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (Character.isWhitespace(c)) {
                continue;
            }
            sawToken = true;
            if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '{' || c == '[') {
                char closing = (c == '{') ? '}' : ']';
                int nextIdx = nextNonWhitespace(raw, i + 1);
                if (nextIdx >= 0 && raw.charAt(nextIdx) == closing) {
                    out.append(c).append(closing);
                    i = nextIdx;
                } else {
                    out.append(c).append('\n');
                    depth++;
                    appendIndent(out, depth);
                }
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth < 0) {
                    return raw;
                }
                out.append('\n');
                appendIndent(out, depth);
                out.append(c);
            } else if (c == ',') {
                out.append(",\n");
                appendIndent(out, depth);
            } else if (c == ':') {
                out.append(": ");
            } else {
                out.append(c);
            }
        }

        if (!sawToken || inString || depth != 0) {
            return raw;
        }
        if (raw.endsWith("\n")) {
            out.append('\n');
        }
        return out.toString();
    }

    private static int nextNonWhitespace(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static void appendIndent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) {
            out.append(INDENT_UNIT);
        }
    }

    private static final class TransformFormatter extends AsyncFormatter {
        private final UnaryOperator<String> transform;

        TransformFormatter(UnaryOperator<String> transform) {
            this.transform = transform;
        }

        @Override
        public TextRange formatAsync(Content text, TextRange cursorRange) {
            String original = text.toString();
            String formatted = transform.apply(original);
            if (formatted != null && !formatted.equals(original)) {
                int lastLine = Math.max(0, text.getLineCount() - 1);
                int lastCol = text.getColumnCount(lastLine);
                text.replace(0, 0, lastLine, lastCol, formatted);
            }
            // Returning null tells CodeEditor to preserve/clamp the prior cursor safely.
            return null;
        }

        @Override
        public TextRange formatRegionAsync(
                Content text, TextRange rangeToFormat, TextRange cursorRange) {
            return formatAsync(text, cursorRange);
        }
    }
}

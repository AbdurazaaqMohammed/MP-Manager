package io.github.abdurazaaqmohammed.domain.text;

/**
 * Text counting logic extracted from ToolRunnerActivity.buildTextCounter().
 */
public final class TextStats {

    private TextStats() {
    }

    public static int chars(String t) {
        return t == null ? 0 : t.length();
    }

    public static int lines(String t) {
        if (t == null || t.isEmpty()) return 0;
        return t.split("\n", -1).length;
    }

    public static int words(String t) {
        if (t == null) return 0;
        String trimmed = t.trim();
        if (trimmed.isEmpty()) return 0;
        return trimmed.split("\\s+").length;
    }

    public static int sentences(String t) {
        if (t == null) return 0;
        int count = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '.' || c == '!' || c == '?') count++;
        }
        return count;
    }

    public static String summary(String t) {
        String text = t == null ? "" : t;
        return "Chars: " + chars(text)
                + "  Words: " + words(text)
                + "  Lines: " + lines(text)
                + "  Sentences: " + sentences(text);
    }
}

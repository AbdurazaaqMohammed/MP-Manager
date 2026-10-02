package io.github.abdurazaaqmohammed.packs.notes;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

public final class Markdown {

    private Markdown() {
    }

    public static CharSequence render(String src, int accent) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        if (src == null || src.isEmpty()) return out;
        String[] lines = src.split("\n", -1);
        boolean inCode = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                int start = out.length();
                out.append(line).append('\n');
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(0x14000000), start, out.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            if (trimmed.isEmpty()) {
                out.append('\n');
                continue;
            }
            if (renderHeading(out, trimmed, accent)) continue;
            if (renderQuote(out, trimmed, accent)) continue;
            if (renderRule(out, trimmed)) continue;
            if (renderBullet(out, trimmed)) continue;
            int start = out.length();
            out.append(trimmed).append('\n');
            inline(out, start, out.length() - 1, accent);
        }
        trimTrailingNewlines(out);
        return out;
    }

    private static boolean renderHeading(SpannableStringBuilder out, String trimmed, int accent) {
        int hashes = 0;
        while (hashes < trimmed.length() && trimmed.charAt(hashes) == '#') hashes++;
        if (hashes == 0 || hashes > 6 || hashes >= trimmed.length()
                || trimmed.charAt(hashes) != ' ') return false;
        int start = out.length();
        out.append(trimmed.substring(hashes + 1).trim()).append('\n');
        out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(1.55f - hashes * 0.11f), start, out.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(accent), start, out.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return true;
    }

    private static boolean renderQuote(SpannableStringBuilder out, String trimmed, int accent) {
        if (!trimmed.startsWith("> ")) return false;
        int start = out.length();
        out.append(trimmed.substring(2)).append('\n');
        out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(accent), start, out.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return true;
    }

    private static boolean renderRule(SpannableStringBuilder out, String trimmed) {
        if (!trimmed.equals("---") && !trimmed.equals("***") && !trimmed.equals("___")) return false;
        int start = out.length();
        out.append("\u2014\u2014\u2014\u2014\u2014\n");
        out.setSpan(new ForegroundColorSpan(0x55000000), start, out.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return true;
    }

private static boolean renderBullet(SpannableStringBuilder out, String trimmed) {
        String bullet = bulletOf(trimmed);
        if (bullet == null) return false;
        int start = out.length();
        int idx = bullet.length();
        boolean done = idx < trimmed.length()
                && (trimmed.charAt(idx) == 'x' || trimmed.charAt(idx) == 'X');
        String rest = trimmed.substring(Math.min(trimmed.length(), idx + 1)).trim();
        out.append((done ? "\u2611 " : "\u2610 ") + rest).append('\n');
        if (done) {
            out.setSpan(new StrikethroughSpan(), start, out.length() - 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new ForegroundColorSpan(0x88000000), start, out.length() - 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return true;
    }


    private static void inline(SpannableStringBuilder out, int start, int end, int accent) {
        String text = out.subSequence(start, end).toString();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '*' || c == '_') {
                String marker = String.valueOf(c);
                int close = text.indexOf(marker, i + 1);
                if (close > i + 1 && close - i <= 200
                        && text.substring(i + 1, close).indexOf('\n') < 0
                        && !(c == '_' && isWordChar(text.charAt(i + 1)))) {
                    out.setSpan(new StyleSpan(c == '*' ? Typeface.BOLD : Typeface.ITALIC),
                            start + i + 1, start + close, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            }
            if (c == '~' && i + 1 < text.length() && text.charAt(i + 1) == '~') {
                int close = text.indexOf("~~", i + 2);
                if (close > i + 2) {
                    out.setSpan(new StrikethroughSpan(), start + i + 2, start + close,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 2;
                    continue;
                }
            }
            if (c == '`') {
                int close = text.indexOf('`', i + 1);
                if (close > i + 1) {
                    out.setSpan(new TypefaceSpan("monospace"), start + i + 1, start + close,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new BackgroundColorSpan(0x14000000), start + i + 1, start + close,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = close + 1;
                    continue;
                }
            }
            if (c == 'h' && text.startsWith("http", i)) {
                int stop = i;
                while (stop < text.length() && !Character.isWhitespace(text.charAt(stop))) stop++;
                String url = text.substring(i, stop);
                while (url.endsWith(".") || url.endsWith(",") || url.endsWith(")")) {
                    url = url.substring(0, url.length() - 1);
                }
                if (url.length() > 7) {
                    out.setSpan(new URLSpan(url), start + i, start + i + url.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new UnderlineSpan(), start + i, start + i + url.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(accent), start + i, start + i + url.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i += url.length();
                    continue;
                }
            }
            i++;
        }
    }

    private static void trimTrailingNewlines(SpannableStringBuilder out) {
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    public static String bulletOf(String trimmed) {
        if (trimmed == null) return null;
        if (starts(trimmed, "- [ ] ") || starts(trimmed, "- [x] ") || starts(trimmed, "- [X] ")) {
            return "- [ ] ";
        }
        if (starts(trimmed, "* [ ] ") || starts(trimmed, "* [x] ") || starts(trimmed, "* [X] ")) {
            return "* [ ] ";
        }
        if (starts(trimmed, "+ [ ] ") || starts(trimmed, "+ [x] ") || starts(trimmed, "+ [X] ")) {
            return "+ [ ] ";
        }
        if (starts(trimmed, "[] ")) return "[] ";
        if (starts(trimmed, "[ ] ") || starts(trimmed, "[x] ") || starts(trimmed, "[X] ")) {
            return "[] ";
        }
        return null;
    }

    private static boolean starts(String s, String prefix) {
        return s.length() >= prefix.length() && s.startsWith(prefix);
    }

    public static boolean isDoneBullet(String trimmed) {
        String bullet = bulletOf(trimmed);
        if (bullet == null) return false;
        int idx = bullet.length();
        return idx < trimmed.length()
                && (trimmed.charAt(idx) == 'x' || trimmed.charAt(idx) == 'X');
}


public static String plain(String line) {
        if (line == null) return "";
        String s = line.trim();
        int hashes = 0;
        while (hashes < s.length() && s.charAt(hashes) == '#') hashes++;
        if (hashes > 0 && hashes < s.length() && s.charAt(hashes) == ' ') {
            s = s.substring(hashes + 1).trim();
        }
        if (s.startsWith("> ")) s = s.substring(2);
        String bullet = bulletOf(s);
        if (bullet != null) {
            s = s.substring(Math.min(s.length(), bullet.length() + 1)).trim();
        } else if (s.startsWith("- ") || s.startsWith("* ") || s.startsWith("+ ")) {
            s = s.substring(2);
        }
        s = s.replace("**", "").replace("~~", "").replace("`", "");
        s = s.replace("*", "").replace("_", "");
        if (s.startsWith("[] ")) s = s.substring(3);
        return s.trim();
    }

    public static String plainText(String src) {
        if (src == null || src.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean inCode = false;
        for (String line : src.split("\n", -1)) {
            if (line.trim().startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            String v = inCode ? line : plain(line);
            if (sb.length() > 0) sb.append('\n');
            sb.append(v);
        }
        trimTrailingNewlines(sb);
        return sb.toString();
    }

    private static void trimTrailingNewlines(StringBuilder sb) {
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.deleteCharAt(sb.length() - 1);
        }
    }

    public static int words(String src) {
        if (src == null || src.isEmpty()) return 0;
        int n = 0;
        boolean inWord = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                n++;
            }
        }
        return n;
    }

    public static int checklistTotal(String src) {
        if (src == null) return 0;
        int n = 0;
        for (String line : src.split("\n")) {
            if (bulletOf(line.trim()) != null) n++;
        }
        return n;
    }

    public static int checklistDone(String src) {
        if (src == null) return 0;
        int n = 0;
        for (String line : src.split("\n")) {
            if (isDoneBullet(line.trim())) n++;
        }
        return n;
    }

    public static boolean isBullet(String line) {
        return line != null && bulletOf(line.trim()) != null;
    }

    public static String toggleBullet(String line, boolean done) {
        String trimmed = line == null ? "" : line.trim();
        String bullet = bulletOf(trimmed);
        String rest;
        if (bullet != null) {
            rest = trimmed.substring(Math.min(trimmed.length(), bullet.length() + 1)).trim();
        } else {
            rest = plain(trimmed);
        }
        return "- [" + (done ? "x" : " ") + "] " + rest;
    }

    public static String toMarkdownFile(Note note) {
        StringBuilder sb = new StringBuilder();
        String title = note.displayTitle();
        if (!title.isEmpty()) sb.append("# ").append(title).append("\n\n");
        sb.append(note.plain()).append('\n');
        if (note.tags != null && !note.tags.isEmpty()) {
            sb.append("\n---\n");
            for (String t : note.tags) sb.append("#").append(t).append(' ');
        }
        return sb.toString();
    }

    public static String toPlainFile(Note note) {
        StringBuilder sb = new StringBuilder();
        String title = note.displayTitle();
        if (!title.isEmpty()) sb.append(title).append("\n\n");
        sb.append(plainText(note.plain())).append('\n');
        return sb.toString();
    }

    public static String colorHex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    public static int dim(int color) {
        return Color.argb(140, Color.red(color), Color.green(color), Color.blue(color));
    }
}

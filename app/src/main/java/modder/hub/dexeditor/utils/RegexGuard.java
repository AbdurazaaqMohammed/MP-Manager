package modder.hub.dexeditor.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Safety net around user-supplied regular expressions.
 *
 * The search and replace dialogs used to compile the raw query straight from a
 * dialog button listener, so a half-typed pattern such as {@code (a[} escaped
 * as an unchecked {@link PatternSyntaxException} and took the whole activity
 * down. {@link java.util.regex} is also a backtracking engine with no timeout,
 * so a nested-quantifier pattern can lock a worker up for minutes; matches are
 * therefore bounded in size and checked for cancellation between attempts.
 */
public final class RegexGuard {

    /**
     * Longest text handed to a single match operation. Backtracking cost grows
     * with the subject length, so capping it puts a ceiling on the worst case.
     */
    private static final int MAX_SUBJECT = 1 << 20;

    private RegexGuard() {
    }

    public static Pattern compile(String regex, boolean matchCase) throws PatternSyntaxException {
        return Pattern.compile(regex, (matchCase ? 0 : Pattern.CASE_INSENSITIVE) | Pattern.MULTILINE);
    }

    /**
     * Appends replacement safely handling escape sequences (\n, \t, \r, \\),
     * valid capture groups ($1..$N), and graceful fallback for literal $ or unassigned groups.
     */
    public static void appendCustomReplacement(Matcher matcher, StringBuffer sb, String replacement) {
        String unescaped = unescapeReplacement(replacement);
        int cursor = 0;
        int len = unescaped.length();
        StringBuilder res = new StringBuilder();
        while (cursor < len) {
            char c = unescaped.charAt(cursor);
            if (c == '\\') {
                cursor++;
                if (cursor < len) {
                    char next = unescaped.charAt(cursor);
                    if (next == '$' || next == '\\') {
                        res.append(next);
                    } else {
                        res.append('\\').append(next);
                    }
                    cursor++;
                } else {
                    res.append('\\');
                }
            } else if (c == '$') {
                cursor++;
                if (cursor < len && Character.isDigit(unescaped.charAt(cursor))) {
                    int start = cursor;
                    while (cursor < len && Character.isDigit(unescaped.charAt(cursor))) {
                        cursor++;
                    }
                    int groupNum = Integer.parseInt(unescaped.substring(start, cursor));
                    if (groupNum <= matcher.groupCount()) {
                        String groupVal = matcher.group(groupNum);
                        if (groupVal != null) res.append(groupVal);
                    } else {
                        res.append('$').append(unescaped.substring(start, cursor));
                    }
                } else {
                    res.append('$');
                }
            } else {
                res.append(c);
                cursor++;
            }
        }
        matcher.appendReplacement(sb, Matcher.quoteReplacement(res.toString()));
    }

    public static String unescapeReplacement(String str) {
        if (str == null || str.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\\' && i + 1 < str.length()) {
                char next = str.charAt(i + 1);
                if (next == 'n') { sb.append('\n'); i++; }
                else if (next == 't') { sb.append('\t'); i++; }
                else if (next == 'r') { sb.append('\r'); i++; }
                else if (next == '\\') { sb.append('\\'); i++; }
                else { sb.append(c); }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Returns null when the pattern is usable, otherwise a readable reason. */
    public static String validate(String regex, boolean matchCase) {
        if (regex == null || regex.isEmpty()) return "Regular expression is empty";
        try {
            compile(regex, matchCase);
        } catch (PatternSyntaxException e) {
            return describe(e);
        } catch (Throwable t) {
            return "Invalid regular expression: " + t.getMessage();
        }
        return null;
    }

    public static String describe(PatternSyntaxException e) {
        StringBuilder sb = new StringBuilder("Invalid regular expression");
        if (e.getDescription() != null) sb.append(": ").append(e.getDescription());
        if (e.getIndex() >= 0) sb.append(" (at position ").append(e.getIndex() + 1).append(")");
        return sb.toString();
    }

    /**
     * Wraps a pattern so {@code ^} and {@code $} bind to the whole pattern
     * instead of only its first and last alternative. Plain concatenation turns
     * {@code a|b} into {@code ^a|b$}, which silently matches the wrong text.
     */
    public static String anchorWhole(String regex) {
        return "(?:" + regex + ")";
    }

    /** Matcher over at most {@link #MAX_SUBJECT} characters of the subject. */
    public static Matcher boundedMatcher(Pattern pattern, CharSequence subject) {
        if (subject.length() <= MAX_SUBJECT) return pattern.matcher(subject);
        return pattern.matcher(subject.subSequence(0, MAX_SUBJECT));
    }

    /**
     * Flags the classic nested-quantifier shape that makes {@link java.util.regex}
 * backtrack exponentially: a quantified group that contains its own quantifier,
     * as in {@code (a+)+} or {@code (x+x+)+y}. Used to warn before a search locks
     * up, not to reject the pattern, so a false negative costs a slow search and a
     * false positive costs one toast. Ambiguous alternations such as {@code (a|a)*}
 * are not detected; the bounded subject and the cancellation checks are what keep
     * those survivable.
     */
    public static boolean mayBacktrackCatastrophically(String regex) {
        if (regex == null || regex.isEmpty()) return false;
        int depth = 0;
        boolean[] groupHasQuantifier = new boolean[64];
        boolean quantifiedNestedGroup = false;
        int escaped = 0;
        boolean inClass = false;
        for (int i = 0; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (escaped > 0) {
                escaped = 0;
                continue;
            }
            if (c == '\\') {
                escaped = 1;
                quantifiedNestedGroup = false;
                continue;
            }
            if (inClass) {
                if (c == ']') inClass = false;
                quantifiedNestedGroup = false;
                continue;
            }
            if (c == '[') {
                inClass = true;
                quantifiedNestedGroup = false;
                continue;
            }
            if (c == '(') {
                depth++;
                if (depth < groupHasQuantifier.length) groupHasQuantifier[depth] = false;
                quantifiedNestedGroup = false;
                continue;
            }
            if (c == ')') {
                boolean nested = false;
                if (depth > 0) {
                    depth--;
                    if (depth < groupHasQuantifier.length) {
                        nested = groupHasQuantifier[depth + 1];
                        groupHasQuantifier[depth + 1] = false;
                    }
                }
                if (nested && depth < groupHasQuantifier.length) groupHasQuantifier[depth] = true;
                quantifiedNestedGroup = nested;
                continue;
            }
            if (c == '*' || c == '+' || (c == '{' && looksLikeQuantifier(regex, i))) {
                if (quantifiedNestedGroup) return true;
                if (depth > 0 && depth < groupHasQuantifier.length) groupHasQuantifier[depth] = true;
                quantifiedNestedGroup = false;
                continue;
            }
            quantifiedNestedGroup = false;
        }
        return false;
    }

    private static boolean looksLikeQuantifier(String regex, int i) {
        int j = i + 1;
        if (j < regex.length() && regex.charAt(j) == '}') return false;
        while (j < regex.length() && regex.charAt(j) >= '0' && regex.charAt(j) <= '9') j++;
        if (j < regex.length() && (regex.charAt(j) == ',' || regex.charAt(j) == 'n' || regex.charAt(j) == 'm')) {
            while (j < regex.length() && regex.charAt(j) != '}') j++;
        }
        return j < regex.length() && regex.charAt(j) == '}';
    }
}
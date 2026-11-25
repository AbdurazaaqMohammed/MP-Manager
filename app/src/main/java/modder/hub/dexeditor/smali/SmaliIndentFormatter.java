package modder.hub.dexeditor.smali;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Formats Smali disassembly into consistent hierarchical 4-space indentation so
 * method headers, executable bodies, try/catch scopes, switches, array data,
 * and nested annotations remain visually distinct and align with editor indent
 * guide lines.
 */
public final class SmaliIndentFormatter {

    /** Four spaces per nesting level (matches Baksmali base indent and editor tab width). */
    public static final String INDENT_UNIT = "    ";

    private static final Pattern CATCH_RANGE_PATTERN = Pattern.compile(
            "^\\.(?:catch|catchall)\\b.*\\{\\s*:?([^\\s.]+)\\s*\\.\\.\\s*:?([^\\s}]+)\\s*\\}");

    private enum BlockKind {
        FIELD,
        PARAM,
        ANNOTATION,
        SUBANNOTATION,
        BRACE,
        PAYLOAD
    }

    private static final class BlockFrame {
        final BlockKind kind;
        final int indentLevel;

        BlockFrame(BlockKind kind, int indentLevel) {
            this.kind = kind;
            this.indentLevel = indentLevel;
        }
    }

    private SmaliIndentFormatter() {
    }

    /**
     * Re-indents {@code rawSmali} line by line without modifying non-whitespace
     * tokens or trailing newline semantics. Idempotent across repeated calls.
     */
    public static String format(String rawSmali) {
        if (rawSmali == null || rawSmali.isEmpty()) {
            return rawSmali;
        }
        String lineSep = rawSmali.contains("\r\n") ? "\r\n" : "\n";
        boolean endsWithNewline = rawSmali.endsWith("\n") || rawSmali.endsWith("\r");

        List<String> lines = splitLines(rawSmali);
        List<String> trimmedLines = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            trimmedLines.add(lines.get(i).trim());
        }

        StringBuilder out = new StringBuilder(rawSmali.length() + 256);
        int i = 0;
        int n = trimmedLines.size();
        Deque<BlockFrame> classStack = new ArrayDeque<>();

        while (i < n) {
            String t = trimmedLines.get(i);
            if (isDirective(t, ".method")) {
                classStack.clear();
                int methodEnd = findMethodEnd(trimmedLines, i);
                formatMethod(trimmedLines, i, methodEnd, out, lineSep);
                i = methodEnd + 1;
                if (i < n || endsWithNewline) {
                    out.append(lineSep);
                }
                continue;
            }

            if (t.isEmpty()) {
                // Keep blank lines free of spaces so off-side indent guides close cleanly.
                if (i < n - 1 || endsWithNewline) {
                    out.append(lineSep);
                }
                i++;
                continue;
            }

            int level = computeClassLineLevel(trimmedLines, i, t, classStack);
            appendIndentedLine(out, level, t);
            if (i < n - 1 || endsWithNewline) {
                out.append(lineSep);
            }
            i++;
        }

        return out.toString();
    }

    private static int computeClassLineLevel(
            List<String> lines, int idx, String t, Deque<BlockFrame> stack) {
        if (isDirective(t, ".end field")) {
            popFrame(stack, BlockKind.FIELD);
            return 0;
        }
        if (isDirective(t, ".field")) {
            stack.clear();
            if (hasMatchingEndBeforeClassMember(lines, idx + 1, ".end field")) {
                stack.push(new BlockFrame(BlockKind.FIELD, 0));
            }
            return 0;
        }
        if (isDirective(t, ".class") || isDirective(t, ".super")
                || isDirective(t, ".source") || isDirective(t, ".implements")) {
            stack.clear();
            return 0;
        }
        return applyBlockDirectives(t, stack, 0);
    }

    private static void formatMethod(
            List<String> lines, int startIdx, int endIdx, StringBuilder out, String lineSep) {
        appendIndentedLine(out, 0, lines.get(startIdx));

        int bodyStart = startIdx + 1;
        boolean hasExplicitEnd = endIdx > startIdx && isDirective(lines.get(endIdx), ".end method");
        int contentEnd = hasExplicitEnd ? endIdx - 1 : endIdx;
        if (bodyStart <= contentEnd) {
            int[] tryDepths = computeMethodTryDepths(lines, bodyStart, contentEnd);
            Deque<BlockFrame> blockStack = new ArrayDeque<>();
            boolean bodyStarted = false;

            for (int i = bodyStart; i <= contentEnd; i++) {
                out.append(lineSep);
                String t = lines.get(i);
                if (t.isEmpty()) {
                    continue;
                }
                bodyStarted = appendMethodLine(
                        lines, i, contentEnd, t, tryDepths[i - bodyStart], blockStack, bodyStarted, out);
            }
        }

        if (hasExplicitEnd) {
            out.append(lineSep);
            appendIndentedLine(out, 0, lines.get(endIdx));
        }
    }

    private static boolean appendMethodLine(
            List<String> lines,
            int idx,
            int contentEnd,
            String t,
            int tryDepth,
            Deque<BlockFrame> stack,
            boolean bodyStarted,
            StringBuilder out) {
        // Closing header/payload blocks take precedence so closing tag aligns with its opener.
        if (isDirective(t, ".end param")) {
            BlockFrame frame = popFrame(stack, BlockKind.PARAM);
            int level = frame != null ? frame.indentLevel : 1;
            appendIndentedLine(out, level, t);
            return bodyStarted;
        }
        if (isDirective(t, ".end annotation")) {
            BlockFrame frame = popFrame(stack, BlockKind.ANNOTATION);
            int level = frame != null ? frame.indentLevel : (bodyStarted ? 2 + tryDepth : 1);
            appendIndentedLine(out, level, t);
            return bodyStarted;
        }
        if (isDirective(t, ".end subannotation")) {
            BlockFrame frame = popFrame(stack, BlockKind.SUBANNOTATION);
            int level = frame != null ? frame.indentLevel : (bodyStarted ? 2 + tryDepth : 1);
            appendIndentedLine(out, level, t);
            return bodyStarted;
        }
        if (isDirective(t, ".end packed-switch")
                || isDirective(t, ".end sparse-switch")
                || isDirective(t, ".end array-data")) {
            BlockFrame frame = popFrame(stack, BlockKind.PAYLOAD);
            int level = frame != null ? frame.indentLevel : (2 + tryDepth);
            appendIndentedLine(out, level, t);
            return true;
        }

        // Method header directives always stay at level 1 when not nested inside an annotation.
        if (stack.isEmpty() && isMethodHeaderDirective(t)) {
            appendIndentedLine(out, 1, t);
            return bodyStarted;
        }
        if (stack.isEmpty() && isDirective(t, ".param")) {
            appendIndentedLine(out, 1, t);
            if (hasMatchingEndInRange(lines, idx + 1, contentEnd, ".end param")) {
                stack.push(new BlockFrame(BlockKind.PARAM, 1));
            }
            return bodyStarted;
        }

        // Annotations/subannotations/braces inside a header (.param or pre-body) stay under level 1;
        // once executable body begins, base is 2 + tryDepth.
        boolean inHeaderAnnotation = !stack.isEmpty() || !bodyStarted;
        int currentBase = stack.isEmpty()
                ? (bodyStarted ? 2 + tryDepth : 1)
                : (stack.peek().indentLevel + 1);

        if (isDirective(t, ".annotation")) {
            int openLevel = stack.isEmpty() ? (inHeaderAnnotation && !bodyStarted ? 1 : 2 + tryDepth) : currentBase;
            appendIndentedLine(out, openLevel, t);
            stack.push(new BlockFrame(BlockKind.ANNOTATION, openLevel));
            return bodyStarted;
        }

        if (!stack.isEmpty() && (stack.peek().kind == BlockKind.ANNOTATION
                || stack.peek().kind == BlockKind.SUBANNOTATION
                || stack.peek().kind == BlockKind.BRACE)) {
            int level = applyBraceAndSubannotation(t, stack);
            appendIndentedLine(out, level, t);
            return bodyStarted;
        }

        // Switch / array payloads always belong to executable method body.
        if (isDirective(t, ".packed-switch")
                || isDirective(t, ".sparse-switch")
                || isDirective(t, ".array-data")) {
            int openLevel = 2 + tryDepth + stack.size();
            appendIndentedLine(out, openLevel, t);
            stack.push(new BlockFrame(BlockKind.PAYLOAD, openLevel));
            return true;
        }

        if (!stack.isEmpty() && stack.peek().kind == BlockKind.PAYLOAD) {
            appendIndentedLine(out, stack.peek().indentLevel + 1, t);
            return true;
        }

        // Comments preserve current phase level; anything else starts executable method body.
        if (t.startsWith("#")) {
            int commentLevel = (bodyStarted ? 2 + tryDepth : 1);
            appendIndentedLine(out, commentLevel, t);
            return bodyStarted;
        }

        appendIndentedLine(out, 2 + tryDepth, t);
        return true;
    }

    private static int applyBlockDirectives(String t, Deque<BlockFrame> stack, int rootLevel) {
        if (isDirective(t, ".end annotation")) {
            BlockFrame frame = popFrame(stack, BlockKind.ANNOTATION);
            return frame != null ? frame.indentLevel : rootLevel;
        }
        if (isDirective(t, ".end subannotation")) {
            BlockFrame frame = popFrame(stack, BlockKind.SUBANNOTATION);
            return frame != null ? frame.indentLevel : rootLevel;
        }

        int currentLevel = stack.isEmpty() ? rootLevel : stack.peek().indentLevel + 1;
        if (isDirective(t, ".annotation")) {
            stack.push(new BlockFrame(BlockKind.ANNOTATION, currentLevel));
            return currentLevel;
        }
        if (!stack.isEmpty()) {
            return applyBraceAndSubannotation(t, stack);
        }
        return rootLevel;
    }

    private static int applyBraceAndSubannotation(String t, Deque<BlockFrame> stack) {
        // Trim leading closing braces first so "}" aligns with the key/line that opened "{".
        int leadingCloses = countLeadingClosingBraces(t);
        int pops = 0;
        while (pops < leadingCloses && !stack.isEmpty() && stack.peek().kind == BlockKind.BRACE) {
            stack.pop();
            pops++;
        }

        int lineLevel = stack.isEmpty() ? 0 : stack.peek().indentLevel + 1;

        if (containsSubannotationOpen(t)) {
            stack.push(new BlockFrame(BlockKind.SUBANNOTATION, lineLevel));
            return lineLevel;
        }

        int netUnclosed = countNetOpenBracesOutsideQuotes(t) + pops;
        for (int b = 0; b < netUnclosed; b++) {
            int openerLevel = stack.isEmpty() ? lineLevel : stack.peek().indentLevel + 1;
            // The first unclosed "{" on this line belongs to lineLevel itself.
            if (b == 0) {
                openerLevel = lineLevel;
            }
            stack.push(new BlockFrame(BlockKind.BRACE, openerLevel));
        }
        return lineLevel;
    }

    private static int[] computeMethodTryDepths(List<String> lines, int start, int end) {
        int len = end - start + 1;
        int[] depths = new int[len];
        if (len <= 0) {
            return depths;
        }

        Map<String, List<Integer>> labelPositions = new HashMap<>();
        for (int i = start; i <= end; i++) {
            String t = lines.get(i);
            String label = extractLabelName(t);
            if (label != null) {
                labelPositions.computeIfAbsent(label, k -> new ArrayList<>(2)).add(i);
            }
        }

        List<int[]> spans = new ArrayList<>();
        Set<Long> seenSpans = new HashSet<>();
        Set<Integer> matchedStartLines = new HashSet<>();

        // Primary resolution from explicit .catch / .catchall {:try_start .. :try_end} ranges.
        for (int i = start; i <= end; i++) {
            String t = lines.get(i);
            if (!isDirective(t, ".catch") && !isDirective(t, ".catchall")) {
                continue;
            }
            Matcher m = CATCH_RANGE_PATTERN.matcher(t);
            if (!m.find()) {
                continue;
            }
            List<Integer> starts = labelPositions.get(m.group(1));
            List<Integer> ends = labelPositions.get(m.group(2));
            if (starts == null || ends == null) {
                continue;
            }
            for (int s : starts) {
                Integer matchingEnd = firstAfter(ends, s);
                if (matchingEnd != null) {
                    addSpanIfUnique(spans, seenSpans, matchedStartLines, s, matchingEnd);
                    break;
                }
            }
        }

        // Fallback pairing for in-progress manual edits using :try_start_<id> / :try_end_<id>.
        for (int i = start; i <= end; i++) {
            String label = extractLabelName(lines.get(i));
            if (label == null || !label.startsWith("try_start_") || matchedStartLines.contains(i)) {
                continue;
            }
            String suffix = label.substring("try_start_".length());
            List<Integer> sameSuffixEnds = labelPositions.get("try_end_" + suffix);
            if (sameSuffixEnds != null) {
                Integer e = firstAfter(sameSuffixEnds, i);
                if (e != null) {
                    addSpanIfUnique(spans, seenSpans, matchedStartLines, i, e);
                }
            }
        }

        // Prefix-sum diff array turns every span (s, e) into +1 over [s + 1, e - 1] in O(1).
        int[] diff = new int[len + 1];
        for (int[] span : spans) {
            int openExclusive = span[0] - start + 1;
            int closeExclusive = span[1] - start;
            if (openExclusive < closeExclusive && openExclusive >= 0 && closeExclusive <= len) {
                diff[openExclusive]++;
                diff[closeExclusive]--;
            }
        }
        int running = 0;
        for (int k = 0; k < len; k++) {
            running += diff[k];
            depths[k] = Math.max(0, running);
        }
        return depths;
    }

    private static void addSpanIfUnique(
            List<int[]> spans, Set<Long> seen, Set<Integer> matchedStarts, int s, int e) {
        if (s >= e) {
            return;
        }
        long key = (((long) s) << 32) | (e & 0xFFFFFFFFL);
        if (seen.add(key)) {
            spans.add(new int[]{s, e});
            matchedStarts.add(s);
        }
    }

    private static Integer firstAfter(List<Integer> positions, int threshold) {
        for (int i = 0; i < positions.size(); i++) {
            int pos = positions.get(i);
            if (pos > threshold) {
                return pos;
            }
        }
        return null;
    }

    private static String extractLabelName(String t) {
        if (!t.startsWith(":")) {
            return null;
        }
        int end = 1;
        int len = t.length();
        while (end < len) {
            char c = t.charAt(end);
            if (Character.isWhitespace(c) || c == '#') {
                break;
            }
            end++;
        }
        return end > 1 ? t.substring(1, end) : null;
    }

    private static int findMethodEnd(List<String> lines, int startIdx) {
        int n = lines.size();
        for (int i = startIdx + 1; i < n; i++) {
            String t = lines.get(i);
            if (isDirective(t, ".end method")) {
                return i;
            }
            if (isDirective(t, ".method")) {
                return i - 1;
            }
        }
        return n - 1;
    }

    private static boolean hasMatchingEndBeforeClassMember(
            List<String> lines, int fromIdx, String endDirective) {
        int n = lines.size();
        for (int i = fromIdx; i < n; i++) {
            String t = lines.get(i);
            if (isDirective(t, endDirective)) {
                return true;
            }
            if (isDirective(t, ".field") || isDirective(t, ".method")
                    || isDirective(t, ".class") || isDirective(t, ".end method")) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasMatchingEndInRange(
            List<String> lines, int fromIdx, int endIdx, String endDirective) {
        for (int i = fromIdx; i <= endIdx; i++) {
            String t = lines.get(i);
            if (isDirective(t, endDirective)) {
                return true;
            }
            if (isDirective(t, ".param") || isMethodHeaderDirective(t)
                    || isMethodBodyDirectiveOrLabel(t)) {
                return false;
            }
        }
        return false;
    }

    private static boolean isMethodHeaderDirective(String t) {
        return isDirective(t, ".registers")
                || isDirective(t, ".locals")
                || isDirective(t, ".prologue");
    }

    private static boolean isMethodBodyDirectiveOrLabel(String t) {
        return t.startsWith(":")
                || isDirective(t, ".line")
                || isDirective(t, ".local")
                || isDirective(t, ".end local")
                || isDirective(t, ".restart local")
                || isDirective(t, ".catch")
                || isDirective(t, ".catchall")
                || isDirective(t, ".packed-switch")
                || isDirective(t, ".sparse-switch")
                || isDirective(t, ".array-data");
    }

    private static boolean isDirective(String trimmed, String directive) {
        if (directive.startsWith(".end ")) {
            return isEndDirective(trimmed, directive.substring(5));
        }
        if (!trimmed.startsWith(directive)) {
            return false;
        }
        int len = directive.length();
        return trimmed.length() == len
                || Character.isWhitespace(trimmed.charAt(len))
                || trimmed.charAt(len) == '#';
    }

    private static boolean isEndDirective(String trimmed, String name) {
        if (!trimmed.startsWith(".end")) {
            return false;
        }
        int i = 4;
        int n = trimmed.length();
        if (i >= n || !Character.isWhitespace(trimmed.charAt(i))) {
            return false;
        }
        while (i < n && Character.isWhitespace(trimmed.charAt(i))) {
            i++;
        }
        if (!trimmed.regionMatches(i, name, 0, name.length())) {
            return false;
        }
        int after = i + name.length();
        return after == n || Character.isWhitespace(trimmed.charAt(after)) || trimmed.charAt(after) == '#';
    }

    private static BlockFrame popFrame(Deque<BlockFrame> stack, BlockKind target) {
        while (!stack.isEmpty()) {
            BlockFrame top = stack.pop();
            if (top.kind == target) {
                return top;
            }
        }
        return null;
    }

    private static boolean containsSubannotationOpen(String t) {
        int idx = indexOutsideQuotesAndComment(t, ".subannotation");
        if (idx < 0) {
            return false;
        }
        int after = idx + ".subannotation".length();
        boolean boundaryBefore = idx == 0 || !Character.isLetterOrDigit(t.charAt(idx - 1));
        boolean boundaryAfter = after == t.length() || Character.isWhitespace(t.charAt(after));
        return boundaryBefore && boundaryAfter;
    }

    private static int indexOutsideQuotesAndComment(String t, String needle) {
        boolean inString = false;
        boolean escaped = false;
        int len = t.length();
        int nLen = needle.length();
        for (int i = 0; i < len; i++) {
            char c = t.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '#') {
                return -1;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (i + nLen <= len && t.regionMatches(i, needle, 0, nLen)) {
                return i;
            }
        }
        return -1;
    }

    private static int countLeadingClosingBraces(String t) {
        int count = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '}') {
                count++;
            } else if (!Character.isWhitespace(c)) {
                break;
            }
        }
        return count;
    }

    private static int countNetOpenBracesOutsideQuotes(String t) {
        int balance = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '#') {
                break;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                balance++;
            } else if (c == '}') {
                balance--;
            }
        }
        return balance;
    }

    private static void appendIndentedLine(StringBuilder out, int level, String trimmed) {
        for (int k = 0; k < level; k++) {
            out.append(INDENT_UNIT);
        }
        out.append(trimmed);
    }

    private static List<String> splitLines(String s) {
        List<String> list = new ArrayList<>();
        int start = 0;
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                int lineEnd = (i > start && s.charAt(i - 1) == '\r') ? i - 1 : i;
                list.add(s.substring(start, lineEnd));
                start = i + 1;
            }
        }
        if (start < n) {
            int lineEnd = s.endsWith("\r") ? n - 1 : n;
            list.add(s.substring(start, lineEnd));
        }
        return list;
    }
}

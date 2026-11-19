package io.github.abdurazaaqmohammed.util.text;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

/**
 * Pure line-diff engine behind the text compare screen (no Android classes,
 * so the whole pipeline is unit tested): builds the row model from two raw
 * line lists (honoring the ignore/match-case options), filters it for the
 * all / differences / matches views and renders it as a conventional unified
 * diff that {@code patch} and {@code git apply} can consume.
 */
public final class TextDiffEngine {

    /** Context lines shown around each change block in the exported .diff. */
    public static final int CONTEXT = 3;

    /** GNU patch's marker for a final line without a trailing newline. */
    private static final String NO_EOL_MARKER = "\\ No newline at end of file\n";

    private TextDiffEngine() {
    }

    /** How whitespace and case are treated while matching lines. */
    public enum IgnoreMode {NONE, TRIM, WHITESPACE, WHITESPACE_EMPTY}

    /** Which rows the compare list shows. */
    public enum FilterMode {ALL, DIFFERENT, SAME}

    /**
     * One rendered row of the diff: the tag plus both raw texts and their
     * 1-based numbers in the original files (0 = the side has no line here).
     */
    public static final class DiffLine {
        public final DiffRow.Tag tag;
        public final String oldLine;
        public final String newLine;
        public final int oldNumber;
        public final int newNumber;

        public DiffLine(DiffRow.Tag tag, String oldLine, String newLine, int oldNumber, int newNumber) {
            this.tag = tag;
            this.oldLine = oldLine;
            this.newLine = newLine;
            this.oldNumber = oldNumber;
            this.newNumber = newNumber;
        }
    }

    // ==========================================
    // Row model
    // ==========================================

    /**
     * Builds the diff rows for two raw line lists. {@code leftEndsWithNewline}
     * / {@code rightEndsWithNewline} say whether the file contents end with a
     * line terminator; an EQUAL row whose sides disagree on that termination
     * is reported as CHANGE, because the byte content differs — the same
     * distinction the {@code \ No newline at end of file} marker makes.
     * Identical prefix and suffix regions are trimmed before the engine runs
     * (they are appended back as EQUAL rows), which keeps large
     * mostly-identical files fast.
     */
    public static List<DiffLine> buildDiff(List<String> leftRaw, boolean leftEndsWithNewline,
                                           List<String> rightRaw, boolean rightEndsWithNewline,
                                           IgnoreMode ignoreMode, boolean matchCase) {
        boolean dropBlanks = ignoreMode == IgnoreMode.WHITESPACE_EMPTY;
        // Keep the original 1-based numbers of every kept line so ignored lines
        // (blanks) don't shift the numbering.
        int[] oldNumbers = new int[leftRaw.size() + 1];
        int[] newNumbers = new int[rightRaw.size() + 1];
        List<String> oldLines = significantLines(leftRaw, oldNumbers, dropBlanks);
        List<String> newLines = significantLines(rightRaw, newNumbers, dropBlanks);

        BiPredicate<String, String> eq = equalizer(ignoreMode, matchCase);
        int maxPrefix = Math.min(oldLines.size(), newLines.size());
        int prefix = 0;
        while (prefix < maxPrefix && eq.test(oldLines.get(prefix), newLines.get(prefix))) prefix++;
        int maxSuffix = maxPrefix - prefix;
        int suffix = 0;
        while (suffix < maxSuffix
                && eq.test(oldLines.get(oldLines.size() - 1 - suffix), newLines.get(newLines.size() - 1 - suffix))) {
            suffix++;
        }

        List<String> midOld = oldLines.subList(prefix, oldLines.size() - suffix);
        List<String> midNew = newLines.subList(prefix, newLines.size() - suffix);

        List<DiffRow> midRows = midOld.isEmpty() && midNew.isEmpty()
                ? new ArrayList<>(0)
                : middleDiffRows(new ArrayList<>(midOld), new ArrayList<>(midNew), ignoreMode, matchCase);

        List<DiffLine> result = new ArrayList<>(prefix + suffix + midRows.size());
        for (int i = 0; i < prefix; i++) {
            result.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(i), newLines.get(i),
                    oldNumbers[i], newNumbers[i]));
        }
        int oldIndex = prefix;
        int newIndex = prefix;
        for (DiffRow row : midRows) {
            DiffRow.Tag tag = row.getTag();
            int oldNum = 0;
            int newNum = 0;
            switch (tag) {
                case EQUAL, CHANGE -> {
                    oldNum = oldNumbers[oldIndex];
                    newNum = newNumbers[newIndex];
                    oldIndex++;
                    newIndex++;
                }
                case DELETE -> {
                    oldNum = oldNumbers[oldIndex];
                    oldIndex++;
                }
                case INSERT -> {
                    newNum = newNumbers[newIndex];
                    newIndex++;
                }
            }
            result.add(new DiffLine(tag, row.getOldLine(), row.getNewLine(), oldNum, newNum));
        }
        for (int i = 0; i < suffix; i++) {
            int oldPos = oldLines.size() - suffix + i;
            int newPos = newLines.size() - suffix + i;
            result.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(oldPos), newLines.get(newPos),
                    oldNumbers[oldPos], newNumbers[newPos]));
        }
        // A row can only be EQUAL when both sides agree on the line terminator.
        for (int i = 0; i < result.size(); i++) {
            DiffLine line = result.get(i);
            if (line.tag != DiffRow.Tag.EQUAL) continue;
            boolean oldUnterminated = line.oldNumber == leftRaw.size() && !leftEndsWithNewline;
            boolean newUnterminated = line.newNumber == rightRaw.size() && !rightEndsWithNewline;
            if (oldUnterminated != newUnterminated) {
                result.set(i, new DiffLine(DiffRow.Tag.CHANGE, line.oldLine, line.newLine,
                        line.oldNumber, line.newNumber));
            }
        }
        return result;
    }

    /** Diff of the differing middle region, matching how the diff rows are consumed. */
    private static List<DiffRow> middleDiffRows(List<String> midOld, List<String> midNew,
                                                IgnoreMode ignoreMode, boolean matchCase) {
        DiffRowGenerator generator = DiffRowGenerator.create()
                .showInlineDiffs(false)
                .reportLinesUnchanged(true)
                .equalizer(equalizer(ignoreMode, matchCase))
                .build();
        return generator.generateDiffRows(midOld, midNew);
    }

    /** Keeps the lines the blank mode doesn't ignore, recording their original numbers. */
    private static List<String> significantLines(List<String> lines, int[] numbers, boolean dropBlanks) {
        List<String> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (dropBlanks && line.trim().isEmpty()) continue;
            numbers[out.size()] = i + 1;
            out.add(line);
        }
        return out;
    }

    private static BiPredicate<String, String> equalizer(IgnoreMode ignoreMode, boolean matchCase) {
        return (a, b) -> {
            if (a.equals(b)) return true;
            String x = a;
            String y = b;
            switch (ignoreMode) {
                case TRIM -> {
                    x = x.trim();
                    y = y.trim();
                }
                case WHITESPACE, WHITESPACE_EMPTY -> {
                    x = stripWhitespace(x);
                    y = stripWhitespace(y);
                }
                default -> {
                }
            }
            if (!matchCase) {
                x = x.toLowerCase(Locale.ROOT);
                y = y.toLowerCase(Locale.ROOT);
            }
            return x.equals(y);
        };
    }

    private static String stripWhitespace(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }

    // ==========================================
    // Filtering
    // ==========================================

    /** Returns the rows the given view mode shows, in order. */
    public static List<DiffLine> filter(List<DiffLine> lines, FilterMode mode) {
        List<DiffLine> out = new ArrayList<>(lines.size());
        if (mode == FilterMode.ALL) {
            out.addAll(lines);
            return out;
        }
        for (DiffLine line : lines) {
            boolean equal = line.tag == DiffRow.Tag.EQUAL;
            if (mode == FilterMode.SAME ? equal : !equal) out.add(line);
        }
        return out;
    }

    // ==========================================
    // Unified diff export
    // ==========================================

    /**
     * Renders a conventional unified diff ({@code --- / +++ / @@} hunks with
     * 3 context lines) for the given rows. {@code oldRaw} / {@code newRaw}
     * are the full unfiltered line lists the numbers in {@code lines} refer
     * to; they supply lines the row model skipped (e.g. blanks dropped by
     * {@link IgnoreMode#WHITESPACE_EMPTY}) so every hunk stays contiguous on
     * both sides, as {@code patch} and {@code git apply} require. Returns
     * {@code ""} when the rows contain no difference.
     */
    public static String toUnifiedDiff(List<DiffLine> lines, List<String> oldRaw, List<String> newRaw,
                                       String oldName, String newName,
                                       boolean oldEndsWithNewline, boolean newEndsWithNewline) {
        List<Integer> changed = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).tag != DiffRow.Tag.EQUAL) changed.add(i);
        }
        if (changed.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("--- ").append(oldName).append('\n');
        sb.append("+++ ").append(newName).append('\n');
        int i = 0;
        while (i < changed.size()) {
            int from = Math.max(0, changed.get(i) - CONTEXT);
            int to = Math.min(lines.size() - 1, changed.get(i) + CONTEXT);
            int next = i + 1;
            // Blocks whose context windows touch share one hunk (diff -U3 behavior).
            while (next < changed.size() && changed.get(next) - CONTEXT <= to + 1) {
                to = Math.min(lines.size() - 1, changed.get(next) + CONTEXT);
                next++;
            }
            emitHunk(sb, lines, from, to, oldRaw, newRaw, oldEndsWithNewline, newEndsWithNewline);
            i = next;
        }
        return sb.toString();
    }

    private static void emitHunk(StringBuilder sb, List<DiffLine> lines, int from, int to,
                                 List<String> oldRaw, List<String> newRaw,
                                 boolean oldEndsWithNewline, boolean newEndsWithNewline) {
        int minOld = Integer.MAX_VALUE;
        int maxOld = 0;
        int minNew = Integer.MAX_VALUE;
        int maxNew = 0;
        for (int i = from; i <= to; i++) {
            DiffLine line = lines.get(i);
            if (line.oldNumber > 0) {
                minOld = Math.min(minOld, line.oldNumber);
                maxOld = Math.max(maxOld, line.oldNumber);
            }
            if (line.newNumber > 0) {
                minNew = Math.min(minNew, line.newNumber);
                maxNew = Math.max(maxNew, line.newNumber);
            }
        }
        int oldStart = maxOld > 0 ? minOld : nearestNumber(lines, from, true);
        int oldCount = maxOld > 0 ? maxOld - minOld + 1 : 0;
        int newStart = maxNew > 0 ? minNew : nearestNumber(lines, from, false);
        int newCount = maxNew > 0 ? maxNew - minNew + 1 : 0;
        sb.append("@@ -").append(oldStart).append(',').append(oldCount)
                .append(" +").append(newStart).append(',').append(newCount).append(" @@\n");

        // Each side walks its own raw span. A row can only be shared context
        // when both raw texts are identical; rows the modes declared equal
        // despite differing raw text (trim / whitespace modes) and lines the
        // row model skipped (dropped blanks) become honest -/+ pairs so the
        // hunk stays applicable and the counts stay exact.
        int expectedOld = minOld;
        int expectedNew = minNew;
        for (int i = from; i <= to; i++) {
            DiffLine line = lines.get(i);
            while (line.oldNumber > 0 && line.oldNumber > expectedOld) {
                appendLine(sb, '-', oldRaw.get(expectedOld - 1));
                expectedOld++;
            }
            while (line.newNumber > 0 && line.newNumber > expectedNew) {
                appendLine(sb, '+', newRaw.get(expectedNew - 1));
                expectedNew++;
            }
            if (line.oldNumber > 0) {
                String oldText = oldRaw.get(line.oldNumber - 1);
                String newText = line.newNumber > 0 ? newRaw.get(line.newNumber - 1) : null;
                boolean oldUnterminated = line.oldNumber == oldRaw.size() && !oldEndsWithNewline;
                boolean newUnterminated = line.newNumber == newRaw.size() && !newEndsWithNewline;
                // Shared context only when the bytes match on both sides, which
                // includes agreeing on the line terminator; an EOL-only change
                // (same text, one side unterminated) needs its own -/+ pair.
                if (newText != null && newText.equals(oldText) && oldUnterminated == newUnterminated) {
                    appendLine(sb, ' ', oldText);
                    if (oldUnterminated) sb.append(NO_EOL_MARKER);
                } else {
                    appendLine(sb, '-', oldText);
                    if (oldUnterminated) sb.append(NO_EOL_MARKER);
                    if (newText != null) {
                        appendLine(sb, '+', newText);
                        if (newUnterminated) sb.append(NO_EOL_MARKER);
                    }
                }
                expectedOld = line.oldNumber + 1;
                if (line.newNumber > 0) expectedNew = line.newNumber + 1;
            }
            if (line.newNumber > 0 && line.oldNumber == 0) {
                appendLine(sb, '+', newRaw.get(line.newNumber - 1));
                if (line.newNumber == newRaw.size() && !newEndsWithNewline) sb.append(NO_EOL_MARKER);
                expectedNew = line.newNumber + 1;
            }
        }
    }

    private static void appendLine(StringBuilder sb, char marker, String text) {
        sb.append(marker).append(text).append('\n');
    }

    /**
     * The line number (old or new side) the hunk hangs off when it has no
     * lines of that side: the preceding line's number, or 0 at file start —
     * the zero-count convention {@code patch} expects.
     */
    private static int nearestNumber(List<DiffLine> lines, int from, boolean oldSide) {
        for (int i = from - 1; i >= 0; i--) {
            DiffLine line = lines.get(i);
            int number = oldSide ? line.oldNumber : line.newNumber;
            if (number > 0) return number;
        }
        return 0;
    }
}

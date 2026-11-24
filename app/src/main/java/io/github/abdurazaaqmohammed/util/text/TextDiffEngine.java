package io.github.abdurazaaqmohammed.util.text;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.github.difflib.text.DiffRow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * Above this gap size (with no common unique anchors), pair lines directly
     * instead of running quadratic Myers DP on binary/unmatchable noise.
     */
    private static final int MAX_MYERS_GAP = 4096;

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

    /**
     * Contiguous hunk mapping real 0-based line ranges between left and right
     * files without blank spacer lines, used by the dual-pane Bezier diff view.
     */
    public static final class DiffHunk {
        public final DiffRow.Tag tag;
        public final int leftStart;
        public final int leftEnd;
        public final int rightStart;
        public final int rightEnd;
        public final int firstDiffIndex;

        public DiffHunk(DiffRow.Tag tag, int leftStart, int leftEnd,
                        int rightStart, int rightEnd, int firstDiffIndex) {
            this.tag = tag;
            this.leftStart = leftStart;
            this.leftEnd = leftEnd;
            this.rightStart = rightStart;
            this.rightEnd = rightEnd;
            this.firstDiffIndex = firstDiffIndex;
        }
    }

    /** Collapses {@link DiffLine} rows into contiguous {@link DiffHunk} blocks. */
    public static List<DiffHunk> buildHunks(List<DiffLine> diffLines) {
        List<DiffHunk> hunks = new ArrayList<>();
        if (diffLines == null || diffLines.isEmpty()) return hunks;

        int leftCur = 0;
        int rightCur = 0;
        int i = 0;
        int n = diffLines.size();
        while (i < n) {
            DiffLine first = diffLines.get(i);
            boolean isEqual = first.tag == DiffRow.Tag.EQUAL;
            int leftStart = leftCur;
            int rightStart = rightCur;
            int firstDiffIdx = isEqual ? -1 : i;

            boolean hasDelete = false;
            boolean hasInsert = false;
            boolean hasChange = false;

            while (i < n) {
                DiffLine row = diffLines.get(i);
                if ((row.tag == DiffRow.Tag.EQUAL) != isEqual) break;
                if (row.oldNumber > 0) leftCur++;
                if (row.newNumber > 0) rightCur++;
                if (row.tag == DiffRow.Tag.DELETE) hasDelete = true;
                else if (row.tag == DiffRow.Tag.INSERT) hasInsert = true;
                else if (row.tag == DiffRow.Tag.CHANGE) hasChange = true;
                i++;
            }

            DiffRow.Tag hunkTag;
            if (isEqual) {
                hunkTag = DiffRow.Tag.EQUAL;
            } else if (hasChange || (hasDelete && hasInsert)) {
                hunkTag = DiffRow.Tag.CHANGE;
            } else if (hasDelete) {
                hunkTag = DiffRow.Tag.DELETE;
            } else {
                hunkTag = DiffRow.Tag.INSERT;
            }
            hunks.add(new DiffHunk(hunkTag, leftStart, leftCur, rightStart, rightCur, firstDiffIdx));
        }
        return hunks;
    }

    // ==========================================
    // Row model
    // ==========================================

    public static List<DiffLine> buildDiff(List<String> leftRaw, boolean leftEndsWithNewline,
                                           List<String> rightRaw, boolean rightEndsWithNewline,
                                           IgnoreMode ignoreMode, boolean matchCase) {
        boolean dropBlanks = ignoreMode == IgnoreMode.WHITESPACE_EMPTY;
        int[] oldNumbers = new int[leftRaw.size() + 1];
        int[] newNumbers = new int[rightRaw.size() + 1];
        List<String> oldLines = significantLines(leftRaw, oldNumbers, dropBlanks);
        List<String> newLines = significantLines(rightRaw, newNumbers, dropBlanks);

        int oldSize = oldLines.size();
        int newSize = newLines.size();

        // Fast prefix/suffix trim on raw equality first when no normalization is needed,
        // otherwise normalize once into integer IDs.
        boolean exactMode = ignoreMode == IgnoreMode.NONE && matchCase;
        int maxPrefix = Math.min(oldSize, newSize);
        int prefix = 0;
        if (exactMode) {
            while (prefix < maxPrefix && oldLines.get(prefix).equals(newLines.get(prefix))) prefix++;
        } else {
            while (prefix < maxPrefix && normalize(oldLines.get(prefix), ignoreMode, matchCase)
                    .equals(normalize(newLines.get(prefix), ignoreMode, matchCase))) {
                prefix++;
            }
        }

        int maxSuffix = maxPrefix - prefix;
        int suffix = 0;
        if (exactMode) {
            while (suffix < maxSuffix
                    && oldLines.get(oldSize - 1 - suffix).equals(newLines.get(newSize - 1 - suffix))) {
                suffix++;
            }
        } else {
            while (suffix < maxSuffix
                    && normalize(oldLines.get(oldSize - 1 - suffix), ignoreMode, matchCase)
                    .equals(normalize(newLines.get(newSize - 1 - suffix), ignoreMode, matchCase))) {
                suffix++;
            }
        }

        int midOldLen = oldSize - prefix - suffix;
        int midNewLen = newSize - prefix - suffix;

        List<DiffLine> result = new ArrayList<>(prefix + suffix + Math.max(midOldLen, midNewLen));
        for (int i = 0; i < prefix; i++) {
            result.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(i), newLines.get(i),
                    oldNumbers[i], newNumbers[i]));
        }

        if (midOldLen > 0 || midNewLen > 0) {
            appendMiddleDiff(result, oldLines, oldNumbers, prefix, prefix + midOldLen,
                    newLines, newNumbers, prefix, prefix + midNewLen, ignoreMode, matchCase);
        }

        for (int i = 0; i < suffix; i++) {
            int oldPos = oldSize - suffix + i;
            int newPos = newSize - suffix + i;
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

    private static void appendMiddleDiff(List<DiffLine> out,
                                         List<String> oldLines, int[] oldNumbers, int oldStart, int oldEnd,
                                         List<String> newLines, int[] newNumbers, int newStart, int newEnd,
                                         IgnoreMode ignoreMode, boolean matchCase) {
        int m = oldEnd - oldStart;
        int n = newEnd - newStart;
        int[] oldIds = new int[m];
        int[] newIds = new int[n];
        Map<String, Integer> pool = new HashMap<>(Math.min(m + n, 65536));
        int nextId = 1;
        for (int i = 0; i < m; i++) {
            String key = normalize(oldLines.get(oldStart + i), ignoreMode, matchCase);
            Integer id = pool.get(key);
            if (id == null) {
                id = nextId++;
                pool.put(key, id);
            }
            oldIds[i] = id;
        }
        for (int j = 0; j < n; j++) {
            String key = normalize(newLines.get(newStart + j), ignoreMode, matchCase);
            Integer id = pool.get(key);
            if (id == null) {
                id = nextId++;
                pool.put(key, id);
            }
            newIds[j] = id;
        }
        pool.clear();

        // Patience-style unique anchor splitting: find unique lines present on both sides
        // and compute their LCS in O(K log K), splitting large diffs into small fast gaps.
        List<int[]> anchors = findUniqueAnchors(oldIds, newIds, nextId);
        int curOld = 0;
        int curNew = 0;
        for (int[] anchor : anchors) {
            int aOld = anchor[0];
            int aNew = anchor[1];
            if (aOld > curOld || aNew > curNew) {
                diffGap(out, oldLines, oldNumbers, oldStart, oldIds, curOld, aOld,
                        newLines, newNumbers, newStart, newIds, curNew, aNew);
            }
            int absOld = oldStart + aOld;
            int absNew = newStart + aNew;
            out.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(absOld), newLines.get(absNew),
                    oldNumbers[absOld], newNumbers[absNew]));
            curOld = aOld + 1;
            curNew = aNew + 1;
        }
        if (m > curOld || n > curNew) {
            diffGap(out, oldLines, oldNumbers, oldStart, oldIds, curOld, m,
                    newLines, newNumbers, newStart, newIds, curNew, n);
        }
    }

    private static List<int[]> findUniqueAnchors(int[] oldIds, int[] newIds, int maxId) {
        int[] oldCount = new int[maxId];
        int[] oldPos = new int[maxId];
        for (int i = 0; i < oldIds.length; i++) {
            int id = oldIds[i];
            oldCount[id]++;
            oldPos[id] = i;
        }
        int[] newCount = new int[maxId];
        int[] newPos = new int[maxId];
        for (int j = 0; j < newIds.length; j++) {
            int id = newIds[j];
            newCount[id]++;
            newPos[id] = j;
        }

        // Collect (oldIdx, newIdx) in increasing oldIdx order for lines unique on both sides.
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < oldIds.length; i++) {
            int id = oldIds[i];
            if (oldCount[id] == 1 && newCount[id] == 1) {
                pairs.add(new int[]{i, newPos[id]});
            }
        }
        if (pairs.isEmpty()) return pairs;

        // Patience LCS on newIdx using binary search.
        int k = pairs.size();
        int[] tails = new int[k];
        int[] tailIdx = new int[k];
        int[] prev = new int[k];
        int len = 0;
        for (int i = 0; i < k; i++) {
            int val = pairs.get(i)[1];
            int lo = 0, hi = len;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (tails[mid] < val) lo = mid + 1;
                else hi = mid;
            }
            tails[lo] = val;
            tailIdx[lo] = i;
            prev[i] = lo > 0 ? tailIdx[lo - 1] : -1;
            if (lo == len) len++;
        }

        List<int[]> lcs = new ArrayList<>(len);
        int cur = tailIdx[len - 1];
        while (cur >= 0) {
            lcs.add(pairs.get(cur));
            cur = prev[cur];
        }
        java.util.Collections.reverse(lcs);
        return lcs;
    }

    private static void diffGap(List<DiffLine> out,
                                List<String> oldLines, int[] oldNumbers, int oldBase, int[] oldIds, int oFrom, int oTo,
                                List<String> newLines, int[] newNumbers, int newBase, int[] newIds, int nFrom, int nTo) {
        int oLen = oTo - oFrom;
        int nLen = nTo - nFrom;
        if (oLen == 0 && nLen == 0) return;

        if (oLen == 0) {
            for (int j = nFrom; j < nTo; j++) {
                int absNew = newBase + j;
                out.add(new DiffLine(DiffRow.Tag.INSERT, "", newLines.get(absNew), 0, newNumbers[absNew]));
            }
            return;
        }
        if (nLen == 0) {
            for (int i = oFrom; i < oTo; i++) {
                int absOld = oldBase + i;
                out.add(new DiffLine(DiffRow.Tag.DELETE, oldLines.get(absOld), "", oldNumbers[absOld], 0));
            }
            return;
        }

        // Trim common edges inside the gap as well.
        while (oFrom < oTo && nFrom < nTo && oldIds[oFrom] == newIds[nFrom]) {
            int absOld = oldBase + oFrom;
            int absNew = newBase + nFrom;
            out.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(absOld), newLines.get(absNew),
                    oldNumbers[absOld], newNumbers[absNew]));
            oFrom++;
            nFrom++;
        }
        int suffixCount = 0;
        while (oTo - suffixCount > oFrom && nTo - suffixCount > nFrom
                && oldIds[oTo - 1 - suffixCount] == newIds[nTo - 1 - suffixCount]) {
            suffixCount++;
        }
        int oMidTo = oTo - suffixCount;
        int nMidTo = nTo - suffixCount;

        int oMidLen = oMidTo - oFrom;
        int nMidLen = nMidTo - nFrom;
        if (oMidLen > 0 || nMidLen > 0) {
            if ((long) oMidLen + nMidLen > MAX_MYERS_GAP) {
                emitDirectPairedBlock(out, oldLines, oldNumbers, oldBase, oFrom, oMidTo,
                        newLines, newNumbers, newBase, nFrom, nMidTo);
            } else {
                List<Integer> subOld = new ArrayList<>(oMidLen);
                for (int i = oFrom; i < oMidTo; i++) subOld.add(oldIds[i]);
                List<Integer> subNew = new ArrayList<>(nMidLen);
                for (int j = nFrom; j < nMidTo; j++) subNew.add(newIds[j]);

                Patch<Integer> patch = DiffUtils.diff(subOld, subNew);
                int curO = 0;
                int curN = 0;
                for (AbstractDelta<Integer> delta : patch.getDeltas()) {
                    Chunk<Integer> src = delta.getSource();
                    Chunk<Integer> tgt = delta.getTarget();
                    while (curO < src.getPosition() && curN < tgt.getPosition()) {
                        int absOld = oldBase + oFrom + curO;
                        int absNew = newBase + nFrom + curN;
                        out.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(absOld), newLines.get(absNew),
                                oldNumbers[absOld], newNumbers[absNew]));
                        curO++;
                        curN++;
                    }
                    DeltaType type = delta.getType();
                    if (type == DeltaType.DELETE) {
                        for (int k = 0; k < src.size(); k++) {
                            int absOld = oldBase + oFrom + curO++;
                            out.add(new DiffLine(DiffRow.Tag.DELETE, oldLines.get(absOld), "", oldNumbers[absOld], 0));
                        }
                    } else if (type == DeltaType.INSERT) {
                        for (int k = 0; k < tgt.size(); k++) {
                            int absNew = newBase + nFrom + curN++;
                            out.add(new DiffLine(DiffRow.Tag.INSERT, "", newLines.get(absNew), 0, newNumbers[absNew]));
                        }
                    } else {
                        emitDirectPairedBlock(out, oldLines, oldNumbers, oldBase,
                                oFrom + curO, oFrom + curO + src.size(),
                                newLines, newNumbers, newBase,
                                nFrom + curN, nFrom + curN + tgt.size());
                        curO += src.size();
                        curN += tgt.size();
                    }
                }
                while (curO < oMidLen && curN < nMidLen) {
                    int absOld = oldBase + oFrom + curO++;
                    int absNew = newBase + nFrom + curN++;
                    out.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(absOld), newLines.get(absNew),
                            oldNumbers[absOld], newNumbers[absNew]));
                }
            }
        }

        for (int s = 0; s < suffixCount; s++) {
            int absOld = oldBase + oMidTo + s;
            int absNew = newBase + nMidTo + s;
            out.add(new DiffLine(DiffRow.Tag.EQUAL, oldLines.get(absOld), newLines.get(absNew),
                    oldNumbers[absOld], newNumbers[absNew]));
        }
    }

    private static void emitDirectPairedBlock(List<DiffLine> out,
                                              List<String> oldLines, int[] oldNumbers, int oldBase, int oFrom, int oTo,
                                              List<String> newLines, int[] newNumbers, int newBase, int nFrom, int nTo) {
        int oLen = oTo - oFrom;
        int nLen = nTo - nFrom;
        int common = Math.min(oLen, nLen);
        for (int k = 0; k < common; k++) {
            int absOld = oldBase + oFrom + k;
            int absNew = newBase + nFrom + k;
            out.add(new DiffLine(DiffRow.Tag.CHANGE, oldLines.get(absOld), newLines.get(absNew),
                    oldNumbers[absOld], newNumbers[absNew]));
        }
        for (int k = common; k < oLen; k++) {
            int absOld = oldBase + oFrom + k;
            out.add(new DiffLine(DiffRow.Tag.DELETE, oldLines.get(absOld), "", oldNumbers[absOld], 0));
        }
        for (int k = common; k < nLen; k++) {
            int absNew = newBase + nFrom + k;
            out.add(new DiffLine(DiffRow.Tag.INSERT, "", newLines.get(absNew), 0, newNumbers[absNew]));
        }
    }

    private static String normalize(String value, IgnoreMode ignoreMode, boolean matchCase) {
        String s = value;
        switch (ignoreMode) {
            case TRIM -> s = s.trim();
            case WHITESPACE, WHITESPACE_EMPTY -> s = stripWhitespace(s);
            default -> {}
        }
        return matchCase ? s : s.toLowerCase(Locale.ROOT);
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

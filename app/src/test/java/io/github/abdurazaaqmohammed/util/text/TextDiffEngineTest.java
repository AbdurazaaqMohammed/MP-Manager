package io.github.abdurazaaqmohammed.util.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.difflib.text.DiffRow;

/**
 * Pure coverage for the text-compare engine: row building (ignore modes,
 * numbering, prefix/suffix trimming, line-termination awareness), the show
 * filter, and conventional unified .diff export — validated by applying the
 * generated patch back to the old content with {@code patch} semantics and
 * requiring byte-exact equality with the new content.
 */
public class TextDiffEngineTest {

    private static List<String> lines(String... values) {
        return Arrays.asList(values);
    }

    private static List<TextDiffEngine.DiffLine> diff(List<String> oldLines, List<String> newLines,
                                                      TextDiffEngine.IgnoreMode mode, boolean matchCase) {
        return TextDiffEngine.buildDiff(oldLines, true, newLines, true, mode, matchCase);
    }

    private static String export(List<String> oldLines, List<String> newLines,
                                 TextDiffEngine.IgnoreMode mode, boolean matchCase, String oldName, String newName) {
        List<TextDiffEngine.DiffLine> rows = diff(oldLines, newLines, mode, matchCase);
        return TextDiffEngine.toUnifiedDiff(rows, oldLines, newLines, oldName, newName, true, true);
    }

    private static List<Integer> tags(List<TextDiffEngine.DiffLine> rows) {
        List<Integer> out = new ArrayList<>();
        for (TextDiffEngine.DiffLine row : rows) out.add(row.tag.ordinal());
        return out;
    }

    /** Rebuilds file text from readLine() output plus the trailing-newline flag. */
    private static String join(List<String> lines, boolean endsWithNewline) {
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : lines) sb.append(line).append('\n');
        String text = sb.toString();
        return endsWithNewline ? text : text.substring(0, text.length() - 1);
    }

    // ==========================================
    // Row building
    // ==========================================

    @Test
    public void identicalFiles_produceOnlyEqualRows() {
        List<TextDiffEngine.DiffLine> rows = diff(lines("a", "b", "c"), lines("a", "b", "c"),
                TextDiffEngine.IgnoreMode.NONE, true);
        assertEquals(3, rows.size());
        for (TextDiffEngine.DiffLine row : rows) {
            assertEquals(DiffRow.Tag.EQUAL, row.tag);
            assertEquals(row.oldNumber, row.newNumber);
        }
    }

    @Test
    public void simpleChange_keepsRowTextAndNumbers() {
        List<TextDiffEngine.DiffLine> rows = diff(lines("a", "b", "c"), lines("a", "X", "c"),
                TextDiffEngine.IgnoreMode.NONE, true);
        assertEquals(3, rows.size());
        assertEquals(DiffRow.Tag.EQUAL, rows.get(0).tag);
        assertEquals(1, rows.get(0).oldNumber);
        assertEquals(1, rows.get(0).newNumber);
        assertEquals(DiffRow.Tag.CHANGE, rows.get(1).tag);
        assertEquals("b", rows.get(1).oldLine);
        assertEquals("X", rows.get(1).newLine);
        assertEquals(2, rows.get(1).oldNumber);
        assertEquals(2, rows.get(1).newNumber);
        assertEquals(DiffRow.Tag.EQUAL, rows.get(2).tag);
        assertEquals(3, rows.get(2).oldNumber);
        assertEquals(3, rows.get(2).newNumber);
    }

    @Test
    public void insertAndDelete_carryNumberOnOneSideOnly() {
        // Insert: old [a, b] -> new [a, x, b]
        List<TextDiffEngine.DiffLine> rows = diff(lines("a", "b"), lines("a", "x", "b"),
                TextDiffEngine.IgnoreMode.NONE, true);
        boolean hasInsert = false;
        for (TextDiffEngine.DiffLine row : rows) {
            if (row.tag == DiffRow.Tag.INSERT) {
                hasInsert = true;
                assertEquals("x", row.newLine);
                assertEquals(2, row.newNumber);
                assertEquals(0, row.oldNumber);
            }
        }
        assertTrue(hasInsert);

        // Delete: old [a, x, b] -> new [a, b]
        rows = diff(lines("a", "x", "b"), lines("a", "b"), TextDiffEngine.IgnoreMode.NONE, true);
        boolean hasDelete = false;
        for (TextDiffEngine.DiffLine row : rows) {
            if (row.tag == DiffRow.Tag.DELETE) {
                hasDelete = true;
                assertEquals("x", row.oldLine);
                assertEquals(2, row.oldNumber);
                assertEquals(0, row.newNumber);
            }
        }
        assertTrue(hasDelete);
    }

    @Test
    public void trimMode_makesWhitespaceOnlyDifferencesEqual() {
        List<TextDiffEngine.DiffLine> strict = diff(lines("a ", " b"), lines("a", "b"),
                TextDiffEngine.IgnoreMode.NONE, true);
        assertNotEquals(Arrays.asList(DiffRow.Tag.EQUAL.ordinal(), DiffRow.Tag.EQUAL.ordinal()), tags(strict));

        List<TextDiffEngine.DiffLine> trimmed = diff(lines("a ", " b"), lines("a", "b"),
                TextDiffEngine.IgnoreMode.TRIM, true);
        assertEquals(2, trimmed.size());
        for (TextDiffEngine.DiffLine row : trimmed) assertEquals(DiffRow.Tag.EQUAL, row.tag);
    }

    @Test
    public void whitespaceMode_stripsAllWhitespaceBeforeMatching() {
        List<TextDiffEngine.DiffLine> rows = diff(lines("a b c"), lines("abc"),
                TextDiffEngine.IgnoreMode.WHITESPACE, true);
        assertEquals(1, rows.size());
        assertEquals(DiffRow.Tag.EQUAL, rows.get(0).tag);
    }

    @Test
    public void emptyLinesDropping_keepsOriginalFileNumbers() {
        // The blank line is ignored entirely but the following line keeps
        // its real file number instead of shifting up.
        List<TextDiffEngine.DiffLine> rows = diff(lines("a", "", "b"), lines("a", "b"),
                TextDiffEngine.IgnoreMode.WHITESPACE_EMPTY, true);
        assertEquals(2, rows.size());
        assertEquals(DiffRow.Tag.EQUAL, rows.get(0).tag);
        assertEquals(1, rows.get(0).oldNumber);
        assertEquals(1, rows.get(0).newNumber);
        assertEquals(DiffRow.Tag.EQUAL, rows.get(1).tag);
        assertEquals(3, rows.get(1).oldNumber);
        assertEquals(2, rows.get(1).newNumber);
    }

    @Test
    public void droppedBlanksAroundChange_doNotShiftNumbers() {
        List<TextDiffEngine.DiffLine> rows = diff(lines("a", "", "b"), lines("a", "x", "b"),
                TextDiffEngine.IgnoreMode.WHITESPACE_EMPTY, true);
        boolean foundChange = false;
        for (TextDiffEngine.DiffLine row : rows) {
            if (row.tag == DiffRow.Tag.INSERT) {
                foundChange = true;
                assertEquals(2, row.newNumber);
            }
        }
        assertTrue(foundChange);
    }

    @Test
    public void matchCaseOption_controlsCaseSensitivity() {
        List<TextDiffEngine.DiffLine> sensitive = diff(lines("Hello"), lines("hello"),
                TextDiffEngine.IgnoreMode.NONE, true);
        assertEquals(1, sensitive.size());
        assertEquals(DiffRow.Tag.CHANGE, sensitive.get(0).tag);

        List<TextDiffEngine.DiffLine> insensitive = diff(lines("Hello"), lines("hello"),
                TextDiffEngine.IgnoreMode.NONE, false);
        assertEquals(1, insensitive.size());
        assertEquals(DiffRow.Tag.EQUAL, insensitive.get(0).tag);
    }

    @Test
    public void largeSharedPrefixAndSuffix_onlyMiddleDiffers() {
        List<String> oldLines = new ArrayList<>();
        List<String> newLines = new ArrayList<>();
        for (int i = 1; i <= 500; i++) oldLines.add("head" + i);
        for (int i = 1; i <= 500; i++) newLines.add("head" + i);
        oldLines.add("middle-old");
        newLines.add("middle-new");
        for (int i = 501; i <= 1000; i++) oldLines.add("tail" + i);
        for (int i = 501; i <= 1000; i++) newLines.add("tail" + i);

        List<TextDiffEngine.DiffLine> rows = diff(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true);
        // Exactly one changed row surrounded by 1000 EQUAL rows with unbroken numbering.
        assertEquals(1001, rows.size());
        int changed = 0;
        for (int i = 0; i < rows.size(); i++) {
            TextDiffEngine.DiffLine row = rows.get(i);
            assertEquals(i + 1, row.oldNumber);
            assertEquals(i + 1, row.newNumber);
            if (row.tag != DiffRow.Tag.EQUAL) {
                changed++;
                assertEquals("middle-old", row.oldLine);
                assertEquals("middle-new", row.newLine);
            }
        }
        assertEquals(1, changed);
    }

    @Test
    public void emptyFileVersusContent_producesInsertRows() {
        List<TextDiffEngine.DiffLine> rows = TextDiffEngine.buildDiff(new ArrayList<>(), true,
                lines("a", "b"), true, TextDiffEngine.IgnoreMode.NONE, true);
        assertEquals(2, rows.size());
        for (int i = 0; i < 2; i++) {
            assertEquals(DiffRow.Tag.INSERT, rows.get(i).tag);
            assertEquals(0, rows.get(i).oldNumber);
            assertEquals(i + 1, rows.get(i).newNumber);
        }
    }

    // ==========================================
    // Line-termination awareness
    // ==========================================

    @Test
    public void terminationDifferenceOnLastLine_isReportedAsChange() {
        // Same visible text, but only the new content ends with a newline.
        List<TextDiffEngine.DiffLine> rows = TextDiffEngine.buildDiff(
                lines("a", "b"), false, lines("a", "b"), true,
                TextDiffEngine.IgnoreMode.NONE, true);
        assertEquals(2, rows.size());
        assertEquals(DiffRow.Tag.EQUAL, rows.get(0).tag);
        assertEquals(DiffRow.Tag.CHANGE, rows.get(1).tag);
    }

    @Test
    public void unterminatedLastLineContinueIsChange() {
        // Old ends at "b" without a newline, new continues after "b".
        List<TextDiffEngine.DiffLine> rows = TextDiffEngine.buildDiff(
                lines("a", "b"), false, lines("a", "b", "c"), true,
                TextDiffEngine.IgnoreMode.NONE, true);
        boolean foundChange = false;
        for (TextDiffEngine.DiffLine row : rows) {
            if (row.tag == DiffRow.Tag.CHANGE) {
                foundChange = true;
                assertEquals("b", row.oldLine);
                assertEquals("b", row.newLine);
            }
        }
        assertTrue(foundChange);
    }

    @Test
    public void identicalContentBothUnterminated_staysEqual() {
        List<TextDiffEngine.DiffLine> rows = TextDiffEngine.buildDiff(
                lines("a", "b"), false, lines("a", "b"), false,
                TextDiffEngine.IgnoreMode.NONE, true);
        assertEquals(2, rows.size());
        for (TextDiffEngine.DiffLine row : rows) assertEquals(DiffRow.Tag.EQUAL, row.tag);
    }

    // ==========================================
    // Show filter
    // ==========================================

    @Test
    public void filter_modes() {
        List<TextDiffEngine.DiffLine> rows = diff(lines("a", "b", "c", "d"), lines("a", "X", "c", "Y"),
                TextDiffEngine.IgnoreMode.NONE, true);

        List<TextDiffEngine.DiffLine> all = TextDiffEngine.filter(rows, TextDiffEngine.FilterMode.ALL);
        assertEquals(rows.size(), all.size());

        List<TextDiffEngine.DiffLine> different = TextDiffEngine.filter(rows, TextDiffEngine.FilterMode.DIFFERENT);
        assertEquals(2, different.size());
        assertNotEquals(DiffRow.Tag.EQUAL, different.get(0).tag);
        assertNotEquals(DiffRow.Tag.EQUAL, different.get(1).tag);

        List<TextDiffEngine.DiffLine> same = TextDiffEngine.filter(rows, TextDiffEngine.FilterMode.SAME);
        assertEquals(2, same.size());
        assertEquals(DiffRow.Tag.EQUAL, same.get(0).tag);
        assertEquals(DiffRow.Tag.EQUAL, same.get(1).tag);
    }

    @Test
    public void filter_onIdenticalFilesYieldsEmptyDifferencesView() {
        List<TextDiffEngine.DiffLine> rows = diff(lines("a", "b"), lines("a", "b"),
                TextDiffEngine.IgnoreMode.NONE, true);
        assertTrue(TextDiffEngine.filter(rows, TextDiffEngine.FilterMode.DIFFERENT).isEmpty());
        assertEquals(2, TextDiffEngine.filter(rows, TextDiffEngine.FilterMode.SAME).size());
    }

    // ==========================================
    // Unified diff export + patch round-trip
    // ==========================================

    private static final Pattern HUNK = Pattern.compile("@@ -(\\d+),(\\d+) \\+(\\d+),(\\d+) @@");

    private static final class Applied {
        final List<String> lines;
        final boolean endsWithNewline;

        Applied(List<String> lines, boolean endsWithNewline) {
            this.lines = lines;
            this.endsWithNewline = endsWithNewline;
        }
    }

    /**
     * Applies a unified patch the way {@code patch} does: lines outside the
     * hunks are kept, {@code oldCount == 0} hunks hang after the given old
     * line, context/deleted lines must match the old file, and the
     * {@code \ No newline at end of file} markers are honored.
     */
    private static Applied applyPatchExact(List<String> oldLines, String patch) {
        String[] patchLines = patch.split("\n", -1);
        List<String> result = new ArrayList<>();
        List<String> source = new ArrayList<>(oldLines);
        boolean newEndsWithNewline = true;
        int src = 0; // 0-based next source line to copy
        int i = 0;
        while (i < patchLines.length && (patchLines[i].startsWith("--- ") || patchLines[i].startsWith("+++ "))) i++;
        int hunks = 0;
        while (i < patchLines.length) {
            Matcher m = HUNK.matcher(patchLines[i]);
            assertTrue("bad hunk header: " + patchLines[i], m.matches());
            i++;
            hunks++;
            int oldStart = Integer.parseInt(m.group(1));
            int oldCount = Integer.parseInt(m.group(2));
            int newCount = Integer.parseInt(m.group(4));
            // Zero counts mean "insert after this old line"; otherwise hunk starts at oldStart.
            int target = oldCount == 0 ? oldStart : oldStart - 1;
            while (src < target) {
                assertTrue("hunk " + hunks + " skips past source line " + (src + 1), src < source.size());
                result.add(source.get(src++));
            }
            int consumedOld = 0;
            int producedNew = 0;
            boolean lastWasNewSide = false;
            while (consumedOld < oldCount || producedNew < newCount) {
                assertTrue("hunk " + hunks + " ran out of body", i < patchLines.length);
                String body = patchLines[i++];
                if (body.startsWith("\\")) {
                    if (lastWasNewSide) newEndsWithNewline = false;
                    continue;
                }
                char marker = body.isEmpty() ? ' ' : body.charAt(0);
                String text = body.isEmpty() ? "" : body.substring(1);
                lastWasNewSide = false;
                if (marker == ' ') {
                    assertTrue("context mismatch at old line " + (src + 1), src < source.size());
                    assertEquals(source.get(src), text);
                    result.add(text);
                    src++;
                    consumedOld++;
                    producedNew++;
                    lastWasNewSide = true;
                } else if (marker == '-') {
                    assertTrue("delete mismatch at old line " + (src + 1), src < source.size());
                    assertEquals(source.get(src), text);
                    src++;
                    consumedOld++;
                } else if (marker == '+') {
                    result.add(text);
                    producedNew++;
                    lastWasNewSide = true;
                } else {
                    throw new AssertionError("bad patch body: " + body);
                }
            }
            assertTrue("hunk " + hunks + " emitted wrong counts", consumedOld == oldCount && producedNew == newCount);
            while (i < patchLines.length && patchLines[i].startsWith("\\")) {
                if (lastWasNewSide) newEndsWithNewline = false;
                i++;
            }
            if (i == patchLines.length - 1 && patchLines[i].isEmpty()) i++;
        }
        while (src < source.size()) result.add(source.get(src++));
        return new Applied(result, newEndsWithNewline);
    }

    private static List<String> applyPatch(List<String> oldLines, String patch) {
        return applyPatchExact(oldLines, patch).lines;
    }

    /** Exports a diff between the exact file texts and requires a byte-equal round trip. */
    private static void assertByteExactRoundTrip(String oldText, String newText,
                                                 TextDiffEngine.IgnoreMode mode, boolean matchCase) {
        List<String> oldLines = splitLines(oldText);
        List<String> newLines = splitLines(newText);
        List<TextDiffEngine.DiffLine> rows = TextDiffEngine.buildDiff(oldLines,
                oldText.endsWith("\n") || oldText.isEmpty(),
                newLines, newText.endsWith("\n") || newText.isEmpty(), mode, matchCase);
        String patch = TextDiffEngine.toUnifiedDiff(rows, oldLines, newLines, "old", "new",
                oldText.endsWith("\n") || oldText.isEmpty(),
                newText.endsWith("\n") || newText.isEmpty());
        Applied applied = applyPatchExact(oldLines, patch);
        assertEquals("round trip bytes", newText, join(applied.lines, applied.endsWithNewline));
    }

    private static List<String> splitLines(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                out.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length()) out.add(text.substring(start));
        return out;
    }

    @Test
    public void unifiedDiff_hasConventionalHeaderAndRoundTrips() {
        List<String> oldLines = lines("alpha", "bravo", "charlie", "delta", "echo");
        List<String> newLines = lines("alpha", "BRAVO!", "charlie", "delta", "echo", "foxtrot");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "a/old.txt", "b/new.txt");

        assertTrue(patch.startsWith("--- a/old.txt\n+++ b/new.txt\n"));
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_mixedChangesRoundTrip() {
        List<String> oldLines = lines(
                "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen");
        List<String> newLines = lines(
                "one", "two-changed", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "inserted", "twelve", "thirteen", "fourteen", "fifteen");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_trimMode_rawDifferingEqualRowsAreHonestChanges() {
        // All rows EQUAL under TRIM, so nothing to export.
        List<String> oldLines = lines("alpha ", "bravo", "  charlie", "delta");
        List<String> newLines = lines("alpha", "bravo  ", "charlie", "delta ");
        assertTrue(export(oldLines, newLines, TextDiffEngine.IgnoreMode.TRIM, true, "old", "new").isEmpty());

        // Same mode but real changes too: the raw-differing EQUAL rows must be
        // exported as -/+ pairs so the patch reproduces the actual new bytes.
        assertByteExactRoundTrip("alpha \nbravo\nchanged-old\ndelta\n",
                "alpha\nbravo  \nchanged new\ndelta \n",
                TextDiffEngine.IgnoreMode.TRIM, true);
    }

    @Test
    public void unifiedDiff_whitespaceEmptyMode_droppedBlanksAreRecorded() {
        // Blanks ignored for matching still exist in the files, so they are
        // recorded as real deletions/insertions and the patch round-trips to
        // the actual new content.
        List<String> oldLines = lines("one", "", "two", "three", "", "four", "five");
        List<String> newLines = lines("one", "two", "three", "four!", "five");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.WHITESPACE_EMPTY, true, "old", "new");
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_pureInsertAtStart_keepsOldContextSpan() {
        List<String> oldLines = lines("b", "c");
        List<String> newLines = lines("a", "b", "c");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertTrue(patch, patch.contains("@@ -1,2 +1,3 @@"));
        assertTrue(patch, patch.startsWith("--- old\n+++ new\n"));
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_pureInsertInside_roundTripsWithContext() {
        List<String> oldLines = lines("a", "b", "c");
        List<String> newLines = lines("a", "x", "y", "b", "c");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertTrue(patch, patch.contains("@@ -1,3 +1,5 @@"));
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_pureDelete_roundTrips() {
        List<String> oldLines = lines("a", "b", "c");
        List<String> newLines = lines("a");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_deleteFirstLine_roundTrips() {
        List<String> oldLines = lines("a", "b", "c");
        List<String> newLines = lines("b", "c");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertTrue(patch, patch.contains("@@ -1,3 +1,2 @@"));
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_identicalFiles_isEmpty() {
        String patch = export(lines("a", "b"), lines("a", "b"), TextDiffEngine.IgnoreMode.NONE, true, "o", "n");
        assertEquals("", patch);
    }

    @Test
    public void unifiedDiff_identicalUnderIgnoreMode_isEmpty() {
        String patch = export(lines("a ", "b"), lines("a", "b "), TextDiffEngine.IgnoreMode.TRIM, true, "o", "n");
        assertEquals("", patch);
    }

    @Test
    public void unifiedDiff_emptyOldFile_zeroOldCountAndRoundTrip() {
        List<String> oldLines = new ArrayList<>();
        List<String> newLines = lines("a", "b");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertTrue(patch, patch.contains("@@ -0,0 +1,2 @@"));
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_emptyNewFile_zeroNewCountAndRoundTrip() {
        List<String> oldLines = lines("a", "b");
        List<String> newLines = new ArrayList<>();
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertTrue(patch, patch.contains("@@ -1,2 +0,0 @@"));
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_farApartChanges_makeSeparateHunks() {
        List<String> oldLines = new ArrayList<>();
        List<String> newLines = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            oldLines.add("line" + i);
            newLines.add("line" + i);
        }
        oldLines.set(4, "changed5");
        newLines.set(4, "CHANGED5");
        oldLines.set(19, "changed20");
        newLines.set(19, "CHANGED20");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        int headers = 0;
        for (String line : patch.split("\n")) {
            if (line.startsWith("@@ ")) headers++;
        }
        assertEquals(2, headers);
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_closeChanges_mergeIntoOneHunk() {
        List<String> oldLines = new ArrayList<>();
        List<String> newLines = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            oldLines.add("line" + i);
            newLines.add("line" + i);
        }
        oldLines.set(4, "changed5");
        newLines.set(4, "CHANGED5");
        oldLines.set(9, "changed10");
        newLines.set(9, "CHANGED10");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        int headers = 0;
        for (String line : patch.split("\n")) {
            if (line.startsWith("@@ ")) headers++;
        }
        assertEquals(1, headers);
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_hunkHeaders_matchEmittedCounts() {
        List<String> oldLines = lines(
                "alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel",
                "india", "juliet", "kilo", "lima", "mike", "november", "oscar", "papa");
        List<String> newLines = lines(
                "alpha", "bravo", "CHARLIE", "delta", "echo", "foxtrot", "golf", "hotel",
                "india", "juliet", "kilo", "LIMA", "mike", "november", "oscar", "papa",
                "quebec");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");

        String[] patchLines = patch.split("\n");
        int i = 2; // skip --- and +++
        while (i < patchLines.length) {
            Matcher m = HUNK.matcher(patchLines[i]);
            assertTrue("bad header: " + patchLines[i], m.matches());
            int oldCount = Integer.parseInt(m.group(2));
            int newCount = Integer.parseInt(m.group(4));
            int countedOld = 0;
            int countedNew = 0;
            i++;
            while (countedOld < oldCount || countedNew < newCount) {
                assertTrue("ran out of hunk body", i < patchLines.length);
                String body = patchLines[i++];
                if (body.startsWith("\\")) continue; // no-newline marker adds no line
                char marker = body.charAt(0);
                if (marker == ' ') {
                    countedOld++;
                    countedNew++;
                } else if (marker == '-') {
                    countedOld++;
                } else if (marker == '+') {
                    countedNew++;
                } else {
                    throw new AssertionError("bad body: " + body);
                }
            }
        }
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_emptyLinesInContent_roundTrip() {
        List<String> oldLines = lines("", "a", "", "b", "");
        List<String> newLines = lines("", "a", "x", "b", "");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    @Test
    public void unifiedDiff_changedPlusDeletedPlusInserted_roundTrips() {
        List<String> oldLines = lines("keep1", "del1", "del2", "keep2", "chg-old", "keep3");
        List<String> newLines = lines("keep1", "keep2", "chg-new", "keep3", "ins1", "ins2");
        String patch = export(oldLines, newLines, TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertEquals(newLines, applyPatch(oldLines, patch));
    }

    // ==========================================
    // No-newline marker + byte-exact round trips
    // ==========================================

    @Test
    public void noNewlineOnBothSides_changeAtEnd_marksBothSidesAndRoundTrips() {
        List<String> oldLines = lines("a", "b");
        List<String> newLines = lines("a", "B");
        String patch = TextDiffEngine.toUnifiedDiff(TextDiffEngine.buildDiff(oldLines, false,
                newLines, false, TextDiffEngine.IgnoreMode.NONE, true), oldLines, newLines,
                "old", "new", false, false);
        assertTrue(patch, patch.contains("-b\n\\ No newline at end of file\n"));
        assertTrue(patch, patch.contains("+B\n\\ No newline at end of file\n"));
        assertByteExactRoundTrip("a\nb", "a\nB", TextDiffEngine.IgnoreMode.NONE, true);
    }

    @Test
    public void noNewlineOldOnly_markerOnOldSideAndRoundTrips() {
        assertByteExactRoundTrip("a\nb", "a\nb\nc\n", TextDiffEngine.IgnoreMode.NONE, true);
    }

    @Test
    public void noNewlineNewOnly_markerOnNewSideAndRoundTrips() {
        assertByteExactRoundTrip("a\nb\n", "a\nb", TextDiffEngine.IgnoreMode.NONE, true);
    }

    @Test
    public void noNewlineOnBothSides_contextAtEndGetsSingleMarker() {
        // The change is in the middle; the shared final line is context and
        // carries exactly one no-newline marker.
        List<String> oldLines = lines("x", "y", "a", "b");
        List<String> newLines = lines("x", "z", "a", "b");
        String patch = TextDiffEngine.toUnifiedDiff(TextDiffEngine.buildDiff(oldLines, false,
                newLines, false, TextDiffEngine.IgnoreMode.NONE, true), oldLines, newLines,
                "old", "new", false, false);
        assertTrue(patch, patch.contains(" b\n\\ No newline at end of file\n"));
        assertEquals(1, patch.split("\\\\ No newline", -1).length - 1);
        assertByteExactRoundTrip("x\ny\na\nb", "x\nz\na\nb", TextDiffEngine.IgnoreMode.NONE, true);
    }

    @Test
    public void terminatedFiles_emitNoMarker() {
        String patch = export(lines("a", "b"), lines("a", "B"), TextDiffEngine.IgnoreMode.NONE, true, "old", "new");
        assertFalse(patch.contains("No newline"));
        assertByteExactRoundTrip("a\nb\n", "a\nB\n", TextDiffEngine.IgnoreMode.NONE, true);
    }

    @Test
    public void byteExactRoundTrip_mixedScenario() {
        assertByteExactRoundTrip(
                "one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\neleven\ntwelve\nthirteen\nfourteen\nfifteen\nsixteen\n",
                "one\ntwo-changed\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten\neleven\ninserted\ntwelve\nthirteen\nfourteen\nfifteen",
                TextDiffEngine.IgnoreMode.NONE, true);
    }

    private static void assertFalse(boolean condition) {
        org.junit.Assert.assertFalse(condition);
    }
}

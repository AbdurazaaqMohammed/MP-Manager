package io.github.abdurazaaqmohammed.plugins.api.ocr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Coordinates always refer to the input bitmap, not a detector's resized tensor. */
public final class OcrResult {
    public record Box(float left, float top, float right, float bottom) {
        public float width() { return right - left; }
        public float height() { return bottom - top; }
        public float centerY() { return (top + bottom) / 2f; }
        public float centerX() { return (left + right) / 2f; }
        public boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
        public Box offset(float x, float y) {
            return new Box(left + x, top + y, right + x, bottom + y);
        }
    }

    public record Word(String text, float confidence, Box box) {}
    public record Line(String text, float confidence, Box box, List<Word> words) {
        public Line {
            words = Collections.unmodifiableList(new ArrayList<>(words));
        }
    }

    public final List<Line> lines;
    public final long elapsedMillis;

    public OcrResult(List<Line> lines, long elapsedMillis) {
        this.lines = Collections.unmodifiableList(readingOrder(lines));
        this.elapsedMillis = elapsedMillis;
    }

    public String text() {
        StringBuilder text = new StringBuilder();
        Line previous = null;
        for (Line line : lines) {
            if (text.length() > 0) {
                float overlap = previous == null ? 0 : Math.min(previous.box.bottom, line.box.bottom)
                        - Math.max(previous.box.top, line.box.top);
                boolean sameRow = previous != null && overlap > Math.min(previous.box.height(), line.box.height()) * .5f;
                text.append(sameRow ? ' ' : '\n');
            }
            text.append(line.text);
            previous = line;
        }
        return text.toString();
    }

    public OcrResult offset(float x, float y) {
        List<Line> shifted = new ArrayList<>();
        for (Line line : lines) {
            List<Word> words = new ArrayList<>();
            for (Word word : line.words) words.add(new Word(word.text, word.confidence, word.box.offset(x, y)));
            shifted.add(new Line(line.text, line.confidence, line.box.offset(x, y), words));
        }
        return new OcrResult(shifted, elapsedMillis);
    }

    /** Row grouping avoids non-transitive y/x comparators on uneven text baselines. */
    private static List<Line> readingOrder(List<Line> source) {
        List<Line> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparingDouble(line -> line.box.top));
        List<List<Line>> rows = new ArrayList<>();
        for (Line line : ordered) {
            List<Line> row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            Line anchor = row == null ? null : row.get(0);
            float overlap = anchor == null ? 0 : Math.min(anchor.box.bottom, line.box.bottom)
                    - Math.max(anchor.box.top, line.box.top);
            if (anchor == null || overlap < Math.min(anchor.box.height(), line.box.height()) * .5f) {
                row = new ArrayList<>();
                rows.add(row);
            }
            row.add(line);
        }
        ordered.clear();
        for (List<Line> row : rows) {
            boolean rtl = !row.isEmpty() && isRightToLeft(row.get(0).text);
            row.sort(rtl ? Comparator.comparingDouble((Line line) -> line.box.left).reversed()
                    : Comparator.comparingDouble(line -> line.box.left));
            ordered.addAll(row);
        }
        return ordered;
    }

    public static boolean isRightToLeft(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            byte direction = Character.getDirectionality(codePoint);
            if (direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                    || direction == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC) return true;
            if (direction == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false;
            offset += Character.charCount(codePoint);
        }
        return false;
    }
}

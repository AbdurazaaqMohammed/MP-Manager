package io.github.abdurazaaqmohammed.plugins.api.ocr;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class OcrResultTest {
    @Test public void readingOrderGroupsUnevenBaselinesWithoutComparatorViolations() {
        OcrResult result = new OcrResult(List.of(line("third", 0, 60), line("second", 50, 11),
                line("first", 0, 10)), 100);
        assertEquals("first second\nthird", result.text());
    }
    @Test public void offsetMovesWordAndLineGeometryTogether() {
        OcrResult result = new OcrResult(List.of(line("hello", 2, 3)), 12).offset(10, 20);
        assertEquals(12, result.lines.get(0).box().left(), 0);
        assertEquals(23, result.lines.get(0).words().get(0).box().top(), 0);
        assertEquals(12, result.elapsedMillis);
    }
    @Test public void recognizesArabicDirection() {
        assertTrue(OcrResult.isRightToLeft("مرحبا بالعالم"));
        assertFalse(OcrResult.isRightToLeft("Hello world"));
    }
    private OcrResult.Line line(String text, float x, float y) {
        OcrResult.Box box = new OcrResult.Box(x, y, x + 40, y + 20);
        return new OcrResult.Line(text, .9f, box, List.of(new OcrResult.Word(text, .9f, box)));
    }
}

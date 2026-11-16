package io.github.abdurazaaqmohammed.packs.ocr;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.*;

public class DbDetectorTest {
    @Test public void scalesUnclippedBoxesToOriginalImage() {
        float[] map = new float[100 * 80];
        for (int y = 20; y < 30; y++) for (int x = 10; x < 60; x++) map[y * 100 + x] = .95f;
        List<DbDetector.Quad> boxes = DbDetector.boxes(map, 100, 80, 200, 160, () -> false);
        assertEquals(1, boxes.size());
        assertTrue(boxes.get(0).topLeft().x() < 20);
        assertTrue(boxes.get(0).bottomRight().x() > 118);
        assertTrue(boxes.get(0).confidence() > .7f);
    }
    @Test public void rejectsLowConfidenceNoiseAndBlankScreen() {
        float[] map = new float[80 * 80];
        assertTrue(DbDetector.boxes(map, 80, 80, 80, 80, () -> false).isEmpty());
        for (int y = 10; y < 20; y++) for (int x = 10; x < 60; x++) map[y * 80 + x] = .35f;
        assertTrue(DbDetector.boxes(map, 80, 80, 80, 80, () -> false).isEmpty());
    }
    @Test(expected = CancellationException.class) public void cancellationStopsPostprocessing() {
        float[] map = new float[100];
        Arrays.fill(map, 1f);
        DbDetector.boxes(map, 10, 10, 10, 10, () -> true);
    }
}

package io.github.abdurazaaqmohammed.packs.ocr;

import org.junit.Test;
import java.nio.FloatBuffer;
import static org.junit.Assert.*;

public class CtcDecoderTest {
    @Test public void blanksSeparateRepeatedLettersAndSpaces() {
        CtcDecoder decoder = new CtcDecoder("a\nb\n");
        int[] indices = {1, 1, 0, 1, 3, 2, 2, 0};
        FloatBuffer output = FloatBuffer.allocate(indices.length * 4);
        for (int step = 0; step < indices.length; step++) output.put(step * 4 + indices[step], .95f);
        CtcDecoder.Decoded decoded = decoder.decode(output, indices.length, 4);
        assertEquals("aa b", decoded.text());
        assertEquals(4, decoded.tokens().size());
        assertEquals(2, decoded.tokens().get(0).end());
        assertEquals(.95f, decoded.confidence(), .001f);
    }
    @Test(expected = IllegalArgumentException.class) public void refusesMismatchedDictionary() {
        new CtcDecoder("a\nb").decode(FloatBuffer.allocate(5), 1, 5);
    }
    @Test public void keepsSupplementaryUnicodeCharacters() {
        CtcDecoder.Decoded decoded = new CtcDecoder("𝑢\n中\n")
                .decode(FloatBuffer.wrap(new float[]{0, 1, 0, 0, 0, 0, 1, 0}), 2, 4);
        assertEquals("𝑢中", decoded.text());
    }
}

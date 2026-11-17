package io.github.abdurazaaqmohammed.packs.ocr;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Greedy CTC decoding, retaining time spans for word-level screen selection. */
final class CtcDecoder {
    record Token(String text, float confidence, int start, int end) {}
    record Decoded(String text, float confidence, List<Token> tokens) {}
    private final String[] characters;

    CtcDecoder(String metadata) {
        if (metadata == null || metadata.isEmpty()) throw new IllegalArgumentException("Model dictionary is missing");
        List<String> dictionary = new ArrayList<>(Arrays.asList(metadata.split("\\r?\\n")));
        dictionary.add(0, ""); // CTC blank
        dictionary.add(" ");
        characters = dictionary.toArray(new String[0]);
    }

    Decoded decode(FloatBuffer predictions, int steps, int classes) {
        if (classes != characters.length || (long) steps * classes > predictions.remaining()) {
            throw new IllegalArgumentException("Recognition output does not match the model dictionary");
        }
        List<Token> tokens = new ArrayList<>();
        int previous = -1;
        for (int time = 0; time < steps; time++) {
            int best = 0;
            float probability = predictions.get(time * classes);
            for (int index = 1; index < classes; index++) {
                float value = predictions.get(time * classes + index);
                if (value > probability) {
                    probability = value;
                    best = index;
                }
            }
            if (best != 0) {
                if (best == previous && !tokens.isEmpty()) {
                    Token token = tokens.remove(tokens.size() - 1);
                    tokens.add(new Token(token.text, Math.max(token.confidence, probability), token.start, time + 1));
                } else {
                    tokens.add(new Token(characters[best], probability, time, time + 1));
                }
            }
            previous = best;
        }
        StringBuilder text = new StringBuilder();
        float confidence = 0;
        for (Token token : tokens) {
            text.append(token.text);
            confidence += token.confidence;
        }
        return new Decoded(text.toString().trim(), tokens.isEmpty() ? 0 : confidence / tokens.size(), tokens);
    }
}

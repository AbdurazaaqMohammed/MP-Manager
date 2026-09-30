package io.github.abdurazaaqmohammed.domain.text;

import java.util.Random;

/**
 * Lorem ipsum generator extracted from ToolRunnerActivity.
 */
public final class Lorem {

    private Lorem() {
    }

    public static final String[] WORDS = {"lorem", "ipsum", "dolor", "sit", "amet",
            "consectetur", "adipiscing", "elit", "sed", "do", "eiusmod", "tempor",
            "incididunt", "ut", "labore", "et", "dolore", "magna", "aliqua", "enim",
            "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco",
            "laboris", "nisi", "aliquip", "commodo", "consequat", "duis", "aute",
            "irure", "fugiat", "nulla", "pariatur", "excepteur", "sint", "occaecat",
            "cupidatat", "proident", "sunt", "culpa", "qui", "officia", "deserunt",
            "mollit", "anim", "est", "laborum"};

    public static String generate(int paragraphs, Random random) {
        StringBuilder b = new StringBuilder();
        for (int p = 0; p < paragraphs; p++) {
            int sentences = 3 + random.nextInt(3);
            for (int s = 0; s < sentences; s++) {
                int len = 5 + random.nextInt(8);
                for (int w = 0; w < len; w++) {
                    String word = WORDS[random.nextInt(WORDS.length)];
                    if (w == 0) {
                        word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
                    }
                    b.append(word);
                    b.append(w == len - 1 ? ". " : " ");
                }
            }
            if (p < paragraphs - 1) {
                b.append("\n\n");
            }
        }
        return b.toString().trim();
    }
}

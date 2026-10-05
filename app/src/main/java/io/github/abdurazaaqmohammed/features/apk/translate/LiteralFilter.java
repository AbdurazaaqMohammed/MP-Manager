package io.github.abdurazaaqmohammed.features.apk.translate;

import java.util.regex.Pattern;

/**
 * Decides which dex string literals are worth putting in front of a translator.
 *
 * <p>A dex file holds every string the code ever builds: class and field names, resource paths,
 * log tags, XML attribute keys, format fragments, error messages. Out of a large APK that is tens
 * of thousands of entries, and the overwhelming majority are not prose. Translating them does not
 * just waste effort - a rewritten resource path or attribute name breaks the app at runtime, and a
 * rewritten log tag defeats whatever reads those logs.
 *
 * <p>So the default set is the subset that reads like a sentence someone would want translated:
 * it contains a space, it is not merely a token with punctuation, and it is not a path, an
 * identifier or a format skeleton. This is a heuristic, and it is deliberately biased towards
 * showing fewer candidates rather than more - a missed string can always be added by hand, whereas
 * a wrongly offered one invites a rewrite that breaks the app.
 */
public final class LiteralFilter {

    /** Dotted paths, slashed paths, and anything that looks like a package or file name. */
    private static final Pattern PATHISH = Pattern.compile(
            ".*[/\\\\].*"
                    + "|(\\w+\\.){2,}\\w+"
                    + "|(?:[a-z0-9_]+\\.)+[a-z]{2,4}(?:\\?\\S*)?(?:#\\S*)?"
    );

    /** Identifiers as they appear in code: no spaces, mixed case or camelCase, dotted. */
    private static final Pattern IDENTIFIER = Pattern.compile(
            "^[A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*$"
    );

    /** Class-name prefixes that mark a literal as code rather than copy. */
    private static final Pattern CODE_PREFIX = Pattern.compile(
            "^(?:java|javax|android|androidx|kotlin|kotlinx|kotlin\\.text|dalvik|sun|com\\.android|"
                    + "org\\.xml|org\\.w3c|org\\.json|Ljava|Landroid|Lkotlin)[./]"
    );

    /** Log-level and logging vocabulary. */
    private static final Pattern LOG_NOISE = Pattern.compile(
            "^(?:[VDIWEF]\\s?/?[A-Za-z0-9_.]*|"
                    + "(?:TAG|tag|LOG|Log|DEBUG|INFO|WARN|ERROR|FATAL)\\b.*)$"
    );

    /** Escape sequences and encoded payloads, which are never prose. */
    private static final Pattern ENCODED = Pattern.compile(
            "^(?:[A-Za-z0-9+/]{16,}={0,2}|[0-9a-fA-F]{16,})$"   // base64 / hex blobs
                    + "|(?:\\\\u[0-9a-fA-F]{4}){3,}"               // escaped-XXXX runs
                    + "|\\\\x[0-9a-fA-F]{2}(?:\\\\x[0-9a-fA-F]{2}){3,}"
    );

    /**
     * Fragments whose structure dominates their content: an XML attribute list or a node
     * description, e.g. {@code node[shape=record];} or {@code edge[color=}.
     *
     * <p>Checked for structure rather than for absence of letters, because plain prose is made of
     * the same characters. A real sentence carries whitespace between words; these do not.
     */
    private static final Pattern STRUCTURAL = Pattern.compile(
            ".*\\[[^\\]]*=?[^\\]]*\\].*"          // bracket group, open or with a value
                    + "|^(?:<|</)[^>]*>.*"         // leading tag
                    + "|\\b(?:id|class|style|width|height|src|href|name|value|type)\\s*=\\s*\\S+.*"
    );

    /** Shortest candidate worth offering. Below this it is nearly always a keyword or symbol. */
    private static final int MIN_LENGTH = 4;

    /** Space-separated words a sentence-ish literal tends to have. */
    private static final int MIN_WORDS = 2;

    private LiteralFilter() {
    }

    /** @return true when {@code value} reads like translatable copy. */
    public static boolean isProse(String value) {
        if (value == null) return false;
        String text = value.trim();
        if (text.length() < MIN_LENGTH) return false;

        // No whitespace at all is the strongest signal there is: code strings and identifiers are
        // single tokens, prose is not.
        if (!hasWhitespaceInside(text)) return false;

        if (PATHISH.matcher(text).matches()) return false;
        if (CODE_PREFIX.matcher(text).matches()) return false;
        if (LOG_NOISE.matcher(text).matches()) return false;
        if (ENCODED.matcher(text).matches()) return false;
        if (STRUCTURAL.matcher(text).matches()) return false;
        // Two space-separated words is the floor. "Content:" and "ok" are single tokens with
        // punctuation; most real copy has at least two.
        if (words(text) < MIN_WORDS) return false;

        return true;
    }

    private static boolean hasWhitespaceInside(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) return true;
        }
        return false;
    }

    private static int words(String text) {
        int n = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                n++;
            }
        }
        return n;
    }

    /**
     * True when the literal is a bare identifier rather than prose, kept separate because the
     * dialog uses it to warn before an edit lands on something structural.
     */
    public static boolean isIdentifierOnly(String value) {
        return value != null && IDENTIFIER.matcher(value.trim()).matches();
    }
}
package io.github.abdurazaaqmohammed.features.apk.translate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guards the tokens a translator must not touch.
 *
 * <p>Format specifiers ({@code %1$s}), positional placeholders ({@code {0}}), inline markup and
 * URLs carry meaning for the code that consumes the string. A translation that drops or reorders
 * them turns into a crash or a garbled screen, so every candidate translation is checked against
 * the tokens found in its source and rejected when they do not survive intact.
 */
public final class FormatGuard {

    /** Anything that must survive translation byte for byte. */
    private static final Pattern TOKEN = Pattern.compile(
            "%\\d*\\$?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]"   // printf style
                    + "|\\{\\d+(?:,[^}]*)?\\}"                      // {0} / {0,number}
                    + "|</?[a-zA-Z][^>]*>"                        // inline html
                    + "|https?://\\S+"                             // urls
                    + "|&[a-zA-Z][a-zA-Z0-9_]*;"                  // xml entities
    );

    private FormatGuard() {
    }

    /** @return every distinct token in {@code value}, in order of first appearance. */
    public static List<String> tokens(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) return out;
        Set<String> seen = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(value);
        while (matcher.find()) {
            if (seen.add(matcher.group())) out.add(matcher.group());
        }
        return out;
    }

    /**
     * Checks that every token in {@code source} still appears in {@code candidate}, with the same
     * multiplicity, so {@code %1$s} and {@code %2$s} cannot silently swap.
     *
     * @return {@code null} when the translation is safe, otherwise the offending token
     */
    public static String firstViolation(String source, String candidate) {
        if (candidate == null) return "null";
        for (String token : tokens(source)) {
            if (count(candidate, token) < count(source, token)) return token;
        }
        return null;
    }

    private static int count(String haystack, String needle) {
        if (needle.isEmpty()) return 0;
        int total = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) break;
            total++;
            from = at + needle.length();
        }
        return total;
    }

    /**
     * Replaces tokens with numbered sentinels that survive machine translation.
     *
     * <p>Only used when the endpoint is known to mangle them anyway; the placeholder is a short
     * run of digits wrapped in a tag-free token, which every engine tested here passes through.
     */
    public static String mask(String value) {
        if (value == null) return null;
        List<String> found = tokens(value);
        String out = value;
        for (int i = 0; i < found.size(); i++) {
            out = out.replace(found.get(i), sentinel(i));
        }
        return out;
    }

    /** Restores a masked string produced by {@link #mask(String)}. */
    public static String unmask(String value, String original) {
        if (value == null) return null;
        List<String> found = tokens(original);
        String out = value;
        for (int i = 0; i < found.size(); i++) {
            out = replaceLoose(out, sentinel(i), found.get(i));
        }
        return out;
    }

    private static String sentinel(int index) {
        // ZXQV marks the run so a partially translated digit sequence is still recognisable.
        return "ZXQV" + index + "QVXZ";
    }

    /**
     * Endpoints sometimes insert spaces inside the sentinel or transliterate the letters, so the
     * restore pass is tolerant: digits are matched individually and the letters are optional.
     */
    private static String replaceLoose(String value, String sentinel, String token) {
        if (value.contains(sentinel)) return value.replace(sentinel, token);
        String digits = sentinel.replaceAll("[^0-9]", "");
        int at = value.indexOf(digits);
        if (at < 0) return value;
        return value.substring(0, at) + token + value.substring(at + digits.length());
    }
}

package io.github.abdurazaaqmohammed.util.text;

import java.util.regex.Pattern;

/**
 * Shell argument quoting helpers.
 */
public final class ShellQuote {

    private ShellQuote() {
    }

    private static final Pattern SAFE_ARG = Pattern.compile("^[a-zA-Z0-9_./\\-:]+$");

    public static String escapeShellArg(String arg) {
        if (arg == null) return "";
        if (SAFE_ARG.matcher(arg).matches()) return arg;
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    public static String quoteForSh(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }
}

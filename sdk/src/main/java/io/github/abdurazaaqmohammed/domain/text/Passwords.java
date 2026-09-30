package io.github.abdurazaaqmohammed.domain.text;

import java.security.SecureRandom;
import java.text.DecimalFormat;

/**
 * Password generation + strength estimation extracted from ToolRunnerActivity.
 */
public final class Passwords {

    private Passwords() {
    }

    public static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    public static final String DIGITS = "0123456789";
    public static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?";

    public static String generate(int len, boolean upper, boolean lower, boolean digit, boolean symbol) {
        StringBuilder pool = new StringBuilder();
        if (upper) pool.append(UPPER);
        if (lower) pool.append(LOWER);
        if (digit) pool.append(DIGITS);
        if (symbol) pool.append(SYMBOLS);
        if (pool.length() == 0) throw new IllegalArgumentException("empty pool");
        SecureRandom random = new SecureRandom();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < len; i++) {
            out.append(pool.charAt(random.nextInt(pool.length())));
        }
        return out.toString();
    }

    public static double entropyBits(String password) {
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else {
                symbol = true;
            }
        }
        int pool = (lower ? 26 : 0) + (upper ? 26 : 0) + (digit ? 10 : 0) + (symbol ? 33 : 0);
        if (pool == 0) return 0;
        return password.length() * (Math.log(pool) / Math.log(2));
    }

    public static String strengthLabel(double entropy) {
        if (entropy < 28) {
            return "Very weak";
        } else if (entropy < 36) {
            return "Weak";
        } else if (entropy < 60) {
            return "Fair";
        } else if (entropy < 80) {
            return "Strong";
        } else {
            return "Excellent";
        }
    }

    public static String guessesToTime(double guesses) {
        double perSecond = 10000000000.0;
        double seconds = guesses / perSecond;
        if (seconds < 1) {
            return "under a second";
        } else if (seconds < 60) {
            return new DecimalFormat("0").format(seconds) + " seconds";
        } else if (seconds < 3600) {
            return new DecimalFormat("0").format(seconds / 60) + " minutes";
        } else if (seconds < 86400) {
            return new DecimalFormat("0").format(seconds / 3600) + " hours";
        } else if (seconds < 31536000) {
            return new DecimalFormat("0").format(seconds / 86400) + " days";
        } else if (seconds < 3153600000L) {
            return new DecimalFormat("0").format(seconds / 31536000) + " years";
        }
        return "centuries";
    }
}

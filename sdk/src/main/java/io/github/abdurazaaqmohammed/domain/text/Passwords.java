package io.github.abdurazaaqmohammed.domain.text;

import android.content.Context;

import io.github.abdurazaaqmohammed.sdk.R;

import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.util.Locale;

/**
 * Password generation + strength estimation extracted from ToolRunnerActivity.
 *
 * <p>The two label methods have a {@link Context} overload that resolves
 * {@code R.string.*} from the sdk resources so the labels follow the device
 * language, plus the original no-{@code Context} overload. The latter delegates
 * with {@code null} and falls back to the English literal: this class is a
 * published plugin API ({@code compileOnly project(':sdk')}), so dropping the
 * old signatures would break third-party packs compiled against them.
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

    public static String strengthLabel(Context c, double entropy) {
        if (entropy < 28) {
            return str(c, R.string.passwords_strength_very_weak, "Very weak");
        } else if (entropy < 36) {
            return str(c, R.string.passwords_strength_weak, "Weak");
        } else if (entropy < 60) {
            return str(c, R.string.passwords_strength_fair, "Fair");
        } else if (entropy < 80) {
            return str(c, R.string.passwords_strength_strong, "Strong");
        } else {
            return str(c, R.string.passwords_strength_excellent, "Excellent");
        }
    }

    /** @see #strengthLabel(Context, double) */
    public static String strengthLabel(double entropy) {
        return strengthLabel(null, entropy);
    }

    public static String guessesToTime(Context c, double guesses) {
        double perSecond = 10000000000.0;
        double seconds = guesses / perSecond;
        if (seconds < 1) {
            return str(c, R.string.passwords_time_under_a_second, "under a second");
        } else if (seconds < 60) {
            return str(c, R.string.passwords_time_seconds, "%1$s seconds",
                    new DecimalFormat("0").format(seconds));
        } else if (seconds < 3600) {
            return str(c, R.string.passwords_time_minutes, "%1$s minutes",
                    new DecimalFormat("0").format(seconds / 60));
        } else if (seconds < 86400) {
            return str(c, R.string.passwords_time_hours, "%1$s hours",
                    new DecimalFormat("0").format(seconds / 3600));
        } else if (seconds < 31536000) {
            return str(c, R.string.passwords_time_days, "%1$s days",
                    new DecimalFormat("0").format(seconds / 86400));
        } else if (seconds < 3153600000L) {
            return str(c, R.string.passwords_time_years, "%1$s years",
                    new DecimalFormat("0").format(seconds / 31536000));
        }
        return str(c, R.string.passwords_time_centuries, "centuries");
    }

    /** @see #guessesToTime(Context, double) */
    public static String guessesToTime(double guesses) {
        return guessesToTime(null, guesses);
    }

    /** Resolves {@code resId} from {@code c}, or returns the English literal when it is null. */
    private static String str(Context c, int resId, String fallback) {
        return c == null ? fallback : c.getString(resId);
    }

    /** Format-args aware {@link #str(Context, int, String)}; the fallback is formatted the same way. */
    private static String str(Context c, int resId, String fallback, Object... args) {
        return c == null ? String.format(Locale.US, fallback, args) : c.getString(resId, args);
    }
}

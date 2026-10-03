package io.github.abdurazaaqmohammed.domain.math;

import android.content.Context;
import android.content.res.Configuration;

import io.github.abdurazaaqmohammed.sdk.R;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Date/duration math extracted from ToolRunnerActivity.
 * Uses java.time (desugared on old devices by the host/pack builds).
 *
 * <p>The prose-returning methods have a {@link Context} overload that resolves
 * {@code R.string.*} from the sdk resources so the wording follows the device
 * language, plus the original no-{@code Context} overload. The latter delegates
 * with {@code null} and falls back to the English literal: this class is a
 * published plugin API ({@code compileOnly project(':sdk')}), so dropping the
 * old signatures would break third-party packs compiled against them.
 *
 * <p>The {@code yyyy-MM-dd} patterns stay pinned to {@link Locale#US} because
 * they are a data format, not display. Only the {@code EEEE} day-name
 * formatter follows the device locale.
 */
public final class DateTime {

    private DateTime() {
    }

    public static String diff(Context ctx, String aIso, String bIso) throws Exception {
        LocalDate a = LocalDate.parse(aIso.trim());
        LocalDate b = LocalDate.parse(bIso.trim());
        LocalDate from = a.isBefore(b) ? a : b;
        LocalDate to = a.isBefore(b) ? b : a;
        long days = ChronoUnit.DAYS.between(from, to);
        Period p = Period.between(from, to);
        long weeks = days / 7;
        return str(ctx, R.string.datetime_diff, "%1$d days  (%2$d weeks, %3$dy %4$dm %5$dd)",
                days, weeks, p.getYears(), p.getMonths(), p.getDays());
    }

    /** @see #diff(Context, String, String) */
    public static String diff(String aIso, String bIso) throws Exception {
        return diff(null, aIso, bIso);
    }

    public static String ageFrom(Context ctx, String birthIso) throws Exception {
        LocalDate birth = LocalDate.parse(birthIso.trim());
        LocalDate today = LocalDate.now();
        if (birth.isAfter(today)) throw new IllegalArgumentException("future");
        Period p = Period.between(birth, today);
        long totalDays = ChronoUnit.DAYS.between(birth, today);
        return str(ctx, R.string.datetime_age_short,
                "%1$d years, %2$d months, %3$d days  (%4$d days total)",
                p.getYears(), p.getMonths(), p.getDays(), totalDays);
    }

    /** @see #ageFrom(Context, String) */
    public static String ageFrom(String birthIso) throws Exception {
        return ageFrom(null, birthIso);
    }

    public static String ageDetails(Context ctx, String birthIso) throws Exception {
        LocalDate birth = LocalDate.parse(birthIso.trim());
        LocalDate today = LocalDate.now();
        if (birth.isAfter(today)) throw new IllegalArgumentException("future");
        Period p = Period.between(birth, today);
        long totalDays = ChronoUnit.DAYS.between(birth, today);
        LocalDate next = birth.withYear(today.getYear());
        if (!next.isAfter(today)) {
            next = next.plusYears(1);
        }
        long toNext = ChronoUnit.DAYS.between(today, next);
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEEE", displayLocale(ctx));
        return str(ctx, R.string.datetime_age_parts, "%1$d years, %2$d months, %3$d days",
                p.getYears(), p.getMonths(), p.getDays()) + "\n"
                + str(ctx, R.string.datetime_age_total, "Total %1$d days  (%2$d weeks)",
                totalDays, totalDays / 7) + "\n"
                + str(ctx, R.string.datetime_age_born_on, "Born on a %1$s",
                birth.format(dayFmt)) + "\n"
                + str(ctx, R.string.datetime_age_next_bday, "Next birthday in %1$d days", toNext);
    }

    /** @see #ageDetails(Context, String) */
    public static String ageDetails(String birthIso) throws Exception {
        return ageDetails(null, birthIso);
    }

    public static String addDays(Context ctx, String startIso, int n) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        Date start = f.parse(startIso.trim());
        Calendar c = Calendar.getInstance();
        c.setTime(start);
        c.add(Calendar.DAY_OF_MONTH, n);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", displayLocale(ctx));
        return f.format(c.getTime()) + "  (" + dayFmt.format(c.getTime()) + ")";
    }

    /** @see #addDays(Context, String, int) */
    public static String addDays(String startIso, int n) throws Exception {
        return addDays(null, startIso, n);
    }

    public static long parseDurationToSeconds(String s) throws Exception {
        String[] parts = s.trim().split(":");
        if (parts.length == 1) {
            return Long.parseLong(parts[0].trim());
        } else if (parts.length == 2) {
            return Long.parseLong(parts[0].trim()) * 60 + Long.parseLong(parts[1].trim());
        } else if (parts.length == 3) {
            return Long.parseLong(parts[0].trim()) * 3600 + Long.parseLong(parts[1].trim()) * 60 + Long.parseLong(parts[2].trim());
        }
        throw new Exception("bad");
    }

    public static String formatDuration(long total) {
        boolean neg = total < 0;
        total = Math.abs(total);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return (neg ? "-" : "") + String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    /** @return {days, hours, mins, secs} remaining, or null when passed. */
    public static long[] countdownParts(long diffMs) {
        if (diffMs <= 0) return null;
        long days = diffMs / 86400000L;
        long hours = (diffMs % 86400000L) / 3600000L;
        long mins = (diffMs % 3600000L) / 60000L;
        long secs = (diffMs % 60000L) / 1000L;
        return new long[]{days, hours, mins, secs};
    }

    public static String todayIso() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** Resolves {@code resId} from {@code ctx}, or returns the English literal when it is null. */
    private static String str(Context ctx, int resId, String fallback) {
        return ctx == null ? fallback : ctx.getString(resId);
    }

    /** Format-args aware {@link #str(Context, int, String)}; the fallback is formatted the same way. */
    private static String str(Context ctx, int resId, String fallback, Object... args) {
        return ctx == null ? String.format(Locale.US, fallback, args) : ctx.getString(resId, args);
    }

    /**
     * Locale for display-only formatters (the {@code EEEE} day name). Prefers the locale the
     * host configured on {@code ctx} so a per-app language override wins over the process default.
     * {@code Configuration.locale} rather than {@code getLocales()} because minSdk is 19.
     */
    private static Locale displayLocale(Context ctx) {
        if (ctx == null) return Locale.getDefault();
        try {
            Locale loc = ctx.getResources().getConfiguration().locale;
            return loc == null ? Locale.getDefault() : loc;
        } catch (Exception e) {
            return Locale.getDefault();
        }
    }
}

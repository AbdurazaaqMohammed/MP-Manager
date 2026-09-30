package io.github.abdurazaaqmohammed.domain.math;

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
 */
public final class DateTime {

    private DateTime() {
    }

    public static String diff(String aIso, String bIso) throws Exception {
        LocalDate a = LocalDate.parse(aIso.trim());
        LocalDate b = LocalDate.parse(bIso.trim());
        LocalDate from = a.isBefore(b) ? a : b;
        LocalDate to = a.isBefore(b) ? b : a;
        long days = ChronoUnit.DAYS.between(from, to);
        Period p = Period.between(from, to);
        long weeks = days / 7;
        return days + " days  (" + weeks + " weeks, " + p.getYears() + "y " + p.getMonths() + "m " + p.getDays() + "d)";
    }

    public static String ageFrom(String birthIso) throws Exception {
        LocalDate birth = LocalDate.parse(birthIso.trim());
        LocalDate today = LocalDate.now();
        if (birth.isAfter(today)) throw new IllegalArgumentException("future");
        Period p = Period.between(birth, today);
        long totalDays = ChronoUnit.DAYS.between(birth, today);
        return p.getYears() + " years, " + p.getMonths() + " months, " + p.getDays()
                + " days  (" + totalDays + " days total)";
    }

    public static String ageDetails(String birthIso) throws Exception {
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
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.US);
        return p.getYears() + " years, " + p.getMonths() + " months, " + p.getDays() + " days\n"
                + "Total " + totalDays + " days  (" + totalDays / 7 + " weeks)\n"
                + "Born on a " + birth.format(dayFmt) + "\nNext birthday in " + toNext + " days";
    }

    public static String addDays(String startIso, int n) throws Exception {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        Date start = f.parse(startIso.trim());
        Calendar c = Calendar.getInstance();
        c.setTime(start);
        c.add(Calendar.DAY_OF_MONTH, n);
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEEE", Locale.US);
        return f.format(c.getTime()) + "  (" + dayFmt.format(c.getTime()) + ")";
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
}

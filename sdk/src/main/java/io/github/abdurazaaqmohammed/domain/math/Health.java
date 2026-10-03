package io.github.abdurazaaqmohammed.domain.math;

import android.content.Context;

import io.github.abdurazaaqmohammed.sdk.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Health math extracted from ToolRunnerActivity. Pure logic apart from the
 * localized labels.
 *
 * <p>The label methods have a {@link Context} overload that resolves
 * {@code R.string.*} from the sdk resources so the labels follow the device
 * language, plus the original no-{@code Context} overload. The latter delegates
 * with {@code null} and falls back to the English literal: this class is a
 * published plugin API ({@code compileOnly project(':sdk')}), so dropping the
 * old signatures would break third-party packs compiled against them.
 */
public final class Health {

    private Health() {
    }

    public static double bmi(double weightKg, double heightCm) {
        double h = heightCm / 100.0;
        return weightKg / (h * h);
    }

    public static String bmiCategory(Context c, double bmi) {
        if (bmi < 18.5) return str(c, R.string.health_bmi_underweight, "Underweight");
        if (bmi < 25) return str(c, R.string.health_bmi_normal, "Normal");
        if (bmi < 30) return str(c, R.string.health_bmi_overweight, "Overweight");
        return str(c, R.string.health_bmi_obese, "Obese");
    }

    /** @see #bmiCategory(Context, double) */
    public static String bmiCategory(double bmi) {
        return bmiCategory(null, bmi);
    }

    /** Mifflin-St Jeor. */
    public static double bmr(boolean male, int age, double heightCm, double weightKg) {
        return 10 * weightKg + 6.25 * heightCm - 5 * age + (male ? 5 : -161);
    }

    public static double tdee(double bmr, double activityFactor) {
        return bmr * activityFactor;
    }

    /** US Navy method, all measurements in cm. */
    public static double bodyFatMale(double waist, double neck, double height) {
        return 495 / (1.0324 - 0.19077 * Math.log10(waist - neck) + 0.15456 * Math.log10(height)) - 450;
    }

    /** US Navy method, all measurements in cm. */
    public static double bodyFatFemale(double waist, double hip, double neck, double height) {
        return 495 / (1.29579 - 0.35004 * Math.log10(waist + hip - neck) + 0.22100 * Math.log10(height)) - 450;
    }

    public static String bodyFatCategory(Context c, boolean male, double bf) {
        double athletic = male ? 6 : 14;
        double fitLow = male ? 18 : 25;
        double fitHigh = male ? 25 : 32;
        if (bf < athletic) return str(c, R.string.health_bf_essential, "Essential");
        if (bf < fitLow) return str(c, R.string.health_bf_athletic, "Athletic");
        if (bf < fitHigh) return str(c, R.string.health_bf_fit, "Fit");
        return str(c, R.string.health_bf_high, "High");
    }

    /** @see #bodyFatCategory(Context, boolean, double) */
    public static String bodyFatCategory(boolean male, double bf) {
        return bodyFatCategory(null, male, bf);
    }

    public static int waterTargetMl(double weightKg) {
        return (int) Math.round(weightKg * 35);
    }

    /** Bedtimes for 6..3 full cycles before the given wake time. */
    public static List<String> bedtimesForWake(Context c, int hour, int minute) {
        List<String> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        for (int i = 6; i >= 3; i--) {
            Calendar t = (Calendar) cal.clone();
            t.add(Calendar.MINUTE, -i * 90 - 15);
            out.add(cycleRow(c, i, f.format(t.getTime())));
        }
        return out;
    }

    /** @see #bedtimesForWake(Context, int, int) */
    public static List<String> bedtimesForWake(int hour, int minute) {
        return bedtimesForWake(null, hour, minute);
    }

    /** Wake times for 3..6 full cycles from now. */
    public static List<String> wakeTimesFromNow(Context c) {
        List<String> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        Calendar now = Calendar.getInstance();
        for (int i = 3; i <= 6; i++) {
            Calendar t = (Calendar) now.clone();
            t.add(Calendar.MINUTE, i * 90 + 15);
            out.add(cycleRow(c, i, f.format(t.getTime())));
        }
        return out;
    }

    /** @see #wakeTimesFromNow(Context) */
    public static List<String> wakeTimesFromNow() {
        return wakeTimesFromNow(null);
    }

    /** Resolves {@code resId} from {@code c}, or returns the English literal when it is null. */
    private static String str(Context c, int resId, String fallback) {
        return c == null ? fallback : c.getString(resId);
    }

    /** Format-args aware {@link #str(Context, int, String)}; the fallback is formatted the same way. */
    private static String str(Context c, int resId, String fallback, Object... args) {
        return c == null ? String.format(Locale.US, fallback, args) : c.getString(resId, args);
    }

    private static String cycleRow(Context c, int cycles, String hhmm) {
        return str(c, R.string.health_cycles_at, "%1$d cycles: %2$s", cycles, hhmm);
    }
}

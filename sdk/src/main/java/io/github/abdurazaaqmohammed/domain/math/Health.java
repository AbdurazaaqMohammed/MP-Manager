package io.github.abdurazaaqmohammed.domain.math;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Health math extracted from ToolRunnerActivity. Pure logic.
 */
public final class Health {

    private Health() {
    }

    public static double bmi(double weightKg, double heightCm) {
        double h = heightCm / 100.0;
        return weightKg / (h * h);
    }

    public static String bmiCategory(double bmi) {
        if (bmi < 18.5) return "Underweight";
        if (bmi < 25) return "Normal";
        if (bmi < 30) return "Overweight";
        return "Obese";
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

    public static String bodyFatCategory(boolean male, double bf) {
        double athletic = male ? 6 : 14;
        double fitLow = male ? 18 : 25;
        double fitHigh = male ? 25 : 32;
        if (bf < athletic) return "Essential";
        if (bf < fitLow) return "Athletic";
        if (bf < fitHigh) return "Fit";
        return "High";
    }

    public static int waterTargetMl(double weightKg) {
        return (int) Math.round(weightKg * 35);
    }

    /** Bedtimes for 6..3 full cycles before the given wake time. */
    public static List<String> bedtimesForWake(int hour, int minute) {
        List<String> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        for (int i = 6; i >= 3; i--) {
            Calendar t = (Calendar) c.clone();
            t.add(Calendar.MINUTE, -i * 90 - 15);
            out.add(i + " cycles: " + f.format(t.getTime()));
        }
        return out;
    }

    /** Wake times for 3..6 full cycles from now. */
    public static List<String> wakeTimesFromNow() {
        List<String> out = new ArrayList<>();
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        Calendar now = Calendar.getInstance();
        for (int i = 3; i <= 6; i++) {
            Calendar t = (Calendar) now.clone();
            t.add(Calendar.MINUTE, i * 90 + 15);
            out.add(i + " cycles: " + f.format(t.getTime()));
        }
        return out;
    }
}

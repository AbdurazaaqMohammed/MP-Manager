package io.github.abdurazaaqmohammed.domain.math;

/**
 * Price, tax, interest and savings math extracted from ToolRunnerActivity.
 * Pure logic; no Android dependencies.
 */
public final class Money {

    private Money() {
    }

    /** @return {saved, total} after discount then tax. */
    public static double[] discount(double price, double discPct, double taxPct) {
        double saved = price * discPct / 100.0;
        double afterDisc = price - saved;
        double total = afterDisc + afterDisc * taxPct / 100.0;
        return new double[]{saved, total};
    }

    /** @return {emi, total, interest}. */
    public static double[] emi(double principal, double annualPct, int months) {
        double r = annualPct / 1200.0;
        double emi;
        if (r == 0) {
            emi = principal / months;
        } else {
            double pow = Math.pow(1 + r, months);
            emi = principal * r * pow / (pow - 1);
        }
        double total = emi * months;
        return new double[]{emi, total, total - principal};
    }

    /** @return {tip, total, each}. */
    public static double[] tipSplit(double bill, double tipPct, int people) {
        double tip = bill * tipPct / 100.0;
        double total = bill + tip;
        return new double[]{tip, total, total / people};
    }

    /** Compound growth of a lump sum. annualRate is 0-1, n periods per year. */
    public static double compound(double principal, double annualRate, double years, int n) {
        double r = annualRate / n;
        return principal * Math.pow(1 + r, n * years);
    }

    /** Future value of monthly deposits (annuity due). annualRate is 0-1. */
    public static double sipFutureValue(double monthly, double annualRate, double years) {
        double mr = annualRate / 12.0;
        int months = (int) Math.round(years * 12);
        if (mr == 0) return monthly * months;
        return monthly * (Math.pow(1 + mr, months) - 1) / mr * (1 + mr);
    }

    /** Months to reach target saving monthly; -1 when unreachable in 100 years. */
    public static int savingsMonths(double target, double balance, double monthly, double annualPct) {
        if (monthly <= 0) return -1;
        double mr = annualPct / 1200.0;
        int months = 0;
        while (balance < target && months < 1200) {
            balance += monthly;
            balance *= (1 + mr);
            months++;
        }
        return months >= 1200 ? -1 : months;
    }

    /** @return {tax, total} adding tax. */
    public static double[] gstAdd(double amount, double rate) {
        double tax = amount * rate / 100.0;
        return new double[]{tax, amount + tax};
    }

    /** @return {net, tax} removing tax. */
    public static double[] gstRemove(double amount, double rate) {
        double net = amount / (1 + rate / 100.0);
        return new double[]{net, amount - net};
    }

    public static double percentOf(double x, double y) {
        return x * y / 100.0;
    }

    public static double whatPercent(double x, double y) {
        return x / y * 100.0;
    }

    public static double percentChange(double x, double y) {
        return (y - x) / x * 100.0;
    }

    /** @return {unitA, unitB}. */
    public static double[] unitPrices(double priceA, double qtyA, double priceB, double qtyB) {
        return new double[]{priceA / qtyA, priceB / qtyB};
    }
}

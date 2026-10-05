package io.github.abdurazaaqmohammed.domain.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MathLogicTest {

    @Test
    public void expressionEvaluatorPrecedence() throws Exception {
        assertEquals(14.0, ExpressionEvaluator.eval("2+3*4"), 1e-9);
        assertEquals(20.0, ExpressionEvaluator.eval("(2+3)*4"), 1e-9);
        assertEquals(1024.0, ExpressionEvaluator.eval("2^10"), 1e-9);
        assertEquals(4.0, ExpressionEvaluator.eval("sqrt(16)"), 1e-9);
        assertEquals(1.0, ExpressionEvaluator.eval("sin(90)"), 1e-9);
        assertEquals(1e-9, ExpressionEvaluator.eval("   "), 1e-9);
    }

    @Test
    public void expressionEvaluatorFormat() {
        assertEquals("0.5", ExpressionEvaluator.format(0.5));
        assertEquals("Error", ExpressionEvaluator.format(Double.NaN));
    }

    @Test
    public void primes() {
        assertTrue(Primes.isPrime(7));
        assertFalse(Primes.isPrime(8));
        assertFalse(Primes.isPrime(1));
        assertEquals("2^2 x 3", Primes.factorize(12));
        assertEquals(11, Primes.nextPrime(10));
        assertEquals(4, Primes.listUpTo(10).size());
        assertTrue(Primes.listUpTo(10).contains(7));
        assertEquals(6, Primes.gcd(12, 18));
    }

    @Test
    public void dateTimeDiff() throws Exception {
        String d = DateTime.diff("2024-01-01", "2024-01-11");
        assertTrue(d.startsWith("10 days"));
        assertEquals("2024-01-31  (Wednesday)", DateTime.addDays("2024-01-01", 30));
    }

    @Test
    public void dateTimeDurations() throws Exception {
        assertEquals(90, DateTime.parseDurationToSeconds("1:30"));
        assertEquals(3661, DateTime.parseDurationToSeconds("1:01:01"));
        assertEquals("01:30:00", DateTime.formatDuration(5400));
        long[] parts = DateTime.countdownParts(90061000L);
        assertEquals(1, parts[0]);
        assertEquals(1, parts[1]);
        assertEquals(1, parts[2]);
        assertEquals(1, parts[3]);
        assertNull(DateTime.countdownParts(-5));
    }

    @Test
    public void healthBasics() {
        assertEquals(22.86, Health.bmi(70, 175), 0.01);
        assertEquals("Normal", Health.bmiCategory(21.5));
        double bmrM = Health.bmr(true, 30, 175, 70);
        assertEquals(1649 + 0, bmrM, 5);
        assertEquals(3500, Health.waterTargetMl(100), 0);
    }

    @Test
    public void moneyBasics() {
        double[] disc = Money.discount(100, 10, 0);
        assertEquals(10.0, disc[0], 1e-9);
        assertEquals(90.0, disc[1], 1e-9);
        double[] emi = Money.emi(1200, 0, 12);
        assertEquals(100.0, emi[0], 1e-6);
        double fv = Money.compound(1000, 0.10, 1, 1);
        assertEquals(1100.0, fv, 1e-6);
        assertEquals(10.0, Money.percentOf(100, 10), 1e-9);
        assertEquals(-1, Money.savingsMonths(1000, 0, 0, 0));
    }

    @Test
    public void unitConversion() {
        double m = UnitConverter.convert("Length", 1, "km", "m");
        assertEquals(1000.0, m, 1e-6);
        assertEquals(0.0, UnitConverter.convertTemp(0, "C", "C"), 1e-9);
        assertEquals(212.0, UnitConverter.convertTemp(100, "C", "F"), 1e-6);
        assertEquals(0.0, UnitConverter.convertTemp(32, "F", "C"), 1e-6);
    }
}

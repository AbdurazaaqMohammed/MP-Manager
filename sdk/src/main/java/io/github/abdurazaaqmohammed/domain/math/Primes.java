package io.github.abdurazaaqmohammed.domain.math;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prime utilities extracted from ToolRunnerActivity.
 */
public final class Primes {

    private Primes() {
    }

    public static boolean isPrime(long n) {
        if (n < 2) {
            return false;
        }
        if (n == 2 || n == 3) {
            return true;
        }
        if (n % 2 == 0) {
            return false;
        }
        for (long i = 3; i * i <= n; i += 2) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }

    public static String factorize(long n) {
        StringBuilder b = new StringBuilder();
        long rest = n;
        boolean first = true;
        for (long p = 2; p * p <= rest; p += (p == 2 ? 1 : 2)) {
            int exp = 0;
            while (rest % p == 0) {
                rest /= p;
                exp++;
            }
            if (exp > 0) {
                if (!first) {
                    b.append(" x ");
                }
                b.append(p);
                if (exp > 1) {
                    b.append("^").append(exp);
                }
                first = false;
            }
        }
        if (rest > 1) {
            if (!first) {
                b.append(" x ");
            }
            b.append(rest);
        }
        return b.toString();
    }

    public static long nextPrime(long n) {
        long next = n + 1;
        while (!isPrime(next)) {
            next++;
        }
        return next;
    }

    public static List<Integer> listUpTo(int n) {
        List<Integer> out = new ArrayList<>();
        if (n < 2) return out;
        boolean[] sieve = new boolean[n + 1];
        Arrays.fill(sieve, true);
        sieve[0] = false;
        sieve[1] = false;
        for (int i = 2; i * i <= n; i++) {
            if (sieve[i]) {
                for (int j = i * i; j <= n; j += i) {
                    sieve[j] = false;
                }
            }
        }
        for (int i = 2; i <= n; i++) {
            if (sieve[i]) out.add(i);
        }
        return out;
    }

    public static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return a == 0 ? 1 : a;
    }
}

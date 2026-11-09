package io.github.abdurazaaqmohammed.utils;

import java.util.function.BooleanSupplier;

/** Linear-time, bounded-buffer search of a virtual file, including reverse search. */
public final class BytePatternSearch {
    public interface Source { byte[] read(long start, long end); }
    private static final int BLOCK = 64 * 1024;
    private BytePatternSearch() {}

    public static long find(Source source, long size, byte[] pattern, long start,
                            boolean forward, BooleanSupplier cancelled) {
        if (pattern.length == 0 || pattern.length > size) return -1;
        byte[] needle = pattern;
        if (!forward) {
            needle = new byte[pattern.length];
            for (int i = 0; i < pattern.length; i++) needle[i] = pattern[pattern.length - 1 - i];
        }
        int[] prefix = new int[needle.length];
        for (int i = 1, j = 0; i < needle.length; i++) {
            while (j > 0 && needle[i] != needle[j]) j = prefix[j - 1];
            if (needle[i] == needle[j]) j++;
            prefix[i] = j;
        }
        int matched = 0;
        long position = forward ? Math.max(0, start) : Math.min(start, size - needle.length) + needle.length;
        while (forward ? position < size : position > 0) {
            if (cancelled.getAsBoolean()) return -1;
            long from = forward ? position : Math.max(0, position - BLOCK);
            long to = forward ? Math.min(size, position + BLOCK) : position;
            byte[] bytes = source.read(from, to);
            if (bytes.length != to - from) return -1;
            for (int n = 0; n < bytes.length; n++) {
                if ((n & 4095) == 0 && cancelled.getAsBoolean()) return -1;
                int i = forward ? n : bytes.length - 1 - n;
                while (matched > 0 && bytes[i] != needle[matched]) matched = prefix[matched - 1];
                if (bytes[i] == needle[matched]) matched++;
                if (matched == needle.length) return forward ? from + i - needle.length + 1 : from + i;
            }
            position = forward ? to : from;
        }
        return -1;
    }
}

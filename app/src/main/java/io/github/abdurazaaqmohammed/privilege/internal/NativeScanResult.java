package io.github.abdurazaaqmohammed.privilege.internal;

import io.github.abdurazaaqmohammed.util.io.DirectoryStats;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Strict parser: a partial/missing scan must never become a cached zero-byte directory. */
final class NativeScanResult {
    final List<DirectoryStats> items;
    DirectoryStats total;

    NativeScanResult(int expected) {
        items = new ArrayList<>(Collections.nCopies(expected, null));
    }

    void accept(String line) {
        if (line.startsWith("ITEM_RESULT ")) {
            long index = counter(line, "idx=");
            if (index >= 0 && index < items.size()) items.set((int) index, stats(line));
        } else if (line.startsWith("SCAN_RESULT ")) total = stats(line);
    }

    boolean valid() {
        if (total == null || items.contains(null)) return false;
        DirectoryStats sum = new DirectoryStats(0, 0, 0, 0, 0);
        for (DirectoryStats item : items) sum = sum.plus(item);
        return sum.equals(total);
    }

    private static DirectoryStats stats(String line) {
        long files = counter(line, "files="), folders = counter(line, "dirs="), bytes = counter(line, "bytes=");
        long hidden = counter(line, "hidden="), errors = counter(line, "errors=");
        if (files < 0 || folders < 0 || bytes < 0 || hidden < 0 || errors < 0) return null;
        return new DirectoryStats(files, folders, bytes, hidden, errors);
    }

    static long counter(String line, String key) {
        for (String part : line.split("\\s+")) {
            if (part.startsWith(key)) {
                try { return Long.parseLong(part.substring(key.length())); }
                catch (NumberFormatException ignored) { return -1; }
            }
        }
        return -1;
    }
}

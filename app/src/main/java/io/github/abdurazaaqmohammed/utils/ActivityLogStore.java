package io.github.abdurazaaqmohammed.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * In-memory record of foreground screens, filled by {@code ActivityLoggerService}.
 *
 * <p>Held in memory only: this is a scratchpad for watching an app's navigation
 * while inspecting it, not an audit trail that should outlive the session or
 * accumulate a history of what the user did on their device. The ring bound
 * keeps a long session from growing without limit.
 */
public final class ActivityLogStore {

    /** One observed screen. */
    public static final class Entry {
        public final long time;
        public final String packageName;
        public final String className;
        /** True for a window that is not the app's main screen, i.e. a dialog or popup. */
        public final boolean isWindow;

        public Entry(long time, String packageName, String className, boolean isWindow) {
            this.time = time;
            this.packageName = packageName == null ? "" : packageName;
            this.className = className == null ? "" : className;
            this.isWindow = isWindow;
        }
    }

    private static final int MAX = 500;
    private static final Deque<Entry> ENTRIES = new ArrayDeque<>();
    private static volatile boolean ignoreSelf = true;

    private ActivityLogStore() {
    }

    private static final String SELF_PACKAGE = "io.github.abdurazaaqmohammed.MPManager";

    public static void setIgnoreSelf(boolean value) {
        ignoreSelf = value;
    }

    public static boolean ignoreSelf() {
        return ignoreSelf;
    }

    public static void add(String packageName, String className, boolean isWindow) {
        if (ignoreSelf && SELF_PACKAGE.equals(packageName)) return;
        // A rotation or focus blip repeats the same screen; only record changes.
        Entry newest = ENTRIES.peekLast();
        if (newest != null && newest.className.equals(className)
                && newest.packageName.equals(packageName) && newest.isWindow == isWindow) {
            return;
        }
        synchronized (ENTRIES) {
            ENTRIES.addLast(new Entry(System.currentTimeMillis(), packageName, className, isWindow));
            while (ENTRIES.size() > MAX) ENTRIES.removeFirst();
        }
    }

    /** Newest first. */
    public static List<Entry> snapshot() {
        synchronized (ENTRIES) {
            List<Entry> out = new ArrayList<>(ENTRIES);
            java.util.Collections.reverse(out);
            return out;
        }
    }

    public static int count() {
        return ENTRIES.size();
    }

    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
    }

    /** Tab-separated, newest first, for pasting elsewhere. */
    public static String asText() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : snapshot()) {
            sb.append(e.packageName).append('\t').append(e.className).append('\t')
                    .append(e.isWindow ? "window" : "activity").append('\n');
        }
        return sb.toString();
    }
}
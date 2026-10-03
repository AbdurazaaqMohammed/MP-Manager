package io.github.abdurazaaqmohammed.domain.remote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Listing rules shared by every backend: the same sorting and filtering the
 * local pane uses, so switching a pane between local and remote does not
 * reshuffle the list.
 */
public final class RemoteListing {

    /** Directories first, then case-insensitive name. */
    public static final Comparator<RemoteEntry> BY_NAME =
            Comparator.comparing((RemoteEntry e) -> !e.directory())
                    .thenComparing(RemoteEntry::name, String.CASE_INSENSITIVE_ORDER);

    public static final Comparator<RemoteEntry> BY_SIZE =
            Comparator.comparing((RemoteEntry e) -> !e.directory())
                    .thenComparingLong(RemoteEntry::size);

    public static final Comparator<RemoteEntry> BY_DATE =
            Comparator.comparing((RemoteEntry e) -> !e.directory())
                    .thenComparingLong(RemoteEntry::modifiedAt);

    private RemoteListing() {
    }

    public static List<RemoteEntry> sort(List<RemoteEntry> entries, String mode, boolean reverse) {
        List<RemoteEntry> copy = new ArrayList<>(entries);
        Comparator<RemoteEntry> c;
        switch (mode == null ? "name" : mode) {
            case "size": c = BY_SIZE; break;
            case "date": c = BY_DATE; break;
            default: c = BY_NAME; break;
        }
        copy.sort(reverse ? c.reversed() : c);
        return copy;
    }

    /** Case-insensitive substring match on the name; empty query returns all. */
    public static List<RemoteEntry> filter(List<RemoteEntry> entries, String query) {
        if (query == null || query.trim().isEmpty()) return entries;
        String q = query.trim().toLowerCase();
        List<RemoteEntry> out = new ArrayList<>();
        for (RemoteEntry e : entries) {
            if (e.name().toLowerCase().contains(q)) out.add(e);
        }
        return out;
    }

    /** True when "." and ".." should be offered for a directory listing. */
    public static boolean wantsParentLink(String path) {
        return path != null && !path.isEmpty() && !"/".equals(path);
    }

    /** Join a parent directory and a child name without doubling the separator. */
    public static String join(String dir, String name) {
        String base = (dir == null || dir.isEmpty()) ? "/" : dir;
        if (base.endsWith("/")) return base + name;
        return base + "/" + name;
    }
}
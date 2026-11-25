package io.github.abdurazaaqmohammed.privilege.internal;

import io.github.abdurazaaqmohammed.util.text.ShellQuote;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Shared single-shell listing helpers used by fallback root and Shizuku paths
 * so symlinks resolve inside the same command as {@code ls -la} without a
 * second process spawn, and Binder {@code dirStat} records parse uniformly.
 */
final class LsListingSupport {

    static final String LINKS_MARKER = "__MP_LINKS__";

    private LsListingSupport() {
    }

    /**
     * Builds a single shell script that validates {@code dirPath} as a directory,
     * runs {@code ls -la}, and appends resolved directory/file hints for any
     * symlinks found in the same pass.
     */
    static String buildSingleCallLsCommand(String dirPath) {
        String clean = (dirPath.endsWith("/") && dirPath.length() > 1)
                ? dirPath.substring(0, dirPath.length() - 1) : dirPath;
        String listTarget = clean.endsWith("/") ? clean : clean + "/";
        String cleanArg = ShellQuote.escapeShellArg(clean);
        String targetArg = ShellQuote.escapeShellArg(listTarget);
        return "test -d " + cleanArg + " || exit 4; "
                + "LC_ALL=C ls -la -- " + targetArg + " || exit 1; "
                + "printf '" + LINKS_MARKER + "\\n'; "
                + "p=" + cleanArg + "; p=${p%/}; "
                + "for f in \"$p\"/* \"$p\"/.*; do "
                + "[ -L \"$f\" ] || continue; "
                + "n=${f##*/}; "
                + "[ \"$n\" = \".\" ] || [ \"$n\" = \"..\" ] && continue; "
                + "if [ -d \"$f\" ] || [ -d \"$f/\" ]; then printf 'D:%s\\n' \"$n\"; "
                + "elif [ -e \"$f\" ] || [ -L \"$f\" ]; then printf 'F:%s\\n' \"$n\"; fi; "
                + "done";
    }

    static File[] parseLsWithLinksOutput(String cleanParent, String output, boolean asShizukuFile) {
        if (output == null) return null;
        String[] lines = output.split("\\r?\\n");
        List<File> files = new ArrayList<>();
        Map<String, Integer> linkIdxByName = new HashMap<>();
        boolean inLinksSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (!inLinksSection) {
                if (LINKS_MARKER.equals(trimmed)) {
                    inLinksSection = true;
                    continue;
                }
                if (trimmed.isEmpty() || trimmed.startsWith("total ")) continue;
                Matcher matcher = RootManager.LS_LONG_LINE.matcher(line);
                if (!matcher.matches()) continue;
                String rawMode = matcher.group(1);
                char type = rawMode.charAt(0);
                if (type != '-' && type != 'd' && type != 'l') continue;
                String name = matcher.group(4);
                int arrow = name.indexOf(" -> ");
                if (arrow > 0) name = name.substring(0, arrow);
                if (name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;

                long size = parseLongSafe(matcher.group(2), 0L);
                long mtime = RootManager.parseLsDateTimeFlexible(matcher.group(3));
                String mode = rawMode.length() > 1 ? rawMode.substring(1) : null;
                boolean isDir = type == 'd';
                boolean isLink = type == 'l';
                if (isLink) {
                    linkIdxByName.put(name, files.size());
                }
                files.add(makeEntry(cleanParent, name, isDir, !isDir, isLink,
                        isDir ? 0L : Math.max(0L, size), mtime, mode, asShizukuFile));
            } else {
                if (line.length() < 3) continue;
                if (line.startsWith("D:") || line.startsWith("F:")) {
                    boolean resolvedDir = line.charAt(0) == 'D';
                    String linkName = line.substring(2);
                    Integer idx = linkIdxByName.get(linkName);
                    if (idx != null) {
                        File prev = files.get(idx);
                        String prevMode = prev instanceof RootFile
                                ? ((RootFile) prev).getRootMode()
                                : (prev instanceof ShizukuFile ? ((ShizukuFile) prev).getMode() : null);
                        files.set(idx, makeEntry(cleanParent, prev.getName(), resolvedDir, !resolvedDir,
                                true, resolvedDir ? 0L : prev.length(), prev.lastModified(),
                                prevMode, asShizukuFile));
                    }
                }
            }
        }
        return files.toArray(asShizukuFile ? new ShizukuFile[0] : new File[0]);
    }

    /**
     * Parses Binder {@code dirStat} lines into {@link RootFile} or {@link ShizukuFile}
     * arrays. Supports both legacy 4-field lines and extended 6-field lines carrying
     * symlink and octal permission bits.
     */
    static File[] parseDirStatLines(String dirPath, List<String> lines, boolean asShizukuFile) {
        if (lines == null) return null;
        String cleanParent = (dirPath.endsWith("/") && dirPath.length() > 1)
                ? dirPath.substring(0, dirPath.length() - 1) : dirPath;
        List<File> files = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            String[] parts = line.split("\037", -1);
            if (parts.length < 4) continue;
            String name = parts[0];
            if (name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;
            boolean dir = "1".equals(parts[1].trim());
            long size = Math.max(0L, parseLongSafe(parts[2].trim(), 0L));
            long mtime = Math.max(0L, parseLongSafe(parts[3].trim(), 0L));
            boolean isLink = parts.length > 4 && "1".equals(parts[4].trim());
            String rawMode = parts.length > 5 ? parts[5].trim() : "";
            String mode = rawMode.isEmpty() || "-".equals(rawMode) ? null : rawMode;
            files.add(makeEntry(cleanParent, name, dir, !dir, isLink,
                    dir ? 0L : size, mtime, mode, asShizukuFile));
        }
        return files.toArray(asShizukuFile ? new ShizukuFile[0] : new File[0]);
    }

    private static File makeEntry(String cleanParent, String name, boolean isDir, boolean isFile,
                                  boolean isLink, long size, long mtime, String mode,
                                  boolean asShizukuFile) {
        if (asShizukuFile) {
            String fullPath = cleanParent.endsWith("/") ? cleanParent + name : cleanParent + "/" + name;
            return new ShizukuFile(fullPath, isDir, size, mtime, isLink, mode, true);
        }
        return new RootFile(cleanParent, name, true, isDir, isFile, size, mtime, mode, isLink);
    }

    private static long parseLongSafe(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

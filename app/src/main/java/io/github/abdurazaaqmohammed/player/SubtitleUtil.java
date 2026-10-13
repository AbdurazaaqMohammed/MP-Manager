package io.github.abdurazaaqmohammed.player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds, parses and cleans external subtitle files for video playback.
 *
 * <p>External cues are parsed in-app into timed {@link Cue}s and driven by the
 * player clock, which works regardless of {@code MediaPlayer} timed-text state
 * quirks. Embedded tracks continue to use the framework listener.
 */
public final class SubtitleUtil {

    private SubtitleUtil() {
    }

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern ASS_OVERRIDE = Pattern.compile("\\{[^}]*\\}");
    private static final Pattern ASS_NEWLINE = Pattern.compile("\\\\[Nn]");
    private static final Pattern MULTI_BLANK = Pattern.compile("\n{3,}");
    // MicroDVD {startFrame}{endFrame} — frames are converted at 23.976 fps.
    private static final Pattern MICRODVD = Pattern.compile("^\\{(\\d+)\\}\\{(\\d+)\\}(.*)$");
    private static final double MICRODVD_FPS = 23.976;

    /** Extensions the player can render (parsed in-app, timed to playback). */
    public static final String[] SUPPORTED_EXTENSIONS = {"srt", "vtt", "ass", "ssa", "sub"};

    public static String[] getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS.clone();
    }

    /** One timed subtitle cue. */
    public static final class Cue {
        public final long startMs;
        public final long endMs;
        public final String text;

        public Cue(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }

    /** Extensions the player can use. */
    public static boolean isSupportedExternal(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (lower.endsWith("." + ext)) return true;
        }
        return false;
    }

    /**
     * Best sidecar next to the video: exact same base name wins, then
     * {@code base.<lang>.<ext>} variants (e.g. movie.en.srt). Extension
     * priority: srt, vtt, ass, ssa, sub. Returns null when none exists.
     */
    public static File findSidecar(String videoPath) {
        List<File> all = findSidecars(videoPath);
        return all.isEmpty() ? null : all.get(0);
    }

    /** All usable sidecars next to the video, best match first. */
    public static List<File> findSidecars(String videoPath) {
        List<File> exact = new ArrayList<>();
        List<File> tagged = new ArrayList<>();
        if (videoPath == null || videoPath.isEmpty()) return exact;
        File video = new File(videoPath);
        File dir = video.getParentFile();
        if (dir == null || !dir.isDirectory()) return exact;
        String name = video.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (SecurityException e) {
            return exact;
        }
        if (files == null) return exact;
        String baseLower = base.toLowerCase(Locale.ROOT);
        for (File f : files) {
            if (f == null || !f.isFile() || !f.canRead()) continue;
            String n = f.getName();
            String nl = n.toLowerCase(Locale.ROOT);
            int d = nl.lastIndexOf('.');
            if (d <= 0) continue;
            if (f.length() <= 0 || f.length() > 8L * 1024L * 1024L) continue;
            if (!isSupportedExternal(n)) continue;
            String stem = nl.substring(0, d);
            if (stem.equals(baseLower)) {
                exact.add(f);
            } else if (stem.startsWith(baseLower + ".")) {
                tagged.add(f);
            }
        }
        Comparator<File> byExt = (a, b) ->
                Integer.compare(extPriority(a.getName()), extPriority(b.getName()));
        exact.sort(byExt);
        tagged.sort(byExt);
        exact.addAll(tagged);
        return exact;
    }

    private static int extPriority(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".srt")) return 0;
        if (lower.endsWith(".vtt")) return 1;
        if (lower.endsWith(".ass")) return 2;
        if (lower.endsWith(".ssa")) return 3;
        return 4;
    }

    /**
     * Parses a subtitle file into timed cues. Supports SRT, WebVTT, ASS/SSA
     * (Dialogue lines) and MicroDVD ({f1}{f2}text). Returns an empty list when
     * nothing usable is found.
     */
    public static List<Cue> parse(File input) throws IOException {
        List<String> lines = readAllLines(input);
        if (lines.isEmpty()) return new ArrayList<>();
        String name = input.getName().toLowerCase(Locale.ROOT);
        List<Cue> cues;
        if (name.endsWith(".vtt")) {
            cues = parseVtt(lines);
        } else if (name.endsWith(".ass") || name.endsWith(".ssa")) {
            cues = parseAss(lines);
        } else if (name.endsWith(".sub")) {
            cues = parseMicroDvd(lines);
            if (cues.isEmpty()) cues = parseSrt(lines);
        } else {
            cues = parseSrt(lines);
            if (cues.isEmpty()) cues = parseVtt(lines);
        }
        cues.sort((a, b) -> Long.compare(a.startMs, b.startMs));
        return cues;
    }

    /** Finds the cue visible at a position (with delay applied), or null. */
    public static String cueAt(List<Cue> cues, long positionMs, int delayMs) {
        if (cues == null || cues.isEmpty()) return null;
        long t = positionMs + delayMs;
        if (t < 0) return null;
        // Binary search: last cue with start <= t.
        int lo = 0, hi = cues.size() - 1, idx = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (cues.get(mid).startMs <= t) {
                idx = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        if (idx < 0) return null;
        // Overlapping cues: prefer the latest-starting one still visible.
        for (int i = idx; i >= 0 && i >= idx - 4; i--) {
            Cue c = cues.get(i);
            if (t < c.endMs) return c.text;
            if (c.endMs <= t && i == idx) {
                // Fall through: maybe an earlier overlapping cue still covers t.
                continue;
            }
        }
        Cue c = cues.get(idx);
        return t < c.endMs ? c.text : null;
    }

    private static List<String> readAllLines(File input) throws IOException {
        if (input == null || !input.isFile()) throw new IOException("Missing subtitle file");
        byte[] raw;
        try (FileInputStream fis = new FileInputStream(input)) {
            long len = input.length();
            if (len <= 0 || len > 8L * 1024L * 1024L) throw new IOException("Bad subtitle file size");
            raw = new byte[(int) len];
            int off = 0;
            while (off < raw.length) {
                int n = fis.read(raw, off, raw.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        String text = decodeBytes(raw);
        List<String> lines = new ArrayList<>();
        // Preserve trailing empty tokens so block splitting keeps working.
        String[] split = text.split("\r\n|\r|\n", -1);
        for (String s : split) lines.add(s);
        // Drop a leading BOM if the decoder left one.
        if (!lines.isEmpty()) lines.set(0, lines.get(0).replace("﻿", ""));
        // Trim extra empty lines at the very end but keep one terminator.
        while (lines.size() > 1
                && lines.get(lines.size() - 1).isEmpty()
                && lines.get(lines.size() - 2).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String decodeBytes(byte[] raw) {
        if (raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF) {
            return new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8);
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFF && (raw[1] & 0xFF) == 0xFE) {
            return new String(raw, StandardCharsets.UTF_16LE);
        }
        if (raw.length >= 2 && (raw[0] & 0xFF) == 0xFE && (raw[1] & 0xFF) == 0xFF) {
            return new String(raw, StandardCharsets.UTF_16BE);
        }
        // Try UTF-8 first; fall back to windows-1252 for legacy Latin files.
        String utf = new String(raw, StandardCharsets.UTF_8);
        if (!utf.contains("�")) return utf;
        try {
            return new String(raw, "windows-1252");
        } catch (Exception e) {
            return utf;
        }
    }

    /** Parses SRT (or SRT-like) blocks into cues. */
    static List<Cue> parseSrt(List<String> lines) {
        List<Cue> cues = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            while (i < lines.size() && lines.get(i).trim().isEmpty()) i++;
            if (i >= lines.size()) break;
            String head = lines.get(i).trim();
            if (head.matches("\\d+")
                    && i + 1 < lines.size()
                    && lines.get(i + 1).contains("-->")) {
                i++;
                if (i >= lines.size()) break;
                head = lines.get(i).trim();
            }
            int arrow = head.indexOf("-->");
            if (arrow < 0) {
                i++;
                continue;
            }
            long start = parseTimestamp(head.substring(0, arrow).trim());
            String[] parts = head.substring(arrow + 3).trim().split("\\s+");
            long end = parts.length == 0 ? -1 : parseTimestamp(parts[0]);
            if (start < 0 || end < 0 || end <= start) {
                i++;
                continue;
            }
            i++;
            StringBuilder text = new StringBuilder();
            while (i < lines.size() && !lines.get(i).trim().isEmpty()) {
                String t = cleanCue(lines.get(i));
                if (t != null && !t.isEmpty()) {
                    if (text.length() > 0) text.append('\n');
                    text.append(t);
                }
                i++;
            }
            if (text.length() > 0) cues.add(new Cue(start, end, text.toString()));
        }
        return cues;
    }

    /** Parses WebVTT blocks into cues (skips header/NOTE/STYLE/REGION). */
    static List<Cue> parseVtt(List<String> lines) {
        List<Cue> cues = new ArrayList<>();
        int i = 0;
        while (i < lines.size() && !lines.get(i).trim().isEmpty()) i++;
        while (i < lines.size()) {
            while (i < lines.size() && lines.get(i).trim().isEmpty()) i++;
            if (i >= lines.size()) break;
            String head = lines.get(i).trim();
            if (head.startsWith("NOTE") || head.startsWith("STYLE") || head.startsWith("REGION")) {
                while (i < lines.size() && !lines.get(i).trim().isEmpty()) i++;
                continue;
            }
            if (!head.contains("-->")) i++;
            if (i >= lines.size()) break;
            String timing = lines.get(i).trim();
            int arrow = timing.indexOf("-->");
            if (arrow < 0) {
                i++;
                continue;
            }
            long start = parseTimestamp(timing.substring(0, arrow).trim());
            String[] parts = timing.substring(arrow + 3).trim().split("\\s+");
            long end = parts.length == 0 ? -1 : parseTimestamp(parts[0]);
            if (start < 0 || end < 0 || end <= start) {
                i++;
                continue;
            }
            i++;
            StringBuilder text = new StringBuilder();
            while (i < lines.size() && !lines.get(i).trim().isEmpty()) {
                String t = cleanCue(lines.get(i));
                if (t != null && !t.isEmpty() && !t.startsWith("NOTE")) {
                    if (text.length() > 0) text.append('\n');
                    text.append(t);
                }
                i++;
            }
            if (text.length() > 0) cues.add(new Cue(start, end, text.toString()));
        }
        return cues;
    }

    /** Parses ASS/SSA Dialogue lines into cues. */
    static List<Cue> parseAss(List<String> lines) {
        List<Cue> cues = new ArrayList<>();
        boolean inEvents = false;
        int idxStart = 1, idxEnd = 2, idxText = 9;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("[") && line.endsWith("]")) {
                inEvents = line.equalsIgnoreCase("[Events]");
                continue;
            }
            if (!inEvents) continue;
            if (line.regionMatches(true, 0, "Format:", 0, 7)) {
                String[] cols = line.substring(7).split(",");
                int s = -1, e = -1, t = -1;
                for (int k = 0; k < cols.length; k++) {
                    String c = cols[k].trim().toLowerCase(Locale.ROOT);
                    if (c.equals("start")) s = k;
                    else if (c.equals("end")) e = k;
                    else if (c.equals("text")) t = k;
                }
                if (s >= 0 && e >= 0 && t >= 0) {
                    idxStart = s;
                    idxEnd = e;
                    idxText = t;
                }
                continue;
            }
            if (!line.regionMatches(true, 0, "Dialogue:", 0, 9)) continue;
            String body = line.substring(9).trim();
            int need = Math.max(idxText + 1, 10);
            String[] fields = body.split(",", need);
            if (fields.length <= Math.max(idxStart, Math.max(idxEnd, idxText))) {
                fields = body.split(",", 10);
                idxStart = 1;
                idxEnd = 2;
                idxText = 9;
                if (fields.length < 10) continue;
            }
            long start = parseAssTimestamp(fields[idxStart].trim());
            long end = parseAssTimestamp(fields[idxEnd].trim());
            if (start < 0 || end < 0 || end <= start) continue;
            String text = ASS_NEWLINE.matcher(fields[idxText]).replaceAll("\n");
            text = cleanCue(text);
            if (text == null || text.isEmpty()) continue;
            cues.add(new Cue(start, end, text));
        }
        return cues;
    }

    /** Parses MicroDVD .sub ({startFrame}{endFrame}text) lines. */
    static List<Cue> parseMicroDvd(List<String> lines) {
        List<Cue> cues = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m = MICRODVD.matcher(line);
            if (!m.matches()) continue;
            try {
                long f1 = Long.parseLong(m.group(1));
                long f2 = Long.parseLong(m.group(2));
                long start = (long) (f1 * 1000.0 / MICRODVD_FPS);
                long end = (long) (f2 * 1000.0 / MICRODVD_FPS);
                if (end <= start) continue;
                String text = cleanCue(m.group(3).replace('|', '\n'));
                if (text == null || text.isEmpty()) continue;
                cues.add(new Cue(start, end, text));
            } catch (NumberFormatException e) {
                // Skip malformed frame numbers.
            }
        }
        return cues;
    }

    /** Parses HH:MM:SS,mmm / MM:SS.mmm (comma or dot) into ms, or -1. */
    static long parseTimestamp(String t) {
        if (t == null) return -1;
        String s = t.trim().replace('.', ',');
        try {
            String main = s;
            String msPart = "0";
            int comma = s.lastIndexOf(',');
            if (comma >= 0) {
                msPart = s.substring(comma + 1).trim();
                main = s.substring(0, comma).trim();
            }
            String[] hms = main.split(":");
            long h = 0, m, sec;
            if (hms.length == 3) {
                h = Long.parseLong(hms[0].trim());
                m = Long.parseLong(hms[1].trim());
                sec = Long.parseLong(hms[2].trim());
            } else if (hms.length == 2) {
                m = Long.parseLong(hms[0].trim());
                sec = Long.parseLong(hms[1].trim());
            } else {
                return -1;
            }
            if (msPart.length() > 3) msPart = msPart.substring(0, 3);
            while (msPart.length() < 3) msPart += "0";
            long ms = Long.parseLong(msPart);
            return h * 3600000L + m * 60000L + sec * 1000L + ms;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Parses ASS H:MM:SS.cc timestamps into ms, or -1. */
    static long parseAssTimestamp(String t) {
        if (t == null) return -1;
        try {
            String[] hms = t.trim().split(":");
            if (hms.length != 3) return -1;
            long h = Long.parseLong(hms[0].trim());
            long m = Long.parseLong(hms[1].trim());
            String[] sc = hms[2].split("\\.");
            long sec = Long.parseLong(sc[0].trim());
            long cs = sc.length > 1 ? Long.parseLong(sc[1].trim()) : 0;
            return h * 3600000L + m * 60000L + sec * 1000L + cs * 10L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Strips inline formatting, preserving line breaks. */
    public static String cleanCue(String cue) {
        if (cue == null) return null;
        String s = ASS_OVERRIDE.matcher(cue).replaceAll("");
        s = TAG.matcher(s).replaceAll("");
        s = s.replaceAll("\\{[yY]:[^}]*\\}", "");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        s = s.replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        String[] rows = s.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (String row : rows) {
            String r = row.replaceAll("[ \t\f\r]+", " ").trim();
            if (r.isEmpty()) continue;
            if (out.length() > 0) out.append('\n');
            out.append(r);
        }
        return MULTI_BLANK.matcher(out.toString()).replaceAll("\n\n");
    }
}

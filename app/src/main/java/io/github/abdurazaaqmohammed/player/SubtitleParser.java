package io.github.abdurazaaqmohammed.player;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser subtitle untuk format SRT, VTT, dan ASS/SSA.
 * Auto-detect format dari isi file.
 */
public class SubtitleParser {

    public static SubtitleTrack parse(File file) {
        if (file == null || !file.exists()) return null;
        SubtitleTrack track = new SubtitleTrack(file.getAbsolutePath());
        try {
            String content = readFile(file);
            if (content == null || content.isEmpty()) return null;
            String trimmed = content.trim();
            if (trimmed.startsWith("WEBVTT")) parseVTT(content, track);
            else if (trimmed.contains("[Script Info]") || trimmed.contains("[Events]")) parseASS(content, track);
            else parseSRT(content, track);
            return track.cues.isEmpty() ? null : track;
        } catch (Exception e) {
            return null;
        }
    }

    private static String readFile(File file) throws Exception {
        try (InputStream is = new FileInputStream(file)) {
            byte[] data = readAll(is);
            String content;
            if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
                content = new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
            } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
                content = new String(data, 2, data.length - 2, StandardCharsets.UTF_16LE);
            } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
                content = new String(data, 2, data.length - 2, StandardCharsets.UTF_16BE);
            } else {
                content = new String(data, StandardCharsets.UTF_8);
                if (content.contains("\uFFFD")) content = new String(data, StandardCharsets.ISO_8859_1);
            }
            return content;
        }
    }

    private static byte[] readAll(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static final Pattern SRT_TIME = Pattern.compile(
            "(\\d{1,2}):(\\d{2}):(\\d{2})[,.]?(\\d{1,3})?\\s*-->\\s*"
                    + "(\\d{1,2}):(\\d{2}):(\\d{2})[,.]?(\\d{1,3})?");

    private static void parseSRT(String content, SubtitleTrack track) {
        String[] blocks = content.split("\\r?\\n\\r?\\n");
        for (String block : blocks) {
            String[] lines = block.split("\\r?\\n");
            if (lines.length < 2) continue;
            int timeLineIdx = -1;
            Matcher m = null;
            for (int i = 0; i < lines.length; i++) {
                m = SRT_TIME.matcher(lines[i]);
                if (m.find()) { timeLineIdx = i; break; }
            }
            if (timeLineIdx < 0 || m == null) continue;
            long start = timeToMs(m.group(1), m.group(2), m.group(3), m.group(4));
            long end = timeToMs(m.group(5), m.group(6), m.group(7), m.group(8));
            StringBuilder sb = new StringBuilder();
            for (int i = timeLineIdx + 1; i < lines.length; i++) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(stripTags(lines[i]));
            }
            String text = sb.toString().trim();
            if (!text.isEmpty()) track.cues.add(new SubtitleTrack.Cue(start, end, text));
        }
    }

    private static void parseVTT(String content, SubtitleTrack track) {
        String[] blocks = content.split("\\r?\\n\\r?\\n");
        for (String block : blocks) {
            String[] lines = block.split("\\r?\\n");
            if (lines.length < 2) continue;
            int timeLineIdx = -1;
            Matcher m = null;
            for (int i = 0; i < lines.length; i++) {
                m = SRT_TIME.matcher(lines[i]);
                if (m.find()) { timeLineIdx = i; break; }
            }
            if (timeLineIdx < 0 || m == null) continue;
            long start = timeToMs(m.group(1), m.group(2), m.group(3), m.group(4));
            long end = timeToMs(m.group(5), m.group(6), m.group(7), m.group(8));
            StringBuilder sb = new StringBuilder();
            for (int i = timeLineIdx + 1; i < lines.length; i++) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(stripTags(lines[i]));
            }
            String text = sb.toString().trim();
            if (!text.isEmpty()) track.cues.add(new SubtitleTrack.Cue(start, end, text));
        }
    }

    private static final Pattern ASS_TIME = Pattern.compile("(\\d+):(\\d{2}):(\\d{2})[.:](\\d{2})");

    private static void parseASS(String content, SubtitleTrack track) {
        String[] lines = content.split("\\r?\\n");
        boolean inEvents = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("[Events]")) { inEvents = true; continue; }
            if (trimmed.startsWith("[") && inEvents) { inEvents = false; continue; }
            if (!inEvents) continue;
            if (!trimmed.startsWith("Dialogue:")) continue;
            String[] parts = trimmed.substring(9).split(",", 10);
            if (parts.length < 10) continue;
            String startStr = parts[1].trim();
            String endStr = parts[2].trim();
            String text = parts[9];
            Matcher m1 = ASS_TIME.matcher(startStr);
            Matcher m2 = ASS_TIME.matcher(endStr);
            if (!m1.find() || !m2.find()) continue;
            long start = timeToMs(m1.group(1), m1.group(2), m1.group(3), m1.group(4));
            long end = timeToMs(m2.group(1), m2.group(2), m2.group(3), m2.group(4));
            text = text.replaceAll("\\{[^}]*\\}", "").replace("\\N", "\n").replace("\\n", "\n").trim();
            if (!text.isEmpty()) track.cues.add(new SubtitleTrack.Cue(start, end, text));
        }
    }

    private static long timeToMs(String h, String m, String s, String ms) {
        long hours = parseLong(h, 0);
        long mins = parseLong(m, 0);
        long secs = parseLong(s, 0);
        long millis = 0;
        if (ms != null) {
            if (ms.length() == 1) millis = parseLong(ms, 0) * 100;
            else if (ms.length() == 2) millis = parseLong(ms, 0) * 10;
            else millis = parseLong(ms, 0);
        }
        return hours * 3600000L + mins * 60000L + secs * 1000L + millis;
    }

    private static long parseLong(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    private static String stripTags(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]+>", "").trim();
    }
}

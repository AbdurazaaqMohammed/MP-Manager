package io.github.abdurazaaqmohammed.player;

import java.util.ArrayList;
import java.util.List;

public class SubtitleTrack {

    public static class Cue {
        public long startMs;
        public long endMs;
        public String text;

        public Cue(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }

        public boolean isActive(long positionMs) {
            return positionMs >= startMs && positionMs < endMs;
        }
    }

    public String filePath;
    public String name;
    public String language;
    public List<Cue> cues = new ArrayList<>();

    public SubtitleTrack(String filePath) {
        this.filePath = filePath;
        this.name = new java.io.File(filePath).getName();
        this.language = detectLanguage(filePath);
    }

    public Cue getCueAt(long positionMs) {
        for (Cue cue : cues) {
            if (cue.isActive(positionMs)) return cue;
        }
        return null;
    }

    private static String detectLanguage(String path) {
        String name = new java.io.File(path).getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            String suffix = name.substring(lastDot + 1);
            if (suffix.length() <= 5 && suffix.matches("[a-z]{2,5}")) return suffix;
        }
        return null;
    }
}

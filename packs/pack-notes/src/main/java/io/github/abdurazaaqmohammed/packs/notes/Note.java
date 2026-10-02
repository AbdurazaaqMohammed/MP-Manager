package io.github.abdurazaaqmohammed.packs.notes;

import java.util.ArrayList;
import java.util.List;

public class Note {

    public String id = "";
    public String title = "";
    public String body = "";
    public String cipher = "";
    public boolean locked;
    public long created;
    public long updated;
    public long trashedAt;
    public boolean pinned;
    public boolean archived;
    public boolean trashed;
    public int color = -1;
    public List<String> tags = new ArrayList<>();

    public static Note create() {
        Note n = new Note();
        long now = System.currentTimeMillis();
        n.id = Long.toHexString(now) + Integer.toHexString((int) (Math.random() * 0xFFFFFF));
        n.created = now;
        n.updated = now;
        return n;
    }

    public boolean isBlank() {
        return title.trim().isEmpty() && plain().trim().isEmpty();
    }

    public String plain() {
        return locked ? "" : body;
    }

    public String displayTitle() {
        String t = title == null ? "" : title.trim();
        if (!t.isEmpty()) return t;
        String[] lines = plain().split("\n");
        for (String line : lines) {
            String v = Markdown.plain(line).trim();
            if (!v.isEmpty()) {
                return v.length() > 90 ? v.substring(0, 90) + "…" : v;
            }
        }
        return "";
    }

    public String preview(int maxLines) {
        StringBuilder sb = new StringBuilder();
        if (locked) return "Locked note";
        String t = title == null ? "" : title.trim();
        String[] lines = plain().split("\n");
        for (String line : lines) {
            String v = Markdown.plain(line).trim();
            if (v.isEmpty()) continue;
            if (!t.isEmpty() && v.equals(t)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(v);
            if (maxLines > 0 && sb.toString().split("\n").length >= maxLines) break;
        }
        return sb.toString();
    }

    public int words() {
        return locked ? 0 : Markdown.words(plain());
    }

    public int characters() {
        return locked ? 0 : plain().length();
    }

    public int checklistTotal() {
        return locked ? 0 : Markdown.checklistTotal(plain());
    }

    public int checklistDone() {
        return locked ? 0 : Markdown.checklistDone(plain());
    }

    public boolean isChecklist() {
        return !locked && Markdown.checklistTotal(plain()) > 0;
    }

    public int readMinutes() {
        int w = words();
        return Math.max(1, (w + 199) / 200);
    }

    public boolean hasTag(String tag) {
        if (tag == null || tags == null) return false;
        String needle = tag.trim().toLowerCase();
        for (String t : tags) {
            if (t != null && t.toLowerCase().equals(needle)) return true;
        }
        return false;
    }

    public void addTag(String tag) {
        if (tag == null) return;
        String v = tag.trim().replace("#", "");
        if (v.isEmpty()) return;
        if (tags == null) tags = new ArrayList<>();
        if (!hasTag(v)) tags.add(v);
    }

    public void removeTag(String tag) {
        if (tags == null || tag == null) return;
        String needle = tag.trim().toLowerCase();
        for (int i = tags.size() - 1; i >= 0; i--) {
            String t = tags.get(i);
            if (t != null && t.toLowerCase().equals(needle)) tags.remove(i);
        }
    }

    public Note copy() {
        Note n = new Note();
        n.id = Long.toHexString(System.currentTimeMillis())
                + Integer.toHexString((int) (Math.random() * 0xFFFFFF));
        n.title = title;
        n.body = body;
        n.color = color;
        n.pinned = false;
        n.archived = archived;
        n.locked = locked;
        if (tags != null) n.tags = new ArrayList<>(tags);
        long now = System.currentTimeMillis();
        n.created = now;
        n.updated = now;
        return n;
    }
}

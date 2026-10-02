package io.github.abdurazaaqmohammed.packs.time.notes;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class NoteStore {

    private static final String PREFS = "mp_notes";
    private static final String KEY = "notes_json";
    private static final String BACKUP_HEADER = "mp-notes-backup-v1";

    public static final int FILTER_ALL = 0;
    public static final int FILTER_PINNED = 1;
    public static final int FILTER_ARCHIVED = 2;
    public static final int FILTER_TRASH = 3;
    public static final int FILTER_TAGGED = 4;

    public static final int SORT_UPDATED = 0;
    public static final int SORT_CREATED = 1;
    public static final int SORT_TITLE = 2;

    private static NoteStore instance;

    private final Context context;
    private final Gson gson = new Gson();
    private final List<Note> notes = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public static synchronized NoteStore get(Context context) {
        if (instance == null) {
            instance = new NoteStore(context.getApplicationContext());
        }
        return instance;
    }

    private NoteStore(Context context) {
        this.context = context;
        load();
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void addListener(Runnable r) {
        if (r != null && !listeners.contains(r)) listeners.add(r);
    }

    public void removeListener(Runnable r) {
        listeners.remove(r);
    }

    private void load() {
        notes.clear();
        try {
            String raw = prefs().getString(KEY, "");
            if (raw == null || raw.isEmpty()) return;
            Type type = new TypeToken<List<Note>>() {
            }.getType();
            List<Note> loaded = gson.fromJson(raw, type);
            if (loaded != null) {
                for (Note n : loaded) {
                    if (n != null && n.id != null && !n.id.isEmpty()) {
                        if (n.tags == null) n.tags = new ArrayList<>();
                        notes.add(n);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void save() {
        try {
            prefs().edit().putString(KEY, gson.toJson(notes)).apply();
        } catch (Exception ignored) {
        }
        for (Runnable r : new ArrayList<>(listeners)) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }

    public List<Note> all() {
        return new ArrayList<>(notes);
    }

    public Note byId(String id) {
        if (id == null) return null;
        for (Note n : notes) {
            if (id.equals(n.id)) return n;
        }
        return null;
    }

    public Note create() {
        Note n = Note.create();
        notes.add(0, n);
        save();
        return n;
    }

    public void put(Note note) {
        if (note == null) return;
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(note.id)) {
                notes.set(i, note);
                save();
                return;
            }
        }
        notes.add(0, note);
        save();
    }

    public void touch(Note note) {
        if (note == null) return;
        note.updated = System.currentTimeMillis();
        save();
    }

    public void delete(Note note) {
        if (note == null) return;
        for (int i = 0; i < notes.size(); i++) {
            if (notes.get(i).id.equals(note.id)) {
                notes.remove(i);
                save();
                return;
            }
        }
    }
public List<Note> query(int filter, String tag, String search, int sort, boolean ascending) {
        List<Note> out = new ArrayList<>();
        String q = search == null ? "" : search.trim().toLowerCase();
        for (Note n : notes) {
            if (!matchesFilter(n, filter, tag)) continue;
            if (!q.isEmpty() && !matchesSearch(n, q)) continue;
            out.add(n);
        }
        sort(out, sort, ascending);
        return out;
    }

    private boolean matchesFilter(Note n, int filter, String tag) {
        if (filter == FILTER_TRASH) return n.trashed;
        if (n.trashed) return false;
        switch (filter) {
            case FILTER_PINNED:
                return n.pinned;
            case FILTER_ARCHIVED:
                return n.archived;
            case FILTER_TAGGED:
                return n.hasTag(tag);
            default:
                return !n.archived;
        }
    }

    private boolean matchesSearch(Note n, String q) {
        if (n.title != null && n.title.toLowerCase().contains(q)) return true;
        if (n.locked) return false;
        if (n.body != null && Markdown.plainText(n.body).toLowerCase().contains(q)) return true;
        if (n.tags != null) {
            for (String t : n.tags) {
                if (t != null && t.toLowerCase().contains(q)) return true;
            }
        }
        return false;
    }

    private void sort(List<Note> list, int sort, boolean ascending) {
        final boolean asc = ascending;
        Collections.sort(list, new Comparator<Note>() {
            @Override
            public int compare(Note a, Note b) {
                if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                int r;
                if (sort == SORT_CREATED) {
                    r = Long.compare(a.created, b.created);
                } else if (sort == SORT_TITLE) {
                    r = a.displayTitle().compareToIgnoreCase(b.displayTitle());
                } else {
                    r = Long.compare(a.updated, b.updated);
                }
                return asc ? r : -r;
            }
        });
    }

    public List<String> tags() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Note n : notes) {
            if (n.trashed || n.tags == null) continue;
            for (String t : n.tags) {
                if (t == null || t.trim().isEmpty()) continue;
                String key = t.trim();
                Integer c = counts.get(key);
                counts.put(key, c == null ? 1 : c + 1);
            }
        }
        List<String> out = new ArrayList<>(counts.keySet());
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public int countForTag(String tag) {
        int c = 0;
        for (Note n : notes) {
            if (!n.trashed && n.hasTag(tag)) c++;
        }
        return c;
    }

    public int count(int filter) {
        int c = 0;
        for (Note n : notes) {
            if (matchesFilter(n, filter, null)) c++;
        }
        return c;
    }

    public int pinnedCount() {
        return count(FILTER_PINNED);
    }

    public int archivedCount() {
        return count(FILTER_ARCHIVED);
    }

    public int allCount() {
        return count(FILTER_ALL);
    }

    public int totalWords() {
        int total = 0;
        for (Note n : notes) {
            if (!n.trashed && !n.locked) total += n.words();
        }
        return total;
    }

    public int totalCharacters() {
        int total = 0;
        for (Note n : notes) {
            if (!n.trashed && !n.locked) total += n.characters();
        }
        return total;
    }

    public int totalChecklistItems() {
        int total = 0;
        for (Note n : notes) {
            if (!n.trashed && !n.locked) total += n.checklistTotal();
        }
        return total;
    }

    public int totalChecklistDone() {
        int total = 0;
        for (Note n : notes) {
            if (!n.trashed && !n.locked) total += n.checklistDone();
        }
        return total;
    }

    public int createdThisWeek() {
        long week = 7L * 24L * 60L * 60L * 1000L;
        long now = System.currentTimeMillis();
        int c = 0;
        for (Note n : notes) {
            if (!n.trashed && now - n.created <= week) c++;
        }
        return c;
    }

    public Note oldest() {
        Note best = null;
        for (Note n : notes) {
            if (n.trashed) continue;
            if (best == null || n.created < best.created) best = n;
        }
        return best;
    }

    public Note longest() {
        Note best = null;
        for (Note n : notes) {
            if (n.trashed || n.locked) continue;
            if (best == null || n.characters() > best.characters()) best = n;
        }
        return best;
    }


    public void trash(Note note) {
        if (note == null) return;
        note.trashed = true;
        note.trashedAt = System.currentTimeMillis();
        save();
    }

    public void restore(Note note) {
        if (note == null) return;
        note.trashed = false;
        note.trashedAt = 0;
        save();
    }

    public void emptyTrash() {
        for (int i = notes.size() - 1; i >= 0; i--) {
            if (notes.get(i).trashed) notes.remove(i);
        }
        save();
    }

    public void purgeOlderThan(long millis) {
        long now = System.currentTimeMillis();
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note n = notes.get(i);
            if (n.trashed && n.trashedAt > 0 && now - n.trashedAt > millis) notes.remove(i);
        }
        save();
    }

    public int trashCount() {
        int c = 0;
        for (Note n : notes) {
            if (n.trashed) c++;
        }
        return c;
    }

public String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format\": \"").append(BACKUP_HEADER).append("\",\n");
        sb.append("  \"exported\": ").append(System.currentTimeMillis()).append(",\n");
        sb.append("  \"notes\": ").append(gson.toJson(notes)).append("\n}\n");
        return sb.toString();
    }

    public int importJson(String json, boolean replace) {
        if (json == null || json.trim().isEmpty()) return 0;
        int added = 0;
        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("{")) {
                com.google.gson.JsonObject root =
                        new com.google.gson.JsonParser().parse(trimmed).getAsJsonObject();
                if (root.has("notes")) trimmed = root.get("notes").toString();
            }
            Type type = new TypeToken<List<Note>>() {
            }.getType();
            List<Note> incoming = gson.fromJson(trimmed, type);
            if (incoming == null) return 0;
            if (replace) notes.clear();
            for (Note n : incoming) {
                if (n == null || n.id == null || n.id.isEmpty()) continue;
                if (n.tags == null) n.tags = new ArrayList<>();
                if (byId(n.id) != null) n.id = n.id + "_" + System.currentTimeMillis();
                notes.add(n);
                added++;
            }
            save();
        } catch (Exception ignored) {
        }
        return added;
    }

    public Note importText(String text, String fallbackTitle) {
        if (text == null || text.trim().isEmpty()) return null;
        String body = text.trim();
        String[] lines = body.split("\n", 2);
        String head = Markdown.plain(lines[0]).trim();
        Note n = create();
        if (lines.length > 1 && !head.isEmpty() && head.length() <= 80
                && lines[1].trim().length() > 0 && !lines[1].startsWith("#")) {
            n.title = head;
            n.body = lines[1];
        } else {
            n.title = head.isEmpty() ? (fallbackTitle == null ? "Imported note" : fallbackTitle) : head;
            n.body = lines.length > 1 ? body : "";
        }
        n.updated = System.currentTimeMillis();
        save();
        return n;
    }

    public String exportAllMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Notes backup\n\n");
        for (Note n : query(FILTER_ALL, null, "", SORT_UPDATED, false)) {
            sb.append(Markdown.toMarkdownFile(n)).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    public void removeTagEverywhere(String tag) {
        for (Note n : notes) n.removeTag(tag);
        save();
    }

    public void renameTagEverywhere(String from, String to) {
        if (to == null || to.trim().isEmpty()) return;
        for (Note n : notes) {
            if (n.hasTag(from)) {
                n.removeTag(from);
                n.addTag(to);
            }
        }
        save();
    }

}

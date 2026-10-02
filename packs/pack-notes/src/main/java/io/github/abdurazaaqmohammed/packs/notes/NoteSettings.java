package io.github.abdurazaaqmohammed.packs.notes;

import android.content.Context;
import android.content.SharedPreferences;

public class NoteSettings {

    private static final String PREFS = "mp_notes_settings";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final String[] FONT_NAMES = {"System", "Serif", "Monospace", "Cursive"};
    public static final String[] FONT_FAMILIES = {"sans-serif", "serif", "monospace", "cursive"};

    private static NoteSettings instance;

    private final SharedPreferences prefs;

    public static synchronized NoteSettings get(Context context) {
        if (instance == null) {
            instance = new NoteSettings(context.getApplicationContext());
        }
        return instance;
    }

    private NoteSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public int theme() {
        return prefs.getInt("theme", THEME_SYSTEM);
    }

    public void setTheme(int value) {
        prefs.edit().putInt("theme", value).apply();
    }

    public int font() {
        return clamp(prefs.getInt("font", 0), 0, FONT_NAMES.length - 1);
    }

    public void setFont(int value) {
        prefs.edit().putInt("font", clamp(value, 0, FONT_NAMES.length - 1)).apply();
    }

    public String fontFamily() {
        return FONT_FAMILIES[font()];
    }

    public String fontName() {
        return FONT_NAMES[font()];
    }

    public float textScale() {
        float v = prefs.getFloat("text_scale", 1f);
        return clamp(v, 0.8f, 1.6f);
    }

    public void setTextScale(float value) {
        prefs.edit().putFloat("text_scale", clamp(value, 0.8f, 1.6f)).apply();
    }

    public float lineSpacing() {
        float v = prefs.getFloat("line_spacing", 1.35f);
        return clamp(v, 1f, 2.2f);
    }

    public void setLineSpacing(float value) {
        prefs.edit().putFloat("line_spacing", clamp(value, 1f, 2.2f)).apply();
    }

    public int previewLines() {
        return clamp(prefs.getInt("preview_lines", 3), 1, 10);
    }

    public void setPreviewLines(int value) {
        prefs.edit().putInt("preview_lines", clamp(value, 1, 10)).apply();
    }

    public boolean grid() {
        return prefs.getBoolean("grid", false);
    }

    public void setGrid(boolean value) {
        prefs.edit().putBoolean("grid", value).apply();
    }

    public boolean showTimestamps() {
        return prefs.getBoolean("timestamps", true);
    }

    public void setShowTimestamps(boolean value) {
        prefs.edit().putBoolean("timestamps", value).apply();
    }

    public boolean relativeDates() {
        return prefs.getBoolean("relative_dates", true);
    }

    public void setRelativeDates(boolean value) {
        prefs.edit().putBoolean("relative_dates", value).apply();
    }

    public boolean confirmDelete() {
        return prefs.getBoolean("confirm_delete", true);
    }

    public void setConfirmDelete(boolean value) {
        prefs.edit().putBoolean("confirm_delete", value).apply();
    }

    public boolean showWordCount() {
        return prefs.getBoolean("word_count", true);
    }

    public void setShowWordCount(boolean value) {
        prefs.edit().putBoolean("word_count", value).apply();
    }

    public int accent() {
        return prefs.getInt("accent", -1);
    }

    public void setAccent(int value) {
        prefs.edit().putInt("accent", value).apply();
    }

    public int autoPurgeDays() {
        return clamp(prefs.getInt("auto_purge", 30), 0, 3650);
    }

    public void setAutoPurgeDays(int value) {
        prefs.edit().putInt("auto_purge", clamp(value, 0, 3650)).apply();
    }

    public boolean markdownShortcuts() {
        return prefs.getBoolean("md_shortcuts", true);
    }

    public void setMarkdownShortcuts(boolean value) {
        prefs.edit().putBoolean("md_shortcuts", value).apply();
    }

    public int sort() {
        return clamp(prefs.getInt("sort", NoteStore.SORT_UPDATED), 0, 2);
    }

    public void setSort(int value) {
        prefs.edit().putInt("sort", clamp(value, 0, 2)).apply();
    }

    public boolean ascending() {
        return prefs.getBoolean("ascending", false);
    }

    public void setAscending(boolean value) {
        prefs.edit().putBoolean("ascending", value).apply();
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}

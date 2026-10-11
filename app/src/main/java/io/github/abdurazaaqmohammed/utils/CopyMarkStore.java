package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nyimpen SATU SESI mark terakhir.
 * Cuma file dalam sesi terakhir yang dikasih warna HIJAU.
 *
 * Setiap mark baru → hapus sesi lama → simpan sesi baru.
 */
public class CopyMarkStore {

    private static final String PREF_KEY = "last_copy_session_paths";

    /**
     * Mark 1 file (sesi baru — ganti yang lama).
     */
    public static void markCopied(Context context, String path) {
        if (context == null || path == null || path.isEmpty()) return;
        markSession(context, Collections.singletonList(path));
    }

    /**
     * Mark 1 file sebagai hasil terbaru (hijau).
     * Shortcut buat operasi yang bikin 1 file aja.
     */
    public static void markResult(Context context, File file) {
        if (context == null || file == null) return;
        markSession(context, Collections.singletonList(file.getAbsolutePath()));
    }

    /**
     * Mark banyak file sebagai hasil terbaru.
     */
    public static void markResultFiles(Context context, List<File> files) {
        if (context == null || files == null || files.isEmpty()) return;
        List<String> paths = new ArrayList<>();
        for (File f : files) {
            if (f != null) paths.add(f.getAbsolutePath());
        }
        markSession(context, paths);
    }

    /**
     * Mark banyak file sekaligus (sesi baru — ganti yang lama).
     */
    public static void markSession(Context context, List<String> paths) {
        if (context == null || paths == null || paths.isEmpty()) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> newMarks = new HashSet<>();
            for (String p : paths) {
                if (p != null && !p.isEmpty()) newMarks.add(p);
            }
            prefs.edit().putStringSet(PREF_KEY, newMarks).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Cek apakah file ini bagian dari sesi terakhir.
     */
    public static boolean isCopied(Context context, String path) {
        if (context == null || path == null || path.isEmpty()) return false;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> marks = prefs.getStringSet(PREF_KEY, new HashSet<>());
            return marks.contains(path);
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Hapus 1 file dari sesi.
     */
    public static void unmarkCopied(Context context, String path) {
        if (context == null || path == null || path.isEmpty()) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> marks = new HashSet<>(prefs.getStringSet(PREF_KEY, new HashSet<>()));
            marks.remove(path);
            prefs.edit().putStringSet(PREF_KEY, marks).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Hapus semua mark (reset).
     */
    public static void clearAll(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().remove(PREF_KEY).apply();
        } catch (Exception ignored) {}
    }

    /**
     * Ambil semua file yang di-mark.
     */
    public static Set<String> getAll(Context context) {
        if (context == null) return new HashSet<>();
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return new HashSet<>(prefs.getStringSet(PREF_KEY, new HashSet<>()));
        } catch (Exception ignored) {
            return new HashSet<>();
        }
    }
}

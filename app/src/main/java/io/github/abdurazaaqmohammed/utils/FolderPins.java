package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Pane folder pins are independent of bookmark lists and survive app restarts. */
public final class FolderPins {
    private static final String KEY = "pane_folder_pins";
    private FolderPins() {}

    public static String key(File file) {
        if (file instanceof io.github.abdurazaaqmohammed.feature.files.network.RemoteFile remote)
            return remote.displayPath();
        if (file instanceof io.github.abdurazaaqmohammed.feature.ftp.FTPFileWrapper ftp)
            return ftp.displayPath();
        return SafFs.isSaf(file) ? file.getPath() : file.getAbsolutePath();
    }

    public static Set<String> load(Context context) {
        return new HashSet<>(PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(KEY, java.util.Collections.emptySet()));
    }

    public static boolean isPinned(Context context, File file) { return load(context).contains(key(file)); }

    public static void set(Context context, Iterable<? extends File> folders, boolean pinned) {
        Set<String> paths = load(context);
        for (File folder : folders) {
            if (folder == null || !folder.isDirectory() || "..".equals(folder.getName())) continue;
            if (pinned) paths.add(key(folder)); else paths.remove(key(folder));
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit().putStringSet(KEY, paths).apply();
    }

    /** Stable partition: keep the chosen sort within each group, even in reverse sort. */
    public static void sort(File[] files, Set<String> pins) {
        if (pins.isEmpty()) return;
        // Snapshot pin membership once; the comparator must not re-stat folders
        // O(n log n) times when opening a very large directory.
        Set<File> pinned = new HashSet<>();
        for (File file : files) if (file != null && file.isDirectory() && pins.contains(key(file))) pinned.add(file);
        Arrays.sort(files, java.util.Comparator.comparingInt(file ->
                pinned.contains(file) ? 0 : 1));
    }
}

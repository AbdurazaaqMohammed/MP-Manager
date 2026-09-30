package io.github.abdurazaaqmohammed.core.ui.theme;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Discovers theme palettes from two places:
 *   assets/ui_themes/*.json   built-in (system_default, light, dark, black, mt_dark)
 *   filesDir/ui_themes/*.json user imports (SAF copy target)
 *
 * Note assets/themes/ is the *editor* TextMate theme folder and must never be
 * used here; see UnifiedEditorFragment which reads "themes/" + name.
 */
public final class ThemeStore {

    public static final String ASSET_DIR = "ui_themes";

    private static final List<ThemePalette> CACHE = new ArrayList<>();
    private static boolean loaded;
    private static File userDir;

    private ThemeStore() {
    }

    public static synchronized List<ThemePalette> all(Context context) {
        load(context);
        return Collections.unmodifiableList(CACHE);
    }

    public static synchronized ThemePalette byId(Context context, String id) {
        if (id == null) return null;
        for (ThemePalette p : all(context)) {
            if (id.equals(p.id())) return p;
        }
        return null;
    }

    /** Drops the cache; call after an import or delete. */
    public static synchronized void invalidate() {
        loaded = false;
        CACHE.clear();
    }

    private static synchronized void load(Context context) {
        if (loaded) return;
        loaded = true;
        if (context == null) return;

        String[] assets;
        try {
            assets = context.getAssets().list(ASSET_DIR);
        } catch (IOException e) {
            assets = null;
        }
        if (assets != null) {
            for (String f : assets) {
                if (!f.endsWith(".json")) continue;
                try (InputStream in = context.getAssets().open(ASSET_DIR + "/" + f)) {
                    ThemePalette p = ThemeJson.parse(readAll(in), f, true);
                    if (p != null) CACHE.add(p);
                } catch (Exception ignored) {
                }
            }
        }

        File dir = userDir(context);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.getName().endsWith(".json")) continue;
                try (InputStream in = new FileInputStream(file)) {
                    ThemePalette p = ThemeJson.parse(readAll(in), file.getName(), false);
                    if (p != null) CACHE.add(p);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static synchronized File userDir(Context context) {
        if (userDir == null) {
            File dir = new File(context.getFilesDir(), ASSET_DIR);
            if (!dir.exists()) dir.mkdirs();
            userDir = dir;
        }
        return userDir;
    }

    /** @return true when the file was copied and parses as a theme. */
    public static synchronized boolean importFile(Context context, File source) {
        if (context == null || source == null || !source.canRead()) return false;
        try {
            ThemePalette parsed;
            try (InputStream in = new FileInputStream(source)) {
                parsed = ThemeJson.parse(readAll(in), source.getName(), false);
            }
            if (parsed == null) return false;
            File dest = new File(userDir(context), source.getName());
            copy(source, dest);
            invalidate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** @return true when a user-imported theme was removed. */
    public static synchronized boolean delete(Context context, String fileName) {
        if (context == null || fileName == null) return false;
        if (fileName.indexOf('/') >= 0 || fileName.contains("..")) return false;
        File f = new File(userDir(context), fileName);
        boolean ok = f.exists() && f.delete();
        invalidate();
        return ok;
    }

    private static void copy(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src);
             java.io.OutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}

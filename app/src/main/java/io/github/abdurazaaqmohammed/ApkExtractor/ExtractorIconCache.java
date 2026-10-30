package io.github.abdurazaaqmohammed.ApkExtractor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;

public final class ExtractorIconCache {

    private static final int MAX_FILES = 1000;

    private final LruCache<String, Drawable> memory = new LruCache<>(512) {
        @Override
        protected int sizeOf(String key, Drawable value) {
            return 1;
        }
    };

    private File dir(Context context) {
        File d = new File(context.getCacheDir(), "extractor_icons");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    private static String keyOf(String pkg, int versionCode) {
        return pkg + "_" + versionCode;
    }

    private static File fileOf(File dir, String pkg, int versionCode) {
        return new File(dir, keyOf(pkg, versionCode).replaceAll("[^a-zA-Z0-9_.-]", "_") + ".png");
    }

    public synchronized Drawable getMemory(String pkg, int versionCode) {
        if (pkg == null) return null;
        return memory.get(keyOf(pkg, versionCode));
    }

    public Drawable getDisk(Context context, String pkg, int versionCode) {
        if (pkg == null) return null;
        try {
            File f = fileOf(dir(context), pkg, versionCode);
            if (!f.isFile()) return null;
            Bitmap bm = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bm == null) {
                try { f.delete(); } catch (Exception ignored) {}
                return null;
            }
            Drawable d = new BitmapDrawable(context.getResources(), bm);
            synchronized (this) {
                memory.put(keyOf(pkg, versionCode), d);
            }
            return d;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void put(Context context, String pkg, int versionCode, Drawable icon) {
        if (pkg == null || icon == null) return;
        synchronized (this) {
            memory.put(keyOf(pkg, versionCode), icon);
        }
        try {
            File d = dir(context);
            File f = fileOf(d, pkg, versionCode);
            if (f.isFile()) return;
            Bitmap bm = toBitmap(icon);
            if (bm == null) return;
            try (OutputStream os = new FileOutputStream(f)) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            prune(d);
        } catch (Exception ignored) {}
    }

    private void prune(File d) {
        try {
            File[] files = d.listFiles();
            if (files == null || files.length <= MAX_FILES) return;
            Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (int i = 0; i < files.length - MAX_FILES; i++) {
                try { files[i].delete(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    public static Bitmap toBitmap(Drawable drawable) {
        try {
            if (drawable instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) drawable).getBitmap();
                if (b != null) return b;
            }
            int w = drawable.getIntrinsicWidth();
            int h = drawable.getIntrinsicHeight();
            if (w <= 0) w = 96;
            if (h <= 0) h = 96;
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, w, h);
            drawable.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }
}

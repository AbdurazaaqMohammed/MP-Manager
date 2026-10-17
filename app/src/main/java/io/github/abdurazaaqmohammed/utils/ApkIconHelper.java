package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ApkIconHelper {
    private ApkIconHelper() {}

    public static boolean isApkLikeExt(String extWithDot) {
        return ".apk".equals(extWithDot) || ".apks".equals(extWithDot)
                || ".xapk".equals(extWithDot) || ".apkm".equals(extWithDot)
                || ".aspk".equals(extWithDot);
    }

    public static boolean isApkLikeFile(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.ROOT);
        return l.endsWith(".apk") || l.endsWith(".apks") || l.endsWith(".xapk")
                || l.endsWith(".apkm") || l.endsWith(".aspk");
    }

    /**
     * True for archives that are really signable APKs. Split bundles
     * (.apks/.xapk/.apkm/.aspk) are containers, so signing one rewrites the
     * container rather than the app inside it.
     */
    public static boolean isApkArchiveName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    public static Drawable getApkIcon(Context context, String path) {
        if (context == null || path == null) return null;
        File src = new File(path);
        if (!src.isFile() || !src.canRead()) return null;
        long mtime = 0;
        long size = 0;
        try { mtime = src.lastModified(); size = src.length(); } catch (Exception ignored) {}
        File cached = diskCacheFile(context, path, mtime, size);
        if (cached != null && cached.isFile() && cached.length() > 0) {
            try {
                Bitmap bm = BitmapFactory.decodeFile(cached.getAbsolutePath());
                if (bm != null) return new BitmapDrawable(context.getResources(), bm);
            } catch (Exception ignored) {}
        }
        Drawable icon = null;
        try {
            String lower = path.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".apk")) {
                icon = loadFromPlainApk(context, path);
            } else {
                icon = loadFromSplitContainer(context, path);
            }
        } catch (Exception ignored) { icon = null; }
        if (icon != null && cached != null) {
            try { saveDrawableToFile(context, icon, cached); } catch (Exception ignored) {}
        }
        return icon;
    }

    private static Drawable loadFromPlainApk(Context context, String path) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageArchiveInfo(path, PackageManager.GET_ACTIVITIES);
            if (pi == null || pi.applicationInfo == null) return null;
            ApplicationInfo ai = pi.applicationInfo;
            if (TextUtils.isEmpty(ai.sourceDir) || TextUtils.isEmpty(ai.publicSourceDir)) {
                ai.sourceDir = path;
                ai.publicSourceDir = path;
            }
            return ai.loadIcon(pm);
        } catch (Exception ignored) { return null; }
    }

    private static Drawable loadFromSplitContainer(Context context, String path) {
        ZipFile zf = null;
        try {
            zf = new ZipFile(path);
            Enumeration<? extends ZipEntry> en = zf.entries();
            ZipEntry iconEntry = null;
            ZipEntry firstApkEntry = null;
            String firstApkName = null;
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e == null || e.isDirectory()) continue;
                String n = e.getName();
                if (n == null) continue;
                String ln = n.toLowerCase(Locale.ROOT);
                String base = ln.contains("/") ? ln.substring(ln.lastIndexOf('/') + 1) : ln;
                if (base.equals("icon.png")) {
                    iconEntry = e;
                    break;
                }
            }
            if (iconEntry != null) {
                InputStream is = null;
                try {
                    is = zf.getInputStream(iconEntry);
                    Bitmap bm = BitmapFactory.decodeStream(is);
                    if (bm != null) return new BitmapDrawable(context.getResources(), bm);
                } catch (Exception ignored) {
                } finally {
                    try { if (is != null) is.close(); } catch (Exception ignored) {}
                }
            }
            en = zf.entries();
            long bestSize = -1;
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e == null || e.isDirectory()) continue;
                String n = e.getName();
                if (n == null) continue;
                if (!n.toLowerCase(Locale.ROOT).endsWith(".apk")) continue;
                if (firstApkEntry == null) { firstApkEntry = e; firstApkName = n; bestSize = e.getSize(); continue; }
                String bn = n.toLowerCase(Locale.ROOT);
                String fb = firstApkName.toLowerCase(Locale.ROOT);
                boolean eBase = bn.endsWith("base.apk") || bn.endsWith("master.apk") || bn.endsWith("main.apk");
                boolean fBase = fb.endsWith("base.apk") || fb.endsWith("master.apk") || fb.endsWith("main.apk");
                if (eBase && !fBase) { firstApkEntry = e; firstApkName = n; bestSize = e.getSize(); }
                else if (!eBase && !fBase && e.getSize() > bestSize) { firstApkEntry = e; firstApkName = n; bestSize = e.getSize(); }
            }
            if (firstApkEntry == null) return null;
            File tmp = null;
            try {
                File dir = new File(context.getCacheDir(), "apk_icon_tmp");
                dir.mkdirs();
                tmp = new File(dir, "inner_" + System.currentTimeMillis() + ".apk");
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = zf.getInputStream(firstApkEntry);
                    os = new FileOutputStream(tmp);
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = is.read(buf)) != -1) os.write(buf, 0, r);
                } finally {
                    try { if (os != null) os.close(); } catch (Exception ignored) {}
                    try { if (is != null) is.close(); } catch (Exception ignored) {}
                }
                if (tmp.isFile() && tmp.length() > 0) return loadFromPlainApk(context, tmp.getAbsolutePath());
                return null;
            } finally {
                try { if (tmp != null) tmp.delete(); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (zf != null) zf.close(); } catch (Exception ignored) {}
        }
    }

    private static File diskCacheFile(Context context, String path, long mtime, long size) {
        try {
            File dir = new File(context.getCacheDir(), "apk_icon_cache");
            dir.mkdirs();
            int h = (path + "|" + mtime + "|" + size).hashCode();
            String name = Integer.toHexString(h) + ".png";
            return new File(dir, name);
        } catch (Exception ignored) { return null; }
    }

    private static void saveDrawableToFile(Context context, Drawable d, File out) {
        Bitmap bm = drawableToBitmap(d);
        if (bm == null) return;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(out);
            bm.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (Exception ignored) {
        } finally {
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
        }
    }

    private static Bitmap drawableToBitmap(Drawable d) {
        if (d == null) return null;
        try {
            if (d instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) d).getBitmap();
                if (b != null) return b;
            }
            int w = d.getIntrinsicWidth();
            int h = d.getIntrinsicHeight();
            if (w <= 0) w = 192;
            if (h <= 0) h = 192;
            Bitmap bm = Bitmap.createBitmap(Math.min(w, 512), Math.min(h, 512), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            d.setBounds(0, 0, c.getWidth(), c.getHeight());
            d.draw(c);
            return bm;
        } catch (Exception ignored) { return null; }
    }
}

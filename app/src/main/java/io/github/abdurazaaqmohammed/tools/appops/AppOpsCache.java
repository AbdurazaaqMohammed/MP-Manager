package io.github.abdurazaaqmohammed.tools.appops;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.List;

public class AppOpsCache {
    private static final int MAX_ICON_CACHE = 150;
    private static final LruCache<String, Drawable> iconCache = new LruCache<String, Drawable>(MAX_ICON_CACHE) {
        @Override
        protected int sizeOf(String key, Drawable value) {
            return 1;
        }
    };

    private static volatile List<AppItem> cachedAppList = null;
    private static volatile long lastAppListFetch = 0;

    public static Drawable getIcon(String packageName) {
        synchronized (iconCache) {
            return iconCache.get(packageName);
        }
    }

    public static void putIcon(String packageName, Drawable icon) {
        if (packageName == null || icon == null) return;
        synchronized (iconCache) {
            iconCache.put(packageName, icon);
        }
    }

    public static List<AppItem> getCachedAppList() {
        if (cachedAppList != null && (System.currentTimeMillis() - lastAppListFetch < 30_000)) {
            return new ArrayList<>(cachedAppList);
        }
        return null;
    }

    public static void setCachedAppList(List<AppItem> list) {
        cachedAppList = (list != null) ? new ArrayList<>(list) : null;
        lastAppListFetch = System.currentTimeMillis();
    }

    public static void clear() {
        synchronized (iconCache) {
            iconCache.evictAll();
        }
        cachedAppList = null;
        lastAppListFetch = 0;
    }
}

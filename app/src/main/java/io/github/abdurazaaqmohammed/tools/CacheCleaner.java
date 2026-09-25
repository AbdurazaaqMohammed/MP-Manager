package io.github.abdurazaaqmohammed.tools;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CacheCleaner {
    public static class QueueItem {
        public final String packageName;
        public final String label;

        public QueueItem(String packageName, String label) {
            this.packageName = packageName;
            this.label = label == null ? packageName : label;
        }
    }

    public interface Listener {
        void onAppStarted(String label, int index, int total);

        void onAppCleared(String packageName);

        void onAppSkipped(String packageName);

        void onFinished(int cleared, int total);
    }

    private static final String[] STORAGE_WORDS = new String[]{
            "storage", "almacenamiento", "stockage", "speicher", "armazenamento",
            "archiviazione", "spazio", "хранилище", "память", "depolama", "saklama",
            "kullanım", "تخزين", "مساحة", "存储", "儲存", "저장", "ストレージ", "ที่เก็บ", "penyimpanan"
    };
    private static final String[] CLEAR_WORDS = new String[]{
            "clear", "borrar", "vaciar", "vider", "effacer", "supprimer", "leeren",
            "löschen", "limpar", "apagar", "cancella", "svuota", "elimina",
            "очистить", "стереть", "temizle", "sil", "مسح", "清除", "清理",
            "지우기", "삭제", "消去", "クリア", "ล้าง", "xóa", "hapus"
    };
    private static final String[] CACHE_WORDS = new String[]{
            "cach", "кеш", "кэш", "önbellek", "缓存", "快取", "캐시", "キャッシュ", "แคช", "đệm"
    };
    private static final String[] KEEP_WORDS = new String[]{
            "stor", "data", "datos", "données", "donnee", "daten", "dados", "dati",
            "данн", "veril", "datum", "dữ liệu", "数据", "資料", "데이터", "データ", "ข้อมูล"
    };

    private static final Object LOCK = new Object();
    private static List<QueueItem> queue;
    private static int position;
    private static int cleared;
    private static int state;
    private static long deadline;
    private static long lastAction;
    private static Listener listener;
    private static Context appContext;
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Runnable watchdog = CacheCleaner::onWatchdog;

    private static final int STATE_FIND = 0;
    private static final int STATE_AFTER_CLEAR = 1;
    private static final int STATE_BACKING_OUT = 2;
    private static final long APP_TIMEOUT = 25000L;
    private static final long ACTION_COOLDOWN = 700L;

    public static boolean isServiceEnabled(Context context) {
        try {
            String flat = new ComponentName(context.getPackageName(), CacheCleanerService.class.getName()).flattenToString();
            String active = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (TextUtils.isEmpty(active)) return false;
            for (String part : active.split(":")) {
                if (flat.equalsIgnoreCase(part.trim())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void openAccessibilitySettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public static boolean isRunning() {
        synchronized (LOCK) {
            return queue != null;
        }
    }

    public static boolean start(Context context, List<QueueItem> items, Listener callback) {
        synchronized (LOCK) {
            if (queue != null) return false;
            if (items == null || items.isEmpty()) return false;
            if (!isServiceEnabled(context)) return false;
            queue = new ArrayList<>(items);
            position = -1;
            cleared = 0;
            listener = callback;
            appContext = context.getApplicationContext();
        }
        handler.post(watchdog);
        nextApp();
        return true;
    }

    public static void stop() {
        Listener done;
        int c;
        int t;
        synchronized (LOCK) {
            done = listener;
            c = cleared;
            t = queue == null ? 0 : queue.size();
            queue = null;
            listener = null;
        }
        handler.removeCallbacks(watchdog);
        if (done != null) done.onFinished(c, t);
    }

    public static void stopQuiet() {
        synchronized (LOCK) {
            queue = null;
            listener = null;
        }
        handler.removeCallbacks(watchdog);
    }

    static void onEvent(AccessibilityService service, AccessibilityEvent event) {
        List<QueueItem> q;
        synchronized (LOCK) {
            q = queue;
        }
        if (q == null || service == null) return;
        try {
            CharSequence pkg = event == null ? null : event.getPackageName();
            if (pkg != null && appContext != null && pkg.toString().equals(appContext.getPackageName())) return;
        } catch (Exception ignored) {
        }
        int type = event == null ? -1 : event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        drive(service);
    }

    private static void onWatchdog() {
        List<QueueItem> q;
        synchronized (LOCK) {
            q = queue;
        }
        if (q == null) return;
        long now = System.currentTimeMillis();
        boolean expired;
        synchronized (LOCK) {
            expired = now > deadline;
        }
        if (expired) {
            skipCurrent("timeout");
            return;
        }
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, 1000);
    }

    private static void nextApp() {
        QueueItem item;
        int total;
        synchronized (LOCK) {
            if (queue == null) return;
            position++;
            if (position >= queue.size()) {
                List<QueueItem> q = queue;
                queue = null;
                Listener done = listener;
                listener = null;
                int c = cleared;
                handler.removeCallbacks(watchdog);
                if (done != null) done.onFinished(c, q.size());
                return;
            }
            item = queue.get(position);
            total = queue.size();
            state = STATE_FIND;
            deadline = System.currentTimeMillis() + APP_TIMEOUT;
            lastAction = 0;
        }
        final QueueItem current = item;
        final int index = position;
        Listener cb;
        synchronized (LOCK) {
            cb = listener;
        }
        if (cb != null) cb.onAppStarted(current.label, index, total);
        handler.removeCallbacks(watchdog);
        handler.postDelayed(watchdog, 1000);
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + current.packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(intent);
        } catch (Exception e) {
            skipCurrent("open failed");
        }
    }

    private static void skipCurrent(String reason) {
        QueueItem item;
        synchronized (LOCK) {
            if (queue == null) return;
            item = position >= 0 && position < queue.size() ? queue.get(position) : null;
        }
        Listener cb;
        synchronized (LOCK) {
            cb = listener;
        }
        if (cb != null && item != null) cb.onAppSkipped(item.packageName);
        backOutThenNext();
    }

    private static void drive(AccessibilityService service) {
        int st;
        synchronized (LOCK) {
            if (queue == null) return;
            st = state;
        }
        if (st == STATE_BACKING_OUT) return;
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (now - lastAction < ACTION_COOLDOWN) return;
        }
        AccessibilityNodeInfo root;
        try {
            root = service.getRootInActiveWindow();
        } catch (Exception e) {
            return;
        }
        if (root == null) return;
        try {
            if (st == STATE_FIND) {
                AccessibilityNodeInfo clear = findClearCache(root);
                if (clear != null && clickNode(clear)) {
                    synchronized (LOCK) {
                        lastAction = System.currentTimeMillis();
                        state = STATE_AFTER_CLEAR;
                        deadline = lastAction + 6000;
                    }
                    handler.postDelayed(() -> {
                        synchronized (LOCK) {
                            if (queue == null || state != STATE_AFTER_CLEAR) return;
                        }
                        QueueItem item;
                        synchronized (LOCK) {
                            item = position >= 0 && position < queue.size() ? queue.get(position) : null;
                        }
                        Listener cb;
                        synchronized (LOCK) {
                            cb = listener;
                            cleared++;
                        }
                        if (cb != null && item != null) cb.onAppCleared(item.packageName);
                        backOutThenNext();
                    }, 1500);
                    return;
                }
                AccessibilityNodeInfo storage = findStorage(root);
                if (storage != null && clickNode(storage)) {
                    synchronized (LOCK) {
                        lastAction = System.currentTimeMillis();
                        deadline = lastAction + APP_TIMEOUT;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            try {
                root.recycle();
            } catch (Exception ignored) {
            }
        }
    }

    private static void backOutThenNext() {
        synchronized (LOCK) {
            if (queue == null) return;
            state = STATE_BACKING_OUT;
        }
        handler.postDelayed(() -> {
            synchronized (LOCK) {
                if (queue == null) return;
            }
            performBack();
            handler.postDelayed(() -> {
                synchronized (LOCK) {
                    if (queue == null) return;
                }
                performBack();
                handler.postDelayed(CacheCleaner::nextApp, 900);
            }, 700);
        }, 400);
    }

    private static void performBack() {
        try {
            AccessibilityService svc = activeService;
            if (svc != null) svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        } catch (Exception ignored) {
        }
    }

    private static volatile AccessibilityService activeService;

    static void setActiveService(AccessibilityService service) {
        activeService = service;
    }

    private static AccessibilityNodeInfo findStorage(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collectTextNodes(root, all);
        for (AccessibilityNodeInfo node : all) {
            String text = nodeText(node).toLowerCase(Locale.ROOT);
            if (text.isEmpty()) continue;
            if (containsAny(text, CLEAR_WORDS) && containsAny(text, CACHE_WORDS)) continue;
            if (!containsAny(text, STORAGE_WORDS)) continue;
            AccessibilityNodeInfo clickable = clickableAncestor(node);
            if (clickable != null) return clickable;
        }
        return null;
    }

    private static AccessibilityNodeInfo findClearCache(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> all = new ArrayList<>();
        collectTextNodes(root, all);
        for (AccessibilityNodeInfo node : all) {
            String text = nodeText(node).toLowerCase(Locale.ROOT);
            if (text.isEmpty()) continue;
            if (!containsAny(text, CLEAR_WORDS)) continue;
            if (!containsAny(text, CACHE_WORDS)) continue;
            if (containsAny(text, KEEP_WORDS)) continue;
            AccessibilityNodeInfo clickable = clickableAncestor(node);
            if (clickable == null) continue;
            try {
                if (!clickable.isEnabled()) continue;
            } catch (Exception ignored) {
            }
            return clickable;
        }
        return null;
    }

    private static void collectTextNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        try {
            CharSequence t = node.getText();
            if (t != null && t.length() > 0) out.add(node);
            int kids = node.getChildCount();
            for (int i = 0; i < kids; i++) {
                AccessibilityNodeInfo kid = null;
                try {
                    kid = node.getChild(i);
                } catch (Exception ignored) {
                }
                if (kid != null) collectTextNodes(kid, out);
            }
        } catch (Exception ignored) {
        }
    }

    private static String nodeText(AccessibilityNodeInfo node) {
        try {
            CharSequence t = node.getText();
            if (t != null && t.length() > 0) return t.toString();
            CharSequence d = node.getContentDescription();
            if (d != null && d.length() > 0) return d.toString();
        } catch (Exception ignored) {
        }
        return "";
    }

    private static AccessibilityNodeInfo clickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = node;
        while (current != null) {
            try {
                if (current.isClickable()) return current;
            } catch (Exception e) {
                return null;
            }
            AccessibilityNodeInfo parent;
            try {
                parent = current.getParent();
            } catch (Exception e) {
                return null;
            }
            if (parent == null) return null;
            if (current != node) {
                try {
                    current.recycle();
                } catch (Exception ignored) {
                }
            }
            current = parent;
        }
        return null;
    }

    private static boolean clickNode(AccessibilityNodeInfo node) {
        try {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean containsAny(String text, String[] words) {
        for (String w : words) {
            if (text.contains(w)) return true;
        }
        return false;
    }
}

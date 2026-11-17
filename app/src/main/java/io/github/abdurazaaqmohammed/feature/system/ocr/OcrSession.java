package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.abdurazaaqmohammed.plugins.api.PluginRegistry;
import io.github.abdurazaaqmohammed.plugins.api.ToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrEngine;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrResult;
import io.github.abdurazaaqmohammed.plugins.api.ocr.OcrToolPlugin;
import io.github.abdurazaaqmohammed.plugins.packs.PackCatalog;
import io.github.abdurazaaqmohammed.plugins.packs.PackDescriptor;
import io.github.abdurazaaqmohammed.plugins.packs.PackManager;

/**
 * Ephemeral, process-local capture/result handoff. Nothing is serialized to disk
 * or saved state. Engine initialization/inference/close share one worker.
 */
final class OcrSession {
    interface Listener { void changed(); }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "screen-ocr"));
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static Bitmap image;
    private static OcrResult result;
    private static boolean busy;
    private static String error;
    // Worker-thread-only fields.
    private static OcrEngine engine;
    private static String engineKey;
    private static final Runnable IDLE_RELEASE = OcrSession::releaseEngine;

    private OcrSession() {}
    static Bitmap image() { return image; }
    static OcrResult result() { return result; }
    static boolean busy() { return busy; }
    static String error() { return error; }
    static void observe(Listener listener) { LISTENERS.add(listener); listener.changed(); }
    static void removeObserver(Listener listener) { LISTENERS.remove(listener); }

    static void setImage(Bitmap bitmap) {
        GENERATION.incrementAndGet();
        image = bitmap;
        result = null;
        error = null;
        busy = false;
        notifyListeners();
    }

    static void cancelRecognition() {
        GENERATION.incrementAndGet();
        busy = false;
        notifyListeners();
    }

    static void clear() {
        keepEngineWarm();
        clearImage();
        releaseEngine();
    }

    /** Drop sensitive capture data but keep the initialized engine for fast repeat captures. */
    static void clearImage() {
        GENERATION.incrementAndGet();
        image = null;
        result = null;
        error = null;
        busy = false;
        notifyListeners();
    }

    static void releaseEngine() { WORKER.execute(OcrSession::closeEngine); }

    static void scheduleIdleRelease() {
        MAIN.removeCallbacks(IDLE_RELEASE);
        MAIN.postDelayed(IDLE_RELEASE, 60_000);
    }

    static void keepEngineWarm() { MAIN.removeCallbacks(IDLE_RELEASE); }

    static void recognize(Context context, Rect requestedRegion) {
        if (image == null || busy) return;
        keepEngineWarm();
        Context app = context.getApplicationContext();
        OcrModels.Language language = OcrModels.selected(app);
        Bitmap source = image;
        Rect region = new Rect(requestedRegion);
        if (!region.intersect(0, 0, source.getWidth(), source.getHeight()) || region.isEmpty()) return;
        int generation = GENERATION.incrementAndGet();
        busy = true;
        result = null;
        error = null;
        notifyListeners();
        WORKER.execute(() -> {
            Bitmap crop = null;
            try {
                if (GENERATION.get() != generation) return;
                File detection = OcrModels.file(app, OcrModels.DETECTOR);
                File recognition = OcrModels.file(app, language.model());
                String key = language.id() + ":" + recognition.lastModified() + ":" + detection.lastModified()
                        + ":" + PackManager.packFile(app, "ocr").lastModified();
                if (!key.equals(engineKey) || engine == null) {
                    closeEngine();
                    ToolPlugin plugin = ensurePlugin(app);
                    if (!(plugin instanceof OcrToolPlugin ocr)) throw new IllegalStateException("OCR Pack is not ready");
                    engine = ocr.createEngine(detection, recognition);
                    engineKey = key;
                }
                if (GENERATION.get() != generation) return;
                crop = Bitmap.createBitmap(source, region.left, region.top, region.width(), region.height());
                OcrResult recognized = engine.recognize(crop, () -> GENERATION.get() != generation)
                        .offset(region.left, region.top);
                MAIN.post(() -> {
                    if (GENERATION.get() != generation) return;
                    result = recognized;
                    busy = false;
                    notifyListeners();
                });
            } catch (CancellationException ignored) {
                // A newer capture, cancellation or activity close owns the UI now.
            } catch (Exception | LinkageError | OutOfMemoryError failure) {
                closeEngine();
                String reason = failure.getMessage() == null ? failure.toString() : failure.getMessage();
                MAIN.post(() -> {
                    if (GENERATION.get() != generation) return;
                    error = reason;
                    busy = false;
                    notifyListeners();
                });
            } finally {
                if (crop != null && crop != source) crop.recycle();
            }
        });
    }

    static ToolPlugin ensurePlugin(Context context) {
        ToolPlugin plugin = PluginRegistry.findCustom("screen_ocr");
        if (plugin != null) return plugin;
        PackDescriptor pack = PackCatalog.byId(PackCatalog.load(context), "ocr");
        if (pack != null && PackManager.isInstalled(context, pack.id)) {
            PackManager.loadPack(context, PackManager.packFile(context, pack.id), pack.entryClass, pack.id);
        }
        return PluginRegistry.findCustom("screen_ocr");
    }

    private static void closeEngine() {
        try { if (engine != null) engine.close(); } catch (Exception ignored) {}
        engine = null;
        engineKey = null;
    }

    private static void notifyListeners() {
        for (Listener listener : LISTENERS) listener.changed();
    }
}

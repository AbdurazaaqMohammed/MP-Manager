package io.github.abdurazaaqmohammed.feature.system.ocr;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit, cancellable downloads of data only. No remote code or inference requests. */
public final class OcrModelDownloads {
    public record State(String language, boolean running, long bytes, long total, String error) {}
    public interface Listener { void changed(State state); }
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> new Thread(r, "ocr-model-download"));
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final AtomicBoolean CANCELLED = new AtomicBoolean();
    private static volatile State state = new State("", false, 0, 0, null);
    private static volatile HttpURLConnection connection;
    private static final long MAX_ARTIFACT_BYTES = 64L * 1024 * 1024;

    private OcrModelDownloads() {}

    public static State state() { return state; }
    public static void observe(Listener listener) {
        LISTENERS.add(listener);
        listener.changed(state);
    }
    public static void removeObserver(Listener listener) { LISTENERS.remove(listener); }

    public static synchronized void download(Context context, OcrModels.Language language) {
        if (state.running) return;
        Context app = context.getApplicationContext();
        CANCELLED.set(false);
        publish(new State(language.id(), true, 0, 0, null));
        WORKER.execute(() -> {
            String error = null;
            try {
                downloadArtifact(app, language.id(), OcrModels.DETECTOR);
                downloadArtifact(app, language.id(), language.model());
            } catch (Exception failure) {
                if (!CANCELLED.get()) error = failure.getMessage() == null ? failure.toString() : failure.getMessage();
            } finally {
                publish(new State(language.id(), false, 0, 0, error));
            }
        });
    }

    public static void cancel() {
        CANCELLED.set(true);
        HttpURLConnection active = connection;
        if (active != null) active.disconnect();
    }

    private static void downloadArtifact(Context context, String language, OcrModels.Artifact artifact) throws Exception {
        File destination = OcrModels.file(context, artifact);
        if (destination.isFile() && artifact.sha256().equals(sha256(destination))) return;
        if (CANCELLED.get()) throw new IOException("Cancelled");
        File directory = destination.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create model directory");
        File temporary = new File(directory, artifact.filename() + ".part");
        HttpURLConnection download = null;
        try {
            URL url = new URL(artifact.url());
            // Validate every redirect: never downgrade an HTTPS model download.
            for (int redirect = 0; redirect <= 5; redirect++) {
                if (!"https".equals(url.getProtocol())) throw new IOException("Model download requires HTTPS");
                download = (HttpURLConnection) url.openConnection();
                connection = download;
                download.setConnectTimeout(15000);
                download.setReadTimeout(20000);
                download.setInstanceFollowRedirects(false);
                download.setRequestProperty("Accept-Encoding", "identity");
                int status = download.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = download.getHeaderField("Location");
                    download.disconnect();
                    if (location == null || redirect == 5) throw new IOException("Too many download redirects");
                    url = new URL(url, location);
                    continue;
                }
                if (status != 200) throw new IOException("Model server returned HTTP " + status);
                break;
            }
            long total = download.getContentLengthLong();
            if (total > MAX_ARTIFACT_BYTES || total > 0 && directory.getUsableSpace() < total + 1024 * 1024) {
                throw new IOException("Not enough space for this model");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0, lastUpdate = 0;
            try (InputStream input = download.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (CANCELLED.get()) throw new IOException("Cancelled");
                    bytes += read;
                    if (bytes > MAX_ARTIFACT_BYTES) throw new IOException("Model download is too large");
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    long now = System.nanoTime();
                    if (now - lastUpdate > 100_000_000) {
                        publish(new State(language, true, bytes, total, null));
                        lastUpdate = now;
                    }
                }
                output.getFD().sync();
            }
            if (CANCELLED.get()) throw new IOException("Cancelled");
            if (bytes == 0 || total > 0 && bytes != total || !artifact.sha256().equals(hex(digest.digest()))) {
                throw new IOException("Model checksum mismatch. The download was not installed.");
            }
            // Atomic replacement keeps a working model intact on interrupted updates.
            Os.rename(temporary.getAbsolutePath(), destination.getAbsolutePath());
        } finally {
            connection = null;
            if (download != null) download.disconnect();
            temporary.delete();
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format(Locale.ROOT, "%02x", value & 255));
        return result.toString();
    }

    private static void publish(State update) {
        state = update;
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) listener.changed(update);
        });
    }
}

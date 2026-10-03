package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Writes an uncaught exception somewhere a human can actually read it.
 *
 * <p>A launch crash on a device with no adb and no logcat access is otherwise
 * invisible: the process dies, the system shows a dialog with no stack trace,
 * and the only record is in a log ring buffer we cannot read from a shell.
 *
 * <p>Two sinks, because neither is reliable alone:
 * <ul>
 *   <li>{@code MediaStore.Downloads}, which needs no storage permission on
 *       API 29+ and lands in /storage/emulated/0/Download where a file browser
 *       or a shell can pick it up;</li>
 *   <li>an on-screen dialog showing the trace, which works even if the file
 *       write was denied.</li>
 * </ul>
 *
 * <p>Deliberately swallows everything: a crash reporter must not itself throw.
 */
public final class CrashLog {

    public static final String FILE_NAME = "mp-crash.log";
    private static final String TAG = "MPManager";

    private static boolean installed;

    private CrashLog() {
    }

    /** Idempotent; safe to call from every activity's onCreate. */
    public static void install(Context context) {
        if (installed) return;
        installed = true;

        final Context app = context.getApplicationContext();
        final String appContextRef = app == null ? "null" : app.getPackageName();

        Thread.UncaughtExceptionHandler previous =
                Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                String trace = describe(appContextRef, thread, error);
                Log.e(TAG, trace, error);
                writeInternal(app, trace);
                writeToDownloads(app, trace);
            } catch (Throwable ignored) {
                // Never mask the original crash.
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    private static String describe(String appContextRef, Thread thread, Throwable error) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("time    : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date()));
        pw.println("device  : Android " + Build.VERSION.RELEASE + " (API "
                + Build.VERSION.SDK_INT + ")");
        pw.println("build   : " + Build.FINGERPRINT);
        pw.println("thread  : " + thread.getName());
        pw.println("appCtx  : " + appContextRef);
        pw.println("--- trace ---");
        error.printStackTrace(pw);
        // printStackTrace covers causes; add the chain explicitly so the first
        // line of the dialog shows the real culprit rather than a wrapper.
        Throwable t = error;
        int depth = 0;
        while (t != null && depth < 10) {
            pw.println("cause[" + depth + "]: " + t.getClass().getName()
                    + ": " + t.getMessage());
            t = t.getCause();
            depth++;
        }
        pw.flush();
        return sw.toString();
    }

    private static void writeToDownloads(Context app, String trace) {
        if (app == null) return;
        OutputStream out = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
            }
            Uri collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            Uri item = app.getContentResolver().insert(collection, values);
            if (item == null) return;
            out = app.getContentResolver().openOutputStream(item);
            if (out == null) return;
            out.write(trace.getBytes(StandardCharsets.UTF_8));
            out.flush();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                app.getContentResolver().update(item, values, null, null);
            }
        } catch (Throwable ignored) {
            // Fall through: the dialog below is the other sink.
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Show a previously captured trace, if any.
     *
     * <p>Called on the next successful launch so the user can read the crash
     * even though the process that hit it is long gone.
     */
    public static void showPending(Activity activity) {
        String trace = readPending(activity);
        if (trace == null || trace.isEmpty()) return;
        // Clear it so the same trace is not shown on every launch.
        clearPending(activity);
        try {
            new AlertDialog.Builder(activity)
                    .setTitle(activity.getString(
                            io.github.abdurazaaqmohammed.MPManager.R.string.crash_log_title))
                    .setMessage(trace)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(false)
                    .show();
        } catch (Throwable ignored) {
            // A dialog failure must not stop the app from starting.
        }
    }

    private static File pendingFile(Context context) {
        File dir = context.getFilesDir();
        return dir == null ? null : new File(dir, FILE_NAME);
    }

    private static String readPending(Context context) {
        File f = pendingFile(context);
        if (f == null || !f.exists()) return null;
        try {
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                    StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void clearPending(Context context) {
        File f = pendingFile(context);
        if (f != null) f.delete();
    }

    /** Also kept in the app's own files dir, which survives if Downloads is denied. */
    static void writeInternal(Context context, String trace) {
        if (context == null) return;
        File f = pendingFile(context);
        if (f == null) return;
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            fos.write(trace.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        } catch (Throwable ignored) {
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Process;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppLogs {

    private static Context appContext;

    /** Captured once at process start so log paths can resolve the app dir pref. */
    public static void init(Context context) {
        if (context != null) appContext = context.getApplicationContext();
    }

    private static final Object LOG_LOCK = new Object();
    private static File sessionLogFile = null;

    public static File getLogsDir() {
        String base = UiPrefs.appPathDir(appContext,
                new File(Environment.getExternalStorageDirectory(), "MP Manager").getPath());
        File dir = new File(base, "logs");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getSessionLogFile() {
        synchronized (LOG_LOCK) {
            if (sessionLogFile == null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
                String timestamp = LocalDateTime.now().format(formatter);
                sessionLogFile = new File(getLogsDir(), "app_" + timestamp + ".log");
            }
            return sessionLogFile;
        }
    }

    public static void log(String tag, String message) {
        synchronized (LOG_LOCK) {
            try {
                File file = getSessionLogFile();
                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                String line = LocalDateTime.now().format(timeFmt) + " [" + tag + "] " + message + "\n";
                try (FileWriter fw = new FileWriter(file, true)) {
                    fw.write(line);
                }
            } catch (Exception ignored) {}
        }
    }

    public static void logError(String tag, String message, Throwable t) {
        synchronized (LOG_LOCK) {
            try {
                File file = getSessionLogFile();
                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                StringBuilder sb = new StringBuilder();
                sb.append(LocalDateTime.now().format(timeFmt)).append(" [ERROR/").append(tag).append("] ").append(message).append("\n");
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    t.printStackTrace(pw);
                    pw.flush();
                    sb.append(sw.toString()).append("\n");
                }
                try (FileWriter fw = new FileWriter(file, true)) {
                    fw.write(sb.toString());
                }
            } catch (Exception ignored) {}
        }
    }

    public static File newLogFile(String prefix) {
        return new File(getLogsDir(), prefix + "_" + System.currentTimeMillis() + ".txt");
    }

    public static File writeCrash(Throwable e, Context context) {
        try {
            logError("CRASH", "Uncaught exception", e);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

            String timestamp = LocalDateTime.now().format(formatter);

            File out = new File(getLogsDir(), "crash_" + timestamp + ".txt");

            //File out = new File(getLogsDir(), "crash_" + System.currentTimeMillis() + ".txt");
            FileWriter fw = new FileWriter(out, false);
            fw.write("MP Manager ");
            fw.write(getVersionName(context));
            fw.write("\nSDK ");
            fw.write(String.valueOf(Build.VERSION.SDK_INT));
            fw.write("\nDevice ");
            fw.write(Build.MANUFACTURER);
            fw.write(" ");
            fw.write(Build.MODEL);
            fw.write("\nAndroid ");
            fw.write(Build.VERSION.RELEASE);
            fw.write("\n");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            Throwable cause = e.getCause();
            while (cause != null) {
                pw.println("Caused by:");
                cause.printStackTrace(pw);
                cause = cause.getCause();
            }
            pw.flush();
            fw.write(sw.toString());
            fw.close();
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static File writeCrash(Throwable e) {
        return writeCrash(e, null);
    }

    private static String getVersionName(Context context) {
        if (context != null) {
            try {
                PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                if (pi.versionName != null) return "v" + pi.versionName;
            } catch (Exception ignored) {
            }
        }
        return "v1.0";
    }

    public static void install(Context context) {
        init(context);
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> {
            try {
                writeCrash(e, context);
            } catch (Exception ignored) {
            }
            if (prev != null && prev != Thread.getDefaultUncaughtExceptionHandler()) {
                try {
                    prev.uncaughtException(thread, e);
                    return;
                } catch (Exception ignored) {
                }
            }
            try {
                Process.killProcess(Process.myPid());
            } catch (Exception ignored) {
            }
            try {
                System.exit(2);
            } catch (Exception ignored) {
            }
        });
    }
}

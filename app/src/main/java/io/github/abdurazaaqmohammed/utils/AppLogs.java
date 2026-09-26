package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppLogs {
    public static File getLogsDir() {
        File dir = new File(new File(Environment.getExternalStorageDirectory(), "MP Manager"), "logs");
        dir.mkdirs();
        return dir;
    }

    public static File newLogFile(String prefix) {
        return new File(getLogsDir(), prefix + "_" + System.currentTimeMillis() + ".txt");
    }

    public static File writeCrash(Throwable e, Context context) {
        try {
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
                android.os.Process.killProcess(android.os.Process.myPid());
            } catch (Exception ignored) {
            }
            try {
                System.exit(2);
            } catch (Exception ignored) {
            }
        });
    }
}

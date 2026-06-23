package io.github.abdurazaaqmohammed.utils;

import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.reandroid.apk.APKLogger;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class LogUtil {
    public static APKLogger getApkLogger(TextView textView, Handler handler, Activity context) {
        boolean saveLog = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("logEnabled", false);
        try {
            FileWriter fw;
            if(saveLog) {
                File folder = new File(new File(Environment.getExternalStorageDirectory(), "MP Manager"), "logs");
                folder.mkdirs();
                File logFile = new File(folder, "log_" + System.currentTimeMillis() + ".txt");
                fw = new FileWriter(logFile, true);
            } else fw = null;
            return new APKLogger() {
                @Override
                public void logMessage(String s) {
                    handler.post(() -> textView.setText(s));
                    if(saveLog) try {fw.write(s);fw.write(System.lineSeparator());} catch (IOException ignored) {}
                }

                @Override
                public void logError(String s, Throwable throwable) {
                    new ErrorUtil(context).showError(throwable);
                    if(saveLog) try {
                        fw.write(s);
                        fw.write(System.lineSeparator());
                        for(StackTraceElement ste : throwable.getStackTrace()) fw.write(ste.toString());
                    } catch (IOException ignored) {}
                }

                @Override
                public void logVerbose(String s) {
                    handler.post(() -> textView.setText(s));
                    if(saveLog) try {fw.write(s);fw.write(System.lineSeparator());} catch (IOException ignored) {}
                }

                @Override
                public void close() { try { if (fw != null) fw.close(); } catch (IOException ignored) { } }
            };
        } catch (IOException e) { return new APKLogger() {
            @Override
            public void logMessage(String s) {
                handler.post(() -> textView.setText(s));
            }

            @Override
            public void logError(String s, Throwable throwable) {
                new ErrorUtil(context).showError(throwable);
            }

            @Override
            public void logVerbose(String s) {
                handler.post(() -> textView.setText(s));
            }
        }; }
    }
}
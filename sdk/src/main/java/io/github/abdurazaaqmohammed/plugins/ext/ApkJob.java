package io.github.abdurazaaqmohammed.plugins.ext;

import android.content.Context;

import java.io.File;

/**
 * Context handed to {@link ApkMoreAction#run}: the APK the info dialog was
 * opened for, plus the host context to show further dialogs from.
 */
public final class ApkJob {

    private final Context context;
    private final File file;
    private final String fileName;
    private final String filePath;

    public ApkJob(Context context, File file, String fileName, String filePath) {
        this.context = context;
        this.file = file;
        this.fileName = fileName;
        this.filePath = filePath;
    }

    /** Host context (MainActivity). */
    public Context context() {
        return context;
    }

    /** The APK file. */
    public File file() {
        return file;
    }

    /** Display name used by the dialog. */
    public String fileName() {
        return fileName;
    }

    /** Absolute path of the APK. */
    public String filePath() {
        return filePath;
    }
}

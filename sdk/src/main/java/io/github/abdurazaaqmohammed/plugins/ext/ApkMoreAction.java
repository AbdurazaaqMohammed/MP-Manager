package io.github.abdurazaaqmohammed.plugins.ext;

/**
 * Adds one entry to the APK info dialog's "More" functions list, appended
 * after the built-in functions in registration order.
 */
public interface ApkMoreAction extends AppExtension {

    /** Stable id, e.g. "mypack.apkscan". */
    String id();

    /** Entry title shown in the More list. */
    String title();

    /** Called on tap with the APK context. */
    void run(ApkJob job);
}

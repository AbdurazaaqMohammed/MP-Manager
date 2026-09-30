package io.github.abdurazaaqmohammed.plugins.ext;

import android.content.Context;

/**
 * Adds one button row to the app settings dialog ("Plugins" section).
 */
public interface SettingAction extends AppExtension {

    /** Stable id, e.g. "mypack.clearcache". */
    String id();

    /** Row title. */
    String title();

    /** Row summary, or "" for none. */
    String summary();

    /** Button caption, e.g. "Clear now". */
    String buttonText();

    /** Called on tap. Receives an Activity context. */
    void run(Context context);
}

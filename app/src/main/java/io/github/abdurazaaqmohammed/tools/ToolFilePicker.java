package io.github.abdurazaaqmohammed.tools;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * File choosing for the tools, through the system document picker.
 *
 * <p>SAF is used rather than walking storage directly: it needs no permission,
 * works the same on every Android version, and hands back a document the user
 * chose on purpose. The catch is that a content URI is not a path, so whatever a
 * tool needs to read is staged into the cache first. Every tool here works on a
 * copy anyway, which is what makes the output safe to hand back to the user.
 */
public final class ToolFilePicker {

    /** Request code for a single-document pick. */
    public static final int REQUEST_PICK = 4201;

    private ToolFilePicker() {
    }

    /** Opens the picker for any file. */
    public static void pick(Activity activity) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivityForResult(i, REQUEST_PICK);
        } catch (Exception ignored) {
        }
    }

    /** Opens the picker restricted to one mime type, e.g. "text/plain". */
    public static void pick(Activity activity, String mimeType) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mimeType);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivityForResult(i, REQUEST_PICK);
        } catch (Exception ignored) {
        }
    }

    /** The display name of a picked document, never null. */
    public static String displayName(Activity activity, Uri uri) {
        try (android.database.Cursor c = activity.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && c.getString(i) != null) return c.getString(i);
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }

    public static long size(Activity activity, Uri uri) {
        try (android.database.Cursor c = activity.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * Copies a picked document into the cache so it can be read as a file.
     *
     * @return the staged file, or null if it could not be copied.
     */
    public static File stage(Activity activity, Uri uri, String name) {
        File dir = new File(activity.getCacheDir(), "tool-input");
        if (!dir.exists() && !dir.mkdirs()) return null;
        File out = new File(dir, name == null ? "input" : name);
        try (InputStream in = activity.getContentResolver().openInputStream(uri);
             OutputStream os = new FileOutputStream(out)) {
            if (in == null) return null;
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) os.write(buf, 0, n);
        } catch (IOException | SecurityException e) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
            return null;
        }
        return out;
    }
}
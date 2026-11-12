package io.github.abdurazaaqmohammed.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Locale;

import io.github.ratul.topactivity.App;

/** Keeps app-uid shared-storage I/O consistent with Android's FUSE view. */
final class SharedStorageCompat {
    private SharedStorageCompat() {
    }

    private static String publicPath(File file) throws IOException {
        String path = file.getCanonicalPath();
        String relative;
        String primary = Environment.getExternalStorageDirectory().getCanonicalPath() + "/";
        if (path.startsWith(primary)) {
            relative = path.substring(primary.length());
        } else if (FileUtils.isOnSecondaryVolume(new File(path))) {
            int slash = path.indexOf('/', "/storage/".length());
            if (slash < 0) return null;
            relative = path.substring(slash + 1);
        } else {
            return null;
        }
        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.equals("android/data") || lower.startsWith("android/data/")
                || lower.equals("android/obb") || lower.startsWith("android/obb/")) return null;
        return path;
    }

    /** Public files should be unlinked through FUSE, even when root is enabled. */
    static boolean canDeleteThroughApp(File file) {
        if (Build.VERSION.SDK_INT < 30 || !Environment.isExternalStorageManager()) return false;
        try {
            File parent = file.getParentFile();
            return publicPath(file) != null && file.exists() && file.canWrite()
                    && parent != null && parent.canWrite();
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * A raw /data/media unlink can leave a positive FUSE dentry behind. Opening
     * the indexed destination through MediaStore recreates the lower file and
     * invalidates that dentry. "rw" deliberately does NOT truncate or delete
     * anything: an existing file, including one created concurrently, is safe.
     * Normal missing-parent and permission errors are left to the caller.
     */
    static boolean recoverDestination(File file) {
        if (Build.VERSION.SDK_INT < 30 || !Environment.isExternalStorageManager()) return false;
        Context context = App.getInstance();
        if (context == null) return false;
        try {
            String path = publicPath(file);
            File parent = file.getParentFile();
            if (path == null || parent == null || !parent.isDirectory() || file.isDirectory()) return false;

            // exists()/lstat() can both return cached attributes. A real open
            // must report ENOENT before we attempt to repair the destination.
            try {
                FileDescriptor descriptor = Os.open(file.getAbsolutePath(),
                        OsConstants.O_RDONLY | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0);
                Os.close(descriptor);
                return false;
            } catch (ErrnoException error) {
                if (error.errno != OsConstants.ENOENT) return false;
            }

            Uri collection = MediaStore.Files.getContentUri("external");
            Uri uri;
            try (Cursor cursor = context.getContentResolver().query(collection,
                    new String[]{MediaStore.MediaColumns._ID}, MediaStore.MediaColumns.DATA + "=?",
                    new String[]{path}, null)) {
                if (cursor == null || !cursor.moveToFirst()) return false;
                uri = ContentUris.withAppendedId(collection, cursor.getLong(0));
            }
            try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "rw")) {
                return descriptor != null;
            }
        } catch (Exception ignored) {
            // Keep the original I/O error if the provider cannot repair it.
            return false;
        }
    }
}

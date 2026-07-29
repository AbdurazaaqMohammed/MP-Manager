package io.github.abdurazaaqmohammed.commandhelper;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class FileUtils {

    public static String getFileName(Context context, Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("file")) {
            fileName = uri.getLastPathSegment();
        } else {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                }
            } catch (Exception ignored) {
            }
            if (fileName == null) {
                fileName = uri.getLastPathSegment();
            }
        }
        return fileName;
    }
}

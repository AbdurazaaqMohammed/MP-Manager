package io.github.abdurazaaqmohammed.feature.files;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Locale;

import io.github.abdurazaaqmohammed.ui.main.MainActivity;
import io.github.abdurazaaqmohammed.ui.main.FileIconSpec;

/** Shortcut identity, bitmap rendering and explicit navigation are shared by both launcher modes. */
public final class BookmarkShortcuts {
    public static final String ACTION_OPEN = "io.github.abdurazaaqmohammed.OPEN_BOOKMARK";
    public static final String EXTRA_PATH = "bookmark_shortcut_path";
    public static final String EXTRA_DIRECTORY = "bookmark_shortcut_directory";
    private BookmarkShortcuts() {}

    public static String id(String path) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(path.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("bookmark_");
            for (byte b : hash) id.append(String.format(Locale.ROOT, "%02x", b & 255));
            return id.toString();
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    public static Intent intent(Context context, File file, boolean directory) {
        return new Intent(context, MainActivity.class).setAction(ACTION_OPEN)
                .putExtra(EXTRA_PATH, file.getPath()).putExtra(EXTRA_DIRECTORY, directory)
                .putExtra("locatePath", file.getPath()) // Suppress the ordinary cold-start pane-1 load.
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    public static ShortcutInfoCompat info(Context context, File file, boolean directory, String name, Bitmap icon) {
        return new ShortcutInfoCompat.Builder(context, id(file.getPath()))
                .setShortLabel(name).setLongLabel(name).setIntent(intent(context, file, directory))
                .setIcon(IconCompat.createWithBitmap(icon)).build();
    }

    public static boolean create(Context context, ShortcutInfoCompat info, boolean launcher) {
        if (launcher) return ShortcutManagerCompat.requestPinShortcut(context, info, null);
        // addDynamicShortcuts fails cleanly at the launcher's limit; never remove
        // unrelated tool/DNS shortcuts to make room for a bookmark.
        return ShortcutManagerCompat.addDynamicShortcuts(context, Collections.singletonList(info));
    }

    public static Bitmap render(Context context, FileIconSpec spec, int foreground, int background,
                                boolean withBackground, Bitmap image, boolean tintImage) {
        final int size = 192;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (withBackground) {
            paint.setColor(background);
            canvas.drawRoundRect(new RectF(0, 0, size, size), size * .2f, size * .2f, paint);
        }
        int inset = withBackground ? Math.round(size * .2f) : 0;
        if (image != null) {
            int side = Math.min(image.getWidth(), image.getHeight());
            Rect source = new Rect((image.getWidth() - side) / 2, (image.getHeight() - side) / 2,
                    (image.getWidth() + side) / 2, (image.getHeight() + side) / 2);
            if (tintImage) paint.setColorFilter(new PorterDuffColorFilter(foreground, PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(image, source, new Rect(inset, inset, size - inset, size - inset), paint);
        } else {
            Drawable glyph = ContextCompat.getDrawable(context, spec.glyph());
            if (glyph != null) {
                glyph = glyph.mutate();
                glyph.setTint(foreground);
                glyph.setBounds(inset, inset, size - inset, size - inset);
                glyph.draw(canvas);
            }
        }
        return bitmap;
    }
}

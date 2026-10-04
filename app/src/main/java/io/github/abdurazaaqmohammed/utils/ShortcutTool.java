package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.Collections;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

/**
 * Putting a file or folder on the home screen.
 *
 * <p>Uses the pinned-shortcut API where it exists, so the launcher shows its own
 * permission dialog rather than the shortcut appearing silently. On launchers
 * that predate it the request simply returns false and the caller says so,
 * instead of the shortcut looking like it worked when it did not.
 *
 * <p>Each shortcut opens this app at that file, which is why the intent carries
 * an extra rather than pointing at the file's own mime type: a video or an
 * archive has no handler worth opening, but the file manager does.
 */
public final class ShortcutTool {

    /** Where the file manager should go when the shortcut is tapped. */
    public static final String EXTRA_PATH = "io.github.abdurazaaqmohammed.extra.SHORTCUT_PATH";
    /** Optional: highlight this file once the folder is open. */
    public static final String EXTRA_FOCUS = "io.github.abdurazaaqmohammed.extra.SHORTCUT_FOCUS";
    /** Which of the five actions this shortcut carries; see ShortcutActionStore. */
    public static final String EXTRA_ACTION = "io.github.abdurazaaqmohammed.extra.SHORTCUT_ACTION";

    private ShortcutTool() {
    }

    /** The launcher activity that opens the file manager. */
    private static Intent launcherIntent(Context context, File target) {
        Intent main = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        Intent i = new Intent(main == null ? Intent.ACTION_VIEW : main.getAction())
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (main != null && main.getComponent() != null) i.setComponent(main.getComponent());
        i.putExtra(EXTRA_PATH, target.getAbsolutePath());
        i.putExtra(EXTRA_FOCUS, target.getName());
        return i;
    }

    /**
     * Asks the launcher to pin a shortcut to {@code target}.
     *
     * @return true when the request was accepted; false means the launcher does
     *         not support it, and nothing was created.
     */
    public static boolean pinToHome(Context context, File target) {
        String name = target.getName();
        try {
            ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(context, shortcutId(target))
                    .setShortLabel(shortLabel(name))
                    .setLongLabel(name)
                    .setIcon(IconCompat.createWithBitmap(iconFor(context, target)))
                    .setIntent(launcherIntent(context, target))
                    .build();
            return ShortcutManagerCompat.requestPinShortcut(context, info, null);
        } catch (Exception e) {
            return false;
        }
    }

    /** Removes a previously pinned shortcut. Returns true if one was removed. */
    public static boolean unpin(Context context, File target) {
        try {
            return ShortcutManagerCompat.removeDynamicShortcut(context,
                    Collections.singletonList(shortcutId(target)));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isPinned(Context context, File target) {
        try {
            return ShortcutManagerCompat.isRequestPinShortcutSupported(context)
                    && ShortcutManagerCompat.getShortcuts(context, android.content.pm.ShortcutManager.FLAG_MATCH_PINNED)
                    .stream().anyMatch(s -> s.getId().equals(shortcutId(target)));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSupported(Context context) {
        try {
            return ShortcutManagerCompat.isRequestPinShortcutSupported(context);
        } catch (Exception e) {
            return false;
        }
    }

    private static String shortcutId(File target) {
        // Ids must be unique per shortcut and may only contain certain characters.
        return "mp_" + Integer.toHexString(target.getAbsolutePath().hashCode()) + "_shortcut";
    }

    /** Keeps the label from filling the whole tile on a long name. */
    private static String shortLabel(String name) {
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name);
        return base.length() <= 14 ? base : base.substring(0, 13) + "…";
    }

    /** Renders the file's own icon at launcher size. */
    private static Bitmap iconFor(Context context, File target) {
        Drawable d = null;
        try {
            if (target.isDirectory()) {
                d = context.getResources().getDrawable(
                        io.github.abdurazaaqmohammed.MPManager.R.drawable.folder_24px, null);
            }
        } catch (Exception ignored) {
        }
        if (d == null) d = new BitmapDrawable(context.getResources(),
                android.graphics.BitmapFactory.decodeResource(
                        context.getResources(), io.github.abdurazaaqmohammed.MPManager.R.drawable.apk_document_24px));
        return toBitmap(d, 192);
    }

    private static Bitmap toBitmap(Drawable d, int sizePx) {
        if (d == null) return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return ((BitmapDrawable) d).getBitmap();
        }
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, sizePx, sizePx);
        d.draw(c);
        return bmp;
    }
}
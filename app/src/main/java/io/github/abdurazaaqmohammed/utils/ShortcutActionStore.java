package io.github.abdurazaaqmohammed.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import java.io.File;

/**
 * Home screen shortcuts that carry an action, not just an icon.
 *
 * <p>Five actions are offered: locate the file, locate and open it, open it in
 * the text editor, run it as a script, or preview it as HTML.
 *
 * <p>Each one launches this app with an instruction rather than trying to reach
 * into another app's window. That is a deliberate limit: from Android 10 an
 * accessibility service cannot click through to arbitrary apps' controls without
 * the user doing it in the accessibility dialog each time, so a "locate and
 * click" shortcut that silently did nothing would be worse than one that opens
 * the right place and lets the user tap. Locate-and-click therefore brings the
 * folder up with the file highlighted and then opens it, which is the part that
 * can be done reliably.
 */
public final class ShortcutActionStore {

    public static final int ACT_LOCATE = 0;
    public static final int ACT_LOCATE_CLICK = 1;
    public static final int ACT_EDITOR = 2;
    public static final int ACT_SCRIPT = 3;
    public static final int ACT_HTML = 4;

    private ShortcutActionStore() {
    }

    /** Reads the action a shortcut was created with, or -1 when there is none. */
    public static int actionOf(Intent intent) {
        if (intent == null) return -1;
        return intent.getIntExtra(ShortcutTool.EXTRA_ACTION, -1);
    }

    /** The file a shortcut points at, or null. */
    public static String pathOf(Intent intent) {
        return intent == null ? null : intent.getStringExtra(ShortcutTool.EXTRA_PATH);
    }

    public static boolean create(Context context, File target, int action) {
        String name = target.getName();
        try {
            ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(context, id(target, action))
                    .setShortLabel(label(name))
                    .setLongLabel(name)
                    .setIcon(IconCompat.createWithBitmap(icon(context, target)))
                    .setIntent(intentFor(context, target, action))
                    .build();
            return ShortcutManagerCompat.requestPinShortcut(context, info, null);
        } catch (Exception e) {
            return false;
        }
    }

    private static Intent intentFor(Context context, File target, int action) {
        Intent main = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        Intent i = new Intent(main == null ? Intent.ACTION_VIEW : main.getAction())
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (main != null && main.getComponent() != null) i.setComponent(main.getComponent());
        i.putExtra(ShortcutTool.EXTRA_PATH, target.getAbsolutePath());
        i.putExtra(ShortcutTool.EXTRA_FOCUS, target.getName());
        i.putExtra(ShortcutTool.EXTRA_ACTION, action);
        return i;
    }

    private static String id(File target, int action) {
        return "mp_act" + action + "_"
                + Integer.toHexString(target.getAbsolutePath().hashCode());
    }

    private static String label(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base.length() <= 14 ? base : base.substring(0, 13) + "…";
    }

    private static Bitmap icon(Context context, File target) {
        Drawable d = null;
        int res = io.github.abdurazaaqmohammed.MPManager.R.drawable.apk_document_24px;
        if (target.isDirectory()) {
            res = io.github.abdurazaaqmohammed.MPManager.R.drawable.folder_24px;
        }
        try {
            d = context.getResources().getDrawable(res, null);
        } catch (Exception ignored) {
        }
        if (d == null) d = new BitmapDrawable(context.getResources(),
                android.graphics.BitmapFactory.decodeResource(context.getResources(), res));
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != null) {
            return ((BitmapDrawable) d).getBitmap();
        }
        Bitmap bmp = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, 192, 192);
        d.draw(c);
        return bmp;
    }
}
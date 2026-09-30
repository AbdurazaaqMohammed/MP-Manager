package io.github.abdurazaaqmohammed.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;



import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.core.ui.UIKit;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemePalette;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeRegistry;
import io.github.abdurazaaqmohammed.core.ui.theme.ThemeStore;

/**
 * Lists every theme in ThemeStore (built-in JSON + imported files), lets the
 * user apply or delete one, and imports new ones through SAF.
 *
 * Built programmatically rather than with a layout: the list length is not
 * known ahead of time and the rows are plain text.
 */
public final class ThemePickerDialog {

    public static final int REQ_IMPORT_THEME = 0x7E11;

    private ThemePickerDialog() {
    }

    public static void show(Activity activity) {
        buildList(activity);
    }

    /**
     * Long-press entry point: a built-in theme is a no-op (toast), an imported
     * one asks to be deleted.
     */
    public static void onThemeLongPressed(Activity activity, String themeId) {
        ThemePalette p = ThemeStore.byId(activity, themeId);
        if (p == null) return;
        if (p.isBuiltin()) {
            UIKit.toast(activity, p.name());
            return;
        }
        offerDelete(activity, p);
    }

    private static void buildList(Context context) {
        ThemeStore.invalidate();
        final List<ThemePalette> all = ThemeStore.all(context);
        final String current = ThemeRegistry.getCurrentId(context);
        String[] labels = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            ThemePalette p = all.get(i);
            StringBuilder sb = new StringBuilder(p.name());
            if (p.id().equals(current)) sb.append("  ✓");
            if (!p.isBuiltin()) sb.append("  [").append(p.fileName()).append("]");
            labels[i] = sb.toString();
        }

        UIKit.dialog(context)
                .setTitle(R.string.choose_theme)
                .setItems(labels, (d, which) -> applyAndReload(context, all.get(which)))
                .setNeutralButton(R.string.import_theme_json, (d, w) -> promptImport(context))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void applyAndReload(Context context, ThemePalette palette) {
        ThemeRegistry.setCurrentId(context, palette.id());
        if (context instanceof Activity) ((Activity) context).recreate();
    }

    private static void promptImport(Context context) {
        if (!(context instanceof Activity)) return;
        Activity a = (Activity) context;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
                    .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain"});
            a.startActivityForResult(intent, REQ_IMPORT_THEME);
        } catch (Exception e) {
            UIKit.toast(a, a.getString(R.string.theme_import_failed));
        }
    }

    /** Long-press handler: offer to delete an imported theme. */
    public static void offerDelete(final Context context, final ThemePalette palette) {
        if (palette == null || palette.isBuiltin()) {
            UIKit.toast(context, context.getString(R.string.theme_delete_failed));
            return;
        }
        UIKit.dialog(context)
                .setTitle(palette.name())
                .setMessage(palette.fileName())
                .setPositiveButton(R.string.delete_theme, (d, w) -> {
                    boolean ok = ThemeStore.delete(context, palette.fileName());
                    UIKit.toast(context, context.getString(
                            ok ? R.string.theme_deleted : R.string.theme_delete_failed));
                    if (ok && palette.id().equals(ThemeRegistry.getCurrentId(context))) {
                        ThemeRegistry.setCurrentId(context,
                                io.github.abdurazaaqmohammed.core.ui.theme.BuiltInThemes.SYSTEM_DEFAULT_ID);
                    }
                    buildList(context);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Handle the SAF result. Call from the host activity's onActivityResult.
     *
     * @return true when the URI was consumed.
     */
    public static boolean handleImportResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_IMPORT_THEME) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        File tmp = null;
        try {
            String name = queryName(activity, uri);
            tmp = new File(activity.getCacheDir(), "theme_import_" + name);
            try (InputStream in = activity.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            boolean ok = ThemeStore.importFile(activity, tmp);
            UIKit.toast(activity, activity.getString(
                    ok ? R.string.theme_imported : R.string.theme_import_failed));
            if (ok) buildList(activity);
        } catch (Exception e) {
            UIKit.toast(activity, activity.getString(R.string.theme_import_failed));
        } finally {
            if (tmp != null) tmp.delete();
        }
        return true;
    }

    private static String queryName(Context context, Uri uri) {
        String name = "theme.json";
        try (android.database.Cursor c = context.getContentResolver()
                .query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.getString(idx) != null) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        }
        if (!name.endsWith(".json")) name = name + ".json";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}

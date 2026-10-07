package io.github.abdurazaaqmohammed.packs.device;

import io.github.abdurazaaqmohammed.plugins.res.PackRes;

import android.app.WallpaperManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import io.github.abdurazaaqmohammed.plugins.api.BaseToolPlugin;
import io.github.abdurazaaqmohammed.plugins.api.ToolCategories;
import io.github.abdurazaaqmohammed.plugins.tools.common.ToolViewFactory;
import io.github.abdurazaaqmohammed.ui.views.ColorWheelView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * Extraction of ToolRunnerActivity.buildWallpaper().
 *
 * <p>The built-in "Locate file" opened the host file manager; packs cannot
 * do that, so it became "Open file" via a system viewer instead.
 */
public class WallpaperTool extends BaseToolPlugin {

    public WallpaperTool() {
        super("wallpaper", "Wallpaper Maker", "Design gradient wallpapers", ToolCategories.DEVICE);
    }

    private static void renderWallpaperPreview(Context context, ImageView preview, int first, int second, boolean gradient, String direction) {
        if (preview == null) return;
        try {
            DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int w = Math.max(540, Math.min(1080, dm.widthPixels));
            int h = Math.max(960, Math.min(1920, dm.heightPixels));
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            if (!gradient) {
                bmp.eraseColor(first);
            } else if ("Horizontal".equals(direction)) {
                int[] row = new int[w];
                for (int x = 0; x < w; x++) {
                    float t = x / (float) w;
                    row[x] = Color.argb(
                            Math.round(Color.alpha(first) * (1 - t) + Color.alpha(second) * t),
                            Math.round(Color.red(first) * (1 - t) + Color.red(second) * t),
                            Math.round(Color.green(first) * (1 - t) + Color.green(second) * t),
                            Math.round(Color.blue(first) * (1 - t) + Color.blue(second) * t));
                }
                for (int y = 0; y < h; y++) bmp.setPixels(row, 0, w, 0, y, w, 1);
            } else if ("Diagonal".equals(direction)) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        float t = (x / (float) w + y / (float) h) / 2f;
                        bmp.setPixel(x, y, Color.argb(
                                Math.round(Color.alpha(first) * (1 - t) + Color.alpha(second) * t),
                                Math.round(Color.red(first) * (1 - t) + Color.red(second) * t),
                                Math.round(Color.green(first) * (1 - t) + Color.green(second) * t),
                                Math.round(Color.blue(first) * (1 - t) + Color.blue(second) * t)));
                    }
                }
            } else if ("Radial".equals(direction)) {
                float cx = w / 2f;
                float cy = h / 2f;
                float max = (float) Math.sqrt(cx * cx + cy * cy);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        float dx = x - cx;
                        float dy = y - cy;
                        float t = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / max);
                        bmp.setPixel(x, y, Color.argb(
                                Math.round(Color.alpha(first) * (1 - t) + Color.alpha(second) * t),
                                Math.round(Color.red(first) * (1 - t) + Color.red(second) * t),
                                Math.round(Color.green(first) * (1 - t) + Color.green(second) * t),
                                Math.round(Color.blue(first) * (1 - t) + Color.blue(second) * t)));
                    }
                }
            } else {
                int[] pixels = new int[w * h];
                for (int y = 0; y < h; y++) {
                    float t = y / (float) h;
                    int r = Math.round(Color.red(first) * (1 - t) + Color.red(second) * t);
                    int g = Math.round(Color.green(first) * (1 - t) + Color.green(second) * t);
                    int b = Math.round(Color.blue(first) * (1 - t) + Color.blue(second) * t);
                    int a = Math.round(Color.alpha(first) * (1 - t) + Color.alpha(second) * t);
                    Arrays.fill(pixels, y * w, (y + 1) * w, Color.argb(a, r, g, b));
                }
                bmp.setPixels(pixels, 0, w, 0, 0, w, h);
            }
            Object old = preview.getTag();
            preview.setImageBitmap(bmp);
            preview.setTag(bmp);
            if (old instanceof Bitmap && old != bmp) {
                try {
                    ((Bitmap) old).recycle();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            ToolViewFactory.toast(context, PackRes.str("device", R.string.s_render_failed, "Render failed"));
        }
    }

    @Override
    public View createView(Context context, ViewGroup container) {
        LinearLayout box = ToolViewFactory.container(context);
        ToolViewFactory.addTitle(box, PackRes.str("device", R.string.s_wallpaper_maker, "Wallpaper Maker"));
        ToolViewFactory.addLabel(box, PackRes.str("device", R.string.s_pick_any_colors_from_the_wheel_blend_gradients_p, "Pick any colors from the wheel, blend gradients, preview fullscreen, then set or save."));
        final int[] first = new int[]{Color.parseColor("#1B73E8")};
        final int[] second = new int[]{Color.parseColor("#681DA8")};
        final int[] which = new int[]{0};
        final String[] direction = new String[]{"Vertical"};

        LinearLayout whichRow = ToolViewFactory.makeRow(box);
        MaterialButton firstTab = ToolViewFactory.makeRowButton(whichRow, PackRes.str("device", R.string.s_color_1, "Color 1 ●"), 1f);
        MaterialButton secondTab = ToolViewFactory.makeRowButton(whichRow, PackRes.str("device", R.string.s_color_2, "Color 2"), 1f);
        final View currentSwatch = new View(context);
        currentSwatch.setBackgroundColor(first[0]);
        box.addView(currentSwatch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 56)));
        TextView hexLabel = ToolViewFactory.makeOutput(box);
        hexLabel.setText(PackRes.str("device", R.string.s_1b73e8, "#1B73E8"));

        final ColorWheelView wheel = new ColorWheelView(context);
        box.addView(wheel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 300)));
        ToolViewFactory.addLabel(box, PackRes.str("device", R.string.s_alpha_transparency, "Alpha (transparency)"));
        SeekBar alphaBar = new SeekBar(context);
        alphaBar.setMax(255);
        alphaBar.setProgress(255);
        box.addView(alphaBar);

        final ImageView[] previewHolder = new ImageView[1];
        final boolean[] gradientHolder = new boolean[]{true};

        wheel.setListener(() -> {
            int c = wheel.getColor(alphaBar.getProgress());
            if (which[0] == 0) first[0] = c; else second[0] = c;
            currentSwatch.setBackgroundColor(c);
            hexLabel.setText(String.format("#%08X", c));
            renderWallpaperPreview(context, previewHolder[0], first[0], second[0], gradientHolder[0], direction[0]);
        });
        alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                int c = wheel.getColor(progress);
                if (which[0] == 0) first[0] = c; else second[0] = c;
                currentSwatch.setBackgroundColor(c);
                hexLabel.setText(String.format("#%08X", c));
                renderWallpaperPreview(context, previewHolder[0], first[0], second[0], gradientHolder[0], direction[0]);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        firstTab.setOnClickListener(v -> {
            which[0] = 0;
            firstTab.setText(PackRes.str("device", R.string.s_color_1, "Color 1 ●"));
            secondTab.setText(PackRes.str("device", R.string.s_color_2, "Color 2"));
            wheel.setColor(first[0]);
            currentSwatch.setBackgroundColor(first[0]);
        });
        secondTab.setOnClickListener(v -> {
            which[0] = 1;
            firstTab.setText(PackRes.str("device", R.string.s_color_1_2, "Color 1"));
            secondTab.setText(PackRes.str("device", R.string.s_color_2_2, "Color 2 ●"));
            wheel.setColor(second[0]);
            currentSwatch.setBackgroundColor(second[0]);
        });
        wheel.setColor(first[0]);

        CheckBox gradientBox = new CheckBox(context);
        gradientBox.setText(PackRes.str("device", R.string.s_gradient_blend_off_solid_color_1, "Gradient blend (off = solid Color 1)"));
        gradientBox.setChecked(true);
        box.addView(gradientBox);
        gradientHolder[0] = true;
        ToolViewFactory.addLabel(box, PackRes.str("device", R.string.s_gradient_direction, "Gradient direction"));
        String[] dirs = new String[]{"Vertical", "Horizontal", "Diagonal", "Radial"};
        MaterialButtonToggleGroup dirToggle =
                new MaterialButtonToggleGroup(context);
        dirToggle.setSingleSelection(true);
        dirToggle.setSelectionRequired(true);
        for (String d : dirs) {
            MaterialButton b = new MaterialButton(context);
            b.setId(View.generateViewId());
            b.setText(d);
            b.setTextSize(12);
            dirToggle.addView(b, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        int tm = ToolViewFactory.dp(context, 8);
        LinearLayout.LayoutParams dgp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dgp.setMargins(0, 0, 0, tm);
        box.addView(dirToggle, dgp);
        dirToggle.addOnButtonCheckedListener((g, checkedId, checked) -> {
            if (!checked) return;
            for (int i = 0; i < dirs.length; i++) {
                if (dirToggle.getChildAt(i).getId() == checkedId) {
                    direction[0] = dirs[i];
                    renderWallpaperPreview(context, previewHolder[0], first[0], second[0], gradientHolder[0], direction[0]);
                    return;
                }
            }
        });
        dirToggle.check(dirToggle.getChildAt(0).getId());
        direction[0] = dirs[0];

        ImageView preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        box.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ToolViewFactory.dp(context, 260)));
        previewHolder[0] = preview;
        gradientBox.setOnCheckedChangeListener((b, checked) -> {
            gradientHolder[0] = checked;
            renderWallpaperPreview(context, preview, first[0], second[0], checked, direction[0]);
        });
        renderWallpaperPreview(context, preview, first[0], second[0], true, direction[0]);

        LinearLayout btnRow = ToolViewFactory.makeRow(box);
        MaterialButton previewBtn = ToolViewFactory.makeRowButton(btnRow, PackRes.str("device", R.string.s_fullscreen_preview, "Fullscreen preview"), 1f);
        MaterialButton swapBtn = ToolViewFactory.makeRowButton(btnRow, PackRes.str("device", R.string.s_swap, "Swap"), 1f);
        previewBtn.setOnClickListener(v -> {
            Object tag = preview.getTag();
            if (!(tag instanceof Bitmap)) {
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_nothing_to_preview, "Nothing to preview"));
                return;
            }
            ImageView full = new ImageView(context);
            full.setImageBitmap((Bitmap) tag);
            full.setScaleType(ImageView.ScaleType.CENTER_CROP);
            FrameLayout root = new FrameLayout(context);
            root.setBackgroundColor(Color.BLACK);
            root.addView(full, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            MaterialButton exit = new MaterialButton(context);
            exit.setText(PackRes.str("device", R.string.s_exit_preview, "EXIT preview"));
            FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int m = ToolViewFactory.dp(context, 24);
            ep.setMargins(m, m, m, ToolViewFactory.dp(context, 48));
            root.addView(exit, ep);
            AlertDialog[] holder = new AlertDialog[1];
            root.setOnClickListener(v2 -> { try { holder[0].dismiss(); } catch (Exception ignored) {} });
            exit.setOnClickListener(v2 -> { try { holder[0].dismiss(); } catch (Exception ignored) {} });
            AlertDialog d = new MaterialAlertDialogBuilder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen).setView(root).create();
            holder[0] = d;
            d.show();
            if (d.getWindow() != null) d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        });
        swapBtn.setOnClickListener(v -> {
            int t = first[0];
            first[0] = second[0];
            second[0] = t;
            wheel.setColor(which[0] == 0 ? first[0] : second[0]);
            renderWallpaperPreview(context, preview, first[0], second[0], gradientHolder[0], direction[0]);
        });
        LinearLayout btnRow2 = ToolViewFactory.makeRow(box);
        MaterialButton applyBtn = ToolViewFactory.makeRowButton(btnRow2, PackRes.str("device", R.string.s_set_as_wallpaper, "Set as wallpaper"), 1f);
        MaterialButton saveBtn = ToolViewFactory.makeRowButton(btnRow2, PackRes.str("device", R.string.s_save_to_gallery, "Save to gallery"), 1f);
        applyBtn.setOnClickListener(v -> {
            try {
                Object tag = preview.getTag();
                if (!(tag instanceof Bitmap)) {
                    return;
                }
                WallpaperManager wm = WallpaperManager.getInstance(context);
                DisplayMetrics dm = context.getResources().getDisplayMetrics();
                Bitmap scaled = Bitmap.createScaledBitmap((Bitmap) tag, dm.widthPixels, dm.heightPixels, true);
                try {
                    if (Build.VERSION.SDK_INT >= 24) {
                        wm.setBitmap(scaled, null, true, WallpaperManager.FLAG_SYSTEM);
                    } else {
                        wm.setBitmap(scaled);
                    }
                } finally {
                    try { if (scaled != tag) scaled.recycle(); } catch (Exception ignored) {}
                }
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_wallpaper_set, "Wallpaper set"));
            } catch (Exception e) {
                ToolViewFactory.toast(context, "Failed: " + e.getMessage());
            }
        });
        saveBtn.setOnClickListener(v -> {
            try {
                Object tag = preview.getTag();
                if (!(tag instanceof Bitmap)) {
                    return;
                }
                String name = "wallpaper_" + System.currentTimeMillis() + ".png";
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) {
                    ToolViewFactory.toast(context, PackRes.str("device", R.string.s_save_failed, "Save failed"));
                    return;
                }
                OutputStream os = context.getContentResolver().openOutputStream(uri);
                ((Bitmap) tag).compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_saved_to_gallery, "Saved to gallery"));
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_save_failed, "Save failed"));
            }
        });
        MaterialButton copyHex = ToolViewFactory.makeButton(box, PackRes.str("device", R.string.s_copy_colors_as_hex, "Copy colors as HEX"));
        copyHex.setOnClickListener(v -> ToolViewFactory.copyText(context, PackRes.str("device", R.string.s_wallpaper, "wallpaper"), String.format(Locale.US, "Color1 #%08X  Color2 #%08X", first[0], second[0])));
        LinearLayout btnRow3 = ToolViewFactory.makeRow(box);
        MaterialButton shareWpBtn = ToolViewFactory.makeRowButton(btnRow3, PackRes.str("device", R.string.s_share_image, "Share image"), 1f);
        MaterialButton openWpBtn = ToolViewFactory.makeRowButton(btnRow3, PackRes.str("device", R.string.s_open_file, "Open file"), 1f);
        shareWpBtn.setOnClickListener(v -> {
            try {
                Object tag = preview.getTag();
                if (!(tag instanceof Bitmap)) return;
                File dir = new File(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES), "Wallpapers");
                dir.mkdirs();
                File out = new File(dir, "wallpaper_" + System.currentTimeMillis() + ".png");
                FileOutputStream os = new FileOutputStream(out);
                ((Bitmap) tag).compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", out);
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("image/png");
                s.putExtra(Intent.EXTRA_STREAM, uri);
                s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(s, "Share"));
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_share_failed, "Share failed"));
            }
        });
        openWpBtn.setOnClickListener(v -> {
            try {
                File dir = new File(new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_PICTURES), "Wallpapers");
                File[] all = dir.listFiles();
                if (all == null || all.length == 0) {
                    ToolViewFactory.toast(context, PackRes.str("device", R.string.s_save_or_share_first, "Save or share first"));
                    return;
                }
                File latest = all[0];
                for (File f : all) if (f.lastModified() > latest.lastModified()) latest = f;
                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", latest);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "image/png");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(Intent.createChooser(i, "Open wallpaper"));
            } catch (Exception e) {
                ToolViewFactory.toast(context, PackRes.str("device", R.string.s_open_failed, "Open failed"));
            }
        });
        return box;
    }
}




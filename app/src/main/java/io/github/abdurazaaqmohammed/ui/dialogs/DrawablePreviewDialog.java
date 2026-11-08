package io.github.abdurazaaqmohammed.ui.dialogs;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.drawable.ApkDrawableRenderer;
import io.github.abdurazaaqmohammed.player.ZoomableImageView;

/**
 * Full size preview for drawable XMLs under the res/drawable*, res/mipmap*
 * and res/color* directories of an APK/ZIP archive (and for loose drawable
 * XML files).
 *
 * <p>Shows the rendered drawable on a checkerboard with pinch zoom, lets the
 * user switch states of a selector / level of a level-list or clip drawable,
 * toggle the background, inspect the decoded XML and export a PNG.</p>
 */
public final class DrawablePreviewDialog {

    private static final int RENDER_PX = 768;
    private static final String AUTHORITY = "io.github.abdurazaaqmohammed.MPManager.provider";

    private DrawablePreviewDialog() {
    }

    public static void show(Activity activity, File archive, String entryPath) {
        show(activity, archive, entryPath, null);
    }

    /**
     * @param viewSource optional callback for "XML source" - when supplied the
     *                   host opens its own editor (so edits can be written back
     *                   into the archive) instead of showing a read-only dialog.
     */
    public static void show(Activity activity, File archive, String entryPath,
                            Runnable viewSource) {
        if (activity == null || entryPath == null || activity.isFinishing()
                || activity.isDestroyed()) return;
        new Controller(activity, archive, entryPath, viewSource).start();
    }

    /** Preview a loose (extracted / decompiled) drawable XML file. */
    public static void showFile(Activity activity, File xmlFile) {
        if (xmlFile == null) return;
        show(activity, null, xmlFile.getAbsolutePath(), null);
    }

    private static final class StateChip {
        final String label;
        final int[] state;
        TextView view;

        StateChip(String label, int... state) {
            this.label = label;
            this.state = state;
        }
    }

    private static final class Controller {

        final Activity activity;
        final File archive;
        final String entryPath;
        final Runnable viewSource;
        final String fileName;

        LinearLayout stateRow;
        FrameLayout frame;
        ZoomableImageView image;
        ProgressBar progress;
        TextView error;
        TextView info;
        Button backgroundBtn;
        Button xmlBtn;
        Button shareBtn;
        LinearLayout levelRow;
        SeekBar levelSeek;

        AlertDialog dialog;
        volatile ApkDrawableRenderer.Result result;
        volatile Bitmap lastBitmap;
        volatile String fallbackXml;
        final List<StateChip> chips = new ArrayList<>();
        int selectedChip = 0;
        int level = 0;
        int backgroundMode = 0;
        int defaultChipTextColor;

        Controller(Activity activity, File archive, String entryPath, Runnable viewSource) {
            this.activity = activity;
            this.archive = archive;
            this.entryPath = entryPath;
            this.viewSource = viewSource;
            int slash = entryPath.replace('\\', '/').lastIndexOf('/');
            this.fileName = slash >= 0 ? entryPath.substring(slash + 1) : entryPath;
        }

        void start() {
            View root = LayoutInflater.from(activity)
                    .inflate(R.layout.dialog_drawable_preview, null);
            stateRow = root.findViewById(R.id.drawableStateRow);
            frame = root.findViewById(R.id.drawablePreviewFrame);
            image = root.findViewById(R.id.drawablePreviewImage);
            progress = root.findViewById(R.id.drawablePreviewProgress);
            error = root.findViewById(R.id.drawablePreviewError);
            info = root.findViewById(R.id.drawablePreviewInfo);
            backgroundBtn = root.findViewById(R.id.drawableBackgroundBtn);
            xmlBtn = root.findViewById(R.id.drawableXmlBtn);
            shareBtn = root.findViewById(R.id.drawableShareBtn);
            levelRow = root.findViewById(R.id.drawableLevelRow);
            levelSeek = root.findViewById(R.id.drawableLevelSeek);

            frame.setBackground(ApkDrawableRenderer.checkerboardTile(activity));
            defaultChipTextColor = new TextView(activity).getCurrentTextColor();
            buildStateChips();

            backgroundBtn.setOnClickListener(v -> cycleBackground());
            xmlBtn.setOnClickListener(v -> openXml());
            shareBtn.setOnClickListener(v -> sharePng());
            levelSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                    if (fromUser) {
                        level = p;
                        rasterize();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            dialog = new MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.drawable_preview) + ": " + fileName)
                    .setView(root)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            dialog.show();
            info.setText(fileName);
            load();
        }

        private void buildStateChips() {
            int E = android.R.attr.state_enabled;
            int W = android.R.attr.state_window_focused;
            chips.add(new StateChip(activity.getString(R.string.drawable_preview_default), E, W));
            chips.add(new StateChip(activity.getString(R.string.drawable_preview_pressed),
                    android.R.attr.state_pressed, E, W));
            chips.add(new StateChip(activity.getString(R.string.drawable_preview_focused),
                    android.R.attr.state_focused, E, W));
            chips.add(new StateChip(activity.getString(R.string.drawable_preview_selected),
                    android.R.attr.state_selected, E, W));
            chips.add(new StateChip(activity.getString(R.string.drawable_preview_checked),
                    android.R.attr.state_checked, E, W));
            chips.add(new StateChip(activity.getString(R.string.drawable_preview_activated),
                    android.R.attr.state_activated, E, W));
            chips.add(new StateChip(activity.getString(R.string.drawable_preview_disabled)));

            float density = activity.getResources().getDisplayMetrics().density;
            int padH = (int) (12 * density);
            int padV = (int) (6 * density);
            for (int i = 0; i < chips.size(); i++) {
                final int index = i;
                StateChip chip = chips.get(i);
                TextView tv = new TextView(activity);
                tv.setText(chip.label);
                tv.setTextSize(12);
                tv.setPadding(padH, padV, padH, padV);
                tv.setSingleLine(true);
                tv.setOnClickListener(v -> selectChip(index));
                chip.view = tv;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd((int) (6 * density));
                stateRow.addView(tv, lp);
            }
            highlightChips();
        }

        private void selectChip(int index) {
            selectedChip = index;
            highlightChips();
            rasterize();
        }

        private void highlightChips() {
            for (int i = 0; i < chips.size(); i++) {
                TextView tv = chips.get(i).view;
                if (tv == null) continue;
                GradientDrawable bg = new GradientDrawable();
                float density = activity.getResources().getDisplayMetrics().density;
                bg.setCornerRadius(16 * density);
                if (i == selectedChip) {
                    bg.setColor(0x334488FF);
                    bg.setStroke((int) density, 0xFF4488FF);
                    tv.setTextColor(0xFF64B5F6);
                } else {
                    bg.setColor(Color.TRANSPARENT);
                    bg.setStroke((int) density, 0x66888888);
                    tv.setTextColor(defaultChipTextColor);
                }
                tv.setBackground(bg);
            }
        }

        private void cycleBackground() {
            backgroundMode = (backgroundMode + 1) % 4;
            int label;
            switch (backgroundMode) {
                case 1:
                    frame.setBackground(new ColorDrawable(0xFFFFFFFF));
                    label = R.string.drawable_bg_white;
                    break;
                case 2:
                    frame.setBackground(new ColorDrawable(0xFF202124));
                    label = R.string.drawable_bg_black;
                    break;
                case 3:
                    frame.setBackground(null);
                    label = R.string.drawable_bg_theme;
                    break;
                default:
                    frame.setBackground(ApkDrawableRenderer.checkerboardTile(activity));
                    label = R.string.drawable_bg_checkerboard;
                    break;
            }
            backgroundBtn.setText(label);
        }

        // ------------------------------------------------------------ rendering

        private void load() {
            progress.setVisibility(View.VISIBLE);
            error.setVisibility(View.GONE);
            new Thread(() -> {
                try {
                    ApkDrawableRenderer.Result r = ApkDrawableRenderer.render(
                            activity.getApplicationContext(), archive, entryPath, RENDER_PX);
                    Bitmap bm = ApkDrawableRenderer.rasterize(r,
                            chips.get(selectedChip).state, level, RENDER_PX);
                    result = r;
                    lastBitmap = bm;
                    post(() -> apply(r, bm));
                } catch (ApkDrawableRenderer.RenderException e) {
                    fallbackXml = e.xml;
                    post(() -> fail(e.getMessage(), e.xml));
                } catch (Throwable t) {
                    post(() -> fail(String.valueOf(t.getMessage()), null));
                }
            }, "drawable-preview").start();
        }

        private void rasterize() {
            final ApkDrawableRenderer.Result r = result;
            if (r == null) return;
            final int[] state = chips.get(selectedChip).state;
            final int lvl = level;
            new Thread(() -> {
                Bitmap bm = ApkDrawableRenderer.rasterize(r, state, lvl, RENDER_PX);
                lastBitmap = bm;
                post(() -> {
                    if (alive() && bm != null) image.setImageBitmap(bm);
                });
            }, "drawable-state").start();
        }

        private void apply(ApkDrawableRenderer.Result r, Bitmap bm) {
            if (!alive()) return;
            progress.setVisibility(View.GONE);
            if (bm != null) image.setImageBitmap(bm);
            String renderer = "APK resources".equals(r.source)
                    ? activity.getString(R.string.drawable_preview_renderer_apk)
                    : activity.getString(R.string.drawable_preview_renderer_xml);
            StringBuilder sb = new StringBuilder();
            sb.append(activity.getString(R.string.drawable_preview_info,
                    r.resourceRef == null ? fileName : r.resourceRef,
                    r.rootTag,
                    r.intrinsicWidth > 0 ? r.intrinsicWidth : (bm == null ? 0 : bm.getWidth()),
                    r.intrinsicHeight > 0 ? r.intrinsicHeight : (bm == null ? 0 : bm.getHeight())));
            sb.append('\n').append(activity.getString(R.string.drawable_preview_rendered_with, renderer));
            if (r.note != null && !r.note.isEmpty()) sb.append('\n').append(r.note);
            info.setText(sb.toString());

            String tag = r.rootTag;
            boolean levelBased = "level-list".equals(tag) || "clip".equals(tag)
                    || "scale".equals(tag) || "rotate".equals(tag)
                    || "transition".equals(tag) || "animation-list".equals(tag);
            levelRow.setVisibility(levelBased ? View.VISIBLE : View.GONE);
            shareBtn.setEnabled(bm != null);
        }

        private void fail(String message, String xml) {
            if (!alive()) return;
            fallbackXml = xml;
            progress.setVisibility(View.GONE);
            error.setVisibility(View.VISIBLE);
            error.setText(activity.getString(R.string.drawable_preview_error,
                    message == null ? "unknown error" : message));
            info.setText(fileName);
            image.setVisibility(View.GONE);
            shareBtn.setEnabled(false);
        }

        // --------------------------------------------------------------- actions

        private void openXml() {
            if (viewSource != null) {
                viewSource.run();
                return;
            }
            ApkDrawableRenderer.Result r = result;
            String xml = r != null ? r.xml : fallbackXml;
            if (xml == null || xml.isEmpty()) {
                Toast.makeText(activity, R.string.cannot_open_item, Toast.LENGTH_SHORT).show();
                return;
            }
            ScrollView scroll = new ScrollView(activity);
            TextView tv = new TextView(activity);
            tv.setText(xml);
            tv.setTextIsSelectable(true);
            tv.setTextSize(12);
            int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
            tv.setPadding(pad, pad, pad, pad);
            scroll.addView(tv);
            new MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.drawable_preview_xml_title, fileName))
                    .setView(scroll)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.drawable_preview_copy_xml, (d, w) -> {
                        ClipboardManager cm = (ClipboardManager) activity
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(fileName, xml));
                    })
                    .show();
        }

        private void sharePng() {
            final Bitmap bm = lastBitmap;
            if (bm == null) return;
            new Thread(() -> {
                try {
                    File dir = new File(activity.getCacheDir(), "drawable_previews");
                    if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir");
                    String base = fileName.toLowerCase(Locale.ROOT).replaceAll("\\.xml$", "");
                    if (base.isEmpty()) base = "drawable";
                    File png = new File(dir, base + ".png");
                    try (FileOutputStream out = new FileOutputStream(png)) {
                        if (!bm.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw new IllegalStateException("compress");
                        }
                    }
                    Uri uri = FileProvider.getUriForFile(activity, AUTHORITY, png);
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("image/png");
                    send.putExtra(Intent.EXTRA_STREAM, uri);
                    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    post(() -> {
                        if (!alive()) return;
                        try {
                            activity.startActivity(Intent.createChooser(send,
                                    activity.getString(R.string.drawable_preview_share_png)));
                        } catch (Throwable ignored) {
                        }
                    });
                } catch (Throwable t) {
                    post(() -> {
                        if (alive()) Toast.makeText(activity,
                                R.string.drawable_preview_share_failed,
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }, "drawable-export").start();
        }

        private void post(Runnable r) {
            activity.runOnUiThread(r);
        }

        private boolean alive() {
            return dialog != null && dialog.isShowing()
                    && !activity.isFinishing() && !activity.isDestroyed();
        }
    }
}

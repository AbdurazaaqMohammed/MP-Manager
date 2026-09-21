package io.github.abdurazaaqmohammed.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.exifinterface.media.ExifInterface;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.JpegMetaStrip;
import io.github.abdurazaaqmohammed.utils.JpegtranJni;
import io.github.abdurazaaqmohammed.utils.NativeToolManager;
import io.github.abdurazaaqmohammed.utils.ProgressManager;

public class ImageEditActivity extends AppCompatActivity {

    static String sessionPath;

    private String originalPath;
    private File workingFile;
    private boolean isJpeg;
    private ImageView preview;
    private CropOverlayView overlay;
    private TextView infoView;
    private RectF cropImageRect;
    private float aspect;
    private boolean dirty;
    private boolean fromShared;
    private int imgW;
    private int imgH;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTheme(getIntent().getIntExtra("theme", prefs.getInt("theme", dark ? R.style.Theme_MyApp_Dark : R.style.Theme_MyApp_Light)));
        super.onCreate(savedInstanceState);
        DynamicColors.applyToActivitiesIfAvailable(getApplication());
        originalPath = sessionPath;
        sessionPath = null;
        android.net.Uri incoming = getIntent().getData();
        if (incoming == null && android.content.Intent.ACTION_SEND.equals(getIntent().getAction())) {
            try {
                incoming = getIntent().getParcelableExtra(android.content.Intent.EXTRA_STREAM);
            } catch (Exception ignored) {
            }
        }
        if (originalPath == null && incoming != null) {
            resolveSharedImage(incoming);
            return;
        }
        if (originalPath == null || !new File(originalPath).isFile()) {
            finish();
            return;
        }
        initEditor();
        initEditor();
    }

    private void resolveSharedImage(android.net.Uri uri) {
        if ("file".equals(uri.getScheme())) {
            originalPath = uri.getPath();
            fromShared = false;
            if (originalPath == null || !new File(originalPath).isFile()) {
                finish();
                return;
            }
            initEditor();
            return;
        }
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                String name = "shared_image";
                try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) {
                            String display = cursor.getString(idx);
                            if (display != null && !display.isEmpty()) name = display;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (!name.contains(".")) name += ".jpg";
                File dest = new File(getCacheDir(), "shared_" + System.currentTimeMillis() + "_" + name.replaceAll("[^a-zA-Z0-9._-]", "_"));
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream os = new FileOutputStream(dest)) {
                    if (in == null) throw new Exception("Cannot open shared file");
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                }
                originalPath = dest.getAbsolutePath();
                fromShared = true;
                pm.dismiss();
                runOnUiThread(this::initEditor);
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> {
                    showError(e.getMessage() != null ? e.getMessage() : e.toString());
                    finish();
                });
            }
        }).start();
    }

    private void initEditor() {
        String lower = originalPath.toLowerCase(Locale.US);
        isJpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        toolbar = new MaterialToolbar(this);
        toolbar.setTitle(new File(originalPath).getName());
        toolbar.setSubtitle("Image editor");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        Menu toolbarMenu = toolbar.getMenu();
        toolbarMenu.add(0, 1, 0, "Save").setIcon(R.drawable.save_24px).setShowAsAction(1);
        toolbar.setOnMenuItemClickListener(item -> {
            saveAndFinish();
            return true;
        });
        main.addView(toolbar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        infoView = new TextView(this);
        infoView.setTextSize(12);
        int pad = dp(12);
        infoView.setPadding(pad, pad / 2, pad, 0);
        main.addView(infoView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout stage = new FrameLayout(this);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setAdjustViewBounds(false);
        stage.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay = new CropOverlayView(this);
        stage.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setCropListener(rect -> updateInfo());
        stage.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) resetCropToFull();
        });
        main.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        main.addView(buildAspectRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        main.addView(buildOpsRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        main.addView(buildMetaRow(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(main);
        prepareWorkingCopy();
    }

    private LinearLayout buildAspectRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        String[] names = {"Free", "1:1", "4:3", "3:4", "16:9", "9:16", "Custom"};
        float[] ratios = {0f, 1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f, -1f};
        for (int i = 0; i < names.length; i++) {
            final float ratio = ratios[i];
            Button b = new Button(this, null, android.R.attr.buttonStyleSmall);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(11);
            if (ratio < 0) {
                b.setOnClickListener(v -> showCustomAspectDialog());
            } else {
                b.setOnClickListener(v -> {
                    aspect = ratio;
                    overlay.setAspectRatio(ratio);
                    resetCropToFull();
                    updateInfo();
                });
            }
            row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private void showCustomAspectDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);
        EditText wInput = new EditText(this);
        wInput.setHint("W");
        wInput.setText("3");
        wInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        wInput.setSingleLine(true);
        root.addView(wInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView colon = new TextView(this);
        colon.setText(" : ");
        colon.setTextSize(16);
        root.addView(colon);
        EditText hInput = new EditText(this);
        hInput.setHint("H");
        hInput.setText("2");
        hInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hInput.setSingleLine(true);
        root.addView(hInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.img_aspect))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        float fw = Float.parseFloat(wInput.getText().toString().trim());
                        float fh = Float.parseFloat(hInput.getText().toString().trim());
                        if (fw <= 0 || fh <= 0) {
                            showError(getString(R.string.img_above_zero));
                            return;
                        }
                        aspect = fw / fh;
                        overlay.setAspectRatio(aspect);
                        resetCropToFull();
                        updateInfo();
                    } catch (NumberFormatException e) {
                        showError(getString(R.string.img_enter_numbers));
                    }
                }).show();
    }

    private LinearLayout buildOpsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        String[] names = {getString(R.string.img_rot_l), getString(R.string.img_rot_r), getString(R.string.img_flip_h), getString(R.string.img_flip_v)};
        String[] ops = {"left", "right", "flipH", "flipV"};
        for (int i = 0; i < names.length; i++) {
            final String op = ops[i];
            MaterialButton b = new MaterialButton(this);
            b.setText(names[i]);
            b.setOnClickListener(v -> applyOp(op));
            row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private LinearLayout buildMetaRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        MaterialButton exifBtn = new MaterialButton(this);
        exifBtn.setText(getString(R.string.img_exif));
        exifBtn.setOnClickListener(v -> showExifEditor());
        row.addView(exifBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton stripBtn = new MaterialButton(this);
        stripBtn.setText(getString(R.string.img_strip_meta));
        stripBtn.setOnClickListener(v -> confirmStripMetadata());
        row.addView(stripBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void prepareWorkingCopy() {
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                File src = new File(originalPath);
                workingFile = new File(getCacheDir(), "imageedit_" + System.currentTimeMillis()
                        + (isJpeg ? ".jpg" : ".png"));
                copyFile(src, workingFile);
                if (isJpeg) normalizeOrientation();
                readDims();
                runOnUiThread(() -> {
                    pm.dismiss();
                    reloadPreview();
                    resetCropToFull();
                    updateInfo();
                });
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> {
                    showError(e.getMessage() != null ? e.getMessage() : e.toString());
                    finish();
                });
            }
        }).start();
    }

    private void normalizeOrientation() {
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        try {
            orientation = new ExifInterface(workingFile.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {
        }
        if (orientation == ExifInterface.ORIENTATION_NORMAL) return;
        int op = -1;
        if (orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL) op = JpegtranJni.OP_FLIP_H;
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) op = JpegtranJni.OP_ROT_180;
        else if (orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL) op = JpegtranJni.OP_FLIP_V;
        else if (orientation == ExifInterface.ORIENTATION_TRANSPOSE) op = JpegtranJni.OP_TRANSPOSE;
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_90) op = JpegtranJni.OP_ROT_90;
        else if (orientation == ExifInterface.ORIENTATION_TRANSVERSE) op = JpegtranJni.OP_TRANSVERSE;
        else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) op = JpegtranJni.OP_ROT_270;
        if (op < 0) return;
        try {
            ensureJniBlocking();
            File tmp = new File(getCacheDir(), "norm_" + System.currentTimeMillis() + ".jpg");
            NativeToolManager.runJpegtranJni(this, op, workingFile.getAbsolutePath(), tmp.getAbsolutePath(), 0, 0, 0, 0);
            copyFile(tmp, workingFile);
            tmp.delete();
            ExifInterface exif = new ExifInterface(workingFile.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            exif.saveAttributes();
        } catch (Exception e) {
            runOnUiThread(() -> showError(getString(R.string.img_orientation_kept)));
        }
    }

    private void ensureJniBlocking() throws Exception {
        if (NativeToolManager.loadJpegtranJni(this)) return;
        final Exception[] failure = new Exception[1];
        final boolean[] done = {false};
        runOnUiThread(() -> NativeToolManager.ensureJpegtran(this, new NativeToolManager.ReadyCallback() {
            public void onReady() {
                synchronized (done) {
                    done[0] = true;
                    done.notifyAll();
                }
            }

            public void onError(String message) {
                synchronized (done) {
                    failure[0] = new Exception(message);
                    done.notifyAll();
                }
            }
        }));
        synchronized (done) {
            long deadline = System.currentTimeMillis() + 300000;
            while (!done[0] && failure[0] == null && System.currentTimeMillis() < deadline) {
                try {
                    done.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (failure[0] != null) throw failure[0];
        if (!NativeToolManager.loadJpegtranJni(this)) throw new Exception(getString(R.string.img_jni_missing));
    }

    private void readDims() {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(workingFile.getAbsolutePath(), bounds);
        imgW = bounds.outWidth;
        imgH = bounds.outHeight;
    }

    private void reloadPreview() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeFile(workingFile.getAbsolutePath(), opts);
        } catch (OutOfMemoryError e) {
            opts.inSampleSize = 2;
            try {
                bitmap = BitmapFactory.decodeFile(workingFile.getAbsolutePath(), opts);
            } catch (OutOfMemoryError ignored) {
            }
        }
        if (bitmap != null) preview.setImageBitmap(bitmap);
    }

    private RectF displayedRect() {
        int vw = preview.getWidth();
        int vh = preview.getHeight();
        if (vw <= 0 || vh <= 0 || imgW <= 0 || imgH <= 0) return null;
        float scale = Math.min(vw / (float) imgW, vh / (float) imgH);
        float dx = (vw - imgW * scale) / 2f;
        float dy = (vh - imgH * scale) / 2f;
        return new RectF(dx, dy, dx + imgW * scale, dy + imgH * scale);
    }

    private void resetCropToFull() {
        RectF shown = displayedRect();
        if (shown == null) {
            preview.post(this::resetCropToFull);
            return;
        }
        overlay.setImageBounds(shown);
        overlay.setImageRect(shown);
        cropImageRect = null;
        updateInfo();
    }

    private void updateInfo() {
        if (infoView == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append(imgW).append("x").append(imgH);
        RectF viewRect = overlay != null && overlay.hasCrop() ? overlay.getCropRect() : null;
        RectF shown = displayedRect();
        if (viewRect != null && shown != null && imgW > 0 && imgH > 0) {
            float scale = Math.min(preview.getWidth() / (float) imgW, preview.getHeight() / (float) imgH);
            float left = Math.max(shown.left, viewRect.left);
            float top = Math.max(shown.top, viewRect.top);
            float right = Math.min(shown.right, viewRect.right);
            float bottom = Math.min(shown.bottom, viewRect.bottom);
            int x = Math.max(0, Math.round((left - shown.left) / scale));
            int y = Math.max(0, Math.round((top - shown.top) / scale));
            int w = Math.round((right - left) / scale);
            int h = Math.round((bottom - top) / scale);
            if (w > 0 && h > 0) {
                cropImageRect = new RectF(x, y, x + w, y + h);
                sb.append("  crop ").append(w).append("x").append(h).append("+").append(x).append("+").append(y);
            } else {
                cropImageRect = null;
            }
        } else {
            cropImageRect = null;
        }
        if (dirty) sb.append("  *");
        infoView.setText(sb.toString());
    }

    private void applyOp(String op) {
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                ensureJniBlocking();
                if (isJpeg) {
                    int jop = op.equals("left") ? JpegtranJni.OP_ROT_270
                            : op.equals("right") ? JpegtranJni.OP_ROT_90
                            : op.equals("flipH") ? JpegtranJni.OP_FLIP_H
                            : JpegtranJni.OP_FLIP_V;
                    File tmp = new File(getCacheDir(), "op_" + System.currentTimeMillis() + ".jpg");
                    NativeToolManager.runJpegtranJni(this, jop, workingFile.getAbsolutePath(), tmp.getAbsolutePath(), 0, 0, 0, 0);
                    copyFile(tmp, workingFile);
                    tmp.delete();
                    resetOrientationTag();
                } else {
                    applyBitmapOp(op, null);
                }
                readDims();
                dirty = true;
                pm.dismiss();
                runOnUiThread(() -> {
                    reloadPreview();
                    resetCropToFull();
                });
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).start();
    }

    private void applyBitmapOp(String op, int[] crop) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap src = BitmapFactory.decodeFile(workingFile.getAbsolutePath(), opts);
        if (src == null) throw new Exception("Cannot decode image");
        Bitmap out = src;
        try {
            if (crop != null) {
                int x = Math.max(0, Math.min(crop[0], src.getWidth() - 1));
                int y = Math.max(0, Math.min(crop[1], src.getHeight() - 1));
                int w = Math.max(1, Math.min(crop[2], src.getWidth() - x));
                int h = Math.max(1, Math.min(crop[3], src.getHeight() - y));
                out = Bitmap.createBitmap(src, x, y, w, h);
            } else {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                if (op.equals("left")) matrix.postRotate(-90);
                else if (op.equals("right")) matrix.postRotate(90);
                else if (op.equals("flipH")) matrix.postScale(-1, 1);
                else matrix.postScale(1, -1);
                out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
            }
            try (FileOutputStream fos = new FileOutputStream(workingFile)) {
                out.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
        } finally {
            if (out != src) out.recycle();
            src.recycle();
        }
    }

    private void resetOrientationTag() {
        try {
            ExifInterface exif = new ExifInterface(workingFile.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            exif.saveAttributes();
        } catch (Exception ignored) {
        }
    }

    private void showExifEditor() {
        String[] tags = {
                ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_DATETIME};
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);
        ScrollView scroll = new ScrollView(this);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(fields, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        List<EditText> inputs = new ArrayList<>();
        try {
            ExifInterface exif = new ExifInterface(workingFile.getAbsolutePath());
            for (String tag : tags) {
                TextView label = new TextView(this);
                label.setTextSize(13);
                label.setText(tag);
                fields.addView(label);
                EditText input = new EditText(this);
                String val = exif.getAttribute(tag);
                input.setText(val == null ? "" : val);
                input.setSingleLine(true);
                fields.addView(input);
                inputs.add(input);
            }
        } catch (Exception e) {
            showError(getString(R.string.img_no_exif));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.img_exif))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.save), (d, w) -> {
                    new Thread(() -> {
                        try {
                            ExifInterface exif = new ExifInterface(workingFile.getAbsolutePath());
                            for (int i = 0; i < tags.length; i++) {
                                String text = inputs.get(i).getText() == null ? "" : inputs.get(i).getText().toString();
                                exif.setAttribute(tags[i], text);
                            }
                            exif.saveAttributes();
                            dirty = true;
                            runOnUiThread(() -> {
                                updateInfo();
                                showError(getString(R.string.img_exif_saved));
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
                        }
                    }).start();
                }).show();
    }

    private void confirmStripMetadata() {
        if (!isJpeg) {
            showError(getString(R.string.img_only_jpeg));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.img_strip_meta))
                .setMessage(getString(R.string.img_strip_msg))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(getString(R.string.img_strip), (d, w) -> {
                    ProgressManager pm = new ProgressManager(this, true).show();
                    new Thread(() -> {
                        try {
                            JpegMetaStrip.stripFile(workingFile);
                            readDims();
                            dirty = true;
                            pm.dismiss();
                            runOnUiThread(() -> {
                                reloadPreview();
                                updateInfo();
                                showError(getString(R.string.img_meta_stripped));
                            });
                        } catch (Exception e) {
                            pm.dismiss();
                            runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
                        }
                    }).start();
                }).show();
    }

    private boolean allowLossyCrop;

    private boolean hasCrop() {
        RectF crop = cropImageRect;
        return crop != null && crop.width() > 0 && crop.height() > 0
                && (crop.width() < imgW || crop.height() < imgH || crop.left > 0 || crop.top > 0);
    }

    private void saveAndFinish() {
        if (!dirty && !hasCrop()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        if (hasCrop() && isJpeg && !allowLossyCrop && !NativeToolManager.loadJpegtranJni(this)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.img_crop_quality))
                    .setMessage(getString(R.string.img_crop_msg))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(getString(R.string.img_standard_crop), (d, w) -> {
                        allowLossyCrop = true;
                        doSave();
                    })
                    .setPositiveButton(getString(R.string.img_lossless), (d, w) -> NativeToolManager.ensureJpegtran(this,
                            new NativeToolManager.ReadyCallback() {
                                public void onReady() {
                                    doSave();
                                }

                                public void onError(String message) {
                                    showError(message);
                                }
                            })).show();
            return;
        }
        doSave();
    }

    private void applyLossyCrop(File file, int x, int y, int w, int h) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap src = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        if (src == null) throw new Exception("Cannot decode image");
        try {
            x = Math.max(0, Math.min(x, src.getWidth() - 1));
            y = Math.max(0, Math.min(y, src.getHeight() - 1));
            w = Math.max(1, Math.min(w, src.getWidth() - x));
            h = Math.max(1, Math.min(h, src.getHeight() - y));
            Bitmap out = Bitmap.createBitmap(src, x, y, w, h);
            try {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    out.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                }
            } finally {
                if (out != src) out.recycle();
            }
        } finally {
            src.recycle();
        }
    }

    private void doSave() {
        final boolean lossy = allowLossyCrop;
        allowLossyCrop = false;
        boolean hasCrop = hasCrop();
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                if (hasCrop) {
                    RectF crop = cropImageRect;
                    int x = Math.round(crop.left);
                    int y = Math.round(crop.top);
                    int w = Math.round(crop.width());
                    int h = Math.round(crop.height());
                    if (isJpeg && !lossy) {
                        ensureJniBlocking();
                        x -= x % 16;
                        y -= y % 16;
                        w -= w % 16;
                        h -= h % 16;
                        File tmp = new File(getCacheDir(), "crop_" + System.currentTimeMillis() + ".jpg");
                        NativeToolManager.runJpegtranJni(this, JpegtranJni.OP_CROP,
                                workingFile.getAbsolutePath(), tmp.getAbsolutePath(), w, h, x, y);
                        copyFile(tmp, workingFile);
                        tmp.delete();
                    } else if (isJpeg) {
                        applyLossyCrop(workingFile, x, y, w, h);
                    } else {
                        applyBitmapOp(null, new int[]{x, y, w, h});
                    }
                    resetOrientationTag();
                }
                File original = new File(originalPath);
                try {
                    copyFile(original, new File(originalPath + ".bak"));
                } catch (Exception ignored) {
                }
                copyFile(workingFile, original);
                pm.dismiss();
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    if (fromShared) showError(getString(R.string.logger_saved_to, originalPath));
                    finish();
                });
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).start();
    }

    private void confirmExit() {
        if (!dirty && !hasCrop()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.unsaved_changes))
                .setMessage(getString(R.string.arsc_save_before_exit))
                .setPositiveButton(getString(R.string.save), (d, w) -> saveAndFinish())
                .setNegativeButton(getString(R.string.discard), (d, w) -> {
                    setResult(RESULT_CANCELED);
                    finish();
                })
                .setNeutralButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    @Override
    protected void onDestroy() {
        try {
            if (workingFile != null) workingFile.delete();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    private static void copyFile(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); OutputStream os = new FileOutputStream(dst)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
        }
    }

    private void showError(String message) {
        Extensions.showMessage(this, message);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

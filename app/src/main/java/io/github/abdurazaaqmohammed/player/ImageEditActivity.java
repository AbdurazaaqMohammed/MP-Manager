package io.github.abdurazaaqmohammed.player;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import io.github.abdurazaaqmohammed.core.ui.base.BaseActivity;
import io.github.abdurazaaqmohammed.utils.AppLogs;
import androidx.exifinterface.media.ExifInterface;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
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
import io.github.abdurazaaqmohammed.utils.AccessManager;
import io.github.abdurazaaqmohammed.utils.JpegMetaStrip;
import io.github.abdurazaaqmohammed.utils.JpegtranJni;
import io.github.abdurazaaqmohammed.utils.NativeToolManager;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.abdurazaaqmohammed.utils.RootManager;
import io.github.abdurazaaqmohammed.utils.RootStaging;
import io.github.abdurazaaqmohammed.ui.dialogs.FilePickerDialog;

public class ImageEditActivity extends BaseActivity {

    static String sessionPath;

    private String originalPath;
    private File workingFile;
    private boolean isJpeg;
    private String sharedName;
    private ImageView preview;
    private CropOverlayView overlay;
    private TextView infoView;
    private RectF cropImageRect;
    private float aspect;
    private boolean dirty;
    private boolean hasPixelEdits;
    private long targetMtime = -1L;
    private boolean fromShared;
    private int imgW;
    private int imgH;
    private MaterialToolbar toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        originalPath = sessionPath;
        sessionPath = null;
        Uri incoming = getIntent().getData();
        if (incoming == null && Intent.ACTION_SEND.equals(getIntent().getAction())) {
            try {
                incoming = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            } catch (Exception ignored) {
            }
        }
        if (originalPath == null && incoming != null) {
            resolveSharedImage(incoming);
            return;
        }
        String rootOriginal = getIntent().getStringExtra("rootOriginalPath");
        if (rootOriginal != null && !rootOriginal.isEmpty()) {
            originalPath = rootOriginal;
        }
        if (originalPath != null && originalPath.contains("/cache/root_staging/")) {
            // Never allow originalPath to stay inside cache/root_staging
            if (rootOriginal != null && !rootOriginal.isEmpty()) {
                originalPath = rootOriginal;
            } else {
                String stagedName = new File(originalPath).getName();
                int idx = stagedName.indexOf('_');
                String realName = (idx > 0 && idx < stagedName.length() - 1) ? stagedName.substring(idx + 1) : stagedName;
                String[] candidateDirs = {
                        "/storage/emulated/0/DCIM/Screenshots",
                        "/storage/emulated/0/Pictures/Screenshots",
                        "/storage/emulated/0/DCIM/Camera",
                        "/storage/emulated/0/Download",
                        "/storage/emulated/0/Pictures"
                };
                for (String dir : candidateDirs) {
                    File candidate = new File(dir, realName);
                    if (candidate.isFile()) {
                        originalPath = candidate.getAbsolutePath();
                        break;
                    }
                }
            }
        }
        if (originalPath != null) {
            originalPath = toPhysicalPathIfRoot(originalPath);
        }
        if (originalPath == null || (!new File(originalPath).isFile() && !isRootFile(originalPath))) {
            finish();
            return;
        }
        initEditor();
    }

    private String toPhysicalPathIfRoot(String path) {
        if (path == null) return null;
        try {
            RootManager rm = RootManager.getInstance(this);
            if (rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
                if (path.startsWith("/storage/emulated/0")) {
                    return "/data/media/0" + path.substring("/storage/emulated/0".length());
                } else if (path.startsWith("/storage/emulated/")) {
                    return "/data/media/" + path.substring("/storage/emulated/".length());
                }
            }
        } catch (Exception ignored) {}
        return path;
    }

    private boolean isRootFile(String path) {
        if (path == null) return false;
        try {
            RootManager rm = RootManager.getInstance(this);
            if (rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
                RootManager.ShellResult r = rm.execute("test -f " + RootManager.escapeShellArg(path), 5);
                return r.isSuccess();
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void resolveSharedImage(Uri uri) {
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
                // Try to resolve the real underlying storage or root path first
                SharedPathResolver.Result resolved = SharedPathResolver.resolve(this, uri, null);
                if (resolved != null && resolved.path != null) {
                    boolean exists = false;
                    try {
                        exists = new File(resolved.path).isFile() || AccessManager.exists(this, resolved.path);
                    } catch (Exception ignored) {}
                    if (exists) {
                        originalPath = resolved.path;
                        fromShared = false;
                        pm.dismiss();
                        runOnUiThread(this::initEditor);
                        return;
                    }
                }

                String name = "shared_image";
                try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) {
                            String display = cursor.getString(idx);
                            if (display != null && !display.isEmpty()) name = display;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (!name.contains(".")) name += ".jpg";
                sharedName = name;
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
        toolbar.setSubtitle(R.string.image_editor);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        Menu toolbarMenu = toolbar.getMenu();
        toolbarMenu.add(0, 1, 0, R.string.save).setIcon(R.drawable.save_24px).setShowAsAction(1);
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
        String[] names = {getString(R.string.free), "1:1", "4:3", "3:4", "16:9", "9:16", getString(R.string.custom)};
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
        wInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        wInput.setSingleLine(true);
        root.addView(wInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView colon = new TextView(this);
        colon.setText(" : ");
        colon.setTextSize(16);
        root.addView(colon);
        EditText hInput = new EditText(this);
        hInput.setHint("H");
        hInput.setText("2");
        hInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
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
        int pad = dp(4);
        row.setPadding(pad, 0, pad, 0);

        MaterialButton exifBtn = new MaterialButton(this);
        exifBtn.setText(getString(R.string.img_exif));
        exifBtn.setTextSize(11);
        exifBtn.setPadding(dp(4), dp(6), dp(4), dp(6));
        exifBtn.setOnClickListener(v -> showExifEditor());
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p1.setMargins(dp(2), 0, dp(2), 0);
        row.addView(exifBtn, p1);

        MaterialButton resizeBtn = new MaterialButton(this);
        resizeBtn.setIconResource(R.drawable.ic_size_select);
        resizeBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        resizeBtn.setIconPadding(dp(4));
        resizeBtn.setIconSize(dp(16));
        resizeBtn.setText(getString(R.string.crop));
        resizeBtn.setTextSize(11);
        resizeBtn.setPadding(dp(4), dp(6), dp(4), dp(6));
        resizeBtn.setOnClickListener(v -> showResizeDialog());
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p2.setMargins(dp(2), 0, dp(2), 0);
        row.addView(resizeBtn, p2);

        MaterialButton stripBtn = new MaterialButton(this);
        stripBtn.setText(getString(R.string.img_strip_meta));
        stripBtn.setTextSize(11);
        stripBtn.setPadding(dp(4), dp(6), dp(4), dp(6));
        stripBtn.setOnClickListener(v -> confirmStripMetadata());
        LinearLayout.LayoutParams p3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p3.setMargins(dp(2), 0, dp(2), 0);
        row.addView(stripBtn, p3);

        return row;
    }

    private void showResizeDialog() {
        int initialW = imgW;
        int initialH = imgH;
        final int cropX, cropY, cropW, cropH;
        if (hasCrop()) {
            RectF crop = cropImageRect;
            cropX = Math.round(crop.left);
            cropY = Math.round(crop.top);
            cropW = Math.round(crop.width());
            cropH = Math.round(crop.height());
            initialW = cropW;
            initialH = cropH;
        } else {
            cropX = 0;
            cropY = 0;
            cropW = imgW;
            cropH = imgH;
        }

        final int baseCropW = cropW;
        final int baseCropH = cropH;
        final float ratio = initialH > 0 ? initialW / (float) initialH : 1f;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);

        EditText widthView = new EditText(this);
        widthView.setInputType(InputType.TYPE_CLASS_NUMBER);
        widthView.setHint(getString(R.string.width));
        widthView.setText(String.valueOf(initialW));
        widthView.setSingleLine(true);
        inputRow.addView(widthView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView colon = new TextView(this);
        colon.setText(" : ");
        colon.setTextSize(16);
        colon.setTypeface(colon.getTypeface(), android.graphics.Typeface.BOLD);
        colon.setPadding(dp(8), 0, dp(8), 0);
        inputRow.addView(colon);

        EditText heightView = new EditText(this);
        heightView.setInputType(InputType.TYPE_CLASS_NUMBER);
        heightView.setHint(getString(R.string.height));
        heightView.setText(String.valueOf(initialH));
        heightView.setSingleLine(true);
        inputRow.addView(heightView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(inputRow);

        android.widget.CheckBox keepAspect = new android.widget.CheckBox(this);
        keepAspect.setText(getString(R.string.keep_aspect_ratio));
        keepAspect.setChecked(true);
        keepAspect.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(keepAspect);

        android.widget.CheckBox stretchImage = new android.widget.CheckBox(this);
        stretchImage.setText(getString(R.string.stretch_image));
        stretchImage.setChecked(false);
        stretchImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(stretchImage);

        stretchImage.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                keepAspect.setChecked(false);
            }
        });

        keepAspect.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                stretchImage.setChecked(false);
                try {
                    String wStr = widthView.getText().toString().trim();
                    if (!wStr.isEmpty()) {
                        int wVal = Integer.parseInt(wStr);
                        if (wVal > 0 && ratio > 0) {
                            heightView.setText(String.valueOf(Math.round(wVal / ratio)));
                        }
                    }
                } catch (Exception ignored) {}
            }
        });

        widthView.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (widthView.hasFocus() && keepAspect.isChecked()) {
                    try {
                        String str = s.toString().trim();
                        if (!str.isEmpty()) {
                            int wVal = Integer.parseInt(str);
                            if (wVal > 0 && ratio > 0) {
                                heightView.setText(String.valueOf(Math.round(wVal / ratio)));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        });

        heightView.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (heightView.hasFocus() && keepAspect.isChecked()) {
                    try {
                        String str = s.toString().trim();
                        if (!str.isEmpty()) {
                            int hVal = Integer.parseInt(str);
                            if (hVal > 0 && ratio > 0) {
                                widthView.setText(String.valueOf(Math.round(hVal * ratio)));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.resize_and_save))
                .setView(root)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(getString(R.string.preview), null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                int targetW = 0, targetH = 0;
                try {
                    targetW = Integer.parseInt(widthView.getText().toString().trim());
                    targetH = Integer.parseInt(heightView.getText().toString().trim());
                } catch (Exception ignored) {}
                if (targetW <= 0 || targetH <= 0) {
                    showError(getString(R.string.invalid_values));
                    return;
                }
                showResizePreview(targetW, targetH, cropX, cropY, baseCropW, baseCropH);
            });

            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int targetW = 0, targetH = 0;
                try {
                    targetW = Integer.parseInt(widthView.getText().toString().trim());
                    targetH = Integer.parseInt(heightView.getText().toString().trim());
                } catch (Exception ignored) {}
                if (targetW <= 0 || targetH <= 0) {
                    showError(getString(R.string.invalid_values));
                    return;
                }
                dialog.dismiss();
                applyResizeAndCrop(targetW, targetH, cropX, cropY, baseCropW, baseCropH);
            });
        });

        dialog.show();
    }

    private void showResizePreview(int targetW, int targetH, int cropX, int cropY, int cropW, int cropH) {
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap src = BitmapFactory.decodeFile(workingFile.getAbsolutePath(), opts);
                if (src == null) throw new Exception("Cannot decode image");

                int cx = Math.max(0, Math.min(cropX, src.getWidth() - 1));
                int cy = Math.max(0, Math.min(cropY, src.getHeight() - 1));
                int cw = Math.max(1, Math.min(cropW, src.getWidth() - cx));
                int ch = Math.max(1, Math.min(cropH, src.getHeight() - cy));

                Bitmap cropped = Bitmap.createBitmap(src, cx, cy, cw, ch);
                if (cropped != src) src.recycle();

                int maxDim = 2048;
                int pw = targetW;
                int ph = targetH;
                if (pw > maxDim || ph > maxDim) {
                    if (pw >= ph) {
                        ph = Math.max(1, Math.round(maxDim * (ph / (float) pw)));
                        pw = maxDim;
                    } else {
                        pw = Math.max(1, Math.round(maxDim * (pw / (float) ph)));
                        ph = maxDim;
                    }
                }

                Bitmap previewBitmap = Bitmap.createScaledBitmap(cropped, pw, ph, true);
                if (previewBitmap != cropped) cropped.recycle();

                runOnUiThread(() -> {
                    pm.dismiss();
                    ImageView iv = new ImageView(this);
                    iv.setAdjustViewBounds(true);
                    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    iv.setImageBitmap(previewBitmap);
                    int p = dp(16);
                    iv.setPadding(p, p, p, p);

                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.preview) + " (" + targetW + " × " + targetH + ")")
                            .setView(iv)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                });
            } catch (Throwable e) {
                pm.dismiss();
                runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).start();
    }

    private void applyResizeAndCrop(int targetW, int targetH, int cropX, int cropY, int cropW, int cropH) {
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap src = BitmapFactory.decodeFile(workingFile.getAbsolutePath(), opts);
                if (src == null) throw new Exception("Cannot decode image");

                int cx = Math.max(0, Math.min(cropX, src.getWidth() - 1));
                int cy = Math.max(0, Math.min(cropY, src.getHeight() - 1));
                int cw = Math.max(1, Math.min(cropW, src.getWidth() - cx));
                int ch = Math.max(1, Math.min(cropH, src.getHeight() - cy));

                Bitmap cropped = Bitmap.createBitmap(src, cx, cy, cw, ch);
                if (cropped != src) src.recycle();

                Bitmap scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true);
                if (scaled != cropped) cropped.recycle();

                try (FileOutputStream fos = new FileOutputStream(workingFile)) {
                    if (isJpeg) {
                        scaled.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                    } else {
                        scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                }
                scaled.recycle();

                resetOrientationTag();
                readDims();
                dirty = true;
                hasPixelEdits = true;
                pm.dismiss();
                runOnUiThread(() -> {
                    reloadPreview();
                    resetCropToFull();
                    updateInfo();
                });
            } catch (Throwable e) {
                pm.dismiss();
                runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).start();
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
            if (isJpeg) JpegMetaStrip.fixJpegMarkerOrder(workingFile);
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
                hasPixelEdits = true;
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
                Matrix matrix = new Matrix();
                switch (op) {
                    case "left" -> matrix.postRotate(-90);
                    case "right" -> matrix.postRotate(90);
                    case "flipH" -> matrix.postScale(-1, 1);
                    default -> matrix.postScale(1, -1);
                }
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
            if (isJpeg) JpegMetaStrip.fixJpegMarkerOrder(workingFile);
        } catch (Exception ignored) {
        }
    }

    private void showExifEditor() {
        String[] tags = {
                ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_SOFTWARE};
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad / 2, pad, 0);
        ScrollView scroll = new ScrollView(this);
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(fields, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
        List<EditText> inputs = new ArrayList<>();

        EditText dateTakenInput = new EditText(this);
        EditText mtimeInput = new EditText(this);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

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

            // 1. Date taken
            TextView dateTakenLabel = new TextView(this);
            dateTakenLabel.setTextSize(13);
            dateTakenLabel.setText(getString(R.string.date_taken));
            dateTakenLabel.setPadding(0, dp(8), 0, 0);
            fields.addView(dateTakenLabel);

            LinearLayout dateTakenRow = new LinearLayout(this);
            dateTakenRow.setOrientation(LinearLayout.HORIZONTAL);
            dateTakenRow.setGravity(Gravity.CENTER_VERTICAL);

            String origDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            long parsedTaken = parseDateTime(origDate);
            if (parsedTaken > 0) {
                dateTakenInput.setText(sdf.format(new java.util.Date(parsedTaken)));
            } else {
                dateTakenInput.setText(origDate != null ? origDate : "");
            }
            dateTakenInput.setSingleLine(true);
            dateTakenRow.addView(dateTakenInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            ImageView dateTakenPickerBtn = new ImageView(this);
            dateTakenPickerBtn.setImageResource(R.drawable.ic_calendar_today_24);
            int iconPad = dp(8);
            dateTakenPickerBtn.setPadding(iconPad, iconPad, iconPad, iconPad);
            dateTakenPickerBtn.setOnClickListener(v -> showDateTimePicker(dateTakenInput, sdf));
            dateTakenRow.addView(dateTakenPickerBtn);
            fields.addView(dateTakenRow);

            // 2. Last modified time
            TextView mtimeLabel = new TextView(this);
            mtimeLabel.setTextSize(13);
            mtimeLabel.setText(getString(R.string.sort_by_modified_time));
            mtimeLabel.setPadding(0, dp(8), 0, 0);
            fields.addView(mtimeLabel);

            LinearLayout mtimeRow = new LinearLayout(this);
            mtimeRow.setOrientation(LinearLayout.HORIZONTAL);
            mtimeRow.setGravity(Gravity.CENTER_VERTICAL);

            long curMtime = targetMtime > 0 ? targetMtime : -1L;
            if (curMtime <= 0 && originalPath != null) {
                curMtime = new File(originalPath).lastModified();
                if (curMtime <= 0) {
                    try {
                        curMtime = AccessManager.getMtime(this, originalPath);
                    } catch (Exception ignored) {}
                }
            }
            if (curMtime <= 0) {
                String dtTag = exif.getAttribute(ExifInterface.TAG_DATETIME);
                curMtime = parseDateTime(dtTag);
            }
            if (curMtime <= 0) curMtime = workingFile.lastModified();
            if (curMtime <= 0) curMtime = System.currentTimeMillis();
            mtimeInput.setText(sdf.format(new java.util.Date(curMtime)));
            mtimeInput.setSingleLine(true);
            mtimeRow.addView(mtimeInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            ImageView mtimePickerBtn = new ImageView(this);
            mtimePickerBtn.setImageResource(R.drawable.ic_calendar_today_24);
            mtimePickerBtn.setPadding(iconPad, iconPad, iconPad, iconPad);
            mtimePickerBtn.setOnClickListener(v -> showDateTimePicker(mtimeInput, sdf));
            mtimeRow.addView(mtimePickerBtn);
            fields.addView(mtimeRow);

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

                            // Save Date Taken
                            String takenText = dateTakenInput.getText() == null ? "" : dateTakenInput.getText().toString().trim();
                            if (!takenText.isEmpty()) {
                                long takenMillis = parseDateTime(takenText);
                                java.text.SimpleDateFormat exifSdf = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                                String formattedTaken = takenMillis > 0 ? exifSdf.format(new java.util.Date(takenMillis)) : takenText;
                                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, formattedTaken);
                                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, formattedTaken);
                            } else {
                                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null);
                                exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, null);
                            }

                            // Save Mtime to ExifInterface and file system
                            String mtimeText = mtimeInput.getText() == null ? "" : mtimeInput.getText().toString().trim();
                            long parsedMtime = parseDateTime(mtimeText);
                            if (parsedMtime > 0) {
                                targetMtime = parsedMtime;
                                java.text.SimpleDateFormat exifSdf = new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US);
                                exif.setAttribute(ExifInterface.TAG_DATETIME, exifSdf.format(new java.util.Date(parsedMtime)));
                            }

                            exif.saveAttributes();
                            if (isJpeg) JpegMetaStrip.fixJpegMarkerOrder(workingFile);

                            if (parsedMtime > 0) {
                                workingFile.setLastModified(parsedMtime);
                                if (originalPath != null) {
                                    new File(originalPath).setLastModified(parsedMtime);
                                    try {
                                        AccessManager.touchMtime(ImageEditActivity.this, originalPath, parsedMtime);
                                    } catch (Exception ignored) {}
                                }
                            }

                            if (originalPath != null) {
                                scanMediaFile(originalPath);
                            }

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

    private void showDateTimePicker(EditText targetInput, java.text.SimpleDateFormat sdf) {
        long current = parseDateTime(targetInput.getText() != null ? targetInput.getText().toString().trim() : "");
        if (current <= 0) current = System.currentTimeMillis();

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(current);
        int year = cal.get(java.util.Calendar.YEAR);
        int month = cal.get(java.util.Calendar.MONTH);
        int day = cal.get(java.util.Calendar.DAY_OF_MONTH);
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int minute = cal.get(java.util.Calendar.MINUTE);

        new android.app.DatePickerDialog(this, (dp, y, m, d) ->
                new android.app.TimePickerDialog(this, (tp, h, min) -> {
                    java.util.Calendar res = java.util.Calendar.getInstance();
                    res.set(y, m, d, h, min, 0);
                    targetInput.setText(sdf.format(new java.util.Date(res.getTimeInMillis())));
                }, hour, minute, true).show(), year, month, day).show();
    }

    private static long parseDateTime(String str) {
        if (str == null || str.trim().isEmpty()) return -1;
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss",
                "yyyy:MM:dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                java.util.Date date = sdf.parse(str.trim());
                if (date != null) return date.getTime();
            } catch (Exception ignored) {}
        }
        try {
            long millis = Long.parseLong(str.trim());
            if (millis > 0) return millis;
        } catch (Exception ignored) {}
        return -1;
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
                boolean isRoot = false;
                try {
                    RootManager rm = RootManager.getInstance(this);
                    isRoot = (AccessManager.active(this) == AccessManager.Backend.ROOT)
                            || (rm.isRootFileOpsEnabled() && rm.isRootAvailable());
                } catch (Exception ignored) {}

                if (isRoot) {
                    // ==========================================
                    // PATH A: Root Mode (su / elevated)
                    // ==========================================
                    String targetPath = toPhysicalPathIfRoot(originalPath);
                    if (targetPath == null || targetPath.isEmpty()) targetPath = originalPath;
                    String bakPath = targetPath + ".bak";
                    AppLogs.log("IMAGE_EDIT", "[ROOT] Saving image to " + targetPath + ", backup to " + bakPath);

                    RootManager rm = RootManager.getInstance(this);
                    // 1. Create backup with root preserving all attributes
                    rm.copyFilePreserving(targetPath, bakPath);

                    // 2. Copy EXIF metadata from original backup to workingFile
                    copyExif(bakPath, workingFile.getAbsolutePath());

                    // 3. Write edited image to targetPath using root
                    rm.copyFilePreserving(workingFile.getAbsolutePath(), targetPath);

                    // 4. Restore original ownership, mode, and timestamp on targetPath matching bakPath
                    try {
                        String restoreCmd = "chown $(stat -c '%u:%g' " + RootManager.escapeShellArg(bakPath) + ") "
                                + RootManager.escapeShellArg(targetPath) + " 2>/dev/null; "
                                + "chmod $(stat -c '%a' " + RootManager.escapeShellArg(bakPath) + ") "
                                + RootManager.escapeShellArg(targetPath) + " 2>/dev/null; "
                                + "touch -r " + RootManager.escapeShellArg(bakPath) + " "
                                + RootManager.escapeShellArg(targetPath) + " 2>/dev/null";
                        rm.execute(restoreCmd, 5);
                    } catch (Exception ignored) {}

                    // 5. Scan media scanner for both files
                    scanMediaFile(targetPath);
                    scanMediaFile(bakPath);
                    AppLogs.log("IMAGE_EDIT", "[ROOT] Successfully saved and scanned: " + targetPath);
                } else {
                    // ==========================================
                    // PATH B: Normal Mode (Non-root users)
                    // ==========================================
                    File original = originalPath != null ? new File(originalPath) : null;
                    if (fromShared || original == null || !original.canWrite()) {
                        pm.dismiss();
                        runOnUiThread(this::promptSharedSaveLocation);
                        return;
                    }

                    String bakPath = originalPath + ".bak";
                    AppLogs.log("IMAGE_EDIT", "[NORMAL] Saving in-place to " + originalPath + ", backup to " + bakPath);
                    File bak = new File(bakPath);
                    try {
                        copyFile(original, bak);
                    } catch (Exception e) {
                        AppLogs.logError("IMAGE_EDIT", "Failed to create normal backup", e);
                    }

                    // Copy EXIF
                    copyExif(bakPath, workingFile.getAbsolutePath());

                    // Write workingFile to original
                    copyFile(workingFile, original);

                    long mtimeToPreserve = targetMtime > 0 ? targetMtime : bak.lastModified();
                    if (mtimeToPreserve > 0) {
                        try {
                            original.setLastModified(mtimeToPreserve);
                            AccessManager.touchMtime(ImageEditActivity.this, originalPath, mtimeToPreserve);
                        } catch (Exception ignored) {}
                    }

                    scanMediaFile(originalPath);
                    scanMediaFile(bakPath);
                    AppLogs.log("IMAGE_EDIT", "[NORMAL] Successfully saved: " + originalPath);
                }

                pm.dismiss();
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                AppLogs.logError("IMAGE_EDIT", "Error during doSave", e);
                pm.dismiss();
                runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).start();
    }

    private void promptSharedSaveLocation() {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        FilePickerDialog.Properties props = new FilePickerDialog.Properties();
        props.selection_mode = FilePickerDialog.SINGLE_MODE;
        props.selection_type = FilePickerDialog.DIR_SELECT;
        if (pictures != null && pictures.isDirectory()) props.offset = pictures;
        FilePickerDialog picker = new FilePickerDialog(this, props);
        picker.setTitle(getString(R.string.save_to_folder));
        picker.setDialogSelectionListener(files -> {
            if (files == null || files.length == 0 || files[0] == null) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            saveSharedToFolder(new File(files[0]));
        });
        picker.show();
    }

    private void saveSharedToFolder(File dir) {
        ProgressManager pm = new ProgressManager(this, true).show();
        new Thread(() -> {
            try {
                String name = sharedName != null ? sharedName : new File(originalPath).getName();
                File dest = new File(dir, name);
                int copy = 1;
                while (dest.exists()) {
                    int dot = name.lastIndexOf('.');
                    String base = dot > 0 ? name.substring(0, dot) : name;
                    String ext = dot > 0 ? name.substring(dot) : "";
                    dest = new File(dir, base + " (" + (++copy) + ")" + ext);
                }
                copyFile(workingFile, dest);
                final String savedPath = dest.getAbsolutePath();
                scanMediaFile(savedPath);
                pm.dismiss();
                runOnUiThread(() -> {
                    showError(getString(R.string.logger_saved_to, savedPath));
                    finish();
                });
            } catch (Exception e) {
                pm.dismiss();
                runOnUiThread(() -> showError(e.getMessage() != null ? e.getMessage() : e.toString()));
            }
        }).start();
    }

    private void scanMediaFile(String path) {
        if (path == null) return;
        try {
            MediaScannerConnection.scanFile(this, new String[]{path}, null, null);
            String emulated = null;
            if (path.startsWith("/data/media/0/")) {
                emulated = "/storage/emulated/0/" + path.substring("/data/media/0/".length());
            } else if (path.startsWith("/data/media/")) {
                emulated = "/storage/emulated/" + path.substring("/data/media/".length());
            }
            if (emulated != null) {
                MediaScannerConnection.scanFile(this, new String[]{emulated}, null, null);
            }
            String scanTarget = emulated != null ? emulated : path;
            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            scanIntent.setData(Uri.fromFile(new File(scanTarget)));
            sendBroadcast(scanIntent);

            try {
                RootManager rm = RootManager.getInstance(this);
                if (rm.isRootFileOpsEnabled() && rm.isRootAvailable()) {
                    rm.execute("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "
                            + RootManager.escapeShellArg("file://" + scanTarget) + " >/dev/null 2>&1", 3);
                }
            } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    private void confirmExit() {
        if (!dirty && !hasCrop()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.unsaved_changes))
                .setMessage(getString(R.string.save_before_exit))
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

    private void copyFile(File src, File dst) throws Exception {
        try {
            AccessManager.copyFile(this, src.getAbsolutePath(), dst.getAbsolutePath(), AccessManager.needsElevated(this, src.getAbsolutePath()) || AccessManager.needsElevated(this, dst.getAbsolutePath()));
            return;
        } catch (Exception e) {
            // Fall back to direct stream or executeFs
            try (InputStream in = new FileInputStream(src); OutputStream os = new FileOutputStream(dst)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
                return;
            } catch (Exception directError) {
                try {
                    RootManager rm = RootManager.getInstance(this);
                    if (rm.isRootAvailable()) {
                        rm.copyFile(src.getAbsolutePath(), dst.getAbsolutePath());
                        return;
                    }
                } catch (Exception ignored) {}
                throw e;
            }
        }
    }

    private void showError(String message) {
        Extensions.showMessage(this, message);
    }

    private void copyExif(String srcPath, String dstPath) {
        if (!isJpeg || srcPath == null || dstPath == null) return;
        File stagedSrc = null;
        try {
            ExifInterface srcExif;
            try {
                srcExif = new ExifInterface(srcPath);
            } catch (Exception e) {
                // If srcPath is root-protected or not directly accessible
                stagedSrc = RootStaging.stageForRead(this, srcPath);
                srcExif = new ExifInterface(stagedSrc.getAbsolutePath());
            }
            ExifInterface dstExif = new ExifInterface(dstPath);
            String[] tags = {
                    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_DATETIME, ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_ISO_SPEED_RATINGS,
                    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_APERTURE_VALUE,
                    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_DATESTAMP,
                    ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
                    ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
                    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_IMAGE_DESCRIPTION,
                    ExifInterface.TAG_USER_COMMENT, ExifInterface.TAG_EXIF_VERSION
            };
            for (String tag : tags) {
                String val = srcExif.getAttribute(tag);
                if (val != null) {
                    dstExif.setAttribute(tag, val);
                }
            }
            dstExif.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            dstExif.saveAttributes();
            JpegMetaStrip.fixJpegMarkerOrder(new File(dstPath));
        } catch (Exception e) {
            AppLogs.logError("IMAGE_EDIT", "Failed to copy EXIF attributes from " + srcPath + " to " + dstPath, e);
        } finally {
            if (stagedSrc != null) {
                try { stagedSrc.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

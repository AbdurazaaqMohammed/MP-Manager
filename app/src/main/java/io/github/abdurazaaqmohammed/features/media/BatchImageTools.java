package io.github.abdurazaaqmohammed.features.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.exifinterface.media.ExifInterface;

import io.github.abdurazaaqmohammed.MPManager.MainActivity;
import io.github.abdurazaaqmohammed.MPManager.R;
import io.github.abdurazaaqmohammed.utils.DialogUtil;
import io.github.abdurazaaqmohammed.utils.ErrorUtil;
import io.github.abdurazaaqmohammed.utils.FileUtils;
import io.github.abdurazaaqmohammed.utils.JpegMetaStrip;
import io.github.abdurazaaqmohammed.utils.JpegtranJni;
import io.github.abdurazaaqmohammed.utils.NativeToolManager;
import io.github.abdurazaaqmohammed.utils.ProgressManager;
import io.github.codehasan.colorpicker.extensions.Extensions;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Batch JPEG/PNG operations (crop, EXIF edit, metadata strip) extracted
 * from MainFilesArrayAdapter. Selection comes from the caller; completion
 * is reported back so the caller can refresh.
 */
public class BatchImageTools {

    public interface Selection {
        List<File> selectedImages();

        List<File> selectedJpegs();
    }

    public interface Finish {
        void onBatchDone(String doneText);
    }

    private final MainActivity context;
    private final DialogUtil dialogUtil;
    private final Selection selection;
    private final Finish finish;

    public BatchImageTools(MainActivity context, DialogUtil dialogUtil,
            Selection selection, Finish finish) {
        this.context = context;
        this.dialogUtil = dialogUtil;
        this.selection = selection;
        this.finish = finish;
    }

    public static boolean isJpegPath(String name) {
        String lower = name.toLowerCase(Locale.ENGLISH);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    public static boolean isPngPath(String name) {
        return name.toLowerCase(Locale.ENGLISH).endsWith(".png");
    }

    public void batchCrop() {
        List<File> images = selection.selectedImages();
        if (images.isEmpty()) {
            Extensions.showMessage(context, R.string.no_images_selected);
            return;
        }
        showBatchCropDialog(images);
    }

    public void batchExif() {
        List<File> images = selection.selectedJpegs();
        if (images.isEmpty()) {
            Extensions.showMessage(context, R.string.no_jpeg_files_selected);
            return;
        }
        showBatchExifDialog(images);
    }

    public void batchStrip() {
        List<File> images = selection.selectedJpegs();
        if (images.isEmpty()) {
            Extensions.showMessage(context, R.string.no_jpeg_files_selected);
            return;
        }
        confirmBatchStrip(images);
    }

    private void backupImage(File f) {
        try {
            FileUtils.copyFile(f, new File(f.getParent(), f.getName() + ".bak"));
        } catch (Exception ignored) {
        }
    }

    private int[] imageDims(File f) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
        return new int[]{bounds.outWidth, bounds.outHeight};
    }

    private void finishBatchOp(String doneText) {
        finish.onBatchDone(doneText);
    }

    private void showBatchCropDialog(List<File> images) {
        int minW = Integer.MAX_VALUE;
        int minH = Integer.MAX_VALUE;
        for (File f : images) {
            int[] dims = imageDims(f);
            if (dims[0] > 0) minW = Math.min(minW, dims[0]);
            if (dims[1] > 0) minH = Math.min(minH, dims[1]);
        }
        if (minW == Integer.MAX_VALUE) {
            Extensions.showMessage(context, "Cannot read images");
            return;
        }
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad / 2, pad, 0);
        EditText wInput = new EditText(context);
        wInput.setHint("Width");
        wInput.setText(String.valueOf(minW));
        wInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        wInput.setSingleLine(true);
        root.addView(wInput);
        EditText hInput = new EditText(context);
        hInput.setHint("Height");
        hInput.setText(String.valueOf(minH));
        hInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        hInput.setSingleLine(true);
        root.addView(hInput);
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(context.getString(R.string.crop_X_imgs, images.size()))
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.crop, (d, w) -> {
                    int reqW;
                    int reqH;
                    try {
                        reqW = Integer.parseInt(wInput.getText().toString().trim());
                        reqH = Integer.parseInt(hInput.getText().toString().trim());
                        if (reqW <= 0 || reqH <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        Extensions.showMessage(context, R.string.enter_width_and_height);
                        return;
                    }
                    ArrayList<File> targets = new ArrayList<>(images);
                    boolean needJni = false;
                    for (File f : targets) {
                        if (isJpegPath(f.getName())) {
                            needJni = true;
                            break;
                        }
                    }
                    if (needJni && !NativeToolManager.loadJpegtranJni(context)) {
                        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                                .setTitle(R.string.crop_quality)
                                .setMessage(R.string.jpegtran_info)
                                .setNegativeButton(android.R.string.cancel, null)
                                .setNeutralButton(R.string.standard_crop, (dd, ww) -> runBatchCrop(targets, reqW, reqH, true))
                                .setPositiveButton(R.string.lossless, (dd, ww) -> NativeToolManager.ensureJpegtran(context,
                                        new NativeToolManager.ReadyCallback() {
                                            public void onReady() {
                                                runBatchCrop(targets, reqW, reqH, false);
                                            }

                                            public void onError(String message) {
                                                Extensions.showMessage(context, message);
                                            }
                                        })).create());
                    } else {
                        runBatchCrop(targets, reqW, reqH, false);
                    }
                }).create());
    }

    private void runBatchCrop(List<File> images, int reqW, int reqH, boolean forceLossy) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            try {
                boolean useJni = NativeToolManager.loadJpegtranJni(context);
                int done = 0;
                int skipped = 0;
                for (File f : images) {
                    pm.setText(context.rss.getString(R.string.processing_x, f.getName()));
                    int[] dims = imageDims(f);
                    if (dims[0] <= 0 || dims[1] <= 0) {
                        skipped++;
                        continue;
                    }
                    int w = Math.min(reqW, dims[0]);
                    int h = Math.min(reqH, dims[1]);
                    int x = (dims[0] - w) / 2;
                    int y = (dims[1] - h) / 2;
                    backupImage(f);
                    if (isJpegPath(f.getName()) && useJni && !forceLossy) {
                        x -= x % 16;
                        y -= y % 16;
                        w -= w % 16;
                        h -= h % 16;
                        if (w <= 0 || h <= 0) {
                            skipped++;
                            continue;
                        }
                        File tmp = new File(context.getCacheDir(), "batchcrop_" + System.currentTimeMillis() + ".jpg");
                        String[] err = new String[1];
                        int rc = JpegtranJni.transform(f.getAbsolutePath(), tmp.getAbsolutePath(),
                                JpegtranJni.OP_CROP, w, h, x, y, err);
                        if (rc != 0) {
                            tmp.delete();
                            skipped++;
                            continue;
                        }
                        FileUtils.copyFile(tmp, f);
                        tmp.delete();
                        try {
                            ExifInterface exif =
                                    new ExifInterface(f.getAbsolutePath());
                            exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                                    String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                            exif.saveAttributes();
                        } catch (Exception ignored) {
                        }
                    } else {
                        boolean jpeg = isJpegPath(f.getName());
                        BitmapFactory.Options bitmapOpts = new BitmapFactory.Options();
                        bitmapOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        Bitmap src = BitmapFactory.decodeFile(f.getAbsolutePath(), bitmapOpts);
                        if (src == null) {
                            skipped++;
                            continue;
                        }
                        int cx = Math.max(0, Math.min(x, src.getWidth() - 1));
                        int cy = Math.max(0, Math.min(y, src.getHeight() - 1));
                        int cw = Math.max(1, Math.min(w, src.getWidth() - cx));
                        int ch = Math.max(1, Math.min(h, src.getHeight() - cy));
                        Bitmap out = Bitmap.createBitmap(src, cx, cy, cw, ch);
                        try (FileOutputStream fos = new FileOutputStream(f)) {
                            out.compress(jpeg ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG,
                                    jpeg ? 95 : 100, fos);
                        } catch (Exception e) {
                            skipped++;
                            continue;
                        } finally {
                            if (out != src) out.recycle();
                            src.recycle();
                        }
                        if (jpeg) {
                            try {
                                ExifInterface exif =
                                        new ExifInterface(f.getAbsolutePath());
                                exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                                        String.valueOf(ExifInterface.ORIENTATION_NORMAL));
                                exif.saveAttributes();
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    done++;
                }
                pm.dismiss();
                int doneCount = done;
                int skippedCount = skipped;
                context.handler.post(() -> finishBatchOp(context.getString(R.string.cropped_xskippedx, doneCount, skippedCount)));
            } catch (Exception e) {
                pm.dismiss();
                new ErrorUtil(context).showError(e);
            }
        }).start();
    }

    private void showBatchExifDialog(List<File> images) {
        String[] tags = {
                ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_DATETIME};
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density + 0.5f);
        root.setPadding(pad, pad / 2, pad, 0);
        TextView hint = new TextView(context);
        hint.setTextSize(13);
        hint.setText(context.getString(R.string.hintbatchexif, images.size()));
        root.addView(hint);
        List<EditText> inputs = new ArrayList<>();
        for (String tag : tags) {
            TextView label = new TextView(context);
            label.setTextSize(13);
            label.setText(tag);
            root.addView(label);
            EditText input = new EditText(context);
            input.setSingleLine(true);
            root.addView(input);
            inputs.add(input);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.set_exif_tags)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.apply, (d, w) -> {
                    String[] vals = new String[tags.length];
                    for (int i = 0; i < tags.length; i++) {
                        vals[i] = inputs.get(i).getText() == null ? "" : inputs.get(i).getText().toString();
                    }
                    runBatchExif(new ArrayList<>(images), tags, vals);
                }).create());
    }

    private void runBatchExif(List<File> images, String[] tags, String[] vals) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            int done = 0;
            for (File f : images) {
                pm.setText(context.rss.getString(R.string.processing_x, f.getName()));
                try {
                    backupImage(f);
                    ExifInterface exif =
                            new ExifInterface(f.getAbsolutePath());
                    for (int i = 0; i < tags.length; i++) {
                        if (!vals[i].isEmpty()) exif.setAttribute(tags[i], vals[i]);
                    }
                    exif.saveAttributes();
                    done++;
                } catch (Exception ignored) {
                }
            }
            pm.dismiss();
            int doneCount = done;
            context.handler.post(() -> finishBatchOp(context.rss.getString(R.string.updated_i_of_i, doneCount, images.size())));
        }).start();
    }

    private void confirmBatchStrip(List<File> images) {
        dialogUtil.styleAlertDialog(dialogUtil.getDialogBuilder()
                .setTitle(R.string.remove_metadata)
                .setMessage(context.getString(R.string.strip_metadata_info, images.size()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.remove, (d, w) -> runBatchStrip(new ArrayList<>(images)))
                .create());
    }

    private void runBatchStrip(List<File> images) {
        ProgressManager pm = new ProgressManager(context, true).show();
        new Thread(() -> {
            int done = 0;
            for (File f : images) {
                pm.setText(context.rss.getString(R.string.processing_x, f.getName()));
                try {
                    backupImage(f);
                    JpegMetaStrip.stripFile(f);
                    done++;
                } catch (Exception ignored) {
                }
            }
            pm.dismiss();
            int doneCount = done;
            context.handler.post(() -> finishBatchOp(context.getString(R.string.removed_metadata_fromxofx, doneCount, images.size())));
        }).start();
    }
}
